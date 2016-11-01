package nars.nal.nal1;

import nars.$;
import nars.NAR;
import nars.Narsese;
import nars.nar.Default;
import nars.nar.Terminal;
import nars.nar.util.Answerer;
import nars.nar.util.OperationAnswerer;
import nars.term.Compound;
import nars.term.Term;
import nars.test.DeductiveMeshTest;
import nars.test.TestNAR;
import nars.util.TaskStatistics;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static nars.time.Tense.ETERNAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by me on 5/24/16.
 */
public class QuestionTest {

    final int withinCycles = 16;

    @Test
    public void whQuestionUnifyQueryVar() throws Narsese.NarseseException {
        testQuestionAnswer(withinCycles, "<bird --> swimmer>", "<?x --> swimmer>", "<bird --> swimmer>");
    }

    @Test
    public void yesNoQuestion() throws Narsese.NarseseException {
        testQuestionAnswer(withinCycles, "<bird --> swimmer>", "<bird --> swimmer>", "<bird --> swimmer>");
    }

    /** question to answer matching */
    public void testQuestionAnswer(int cycles, @NotNull String belief, @NotNull String question, @NotNull String expectedSolution) {
        AtomicBoolean ok = new AtomicBoolean(false);


        Term expectedSolutionTerm = $.$(expectedSolution);

        NAR nar = new Default();
        nar.nal(1);
        //nar.log();

        nar
                .believe(belief, 1.0f, 0.9f)
                .next()
                .ask(question, ETERNAL, b -> {
                    if (b.punc() == '.' && b.term().equals(expectedSolutionTerm))
                        ok.set(true);
                    return false;
                });

        nar.run(cycles);

        assertTrue(ok.get());

//           .onAnswer(question, a -> { //.en("What is a type of swimmer?")
//
//                System.out.println(nar.time() + ": " + question + " " + a);
//                //test for a few task conditions, everything except for evidence
//                if (a.punc() == expectedTask.punc())
//                    if (a.term().equals(expectedTask.term())) {
//                        if (Objects.equals(a.truth(), expectedTask.truth()))
//                            solved.set(true);
//                }
//
//            }).run(cycles);


    }


    @Test public void testQuestionHandler() {
        NAR nar = new Terminal();

        final int[] s = {0};
        new Answerer( $.$("add(%1, %2, #x)"), nar) {

            @Override
            protected void onMatch(Map<Term, Term> xy) {
                s[0] = xy.size();
            }
        };

        nar.ask($.$("add(1, 2, #x)"));

        assertEquals(3, s[0]);

    }

    @Test public void testOperationHandler() {
        NAR nar = new Terminal();

        final int[] s = {0};
        new OperationAnswerer( $.$("add(%1, %2, #x)"), nar) {

            @Override
            protected void onMatch(Term[] args) {
                System.out.println(Arrays.toString(args));
            }
        };

        nar.ask($.$("add(1, 2, #x)"));


    }

    @Test public void questionDrivesInference() {
        long seed  = 1;

        final int[] dims = {3, 2};
        final int timelimit = 3000;

        TaskStatistics withTasks = new TaskStatistics();
        TaskStatistics withoutTasks = new TaskStatistics();
        DoubleSummaryStatistics with = new DoubleSummaryStatistics();
        DoubleSummaryStatistics withOut = new DoubleSummaryStatistics();
        for (int i = 0; i < 10; i++) {
            TestNAR withQuestion = new TestNAR(new Default());
            withQuestion.nar.random.setSeed(seed++);
            withQuestion.nar.nal(4);

            new DeductiveMeshTest(withQuestion, dims, timelimit);
            withQuestion.run(true);



            TestNAR withoutQuestion = new TestNAR(new Default());
            withoutQuestion.nar.random.setSeed(seed++);
            withoutQuestion.nar.nal(4);

            new DeductiveMeshTest(withoutQuestion, dims, timelimit) {
                @Override
                public void ask(@NotNull TestNAR n, Compound term) {
                    //disabled
                }
            };
            withoutQuestion.run(true);

            long withQuestionTime = withQuestion.time();
            with.accept(withQuestionTime);
            long withoutQuestionTime = withoutQuestion.time();
            withOut.accept(withoutQuestionTime);

            System.out.println("with: " + withQuestionTime + " vs without: " + withoutQuestionTime);

            withTasks.add(withQuestion.nar);
            withoutTasks.add(withoutQuestion.nar);
        }

        System.out.println("with: " + with + "\n");
        withTasks.print();
        System.out.println("withOut: " + withOut + "\n" + withoutTasks);
        withoutTasks.print();
    }


//    @Test public void testSaneBudgeting() {
//        Param.DEBUG = true;
//        String c = "((parent($X,$Y) && parent($Y,$Z)) ==> grandparent($X,$Z))";
//        new Default(1000, 8, 1, 3)
//            .logSummaryGT(System.out, 0.1f)
//            .eachFrame(nn->{
//                Concept cc = nn.concept(c);
//                if (cc!=null) {
//                    cc.print(System.out, false, false, true, false);
//                }
//            })
//            .input(c + ".", "")
//            .run(100);
//
//    }

//    @Test public void testPrologLike1() {
//        Param.DEBUG = true;
//        new Default(1000, 8, 1, 3)
//            .logSummaryGT(System.out, 0.1f)
//            .input(
//                "((parent($X,$Y) && parent($Y,$Z)) ==> grandparent($X,$Z)).",
//                "parent(c, p).",
//                "parent(p, g).",
//                "grandparent(p, #g)?"
//            )
//            .run(800);
//
//    }
}
