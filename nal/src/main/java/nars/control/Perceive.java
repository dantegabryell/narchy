package nars.control;

import jcog.WTF;
import nars.$;
import nars.NAR;
import nars.Task;
import nars.concept.Concept;
import nars.concept.Operator;
import nars.control.proto.TaskEvent;
import nars.task.ITask;
import nars.term.Functor;
import nars.term.Term;
import nars.term.atom.Bool;
import org.eclipse.collections.api.tuple.primitive.ObjectBooleanPair;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static nars.Op.*;
import static nars.term.atom.Bool.True;

/** transforms an input task into any smaller sub-tasks that constitute the perception process */
public enum Perceive { ;

    static final Logger logger = LoggerFactory.getLogger(Perceive.class);

    /** returns true if the task is acceptable */
    public static boolean tryPerceive(Task input, Term y, Collection<ITask> queue, NAR n) {

        if (y == Bool.Null) {
            //logger.debug("nonsense {}", input);
            return false;
        }

        Task t;
        if (!input.term().equals(y)) {
            byte punc = input.punc();
            if (y.op()==BOOL) {
                if (punc == QUESTION || punc == QUEST) {
                    //conver to an answering belief/goal now that the absolute truth has been determined


                    byte answerPunc;
                    if (punc == QUESTION) answerPunc = BELIEF;
                    else answerPunc = GOAL;

                    t = Task.clone(input,
                            input.term(),
                            $.t(y==True ? 1 : 0, n.confDefault(answerPunc)),
                            answerPunc,
                            input.start(), input.end());

                } else {
                    return false;
                }
            } else {

                @Nullable ObjectBooleanPair<Term> yy = Task.tryContent(y, punc,
                        !input.isInput() // || !Param.DEBUG
                );
                if (yy == null)
                    return false;


                Term yyz = yy.getOne();
                @Nullable Task u;

                u = Task.clone(input, yyz.negIf(yy.getTwo()));
                if (u!=null) {
                    return queue.add(u);
                } else {
                    throw new WTF();
                    //return false;
                }

            }
        } else {
            t = input;
        }

        return t != null && perceived(t, queue, n);
    }



    static boolean perceived(Task t, Collection<ITask> queue, NAR n) {
//        Term tt = t.term();
//        Op tto = tt.op();
//        if (!tto.taskable) {
//            if (tto == NEG) throw new WTF("neg could be inverted and it would be ok"); //HACK
//
//            return false; //fast filter
//        }


        byte punc = t.punc();
        boolean cmd = punc == COMMAND;
        if (!cmd) {
            ITask p = t.perceive(t, n);
            if (p != null)
                queue.add(p);
        }

        if (cmd || (punc == GOAL && !t.isEternal())) {
            if (!execute(t, queue, n, cmd))
                return false;
        }


        return true;
    }

//    private static void conjDecompose(Task t, Collection<ITask> queue, NAR n) {
//        byte punc = t.punc();
//        Term tt = t.term();
//        Truth tTruth = t.truth();
//
//        Truth reducedTruth = NALTruth.StructuralDeduction.apply(tTruth, null, n, n.confMin.floatValue());
//        if (reducedTruth != null)
//            reducedTruth.dithered(n);
//
//        if (reducedTruth != null) {
//            long s = t.start();
//            long range = t.end() - s;
//            List<Task> subTasks = new FasterList(2);
//
//            tt.eventsWhile((when, what) -> {
//                assert (!what.equals(tt));
//
//                Task tSubEvent = Task.clone(t, what, reducedTruth, punc, Tense.dither(when, n), Tense.dither(when + range, n));
//
//                if (tSubEvent != null)
//                    subTasks.add(tSubEvent);
//                return true;
//            }, s, true, false, false, 0);
//
//
//            if (!subTasks.isEmpty()) {
//                int ns = subTasks.size();
//                float conf = tTruth.conf();
//                float pr = (reducedTruth.conf() / conf) * t.priElseZero() / ns;
//                for (Task ss : subTasks) {
//                    ss.pri(0);
//                    ss.take(t, pr, true, false);
//                    //ss.setCyclic(true);
//                }
//                queue.addAll(subTasks);
//            }
//        }
//    }

    private static boolean execute(Task t, Collection<ITask> queue, NAR n, boolean cmd) {
        Term maybeOperator = Functor.func(t.term());

        if (maybeOperator!= Bool.Null) {
            Concept oo = n.concept(maybeOperator);
            if (oo instanceof Operator) {
                Operator o = (Operator)oo;
                try {
                    Task yy = o.model.apply(t, n);
                    if (yy != null && !t.equals(yy)) {
                        queue.add(yy);
                    }
                } catch (Throwable xtt) {
                    logger.warn("{} operator {} exception {}", t, o, xtt);
                    //queue.add(Operator.error(this, xtt, n.time()));
                    return false;
                }
                if (cmd) {
                    queue.add(new TaskEvent(t));
                }

            }
        }
        return true;
    }
}
