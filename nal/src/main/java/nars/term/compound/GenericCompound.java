package nars.term.compound;

import com.gs.collections.api.block.predicate.primitive.IntObjectPredicate;
import nars.Op;
import nars.nal.Tense;
import nars.term.Compound;
import nars.term.Term;
import nars.term.TermPrinter;
import nars.term.Termed;
import nars.term.container.TermContainer;
import nars.util.data.Util;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;

import static nars.nal.Tense.DTERNAL;


public class GenericCompound<T extends Term> implements Compound<T> {

    @NotNull
    public final Op op;

    /**
     * subterm vector
     */
    @NotNull
    public final TermContainer<T> subterms;

    /**
     * subterm relation, resolves to unique concept
     */
    public final int relation;

    /**
     * temporal relation (dt), resolves to same concept
     */
    public final int dt;


    private final transient int hash;
    public transient boolean normalized;


    public GenericCompound(@NotNull Op op, @NotNull TermContainer subterms) {
        this(op, -1, subterms);
    }

    public GenericCompound(@NotNull Op op, int relation, @NotNull TermContainer subterms) {
        this(op, relation, Tense.DTERNAL, subterms);
    }

    public GenericCompound(@NotNull Op op, int relation, int dt, @NotNull TermContainer subterms) {
        this.subterms = subterms;
        this.normalized = (subterms.vars() == 0) && (subterms.varPattern() == 0) /* not included in the count */;
        this.op = op;

        if (!op.isTemporal() && dt != DTERNAL)
            throw new RuntimeException("invalid temporal relation for " + op);

        //t = op.isTemporal() ? t : ITERNAL;
        this.dt = dt;

        this.relation = relation;

        this.hash = Util.hashCombine(subterms.hashCode(), opRel(), dt);
    }

    @Override
    public final boolean term(int i, @NotNull Op o) {
        return subterms.term(i, o);
    }

//    protected GenericCompound(GenericCompound copy, int newT) {
//        this.terms = copy.terms;
//        this.normalized = copy.normalized;
//        this.op = copy.op;
//        this.relation = copy.relation;
//        this.hash = copy.
//    }

    @NotNull
    @Override
    public final Op op() {
        return op;
    }


    @Override
    public final boolean isCommutative() {
        if (op.isCommutative() && size() > 1) {
            int t = dt();
            return (t == DTERNAL || ((op == Op.CONJUNCTION && t == 0)));
        }
        return false;
    }

//    /** true if this compound does not involve temporal relation between its terms */
//    public boolean atemporal() {
//        return t()==ITERNAL;
//    }

    @Override
    public final void append(@NotNull Appendable p) throws IOException {
        TermPrinter.append(this, p);
    }

    @NotNull
    @Override
    public String toString() {
        return TermPrinter.stringify(this).toString();
    }

    @Override
    public int compareTo(@NotNull Object o) {
        if (this == o) return 0;

        Termed t = (Termed) o;
        //int diff = op().compareTo(t.op());

        //sort by op and relation first
        int diff = Integer.compare(opRel(), t.opRel()); //op.ordinal(), t.op().ordinal());
        if (diff != 0)
            return diff;


        Compound c = (Compound) (t.term());

//        int diff2 = Integer.compare(this.hash, c.hashCode());
//        if (diff2 != 0)
//            return diff2;


        int diff3 = Integer.compare(this.dt, c.dt());
        if (diff3 != 0)
            return diff3;

        return subterms.compareTo(c.subterms());

    }



    @Override
    public final void addAllTo(@NotNull Collection<Term> set) {
        subterms.addAllTo(set);
    }

    @NotNull
    @Override
    public final TermContainer<T> subterms() {
        return subterms;
    }

    @Override
    public boolean equals(@NotNull Object that) {
        return this == that ||( hash == that.hashCode() && equalsFurther((Termed) that) );
    }

    @Override
    public boolean equalTerms(TermContainer c) {
        return subterms.equalTerms(c);
    }

    private boolean equalsFurther(@NotNull Termed thatTerm) {

        boolean r = false;
        Term u = thatTerm.term();
        if ((u.op() == op) /*&& (((t instanceof Compound))*/) {
            Compound c = (Compound) u;
            r = subterms.equals(c.subterms())
                    && (relation == c.relation())
                    && (dt == c.dt());
        }
        return r;
    }


