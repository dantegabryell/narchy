package nars;

import jcog.Util;
import jcog.math.FloatRange;
import jcog.math.FloatRangeRounded;
import jcog.math.IntRange;
import jcog.math.Range;
import jcog.pri.ScalarValue;
import jcog.pri.op.PriMerge;
import jcog.util.FloatFloatToFloatFunction;
import nars.task.util.TaskBuffer;
import nars.term.atom.Atom;
import nars.term.util.builder.MemoizingTermBuilder;
import nars.truth.polation.LinearTruthPolation;
import nars.truth.polation.TruthPolation;

import java.util.function.Predicate;

import static nars.Op.*;

/**
 * NAR Parameters
 */
public abstract class Param {



    static {
        Op.terms =
                //HeapTermBuilder.the;

                //new InterningTermBuilder();

                new MemoizingTermBuilder();

//                new VerifyingTermBuilder(
//                    new MemoizingTermBuilder(),
//                    new VerifyingTermBuilder(
//                            new MemoizingTermBuilder() //new InterningTermBuilder()
//                            ,
//                            HeapTermBuilder.the
//                    )
//                );
    }

    public static final boolean FILTER_SIGNAL_TABLE_TEMPORAL_TASKS = true;



    public static final boolean SHUFFLE_TERMUTES = true; //this may be dangerous



    public static final boolean ALLOW_REVISION_OVERLAP_IF_DISJOINT_TIME = false;
    public static final boolean INPUT_PREMISE_ANSWER_BELIEF = false;

    public static final boolean DYNAMIC_TRUTH_STAMP_OVERLAP_FILTER = true;


    /** in Revision.dtDiff measurement */
    public static final float TRUTHPOLATION_INTERMPOLATION_THRESH =
            //0.5f;
            1f;

    /**
     * when merging dt's, ratio of the maximum difference in dt allowed
     * ratio of the dt difference compared to the smaller dt of the two being merged
     * probably values less than 0.5 are safe
     */
    public final FloatRange intermpolationRangeLimit = new FloatRange(
            //0.5f
            1f
            , 0, 1);


    public static final int TaskLinkSpreadDefault =
            //32;
            //16;
            //12;
            //10;
            8;
            //7;
            //6;
            //5;
            //2;
            //1;

//    public static final int TERM_BYTE_KEY_CACHED_BELOW_VOLUME = 8;
    //public static final int SUBTERM_BYTE_KEY_CACHED_BELOW_VOLUME = 10; //TODO

    public static final int SIGNAL_BELIEF_TABLE_SERIES_SIZE = 512;
//    public static final int CURIOSITY_BELIEF_TABLE_SERIES_SIZE = 64;




    /** can produce varieties of terms with dt below the dithered threshold time */
    public static final boolean ALLOW_UNDITHERED_DT_IF_DITHERED_FAILS = false;

    public static final boolean VOLMAX_RESTRICTS_INPUT = false;
    public static final boolean VOLMAX_RESTRICTS_EVAL = true;


    public static boolean ETERNALIZE_BELIEF_PROJECTED_IN_DERIVATION = true;

    /** if ETERNALIZE_BELIEF_PROJECTED_IN_DERIVATION is true, then this is tested: */
    public static final Predicate<Op> eternalizeInDerivation = (o) -> {
        return true;
        //return o == IMPL;
    };

//    public static final boolean ETERNALIZE_BELIEF_PROJECTED_FOR_GOAL_DERIVATION = false;

    /** whether INT atoms can name a concept directly */
    public static final boolean INT_CONCEPTUALIZABLE = false;


    public static final boolean OVERLAP_DOUBLE_SET_CYCLIC = true;

//    /** durs surrounding a derived temporal goal with one eternal (of two) parent tasks */
//    public static final float GOAL_PROJECT_TO_PRESENT_RADIUS_DURS = 1;

    /** TODO needs tested whether recursive Unification inherits TTL */
    public static final int EVALUATION_TTL = 32;

