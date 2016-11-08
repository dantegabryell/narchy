package nars.nal.rule;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import nars.$;
import nars.Op;
import nars.index.term.PatternTermIndex;
import nars.index.term.TermIndex;
import nars.nal.meta.*;
import nars.nal.meta.constraint.*;
import nars.nal.meta.match.Ellipsis;
import nars.nal.meta.match.EllipsisOneOrMore;
import nars.nal.meta.match.EllipsisTransform;
import nars.nal.meta.match.EllipsisZeroOrMore;
import nars.nal.meta.op.*;
import nars.nal.meta.op.AbstractPatternOp.PatternOpNot;
import nars.nal.meta.op.AbstractPatternOp.PatternOpNotContained;
import nars.term.Compound;
import nars.term.Term;
import nars.term.atom.Atomic;
import nars.term.compound.GenericCompound;
import nars.term.container.TermContainer;
import nars.term.container.TermVector;
import nars.term.transform.CompoundTransform;
import nars.term.transform.VariableNormalization;
import nars.term.var.AbstractVariable;
import nars.term.var.Variable;
import nars.time.TimeFunctions;
import nars.truth.func.BeliefFunction;
import nars.truth.func.GoalFunction;
import nars.truth.func.TruthOperator;
import nars.util.data.list.FasterList;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

import static java.util.Collections.addAll;
import static nars.$.*;
import static nars.Op.VAR_PATTERN;
import static nars.nal.meta.TaskBeliefSubterms.withinNonCommutive;
import static nars.term.Terms.*;

/**
 * A rule which matches a Premise and produces a Task
 * contains: preconditions, predicates, postconditions, post-evaluations and metainfo
 */
public class PremiseRule extends GenericCompound {

    public boolean allowBackward;
    public boolean allowForward = false;

//    @NotNull
//    @Override
//    public String toString() {
//        return "PremiseRule{" +
//                "\t prePreconditions=" + Arrays.toString(precon) +
//                "\t match=" + match +
//                "\t postconditions=" + Arrays.toString(postconditions) +
//                "\t temporalize=" + timeFunction +
//                "\t eternalize=" + eternalize +
//                "\t anticipate=" + anticipate +
//                "\t minNAL=" + minNAL +
//                "\t source='" + source + '\'' +
//                '}';
//    }

    //    /**
//     * blank marker trie node indicating the derivation and terminating the branch
//     */
//    public static final BooleanCondition END = new AtomicBooleanCondition<PremiseEval>() {
//
//        @Override
//        public boolean booleanValueOf(PremiseEval versioneds) {
//            return true;
//        }
//
//        @Override
//        public String toString() {
//            return "End";
//        }
//    };

    public boolean eternalize;

    public boolean anticipate;


    /**
     * conditions which can be tested before term matching
     */
    public BoolCondition[] pre;

    public PostCondition[] postconditions;

//    public PatternCompound pattern;

    //it has certain pre-conditions, all given as predicates after the two input premises


    /**
     * maximum of the minimum NAL levels involved in the postconditions of this rule
     */
    public int minNAL;

    public String source;

    public
    @Nullable
    MatchTaskBelief match;

    private @Nullable TimeFunctions timeFunction = TimeFunctions.Auto;


    @Nullable
    private static final CompoundTransform<Compound, Term> truthSwap = new PremiseTruthTransform(true, true) {
        @Override
        public Term apply(@NotNull Term func) {
            return $.the(func.toString() + 'X');
        }
    };
//    @Nullable
//    private static final CompoundTransform<Compound, Term> truthNegate = new PremiseTruthTransform(true, true) {
//        @Override
//        public Term apply(@NotNull Term func) {
//            return $.the(func.toString() + 'N');
//        }
//    };

    @NotNull
    public final Compound getPremise() {
        return (Compound) term(0);
    }

    @NotNull
    public final Compound getConclusion() {
        return (Compound) term(1);
    }

    PremiseRule(@NotNull Compound premisesResultProduct) {
        this((Compound) premisesResultProduct.term(0), (Compound) premisesResultProduct.term(1));
    }

    public PremiseRule(@NotNull Compound premises, @NotNull Compound result) {
        super(Op.PROD, TermVector.the(premises, result));
    }


//    public final boolean validTaskPunctuation(final char p) {
//        if ((p == Symbols.QUESTION) && !allowQuestionTask)
//            return false;
//        return true;
//    }

    protected final void ensureValid() {

//        if (getConclusionTermPattern().containsTemporal()) {
//            if ((!getTaskTermPattern().containsTemporal())
//                    &&
//                    (!getBeliefTermPattern().containsTemporal())) {
//                //if conclusion is temporal term but the premise has none:
//
//                String s = toString();
//                if ((!s.contains("after")) && (!s.contains("concurrent") && (!s.contains("measure")))) {
//                    //System.err.println
//                  throw new RuntimeException
//                            ("Possibly invalid temporal rule from atemporal premise: " + this);
//
//                }
//            }
//        }
//

        if (postconditions.length == 0)
            throw new RuntimeException(this + " has no postconditions");
//        if (!getTask().hasVarPattern())
//            throw new RuntimeException("rule's task term pattern has no pattern variable");
//        if (!getBelief().hasVarPattern())
//            throw new RuntimeException("rule's task belief pattern has no pattern variable");
//        if (!getConclusionTermPattern().hasVarPattern())
//            throw new RuntimeException("rule's conclusion belief pattern has no pattern variable");
    }