    @Override
    public int hashCode() {
        return hash;
    }


    @Override
    public final int varDep() {
        return subterms.varDep();
    }

    @Override
    public final int varIndep() {
        return subterms.varIndep();
    }

    @Override
    public final int varQuery() {
        return subterms.varQuery();
    }

    @Override
    public final int varPattern() {
        return subterms.varPattern();
    }


    @Override
    public final int vars() {
        return subterms.vars();
    }

//    public final Term[] cloneTermsReplacing(int index, Term replaced) {
//        return terms.cloneTermsReplacing(index, replaced);
//    }

    @Override
    public final boolean isEmpty() {
        return subterms.isEmpty();
    }

    @Override
    public final boolean contains(Object o) {
        return subterms.contains(o);
    }


    @Override
    public final void forEach(@NotNull Consumer<? super T> action, int start, int stop) {
        subterms.forEach(action, start, stop);
    }

    @NotNull
    @Override
    public TermContainer replacing(int subterm, Term replacement) {
        throw new RuntimeException("n/a for compound");
    }

    @Override
    public final void forEach(@NotNull Consumer<? super T> c) {
        subterms.forEach(c);
    }

    @NotNull
    @Override @Deprecated
    public T[] terms() {
        return subterms.terms();
    }

    @Override
    public final Term[] terms(@NotNull IntObjectPredicate<T> filter) {
        return subterms.terms(filter);
    }

    @Override
    public final Iterator<T> iterator() {
        return subterms.iterator();
    }

    @Override
    public int structure() {
        return subterms.structure() | op.bit();
    }

    @Override
    public final T term(int i) {
        return subterms.term(i);
    }

    @Override
    public final boolean containsTerm(@NotNull Term target) {
        return subterms.containsTerm(target);
    }

    @Override
    public final int size() {
        return subterms.size();
    }

    @Override
    public final int complexity() {
        return subterms.complexity();
    }

    @Override
    public final int volume() {
        return subterms.volume();
    }

    @Override
    public final boolean impossibleSubTermVolume(int otherTermVolume) {
        return subterms.impossibleSubTermVolume(otherTermVolume);
    }

    @Override
    public final boolean isNormalized() {
        return normalized;
    }

    /**
     * WARNING: this does not perform commutive handling correctly. use the index newTerm method for now
     */
    @NotNull
    @Override
    public final Compound dt(int cycles) {
        if (cycles == dt) return this;
        GenericCompound g = new GenericCompound(op, relation, cycles, subterms);
        if (normalized) g.setNormalized();
        return g;
    }

    @Override
    public final int dt() {
        return dt;
    }



    @Override
    public final int relation() {
        return relation;
    }

    /**
     * do not call this manually, it will be set by VariableNormalization only
     */
    public final void setNormalized() {
        this.normalized = true;
    }


//    @Override
//    public int bytesLength() {
//        int len = /* opener byte */1 + (op.isImage() ? 1 : 0);
//
//        int n = size();
//        for (int i = 0; i < n; i++) {
//            len += term(i).bytesLength() + 1 /* separator or closer if end*/;
//        }
//
//        return len;
//    }

//    @Override
//    public final byte[] bytes() {
//
//        ByteBuf b = ByteBuf.create(bytesLength());
//
//        b.add((byte) op().ordinal()); //header
//
//        if (op().isImage()) {
//            b.add((byte) relation); //header
//        }
//
//        appendSubtermBytes(b);
//
//        if (op().maxSize != 1) {
//            b.add(COMPOUND_TERM_CLOSERbyte); //closer
//        }
//
//        return b.toBytes();
//    }
//
//
//    @Override
//    public void appendSubtermBytes(ByteBuf b) {
//        terms.appendSubtermBytes(b);
//    }
//
//    @Override
//    public final boolean and(Predicate<? super Term> v) {
//        return v.test(this) && terms.and(v);
//    }
//    @Override
//    public final boolean or(Predicate<? super Term> v) {
//        return v.test(this) || terms.or(v);
//    }


}
