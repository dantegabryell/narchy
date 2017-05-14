package nars.term.compound;

import jcog.Util;
import nars.IO;
import nars.Op;
import nars.term.Compound;
import nars.term.Term;
import nars.term.container.TermContainer;
import nars.term.container.TermVector1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Iterator;
import java.util.function.Consumer;

import static nars.time.Tense.DTERNAL;

/**
 * Compound inheriting directly from TermVector1
 * NOT READY YET
 */
public class UnitCompound1 extends TermVector1 implements Compound {

    private final Op op;
    private final int hash;

    public UnitCompound1(@NotNull Op op, @NotNull Term arg) {
        super(arg);

        this.op = op;
        this.hash = Util.hashCombine(hashCodeSubTerms(), op.ordinal(), DTERNAL);
        if (arg.isNormalized()) setNormalized();
    }

//    @Override
//    public int init(@NotNull int[] meta) {
//        the.init(meta);
//        //meta[5] |= op.bit;
//        return hash;
//    }


    @Override
    public int structure() {
        return op.bit | super.structure();
    }


    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public int hashCodeSubTerms() {
        return super.hashCode();
    }


    @Override
    public boolean equals(@Nullable Object that) {
        if (this == that) return true;

        if (!(that instanceof Compound)) {
            return false;
        }

        Compound t = (Compound) that;

        //TODO if this is a NEG then size and dt can be assumed
        return hash == that.hashCode() && (op == t.op()) && (t.size() == 1) && (t.dt() == DTERNAL) && equalTo(t);
    }



    @NotNull
    @Override
    public String toString() {
        return IO.Printer.stringify(this).toString();
    }

    @Override
    public @NotNull Op op() {
        return op;
    }

    @Deprecated /* HACK */
    @Override
    public @NotNull TermContainer subterms() {
        return new SubtermView(this);
    }

    @Override
    public boolean isNormalized() {
        return the.isNormalized();
    }

    @Override
    public void setNormalized() {
        if (the instanceof Compound)
            ((Compound) the).setNormalized();
    }

    @Override
    public int dt() {
        return DTERNAL;
    }

    private static final class SubtermView implements TermContainer {
        private final Compound c;

        public SubtermView(Compound c) {
            this.c = c;
        }

        @Override
        public int volume() {
            return c.volume() - 1;
        }

        @Override
        public int complexity() {
            return c.complexity() - 1;
        }

        @Override
        public @NotNull Term sub(int i) {
            return c.sub(i);
        }

        @Override
        public int hashCode() {
            return c.hashCodeSubTerms();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;

            if (obj instanceof TermContainer) {
                TermContainer t = (TermContainer) obj;
                if (t.hashCode() == hashCode()) {
                    int ss = size();
                    if (t.size() == ss) {
                        for (int i = 0; i < ss; i++) {
                            if (!sub(i).equals(t.sub(i)))
                                return false;
                        }

                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void forEach(Consumer<? super Term> action, int start, int stop) {
            for (int i = start; i < stop; i++)
                action.accept(c.sub(i));
        }

        @NotNull
        @Override
        public Iterator<Term> iterator() {
            return c.iterator();
        }

        @Override
        public int size() {
            return c.size();
        }
    }
}
