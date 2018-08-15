package nars.term;

import jcog.data.list.FasterList;
import jcog.data.set.ArrayHashSet;
import jcog.version.VersionMap;
import jcog.version.Versioning;
import nars.NAR;
import nars.Op;
import nars.Param;
import nars.subterm.Subterms;
import nars.term.atom.Atomic;
import nars.term.util.transform.DirectTermTransform;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static nars.Op.ATOM;
import static nars.Op.Null;

public class Evaluation {

    private final Evaluables operations;
    private final Predicate<Term> each;
    private final Term term;
    private final Term x;

    private List<Iterable<Predicate<VersionMap<Term, Term>>>> termutator = null;

    private Versioning v;

    private VersionMap<Term, Term> subst;

    ///ArrayHashSet<Term> seen = new ArrayHashSet<>(1); //deduplicator,TODO use different deduplication strategies including lossy ones (Bagutator)

    @Nullable public static Evaluation eval(Term x, NAR nar, Predicate<Term> each) {
        return eval(x, nar::functor, each);
    }

    @Nullable public static Evaluation eval(Term x, Function<Term, Functor> resolver, Predicate<Term> each) {
        if (possiblyNeedsEval(x)) {
            Evaluables y = new Evaluables(resolver).discover(x);
            if (!y.isEmpty())
                return new Evaluation(x, y, each);
        }

        each.test(x); //didnt need evaluating, just input
        return null;
    }

    private Evaluation(Term x, Evaluables operations, Predicate<Term> each) {
        this.term = x;
        this.each = each;
        this.operations = operations.sortDecreasingVolume();
        this.x = x;

        ensureReady();

        int freeVariables = operations.vars.size();

        //iterate until stable
        Term y = x, prev;
        int vEnd, vStart;
        main: do {
            prev = y;
            ListIterator<Term> ii = this.operations.listIterator();
            vStart = v.now();
            while (ii.hasNext()) {
                Term a = ii.next();
                Term func = a.sub(1);
                Subterms args = a.sub(0).subterms();
                Term z = ((BiFunction<Evaluation, Subterms, Term>) func).apply(this, args);
                if (z!=a && z!=null) {

                    if (z == Null) {
                        y = z; //poisoned
                        break main;
                    }

                    boolean removeEntry = false;
                    if (Functor.isFunc(z)) {

                        //still a functor, but different. update it

                        //run the functor resolver for any new functor terms which may have appeared
                        Term zf = Functor.func(z);
                        if (!(zf instanceof Functor)) {
                            //try resolving
                            Term zz = operations.transform(z);
                            if (zz == z) {
                                //not evaluable.
                                removeEntry = true;
                            } else {
                                z = zz;
                            }
                        }


                    } else {
                        removeEntry = true;
                    }

                    //TODO
                    //forward substitute any terms that may contain this term.
                    //the dependencies of a given term can be precomputed in the operation discovery process in a simple DAG


                    y = y.replace(a, z);

                    if (!y.op().conceptualizable)
                        break main; //reached a terminal state

                    if (removeEntry) {

                        ii.remove();
                        if (operations.isEmpty())
                            break main;

                    } else {
                        ii.set(z);

                    }

                }
            }
            vEnd = v.now();
            if (vEnd!=vStart && subst.size()>=freeVariables && operations.vars.allSatisfy(subst::containsKey)) {
                break; //all variables have been specified
            }
        } while ((y != prev) || (vEnd != vStart));

        assert(y!=null);

        y = y.replace(subst);

        //if termutators, collect all results. otherwise 'cur' is the only result to return
        /*if (!termutator.isEmpty()) {
            throw new TODO();
        } else */{
            each.test(y);
        }

    }



    /** returns first result. returns null if no solutions */
    public static Term solveFirst(Term x, NAR n) {
        Term[] y = new Term[1];
        Evaluation.eval(x, n, (what) -> {
            y[0] = what;
            return false;
        });
        return y[0];
    }




