package nars.term.control;

import jcog.Util;
import jcog.WTF;
import nars.$;
import nars.term.Term;
import org.eclipse.collections.api.block.function.primitive.FloatFunction;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static jcog.data.iterator.ArrayIterator.stream;

/** AND/while predicate chain */
abstract public class AND<X> extends AbstractPred<X> {

    private final float cost;

    protected AND(PREDICATE<X>... cond) {
        super(
                $.pFast(cond)
        );
        assert (cond.length >= 2) : "unnecessary use of AndCondition";
        this.cost = Util.sum((FloatFunction<PREDICATE>) PREDICATE::cost, cond);
    }

    public final static class ANDn<X> extends AND<X> {
        /*@Stable*/
        public final PREDICATE<X>[] cond;

        protected ANDn(PREDICATE<X>[] cond) {
            super(cond);
            this.cond = cond;
        }

        @Override
        public final boolean test(X m) {
            for (PREDICATE x : cond) {
                boolean b = x.test(m);
                if (!b)
                    return false;
            }
            return true;
        }


        public PREDICATE<X> first() {
            return Util.first(cond);
        }

        public PREDICATE<X> last() {
            return Util.last(cond);
        }

    }
    public final static class AND2<X> extends AND<X> {
        /*@Stable*/
        public final PREDICATE<X> a, b;

        protected AND2(PREDICATE<X> a, PREDICATE<X> b) {
            super(a, b);
            this.a = a; this.b = b;
        }

        @Override
        public final boolean test(X x) {
            return a.test(x) && b.test(x);
        }

        public PREDICATE<X> first() {
            return a;
        }

        public PREDICATE<X> last() {
            return b;
        }

    }







    public static @Nullable <D> PREDICATE<D> the(List<PREDICATE<D>> cond) {
        int s = cond.size();
        switch (s) {
            case 0: return null;
            case 1: return cond.get(0);
            default: return the(cond.toArray(new PREDICATE[s]));
        }
    }

    public static @Nullable <D> PREDICATE<D> the(Term... cond) {
        int s = cond.length;
        switch (s) {
            case 0: return null;
            case 1: return (PREDICATE<D>) cond[0];
            default:
                boolean needsFlat = false;
                for (Term c : cond) {
                    if (c instanceof AND) {
                        needsFlat = true;
                        break;
                    }
                }
                if (needsFlat || !(cond instanceof PREDICATE[])) {
                    cond = stream(cond).flatMap(
                            x -> x instanceof AND ?
                                    x.subStream() :
                                    Stream.of(x))
                    .toArray(PREDICATE[]::new);
                }


                switch (cond.length) {
                    case 0:
                    case 1:
                        throw new WTF();
                    case 2:
                        return new AND2<>((PREDICATE)cond[0], (PREDICATE)cond[1]);
                    default:
                        return new ANDn<>((PREDICATE[])cond);
                }

        }
    }

    abstract public PREDICATE<X> first();
    abstract public PREDICATE<X> last();


    @Override
    public final float cost() {
        return cost;
    }


    /** chase the last of the last of the last(...etc.) condition in any number of recursive AND's */
    public static PREDICATE last(PREDICATE b) {
        while (b instanceof AND)
            b = ((AND)b).last();
        return b;
    }

     /** chase the last of the first of the first (...etc.) condition in any number of recursive AND's */
    public static PREDICATE first(PREDICATE b) {
        while (b instanceof AND)
            b = ((AND)b).first();
        return b;
    }
    @Nullable public static PREDICATE first(AND b, Predicate<PREDICATE> test) {
        int s = b.subs();
        for (int i = 0; i < s; i++) {
            PREDICATE x = (PREDICATE) b.sub(i);
            if (test.test(x))
                return x;
        }
        return null;
    }

    /** recursive */
    @Override public PREDICATE<X> transform(Function<PREDICATE<X>, PREDICATE<X>> f) {
        //return transform((Function)f, f);
        throw new UnsupportedOperationException();
    }

//    public PREDICATE<D> transform(Function<AND<D>, PREDICATE<D>> outer, @Nullable Function<PREDICATE<D>, PREDICATE<D>> f) {
//        PREDICATE[] yy = transformedConditions(f);
//        PREDICATE<D> z = yy != cond ? AND.the(yy) : this;
//        if (z instanceof AND)
//            return outer.apply((AND)z);
//        else
//            return z;
//    }

//    public PREDICATE[] transformedConditions(@Nullable Function<PREDICATE<D>, PREDICATE<D>> f) {
//        if (f == null)
//            return cond;
//
//        final boolean[] changed = {false};
//        PREDICATE[] yy = Util.map(x -> {
//            PREDICATE<D> y = x.transform(f);
//            if (y != x)
//                changed[0] = true;
//            return y;
//        }, new PREDICATE[cond.length], cond);
//        if (!changed[0])
//            return cond;
//        else
//            return yy;
//    }


//    public @Nullable PREDICATE<D> without(PREDICATE<D> condition) {
//        PREDICATE[] x = ArrayUtils.remove(cond, PREDICATE[]::new, ArrayUtils.indexOf(cond, condition));
//        assert(x.length != cond.length);
//        return AND.the(x);
//    }



}
