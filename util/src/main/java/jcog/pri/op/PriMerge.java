package jcog.pri.op;

import jcog.TODO;
import jcog.Util;
import jcog.pri.Prioritizable;
import jcog.pri.Prioritized;

import java.util.function.BiConsumer;

/**
 * Budget merge function, with input scale factor
 */

public enum PriMerge implements BiConsumer<Prioritizable, Prioritized> {

    plus {
        @Override float merge(float e, float i) {  return e + i; }
    },
    avg {
        @Override float merge(float e, float i) {  return (e + i)/2; }
    },
    and {
        @Override float merge(float e, float i) {  return e * i; }

        @Override
        protected boolean undelete() {
            return false;
        }
    },
    or {
        @Override float merge(float e, float i) {  return Util.or(e, i); }
    },
    max {
        @Override float merge(float e, float i) {  return e >= i ? e : i; }
    },
    replace {
        @Override float merge(float e, float i) {  return i; }

        @Override
        protected boolean ignoreDeletedIncoming() {
            return false;
        }
    }
    //    AVG_GEO,
    //    AVG_GEO_SLOW, //adds momentum by includnig the existing priority as a factor twice against the new value once
    //    AVG_GEO_FAST,

    ;



    abstract float merge(float e, float i);

    /**
     * merge 'incoming' budget (scaled by incomingScale) into 'existing'
     *
     * @return any resultng overflow priority which was not absorbed by the target, >=0
     */
    public final float merge(Prioritizable existing, Prioritized incoming) {
        return merge(existing, incoming.pri());
    }


    @Override public final void accept(Prioritizable existing, Prioritized incoming) {
        merge(existing, incoming.pri());
    }

    /**
     * merge 'incoming' budget (scaled by incomingScale) into 'existing'
     *
     * @return any resultng overflow priority which was not absorbed by the target, >=0
     */
    public final float merge(Prioritizable existing, float incoming) {


        if (incoming!=incoming && ignoreDeletedIncoming()) {
            return 0;
        }

        final float[] e = new float[1];
        float ePriAfter = existing.pri((x, y) -> {

            if (x != x) {
                if (undelete())
                    x = 0; //undelete
                else
                    return Float.NaN;
            }

            e[0] = x;

            return merge(x, y);
        }, incoming);


        float z;
        if (ePriAfter != ePriAfter) {
            //deleted
            if (incoming!=incoming)
                z = 0;
            else
                z = incoming;
        } else {
            z = incoming - (ePriAfter - e[0]);
        }

        assert(z==z);

        return z;
    }

    protected boolean ignoreDeletedIncoming() {
        return true;
    }

    /** if the existing value is deleted, whether to undelete (reset to zero) */
    protected boolean undelete() {
        return true;
    }
    protected boolean commutative() {
        throw new TODO();
    }






//
//    /**
//     * sum priority
//     */
//    PriMerge<Prioritizable,Prioritized> plus = (tgt, src) -> merge(tgt, src, PLUS);
//
//    /**
//     * avg priority
//     */
//    PriMerge<Prioritizable,Prioritized> avg = (tgt, src) -> merge(tgt, src, AVG);
//
//    PriMerge<Prioritizable,Prioritized> or = (tgt, src) -> merge(tgt, src, OR);
//
//
//    PriMerge<Prioritizable,Prioritized> max = (tgt, src) -> merge(tgt, src, MAX);
//
//    /**
//     * avg priority
//     */
//    PriMerge<Prioritizable,Prioritized> replace = (tgt, src) -> tgt.pri((FloatSupplier)()-> src.pri());


//    PriMerge<Prioritizable,Prioritized> avgGeoSlow = (tgt, src) -> merge(tgt, src, AVG_GEO_SLOW);
//    PriMerge<Prioritizable,Prioritized> avgGeoFast = (tgt, src) -> merge(tgt, src, AVG_GEO_FAST);
//    PriMerge<Prioritizable,Prioritized> avgGeo = (tgt, src) -> merge(tgt, src, AVG_GEO); //geometric mean


}
