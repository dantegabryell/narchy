package nars.task;

import jcog.data.list.FasterList;
import jcog.data.map.CompactArrayMap;
import jcog.pri.UnitPri;
import jcog.pri.op.PriMerge;
import jcog.util.ArrayUtils;
import nars.Param;
import nars.Task;
import nars.control.CauseMerge;
import nars.task.util.TaskException;
import nars.term.Term;
import nars.truth.Stamp;
import nars.truth.Truth;
import nars.truth.Truthed;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static nars.Op.BELIEF;
import static nars.Op.GOAL;

/**
 * generic immutable Task implementation,
 * with mutable cause[] and initially empty meta table
 */
public class NALTask extends UnitPri implements Task {

    private final Term term;
    private final Truth truth;
    public final byte punc;
    private final int hash;
    private final long creation, start, end;
    /*@Stable*/ private final long[] stamp;
    private volatile short[] cause = ArrayUtils.EMPTY_SHORT_ARRAY;

    private volatile boolean cyclic;

    public NALTask(Term term, byte punc, @Nullable Truthed truth, long creation, long start, long end, long[] stamp) throws TaskException {
        super();

        if (!term.op().taskable)
            throw new TaskException(term, "invalid term: " + term);

        if (truth == null ^ (!((punc == BELIEF) || (punc == GOAL))))
            throw new TaskException(term, "null truth");

//        if (truth!=null && truth.conf() < Param.TRUTH_EPSILON)
//            throw new Truth.TruthException("evidence underflow: conf=", truth.conf());

        if (Param.DEBUG) {
            if (!Stamp.validStamp(stamp))
                throw new TaskException(term, "invalid stamp: " + Arrays.toString(stamp));
        }

        if ((start == ETERNAL && end != ETERNAL) ||
                (start > end) ||
                (start == TIMELESS) || (end == TIMELESS)
        )
            throw new RuntimeException("start=" + start + ", end=" + end + " is invalid task occurrence time");

        if (Param.DEBUG_EXTRA)
            Task.taskConceptTerm(term, punc, false);

        this.term = term;
        this.truth = truth != null ? /*Truth.the*/(truth.truth()) : null;
        this.punc = punc;
        this.start = start;
        this.end = end;
        this.stamp = stamp;

        this.hash = hashCalculate();

        this.creation = creation;

    }


    protected int hashCalculate() {
        return Task.hash(
                term,
                truth,
                punc,
                start, end, stamp);
    }


    @Override
    public final int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object that) {
        return Task.equal(this, that);
    }


    @Override
    public boolean isCyclic() {
        return cyclic;
    }

    @Override
    public void setCyclic(boolean c) {
        this.cyclic = c;
    }

    /**
     * combine cause: should be called in all Task bags and belief tables on merge
     */
    public Task priCauseMerge(Task incoming, CauseMerge merge) {
        if (incoming == this) return this;

        /*(incoming.isInput() ? PriMerge.replace : Param.taskEquivalentMerge).*/
        PriMerge.max.merge(this, incoming);

        return causeMerge(incoming.cause(), merge);
    }

    public final Task priCauseMerge(Task incoming) {
        return priCauseMerge(incoming, CauseMerge.Append);
    }

    public Task causeMerge(short[] c, CauseMerge merge) {
        this.cause = merge.merge(cause(), c, Param.causeCapacity.intValue());
        return this;
    }

    @Override
    public float freq(long start, long end) {
        return truth.freq();
    }

    @Nullable
    @Override
    public final Truth truth() {
        return truth;
    }

    @Override
    public byte punc() {
        return punc;
    }

    @Override
    public long creation() {
        return creation;
    }

    @Override
    public Term term() {
        return term;
    }

    @Override
    public long start() {
        return start;
    }

    @Override
    public long end() {
        return end;
    }

    @Override
    public long[] stamp() {
        return stamp;
    }

    @Override
    public short[] cause() {
        return cause;
    }

    /**
     * set the cause[]
     */
    public NALTask cause(short[] cause) {
        this.cause = cause;
        return this;
    }


    @Override
    public String toString() {
        return appendTo(null).toString();
    }

    @Override
    public double coord(int dimension, boolean maxOrMin) {
        switch (dimension) {
            case 0:
                return maxOrMin ? end() : start();
            case 1:
                return freq();
            case 2:
                return conf();
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public final double range(int dim) {
        switch (dim) {
            case 0:
                return end() - start();
            case 1:
            case 2:
                return 0;
            default:
                throw new UnsupportedOperationException();
        }
    }

    /**
     * extended: with meta table
     */
    public static class NALTaskX extends NALTask implements jcog.data.map.MetaMap {

        private final CompactArrayMap<String, Object> meta = new CompactArrayMap<>();

        NALTaskX(Term term, byte punc, @Nullable Truthed truth, long creation, long start, long end, long[] stamp) throws TaskException {
            super(term, punc, truth, creation, start, end, stamp);
        }

        @Override
        public @Nullable List log(boolean createIfMissing) {
            if (createIfMissing)
                return meta("!", (x) -> new FasterList(1));
            else
                return meta("!");
        }

        @Override
        public <X> X meta(String key, Function<String, Object> valueIfAbsent) {
            CompactArrayMap<String, Object> m = this.meta;
            return m != null ? (X) m.computeIfAbsent(key, valueIfAbsent) : null;
        }

        @Override
        public Object meta(String key, Object value) {
            CompactArrayMap<String, Object> m = this.meta;
            if (m != null) m.put(key, value);
            return value;
        }

        @Override
        public <X> X meta(String key) {
            CompactArrayMap<String, Object> m = this.meta;
            return m != null ? (X) m.get(key) : null;
        }
    }


}
