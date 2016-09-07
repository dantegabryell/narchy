package nars.term.subst.choice;

import nars.$;
import nars.term.Termlike;
import nars.term.subst.FindSubst;
import org.jetbrains.annotations.NotNull;

/**
 * AIKR choicepoint used in deciding possible mutations to apply in deriving new compounds
 */
public abstract class Termutator  {

    /** should have equals consistency */
    public final Object key;

    public Termutator(@NotNull Object key) {
        this.key = key;
    }


    /** match all termutations recursing to the next after each successful one */
    public abstract void run(FindSubst f, Termutator[] chain, int current);

    /** call this to invoke the next termutator in the chain */
    protected static boolean next(FindSubst f, Termutator[] chain, int next) {

        //increment the version counter by one and detect if the limit exceeded.
        // this is to prevent infinite recursions in which no version incrementing
        // occurrs that would otherwise trigger overflow to interrupt it.
        if (f.versioning.next()==-1)
            return false;

        chain[++next].run(f, chain, next);

        return true;
    }

    public abstract int getEstimatedPermutations();


    @Override
    public final boolean equals(@NotNull Object obj) {
        if (this == obj) return true;
        return key.equals(((Termutator)obj).key);
    }

    @Override
    public final int hashCode() {
        return key.hashCode();
    }

//    public final String toStringCached() {
//        String s = this.asStringCached;
//        if (s == null)
//            s = this.asStringCached = toString();
//        return s;
//    }

}
