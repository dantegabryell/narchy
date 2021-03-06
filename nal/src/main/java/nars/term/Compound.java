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
 * along with Open-NARS.  If not, see <http:
 */
package nars.term;

import com.google.common.io.ByteArrayDataOutput;
import jcog.Util;
import jcog.data.byt.util.IntCoding;
import jcog.data.sexpression.IPair;
import jcog.data.sexpression.Pair;
import jcog.util.ArrayUtils;
import nars.IO;
import nars.Op;
import nars.The;
import nars.subterm.Subterms;
import nars.subterm.TermList;
import nars.term.anon.Anon;
import nars.term.atom.Bool;
import nars.term.compound.UnitCompound;
import nars.term.util.transform.Retemporalize;
import nars.term.util.transform.TermTransform;
import nars.unify.Unify;
import org.eclipse.collections.api.block.predicate.primitive.LongObjectPredicate;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static nars.Op.CONJ;
import static nars.Op.NEG;
import static nars.time.Tense.*;

/**
 * a compound term
 * TODO make this an interface extending Subterms
 */
public interface Compound extends Term, IPair, Subterms {


    static boolean equals(/*@NotNull*/ Compound A, Object b) {
        if (A == b) return true;

        if (((b instanceof Compound) && (A.hashCode() == b.hashCode()))) {
            Compound B = (Compound) b;
            Op ao = A.op();
            if (ao == B.op()) {
                return
                        (!ao.temporal || (A.dt() == B.dt()))
                                &&
                                equalSubs(A, B)
                        ;
            }
        }

        return false;
    }

    static boolean equalSubs(Compound a, Compound b) {

        if (a instanceof UnitCompound || b instanceof UnitCompound) {
            //avoid instantiating dummy subterms instance
            int as = a instanceof UnitCompound ? 1 : a.subs();
            int bs = b instanceof UnitCompound ? 1 : b.subs();
            return as == bs && a.sub(0).equals(b.sub(0));
        } else
            return a.subterms().equals(b.subterms());
    }

    static String toString(Compound c) {
        return toStringBuilder(c).toString();
    }

