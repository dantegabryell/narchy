package nars.concept.dynamic;

import jcog.data.list.FasterList;
import nars.*;
import nars.concept.Concept;
import nars.concept.TaskConcept;
import nars.table.BeliefTable;
import nars.table.BeliefTables;
import nars.table.dynamic.DynamicTruthTable;
import nars.term.Compound;
import nars.term.Term;
import nars.truth.Truth;
import nars.truth.dynamic.DynamicTruthModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static nars.$.$;
import static nars.$.$$;
import static nars.Op.BELIEF;
import static nars.term.util.TermTest.assertEq;
import static nars.time.Tense.ETERNAL;
import static nars.time.Tense.XTERNAL;
import static org.junit.jupiter.api.Assertions.*;

class DynamicConjTest {
    @Test
    void testDynamicConjunction2() throws Narsese.NarseseException {
        NAR n = NARS.shell();
        n.believe("a:x", 1f, 0.9f);
        n.believe("a:y", 1f, 0.9f);
        n.believe("b:x", 0f, 0.9f);
        n.run(1);
        long now = n.time();

        assertEquals($.t(1f, 0.81f), n.beliefTruth($("(a:x && a:y)"), now));

        assertEquals($.t(0f, 0.81f), n.beliefTruth("(a:x && (--,a:y))", now));

        assertEquals($.t(1f, 0.81f), n.belief($("(a:x && a:y)"), now).truth());

        assertEquals($.t(0f, 0.81f), n.beliefTruth("(b:x && a:y)", now));
        assertEquals($.t(1f, 0.81f), n.beliefTruth("((--,b:x) && a:y)", now));
        assertEquals($.t(0f, 0.81f), n.beliefTruth("((--,b:x) && (--,a:y))", now));
    }

    @Test
    void testDynamicConjunctionEternalOverride() throws Narsese.NarseseException {
        NAR n = NARS.shell()
                .believe($$("a:x"), 0)
                .believe($$("a:y"), 0);

        long now = n.time();
        assertEquals($.t(1f, 0.81f), n.beliefTruth($("(a:x && a:y)"), now));

        {
            //temporal evaluated a specific point
            Task xy = n.belief($("(a:x && a:y)"), now);
            assertEquals("((x-->a)&|(y-->a))", xy.term().toString());
            assertEquals($.t(1f, 0.81f), xy.truth());
        }

        {
            Task xAndNoty = n.belief($("(a:x && --a:y)"), now);
            assertEquals("((--,(y-->a))&|(x-->a))", xAndNoty.term().toString());
            assertEquals($.t(0f, 0.81f), xAndNoty.truth());
        }

        {
            //remain eternal (&& not &|)
            Task xy = n.belief($("(a:x && a:y)"), ETERNAL);
            assertEquals(0, xy.start()); //exact time since it was what was stored
            assertEquals("((x-->a)&|(y-->a))", xy.term().toString());
            assertEquals($.t(1f, 0.81f), xy.truth());
        }


        //override or revise dynamic with an input belief
        {
            n.believe($$("--(a:x && a:y)"), 0);
            assertEquals(1, n.concept("(a:x && a:y)").beliefs().size());

            Task ttEte = n.matchBelief($("(a:x && a:y)"), now);
            assertEquals(1, ttEte.stamp().length);

            //truths dont get merged since the dynamic belief will compute for &| and this asked for &&
            assertTrue(ttEte.toString().contains("((x-->a)&&(y-->a)). 0 %0.0;.90%"));

            Truth tNow = n.beliefTruth($("(a:x && a:y)"), now);
            assertTrue(
                    $.t(0.00f, 0.90f)
                    //$.t(0.32f, 0.90f /*0.87f*/)
                            .equalsIn(tNow, n), ()->"was " + tNow + " at " + now);

        }
        {
            n.believe($$("--(a:x &| a:y)"), 0);
            assertEquals(2, n.concept("(a:x && a:y)").beliefs().size());

            Task ttNow = n.matchBelief($("(a:x &| a:y)"), now);
            assertTrue(ttNow.toString().contains("((x-->a)&|(y-->a)). 0 %.32;.93%"), ttNow.toString());
        }


        Truth tAfter = n.beliefTruth($("(a:x &| a:y)"), now + 2);
        assertTrue($.t(0.32f, 0.82f).equalsIn(tAfter, n), () -> tAfter.toString());

        Truth tLater = n.beliefTruth($("(a:x &| a:y)"), now + 5);
        assertTrue($.t(0.32f, 0.69f).equalsIn(tLater, n), () -> tLater.toString());
    }

