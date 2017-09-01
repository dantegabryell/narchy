package nars.derive;

import jcog.Util;
import nars.$;
import nars.NAR;
import nars.Param;
import nars.Task;
import nars.control.CauseChannel;
import nars.control.Derivation;
import nars.op.DepIndepVarIntroduction;
import nars.task.DebugDerivedTask;
import nars.task.DerivedTask;
import nars.task.NALTask;
import nars.term.InvalidTermException;
import nars.term.Term;
import nars.time.Tense;
import nars.truth.Truth;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.collections.api.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;

import static nars.Op.GOAL;
import static nars.Param.FILTER_SIMILAR_DERIVATIONS;
import static nars.time.Tense.ETERNAL;

/**
 * Final conclusion step of the derivation process that produces a derived task
 * <p>
 * Each rule corresponds to a unique instance of this
 * <p>
 * runtime instance. each leaf of each NAR's derivation tree should have
 * a unique instance, assigned the appropriate cause id by the NAR
 * at initialization.
 */
public class Conclusion extends AbstractPred<Derivation> {

    /**
     * destination of any derived tasks; also may be used to communicate backpressure
     * from the recipient.
     */
    private final CauseChannel<Task> channel;

    private final static Logger logger = LoggerFactory.getLogger(Conclusion.class);
    private final Term pattern;
    private final String rule;
    private final boolean varIntro, goalUrgent;
    private final int minNAL;

    public Conclusion(@NotNull Conclude id, CauseChannel<Task> input) {
        super($.func("derive", /*$.the(cid), */id.sub(0) /* prod args */));
        this.channel = input;
        this.pattern = id.pattern;
        this.varIntro = id.varIntro;
        this.goalUrgent = id.goalUrgent;
        this.rule = id.rule.toString(); //only store toString of the rule to avoid remaining attached to the RuleSet
        this.minNAL = id.rule.minNAL;
        //assert(this.minNAL!=0): "unknown min NAL level for rule: " + rule;
    }

