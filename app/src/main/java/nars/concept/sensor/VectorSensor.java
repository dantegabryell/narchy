package nars.concept.sensor;

import com.google.common.collect.Iterables;
import nars.$;
import nars.NAR;
import nars.attention.AttVectorNode;
import nars.concept.NodeConcept;
import nars.control.channel.CauseChannel;
import nars.table.dynamic.SeriesBeliefTable;
import nars.task.ITask;
import nars.term.Termed;
import nars.truth.Truth;
import org.eclipse.collections.api.block.function.primitive.FloatFloatToObjectFunction;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static nars.Op.BELIEF;

/**
 * base class for a group of concepts representing a sensor 'vector'
 */
abstract public class VectorSensor extends AbstractSensor implements Iterable<Signal> {


    public final CauseChannel<ITask> in;

    public final AttVectorNode attn;


    @Override
    public Iterable<Termed> components() {
        return Iterables.transform(this, NodeConcept::term);
    }

//    protected VectorSensor(@Nullable FloatSupplier input, @Nullable Term id, NAR nar) {
//        this(input, id, (prev, next) -> next==next ? $.t(Util.unitize(next), nar.confDefault(BELIEF)) : null, nar);
//    }

    protected VectorSensor(NAR nar) {
        super(nar);

        attn = new AttVectorNode(this, this);

        this.in = newChannel(nar);
    }

    protected CauseChannel<ITask> newChannel(NAR nar) {
        return nar.newChannel(id != null ? id : this);
    }

    /**
     * best to override
     */
    public int size() {
        return Iterables.size(this);
    }



    @Override
    public void update(long last, long now, long next, NAR nar) {

        in.input(updateSensor(last, now, nar));
    }

    public final Stream<SeriesBeliefTable.SeriesRemember> updateSensor(long last, long now, NAR nar) {
        float confDefault = nar.confDefault(BELIEF);
        float min = nar.confMin.floatValue();

        return updateSensor(last, now, (p, n) -> {
            float c = confDefault;// * Math.abs(n - 0.5f) * 2f;
            return c > min ? $.t(n, c) : null;
        });
    }

    protected final Stream<SeriesBeliefTable.SeriesRemember> updateSensor(long last, long now, FloatFloatToObjectFunction<Truth> truther) {
        return StreamSupport.stream(spliterator(), false).map(s -> {
            SeriesBeliefTable.SeriesRemember r = s.update(last, now, truther, nar);
            if (r!=null) {
                attn.ensure(r.input, attn.elementPri(nar));
            }
            return r;
        });
    }

}
