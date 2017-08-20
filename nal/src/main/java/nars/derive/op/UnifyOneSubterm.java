package nars.derive.op;

import nars.$;
import nars.Param;
import nars.control.Derivation;
import nars.derive.PrediTerm;
import nars.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Created by me on 5/21/16.
 */
public final class UnifyOneSubterm extends UnificationPrototype {

    /**
     * either 0 (task) or 1 (belief)
     */
    private final int subterm;

    private final boolean finish;

    public UnifyOneSubterm(@NotNull Term x, int subterm, boolean finish) {
        super( $.func("unifyTask", $.the(subterm==0 ? "task" : "belief"), x), x );
        this.subterm = subterm;
        this.finish = finish;
    }

    @Override
    public final PrediTerm<Derivation> transform(Function<PrediTerm<Derivation>, PrediTerm<Derivation>> f) {
      PrediTerm<Derivation> eachMatch = buildEachMatch();
      return build( eachMatch!=null ? eachMatch.transform(f) : null ).transform(f);
    }


    @Override @NotNull
    protected PrediTerm build(@Nullable PrediTerm eachMatch) {
        assert(finish ? eachMatch != null : eachMatch == null): "conclusion wrong";
        if (!finish) {
            return new UnifySubterm(subterm, pattern);
        }else {
            return new UnifySubtermThenConclude(subterm, pattern, eachMatch);
        }
    }


    public static final class UnifySubterm extends MatchTerm {

        /** which premise component, 0 (task) or 1 (belief) */
        private final int subterm;

        public UnifySubterm(int subterm, @NotNull Term pattern) {
            super($.func("unify", $.the(subterm), pattern), pattern);
            this.subterm = subterm;
        }

        @Override
        public final boolean test(@NotNull Derivation p) {
            if (!p.unify(super.pattern, term(p) /* current term */, false)) {
                return false;
            } else {
                return p.live();
            }
        }

        final @NotNull Term term(@NotNull Derivation p) {
            return subterm == 0 ? p.taskTerm : p.beliefTerm;
        }
    }

    public static final class UnifySubtermThenConclude extends MatchTerm {

        /** which premise component, 0 (task) or 1 (belief) */
        private final int subterm;
        public final @Nullable PrediTerm eachMatch;

        public UnifySubtermThenConclude(int subterm, @NotNull Term pattern, @NotNull PrediTerm eachMatch) {
            super($.func("unify", $.the(subterm), pattern, eachMatch), pattern);
            this.subterm = subterm;
            this.eachMatch = eachMatch;
        }

        @Override
        public final boolean test(@NotNull Derivation p) {
            if (!p.use(Param.TTL_UNIFY))
                return false;

            int now = p.now();
            p.unifyAll(super.pattern, subterm == 0 ? p.taskTerm : p.beliefTerm /* current term */, eachMatch);
            return p.revertAndContinue(now);

            //return p.live();
        }
    }

}