    /** gathers results from one truth set, ex: +1 (true) */
    public static Set<Term> solveAll(Term x, NAR n) {
        final Set[] yy = {null};
        Evaluation.eval(x, n, (y) -> {
            if (yy[0] == null) {
                yy[0] = new UnifiedSet<>(1);
            }
            yy[0].add(y);
            return true;
        });

        Set z = yy[0];
        return z == null ? Set.of(x) : yy[0];
    }


    private void ensureReady() {
        if (v == null) {
            v = new Versioning<>(Param.UnificationStackMax, Param.EVALUATION_TTL);
            subst = new VersionMap<>(v);
            termutator = new FasterList<>(1);
        }
    }








//    private Term _eval(Term c) {
//        Op o = c.op();
//        if (o == NEG) {
//            Term xu = c.unneg();
//            Term yu = _eval(xu);
//            if (xu != yu)
//                return yu.neg();
//            return c; //unchanged
//        }
//
//        /*if (hasAll(opBits))*/
//
//        /*if (subterms().hasAll(opBits))*/
//
//        Subterms uu = c.subterms();
//        Term[] xy = null;
//
//
//        int ellipsisAdds = 0, ellipsisRemoves = 0;
//
//        boolean evalConjOrImpl = !wrapBool && (o == CONJ || o == IMPL);
//        int polarity = 0;
//
//        for (int i = 0, n = uu.subs(); i < n; i++) {
//            Term xi = xy != null ? xy[i] : uu.sub(i);
//            Term yi = possiblyNeedsEval(xi) ? _eval(xi) : xi;
//            if (yi == Null)
//                return Null;
//            if (evalConjOrImpl && yi instanceof Bool && (i == 0 /* impl subj only */ || o == CONJ)) {
//                if (yi == True) {
//                    polarity = +1;
//                } else /*if (yi == False)*/ {
//                    if (o == IMPL)
//                        return Null;
//                    polarity = -1;
//                    break;
//                }
//            } else {
//                if (xi != yi) {
//                    yi = boolWrap(xi, yi);
//                }
//            }
//
//            if (xi != yi) {
//                if (yi == null) {
//
//                } else {
//
//
//                    if (yi instanceof EllipsisMatch) {
//                        int ys = yi.subs();
//                        ellipsisAdds += ys;
//                        ellipsisRemoves++;
//                    }
//
//                    if (xi != yi) {
//                        if (xy == null) {
//                            xy = c.arrayClone();
//                        }
//                        xy[i] = yi;
//                    }
//                }
//            }
//        }
//
//        if (polarity != 0) {
//            if (o == CONJ) {
//                if (polarity < 0) {
//                    return False; //short circuit
//                }
//            } else if (o == IMPL) {
//                assert (polarity > 0); //False and Null already handled
//                return xy[1];
//            }
//        }
//
//
//        Term u;
//        if (xy != null) {
//            if (ellipsisAdds > 0) {
//
//                xy = EllipsisMatch.flatten(xy, ellipsisAdds, ellipsisRemoves);
//            }
//
//            u = o.the(c.dt(), xy);
//            o = u.op();
//            uu = u.subterms();
//        } else {
//            u = c;
//        }
//
//
//        if (o == INH && uu.hasAll(Op.FuncInnerBits)) {
//            Term pred, subj;
//            if ((pred = uu.sub(1)) instanceof Functor && (subj = uu.sub(0)).op() == PROD) {
//
//                Term v = ((BiFunction<Evaluation, Subterms, Term>) pred).apply(this, subj.subterms());
//                if (v != null) {
//                    if (v instanceof AbstractPred) {
//                        u = $.the(((Predicate) v).test(null));
//                    } else {
//                        u = v;
//                    }
//                } /* else v == null, no change */
//            }
//        }
//
////        if (u != c && (u.equals(c) && u.getClass() == c.getClass()))
////            return c;
//
//        return u;
//    }