    @Test
    void testDynamicConjunctionEternalTemporalMix() throws Narsese.NarseseException {

        String xx = "((e&&x)&|(e&&y))";
//        assertEquals(xx, $$("((x&|y)&&e)").toString());

        NAR n = NARS.shell()
                .believe($$("x"), 0)
                .believe($$("y"), 0)
                .believe($$("e"), ETERNAL);

        Task atZero = n.belief($("(&&,x,y,e)"), 0);
        Task atOne = n.belief($("(&&,x,y,e)"), 1);
        Task atEte = n.belief($("(&&,x,y,e)"), ETERNAL);

//        assertEquals(xx, atZero.term().toString());
//        assertEquals(xx, atOne.term().toString());
//        assertEquals(xx, atEte.term().toString());

        assertEquals(0, atZero.start());
        assertEquals(1, atOne.start());
        assertEquals(0, atEte.start());

        assertEquals(0.73f, atZero.conf(), 0.01f);
        assertEquals(0.73f, atEte.conf(), 0.01f);
        assertEquals(0.60f, atOne.conf(), 0.05f);
    }

    @Test
    void testDynamicConjunctionTemporalOverride() throws Narsese.NarseseException {
        NAR n = NARS.shell()
                .believe("a:x", 1f, 0.9f)
                .believe("a:y", 1f, 0.9f);

        n.run(1);
        long now = n.time();
        assertEquals($.t(1f, 0.81f), n.beliefTruth($("(a:x && a:y)"), now));

        n.believe($$("--(a:x && a:y)"), now);


        Truth tt = n.belief($("(a:x && a:y)"), now).truth();
        assertTrue($.t(0.32f, 0.93f).equalsIn(tt, n), () -> tt.toString());
    }

    @Test
    void testDynamicConjunction3() throws Narsese.NarseseException {
        NAR n = NARS.shell();
        n.believe("a:x", 1f, 0.9f);
        n.believe("a:y", 1f, 0.9f);
        n.believe("a:z", 1f, 0.9f);
        n.run(1);

        TaskConcept cc = (TaskConcept) n.conceptualize($("(&&, a:x, a:y, a:z)"));
        Truth now = n.beliefTruth(cc, n.time());
        assertNotNull(now);
        assertTrue($.t(1f, 0.73f).equalsIn(now, 0.1f), now + " truth at " + n.time());


        {
            TaskConcept ccn = (TaskConcept) n.conceptualize($("(&&, a:x, a:w)"));
            Truth nown = n.beliefTruth(ccn, n.time());
            assertNull(nown);
        }


        Concept ccn = n.conceptualize($("(&&, a:x, (--, a:y), a:z)"));

        {
            Task t = n.belief(ccn.term());
            assertNotNull(t);
            assertEquals(0f, t.freq());
        }

        assertTrue(ccn instanceof TaskConcept);
        Truth nown = n.beliefTruth(ccn, n.time());
        assertEquals("%0.0;.73%", nown.toString());

        n.clear();


        n.believe("a:y", 0, 0.95f);
        n.run(1);
        n.concept("a:y").print();
        Task ay = n.belief($$("a:y"));
        assertTrue(ay.freq() < 0.5f);

        Task bb = n.belief(n.conceptualize($("(&&, a:x, a:y, a:z)")), n.time());
        Truth now2 = bb.truth();
        assertTrue(now2.freq() < 0.4f);

    }

