package nars.nal.nal8.operator;

import nars.$;
import nars.Op;
import nars.Symbols;
import nars.nal.Tense;
import nars.nal.nal8.Execution;
import nars.nal.nal8.Operator;
import nars.task.MutableTask;
import nars.task.Task;
import nars.term.Compound;
import nars.term.Term;
import nars.term.TermBuilder;
import nars.term.atom.Atom;
import nars.truth.DefaultTruth;
import nars.truth.Truth;
import nars.util.Texts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/** 
 * Superclass of functions that execute synchronously (blocking, in thread) and take
 * N input Global and one variable argument (as the final argument), generating a new task
 * with the result of the function substituted in the variable's place.
 */
public abstract class TermFunction<O> extends SyncOperator {

    protected TermFunction() {
    }


    protected TermFunction(String name) {
        super(name);
    }

    public static int integer(@NotNull Term x, int defaultValue)  {
        try {
            return integer(x);
        }
        catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int integer(@NotNull Term x) throws NumberFormatException {
        return Texts.i(Atom.unquote(x));
    }

    public static boolean isPunctuation(char c) {
        switch (c) {
            case Symbols.BELIEF:
            case Symbols.GOAL:
            case Symbols.QUEST:
            case Symbols.QUESTION:
                return true;
        }
        return false;
    }

    //TODO supply the execution instead of the TermBuilder which is referenced from it. in TermBuilder, supply a dummy Execution context for the ImmediateTransforms that need it
    /** y = function(x) 
     * @return y, or null if unsuccessful
     * @param arguments - the product subject of an Operation
     * @param i
     */
    @Nullable
    public abstract O function(Compound arguments, TermBuilder i);


    @Nullable
    protected MutableTask result(@NotNull Task e, Term y/*, Term[] x0, Term lastTerm*/) {
        return Execution.result(nar, e, y, getResultTense());
    }

    /** default tense applied to result tasks */
    @NotNull
    public Tense getResultTense() {
        return Tense.Present;
    }

//    /** default confidence applied to result tasks */
//    public float getResultFrequency() {
//        return 1.0f;
//    }
//
//
//    /** default confidence applied to result tasks */
//    public float getResultConfidence() {
//        return 0.99f;
//    }



//    protected ArrayList<Task> result2(Operation operation, Term y, Term[] x0, Term lastTerm) {
//        //since Peis approach needs it to directly generate op(...,$output) =|> <$output <-> result>,
//        //which wont happen on temporal induction with dependent variable for good rule,
//        //because in general the two dependent variables of event1 and event2
//        //can not be assumed to be related, but here we have to assume
//        //it if we don't want to use the "resultof"-relation.
//
//
//        Compound actual = Implication.make(
//                operation.cloneWithArguments(x0, var),
//                Similarity.make(var, y),
//                TemporalRules.ORDER_FORWARD);
//
//        if (actual == null) return null;
//
//        Compound actual_dep_part = lastTerm!=null ? Similarity.make(lastTerm, y) : null;
//
//
//        float confidence = operation.getTask().getConfidence();
//        //TODO add a delay discount/projection for executions that happen further away from creation time
//
//        return Lists.newArrayList(
//
//                TaskSeed.make(nar.memory, actual).judgment()
//                        .budget(Global.DEFAULT_JUDGMENT_PRIORITY, Global.DEFAULT_JUDGMENT_DURABILITY)
//                        .truth(getResultFrequency(), getResultConfidence())
//                        .parent(operation.getTask())
//                        .tense(getResultTense()),
//
//                actual_dep_part != null ?
//                        TaskSeed.make(nar.memory, actual_dep_part).judgment()
//                                .budget(Global.DEFAULT_JUDGMENT_PRIORITY, Global.DEFAULT_JUDGMENT_DURABILITY)
//                                .truth(1f, confidence)
//                                .present()
//                                .parent(operation.getTask()) : null
//
//        );
//
//    }

//    @Override
//    protected void noticeExecuted(Task<Operation> operation) {
//        //no notice
//    }




    @Override
    public void execute(@NotNull Task tt) {
        final Compound ttt = tt.term();
        final Compound args = Operator.opArgs(ttt);
        if (!tt.isCommand()) {
            if (!validArgs(args))
                return;
        }
        O y = function(args, nar.index());
        if (!tt.isCommand()) {
            feedback(tt, y);
        }
    }

    protected void feedback(@NotNull Task cause, @Nullable Object y) {

        if (y == null || (y instanceof Term)) {
            Execution.feedback( cause, result(cause, (Term) y), nar );
            return;
        }

        if (y instanceof Boolean) {
            boolean by = (Boolean)y;
            y = new DefaultTruth(by ? 1 : 0, 0.99f);
        }

        if (y instanceof Truth) {
            Execution.feedback( cause, result(cause, null).truth((Truth)y), nar );
            return;
        }

        if (y instanceof Task) {
            Task ty = (Task)y;
            if (ty.pri() == 0) {
                //set a resulting zero budget to the input task's
                ty.budget().set(cause.budget());
            }
            Execution.feedback( cause, (Task)y, nar );
            return;
        }

        if (y instanceof Number) {
            y = ($.the((Number)y));
        }


        String ys = y.toString();


        //1. try to parse as task
        char mustBePuncToBeTask = ys.charAt(ys.length()-1); //early prevention from invoking parser
        if (isPunctuation(mustBePuncToBeTask) || mustBePuncToBeTask == ':' /* tense ending character */) {
            try {
                Task t = nar.task(ys);
                if (t != null) {
                    Execution.feedback(cause, t, nar );
                    return;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        //2. try to parse as term

        Term t = $.the(ys, true);

        if (t != null) {
            Execution.feedback( cause, result(cause, t/*, x, lastTerm*/), nar );
            return;
        }

        throw new RuntimeException(this + " return value invalid: " + y);
    }

    /** the term that the output will inherit from; analogous to the 'Range' of a function in mathematical terminology */
    //protected Term getRange() {        return null;    }

    //protected int getMinArity() {        return 0;    }
    //abstract protected int getMaxArity();


    /** (can be overridden in subclasses) the extent to which it is truth 
     * that the 2 given terms are equal.  in other words, a distance metric
     */
    public float equals(@NotNull Term a, Term b) {
        //default: Term equality
        return a.equals(b) ? 1.0f : 0.0f;
    }

    private boolean validArgs(@NotNull Compound args) {
        //TODO filtering
        return args.size()>=1 && args.last().op() == Op.VAR_DEP;
    }
}



//if (variable) {
//}
//else {
            /*float equal = equals(lastTerm, y);



            float confidence = 0.99f;
            ArrayList<Task> rt = Lists.newArrayList(
                    m.newTaskAt(actual, Symbols.JUDGMENT,
                            1.0f, confidence,
                            Global.DEFAULT_JUDGMENT_PRIORITY,
                            Global.DEFAULT_JUDGMENT_DURABILITY,
                            operation.getTask()));

            if (equal < 1.0f) {
                rt.add(m.newTaskAt(operation, Symbols.JUDGMENT,
                            equal, confidence,
                            Global.DEFAULT_JUDGMENT_PRIORITY,
                            Global.DEFAULT_JUDGMENT_DURABILITY,
                            operation.getTask()));
            }
            return rt;
            */

//   return null;

//}
