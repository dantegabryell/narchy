package nars.op.data;

import nars.nal.op.BinaryTermOperator;
import nars.term.Compound;
import nars.term.Term;
import nars.index.TermIndex;
import nars.term.container.TermContainer;
import org.jetbrains.annotations.NotNull;

public class union extends BinaryTermOperator {
    
    @NotNull
    @Override public Term apply(@NotNull Term a, Term b, @NotNull TermIndex i) {
        if (!(a instanceof Compound) || !(b instanceof Compound))
            return null;

        if (a.equals(b)) return a;

        return TermContainer.union(i.builder(), (Compound) a, (Compound) b );
    }

}
