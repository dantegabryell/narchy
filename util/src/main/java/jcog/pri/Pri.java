package jcog.pri;


import jcog.Util;
import org.eclipse.collections.api.block.function.primitive.FloatFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * default mutable prioritized implementation
 */
public class Pri implements Priority {

    /**
     * The relative share of time resource to be allocated
     */
    protected float pri;


    public Pri() {
        pri = Float.NaN;
    }

    public Pri(@NotNull Prioritized b, float scale) {
        this(b.pri()*scale);
    }

    public Pri(@NotNull Prioritized b) {
        this(b.pri());
    }

    public Pri(float p) {
        setPri(p);
    }

    @Override
    public boolean isDeleted() {
        float p = pri();
        return p!=p; //fast NaN check
    }

    @Nullable
    @Deprecated @Override
    public Priority clonePri() {
//        throw new UnsupportedOperationException();
        float p = pri();
        return p != p /* deleted? */ ? null : new Pri(p);
    }

    /**
     * Get priority value
     *
     * @return The current priority
     */
    @Override
    public final float pri() {
        return pri;
    }


    /** duplicate of Prioritized's impl, for speed (hopefully) */
    @Override public final float priElseNeg1() {
        float p = pri; //pri() if there are any subclasses they should call pri()
        return p == p ? p : -1;
    }
    /** duplicate of Prioritized's impl, for speed (hopefully) */
    @Override public final float priElseZero() {
        float p = pri; //pri() if there are any subclasses they should call pri()
        return p == p ? p : 0;
    }

    @Override
    public boolean delete() {
        float p = pri;
        if (p==p) {
        //if (!isDeleted()) { //dont call isDeleted it may be overridden in a cyclical way
            this.pri = Float.NaN;
            return true;
        }
        //logger.warn("alredy deleted");
//            throw new RuntimeException("Already Deleted");
        return false;
    }




//    public boolean equals(Object that) {
//        throw new UnsupportedOperationException();
//    }
//
//    @Override
//    public int hashCode() {
//        throw new UnsupportedOperationException();
//     }

    /**
     * Fully display the BudgetValue
     *
     * @return String representation of the value
     */
    @NotNull
    @Override
    public String toString() {
        return getBudgetString();
    }

    @Override
    public final float setPri(float p) {
        return this.pri = Util.unitize(p);
    }

    @NotNull public Pri setPriThen(float p) {
        setPri(p);
        return this;
    }


    public static final FloatFunction<? extends Pri> floatValue = Pri::pri;

    public static float sum(Prioritized... src) {
        return Util.sum(Prioritized::priElseZero, src);
    }


}
