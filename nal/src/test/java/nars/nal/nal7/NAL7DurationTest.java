package nars.nal.nal7;

import com.google.common.collect.Lists;
import nars.NAR;
import nars.Narsese;
import nars.Param;
import nars.Task;
import nars.concept.Concept;
import nars.nar.Default;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by me on 2/2/17.
 */
public class NAL7DurationTest {

    @Test
    public void testTemporalIntersection() throws Narsese.NarseseException {

        NAR n = new Default();
        n.inputAt( 0,"a:x. :|:");
        n.inputAt(10,"a:y. :|:");
        n.run(25);

        assertDuration(n, "(x<->y)", 0, 10);
        assertDuration(n, "((x|y)-->a)", 0, 10);
        assertDuration(n, "((x&y)-->a)", 0, 10);
        assertDuration(n, "((x~y)-->a)", 0, 10);

        //n.concept("(x-->a)").print();
        //n.concept("(y-->a)").print();
    }

    static void assertDuration(NAR n, String c, long start, long end) throws Narsese.NarseseException {
        Concept cc = n.concept(c);
        assertNotNull(c + " unconceptualized", cc);

        List<Task> tt = Lists.newArrayList(cc.beliefs());
        assertTrue(c + " not believed", tt.size() > 0);

        Task t = tt.get(0);
        //System.out.println(sim.proof());
        //System.out.println(sim.start() + ".." + /*sim.occurrence() + ".."*/ + sim.end());
        assertEquals(start, t.start());
        assertEquals(end, t.end());
    }
}
