package nars.op.sys;

import alice.tuprolog.*;
import nars.NAR;
import nars.Op;
import nars.data.Range;
import nars.task.Task;
import nars.term.Compound;
import nars.term.Term;
import nars.term.Termed;
import nars.term.atom.Atomic;
import nars.term.variable.Variable;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/** Prolog mental coprocessor for accelerating reasoning
 *  WARNING - introduces cognitive distortion
 *
 * Causes a NARProlog to mirror certain activity of a NAR.  It generates
 * prolog terms from NARS beliefs, and answers NARS questions with the results
 * of a prolog solution (converted to NARS terms), which are input to NARS memory
 * with the hope that this is sooner than NARS can solve it by itself.
 */
public class PrologCore extends Agent implements Consumer<Task> {

    private final NAR nar;

    final static Logger logger = LoggerFactory.getLogger(PrologCore.class);

    /** beliefs above this expectation will be asserted as prolog beliefs */
    @Range(min=0.5, max=1.0)
    public final MutableFloat trueExpectationThreshold = new MutableFloat(0.9f);

    /** beliefs below this expectation will be asserted as negated prolog beliefs */
    @Range(min=0, max=0.5)
    public final MutableFloat falseExpectationThreshold = new MutableFloat(0.1f);

    /*final ObjectBooleanHashMap<Term> beliefs = new ObjectBooleanHashMap() {


    };*/

    public PrologCore(NAR n) {
        this(n, null);
    }

    public PrologCore(NAR n, String theory) {
        super(theory);
        this.nar = n;

        n.eventTaskProcess.on(this);
    }

    @Override
    public void accept(Task task) {

        if (task.isJudgment() ) {
            if (task.isEternal()) {
                float exp = task.expectation();
                if (exp > trueExpectationThreshold.floatValue())
                    believe(task, true);
                else if (exp < falseExpectationThreshold.floatValue())
                    believe(task, false);
                /* else: UNSURE */
            }
        } else if (task.isQuestion()) {
            if (task.isEternal()) {
                question(task);
            }
        }


        //TODO if task is goal then wrap as goal belief
    }

    protected void believe(Termed t, boolean truth) {
        Term tt = t.term();
        if (t.op() == Op.NEGATE) {
            //unwrap negation
            tt = ((Compound)tt).term(0);
            truth = !truth;
        }
        believe(pterm(tt), truth);
    }

    //TODO async
    protected void question(Task question) {
        Term tt = question.term();
        /*if (t.op() == Op.NEGATE) {
            //unwrap negation
            tt = ((Compound)tt).term(0);
            truth = !truth;
        }*/

        PTerm questionTerm = pterm(tt);

        solve(questionTerm, (answer)-> {

            // supply input an answer to the NAR

            logger.info("question {} answer {}", question, answer);
            switch (answer.result()) {
                case Solution.TRUE:
                case Solution.TRUE_CP:
                    logger.info("TRUE"); //TODO input
                    break;
                case Solution.FALSE:
                    logger.info("FALSE"); //TODO input
                    break;
                default:
                    //no known solution, remain silent
                    break;
            }
        });

    }


    //TODO async
    protected void believe(PTerm p, boolean truth) {
        if (!truth) {
            //wrap in negate
            p = negate(p);
        }
        Struct belief = assertion(p);

        Solution s = solve(belief);
        logger.info("believe {} {}", belief, s);

    }

    public static Struct assertion(PTerm p) {
        return new Struct("assertz", p);
    }

    public static Struct negate(PTerm p) {
        return new Struct("not", p); //TODO issue retraction on the opposite? ex: retract(x), assertz(not(x))
    }

    public static PTerm[] psubterms(final Compound subtermed) {
        int l = subtermed.size();
        PTerm[] p = new PTerm[l];
        for (int i = 0; i < l; i++) {
            p[i] = pterm(subtermed.term(i));
        }
        return p;
    }