    @Test
    void testDynamicConjunctionEternal() throws Narsese.NarseseException {
        NAR n = NARS.shell();
        n.believe($("x"));
        n.believe($("y"));
        n.believe($("--z"));


        for (long w: new long[]{ETERNAL, 0, 1}) {
            assertEquals($.t(1, 0.81f), n.truth($("(x && y)"), BELIEF, w));
            assertEquals($.t(0, 0.81f), n.truth($("(x && --y)"), BELIEF, w));
            assertEquals($.t(1, 0.81f), n.truth($("(x && --z)"), BELIEF, w));
        }
    }

    @Test
    void testDynamicConjunction2Temporal() throws Narsese.NarseseException {
        NAR n = NARS.shell();
        n.believe($("(x)"), 0);
        n.believe($("(y)"), 4);
        n.time.dur(8);
        TaskConcept cc = (TaskConcept) n.conceptualize($("((x) && (y))"));

        BeliefTable xtable = cc.beliefs();


        assertEquals(0.81f, xtable.answer(0, 0, $("((x) &&+4 (y))"), n).conf(), 0.05f);
        assertEquals(0.74f, xtable.answer(0, 0, $("((x) &&+6 (y))"), n).conf(), 0.07f);
        assertEquals(0.75f, xtable.answer(0, 0, $("((x) &&+2 (y))"), n).conf(), 0.07f);
        assertEquals(0.75f, xtable.answer(0, 0, $("((x) &&+0 (y))"), n).conf(), 0.07f);
        assertEquals(0.62f, xtable.answer(0, 0, $("((x) &&-32 (y))"), n).conf(), 0.2f);


    }

    @Test
    void testDynamicConceptValid1() throws Narsese.NarseseException {
        Term c =

                Op.CONJ.the(XTERNAL, new Term[]{$.$("(--,($1 ==>+- (((joy-->fz)&&fwd) &&+- $1)))"), $.$("(joy-->fz)"), $.$("fwd")}).normalize();

        assertTrue(c instanceof Compound, () -> c.toString());
        assertTrue(Task.taskConceptTerm(c), () -> c + " should be a valid task term");
    }

    @Test
    void testDynamicConceptValid2() throws Narsese.NarseseException {
        Term c =

                Op.CONJ.the(XTERNAL, new Term[]{$.$("(--,((--,#1)&&#2))"), $.$("(--,#2)"), $.varDep(1)}).normalize();

        assertTrue(c instanceof Compound, () -> c.toString());
        assertTrue(Task.taskConceptTerm(c), () -> c + " should be a valid task term");
    }

    @Test
    void testDynamicConjunctionXYZ() throws Narsese.NarseseException {


        NAR n = NARS.shell();
        n.believe("x", 1f, 0.50f);
        n.believe("y", 1f, 0.50f);
        n.believe("z", 0f, 0.81f);
        n.run(1);
        assertEquals(
                "%0.0;.20%", n.beliefTruth(
                        n.conceptualize($("(&&,x,y,z)")
                        ), n.time()).toString()
        );
        {

            Task bXYZ = n.belief($("(&&,x,y,z)"), n.time());
            assertEquals("(&&,x,y,z)", bXYZ.term().toString());
            assertEquals(3, bXYZ.stamp().length);
        }
        {

            Task bXY = n.belief($("(x && y)"), n.time());
            assertEquals("(x&&y)", bXY.term().toString());
            assertEquals(2, bXY.stamp().length);
        }
        {

            Task bXY = n.belief($("(x && y)"), ETERNAL);
            assertEquals("(x&&y)", bXY.term().toString());
            assertEquals(2, bXY.stamp().length);
        }

        assertEquals(
                "%0.0;.41%", n.beliefTruth(
                        n.conceptualize($("(&&,y,z)")
                        ), n.time()).toString()
        );
        assertEquals(
                "%1.0;.25%", n.beliefTruth(
                        n.conceptualize($("(&&,x,y)")
                        ), n.time()).toString()
        );
    }


