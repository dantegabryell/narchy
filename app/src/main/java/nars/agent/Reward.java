package nars.agent;

import jcog.Util;
import jcog.math.FloatSupplier;
import nars.$;
import nars.NAR;
import nars.Task;
import nars.attention.AttNode;
import nars.attention.AttVectorNode;
import nars.concept.Concept;
import nars.concept.sensor.Signal;
import nars.control.channel.CauseChannel;
import nars.op.mental.Inperience;
import nars.table.eternal.DefaultOnlyEternalTable;
import nars.task.ITask;
import nars.task.NALTask;
import nars.term.Term;
import nars.term.Termed;
import nars.truth.PreciseTruth;
import nars.truth.Truth;
import org.eclipse.collections.api.block.function.primitive.FloatFloatToObjectFunction;

import java.util.List;

import static nars.Op.BELIEF;
import static nars.Op.GOAL;
import static nars.time.Tense.ETERNAL;

public abstract class Reward implements Termed, Iterable<Signal> {

    //public final FloatRange motivation = new FloatRange(1f, 0, 1f);

    protected final NAgent agent;
    private final FloatSupplier rewardFunc;

    protected transient volatile float reward = Float.NaN;

    protected final CauseChannel<ITask> in;


    final AttNode attn;

    public Reward(NAgent a, FloatSupplier r) {
    //TODO
    //public Reward(NAgent a, FloatSupplier r, float confFactor) {
        this.agent = a;
        this.rewardFunc = r;

        this.attn = new AttNode(this);

        in = a.nar().newChannel(this);

    }

    public final NAR nar() { return agent.nar(); }

    public final void update(long prev, long now, long next) {
        reward = rewardFunc.asFloat();
        updateReward(prev, now, next);
    }

    abstract protected void updateReward(long prev, long now, long next);

    @Deprecated protected FloatFloatToObjectFunction<Truth> truther() {
        return (prev, next) -> (next == next) ?
                $.t(Util.unitize(next), nar().confDefault(BELIEF)) : null;
    }

    public float summary() {
        return reward;
    }

    public void setDefault(PreciseTruth t) {
        for (Concept c : this) {
            //TODO assert that it has no eternal tables already
            new DefaultOnlyEternalTable(c,t,nar());

        }

    }
    public void alwaysWantEternally(Term goal) {
        alwaysWantEternally(goal, nar().confDefault(GOAL));
    }

    public void alwaysWantEternally(Term goal, float conf) {
        Task t = new NALTask(goal, GOAL, $.t(1f, conf), nar().time(),
                ETERNAL, ETERNAL,
                nar().evidence()
                //Stamp.UNSTAMPED
        );

        Term at = term().equals(goal) ? $.func(Inperience.want, goal) : $.func(Inperience.want, this.term(), goal);
        AttNode a = new AttVectorNode(at, List.of(t)) {

            @Override
            public float elementPri(NAR nar) {
                return nar.priDefault(GOAL);
            }

            @Override
            public void update(NAR nar) {
                super.update(nar);
                ensure(t, Math.max(0, elementPri(nar) - t.priElseZero()));
                in.input(t);
            }
        };
        a.parent(attn);
    }

}