    /**
     * compiles the conditions which are necessary to activate this rule
     */
    @NotNull
    public List<Term> conditions(@NotNull PostCondition post) {

        Set<Term> s = newHashSet(2); //for ensuring uniqueness / no duplicates
        Solve truth = solve(post, this, anticipate, eternalize, timeFunction);

        //PREFIX
        {
            addAll(s, pre);

            s.add(truth);

            addAll(s, match.pre);
        }

        List<Term> l = sort(new FasterList(s));

        //SUFFIX (order already determined for matching)
        {

            addAll(l, match.procedure);

            l.add(truth.conclude); //will be linked to and invoked by match callbacks

        }

        return l;
    }

    //    public static void eachOperator(NAR nar, BiConsumer<Class, TermTransform> eachTransform) {
//        for (Class<? extends TermTransform> c : PremiseRule.Operators) {
//
//            Constructor<?>[] ccc = c.getConstructors();
//            try {
//                int n = 0;
//                TermTransform o = null;
//                do {
//                    Constructor cc = ccc[n++];
//
//                    if (Modifier.isPublic(cc.getModifiers())) {
//                        int params = cc.getParameterCount();
//                        if (params == 0) {
//                            //default empty constructor
//                            o = (c.newInstance());
//                        } else if (params == 1) {
//                            //HACK support 'NAR' only parameter constructor
//                            o = ((TermTransform) cc.newInstance(nar));
//                        }
//                    }
//                } while (o == null && n < ccc.length);
//
//                eachTransform.accept(c, o);
//
//            } catch (Exception e) {
//                throw new RuntimeException("Invalid ImmediateTermTransform: " + c);
//            }
//
//
//        }
//    }


    /**
     * higher is earlier
     */
    static final HashMap<Object, Integer> preconditionScore = new HashMap() {{

        put("PatternOp1", 25);

        put(TaskPunctuation.class, 22);
        put(TaskPositive.class, 21); //includes either positive or negative

        put(events.class, 20);

        put("PatternOp0", 20);

        put(SubTermsStructure.class, 18);
        put(PatternOpNot.class, 16);

        put(SubTermStructure.class, 12);

        put(TermNotEquals.class, 11);

        put(Solve.class, 10);


//        put(BeliefNegative.class, 7);
//        put(BeliefPositive.class, 7);


//        put(SubTermOp.class, 10);
//        put(TaskPunctuation.class, 9);
//        put(TaskNegative.class, 8);
//        put(SubTermStructure.class, 7);
//        put(Solve.class, 1);
    }};

    private static Object classify(Term b) {
        if (b instanceof AbstractPatternOp.PatternOp)
            return "PatternOp" + (((AbstractPatternOp.PatternOp) b).subterm == 0 ? "0" : "1"); //split


        if ((b == TaskPositive.the) || (b == TaskNegative.the)) return TaskPositive.class;

        if (b == TaskPunctuation.Goal) return TaskPunctuation.class;
        if (b == TaskPunctuation.Belief) return TaskPunctuation.class;
        if (b == TaskPunctuation.NotQuestion) return TaskPunctuation.class;
        if (b == TaskPunctuation.QuestionOrQuest) return TaskPunctuation.class;
        if (b == TaskPunctuation.Question) return TaskPunctuation.class;
        if (b == TaskPunctuation.Quest) return TaskPunctuation.class;

        if (b instanceof TermNotEquals) return TermNotEquals.class;

        if (b instanceof PatternOpNot) return PatternOpNot.class;
        if (b instanceof PatternOpNotContained) return PatternOpNot.class;

        //if (b == TaskPunctuation.NotGoal) return TaskPunctuation.class;
        //if (b == TaskPunctuation.NotBelief) return TaskPunctuation.class;

        if (b == events.after) return events.class;
        if (b == events.afterOrEternal) return events.class;
        if (b == IfTermLinkBefore.ifTermLinkBefore) return events.class;
        if (b == IfTermLinkBefore.ifBeliefBefore) return events.class;

        if (b instanceof Solve) return Solve.class;

        return b.getClass();
    }

