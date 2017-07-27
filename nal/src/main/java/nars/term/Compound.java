/*
 * CompoundTerm.java
 *
 * Copyright (C) 2008  Pei Wang
 *
 * This file is part of Open-NARS.
 *
 * Open-NARS is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Open-NARS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open-NARS.  If not, see <http://www.gnu.org/licenses/>.
 */
package nars.term;

import com.google.common.io.ByteArrayDataOutput;
import jcog.Util;
import jcog.data.sexpression.IPair;
import jcog.data.sexpression.Pair;
import nars.$;
import nars.IO;
import nars.Op;
import nars.index.term.NewCompound;
import nars.index.term.TermContext;
import nars.op.mental.AliasConcept;
import nars.term.atom.Atomic;
import nars.term.atom.Bool;
import nars.term.container.TermContainer;
import nars.term.subst.Unify;
import nars.term.transform.CompoundTransform;
import nars.term.transform.VariableNormalization;
import nars.term.var.Variable;
import org.eclipse.collections.api.list.primitive.ByteList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.primitive.ObjectLongPair;
import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.Collections.emptySet;
import static nars.Op.*;
import static nars.time.Tense.DTERNAL;
import static nars.time.Tense.XTERNAL;

/**
 * a compound term
 * TODO make this an interface extending Subterms
 */
public interface Compound extends Term, IPair, TermContainer {

    static boolean equals(@NotNull Term a, @NotNull Object b) {

        if (a == b)
            return true;

        if (!(b instanceof Term) || a.hashCode() != b.hashCode())
            return false;

        Term bb = (Term) b;

        return
                (a.opX() == bb.opX())
                &&
                (a.subterms().equals(bb.subterms()))
                &&
                (a.dt() == bb.dt())
                ;

        //subterm sharing:
//        if (as != cs) {
//            if (!as.equivalent(cs)) {
//                return false;
//            } else {
//                //share the subterms vector
//                if (cthat instanceof GenericCompound) {
//                    this.subterms = cs; //HACK cast sucks
//                }
//            }
//        }
    }

    @Override
    default boolean containsRecursively(Term t, Predicate<Term> inSubtermsOf) {
        return inSubtermsOf.test(this) && subterms().containsRecursively(t, inSubtermsOf);
    }

    @NotNull
    TermContainer subterms();

    @Override
    default int hashCodeSubTerms() {
        return subterms().hashCode();
    }


    @Override
    default int opX() {
        return Term.opX(op(), size());
    }

    /**
     * if the compound tracks normalization state, this will set the flag internally
     */
    void setNormalized();




    //    @NotNull
//    default MutableSet<Term> termsToSet(boolean recurse, int inStructure, MutableSet<Term> t) {
//        if (recurse) {
//            recurseTerms((s) -> {
//                    t.add(s);
//            });
//        } else {
//            for (int i = 0; i < size(); i++) {
//                @NotNull T s = term(i);
//                if ((s.structure() & inStructure) > 0)
//                    t.add(s);
//            }
//        }
//        return t;//.toImmutable();
//    }


    /**
     * temporary: this will be replaced with a smart visitor api
     */
    @Override
    default boolean recurseTerms(BiPredicate<Term, Term> whileTrue) {
        return recurseTerms(whileTrue, this);
    }

    @Override
    default boolean recurseTerms(BiPredicate<Term, Term> whileTrue, @Nullable Term parent) {
        if (whileTrue.test(this, parent)) {
            return subterms().recurseSubTerms(whileTrue, this);
        }
        return false;
    }


    default boolean recurseTerms(Predicate<Term> parentsMust, Predicate<Term> whileTrue) {
        return recurseTerms(parentsMust, whileTrue, this);
    }

    @Override
    default boolean recurseTerms(Predicate<Term> parentsMust, Predicate<Term> whileTrue, @Nullable Term parent) {
        if (parentsMust.test(this)) {
            return subterms().recurseTerms(parentsMust, whileTrue, this);
        }
        return false;
    }


    @Override
    default void recurseTerms(@NotNull Consumer<Term> v) {
        v.accept(this);
        //subterms().forEach(s -> s.recurseTerms(v));
        subterms().recurseTerms(v);
    }


    @Override
    default boolean ORrecurse(@NotNull Predicate<Term> p) {
        if (p.test(this))
            return true;
        return subterms().ORrecurse(p);
    }

    @Override
    default boolean ANDrecurse(@NotNull Predicate<Term> p) {
        if (!p.test(this))
            return false;
        return subterms().ANDrecurse(p);
    }

    @Override
    default void init(@NotNull int[] meta) {

        subterms().init(meta);

        meta[5] |= op().bit;

    }


    @NotNull
    default ByteList structureKey(@NotNull ByteArrayList appendTo) {
        appendTo.add((byte) op().id);
        appendTo.add((byte) size());
        forEach(x -> {
            x.structureKey(appendTo);
        });
        return appendTo;
    }

