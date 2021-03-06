package nars.concept.util;

import jcog.WTF;
import jcog.pri.bag.Bag;
import nars.Op;
import nars.Task;
import nars.concept.Concept;
import nars.concept.NodeConcept;
import nars.concept.TaskConcept;
import nars.link.TermLinker;
import nars.subterm.Subterms;
import nars.table.BeliefTable;
import nars.table.eternal.EternalTable;
import nars.table.question.QuestionTable;
import nars.table.temporal.TemporalBeliefTable;
import nars.term.Compound;
import nars.term.Term;
import nars.term.Termed;
import nars.term.util.Image;
import nars.truth.dynamic.DynamicTruthModel;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static nars.Op.*;

/**
 * Created by me on 3/23/16.
 */
public abstract class ConceptBuilder implements BiFunction<Term, Termed, Termed> {

//    private final Map<Term, Conceptor> conceptors = new ConcurrentHashMap<>();

    public abstract QuestionTable questionTable(Term term, boolean questionOrQuest);

    public abstract BeliefTable newTable(Term t, boolean beliefOrGoal);

    public abstract EternalTable newEternalTable(Term c);

    public abstract TemporalBeliefTable newTemporalTable(Term c, boolean beliefOrGoal);

    public abstract Bag newLinkBag(Term term);

    private Concept taskConcept(final Term t) {

        BeliefTable B, G;


        DynamicTruthModel dmt = ConceptBuilder.dynamicModel(t);

        Bag L;
        if (dmt != null) {

            //2. handle dynamic truth tables
            B = dmt.newTable(t, true, this);
            G = goalable(t) ?
                    dmt.newTable(t, false, this) :
                    BeliefTable.Empty;
            L = dmt.newTaskLinkBag(t, this);
        } else {
//                //3. handle dynamic conceptualizers (experimental)
//                Term conceptor = Functor.func(t);
//                if (conceptor != Bool.Null) {
//                    @Nullable Conceptor cc = conceptors.get(conceptor);
//                    if (cc instanceof Conceptor) {
//
//                        Concept x = cc.apply(conceptor, Operator.args(t));
//                        if (x != null)
//                            return x;
//                    }
//                }

            //4. default task concept
            B = this.newTable(t, true);
            G = goalable(t) ? this.newTable(t, false) : BeliefTable.Empty;
            L = this.newLinkBag(t);
        }



        return new TaskConcept(t, B, G,
                this.questionTable(t, true), this.questionTable(t, false),
                this.termlinker(t),
                L);
    }


    protected abstract NodeConcept nodeConcept(final Term t);

//    public void on(Conceptor c) {
//        conceptors.put(c.term, c);
//    }


    private static final Predicate<Term> validDynamicSubterm = x -> Task.taskConceptTerm(x.unneg());


    private static boolean validDynamicSubterms(Subterms subterms) {
        return subterms.AND(validDynamicSubterm);
    }


    private static boolean validDynamicSubtermsAndNoSharedVars(Term conj) {
        Subterms conjSubterms = conj.subterms();
        if (validDynamicSubterms(conjSubterms)) {
            if (conjSubterms.hasAny(VAR_DEP)) {

                Map<Term, Term> varLocations = new UnifiedMap(conjSubterms.subs());

                return conj.eventsWhile((when, event) ->
                                !event.hasAny(VAR_DEP) ||
                                        event.recurseTerms(x -> x.hasAny(VAR_DEP),
                                                (possiblyVar, parent) ->
                                                        (possiblyVar.op() != VAR_DEP) ||
                                                                varLocations.putIfAbsent(possiblyVar, event) == null
                                                , null)

                        , 0, true, true, true, 0);
            }
            return true;
        }
        return false;
    }

