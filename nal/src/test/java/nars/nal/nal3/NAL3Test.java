package nars.nal.nal3;


import nars.NAR;
import nars.NARS;
import nars.Param;
import nars.test.NALTest;
import nars.test.TestNAR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static nars.Op.BELIEF;
import static nars.time.Tense.ETERNAL;

public class NAL3Test extends NALTest {

    static final int cycles = 100;

    @Override
    protected NAR nar() {
        NAR n = NARS.tmp(3);
        n.termVolumeMax.set(7);
        return n;
    }

    @Test
    void compound_composition_two_premises() {

        TestNAR tester = test;
        tester.believe("(swan --> swimmer)", 0.9f, 0.9f);
        tester.believe("(swan --> bird)", 0.8f, 0.9f);
        tester.mustBelieve(cycles, "(swan --> (bird | swimmer))", 0.98f, 0.81f);
        tester.mustBelieve(cycles, "(swan --> (bird & swimmer))", 0.72f, 0.81f);

    }

    @Test
    void compound_composition_two_premises2() {

        TestNAR tester = test;
        tester.believe("<sport --> competition>", 0.9f, 0.9f);
        tester.believe("<chess --> competition>", 0.8f, 0.9f);
        tester.mustBelieve(cycles, "<(|,chess,sport) --> competition>", 0.72f, 0.81f);
        tester.mustBelieve(cycles, "<(&,chess,sport) --> competition>", 0.98f, 0.81f);

    }

    @Test
    void compound_decomposition_two_premises() {

        TestNAR tester = test;
        tester.believe("<robin --> (bird | swimmer)>", 1.0f, 0.9f);
        tester.believe("<robin --> swimmer>", 0.0f, 0.9f);
        tester.mustBelieve(cycles, "<robin --> bird>", 1.0f, 0.81f);

    }

    @ValueSource(floats = {0, 0.25f, 0.5f, 0.75f, 1})
    @ParameterizedTest
    void compound_decomposition_two_premises_Negative_DiffIntensional(float freq) {
        String known = "<robin --> swimmer>";
        String composed = "<robin --> (mammal - swimmer)>";
        String unknown = "<robin --> mammal>";

        TestNAR test = testDecomposeNegDiff(freq, known, composed, unknown);

        test.mustNotOutput(cycles, "<robin --> --swimmer>", BELIEF, 0, 1, 0, 1, ETERNAL);

        //test neqRCom
        test.mustNotOutput(cycles, "((mammal-swimmer)-->swimmer)", BELIEF, 0, 1, 0, 1, ETERNAL);
    }

    @ValueSource(floats = {0, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1})
    @ParameterizedTest
    void compound_decomposition_two_premises_Negative_DiffExtensional(float freq) {
        String known = "<b-->x>";
        String composed = "<(a - b) --> x>";
        String unknown = "<a --> x>";

        TestNAR test = testDecomposeNegDiff(freq, known, composed, unknown);

        test.mustNotOutput(cycles, "<--a --> x>", BELIEF, 0, 1, 0, 1, ETERNAL);
        test.mustNotOutput(cycles, "<--b --> x>", BELIEF, 0, 1, 0, 1, ETERNAL);

        //test neqRCom
        test.mustNotOutput(cycles, "(b --> (a-b))", BELIEF, 0, 1, 0, 1, ETERNAL);
    }

    private TestNAR testDecomposeNegDiff(float freq, String known, String composed, String unknown) {
        test
            .believe(known, freq, 0.9f)
            .believe(composed, 0.0f, 0.9f)
        ;

        if (freq==0 || freq == 1) {
            //0.81 conf
            test.mustBelieve(cycles, unknown, freq, 0.81f);
        } else {
            test.mustBelieve(cycles, unknown, freq, freq, 0, 0.81f); //up to 0.81 conf
        }

        if (freq > 0)
            test.mustNotOutput(cycles, known, BELIEF, 0, freq - Param.TRUTH_EPSILON, 0, 1, ETERNAL);
        if (freq < 1)
            test.mustNotOutput(cycles, known, BELIEF, freq + Param.TRUTH_EPSILON, 1, 0, 1, ETERNAL);
        return test;
    }


    @Test
    void intersectionComposition() {
        test
                .believe("(swan --> bird)")
                .believe("(swimmer--> bird)")
                .mustBelieve(cycles, "((swan&swimmer) --> bird)", 1f, 0.81f);
    }

    @Test
    void intersectionCompositionWrappedInProd() {
        test
                .believe("((swan) --> bird)")
                .believe("((swimmer)--> bird)")
                .mustBelieve(cycles, "(((swan)&(swimmer)) --> bird)", 1f, 0.81f);
    }


