package nars.term.util;

import jcog.TODO;
import jcog.Util;
import jcog.WTF;
import jcog.data.bit.MetalBitSet;
import jcog.data.list.FasterList;
import jcog.util.ArrayUtils;
import nars.NAR;
import nars.Op;
import nars.Param;
import nars.subterm.Subterms;
import nars.term.Compound;
import nars.term.Term;
import nars.term.atom.Bool;
import nars.term.util.builder.TermBuilder;
import nars.time.Tense;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.block.predicate.primitive.BytePredicate;
import org.eclipse.collections.api.block.procedure.primitive.ByteProcedure;
import org.eclipse.collections.api.iterator.LongIterator;
import org.eclipse.collections.api.iterator.MutableByteIterator;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.primitive.LongObjectPair;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectByteHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.ByteHashSet;
import org.jetbrains.annotations.Nullable;
import org.roaringbitmap.ImmutableBitmapDataProvider;
import org.roaringbitmap.PeekableIntIterator;
import org.roaringbitmap.RoaringBitmap;

import java.util.*;
import java.util.function.IntPredicate;

import static java.lang.System.arraycopy;
import static nars.Op.*;
import static nars.term.Terms.sorted;
import static nars.term.atom.Bool.*;
import static nars.time.Tense.*;
import static org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples.pair;

/**
 * representation of conjoined (eternal, parallel, or sequential) events specified in one or more conjunctions,
 * for use while constructing, merging, and/or analyzing
 */
public class Conj extends ByteAnonMap {


    public static final int ROARING_UPGRADE_THRESH = 8;

    /** TermBuilder to use internally */
    private static final TermBuilder terms =
            //HeapTermBuilder.the;
            Op.terms;


    public final LongObjectHashMap<Object> event;


    /**
     * state which will be set in a terminal condition, or upon term construction in non-terminal condition
     */
    private Term term = null;

    public Conj() {
        this(2);
    }


//    /**
//     * but events are unique
//     */
//    public static Conj shareSameTermMap(Conj x) {
//        return new Conj(x.termToId, x.idToTerm);
//    }

    public Conj(ObjectByteHashMap<Term> x, FasterList<Term> y) {
        super(x, y);
        event = new LongObjectHashMap<>(2);
    }

    public Conj(int n) {
        super(n);
        event = new LongObjectHashMap<>(n);
    }

    public static int eventCount(Object what) {
        if (what instanceof byte[]) {
            return indexOfZeroTerminated((byte[]) what, (byte) 0);
        } else {
            return ((ImmutableBitmapDataProvider) what).getCardinality();
        }
    }

//    /**
//     * TODO impl levenshtein via byte-array ops
//     */
//    public static StringBuilder sequenceString(Term a, Conj x) {
//        StringBuilder sb = new StringBuilder(4);
//        int range = a.eventRange();
//        final float stepResolution = 16f;
//        float factor = stepResolution / range;
//        a.eventsWhile((when, what) -> {
//            int step = Math.round(when * factor);
//            sb.append((char) step);
//
//            if (what.op() == NEG)
//                sb.append('-'); //since x.add(what) will store the unneg id
//            sb.append(((char) x.add(what)));
//            return true;
//        }, 0, true, true, false, 0);
//
//        return sb;
//    }

    /**
     * returns null if wasnt contained, Null if nothing remains after removal
     */
    @Nullable
    public static Term conjDrop(Term conj, Term event, boolean earlyOrLate, boolean filterContradiction) {
        if (conj.op() != CONJ || conj.impossibleSubTerm(event))
            return Null;

        int cdt = conj.dt();

        if (dtSpecial(cdt)) {
            Term[] csDropped = conj.subterms().subsExcept(event);

            if (csDropped != null) {
                if (csDropped.length == 1)
                    return csDropped[0];
                else
                    return terms.conj(cdt, csDropped);
            }

            return conj; //no change

        } else {

            Conj c = Conj.from(conj);

//            if (event.op()!=CONJ) {
            return dropEvent(event, earlyOrLate, filterContradiction, c) ? c.term() : Null;
//            } else {
//                //TODO drop in correct sequence order
//                for (Term x : event.subterms()) {
//                    if (!dropEvent(x, earlyOrLate, filterContradiction, c))
//                        return Null; //could not drop
//                }
//                return c.term();
//            }
        }


    }

    @Nullable
    static boolean dropEvent(final Term event, boolean earlyOrLate, boolean filterContradiction, Conj c) {
    /* check that event.neg doesnt occurr in the result.
        for use when deriving goals.
         since it would be absurd to goal the opposite just to reach the desired later
         */
        byte id = c.get(event);
        if (id == Byte.MIN_VALUE)
            return false; //not found


        long targetTime;
        if (c.event.size() == 1) {

            targetTime = c.event.keysView().longIterator().next();
        } else if (earlyOrLate) {
            Object eternalTemporarilyRemoved = c.event.remove(ETERNAL); //HACK
            targetTime = c.event.keysView().min();
            if (eternalTemporarilyRemoved != null) c.event.put(ETERNAL, eternalTemporarilyRemoved); //UNDO HACK
        } else {
            targetTime = c.event.keysView().max();
        }
        assert (targetTime != XTERNAL);

        if (filterContradiction) {


            byte idNeg = (byte) -id;

            final boolean[] contradiction = {false};
            c.event.forEachKeyValue((w, wh) -> {
                if (w == targetTime || contradiction[0])
                    return; //HACK should return early via predicate method

                if ((wh instanceof byte[] && ArrayUtils.indexOf((byte[]) wh, idNeg) != -1)
                        ||
                        (wh instanceof RoaringBitmap && ((RoaringBitmap) wh).contains(idNeg)))
                    contradiction[0] = true;
            });
            if (contradiction[0])
                return false;
        }


        boolean removed = c.remove(targetTime, event);
        if (!removed) {
            return false;
        }
        return true;
    }

//    public static FasterList<LongObjectPair<Term>> eventList(Term t) {
//        return t.eventList(t.dt() == DTERNAL ? ETERNAL : 0, 1, true, true);
//    }

    public static Conj from(Term t) {
        Conj c = new Conj();
        c.add(t.dt() == DTERNAL ? ETERNAL : 0, t);
        return c;
    }

    /**
     * means that the internal represntation of the term is concurrent
     */
    public static boolean concurrentInternal(int dt) {
        switch (dt) {
            case XTERNAL:
            case DTERNAL:
            case 0:
                return true;
        }
        return false;
    }

    /**
     * this refers to an internal concurrent representation but may not be consistent with all cases
     */
    @Deprecated
    public static boolean concurrent(int dt) {
        if (dt == XTERNAL)
            return true; //TEMPORARY
        switch (dt) {
            case XTERNAL:
            case DTERNAL:
            case 0:
                return true;
        }
        return false;
    }

