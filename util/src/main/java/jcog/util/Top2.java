package jcog.util;

import com.google.common.collect.Lists;
import org.eclipse.collections.api.block.function.primitive.FloatFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * @see TopKSelector (guava)
 */
public final class Top2<T> implements Consumer<T> {

    private final FloatFunction<T> rank;
    public T a, b;
    public float aa = Float.NEGATIVE_INFINITY, bb = Float.NEGATIVE_INFINITY;

    public Top2(FloatFunction<T> rank) {
        this.rank = rank;
    }

    public Top2(FloatFunction<T> rank, Iterable<T> from) {
        this(rank);
        from.forEach(this);
    }

    @Override
    public void accept(T x) {
        float xx = rank.floatValueOf(x);
        if (xx > aa) {
            b = a;
            bb = aa; //shift down
            a = x;
            aa = xx;
        } else if (xx > bb) {
            b = x;
            bb = xx;
        }
    }

    @NotNull
    public List<T> toList() {
        if (a != null && b != null) {
            return Lists.newArrayList(a, b);
        } else if (b == null && a!=null) {
            return Collections.singletonList(a);
        } else {
            return Collections.emptyList();
        }
    }

    public Top2<T> of(Iterator<T> iterator) {
        iterator.forEachRemaining(this);
        return this;
    }

}
