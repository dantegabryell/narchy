package nars.nal.nal7;

import nars.NAR;
import nars.nal.AbstractNALTest;
import nars.nal.Tense;
import nars.util.signal.TestNAR;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.function.Supplier;

import static nars.nal.Tense.ETERNAL;
import static nars.nal.Tense.Present;

/**
 * Created by me on 1/9/16.
 */
@RunWith(Parameterized.class)
public class NAL7Test extends AbstractNALTest {

    final int cycles = 160;

    public NAL7Test(Supplier<NAR> b) {
        super(b);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable configurations() {
        return AbstractNALTest.nars(7, true, true);
    }

    @Test
    public void induction_on_events() {
        //    P, S, after(Task,Belief), measure_time(I), notImplicationOrEquivalence(P), notImplicationOrEquivalence(S) |-
        //              ((&/,S,I) =/> P), (Belief:Induction, Eternalize:Immediate),
        //              (P =\> (&/,S,I)), (Belief:Abduction),
        //              ((&/,S,I) </> P), (Belief:Comparison)
        //    P, S, after(Task,Belief), notConjunction(P), notConjunction(S),  measure_time(I), notImplicationOrEquivalence(P), notImplicationOrEquivalence(S) |-
        //              (&/,S,I,P), (Belief:Intersection)

        long time = 200;
        test()
                //.log()
                .input("x:before. :|:")
                .inputAt(10, "x:after. :|:")
                .mustBelieve(time, "(x:before ==>+10 x:after)", 1.00f, 0.45f /*abductionConf*/,    0)
                .mustBelieve(time, "(x:after ==>-10 x:before)", 1.00f, 0.45f /*inductionConf*/,    0)
                .mustBelieve(time, "(x:after <=>-10 x:before)", 1.00f, 0.45f /*comparisonConf*/,   0)
                .mustBelieve(time, "(x:after &&-10 x:before)",  1.00f, 0.81f /*intersectionConf*/, 0)
        ;
    }


    @Test
    public void temporal_explification() {
        TestNAR tester = test();
        tester.believe("(<($x, room) --> enter> ==>-5 <($x, door) --> open>)", 0.9f, 0.9f);
        tester.believe("(<($y, door) --> open> ==>-5 <($y, key) --> hold>)", 0.8f, 0.9f);

        tester.mustBelieve(cycles, "(<($1,key) --> hold> ==>+10 <($1,room) --> enter>)", 1.00f, 0.37f);

    }


    @Test
    public void temporal_analogy() {
        test().log()

        .believe("( open:($x, door) ==>+5 enter:($x, room) )",  0.95f, 0.9f)
        .believe("( enter:($x, room) <=>+0 leave:($x, corridor_100) )", 1.0f, 0.9f)
        .mustBelieve(cycles, "( open:($1, door) ==>+5 leave:($1, corridor_100) )", 0.95f, 0.81f);

    }


    @Test
    public void updating_and_revision() {
        testTemporalRevision(10, 0.50f, 0.7f, "<(John,key) --> hold>");
    }

    @Test
    public void updating_and_revision2() {
        testTemporalRevision(1, 0.5f, 0.7f, "<(John,key) --> hold>");
    }

    void testTemporalRevision(int delay, float freq, float conf, String belief) {
        TestNAR tester = test();
        //tester.nar.log();
        tester.input(belief + ". :|: %1.00;0.65%");
        tester.inputAt(delay, belief + ". :|: %0.5;0.70%");
        tester.inputAt(delay + 1, belief + "? :|:");
        tester.mustBelieve(delay + 50, belief, freq, conf, delay);
    }

    @Test
    public void testSumNeg() {
        //(P ==> M), (M ==> S), neq(S,P), dt(sumNeg) |- (S ==> P), (Belief:Exemplification, Derive:AllowBackward)
        test()
                .believe("(x ==>+2 y)")
                .believe("(y ==>+3 z)")
                .mustBelieve(2, "(z ==>-5 x)", 1.00f, 0.45f);

    }

    @Test
    public void testSum() {
        test()
                .log()
                .believe("(x ==>+2 y)")
                .believe("(y ==>+3 z)")
                .mustBelieve(8, "(x ==>+5 z)", 1.00f, 0.81f);
    }

    @Test
    public void testBminT() {
        //(P ==> M), (S ==> M), neq(S,P), dt(bmint) |- (S ==> P), (Belief:Induction, Derive:AllowBackward)
        test()
                .believe("(x ==>+2 y)")
                .believe("(z ==>+3 y)")
                .mustBelieve(5, "(z ==>+1 x)", 1.00f, 0.45f);
    }

    @Test
    public void testTminB() {
        //(M ==> P), (M ==> S), neq(S,P), dt(tminb) |- (S ==> P), (Belief:Abduction, Derive:AllowBackward)

        test()
                .believe("(y ==>+3 x)")
                .believe("(y ==>+2 z)")
                .mustBelieve(5, "(z ==>+1 x)", 1.00f, 0.45f);
    }

    @Test
    public void testImplQuery() {
        test()
                .believe("(y ==>+3 x)")
                .input("(y ==>+3 ?x)?")
                .mustAnswer(15, "(y ==>+3 x)", 1.00f, 0.9f, Tense.Eternal);
    }

    @Test
    public void testImplQueryTense() {
        test()
                .input("(y ==>+3 x). :|:")
                .input("(y ==>+3 ?x)? :|:")
                .mustAnswer(15, "(y ==>+3 x)", 1.00f, 0.90f, Tense.Present);
    }

    @Test
    public void testImplQueryTenseFuture() {
        test()
                //.log()
                .input("(y ==>+3 x). :\\:")
                .inputAt(25, "(y ==>+3 ?z)? :/:")
                //.mustAnswer(50, "(y ==>+3 x)", 1.00f, 0.74f, 15);
                .mustAnswer(50, "(y ==>+3 x)", 1.00f, 0.9f, 25 + 5);
    }

//    @Test public void testImplQuery2() {
//        TestNAR t = test();
//        t.nar.log();
//        t.believe("(y ==>+3 x)")
//        .input("(y ==> x)?")
//        .mustAnswer(15, "(y ==>+3 x)", 1.00f, 0.9f, Tense.Eternal);
//    }

    @Test
    public void intervalPreserve_and_shift_occurence_corner_case() {
        TestNAR tester = test();
        tester.input("S:s.");
        tester.inputAt(3, "(S:s &&+3 Z:z). :|:");
        tester.mustBelieve(cycles, "S:s.", 1.00f, 0.81f /* 0.42? */, 3);
        tester.mustBelieve(cycles, "Z:z.", 1.00f, 0.81f /* 0.42? */, 6);
        tester.mustNotOutput(cycles, "Z:z", '.', 0, 1, 0.47f, 1, ETERNAL); //nothing higher conf than the eternalized belief
    }

    @Test
    public void intervalPreserve_and_shift_occurence() {
        TestNAR tester = test();
        tester.input("S:s.");
        tester.inputAt(10, "(S:s &&+50 (Y:y &&+3 Z:z)). :|:");
        tester.mustBelieve(cycles, "S:s.", 1.00f, 0.81f /* 0.42? */, 10);
        tester.mustBelieve(cycles, "(Y:y &&+3 Z:z).", 1.00f, 0.81f /* 0.42? */, 60);
    }


    @Test
    public void temporal_deduction() {
        TestNAR tester = test();
        tester.believe("((($x, room) --> enter) ==>-3 (($x, door) --> open))", 0.9f, 0.9f);
        tester.believe("((($y, door) --> open) ==>-4 (($y, key) --> hold))", 0.8f, 0.9f);

        tester.mustBelieve(cycles, "((($1,room) --> enter) ==>-7 (($1,key) --> hold))", 0.72f, 0.58f);
    }

    @Test
    public void temporal_induction_comparison() {
        TestNAR tester = test();

        // hold ==>+4 open ==>+5 enter
        tester.believe("((( $x, door) --> open) ==>+5 (( $x, room) --> enter))", 0.9f, 0.9f);
        tester.believe("((( $y, door) --> open) ==>-4 (( $y, key) --> hold))", 0.8f, 0.9f);


        tester.mustBelieve(cycles, "((($1,key) --> hold) ==>+9 (($1,room) --> enter))", 0.9f, 0.39f);
        tester.mustBelieve(cycles, "((($1,room) --> enter) ==>-9 (($1,key) --> hold))", 0.8f, 0.42f);
        tester.mustBelieve(cycles, "((($1,key) --> hold) <=>+9 (($1,room) --> enter))", 0.73f, 0.44f);

    }

    @Test
    public void inference_on_tense() {
        TestNAR tester = test();

        tester.log();

        tester.input("((($x, key) --> hold) ==>+7 (($x, room) --> enter)).");
        tester.input("<(John, key) --> hold>. :|:");

        tester.mustBelieve(8, "<(John,room) --> enter>", 1.00f, 0.81f, 7);
    }

    @Test
    public void inference_on_tense_reverse() {
        TestNAR tester = test();

        tester.input("((($x, key) --> hold) ==>+7 (($x, room) --> enter)).");
        tester.input("<(John, room) --> enter>. :|:");

        tester.mustBelieve(cycles, "<(John,key) --> hold>", 1.00f, 0.45f, -7);
    }

    @Test
    public void inference_on_tense_reverse_novar() {
        TestNAR tester = test();

        tester.input("(((John, key) --> hold) ==>+7 ((John, room) --> enter)).");
        tester.input("<(John, room) --> enter>. :|:");

        tester.mustBelieve(cycles, "<(John,key) --> hold>", 1.00f, 0.45f, -7);
    }

    @Test
    public void inference_on_tense_3() {
        TestNAR tester = test();
        //tester.log();
        tester.believe("(((John,key) --> hold) ==>+3 ((John,room) --> enter))", 1.0f, 0.9f);
        tester.input("<(John,key) --> hold>. :|:");

        tester.mustBelieve(cycles, "<(John,room) --> enter>",
                1.00f, 0.81f, 3);

    }

    @Test
    public void inference_on_tense_4() {
        TestNAR tester = test();
        tester.log();
        tester.believe("(((John,key) --> hold) ==>+3 ((John,room) --> enter))");
        tester.input("<(John,room) --> enter>. :|:");

        tester.mustBelieve(cycles, "<(John,key) --> hold>",
                1.00f, 0.45f, -3);
    }

    @Test
    public void induction_on_events_0() {
        test().log()
            .input("open:(John,door). :|:")
            .inputAt(4, "enter:(John,room). :|:")
            .mustBelieve(cycles, "( enter:(John, room) ==>-4 open:(John, door) )",
                    1.00f, 0.45f, 0);
    }


    @Test
    public void induction_on_events2() {
        TestNAR tester = test();

        tester.input("<(John,door) --> open>. :|:");
        tester.inputAt(4, "<(John,room) --> enter>. :|:");

        tester.mustBelieve(cycles, "(((John, door) --> open) ==>+4 ((John, room) --> enter))",
                1.00f, 0.45f, 0);

    }

    @Test
    public void induction_on_events3() {
        TestNAR tester = test();

        tester.input("open:(John,door). :|:");
        tester.inputAt(4, "enter:(John,room). :|:");

        tester.mustBelieve(cycles, "(open:(John, door) <=>+4 enter:(John, room))",
                1.00f, 0.45f,
                0);

    }

    @Test
    public void induction_on_events3_simple() {
        TestNAR tester = test();
        tester.input("<door --> open>. :|:");
        tester.inputAt(4, "<room --> enter>. :|:");

        tester.mustBelieve(cycles, "(<door --> open> <=>+4 <room --> enter>)",
                1.00f, 0.45f,
                0);
    }

    @Test
    public void induction_on_events3_simple_reversed() {
        //TESTS COMMUTIVITY
        test()
                //.log()
                .input("<room --> enter>. :|:")
                .inputAt(4, "<door --> open>. :|:")
                .mustBelieve(cycles, "(open:door <=>-4 enter:room)",
                    1.00f, 0.45f,
                    0)
                .mustBelieve(cycles, "(enter:room <=>+4 open:door)", //same as other condition
                    1.00f, 0.45f,
                    0)
        ;
    }

//    @Test public void testInductGoalBelief() {
//        test()
//                .log()
//                .input("<room --> enter>! :|:")
//                .inputAt(4, "<door --> open>. :|:")
//                .mustNotOutput(16, "(open:door <=>-4 enter:room)", '!', 4)
//                .mustNotOutput(16, "(open:door <=>-4 enter:room)", '.', 4);
//    }
//    @Test public void testInductBeliefGoal() {
//        test()
//                .log()
//                .input("<room --> enter>. :|:")
//                .inputAt(4, "<door --> open>! :|:")
//                .mustDesire(cycles, "((door-->open) &&-4 (room-->enter))", 1f, 0.81f, 0)
//                //.mustNotOutput(16, "(open:door <=>-4 enter:room)", '!', 4)
//                //.mustNotOutput(16, "(open:door <=>-4 enter:room)", '.', 4)
//        ;
//    }

    @Test
    public void induction_on_events_with_variable_introduction() {
        TestNAR tester = test();


        tester.input("<John --> (/,open,_,door)>. :|:");
        tester.inputAt(2, "<John --> (/,enter,_,room)>. :|:");

        //note: this result is reversed (pred equiv direction AND the occurrence time) from the original NAL7 test but its semantics are equivalent
        tester.mustBelieve(cycles,
                "(<$1 --> (/, enter, _, room)> <=>-2 <$1 --> (/, open, _, door)>)",
                1.00f, 0.81f, //0.45f,
                0
        );

    }


    @Test
    public void induction_on_events_with_variable_introduction2() {
        TestNAR tester = test();


        tester.input("<John --> (/,open,_,door)>. :|:");
        tester.inputAt(2, "<John --> (/,enter,_,room)>. :|:");


        tester.mustBelieve(cycles,
                "(<$1 --> (/, open, _, door)> ==>+2 <$1 --> (/, enter, _, room)>)",
                1.00f,
                0.81f, //0.45f,
                0
        );

        //REVERSE:
//        tester.mustBelieve(cycles*4,
//                "(<$1 --> (/, open, _, door)> ==>+2 <$1 --> (/, enter, _, room)>)",
//                1.00f, 0.45f,
//                0
//        );

    }

    @Test
    public void induction_on_events_composition_pre() {
        TestNAR tester = test();

        //tester.nar.log();

        tester.input("(open:(John,door) ==>+5 enter:(John,room)). :|:");


        tester.mustBelieve(cycles, "open:(John,door)",
                1.00f, 0.81f,
                0);

        tester.mustBelieve(cycles, "enter:(John,room)",
                1.00f, 0.81f,
                5);
    }

    @Test
    public void induction_on_events_composition1() {
        compositionTest(1, 5);
    }

    @Test
    public void induction_on_events_composition2() {
        compositionTest(1, 7);
    }

    @Test
    public void induction_on_events_composition3() {
        compositionTest(4, 3);
    }

    @Test
    public void induction_on_events_composition_post() {
        TestNAR tester = test();

        int t = 1;
        int dt = 7;
        String component = "(open:(John,door) &&+0 hold:(John,key))";
        tester.inputAt(t, component + ". :|:");
        tester.inputAt(t + dt, "enter:(John,room). :|:");

        tester.mustBelieve((t + dt) + dt + 1 /** approx */,
                "(" + component + " ==>+" + dt + " enter:(John,room))",
                1.00f, 0.45f,
                t);


    }

    private void compositionTest(int t, int dt) {
        TestNAR tester = test();
        tester.inputAt(t, "hold:(John,key). :|:");
        tester.inputAt(t, "(open:(John,door) ==>+" + dt + " enter:(John,room)). :|:");

        //tester.log();

        String component = "(open:(John,door) &&+0 hold:(John,key))";

        //Given:
        tester.mustBelieve(cycles * 2, "hold:(John,key)",
                1.00f, 0.9f,
                t);

        //Result of 2nd Input's Decomposition
        tester.mustBelieve(cycles * 2, "open:(John,door)",
                1.00f, 0.81f,
                t);
        tester.mustBelieve(cycles * 2, "enter:(John,room)",
                1.00f, 0.81f,
                t + dt);

        tester.mustBelieve(cycles * 2, component,
                1.00f, 0.73f,
                t);

        //this is probably prevented by stamp overlap:
//        tester.mustBelieve(cycles*12, "(" + component + " ==>+" + dt + " enter:(John,room))",
//                1.00f, 0.45f,
//                t+dt);
    }

    @Test
    public void variable_introduction_on_events() {
        TestNAR tester = test();

        tester.input("<{t003} --> (/,at,SELF,_)>. :|:");
        tester.inputAt(10, "<{t003} --> (/,on,{t002},_)>. :|:");

        tester.mustBelieve(cycles, "(<#1 --> (/,at,SELF,_)> &&+10 <#1 --> (/,on,{t002},_)>)",
                1.0f, 0.81f,
                0);

    }

    //    //TODO: investigate
    @Test
    public void variable_elimination_on_temporal_statements() {
        TestNAR tester = test();


        tester.input("(on:({t002},#1) &&+0 at:(SELF,#1)). :|:");
        tester.inputAt(10, "((on:($1,#2) &&+0 at:(SELF,#2)) ==>+0 reachable:(SELF,$1)).");

        tester.mustBelieve(cycles, "reachable:(SELF,{t002})",
                1.0f, 0.81f, 0);

    }

    //
    @Test
    public void temporalOrder() {
        test()
                .log()
                .input("(<m --> M> ==>+5 <p --> P>).")
                .inputAt(10, "(<s --> S> <=>+0 <m --> M>). %0.9;0.9%")
                .mustBelieve(cycles, "(<s --> S> ==>+5 <p --> P>)", 0.90f, 0.73f)
                .mustBelieve(cycles, "<m-->M>", 1f, 0.81f)
                .mustBelieve(cycles, "<p-->P>", 1f, 0.81f);

        //(M =/> P), (S <|> M), not_equal(S,P) |- (S =/> P), (Truth:Analogy, Derive:AllowBackward)
    }
//


}