    /**
     * apply deterministic and uniform sort to the current preconditions.
     * the goal of this is to maximally fold subexpressions while also
     * pulling the cheapest and most discriminating tests to the beginning.
     */
    @NotNull
    private static List<Term> sort(@NotNull List<Term> l) {
        HashMap<Object, Integer> ps = PremiseRule.preconditionScore;

        Collections.sort(l, (a, b) -> {

            int ascore = 0, bscore = 0;

            Object ac = classify(a);

            if (!ps.containsKey(ac)) {
                //System.err.println("preconditionRank missing " + a + " classified as: " + ac);
                ascore = -1;
            } else {
                ascore = ps.get(ac);
            }

            Object bc = classify(b);
            if (!ps.containsKey(bc)) {
                //System.err.println("preconditionRank missing " + b + " classified as: " + bc);
                bscore = -1;
            } else {
                bscore = ps.get(bc);
            }

            if (ascore != bscore) {
                return Integer.compare(bscore, ascore);
            }

            return b.compareTo(a);
        });
        return l;
    }


    @NotNull
    public static Solve solve(@NotNull PostCondition p, @NotNull PremiseRule rule, boolean anticipate, boolean eternalize,
                              @NotNull TimeFunctions temporalizer) {


        char puncOverride = p.puncOverride;

        TruthOperator belief = BeliefFunction.get(p.beliefTruth);
        if ((p.beliefTruth != null) && !p.beliefTruth.equals(TruthOperator.NONE) && (belief == null)) {
            throw new RuntimeException("unknown BeliefFunction: " + p.beliefTruth);
        }
        TruthOperator desire = GoalFunction.get(p.goalTruth);
        if ((p.goalTruth != null) && !p.goalTruth.equals(TruthOperator.NONE) && (desire == null)) {
            throw new RuntimeException("unknown DesireFunction: " + p.goalTruth);
        }

        Conclude der = new Conclude(rule, p.pattern,
                /*anticipate,*/
                eternalize, temporalizer);

        String beliefLabel = belief != null ? belief.toString() : "_";
        String desireLabel = desire != null ? desire.toString() : "_";

        String sn = "Truth(";
        String i =
                sn + beliefLabel + ',' + desireLabel + ",punc:\"" +
                        (puncOverride == 0 ? '_' : puncOverride) + '\"';
        i += ')';

        return puncOverride == 0 ?
                new SolvePuncFromTask(i, der, belief, desire) :
                new SolvePuncOverride(i, der, puncOverride, belief, desire);


    }


    public void setSource(String source) {
        this.source = source;
    }


    /**
     * source string that generated this rule (for debugging)
     */
    public String getSource() {
        return source;
    }

    /**
     * the task-term pattern
     */
    @NotNull
    public final Term getTask() {
        return getPremise().term(0);
    }

    /**
     * the belief-term pattern
     */
    @NotNull
    public final Term getBelief() {
        return getPremise().term(1);
    }

    @NotNull
    protected final Term getConclusionTermPattern() {
        return getConclusion().term(0);
    }


//    @Override
//    public final String toString(boolean pretty) {
//        return str;
//    }

//    @Nullable
//    public final Term task() {
//        return pattern.term(0);
//    }
//
//    @Nullable
//    public final Term belief() {
//        return pattern.term(1);
//    }

    /**
     * deduplicate and generate match-optimized compounds for rules
     */
    public void compile(@NotNull TermIndex index) {
        Term[] premisePattern = ((Compound) term(0)).terms();
        premisePattern[0] = index.get(premisePattern[0], true).term(); //task pattern
        premisePattern[1] = index.get(premisePattern[1], true).term(); //belief pattern
    }

//    @NotNull
//    public Term reified() {
//
//        //TODO include representation of precondition and postconditions
//        return $.impl(
//                p(getTask(), getBelief()),
//                getConclusion()
//        );
//    }

    static final class UppercaseAtomsToPatternVariables implements CompoundTransform<Compound, Term> {


        @Override
        public boolean test(Term term) {
            if (term instanceof Atomic) {
                String name = term.toString();
                return (Character.isUpperCase(name.charAt(0)));
            }
            return false;
        }

        @NotNull
        @Override
        public Term apply(@NotNull Compound containingCompound, @NotNull Term v) {

            //do not alter postconditions
            if ((containingCompound.op() == Op.INH)
                    && PostCondition.reservedMetaInfoCategories.contains(
                    containingCompound.term(1)))
                return v;

            return v(Op.VAR_PATTERN, v.toString());
        }
    }

    static final UppercaseAtomsToPatternVariables UppercaseAtomsToPatternVariables = new UppercaseAtomsToPatternVariables();


    @NotNull
    public final PremiseRule normalizeRule(@NotNull PatternTermIndex index) {
        //try {

        //HACK
        Compound ss = (Compound) index.transform(this, UppercaseAtomsToPatternVariables);
        if (ss == null)
            throw new RuntimeException("unnormalizable: " + this);

        Term tt = index.transform(ss, new PremiseRuleVariableNormalization());

        if (tt == null)
            throw new RuntimeException("unnormalizable: " + this);

        Compound premiseComponents = (Compound) index.get(tt, true);


        return new PremiseRule(premiseComponents);

//        } catch (Exception e) {
//            e.printStackTrace();
//            logger.error("normalizeRule untransformed: {} {}", this, e.getCause());
//            throw e;
//        }


    }


