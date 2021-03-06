package nars.nal.nal8;

import nars.$;
import nars.Narsese;
import nars.Op;
import nars.nal.nal7.NAL7Test;
import nars.task.NALTask;
import nars.term.Term;
import nars.test.NALTest;
import nars.test.TestNAR;
import nars.time.Tense;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static nars.Op.*;
import static nars.time.Tense.ETERNAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class NAL8Test extends NALTest {

    public static final int cycles = 100;

    @BeforeEach
    void setTolerance() {
        test.confTolerance(NAL7Test.CONF_TOLERANCE_FOR_PROJECTIONS);
        test.nar.confResolution.set(0.04f); //coarse
        test.nar.freqResolution.set(0.01f); //coarse
        test.nar.termVolumeMax.set(24);
    }

    @Test
    void subsent_1_even_simpler_simplerBeliefTemporal() {

        test

                .input("(open(t1) &&+5 (t1-->[opened])). |")
                .mustBelieve(cycles, "open(t1)", 1.0f, 0.81f, 0)
                .mustBelieve(cycles, "(t1-->[opened])", 1.0f, 0.81f, 5)
                .mustNotOutput(cycles, "open(t1)", BELIEF, (t) -> t != 0)
                .mustNotOutput(cycles, "(t1-->[opened])", BELIEF, (t) -> t != 5)
        ;
    }

    @Test
    void subsent_1_even_simpler_simplerGoalTemporal() {

        test

                .input("(open(t1) &&+5 opened(t1))! |")
                .mustGoal(cycles, "open(t1)", 1.0f, 0.81f, 0)
                .mustNotOutput(cycles, "open(t1)", GOAL, t -> t != 0)
                .mustNotOutput(cycles, "opened(t1)", GOAL)
        ;
    }


    @Test
    void testCorrectGoalOccAndDuration() throws Narsese.NarseseException {
        /*
        $1.0 (happy-->dx)! 1751 %.46;.15% {1753: _gpeß~Èkw;_gpeß~Èky} (((%1-->%2),(%3-->%2),neqRCom(%1,%3)),((%1-->%3),((Induction-->Belief),(Weak-->Goal),(Backwards-->Permute))))
            $NaN (happy-->noid)! 1754⋈1759 %1.0;.90% {1748: _gpeß~Èky}
            $1.0 (dx-->noid). 1743⋈1763 %.46;.90% {1743: _gpeß~Èkw}
         */

        test
                .input(new NALTask($.$("(a-->b)"), GOAL, $.t(1f, 0.9f), 5, 10, 20, new long[]{100}).priSet(0.5f))
                .input(new NALTask($.$("(c-->b)"), BELIEF, $.t(1f, 0.9f), 4, 5, 25, new long[]{101}).priSet(0.5f))
                .mustGoal(cycles, "(a-->c)", 1f, 0.4f,

                        x -> x >= 5 && x <= 25
                )
        ;

    }


    @Test
    void firstGoalConjunctionEvent() {

        test
                .input("(hold(SELF,{t002}) &&+5 (at(SELF,{t001}) && open({t001})))! |")
                .mustGoal(cycles, "hold(SELF,{t002})", 1.0f, 0.81f, 0)
                .mustNotOutput(cycles, "hold(SELF,{t002})", GOAL, ETERNAL);
    }

    @Test
    void subgoal_2_inner_dt() {

        test
                .input("(hold(SELF,{t002}) &&+5 (at(SELF,{t001}) &&+5 open({t001})))! |")
                .mustGoal(cycles, "hold(SELF,{t002})", 1.0f, 0.73f, 0)
                .mustNotOutput(cycles, "hold(SELF,{t002})", GOAL, ETERNAL);
    }

    @Test
    void subbelief_2() throws Narsese.NarseseException {

        {
            Term t = $.$("(hold(SELF,{t002}) &&+5 (at(SELF,{t001}) &&+5 open({t001})))");
            assertEquals(2, t.subs());
            assertEquals(10, t.eventRange());
        }

        test
                .input("(hold(SELF,{t002}) &&+2 (at(SELF,{t001}) &&+2 open({t001}))). |")
                .mustBelieve(cycles, "hold(SELF,{t002})", 1.0f, 0.73f, 0)
                .mustBelieve(cycles, "(at(SELF,{t001}) &&+2 open({t001}))", 1.0f, 0.81f, 2)
        ;
    }

    @Test
    void subbelief_2easy() {
        test
                .input("(a:b &&+5 x:y). |")
                .mustBelieve(cycles, "a:b", 1.0f, 0.81f, (t -> t == 0))
                .mustBelieve(cycles, "x:y", 1.0f, 0.81f, (t -> t == 5))
        ;
    }

    @Test
    void temporal_deduction_1() {

        TestNAR tester = test;

        tester.input("pick:t2. |");
        tester.inputAt(2, "(pick:t2 ==>+5 hold:t2).");
        tester.mustBelieve(cycles, "hold:t2", 1.0f, 0.81f, 5);

    }

    @Test
    void subbelief_2medium() {


        test
                .input("(a:b &&+5 (c:d &&+5 x:y)). |")
                .mustBelieve(cycles, "a:b", 1.0f, 0.73f, 0)
                .mustBelieve(cycles, "c:d", 1.0f, 0.73f, 5)
                .mustBelieve(cycles, "x:y", 1.0f, 0.73f, 10)
        ;
    }


    @Test
    void testDesiredConjPos() {
        TestNAR t = test;
        t
                .believe("x")
                .goal("(x&&y)")
                .mustGoal(cycles, "y", 1f, 0.81f);
    }

    @Test
    void testDesiredConjNeg() {
        test.believe("--x")
                .goal("(--x && y)")
                .mustGoal(cycles, "y", 1f, 0.81f);
    }

    @Test
    void testImplGoalDuration() {
        /* wrong time
        $.30 x(intValue,(),3)! 5648⋈5696 %1.0;.13% {6874: 1;2;4;9;d;e;l;q} ((%1,(%2==>%3),notImpl(%1)),(unisubst(%2,%3,%1,"$"),((AbductionPB-->Belief),(DeciInduction-->Goal))))
            $1.0 x(intValue,(),3)! 5600 %1.0;.90% {5600: q}
            $.07 (x(intValue,(),3) ==>-48 x(intValue,(),3)). 3600 %1.0;.19% {5415: 1;2;4;9;d;e;l}
        */
        test
                .goal("x(intValue,(),3)", Tense.Present, 1f, 0.9f)
                .believe("(x(intValue,(),3) ==>-48 x(intValue,(),3))")
                .mustGoal(cycles, "x(intValue,(),3)", 1f, 0.81f, 48, 48);
    }


    @Test
    void testBelievedImplOfDesire() {

        TestNAR t = test;
        t
                .goal("x")
                .believe("(x==>y)")
                .mustGoal(cycles, "y", 1f, 0.81f);
    }


//    @Test
//    void testGoalConjunctionDecomposeNeg() {
//        test
//                .goal("(x &&+3 y)", Tense.Present, 0f, 0.9f)
//                .mustNotOutput(cycles, "x", GOAL, 0);
//    }

    @Disabled
    @Test
    void testGoalConjunctionDecomposeViaStrongTruth() {

        test
                .goal("(&&, x, y, z, w)", Tense.Present, 1f, 0.9f)
                .believe("w", Tense.Present, 0.9f, 0.9f)
                .mustGoal(cycles, "w", 0.9f, 0.81f, 0)
        ;
    }

    @Disabled
    @Test
    void testGoalConjunctionDecomposeViaStrongTruthNeg() {

        test
                .goal("(&&, x, y, z, --w)", Tense.Present, 1f, 0.9f)
                .believe("w", Tense.Present, 0.1f, 0.9f)
                .mustGoal(cycles, "w", 0.9f, 0.81f, 0)
        ;
    }

    @Test
    void testStrongNegativePositiveInheritance() {

        test
                .goal("--(A-->B)")
                .believe("(B-->C)")
                .mustGoal(cycles, "(A-->C)", 0f, 0.81f)
        ;
    }

    @Test
    void testStrongNegativeNegativeInheritance() {

        test
                .goal("--(A-->B)")
                .believe("--(B-->C)")
                .mustNotOutput(5, "(A-->C)", GOAL, 0f, 1f, 0f, 1f, ETERNAL)
        ;
    }


    @Test
    void testConditionalGoalConjunctionDecomposePositiveGoal() {

        test
                .goal("x", Tense.Present, 1f, 0.9f)
                .believe("(x &&+3 y)", Tense.Present, 1f, 0.9f)
                .mustBelieve(cycles, "x", 1f, 0.81f, 0)
                .mustBelieve(cycles, "y", 1f, 0.81f, 3)

        ;
    }


    @Test
    void testConditionalGoalConjunctionDecomposePositivePostconditionGoal() {

        test
                .goal("y", Tense.Present, 1f, 0.9f)
                .believe("(x &&+3 y)", Tense.Present, 1f, 0.9f)
                .mustBelieve(cycles, "x", 1f, 0.81f, 0)
                .mustBelieve(cycles, "y", 1f, 0.81f, 3)
                .mustGoal(cycles, "x", 1f, 0.81f, t -> t >= 0);
    }

    @Test
    void testConditionalGoalConjunctionDecomposePositiveGoalNegativeBeliefSubterm() {

        test
                .goal("x", Tense.Present, 1f, 0.9f)
                .believe("(--x &&+3 y)", Tense.Present, 1f, 0.9f)
                .mustBelieve(cycles, "x", 0f, 0.81f, 0)
                .mustBelieve(cycles, "y", 1f, 0.81f, 3)
        ;

    }

    @Test
    void testConditionalGoalConjunctionDecomposeNegativeGoal() {

        test
                .goal("x", Tense.Present, 0f, 0.9f)
                .believe("(x &&+3 y)", Tense.Present, 1f, 0.9f)
                .mustBelieve(cycles, "x", 1f, 0.81f, 0)
                .mustBelieve(cycles, "y", 1f, 0.81f, 3)

        ;
    }


    @Test
    void testConjSeqGoalDecomposeForward() {


        test
                .goal("(x &&+3 y)", Tense.Present, 1f, 0.9f)
                .believe("x", Tense.Present, 1f, 0.9f)
                .mustGoal(cycles, "y", 1f, 0.81f, (t) -> t == 3)
                .mustNotOutput(cycles, "y", GOAL, t -> t != 3);
    }

    @Test
    void testConjParGoalDecomposeForward() {


        test
                .goal("(x &| y)", Tense.Present, 1f, 0.9f)
                .believe("x", Tense.Present, 1f, 0.9f)
                .mustGoal(cycles, "y", 1f, 0.81f, 0)
                .mustNotOutput(cycles, "y", GOAL, t -> t != 0);
    }

    @Test
    void testConjSeqGoalNegDecomposeForward() {


        test
                .goal("(--x &&+3 y)", Tense.Present, 1f, 0.9f)
                .believe("x", Tense.Present, 0f, 0.9f)
                .mustGoal(cycles, "y", 1f, 0.81f, 3)
                .mustNotOutput(cycles, "y", GOAL, ETERNAL)
                .mustNotOutput(cycles, "y", GOAL, 0)
        ;
    }

    @Test
    void conditionalDisjDecomposePos() {

        test
                .goal("(x || y)", Tense.Present, 1f, 0.9f)
                .inputAt(2, "--x. |")
                .mustGoal(cycles, "y", 1f, 0.81f, 2)
        ;
    }

    @Test
    void conditionalDisjDecomposeNeg() {

        test
                .goal("(--x || y)", Tense.Present, 1f, 0.9f)
                .believe("x", Tense.Present, 1f, 0.9f)
                .mustGoal(cycles, "y", 1f, 0.81f, 0)
        ;
    }

    @Test
    void testInhibition() {

        test.nar.termVolumeMax.set(5);

        test
                .goal("reward")
                .believe("(good ==> reward)", 1, 0.9f)
                .believe("(--bad ==> reward)", 1, 0.9f)
                .mustGoal(cycles, "good", 1.0f, 0.45f)
                .mustGoal(cycles, "bad", 0.0f, 0.45f);

    }

    @Test
    void testInhibitionInverse() {
        test.nar.termVolumeMax.set(5);

        test
                .goal("--reward")
                .believe("(good ==> reward)", 1, 0.9f)
                .believe("(bad ==> reward)", 0, 0.9f)
                .mustGoal(cycles, "bad", 1.0f, 0.81f)
        //mustGoal(cycles, "good", 0.0f, 0.81f)
        //.mustNotOutput(cycles, "good", GOAL, 0f, 1f, 0.8f, 1f, ETERNAL)
        ;
    }


    @Test
    void testInhibition0() {
        test.nar.termVolumeMax.set(5);

        test
                .goal("reward")
                .believe("(bad ==> --reward)", 1, 0.9f)

                .mustNotOutput(cycles, "bad", GOAL, 0.5f, 1f, 0f, 1f, ETERNAL);
    }

    @Test
    void testInhibition1() {
        test.nar.termVolumeMax.set(5);


        test
                .goal("reward")
                .believe("(good ==> reward)", 1, 0.9f)

                .mustGoal(cycles, "good", 1.0f, 0.81f)
                .mustNotOutput(cycles, "good", GOAL, 0.0f, 0.7f, 0.5f, 1f, ETERNAL)


        ;
    }

    @Test
    void testInhibitionReverse() {
        test.nar.termVolumeMax.set(5);

        test
                .goal("reward")
                .believe("(reward ==> good)", 1, 0.9f)

                .mustGoal(cycles, "good", 1.0f, 0.45f)
                .mustNotOutput(cycles, "good", GOAL, 0.0f, 0.5f, 0.0f, 1f, ETERNAL);

    }


    @Test
    void testGoalSimilaritySpreading() {
        test.nar.termVolumeMax.set(5);

        test
                .input("R!")
                .input("(G <-> R).")
                .mustGoal(cycles, "G", 1.0f, 0.81f);
    }

    @Disabled
    @Test
    void testGoalSimilaritySpreadingNeg() {
        test.nar.termVolumeMax.set(5);
        test
                .input("R!")
                .input("--(G <-> R).")
                .mustGoal(cycles, "G", 0.0f, 0.4f);
    }

    @Test
    void testGoalSimilaritySpreadingNegInside() {
        test.nar.termVolumeMax.set(5);

        test
                .input("--R!")
                .input("(G <-> --R).")
                .mustGoal(cycles, "G", 1.0f, 0.81f);
    }

//    @Test
//    void testGoalSimilaritySpreadingNegInsideNeg() {
//        test
//                .input("--R!")
//                .input("--(G <-> --R).")
//                .mustGoal(cycles, "G", 0.0f, 0.4f);
//    }

    @Test
    void testGoalSimilaritySpreadingParameter() {
        test.nar.termVolumeMax.set(5);
        test
                .input("R(x)!")
                .input("(x <-> y).")
                .mustGoal(cycles, "R(y)", 1.0f, 0.4f);
    }


    @Disabled
    @Test
    void testInheritanceDecompositionTemporalGoal() {


        test

                .inputAt(0, "(((in)|(left))-->cam)! |")
                .mustGoal(cycles, "cam(in)", 1f, 0.81f, 0)
                .mustGoal(cycles, "cam(left)", 1f, 0.81f, 0);

    }

    @Test
    void testInheritanceDecompositionTemporalBelief() {


        test
                .inputAt(0, "(((in)|(left))-->cam). |")
                .mustBelieve(cycles, "cam(in)", 1f, 0.81f, 0)
                .mustBelieve(cycles, "cam(left)", 1f, 0.81f, 0);

    }

    @Test
    @Disabled
    void disjunctionBackwardsQuestionEternal() {


        test
                .inputAt(0, "(||, x, y)?")
                .believe("x")
                .mustBelieve(cycles, "(&&, (--,x), (--,y))", 0f, 0.81f, ETERNAL);
    }


    @Disabled
    @Test
    void questConjunction() {

        test
                .input("(a && b).")
                .input("a@")
                .mustOutput(cycles, "b", Op.QUEST, 0, 1f);

    }

    @Test
    void testGoalConjunctionPos1Eternal() {

        test
                .input("a!")
                .input("(a && b).")
                .mustGoal(cycles, "b", 1f, 0.81f);
    }

    @Test
    void testGoalConjunctionPos1Parallel() {

        test
                .input("a!")
                .input("(a &| b).")
                .mustGoal(cycles, "b", 1f, 0.81f);
    }

    @Test
    void testGoalConjunctionNegative1N() {


        test
                .input("--a!")
                .input("(--a && b).")
                .mustGoal(cycles, "b", 1f, 0.81f);
    }

    @Test
    void testGoalConjunctionNegative2() {

        test
                .input("a!")
                .input("(a && --b).")
                .mustGoal(cycles, "b", 0f, 0.81f);
    }


    @ParameterizedTest
    @ValueSource(strings = {"&|", "&&"})
    void testGoalImplComponentEternal(String conj) {
        test.nar.termVolumeMax.set(3);
        test
                .input("happy!")
                .input("(in =|> (happy " + conj + " --out)).")
                .mustBelieve(cycles, "(in =|> happy)", 1f, 0.81f)
                .mustGoal(cycles, "in", 1f, 0.73f);
    }

    @ParameterizedTest
    @ValueSource(strings = {"&&", "&|"})
    void testGoalImplComponentEternalSubjNeg(String conj) {
        test.input("happy!")
                .input("(--in =|> (happy " + conj + " --out)).")
                .mustGoal(cycles, "in", 0f, 0.42f);
    }

    @Test
    void testConjDecomposeWithDepVar() {

        test
                .input("(#1 && --out)! |")
                .mustGoal(cycles, "out", 0f, 0.81f, 0);
    }

    @Test
    void testPredictiveImplicationTemporalTemporal() {
        /*
        wrong timing: should be (out)! @ 16
        $.36;.02$ (out)! 13 %.35;.05% {13: 9;a;b;t;S;Ü} ((%1,(%2==>%3),belief(negative),time(decomposeBelief)),((--,unisubst(%2,%3,%1)),((AbductionPN-->Belief),(DeductionPN-->Goal))))
            $.50;.90$ (happy)! 13 %1.0;.90% {13: Ü}
            $0.0;.02$ ((out) ==>-3 (happy)). 10 %.35;.05% {10: 9;a;b;t;S} ((%1,(%2==>((--,%3)&&%1073742340..+)),time(dtBeliefExact),notImpl(%1073742340..+)),(unisubst((%2 ==>+- (&&,%1073742340..+)),(--,%3),(--,%1)),((DeductionN-->Belief))))
        */

        test
                .inputAt(0, "((out) ==>-3 (happy)). |")
                .inputAt(13, "(happy)! |")
                .mustGoal(cycles, "(out)", 1f, 0.45f, (t) -> t > 13)
                .mustNotOutput(cycles, "(out)", GOAL, 3);
    }

    @Test
    void testPredictiveImplicationTemporalTemporalOpposite() {

        test
                .inputAt(0, "(happy ==>-3 out). |")
                .inputAt(2, "happy! |")
                .mustGoal(cycles, "out", 1f, 0.45f, (t) -> t == -1);


    }

    @Disabled
    @Test
    void testConjDecomposeSequenceEmbedsAntiGoalNeg() {

        test
                .input("(a &&+1 (x &&+1 y)).")
                .input("--x!")
                .mustGoal(cycles, "(a &&+2 y)", 0f, 0.4f, t -> t == ETERNAL);
    }

    @Disabled
    @Test
    void testConjDecomposeSequenceEmbedsAntiGoalPos() {

        test
                .input("(a &&+1 (--x &&+1 y)).")
                .input("x!")
                .mustGoal(cycles, "(a &&+2 y)", 0f, 0.4f, t -> t == ETERNAL);
    }

    @Test
    void testPredictiveImplicationTemporalTemporalNeg() {

        test
                .inputAt(0, "(--(out) ==>-3 (happy)). |")
                .inputAt(5, "(happy)! |")
                .mustGoal(cycles, "(out)", 0f, 0.45f, /*~*/8);

    }


    @Test
    void testPredictiveEquivalenceTemporalTemporalNeg() {

        test
                .inputAt(0, "(--(out) ==>-3 (happy)). |")
                .inputAt(0, "((happy) ==>+3 --(out)). |")
                .inputAt(5, "(happy)! |")
                .mustGoal(cycles, "(out)", 0f, 0.45f, 8)
                .mustNotOutput(cycles, "(out)", GOAL, 3);
    }


    @ParameterizedTest
    @ValueSource(ints = {-4, -3, +0, +3, +4})
    void implDecomposeGoalPredicate1(int dt) {
        testDecomposeGoalPredicateImplSubjPred(dt, "a/b");
    }

    @ParameterizedTest
    @ValueSource(ints = {-4, -3, +0, +3, +4})
    void implDecomposeGoalPredicate2(int dt) {
        testDecomposeGoalPredicateImplSubjPred(dt, "(a &&+1 a2)/b");
    }

    @ParameterizedTest
    @ValueSource(ints = {-3, +0, +3})
    void implDecomposeGoalPredicate2swap(int dt) {
        testDecomposeGoalPredicateImplSubjPred(dt, "(a2 &&+1 a)/b");
    }

    @ParameterizedTest
    @ValueSource(ints = {-4, -3, +0, +3, +4})
    void implDecomposeGoalPredicate3(int dt) {
        testDecomposeGoalPredicateImplSubjPred(dt, "(a &&+1 --a)/b");
    }

    @ParameterizedTest
    @ValueSource(ints = {-4, -3, +0, +3, +4})
    void implDecomposeGoalPredicate4(int dt) {
        testDecomposeGoalPredicateImplSubjPred(dt, "(a &&+1 a)/b");
    }


    private void testDecomposeGoalPredicateImplSubjPred(int dt, String sj) {

        int start = 1;
        int when = 6;

        int goalAt = //Math.max(when,
                when - dt - $.$$(sj).eventRange();

        String[] subjPred = sj.split("\\/");
        assertEquals(2, subjPred.length);

        test
                .inputAt(start, "(" + subjPred[0] + " ==>" + ((dt >= 0 ? "+" : "-") + Math.abs(dt)) + " " + subjPred[1] + "). |")
                .inputAt(when, "b! |")
                .mustGoal(when * 32, subjPred[0], 1f, 0.45f,
                        (t) -> t >= goalAt)

        ;
    }

    @Test
    void conjDecomposeGoalAfter() {

        test
                .inputAt(1, "(a &&+3 b). |")
                .inputAt(5, "b! |")

                .mustGoal(cycles, "a", 1f, 0.3f, t -> t == 2)
                .mustNotOutput(cycles, "a", GOAL, ETERNAL);
    }

    @Test
    void conjDecomposeGoalAfterNeg() {

        test
                .inputAt(1, "(a &&+3 --b).")
                .inputAt(5, "b! |")
                .mustGoal(cycles, "a", 0f, 0.3f, t -> t == 2)
                .mustNotOutput(cycles, "a", GOAL, ETERNAL);
    }
    @Test
    void conjDecomposeGoalAfterNegSeq() {

        test
                .input("((x &&+3 y) &&+1 --b).")
                .inputAt(10, "b! |")
                .mustGoal(cycles, "(x &&+3 y)", 0f, 0.81f, t -> t == 6);
    }
    @Test
    void conjDecomposeGoalAfterPosNeg() {

        test
                .inputAt(3, "(--a &&+3 b). |")
                .inputAt(6, "b! |")
                .mustGoal(cycles, "a", 0f, 0.81f, t -> t == 3)
                .mustNotOutput(cycles, "a", GOAL, ETERNAL);
    }

    @Test
    void implDecomposeGoalAfterPosNeg() {

        test
                .inputAt(0, "(--a ==>+1 b). |")
                .inputAt(1, "b! |")
                .mustGoal(5, "a", 0f, 0.81f, (t) -> t == 0);

    }

    @Test
    void conjDecomposeGoalAfterNegNeg() {

        test
                .inputAt(3, "(a &&+3 --b). |")
                .inputAt(6, "--b! |")
                .mustGoal(16, "a", 1f, 0.5f, t -> t >= 3)
                .mustNotOutput(16, "a", GOAL, ETERNAL);
    }

    @Test
    void implDecomposeGoalBeforeTemporalEte() {

        test
                .inputAt(1, "(x ==>-1 y).")
                .inputAt(2, "y! |")
                .mustGoal(cycles, "x", 1f, 0.45f, 3);

    }

    @Test
    void implDecomposeGoalBeforeTemporalSameTerm() {
        test
                .inputAt(1, "(x ==>-1 x).")
                .inputAt(2, "x! |")
                .mustGoal(cycles, "x", 1f, 0.45f, 3);
    }

    @Test
    void implDecomposeGoalBeforeTemporalSameTermNegated() {
        test
                .inputAt(1, "(--x ==>-1 x).")
                .inputAt(2, "x! |")
                .mustGoal(cycles, "x", 0f, 0.45f, (t) -> t >= 2);
    }

    @Test
    void implDecomposeGoalBeforeTemporalImpl() {

        test
                .inputAt(1, "(x ==>-1 y). |")
                .inputAt(2, "y! |")
                .mustGoal(cycles, "x", 1f, 0.81f, 3);
    }

    @Test
    void deriveNegInhGoal() {

        test
                .input("b:a!")
                .input("c:b.")
                .input("--y:x!")
                .input("z:y.")
                .mustGoal(cycles, "c:a", 1f, 0.81f)
                .mustGoal(cycles, "z:x", 0f, 0.81f);
    }


    @Disabled
    @Test
    void questImplDt() {

        test
                .inputAt(0, "(a,b).")
                .inputAt(0, "a. |")
                .inputAt(4, "b@ |")

                .mustOutput(cycles, "(b ==>-4 a)", QUESTION, 0f, 1f, 0f, 1f, 4);
    }


    @Test
    void testConjPrior() {
        test.input("happy!")
                .input("((((--,happy) &&+2 happy) &&+20 y) &&+2 ((--,y) &&+1 happy)). |")
                .mustGoal(cycles, "((happy &&+20 y) &&+2 ((--,y) &&+1 happy))", 1f, 0.2f, (t) -> t >= 0)
                .mustGoal(cycles, "((happy &&+20 y) &&+2 (--,y))", 1f, 0.4f, (t) -> t >= 0)
                .mustGoal(cycles, "(y &&+2 (--,y))", 1f, 0.5f, (t) -> t >= 0);
    }

    @Test
    void testSimilarityGoalPosBelief() {
        test.goal("(it<->here)")
                .believe("(here<->near)")
                .mustGoal(cycles, "(it<->near)", 1f, 0.45f);
    }

    @Test
    void testSimilarityGoalNegBelief() {
        test.goal("--(it<->here)")
                .believe("(here<->near)")
                .mustGoal(cycles, "(it<->near)", 0f, 0.45f);
    }

    @Disabled
    @Test
    void testGoalByConjAssociationPosPos() {

        test.goal("a")
                .believe("(b &&+1 (a &&+1 c))")
                .mustGoal(cycles, "(b &&+2 c)", 1f, 0.45f)
                .mustNotOutput(cycles, "(b &&+2 c)", GOAL, 0f, 0.5f, 0f, 1f, x -> true);
    }

    @Disabled
    @Test
    void testGoalByConjAssociationNegPos() {

        test.goal("--a")
                .believe("(b &&+1 (a &&+1 c))")
                .mustGoal(cycles, "(b &&+2 c)", 0f, 0.45f, (t) -> t == ETERNAL)
                .mustNotOutput(cycles, "(b &&+2 c)", GOAL, 0.5f, 1f, 0f, 1f, x -> true);
    }

    @Disabled
    @Test
    void testGoalByConjAssociationPosNeg() {

        test.goal("a")
                .believe("(b &&+1 (--a &&+1 c))")
                .mustGoal(cycles, "(b &&+2 c)", 0f, 0.45f, (t) -> t == ETERNAL)
                .mustNotOutput(cycles, "(b &&+2 c)", GOAL, 0.5f, 1f, 0f, 1f, x -> true);
    }

    @Disabled
    @Test
    void testGoalByConjAssociationNegNeg() {

        test.goal("--a")
                .believe("(b &&+1 (--a &&+1 c))")
                .mustGoal(cycles, "(b &&+2 c)", 1f, 0.45f, (t) -> t == ETERNAL)
                .mustNotOutput(cycles, "(b &&+2 c)", GOAL, 0f, 0.5f, 0f, 1f, x -> true);
    }

    //    @Test void testGoalBeliefDecomposeTimeRangingRepeat() {
//        /*
//        $.03 vel(fz,move)! 1536601075540⋈1536601112860 %.57;.03% {1536601090589: } ((%1,%2,eventOf(%2,%1)),(conjDropIfLatest(%2,%1),((Desire-->Goal))))
//            //belief timing ignored
//
//            $1.0 vel(fz,move)! 1536601075540⋈1536601112860 %.57;.15% {1536601088494: }
//                    37S
//
//            $.09 (vel(fz,move) &&+460 vel(fz,move)). 1536601089440⋈1536601089580 %.39;.82% {1536601090008: ÖqdUçëípß;ÖqdUçëíqc;ÖqdUçëíqO} ((%1,%2,(--,is(%2,"==>")),(--,is(%1,"==>"))),((polarize(%1,task) &&+- polarize(%2,belief)),((IntersectionDepolarized-->Belief),(Relative-->Time),(VarIntro-->Also))))
//                    140ms, at the correct start
//         */
//        test
//            .input("x! +0..+100")
//            .input("(x &&+5 x). +20..+30")
//            .mustGoal(cycles, "x", 1f, 0.1f, (t) -> t > 20 /* dropping 2nd event */)
//            .mustNotOutput(cycles, "x", GOAL, 0f, 0.5f, 0f, 1f, (long s, long e) -> (e-s > 10));
//
//    }
    @Test
    void testGoalBeliefDecomposeTimeRangingDiffer() {
        test
                .input("x! +0..+100")
                .input("(y &&+5 x). +20..+30")
                .mustGoal(cycles, "y", 1f, 0.1f, (a, b) -> (a == -5 && b == 5))
                .mustNotOutput(cycles, "x", GOAL, (t) -> true /* shouldnt drop first event */)
                .mustNotOutput(cycles, "x", GOAL, 0f, 0.5f, 0f, 1f, (long s, long e) -> (e - s > 10))
                .mustNotOutput(cycles, "y", GOAL, 0f, 0.5f, 0f, 1f, (long s, long e) -> (e - s > 10));


    }


    @Test
    void decomposeConjuncitonGoalBeliefRelative() {
        /*
        wrong: output should be relative to belief
        $.01 ((Y-->trackXY),"+")! 2428⋈2432 %1.0;.20% {3101: Gæ;IM;IÂ} ((%1,%2,eventOfNeg(%1,%2)),(conjDropIfEarliest(%1,(--,%2)),((DeductionPN-->Belief),(DesirePN-->Goal))))
            $.50 ((--,((X-->trackXY),"+")) &&+8 (--,((Y-->trackXY),"+")))! 2420⋈2424 %0.0;.20% {3101: IM;IÂ}
            $.49 ((X-->trackXY),"+"). 2340⋈2576 %0.0;.90% {2340: Gæ}
        */
        test.nar.time.dur(16);

        test

                .inputAt(0, "(y &&+5 x)! |")
                .inputAt(2, "y. |")
                .mustGoal(cycles, "x", 1f, 0.1f, (t) -> t >= 7)
                .mustNotOutput(cycles, "x", GOAL, (t) -> t < 7)
        ;
    }

    @Test
    void testRepeatConjDropWTF() {
        /* wrong:
        $.02 reward(trackXY)! 172⋈216 %1.0;.50% {11461: 9;b;3e} ((%1,%2,eventOfNeg(%2,%1)),(conjDropIfLatest(%2,(--,%1)),((DesireN-->Goal),(TaskInBelief-->Time))))
            $1.0 reward(trackXY)! 172⋈276 %1.0;.95% {626: }
            $.32 ((--,reward(trackXY)) &&+172 (--,reward(trackXY))). 172⋈216 %1.0;.65% {969: 9;b;3e©} ((%1,%2,eventOfNeg(%1,%2)),(conjWithout(%1,(--,%2)),((StructuralDeduction-->Belief))))
            */
        test.confTolerance(0.01f);
        test
                .inputAt(0, "(--x &&+2 --x).")
                .inputAt(2, "x! |")
                .mustNotOutput(cycles, "x", GOAL, 0, 1, 0, 0.85f, t -> true)
        ;

    }

    @Test
    void testConjSequenceOutcome() {
        test.confTolerance(0.01f);
        test

                .inputAt(0, "(x &&+1 y)! |")
                .inputAt(0, "(z &&+1 (x &&+1 y)).")
                .mustGoal(cycles, "z", 1, 0.81f, t -> true)
        ;
    }

    @Test
    void testNotEventOfNeg() {
    /*
    NO
    $.01 ((--,(reward-->trackXY)) &&+15 (reward-->trackXY))! 5811⋈5817 %1.0;.01% {5814: 1;d.;dÇ;dÓ;dá;dë;d÷;dþ;e7;Cù;D6;DQ;DÀ;DÇ;DÎ;DÖ}
                X, C, eventOf(C,X), --eventOfNeg(C,X)   |- C, (Goal:DesireWeak, Time:BeliefAtTask)
        $.50 (reward-->trackXY)! %1.0;.90% {0: 1}
        $1.0 ((--,(reward-->trackXY)) &&+15 (reward-->trackXY)). 5811⋈5816 %.03;.87% {5814: d.;dÇ;dÓ;dá;dë;d÷;dþ;e4;e7;Cù;D6;DQ;DÀ;DÇ;DÎ;DÖ}
        */
        test
                .believe("(--x &&+1 x)")
                .goal("x")
                .mustNotOutput(cycles, "(--x &&+1 x)", GOAL);
    }

    @Test
    void testSubIfWTF() {
        /*
        $.57 good! 144⋈152 %.73;.13% {966: 1;Ð;Ô;Ø} B, (C ==> A), --is(A,"#"),--is(C,"#"), --is(B,"==>") |- unisubst(C,A,B), (Belief:Post, Goal:PostStrong, Time:TaskMinusBeliefDT)
            $1.0 good! %1.0;.90% {0: 1}
            $.48 (better ==>+8 better). 152⋈160 %.73;.20% {699: Ð;Ô;Ø} B, A, --is(A,"==>") |- polarize((polarize(A,belief) ==> B),task), (Belief:InductionDepolarized, Time:BeliefRelative, Also:VarIntro)
            */

        test
            .input("good! |")
            .input("(better ==>+1 better). |")
            .mustNotOutput(cycles, "good", GOAL, 0, 1, 0, 1, t->true);
    }
}