    public static int conjEarlyLate(Term x, boolean earlyOrLate) {
        assert (x.op() == CONJ);
        int dt = x.dt();
        switch (dt) {
            case XTERNAL:
                throw new UnsupportedOperationException();

            case DTERNAL:
            case 0:
                return earlyOrLate ? 0 : 1;

            default: {


                return (dt < 0) ? (earlyOrLate ? 1 : 0) : (earlyOrLate ? 0 : 1);
            }
        }
    }

    public static Term negateEvents(Term x) {
        switch (x.op()) {
            case NEG:
                return negateEvents(x.unneg()).neg();
            case CONJ: {
                Conj c = Conj.from(x);
                c.negateEvents();
                return c.term();
            }
            default:
                return x.neg();
        }
    }

    public static Term dropAnyEvent(Term x, NAR nar) {
        Op oo = x.op();
        boolean negated = (oo == NEG);
        if (negated) {
            x = x.unneg();
            oo = x.op();
        }
        if (oo != CONJ)
            return Null;

        Term y;
        int dt = x.dt();
        if (x.eventRange()==0 || !(x.subterms().hasAny(CONJ))) {
            Subterms xx = x.subterms();
            int ns = xx.subs();
            Random rng = nar.random();
            if (ns == 2) {
                //choose which one will remain
                y = xx.sub(rng.nextInt(ns));
            } else {
                y = terms.conj(dt, xx.subsExcept(rng.nextInt(ns)));
            }
        } else {
            Conj c = from(x);
            long eventAt;
            Random rng = nar.random();
            if (c.event.size() == 1) {
                eventAt = c.event.keysView().longIterator().next();
            } else {
                //choose random event
                long[] events = c.event.keySet().toArray();
                eventAt = events[rng.nextInt(events.length)];
            }
            Object event = c.event.get(eventAt);
            if (event instanceof byte[]) {
                int events = eventCount(event);
                byte b = ((byte[])event)[rng.nextInt(events)];
                boolean removed = c.remove(eventAt, b);
                assert(removed);
            } else {
                throw new TODO();
            }
            y =c.term();
//                FasterList<LongObjectPair<Term>> ee = Conj.eventList(x);
//                ee.remove(nar.random().nextInt(ee.size()));
//                y = Conj.conj(ee);
//                if (y.equals(x))
//                    return Null;
//                assert (y != null);
        }
        return y.negIf(negated);
    }

    private void negateEvents() {
        event.forEachValue(x -> {
            if (!(x instanceof byte[]))
                throw new TODO();
            byte[] bx = (byte[]) x;
            for (int i = 0; i < bx.length; i++) {
                byte b = bx[i];
                if (b == 0) break; //null terminator
                bx[i] = (byte) -b;
            }
        });
    }


    public static Term conj(FasterList<LongObjectPair<Term>> events) {
        int eventsSize = events.size();
        switch (eventsSize) {
            case 0:
                return Null;
            case 1:
                return events.get(0).getTwo();
        }

        Conj ce = new Conj(eventsSize);

        for (LongObjectPair<Term> o : events) {
            if (!ce.add(o.getOne(), o.getTwo())) {
                break;
            }
        }

        return ce.term();
    }

    public static Term conj(Collection<LongObjectPair<Term>> events) {
        int eventsSize = events.size();
        switch (eventsSize) {
            case 0:
                return Null;
            case 1:
                return events.iterator().next().getTwo();
        }

        Conj ce = new Conj();

        for (LongObjectPair<Term> o : events) {
            if (!ce.add(o.getOne(), o.getTwo())) {
                break;
            }
        }

        return ce.term();
    }

    public static Term without(Term include, Term exclude, boolean excludeNeg) {
        if (excludeNeg) {
            if (include.unneg().equals(exclude.unneg()))
                return True;
        } else {
            if (include.equals(exclude))
                return True;
        }

        if (include.op() != CONJ || include.impossibleSubTerm(excludeNeg ? exclude.unneg() : exclude))
            return include;

        Subterms s = include.subterms();
        int dt = include.dt();
        if (dtSpecial(dt) && !s.hasAny(Op.CONJ)) {

            //try positive first
            Term[] ss = s.subsExcept(exclude);
            if (ss != null) {
                return terms.conj(dt, ss);
            } else {
                //try negative next
                if (excludeNeg) {
                    ss = s.subsExcept(exclude.neg());
                    if (ss != null) {
                        return terms.conj(dt, ss);
                    }
                }

                return include; //not found
            }

        } else {

            Conj xx = Conj.from(include);
            if (xx.removeEventsByTerm(exclude, true, excludeNeg)) {
                return xx.term();
            } else {
                return include;
            }
        }
    }

    public static Term withoutAll(Term include, Term exclude) {
        return withoutAll(include, exclude, false);
    }

    /**
     * if same timing, does not allow relative shift
     */
    public static Term withoutAll(Term include, Term exclude, boolean atExactTime) {
        if (include.op() != CONJ)
            return include;

        if (include.equals(exclude))
            return True;

        if (exclude.op() != CONJ)
            return without(include, exclude, false);


        if (concurrent(include.dt()) && concurrent(exclude.dt())) {

            Subterms es = exclude.subterms();
            @Nullable MutableSet<Term> ii = include.subterms().toSet(i -> !es.contains(i));
            if (ii != null && (ii.size() < include.subs())) {
                return terms.conj(include.dt(), ii.toArray(EmptyTermArray));
            } else {
                return include; //no change
            }

        } else {


            Conj x = Conj.from(include);
            int idt = include.dt();
            boolean[] removed = new boolean[]{false};
            exclude.eventsWhile((when, what) -> {
                //removed[0] |= x.remove(what, when);
                removed[0] |= atExactTime ? x.remove(when, what) : x.removeAll(what);
                return true;
            }, idt == DTERNAL ? ETERNAL : 0, true, exclude.dt() == DTERNAL, false, 0);

            return removed[0] ? x.term() : include;
        }
    }

    public boolean removeAll(Term what) {
        byte id = get(what);
        if (id != Byte.MIN_VALUE) {
            long[] events = event.keySet().toArray(); //create an array because removal will interrupt direct iteration of the keySet
            boolean removed = false;
            for (long e : events) {
                removed |= remove(e, what);
            }
            return removed;
        }
        return false;
    }

    static public Term sequence(Term a, long aStart, Term b, long bStart) {

//        if (aStart == 0 && a.eventRange() == 0)
//            return CONJ.the(a, Tense.occToDT(bStart), b); //HACK use an optimized internable construction route

        Conj c = new Conj();
//        if (aStart == bStart) {
//            if (c.addAuto(a)) {
//                c.addAuto(b);
//            }
//        } else {
        if (c.add(aStart, a)) {
            c.add(bStart, b);
        }
//        }
        return c.term();
    }

    private static int indexOfZeroTerminated(byte[] b, byte val) {
        for (int i = 0; i < b.length; i++) {
            byte bi = b[i];
            if (val == bi)
                return i;
            else if (bi == 0)
                return -1;
        }
        return -1;
    }

