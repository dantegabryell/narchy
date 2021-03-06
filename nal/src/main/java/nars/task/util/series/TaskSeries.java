package nars.task.util.series;

import jcog.sort.TopN;
import nars.Task;
import nars.task.util.Answer;
import nars.truth.dynamic.DynStampTruth;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * fixed size buffer of Ts
 */
public interface TaskSeries<T extends Task> {



    @Nullable
    default DynStampTruth truth(long start, long end, Predicate<Task> filter) {

        int size = size();
        if (size == 0)
            return null;

        int limit = Answer.TASK_LIMIT_DEFAULT;

        DynStampTruth d = new DynStampTruth(Math.min(size, limit));

        TopN<Task> inner = TopN.pooled(Answer.topTasks, Math.min(size, limit), filter != null ?
                (t) -> filter.test(t) ? -t.minTimeTo(start, end) : Float.NaN
                :
                (t) -> -t.minTimeTo(start, end), Task[]::new);

        try {

//        TopN<Task> inner = new TopN<>(new Task[Math.min(size, limit)],
//                //this assumes they are all of the same evidence which is not true if the ranges differ
//                filter!=null ?
//                    (t, min) -> filter.test(t) ? -t.minTimeTo(start, end) : Float.NaN
//                    :
//                    (t, min) -> -t.minTimeTo(start, end)
//                //TruthIntegration.eviInteg(t, start, end, 1) //TODO this may be better as a double value comparison, long -> float could be lossy
//        );

            forEach(start, end, true, inner::add);


            int l = inner.size();
            if (l > 0) {
                Object[] ii = inner.items;
                int i;
                for (i = 0; i < l; i++)
                    d.add((Task) ii[i]);

                return d;
            }

            return null;
        } finally {
            TopN.unpool(Answer.topTasks, inner);
        }

    }

    int size();

    default void forEach(long minT, long maxT, boolean exactRange, Consumer<? super T> x) {
        if (!isEmpty()) {
//            if (minT == ETERNAL) {
//                T l = last();
//                if(x!=null)
//                    x.accept(l);
//                return;
//            }

            whileEach(minT, maxT, exactRange, (t) -> {
                x.accept(t);
                return true;
            });
        }
    }

    /**
     * returns false if the predicate ever returns false; otherwise returns true even if empty.  this allows it to be chained recursively to other such iterators
     */
    boolean whileEach(long minT, long maxT, boolean exactRange, Predicate<? super T> x);

    void clear();

    Stream<T> stream();



    void forEach(Consumer<? super T> action);

    /** returns Tense.TIMELESS (Long.MAX_VALUE) if empty */
    long start();

    /** returns Tense.TIMELESS (Long.MAX_VALUE) if empty */
    long end();

    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * returns false if there is some data which occurrs inside the given interval
     */
    default boolean isEmpty(long start, long end) {
        return whileEach(start, end, true, (x)->{
            if (x.intersects(start, end))
                return false; //found
            return true; //keep looking
        });
    }


    T first();
    T last();



}