    //    /** weather the given term has any potential free variables that could be assigned in unification */
//    default boolean freeVars(@Nullable Op type) {
//        return type == null ?
//                (volume() > complexity()) /* any variable, including pattern */
//                    :
//                (hasAny(type));
//    }
    default Set<Term> varsUnique(@Nullable Op type) {
        int num = vars(type);
        if (num == 0)
            return emptySet();
        else {
            //must check all in case of repeats
            Set<Term> u = new UnifiedSet(num);
            final int[] remain = {num};

            recurseTerms(parent -> vars(type) > 0,
                    (sub) -> {
                        if (sub.op() == type) {
                            u.add(sub);
                            remain[0]--;
                        }
                        return (remain[0] > 0);
                    });
            return u;
        }
    }

    @Nullable
    default Set<Term> varsUnique(@Nullable Op type, @NotNull Set<Term> unlessHere) {
        int num = vars(type);
        if (num == 0)
            return null;
        else {
            //must check all in case of repeats
            MutableSet<Term> u = new UnifiedSet(num);
            final int[] remain = {num};

            recurseTerms(parent -> vars(type) > 0,
                    (sub) -> {
                        if (sub.op() == type) {
                            if (!unlessHere.contains(sub))
                                u.add(sub);
                            remain[0]--;
                        }
                        return (remain[0] > 0);
                    });
            if (u.isEmpty())
                return null;
            return u;
        }
    }

    /**
     * unification matching entry point (default implementation)
     *
     * @param y     compound to match against (the instance executing this method is considered 'x')
     * @param subst the substitution context holding the match state
     * @return whether match was successful or not, possibly having modified subst regardless
     */
    @Override
    default boolean unify(@NotNull Term ty, @NotNull Unify subst) {


        if (ty instanceof Compound) {
            if (equals(ty))
                return true;

//            if (vars(subst.type) == 0)
//                return false; //no free vars, the only way unification can proceed is if equal

            Op op = op();
            if (op != ty.op())
                return false;

            int xs;
            if ((xs = size()) != ty.size())
                return false;

            Compound y = (Compound) ty;
            if (op.temporal) {
                int sdur = subst.dur;
                if (sdur >= 0) {
                    if (!matchTemporalDT(this, y, sdur))
                        return false;
                }
            }

            TermContainer xsubs = subterms();
            TermContainer ysubs = y.subterms();


            //do not do a fast termcontainer test unless it's linear; in commutive mode we want to allow permutations even if they are initially equal
            if (isCommutative() || y.isCommutative() /* in case one is XTERNAL and the other isn't; to be sure use this */) {
                return xsubs.unifyCommute(ysubs, subst);
            } else {
                return /*xsubs.equals(ysubs) || */xsubs.unifyLinear(ysubs, subst);
            }

        } else if (ty instanceof AliasConcept.AliasAtom) {
            Term abbreviated = ((AliasConcept.AliasAtom) ty).target;
            return abbreviated.equals(this) || unify(abbreviated, subst);
        }

        return false;

    }

    //TODO generalize
    static boolean matchTemporalDT(Term aa, Term bb, int dur) {
        int a = aa.dt();
        if (a == XTERNAL || a == DTERNAL) return true;
        int b = bb.dt();
        if (b == XTERNAL || b == DTERNAL) return true;

        return Math.abs(a - b) <= dur;
    }

    @Override
    default Term term() {
        return this;
    }

    @Override
    default void append(@NotNull Appendable p) throws IOException {
        IO.Printer.append(this, p);
    }


//    @Nullable
//    default Term subterm(@NotNull int... path) {
//        Term ptr = this;
//        for (int i : path) {
//            if ((ptr = ptr.termOr(i, null)) == null)
//                return null;
//        }
//        return ptr;
//    }



    default Term sub(int i, @Nullable Term ifOutOfBounds) {
        return subterms().sub(i, ifOutOfBounds);
    }


    @Nullable
    @Override
    default Object _car() {
        //if length > 0
        return sub(0);
    }

    /**
     * cdr or 'rest' function for s-expression interface when arity > 1
     */
    @Nullable
    @Override
    default Object _cdr() {
        int len = size();
        switch (len) {
            case 1:
                throw new RuntimeException("Pair fault");
            case 2:
                return sub(1);
            case 3:
                return new Pair(sub(1), sub(2));
            case 4:
                return new Pair(sub(1), new Pair(sub(2), sub(3)));
        }

        //this may need tested better:
        Pair p = null;
        for (int i = len - 2; i >= 0; i--) {
            p = new Pair(sub(i), p == null ? sub(i + 1) : p);
        }
        return p;
    }


    @NotNull
    @Override
    default Object setFirst(Object first) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    default Object setRest(Object rest) {
        throw new UnsupportedOperationException();
    }


    @Override
    default int varDep() {
        return subterms().varDep();
    }

    @Override
    default int varIndep() {
        return subterms().varIndep();
    }

    @Override
    default int varQuery() {
        return subterms().varQuery();
    }

    @Override
    default int varPattern() {
        return subterms().varPattern();
    }

    @Override
    default int vars() {
        return subterms().vars();
    }


    @NotNull
    @Override
    default Term sub(int i) {
        return subterms().sub(i);
    }

    @Override
    default boolean OR(@NotNull Predicate<Term> p) {
        return subterms().OR(p);
    }

