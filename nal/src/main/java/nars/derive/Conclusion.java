package nars.derive;

import nars.*;
import nars.control.CauseChannel;
import nars.control.Derivation;
import nars.op.DepIndepVarIntroduction;
import nars.task.DebugDerivedTask;
import nars.task.DerivedTask;
import nars.term.InvalidTermException;
import nars.term.Term;
import nars.term.atom.Bool;
import nars.term.var.Variable;
import nars.time.Tense;
import nars.truth.Truth;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.collections.api.tuple.primitive.ObjectBooleanPair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static nars.Op.GOAL;
import static nars.time.Tense.DTERNAL;
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

    public final static Logger logger = LoggerFactory.getLogger(Conclusion.class);
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
        Term b1  = d.transform(this.pattern);
        if (b1.vars(null) > 0) {
            Term b2 = d.transform(b1);
            //            if (!b1.equals(b2))
            //                System.out.println("second transform");
            b1 = b2;
        }
        assert (b1.varPattern() == 0);

        /// 2. EVAL ----

        d.use(Param.TTL_DERIVE_TASK_ATTEMPT);
        nar.emotion.derivationEval.increment();

        //TODO cache eval terms
        Term c1 = b1.eval(d);

        if (c1!=null && (c1 instanceof Variable || c1 instanceof Bool))
            return true;



        // 3. TEMPORALIZE --

        Truth truth = d.concTruth;


        @NotNull final long[] occ;

        Term c2;
        if (d.temporal) {

            Term t1;
            try {
                t1 = Temporalize.solve(d, c1, occ = new long[]{ETERNAL, ETERNAL});
            } catch (InvalidTermException t) {
                if (Param.DEBUG) {
                    logger.error("temporalize error: {} {} {}", d, c1, t.getMessage());
                }
                return true;
            }

            //invalid or impossible temporalization; could not determine temporal attributes. seems this can happen normally
            if (t1 == null || t1 instanceof Variable || t1 instanceof Bool /*|| (Math.abs(occReturn[0]) > 2047483628)*/ /* long cast here due to integer wraparound */) {
//                            throw new InvalidTermException(c1.op(), c1.dt(), "temporalization failure"
//                                    //+ (Param.DEBUG ? rule : ""), c1.toArray()
//                            );

                return true;
            }

            if (occ[1] == ETERNAL) occ[1] = occ[0];

            if (goalUrgent && d.concPunc==GOAL/* && occ[0]!=ETERNAL*/) {
                long taskStart = d.task.start();

                if (taskStart!=ETERNAL) { //preserve any temporality, dont overwrite as eternal
                    long taskDur = occ[1] - occ[0];


                    int derInBelief = d.transform(d.beliefTerm).subtermTime(t1);
                    if (derInBelief!=DTERNAL) {
                        taskStart += derInBelief;
                    }

                    occ[0] = taskStart;
                    occ[1] = occ[0] + taskDur;
                }
            }


            c2 = t1;

        } else {
            occ = Tense.ETERNAL_RANGE;
            c2 = c1;
        }

        if (varIntro) {
            Term cu = DepIndepVarIntroduction.varIntro(c2, nar);
            if (cu instanceof Variable || cu instanceof Bool || (cu.equals(c2) /* keep only if it differs */))
                return true;

//            Term Cv = normalizedOrNull(cu, d.terms,
//                    d.temporal ? d.terms.retemporalizeZero : d.terms.retemporalizeDTERNAL //select between eternal and parallel depending on the premises's temporality
//            );
//            if (Cv == null)
//                return true;

            c2 = cu;
        }

        byte punc = d.concPunc;
        @Nullable ObjectBooleanPair<Term> c3n = Task.tryContent(c2, punc, true);
        if (c3n != null) {

            boolean negating = c3n.getTwo();

            final Term C = c3n.getOne();
            if (C instanceof Variable || C instanceof Bool)
                return true;

            long start = occ[0];
            long end = occ[1];
            //assert (end >= start);
            if (end < start) {
                long t = end;
                end = start;
                start = t;
            }

            float priority = d.premisePri; //d.budgeting.budget(d, C, truth, punc, start, end);
            assert (priority == priority);

            if (truth != null) {

                if (negating)
                    truth = truth.negated();
            }

            short[] cause = ArrayUtils.addAll(d.parentCause, channel.id);

            DerivedTask t =
                    Param.DEBUG ?
                            new DebugDerivedTask(C, punc, truth, d, start, end, cause) :
                            new DerivedTask(C, punc, truth, d, start, end, cause);

            if (t.equals(d.task) || t.equals(d.belief)) {
                return true; //created a duplicate of the task
            }

            t.setPri(priority);


            if (Param.DEBUG)
                t.log(rule);

            d.accept(t);
            d.use(Param.TTL_DERIVE_TASK_SUCCESS);
            return true;
        }

        //        } catch (InvalidTermException | InvalidTaskException e) {
//            if (Param.DEBUG_EXTRA)
//                logger.warn("{} {}", m, e.getMessage());
//        }

        d.use(Param.TTL_DERIVE_TASK_FAIL);
        return true;
    }


}