    /**
     * returns the builder for the term, or null if the term is not dynamically truthable
     */
    @Nullable
    public static DynamicTruthModel dynamicModel(Term t) {

        if (t.hasAny(Op.VAR_QUERY.bit))
            return null; //TODO maybe this can answer query questions by index query

        switch (t.op()) {

            case INH:
                return dynamicInh(t);

            case SIM:
                //TODO NAL2 set identities?
                break;

//            //TODO not done yet
            case IMPL: {
                //TODO allow indep var if they are involved in (contained within) either but not both subj and pred
                if (t.hasAny(Op.VAR_INDEP))
                    return null;
                Term su = t.sub(0);
//                if (su.hasAny(Op.VAR_INDEP))
//                    return null;
                Term pu = t.sub(1);
//                if (pu.hasAny(Op.VAR_INDEP))
//                    return null;

                Op suo = su.op();
                //subject has special negation union case
                boolean subjDyn = (
                        suo == CONJ && validDynamicSubtermsAndNoSharedVars(su)
                                ||
                                suo == NEG && (su.unneg().op() == CONJ && validDynamicSubtermsAndNoSharedVars(su.unneg()))
                );
                boolean predDyn = (pu.op() == CONJ && validDynamicSubtermsAndNoSharedVars(pu));


                if (subjDyn && predDyn) {
                    //choose the simpler to dynamically calculate for
                    if (su.volume() <= pu.volume()) {
                        predDyn = false; //dyn subj
                    } else {
                        subjDyn = false; //dyn pred
                    }
                }

                if (subjDyn) {
                    if (suo == NEG) {
                        return DynamicTruthModel.DynamicSectTruth.UnionImplSubj;
                    } else {
                        return DynamicTruthModel.DynamicSectTruth.SectImplSubj;
                    }
                } else if (predDyn) {
                    //TODO infer union case if the subterms of the pred's conj are all negative
                    return DynamicTruthModel.DynamicSectTruth.SectImplPred;
                }

                break;
            }

            case CONJ:
                if (validDynamicSubtermsAndNoSharedVars(t))
                    return DynamicTruthModel.DynamicConjTruth.ConjIntersection;
                break;

//            case SECTe:
//                if (validDynamicSubterms(t.subterms()))
//                    return DynamicTruthModel.DynamicSectTruth.SectRoot;
//                break;

            case NEG:
                throw new RuntimeException("negation terms can not be conceptualized as something separate from that which they negate");
        }
        return null;
    }

    private static DynamicTruthModel dynamicInh(Term t) {

        //quick pre-test
        Subterms tt = t.subterms();
        if (tt.hasAny(Op.Sect | Op.PROD.bit)) {


            if ((tt.OR(s -> s.isAny(Op.Sect)))) {


                Term subj = tt.sub(0), pred = tt.sub(1);

                Op so = subj.op(), po = pred.op();


                if ((so == Op.SECTi) || (so == Op.SECTe)) {

                    //TODO move this to impl-specific test function
                    Subterms subjsubs = subj.subterms();
                    int s = subjsubs.subs();
                    for (int i = 0; i < s; i++) {
                        if (!validDynamicSubterm.test(INH.the(subjsubs.sub(i), pred)))
                            return null;
                    }

                    switch (so) {
                        case SECTi:
                            return DynamicTruthModel.DynamicSectTruth.SectSubj;
                        case SECTe:
                            return DynamicTruthModel.DynamicSectTruth.UnionSubj;
                    }


                }


                if (((po == Op.SECTi) || (po == Op.SECTe))) {


                    Compound cpred = (Compound) pred;
                    int s = cpred.subs();
                    for (int i = 0; i < s; i++) {
                        if (!validDynamicSubterm.test(INH.the(subj, cpred.sub(i))))
                            return null;
                    }

                    switch (po) {
                        case SECTi:
                            return DynamicTruthModel.DynamicSectTruth.UnionPred;
                        case SECTe:
                            return DynamicTruthModel.DynamicSectTruth.SectPred;
                    }
                }
            }
        }

        Term it = Image.imageNormalize(t);
        if (it != t)
            return DynamicTruthModel.ImageDynamicTruthModel;

        return null;
    }

    @Override
    public final Termed apply(Term x, Termed prev) {
        if (prev != null) {
            Concept c = ((Concept) prev);
            if (!c.isDeleted())
                return c;
        }

        return apply(x);
    }

    public final Termed apply(Term x) {

        Concept c = Task.taskConceptTerm(x) ? taskConcept(x) : nodeConcept(x);
        if (c == null)
            throw new WTF(x + " unconceptualizable");


        start(c);

        return c;
    }

    /**
     * called after constructing a new concept, or after a permanent concept has been installed
     */
    public void start(Concept c) {

    }

    abstract public TermLinker termlinker(Term term);

    /**
     * passes through terms without creating any concept anything
     */
    public static final ConceptBuilder NullConceptBuilder = new ConceptBuilder() {

//        @Override
//        public void on(Conceptor c) {
//            throw new UnsupportedOperationException();
//        }

        @Override
        public NodeConcept nodeConcept(Term t) {
            throw new UnsupportedOperationException();
        }


        @Override
        public TermLinker termlinker(Term term) {
            return TermLinker.NullLinker;
        }

        @Override
        public TemporalBeliefTable newTemporalTable(Term c, boolean beliefOrGoal) {
            return null;
        }

        @Override
        public BeliefTable newTable(Term t, boolean beliefOrGoal) {
            return BeliefTable.Empty;
        }

        @Override
        public EternalTable newEternalTable(Term c) {
            return null;
        }

        @Override
        public QuestionTable questionTable(Term term, boolean questionOrQuest) {
            return QuestionTable.Empty;
        }

        @Override
        public Bag newLinkBag(Term term) {
            return Bag.EMPTY;
        }
    };

}