    @NotNull
    public final PremiseRule setup(@NotNull PatternTermIndex index) /* throws PremiseRuleException */ {

        compile(index);

        //1. construct precondition term array
        //Term[] terms = terms();

        Term[] precon = ((Compound) term(0)).terms();
        Term[] postcons = ((Compound) term(1)).terms();


        Set<BoolCondition> pres =
                //Global.newArrayList(precon.length);
                new TreeSet(); //for consistent ordering to maximize folding



        Term taskTermPattern = getTask();
        Term beliefTermPattern = getBelief();

        if (beliefTermPattern.op() == Op.ATOM) {
            throw new RuntimeException("belief term must contain no atoms: " + beliefTermPattern);
        }

        //if it contains an atom term, this means it is a modifier,
        //and not a belief term pattern
        //(which will not reference any particular atoms)

        //pattern = PatternCompound.make(p(taskTermPattern, beliefTermPattern));


        ListMultimap<Term, MatchConstraint> constraints =
                MultimapBuilder.treeKeys().arrayListValues().build();

        char taskPunc = 0;

        //additional modifiers: either preConditionsList or beforeConcs, classify them here
        for (int i = 2; i < precon.length; i++) {

            Compound predicate = (Compound) precon[i];
            Term predicate_name = predicate.term(1);

            String predicateNameStr = predicate_name.toString();

            Term[] args;
            Term X, Y;

            //if (predicate.getSubject() instanceof SetExt) {
            //decode precondition predicate arguments
            args = ((Compound) (predicate.term(0))).terms();
            X = (args.length > 0) ? args[0] : null;
            Y = (args.length > 1) ? args[1] : null;
            /*} else {
                throw new RuntimeException("invalid arguments");*/
                /*args = null;
                arg1 = arg2 = null;*/
            //}

            switch (predicateNameStr) {


                case "neq":

                    neqPrefilter(pres, taskTermPattern, beliefTermPattern, X, Y);

                    neq(constraints, X, Y); //should the constraints be ommited in this case?

                    break;


                case "neqAndCom":
                    neqPrefilter(pres, taskTermPattern, beliefTermPattern, X, Y);
                    constraints.put(Y, new CommonSubtermConstraint(X));
                    constraints.put(X, new CommonSubtermConstraint(Y));
                    break;

                case "neqCom":
                    neqPrefilter(pres, taskTermPattern, beliefTermPattern, X, Y);
                    constraints.put(Y, new NoCommonSubtermConstraint(X, false));
                    constraints.put(X, new NoCommonSubtermConstraint(Y, false));
                    break;
                case "neqRCom":
                    neqPrefilter(pres, taskTermPattern, beliefTermPattern, X, Y);
                    constraints.put(Y, new NoCommonSubtermConstraint(X, true));
                    constraints.put(X, new NoCommonSubtermConstraint(Y, true));
                    break;


                case "neqRec":
                    neqPrefilter(pres, taskTermPattern, beliefTermPattern, X, Y);

                    constraints.put(X, new NoCommonRecursiveSubtermConstraint(Y));
                    constraints.put(Y, new NoCommonRecursiveSubtermConstraint(X));
                    //neqPrefilter(pres, taskTermPattern, beliefTermPattern, X, Y);
                    break;

                case "notSet":
                    opNot(taskTermPattern, beliefTermPattern, pres, constraints, X, Op.SetsBits);
                    break;

                case "hasNoDepVar":
                    opNot(taskTermPattern, beliefTermPattern, pres, constraints, X, Op.VAR_DEP.bit);
                    break;

                case "setext":
                    //assumes arity=2 but arity=1 support can be written
                    constraints.put(X, new OpConstraint(Op.SETe));
                    constraints.put(Y, new OpConstraint(Op.SETe));
                    pres.add(new SubTermsStructure(Op.SETe.bit));
                    ////additionally prohibits the two terms being equal
                    neq(constraints, X, Y);
                    break;

                case "setint":
                    //assumes arity=2 but arity=1 support can be written
                    constraints.put(X, new OpConstraint(Op.SETi));
                    constraints.put(Y, new OpConstraint(Op.SETi));
                    pres.add(new SubTermsStructure(Op.SETi.bit));
                    //additionally prohibits the two terms being equal
                    neq(constraints, X, Y);
                    break;


                case "notEqui":
                    opNotContained(taskTermPattern, beliefTermPattern, pres, constraints, X, Op.EQUI.bit);
                    break;

                case "notImplEqui":
                    opNotContained(taskTermPattern, beliefTermPattern, pres, constraints, X, Op.ImplicationOrEquivalenceBits);
                    break;

                case "events":
                    throw new RuntimeException("depr");

//                case "contains":
//                    pres.add(new ContainedBy(withinNonCommutive(taskTermPattern, beliefTermPattern, X, Y)));
//                    break;

                case "component":
                    if (X.toString().equals("task") && Y.toString().equals("belief")) {
                        pres.add(new ComposedBy(0,1));
                    } else {
                        throw new UnsupportedOperationException();
                    }
                    break;

                case "time":
                    switch (X.toString()) {
                        case "after":
                            pres.add(events.after);
                            break;

                        case "eternal":
                            pres.add(events.eternal);
                            timeFunction = TimeFunctions.dternal;
                            break;

                        case "afterOrEternal":
                            pres.add(events.afterOrEternal);
                            break;
                        /*case "taskPredicate":
                            pres.add( events.taskPredicate;
                            break;*/
                        case "dt":
                            timeFunction = TimeFunctions.occForward;
                            break;


                        case "dtBelief":
                            timeFunction = TimeFunctions.dtBelief;
                            break;
                        case "dtBeliefEnd":
                            timeFunction = TimeFunctions.dtBeliefEnd;
                            break;
                        case "dtBeliefExact":
                            timeFunction = TimeFunctions.dtBeliefExact;
                            break;

                        case "dtTask":
                            timeFunction = TimeFunctions.dtTask;
                            break;
                        case "dtTaskEnd":
                            timeFunction = TimeFunctions.dtTaskEnd;
                            break;
                        case "dtTaskExact":
                            timeFunction = TimeFunctions.dtTaskExact;
                            break;

                        case "decomposeTask":
                            timeFunction = TimeFunctions.decomposeTask;
                            break;
                        case "decomposeTaskIfTemporal":
                            pres.add(events.taskNotDTernal);
                            timeFunction = TimeFunctions.decomposeTask;
                            break;

                        case "decomposeTaskSubset":
                            timeFunction = TimeFunctions.decomposeTaskSubset;
                            break;

                        case "decomposeTaskIfTermLinkBefore":
                            timeFunction = TimeFunctions.decomposeTask;
                            pres.add(IfTermLinkBefore.ifTermLinkBefore);
                            break;

                        case "decomposeTaskIfBeliefBefore":
                            timeFunction = TimeFunctions.decomposeTask;
                            pres.add(IfTermLinkBefore.ifBeliefBefore);
                            break;

                        case "decomposeBelief": timeFunction = TimeFunctions.decomposeBelief; break;

                        case "dtCombine":     timeFunction = TimeFunctions.dtCombine;  break;
                        case "dtCombinePre":  timeFunction = TimeFunctions.dtCombinePre; break;
                        case "dtCombinePost": timeFunction = TimeFunctions.dtCombinePost; break;

//                        case "dtForward":
//                            timeFunction = TimeFunctions.occForward;
//                            pres.add(events.bothTemporal);
//                            break;

                        case "dtAfter":
                            timeFunction = TimeFunctions.occForward;
                            pres.add(events.after);
                            break;
                        case "dtAfterReverse":
                            timeFunction = TimeFunctions.occReverse;
                            pres.add(events.after);
                            break;

                        case "dtBefore":
                            timeFunction = TimeFunctions.occReverse;
                            pres.add(events.after);
                            break;

//                        case "dtIfEvent":
//                            temporalize = Temporalize.dtIfEvent;
//                            break;




                        case "dtAfterOrEternal":
                            timeFunction = TimeFunctions.occForward;
                            pres.add(events.afterOrEternal);
                            break;
                        case "dtAfterOrEternalReverse":
                            timeFunction = TimeFunctions.occReverse;
                            pres.add(events.afterOrEternal);
                            break;

                        case "dtTminB":
                            timeFunction = TimeFunctions.dtTminB;
                            break;
                        case "dtBminT":
                            timeFunction = TimeFunctions.dtBminT;
                            break;
                        case "dtIntersect":
                            timeFunction = TimeFunctions.dtIntersect;
                            break;
                        case "dtUnion":
                            timeFunction = TimeFunctions.dtUnion;
                            break;
                        case "dtUnionReverse":
                            timeFunction = TimeFunctions.dtUnionReverse;
                            break;
                        case "occMerge":
                            timeFunction = TimeFunctions.occMerge;
                            break;

                        default:
                            throw new RuntimeException("invalid events parameters");
                    }
                    break;

//                case "temporal":
//                    pres.add( Temporality.either;
//                    break;

//                case "occurr":
////                    pres.add( new occurr(arg1,arg2);
//                    break;

//                case "after":
//                    switch (arg1.toString()) {
//                        case "forward":
//                            pres.add( Event.After.forward;
//                            break;
//                        case "reverseStart":
//                            pres.add( Event.After.reverseStart;
//                            break;
//                        case "reverseEnd":
//                            pres.add( Event.After.reverseEnd;
//                            break;
//                        default:
//                            throw new RuntimeException("invalid after() argument: " + arg1);
//                    }
//                    break;

//                case "dt":
////                    switch (arg1.toString()) {
////                        case "avg":
////                            pres.add( dt.avg; break;
////                        case "task":
////                            pres.add( dt.task; break;
////                        case "belief":
////                            pres.add( dt.belief; break;
////                        case "exact":
////                            pres.add( dt.exact; break;
////                        case "sum":
////                            pres.add( dt.sum; break;
////                        case "sumNeg":
////                            pres.add( dt.sumNeg; break;
////                        case "bmint":
////                            pres.add( dt.bmint; break;
////                        case "tminb":
////                            pres.add( dt.tminb; break;
////
////                        case "occ":
////                            pres.add( dt.occ; break;
////
////                        default:
////                            throw new RuntimeException("invalid dt() argument: " + arg1);
////                    }
//                    break;

//                case "belief":
//                    switch (arg1.toString()) {
//                        case "negative":
//                            pres.add( BeliefNegative.the;
//                            break;
//                        case "positive":
//                            pres.add( BeliefPositive.the;
//                            break;
//                    }
//                    break;

                case "task":
                    switch (X.toString()) {
                        case "negative":
                            pres.add(TaskNegative.the);
                            break;
                        case "positive":
                            pres.add(TaskPositive.the);
                            break;
                        case "\"?\"":
                            pres.add(TaskPunctuation.Question);
                            taskPunc = '?';
                            break;
                        case "\"?@\"":
                            pres.add(TaskPunctuation.QuestionOrQuest);
                            taskPunc = '?'; //this will choose quest as punctuation type when necessary, according to the task
                            break;
                        case "\"@\"":
                            pres.add(TaskPunctuation.Quest);
                            taskPunc = '@';
                            break;
                        case "\".\"":
                            pres.add(TaskPunctuation.Belief);
                            taskPunc = '.';
                            break;
                        case "\"!\"":
                            pres.add(TaskPunctuation.Goal);
                            taskPunc = '!';
                            break;
                        case "any":
                            taskPunc = ' ';
                            break;
                        default:
                            throw new RuntimeException("Unknown task punctuation type: " + X.toString());
                    }
                    break;


                default:
                    throw new RuntimeException("unhandled postcondition: " + predicateNameStr + " in " + this);

            }


        }


        this.match = new MatchTaskBelief(
                new TaskBeliefPair(getTask(), getBelief()), //HACK
                index,
                constraints);


        if (taskPunc == 0) {
            //default: add explicit no-questions rule
            pres.add(TaskPunctuation.NotQuestion);
        } else if (taskPunc == ' ') {
            //any task type
            taskPunc = 0;
        }

        //store to arrays
        this.pre = pres.toArray(new BoolCondition[pres.size()]);

        List<PostCondition> postConditions = newArrayList();

        for (int i = 0; i < postcons.length; ) {
            Term t = postcons[i++];
            if (i >= postcons.length)
                throw new RuntimeException("invalid rule: missing meta term for postcondition involving " + t);


            Term[] modifiers = ((Compound) postcons[i++]).terms();

            postConditions.add(PostCondition.make(this, t, toSortedSetArray(modifiers)));
        }

        if (Sets.newHashSet(postConditions).size() != postConditions.size())
            throw new RuntimeException("postcondition duplicates:\n\t" + postConditions);

        postconditions = postConditions.toArray(new PostCondition[postConditions.size()]);
        if (postconditions.length == 0) {
            System.out.println(Arrays.toString(postcons));
            //throw new RuntimeException("no postconditions");
        }

        //TODO add modifiers to affect minNAL (ex: anything temporal set to 7)
        //this will be raised by conclusion postconditions of higher NAL level
        minNAL = Math.max(minNAL,
                Math.max(
                        maxLevel(getTask()),
                        maxLevel(getBelief())
                ));


        ensureValid();

        return this;
    }

