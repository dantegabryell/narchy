package nars.concept.action;

import nars.NAR;
import nars.Param;
import nars.Task;
import nars.concept.action.curiosity.Curiosity;
import nars.concept.action.curiosity.CuriosityGoalTable;
import nars.concept.action.curiosity.CuriosityTask;
import nars.control.channel.CauseChannel;
import nars.control.proto.Remember;
import nars.table.BeliefTable;
import nars.table.BeliefTables;
import nars.table.dynamic.SensorBeliefTables;
import nars.table.dynamic.SeriesBeliefTable;
import nars.table.eternal.EternalTable;
import nars.table.temporal.RTreeBeliefTable;
import nars.table.temporal.TemporalBeliefTable;
import nars.task.ITask;
import nars.task.signal.SignalTask;
import nars.task.util.Answer;
import nars.term.Term;
import nars.truth.Truth;
import nars.truth.polation.TruthPolation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static nars.time.Tense.TIMELESS;


/**
 * ActionConcept which is driven by Goals that are interpreted into feedback Beliefs
 */
public class AbstractGoalActionConcept extends ActionConcept {

//    private static final Logger logger = LoggerFactory.getLogger(AbstractGoalActionConcept.class);

    @Nullable private Curiosity curiosity = null;

    private final CuriosityGoalTable curiosityTable;

    /** current calculated goalTask */
    protected @Nullable Truth actionTruth;

    /** truth calculated (in attempt to) excluding curiosity */
    protected @Nullable Truth actionDex;

    /** latches the last non-null actionDex, used for echo/sustain */
    public @Nullable Truth lastActionDex;

    protected final CauseChannel<ITask> in;

    public AbstractGoalActionConcept(Term term,  NAR n) {
        this(term,
                new RTreeBeliefTable(),
                n);
    }

    protected AbstractGoalActionConcept(Term term, BeliefTable goals, NAR n) {
        super(term, new SensorBeliefTables(term, true, n.conceptBuilder),
                new BeliefTables(goals),
                n);

        ((BeliefTables)goals()).tables.add(curiosityTable = new CuriosityGoalTable(term, 64));

        in = newChannel(n);
    }

    protected CauseChannel<ITask> newChannel(NAR n) {
        return n.newChannel(this);
    }

    public AbstractGoalActionConcept curiosity(Curiosity curiosity) {
        this.curiosity = curiosity;
        return this;
    }

    @Override
    public float dexterity() {
        Truth t = this.actionDex;
        return t!=null ? t.conf() : 0;
    }


    /** in cycles; controls https://en.wikipedia.org/wiki/Legato vs. https://en.wikipedia.org/wiki/Staccato */
    float actionSustainDursDex =
            //0;
            //0.5f;
            1;

    float actionSustainDursCuri =
            //0;
            0.5f;
            //1f;

//    public AbstractGoalActionConcept actionDur(int actionDur) {
//        this.actionSustain = actionDur;
//        return this;
//    }


    static final Predicate<Task> withoutCuriosity = t -> !(t instanceof CuriosityTask) && !t.isEternal();  /* filter curiosity tasks? */