    //NARS term -> Prolog term
    public static PTerm pterm(final Term term) {
        if (term instanceof Compound) {
            Op op = term.op();
            return new Struct(op.str, psubterms( ((Compound)term) ));
        } else if (term instanceof Variable) {
            switch (term.op()) {
                case VAR_QUERY:
                case VAR_PATTERN:
                case VAR_INDEP: //??
                    return new Var("_" + (((Variable) term).id()));
                case VAR_DEP: //?? as if atomic
                    return new Struct( "'#" + ((Variable)term).id() + '\'' );
            }
        } else if (term instanceof Atomic) {
            return new Struct(term.toString());
        }

        throw new UnsupportedOperationException();

//        //CharSequence s = termString(term);
//        if (term instanceof Statement) {
//            Statement i = (Statement)term;
//            String predicate = classPredicate(i.getClass());
//            PTerm subj = pterm(i.getSubject());
//            PTerm obj = pterm(i.getPredicate());
//            if ((subj!=null) && (obj!=null))
//                return new Struct(predicate, subj, obj);
//        }
//        else if ((term instanceof SetTensional) || (term instanceof Product) /* conjunction */) {
//            Compound s = (Compound)term;
//            String predicate = classPredicate(s.getClass());
//            PTerm[] args = pterms(s.term);
//            if (args!=null)
//                return new Struct(predicate, args);
//        }
//        //Image...
//        //Conjunction...
//        else if (term instanceof Negation) {
//            PTerm np = pterm(((Negation)term).term[0]);
//            if (np == null) return null;
//            return new Struct("negation", np);
//        }
//        else if (term.getClass().equals(Variable.class)) {
//            return getVariable((Variable)term);
//        }
//        else if (term.getClass().equals(Atom.class)) {
//            return new Struct(pescape(term.toString()));
//        }
//        else if (term instanceof Compound) {
//            //unhandled type of compound term, store as an atomic string
//            //NOT ready yet
//            if (allTerms) {
//                return new Struct('_' + pescape(term.toString()));
//            }
//        }
//
//        return null;
    }

//    private static Term getVar(Var v) {
//        //assume it is a dependent variable
//        return new Variable('#' + v.getName());
//    }
//
//
//    private static Var getVariable(Variable v) {
//        if (v.hasVarIndep())
//            return new Var('I' + v.getIdentifier());
//        if (v.hasVarQuery())
//            return new Var("Q" + nextQueryID++);
//        if (v.hasVarDep()) //check this
//            return new Var("D" + (variableContext) + '_' + v.getIdentifier());
//        return null;
//    }
//
//    /** Prolog term --> NARS statement */
//    public static Term nterm(final PTerm term) {
//
//        if (term instanceof Struct) {
//            Struct s = (Struct)term;
//            int arity = s.getArity();
//            String predicate = s.getName();
//            if (arity == 0) {
//                return Atom.get(unpescape(predicate));
//            }
//            if (arity == 1) {
//                switch (predicate) {
//                    case "negation":
//                        return Negation.make(nterm(s.getArg(0)));
////                    default:
////                        throw new RuntimeException("Unknown 1-arity nars predicate: " + predicate);
//                }
//            }
//            switch (predicate) {
//                case "product":
//                    Term[] a = nterm(s.getArg());
//                    if (a != null) return Product.make(a);
//                    else return null;
//                case "setint":
//                    Term[] b = nterm(s.getArg());
//                    if (b!=null) return SetInt.make(b);
//                    else return null;
//                case "setext":
//                    Term[] c = nterm(s.getArg());
//                    if (c!=null) return SetExt.make(c);
//                    else return null;
//
//            }
//
//            if (arity == 2) {
//                Term a = nterm(s.getArg(0));
//                Term b = nterm(s.getArg(1));
//                if ((a!=null) && (b!=null)) {
//                    switch (predicate) {
//                        case "inheritance":
//                            return Inheritance.make(a, b);
//                        case "similarity":
//                            return Similarity.make(a, b);
//                        case "implication":
//                            return Implication.make(a, b);
//                        case "equivalence":
//                            return Equivalence.makeTerm(a, b);
//                        //TODO more types
////                        default:
////                            throw new RuntimeException("Unknown 2-arity nars predicate: " + predicate);
//
//
//                    }
//                }
//            }
//            System.err.println("nterm() does not yet support translation to NARS terms of Prolog: " + term);
//        }
//        else if (term instanceof Var) {
//            Var v = (Var)term;
//            PTerm t = v.getTerm();
//            if (t!=v) {
//                //System.out.println("Bound: " + v + " + -> " + t + " " + nterm(t));
//                return nterm(t);
//            }
//            else {
//                //System.out.println("Unbound: " + v);
//                //unbound variable, is there anything we can do with it?
//                return getVar(v);
//            }
//        }
//        else if (term instanceof nars.tuprolog.Number) {
//            nars.tuprolog.Number n = (nars.tuprolog.Number)term;
//            return Atom.get('"' + String.valueOf(n.doubleValue()) + '"');
//        }
//
//        return null;
//    }



//    public final NAR nar;
//    public final NARTuprolog prolog;
//
//    Theory axioms;
//
//    private float trueThreshold = 0.80f;
//    private float falseThreshold = 0.20f;
//    private float confidenceThreshold;
//    private final Map<Sentence,PTerm> beliefs = new HashMap();
//
//    private boolean eternalJudgments = true;
//    private boolean presentJudgments = false;
//
//    /** how much to scale the memory's duration parameter for this reasoner's "now" duration; default=1.0 */
//    float durationMultiplier = 1.0f;
//
//    /** how often to remove temporally irrelevant beliefs */
//    @Deprecated float forgetCyclePeriod; ///TODO use a Map<Long,belief> indexed by expiration time, so they can be removed efficiently
//    private long lastFlush;
//    private int durationCycles;
//
//    static boolean allTerms = false;
//
//    /** in seconds */
//    float maxSolveTime;
//    float minSolveTime;
//
//    /** max # answers returned in response to a question */
//    int maxAnswers = 3;
//
//    boolean reportAssumptions = false;
//    boolean reportForgets = false;
//    boolean reportAnswers = false;
//
//
//
//    public static final Class[] telepathicEvents = { Events.ConceptBeliefAdd.class, Events.ConceptBeliefRemove.class, Events.ConceptQuestionAdd.class, IN.class, OUT.class, Answer.class };
//
//    public static final Class[] inputOutputEvents = { IN.class, OUT.class };
//    private InputMode inputMode = InputMode.InputTask;
//
//    //serial #'s
//    static long nextQueryID = 0;
//    static long variableContext = 0;
//
//    public NARPrologMirror(NAR nar, float minConfidence, boolean telepathic, boolean eternalJudgments, boolean presentJudgments) {
//        super(nar, true, telepathic ? telepathicEvents : inputOutputEvents );
//        this.nar = nar;
//        this.confidenceThreshold = minConfidence;
//        this.prolog = new NARTuprolog(nar);
//        this.forgetCyclePeriod = nar.memory.duration() / 2;
//        this.maxSolveTime = 40.0f / 1e3f;
//        this.minSolveTime = maxSolveTime/2f;
//
//        try {
//            PTerm[] ax = toArray(new Theory(getAxiomString()).iterator(prolog.prolog), PTerm.class);
//            axioms = new Theory(ax);
//        } catch (InvalidTheoryException e) {
//            e.printStackTrace();
//            System.exit(1);
//        }
//
//
//        setTemporalMode(eternalJudgments, presentJudgments);
//    }
//
//    public NARPrologMirror setInputMode(InputMode i) {
//        this.inputMode = i;
//        return this;
//    }
//    public NARPrologMirror setTemporalMode(boolean eternalJudgments, boolean presentJudgments) {
//        this.eternalJudgments = eternalJudgments;
//        this.presentJudgments = presentJudgments;
//        return this;
//    }
//
//    boolean validTemporal(Sentence s) {
//        long e = s.getOccurrenceTime();
//
//        if (eternalJudgments && (e == Stamp.ETERNAL))
//            return true;
//
//        if (presentJudgments) {
//            long now = nar.time();
//            if (TemporalRules.concurrent(now, e, (int)(durationCycles * durationMultiplier)))
//               return true;
//        }
//
//        return false;
//    }
//
//    public Map<Sentence, PTerm> getBeliefs() {
//        return beliefs;
//    }
//
//    protected void beliefsChanged() {
//    }
//
//    protected boolean forget(Sentence belief) {
//        if (beliefs.remove(belief)!=null) {
//
//            beliefsChanged();
//
//            if (reportForgets) {
//                System.err.println("Prolog forget: " + belief);
//            }
//            return true;
//        }
//        return false;
//    }
//
//    protected void updateBeliefs() {
//        if (presentJudgments) {
//            long now = nar.time();
//            durationCycles = (nar.param).duration.get();
//            if (now - lastFlush > (long)(durationCycles/ forgetCyclePeriod) ) {
//
//                Set<Sentence> toRemove = new HashSet();
//                for (Sentence s : beliefs.keySet()) {
//                    if (!validTemporal(s)) {
//                        toRemove.add(s);
//                    }
//                }
//                for (Sentence s : toRemove) {
//                    forget(s);
//                }
//
//                lastFlush = now;
//            }
//        }
//    }
//
//    @Override
//    public void event(final Class channel, final Object... arg) {
//
//        if (channel == ConceptBeliefAdd.class) {
//            Concept c = (Concept)arg[0];
//            Task task = (Task)arg[1];
//            add(task.sentence, task);
//        }
//        else if (channel == ConceptBeliefRemove.class) {
//            Concept c = (Concept)arg[0];
//            remove(c, (Sentence)arg[1]);
//        }
//        else if (channel == Events.ConceptQuestionAdd.class) {
//            Concept c = (Concept)arg[0];
//            Task task = (Task)arg[1];
//            add(task.sentence, task);
//        }
//        else if ((channel == IN.class) || (channel == OUT.class)) {
//            Object o = arg[0];
//            if (o instanceof Task) {
//                Task task = (Task)o;
//                Sentence s = task.sentence;
//
//                add(s, task);
//            }
//        }
//    }
//
//    /** remove belief unless there are other similar beliefs remaining in 'c' */
//    private void remove(Concept c, Sentence forgotten) {
//        for (Task x : c.beliefs) {
//            if (x.equals(forgotten)) continue;
//            if (believable(x.getTruth()) && similarTruth(x.getTruth(), forgotten.truth)
//                    && similarTense(x.sentence, forgotten)) {
//                //there still remains evidence for this belief in the concept
//                return;
//            }
//        }
//
//        remove(forgotten, null);
//    }
//
//    protected void remove(Sentence s, Task task) {
//        //TODO
//    }
//
//    protected void add(Sentence s, Task task) {
//
//        variableContext = s.term.hashCode();
//
//        if (!(s.term instanceof Compound))
//            return;
//
//        if (!validTemporal(s))
//            return;
//
//        updateBeliefs();
//
//        //only interpret input judgments, or any kind of question
//        if (s.isJudgment()) {
//
//            processBelief(s, task, true);
//        }
//        else if (s.isQuestion()) {
//
//            //System.err.println("question: " + s);
//            onQuestion(s);
//
//            float priority = task.getPriority();
//            float solveTime = ((maxSolveTime - minSolveTime) * priority) + minSolveTime;
//
//            if (beliefs.containsKey(s)) {
//                //TODO search for opposite belief
//
//                //already determined it to be true
//                answer(task, s.term, null);
//                return;
//            }
//
//            try {
//                Struct qh = newQuestion(s);
//
//                if (qh!=null) {
//                    //System.out.println("Prolog question: " + s.toString() + " | " + qh.toString() + " ? (" + Texts.n2(priority) + ")");
//
//                    Theory t = getTheory(beliefs);
//                    t.append(axioms);
//
//                    prolog.setTheory(t);
//
//                    SolveInfo si = prolog.query(qh, solveTime);
//
//                    int answers = 0;
//
//                    PTerm lastSolution = null;
//
//                    do {
//                        if (si == null) break;
//
//                        PTerm solution = si.getSolution();
//                        if (solution == null)
//                            break;
//
//                        if (lastSolution!=null && solution.equals(lastSolution))
//                            continue;
//
//                        lastSolution = solution;
//
//                        try {
//                            Term n = nterm(solution);
//                            if (n!=null)
//                                answer(task, n, solution);
//                            else
//                                onUnanswerable(solution);
//                        }
//                        catch (Exception e) {
//                            //problem generating a result
//                            e.printStackTrace();
//                        }
//
//                        si = prolog.prolog.solveNext(solveTime);
//
//                        solveTime /= 2d;
//                    }
//                    while ((answers++) < maxAnswers);
//
//                    prolog.prolog.solveEnd();
//
//                }
//                else {
//                    onUnrecognizable(s);
//                }
//            } catch (NoSolutionException nse) {
//                //no solution, ok
//            } catch (InvalidTermException nse) {
//                nar.emit(NARPrologMirror.class, s + " : not supported yet");
//                nse.printStackTrace();
//            } catch (Exception ex) {
//                nar.emit(ERR.class, ex.toString());
//                ex.printStackTrace();
//            }
//
//        }
//
//    }
//
//    protected void onUnrecognizable(Sentence s) {
//        //System.err.println(this + " unable to express question in Prolog: " + s);
//    }
//
//    protected void onUnanswerable(PTerm solution) {
//        //System.err.println(this + " unable to answer solution: " + solution);
//
//    }
//
//    protected void processBelief(Sentence s, Task task, boolean addOrRemove) {
//
//        Truth tv = s.truth;
//        if (believable(tv)) {
//
//            boolean exists = beliefs.containsKey(s.term);
//            if ((addOrRemove) && (exists))
//                return;
//            else if ((!addOrRemove) && (!exists))
//                return;
//
//            try {
//                Struct th = newJudgmentTheory(s);
//                if (th!=null) {
//
//                    if (tv.getFrequency() < falseThreshold) {
//                        th = negation(th);
//                    }
//
//                    if (addOrRemove) {
//                        if (beliefs.putIfAbsent(s, th)==null) {
//
//                            beliefsChanged();
//
//                            if (reportAssumptions)
//                                System.err.println("Prolog assume: " + th + " | " + s);
//                        }
//                    }
//                    else {
//                        forget(s);
//                    }
//
//                }
//            } catch (Exception ex) {
//                nar.emit(ERR.class, ex.toString());
//            }
//        }
//
//
//    }
//
//    /** creates a theory from a judgment Statement */
//    public static Struct newJudgmentTheory(final Sentence judgment) throws InvalidTheoryException {
//
//        PTerm s;
//        /*if (judgment.truth!=null) {
//            s = pInfer(pterm(judgment.content), judgment.truth);
//        }
//        else {*/
//        try {
//            s = pterm(judgment.term);
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//        //}
//
//        return (Struct) s;
//    }
//
//    Struct newQuestion(final Sentence question) {
//        PTerm s = pterm(question.term);
//        return (Struct) s;
//    }
//
//    //NOT yet working
//    public Struct pInfer(PTerm t, Truth tv) {
//        double freq = tv.getFrequency();
//        double conf = tv.getConfidence();
//        Struct lt = new Struct(new PTerm[] { t,
//            new Struct( new PTerm[] {
//                new nars.tuprolog.Double(freq),
//                new nars.tuprolog.Double(conf)
//            })
//        });
//        return new Struct("infer", lt);
//    }
//
//    public static Struct negation(PTerm t) {
//        return new Struct("negation", t);
//    }
//
//    public static String pquote(final String x) {
//        return "'" + x + '\'';
//    }
//
//    public static String pescape(final String p) {
//        if (!Parser.isAtom(p)) {
//            return pquote(p);
//        }
//        if (Character.isDigit(p.charAt(0))) {
//            return pquote(p);
//        }
//        return p;
//    }
//    public static String unpescape(String p) {
//        return p.toString();
//    }
//
//    public boolean believable(Truth tv) {
//        return (tv.getConfidence() > confidenceThreshold) && ((tv.getFrequency() > trueThreshold) || (tv.getFrequency() < falseThreshold));
//    }
//
//    public boolean similarTense(Sentence a, Sentence b) {
//        boolean ae = a.isEternal();
//        boolean be = b.isEternal();
//        if (ae && be) return true;
//        else if (ae && !be) return false;
//        else if (!ae && be) return false;
//        else {
//            return (TemporalRules.concurrent(a.getOccurrenceTime(), b.getOccurrenceTime(), nar.memory.duration()));
//        }
//    }
//
//
//    public boolean similarTruth(Truth a, Truth b) {
//        float af = a.getFrequency();
//        float bf = b.getFrequency();
//        if ((af < falseThreshold) && (bf < falseThreshold))
//            return true;
//        if ((af > trueThreshold) && (bf > trueThreshold))
//            return true;
//        return false;
//    }
//
//    protected static String classPredicate(final Class c) {
//        String s = c.getSimpleName();
//        switch (s) {
//            case "SetInt1": s = "setint"; break;
//            case "SetExt1": s = "setext"; break;
//        }
//        return s.toLowerCase();
//    }
//
//
//    public Sentence getBeliefSentence(Sentence question, Term belief, Task parentTask) {
//        float freq = 1.0f;
//        float conf = Global.DEFAULT_JUDGMENT_CONFIDENCE;
//        float priority = Global.DEFAULT_JUDGMENT_PRIORITY;
//        float durability = Global.DEFAULT_JUDGMENT_DURABILITY;
//        Tense tense = question.isEternal() ? Tense.Eternal : Tense.Present;
//
//        //TODO use derivation of prolog result to create a correct stamp
//
//        return new Sentence(belief, '.', new Truth(freq, conf),
//                new Stamp(nar.memory, tense));
//    }
//
//    /** reflect a result to NARS, and remember it so that it doesn't get reprocessed here later */
//    public Term answer(Task question, Term t, PTerm pt) {
//        if (reportAnswers)
//            System.err.println("Prolog answer: " + t);
//
//        Sentence a = getBeliefSentence(question.sentence, t, question);
//
//        input(a, inputMode, question);
//
//        if (pt!=null) {
//            beliefs.put(a, pt);
//            beliefsChanged();
//        }
//
//        return t;
//    }
//
//    /*
//    public static class NARStruct extends Struct {
//
//        Sentence sentence = null;
//
//        public NARStruct(Sentence sentence, String predicate, nars.prolog.Term[] args) {
//            super(predicate, args);
//
//            this.sentence = sentence;
//        }
//
//        public NARStruct(String predicate, nars.prolog.Term... args) {
//            this(null, predicate, args);
//        }
//
//        public Sentence getSentence() {
//            return sentence;
//        }
//
//        public void setSentence(Sentence sentence) {
//            this.sentence = sentence;
//        }
//
//
//    }
//    */
//
//
//
//
//
//    private String getAxiomString() {
//        return
//                    "inheritance(A, C) :- inheritance(A,B),inheritance(B,C). " + '\n' +
//                    "similarity(A, B) :- inheritance(A,B),inheritance(B,A). " + '\n' +
//
//                    "implication(A, C) :- implication(A,B),implication(B,C). " + '\n' +
//
//                    "similarity(A, B) :- similarity(B,A). " + '\n' +
//                    "not(similar(A, B)) :- not(inheritance(A,B)),inheritance(B,A). " + '\n' +
//
//                    "equivalence(A, B) :- equivalence(B,A). " + '\n' +
//                    "similarity(A, B) :- equivalence(A,B). " + '\n' +
//
//                    "not(equivalence(A, B)) :- not(similar(A,B)). " + '\n' +
//
//
//                    "A :- not(not(A))." + '\n';
//    }
//
//    public static Theory getTheory(Map<Sentence, PTerm> beliefMap) throws InvalidTheoryException  {
//        return new Theory(new Struct(beliefMap.values().toArray(new Struct[beliefMap.size()])));
//    }
//
//    public Theory getBeliefsTheory() throws InvalidTheoryException {
//        return getTheory(beliefs);
//    }
//
//    protected void onQuestion(Sentence s) {
//    }
//
//    public static PTerm[] pterms(Term[] term) {
//        PTerm[] tt = new PTerm[term.length];
//        int i = 0;
//        for (Term x : term) {
//            if ((tt[i++] = pterm(x)) == null) return null;
//        }
//        return tt;
//    }
//
//    public static Term[] nterm(final PTerm[] term) {
//        Term[] tt = new Term[term.length];
//        int i = 0;
//        for (PTerm x : term) {
//            if ((tt[i++] = nterm(x)) == null) return null;
//        }
//        return tt;
//    }
//


}