    public static void opNot(Term task, Term belief, @NotNull Set<BoolCondition> pres, @NotNull ListMultimap<Term, MatchConstraint> constraints, @NotNull Term t, int structure) {

        boolean prefiltered = false;
        if (t.equals(task)) {
            pres.add(new PatternOpNot(0, structure));
            prefiltered = true;
        } else if (t.equals(belief)) {
            pres.add(new PatternOpNot(1, structure));
            prefiltered = true;
        }

        if (!prefiltered)
            constraints.put(t, new OpNotConstraint(structure));
    }


    public static void opNotContained(Term task, Term belief, @NotNull Set<BoolCondition> pres, @NotNull ListMultimap<Term, MatchConstraint> constraints, @NotNull Term t, int structure) {


        boolean prefiltered = true;

        if (t.equals(task)) {
            pres.add(new PatternOpNotContained(0, structure));
            prefiltered = true;
        } else if (t.equals(belief)) {
            pres.add(new PatternOpNotContained(1, structure));
            prefiltered = true;
        }

        if (!prefiltered)
            constraints.put(t, new OpNotContainedConstraint(structure));

    }

    public void neq(@NotNull ListMultimap<Term, MatchConstraint> constraints, @NotNull Term x, @NotNull Term y) {
        //find if the two compared terms are recursively contained as subterms of either the task or belief
        //and if so, create a precondition constraint rather than a matcher constraint


        constraints.put(x, new NotEqualConstraint(y));
        constraints.put(y, new NotEqualConstraint(x));

    }

