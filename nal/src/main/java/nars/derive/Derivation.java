package nars.derive;

import jcog.Util;
import jcog.WTF;
import jcog.data.byt.DynBytes;
import jcog.data.set.ArrayHashSet;
import jcog.data.set.MetalLongSet;
import jcog.math.Longerval;
import jcog.pri.ScalarValue;
import jcog.sort.SortedList;
import nars.NAR;
import nars.Op;
import nars.Param;
import nars.Task;
import nars.concept.Concept;
import nars.control.CauseMerge;
import nars.derive.op.Occurrify;
import nars.derive.premise.PreDerivation;
import nars.eval.Evaluation;
import nars.link.TaskLink;
import nars.op.Subst;
import nars.op.UniSubst;
import nars.subterm.Subterms;
import nars.term.Functor;
import nars.term.Term;
import nars.term.Termed;
import nars.term.Variable;
import nars.term.anon.AnonWithVarShift;
import nars.term.atom.Atom;
import nars.term.atom.Atomic;
import nars.term.atom.Bool;
import nars.term.control.PREDICATE;
import nars.term.util.transform.Retemporalize;
import nars.truth.PreciseTruth;
import nars.truth.Stamp;
import nars.truth.Truth;
import nars.truth.func.TruthFunc;
import nars.truth.polation.TruthIntegration;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

import static nars.Op.*;
import static nars.Param.TTL_UNIFY;
import static nars.term.atom.Bool.Null;
import static nars.time.Tense.ETERNAL;
import static nars.time.Tense.TIMELESS;
import static nars.truth.func.TruthFunctions.c2wSafe;


/**
 * evaluates a premise (task, belief, termlink, taskLink, ...) to derive 0 or more new tasks
 */
public class Derivation extends PreDerivation {

    public static final ThreadLocal<Derivation> derivation = ThreadLocal.withInitial(Derivation::new);
    protected final static Logger logger = LoggerFactory.getLogger(Derivation.class);

    public static final Atomic Task = Atomic.the("task");
    public static final Atomic Belief = Atomic.the("belief");
    public static final Atomic TaskTerm = Atomic.the("taskTerm");
    public static final Atomic BeliefTerm = Atomic.the("beliefTerm");
    private final static int ANON_INITIAL_CAPACITY = 16;


    public final SortedList<Premise> premiseBuffer =
        new SortedList<>(256);

    public final AnonWithVarShift anon;

    protected final UniSubst myUniSubst = new UniSubst(this);

    protected final Functor polarizeTask = new Functor.AbstractInlineFunctor1("polarizeTask") {
        @Override
        protected Term apply1(Term arg) {
            Truth t = Derivation.this.taskTruth;
            if (t == null)
                return Null;  //TODO WTF
            return t.isPositive() ? arg : arg.neg();
        }
    };

    protected final Functor polarizeRandom = new Functor.AbstractInlineFunctor1("polarizeRandom") {
        @Override
        protected Term apply1(Term arg) {
            return random.nextBoolean() ? arg : arg.neg();
        }
    };

    protected final Functor polarizeBelief = new Functor.AbstractInlineFunctor1("polarizeBelief") {
        @Override
        protected Term apply1(Term arg) {
            Truth t = Derivation.this.beliefTruthRaw;
            if (t == null)
                return Null;  //TODO WTF
            return t.isPositive() ? arg : arg.neg();
        }
    };

    @Deprecated protected final Functor polarizeFunc = new Functor.AbstractInlineFunctor2("polarize") {
        @Override
        protected Term apply(Term subterm, Term whichTask) {
            if (subterm instanceof Bool)
                return subterm;

            Truth compared;
            if (whichTask.equals(Task)) {
                compared = taskTruth;
            } else {
                //assert(whichTask.equals(Belief))
                compared = beliefTruthRaw;
            }
            if (compared == null)
                return Null;
            return compared.isPositive() ? subterm : subterm.neg();
        }
    };

    public NAR nar;


