package nars.nal.nal8;

import nars.Narsese;
import nars.task.Task;
import nars.term.Term;
import nars.term.transform.subst.Subst;
import nars.util.data.random.XorShift128PlusRandom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.function.Function;


public abstract class PatternOperation implements Function<Task, List<Task>> {

    final Random rng = new XorShift128PlusRandom(1);
    @Nullable
    public final Term pattern;

    protected PatternOperation(String pattern) {
        this.pattern = Narsese.the().term(pattern);
    }

    @NotNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + '[' + pattern.toString() + ']';
    }

    @Nullable
    @Override
    public List<Task> apply(Task operationTask) {
//
//        if (operationTask.isGoal()) {
//            FindSubst s = new FindSubst(Op.VAR_PATTERN, rng);
//            if (s.matchAll(pattern, operationTask.get(), Global.UNIFICATION_POWER)) {
//                return run(operationTask, s);
//            }
//        }

        return null;
    }

    @Nullable
    public abstract List<Task> run(Task operationTask, Subst map1);
}