    @Override
    default boolean AND(@NotNull Predicate<Term> p) {
        return subterms().AND(p);
    }

    @NotNull
    @Override
    default Term[] toArray() {
        return subterms().toArray();
    }


    @Override
    default void forEach(@NotNull Consumer<? super Term> c) {
        subterms().forEach(c);
    }


    @Override
    default int structure() {
        return subterms().structure() | op().bit;
    }


    @Override
    default int size() {
        return subterms().size();
    }

    @Override
    default int complexity() {
        return subterms().complexity(); //already has +1 for this compound
    }

    @Override
    default int volume() {
        return subterms().volume();  //already has +1 for this compound
    }

    @Override
    default boolean impossibleSubTermVolume(int otherTermVolume) {
        return subterms().impossibleSubTermVolume(otherTermVolume);
    }


    @Override
    default boolean isCommutative() {
        if (op().commutative) { //TODO only test dt() if equiv or conj
            int dt = dt();
            switch (dt) {
                case 0:
                case DTERNAL:
                    return (size() > 1);
                case XTERNAL:
                default:
                    return false;
            }
        }
        return false;
    }


    @Override
    default void forEach(@NotNull Consumer<? super Term> action, int start, int stop) {
        subterms().forEach(action, start, stop);
    }


    @Override
    default Iterator<Term> iterator() {
        return subterms().iterator();
    }

    @Override
    default void copyInto(@NotNull Collection<Term> set) {
        subterms().copyInto(set);
    }


//    @Nullable
//    @Override
//    default Ellipsis firstEllipsis() {
//        //return subterms().firstEllipsis();
//        return null;
//    }


    @Override
    boolean isNormalized();

//    /** whether the anonymized form of this term equals x */
//    @Override default boolean equalsAnonymously(@NotNull Term x) {
//
//        if ((opRel()==x.opRel()) && (structure()==x.structure()) && (volume()==x.volume())) { //some simple pre-tests to hopefully avoid needing to anonymize
//
//            return anonymous().equals(x);
//        }
//
//        return false;
//    }


    /**
     * gets temporal relation value
     */
    int dt();

    @Override
    default Term dt(int nextDT) {

        int dt;
        Op o = op();
        if (o.temporal && nextDT != (dt = this.dt())) {
            Op op = o;
            Compound b = this instanceof GenericCompoundDT ?
                    ((GenericCompoundDT) this).ref : this;

            if ((nextDT != XTERNAL && !concurrent(nextDT)) && size() > 2)
                return Null; //tried to temporalize what can only be commutive

            if (nextDT == XTERNAL) {
                return new GenericCompoundDT(b, nextDT);
            }

            if (o.commutative && sub(0).compareTo(sub(1)) > 0) {
                //must re-arrange the order to lexicographic, and invert dt
                return o.the(nextDT != DTERNAL ? -nextDT : DTERNAL, sub(1), sub(0));
            } else {
                return op.the(nextDT, toArray());
            }

        } else {
            return this;
        }


    }

    /**
     * similar to a indexOf() call, this will search for a int[]
     * path to the first subterm occurrence of the supplied term,
     * or null if none was found
     */
    @Nullable
    default byte[] isSubterm(@NotNull Term t) {
        if (!impossibleSubTerm(t)) {
            ByteArrayList l = new ByteArrayList();

            if (pathFirst(this, t, l)) {

                return Util.reverse(l);
            }
        }
        return null;
    }


    /**
     * finds the first occurring index path to a recursive subterm equal
     * to 't'
     */

    static boolean pathFirst(@NotNull Compound container, @NotNull Term t, @NotNull ByteArrayList l) {
        int s = container.size();
        for (int i = 0; i < s; i++) {
            Term xx = container.sub(i);
            if (xx.equals(t) || ((xx.contains(t)) && pathFirst((Compound) xx, t, l))) {
                l.add((byte) i);
                return true;
            } //else, try next subterm and its subtree
        }

        return false;
    }


//    @Override
//    default boolean equalsIgnoringVariables(@NotNull Term other, boolean requireSameTime) {
//        if (other instanceof Variable)
//            return true;
//
////        if (op() == NEG)
////            throw new UnsupportedOperationException("left hand side should already be unneg'd");
////
////        if (other.op()==NEG)
////            other = other.unneg();
//
//        Op op = op();
//        if (!(other.op() == op))
//            return false;
//
//        int s = size();
//
//        if (other.size() == s) {
//
//            if (requireSameTime)
//                if (((Compound) other).dt() != dt())
//                    return false;
//
//            Compound o = (Compound) other;
//            Term[] a = toArray();
//            Term[] b = o.toArray();
//            for (int i = 0; i < s; i++) {
//                if (!a[i].equalsIgnoringVariables(b[i], requireSameTime))
//                    return false;
//            }
//            return true;
//        }
//        return false;
//    }

    @Override
    default boolean isTemporal() {
        return hasAny(Op.TemporalBits) &&
                (op().temporal && (dt() != DTERNAL))
                ||
                (subterms().isTemporal());
    }



