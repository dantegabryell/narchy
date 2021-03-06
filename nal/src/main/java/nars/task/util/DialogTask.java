package nars.task.util;

import jcog.data.map.ConcurrentFastIteratingHashSet;
import jcog.data.set.ArrayHashSet;
import jcog.event.Off;
import jcog.event.Offs;
import jcog.util.ArrayUtils;
import nars.NAR;
import nars.Op;
import nars.Task;
import nars.control.NARService;
import nars.derive.BeliefSource;
import nars.derive.Deriver;
import nars.derive.Derivers;
import nars.op.SubUnify;
import nars.term.Term;
import nars.term.util.Image;

import java.util.Set;
import java.util.stream.Collectors;

import static nars.Op.BELIEF;
import static nars.Op.GOAL;

public class DialogTask extends NARService {

    final ConcurrentFastIteratingHashSet<Task> tasks = new ConcurrentFastIteratingHashSet<>(Task.EmptyArray);
    private final Deriver deriver;
    private Offs ons;
    private final Off monitor;
    private final NAR nar;
    final Set<Term> unifyWith = new ArrayHashSet();

    public boolean add(Task t) {
        return tasks.add(t);
    }

    public DialogTask(NAR n, Task... input) {
        this.nar = n;

        assert (input.length > 0);
        boolean questions = false, quests = false;
        for (Task i : input) {
            if (add(i)) {
                unifyWith.add(i.term());
            }
            questions |= i.isQuestion();
            quests |= i.isQuest();
        }


        deriver = BeliefSource.forConcepts(n, Derivers.nal(n, 1, 8),
                tasks.asList().stream().map(t -> {

                    nar.input(t);

                    return nar.concept(t.term(), true);

                }).collect(Collectors.toList()));

        byte[] listenPuncs;
        if (questions && quests)
            listenPuncs = new byte[] { BELIEF, GOAL };
        else if (questions && !quests)
            listenPuncs = new byte[] { BELIEF };
        else if (!questions && quests)
            listenPuncs = new byte[] { GOAL };
        else
            listenPuncs = ArrayUtils.EMPTY_BYTE_ARRAY;

        monitor = n.onTask(this::onTask, listenPuncs);

        n.on(this);
    }

    @Override
    protected void starting(NAR nar) {
        ons = new Offs(deriver, monitor);
    }

    @Override
    protected void stopping(NAR nar) {
        ons.off();
        ons = null;
    }

//    protected void onTask(Collection<Task> x) {
//        x.removeIf(t -> !this.onTask(t));
//        nar.input(x);
//    }

    /**
     * return false to filter this task from input
     */
    protected boolean onTask(Task x) {

        Term xx = Image.imageNormalize( x.term() );
        Op xo = xx.op();

        SubUnify uu = new SubUnify(nar.random());
        for (Term u : unifyWith) {
            if (u.op()==xo) { //prefilter
                if (u.unify(xx, uu.clear())) {
                    if (!onTask(x, u))
                        return false;
                }
            }
        }
        return true;
    }

    protected boolean onTask(Task x, Term unifiedWith) {
        return true;
    }

}