    /**
     * second layer additional substitutions
     */
    public final Map<Term, Term> retransform = new UnifiedMap<>() {
        @Override
        public Term put(Term key, Term value) {
            if (key.equals(value))
                return null;

            return super.put(key, value);
        }
    };
    protected final Subst mySubst = new Subst("substitute") {

        @Override
        public @Nullable Term apply(Evaluation e, Subterms xx) {
            Term input = xx.sub(0);
            Term replaced = xx.sub(1);
            Term replacement = xx.sub(2);

//            if (replaced instanceof Atom) {
//
//                replaced = anon.put(replaced);
//            }

            Term y = apply(xx, input, replaced, replacement);

            use(TTL_UNIFY); //substitute actually

            if (y != null && !(y instanceof Bool)) {

                //retransform.put(input, y);
                retransform.put(replaced, replacement);

            }
            return y;
        }
    };


    /**
     * current MatchTerm to receive matches at the end of the Termute chain; set prior to a complete match by the matchee
     */
    public PREDICATE<Derivation> forEachMatch;
    /**
     * current NAR time, set at beginning of derivation
     */
    public transient long time = ETERNAL;
    public transient float confMin, eviMin;
    public transient int termVolMax;


    /**
     * the base priority determined by the task and/or belief (tasks) of the premise.
     * note: this is not the same as the premise priority, which is determined by the links
     * and has already affected the selection of those links to create derived premises.
     * instead, the derived tasks are budgeted according to the priorities of the
     * parent task(s) NOT the links.  this allows the different budget 'currencies' to remain
     * separate.
     */
    public final Occurrify occ = new Occurrify(this);
    /**
     * whether either the task or belief are events and thus need to be considered with respect to time
     */

    @Deprecated public transient boolean temporal;
    public transient TruthFunc truthFunction;
    public transient int ditherTime;

    public Deriver deriver;
    public final DynBytes tmpPremiseKey = new DynBytes(256);

    /** temporary storage buffer for recently fired tasklinks */
    public final ArrayHashSet<TaskLink> firedTaskLinks = new ArrayHashSet<>(32);
    public final ArrayHashSet<Task> firedTasks = new ArrayHashSet<>(32);
    /** temporary storage buffer for recently activated concepts */
    public final ArrayHashSet<Concept> firedConcepts = new ArrayHashSet<>(32);


    private Function<Atomic, Functor> derivationFunctors;

    /**
     * precise time that the task and belief truth are sampled
     */
    public transient long taskStart, taskEnd, beliefStart, beliefEnd; //TODO taskEnd, beliefEnd

    public long[] taskBeliefTimeIntersects = new long[2];

    private transient Term _beliefTerm;
    private transient long[] evidenceDouble, evidenceSingle;
    private transient int taskUniques;
    private final transient MetalLongSet taskStamp = new MetalLongSet(Param.STAMP_CAPACITY);
    public transient boolean overlapDouble, overlapSingle;


    /** these represent the maximum possible priority of the derivation.
     * the maximum constraint is a contract ensuring the range of priority
     * can be predicted for deciding
     *  whether to attempt before beginning,
     *  or whether to continue deriving during the procedure. */
    public transient float priSingle, priDouble;


    public transient short[] parentCause;
    public transient boolean concSingle;
    public transient float parentVoluplexitySum;
    public transient float taskEvi, beliefEvi;
    public transient long[] concOcc;
    public transient Truth concTruth;
    public transient byte concPunc;
    public transient Term concTerm;
    public transient Task _task, _belief;

    public transient int dur;

    /**
     * if using this, must set: nar, index, random, DerivationBudgeting
     */
    public Derivation() {
        super(
                null
                , null, Param.UnificationStackMax
        );

        this.anon = new AnonWithVarShift(ANON_INITIAL_CAPACITY, Op.VAR_DEP.bit | Op.VAR_QUERY.bit);
    }

    @Override
    public final boolean evalInline() {
        return true;
    }



    private void init(NAR nar) {

        this.clear();

        this.nar = nar;

        this.random = nar.random();
        this.unifyPremise.random(this.random);

        //this.random.setSeed(nar.random().nextLong());

        this.derivationFunctors = DerivationFunctors.get(this);

    }


