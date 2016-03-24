/*
 * Here comes the text of your license
 * Each line should be prefixed with  *
 */
package nars.concept;

import nars.Global;
import nars.NAR;
import nars.Premise;
import nars.Symbols;
import nars.bag.BLink;
import nars.budget.Budget;
import nars.nal.meta.PremiseEval;
import nars.nal.op.Derive;
import nars.task.DerivedTask;
import nars.task.Task;
import nars.term.Compound;
import nars.term.Termed;
import nars.truth.Stamp;
import nars.truth.Truth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static nars.nal.Tense.ETERNAL;
import static nars.nal.Tense.DTERNAL;
import static nars.truth.TruthFunctions.eternalize;

/**
 * Firing a concept (reasoning event). Derives new Tasks via reasoning rules
 * <p>
 * Concept
 * Task
 * TermLinks
 */
abstract public class ConceptProcess implements Premise {

    public final NAR nar;
    public final BLink<? extends Task> taskLink;
    public final BLink<? extends Concept> conceptLink;
    public final BLink<? extends Termed> termLink;
    @Nullable private final Task belief;

    /** lazily cached value :=
     *      -1: unknown
     *      0: not cyclic
     *      1: cyclic
     */
    private transient int cyclic = -1;

    public ConceptProcess(NAR nar, BLink<? extends Concept> conceptLink,
                          BLink<? extends Task> taskLink,
                          BLink<? extends Termed> termLink, @Nullable Task belief) {
        this.nar = nar;

        this.taskLink = taskLink;
        this.conceptLink = conceptLink;
        this.termLink = termLink;

        this.belief = belief;
    }

    @Override public final boolean cyclic() {
        int cc = this.cyclic;
        if (cc != -1) {
            return cc > 0;
        } else {
            boolean isCyclic = Stamp.overlapping(task(), belief);
            this.cyclic = (isCyclic ? 1 : 0);
            return isCyclic;
        }
    }


    @Override
    public final Task task() {
        return taskLink.get();
    }

//    /**
//     * @return the current termLink aka BeliefLink
//     */
//    @Override
//    public final BagBudget<Termed> getTermLink() {
//        return termLink;
//    }

    @NotNull
    @Override
    public Concept concept() {
        return conceptLink.get();
    }


//    protected void beforeFinish(final long now) {
//
//        Memory m = nar.memory();
//        m.logic.TASKLINK_FIRE.hit();
//        m.emotion.busy(getTask(), this);
//
//    }

//    @Override
//    final protected Collection<Task> afterDerive(Collection<Task> c) {
//
//        final long now = nar.time();
//
//        beforeFinish(now);
//
//        return c;
//    }

    @NotNull
    @Override
    public final Termed beliefTerm() {
        Task x = belief();
        return x == null ? termLink.get() :
                x.term();
    }

    @Nullable
    @Override
    public final Task belief() {
        return belief;
    }


    @NotNull
    @Override
    public String toString() {
        return new StringBuilder().append(
                getClass().getSimpleName())
                .append('[').append(conceptLink).append(',')
                .append(taskLink).append(',')
                .append(termLink).append(',')
                .append(belief())
                .append(']')
                .toString();
    }

    @Override
    public final NAR nar() {
        return nar;
    }

    public int getMaxMatches() {
        final float min = Global.matchTermutationsMin, max = Global.matchTermutationsMax;
        return (int) Math.ceil(task().pri() * (max - min) + min);
    }




    /** part 2 */
    public void derive(@NotNull Termed<Compound> c, @Nullable Truth truth, Budget budget, long now, long occ, @NotNull PremiseEval p, @NotNull Derive d) {

        char punct = p.punct.get();
//        Character _punct = p.punct.get();
//        if (_punct == null) {
//            throw new RuntimeException("punct is null");
//        }
//        char punct = _punct;

        Task belief = belief();


        boolean derivedTemporal = occ != ETERNAL;

        boolean single;
        if (belief != null) {
            switch (punct) {
                case Symbols.BELIEF:
                    single = d.beliefSingle;
                    break;
                case Symbols.GOAL:
                    single = d.desireSingle;
                    break;
                default:
                    single = false;
                    break;
            }
        } else {
            single = true;
        }


        Task derived = newDerivedTask(c, punct)
                .truth(truth)
                .time(now, occ)
                .parent(task(), single ? null : belief /* null if single */)
                .budget(budget) // copied in, not shared
                //.anticipate(derivedTemporal && d.anticipate)
                .log( Global.DEBUG ? d.rule : "Derived");

        if (!complete(derived))
            return;

        //ETERNALIZE:

        if (derivedTemporal && (truth != null) && d.eternalize) {

            complete(newDerivedTask(c, punct)
                    .truth(
                        truth.freq(),
                        eternalize(truth.conf())
                    )

                    .time(now, ETERNAL)

                    .parent(derived)  //this is lighter weight and potentially easier on GC than: parent(task, belief)

                    .budgetCompoundForward(budget, this)

                    .log("Immediaternalized") //Immediate Eternalization

            );

        }

    }

    @NotNull
    public DerivedTask newDerivedTask(@NotNull Termed<Compound> c, char punct) {
        return new DerivedTask(c, punct, this);
    }

    private final boolean complete(Task derived) {

//        //pre-normalize to avoid discovering invalidity after having consumed space while in the input queue
//        derived = derived.normalize(nar());
//        if (derived != null) {
//
//            //if (Global.DEBUG) {
//            if (task().equals(derived))
//                return false;
//                //throw new RuntimeException("derivation same as task");
//            if (belief() != null && belief().equals(derived))
//                return false;
//                //throw new RuntimeException("derivation same as belief");
//            //}

            accept(derived);
        return true;
//            return true;
//        }
//        return false;
    }


    /** when a derivation is accepted, this is called  */
    abstract protected void accept(Task derivation);

    /** after a derivation has completed, commit is called allowing it to process anything collected */
    abstract protected void commit();

    public final void run(@NotNull PremiseEval matcher) {
        matcher.start(this);
        commit();
    }

    public boolean hasTemporality() {
        if (task().term().dt()!= DTERNAL) return true;
        @Nullable Task b = belief();
        if (b == null) return false;
        return b.term().dt()!= DTERNAL;
    }

}