    private static Term conjSeq(FasterList<LongObjectPair<Term>> events) {
        return conjSeq(events, 0, events.size());
    }

    /**
     * constructs a correctly merged conjunction from a list of events, in the sublist specified by from..to (inclusive)
     * assumes that all of the event terms have distinct occurrence times
     */
    private static Term conjSeq(List<LongObjectPair<Term>> events, int start, int end) {

        LongObjectPair<Term> first = events.get(start);
        int ee = end - start;
        switch (ee) {
            case 0:
                throw new NullPointerException("should not be called with empty events list");
            case 1:
                return first.getTwo();
            case 2: {
                LongObjectPair<Term> second = events.get(end - 1);
                long a = first.getOne();
                long b = second.getOne();
                return conjSeqFinal(
                        (int) (b - a),
                        /* left */ first.getTwo(), /* right */ second.getTwo());
            }
        }

        int center = start + (end - 1 - start) / 2;


        Term left = conjSeq(events, start, center + 1);
        if (left == Null) return Null;
        if (left == False) return False;

        Term right = conjSeq(events, center + 1, end);
        if (right == Null) return Null;
        if (right == False) return False;

        int dt = (int) (events.get(center + 1).getOne() - first.getOne() - left.eventRange());

        return conjSeqFinal(dt, left, right);
    }

    private static Term conjSeqFinal(int dt, Term left, Term right) {
        assert (dt != XTERNAL);

        if (left == Null) return Null;
        if (right == Null) return Null;


        if (left == False) return False;
        if (right == False) return False;

        if (left == True) return right;
        if (right == True) return left;

        if (left.compareTo(right) > 0) {

            dt = -dt;
            Term t = right;
            right = left;
            left = t;
        }

        if (left.op() == CONJ && right.op() == CONJ) {
            int ldt = left.dt(), rdt = right.dt();
            if (ldt != XTERNAL && !concurrent(ldt) && rdt != XTERNAL && !concurrent(rdt)) {
                int ls = left.subs(), rs = right.subs();
                if ((ls > 1 + rs) || (rs > ls)) {

                    return terms.conj(dt, left, right);
                }
            }
        }


        //return Op.compound(CONJ, dt, left, right);
        return terms.theCompound(CONJ, dt, left, right);
    }

//    /**
//     * similar to conjMerge but interpolates events so the resulting
//     * intermpolation is not considerably more complex than either of the inputs
//     * assumes a and b are both conjunctions
//     */
//    public static Term conjIntermpolate(Term a, Term b, float aProp, long bOffset) {
//
////        if (bOffset == 0) {
////            if (a.subterms().equals(b.subterms())) {
////                //special case: equal subs
////                Term ab = a;
////                if (a.op() == CONJ && Conj.concurrent(a.dt()))
////                    ab = b; //ab if conj must be non-concurrent as a seed for correctly ordering a result
////                return ab.dt(Revision.chooseDT(a, b, aProp, nar));
////            }
////        }
////return Bool.Null; //probably better not to bother
//
//        return new Conjterpolate(a, b, bOffset, aProp).term();
//
//    }

    public void clear() {
        super.clear();
        event.clear();
        term = null;
    }

    public int conflictOrSame(long at, byte what) {
        return conflictOrSame(event.get(at), what);
    }

    static int conflictOrSame(Object e, byte id) {
        if (e == null) {

        } else if (e instanceof RoaringBitmap) {
            RoaringBitmap r = (RoaringBitmap) e;
            if (r.contains(-id))
                return -1;
            else if (r.contains(id)) {
                return +1;
            }
        } else if (e instanceof byte[]) {
            byte[] r = (byte[]) e;
            if (indexOfZeroTerminated(r, (byte) -id) != -1)
                return -1;
            else if (indexOfZeroTerminated(r, id) != -1)
                return +1;
        }
        return 0;
    }

    public final Conj with(long at, Term x) {
        add(at, x);
        return this;
    }

    /**
     * returns false if contradiction occurred, in which case this
     * ConjEvents instance is
     * now corrupt and its result via .term() should be considered final
     */
    public boolean add(long at, Term x) {
        if (term != null)
            throw new RuntimeException("already concluded: " + term);

        if (x instanceof Compound && x.op() == CONJ) {
            int xdt = x.dt();

            if (xdt == DTERNAL) {
                if (at == ETERNAL) {
                    Subterms tt = x.subterms();
                    if (tt.hasAny(CONJ)) {
                        //add any contained sequences first
                        return tt.AND((ttt) ->
                                !(ttt.op() == CONJ && !Tense.dtSpecial(ttt.dt())) || add(0, ttt)
                        ) && tt.AND((ttt) ->
                                (ttt.op() == CONJ && !Tense.dtSpecial(ttt.dt())) || add(ETERNAL, ttt)
                        );
                    }
                }
            }

            if (at != ETERNAL || (xdt == 0) || (xdt == DTERNAL)) {
                return added(x.eventsWhile(this::addEvent, at,
                        at != ETERNAL, //unpack parallel except in DTERNAL root, allowing: ((a&|b) && (c&|d))
                        true,
                        false, 0));
            }
        }

        return added(addEvent(at, x));
    }

    private boolean added(boolean success) {
        if (success)
            return true;
        else {
            if (term == null)
                term = False;
            return false;
        }
    }

    public boolean add(Term t, long start, long end, int maxSamples, int minSegmentLength) {
        if ((start == end) || start == ETERNAL) {
            return add(start, t);
        } else {
            if (maxSamples == 1) {

                return add((start + end) / 2L, t);
            } else {

                long dt = Math.max(minSegmentLength, (end - start) / maxSamples);
                long x = start;
                while (x < end) {
                    if (!add(x, t))
                        return false;
                    x += dt;
                }
                return true;
            }
        }
    }

    private boolean addEvent(long at, Term x) {
//        if (Param.DEBUG) {
//            if (at == DTERNAL) //HACK
//                throw new WTF("probably meant at=ETERNAL not DTERNAL");
//        }

        if (x instanceof Bool) {
            //short circuits
            if (x == True)
                return true;
            else if (x == False) {
                this.term = False;
                return false;
            } else if (x == Null) {
                this.term = Null;
                return false;
            }
        }

        //test this first
        boolean polarity;
        if (x.op() == NEG) {
            polarity = false;

//            //&& DTERNAL -> parallel in sequence event
//            Term ux = x.unneg();
//            if (ux.op() == CONJ && ux.dt() == DTERNAL && at != ETERNAL)
//                x = ux.dt(0).neg();
        } else {
            polarity = true;

//            //&& DTERNAL -> parallel in sequence event
//            if (x.op() == CONJ && x.dt() == DTERNAL && at != ETERNAL)
//                x = x.dt(0);
        }

        //quick test for conflict with existing ETERNALs
        Object eternalEvents = event.get(ETERNAL);
        if (eternalEvents!=null && eventCount(eternalEvents)>0) {
            if (!eventsAND(eternalEvents, (b)->{
                if (unindex(b).equalsNeg(x))
                    return false;
                return true;
            })) {
                this.term = False;
                return false;
            }
        }

        byte id = add(polarity ? x : x.unneg());

        if (!polarity) id = (byte) -id;

        switch (addFilter(at, x, id)) {
            case +1:
                return true; //ignore and continue
            case 0:
                break; //continue
            case -1:
                return false; //reject and fail
        }

        return addEvent(at, id, x);
    }

