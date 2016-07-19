package nars.nal.op;

import nars.term.Compound;
import nars.term.Term;
import org.jetbrains.annotations.Nullable;


@FunctionalInterface  public interface TermTransform {

    @Nullable Term function(Compound args);

}
