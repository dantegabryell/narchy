package nars.index.concept;

import jcog.WTF;
import jcog.pri.bag.Sampler;
import nars.NAR;
import nars.Param;
import nars.concept.Concept;
import nars.concept.PermanentConcept;
import nars.link.Activate;
import nars.term.Term;
import nars.term.Termed;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 *
 */
public abstract class ConceptIndex implements Sampler<Activate> {


    public NAR nar;

    /**
     * internal get procedure (synchronous)
     */
    @Nullable
    public abstract Termed get(/*@NotNull*/ Term key, boolean createIfMissing);

    private CompletableFuture<Termed> getAsync(Term key, boolean createIfMissing) {
        return CompletableFuture.completedFuture(get(key, createIfMissing));
    }


    /**
     * sets or replaces the existing value, unless the existing value is a PermanentConcept it must not
     * be replaced with a non-Permanent concept
     */
    public abstract void set(Term src, Termed target);

    public final void set(Termed t) {
        set(t.term(), t);
    }

    abstract public void clear();


    public void start(NAR nar) {
        this.nar = nar;

    }


    /**
     * # of contained terms
     */
    public abstract int size();

    /**
     * a string containing statistics of the index's current state
     */
    public abstract String summary();

    @Nullable public abstract Termed remove(Term entry);

    public void print(PrintStream out) {
        stream().forEach(out::println);
        out.println();
    }

    abstract public Stream<Termed> stream();


    /**
     * default impl
     */
    public void forEach(Consumer<? super Termed> c) {
        stream().forEach(c);
    }

    /**
     * applies normalization and anonymization to resolve the term of the concept the input term maps t
     * term should be conceptualizable prior to calling this
     */
    @Nullable
    public final Concept concept(Termed _x, boolean createIfMissing) {
        if (_x instanceof Concept && elideConceptGets() && !(((Concept) _x).isDeleted()))
            return ((Concept) _x);

        Term x = _x.term().concept();
        if (x == null || !x.op().conceptualizable) {
            if (Param.DEBUG)
                throw new WTF(_x + " not conceptualizable");
            else
                return null;
        }

//        if (!xx.the())
//            throw new WTF(_x + " not immutable");

        return (Concept) get(x, createIfMissing);
    }

    /**
     * for performance, if lookup of a Concept instance is performed using
     * a supplied non-deleted Concept instance, return that Concept directly.
     * ie. it assumes that the known Concept is the active one.
     * <p>
     * this can be undesirable if the concept index has an eviction mechanism
     * which counts lookup frequency, which would be skewed if elision is enabled.
     */
    boolean elideConceptGets() {
        return true;
    }

    public final void conceptAsync(Term x, boolean createIfMissing, Consumer<Concept> with) {

        Term y;
        if (x instanceof Concept) {
            Concept ct = (Concept) x;
            if (!ct.isDeleted()) {
                with.accept(ct);
                return;
            }

            y = ct.term();
        } else {
            y = x.term().concept();
            if (!y.op().conceptualizable)
                return;
        }


        getAsync(y, createIfMissing).handle((t, e) -> {
            if (e != null) {
                e.printStackTrace();
            } else {
                with.accept((Concept) t);
            }
            return this;
        });
    }

    final void onRemove(Termed value) {
//        if (value instanceof Concept) {
            if (value instanceof PermanentConcept) {
                nar.runLater(() -> set(value));
            } else {
                ((Concept) value).delete(nar);
            }
//        }
    }

    public abstract Stream<Activate> active();

    public abstract void activate(Concept c, float pri);

    /** the current priority value of the concept */
    public abstract float pri(Termed concept, float ifMissing);

}
