package nars;

import jcog.data.list.FasterList;
import jcog.random.XoRoShiRo128PlusRandom;
import nars.attention.impl.DefaultAttention;
import nars.concept.Concept;
import nars.concept.util.ConceptAllocator;
import nars.concept.util.ConceptBuilder;
import nars.concept.util.DefaultConceptBuilder;
import nars.derive.Derivers;
import nars.derive.impl.BatchDeriver;
import nars.exe.Exec;
import nars.exe.UniExec;
import nars.index.concept.ConceptIndex;
import nars.index.concept.SimpleConceptIndex;
import nars.op.stm.STMLinkage;
import nars.time.Time;
import nars.time.clock.CycleTime;
import nars.time.clock.RealTime;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static jcog.Util.curve;

/**
 * NAR builder
 */
public class NARS {

    public final NAR get() {
        NAR n = new NAR(index.get(), exec.get(),
                new DefaultAttention(),
                time, rng.get(), conceptBuilder.get());
        step.forEach(x -> x.accept(n));
        n.synch();
        return n;
    }

    protected Supplier<ConceptIndex> index;

    protected Time time;

    protected Supplier<Exec> exec;

    protected Supplier<Random> rng;

    //protected Supplier<ActiveConcepts> attn;

    protected Supplier<ConceptBuilder> conceptBuilder;


    /**
     * applied in sequence as final step before returning the NAR
     */
    protected final List<Consumer<NAR>> step = new FasterList<>(8);


    public NARS index(@NotNull ConceptIndex concepts) {
        this.index = () -> concepts;
        return this;
    }

    public NARS time(@NotNull Time time) {
        this.time = time;
        return this;
    }

    public NARS exe(@NotNull Exec exe) {
        this.exec = () -> exe;
        return this;
    }

    public NARS concepts(ConceptBuilder cb) {
        this.conceptBuilder = () -> cb;
        return this;
    }

//    public NARS attention(Supplier<ActiveConcepts> sa) {
//        this.attn = sa;
//        return this;
//    }
    /**
     * adds a deriver with the standard rules for the given range (inclusive) of NAL levels
     */
    @Deprecated public NARS withNAL(int minLevel, int maxLevel) {
        return then((n)->
                new BatchDeriver(Derivers.nal(n, minLevel, maxLevel))
        );
    }
    /**
     * generic defaults
     */
    @Deprecated
    public static class DefaultNAR extends NARS {


        public DefaultNAR(int nal, boolean threadSafe) {

            if (threadSafe)
                index =
                        //() -> new CaffeineIndex(64 * 1024)
                        () -> new SimpleConceptIndex(64 * 1024, true)
            ;

            if (nal > 0)
                withNAL(1, nal);

            if (nal >= 7) {
                then((nn)->new STMLinkage(nn, 1));
            }

            then((n)->{

                n.termVolumeMax.set(26);

                n.attn.activating.conceptActivationRate.set(1/100f);

                n.beliefPriDefault.set(0.5f);
                n.goalPriDefault.set(0.5f);
                n.questionPriDefault.set(0.25f);
                n.questPriDefault.set(0.25f);


            });
        }

    }

    /**
     * defaults
     */
    public NARS() {

        index = () ->
                new SimpleConceptIndex(1024)
                //new TemporaryConceptIndex()
        ;

        time = new CycleTime();

        exec = () -> new UniExec();

        rng = () ->
                new XoRoShiRo128PlusRandom(1);

//        attention(()->new ActiveConcepts(96));

        conceptBuilder = ()->new DefaultConceptBuilder(
                new ConceptAllocator(
                        //beliefs ete
                        curve(Concept::volume,
                        1, 8,
                                12, 5,
                                18, 3
                        ),
                        //beliefs tmp
                        curve(Concept::volume,
                                1, 64,
                                16, 32,
                                32, 8
                        ),
                        //goals ete
                        curve(Concept::volume,
                                1, 4,
                                16, 3,
                                32, 2
                        ),
                        //goals tmp
                        curve(Concept::volume,
                                1, 64,
                                16, 32,
                                32, 8
                        ),
                        //questions
                        curve(Concept::volume,
                          1, 8,
                                12, 6,
                                24, 4
                        ),
                        //quests
                        curve(Concept::volume,
                                1, 8,
                                12, 6,
                                24, 4
                        ),
                        //tasklinks
                        curve(Concept::volume,
                                1, 48,
                                24,32,
                                48,8
                        ))
        );


    }

    /**
     * temporary, disposable NAR. safe for single-thread access only.
     * full NAL8 with STM Linkage
     */
    public static NAR tmp() {
        return tmp(8);
    }


    /**
     * temporary, disposable NAR. useful for unit tests or embedded components
     * safe for single-thread access only.
     *
     * @param nal adjustable NAL level. level >= 7 include STM (short-term-memory) Linkage plugin
     */
    public static NAR tmp(int nal) {
        return new DefaultNAR(nal, false).get();
    }

    /**
     * single-thread, limited to NAL6 so it should be more compact than .tmp()
     */
    public static NAR tmpEternal() {
        return new DefaultNAR(6, false).get();
    }

    /**
     * single thread but for multithread usage:
     * unbounded soft reference index
     */
    public static NAR threadSafe() {
        return threadSafe(8);
    }
    
    public static NAR threadSafe(int level) {
        NARS d = new DefaultNAR(level, true)
                .time(new RealTime.CS().durFPS(25f));

        d.rng = ()->new XoRoShiRo128PlusRandom(System.nanoTime());

         return d.get();
    }


    /** milliseconds realtime */
    public static NARS realtime(float durFPS) {
        return new DefaultNAR(0, true).time(new RealTime.MS(true).durFPS(durFPS));
    }

    /**
     * provides only low level functionality.
     * an empty deriver, but allows any kind of term
     */
    public static NAR shell() {
        return tmp(0);
    }

    public NARS memory(String s) {
        return then(n -> {
            File f = new File(s);

            try {
                n.inputBinary(f);
            } catch (FileNotFoundException ignored) {
                
            } catch (IOException e) {
                n.logger.error("input: {} {}", s, e);
            }

            Runnable save = () -> {
                try {
                    n.outputBinary(f, false);
                } catch (IOException e) {
                    n.logger.error("output: {} {}", s, e);
                }
            };
            Runtime.getRuntime().addShutdownHook(new Thread(save));
        });
    }

    /**
     * adds a post-processing step before ready NAR is returned
     */
    public NARS then(Consumer<NAR> n) {
        step.add(n);
        return this;
    }



}
