package nars.experiment.arkanoid;


import com.google.common.collect.Lists;
import jcog.Util;
import jcog.data.FloatParam;
import nars.*;
import nars.concept.SensorConcept;
import nars.gui.Vis;
import nars.task.DerivedTask;
import nars.term.Compound;
import nars.term.Term;
import nars.term.atom.Atomic;
import nars.video.CameraSensor;
import nars.video.Scale;
import nars.video.SwingBitmap2D;
import spacegraph.SpaceGraph;

public class Arkancide extends NAgentX {

    static boolean numeric = true;
    static boolean cam = true;

    public final FloatParam ballSpeed = new FloatParam(2f, 0.1f, 6f);
    //public final FloatParam paddleSpeed = new FloatParam(2f, 0.1f, 3f);


    final int visW = 48;
    final int visH = 24;

    //final int afterlife = 60;

    final float maxPaddleSpeed;


    final Arkanoid noid;

    private float prevScore;

    public static void main(String[] args) {
        Param.DEBUG = true;

        //runRT(Arkancide::new);
        //nRT(Arkancide::new, 25, 5);

        NAR nar = runRT((NAR n) -> {

            Arkancide a = null;

            try {
                a = new Arkancide(n, cam, numeric);

                //a.trace = true;
                //((RealTime)a.nar.time).durSeconds(0.05f);
                //a.nar.log();

            } catch (Narsese.NarseseException e) {
                e.printStackTrace();
            }

            //new RLBooster(a, new HaiQAgent());


            return a;

        }, 20);


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


    public Arkancide(NAR nar, boolean cam, boolean numeric) throws Narsese.NarseseException {
        super((Atomic.the("noid")), nar);

        //nar.derivedEvidenceGain.setValue(1f);


        noid = new Arkanoid(true) {
            @Override
            protected void die() {
                //nar.time.tick(afterlife); //wont quite work in realtime mode
                super.die();
            }
        };


        maxPaddleSpeed = 40 * noid.BALL_VELOCITY;

        float resX = Math.max(0.01f, 0.5f / visW); //dont need more resolution than 1/pixel_width
        float resY = Math.max(0.01f, 0.5f / visH); //dont need more resolution than 1/pixel_width

        if (cam) {

            Scale sw = new Scale(new SwingBitmap2D(noid), visW, visH);
            CameraSensor cc = senseCamera("noid", sw, visW, visH)
                    .resolution(0.25f);
//            CameraSensor ccAe = senseCameraReduced($.the("noidAE"), sw, 16)
//                    .resolution(0.25f);

            //senseCameraRetina("noid", noid, visW/2, visH/2, (v) -> $.t(v, alpha));
            //new CameraGasNet($.the("camF"),new Scale(new SwingCamera(noid), 80, 80), this, 64);
        }


        if (numeric) {
            SensorConcept a = senseNumber("noid:px", (() -> noid.paddle.x / noid.getWidth())).resolution(resX);
            SensorConcept ab = senseNumber("noid:dx", (() -> Math.sqrt /* sharpen */(Math.abs(noid.ball.x - noid.paddle.x) / noid.getWidth())));
            SensorConcept b = senseNumber("noid:bx", (() -> (noid.ball.x / noid.getWidth()))).resolution(resX);
            SensorConcept c = senseNumber("noid:by", (() -> 1f - (noid.ball.y / noid.getHeight()))).resolution(resY);
            //SensorConcept d = senseNumber("noid:bvx", new FloatPolarNormalized(() -> noid.ball.velocityX)).resolution(0.25f);
            //SensorConcept e = senseNumber("noid:bvy", new FloatPolarNormalized(() -> noid.ball.velocityY)).resolution(0.25f);

            //experimental cheat
//            nar.input("--(paddle-ball):x! :|:",
//                      "--(ball-paddle):x! :|:"
//            );

            SpaceGraph.window(Vis.beliefCharts(100,
                    Lists.newArrayList(new Term[]{ab, a, b, c}),
                    nar), 600, 600);
            nar.onTask(t -> {
                if (t instanceof DerivedTask && t.isGoal()) {
                    if (t.term().equals(ab))
                        System.err.println(t.proof());
                }
            });
        }

        /*action(new ActionConcept( $.func("dx", "paddleNext", "noid"), nar, (b, d) -> {
            if (d!=null) {
                paddleSpeed = Util.round(d.freq(), 0.2f);
            }
            return $.t(paddleSpeed, nar.confidenceDefault('.'));
        }));*/
        Compound paddleControl = $.inh(Atomic.the("pxMove"), id);
//        actionUnipolarTransfer(paddleControl, (v) -> {
//            return noid.paddle.moveTo(v, maxPaddleSpeed);
//        });
        /*actionTriState*/
        actionBipolar
                (paddleControl, (s) -> {
//           switch (s) {
//               case 0:
//                   break;
//               case -1:
//               case 1:
                    if (s > 0 && Util.equals(noid.paddle.x, noid.getWidth(), 1f))
                        return 0f; //edge
                    if (s < 0 && Util.equals(noid.paddle.x, 0, 1f))
                        return 0f; //edge
                   noid.paddle.move( maxPaddleSpeed * s);
//                    break;
//           }
                    return s;

        });

//        Param.DEBUG = true;
//        nar.onTask(x -> {
//            if (x.isGoal()
//                    && !x.isInput() && (!(x instanceof ActionConcept.CuriosityTask))
//                    //&& x.term().equals(paddleControl)
//            ) {
//                System.err.println(x.proof());
//            }
//        });


//        actionUnipolar($.inh(Atomic.the("paddle"), Atomic.the("nx") ), (v) -> {
//            noid.paddle.moveTo(v, paddleSpeed.floatValue() * maxPaddleSpeed);
//            return true;
//        });
//        action(new ActionConcept( $.func("nx", "paddle"), nar, (b, d) -> {
//
//
//            float pct;
//            if (d != null) {
//                pct = noid.paddle.moveTo(d.freq(), paddleSpeed.floatValue() * maxPaddleSpeed);// * d.conf());
//            } else {
//                pct = noid.paddle.x / Arkanoid.SCREEN_WIDTH; //unchanged
//            }
//            return $.t(pct, nar.confidenceDefault(BELIEF));
//            //return null; //$.t(0.5f, alpha);
//        })/*.feedbackResolution(resX)*/);

        //nar.log();
//        predictors.add( (MutableTask)nar.input(
//                //"(((noid) --> #y) && (add(#x,1) <-> #y))?"
//                "((cam --> ($x,$y)) && (camdiff($x,$y) --> similaritree($x,$y))). %1.00;0.99%"
//        ).get(0) );

    }

    @Override
    protected float act() {
        noid.BALL_VELOCITY = ballSpeed.floatValue();
        float nextScore = noid.next();
        float reward = Math.max(-1f, Math.min(1f, nextScore - prevScore));
        this.prevScore = nextScore;
        //if (reward == 0) return Float.NaN;
        return reward;
    }


}

//                {
//                    Default m = new Default(256, 48, 1, 2, n.random,
//                            new CaffeineIndex(new DefaultConceptBuilder(), 2048, false, null),
//                            new RealTime.DSHalf().durSeconds(1f));
//                    float metaLearningRate = 0.9f;
//                    m.confMin.setValue(0.02f);
//                    m.goalConfidence(metaLearningRate);
//                    m.termVolumeMax.setValue(16);
//
//                    MetaAgent metaT = new MetaAgent(a, m); //init before loading from file
//                    metaT.trace = true;
//                    metaT.init();
//
//                    String META_PATH = "/tmp/meta.nal";
//                    try {
//                        m.input(new File(META_PATH));
//                    } catch (IOException e) {
//                    }
//                    getRuntime().addShutdownHook(new Thread(() -> {
//                        try {
//                            m.output(new File(META_PATH), (x) -> {
//                                if (x.isBeliefOrGoal() && !x.isDeleted() && (x.op() == IMPL || x.op() == Op.EQUI || x.op() == CONJ)) {
//                                    //Task y = x.eternalized();
//                                    //return y;
//                                    return x;
//                                }
//                                return null;
//                            });
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }));
//                n.onCycle(metaT.nar::cycle);
//                }

