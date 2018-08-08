package nars.term.compound;

import com.google.common.io.ByteArrayDataOutput;
import jcog.Util;
import jcog.data.byt.DynBytes;
import nars.IO;
import nars.Op;
import nars.The;
import nars.subterm.Subterms;
import nars.term.Compound;
import nars.term.Term;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static nars.Op.NEG;
import static nars.time.Tense.DTERNAL;


/**
 * on-heap, caches many commonly used methods for fast repeat access while it survives
 */
abstract public class CachedCompound implements SeparateSubtermsCompound, The {

    /**
     * subterm vector
     */
    private final Subterms subterms;


    /**
     * content hash
     */
    protected final int hash;

    private final byte op;

    private final short _volume;
    private final int _structure;

    public static class SimpleCachedCompound extends CachedCompound {

        public SimpleCachedCompound(Op op, Subterms subterms) {
            super(op, DTERNAL, subterms);
        }

        @Override
        public boolean equalsRoot(Term x) {
            return x instanceof SimpleCachedCompound ? equals(x) : equals(x.root());
        }

        @Override
        public boolean equalsNegRoot(Term x) {
            return x instanceof SimpleCachedCompound ? equalsNeg(x) : equalsNeg(x.root());
        }

        @Override
        public Term root() {
            return this;
        }

        @Override
        public Term concept() {
            return this;
        }

        @Override
        public boolean hasXternal() {
            return false;
        }

        @Override
        public boolean isTemporal() {
            return false;
        }

        @Override
        public int eventCount() {
            return 1;
        }

        @Override
        public int dtRange() {
            return 0;
        }

        @Override
        public int subTimeOnly(Term x) {
            return equals(x) ? 0 : DTERNAL;
        }


        @Override
        public int dt() {
            return DTERNAL;
        }

    }

    public static final class SimpleCachedCompoundWithBytes extends SimpleCachedCompound {

        final byte[] key;

        public SimpleCachedCompoundWithBytes(Op op, Subterms subterms) {
            this(op, subterms, null);
        }

        public SimpleCachedCompoundWithBytes(Op op, Subterms subterms, @Nullable byte[] knownKey) {
            super(op, subterms);

            if (knownKey==null) {
                DynBytes d = new DynBytes(IO.termBytesEstimate(subterms)+1);
                super.appendTo((ByteArrayDataOutput) d);
                key = d.array();
            } else {
                key = knownKey;
            }
        }

        @Override public boolean equals(Object that) {
            if (this == that) return true;
            if (that instanceof SimpleCachedCompoundWithBytes) {
                SimpleCachedCompoundWithBytes tha = ((SimpleCachedCompoundWithBytes) that);
                return hash == tha.hash && Arrays.equals(key, tha.key);
            } else {
                return Compound.equals(this, that);
            }
        }

        @Override
        public void appendTo(ByteArrayDataOutput out) {
            out.write(key);
        }
    }


    /** caches a reference to the root for use in terms that are inequal to their root */
    public static class TemporalCachedCompound extends CachedCompound  {
//        private transient Term rooted = null;
//        private transient Term concepted = null;
        protected final int dt;

        public TemporalCachedCompound(Op op, int dt, Subterms subterms) {
            super(op, dt, subterms);
            this.dt = dt;

//            //TEMPORARY for debug:
//            anon();
        }

        @Override
        public int dt() {
            return dt;
        }

//        @Override
//        public Term root() {
//            Term rooted = this.rooted;
//            return (rooted != null) ? rooted : (this.rooted = super.root());
//        }
//
//        @Override
//        public Term concept() {
//            Term concepted = this.concepted;
//            return (concepted != null) ? concepted : (this.concepted = super.concept());
//        }

    }










    private CachedCompound(/*@NotNull*/ Op op, int dt, Subterms subterms) {

        assert(op!=NEG); 

        int h = (this.subterms = subterms).hashWith(this.op = op.id);
        this.hash = (dt == DTERNAL) ? h : Util.hashCombine(h, dt);


        this._structure = subterms.structure() | op.bit;

        int _volume = subterms.volume();
        assert(_volume < Short.MAX_VALUE-1);
        this._volume = (short)_volume;
    }


    /** since Neg compounds are disallowed for this impl */
    @Override public final Term unneg() {
        return this;
    }

    @Override
    public final int volume() {
        return _volume;
    }

    @Override
    public final int structure() {
        return _structure;
    }

    @Override
    public final Subterms subterms() {
        return subterms;
    }

    @Override
    public int varPattern() {
        return hasVarPattern() ? subterms().varPattern() : 0;
    }
    @Override
    public int varQuery() {
        return hasVarQuery() ? subterms().varQuery() : 0;
    }

    @Override
    public int varDep() {
        return hasVarDep() ? subterms().varDep() : 0;
    }

    @Override
    public int varIndep() {
        return hasVarIndep() ? subterms().varIndep() : 0;
    }

    @Override
    public final int hashCode() {
        return hash;
    }


    abstract public int dt();

    @Override
    public final Op op() {
        
        return Op.ops[op];
    }


    @Override
    public String toString() {
        return Compound.toString(this);
    }


    @Override
    public boolean equals(@Nullable Object that) {
        return Compound.equals(this, that);
    }









}
