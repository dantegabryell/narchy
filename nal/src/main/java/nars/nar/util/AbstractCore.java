package nars.nar.util;

import nars.Global;
import nars.Memory;
import nars.NAR;
import nars.bag.Bag;
import nars.bag.impl.AutoBag;
import nars.budget.Budgeted;
import nars.concept.Concept;
import nars.data.Range;
import nars.link.BLink;
import nars.nal.ConceptProcess;
import nars.nal.PremiseBuilder;
import nars.nal.meta.PremiseEval;
import nars.task.Task;
import nars.term.Termed;
import nars.term.variable.Variable;
import nars.util.data.MutableInteger;
import nars.util.event.Active;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * The original deterministic memory cycle implementation that is currently used as a standard
 * for development and testing.
 */
public abstract class AbstractCore {
    /**
     * How many concepts to fire each cycle; measures degree of parallelism in each cycle
     */
    @Range(min = 0, max = 64, unit = "Concept")
    public final @NotNull MutableInteger conceptsFiredPerCycle;





    public final @NotNull AutoBag<Task> tasklinkUpdate;

    public final @NotNull AutoBag<Termed> termlinkUpdate;


    //public final MutableFloat activationFactor = new MutableFloat(1.0f);

//        final Function<Task, Task> derivationPostProcess = d -> {
//            return LimitDerivationPriority.limitDerivation(d);
//        };


    /**
     * concepts active in this cycle
     */
    @NotNull
    public final Bag<Concept> concepts;

    @Deprecated
    public final transient @NotNull NAR nar;


    @Range(min = 0, max = 16, unit = "TaskLink") //TODO use float percentage
    public final MutableInteger tasklinksFiredPerFiredConcept = new MutableInteger(1);

    @Range(min = 0, max = 16, unit = "TermLink")
    public final MutableInteger termlinksFiredPerFiredConcept = new MutableInteger(1);



    @NotNull private final AutoBag<Concept> conceptUpdate;

    @NotNull private final PremiseEval matcher;

    /**
     * temporary re-usable array for batch firing
     */
    transient private final List<BLink<? extends Termed>> terms = Global.newArrayList();



    /**
     * temporary re-usable
     */
    transient final List<ConceptProcess> processes = Global.newArrayList();



    protected AbstractCore(@NotNull NAR nar, @NotNull PremiseEval matcher) {

        this.nar = nar;

        this.matcher = matcher;


        this.conceptsFiredPerCycle = new MutableInteger(1);

        this.concepts = newConceptBag();
        this.conceptUpdate = new AutoBag<>(nar.perfection);


        this.termlinkUpdate = new AutoBag(nar.perfection);
        this.tasklinkUpdate = new AutoBag(nar.perfection);

    }

    @NotNull
    protected abstract Bag<Concept> newConceptBag();

    public void frame(@NotNull NAR nar) {

        tasklinkUpdate.update(nar);
        termlinkUpdate.update(nar);
        conceptUpdate.update(nar);


        run(nar.cyclesPerFrame.intValue());
    }

    protected final void run(int cycles) {

        int cpf = conceptsFiredPerCycle.intValue();
        float dCycle = 1f / cycles;

        for (int cycleNum = 0; cycleNum < cycles; cycleNum++) {
            float subCycle = dCycle * cycleNum;

            termlinkUpdate.cycle(subCycle);
            tasklinkUpdate.cycle(subCycle);
            conceptUpdate.cycle(subCycle);

            conceptUpdate.update(concepts, false);

            concepts.sample(cpf, this::fireConcept);
        }


    }

    public void reset(Memory m) {
        concepts.clear();
    }

    protected final void fireConcept(@NotNull BLink<Concept> conceptLink) {
        Concept concept = conceptLink.get();

        tasklinkUpdate.update(concept.tasklinks(), true);
        termlinkUpdate.update(concept.termlinks(), false);

        firePremiseSquared(
                conceptLink,
                tasklinksFiredPerFiredConcept.intValue(),
                termlinksFiredPerFiredConcept.intValue()
        );
    }

    /**
     * iteratively supplies a matrix of premises from the next N tasklinks and M termlinks
     * (recycles buffers, non-thread safe, one thread use this at a time)
     */
    public final void firePremiseSquared(@NotNull BLink<? extends Concept> conceptLink, int tasklinks, int termlinks) {

        Concept c = conceptLink.get();

        List<BLink<? extends Termed>> termsBuffer = this.terms;
        Bag<Termed> tl = c.termlinks();
        if (tl.isEmpty())
            return;

        tl.sample(termlinks, termsBuffer::add);

        c.tasklinks().sample(tasklinks, bl -> {
            PremiseBuilder.run(nar, conceptLink, termsBuffer, bl, processes);
        });

        termsBuffer.clear();

        if (!processes.isEmpty()) {
            matcher.run(nar, processes);
            processes.clear();
        }

    }



    public void conceptualize(@NotNull Concept c, @NotNull Budgeted b, float conceptActivation, float linkActivation, MutableFloat conceptOverflow) {

        concepts.put(c, b, conceptActivation, conceptOverflow);
        if (b.isDeleted())
            return;
            //throw new RuntimeException("Concept rejected: " + b);
        if (linkActivation > 0)
            c.link(b, linkActivation, nar, conceptOverflow);
    }


    //try to implement some other way, this is here because of serializability

}
