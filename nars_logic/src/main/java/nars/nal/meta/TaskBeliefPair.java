package nars.nal.meta;

import nars.Op;
import nars.term.Term;
import nars.term.compound.GenericCompound;
import nars.term.container.TermVector;
import org.jetbrains.annotations.NotNull;

/**
 * just holds two terms, not really necessary. recycled
 */
public final class TaskBeliefPair extends GenericCompound {

    //int actualStructure;

    public TaskBeliefPair() {
        super(Op.PRODUCT, TermVector.the(Op.Imdex, Op.Imdex) /* placeholders */);
    }

    public TaskBeliefPair(@NotNull Term a, @NotNull Term b) {
        this();
        set(a, b);
    }


    public void set(@NotNull Term a, @NotNull Term b) {
        Term[] t = terms();
        t[0] = a;
        t[1] = b;

        //this.actualStructure = a.structure() | b.structure();
        //subterms.init();
    }

    @Override
    public int structure() {
        throw new RuntimeException("N/A");
        //return actualStructure;
    }



//    public final boolean substitutesMayExist(final TaskBeliefPair pattern) {
//        //return substitutesMayExistParanoid(pattern);
//        return substitutesMayExistFast(pattern);
//    }

//    private static boolean substitutesMayExistParanoid(PairMatchingProduct pattern) {
//        return true;
//    }


//    public final boolean substitutesMayExistFast(final TaskBeliefPair pattern) {
////        //the pattern structure will include a Product
////        if (impossibleToMatch(pattern.structure())) {
//////            System.out.println(
//////                "impossible? " +
//////                        Integer.toBinaryString(structure()) + " " + this +
//////                        Integer.toBinaryString(pattern.structure()) + " " + pattern
//////            );
////
////            return false;
////        }
//
////        if (volume() < pattern.volume())
////            return false;
//
//        //compare the task and belief individually to filter out more:
//
//        //final Term c = term(0);
//        if (Term.impossibleToMatch(structureA, pattern.structureA)) return false;
//
//        //final Term d = term(1);
//        if (Term.impossibleToMatch(structureB, pattern.structureB)) return false;
//
//
//        if (volA < pattern.volA)
//            return false;
//
//        if (volB < pattern.volB)
//            return false;
//
//
//        return true;
//    }


//    @Override
//    protected <T extends Term> T normalized(boolean destructive) {
//        return (T) this;
//    }

}
