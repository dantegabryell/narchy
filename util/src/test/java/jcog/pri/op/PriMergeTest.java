package jcog.pri.op;

import jcog.pri.PriRO;
import jcog.pri.Prioritizable;
import jcog.pri.Prioritized;
import jcog.pri.UnitPri;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriMergeTest {
    private final static float tol = 0.01f;

    private static final PriRO a = new PriRO(1);
    private static final PriRO b = new PriRO(0.5f);
    private static final PriRO c = new PriRO(0.25f);

    @Test
    void testPlusDQBlend() {
        PriMerge m = PriMerge.plus;

        testMerge(z(), a, m,  1f, 0 /*overflow*/);  
        testMerge(a, z(),  m, a.pri());  

        testMerge(b, c,  m,0.75f); 


        testMerge(a, c, m,  1,  
                0.25f); 


        testMerge(a, a,  m, a.pri()); 

    }

    @Test
    void testAvg() {
        PriMerge AVG = PriMerge.avg;

        
        testMerge(z(), a,  AVG, 0.5f * a.pri());
        
        
        testMerge(a, z(),  AVG, 0.5f * a.pri());

        
        testMerge(b, b,  AVG, b.pri()); 

        testMerge(b, c,  AVG, 0.375f); 
        testMerge(c, b, AVG, 0.375f); 


        testMerge(a, c,  AVG, 0.625f); 

    }

    private static UnitPri z() {
        return new UnitPri(0);
    }
















    private static Prioritized testMerge(Prioritizable x, Prioritized y, @NotNull PriMerge m, float ouPri) {
        return testMerge(x, y, m, ouPri, -1f);
    }
    private static Prioritized testMerge(Prioritizable _x, Prioritized y, @NotNull PriMerge m, float ouPri, float expectedOverflow) {
        UnitPri x = new UnitPri(_x);

        UnitPri x0 = new UnitPri(x);

        float overflow = m.merge(x, y);

        System.out.println(x0 + " <-merge<- " + y + " x "  + "\t\texpect:" + new UnitPri(ouPri) + " ?? actual:" + x);
        assertEquals(ouPri, x.pri(), tol);

        if (expectedOverflow > 0)
            assertEquals(expectedOverflow, overflow, 0.01f);

        return x;
    }

}