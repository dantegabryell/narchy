package nars.term.subst;

import nars.Op;
import nars.Param;
import nars.index.term.TermIndex;
import nars.premise.Derivation;
import nars.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Less powerful one-match only unification
 */
public class SubUnify extends Unify {

    private @Nullable Term xterm;
    private @Nullable Derivation target;

    @Nullable private Term result;


    public SubUnify(TermIndex index, Op type, Random r, int ttl) {
        super(index, type, r, Param.SubUnificationStackMax, ttl);
    }

    public SubUnify(@NotNull Unify parent, @Nullable Op type) {
        super(parent.index, type, parent.random, parent.versioning);
    }


    /**
     * terminates after the first match
     */
    @Override
    public boolean onMatch() {
        //apply the match before the xy/yx mapping gets reverted after leaving the termutator
        if (xterm != null) {
            final Subst s;
            if (target != null) {
                if (!target.put(this)) {
                    return true; //try again
                }
                s = target;
            } else {
                s = this;
            }

            result = s.transform(xterm, index);
        }

        return (result == null); //
    }

    public void tryMatch(@NotNull Term x, @NotNull Term y) {
        this.xterm = null;
        this.target = null;
        this.result = null;
        unify(x, y, true, true);
    }

    @Nullable
    public Term tryMatch(@Nullable Derivation target, @Nullable Term xterm, @NotNull Term x, @NotNull Term y) {
        this.xterm = xterm;
        this.target = target;
        this.result = null;
        unify(x, y, true, true);
        return result;
    }

}
