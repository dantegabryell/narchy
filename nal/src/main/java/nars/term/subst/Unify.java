package nars.term.subst;

import jcog.Util;
import jcog.data.UnenforcedConcatSet;
import jcog.version.VersionMap;
import jcog.version.Versioned;
import jcog.version.Versioning;
import nars.$;
import nars.Op;
import nars.Param;
import nars.derive.constraint.MatchConstraint;
import nars.index.term.NewCompound;
import nars.index.term.TermIndex;
import nars.term.Term;
import nars.term.mutate.Termutator;
import nars.term.var.CommonVariable;
import nars.term.var.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static nars.Op.Null;
import static nars.Param.TTL_UNIFY;


/* recurses a pair of compound term tree's subterms
across a hierarchy of sequential and permutative fanouts
where valid matches are discovered, backtracked,
and collected until power is depleted.



https://github.com/rupertlssmith/lojix/blob/master/lojix/prolog/src/main/com/thesett/aima/logic/fol/prolog/PrologUnifier.java
https://github.com/rupertlssmith/lojix/blob/master/lojix/wam_prolog/src/main/com/thesett/aima/logic/fol/wam/compiler/WAMInstruction.java
see this code for a clear explanation of what a prolog unifier does.
this code does some additional things but shares a general structure with the lojix code which i just found now
So it can be useful for a more easy to understand rewrite of this class TODO


*/
public abstract class Unify extends Versioning implements Subst {

    protected final static Logger logger = LoggerFactory.getLogger(Unify.class);

    @NotNull
    public final Random random;

    @NotNull
    public final Op type;

    @NotNull
    public final Set<Termutator> termutes =
            new HashSet();
    //new LinkedHashSet();

    @NotNull
    public final Versioning<Term> versioning = this;

    @NotNull
    public final TermIndex terms;

    @NotNull
    public final VersionMap<Term, Term> xy;

    /**
     * temporal tolerance; if -1, then it is not tested
     */
    public int dur = -1;


    /**
     * free variables remaining unassigned, for counting
     */
    protected Versioned<Set<Term>> free;
    //public Versioned<Integer> freeCount;

    //protected final Set<NewCompound> matched = new HashSet();


    protected Unify(@Nullable Op type, Random random, int stackMax, int ttl) {
        this($.terms, type, random, stackMax, ttl);
    }


    /**
     * @param terms
     * @param type       if null, unifies any variable type.  if non-null, only unifies that type
     * @param random
     * @param versioning
     */
    protected Unify(TermIndex terms, @Nullable Op type, Random random, int stackMax, int initialTTL) {
        super(stackMax, initialTTL);

        this.terms = terms;

        this.random = random;
        this.type = type;

        xy = new ConstrainedVersionMap(versioning, Param.UnificationVariableCapInitial);
        this.free = new Versioned<>(versioning, 1);
        //this.freeCount = new Versioned<>(versioning, 8);

    }

    /**
     * spend an amount of TTL; returns whether it is still live
     */
    public final boolean use(int cost) {
        return ((ttl -= cost) > 0);
    }


    /**
     * called each time all variables are satisfied in a unique way
     *
     * @return whether to continue on any subsequent matches
     * @param match [variables][2] where index 0 = key, index 1 = value
     */
    public abstract void onMatch(Term[][] match);

    public final void tryMutate(Termutator[] chain, int next) {
        if (++next < chain.length) {
            if (use(Param.TTL_MUTATE))
                chain[next].mutate(this, chain, next);
        } else {
            tryMatch(); //end of chain
        }
    }

    @Override
    public void clear() {
        versioning.clear();
    }

    @Nullable
    @Override
    public Term xy(@NotNull Term t) {
        return xy.get(t);
    }


    public final Unify unifyAll(@NotNull Term x, @NotNull Term y) {
        return unify(x, y, true);
    }

