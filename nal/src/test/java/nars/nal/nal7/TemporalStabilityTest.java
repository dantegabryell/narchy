package nars.nal.nal7;

import nars.Global;
import nars.NAR;
import nars.task.Task;
import nars.util.TimeMap;

import java.util.Set;

import static org.junit.Assert.assertTrue;


abstract public class TemporalStabilityTest {


    public void test(int cycles, NAR n) {
        Global.DEBUG = true;

        input(n);
        //n.log();

        Set<Task> irregular = Global.newHashSet(1);

        for (int i = 0; i < cycles; i++) {

            n.step();
            TimeMap m = new TimeMap(n);
            //Set<Between<Long>> times = m.keySetSorted();
        /*if (times.size() < 3)
            continue; //wait until the initial temporal model is fully constructed*/

            //m.print();

            for (Task tt : m.values()) {

                long o = tt.occurrence();
                if (!validOccurrence(o)) {
                    if (irregular.add(tt)) { //already detected?
                        System.err.println("  instability: " + tt + "\n" + tt.explanation() + "\n");
                        irregular.add(tt);
                    }
                }
            }


            //assertEquals("[[1..1], [2..2], [5..5]]", times.toString());
        }

        assertTrue(irregular.isEmpty());
    }


    abstract public boolean validOccurrence(long o);

    /**
     * inputs the tasks for a test
     */
    abstract public void input(NAR n);
}