    public boolean neqPrefilter(@NotNull Collection<BoolCondition> pres, @NotNull Term task, @NotNull Term belief, @NotNull Term arg1, @NotNull Term arg2) {
        TaskBeliefSubterms tb = withinNonCommutive(task, belief, arg1, arg2);
        if (tb != null) {
            //cheaper to compute this in precondition
            pres.add(TermNotEquals.the(tb));
            return true;
        }

        return false;
    }


    /**
     * for each calculable "question reverse" rule,
     * supply to the consumer
     * <p>
     * ex:
     * (A --> B), (B --> C), not_equal(A,C) |- (A --> C), (Truth:Deduction, Desire:Strong, Derive:AllowBackward)
     * 1. Deriving of backward inference rules, since Derive:AllowBackward it allows deriving:
     * (A --> B), (A --> C), not_equal(A,C), task("?") |- (B --> C), (Truth:Deduction, Desire:Strong, Derive:AllowBackward)
     * (A --> C), (B --> C), not_equal(A,C), task("?") |- (A --> B), (Truth:Deduction, Desire:Strong, Derive:AllowBackward)
     * so each premise gets exchanged with the conclusion in order to form a own rule,
     * additionally task("?") is added to ensure that the derived rule is only used in backward inference.
     */
    public final void backwardPermutation(@NotNull PatternTermIndex index, @NotNull BiConsumer<PremiseRule, String> w) {

        Term T = getTask(); //Task
        Term B = getBelief(); //Belief
        Term C = getConclusionTermPattern(); //Conclusion

        // C, B, [pre], task_is_question() |- T, [post]
        PremiseRule clone1 = clonePermutation(C, B, T, true, index);
        if (clone1 != null)
            w.accept(clone1, "C,B,question |- B");

        // T, C, [pre], task_is_question() |- B, [post]
        PremiseRule clone2 = clonePermutation(T, C, B, true, index);
        if (clone2 != null)
            w.accept(clone2, "T,C,question |- B");


    }


//    @Override
//    public Term clone(TermContainer subs) {
//        return null;
//    }

