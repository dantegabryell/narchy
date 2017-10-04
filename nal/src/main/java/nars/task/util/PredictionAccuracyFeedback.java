package nars.task.util;

import jcog.Util;
import nars.NAR;
import nars.Param;
import nars.Task;
import nars.control.MetaGoal;
import nars.table.BeliefTable;
import nars.task.SignalTask;
import org.jetbrains.annotations.Nullable;

public class PredictionAccuracyFeedback {

    long last = Long.MIN_VALUE;
    final BeliefTable table;
    static final boolean deleteAny = false; //

    public PredictionAccuracyFeedback(BeliefTable table) {
        this.table = table;
    }

    public void accept(@Nullable Task x, NAR nar) {
        if (x == null)
            return;

        long now = x.end();
        feedback(x, Param.DELETE_INACCURATE_PREDICTIONS /* TODO make this adjustable threshold */, now, nar);
        this.last = now;
    }

    /** TODO handle stretched tasks */
    void feedback(Task x, boolean deleteIfIncoherent, long now, NAR nar) {
        float xFreq = x.freq();

        int dur = nar.dur();
        float xConf = x.conf(now, dur);

        float strength = 1;
        long last = this.last;

        //sensor feedback
        //punish any non-signal beliefs at the current time which contradict this sensor reading, and reward those which it supports
        table.forEachTask(false, last, now, (y) -> {

            if (y instanceof SignalTask)
                return; //ignore previous signaltask

            short[] cause = y.cause();
            if (cause.length == 0)
                return;

            //only tasks created before now
            long leadTime = y.start() - y.creation();
            if (leadTime < 0)
                return;

            float yConf = y.conf(now, dur);
            if (yConf!=yConf)
                return;

            float headstart = 1f + (1f+leadTime)/(1f+y.range()) / dur; //divide by range because it must be specific

            float coherence = 1f - Math.abs(xFreq - y.freq());
                    //TruthFunctions.freqSimilarity(xFreq, y.freq());

            float confFraction = Util.clamp(yConf / xConf, 0.5f, 2f);

            /** durations ago since the prediction was created */

            float v;
            if (coherence >= 0.5f) {

                //reward
                v = coherence * 2f * confFraction * headstart * strength;

                MetaGoal.learn(MetaGoal.Accurate, cause, v, nar);

            } else {
                //punish
                v = (1f - coherence) * 2f * confFraction / headstart * strength;

                MetaGoal.learn(MetaGoal.Inaccurate, cause, v, nar);
                if (deleteIfIncoherent)
                    y.delete();
                else
                    y.setPri(0); //drain priority  TODO maybe transfer it to nearby tasks?
            }

        });
    }

}
