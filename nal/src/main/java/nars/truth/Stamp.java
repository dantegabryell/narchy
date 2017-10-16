/*
 * Stamp.java
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
 * GNU General Pbulic License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open-NARS.  If not, see <http://www.gnu.org/licenses/>.
 */
package nars.truth;

import jcog.Util;
import jcog.io.BinTxt;
import nars.Op;
import nars.Param;
import nars.Task;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.collections.api.iterator.MutableLongIterator;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

import java.util.Arrays;
import java.util.Collection;

import static nars.time.Tense.ETERNAL;

public interface Stamp {


    /*@NotNull*/ static long[] zip(/*@NotNull*/ long[] a, /*@NotNull*/ long[] b, float aToB) {
        return zip(a, b, aToB,
                Param.STAMP_CAPACITY,
                true);
    }



    /***
     * zips two evidentialBase arrays into a new one
     * assumes a and b are already sorted in increasing order
     * the later-created task should be in 'b'
     */
    /*@NotNull*/
    static long[] zip(/*@NotNull*/ long[] a, /*@NotNull*/ long[] b, float aToB, int maxLen, boolean newToOld) {

        int aLen = a.length, bLen = b.length;

        //if (isCyclic(a)) aLen--; //cyclic flag is not propagated
        //if (isCyclic(b)) bLen--; //cyclic flag is not propagated
        if (isCyclic(a) && isCyclic(b)) bLen--; //only inherit one cyclic

        int baseLength = Math.min(aLen + bLen, maxLen);

        //how many items to exclude from each due to weighting
        int aMin = 0, bMin = 0;
        if (aLen+bLen > maxLen) {
            if (!newToOld)
                throw new UnsupportedOperationException("reverse weighted not yet unimplemented");

            //find which ones to exclude from

            //usedA + usedB = maxLen
            if (aToB <= 0.5f) {
                int usedA = Math.max(1, (int) Math.floor(aToB * (aLen + bLen)));
                if (usedA < aLen) {
                    if (bLen + usedA < maxLen)
                        usedA+= maxLen - usedA - bLen; //pad to fill
                    aMin = Math.max(0, aLen - usedA);
                }
            } else /* aToB > 0.5f */ {
                int usedB = Math.max(1, (int) Math.floor((1f-aToB) * (aLen + bLen)));
                if (usedB < bLen) {
                    if (aLen + usedB < maxLen)
                        usedB += maxLen - usedB - aLen;  //pad to fill
                    bMin = Math.max(0, bLen - usedB);
                }
            }

        }

        long[] c = new long[baseLength];
        if (newToOld) {
            //"forward" starts with newes, oldest are trimmed
            int ib = bLen-1, ia = aLen-1;
            for (int i = baseLength-1; i >= 0; ) {
                boolean ha = (ia >= aMin), hb = (ib >= bMin);

//                c[i--] = ((ha && hb) ?
//                            ((i & 1) > 0) : ha) ?
//                            a[ia--] : b[ib--];
                long next;
                if (ha && hb) {
                    next = (i & 1) > 0 ? a[ia--] : b[ib--];
                } else if (ha) {
                    next = a[ia--];
                } else if (hb) {
                    next = b[ib--];
                } else {
                    throw new RuntimeException("stamp fault");
                }

                c[i--] = next;
            }
        } else {
            //"reverse" starts with oldest, newest are trimmed
            int ib = 0, ia = 0;
            for (int i = 0; i < baseLength; ) {

                boolean ha = ia < (aLen - aMin), hb = ib < (bLen - bMin);
                c[i++] = ((ha && hb) ?
                            ((i & 1) > 0) : ha) ?
                            a[ia++] : b[ib++];
            }
        }

        return toSetArray(c, maxLen);
    }

    /** computes an estimate of self-overlap of a stamp
     * TODO refine */
    static float cyclicity(long[] s) {

        return isCyclic(s) ? (float) (0.99f / Math.sqrt(s.length - 1)) : 0;
    }

    /*@NotNull*/
    default StringBuilder appendOccurrenceTime(/*@NotNull*/ StringBuilder sb) {
        long oc = start();

        /*if (oc == Stamp.TIMELESS)
            throw new RuntimeException("invalid occurrence time");*/
//        if (ct == ETERNAL)
//            throw new RuntimeException("invalid creation time");

        //however, timeless creation time means it has not been perceived yet

//        if (oc == ETERNAL) {
//            if (ct == TIMELESS) {
//                sb.append(":-:");
//            } else {
//                sb.append(':').append(ct).append(':');
//            }
//
//        } else if (oc == TIMELESS) {
//            sb.append("N/A");

        if (oc != ETERNAL) {
            int estTimeLength = 8; /* # digits */
            sb.ensureCapacity(estTimeLength);
            sb.append(oc);

            long end = end();
            if (end!=oc) {
                sb.append((char)0x22c8 /* bowtie, horizontal hourglass */).append(end);
            }


            //sb.append(ct);

//            long OCrelativeToCT = (oc - ct);
//            if (OCrelativeToCT >= 0)
//                sb.append('+'); //+ sign if positive or zero, negative sign will be added automatically in converting the int to string:
//            sb.append(OCrelativeToCT);

        }

        return sb;
    }


