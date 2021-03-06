package nars.derive.timing;


import jcog.math.FloatRange;
import nars.NAR;
import nars.Task;
import nars.term.Term;

import java.util.function.BiFunction;

public class ActionTiming implements BiFunction<Task, Term, long[]> {

    private final NAR nar;
    public final FloatRange horizonDurs = new FloatRange(4, 0, 32);
    //public final FloatRange widthDurs = new FloatRange(2, 0, 8);

    public ActionTiming(NAR n) {
        this.nar = n;
    }

    @Override
    public long[] apply(Task task, Term term) {


        long start, end;
        long now = nar.time();
        long then = now + Math.round((nar.random().nextFloat() * horizonDurs.floatValue()) * nar.dur());
//        if (task.endsBefore(now)) {
//        if (!task.isEternal()) {
            start = then - nar.dur(); // + Math.round( ( nar.random().nextDouble() * horizonDurs.floatValue() ) * nar.dur() );

        end = then + nar.dur();
                    //Math.round(widthDurs.doubleValue()*nar.dur());

//        } else {
//            start = task.start();
//            end = task.end();
//        }
        return new long[] { start, end };
    }
}