    /** within how many durations a difference in dt is acceptable for term unification */
    public static final float UNIFY_DT_TOLERANCE_DUR_FACTOR = 1f;

//    public static final boolean LINK_VARIABLE_UNIFIED_PREMISE = false;

    public static final int TASK_EVAL_FORK_LIMIT = 8;
    public static final int TASK_EVAL_TRY_LIMIT = TASK_EVAL_FORK_LIMIT*2;

    /** can be > 1 */
    public static final float ANSWER_COMPLETENESS = 1f;



//    /**
//     * warning: can interfere with expected test results
//     */
//    public static boolean ETERNALIZE_FORGOTTEN_TEMPORALS = false;


    public static boolean STRONG_COMPOSITION = false;



    public static final boolean FILTER_SIMILAR_DERIVATIONS = true;
    public static final boolean DEBUG_SIMILAR_DERIVATIONS = false;


    /**
     * use this for advanced error checking, at the expense of lower performance.
     * it is enabled for unit tests automatically regardless of the value here.
     */
    public static boolean DEBUG;
    public static boolean DEBUG_EXTRA = false;
    public static boolean DEBUG_ENSURE_DITHERED_TRUTH = false;
    public static boolean DEBUG_ENSURE_DITHERED_OCCURRENCE = false;
    public static boolean DEBUG_ENSURE_DITHERED_DT = false;

    public static final PriMerge conceptMerge =
            PriMerge.plus;
            //PriMerge.max;


    public static final PriMerge tasklinkMerge =
            //PriMerge.max;
            PriMerge.plus;
            //PriMerge.or;
            //PriMerge.avgGeoFast;

//    /**
//     * for equivalent tasks
//     */
//    public static final PriMerge taskEquivalentMerge =
//            PriMerge.max;
    //PriMerge.avg;
    //PriMerge.avgGeoSlow;

    /**
     * budget factor for combining 2 tasks in derivation
     * ex: double-premise derivations which depends on the task and belief budget
     * priority calculation here currently depends on a commutive and associaive function
     */
    public static final FloatFloatToFloatFunction DerivationPri =
        (t,b)->Util.unitize(t+b);
        //Math::max;
        //Math::min;
        //Util::and;
        //Util::or;
        //Util::mean;



    public static final PriMerge taskMerge = PriMerge.max;



    /**
     * maximum time (in durations) that a signal task can latch its last value before it becomes unknown
     */
    public final static float SIGNAL_STRETCH_DUR = 256;

    public final static float SIGNAL_LATCH_DUR =
            //0.5f;
            //1f;
            2f;


    /**
     * NAgent happiness automatic gain control time parameter
     * TODO verify this is applied based on time, not iterations
     */
    public final static float HAPPINESS_RE_SENSITIZATION_RATE = 0.0002f;
    public final static float HAPPINESS_RE_SENSITIZATION_RATE_FAST = 0.0004f;





    public final TaskBuffer input =
            //new DerivedTasks.DerivedTasksMap(4096);
            new TaskBuffer.BagTasksBuffer(256, 0.2f);
            //new TaskBuffer.BagPuncTasksBuffer(1024, 0.1f);

    public static final boolean INPUT_BUFFER_PRI_BACKPRESSURE = true;

    /**
     * creates instance of the default truthpolation implementation
     */
    public static TruthPolation truth(long start, long end, int dur) {
        return new LinearTruthPolation(start, end, dur);
        //return new FocusingLinearTruthPolation(start, end, dur);
    }

//    /**
//     * provides a start,end pair of time points for the current focus given the current time and duration
//     */
//    public final long[] timeFocus() {
//        return timeFocus(time());
//    }
//
//    public final long[] timeFocus(long when) {
//        return timeFocus(when, dur());
//    }
//
//    //TODO void timeFocus(when, dur, long[] store)
//
//    public final long[] timeFocus(long when, float dur) {
//        if (when == ETERNAL)
//            return new long[]{ETERNAL, ETERNAL};
//
//        if (when == XTERNAL) {
//            throw new RuntimeException();
//        }
//
//        int f = Math.round(dur * timeFocus.floatValue());
//        int ditherCycles = dtDither();
//        long from = Tense.dither(when - f, ditherCycles);
//        long to = Tense.dither(when + f, ditherCycles);
//        return new long[]{from, to};
//    }