    /* collects any contained events within a conjunction*/
    @Override default void events(List<ObjectLongPair<Term>> events, long offset) {
        Op o = op();
        if (o == CONJ) {
            int dt = dt();

            if (dt != DTERNAL && dt != XTERNAL) {

                boolean reverse;
                if (dt < 0) {
                    dt = -dt;
                    reverse = true;
                } else reverse = false;

                TermContainer tt = subterms();
                int s = tt.size();
                long t = offset;
                for (int i = 0; i < s; i++) {
                    Term st = tt.sub(reverse ? (s - 1 - i) : i);
                    st.events(events, t);
                    t += dt + st.dtRange();
                }

                return;
            }

        }

        events.add(PrimitiveTuples.pair(this, offset));
    }



    @Override
    default Term eval(TermContext index) {

        //the presence of these bits means that somewhere in the subterms is a functor to eval
        if (!isDynamic()) //!hasAll(Op.EvalBits))
            return this;

        //unwrap negation before recursion, it should be more efficient
        Op o = op();
        if (o == NEG) {
            Term inner = unneg();
            if (inner == null)
                return this; //dont go further
            else {
                Term outer = $.neg(inner.eval(index));
                if (outer == null)
                    return this; //dont go further
                else
                    return outer;
            }
        }

        //TermContainer tt = subterms();

        @NotNull final Term[] xy = toArray();
        //any contained evaluables
        boolean subsModified = false;

        if (subterms().hasAll(opBits)) {
            int s = xy.length;
            for (int i = 0, evalSubsLength = xy.length; i < evalSubsLength; i++) {
                Term x = xy[i];
                Term y = x.eval(index);
                if (y == null) {
                    //if a functor returns null, it means unmodified
                } else if (x != y) {
                    //the result comparing with the x
                    subsModified = true;
                    xy[i] = y;
                }
            }
        }


        Term u;
        if (subsModified) {
            u = o.the(dt(), xy);
            if (!(u instanceof Compound))
                return u; //atomic, including Bool short-circuits on invalid term
        } else {
            u = this;
        }

        //recursively compute contained subterm functors
        if (u.op() == INH && u.size() == 2) {
            Term possibleArgs = u.sub(0);
            if (possibleArgs instanceof Compound && possibleArgs.op() == PROD) {
                Term possibleFunc = u.sub(1);
                if (possibleFunc instanceof Atomic && possibleFunc.op() == ATOM) {
                    Termed ff = index.getIfPresentElse(possibleFunc);
                    if (ff instanceof Functor) {
                        u = ((Functor) ff).apply(((Compound) possibleArgs).subterms());
                        if (u == null)
                            u = this; //null means to keep the same
                    }
                }
            }
        }

        //atomic, including Bool short-circuits on invalid term
        if (u instanceof Bool || u instanceof Variable || u.equals(this)) {
            return u; //return u and not this
        } else {

            //it has been changed, so eval recursively until stable
            try {
                assert(u!=this): "equality tested previously should have included identity check";
                return u.eval(index);
            } catch (StackOverflowError e) {
                logger.error("eval stack overflow: {} -> {}", this, u);
                return Null;
                //throw new RuntimeException("stack overflow on eval : " + t);
            }
        }
    }

    final static Logger logger = LoggerFactory.getLogger(Compound.class);

    @Nullable
    default Term normalize() {
        if (this.isNormalized())
            return this; //TODO try not to let this happen



        int vars = this.vars();
        int pVars = this.varPattern();
        int totalVars = vars + pVars;

        Term y;
        if (totalVars > 0) {
            y = transform(
                    ((vars == 1) && (pVars == 0)) ?
                            VariableNormalization.singleVariableNormalization //special case for efficiency
                            :
                            new VariableNormalization(totalVars /* estimate */)
            );


        } else {
            y = this;
        }

        if (y != null && y instanceof Compound)
            ((Compound)y).setNormalized();

        return y;
    }

    @Nullable
    default Term transform(@NotNull CompoundTransform t) {
        Compound src = this;
        if (t.testSuperTerm(src)) {
            return transform(src.op(), src.dt(), t);
        } else {
            return src;
        }

    }

    @Nullable
    default Term transform(int newDT, @NotNull CompoundTransform t) {
        if (this.dt() == newDT)
            return transform(t); //no dt change, use non-DT changing method that has early fail
        else {
            return transform(op(), newDT, t);
        }
    }

    @Nullable
    default Term transform(Op op, int dt, @NotNull CompoundTransform t) {

        if (!t.testSuperTerm(this))
            return this;

        boolean filterTrueFalse = !op.allowsBool;

        @NotNull TermContainer srcSubs = this.subterms(); //for faster access, generally
        int s = srcSubs.size(), subtermMods = 0;
        NewCompound target = new NewCompound(op, s);
        for (int i = 0; i < s; i++) {

            Term x = srcSubs.sub(i), y;

            y = t.apply(this, x);

            if (y == null)
                return null;

            if (y instanceof Compound) {
                y = ((Compound) y).transform(t); //recurse
            }

            if (y == null)
                return null;

            if (y != x) {
                if (Term.invalidBoolSubterms(y, filterTrueFalse))
                    return null;

                //            if (y != null)
                //                y = y.eval(this);

                //if (x != y) { //must be refernce equality test for some variable normalization cases
                //if (!x.equals(y)) { //must be refernce equality test for some variable normalization cases
                subtermMods++;

            }

            target.add(y);
        }

        //TODO does it need to recreate the container if the dt has changed because it may need to be commuted ... && (superterm.dt()==dt) but more specific for the case: (XTERNAL -> 0 or DTERNAL)

        //        if (subtermMods == 0 && !opMod && dtMod && (op.image || (op.temporal && concurrent(dt)==concurrent(src.dt()))) ) {
//            //same concurrency, just change dt, keep subterms
//            return src.dt(dt);
//        }
        if (subtermMods > 0 || op != this.op()/* || dt != src.dt()*/) {

            //if (target.internable())
            return op.the(dt, target.theArray());
            //else
            //return Op.compound(op, target.theArray(), false).dt(dt); //HACK

        } else if (dt != this.dt())
            return this.dt(dt);
        else
            return this;
    }


