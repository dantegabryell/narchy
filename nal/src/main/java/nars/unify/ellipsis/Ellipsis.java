package nars.unify.ellipsis;

import nars.$;
import nars.Op;
import nars.term.Term;
import nars.term.Termlike;
import nars.term.Variable;
import nars.term.var.NormalizedVariable;
import nars.term.var.UnnormalizedVariable;
import org.jetbrains.annotations.Nullable;

import static nars.Op.VAR_PATTERN;

public abstract class Ellipsis extends UnnormalizedVariable implements Ellipsislike {

    final byte num;
    public final int minArity;


    Ellipsis(NormalizedVariable target, int minArity) {
        super(VAR_PATTERN, label(target, minArity));

        assert (target.op() == VAR_PATTERN);
        this.minArity = minArity;
        this.num = target.id;

    }

    private static String label(NormalizedVariable target, int minArity) {
        switch (minArity) {
            case 0: return target + "..*";
            case 1: return target + "..+";
        }
        throw new UnsupportedOperationException();
    }

    /**
     * this needs to use .term(x) instead of Term[] because of shuffle terms
     */
    @Nullable
    public static Ellipsislike firstEllipsis(Termlike x) {
        if (x.varPattern() == 0)
            return null;

        int xsize = x.subs();
        for (int i = 0; i < xsize; i++) {
            Term xi = x.sub(i);
            if (xi instanceof Ellipsislike) {
                return (Ellipsislike) xi;
            }
        }
        return null;
    }

    /**
     * this needs to use .term(x) instead of Term[] because of shuffle terms
     */
    @Nullable
    public static Ellipsis firstEllipsis(Term[] x) {

        for (Term xi : x) {
            if (xi instanceof Ellipsis) {
                return (Ellipsis) xi;
            }
        }
        return null;
    }



    public final boolean validSize(int collectable) {
        return collectable >= minArity;
    }

    public static class EllipsisPrototype extends UnnormalizedVariable implements Ellipsislike {

        public final int minArity;

        public EllipsisPrototype(/*@NotNull*/ Op type, Variable target, int minArity) {
            super(type, target
                    + "U.." /* +U instead of 2 to differentiate the prototype from any matching normalized ellipsis */ + (minArity == 0 ? '*' : '+'));
            this.minArity = minArity;
        }


        public static Ellipsis make(byte serial, int minArity) {
            NormalizedVariable v = $.v(VAR_PATTERN, serial);
            switch (minArity) {
                case 0:
                    return new EllipsisZeroOrMore(v);
                case 1:
                    return new EllipsisOneOrMore(v);
                default:
                    throw new RuntimeException("invalid ellipsis minArity");
            }
        }

        @Override
        public
        @Deprecated
        Variable normalizedVariable(byte serial) {
            return make(serial, minArity);
        }
    }


}
