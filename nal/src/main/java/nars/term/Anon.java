package nars.term;

import jcog.Util;
import jcog.list.FasterList;
import nars.Op;
import nars.Task;
import nars.term.atom.Atomic;
import nars.term.atom.Int;
import nars.term.container.TermVector;
import nars.term.transform.CompoundTransform;
import nars.term.var.UnnormalizedVariable;
import nars.term.var.Variable;
import org.eclipse.collections.api.block.function.primitive.ByteFunction;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectByteHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static nars.Op.ATOM;
import static nars.term.Anon.Anom.MAX_ANOM;

/**
 * term anonymization context, for canonicalization and generification of compounds
 */
public class Anon {


    /* indexed anonymous term */
    static final class Anom extends Int {

        final static int MAX_ANOM = 127;
        final static int ANOM = Term.opX(ATOM, 0);

        Anom(int i) {
            super(i);
        }

        @Override
        public int opX() {
            return ANOM;
        }

        @Override
        public /**/ Op op() {
            return ATOM;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public final boolean equals(Object obj) {
            return this == obj;
        }

        //optimized compareTo for frequent Anom->Anom cases
        @Override
        public int compareTo(Termed yy) {
            Term y = yy.term();
            if (y instanceof Anom) {
                return Integer.compare(id, ((Anom)y).id);
            } else {
                int vc = Integer.compare(y.volume(), this.volume());
                if (vc != 0)
                    return vc;

                int oc = Integer.compare(this.opX(), y.opX());
                assert(oc!=0);
                return oc;
            }
            //return super.compareTo(yy);
        }

        static Anom[] cached = Util.map(0, MAX_ANOM, Anom::new, Anom[]::new);

        public static Anom the(int i) {
            return cached[i];
        }
    }

    final ObjectByteHashMap<Term> fwd = new ObjectByteHashMap();
    final List<Term> rev = new FasterList<>();

    final ByteFunction<Term> nextUniqueAtom = (Term next) -> {
        int s = rev.size();
        assert (s < MAX_ANOM);
        rev.add(next);
        return (byte) s;
    };

    final CompoundTransform PUT = new CompoundTransform() {
        @Override
        public @Nullable Termed apply(Term t) {
            return put(t);
        }

        @Override
        public @Nullable Term applyTermOrNull(Term t) {
            return put(t); //may be called more directly
        }
    };

    final CompoundTransform GET = new CompoundTransform() {
        @Override
        public @Nullable Termed apply(Term t) {
            return get(t);
        }

        @Override
        public @Nullable Term applyTermOrNull(Term t) {
            return get(t); //may be called more directly
        }
    };

    public void clear() {
        fwd.clear();
        rev.clear();
    }

    public Term put(Term x) {
        if (x instanceof Variable) {
            assert (!(x instanceof UnnormalizedVariable));
            return x; //ignore normalized variables
        } else if (x instanceof Atomic) {
            return Anom.cached[fwd.getIfAbsentPutWithKey(x, nextUniqueAtom)];
        } else {
            return x.transform(PUT);
        }
    }

    public Term get(Term x) {
        if (x instanceof Anom) {
            return rev.get(((Anom) x).id); //assume it is an int
        } else if (x instanceof Atomic) {
            return x; //ignore variables, ints
        } else {
            return x.transform(GET);
        }
    }

    public Task put(Task t) {
        Term x = t.term();
        assert (x.isNormalized());
        Term y = put(x);
        if (y instanceof Compound)
            ((TermVector) y.subterms()).setNormalized();

        return Task.clone(t, y);
    }
}