    private boolean addEvent(long at, byte id) {
        Term xx = unindex(id);
        return addEvent(at, id, xx);
    }


    private boolean addEvent(long at, byte id, Term x) {

        Object events = event.getIfAbsentPut(at, () -> new byte[ROARING_UPGRADE_THRESH]);
        if (events instanceof byte[]) {
            byte[] b = (byte[]) events;
            for (int i = 0; i < b.length; i++) {
                byte bi = b[i];
                if (bi != 0) {
                    if (id == -bi)
                        return false; //contradiction
                    if (id == bi)
                        return true; //found existing

                    Term result = merge(bi, x, at == ETERNAL);

                    if (result != null) {
                        if (result == True)
                            return true; //absorbed input
                        if (result == False || result == Null) {
                            this.term = result;
                            return false;
                        } else {
                            if (i < b.length - 1) {
                                arraycopy(b, i + 1, b, i, b.length - 1 - i);
                                i--; //compactify
                            } else
                                b[i] = 0; //erase disjunction, continue comparing. the result remains eligible for add

                            return addEvent(at, result);
                            //merged.add(result);
                            //continue

                        }
                    }
                } else {
                    //empty slot, take
                    b[i] = id;
                    return true;
                }
            }

            //no remaining capacity, upgrade to RoaringBitmap

            RoaringBitmap rb = new RoaringBitmap();
            for (byte bb : b)
                rb.add(bb);
            rb.add(id);
            event.put(at, rb);


            return true;
        } else {
            return todoOrFalse();
        }
    }

    /**
     * allows subclass implement different behavior.
     * <p>
     * return:
     * -1: ignore and fail the conjunction
     * 0: default, continue
     * +1: ignore and continue
     */
    protected int addFilter(long at, Term x, byte id) {
        return 0;
    }


    /**
     * merge an incoming term with a disjunctive sub-expression (occurring at same event time) reductions applied:
     * ...
     */
    private static Term disjunctify(Term existing, Term incoming, boolean eternal) {
        Term existingUnneg = existing.unneg();
        Term incomingUnneg = incoming.unneg();
        if (incoming.op() == NEG && incomingUnneg.op() == CONJ) {
            return disjunctionVsDisjunction(existingUnneg, incomingUnneg, eternal);
        } else {
            return disjunctionVsNonDisjunction(incoming, eternal, existingUnneg);
        }
    }

    @Nullable
    private static Term disjunctionVsNonDisjunction(Term incoming, boolean eternal, Term existingUnneg) {
        final Term[] result = new Term[1];
        existingUnneg.eventsWhile((when, what) -> {
            if (eternal || when == 0) {
                if (incoming.equalsNeg(what)) {
                    //overlap with the option so annihilate the disj
                    result[0] = True;
                    return false; //stop iterating
                } else if (incoming.equals(what)) {
                    //contradict
                    result[0] = False;
                    return false;
                }
            }
            return eternal || when <= 0;
        }, 0, true, true, false, 0);


        if (result[0] == True) {
            return incoming; //disjunction annihilated
        }

        if (result[0] == False) {
            //removing the matching subterm from the disjunction and reconstruct it
            //then merge the incoming term

            Term existingShortenedUnneg;
            if (eternal) {
                existingShortenedUnneg = Conj.without(existingUnneg, incoming, false);
            } else {
                //carefully remove the contradicting first event
                existingShortenedUnneg = Conj.conjDrop(existingUnneg, incoming, true, false);

            }

            int dt = eternal ? DTERNAL : 0;

            if (existingShortenedUnneg.equals(incoming)) //quick test
                return False;
            if (existingShortenedUnneg.equals(existingUnneg))
                return True; //unaffected

            //also coudl test for && contains if dtspecial etc


            if (existingShortenedUnneg.op() == CONJ && incoming.op() == CONJ) {
                return terms.theSortedCompound(CONJ, dt, existingShortenedUnneg.neg(), incoming);
            } else {
                return terms.conj(dt, existingShortenedUnneg.neg(), incoming);
            }

        }

        return null; //no interaction

    }


    /**
     * stage 2: a and b are both the inner conjunctions of disjunction terms being compared for contradiction or factorable commonalities.
     * a is the existing disjunction which can be rewritten.  b is the incoming
     */
    private static Term disjunctionVsDisjunction(Term a, Term b, boolean eternal) {
        int adt = a.dt(), bdt = b.dt();
        boolean bothCommute = (adt == 0 || adt == DTERNAL) && (bdt == 0 || bdt == DTERNAL);
        if (bothCommute) {
            {
                if ((adt == bdt || adt == DTERNAL || bdt == DTERNAL)) {
                    //factor out contradicting subterms
                    MutableSet<Term> aa = a.subterms().toSet();
                    MutableSet<Term> bb = b.subterms().toSet();
                    Iterator<Term> bbb = bb.iterator();
                    boolean change = false;
                    while (bbb.hasNext()) {
                        Term bn = bbb.next();
                        if (aa.remove(bn.neg())) {
                            bbb.remove(); //both cases allowed; annihilate both
                            change = true;
                        }
                    }
                    if (change) {
                        //reconstitute the two terms, glue them together as a new super-disjunction to replace the existing (and interrupt adding the incoming)

                        Term A = terms.conj(adt, sorted(aa)).neg();
                        if (A == False || A == Null || aa.equals(bb))
                            return A;

                        Term B = terms.conj(bdt, sorted(bb)).neg();
                        if (B == True || B == False || B == Null || A.equals(B))
                            return B;

                        return terms.conj(eternal ? DTERNAL : 0, A, B);
                    }
                }
            }
            return null; //no change
        } else {
            //TODO sequence conditions
            //throw new TODO(a + " vs " + b);
            return Null; //disallow for now. the complexity might be excessive
        }
    }