    /*@NotNull*/
    default CharSequence stampAsStringBuilder() {

        long[] ev = stamp();
        int len = ev.length;
        int estimatedInitialSize = 8 + (len * 3);

        StringBuilder buffer = new StringBuilder(estimatedInitialSize);
        buffer.append(Op.STAMP_OPENER);

        /*if (creation() == TIMELESS) {
            buffer.append('?');
        } else */
        /*if (!(start() == ETERNAL)) {
            appendTime(buffer);
        } else {*/
            buffer.append(creation());
        //}
        buffer.append(Op.STAMP_STARTER).append(' ');

        for (int i = 0; i < len; i++) {

            if (ev[i] == Long.MAX_VALUE && i == len - 1) {
                buffer.append(';'); //trailing cyclic value
            } else {
                BinTxt.append(buffer, ev[i]);
            }
            if (i < (len - 1)) {
                buffer.append(Op.STAMP_SEPARATOR);
            }
        }

        buffer.append(Op.STAMP_CLOSER); //.append(' ');

        //this is for estimating an initial size of the stringbuffer
        //System.out.println(baseLength + " " + derivationChain.size() + " " + buffer.baseLength());

        return buffer;


    }

    /*@NotNull*/
    static long[] toSetArray(/*@NotNull*/ long[] x) {
        return toSetArray(x, x.length);
    }

    /*@NotNull*/
    static long[] toSetArray(/*@NotNull*/ long[] x, final int outputLen) {
        int l = x.length;

        //copy evidentialBase and sort it
        return (l < 2) ? x : _toSetArray(outputLen, Arrays.copyOf(x, l));
    }


    /*@NotNull*/
    static long[] _toSetArray(int outputLen, /*@NotNull*/ long[] sorted) {

        //Arrays.sort(sorted, 0, isCyclic(sorted) ? sorted.length-1 : sorted.length);
        Arrays.sort(sorted);

        //2. count unique elements
        long lastValue = -1;
        int uniques = 0; //# of unique items

        for (long v : sorted) {
            if (lastValue != v)
                uniques++;
            lastValue = v;
        }

        if ((uniques == outputLen) && (sorted.length == outputLen)) {
            //if no duplicates and it's the right size, just return it
            return sorted;
        }

        //3. de-duplicate
        int outSize = Math.min(uniques, outputLen);
        long[] dedupAndTrimmed = new long[outSize];
        int uniques2 = 0;
        long lastValue2 = -1;
        for (long v : sorted) {
            if (lastValue2 != v)
                dedupAndTrimmed[uniques2++] = v;
            if (uniques2 == outSize)
                break;
            lastValue2 = v;
        }
        return dedupAndTrimmed;
    }

    @Deprecated static boolean overlapping(/*@NotNull*/ Stamp a, /*@NotNull*/ Stamp b) {
        return ((a == b) || overlapping(a.stamp(), b.stamp()));
    }

    /**
     * true if there are any common elements;
     * assumes the arrays are sorted and contain no duplicates
     *
     * @param a evidence stamp in sorted order
     * @param b evidence stamp in sorted order
     */
    @Deprecated static boolean overlapping(/*@NotNull*/ long[] a, /*@NotNull*/ long[] b) {

        if (Param.DEBUG) {
//            if (a == null || b == null)
//                throw new RuntimeException("null evidence");
            if (a.length == 0 || b.length == 0) {
                throw new RuntimeException("missing evidence");
            }
        }

        /** TODO there may be additional ways to exit early from this loop */

        for (long x : a) {
            if (x == Long.MAX_VALUE)
                continue; //ignore the cyclic flag
            for (long y : b) {
                if (x == y) {
                    return true; //commonality detected
                } else if (y > x) {
                    break; //any values after y in b will not be equal to x
                }
            }
        }
        return false;
    }

    /**
     * the fraction of components in common divided by the total amount of unique components.
     *
     * how much two stamps overlap can be used to estimate
     * the potential for information gain vs. redundancy.
     *
     *      == 0 if nothing in common, completely independent
     *      >0 if there is at least one common component;
     *          1.0 if they are equal, or if one is entirely contained within the other
     *          < 1.0 if they have some component in common
     *
     * assumes the arrays are sorted and contain no duplicates
     */
    static float overlapFraction(long[] a, long[] b) {
        //prefer to make a set of the shorter length input
        if (a.length > b.length) {
            //swap
            long[] ab = a;
            a = b;
            b = ab;
        }

        //TODO fast impl for simple cases where a.length=1

        return overlapFraction(LongSets.immutable.of(a), a.length, b);
    }


