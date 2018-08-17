package nars.task.proxy;

import nars.Task;
import nars.task.TaskProxy;
import nars.term.Term;

import static nars.Op.NEG;

/** accepts a separate term as a facade to replace the apparent content term of
 * a proxied task
  */
public class SpecialTermTask extends TaskProxy {

    private final Term term;


    public SpecialTermTask(Term term, Task task) {
        super(task);
        if(term.op()==NEG)
            throw new RuntimeException("task must not be named with NEG term: " + term + " via " + task);
        this.term = term;
    }

    @Override
    protected boolean inheritCyclic() {
        return false;
    }

    @Override
    public Term term() {
        return term;
    }

}