    /**
     * setup for a new derivation.
     * returns false if the premise is invalid to derive
     * <p>
     * this is optimized for repeated use of the same task (with differing belief/beliefTerm)
     */
    public void reset(Task nextTask, final Task nextBelief, Term nextBeliefTerm) {

        Term nextTaskTerm = nextTask.term();

        if (this._task != null && this._task.term().equals(nextTaskTerm)) {

            anon.rollback(taskUniques);

        } else {
            anon.clear();

            this.taskTerm = anon.put(nextTaskTerm);
            if (!taskTerm.op().taskable)
                throw new WTF(nextTaskTerm + " could not be anon, result: " + taskTerm);
            this.taskUniques = anon.uniques();
        }


        if (this._task == null || this._task != nextTask) {

            this._task = nextTask;

            assert (taskTerm != null) : (nextTask + " could not be anonymized: " + nextTaskTerm.anon() + " , " + taskTerm);

            this.taskStamp.clear(); //force (re-)compute in post-derivation stage
            this.taskEvi = Float.NaN; //invalidate

            this.taskPunc = nextTask.punc();
            if ((taskPunc == BELIEF || taskPunc == GOAL)) {
                this.taskTruth = nextTask.truth();

                assert (taskTruth != null);
            } else {
                this.taskTruth = null;
            }

            this.taskStart = nextTask.start();
            this.taskEnd = nextTask.end();

        }


        if (nextBelief != null) {
            this.beliefTruthRaw = nextBelief.truth();
            this.beliefStart = nextBelief.start();
            this.beliefEnd = nextBelief.end();

            this.beliefTruthProjectedToTask = taskStart!=ETERNAL ?
                    nextBelief.truth(taskStart, taskEnd, dur)
                    : beliefTruthRaw;

            if (Param.ETERNALIZE_BELIEF_PROJECTED_IN_DERIVATION && !(beliefStart == ETERNAL && !beliefTruthProjectedToTask.equals(beliefTruthRaw))) {
                if (Param.eternalizeInDerivation.test(nextBelief.op())) {
                    this.beliefTruthProjectedToTask = Truth.stronger(
                            beliefTruthProjectedToTask,
                            beliefTruthRaw.eternalized(1, Param.TRUTH_MIN_EVI, null /* dont dither */)
                    );
                }
            }

            this._belief = beliefTruthRaw != null || beliefTruthProjectedToTask != null ? nextBelief : null;
        } else {
            this.beliefStart = this.beliefEnd = TIMELESS;
            this._belief = null;
        }

        if (this._belief != null) {

            this.beliefTerm = anon.putShift(this._beliefTerm = nextBelief.term(), taskTerm);
//            if (beliefTerm.op()==NEG)
//                anon.putShift(this._beliefTerm = nextBelief.term(), taskTerm); //TEMPORARY
            //this.belief = new SpecialTermTask(beliefTerm, nextBelief);
            this.beliefEvi = Float.NaN; //invalidate
        } else {

            this.beliefTerm =
                    !(nextBeliefTerm instanceof Variable) ?
                        anon.putShift(this._beliefTerm = nextBeliefTerm, taskTerm) :
                        anon.put(this._beliefTerm = nextBeliefTerm); //unshifted, since the term may be structural

            //this.belief = null;
            this.beliefTruthRaw = this.beliefTruthProjectedToTask = null;
            this.beliefEvi = 0;
        }


        assert (beliefTerm != null) : (nextBeliefTerm + " could not be anonymized");
        assert (beliefTerm.op() != NEG) : nextBelief + " , " + nextBeliefTerm + " -> " + beliefTerm + " is invalid NEG op";

    }


    public boolean budget(Task task, Task belief) {
        float taskPri = task.priElseZero();
        float priSingle = taskPri;
        float priDouble = belief == null ?
                taskPri :
                Param.DerivationPri.apply(taskPri, belief.priElseZero());

        if (Param.INPUT_BUFFER_PRI_BACKPRESSURE && Math.max(priDouble,priSingle) < nar.input.priMin() /* TODO cache */)
            return false;

        this.priSingle = priSingle;
        this.priDouble = priDouble;
        return true;
    }

