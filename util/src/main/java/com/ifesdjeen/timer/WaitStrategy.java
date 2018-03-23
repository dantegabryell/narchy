package com.ifesdjeen.timer;

import jcog.Util;

public interface WaitStrategy {

    /**
     * Wait until the given deadline, deadlineNanoseconds
     *
     * @param deadlineNanoseconds deadline to wait for, in milliseconds
     */
    void waitUntil(long deadlineNanoseconds) throws InterruptedException;

    /**
     * Yielding wait strategy.
     * <p>
     * Spins in the loop, until the deadline is reached. Releases the flow control
     * by means of Thread.yield() call. This strategy is less precise than BusySpin
     * one, but is more scheduler-friendly.
     */
    class YieldingWait implements WaitStrategy {

        @Override
        public void waitUntil(long deadline) throws InterruptedException {
            while (deadline >= System.nanoTime()) {
                Thread.yield();
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
            }
        }
    }

    /**
     * BusySpin wait strategy.
     * <p>
     * Spins in the loop until the deadline is reached. In a multi-core environment,
     * will occupy an entire core. Is more precise than Sleep wait strategy, but
     * consumes more resources.
     */
    class BusySpinWait implements WaitStrategy {

        @Override
        public void waitUntil(long deadline) throws InterruptedException {
            while (deadline >= System.nanoTime()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
            }
        }
    }

    /**
     * Sleep wait strategy.
     * <p>
     * Will release the flow control, giving other threads a possibility of execution
     * on the same processor. Uses less resources than BusySpin wait, but is less
     * precise.
     */
    class SleepWait implements WaitStrategy {

        @Override
        public void waitUntil(long deadline) {
            long sleepTimeNanos = deadline - System.nanoTime();
            Util.sleepNano(sleepTimeNanos);
        }
    }


}
