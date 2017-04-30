package nars.experiment.fzero;

import java4k.gradius4k.Gradius4K;
import jcog.Util;
import nars.$;
import nars.NAR;
import nars.NAgentX;
import nars.Narsese;
import nars.experiment.pacman.PacMan;
import nars.nar.Default;
import nars.nar.NARBuilder;
import nars.term.atom.Atomic;
import nars.time.RealTime;

import static nars.$.t;

/**
 * Created by me on 4/30/17.
 */
public class Pacman extends NAgentX {

    private final PacMan g;

    public Pacman(NAR nar) throws Narsese.NarseseException {
        super("G", nar);

        this.g = new PacMan();

        senseCamera("G", g.view, 64, 64, (v) -> t(v, alpha()))
                .setResolution(0.01f);


        actionTriState($.inh(Atomic.the("x"), id), (dh) -> {
            g.keys[0 /* left */] = false;
            g.keys[1 /* right */] = false;
            switch (dh) {
                case +1:
                    g.keys[1] = true;
                    break;
                case -1:
                    g.keys[0] = true;
                    break;
            }
        });

       actionTriState($.inh(Atomic.the("y"), id), (dh) -> {
            g.keys[2 /* up */] = false;
            g.keys[3 /* down */] = false;
            switch (dh) {
                case +1:
                    g.keys[2] = true;
                    break;
                case -1:
                    g.keys[3] = true;
                    break;
            }
        });


    }


    int lastScore = 0;

    @Override
    protected float act() {


        int nextScore = g.score;

        float r = (nextScore - lastScore);
//        if (r > 0)
//            System.out.println(r);
        lastScore = nextScore;


            return 2f * (Util.sigmoid(r) - 0.5f);
    }

    public static void main(String[] args) throws Narsese.NarseseException {
        Default n = NARBuilder.newMultiThreadNAR(
                4,
                new RealTime.DSHalf(true)
                        .durFPS(20f), true);

        Pacman a = new Pacman(n);
        a.runRT(20f);


        NAgentX.chart(a);

    }

}