    /**
     * called after protoderivation has returned some possible Try's
     */
    public void derive(int ttl) {

        if (taskStart == ETERNAL && (_belief==null || beliefStart == ETERNAL)) {
            this.taskBeliefTimeIntersects[0] = this.taskBeliefTimeIntersects[1] = ETERNAL;
        } else  if ((_belief != null) && taskStart == ETERNAL) {
            this.taskBeliefTimeIntersects[0] = beliefStart;
            this.taskBeliefTimeIntersects[1] = beliefEnd;
        } else if ((_belief==null) || beliefStart == ETERNAL) {
            this.taskBeliefTimeIntersects[0] = taskStart;
            this.taskBeliefTimeIntersects[1] = taskEnd;
        } else if (_belief!=null) {
            if (null == Longerval.intersectionArray(taskStart, taskEnd, beliefStart, beliefEnd, this.taskBeliefTimeIntersects)) {
                this.taskBeliefTimeIntersects[0] = this.taskBeliefTimeIntersects[1] = TIMELESS; //no intersection
            }
        }

        this.forEachMatch = null;
        this.concTruth = null;
        this.concPunc = 0;
        this.concTerm = null;
        this.concSingle = false;
        this.truthFunction = null;
        this.evidenceDouble = evidenceSingle = null;

        this.parentVoluplexitySum =
                Util.sum(
                        taskTerm.voluplexity(), beliefTerm.voluplexity()
                );


        this.overlapSingle = _task.isCyclic();

        if (_belief != null) {

            /** to compute the time-discounted truth, find the minimum distance
             *  of the tasks considering their dtRange
             */


            if (taskStamp.isEmpty()) {
                taskStamp.addAll(_task.stamp());
            }

            this.overlapDouble =
                    Stamp.overlaps(this._task, _belief)
                    ||
                    //auto-filter double-premise, with same term and same time
                    taskStart==beliefStart && taskPunc==_belief.punc() && taskTerm.equals(beliefTerm);


        } else {
            this.overlapDouble = false;
        }


        boolean eternalComplete = (taskStart == ETERNAL) && (_belief == null || beliefStart==ETERNAL);
        this.temporal = !eternalComplete || Occurrify.temporal(taskTerm);
        if ((_belief == null) && (!temporal)) {
            if (Occurrify.temporal(beliefTerm)) {
                Term beliefTermEternal = Retemporalize.retemporalizeXTERNALToDTERNAL.transform(beliefTerm); //HACK
                if (Occurrify.temporal(beliefTermEternal)) {
                    temporal = true;
                } else {
                    beliefTerm = beliefTermEternal;
                }
            }
        }

        int causeCap = Param.causeCapacity.intValue();
        this.parentCause =
            CauseMerge.limit(
                _belief != null ?
                    CauseMerge.Append.merge(causeCap -1 /* for channel to be appended */, _task, _belief) :
                    _task.cause(), causeCap-1);
        if (parentCause.length >= causeCap)
            throw new WTF();





        setTTL(ttl);


        deriver.pri.premise(this);

        try {
            deriver.rules.run(this);
        } catch (Exception e) {
            clear();
            throw e;
        }

    }

    @Override
    public final void tryMatch() {


        forEachMatch.test(this);

    }


    /**
     * only returns derivation-specific functors.  other functors must be evaluated at task execution time
     */
    @Override
    public final Term transformAtomic(Atomic atomic) {


        if (atomic instanceof Variable) {
            Term y = resolve(atomic);
            if (y != null)
                return y;
        }

        if (atomic instanceof Atom) {
            if (atomic == TaskTerm) {
                return taskTerm;
            } else if (atomic == BeliefTerm) {
                return beliefTerm;
            }

            Termed f = derivationFunctors.apply(atomic);
            if (f != null)
                return (Term) f;
        }


        return atomic;
    }

