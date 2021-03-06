package nars.task;

import nars.NAR;
import nars.Task;
import nars.task.util.TaskException;
import nars.term.Term;
import nars.truth.Truth;

/** task which bypasses the evaluation procedure on input.
 *  this is faster but also necessary when
 *  something is specified in the task that evaluation
 *  otherwise would un-do.
 */
public class UnevaluatedTask extends NALTask {

    public UnevaluatedTask(Term t, byte punct, Truth truth, long creation, long start, long end, long[] stamp) {
        super(t, punct, truth, creation, start, end,
                stamp /* TODO use an implementation which doenst need an array for this */);
    }

    public UnevaluatedTask(Term c, Task parent, Truth t) throws TaskException {
        super(c, parent.punc(), t, parent.creation(), parent.start(), parent.end(), parent.stamp());
    }

//    @Override
//    public ITask next(NAR n) {
//
//        //HACK, for ensuring the operator invocation etc
//        FasterList<ITask> q = new FasterList(1);
//        preProcess(n, term(), q);
//        return postProcess(q, false);
//    }
    @Override
    public ITask next(NAR n) {
        return perceive(this, n);
    }
}
