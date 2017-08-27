package nars.task;

import jcog.bag.impl.ArrayBag;
import jcog.bag.impl.PLinkArrayBag;
import jcog.event.On;
import jcog.pri.PLink;
import jcog.pri.PriReference;
import jcog.pri.op.PriMerge;
import nars.NAR;
import nars.Param;
import nars.Task;
import nars.term.Term;
import nars.term.subst.Unify;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static nars.Op.*;

/**
 * Question task which accepts a callback to be invoked on answers
 * The question actively listens for unifiable task events, until deleted
 * TODO abstract the matcher into:
 *      --exact (.equals)
 *      --unify
 *      --custom Predicate<Term>
 * TODO separate the action into:
 *      --bag (with filtering)
 *      --custom BiConsumer<Q Task, A Task>
 *      --statistical truth aggregator for easy to understand summary
 */
public class ActiveQuestionTask extends NALTask implements Consumer<Task> {

    private @NotNull final BiConsumer<? super ActiveQuestionTask /* Q */, Task /* A */ > eachAnswer;

    final ArrayBag<Task, PriReference<Task>> answers;
    private On onTask;

    private transient int ttl;
    private transient Random random;

    /** wrap an existing question task */
    public ActiveQuestionTask(Task q, int history, NAR nar, BiConsumer<? super ActiveQuestionTask,Task> eachAnswer ) {
        this(q.term(), q.punc(), q.mid() /*, q.end()*/, history, nar, eachAnswer );
    }

    public ActiveQuestionTask(@NotNull Term term, byte punc, long occ, int history, NAR nar, @NotNull Consumer<Task> eachAnswer) {
        this(term, punc, occ, history, nar, (q, a) -> eachAnswer.accept(a));
    }

    public ActiveQuestionTask(@NotNull Term term, byte punc, long occ, int history, NAR nar, @NotNull BiConsumer<? super ActiveQuestionTask, Task> eachAnswer) {
        super(term, punc, null, nar.time(), occ, occ, new long[] { nar.time.nextStamp() } );

        budget(nar);

        this.answers = newBag(history);
        this.eachAnswer = eachAnswer;
    }

    @Override
    public @Nullable Iterable<? extends ITask> run(@NotNull NAR nar) {
        super.run(nar);
        this.random = nar.random();
        this.ttl = nar.matchTTL.intValue();
        this.onTask = nar.onTask(this);
        return null;
    }

    @Override
    public void accept(Task t) {
           byte tp = t.punc();
            if ((punc == QUESTION && tp == BELIEF || punc == QUEST && tp == GOAL)) {
                MySubUnify u = new MySubUnify(random, ttl);
                u.unifyAll(term(), t.term());
                if (u.match) {
                    onAnswer(t);
                }
            }
    }

    private static class MySubUnify extends Unify {

        boolean match;

        public MySubUnify(Random r, int ttl) {
            super(null, r, Param.UnificationStackMax, ttl);
        }

        @Override
        public void onMatch(Term[][] match) {
            //accept(x, xy);
            this.match = true;
            setTTL(0); //accept only one
        }

    }

    @Override
    public boolean delete() {
        if (this.onTask!=null) {
            this.onTask.off();
            this.onTask = null;
        }
        return super.delete();
    }

    ArrayBag<Task, PriReference<Task>> newBag(int history) {
        return new PLinkArrayBag<>(history, PriMerge.max, new ConcurrentHashMap<>(history)) {
            @Override
            public void onAdded(@NotNull PriReference<Task> t) {
                eachAnswer.accept(ActiveQuestionTask.this, t.get());
            }
        };
    }


    @Override
    public @Nullable Task onAnswered(@NotNull Task answer, @NotNull NAR nar) {
        Task x = super.onAnswered(answer, nar);
        onAnswer(answer);
        return x;
    }

    public Task onAnswer(Task answer) {
        //answer = super.onAnswered(answer, nar);
        answers.putAsync(new PLink<>(answer, answer.priElseZero()));
        return answer;
    }

//    @Override
//    public Task onAnswered(Task answer, NAR nar) {
//        //answer = super.onAnswered(answer, nar);
//        answers.putAsync(new PLinkUntilDeleted<>(answer, answer.priElseZero()));
//        return answer;
//    }
}
