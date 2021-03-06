package nars.nal.nal4;

import nars.NAR;
import nars.NARS;
import nars.Narsese;
import nars.test.NALTest;
import nars.test.TestNAR;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class NAL4MultistepTest extends NALTest {
    @Override
    protected NAR nar() {
        NAR n = NARS.tmp(4);
//        n.confMin.set(0.25f);
        n.termVolumeMax.set(16);
        return n;
    }


    @Test
    void nal4_everyday_reasoning() {
        int time = 1000;


        TestNAR tester = test;

//        tester.nar.freqResolution.set(0.05f);
        tester.confTolerance(0.4f);

        tester.input("({sky} --> [blue]).");
        tester.input("({tom} --> cat).");
        tester.input("likes({tom},{sky}).");

        tester.input("likes(cat,[blue])?");

        tester.mustBelieve(time, "(likes(cat,[blue]) <-> likes(cat,{sky}))",1f,0.45f);
        tester.mustBelieve(time, "(likes(cat,[blue]) <-> likes({tom},[blue]))",1f,0.45f);
        tester.mustBelieve(time, "(likes(cat,[blue]) <-> likes({tom},{sky}))",1f,0.45f);

        tester.mustBelieve(time, "likes(cat,[blue])",
                1f,
                0.45f);


    }

    @Disabled @Test
    void nal4_everyday_reasoning_easiest() throws Narsese.NarseseException {


        test.believe("blue:sky", 1.0f, 0.9f)
                .believe("likes:sky", 1.0f, 0.9f)
                .ask("likes:blue")
                .mustBelieve(100, "likes:blue", 1.0f, 0.4f /* 0.45? */);

    }

    @Disabled
    @Test
    void nal4_everyday_reasoning_easier() {
        int time = 2550;

        test.confTolerance(0.2f);
        test.nar.termVolumeMax.set(8);
        test.nar.freqResolution.set(0.25f);
        test.nar.confResolution.set(0.1f);
        test.believe("blue:sky", 1.0f, 0.9f)
                .believe("cat:tom", 1.0f, 0.9f)
                .believe("likes(tom,sky)", 1.0f, 0.9f)
                .input("$0.99 likes(cat,blue)?")
                .mustBelieve(time, "likes(cat,blue)", 1.0f, 0.27f /*0.45f*/)
        ;

    }


}