    @Override
    @NotNull
    default Term eternal() {
        if (!this.hasAny(Op.TemporalBits))
            return this;

        TermContainer subs = this.subterms();
        Term[] s = subs.toArray();

        //1. determine if any subterms (excluding the term itself) get rewritten
        boolean subsChanged = false;
        if (subs.hasAny(Op.TemporalBits)) {

            //atemporalize subterms first

            int cs = s.length;
            for (int i = 0; i < cs; i++) {

                Term xi = s[i];

                Term yi = xi.eternal();
                if (yi instanceof Bool)
                    return Null;
                if (!xi.equals(yi)) {
                    s[i] = yi;
                    subsChanged = true;
                }

            }
        }


        //2. anonymize the term itself if anything has changed.
        Op o = this.op();
        int dt = this.dt();
        int nextDT = !o.temporal ? dt : DTERNAL;

        if (o.temporal)
            nextDT = XTERNAL;

        if (o.temporal && s.length == 2) {


//            if (s.length s[0].equals(s[1])) {
//                s = new Term[]{s[0], s[0] /* repeated */};
//                subsChanged = true;
//            } else
            if (o.commutative) {
                if (s[0].compareTo(s[1]) > 0) { //lexical sort
                    s = new Term[]{s[1], s[0]};
                    subsChanged = true;
                }
            }
        }

        boolean dtChanging = (nextDT != dt);

        if (!subsChanged && !dtChanging) {
            return this; //no change is necessary
        }

        Term y =
                subsChanged ? o.the(nextDT, s) : this.dt(nextDT)
        ;
        if (y == null)
            return Null;

        return y;
    }

    @Override
    @NotNull
    default Term root() {

        Term term = unneg().eternal();


        //atemporalizing can reset normalization state of the result instance
        //since a manual normalization isnt invoked. until here, which depends if the original input was normalized:

        term = term.normalize(); if (term == null) return Null;

        return term.unneg();
    }


    //    default MutableSet<Term> toSetAtemporal() {
//        int ss = size();
//        MutableSet<Term> s = new UnifiedSet<>(ss);
//        for (int i = 0; i < ss; i++) {
//            s.add(Terms.atemporalize(term(i)));
//        }
//        return s;
//    }


    //    public int countOccurrences(final Term t) {
//        final AtomicInteger o = new AtomicInteger(0);
//
//        if (equals(t)) return 1;
//
//        recurseTerms((n, p) -> {
//            if (n.equals(t))
//                o.incrementAndGet();
//        });
//
//        return o.get();
//    }


//    public static class InvalidTermConstruction extends RuntimeException {
//        public InvalidTermConstruction(String reason) {
//            super(reason);
//        }
//    }


//    /**
//     * single term version of makeCompoundName without iteration for efficiency
//     */
//    @Deprecated
//    protected static CharSequence makeCompoundName(final Op op, final Term singleTerm) {
//        int size = 2; // beginning and end parens
//        String opString = op.toString();
//        size += opString.length();
//        final CharSequence tString = singleTerm.toString();
//        size += tString.length();
//        return new StringBuilder(size).append(COMPOUND_TERM_OPENER).append(opString).append(ARGUMENT_SEPARATOR).append(tString).append(COMPOUND_TERM_CLOSER).toString();
//    }

    //    @Deprecated public static class UnableToCloneException extends RuntimeException {
//
//        public UnableToCloneException(String message) {
//            super(message);
//        }
//
//        @Override
//        public synchronized Throwable fillInStackTrace() {
//            /*if (Parameters.DEBUG) {
//                return super.fillInStackTrace();
//            } else {*/
//                //avoid recording stack trace for efficiency reasons
//                return this;
//            //}
//        }
//
//
//    }


}


//    /** performs a deep comparison of the term structure which should have the same result as normal equals(), but slower */
//    @Deprecated public boolean equalsByTerm(final Object that) {
//        if (!(that instanceof CompoundTerm)) return false;
//
//        final CompoundTerm t = (CompoundTerm)that;
//
//        if (operate() != t.operate())
//            return false;
//
//        if (getComplexity()!= t.getComplexity())
//            return false;
//
//        if (getTemporalOrder()!=t.getTemporalOrder())
//            return false;
//
//        if (!equals2(t))
//            return false;
//
//        if (term.length!=t.term.length)
//            return false;
//
//        for (int i = 0; i < term.length; i++) {
//            if (!term[i].equals(t.term[i]))
//                return false;
//        }
//
//        return true;
//    }
//
//
//
//
//    /** additional equality checks, in subclasses, only called by equalsByTerm */
//    @Deprecated public boolean equals2(final CompoundTerm other) {
//        return true;
//    }