    /**
     * unifies the next component, which can either be at the start (true, false), middle (false, false), or end (false, true)
     * of a matching context
     * <p>
     * setting finish=false allows matching in pieces before finishing
     */
    public Unify unify(@NotNull Term x, @NotNull Term y, boolean finish) {

        //accumulate any new free variables in this next matched term
        free.set(freeVariables(x)); //plus and not equals because this may continue from another unification!!!!!

        //assert (unassigned.isEmpty() ) : "non-purposeful unification";
        //this.freeCount.add( newFree.size() );

        if (unify(x, y)) {
            if (finish) {
                tryMatches();
            }
        }

        return this;
    }

    /**
     * computes a lazy set with the new free variables added by the incoming term, to continue
     * from a previous partial unification if necessary.
     */
    Set<Term> freeVariables(@NotNull Term x) {
        Set<Term> prevFree = free.get();
        Set<Term> nextFree = prevFree != null ? x.varsUnique(type, prevFree) : x.varsUnique(type);
        return UnenforcedConcatSet.the(prevFree, nextFree);
    }


    public void tryMatches() {
        int ts = termutes.size();
        if (ts > 0) {

            //TODO use Termutator[] not List
            Termutator[] t = termutes.toArray(new Termutator[ts]);

            termutes.clear();

            //shuffle the ordering of the termutes themselves
            if (ts > 1)
                Util.shuffle(t, random);

            tryMutate(t, -1); //start combinatorial recurse

        } else {
            tryMatch(); //go directly to conclusion
        }

//        if (matched.size()>1)
//            System.out.println(matched);

//        matched.clear();

    }

    private void tryMatch() {


//        if (freeCount.get() > 0) {
//            //quick test for no assignments
//            return;
//        }

        //filter incomplete matches by detecting them here
        //TODO use a counter to measure this instead of checking all the time
//        Iterator<Map.Entry<Term, Versioned<Term>>> ee = xy.map.entrySet().iterator();
//        while (ee.hasNext()) {
//            Map.Entry<Term, Versioned<Term>> e = ee.next();
//            Versioned<Term> v = e.getValue();
//            if ((v == null || v.get() == null) && matchType(e.getKey()))
//                return;
//        }


        Set<Term> free = this.free.get();
        Term[][] match = new Term[free.size()][];
        int m = 0;
        for (Term x : free) {
            Term y = xy(x);
            if (y == null)
                return;
            match[m++] = new Term[] { x, y };
        }
        Arrays.sort(match, matchElementComparator); //sort by key

//        if (!matched.add(((ConstrainedVersionMap)xy).snapshot()))
//            return; //already seen

        onMatch(match);


    }

    final static Comparator<Term[]> matchElementComparator = Comparator.comparing(v -> v[0]);


    @Override
    public String toString() {
        return xy + "@" + versioning.ttl;
    }

    @Override
    public boolean put(@NotNull Unify m) {
        return m.xy.forEachVersioned(this::putXY);
    }


    public final boolean unify(@NotNull Term x, @NotNull Term y) {
        return use(TTL_UNIFY) && x.unify(y, this);
    }

    /**
     * whether the term is assignable
     */
    public final boolean matchType(@NotNull Term y) {
        return matchType(y.op());
    }

    /**
     * whether the op is assignable
     */
    public final boolean matchType(@NotNull Op oy) {
        Op t = this.type;
        return t == null ?
                oy.var : //any variable
                oy == t; //the specified type
    }


    @Nullable
    public Term resolve(@NotNull Term x) {
        return transform(x);
    }


    public boolean putCommon(@NotNull Variable/* var */ x, @NotNull Variable y) {
        @NotNull Term common = CommonVariable.common((Variable) x, (Variable) y);
        return (putXY(x, common) && putXY(y, common)
                //&& putYX(y, common) //&& putYX(x,common)
        );
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException("slow");
    }


    /**
     * returns true if the assignment was allowed, false otherwise
     */
    public final boolean putXY(@NotNull Term x /* usually a Variable */, @NotNull Term y /* value */) {
        Term x2 = xy(x);
        if (x2 != null) {
            return unify(x2, y);
        } else {
            return xy.tryPut(x, y);
        }
    }

