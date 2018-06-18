package nars.derive;

import nars.Narsese;
import nars.derive.premise.PremisePatternIndex;
import nars.term.Compound;
import org.junit.jupiter.api.Test;

import static nars.$.$;
import static nars.time.Tense.XTERNAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PremisePatternCompoundTest {

    private final PremisePatternIndex i = new PremisePatternIndex();

    @Test
    void testPatternCompoundWithXTERNAL() throws Narsese.NarseseException {
        Compound p = (Compound) i.get($("((x) ==>+- (y))"), true).term();
        assertEquals(XTERNAL, p.dt());
    }

    @Test
    void testEqualityWithNonPatternDT() throws Narsese.NarseseException {
        for (String s : new String[] { "(a ==> b)", "(a ==>+1 b)", "(a &&+1 b)" }) {
            Compound t = $(s);
            Compound p = (Compound) i.get(t, true).term();
            assertEquals(t.dt(), p.dt());
            assertEquals(t, p);
        }
    }
}