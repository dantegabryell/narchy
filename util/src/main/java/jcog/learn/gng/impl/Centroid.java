package jcog.learn.gng.impl;


import org.apache.commons.math3.linear.ArrayRealVector;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static jcog.Texts.n4;

/**
 * Created by Scadgek on 11/3/2014.
 */
public class Centroid extends ArrayRealVector {

    /** serial unique ID */
    private static final AtomicInteger ID = new AtomicInteger();
    public final int id = ID.getAndIncrement();

    public final int _id;

    private double localError;
    private double localDistanceSq; 

    public Centroid(int internalID, int dimensions) {
        super(dimensions);
        this._id = internalID;
        Arrays.fill(getDataRef(), Double.NaN);
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
        //return (this == other) || ((Centroid) other)._id == _id;
    }

    @Override
    public int hashCode() {
        return (id + 1) * 37;
    }

    /**
     * create a node from two existing nodes
     */
    public void set(Centroid maxErrorNode, Centroid maxErrorNeighbour) {

        assert (maxErrorNode != maxErrorNeighbour);
        setLocalError(
                (maxErrorNode.localError)
                
        );

        double[] a = maxErrorNode.getDataRef();
        double[] b = maxErrorNeighbour.getDataRef();
        double[] ab = getDataRef();
        for (int i = 0; i < ab.length; i++) {
            ab[i] = (a[i] + b[i]) / 2;
        }
    }

    public Centroid randomizeUniform(int dim, double min, double max) {
        setEntry(dim, Math.random() * (max - min) + min);
        return this;
    }

    public Centroid randomizeUniform(double min, double max) {
        int dim = getDimension();
        for (int i = 0; i < dim; i++) {
            setEntry(i, Math.random() * (max - min) + min);
        }
        return this;
    }









    public double localError() {
        return localError;
    }

    public Centroid setLocalError(double localError) {
        assert (Double.isFinite(localError));
        this.localError = localError;
        return this;
    }

    public void mulLocalError(double alpha) {
        this.localError *= alpha;
    }

    public double distanceSq(final double[] x, DistanceFunction distanceSq) {
        double d = distanceSq.distance(getDataRef(), x);
        assert (Double.isFinite(d));
        return d;
    }


    public static double distanceCartesianSq(double[] x, double[] y) {
        double s = 0;
        int l = y.length;
        for (int i = 0; i < l; i++) {
            final double d = y[i] - x[i];
            s += d * d;
        }
        return s;
    }


    /**
     * 0 < rate < 1.0
     */
    public void lerp(final double[] x, final double rate) {
        final double[] d = getDataRef();
        final double ir = (1.0 - rate);
        int k = 0;
        for (int i = 0; i < d.length; i++) {
            d[i] = (ir * d[i]) + (rate * x[i]);
        }
    }

    public void add(final double[] x) {
        final double[] d = getDataRef();
        for (int i = 0; i < d.length; i++) {
            
            d[i] += x[i];
        }
    }

    @Override
    public String toString() {
        return id + ": <" + n4(getDataRef()) + "> lErr=" + n4(localError) + " dist=" + n4(localDistance());
    }

    /** tests the first dimension value if not NaN */
    public boolean active() {
        double v = getEntry(0);
        return v == v;
    }

    public interface DistanceFunction {
        double distance(double[] a, double[] b);

        /**
         * for range normalization
         * warning: the result here is the Square of the distance, ie. to
         * avoid needing to calculate sqrt() this can be used in a comparison
         */
        static double distanceCartesianSq(double[] x, double[] y) {
            double s = 0;
            int l = y.length;
            for (int i = 0; i < l; i++) {
                final double d = (y[i] - x[i]);
                s += d * d;
            }
            return s;
        }
        static double distanceCartesianManhattan(double[] x, double[] y) {
            double s = 0;
            int l = y.length;
            for (int i = 0; i < l; i++) {
                final double d = Math.abs(y[i] - x[i]);
                s += d;
            }
            return s;
        }

    }

    public double learn(double[] x, DistanceFunction dist) {
        double[] d = getDataRef();
        if (d[0]!=d[0]) {
            //inactive, assign the value as-is
            System.arraycopy(x, 0, d, 0, x.length);
            return this.localDistanceSq = 0;
        } else {
            return (this.localDistanceSq = dist.distance(d, x));
        }
    }

    public double localDistanceSq() {
        return localDistanceSq;
    }

    public double localDistance() {
        return Math.sqrt(localDistanceSq);
    }


    /*** move the centroid towards the point being learned, at the given rate */
    public void updateLocalError(double[] x, double winnerUpdateRate) {
        setLocalError(localError() + localDistance());
        lerp(x, winnerUpdateRate);
    }


}