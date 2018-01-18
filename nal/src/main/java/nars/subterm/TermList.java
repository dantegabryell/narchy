package nars.subterm;

import jcog.list.FasterList;
import nars.The;
import nars.term.Term;

import java.util.Collection;
import java.util.List;

/** mutable subterms, used in intermediate operations */
public class TermList extends FasterList<Term> implements Subterms {

//    @Nullable
//    transient TermMetadata meta = null;
    public TermList() {
        super(4);
    }

    public TermList(int initialCapacity) {
        super(initialCapacity);
    }

    public TermList(Term[] direct) {
        super(direct.length, direct);
    }

    public TermList(Collection<Term> copied) {
        super(copied.size());
        copied.forEach(this::addWithoutResizeCheck);
    }

    public TermList(Iterable<Term> copied) {
        super(0);
        copied.forEach(this::add);
    }

    @Override
    public int hashCode() {
        return Subterms.hash((List)this);
    }

    @Override
    public Term sub(int i) {
        return get(i);
    }

    @Override
    public Term[] arrayClone() {
        return toArray(new Term[size()]);
    }

    /** creates an immutable instance of this */
    public Subterms the() {
        return The.subterms(this);
    }

    @Override
    public int subs() {
        return size;
    }

    @Override
    public String toString() {
        return Subterms.toString(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        //use hash if available
        if ((obj instanceof TermList)) {
            return fastListEquals(((TermList)obj));
        } else {
            if (hashCode()!=obj.hashCode())
                return false;
            return ((Subterms)obj).equalTerms(this);
        }
    }

    public TermList added(Term... x) {
        ensureCapacity(size + x.length);
        for (Term xx : x)
            addWithoutResizeCheck(xx);
        return this;
    }

    public TermList added(Iterable<? extends Term> x) {
        addAllIterable(x);
        return this;
    }

    public void addAll(Subterms x, int xStart, int xEnd) {
        ensureCapacity(xEnd-xStart);
        for (int i = xStart; i < xEnd; i++) {
            addWithoutResizeCheck(x.sub(i));
        }
    }
}