//    /** may be overridden in subclass to include other details */
//    protected int calcHash() {
//        //return Objects.hash(operate(), Arrays.hashCode(term), getTemporalOrder());
//        return name().hashCode();
//    }

//
//    /**
//     * Orders among terms: variable < atomic < compound
//     *
//     * @param that The Term to be compared with the current Term
//\     * @return The order of the two terms
//     */
//    @Override
//    public int compareTo(final AbstractTerm that) {
//        if (this == that) return 0;
//
//        if (that instanceof CompoundTerm) {
//            final CompoundTerm t = (CompoundTerm) that;
//            if (size() == t.size()) {
//                int opDiff = this.operate().ordinal() - t.operate().ordinal(); //should be faster faster than Enum.compareTo
//                if (opDiff != 0) {
//                    return opDiff;
//                }
//
//                int tDiff = this.getTemporalOrder() - t.getTemporalOrder(); //should be faster faster than Enum.compareTo
//                if (tDiff != 0) {
//                    return tDiff;
//                }
//
//                for (int i = 0; i < term.length; i++) {
//                    final int diff = term[i].compareTo(t.term[i]);
//                    if (diff != 0) {
//                        return diff;
//                    }
//                }
//
//                return 0;
//            } else {
//                return size() - t.size();
//            }
//        } else {
//            return 1;
//        }
//    }



    /*
    @Override
    public boolean equals(final Object that) {
        return (that instanceof Term) && (compareTo((Term) that) == 0);
    }
    */


//
//
//
//
//    /**
//     * Orders among terms: variable < atomic < compound
//     *
//     * @param that The Term to be compared with the current Term
//\     * @return The order of the two terms
//     */
//    @Override
//    public int compareTo(final Term that) {
//        /*if (!(that instanceof CompoundTerm)) {
//            return getClass().getSimpleName().compareTo(that.getClass().getSimpleName());
//        }
//        */
//        return -name.compareTo(that.name());
//            /*
//            if (size() == t.size()) {
//                int opDiff = this.operate().ordinal() - t.operate().ordinal(); //should be faster faster than Enum.compareTo
//                if (opDiff != 0) {
//                    return opDiff;
//                }
//
//                for (int i = 0; i < term.length; i++) {
//                    final int diff = term[i].compareTo(t.term[i]);
//                    if (diff != 0) {
//                        return diff;
//                    }
//                }
//
//                return 0;
//            } else {
//                return size() - t.size();
//            }
//        } else {
//            return 1;
//            */
//    }


//    @Override
//    public int compareTo(final Object that) {
//        if (that == this) return 0;
//
//        // variables have earlier sorting order than non-variables
//        if (!(that instanceof Compound)) return 1;
//
//        final Compound c = (Compound) that;
//
//        int opdiff = compareClass(this, c);
//        if (opdiff != 0) return opdiff;
//
//        return compare(c);
//    }

//    public static int compareClass(final Object b, final Object c) {
//        Class c1 = b.getClass();
//        Class c2 = c.getClass();
//        int h = Integer.compare(c1.hashCode(), c2.hashCode());
//        if (h != 0) return h;
//        return c1.getName().compareTo(c2.getName());
//    }

//    /**
//     * compares only the contents of the subterms; assume that the other term is of the same operator type
//     */
//    public int compareSubterms(final Compound otherCompoundOfEqualType) {
//        return Terms.compareSubterms(term, otherCompoundOfEqualType.term);
//    }


//    final static int maxSubTermsForNameCompare = 2; //tunable
//
//    protected int compare(final Compound otherCompoundOfEqualType) {
//
//        int l = length();
//
//        if ((l != otherCompoundOfEqualType.length()) || (l < maxSubTermsForNameCompare))
//            return compareSubterms(otherCompoundOfEqualType);
//
//        return compareName(otherCompoundOfEqualType);
//    }
//
//
//    public int compareName(final Compound c) {
//        return super.compareTo(c);
//    }

//    public final void recurseSubtermsContainingVariables(final TermVisitor v, Term parent) {
//        if (hasVar()) {
//            v.visit(this, parent);
//            //if (this instanceof Compound) {
//            for (Term t : term) {
//                t.recurseSubtermsContainingVariables(v, this);
//            }
//            //}
//        }
//    }