    /** ignores any cyclic element */
    static float overlapFraction(/*@NotNull*/ LongSet aa, int aSize, /*@NotNull*/ long[] b) {
        int common = 0;
        for (long x: b) {
            if (x!=Long.MAX_VALUE /* cyclic */ && aa.contains(x))
                common++;
        }
        if (common == 0)
            return 0f;

        if (aa.contains(Long.MAX_VALUE))
            aSize--;

        int bSize = b.length;
        if (isCyclic(b))
            bSize--;

        return (((float) common) / Math.min(aSize, bSize));
    }

    long creation();
    long start();
    long end();



    /** originality monotonically decreases with evidence length increase.
     * it must always be < 1 (never equal to one) due to its use in the or(conf, originality) ranking */
    default float originality() {
        return TruthFunctions.originality(stamp().length);
    }

    /**
     * deduplicated and sorted version of the evidentialBase.
     * this can always be calculated deterministically from the evidentialBAse
     * since it is the deduplicated and sorted form of it.
     */
    /*@NotNull*/
    long[] stamp();

    //Stamp setEvidence(long... evidentialSet);

//    /*@NotNull*/
//    static long[] zip(/*@NotNull*/ Task a, /*@NotNull*/ Task b) {
//        @Nullable long[] bb = b.stamp();
//        @Nullable long[] aa = a.stamp();
//        return (a.creation() > b.creation()) ?
//                Stamp.zip(bb, aa) :
//                Stamp.zip(aa, bb);
//    }

//    static int evidenceLength(int aLen, int bLen) {
//        return Math.max(Param.STAMP_CAPACITY, aLen + bLen);
//    }
//    static int evidenceLength(/*@NotNull*/ Task a, /*@NotNull*/ Task b) {
//        return evidenceLength(a.stamp().length, b.stamp().length);
//    }

//    static long[] zip(/*@NotNull*/ TemporalBeliefTable s) {
//        return zip(s, s.size(), Param.STAMP_CAPACITY);
//    }

//    static long[] zip(/*@NotNull*/ Collection<? extends Stamp> s) {
//        assert(!s.isEmpty());
//        return zip(s, Param.STAMP_CAPACITY);
//    }



    static long[] zip(Stamp[] s, int maxLen) {
//        final int extra = 1;
//        int maxPer = Math.max(1, Math.round((float)maxLen / num)) + extra;
        LongHashSet l = new LongHashSet(maxLen);
        boolean cyclic = false;
        int done = 0;
        int S = s.length;
        int p = 0;
        while (done<S && l.size() < (maxLen - (cyclic ? 1 : 0))) {
            done = 0;
            for (int i = 0; i < S; i++) {
                long[] x = s[i].stamp();
                int xl = x.length;
                boolean c = (xl > 1 && x[xl-1]==Long.MAX_VALUE);

                int xi = xl - 1 - p - (c ? 1 : 0);
                if (xi < 0) {
                    done++;
                    continue;
                }

                long v = x[xi]; //skip cyclic
                cyclic |= (!l.add(v)) || c;
            }
            p++;
        }


        int ls = l.size();

        long[] e = new long[ls + (cyclic ? 1 : 0)];
        MutableLongIterator ll = l.longIterator();
        int k = 0;
        while (ll.hasNext()) {
            e[k++] = ll.next();
        }

        if (cyclic)
            e[k] = Long.MAX_VALUE;

        Arrays.sort(e);

        return e;
    }


    /** cyclic tasks are indicated with a final value of Long.MAX_VALUE */
    static boolean isCyclic(/*@NotNull*/ long[] e) {
        int length = e.length;
        return (length > 1 && e[length -1] == Long.MAX_VALUE);
    }

    static long[] uncyclic(/*@NotNull*/ long[] assumedCyclic) {

        return ArrayUtils.remove(assumedCyclic, assumedCyclic.length-1);
    }
    static long[] cyclic(/*@NotNull*/ long[] x) {
        int l = x.length;

        if (isCyclic(x))
            return x;

        long[] y;
        if (l >= Param.STAMP_CAPACITY) {
            y = new long[Param.STAMP_CAPACITY];
            //shift left by one to leave the last entry free
            System.arraycopy(x, 1, y, 0, Param.STAMP_CAPACITY -1);
        } else {
            y = new long[l+1];
            System.arraycopy(x, 0, y, 0, l);
        }

        y[y.length-1] = Long.MAX_VALUE;
        return y;
    }

    static boolean equalsIgnoreCyclic(long[] a, long[] b) {
        boolean aCyclic = isCyclic(a);
        if (aCyclic ^ isCyclic(b)) {
            int alen = a.length;
            if (aCyclic) {
                if (alen != b.length+1) return false;
                return Util.equals(a, b, alen-1);
            } else {
                if (alen != b.length-1) return false;
                return Util.equals(a, b, alen);
            }
        } else {
            return Util.equals(a, b);
        }
    }
}