    //    @Override
//    public Term clone(Term[] x) {
//        return new TaskRule((Compound)x[0], (Compound)x[1]);
//    }


    /**
     * for each calculable "question reverse" rule,
     * supply to the consumer
     * <p>
     * 2. Deriving of forward inference rule by swapping the premises since !s.contains("task(") && !s.contains("after(") && !s.contains("measure_time(") && !s.contains("Structural") && !s.contains("Identity") && !s.contains("Negation"):
     * (B --> C), (A --> B), not_equal(A,C) |- (A --> C), (Truth:Deduction, Desire:Strong, Derive:AllowBackward)
     * <p>
     * after generating, these are then backward permuted
     */
    @Nullable
    public final PremiseRule swapPermutation(@NotNull PatternTermIndex index) {

        // T, B, [pre] |- C, [post] ||--
        Term T = getTask();
        Term B = getBelief();

        if (T.equals(B)) {
            //no change, ignore the permutation
            return null;
        } else {
            Term C = getConclusionTermPattern();
            return clonePermutation(B, T, C, false, index);
        }
    }

    static final Term TaskAny = $.$("task(any)");

//    static final Term BELIEF = $.the("Belief");
//    static final Term DESIRE = $.the("Desire");

    @NotNull
    public PremiseRule positive(PatternTermIndex index) {

//        Term[] pp = getPremise().terms().clone();
//        pp = ArrayUtils.add(pp, TaskPositive.proto);
//        Compound newPremise = (Compound) $.the(getPremise().op(), pp);
//
//        PremiseRule r = new PremiseRule(newPremise, getConclusion());
//        @NotNull PremiseRule pos = normalize(r, index);
//
//        //System.err.println(term(0) + " |- " + term(1) + "  " + "\t\t" + remapped);

        return this;
    }

//    public PremiseRule negative(PatternIndex index) {
//
//        @NotNull Term tt = getTask();
////        if (tt.op() == Op.ATOM) {
////            //raw pattern var, no need to invert the rule
////            //System.err.println("--- " + tt);
////            //return null;
////        } else {
////            //System.err.println("NEG " + neg(tt));
////        }
//
//        Compound newTask = (Compound) neg(tt);
//        Term[] pp = getPremise().terms().clone();
//        pp[0] = newTask;
//
//
//        Map<Term,Term> negMap = Maps.mutable.of(tt, newTask);
//        Term prevTask = pp[1];
//        pp[1] = $.terms.remap(prevTask, negMap);
//
//
//        Compound newPremise = (Compound) $.compound(getPremise().op(), pp);
//
//        @NotNull Compound prevConclusion = getConclusion();
//        Compound newConclusion = (Compound) $.terms.remap(prevConclusion, negMap);
//
//        //only the task was affected
//        if (newConclusion.equals(prevConclusion) && newTask.equals(prevTask))
//            return null;
//
//        //Compound newConclusion = (Compound) terms.transform(getConclusion(), truthNegate);
//
//        @NotNull PremiseRule neg = PremiseRuleSet.normalize(new PremiseRule(newPremise, newConclusion), index);
//
//        //System.err.println(term(0) + " |- " + term(1) + "  " + "\t\t" + remapped);
//
//        return neg;
//    }

