package nars.experiment.arkanoid;


import jcog.math.FloatPolarNormalized;
import nars.*;
import nars.concept.ActionConcept;
import nars.conceptualize.DefaultConceptBuilder;
import nars.index.term.map.CaffeineIndex;
import nars.nar.Default;
import nars.time.RealTime;
import nars.video.CameraGasNet;
import nars.video.PixelBag;
import nars.video.Scale;
import nars.video.SwingCamera;

import java.io.File;
import java.io.IOException;

import static java.lang.Runtime.getRuntime;
import static nars.Op.*;

public class Arkancide extends NAgentX {

    static boolean cam = true;

    private final float paddleSpeed = 1f;


    final int visW = 40;
    final int visH = 24;

    //final int afterlife = 60;

    float maxPaddleSpeed;


    final Arkanoid noid;

    private float prevScore;

    public static void main(String[] args) throws IOException {
        Param.DEBUG = false;

        //runRT(Arkancide::new);
        //nRT(Arkancide::new, 25, 5);

        NAR nar = runRT((NAR n) -> {

            Arkancide a = null;
            try {
                a = new Arkancide(n, cam);

                Default m = new Default(256, 48, 1, 2, n.random,
                        new CaffeineIndex(new DefaultConceptBuilder(), 2048, false, null),
                        new RealTime.DSHalf().dur(1f));
                float metaLearningRate = 0.9f;
                m.confMin.setValue(0.02f);
                m.goalConfidence(metaLearningRate);
                m.termVolumeMax.setValue(9);

                MetaAgent metaT = new MetaAgent(a, m); //init before loading from file
                metaT.trace = true;
                metaT.init();

                String META_PATH = "/tmp/meta.nal";
                try {  m.input(new File(META_PATH)); } catch (IOException e) {                }
                getRuntime().addShutdownHook(new Thread(()->{
                    try { m.output(new File(META_PATH), (x) -> {
                        if (x.isBeliefOrGoal() && !x.isDeleted() && (x.op()==IMPL || x.op()==Op.EQUI || x.op() == CONJ)) {
                            //Task y = x.eternalized();
                            //return y;
                            return x;
                        }
                        return null;
                    }); } catch (IOException e) {  e.printStackTrace(); }
                }));


                n.onCycle(metaT.nar::cycle);
                //metaT.nar.log();

            } catch (Narsese.NarseseException e) {

            }

            return a;

        }, 17, 1, -1);


//        nar.forEachActiveConcept(c -> {
//            c.forEachTask(t -> {
//                System.out.println(t);
//            });
//        });

        //IO.saveTasksToTemporaryTextFile(nar);

        //System.out.println(ts.db.getInfo());

        //ts.db.close();

        //nar.beliefConfidence(0.75f);
        //nar.goalConfidence(0.75f);
    }


    public Arkancide(NAR nar, boolean cam) throws Narsese.NarseseException {
        super($.the("noid"), nar);

        //nar.derivedEvidenceGain.setValue(1f);


        noid = new Arkanoid(!cam) {
            @Override
            protected void die() {
                //nar.time.tick(afterlife); //wont quite work in realtime mode
                super.die();
            }
        };

        maxPaddleSpeed = 15 * Arkanoid.BALL_VELOCITY;

        //float resX = Math.max(0.01f, 1f/visW); //dont need more resolution than 1/pixel_width
        //float resY = Math.max(0.01f, 1f/visH); //dont need more resolution than 1/pixel_width

        if (cam) {
            senseCamera("noid", noid, visW, visH);
            //senseCameraRetina("noid", noid, visW/2, visH/2, (v) -> $.t(v, alpha));
            //new CameraGasNet($.the("camF"),new Scale(new SwingCamera(noid), 80, 80), this, 64);
        } else {
            //nar.termVolumeMax.set(12);
            senseNumber( "x(paddle)", new FloatPolarNormalized(()->noid.paddle.x, noid.getWidth()/2));//.resolution(resX);
            senseNumber( "x(ball)", new FloatPolarNormalized(()->noid.ball.x, noid.getWidth()/2));//.resolution(resX);
            senseNumber( "y(ball)", new FloatPolarNormalized(()->noid.ball.y, noid.getHeight()/2));//.resolution(resY);
            senseNumber("vx(ball)", new FloatPolarNormalized(()->noid.ball.velocityX));
            senseNumber("vy(ball)", new FloatPolarNormalized(()->noid.ball.velocityY));
        }

        /*action(new ActionConcept( $.func("dx", "paddleNext", "noid"), nar, (b, d) -> {
            if (d!=null) {
                paddleSpeed = Util.round(d.freq(), 0.2f);
            }
            return $.t(paddleSpeed, nar.confidenceDefault('.'));
        }));*/
        action(new ActionConcept( $.func("nx", "paddle"), nar, (b, d) -> {


            float pct;
            if (d != null) {
                pct = noid.paddle.moveTo(d.freq(), paddleSpeed * maxPaddleSpeed);// * d.conf());
            } else {
                pct = noid.paddle.x / Arkanoid.SCREEN_WIDTH; //unchanged
            }
            return $.t(pct, nar.confidenceDefault(BELIEF));
            //return null; //$.t(0.5f, alpha);
        })/*.feedbackResolution(resX)*/);

        //nar.log();
//        predictors.add( (MutableTask)nar.input(
//                //"(((noid) --> #y) && (add(#x,1) <-> #y))?"
//                "((cam --> ($x,$y)) && (camdiff($x,$y) --> similaritree($x,$y))). %1.00;0.99%"
//        ).get(0) );

    }

    @Override
    protected float act() {
        float nextScore = noid.next();
        float reward = Math.max(-1f, Math.min(1f,nextScore - prevScore));
        this.prevScore = nextScore;
        if (reward == 0)
            return Float.NaN;
        return reward;
    }


}