    @Test
    void diff_compound_decomposition_single3() {
        TestNAR tester = test;

        tester.believe("<(dinosaur - ant) --> [strong]>", 0.9f, 0.9f);
        tester.mustBelieve(cycles, "<dinosaur --> [strong]>", 0.90f, 0.73f);
        tester.mustBelieve(cycles, "<ant --> [strong]>", 0.10f, 0.73f);
    }
    @Test
    void diff_compound_decomposition_low_dynamic() {
        TestNAR tester = test;
        tester.believe("<(ant - spider) --> [strong]>", 0.1f, 0.9f);
        tester.mustBelieve(cycles, "<spider --> [strong]>", 0.90f, 0.08f);
        tester.mustBelieve(cycles, "<ant --> [strong]>", 0.10f, 0.08f);
    }

    @Test
    void diff_compound_decomposition_single() {

        TestNAR tester = test;

        tester.believe("(robin --> (bird ~ swimmer))", 0.9f, 0.9f);
        tester.mustBelieve(cycles, "<robin --> bird>", 0.90f, 0.73f);

    }


    @Test
    void sect_compound_decomposition_single2() {

        TestNAR tester = test;
        tester.believe("((dinosaur | ant) --> youth)", 0.9f, 0.9f);
        tester.mustBelieve(cycles, "(dinosaur --> youth)", 0.9f, 0.73f);

    }


//    @Test
//    void testDifference() {
//
//        TestNAR tester = test;
//        tester.believe("<swan --> bird>", 0.9f, 0.9f);
//        tester.believe("<dinosaur --> bird>", 0.7f, 0.9f);
//        tester.mustBelieve(cycles, "bird:(swan ~ dinosaur)", 0.27f, 0.81f);
//        tester.mustBelieve(cycles, "bird:(dinosaur ~ swan)", 0.07f, 0.81f);
//    }

    @Test
    void testArity1_Decomposition_IntersectExt() {


        test
                .believe("(a-->b)")
                .believe("(a-->(b&c))", 0f, 0.9f)
                .mustBelieve(cycles, "(a-->c)", 0f, 0.81f, ETERNAL);

    }

    @Test
    void testArity1_Decomposition_IntersectExt2() {
        test
                .believe("(b-->a)", 0.25f, 0.9f)
                .believe("((b&c)-->a)", 0.25f, 0.9f)
                .mustBelieve(cycles, "(c-->a)", 0.19f, 0.15f, ETERNAL);
    }

    @Test
    void testArity1_Decomposition_IntersectInt() {


        test
                .believe("(a-->b)", 0.25f, 0.9f)
                .believe("(a-->(b|c))", 0.25f, 0.9f)
                .mustBelieve(cycles, "(a-->c)", 0.19f, 0.15f, ETERNAL);
    }

    @Test
    void testDisjoint2() {


        test
                .believe("--(x-->(RealNumber&ComplexNumber))")
                .believe("(x-->RealNumber)")
                .mustBelieve(cycles, "(x-->ComplexNumber)", 0f, 0.81f);
        ;

    }

    @Test
    void testDisjoint2Learned() {


        test
                .believe("--(x-->ComplexNumber)")
                .believe("(x-->RealNumber)")
                .mustBelieve(cycles, "(x-->(RealNumber~ComplexNumber))", 1f, 0.81f);
        ;

    }

    @Test
    void testDisjoint3() {

        test
                .believe("--(x-->(&,RealNumber,ComplexNumber,Letter))")
                .believe("(x-->RealNumber)")
                .mustBelieve(cycles, "(x-->(ComplexNumber&Letter))", 0f, 0.81f)
                .mustNotOutput(cycles, "(x-->((&,RealNumber,ComplexNumber,Letter)|RealNumber))", BELIEF, ETERNAL)
        ;

    }

    @Test
    void testDisjointWithVar() {


        test
                .believe("--(#1-->(RealNumber&ComplexNumber))")
                .believe("(x-->RealNumber)")
                .mustBelieve(cycles, "(x-->ComplexNumber)", 0f, 0.81f)
        ;

    }

    @Test
    void testDifferenceQuestion() {
        test
                .believe("((x&y)-->a)")
                .mustQuestion(cycles, "((x~y)-->a)")
                .mustQuestion(cycles, "((y~x)-->a)")
        ;
    }
    @Test
    void testDifferenceQuest() {
        test
                .goal("((x&y)-->a)")
                .mustQuest(cycles, "((x~y)-->a)")
                .mustQuest(cycles, "((y~x)-->a)")
        ;
    }
    @Test
    void unionOfOppositesInt() {
        //Coincidentia oppositorum
        test
                .believe("((  x&z)-->a)")
                .believe("((--x&y)-->a)")
                .mustBelieve(cycles, "((y&z)-->a)", 1f, 0.81f)
        ;
    }

    @Test
    void unionOfOppositesExt() {
        //Coincidentia oppositorum
        test
                .believe("(a-->(  x|z))")
                .believe("(a-->(--x|y))")
                .mustBelieve(cycles, "(a-->(y|z))", 1f, 0.81f)
        ;
    }

}