    private static Term conjoinify(Term conj, Term incoming, boolean eternal) {

        int dtOuter = eternal ? DTERNAL : 0;

        if (incoming.op() != CONJ) {

            int dtInner = conj.dt();

            Subterms cs = conj.subterms();

            if (dtSpecial(dtInner)) {
                if (cs.containsNeg(incoming))
                    return False; //contradiction
                else if (cs.contains(incoming))
                    return True; //present, ignore

                if (!cs.hasAny(Op.CONJ)) {
                    return terms.conj(dtOuter, subAppend(cs, incoming));

//                    if (dtInner == dtOuter) {
//                        //commutive merge
//                        return terms.theSortedCompound(CONJ, dtOuter, subAppend(cs, incoming));
//                    } else {
//                        return terms.theSortedCompound(CONJ, dtOuter, conj, incoming);
//                    }
                }
            }


            boolean innerCommute = Tense.dtSpecial(dtInner) && !conj.subterms().hasAny(Op.CONJ);

            Conj c = !innerCommute ? new Conj() : null;
            FasterList<Term> cx = innerCommute ? new FasterList() : null;
//            boolean incomingHasConj = incoming.hasAny(CONJ);
            boolean ok = conj.eventsWhile((whn, wht) -> {
                Term ww;
                if (wht.equals(incoming))
                    ww = wht;
                else if (wht.equalsNeg(incoming))
                    return false;
                else {
                    ww = terms.conj(dtOuter, wht, incoming);

                    if (ww == False || ww == Null)
                        return false;
                    if (ww == True)
                        return true;
                    if (ww != wht && ww.equals(wht))
                        ww = wht; //use original if possible
                }


                if (innerCommute) {
                    cx.add(ww);
                    return true;
                } else {
                    return c.add(whn, ww);
                }

            }, 0, dtInner == 0, dtInner == DTERNAL, dtInner == XTERNAL, 0);
            if (!ok)
                return False;

            Term d = innerCommute ? terms.conj(dtInner, cx.toArrayRecycled(Term[]::new))
                        :
                        c.term();

            if (d == False || d == Null)
                return d; //fail

            if (d == conj || (d != conj && d.equals(conj)))
                return True;  //no change since the incoming has been absorbed

            //all original subterms remain intact, return simplified factored version
            //return terms.theSortedCompound(CONJ, dtOuter, conj, incoming);
            return d;

        } else {
            assert(incoming.dt()!=dtOuter);
            return terms.theSortedCompound(CONJ, dtOuter, conj, incoming);
        }

    }

    private static Term[] subAppend(Subterms subterms, Term x) {
        int n = subterms.subs();
        Term[] y = subterms.arrayClone(new Term[n + 1]);
        y[n] = x;
        return y;
    }

    /**
     * @param existingId
     * @param incoming
     * @param eternal    * @return codes:
     *                   * True - absorb and ignore the incoming
     *                   * Null/False - short-circuit annihilation due to contradiction
     *                   * non-null - merged value.  if merged equals current item, as-if True returned. otherwise remove the current item, recurse and add the new merged one
     *                   * null - do nothing, no conflict.  add x to the event time
     */
    private Term merge(byte existingId, Term incoming, boolean eternal) {
        boolean existingPolarity = existingId > 0;
        Term existingUnneg = idToTerm.get((existingPolarity ? existingId : -existingId) - 1);
        Term existing = existingUnneg.negIf(!existingPolarity);
        if (existing.equals(incoming))
            return True; //exact same
        if (existing.equalsNeg(incoming))
            return False; //contradiction

        Term incomingUnneg = incoming.unneg();
        if (!Term.commonStructure(existingUnneg, incomingUnneg))
            return null; //no potential for interaction

        boolean xConj = incomingUnneg.op() == CONJ;
        boolean eConj = existingUnneg.op() == CONJ;
        Term result;
        if (eConj && xConj) {
            //TODO decide which is larger
            result = merge(existing, existingPolarity, incoming, eternal);
        } else if (eConj) {
            result = merge(existing, existingPolarity, incoming, eternal);
        } else if (xConj) {
            boolean incomingPolarity = incoming.op() != NEG;
            result = merge(incoming, incomingPolarity, existing, eternal);
        } else {
            return null; //neither a conj/disj
        }
        if (result != null && result.equals(existing))
            return True;
        return result;
    }

    private static Term merge(Term conj, boolean conjPolarity, Term x, boolean eternal) {
        return conjPolarity ? conjoinify(conj, x, eternal) : disjunctify(conj, x, eternal);
    }

    /**
     * @return non-zero byte value
     */
    private byte add(Term t) {
        assert (t != null && !(t instanceof Bool));
        return termToId.getIfAbsentPutWithKey(t.unneg(), tt -> {
            //int s = termToId.size();
            int s = idToTerm.addAndGetSize(tt);
            assert (s < Byte.MAX_VALUE);
            return (byte) s;
        });
    }

    /**
     * returns index of an item if it is present, or -1 if not
     */
    private byte index(Term t) {
        return termToId.getIfAbsent(t.unneg(), (byte) -1);
    }

    private Term unindex(byte id) {
        Term x = idToTerm.get(Math.abs(id)-1);
        if (x == null)
            throw new NullPointerException();
        return x.negIf(id < 0);
    }


    private byte get(Term x) {
        boolean neg;
        if (neg = (x.op() == NEG))
            x = x.unneg();
        byte index = index(x);
        if (index == -1)
            return Byte.MIN_VALUE;

        if (neg)
            index = (byte) (-index);

        return index;
    }

    public boolean remove(long at, Term t) {

        byte i = get(t);
        return remove(at, i);
    }

    public boolean remove(long at, byte what) {
        if (what == Byte.MIN_VALUE)
            return false;

        Object o = event.get(at);
        if (o == null)
            return false;


        if (removeFromEvent(at, o, true, what) != 0) {
            term = null;
            return true;
        }
        return false;
    }


    /**
     * returns:
     * +2 removed, and now this event time is empty
     * +1 removed
     * +0 not removed
     */
    private int removeFromEvent(long at, Object o, boolean autoRemoveIfEmpty, int... i) {
        if (o instanceof RoaringBitmap) {
            boolean b = false;
            RoaringBitmap oo = (RoaringBitmap) o;
            for (int ii : i)
                b |= oo.checkedRemove(ii);
            if (!b) return 0;
            if (oo.isEmpty()) {
                if (autoRemoveIfEmpty)
                    event.remove(at);
                return 2;
            } else {
                return 1;
            }
        } else {
            byte[] b = (byte[]) o;

            int num = ArrayUtils.indexOf(b, (byte) 0);
            if (num == -1) num = b.length;

            int removals = 0;
            for (int ii : i) {
                assert (ii != 0);
                int bi = ArrayUtils.indexOf(b, (byte) ii, 0, num);
                if (bi != -1) {
                    //if (b[bi] != 0) {
                    b[bi] = 0;
                    removals++;
                    //}
                }
            }

            if (removals == 0)
                return 0;
            else if (removals == num) {
                if (autoRemoveIfEmpty)
                    event.remove(at);
                return 2;
            } else {


                //sort all zeros to the end
                ArrayUtils.sort(b, 0, num - 1, (byte x) -> x == 0 ? Byte.MIN_VALUE : x);

//                MetalBitSet toRemove = MetalBitSet.bits(b.length);
//
//                for (int zeroIndex = 0; zeroIndex < b.length; zeroIndex++) {
//                    if (b[zeroIndex] == 0)
//                        toRemove.set(zeroIndex);
//                }
//
//                b = ArrayUtils.removeAll(b, toRemove);
//                event.put(at, b);
                return 1;
            }
        }
    }