    public final int now() {
        return versioning.size();
    }

//    public final boolean revert(int then) {
//        versioning.revert(then);
//        return live();
//    }

    private class ConstrainedVersionMap extends VersionMap<Term, Term> {
        public ConstrainedVersionMap(@NotNull Versioning versioning, int mapCap) {
            super(versioning, mapCap, 1);
        }

//        @Nullable
//        @Override
//        public Term remove(Object key) {
//            Versioned<Term> x = map.remove(key);
//            if (x == null)
//                return null;
//            ConstrainedVersionedTerm cx = (ConstrainedVersionedTerm)x;
//            if (((ConstrainedVersionedTerm) x).forMatchedType)
//                assigned--;
//
//            return x.get();
//        }

//        @Override
//        public boolean tryPut(Term key, @NotNull Term value) {
//            int beforePut = matchType(key) ? now() : Integer.MAX_VALUE;
//            if (super.tryPut(key, value)) {
//                if (now() > beforePut) { //detects change and not just an equals() match
////                    int nextUnassigned = freeCount.get() - 1;
////
////                    if (nextUnassigned < 0)
////                        return false;
////                    //assert(nextUnassigned >= 0): "underflow";
////
////                    freeCount.add(nextUnassigned);
//                }
//                return true;
//            }
//            return false;
//        }

        @NotNull
        @Override
        public Versioned newEntry(Term key) {
            return new ConstrainedVersionedTerm(matchType(key));
        }

        public NewCompound snapshot() {
            NewCompound s = new NewCompound(null, xy.map.size() * 2);
            xy.forEach((x, y) -> {
                s.add(x);
                if (y == null)
                    y = Null; //HACK this should have been handled by the variable count
                s.add(y);
            });
            return s.commit();
        }
    }

    final class ConstrainedVersionedTerm extends Versioned<Term> {

        /**
         * whether this is a for a matched variable type, so when popped we can decrement the assigned count
         */
        private final boolean forMatchedType;

        /**
         * lazyily constructed
         */
        Versioned<MatchConstraint> constraints = null;

//        /**
//         * divide constraints into two classes: fast and slow,
//         * fast ieally are checked first
//         */
//        Versioned<MatchConstraint> fastConstraints = null;

        ConstrainedVersionedTerm(boolean forMatchedType) {
            super(versioning, 1);
            this.forMatchedType = forMatchedType;
        }

//        @Override
//        public void pop() {
//            if (forMatchedType) {
//                if (get() != null) {
//                    freeCount.add(freeCount.get()+1); //relase assigned variable
//                }
//            }
//
//            super.pop();
//        }

        @Nullable
        @Override
        public Versioned<Term> set(@NotNull Term next) {
            return valid(next) ? super.set(next) : null;
        }

        private boolean valid(@NotNull Term x) {
            if (constraints != null) {
                int s = constraints.size();
                for (int i = 0; i < s; i++) {
                    MatchConstraint cc = constraints.get(i);
                    if (cc == null) /* why null? */
                        throw new NullPointerException();
                    //break;
                    if (cc.invalid(x, Unify.this))
                        return false;
                }
            }
            return true;
        }

        public boolean constrain(MatchConstraint m) {

            if (constraints == null)
                constraints = new Versioned(versioning, 0);

            return constraints.set(m) != null;
        }


//
//        boolean isFast(MatchConstraint m) {
//            return !(m instanceof CommonalityConstraint);
//        }

//        Versioned newConstraints() {
//            return new Versioned(versioning, MaxMatchConstraintsPerVariable);
//        }
    }


    public boolean constrain(MatchConstraint... cc) {
        for (MatchConstraint m : cc) {
            Versioned<Term> v = xy.getOrCreateIfAbsent(m.target);
            if (!((ConstrainedVersionedTerm) v).constrain(m))
                return false;
        }
        return true;
    }

}