    /**
     * TTL = 'time to live'
     */
    public final IntRange deriveBranchTTL = new IntRange(2 * TTL_MIN, TTL_MIN, 64 * TTL_MIN );
    public final IntRange subUnifyTTLMax = new IntRange( 4, 1, 32);
    public final IntRange matchTTL = new IntRange(6, 1, 32);

    /**
     * for NALTest's: extends the time all unit tests are allowed to run for.
     * normally be kept to 1 but for debugging this may be increased to find what tests need more time
     */
    public static final float TEST_TIME_MULTIPLIER = 2f;


    @Range(min = 1, max = 32)
    public static final int TEMPORAL_SOLVER_ITERATIONS = 3;


    /**
     * cost of attempting a unification
     */
    @Range(min = 0, max = 64)
    public static final int TTL_UNIFY = 1;

    @Range(min = 0, max = 64)
    public static final int TTL_BRANCH = 1;

    /**
     * cost of executing a termute permutation
     */
    @Range(min = 0, max = 64)
    public static final int TTL_MUTATE = 1;

    /**
     * cost of a successful task derivation
     */
    @Range(min = 0, max = 64)
    public static final int TTL_DERIVE_TASK_SUCCESS = 5;

    /**
     * cost of a repeat (of another within the premise's batch) task derivation
     */
    @Range(min = 0, max = 64)
    public static final int TTL_DERIVE_TASK_REPEAT = 2;


    /**
     * cost of a task derived, but too similar to one of its parents
     */
    @Range(min = 0, max = 64)
    public static final int TTL_DERIVE_TASK_SAME = 2;


    /** whether to decompose conjunction sequences to each event, regardless of a conjunction's sub-conjunction structure */
    public static final boolean TEMPLATE_LINKER_DECOMPOSE_CONJ_SEQ_EVENTS = true;

    /**
     * cost of a failed/aborted task derivation
     */
    @Range(min = 0, max = 64)
    public static final int TTL_DERIVE_TASK_FAIL = 1;
    @Range(min = 0, max = 64)
    public static final int TTL_DERIVE_TASK_UNPRIORITIZABLE = TTL_DERIVE_TASK_FAIL;


    /**
     * estimate
     */
    @Deprecated
    private static final int TTL_MIN =
            (Param.TTL_UNIFY * 2) +
                    (Param.TTL_BRANCH * 1) + Param.TTL_DERIVE_TASK_SUCCESS;




    public final FloatRange taskLinkActivation = new FloatRange(1f, 0, 2f);


    /**
     * how many durations above which to dither dt relations to dt=0 (parallel)
     * set to zero to disable dithering.  typically the value will be 0..1.0.
     * TODO move this to Time class and cache the cycle value rather than dynamically computing it
     */
    public final IntRange timeResolution = new IntRange(1, 1, 1024);

    /**
     * number of time units (cycles) to dither into
     */
    public int dtDither() {
        return timeResolution.intValue();
    }

    abstract int dur();


    abstract long time();


//    /**
//     * abs(term.dt()) safety limit for non-dternal/non-xternal temporal compounds
//     * exists for debugging
//     */
//    @Deprecated
//    public static int DT_ABS_LIMIT =
//            Integer.MAX_VALUE / 16384;


    /**
     * absolute limit for constructing terms in any context in which a NAR is not known, which could provide a limit.
     * typically a NAR instance's 'compoundVolumeMax' parameter will be lower than this
     */
    public static final int COMPOUND_VOLUME_MAX = 127;

    /**
     * limited because some subterm paths are stored as byte[]. to be safe, use 7-bits
     */
    public static final int COMPOUND_SUBTERMS_MAX = 127;