//    @Override
//    public boolean equals(final Object that) {
//        if (this == that)
//            return true;
//
//        if (!(that instanceof Compound)) return false;
//        Compound c = (Compound) that;
//        if (contentHash != c.contentHash ||
//                structureHash != c.structureHash ||
//                volume != c.volume)
//            return false;
//
//        final int s = this.length();
//        Term[] x = this.term;
//        Term[] y = c.term;
//        if (x != y) {
//            boolean canShare =
//                    (structureHash &
//                    ((1 << Op.SEQUENCE.ordinal()) | (1 << Op.PARALLEL.ordinal()))) == 0;
//
//            for (int i = 0; i < s; i++) {
//                Term a = x[i];
//                Term b = y[i];
//                if (!a.equals(b))
//                    return false;
//            }
//            if (canShare) {
//                this.term = (T[]) c.term;
//            }
//            else {
//                this.term = this.term;
//            }
//        }
//
//        if (structure2() != c.structure2() ||
//                op() != c.op())
//            return false;
//
//        return true;
//    }

//    @Override
//    public boolean equals(final Object that) {
//        if (this == that)
//            return true;
//        if (!(that instanceof Compound)) return false;
//
//        Compound c = (Compound) that;
//        if (contentHash != c.contentHash ||
//                structureHash != c.structureHash
//                || volume() != c.volume()
//                )
//            return false;
//
//        final int s = this.length();
//        Term[] x = this.term;
//        Term[] y = c.term;
//        for (int i = 0; i < s; i++) {
//            Term a = x[i];
//            Term b = y[i];
//            if (!a.equals(b))
//                return false;
//        }
//
//        return true;
//    }

    /* UNTESTED
    public Compound clone(VariableTransform t) {
        if (!hasVar())
            throw new RuntimeException("this VariableTransform clone should not have been necessary");

        Compound result = cloneVariablesDeep();
        if (result == null)
            throw new RuntimeException("unable to clone: " + this);

        result.transformVariableTermsDeep(t);

        result.invalidate();

        return result;
    } */


//    /**
//     * true if equal operate and all terms contained
//     */
//    public boolean containsAllTermsOf(final Term t) {
//        if ((op() == t.op())) {
//            return Terms.containsAll(term, ((Compound) t).term);
//        } else {
//            return this.containsTerm(t);
//        }
//    }

//    /**
//     * Try to add a component into a compound
//     *
//     * @param t1 The compound
//     * @param t2 The component
//     * @param memory Reference to the memory
//     * @return The new compound
//     */
//    public static Term addComponents(final CompoundTerm t1, final Term t2, final Memory memory) {
//        if (t2 == null)
//            return t1;
//
//        boolean success;
//        Term[] terms;
//        if (t2 instanceof CompoundTerm) {
//            terms = t1.cloneTerms(((CompoundTerm) t2).term);
//        } else {
//            terms = t1.cloneTerms(t2);
//        }
//        return Memory.make(t1, terms, memory);
//    }


//    /**
//     * Recursively check if a compound contains a term
//     * This method DOES check the equality of this term itself.
//     * Although that is how Term.containsTerm operates
//     *
//     * @param target The term to be searched
//     * @return Whether the target is in the current term
//     */
//    @Override
//    public boolean equalsOrContainsTermRecursively(final Term target) {
//        if (this.equals(target)) return true;
//        return containsTermRecursively(target);
//    }

/**
 * override in subclasses to avoid unnecessary reinit
 */
    /*public CompoundTerm _clone(final Term[] replaced) {
        if (Terms.equals(term, replaced)) {
            return this;
        }
        return clone(replaced);
    }*/

//    @Override
//    public int containedTemporalRelations() {
//        if (containedTemporalRelations == -1) {
//
//            /*if ((this instanceof Equivalence) || (this instanceof Implication))*/
//            {
//                int temporalOrder = this.getTemporalOrder();
//                switch (temporalOrder) {
//                    case TemporalRules.ORDER_FORWARD:
//                    case TemporalRules.ORDER_CONCURRENT:
//                    case TemporalRules.ORDER_BACKWARD:
//                        containedTemporalRelations = 1;
//                        break;
//                    default:
//                        containedTemporalRelations = 0;
//                        break;
//                }
//            }
//
//            for (final Term t : term)
//                containedTemporalRelations += t.containedTemporalRelations();
//        }
//        return this.containedTemporalRelations;
//    }


//    /**
//     * Gives a set of all (unique) contained term, recursively
//     */
//    public Set<Term> getContainedTerms() {
//        Set<Term> s = Global.newHashSet(complexity());
//        for (Term t : term) {
//            s.add(t);
//            if (t instanceof Compound)
//                s.addAll(((Compound) t).getContainedTerms());
//        }
//        return s;
//    }


//    /**
//     * forced deep clone of terms
//     */
//    public ArrayList<Term> cloneTermsListDeep() {
//        ArrayList<Term> l = new ArrayList(length());
//        for (final Term t : term)
//            l.add(t.clone());
//        return l;
//    }



    /*static void shuffle(final Term[] list, final Random randomNumber) {
        if (list.length < 2)  {
            return;
        }


        int n = list.length;
        for (int i = 0; i < n; i++) {
            // between i and n-1
            int r = i + (randomNumber.nextInt() % (n-i));
            Term tmp = list[i];    // swap
            list[i] = list[r];
            list[r] = tmp;
        }
    }*/

/*        public static void shuffle(final Term[] ar,final Random rnd)
        {
            if (ar.length < 2)
                return;



          for (int i = ar.length - 1; i > 0; i--)
          {
            int index = randomNumber.nextInt(i + 1);
            // Simple swap
            Term a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
          }

        }*/

