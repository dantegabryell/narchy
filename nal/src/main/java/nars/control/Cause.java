package nars.control;

import jcog.Util;
import jcog.math.RecycledSummaryStatistics;
import jcog.pri.ScalarValue;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * represents a causal influence and tracks its
 * positive and negative gain (separately).  this is thread safe
 * so multiple threads can safely affect the accumulators. it must be commited
 * periodically (by a single thread, ostensibly) to apply the accumulated values
 * and calculate the values
 * as reported by the value() function which represents the effective
 * positive/negative balance that has been accumulated. a decay function
 * applies forgetting, and this is applied at commit time by separate
 * positive and negative decay rates.  the value is clamped to a range
 * (ex: 0..+1) so it doesn't explode.
 *
 * https://cogsci.indiana.edu/pub/parallel-terraced-scan.pdf
 */
public class Cause implements Comparable<Cause> {

    /**
     * current scalar utility estimate for this cause's support of the current MetaGoal's.
     * may be positive or negative, and is in relation to other cause's values
     */
    private volatile float value = 0;

    /**
     * the value measured contributed by its effect on each MetaGoal.
     * the index corresponds to the ordinal of MetaGoal enum entries.
     * these values are used in determining the scalar 'value' field on each update.
     */
    public final Traffic[] credit;


    public float value() {
        return value;
    }

    /**
     * 0..+1
     */
    public float amp() {
        return Math.max(ScalarValue.EPSILON, gain() / 2f);
    }

    /**
     * 0..+2
     */
    private float gain() {
        return Util.tanhFast(value) + 1f;
    }

    /**
     * value may be in any range (not normalized); 0 is neutral
     */
    public void setValue(float nextValue) {
        assert(Float.isFinite(nextValue));
        value = nextValue;
    }


    /**
     * internally assigned id
     */
    public final short id;

    public final Object name;

    protected Cause(short id) {
        this(id, null);
    }

    public Cause(short id, @Nullable Object name) {
        this.id = id;
        this.name = name != null ? name : id;
        credit = new Traffic[MetaGoal.values().length];
        for (int i = 0; i < credit.length; i++) {
            credit[i] = new Traffic();
        }
    }

    @Override
    public String toString() {
        return name + "[" + id + "]=" + super.toString();
    }

    @Override
    public int hashCode() {
        return Short.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || id == ((Cause) obj).id;
    }

    @Override
    public int compareTo(Cause o) {
        return Short.compare(id, o.id);
    }

    public void commit(RecycledSummaryStatistics[] valueSummary) {
        for (int i = 0, purposeLength = credit.length; i < purposeLength; i++) {
            Traffic p = credit[i];
            p.commit();
            valueSummary[i].accept(p.last);
        }
    }

    public void commit() {
        for (Traffic aGoal : credit)
            aGoal.commit();
    }

    public void commitFast() {
        for (Traffic aGoal : credit)
            aGoal.commitFast();
    }

    public void print(PrintStream out) {
        out.println(this + "\t" +
                IntStream.range(0, credit.length).mapToObj(x->
                    MetaGoal.values()[x] + "=" + credit[x]
                ).collect(toList())
        );

    }



}