    /**
     * hard upper-bound limit on Compound term complexity;
     * if this is exceeded it may indicate a recursively
     * malformed term due to a serious inference bug
     */
    public final IntRange termVolumeMax = new IntRange(COMPOUND_VOLUME_MAX, 0, COMPOUND_VOLUME_MAX);


    static Atom randomSelf() {
        return (Atom) $.quote(/*"I_" + */Util.uuid64());
    }

    /**
     * Evidential Horizon, the amount of future evidence to be considered
     */
    public static final float HORIZON = 1f;


    public static final int MAX_INTERNED_VARS = 32;

    /**
     * how many INT terms are canonically interned/cached. [0..n)
     */
    public final static int MAX_INTERNED_INTS = 64;


    /**
     * Maximum length of the evidental base of the Stamp, a power of 2
     * TODO IntRange
     */
    public static final int STAMP_CAPACITY = 16;

    /** TODO make this NAR-specific */
    public static final int CAUSE_MAX = 32;
    public static final IntRange causeCapacity = new IntRange(32, 1, CAUSE_MAX);


    public final static int UnificationStackMax = 128;


    public static final boolean DEBUG_TASK_LOG = true;

    /**
     * internal granularity which truth components are rounded to
     */
    public static final float TRUTH_EPSILON = 0.01f;
    public static final float TRUTH_MAX_CONF = 1f - TRUTH_EPSILON;
    public static final float TRUTH_MIN_EVI =
                        //c2wSafe(TRUTH_EPSILON);
                        //ScalarValue.EPSILON;
                        Float.MIN_NORMAL;


    /**
     * how precise unit test results must match expected values to pass
     */
    public static final float TESTS_TRUTH_ERROR_TOLERANCE = TRUTH_EPSILON * 2;


    /**
     * truth confidence threshold necessary to form tasks
     */
    public final FloatRange confMin = new FloatRange(TRUTH_EPSILON, TRUTH_EPSILON, TRUTH_MAX_CONF);

    /**
     * global truth frequency resolution by which reasoning is dithered
     */
    public final FloatRange freqResolution = new FloatRangeRounded(TRUTH_EPSILON, TRUTH_EPSILON, 1f, TRUTH_EPSILON);

    /**
     * global truth confidence resolution by which reasoning is dithered
     */
    public final FloatRange confResolution = new FloatRangeRounded(TRUTH_EPSILON, TRUTH_EPSILON, 1f, TRUTH_EPSILON) {
        @Override
        public void set(float value) {
            super.set(value);
            value = get();
            if (confMin.floatValue() < value)
                confMin.set(value);
        }
    };