    /**
     * @return true to allow the matcher to continue matching,
     * false to stop it
     */
    @Override
    public final boolean test(@NotNull Derivation d) {

        NAR nar = d.nar;

        nar.emotion.derivationTry.increment();

        if (minNAL > nar.nal())  //HACK
            return true;

        //TODO make a variation of transform which can terminate early if exceeds a minimum budget threshold
        //  which is already determined bythe constructed term's growing complexity) in m.budget()


        // 1. SUBSTITUTE
        Term b1 = d.transform(this.pattern);
//        if (b1.vars(null) > 0) {
//            Term b2 = d.transform(b1);
//                        if (!b1.equals(b2))
//                            System.out.println("second transform");
//            b1 = b2;
//        }

        //assert (b1.varPattern() == 0) : b1 + " has pattern variables";
        if (b1.varPattern() > 0)
            return true;

        if (!b1.op().conceptualizable)
            return true;

        /// 2. EVAL ----


        d.use(Param.TTL_DERIVE_EVAL);
        nar.emotion.derivationEval.increment();

        Term c1 = b1.eval(d); //TODO cache pure eval terms
        if (!c1.op().conceptualizable)
            return true;




        // 4. TEMPORALIZE --

        Truth truth = d.concTruth;

        @NotNull final long[] occ;
        final float[] eviGain = {1f}; //flat by default

        Term c2;
        if (d.temporal) {

            Term t1;
            try {
                occ = new long[]{ETERNAL, ETERNAL};
                t1 = solveTime(d, c1, occ, eviGain);
//                if (t1!=null && occ[0] == 7) {
//                    //FOR A SPECIFIC TEST TEMPORAR
//                    System.err.println("wtf");
//                    solveTime(d, c1, occ, eviGain);
//                }
            } catch (InvalidTermException t) {
                if (Param.DEBUG) {
                    logger.error("temporalize error: {} {} {}", d, c1, t.getMessage());
                }
                return true;
            }

            //invalid or impossible temporalization; could not determine temporal attributes. seems this can happen normally
            if (t1 == null || !t1.op().conceptualizable/*|| (Math.abs(occReturn[0]) > 2047483628)*/ /* long cast here due to integer wraparound */) {
//                            throw new InvalidTermException(c1.op(), c1.dt(), "temporalization failure"
//                                    //+ (Param.DEBUG ? rule : ""), c1.toArray()
//                            );

                //FOR DEBUGGING
//                if (t1==null)
//                    new Temporalize(d.random).solve(d, c1, new long[]{ETERNAL, ETERNAL});

                return true;
            }

            if (occ[1] == ETERNAL) occ[1] = occ[0]; //HACK probbly isnt needed

            if (goalUrgent && d.concPunc == GOAL) {
                long taskStart = d.task.start();
                if (d.temporal && taskStart == ETERNAL)
                    taskStart = d.time;
                //if (taskStart != ETERNAL) {
                if (taskStart != ETERNAL && occ[0] < taskStart) {

                        long taskDur = occ[1] - occ[0];
                        occ[0] = taskStart;
                        occ[1] = occ[0] + taskDur;

                }
            }

            c2 = t1;

        } else {
            occ = Tense.ETERNAL_RANGE;
            c2 = c1;
        }

                // 3. VAR INTRO
        if (varIntro) {
            //var intro before temporalizing.  otherwise any calculated temporal data may not applied to the changed term (ex: occ shift)
            @Nullable Pair<Term, Map<Term, Term>> vc = DepIndepVarIntroduction.varIntroX(c2, d.random);
            if (vc == null) return true;

            Term v = vc.getOne();
            if (!(v.op().conceptualizable) || (v.equals(c2) /* keep only if it differs */))
                return true;

//            if (d.temporal) {
//                Map<Term, Term> m = vc.getTwo();
//                m.forEach(d.xy::tryPut); //store the mapping so that temporalization can resolve with it
//            }

            c2 = v;
        }

        //5. VALIDATE FOR TASK TERM

        byte punc = d.concPunc;
        Task t = Task.tryTask(c2, punc, truth, (C, tr) -> {

            if (tr != null) { //beliefs and goals
                tr = tr.ditherFreqConf(d.truthResolution, d.confMin, eviGain[0]);
                if (tr == null)
                    return null; //HACK
            }

            long start = occ[0];
            long end = occ[1];
            //assert (end >= start);
            if (end < start) {
                long e = end;
                end = start;
                start = e;
            }


            short[] cause = ArrayUtils.addAll(d.parentCause, channel.id);

            //CONSTRUCT TASK
            return
                    Param.DEBUG ?
                            new DebugDerivedTask(C, punc, tr, d, start, end, cause) :
                            new DerivedTask(C, punc, tr, d, start, end, cause);
        });

        if (t == null) {
            d.use(Param.TTL_DERIVE_TASK_FAIL);
            return true;
        }


        if (same(t, d.task, d.truthResolution) || (d.belief != null && same(t, d.belief, d.truthResolution))) {
            d.use(Param.TTL_DERIVE_TASK_SAME);
            return true; //created a duplicate of the task
        }

        float priority = d.premisePri; //d.budgeting.budget(d, C, truth, punc, start, end);
        assert (priority == priority);

        t.setPri(priority);


        if (Param.DEBUG)
            t.log(rule);

        d.accept(t);
        d.use(Param.TTL_DERIVE_TASK_SUCCESS);
        return true;
    }

    private Term solveTime(@NotNull Derivation d, Term c1, @NotNull long[] occ, float[] eviGain) {
        DerivationTemporalize dt = d.temporalize;
        if (dt == null) {
            d.temporalize = dt = new DerivationTemporalize(d); //cache in derivation
        }
//        dt = new DerivationTemporalize(d);
        return dt.solve(d, c1, occ, eviGain);
    }

    boolean same(Task derived, Task parent, float truthResolution) {
        if (parent.isDeleted())
            return false;

        if (derived.equals(parent)) return true;

        if (FILTER_SIMILAR_DERIVATIONS) {
            //test for same punc, term, start/end, freq, but different conf
            if (parent.term().equals(derived.term()) && parent.punc() == derived.punc() && parent.start() == derived.start() && parent.end() == derived.end()) {
                if (Arrays.equals(derived.stamp(), parent.stamp())) {
                    if (parent.isQuestOrQuestion() ||
                            (Util.equals(parent.freq(), derived.freq(), truthResolution) &&
                                    parent.evi() >= derived.evi())
                            ) {
                        if (Param.DEBUG_SIMILAR_DERIVATIONS)
                            logger.warn("similar derivation to parent:\n\t{} {}\n\t{}", derived, parent, rule);

                        //((NALTask)parent).merge(derived);
                        parent.priMax(derived.priElseZero());
                        ((NALTask) parent).merge(derived); //merge cause
                        return true;
                    }
                }
            }
        }
        return false;
    }


}
