package jcog.bag;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by me on 2/17/17.
 */
public class RawPLink<X> implements PLink<X> {

    protected final X id;
    float pri;

    public RawPLink(X x, float p) {
        this.id = x;
        setPriority(p);
    }

    @Nullable @Override
    public RawPLink<X> clone() {
        float p = pri();
        return (p==p) ? new RawPLink<>(id, p) : null;
    }

    @Override
    public boolean equals(@NotNull Object that) {
        if (this==that)
            return true;

        Object b = ((PLink) that).get();
        return this.id.equals(b);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public void setPriority(float p) {
        this.pri = Priority.validPriority(p);
    }

    @Override
    @NotNull
    public X get() {
        return id;
    }

    @Override
    public String toString() {
        return id + "=" + pri;
    }

    @Override
    public float pri() {
        return pri;
    }

    @Override
    public boolean delete() {
        float pri = this.pri;
        if (pri == pri) {
            this.pri = Float.NaN;
            return true;
        }
        return false;
    }
}