    @Override
    public void update(long prev, long now, long next, NAR n) {



        //TODO mine truthpolation .stamp()'s and .cause()'s for clues




        int dur = n.dur();
        //long s = prev, e = now;
        long s = prev, e = now + dur/2;
        //long s = now - dur, e = now;
        //long s = now - dur/2, e = now + dur/2;
        //long s = now - dur, e = now + dur;
        //long s = now, e = next;
        //long s = now - dur/2, e = next - dur/2;
        //long s = now - dur, e = next - dur;
        //long s = prev, e = next;
        //long agentDur = (now - prev);
        //long s = now - agentDur/2, e = now + agentDur/2;
        //long s = now - dur/2, e = now + dur/2;




        int limit = Answer.TASK_LIMIT_DEFAULT * 2;

//        long recent =
//                //now - dur*2;
//                prev;

        Predicate<Task> fil =
                withoutCuriosity;
                //Answer.filter(withoutCuriosity, (t) -> t.endsAfter(recent)); //prevent stronger past from overriding weaker future

        try(Answer a = Answer.
                relevance(true, limit, s, e, term, fil, n)) {


            @Nullable TemporalBeliefTable temporalTable = ((BeliefTables) goals()).tableFirst(TemporalBeliefTable.class);
            if (temporalTable!=null)
                a.match(temporalTable);
            @Nullable EternalTable eternalTable = ((BeliefTables) goals()).tableFirst(EternalTable.class);
            if (eternalTable!=null)
                a.match(eternalTable);

            TruthPolation organic = a.truthpolation(Math.round(actionSustainDursDex*dur));

            if (organic != null) {
                actionDex = organic.filtered().truth();
                if (actionDex!=null) {
                    lastActionDex = actionDex;
                }
            } else {
                actionDex = null;
            }
        }


        Truth actionCuri = curiosity.curiosity(this);

        Curiosity.CuriosityInjection curiosityInject = null;
        if (actionCuri!=null) {
            curiosityInject = Curiosity.CuriosityInjection.Override;

            Truth curiDithered = actionCuri.ditherFreq(resolution().floatValue()).dithered(n);
            if (curiDithered != null) {

                actionCuri = curiDithered;

                //pre-load curiosity for the future
                if (curiosity.goal.getOpaque()) {
                    long lastCuriosity = curiosityTable.series.end();
                    long curiStart = lastCuriosity != TIMELESS ? Math.max(s, lastCuriosity + 1) : s;
                    long curiEnd = Math.max(curiStart, e);
                    in.input(
                            curiosity(actionCuri /*goal*/, curiStart, curiEnd, n)
                    );
                }


            }
        } else {
            curiosityInject = curiosity.injection.get();

            //use existing curiosity
            @Nullable CuriosityGoalTable curiTable = ((BeliefTables) goals()).tableFirst(CuriosityGoalTable.class);
            try (Answer a = Answer.
                    relevance(true, 1, s, e, term, null, n).match(curiTable)) {
                TruthPolation curi = a.truthpolation(Math.round(actionSustainDursCuri * dur));
                if (curi != null) {
                    actionCuri = curi.filtered().truth();
                } else
                    actionCuri = null;
            }
        }

        if (actionDex != null || actionCuri != null) {

            actionTruth = curiosityInject.inject(actionDex, actionCuri);
        } else {
            actionTruth = null;
        }

        //System.out.println(actionTruth + " " + actionDex);

    }


    @Override
    public void add(Remember r, NAR n) {

        if (r.input instanceof CuriosityTask) {
            //intercept curiosity goals for the curiosity table
            curiosityTable.add(r, n);
        } else {
            super.add(r, n);
        }

    }

    long[] eviShared = null;
    @Nullable SignalTask curiosity(Truth goal, long pStart, long pEnd, NAR n) {
        long[] evi;
        if (Param.ALLOW_REVISION_OVERLAP_IF_DISJOINT_TIME) {
            if (eviShared == null)
                eviShared = n.evidence();
            evi = eviShared;
        } else {
            evi = n.evidence();
        }

        SignalTask curiosity = new CuriosityTask(term, goal, n, pStart, pEnd, evi);
        attn.ensure(curiosity, attn.elementPri(n));
        return curiosity;
    }

    @Deprecated @Nullable public SeriesBeliefTable.SeriesRemember feedback(@Nullable Truth f, long now, long next, NAR nar) {

        SeriesBeliefTable.SeriesRemember r = ((SensorBeliefTables) beliefs()).add(f, now, next,
                this, nar.dur(), nar);

        if (r!=null)
            attn.ensure(r.input, attn.elementPri(nar));

        return r;
    }


    public Truth actionTruth() {
        return actionTruth;
    }


}