///**
// * Check whether the compound contains a certain component
// * Also matches variables, ex: (&&,<a --> b>,<b --> c>) also contains <a --> #1>
// *  ^^^ is this right? if so then try containsVariablesAsWildcard
// *
// * @param t The component to be checked
// * @return Whether the component is in the compound
// */
//return Terms.containsVariablesAsWildcard(term, t);
//^^ ???

//    /**
//     * Try to replace a component in a compound at a given index by another one
//     *
//     * @param index   The location of replacement
//     * @param subterm The new component
//     * @return The new compound
//     */
//    public Term cloneReplacingSubterm(final int index, final Term subterm) {
//
//        final boolean e = (subterm != null) && (op() == subterm.op());
//
//        //if the subterm is alredy equivalent, just return this instance because it will be equivalent
//        if (subterm != null && (e) && (term[index].equals(subterm)))
//            return this;
//
//        List<Term> list = asTermList();//Deep();
//
//        list.remove(index);
//
//        if (subterm != null) {
//            if (!e) {
//                list.add(index, subterm);
//            } else {
//                //splice in subterm's subterms at index
//                for (final Term t : term) {
//                    list.add(t);
//                }
//
//                /*Term[] tt = ((Compound) subterm).term;
//                for (int i = 0; i < tt.length; i++) {
//                    list.add(index + i, tt[i]);
//                }*/
//            }
//        }
//
//        return Memory.term(this, list);
//    }


//    /**
//     * Check whether the compound contains all term of another term, or
//     * that term as a whole
//     *
//     * @param t The other term
//     * @return Whether the term are all in the compound
//     */
//    public boolean containsAllTermsOf_(final Term t) {
//        if (t instanceof CompoundTerm) {
//        //if (operate() == t.operate()) {
//            //TODO make unit test for containsAll
//            return Terms.containsAll(term, ((CompoundTerm) t).term );
//        } else {
//            return Terms.contains(term, t);
//        }
//    }


//    @Override
//    public boolean equals(final Object that) {
//        if (!(that instanceof CompoundTerm))
//            return false;
//
//        final CompoundTerm t = (CompoundTerm)that;
//        return name().equals(t.name());
//
//        /*if (hashCode() != t.hashCode())
//            return false;
//
//        if (operate() != t.operate())
//            return false;
//
//        if (size() != t.size())
//            return false;
//
//        for (int i = 0; i < term.size(); i++) {
//            final Term c = term.get(i);
//            if (!c.equals(t.componentAt(i)))
//                return false;
//        }
//
//        return true;*/
//
//    }


//boolean transform(CompoundTransform<Compound<Term>, T> trans, int depth);


//    /**
//     * returns result of applySubstitute, if and only if it's a CompoundTerm.
//     * otherwise it is null
//     */
//    default Compound applySubstituteToCompound(Map<Term, Term> substitute) {
//        Term t = Term.substituted(this,
//                new MapSubst(substitute));
//        if (t instanceof Compound)
//            return ((Compound) t);
//        return null;
//    }

//    /**
//     * from: http://stackoverflow.com/a/19333201
//     */
//    public static <Term> void shuffle(final T[] array, final Random random) {
//        int count = array.length;
//
//        //probabality for no shuffle at all:
//        if (random.nextInt(factorial(count)) == 0) return;
//
//        for (int i = count; i > 1; i--) {
//            final int a = i - 1;
//            final int b = random.nextInt(i);
//            if (b!=a) {
//                final T t = array[b];
//                array[b] = array[a];
//                array[a] = t;
//            }
//        }
//    }

//    static Term unwrap(Term x, boolean unwrapLen1SetExt, boolean unwrapLen1SetInt, boolean unwrapLen1Product) {
//        if (x instanceof Compound) {
//            Compound c = (Compound) x;
//            if (c.size() == 1) {
//                if ((unwrapLen1SetInt && (c instanceof SetInt)) ||
//                        (unwrapLen1SetExt && (c instanceof SetExt)) ||
//                        (unwrapLen1Product && (c instanceof Product))
//                        ) {
//                    return c.term(0);
//                }
//            }
//        }
//
//        return x;
//    }


//    @NotNull
//    default Set<Term> recurseTermsToSet() {
//        Set<Term> t = $.newHashSet(volume() /* estimate */);
//        recurseTerms(t::add);
//        return t;
//    }

//    @NotNull
//    default SortedSet<Term> recurseTermsToSortedSet() {
//        TreeSet<Term> t = new TreeSet();
//        recurseTerms((x) -> t.add(x));
//        return t;
//    }
//
//    @NotNull
//    default MutableBiMap<Term, Short> recurseTermsToBiMap() {
//        MutableBiMap<Term, Short> t = new HashBiMap(volume() /* estimate */); //BiMaps.mutable.empty();
//        recurseTerms((x) -> t.putIfAbsent(x, (short) t.size()));
//        return t;
//    }

//
//    @NotNull
//    default boolean termsToSet(@NotNull Collection<Term> t, boolean addOrRemoved) {
//        return termsToSet(-1, t, addOrRemoved);
//    }