    public boolean removeEventsByTerm(Term t, boolean pos, boolean neg) {

        boolean negateInput;
        if (t.op() == NEG) {
            negateInput = true;
            t = t.unneg();
        } else {
            negateInput = false;
        }

        int i = get(t);
        if (i == Byte.MIN_VALUE)
            return false;

        int[] ii;
        if (pos && neg) {
            ii = new int[]{i, -i};
        } else if (pos) {
            ii = new int[]{negateInput ? -i : i};
        } else if (neg) {
            ii = new int[]{negateInput ? i : -i};
        } else {
            throw new UnsupportedOperationException();
        }


        final boolean[] removed = {false};
        LongArrayList eventsToRemove = new LongArrayList(4);
        event.forEachKeyValue((when, o) -> {
            int result = removeFromEvent(when, o, false, ii);
            if (result == 2) {
                eventsToRemove.add(when);
            }
            removed[0] |= result > 0;
        });

        if (!eventsToRemove.isEmpty()) {
            eventsToRemove.forEach(event::remove);
        }

        if (removed[0]) {
            term = null;
            return true;
        }
        return false;
    }

    public Term term() {
        if (term != null)
            return term;


        int numEvents = event.size();
        if (numEvents == 0)
            return True;

        factor();

        IntPredicate validator = null;
        Object eternalWhat = event.get(ETERNAL);
        Term eternal = eternalWhat != null ? term(ETERNAL, eternalWhat) : null;
        if (eternal != null) {

            if (eternal instanceof Bool)
                return this.term = eternal;

//            //flatten inner conjunctions
//            if (eternal.op() == CONJ) {
//                Subterms es = eternal.subterms();
//                if (es.hasAny(CONJ)) {
//                    eternal = flatten(eternal, es, DTERNAL);
////                    eternal = flatten(eternal, es, 0);
//                }
//            }

            if (numEvents > 1) {


                if (eternal.op() == CONJ) {

                    if (eternalWhat instanceof byte[]) {
                        byte[] b = (byte[]) eternalWhat;
                        validator = (i) -> indexOfZeroTerminated(b, (byte) -i) == -1;
                    } else {
                        RoaringBitmap b = (RoaringBitmap) eternalWhat;
                        validator = (i) -> !b.contains(-i);
                    }


                } else {
                    Term finalEternal = eternal;
                    validator = (t) -> !finalEternal.equalsNeg(idToTerm.get(Math.abs(t - 1)).negIf(t < 0));
                }
            }
        }

        Term ci;
        if (eternal != null && numEvents == 1) {
            ci = eternal;
        } else {
            FasterList<LongObjectPair<Term>> temporals = null;
            for (LongObjectPair next : event.keyValuesView()) {
                long when = next.getOne();
                if (when == ETERNAL)
                    continue;

                Term wt = term(when, next.getTwo(), validator);

                //TODO merge this with validator
                if (eternal != null && !(wt instanceof Bool) && (
                        Term.commonStructure(wt, eternal) && (
                                eternal.equalsNeg(wt) || eternal.containsNeg(wt) ||
                                        wt.equalsNeg(eternal) || wt.containsNeg(eternal)
                        )
                )) {
                    wt = False;
                }

                if (wt == True) {
                    continue;
                } else if (wt == False) {
                    return this.term = False;
                } else if (wt == Null) {
                    return this.term = Null;
                }

                if (temporals == null)
                    temporals = new FasterList<>(4);

                temporals.add(pair(when, wt));
            }
            Term temporal;
            if (temporals != null) {

                int ee = temporals.size();
                switch (ee) {
                    case 0:
                        temporal = null;
                        break;
                    case 1:
                        temporal = temporals.get(0).getTwo();
                        break;
                    default:
                        temporals.sortThisBy(LongObjectPair::getOne);
                        temporal = conjSeq(temporals);
                        break;
                }
            } else
                temporal = null;

            if (eternal != null && temporal != null) {
                if (temporal.equalsNeg(eternal))
                    return False;
                ci = terms.conj(DTERNAL, temporal, eternal);

//                //int etdt = temporal.dt() == 0 ? 0 /* promote */ : DTERNAL;
//                if (!eternal.hasAny(CONJ)) {
//                    if (temporal.equalsNeg(eternal))
//                        return False;
//                    //ci = terms.theSortedCompound(CONJ, DTERNAL, temporal, eternal);
//                    ci = terms.conj(DTERNAL, temporal, eternal);
//                } else {
//
////                    if (eternal.op() == CONJ && (eternal.dt() == DTERNAL)) {
////                        //flatten dternal subterms
////                        ci = terms.conj(DTERNAL, subAppend(eternal.subterms(), temporal));
////                    } else {
////
////                        try {
//                            ci = terms.conj(DTERNAL, temporal, eternal);
////                        } catch (StackOverflowError e) {
////                            throw new WTF("stack overflow constructing: (" + temporal + " && " + eternal + ")"); //TEMPORARY
////                        }
////                    }
//                }


            } else if (eternal == null) {
                ci = temporal;
            } else /*if (temporal == null)*/ {
                ci = eternal;
            }
        }

        if (ci == null)
            return True;

        if (ci.op() == CONJ && ci.hasAny(NEG)) {
            Subterms cci;
            if ((cci = ci.subterms()).hasAny(CONJ)) {
                int ciDT = ci.dt();
                if (ciDT == 0 || ciDT == DTERNAL) {
                    int s = cci.subs();
                    RoaringBitmap ni = null, nc = null;
                    for (int i = 0; i < s; i++) {
                        Term cii = cci.sub(i);
                        if (cii.op() == NEG) {
                            Term cInner = cii.unneg();
                            if (cInner.op() == CONJ && cInner.dt() == ciDT /* same DT */) {
                                if (nc == null) nc = new RoaringBitmap();
                                nc.add(i);
                            } else {
                                if (ni == null) ni = new RoaringBitmap();
                                ni.add(i);
                            }
                        }
                    }
                    if (nc != null && ni != null) {
                        int[] bb = ni.toArray();
                        MetalBitSet toRemove = MetalBitSet.bits(bb.length);
                        PeekableIntIterator ncc = nc.getIntIterator();
                        while (ncc.hasNext()) {
                            int nccc = ncc.next();
                            for (int aBb : bb) {
                                Term NC = cci.sub(nccc).unneg();
                                Term NX = cci.sub(aBb).unneg();
                                if (NC.contains(NX)) {
                                    toRemove.set(nccc);
                                }
                            }
                        }
                        if (toRemove.cardinality() > 0) {
                            return terms.conj(ciDT, cci.subsExcept(toRemove));
                        }
                    }


                }
            }
        }
        return ci;
    }

