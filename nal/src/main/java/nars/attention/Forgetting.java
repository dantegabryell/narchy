package nars.attention;

import jcog.math.FloatRange;
import jcog.pri.ScalarValue;
import jcog.pri.bag.Bag;
import jcog.pri.op.PriForget;
import nars.NAR;
import nars.concept.Concept;
import nars.link.TaskLink;
import nars.task.Tasklike;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** TODO abstract */
abstract public class Forgetting {

    public void update(NAR n) {

    }

    abstract public void update(Concept c, NAR n);


    public final @Nullable Consumer forget(Bag b, float depressurizationRate, float temperature) {

        if (temperature > Float.MIN_NORMAL) {
            int size = b.size();
            if (size > 0) {
                int cap = b.capacity();
                if (cap > 0) {

                    float pressure = depressurize(b, depressurizationRate);

                    float mass = b.mass();
                    if (mass > Float.MIN_NORMAL) {

                        Consumer eachMustForgetPct = forget(temperature, size, cap, pressure, mass);

                        if (eachMustForgetPct != null)
                            return eachMustForgetPct;

                    }
                }
            }
        }

        return null;

    }

    abstract protected float depressurize(Bag b, float temperature);

    abstract protected @Nullable Consumer forget(float temperature, int size, int cap, float pressure, float mass);

    /** temporally oblivious; uses only incoming pressure to determine forget amounts */
    public static class AsyncForgetting extends Forgetting {


        public final FloatRange tasklinkForgetRate = new FloatRange(0.9f, 0f, 1f);

        @Override protected float depressurize(Bag b, float depressurizationRate) {
            return b.depressurizePct(depressurizationRate);
        }

        public final void update(Concept c, NAR n) {
            Bag<Tasklike, TaskLink> tasklinks = c.tasklinks();
            tasklinks.commit(forgetTasklinks(c, tasklinks));
        }

        protected Consumer<TaskLink> forgetTasklinks(Concept c, Bag<Tasklike, TaskLink> tasklinks) {
            return forget(tasklinks, 1f, tasklinkForgetRate.floatValue());
        }


        @Override
        protected Consumer forget(float temperature, int size, int cap, float pressure, float mass) {
            return PriForget.forgetPressure(temperature, size, cap, pressure, mass);
        }
    }

    /** experimental */
    public static class TimedForgetting extends Forgetting {

        /**
         * number of clock durations composing a unit of short term memory decay (used by bag forgetting)
         */
        public final FloatRange memoryDuration = new FloatRange(1f, 0f, 64f);

        @Override protected float depressurize(Bag b, float temperature) {
            return b.depressurizePct(temperature);
        }

        @Override
        protected Consumer forget(float temperature, int size, int cap, float pressure, float mass) {
            return PriForget.forgetIdeal(temperature,
                                        ScalarValue.EPSILON * cap,
                                        //1f/size,
                                        //1f/cap,
                                        //0.1f,
                                        //0.5f,
                                        size, cap, pressure, mass);
        }


//        @Override
//        public void updateConcepts(Bag<Term, Activate> active, long dt, NAR n) {
//            float temperature = 1f - (float) Math.exp(-(((double) dt) / n.dur()) / memoryDuration.floatValue());
//            active.commit(active.forget(temperature));
//        }

        public void update(Concept c, NAR n) {


            int dur = n.dur();

            Consumer<TaskLink> tasklinkUpdate;
            Bag<Tasklike, TaskLink> tasklinks = c.tasklinks();

            long curTime = n.time();
            Long prevCommit = c.meta("C", curTime);
            if (prevCommit != null) {
                if (curTime - prevCommit > 0) {

                    double deltaDurs = ((double) (curTime - prevCommit)) / dur;

                    //deltaDurs = Math.min(deltaDurs, 1);

                    float forgetRate = (float) (1 - Math.exp(-deltaDurs / memoryDuration.doubleValue()));

                    //System.out.println(deltaDurs + " " + forgetRate);
                    tasklinkUpdate = tasklinks.forget(forgetRate);

                } else {
                    //dont need to commit, it already happened in this cycle
                    return;
                }
            } else {
                tasklinkUpdate = null;

            }

            tasklinks.commit(tasklinkUpdate);

        }
    }
}