    static StringBuilder toStringBuilder(Compound c) {
        StringBuilder sb = new StringBuilder(
                ///* conservative estimate */ c.volume() * 2
                32
        );
        try {
            c.appendTo(sb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb;
    }

    /**
     * reference impl for compound hashcode
     */
    static int hashCode(Compound c) {
        return Util.hashCombine(
                c.hashCodeSubterms(),
                c.op().id
        );
    }

    Op op();


    @Override
    default boolean containsRecursively(Term x, boolean root, Predicate<Term> inSubtermsOf) {
        return !impossibleSubTerm(x) && inSubtermsOf.test(this) && subterms().containsRecursively(x, root, inSubtermsOf);
    }

    /**
     * deprecated; TODO move to SeparateSubtermsCompound interface and allow Compounds which do not have to generate this.  this sums up many of xjrn's suggestions
     */
    @Override
    Subterms subterms();

    @Override
    default int hashCodeSubterms() {
        return subterms().hashCode();
    }

    @Override
    default boolean the() {
        return this instanceof The && subterms().these();
    }

    @Override
    default int opX() {
        //return Term.opX(op(), (short) volume());
        //return Term.opX(op(), (short) subs());

        //upper 11 bits: volume
        //next 5: op
        //lower 16: ? subs ? structure hash
        short volume = (short) volume();
        byte op = op().id;
        byte subs = (byte) subs();
        return opX(volume, op, subs);
    }

    static int opX(short volume, byte op, byte subs) {
        return ((volume & 0b11111111111) << (16 + 5))
                |
                (op << 16)
                |
                subs;
    }


    @Override
    default Term anon() {
        return new Anon(/* TODO size estimate */).put(this);
    }


    @Override
    default boolean recurseTerms(Predicate<Term> aSuperCompoundMust, Predicate<Term> whileTrue, @Nullable Term superterm) {
        return (!aSuperCompoundMust.test(this) ||
                (whileTrue.test(this) && subterms().recurseTerms(aSuperCompoundMust, whileTrue, this)));
    }

    @Override
    default boolean recurseTerms(Predicate<Compound> aSuperCompoundMust, BiPredicate<Term, Compound> whileTrue, @Nullable Compound superterm) {
        return (!aSuperCompoundMust.test(this) ||
                (whileTrue.test(this, superterm) && subterms().recurseTerms(aSuperCompoundMust, whileTrue, this)));
    }


    @Override
    default boolean ORrecurse(Predicate<Term> p) {
        return p.test(this) || subterms().ORrecurse(p);
    }

    @Override
    default boolean ANDrecurse(Predicate<Term> p) {
        return p.test(this) && subterms().ANDrecurse(p);
    }


    default void appendTo(ByteArrayDataOutput out) {

        Op o = op();

        boolean temporal = o.temporal && dt() != DTERNAL;

        out.writeByte(o.id | (temporal ? IO.TEMPORAL_BIT : 0));

        appendSubtermsTo(out);

        if (temporal)
            IntCoding.writeZigZagInt(dt(), out);

    }

    default void appendSubtermsTo(ByteArrayDataOutput out) {
        subterms().appendTo(out);
    }


    /**
     * unification matching entry point (default implementation)
     *
     * @param y compound to match against (the instance executing this method is considered 'x')
     * @param u the substitution context holding the match state
     * @return whether match was successful or not, possibly having modified subst regardless
     */
    @Override
    default boolean unify(/*@NotNull*/ Term y, /*@NotNull*/ Unify u) {

        if (y instanceof Compound) {
            return equals(y) || (op() == y.op() && unifySubterms(y, u));
        }

        if (y instanceof Variable) return y.unify(this, u);

        return false;
    }


    default boolean unifySubterms(Term y, Unify u) {

        Term x = this;

        Subterms xx = subterms();
        Subterms yy = y.subterms();

        Op o = op();
        if (o == CONJ) {
            int xdt = dt(), ydt = y.dt();
            if (xdt==XTERNAL && ydt!=XTERNAL) {
                return xx.equals(yy) || unify(y.root(), u);
            } else if (ydt==XTERNAL && xdt!=XTERNAL) {
                return xx.equals(yy) || root().unify(y, u);
            }
        }

        int xs;
        if ((xs = xx.subs()) != yy.subs())
            return false;
        if (!Subterms.possiblyUnifiable(xx, yy, u))
            return false;

        if (xs == 1)
            return xx.sub(0).unify(yy.sub(0), u);

        boolean xSpecific = false, ySpecific = false;


        int xdt, ydt;
        if (o.temporal) {
            xdt = x.dt();
            ydt = y.dt();
            if (xdt != ydt) {
                xSpecific = (xdt != XTERNAL && xdt != DTERNAL);
                ySpecific = (ydt != XTERNAL && ydt != DTERNAL);
                if (xSpecific && ySpecific)
                    if (!u.unifyDT(xdt, ydt))
                        return false;
            }

            //compound equality would have been true if non-temporal
            if (xx.equals(yy))
                return true;


        } else {
            xdt = ydt = DTERNAL;
        }


        boolean result;
        if (o.commutative /* subs>1 */) {
            if (o == CONJ && !dtSpecial(xdt)) {
                //assert(xs==2);
                result = (xdt >= 0) == (ydt >= 0) ? xx.unifyLinear(yy, u) : xx.unifyLinear(yy.reversed(), u);
            } else {
                result = xx.unifyCommute(yy, u);
            }
        } else { //TODO if temporal, compare in the correct order
            result = xx.unifyLinear(yy, u);
        }

        return result;
//            if (result) {
//                if (xSpecific^ySpecific && u instanceof Derivation) {
//                    //one is not specified.  specify via substitution
//                    Derivation du = (Derivation) u;
//                    if (!xSpecific) {
//                        du.refinements.put(x, x.dt(ydt));
//                    } else {
//                        du.refinements.put(y, y.dt(xdt));
//                    }
//                }
//
//                return true;
//            }
//            return false;

    }


    @Override
    default void appendTo(/*@NotNull*/ Appendable p) throws IOException {
        IO.Printer.append(this, p);
    }


    @Override
    default Term sub(int i, Term ifOutOfBounds) {
        return subterms().sub(i, ifOutOfBounds);
    }


    @Nullable
    @Override
    default Object _car() {

        return sub(0);
    }

    /**
     * cdr or 'rest' function for s-expression interface when arity > 1
     */
    @Nullable
    @Override
    default Object _cdr() {
        int len = subs();
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


        Pair p = null;
        for (int i = len - 2; i >= 0; i--) {
            p = new Pair(sub(i), p == null ? sub(i + 1) : p);
        }
        return p;
    }


    /*@NotNull*/
    @Override
    default Object setFirst(Object first) {
        throw new UnsupportedOperationException();
    }

    /*@NotNull*/
    @Override
    default Object setRest(Object rest) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean isCommutative() {
        Op op = op();
        return op == CONJ ? dtSpecial(dt()) : op.commutative && subs() > 1;
    }


    @Override
    default void addTo(/*@NotNull*/ Collection<Term> set) {
        subterms().addTo(set);
    }

    @Override
    default boolean impossibleSubStructure(int structure) {
        return !subterms().hasAll(structure);
    }

    @Override
    default boolean isNormalized() {
        return subterms().isNormalized();
    }

    /**
     * gets temporal relation value
     */
    @Override
    int dt();


    /**
     * replaces the 'from' term with 'to', recursively
     */
    default Term replace(Term from, Term to) {
        if (this.equals(from))
            return to;

        Subterms oldSubs = subterms();
        Subterms newSubs = oldSubs.replaceSub(from, to, op());

        if (newSubs == oldSubs)
            return this;

        if (newSubs == null)
            return Bool.Null;

        int dt = dt();
        Op o = op();
        if (newSubs instanceof TermList) {
            return o.the(dt, ((TermList) newSubs).arrayKeep());
        } else {
            return o.the(dt, newSubs);
        }

    }

    default int subTimeOnly(Term event) {
        int[] t = subTimes(event, 1);
        if (t == null || t.length != 1) return DTERNAL;
        return t[0];
    }

    default int[] subTimes(Term event) {
        return subTimes(event, Integer.MAX_VALUE);
    }

    /**
     * TODO return XTERNAL not DTERNAL on missing, it is more appropriate
     * expect the output array to be sorted
     */
    default int[] subTimes(Term event, int max) {
        assert (max > 0);

        if (equals(event))
            return new int[]{0};

        if (op() != CONJ || impossibleSubTerm(event))
            return null;

        int dt = dt();
        if (dt == DTERNAL || dt == 0) {
            int[] tt = null;
            boolean needDedup = false;
            for (Term x : subterms()) {
                int[] ss = x.subTimes(event);
                if (ss != null) {
                    if (ss.length > max)
                        return null;
                    if (tt == null)
                        tt = ss;
                    else if (!Arrays.equals(ss, tt)) {
                        int undupN = ss.length + tt.length;
                        tt = ArrayUtils.addAll(ss, tt);
                        needDedup = tt.length != undupN;
                        if (!needDedup && tt.length > max)
                            return null;
                    }
                }
            }
            if (needDedup) {
                tt = ArrayUtils.removeDuplicates(tt);
                if (tt.length > max)
                    return null;
            } else {
                if (tt != null) {
                    if (tt.length > max)
                        return null;
                    if (tt.length > 1)
                        Arrays.sort(tt);
                }
            }
            return tt;
        }

        if (dt == XTERNAL)
            return null;


        int[][] found = new int[1][];
        if ((subTimesWhile(event, (when) -> {
            found[0] = found[0] == null ? new int[]{when} : ArrayUtils.add(found[0], when);
            return true;
        })) == 0)
            return null;

        return found[0];
    }


    @Override
    default Term dt(int nextDT) {
        return Op.dt(this, nextDT);
    }


    /* iterates contained events within a conjunction*/
    @Override
    default boolean eventsWhile(LongObjectPredicate<Term> events, long offset, boolean decomposeConjParallel, boolean decomposeConjDTernal, boolean decomposeXternal, int level) {

        Op o = op();

        if (o == CONJ) {

            int dt = dt();
            boolean decompose;
            switch (dt) {
                case 0:
                    decompose = decomposeConjParallel;
                    break;
                case DTERNAL:
                    if (decompose = decomposeConjDTernal)
                        dt = 0;
                    break;
                case XTERNAL:
                    if (decompose = decomposeXternal)
                        dt = 0;
                    break;
                default:
                    decompose = true;
                    break;
            }

            if (decompose) {

                Subterms tt = subterms();
                int s = tt.subs();
                long t = offset;

                boolean changeDT = t != ETERNAL && t != TIMELESS && dt != 0 /* motionless in time */;

                level++;

                boolean fwd;
                if (changeDT) {
                    fwd = dt >= 0;
                    if (!fwd)
                        dt = -dt;
                } else {
                    fwd = true;
                }

                for (int i = 0; i < s; i++) {
                    Term st = tt.sub(fwd ? i : (s - 1) - i);
                    if (!st.eventsWhile(events, t,
                            decomposeConjParallel, decomposeConjDTernal, decomposeXternal,
                            level))
                        return false;

                    if (changeDT && i < s - 1)
                        t += dt + st.eventRange();
                }


                return true;
            }

        }

        return events.accept(offset, this);
    }


    @Override
    default boolean hasXternal() {
        return dt() == XTERNAL ||
                subterms().hasXternal();
    }

    @Override
    default Term unneg() {
        return op() == NEG ? sub(0) : this;
    }

    @Override
    @Nullable
    default Term normalize(byte varOffset) {
        if (varOffset == 0 && this.isNormalized())
            return this;

        return Op.terms.normalize(this, varOffset);
    }

    @Override
    default Term root() {
        return Op.terms.root(this);
    }


    @Override
    default Term concept() {
        return Op.terms.concept(this);
    }

    @Override
    default int eventRange() {
        Op o = op();
        switch (o) {

            case CONJ:
                Subterms tt = subterms();
                int l = tt.subs();
                if (l == 2) {
                    int dt = dt();

                    switch (dt) {
                        case DTERNAL:
                        case XTERNAL:
                        case 0:
                            dt = 0;
                            break;
                        default:
                            dt = Math.abs(dt);
                            break;
                    }

                    return tt.sub(0).eventRange() + (dt) + tt.sub(1).eventRange();

                } else {
                    int s = 0;


                    for (int i = 0; i < l; i++) {
                        s = Math.max(s, tt.sub(i).eventRange());
                    }

                    return s;
                }

            default:
                return 0;
        }

    }

    @Override
    default int structure() {
        return intifyShallow((s, x) -> s | x.structure(), opBit());
    }

    @Override
    @Nullable
    default Term temporalize(Retemporalize r) {
        return r.transformCompound(this);
    }


    @Override
    default boolean equalsRoot(Term x) {
        if (this.equals(x))
            return true;
        if (!x.hasAny(Op.Temporal))
            return false;


        if (
                op() == x.op()
                        &&
                        structure() == x.structure()
        ) {

            Term root = root();
            return (root != this && root.equals(x)) || root.equals(x.root());
        }

        return false;
    }


    default Term transform(TermTransform f) {
        return transform(f, null, XTERNAL);
    }

    default Term transform(TermTransform f, Op newOp, int newDT) {
        boolean sameOpAndDT = newOp == null;
        Op xop = this.op();


        Op targetOp = sameOpAndDT ? xop : newOp;
        Subterms xx = this.subterms(), yy;

        //try {
        yy = xx.transformSubs(f, targetOp);

//        } catch (StackOverflowError e) {
//            System.err.println("TermTransform stack overflow: " + this + " " + xx + " " + targetOp);
//        }

        if (yy == null)
            return Bool.Null;

        int thisDT = this.dt();
        if (yy == xx && (sameOpAndDT || (xop == targetOp && thisDT == newDT)))
            return this; //no change

        if (targetOp == CONJ) {
            if (yy == Op.FalseSubterm)
                return Bool.False;
            if (yy.subs() == 0)
                return Bool.True;
        }

        if (sameOpAndDT)
            newDT = thisDT;

        return f.transformedCompound(this, targetOp, newDT, xx, yy);
    }

    default Term eventFirst() {
        if (op() == CONJ) {
            if (eventRange() == 0)
                return sub(0); //default to first subterm
            final Term[] first = new Term[1];
            eventsWhile((when, what) -> {
                first[0] = what;
                return false; //done got first
            }, 0, true, true, false, 0);
            return first[0];
        }
        return this;
    }

    /**
     * TODO optimize
     */
    default Term eventLast() {
        if (op() == CONJ) {
            if (eventRange() == 0)
                return sub(0); //default to first subterm
            final Term[] last = new Term[1];
            eventsWhile((when, what) -> {
                last[0] = what;
                return true; //HACK keep going to end
            }, 0, true, true, false, 0);
            return last[0];
        }
        return this;
    }

}























































































    /*
    @Override
    public boolean equals(final Object that) {
        return (that instanceof Term) && (compareTo((Term) that) == 0);
    }
    */









































































































































































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


/**
 * override in subclasses to avoid unnecessary reinit
 */
    /*public CompoundTerm _clone(final Term[] replaced) {
        if (Terms.equals(term, replaced)) {
            return this;
        }
        return clone(replaced);
    }*/





















































    /*static void shuffle(final Term[] list, final Random randomNumber) {
        if (list.length < 2)  {
            return;
        }


        int n = list.length;
        for (int i = 0; i < n; i++) {
            
            int r = i + (randomNumber.nextInt() % (n-i));
            Term tmp = list[i];    
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
            
            Term a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
          }

        }*/















































































































































































