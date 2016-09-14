package nars.concept.table;


import nars.$;
import nars.NAR;
import nars.Param;
import nars.Task;
import nars.budget.Activation;
import nars.budget.Budget;
import nars.budget.BudgetFunctions;
import nars.concept.CompoundConcept;
import nars.concept.Concept;
import nars.task.AnswerTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * implements a Task table suitable for Questions and Quests using an ArrayList.
 * uses a List and assumes that it is an ArrayList that can be
 * accessed by index.
 * <p>
 * TODO use a ring-buffer deque slightly faster than basic ArrayList modification
 */
public class ArrayQuestionTable implements QuestionTable, Comparator<Task> {

    protected int capacity;

    @NotNull
    private final List<Task> list;

    public ArrayQuestionTable(int capacity) {
        super();

        this.list = $.newArrayList(capacity);

        this.capacity = capacity;
        //setCapacity(capacity);
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public final void capacity(int newCapacity, @NotNull List<Task> displ) {

        if (this.capacity == newCapacity)
            return;

        this.capacity = newCapacity;

        @NotNull List<Task> list;

        synchronized (list = this.list) {
            int s = size();

            int toRemove = s - newCapacity;
            while (toRemove > 0) {
                displ.add(list.remove(--s)); //last element
                toRemove--;
            }
        }
    }

    @Override
    public int size() {
        return list.size();
    }


    @Override
    public final boolean isEmpty() {
        return list.isEmpty();
    }

    @Deprecated
    public final void remove(@NotNull Task belief, @NotNull List<Task> displ) {
        //this list removal is slow; indexed or iterator is better
        if (list.remove(belief)) {
            TaskTable.removeTask(belief, null, displ);
        }
    }


    @Override
    public void answer(@NotNull Task a, Concept answerConcept, @NotNull NAR nar, List<Task> displ) {

        if (a instanceof AnswerTask)
            return; //already an answer

        synchronized (list) {

            int size = list.size();
            if (size > 0) {

                //each question is only responsible for 1/N of the effect on the answer
                //TODO calculate this based on fraction of each question's priority of the total


                for (int i = 0; !a.isDeleted() && i < size; ) {
                    Task q = list.get(i);
                    if (!q.isDeleted()) {
                        if (answer(q, a, 1f/size, answerConcept, nar)) {
                            i++;
                            continue;
                        }
                    }

                    remove(i, null, displ);
                    size--;
                }
            }
        }
    }

    /**
     * returns false if the question should be removed after retuning
     */
    private static boolean answer(@NotNull Task q, @NotNull Task a, float scale, @Nullable Concept answerConcept, @NotNull NAR nar) {
//        if (Stamp.overlapping(q.evidence(), a.evidence()))
//            return;


        //float qBudget = q.pri();

        boolean aEtern = a.isEternal();
        boolean qEtern = q.isEternal();
        float factor = scale;
        float aConf = a.conf();
        if (aEtern) {
            if (qEtern)
                factor = scale * (1f - aConf);
        } else {
            if (!qEtern) {
                //TODO BeliefTable.rankTemporalByConfidence()
                factor = scale * (1f - aConf);
            }
        }


        @NotNull Budget qBudget = q.budget();
        BudgetFunctions.transferPri(qBudget, a.budget(), factor);

        if (!qEtern) {
            //if temporal question, also affect the quality so that it will get unranked by more relevant questions in the future
            qBudget.quaMult(factor);
        }

        if (q.onAnswered(a)) {


            boolean sameConcept;
            if (answerConcept!=null) {
                //check if different concepts; ex: if there is a reduction in variables, etc
                Concept qc = nar.concept(q);
                if (!qc.equals(answerConcept)) {
                    sameConcept = false;
                    qc.crossLink(q, a, scale * aConf, nar);
                } else {
                    sameConcept = true;
                }
            } else {
                sameConcept = true;
            }


            if (a.pri() > Param.BUDGET_EPSILON)
                new Activation(a, nar, 1f);

            if (Param.DEBUG_ANSWERS) {
                //if (q.term().equals(a.term()))
                if (!sameConcept) {
                    nar.logger.debug("Q&A: {}\t{}", q, a);
                } else {
                    nar.logger.debug("Q&A: {}\t{}", q, a.truth());
                }
            }

            //amount boosted will be in proportion to the lack of quality, so that a high quality q will survive longer by not being drained so quickly
            //BudgetFunctions.transferPri(q.budget(), a.budget(), (1f - q.qua()) * aConf);

        } else {
            //the qustion self-destructed
        }

        if (aEtern || a.occurrence()!=q.occurrence()) {
            //eternal or different occurrence time; keep the question
            return true;
        } else {
            //discard this fully redundant question; the answer will remain in the same concept
            return false;
        }

//        //generate a projected answer
        //WARNING this creates a huge amount of useless answers
//        if (!qEtern && !aEtern && q.occurrence()!=a.occurrence()) {
//            Concept ac = nar.concept(a);
//            if (ac != null) { //??
//                Task ap = ac.merge(q, a, q.occurrence(), nar);
//                if (ap != null && !ap.isDeleted()) {
//                    if (Stamp.overlapping(q.evidence(), a.evidence()))
//                        nar.inputLater(ap); //avoid infinite loop
//                    else
//                        nar.input(ap);
//
//                    return;
//                }
//            }
//        }

    }

    @Nullable
    @Override
    public final Task add(@NotNull Task question, @NotNull BeliefTable answers, List<Task> displ, @NotNull NAR n) {

        Task questioned;

        synchronized (list) {
            questioned = insert(question, displ);
        }

        //inserted if questioned!=null
        if (questioned != null && !answers.isEmpty()) {
            Task a = answers.top(questioned.occurrence());

            if (a != null && !a.isDeleted()) {

                answer(questioned, a, 1/size(), null,  n);
            }
        }


        return questioned;
    }

    public Task last() {
        return list.get(list.size() - 1);
    }

    @Nullable
    private Task insert(@NotNull Task t, @NotNull List<Task> displaced) {

        int siz = size();

        float tp = t.pri();


        if (siz == capacity()) {


            Collections.sort(list, this);

            if (last().qua() > tp) {
                t.delete("Insufficient Priority");
                return null;
            } else {
                // FIFO, remove oldest question (last)
                float removedPri = remove(siz - 1, "Table Pop", displaced);
                if (removedPri == removedPri) //not deleted
                    t.budget().setPriority(Math.max(t.pri(), removedPri)); //utilize at least its priority since theyre sorted by other factor
            }
        }

        //insert in sorted order by qua
        List<Task> list = this.list;

        int i = 0;
        for (; i < siz - 1; i++) {
            if (list.get(i).qua() < tp)
                break;
        }
        list.add(i, t);

        return t;
    }

    private float remove(int n, Object reason, @NotNull List<Task> displaced) {

        Task removed = list.remove(n);
        if (Param.DEBUG)
            removed.log(reason);
        displaced.add(removed);
        return removed.pri();

        /*if (Global.DEBUG_DERIVATION_STACKTRACES && Global.DEBUG_TASK_LOG)
            task.log(Premise.getStack());*/

        //eventTaskRemoved.emit(task);

        /* else: a more destructive cleanup of the discarded task? */

    }

//    /** returns the original, "revised", or null question to input
//     * @param t incoming question which may be unique, overlapping, or equivalent to an existing question in the table
//     * */
//    private Task tryMerge(@NotNull Task t, @NotNull Memory m) {
//
//
////        List<Task> list1 = this.list;
////        long[] tEvidence = t.evidence();
////        long occ = t.occurrence();
////        for (int i = 0, list1Size = list1.size(); i < list1Size; i++) {
////            Task e = list1.get(i);
////
////            //TODO merge occurrence time if within memory's duration period
////
////            if (occ != e.occurrence())
////                continue;
////
////            long[] eEvidence = e.evidence();
////
////            long[] zipped = Stamp.zip(tEvidence, eEvidence);
////
////            //construct before removing so the budgets arent deleted
////            MutableTask te = new MutableTask(
////                    t, e,
////                    m.time(), occ,
////                    zipped, BudgetMerge.max);
////
////            replace(i, te);
////
////            t.delete("Merged");
////
////
////        /*if (Global.DEBUG_DERIVATION_STACKTRACES && Global.DEBUG_TASK_LOG)
////            task.log(Premise.getStack());*/
////
////            //eventTaskRemoved.emit(task);
////
////        /* else: a more destructive cleanup of the discarded task? */
////
////            return null;
////
////        }
//        return t;
//    }


//    private void replace(int index, @NotNull Task newTask) {
//        list.set(index, newTask);
//    }

    @NotNull
    @Override
    public Iterator<Task> iterator() {
        return list.iterator();
    }

    @Override
    public void forEach(@NotNull Consumer<? super Task> action) {
        list.forEach(action);
    }

    @Override
    public int compare(@NotNull Task o1, @NotNull Task o2) {
        return Float.compare(o2.qua(), o1.qua());
    }

    //    @Override
//    public final boolean contains(Task t) {
//        //        //equality:
////        //  1. term (given because it is looking up in concept)
////        //  2. truth
////        //  3. occurrence time
////        //  4. evidential set
//
//        if (isEmpty()) return false;
//
//        Truth taskTruth = t.getTruth();
//        long taskOccurrrence = t.getOccurrenceTime();
//        long[] taskEvidence = t.getEvidence();
//
//        Task[] aa = getCachedNullTerminatedArray();
//        for (Task x : aa) {
//
//            if (x == null) return false;
//
//            if (
//
//                //different truth value
//                (x.getTruth().equals(taskTruth)) &&
//
//                //differnt occurence time
//                (x.getOccurrenceTime() == taskOccurrrence) &&
//
//                //differnt evidence
//                (Arrays.equals(x.getEvidence(), taskEvidence))
//            )
//                return true;
//        }
//
//        return false;
//
//    }

}