    /** factor common temporal event components to an ETERNAL component */
    private void factor() {

        RichIterable<LongObjectPair<Object>> events = event.keyValuesView();
        int numTemporalEvents = events.count(l -> l.getOne()!=ETERNAL);
        int numTemporalCompoundEvents = events.count(l -> l.getOne()!=ETERNAL && eventCount(l.getTwo())>1);
        if (numTemporalCompoundEvents <= 1 || numTemporalCompoundEvents!=numTemporalEvents)
            return;

        ByteHashSet common = new ByteHashSet();
        //TODO if this is iterated in order of least # of events at each time first, it is optimal
        if (!events.allSatisfy((whenWhat)->{
            long when = whenWhat.getOne();
            if (when ==ETERNAL)
                return true;
            Object what = whenWhat.getTwo();
            if (what instanceof byte[]) {
                byte[] bWhat = (byte[])what;
                if (common.isEmpty()) {
                    //add the first set of events
                    events(bWhat, common::add);
                } else {
                    if (common.removeIf(c -> !eventsContains(bWhat, c))) {
                        if (common.isEmpty())
                            return false; //done //need to keep iterator
                    }
                }

                return true;
            } else {
                throw new TODO();
            }
        }))
            return;

        assert(!common.isEmpty());

        long[] eventTimes = new long[numTemporalCompoundEvents];
        final int[] e = {0};
        final int[] maxSlotEvents = {0};
        if (events.anySatisfy((whenWhat)->{
            long when = whenWhat.getOne();
            if (when ==ETERNAL)
                return false; //skip the eternal event
            Object what = whenWhat.getTwo();
            eventTimes[e[0]++] = when;

            if (what instanceof byte[]) {
                if (eventsAND(((byte[])what), common::contains))
                    return true; //all would be eliminated at this time slot
            }
            return false;
        }))
            return;

        int commonCount = common.size();
        //nothing in common
        if (commonCount != 0) {//verify that no event time is completely eliminated by the common terms
            if (maxSlotEvents[0] == commonCount)
                return;

            MutableByteIterator ii = common.byteIterator();
            while (ii.hasNext()) {
                byte f = ii.next();
                if (addEvent(ETERNAL, f)) {
                    for (long ee : eventTimes) {
                        if (!remove(ee, f))
                            throw new WTF();
                    }
                } else
                    throw new WTF();
            }
        }
    }

    private static boolean eventsContains(byte[] events, byte b) {
        return ArrayUtils.contains(events, b);
    }

    private static void events(byte[] events, ByteProcedure each) {
        for (byte e : events) {
            if (e!=0) {
                each.value(e);
            } else
                break; //null-terminator
        }
    }

    private static boolean eventsAND(Object events, BytePredicate each) {
        if (events instanceof byte[])
            return eventsAND((byte[])events, each);
        else
            throw new TODO();
    }

    private static boolean eventsAND(byte[] events, BytePredicate each) {
        for (byte e : events) {
            if (e!=0) {
                if (!each.accept(e))
                    return false;
            } else
                break; //null-terminator
        }
        return true;
    }


//    private static void flattenInto(Collection<Term> ee, Term ex, int dt) {
//        if (ex.op() == CONJ && ex.dt() == dt)
//            ex.subterms().forEach(eee -> flattenInto(ee, eee, dt));
//        else
//            ee.add(ex);
//    }

    public long shift() {
        long min = Long.MAX_VALUE;
        LongIterator ii = event.keysView().longIterator();
        while (ii.hasNext()) {
            long t = ii.next();
            if (t != DTERNAL) {
                if (t < min)
                    min = t;
            }
        }
        return min;
    }

    public Term term(long when) {
        return term(when, event.get(when), null);
    }

    private Term term(long when, Object what) {
        return term(when, what, null);
    }

    private Term term(long when, Object what, IntPredicate validator) {

        final byte[] b;
        int n = eventCount(what);
        if (n == 0)
            return True; //does this happen?

        if (what instanceof byte[]) {
            //rb = null;
            b = (byte[]) what;
            indexOfZeroTerminated(b, (byte) 0); //TODO cardinality(byte[] b)
        } else {
            return todoOrNull();
//            b = null;
//            rb = (RoaringBitmap) what;
//            n = rb.getCardinality();
        }

        if (n == 1) {
            //only event at this time
            return sub(b[0], null, validator);
        }

        TreeSet<Term> t = null;
        final boolean[] negatives = {false};

        for (byte x : b) {
            if (x == 0)
                break; //null-terminator reached
            Term s = sub(x, negatives, validator);
            if (s instanceof Bool) {
                if (s == False || s == Null)
                    return s;
                else {
                    //ignore True case
                }
            } else {

                if (t == null)
                    t = new TreeSet();

//                if (s.op() == CONJ && Tense.dtSpecial(s.dt()) && (s.dt() == when)) {
//                    for (Term ss : s.subterms())
//                        flattenDternalInto(t, ss);
//                } else {
                t.add(s);
//                }
            }
        }


        int ts = t != null ? t.size() : 0;
        switch (ts) {
            case 0:
                return True;
            case 1:
                return t.first();
            default: {
                int cdt = when == ETERNAL ? DTERNAL : 0;
                if (Util.or(z -> z.dt() == cdt && z.op() == CONJ, t)) {
                    //recurse: still flattening to do
                    //sorta works:
                    //return terms.conj(cdt, t.toArray(EmptyTermArray));

                    //this stack overflows
                    //give up for now, too complex

                    return todoComplicated();

                } else {
                    return terms.theSortedCompound(CONJ, cdt, t);
                    //return terms.conj(cdt, t.toArray(Op.EmptyTermArray));
                    //return Op.compound(CONJ, cdt, Terms.sorted(t));
                }

            }

        }
    }


    private static Term todoComplicated() {
        if (Param.DEBUG_EXTRA)
            throw new WTF();
        return Null;
    }

    private static Term todoOrNull() {
        if (Param.DEBUG)
            throw new TODO();
        else
            return Null;
    }

    private static boolean todoOrFalse() {
        if (Param.DEBUG)
            throw new TODO();
        else
            return false;
    }

    private Term sub(int termIndex, @Nullable boolean[] negatives, @Nullable IntPredicate validator) {
        assert (termIndex != 0);

        boolean neg = termIndex < 0;
        if (neg) {
            termIndex = -termIndex;
        }

        if (validator != null && !validator.test(termIndex))
            return False;

        Term c = idToTerm.get(termIndex - 1);
        if (neg) {
            c = c.neg();
            if (negatives != null)
                negatives[0] = true;
        }
        return c;
    }