    public Derivation next(Deriver deri) {
        NAR pnar = this.nar;

        NAR nar = deri.nar();

        if (pnar != nar) {
            init(nar);
            time = TIMELESS;
        }

        long now = nar.time();
        if (now != this.time) {
            this.time = now;

            this.dur = nar.dur();
            this.ditherTime = nar.dtDither();

            myUniSubst.u.dtTolerance = unifyPremise.dtTolerance = this.dtTolerance =
                    Math.round(Param.UNIFY_DT_TOLERANCE_DUR_FACTOR * dur);

            this.eviMin = c2wSafe(this.confMin = nar.confMin.floatValue());

            this.termVolMax = nar.termVolumeMax.intValue();
            this.beliefEvi = this.taskEvi = Float.NaN;
        }


        this.deriver = deri;

        return this;
    }

    @Nullable
    public long[] evidenceSingle() {
        if (evidenceSingle == null) {
            evidenceSingle = _task.stamp();
        }
        return evidenceSingle;
    }

    @Nullable
    public long[] evidenceDouble() {
        if (evidenceDouble == null) {
            float te, be, tb;
            if (taskPunc == BELIEF || taskPunc == GOAL) {

                te = taskTruth.evi();
                be = beliefTruthRaw != null ? beliefTruthRaw.evi() : 0;
                tb = te / (te + be);
            } else {

                te = _task.priElseZero();
                be = _belief.priElseZero();
                tb = te + be;
                tb = tb < ScalarValue.EPSILON ? 0.5f : te / tb;
            }
            long[] e = Stamp.merge(_task.stamp(), _belief.stamp(), tb, random);
            if (evidenceDouble==null || !Arrays.equals(e, evidenceDouble))
                this.evidenceDouble = e;
            return e;
        } else {
            return evidenceDouble;
        }
    }

    @Override
    public String toString() {
        return _task + " " + (_belief != null ? _belief : _beliefTerm)
                + ' ' + super.toString();
    }

    /**
     * include any .clear() for data structures in case of emergency we can continue to assume they will be clear on next run()
     */
    @Override
    public Derivation clear() {
        anon.clear();
        time = ETERNAL;
        premiseBuffer.clear();

        occ.clear();
        firedTasks.clear();
        firedConcepts.clear();
        _belief = null;
        _task = null;
        taskStamp.clear();
        parentCause = null;
        concTruth = null;
        concTerm = null;
        taskTerm = beliefTerm = null;
        taskTruth = beliefTruthProjectedToTask = beliefTruthRaw = null;
        can.clear();
        will = null;
        ttl = 0;
        taskUniques = 0;
        time = TIMELESS;

        super.clear();

        return this;
    }


    /** resolve a term (ex: task term or belief term) with the result of 2nd-layer substitutions */
    public Term retransform(Term t) {
        return
                    t.replace(retransform); //retransforms only
                    //t.replace(xy).replace(retransform); //avoid functor eval
                    //transform(t).replace(retransform);
                    //t.replace(retransform);
    }

    public final Task add(Task t) {
        return nar.input.add(t);
    }



    public boolean concTruthEviMul(float ratio, boolean eternalize) {
        float e = ratio * concTruth.evi();
        if (eternalize)
            e = Math.max(concTruth.eviEternalized(), e);
        return concTruthEvi(e);
    }

    public boolean concTruthEvi(float e) {
        return e >= eviMin && (this.concTruth = PreciseTruth.byEvi(concTruth.freq(), e)) != null;
    }

    public float parentEvi() {

        if (taskEvi!=taskEvi) {
            this.taskEvi = taskTruth != null ? TruthIntegration.evi(_task) : 0;
        }
        if (beliefEvi!=beliefEvi) {
            this.beliefEvi = _belief != null ? TruthIntegration.evi(_belief) : 0;
        }
        return concSingle ? taskEvi : (taskEvi + beliefEvi);
    }

    public final float parentPri() {
        return (concSingle ? priSingle : priDouble);
    }
}


