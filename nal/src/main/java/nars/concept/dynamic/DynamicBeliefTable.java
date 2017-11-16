package nars.concept.dynamic;

import nars.NAR;
import nars.Param;
import nars.Task;
import nars.concept.BaseConcept;
import nars.concept.Tasklinks;
import nars.table.DefaultBeliefTable;
import nars.table.TemporalBeliefTable;
import nars.task.util.PredictionFeedback;
import nars.term.Term;
import nars.term.transform.Retemporalize;
import nars.truth.Truth;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static nars.time.Tense.DTERNAL;
import static nars.time.Tense.XTERNAL;


public class DynamicBeliefTable extends DefaultBeliefTable {

    final DynamicTruthModel model;
    private final boolean beliefOrGoal;
    private final Term term;


    public DynamicBeliefTable(Term c, TemporalBeliefTable t, DynamicTruthModel model, boolean beliefOrGoal) {
        super(t);
        this.term = c;
        this.model = model;
        this.beliefOrGoal = beliefOrGoal;
    }

    @Override
    public void add(Task input, BaseConcept concept, NAR nar) {

        if (!input.isInput()) {

            Task matched = match(input.start(), input.end(), input.term(), nar);

            if (matched != null) {

                if (!(input.isEternal() ^ matched.isEternal())) { //must be of the same temporality

                    float inputPri = input.priElseZero();
                    if (PredictionFeedback.absorb(matched, input, nar)) {
                        float activationApplied = Math.max(0, inputPri - matched.priElseZero());
                        Tasklinks.linkTask(matched, activationApplied, concept, nar);
                        return;
                    }
                }

            }
        }

        super.add(input, concept, nar);
    }

    @Nullable
    protected Task generate(Term template, long start, long end, NAR nar) {

        Term tt = template(template, start, end, nar);
        if (tt == null)
            return null;

        DynTruth yy = truth(start, end, tt, true, nar);
        if (yy == null)
            return null;

        return yy.task(tt, beliefOrGoal, nar);
    }

    @Override
    public Truth truth(long start, long end, NAR nar) {
        Term t = template(term, start, end, nar);
        if (t == null)
            return null;


        DynTruth d = truth(start, end, t, false, nar);
        return Truth.maxConf(d != null ? d.truth() : null,
                super.truth(start, end, nar) /* includes only non-dynamic beliefs */);
    }

    /** prepare a term, if necessary, for use as template  */
    private Term template(Term template, long start, long end, NAR nar) {
        if (template.dt() == XTERNAL) {
            int newDT = matchDT(template, start, end);
            template = template.dt(newDT);
        }

        //still XTERNAL ? try using start..end as a dt
        if (template.dt() == XTERNAL && template.subs()==2) {
            template = template.dt((int) (end-start));
        }

        template = template.temporalize(Retemporalize.retemporalizeXTERNALToZero);
        if (template == null)
            return null;


        if (!template.conceptual().equals(term))
            return null; //doesnt correspond to this concept anyway

        return template;

//        if (t2 == null) {
//
//
//
//            //for some reason, retemporalizing to DTERNAL failed (ex: conj collision)
//            //so as a backup plan, use dt=+/-1
//            int dur = nar.dur();
//            Random rng = nar.random();
//            t2 = template.temporalize(new Retemporalize.RetemporalizeFromToFunc(XTERNAL,
//                    () -> rng.nextBoolean() ? +dur : -dur));
//        }
////        if (t2!=null && t2.dt()==XTERNAL) {
////            return template(t2, start, end ,nar);//temporary
////            //throw new RuntimeException("wtf xternal");
////        }
//
//        return t2;
    }


    @Nullable
    protected DynTruth truth(long start, long end, Term template, boolean evidence, NAR nar) {
        return model.eval(template, beliefOrGoal, start, end, evidence, nar); //newDyn(evidence);
    }

    /**
     * returns an appropriate dt by sampling the existing beliefs
     * in the table (if any exist).  if no dt can be calculated, return
     * a standard value (ex: 0 or DTERNAL)
     */
    private int matchDT(Term term, long start, long end) {

        //assert (term.op().temporal): term + " is non-temporal but matchDT'd";

        int s = size();
        if (s > 0) {
            final int[] count = {0};
            final long[] sum = {0};

            Consumer<Task> tx = x -> {
                int xdt = x.dt();
                if (xdt!=XTERNAL && xdt != DTERNAL) {
                    sum[0] += xdt;
                    count[0]++;
                }
            };

            final int MAX_TASKS_FOR_COMPLETE_ITERATION = 8;
            if (s < MAX_TASKS_FOR_COMPLETE_ITERATION)
                forEachTask(tx);
            else
                forEachTask(true, start, end, tx); //just the matching subrange, should be cheaper if # of tasks is high

            if (count[0] > 0) {
                return (int) (sum[0] / count[0]);
            }
        }

        //return DTERNAL;
        return 0; //parallel
    }

    @Override
    public Task match(long start, long end, Term template, NAR nar) {
        Task x = super.match(start, end, template, nar);


        Task y = generate(template, start, end, nar);
        if (y == null || y.equals(x)) return x;

        boolean dyn;
        if (x == null) {
            dyn = true;
        } else {
            //choose higher confidence
            int dur = nar.dur();
            float xc = x.evi(start, end, dur);
            float yc = y.evi(start, end, dur);

            //prefer the existing task within a small epsilon lower for efficiency
            dyn = yc >= xc + Param.TRUTH_EPSILON;
        }

        if (dyn) {
            //Activate.activate(y, y.priElseZero(), nar);
            return y;
        } else {
            return x;
        }

    }
}
