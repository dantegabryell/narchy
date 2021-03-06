package jcog.tree.rtree.point;

import jcog.tree.rtree.HyperPoint;

public class Float2D implements HyperPoint, Comparable<Float2D> {
    public final float x;
    public final float y;

    public Float2D(final float x, final float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int dim() {
        return 2;
    }

    @Override
    public Float coord(final int d) {
        if (d == 0) {
            return x;
        } else if (d == 1) {
            return y;
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    @Override
    public double distance(final HyperPoint p) {
        final Float2D p2 = (Float2D) p;

        final float dx = p2.x - x;
        final float dy = p2.y - y;
        return  Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public double distance(final HyperPoint p, final int d) {
        final Float2D p2 = (Float2D) p;
        if (d == 0) {
            return Math.abs(p2.x - x);
        } else if (d == 1) {
            return Math.abs(p2.y - y);
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    @Override
    public String toString() {
        return "<" + x + ',' + y + '>';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Float2D)) return false;

        Float2D float2D = (Float2D) o;

        if (Float.compare(float2D.x, x) != 0) return false;
        return Float.compare(float2D.y, y) == 0;
    }

    @Override
    public int hashCode() {
        long temp = Float.floatToIntBits(x);
        int result = (int) (temp ^ (temp >>> 32));
        temp = Float.floatToIntBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public int compareTo(Float2D o) {
        int a = Float.compare(x, o.x);
        if (a != 0) return a;
        int b = Float.compare(y, o.y);
        return b;
    }

}