package nars.op;

import jcog.Paper;
import jcog.Util;
import jcog.list.FasterList;
import nars.$;
import nars.NAR;
import nars.Op;
import nars.Task;
import nars.bag.leak.LeakBack;
import nars.term.Term;
import nars.term.Variable;
import nars.term.anon.Anom;
import nars.term.anon.Anon;
import nars.term.atom.Int;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.primitive.IntObjectPair;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

import static nars.Op.*;
import static nars.time.Tense.DTERNAL;

/**
 * introduces arithmetic relationships between differing numeric subterms
 * responsible for showing the reasoner mathematical relations between
 * numbers appearing in compound terms.
 *
 * TODO
 *      greater/less than comparisons
 *
 *
 */
@Paper
public class ArithmeticIntroduction extends LeakBack {

    public static Term apply(Term x) {
        return apply(x, true);
    }

    public static Term apply(Term x, boolean eternal) {
        return apply(x, null, eternal);
    }

    public static Term apply(Term x, @Nullable Anon anon, boolean eternal) {
        if (anon == null && !x.hasAny(INT) || x.complexity() < 3)
            return x;

        //find all unique integer subterms
        IntHashSet ints = new IntHashSet(4);
        x.recurseTerms(t->t.hasAny(Op.INT), t -> {
            if (t instanceof Anom) {
                t = anon.get(t);
            }
            if (t instanceof Int) {
                ints.add(((Int) t).id);
            }
            return true;
        }, x);

        int ui = ints.size();
        if (ui <= 1)
            return x; //nothing to do

        int[] ii = ints.toSortedArray();  //increasing so that relational comparisons can assume that 'a' < 'b'

        //potential mods to select from
        //FasterList<Supplier<Term[]>> mods = new FasterList(1);
        IntObjectHashMap<List<Supplier<Term[]>>> mods = new IntObjectHashMap(ii.length);

        Variable v =
                $.varDep("x");
                //$.varIndep("x");

        //test arithmetic relationships
        for (int a = 0; a < ui; a++) {
            int ia = ii[a];
            for (int b = a + 1; b < ui; b++) {
                int ib = ii[b];
                assert ib > ia;

                if (ib - ia < ia && ia!=0) {

                    mods.getIfAbsentPut(ia, FasterList::new).add(()-> new Term[]{
                            Int.the(ib), $.func(MathFunc.add, v, $.the(ib - ia))
                    });

                    mods.getIfAbsentPut(ib, FasterList::new).add(()-> new Term[]{
                            Int.the(ia), $.func(MathFunc.add, v, $.the(ia - ib))
                    });

                } else if (ia!=0 && ia!=1 && ib!=0 && ib!=1 && Util.equals(ib/ia, (float)ib /ia, Float.MIN_NORMAL)) {

                    mods.getIfAbsentPut(ia, FasterList::new).add(()-> new Term[]{
                            Int.the(ib), $.func(MathFunc.mul, v, $.the(ib/ia))
                    });
                } else if (ia == -ib) {
                    //negation (x * -1)
                    mods.getIfAbsentPut(ia, FasterList::new).add(()-> new Term[]{
                            Int.the(ib), $.func(MathFunc.mul, v, $.the(-1))
                    });
                    mods.getIfAbsentPut(ib, FasterList::new).add(()-> new Term[]{
                            Int.the(ia), $.func(MathFunc.mul, v, $.the(-1))
                    });
                }

            }
        }
        if (mods.isEmpty())
            return x;

        //TODO fair select randomly if multiple of the same length

        RichIterable<IntObjectPair<List<Supplier<Term[]>>>> mkv = mods.keyValuesView();


        int ms = mkv.maxBy(e -> e.getTwo().size()).getTwo().size();
        mkv.reject(e->e.getTwo().size() < ms);

        //convention: choose lowest base
        MutableList<IntObjectPair<List<Supplier<Term[]>>>> mmm = mkv.toSortedListBy(IntObjectPair::getOne);

        IntObjectPair<List<Supplier<Term[]>>> m = mmm.get(0);
        int base = m.getOne();
        Term baseTerm = Int.the(base);
        if (anon!=null)
            baseTerm = anon.put(baseTerm);

        Term yy = x.replace(baseTerm, v);

        for (Supplier<Term[]> s : m.getTwo()) {
            Term[] mm = s.get();
            if (anon!=null)
                mm[0] = anon.put(mm[0]);
            yy = yy.replace(mm[0], mm[1]);
        }

        Term y =
                CONJ.the(yy, eternal ? DTERNAL : 0, SIM.the(baseTerm, v));
                //IMPL.the(SIM.the(baseTerm, v), yy);
                //IMPL.the(yy, SIM.the(baseTerm, v));

        if (y.op()!=CONJ) {
        //if (y.op()!=IMPL) {
            return null; //something happened
        }

        if (x.isNormalized()) {
            y = y.normalize();
        }
        return y;
    }

    public static final Logger logger = LoggerFactory.getLogger(ArithmeticIntroduction.class);


    public ArithmeticIntroduction(int taskCapacity, NAR n) {
        super(taskCapacity, n);
    }

    @Override
    protected boolean preFilter(Task next) {
        return next.term().hasAny(Op.INT);
    }
    @Override
    protected float pri(Task t) {
        float p = super.pri(t);
        int intTerms = t.term().intifyRecurse((n,sub)->sub.op()==INT ? n+1 : n, 0);
        assert(intTerms > 0);
        if (intTerms < 2)
            return Float.NaN;

        return p * (1 - 0.5f/(intTerms-1));
    }
    @Override
    protected float leak(Task xx) {
        Term x = xx.term();
        Term y = apply(x, xx.isEternal());
        if (y!=null && !y.equals(x) && y.op().conceptualizable) {
            Task yy = Task.clone(xx, y);
            //TODO apply a pri discount if size grow
            if (yy!=null) {
                input(yy);
                return 1;
            }
        } else {
//            if (Param.DEBUG)
//                logger.warn("fail: task={} result=", xx, y);
        }

        return 0;
    }



}