    /**
     * computes the projected evidence at a specific distance (dt) from a perceptual moment evidence
     * with a perceptual duration used as a time constant
     * dt >= 0
     */
    public static float evi(float evi, long dt, long dur) {


        assert(dur > 0);

        int falloffDurs =
                1;
                //2; //nyquist
                //4;
                //dur;
                //8;
        return evi / (1.0f + (((float)dt) / (falloffDurs * dur)));
        //return evi / (1.0f +    Util.sqr(((float)dt) / (falloffDurs * dur)));
        //return evi / (1.0f +    Util.sqr(((float)dt) / dur)/falloffDurs);

        //return evi * Math.max(0, (1.0f - ((float)dt) / (falloffDurs * dur))); //constant time linear decay



        //return evi / (1.0f + ( Math.max(0,(dt-dur/2f)) / (dur)));

        //return evi / (1.0f + ( Math.max(0f,(dt-dur)) / (dur)));



        //return evi * 1/sqrt(Math.log(1+(Math.pow((dt/dur),3)*2))*(dt/dur)+1); //http://fooplot.com/#W3sidHlwZSI6MCwiZXEiOiIxLyhsb2coMSsoeCp4KngqMikpKih4KSsxKV4wLjUiLCJjb2xvciI6IiMyMTE1QUIifSx7InR5cGUiOjAsImVxIjoiMS8oMSt4KSIsImNvbG9yIjoiIzAwMDAwMCJ9LHsidHlwZSI6MTAwMCwid2luZG93IjpbIi0xLjg4NDM2OTA0NzQ3Njc5OTgiLCI4LjUxNTYzMDk1MjUyMzE2OCIsIi0yLjMxMTMwMDA4MTI0NjM4MTgiLCI0LjA4ODY5OTkxODc1MzU5OCJdLCJzaXplIjpbNjQ2LDM5Nl19XQ--
        //return (float) (evi / (1.0 + Util.sqr(((double)dt) / dur)));
        //return evi * 1/(Math.log(1+((dt/dur)*0.5))*(dt/dur)+1); //http://fooplot.com/#W3sidHlwZSI6MCwiZXEiOiIxLyhsb2coMSsoeCowLjUpKSooeCkrMSleMC41IiwiY29sb3IiOiIjMjExNUFCIn0seyJ0eXBlIjowLCJlcSI6IjEvKDEreCkiLCJjb2xvciI6IiMwMDAwMDAifSx7InR5cGUiOjEwMDAsIndpbmRvdyI6WyIyLjYzMDEyOTMyODgxMzU2ODUiLCIxOC44ODAxMjkzMjg4MTM1MzUiLCItMy45NTk4NDE5MDg3NzE5MTgiLCI2LjA0MDE1ODA5MTIyODA1NyJdLCJzaXplIjpbNjQ4LDM5OF19XQ--
        //return evi * (Util.tanhFast((-(((float)dt)/dur)+2))+1)/2; //http://fooplot.com/#W3sidHlwZSI6MCwiZXEiOiIodGFuaCgteCsyKSsxKS8yIiwiY29sb3IiOiIjMjExNUFCIn0seyJ0eXBlIjowLCJlcSI6IjEvKDEreCkiLCJjb2xvciI6IiMwMDAwMDAifSx7InR5cGUiOjEwMDAsInNpemUiOls2NDgsMzk4XX1d

        //return (float) (evi / (1.0 + Math.log(1 + ((double)dt) / dur)));




    }


    public final float confDefault(byte punctuation) {

        switch (punctuation) {
            case BELIEF:
                return beliefConfDefault.floatValue();

            case GOAL:
                return goalConfDefault.floatValue();

            default:
                throw new RuntimeException("Invalid punctuation " + punctuation + " for a TruthValue");
        }
    }


    /**
     * Default priority of input judgment
     */
    public final FloatRange beliefPriDefault = new FloatRange(0.5f, ScalarValue.EPSILONsqrt, 1f);

    /**
     * Default priority of input question
     */
    public final FloatRange questionPriDefault = new FloatRange(0.5f, ScalarValue.EPSILONsqrt, 1f);

    /**
     * Default priority of input judgment
     */
    public final FloatRange goalPriDefault = new FloatRange(0.5f, ScalarValue.EPSILONsqrt, 1f);

    /**
     * Default priority of input question
     */
    public final FloatRange questPriDefault = new FloatRange(0.5f, ScalarValue.EPSILONsqrt, 1f);


    public float priDefault(byte punctuation) {
        switch (punctuation) {
            case BELIEF:
                return beliefPriDefault.floatValue();

            case QUEST:
                return questPriDefault.floatValue();

            case QUESTION:
                return questionPriDefault.floatValue();

            case GOAL:
                return goalPriDefault.floatValue();

            case COMMAND:
                return 1;
        }
        throw new RuntimeException("Unknown punctuation: " + punctuation);
    }


    public final FloatRange beliefConfDefault = new FloatRange(0.9f, Param.TRUTH_EPSILON, 1f - Param.TRUTH_EPSILON);
    public final FloatRange goalConfDefault = new FloatRange(0.9f, Param.TRUTH_EPSILON, 1f - Param.TRUTH_EPSILON);



}