    /**
     * safe negation
     */
    @NotNull
    private static Term neg(@NotNull Term x) {
        if (x.op() == Op.NEG) {
            return ((Compound) x).term(0); //unwrap
        } else {
            //do this manually for premise rules since they will need to negate atoms which is not usually allowed
            return new GenericCompound(Op.NEG, TermContainer.the(x));
        }
    }

    @NotNull
    private PremiseRule clonePermutation(Term newT, Term newB, Term newR, boolean question, @NotNull PatternTermIndex index) {


        Map<Term, Term> m = new HashMap(3);
        m.put(getTask(), newT);
        m.put(getBelief(), newB);
        boolean swapTruth = (!question && getTask().equals(newB) && getBelief().equals(newT));

        m.put(getConclusionTermPattern(), newR);


        Compound remapped = (Compound) terms.replace(this, m);

        //Append taskQuestion
        Compound pc = (Compound) remapped.term(0);
        Term[] pp = pc.terms(); //premise component
        Compound newPremise;

        Compound newConclusion = (Compound) remapped.term(1);

        if (question) {

            newPremise = p(concat(pp, TaskAny));
            //newPremise = pc; //same


            //remove truth values and add '?' punct
            TermContainer ss = ((Compound) newConclusion.term(1)).subterms();
            newConclusion = p(

                    newConclusion.term(0), $.p(ss.filter((x) -> {
                        return !(((Compound) x).op() == Op.INH && (
                                ((Compound) x).term(1).equals($.the("Belief"))
                                        || ((Compound) x).term(1).equals($.the("Goal"))));
                    }).append($("Punctuation:Question")))
            );

        } else {
            if (swapTruth) {
                newConclusion = (Compound) terms.transform(newConclusion, truthSwap);
            }

            newPremise = pc; //same
        }

        return PremiseRuleSet.normalize(new PremiseRule(newPremise, newConclusion), index);

    }


    public static final class PremiseRuleVariableNormalization extends VariableNormalization {


        public static final int ELLIPSIS_ZERO_OR_MORE_ID_OFFSET = 1 * 256;
        public static final int ELLIPSIS_ONE_OR_MORE_ID_OFFSET = 2 * 256;
        public static final int ELLIPSIS_TRANSFORM_ID_OFFSET = 3 * 256;

        int offset;

        public PremiseRuleVariableNormalization() {
            super(new UnifiedMap<>(8));
        }

        public static AbstractVariable varPattern(int i) {
            return v(VAR_PATTERN, i);
        }

        @NotNull
        @Override
        protected Variable newVariable(@NotNull Variable v, int serial) {


            int actualSerial = serial + offset;

            if (v instanceof Ellipsis.EllipsisTransformPrototype) {
                //special

                Ellipsis.EllipsisTransformPrototype ep = (Ellipsis.EllipsisTransformPrototype) v;

//                Term from = ep.from;
//                if (from != Op.Imdex) from = applyAfter((GenericVariable)from);
//                Term to = ep.to;
//                if (to != Op.Imdex) to = applyAfter((GenericVariable)to);
//
                return EllipsisTransform.make(varPattern(actualSerial + ELLIPSIS_TRANSFORM_ID_OFFSET), ep.from, ep.to, this);

            } else if (v instanceof Ellipsis.EllipsisPrototype) {
                Ellipsis.EllipsisPrototype ep = (Ellipsis.EllipsisPrototype) v;
                return Ellipsis.EllipsisPrototype.make(actualSerial +
                                (ep.minArity == 0 ? ELLIPSIS_ZERO_OR_MORE_ID_OFFSET : ELLIPSIS_ONE_OR_MORE_ID_OFFSET) //these need to be distinct
                        , ep.minArity);
            } else if (v instanceof Ellipsis) {

                int idOffset;
                if (v instanceof EllipsisTransform) {
                    idOffset = ELLIPSIS_TRANSFORM_ID_OFFSET;
                } else if (v instanceof EllipsisZeroOrMore) {
                    idOffset = ELLIPSIS_ZERO_OR_MORE_ID_OFFSET;
                } else if (v instanceof EllipsisOneOrMore) {
                    idOffset = ELLIPSIS_ONE_OR_MORE_ID_OFFSET;
                } else {
                    throw new RuntimeException("N/A");
                }

                Variable r = ((Ellipsis) v).clone(varPattern(actualSerial + idOffset), this);
                offset = 0; //return to zero
                return r;
            } /*else if (v instanceof GenericVariable) {
                return ((GenericVariable) v).normalize(actualSerial); //HACK
            } else {
                return v(v.op(), actualSerial);
            }*/
            return super.newVariable(v, actualSerial);
        }

        @Override
        public final boolean testSuperTerm(@NotNull Compound t) {
            //descend all, because VAR_PATTERN is not yet always considered a variable
            return true;
        }

        @NotNull
        public Term applyAfter(@NotNull Variable secondary) {
            offset++;
            return apply(null, secondary);
        }
    }

}