    public boolean addDithered(Term term, long start, long end, int maxSamples, int minSegmentLength, NAR nar) {
        if (start != ETERNAL) {
            int d = nar.timeResolution.intValue();
            if (d != 1) {
                start = Tense.dither(start, d);
                end = Tense.dither(end, d);
            }
        }
        return add(term, start, end, maxSamples, minSegmentLength);
    }

//    /**
//     * for each of b's events, find the nearest matching event in a while constructing a new Conj consisting of their mergers
//     */
//    static class Conjterpolate extends Conj {
//
////        private final Conj aa;
////        private final Term b;
////        private final NAR nar;
//
//        /**
//         * proportion of a vs. b, ie: (a/(a+b))
//         */
//
//        Conjterpolate(Term a, Term b, long bOffset, float aProp) {
//
////            this.b = b;
////            this.nar = nar;
//
////            this.aa = Conj.from(a);
////            this.idToTerm.addAll(aa.idToTerm);
////            this.termToId.putAll(aa.termToId);
//
//
//            if (addAuto(a)) {
//
//
//                if (bOffset == 0)
//                    addAuto(b);
//                else
//                    add(bOffset, b);
//            }
//
////            compress(Math.max(a.volume(), b.volume()), Math.round(nar.intermpolationDurLimit.floatValue()*nar.dur()));
//
//
////            //merge remaining events from 'a'
////            final boolean[] err = {false};
////            aa.event.forEachKeyValue((long when, Object wat) -> {
////                if (err[0])
////                    return; //HACK
////                if (wat instanceof RoaringBitmap) {
////                    RoaringBitmap r = (RoaringBitmap) wat;
////                    r.forEach((int ri) -> {
////                        boolean neg = (ri < 0);
////                        if (neg) ri = -ri;
////                        if (!add(when, idToTerm.get(ri-1).negIf(neg))) {
////                            err[0] = true;
////                        }
////                    });
////                } else {
////                    byte[] ii = (byte[]) wat;
////                    for (byte ri : ii) {
////                        if (ri == 0)
////                            break; //eol
////                        boolean neg = (ri < 0);
////                        if (neg) ri = (byte) -ri;
////                        if (!add(when, idToTerm.get(ri-1).negIf(neg))) {
////                            err[0] = true;
////                        }
////                    }
////                }
////            });
//        }
//
//        protected void compress(int targetVol, int interpolationThresh /* time units */) {
//            if (interpolationThresh < 1)
//                return;
//
//            //find any two time points that differ by less than the interpolationThresh interval
//            long[] times = this.event.keySet().toSortedArray();
//            if (times.length < 2) return;
//            for (int i = 1; i < times.length; i++) {
//                if (times[i - 1] == DTERNAL)
//                    continue;
//                long dt = times[i] - times[i - 1];
//                if (Math.abs(dt) < interpolationThresh) {
//                    if (combine(times[i - 1], times[i])) {
//                        i++; //skip past current pair
//                    }
//                }
//            }
//        }
//
//        boolean combine(long a, long b) {
//            assert (a != b);
//            assert (a != DTERNAL && b != DTERNAL && a != XTERNAL && b != XTERNAL);
//            ByteHashSet common = new ByteHashSet();
//            addAllTo(common, event.remove(a));
//            addAllTo(common, event.remove(b));
//            common.remove((byte) 0); //remove any '0' value from a byte[] array
//
//            //detect conflicting combination
//            byte[] ca = common.toArray();
//            boolean changed = false;
//            for (byte cc : ca) {
//                if (cc < 0 && common.contains((byte) -cc)) {
//                    common.remove(cc);
//                    common.remove((byte) -cc);
//                    changed = true;
//                }
//            }
//            if (changed) {
//                ca = common.toArray();
//            }
//            if (ca.length > 0) {
//                long mid = (a + b) / 2L; //TODO better choice
//                event.put(mid, ca);
//            }
//            return true;
//        }
//
////        @Override
////        public boolean add(long bt, final Term what) {
////            assert (bt != XTERNAL);
////
////            {
////                boolean neg = what.op() == NEG;
////
////
////                byte tid = termToId.getIfAbsent(neg ? what.unneg() : what, (byte) -1);
////                if (tid == (byte) -1)
////                    return super.add(bt, what);
////
////                byte tInA = (byte) (tid * (neg ? -1 : +1));
////
////
////                LongArrayList whens = new LongArrayList(2);
////
////                aa.event.forEachKeyValue((long when, Object wat) -> {
////                    if (wat instanceof RoaringBitmap) {
////                        RoaringBitmap r = (RoaringBitmap) wat;
////                        if (r.contains(tInA) && !r.contains(-tInA)) {
////                            whens.add(when);
////                        }
////                    } else {
////                        byte[] ii = (byte[]) wat;
////                        if (ArrayUtils.indexOf(ii, tInA) != -1 && ArrayUtils.indexOf(ii, (byte) -tInA) == -1) {
////                            whens.add(when);
////                        }
////                    }
////                });
////
////
////                int ws = whens.size();
////                if (ws > 0) {
////
////                    if (whens.contains(bt))
////                        return true;
////
////                    long at;
////                    if (ws > 1) {
////                        LongToLongFunction temporalDistance;
////                        if (bt == ETERNAL) {
////                            temporalDistance = (a) -> a == ETERNAL ? 0 : Long.MAX_VALUE;
////                        } else {
////                            temporalDistance = (a) -> a == ETERNAL ? Long.MAX_VALUE : Math.abs(bt - a);
////                        }
////                        long[] whensArray = whens.toArray();
////                        ArrayUtils.sort(whensArray, temporalDistance);
////
////                        at = whensArray[whensArray.length - 1];
////                    } else {
////                        at = whens.get(0);
////                    }
////
////                    long merged = merge(at, bt);
////                    if (merged != at) {
////
////                        if ((merged == DTERNAL || merged == XTERNAL) && (at != DTERNAL && bt != DTERNAL && at != XTERNAL && bt != XTERNAL)) {
////                            //add as unique event (below)
////                        } else {
//////                            boolean r = aa.remove(what, at); //remove original add the new merged
//////                            if (!r) {
//////                                assert (r);
//////                            }
////                            return super.add(merged, what);
////                        }
////
////                    } else {
////                        return true; //exact
////                    }
////                }
////                return super.add(bt, what);
////
////            }
////
////        }
//
////        long merge(long a, long b) {
////            if (a == b) return a;
////            if (a == ETERNAL || b == ETERNAL)
////                return ETERNAL;
////            if (a == XTERNAL || b == XTERNAL)
////                throw new RuntimeException("xternal in conjtermpolate");
////
////
////            return Tense.dither(Revision.merge(a, b, aProp, nar), nar);
////
////        }
//
//    }

//    private static void addAllTo(ByteHashSet common, Object o) {
//        if (o instanceof byte[])
//            common.addAll((byte[]) o);
//        else {
//            RoaringBitmap r = (RoaringBitmap) o;
//            r.forEach((int x) -> common.add((byte) x));
//        }
//    }


}
