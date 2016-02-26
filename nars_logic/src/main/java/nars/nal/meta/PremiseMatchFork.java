package nars.nal.meta;

import org.jetbrains.annotations.NotNull;

/**
 * reverting fork for use during premise matching
 */
public final class PremiseMatchFork extends ThenFork {

    private final ProcTerm[] termCache;

    public PremiseMatchFork(ProcTerm[] n) {
        super(n);
        this.termCache = n;
    }

    @Override
    public final void accept(@NotNull PremiseEval m) {
        int revertTime = m.now();
        for (ProcTerm s : termCache) {
            s.accept(m);
            m.revert(revertTime);
        }
    }
}
