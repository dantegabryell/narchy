package jcog.exe;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * potentially executable procedure of some value N >=1 iterations per invocation.
 * represents a functional skill or ability the system is able to perform, particularly
 * once it has learned how, why, and when to invoke it.
 * <p>
 * accumulates, in nanoseconds (long) the time spent, and the # of work items (int)
 */
public class Can  {

    private final static AtomicInteger serial = new AtomicInteger();


    public final String id;

    public Can() {
        this(String.valueOf(serial.incrementAndGet()));
    }

    public Can(String id) {
        super();
        this.id = id;
    }

    @Override
    public String toString() {
        return id;
    }

}
