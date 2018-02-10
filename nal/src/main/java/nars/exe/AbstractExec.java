package nars.exe;

import jcog.bag.Bag;
import jcog.bag.impl.CurveBag;
import jcog.bag.impl.hijack.PriorityHijackBag;
import jcog.event.On;
import nars.NAR;
import nars.Param;
import nars.concept.Concept;
import nars.control.Activate;

import java.util.HashMap;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * unified executor
 * concurrent, thread-safe. one central concept bag
 */
abstract public class AbstractExec extends Exec {

    private final int CAPACITY;

    public Bag<Activate, Activate> active;
    private On onCycle;
    //protected Baggie<Concept> active;

    protected AbstractExec(int capacity) {

        CAPACITY = capacity;
    }

    @Override
    protected void clear() {
        synchronized (this) {
            if (active != null)
                active.clear();
        }
    }

    @Override
    public void activate(Concept c, float activationApplied) {
        active.putAsync(new Activate(c, activationApplied * nar.conceptActivation.floatValue()));
        //active.put(c, activationApplied);
    }



    @Override
    public void fire(Predicate<Activate> each) {

        active.sample(nar.random(), each);

//        float forgetRate = nar.forgetRate.floatValue();
//        active.sample(nar.random(), (l) -> {
//            float pri = l.pri();
//            boolean cont = each.test(new Activate(l.get(), pri)); //TODO eliminate Activate class middle-man
//            //l.set(0.5f * pri); //TODO continuous rate forget based on pressure release
//            l.forget(forgetRate);
//            return cont;
//        });
//        active.depressurize();
    }

    @Override
    public void start(NAR nar) {

        synchronized (this) {
            assert (active == null && onCycle == null);

            active =
                    concurrent() ?

                            //                        new ConcurrentCurveBag<>(
                            //                                Param.activateMerge, new HashMap<>(CAPACITY*2),
                            //                                nar.random(), CAPACITY)

                            new PriorityHijackBag<>(Math.round(CAPACITY * 1.5f), 4) {

                                @Override
                                public Activate key(Activate value) {
                                    return value;
                                }

                                @Override
                                public void onAdd(Activate value) {
                                    value.id.state(nar.terms.conceptBuilder.awake());
                                }

                                @Override
                                public void onRemove(Activate value) {
                                    value.id.state(nar.terms.conceptBuilder.sleep());
                                }
                            }

                            :

                            new CurveBag<>(
                                    Param.activateMerge, new HashMap<>(CAPACITY),
                                    CAPACITY) {

                                @Override
                                public void onAdd(Activate value) {
                                    value.id.state(nar.terms.conceptBuilder.awake());
                                }

                                @Override
                                public void onRemove(Activate value) {
                                    value.id.state(nar.terms.conceptBuilder.sleep());
                                }
                            }
            ;

            super.start(nar);

            onCycle = nar.onCycle(this::update);
        }
    }

    private void update() {
        active.commit(active.forget(nar.forgetRate.floatValue()));
    }

    @Override
    public void stop() {
        synchronized (this) {
            if (onCycle!=null) {
                onCycle.off();
                onCycle = null;
            }
            if (active!=null) {
                active.clear();
                active = null;
            }
        }
    }


    @Override
    public Stream<Activate> active() {

        return active.stream();
        //return active.stream().map(x -> new Activate(x.getOne(), x.getTwo()));
    }

//    final int WINDOW_SIZE = 32;
//    final int WINDOW_RATIO = 8;
//    private final TopN<ITask> top = new TopN<>(new ITask[WINDOW_SIZE], this::pri);
//    int windowTTL = WINDOW_RATIO;
//
//    private BagSample top(ITask x) {
//
//        top.add(x);
//
//        if (--windowTTL <= 0) {
//            windowTTL = WINDOW_RATIO;
//            ITask t = top.pop();
//            if (t!=null)
//                exeSample((ITask) t);
//        }
//
//        return BagSample.Next;
//    }

//    public static void main(String... args) {
//        NARS n = NARS.realtime();
//        n.exe(new Execontinue());
//
////        new Loop(1f) {
////
////            @Override
////            public boolean next() {
////                System.out.println();
////                System.out.println( Joiner.on(" ").join(exe.plan) );
////                return true;
////            }
////        };
//
//        NAR nn = n.get();
//
//        try {
//            nn.log();
//            nn.input("a:b. b:c. c:d.");
//        } catch (Narsese.NarseseException e) {
//            e.printStackTrace();
//        }
//
//    }


}
