package nars.term.util.builder;

import com.google.common.collect.Iterators;
import jcog.memoize.byt.ByteHijackMemoize;
import jcog.pri.PriProxy;
import nars.Op;
import nars.term.Term;
import nars.term.atom.Atomic;
import nars.term.util.InternedCompound;
import org.junit.jupiter.api.Test;

import static nars.Op.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InterningTermBuilderTest {

    final static Term a = Atomic.the("a"), b = Atomic.the("b");

    @Test
    public void test1() {
        InterningTermBuilder t = new InterningTermBuilder();
        Term pab = t.compound(PROD, a, b);
        assertEquals( "(a,b)", pab.toString());

        ByteHijackMemoize<InternedCompound, Term> prodCache = t.terms[PROD.id];

        PriProxy<InternedCompound, Term> pabEntry = Iterators.get(prodCache.iterator(), 0);
        assertEquals(pab, pabEntry.get());
        Term pabSame = t.compound(PROD, a, b);
        assertSame(pab, pabSame);

        Term paab = t.compound(PROD, a, t.compound(PROD, a, b));
//        prodCache.print();

        assertSame(pab, paab.sub(1));

        //Huffman h = prodCache.buildCodec();

    }

    @Test public void testImplicationComplexEndToEnd() {
        //InterningTermBuilder t = new InterningTermBuilder();
        if (Op.terms instanceof InterningTermBuilder) {

            InterningTermBuilder i = (InterningTermBuilder) Op.terms;
//        System.out.println("impl/conj:");
//        i.terms[Op.IMPL.id].print();
//        i.terms[CONJ.id].print();
            IMPL.the(a, CONJ.the(b.neg(), CONJ.the(a, 1, CONJ.the(b.neg(), b)).neg()));
//        System.out.println("impl/conj:");
//        i.terms[Op.IMPL.id].print();
//        i.terms[CONJ.id].print();
        }
    }
}