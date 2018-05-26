package nars.task.util;

import com.google.common.collect.Lists;
import jcog.data.map.ConcurrentFastIteratingHashSet;
import jcog.event.On;
import jcog.event.Ons;
import nars.NAR;
import nars.Task;
import nars.derive.Deriver;
import nars.derive.deriver.SimpleDeriver;

import java.util.Collection;

public class DialogTask {

    final ConcurrentFastIteratingHashSet<Task> tasks = new ConcurrentFastIteratingHashSet<Task>(new Task[0]);
    private final Deriver deriver;
    private final Ons ons;
    private final On monitor;
    private final NAR nar;

    public void add(Task t) {
        tasks.add(t);
    }

    public DialogTask(NAR n,Task... input) {
        this.nar = n;
        for (Task i : input) {
            add(i);
        }

        ons= new Ons(

            deriver = SimpleDeriver.forConcepts(n, Lists.transform(tasks.asList(), t->{

                nar.input(t);

                if (t!=null)
                    return t.concept(nar, true);
                else
                    return null;

            }), this::onTask),

            monitor = n.onTask(this::onTask)
        );
    }

    public void off() {
        ons.off();
    }

    protected void onTask(Collection<Task> x) {
        x.removeIf(t -> !this.onTask(t));
        nar.input(x);
    }

    /** return false to filter this task from input */
    protected boolean onTask(Task x) {
        return true;
    }

}