    @Test
    void testDynamicConjConceptWithNegations() throws Narsese.NarseseException {

        NAR n = NARS.shell();
        for (String s: new String[]{
                "((y-->t) &&+1 (t-->happy))",
                "(--(y-->t) &&+1 (t-->happy))",
                "((y-->t) &&+1 --(t-->happy))",
                "(--(y-->t) &&+1 --(t-->happy))",
        }) {
            Concept c = n.conceptualize($.$(s));
            assertTrue(((BeliefTables)c.beliefs()).tableFirst(DynamicTruthTable.class)!=null);
            assertTrue(((BeliefTables)c.goals()).tableFirst(DynamicTruthTable.class)!=null);
        }

    }

    @Test
    void testDynamicConjunctionFactored() throws Narsese.NarseseException {
        NAR n = NARS.shell();
        n.believe($("x"), ETERNAL);
        n.believe($("y"), 0);
        n.believe($("z"), 2);
        n.time.dur(8);
        TaskConcept cc = (TaskConcept) n.conceptualize($("(&&, x, y, z)"));

        BeliefTable xtable = cc.beliefs();

        {
            Term xyz = $("(x && (y &&+2 z))");
            List<String> components = new FasterList();
            DynamicTruthModel.DynamicConjTruth.ConjIntersection.components(xyz,0, 0,
                    (what,whenStart,whenEnd)->{
                        components.add(what + " @ " + whenStart + ".." + whenEnd); return true;
            });
            assertEquals(2, components.size());

            Task t = xtable.answer(0, 0, xyz, n);
            assertNotNull(t);
            assertEquals(1f, t.freq(), 0.05f);
            assertEquals(0.81f, t.conf(), 0.4f);
            assertEq("((y &&+2 z)&&x)", t.term());
        }
        {
            Term xyz = $("((x &| y) &&+2 (x &| z))");
            assertEq("((y &&+2 z)&&x)", xyz);
            Task t = xtable.answer(0, 0, xyz, n);
            assertEquals(1f, t.freq(), 0.05f);
            assertEquals(0.81f, t.conf(), 0.4f);
        }
        {
            Task t = xtable.answer(0, 0, $("((x && y) &&+2 (x && z))"), n);
            assertEquals(1f, t.freq(), 0.05f);
            assertEquals(0.81f, t.conf(), 0.4f);
        }


    }


    @Test
    void testDynamicConjunctionFactoredInImpl() throws Narsese.NarseseException {
        NAR n = NARS.shell();
        n.believe($("(x==>a)"), ETERNAL);
        n.believe($("(y ==>+2 a)"), 0);
        n.believe($("(z =|> a)"), 2);
        n.time.dur(8);

        {
            Term xyz = $("((x && (y &&+2 z))=|>a)");
            Task t = n.answer(xyz, BELIEF, 0);
            assertEquals(xyz, t.term());
        }
    }

//    @Test public void testDynamicIntersectionInvalidCommon() throws Narsese.NarseseException {
//        //TODO
//        NAR n = NARS.shell();
//        n.believe("(x&&+1):y", 0.75f, 0.50f);
//        n.believe("(x&&+2):z", 0.25f, 0.50f);
//        n.run(1);
//        Term xMinY = $("(x(x ~ y)");
//        Term yMinX = $("(y ~ x)");
//        assertEquals(DynamicTruthBeliefTable.class, n.conceptualize(xMinY).beliefs().getClass());
//        assertNull(
//                "%.56;.25%", n.beliefTruth(xMinY, n.time()).toString()
//        );
//    }
}