    private Evaluation clear() {
        if (v != null) {
            termutator.clear();
            v.reset();
            subst.clear();
        }
        return this;
    }

//    private boolean get(Term _x, Predicate<Term> each) {
//
//        Term x = eval(_x);
//
//        int np = procs();
//        if (np == 0)
//            return each.test(x); //done
//
//        Iterator<Predicate<VersionMap<Term, Term>>[]> pp;
//
//        Iterable[] aa = new Iterable[np];
//        for (int i = 0; i < np; i++)
//            aa[i] = ArrayIterator.iterable(termutator.get(i));
//
//        pp = new CartesianIterator<Predicate<VersionMap<Term, Term>>>(Predicate[]::new, aa);
//
//        int start = v.now();
//
//        nextPermute:
//        while (pp.hasNext()) {
//
//
//
//            v.revert(start);
//
//            Predicate<VersionMap<Term, Term>>[] n = pp.next();
//            assert (n.length > 0);
//
//            for (Predicate p : n) {
//                if (!p.test(subst)) {
//                    return each.test(False);
//                    //continue nextPermute;
//                }
//            }
//
//            Term y = x.replace(subst);
//            if (y == null)
//                continue;
//
//            Term z = eval(y);
//
//            int ps = procs();
//            if (z != null && np == ps && !each.test(z))
//                return false;
//
//            if (np < ps) {
//                int before = v.now();
//                if (!get(z, each))
//                    return false;
//                v.revert(before);
//            }
//
//        }
//
//
//        return true;
//    }
//


    /**
     * assign 1 variable
     * returns false if it could not be assigned (enabling callee fast-fail)
     */
    public boolean is(Term x, Term y) {
        return subst.tryPut(x, y);
    }

    /**
     * assign 2-variables at once.
     * returns false if it could not be assigned (enabling callee fast-fail)
     */
    public boolean is(Term x, Term xx, Term y, Term yy) {
        return subst.tryPut(x, xx) && subst.tryPut(y, yy);
    }

    /**
     * 2-ary AND
     */
    public Predicate<VersionMap<Term, Term>> assign(Term x, Term xx, Term y, Term yy) {
        return m -> m.tryPut(x, xx) && m.tryPut(y, yy);
    }


    /**
     * OR, forked
     */
    public void isAny(Collection<Predicate<VersionMap<Term, Term>>> r) {
        ensureReady();
        termutator.add(r);
    }

    public static Predicate<VersionMap<Term,Term>> assign(Term x, Term y) {
        return (subst) -> subst.tryPut(x, y);
    }




    private static boolean possiblyNeedsEval(Term x) {
        return x.hasAll(Op.FuncBits);
    }



    /** discovers functors within the provided term, or the term itself.
     * transformation results should not be interned, that is why DirectTermTransform used here */
    private static final class Evaluables extends ArrayHashSet<Term> implements DirectTermTransform {

        private final Function<Term, Functor> resolver;
        public final MutableSet<Variable> vars = new UnifiedSet(0);

        Evaluables(Function<Term, Functor> resolver) {
            this.resolver = resolver;
        }

        public Evaluables discover(Term x) {
            x.recurseTerms(s->s.hasAll(Op.FuncBits), xx->{
                if (!contains(xx)) {
                    if (Functor.isFunc(xx)) {
                        Term yy = this.transform(xx);
                        if (yy.sub(1) instanceof Functor) {
                            add(yy);
                        }
                    }
                }
                return true;
            }, null);
            return this;
        }


        @Override
        protected void addUnique(Term x) {
            super.addUnique(x);

            x.sub(0).recurseTerms((Termlike::hasVars), (s -> {
                if (s instanceof Variable)
                    vars.add((Variable)s);
                return true;
            }), null);
        }

        @Override
        public @Nullable Term transformAtomic(Atomic x) {
            if (x instanceof Functor) {
                return x;
            }

            if (x.op() == ATOM) {
                Functor f = resolver.apply(x);
                if (f != null) {
                    return f;
                }
            }
            return x;
        }

        public Evaluables sortDecreasingVolume() {
            if (size() > 1)
                ((FasterList<Term>)list).sortThisByInt(Termlike::volume);
            return this;
        }
    }
}
