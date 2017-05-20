package nars.control;

import jcog.Util;
import jcog.bag.Bag;
import jcog.data.FloatParam;
import jcog.event.On;
import jcog.pri.PLink;
import nars.*;
import nars.concept.Concept;
import nars.premise.Derivation;
import nars.premise.DerivationBudgeting;
import nars.premise.PreferSimpleAndPolarized;
import nars.premise.PremiseBuilder;
import nars.task.UnaryTask;
import nars.task.util.InvalidTaskException;
import nars.term.Term;
import nars.term.util.InvalidTermException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static jcog.bag.Bag.BagCursorAction.Next;
import static jcog.bag.Bag.BagCursorAction.Stop;


/**
 * fires sampled active focus concepts
 */
public class FireConcepts implements Runnable {



    final int MISFIRE_COST = 1;
    int premiseCost = 1;
    int linkSampleCost = 1;


    /**
     * in total priority per cycle, however
     * it is actually sum(1 + concept priority)
     * so that 0 priority concepts consume something
     */
    public final @NotNull FloatParam rate = new FloatParam((Param.UnificationTTLMax * 1), 0f, (512));

    //    public final MutableInteger derivationsInputPerCycle;
//    this.derivationsInputPerCycle = new MutableInteger(Param.TASKS_INPUT_PER_CYCLE_MAX);
    protected final NAR nar;
    private final On on;
    public final Focus source;

//    class PremiseVectorBatch implements Consumer<BLink<Concept>>{
//
//        public PremiseVectorBatch(int batchSize, NAR nar) {
//            nar.focus().sample(batchSize, c -> {
//                if (premiseVector(nar, c.get(), FireConcepts.this)) return true; //continue
//
//                return true;
//            });
//        }
//
//        @Override
//        public void accept(BLink<Concept> conceptBLink) {
//
//        }
//    }

    public class ConceptFire extends UnaryTask<Concept> {


        public ConceptFire(Concept c, float pri) {
            super(c, pri);
        }


        @Override
        public void run(NAR nar) throws Concept.InvalidConceptException, InvalidTermException, InvalidTaskException {


            float pri = this.pri;

            if (pri!=pri)
                return;

            int ttl = Util.lerp(pri, Param.FireTTLMax, Param.FireTTLMin);

            final Concept c = value;

            final Bag<Task, PLink<Task>> tasklinks = c.tasklinks().commit();//.normalize(0.1f);
            final Bag<Term, PLink<Term>> termlinks = c.termlinks().commit();//.normalize(0.1f);
            //nar.terms.commit(c);

            @Nullable PLink<Task> tasklink = null;
            @Nullable PLink<Term> termlink = null;
            float taskLinkPri = -1f, termLinkPri = -1f;

            List<PremiseBuilder> premises = $.newArrayList();
            //also maybe Set is appropriate here

            /**
             * this implements a pair of roulette wheel random selectors
             * which have their options weighted according to the normalized
             * termlink and tasklink priorities.  normalization allows the absolute
             * range to be independent which should be ok since it is only affecting
             * the probabilistic selection sequence and doesnt affect derivation
             * budgeting directly.
             */
            Random rng = nar.random();
            while (ttl > 0) {
                if (tasklink == null || (rng.nextFloat() > taskLinkPri)) { //sample a new link inversely probabalistically in proportion to priority
                    tasklink = tasklinks.sample();
                    ttl -= linkSampleCost;
                    if (tasklink == null)
                        break;

                    taskLinkPri = tasklinks.normalizeMinMax(tasklink.priSafe(0));
                }


                if (termlink == null || (rng.nextFloat() > termLinkPri)) { //sample a new link inversely probabalistically in proportion to priority
                    termlink = termlinks.sample();
                    ttl -= linkSampleCost;
                    if (termlink == null)
                        break;
                    termLinkPri = termlinks.normalizeMinMax(termlink.priSafe(0));
                }

                premises.add(new PremiseBuilder(tasklink, termlink));

                ttl -= premiseCost; //failure of premise generation still causes cost
            }

            //divide priority among the premises
            int num = premises.size();
            if (num > 0) {
                float subPri = pri / num;
                premises.forEach(p -> {
                    p.setPri(subPri);
                    nar.input(p);
                });
            }
        }

    }


