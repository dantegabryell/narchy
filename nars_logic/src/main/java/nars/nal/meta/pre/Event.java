package nars.nal.meta.pre;

import nars.Premise;
import nars.nal.PremiseMatch;
import nars.nal.meta.AtomicBooleanCondition;
import nars.task.Temporal;

/**
 * After(%X,%Y) Means that
 * %X is after %Y
 * TODO use less confusing terminology and order convention
 */
abstract public class Event extends AtomicBooleanCondition<PremiseMatch> {

    //public static final Event the = new Event();

    protected Event() {
    }

    @Override
    abstract public String toString();

    @Override
    public boolean booleanValueOf(PremiseMatch m) {
        Premise premise = m.premise;
        if (premise.isEvent()) {
            int tDelta = ((Temporal) premise.getBelief()).tDelta((Temporal) premise.getTask());
            return booleanValueOfEvent(m, tDelta);
        }
        return false;
    }

    /** @param tDelta task->belief delta (positive if belief after task, negative if task after belief) */
    protected abstract boolean booleanValueOfEvent(PremiseMatch m, int tDelta);

    /** belief then task */
    public final static class After extends Event {

        public static final After forward = new After(true);
        public static final After reverse = new After(false);
        private final boolean positive;

        protected After(boolean positive) {
            super();
            this.positive = positive;
        }

        @Override
        protected boolean booleanValueOfEvent(PremiseMatch m, int tDelta) {
            if (tDelta >= 0) {
                m.tDelta.set(positive ? tDelta : -tDelta);
                return true;
            }
            return false;
        }

        @Override public String toString() {
            return "after(" + (positive ? "forward" : "reverse") + ")";
        }
    }

//    /** task then/simultaneously belief */
//    public static final class Before extends Event {
//
//        public static final Before forward = new Before(true);
//        public static final Before reverse = new Before(false);
//        private final boolean positive;
//
//        protected Before(boolean pos) {
//            super();
//            this.positive = pos;
//        }
//
//        @Override
//        protected boolean booleanValueOfEvent(PremiseMatch m, int tDelta) {
//            if (tDelta >= 0) {
//                m.tDelta.set(positive ? -tDelta : tDelta);
//                return true;
//            }
//            return false;
//        }
//
//        @Override public String toString() {
//            return "before";
//        }
//    }
}