    public FireConcepts(@NotNull Focus source, NAR nar) {

        this.nar = nar;
        this.source = source;
        this.on = nar.onCycle(this);

    }

    @Override
    public void run() {

        float load =
                //0f;
                nar.exe.load();
//            if (load > 0.9f) {
//                logger.error("overload {}", load);
//                return;
//            }

        ConceptBagFocus csrc = (ConceptBagFocus) source;
        if (((ConceptBagFocus) this.source).active.commit().size() == 0)
            return; //no concepts

        final float[] curTTL = { (rate.floatValue() * (1f - load))};
        if (curTTL[0] == 0)
            return; //idle

//        float idealMass = 0.5f /* perfect avg if full */ * csrc.active.capacity();
//        float mass = ((HijackBag) csrc.active).mass;
//        float priDecayFactor =
//                //1f - (((float)csrc.active.size()) / csrc.active.capacity());
//                Util.unitize(
//                        1f - mass / (mass + idealMass)
//                );


        final int[] fired = {0};

        csrc.active.sample(pc -> {

            float priFired = pc.priSafe(0);

            ConceptFire cf = new ConceptFire(pc.get(), priFired);
            fired[0]++;
            nar.input(cf);

            //pc.priMult(priDecayFactor);


            curTTL[0] -= (1f + priFired);
            return curTTL[0] > 0 ? Next : Stop;
        }, false);


    }


    public static final class DirectDerivation extends Derivation {

        @Override
        public void derive(Task x) {
            nar.input(x);
        }

    }


}

//    /**
//     * returns # of derivations processed
//     */
//    int premiseVector0(PLink<Concept> pc, Derivation d, MutableIntRange taskLinksFiredPerConcept, MutableIntRange termLinksFiredPerConcept) {
//
//        Concept c = pc.get();
//        float cPri = pc.priSafe(0);
//
//        List<PLink<Task>> tasklinks = c.tasklinks().commit().sampleToList(taskLinksFiredPerConcept.lerp(cPri));
//        if (tasklinks.isEmpty())
//            return 0;
//
//        List<PLink<Term>> termlinks = c.termlinks().commit().sampleToList(termLinksFiredPerConcept.lerp(cPri));
//        if (termlinks.isEmpty())
//            return 0;
//
//        int count = 0;
//
//        long now = nar.time();
//        for (int i = 0, tasklinksSize = tasklinks.size(); i < tasklinksSize; i++) {
//            PLink<Task> tasklink = tasklinks.get(i);
//
//            float tlPri = tasklink.pri();
//
//            for (int j = 0, termlinksSize = termlinks.size(); j < termlinksSize; j++) {
//                PLink<Term> termlink = termlinks.get(j);
//
//                Premise p = PremiseBuilder.premise(c, tasklink, termlink, now, nar, -1f);
//                if (p != null) {
//
//                    float invest = Util.or(tlPri, termlink.pri());
//                    int ttl = Util.lerp(invest, Param.UnificationTTLMax, Param.UnificationTTLMin);
//
//                    if (deriver.test(d.restart(p, ttl)))
//                        count++;
//                }
//            }
//        }
//
//        return count;
//
//    }


//    class PremiseMatrixBatch implements Consumer<NAR> {
//        private final int _tasklinks;
//        private final int batchSize;
//        private final MutableIntRange _termlinks;
//
//        public PremiseMatrixBatch(int batchSize, int _tasklinks, MutableIntRange _termlinks) {
//            this.batchSize = batchSize;
//            this._tasklinks = _tasklinks;
//            this._termlinks = _termlinks;
//        }
//
//        @Override
//        public void accept(NAR nar) {
//            source.sample(batchSize, c -> {
//                premiser.newPremiseMatrix(c.get(),
//                        _tasklinks, _termlinks,
//                        FireConcepts.this, //input them within the current thread here
//                        nar
//                );
//                return true;
//            });
//        }
//
//    }
