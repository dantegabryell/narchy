package nars;


import jcog.list.FasterList;
import nars.op.SetFunc;
import nars.subterm.ArrayTermVector;
import nars.subterm.Neg;
import nars.subterm.Subterms;
import nars.term.Compound;
import nars.term.Term;
import nars.term.Terms;
import nars.term.atom.Atom;
import nars.term.atom.Atomic;
import nars.term.atom.Bool;
import nars.term.atom.Int;
import nars.term.compound.CachedCompound;
import nars.term.var.NormalizedVariable;
import nars.term.var.UnnormalizedVariable;
import nars.term.var.VarDep;
import nars.time.Tense;
import nars.unify.Unify;
import nars.unify.match.EllipsisMatch;
import nars.unify.match.Ellipsislike;
import nars.util.term.TermBuilder;
import nars.util.term.builder.InterningTermBuilder;
import org.apache.lucene.util.MathUtil;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.primitive.IntIntPair;
import org.eclipse.collections.impl.factory.Maps;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

import static java.util.Arrays.copyOfRange;
import static nars.term.Terms.sorted;
import static nars.time.Tense.DTERNAL;
import static nars.time.Tense.XTERNAL;
import static org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples.pair;

/**
 * NAL symbol table
 */
public enum Op {


    ATOM(".", Op.ANY_LEVEL, OpType.Other),

    NEG("--", 1, Args.One) {
        public Term the(int dt, Term[] u) {

            if (u.length != 1)
                throw new RuntimeException("negation requires one subterm");
            return Neg.the(u[0]);
        }

    },

    INH("-->", 1, OpType.Statement, Args.Two) {
        @Override
        public Term the(int dt, Term[] u) {
            assert (u.length == 2): " requires 2 arguments, but got: " + Arrays.toString(u);
            return terms.statement(this, dt, u[0], u[1]);
        }
    },
    SIM("<->", true, 2, OpType.Statement, Args.Two) {
        @Override
        public Term the(int dt, Term[] u) {
            if (u.length == 1) {
                assert (this == SIM);
                return u[0] == Null ? Null : True;
            } else {
                assert (u.length == 2): " requires 2 arguments, but got: " + Arrays.toString(u);
                return terms.statement(this, dt, u[0], u[1]);
            }
        }
    },

    /**
     * extensional intersection
     */
    SECTe("&", true, 3, Args.GTETwo) {
        @Override
        public Term the(int dt, Term[] u) {
            return intersect(/*Int.intersect*/(u),
                    SECTe,
                    SETe,
                    SETi);
        }
    },

    /**
     * intensional intersection
     */
    SECTi("|", true, 3, Args.GTETwo) {
        @Override
        public Term the(int dt, Term[] u) {
            return intersect(/*Int.intersect*/(u),
                    SECTi,
                    SETi,
                    SETe);
        }
    },

    /**
     * extensional difference
     */
    DIFFe("~", false, 3, Args.Two) {
        @Override
        public Term the(int dt, Term[] u) {
            return differ(this, u);
        }
    },

    /**
     * intensional difference
     */
    DIFFi("-", false, 3, Args.Two) {
        @Override
        public Term the(int dt, Term[] u) {
            return differ(this, u);
        }
    },

    /**
     * PRODUCT
     * classically this is considered NAL4 but due to the use of functors
     * it is much more convenient to classify it in NAL1 so that it
     * along with inheritance (INH), which comprise the functor,
     * can be used to compose the foundation of the system.
     */
    PROD("*", 1, Args.GTEZero),


    /**
     * conjunction
     */
    CONJ("&&", true, 5, Args.GTETwo) {
        @Override
        public Term the(int dt, Term[] u) {
            final int n = u.length;
            switch (n) {

                case 0:
                    return True;

                case 1:
                    Term only = u[0];
                    if (only instanceof EllipsisMatch) {

                        return the(dt, only.arrayShared());
                    } else {


                        return only instanceof Ellipsislike ?
                                compoundExact(CONJ, dt, only)
                                :
                                only;
                    }

            }

            return terms.conj(dt, u);
        }

    },


    /**
     * intensional set
     */
    SETi("[", true, 2, Args.GTEOne) {
        @Override
        public boolean isSet() {
            return true;
        }

        @Override
        public final Term the(int dt, Collection<Term> sub) {
            return compoundExact(this, dt, Terms.sorted(sub));
        }
    },

    /**
     * extensional set
     */
    SETe("{", true, 2, Args.GTEOne) {
        @Override
        public boolean isSet() {
            return true;
        }

        @Override
        public final Term the(int dt, Collection<Term> sub) {
            return compoundExact(this, dt, Terms.sorted(sub));
        }
    },


    /**
     * implication
     */
    IMPL("==>", 5, OpType.Statement, Args.Two) {
        @Override
        public Term the(int dt, Term... u) {
            assert (u.length == 2);
            return terms.statement(this, dt, u[0], u[1]);
        }
    },


    VAR_DEP('#', Op.ANY_LEVEL, OpType.Variable),
    VAR_INDEP('$', 5 /*NAL5..6 for Indep Vars */, OpType.Variable),
    VAR_QUERY('?', Op.ANY_LEVEL, OpType.Variable),
    VAR_PATTERN('%', Op.ANY_LEVEL, OpType.Variable),

    INT("+", Op.ANY_LEVEL, OpType.Other),

    BOOL("B", Op.ANY_LEVEL, OpType.Other),


    /**
     * for ellipsis, when seen as a term
     */

    ;


    /**
     * does this help?  Op.values() bytecode = INVOKESTATIC
     * but accessing this is GETSTATIC
     */
    public static final Op[] ops = Op.values();

    public static final String DISJstr = "||";
    public static final int StatementBits = Op.or(Op.INH, Op.SIM, Op.IMPL);
    public static final int FuncBits = Op.or(Op.ATOM, Op.INH, Op.PROD);
    public static final int FuncInnerBits = Op.or(Op.ATOM, Op.PROD);
    public static final byte BELIEF = '.';
    public static final byte QUESTION = '?';
    public static final byte GOAL = '!';
    public static final byte QUEST = '@';
    public static final byte COMMAND = ';';
    public static final String TENSE_PAST = ":\\:";
    public static final String TENSE_PRESENT = ":|:";
    public static final String TENSE_FUTURE = ":/:";
    public static final String TENSE_ETERNAL = ":-:";
    public static final String TASK_RULE_FWD = "|-";
    public static final char BUDGET_VALUE_MARK = '$';
    public static final char TRUTH_VALUE_MARK = '%';
    public static final char VALUE_SEPARATOR = ';';
    public static final char ARGUMENT_SEPARATOR = ',';
    public static final char SET_INT_CLOSER = ']';
    public static final char SET_EXT_CLOSER = '}';
    public static final char COMPOUND_TERM_OPENER = '(';
    public static final char COMPOUND_TERM_CLOSER = ')';
    @Deprecated
    public static final char OLD_STATEMENT_OPENER = '<';
    @Deprecated
    public static final char OLD_STATEMENT_CLOSER = '>';
    public static final char STAMP_OPENER = '{';
    public static final char STAMP_CLOSER = '}';
    public static final char STAMP_SEPARATOR = ';';
    public static final char STAMP_STARTER = ':';
    /**
     * bitvector of non-variable terms which can not be part of a goal term
     */
    public static final int NonGoalable = or(IMPL);
    public static final int varBits = Op.or(VAR_PATTERN, VAR_DEP, VAR_QUERY, VAR_INDEP);
    /**
     * Image index ("imdex") symbol for products, and anonymous variable in products
     */
    public final static char ImdexSym = '_';
    public static final Atomic Imdex =
            new UnnormalizedVariable(Op.VAR_DEP, String.valueOf(ImdexSym)) {

                final int RANK = Term.opX(VAR_PATTERN, (short) 20 /* different from normalized variables with a subOp of 0 */);

                @Override
                public Term the() {
                    return this;
                }

                @Override
                public int opX() {
                    return RANK;
                }
            };


    public static final char NullSym = '☢';

    public static final char imIntSym = '\\';
    public static final char imExtSym = '/';

    /**
     * absolutely nonsense
     */
    public static final Bool Null = new Bool(String.valueOf(Op.NullSym), ((byte)-1) ) {

        final int rankBoolNull = Term.opX(BOOL, (short) 0);

        @Override
        public final int opX() {
            return rankBoolNull;
        }

        @Override
        public Term neg() {
            return this;
        }

        @Override
        public boolean equalsNeg(Term t) {
            return false;
        }

        @Override
        public boolean equalsNegRoot(Term t) {
            return false;
        }

        @Override
        public Term unneg() {
            return this;
        }
    };
    /**
     * tautological absolute false
     */
    public static final Bool False = new Bool("false", (byte)0) {

        final int rankBoolFalse = Term.opX(BOOL, (short) 1);

        @Override
        public final int opX() {
            return rankBoolFalse;
        }

        @Override
        public boolean equalsNeg(Term t) {
            return t == True;
        }

        @Override
        public boolean equalsNegRoot(Term t) {
            return t == True;
        }

        @Override
        public Term neg() {
            return True;
        }

        @Override
        public Term unneg() {
            return True;
        }
    };
    /**
     * tautological absolute true
     */
    public static final Bool True = new Bool("true", (byte)1) {

        final int rankBoolTrue = Term.opX(BOOL, (short) 2);

        @Override
        public final int opX() {
            return rankBoolTrue;
        }

        @Override
        public Term neg() {
            return False;
        }

        @Override
        public boolean equalsNeg(Term t) {
            return t == False;
        }

        @Override
        public boolean equalsNegRoot(Term t) {
            return t == False;
        }

        @Override
        public Term unneg() {
            return True;
        }
    };
    public static final VarDep imInt = new ImDep((byte) 126, (byte) '\\');
    public static final VarDep imExt = new ImDep((byte) 127, (byte) '/');
    public static final int DiffBits = Op.DIFFe.bit | Op.DIFFi.bit;
    public static final int SectBits = or(Op.SECTe, Op.SECTi);
    public static final int SetBits = or(Op.SETe, Op.SETi);
    public static final int Temporal = or(Op.CONJ, Op.IMPL);

    public static final Atom BELIEF_TERM = (Atom) Atomic.the(String.valueOf((char) BELIEF));
    public static final Atom GOAL_TERM = (Atom) Atomic.the(String.valueOf((char) GOAL));
    public static final Atom QUESTION_TERM = (Atom) Atomic.the(String.valueOf((char) QUESTION));
    public static final Atom QUEST_TERM = (Atom) Atomic.the(String.valueOf((char) QUEST));
    public static final Atom QUE_TERM = (Atom) Atomic.the(String.valueOf((char) QUESTION) + (char) QUEST);

    public static final Term[] EmptyTermArray = new Term[0];
    public static final Subterms EmptySubterms = new ArrayTermVector(EmptyTermArray);
    public static final Term EmptyProduct = new CachedCompound.SimpleCachedCompound(Op.PROD, EmptySubterms);
    public static final int VariableBits = or(Op.VAR_PATTERN, Op.VAR_INDEP, Op.VAR_DEP, Op.VAR_QUERY);
    public static final int[] NALLevelEqualAndAbove = new int[8 + 1];
    static final ImmutableMap<String, Op> stringToOperator;
    /**
     * ops across which reflexivity of terms is allowed
     */
    final static int relationDelimeterStrong = Op.or(Op.PROD, Op.NEG);
    public static final Predicate<Term> recursiveCommonalityDelimeterStrong =
            c -> !c.isAny(relationDelimeterStrong);
    /**
     * allows conj
     */
    final static int relationDelimeterWeak = relationDelimeterStrong | Op.or(Op.CONJ);
    public static final Predicate<Term> recursiveCommonalityDelimeterWeak =
            c -> !c.isAny(relationDelimeterWeak);
    /**
     * specifier for any NAL level
     */
    private static final int ANY_LEVEL = 0;
    public static final int InvalidImplicationSubj = or(IMPL);
    public static TermBuilder terms =
            //HeapTermBuilder.the;
            new InterningTermBuilder();

    public static int AtomicConstants = Op.ATOM.bit | Op.INT.bit;

    static {
        for (Op o : Op.values()) {
            int l = o.minLevel;
            if (l < 0) l = 0;
            for (int i = l; i <= 8; i++) {
                NALLevelEqualAndAbove[i] |= o.bit;
            }
        }

        final Map<String, Op> _stringToOperator = new HashMap<>(values().length * 2);


        for (Op r : Op.values()) {
            _stringToOperator.put(r.toString(), r);

        }
        stringToOperator = Maps.immutable.ofMap(_stringToOperator);


    }

    public final Atom strAtom;
    public final boolean indepVarParent;
    public final boolean depVarParent;
    /**
     * whether it is a special or atomic term that isnt conceptualizable.
     * negation is an exception to this, being unconceptualizable itself
     * but it will have conceptualizable=true.
     */
    public final boolean conceptualizable;
    public final boolean beliefable, goalable;
    /**
     * string representation
     */
    public final String str;
    /**
     * character representation if symbol has length 1; else ch = 0
     */
    public final char ch;
    public final OpType type;
    /**
     * arity limits, range is inclusive >= <=
     * TODO replace with an IntPredicate
     */
    public final int minSize, maxSize;
    /**
     * minimum NAL level required to use this operate, or 0 for N/A
     */
    public final int minLevel;
    public final boolean commutative;
    public final boolean temporal;
    /**
     * 1 << op.ordinal
     */
    public final int bit;
    public final boolean var;
    public final boolean atomic;
    public final boolean statement;
    /**
     * whether this involves an additional numeric component: 'dt' (for temporals) or 'relation' (for images)
     */
    public final boolean hasNumeric;

    /*
    used only by Termlike.hasAny
    public static boolean hasAny(int existing, int possiblyIncluded) {
        return (existing & possiblyIncluded) != 0;
    }*/
    public final byte id;

    Op(char c, int minLevel, OpType type) {
        this(c, minLevel, type, Args.None);
    }

    Op(String s, boolean commutative, int minLevel, IntIntPair size) {
        this(s, commutative, minLevel, OpType.Other, size);
    }

    Op(char c, int minLevel, OpType type, IntIntPair size) {
        this(Character.toString(c), minLevel, type, size);
    }


    Op(String string, int minLevel, IntIntPair size) {
        this(string, minLevel, OpType.Other, size);
    }


    Op(String string, int minLevel, OpType type) {
        this(string, false /* non-commutive */, minLevel, type, Args.None);
    }

    Op(String string, int minLevel, OpType type, IntIntPair size) {
        this(string, false /* non-commutive */, minLevel, type, size);
    }

    Op(String string, boolean commutative, int minLevel, OpType type, IntIntPair size) {

        this.id = (byte) (ordinal());
        this.str = string;
        this.ch = string.length() == 1 ? string.charAt(0) : 0;
        this.strAtom = ch != '.' ? (Atom) Atomic.the('"' + str + '"') : null /* dont compute for ATOM, infinite loops */;

        this.commutative = commutative;
        this.minLevel = minLevel;
        this.type = type;


        this.minSize = size.getOne();
        this.maxSize = size.getTwo();

        this.var = (type == OpType.Variable);

        boolean isImpl = str.equals("==>");
        this.statement = str.equals("-->") || isImpl || str.equals("<->");
        boolean isConj = str.equals("&&");
        this.temporal = isConj || isImpl;


        this.hasNumeric = temporal;


        this.bit = (1 << ordinal());

        final Set<String> ATOMICS = Set.of(".", "+", "B");
        this.atomic = var || ATOMICS.contains(str);


        conceptualizable = !var &&
                !str.equals("B") /* Bool */


        ;

        goalable = conceptualizable && !isImpl;

        beliefable = conceptualizable;

        indepVarParent = isImpl;
        depVarParent = isConj;

    }

    /**
     * TODO option for instantiating CompoundLight base's in the bottom part of this
     */
    public static Term dt(Compound base, int nextDT) {


        return base.op().the(nextDT, base.arrayShared());


    }


    public static boolean hasAny(int existing, int possiblyIncluded) {
        return (existing & possiblyIncluded) != 0;
    }

    public static boolean hasAll(int existing, int possiblyIncluded) {
        return ((existing | possiblyIncluded) == existing);
    }

    public static boolean isTrueOrFalse(Term x) {
        return x == True || x == False;
    }


    public static boolean concurrent(int dt) {
        return (dt == DTERNAL) || (dt == 0);
    }


    public static boolean hasNull(Term[] t) {
        for (Term x : t)
            if (x == Null)
                return true;
        return false;
    }

    private static Term differ(/*@NotNull*/ Op op, Term... t) {


        switch (t.length) {
            case 1:
                Term single = t[0];
                if (single instanceof EllipsisMatch) {
                    return differ(op, single.arrayShared());
                }
                return single instanceof Ellipsislike ?
                        compoundExact(op, DTERNAL, single) :
                        Null;
            case 2:
                Term et0 = t[0], et1 = t[1];

                if (et0 == Null || et1 == Null)
                    return Null;


                if (et0.equalsRoot(et1))
                    return False;


                Op o0 = et0.op();
                if (et1.equalsNegRoot(et0)) {
                    if (o0 == NEG || et0 == False)
                        return False;
                    else
                        return True;
                }

                if (isTrueOrFalse(et0) || isTrueOrFalse(et1))
                    return Null;

                Op o1 = et1.op();

                //((--,X)~(--,Y)) reduces to (Y~X)
                if (o0 == NEG && o1 == NEG) {
                    //un-neg and swap order
                    Term x = et0.unneg();
                    et0 = et1.unneg();
                    o0 = et0.op();
                    et1 = x;
                    o1 = et1.op();
                }


                if (et0.containsRecursively(et1, true, recursiveCommonalityDelimeterWeak)
                        || et1.containsRecursively(et0, true, recursiveCommonalityDelimeterWeak))
                    return Null;

                if (op == DIFFe && et0 instanceof Int.IntRange && o1 == INT) {
                    Term simplified = ((Int.IntRange) et0).subtract(et1);
                    if (simplified != Null)
                        return simplified;
                }


                Op set = op == DIFFe ? SETe : SETi;
                if ((o0 == set && o1 == set)) {
                    return differenceSet(set, et0, et1);
                } else {
                    return differenceSect(op, et0, et1);
                }


        }

        throw new Term.InvalidTermException(op, t, "diff requires 2 terms");

    }

    private static Term differenceSect(Op diffOp, Term a, Term b) {


        Op ao = a.op();
        if (((diffOp == DIFFi && ao == SECTe) || (diffOp == DIFFe && ao == SECTi)) && (b.op() == ao)) {
            Subterms aa = a.subterms();
            Subterms bb = b.subterms();
            MutableSet<Term> common = Subterms.intersect(aa, bb);
            if (common != null) {
                int cs = common.size();
                if (aa.subs() == cs || bb.subs() == cs)
                    return Null;
                return ao.the(common.with(
                        diffOp.the(ao.the(aa.termsExcept(common)), ao.the(bb.termsExcept(common)))
                ));
            }
        }


        if (((diffOp == DIFFi && ao == SECTi) || (diffOp == DIFFe && ao == SECTe)) && (b.op() == ao)) {
            Subterms aa = a.subterms();
            Subterms bb = b.subterms();
            MutableSet<Term> common = Subterms.intersect(aa, bb);
            if (common != null) {
                int cs = common.size();
                if (aa.subs() == cs || bb.subs() == cs)
                    return Null;
                return ao.the(common.collect(Term::neg).with(
                        diffOp.the(ao.the(aa.termsExcept(common)), ao.the(bb.termsExcept(common)))
                ));
            }
        }

        return compoundExact(diffOp, DTERNAL, a, b);
    }

    /*@NotNull*/
    public static Term differenceSet(/*@NotNull*/ Op o, Term a, Term b) {


        if (a.equals(b))
            return Null;

        if (o == INT) {
            if (!(a instanceof Int.IntRange))
                return Null;
            else {
                Term aMinB = ((Int.IntRange) a).subtract(b);
                if (aMinB != Null) {
                    if (a.equals(aMinB))
                        return Null;
                    return aMinB;
                }
            }
        }


        int size = a.subs();
        Collection<Term> xx = o.commutative ? new TreeSet() : new FasterList(size);

        for (int i = 0; i < size; i++) {
            Term x = a.sub(i);
            if (!b.contains(x)) {
                xx.add(x);
            }
        }

        int retained = xx.size();
        if (retained == size) {
            return a;
        } else if (retained == 0) {
            return Null;
        } else {
            return o.the(DTERNAL, xx);
        }

    }


    static boolean in(int needle, int haystack) {
        return (needle & haystack) == needle;
    }

    public static int or(/*@NotNull*/ Op... o) {
        int bits = 0;
        for (Op n : o)
            bits |= n.bit;
        return bits;
    }

    public static boolean containEachOther(Term x, Term y, Predicate<Term> delim) {
        int xv = x.volume();
        int yv = y.volume();
        boolean root = false;
        if (xv == yv)
            return Term.commonStructure(x, y) &&
                    (x.containsRecursively(y, root, delim) || y.containsRecursively(x, root, delim));
        else if (xv > yv)
            return x.containsRecursively(y, root, delim);
        else
            return y.containsRecursively(x, root, delim);
    }


    @Nullable
    public static Op the(String s) {
        return stringToOperator.get(s);
    }

    public static Object theIfPresent(String s) {
        Op x = stringToOperator.get(s);
        if (x != null)
            return x;
        else
            return s;
    }

    private static Term intersect(Term[] t, /*@NotNull*/ Op intersection, /*@NotNull*/ Op setUnion, /*@NotNull*/ Op setIntersection) {

        int trues = 0;
        for (Term x : t) {
            if (x == True) {

                trues++;
            } else if (x == Null || x == False) {
                return Null;
            }
        }
        if (trues > 0) {
            if (trues == t.length) {
                return True;
            } else if (t.length - trues == 1) {

                for (Term x : t) {
                    if (x != True)
                        return x;
                }
            } else {

                Term[] t2 = new Term[t.length - trues];
                int yy = 0;
                for (Term x : t) {
                    if (x != True)
                        t2[yy++] = x;
                }
                t = t2;
            }
        }

        switch (t.length) {

            case 0:
                throw new RuntimeException();

            case 1:

                Term single = t[0];
                if (single instanceof EllipsisMatch) {
                    return intersect(single.arrayShared(), intersection, setUnion, setIntersection);
                }
                return single instanceof Ellipsislike ?
                        compoundExact(intersection, DTERNAL, single) :
                        single;

            case 2:
                return intersect2(t[0], t[1], intersection, setUnion, setIntersection);
            default:

                Term a = intersect2(t[0], t[1], intersection, setUnion, setIntersection);

                Term b = intersect(copyOfRange(t, 2, t.length), intersection, setUnion, setIntersection);

                return intersect2(a, b,
                        intersection, setUnion, setIntersection
                );
        }

    }

    /*@NotNull*/
    @Deprecated
    private static Term intersect2(Term term1, Term term2, /*@NotNull*/ Op intersection, /*@NotNull*/ Op setUnion, /*@NotNull*/ Op setIntersection) {

        if (term1.equals(term2))
            return term1;

        Op o1 = term1.op();
        Op o2 = term2.op();

        if ((o1 == setUnion) && (o2 == setUnion)) {

            return SetFunc.union(setUnion, term1.subterms(), term2.subterms());
        }


        if ((o1 == setIntersection) && (o2 == setIntersection)) {

            return SetFunc.intersect(setIntersection, term1.subterms(), term2.subterms());
        }

        if (o2 == intersection && o1 != intersection) {

            Term x = term1;
            term1 = term2;
            term2 = x;
            o2 = o1;
            o1 = intersection;
        }


        TreeSet<Term> args = new TreeSet<>();
        if (o1 == intersection) {
            ((Iterable<Term>) term1).forEach(args::add);
            if (o2 == intersection)
                ((Iterable<Term>) term2).forEach(args::add);
            else
                args.add(term2);
        } else {
            args.add(term1);
            args.add(term2);
        }

        int aaa = args.size();
        if (aaa == 1)
            return args.first();
        else {
            return compoundExact(intersection, DTERNAL, args.toArray(Op.EmptyTermArray));
        }
    }

    public static boolean goalable(Term c) {
        return !c.hasAny(Op.NonGoalable);
    }

    /**
     * returns null if not found, and Null if no subterms remain after removal
     */
    @Nullable
    public static Term without(Term container, Predicate<Term> filter, Random rand) {


        Subterms cs = container.subterms();

        int i = cs.indexOf(filter, rand);
        if (i == -1)
            return Null;


        switch (cs.subs()) {
            case 1:
                return Null;
            case 2:

                Term remain = cs.sub(1 - i);
                Op o = container.op();
                return o.isSet() ? o.the(remain) : remain;
            default:
                return container.op().the(container.dt(), cs.termsExcept(i));
        }

    }

    public static int conjEarlyLate(Term x, boolean earlyOrLate) {
        assert (x.op() == CONJ);
        int dt = x.dt();
        switch (dt) {
            case DTERNAL:
            case XTERNAL:
            case 0:
                return earlyOrLate ? 0 : 1;

            default: {


                return (dt < 0) ? (earlyOrLate ? 1 : 0) : (earlyOrLate ? 0 : 1);
            }
        }
    }


    /**
     * encodes a structure vector as a human-readable term.
     * if only one bit is set then the Op's strAtom is used instead of the binary
     * representation.
     * TODO make an inverse decoder
     */
    public static Term strucTerm(int struct) {
        int bits = Integer.bitCount(struct);
        switch (bits) {
            case 0:
                throw new UnsupportedOperationException("no bits");
            case 1: {
                Op op = ops[MathUtil.log(Integer.highestOneBit(struct), 2)];
                return op.strAtom;
            }
            default: {
                return $.quote( Integer.toBinaryString(struct)/*.substring(Op.ops.length)*/ );
            }
        }
    }

    public static boolean commute(int dt, int subterms) {
        return subterms > 1 && Op.concurrent(dt);
    }
    public boolean commute(int subterms) {
        return subterms > 1 && commutative;
    }

    public final Term the(Subterms s) {
        return the(s.arrayShared());
    }

    public final Term the(/*@NotNull*/ Term... u) {
        return the(DTERNAL, u);
    }

    @Override
    public String toString() {
        return str;
    }

    /**
     * writes this operator to a Writer in (human-readable) expanded UTF16 mode
     */
    public final void append(Compound c, Appendable w) throws IOException {
        append(c.dt(), w, false);
    }

    /**
     * writes this operator to a Writer in (human-readable) expanded UTF16 mode
     */
    public final void append(int dt, Appendable w, boolean invertDT) throws IOException {


        if (dt == 0) {

            String s;
            switch (this) {
                case CONJ:
                    s = ("&|");
                    break;
                case IMPL:
                    s = ("=|>");
                    break;


                default:
                    throw new UnsupportedOperationException();
            }
            w.append(s);
            return;
        }

        boolean hasTime = dt != Tense.DTERNAL;
        if (hasTime)
            w.append(' ');

        char ch = this.ch;
        if (ch == 0)
            w.append(str);
        else
            w.append(ch);

        if (hasTime) {

            if (invertDT)
                dt = -dt;

            if (dt > 0) w.append('+');
            String ts;
            if (dt == XTERNAL)
                ts = "-";
            else
                ts = Integer.toString(dt);
            w.append(ts).append(' ');

        }
    }

    public final Term[] sortedIfNecessary(int dt, Term[] u) {
        return commutative && commute(dt, u.length) ? sorted(u) : u;
    }

    public final Term the(/*@NotNull*/ Collection<Term> sub) {
        return the(DTERNAL, sub);
    }

    public Term the(int dt, /*@NotNull*/ Collection<Term> sub) {
        return the(dt, sub.toArray(EmptyTermArray));
    }

    /**
     * alternate method args order for 2-term w/ infix DT
     */
    public final Term the(Term a, int dt, Term b) {
        return the(dt, a, b);
    }

    /**
     * entry point into the term construction process.
     * this call tree eventually ends by either:
     * - instance(..)
     * - reduction to another term or True/False/Null
     */
    public Term the(int dt, Term... u) {
        return compoundExact(this, dt, sortedIfNecessary(dt,u));
    }

    /**
     * direct constructor
     * no reductions or validations applied
     * use with caution
     */
    public static Term compoundExact(Op o, int dt, Term... u) {
        return terms.compound(o, dt, u);
    }

    /**
     * true if matches any of the on bits of the vector
     */
    public final boolean in(int vector) {
        return in(bit, vector);
    }

    public boolean isSet() {
        return false;
    }

    public boolean isAny(int bits) {
        return ((bit & bits) != 0);
    }

    /**
     * top-level Op categories
     */
    public enum OpType {
        Statement,
        Variable,
        Other
    }

    enum Args {
        ;
        static final IntIntPair None = pair(0, 0);
        static final IntIntPair One = pair(1, 1);
        static final IntIntPair Two = pair(2, 2);

        static final IntIntPair GTEZero = pair(0, Param.COMPOUND_SUBTERMS_MAX);
        static final IntIntPair GTEOne = pair(1, Param.COMPOUND_SUBTERMS_MAX);
        static final IntIntPair GTETwo = pair(2, Param.COMPOUND_SUBTERMS_MAX);

    }

    final static class ImDep extends VarDep {

        private final String str;
        private final char symChar;
        private final int rank;

        public ImDep(byte id, byte sym) {
            super(id);
            this.str = String.valueOf((char) sym);
            this.symChar = (char) sym;
            this.rank = Term.opX(VAR_DEP, (short) id);
        }

        @Override
        public Term concept() {
            return Null;
        }

        @Override
        public int opX() {
            return rank;
        }

        @Override
        public @Nullable NormalizedVariable normalize(byte vid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean unify(Term y, Unify u) {
            return y == this;
        }

        @Override
        public boolean unifyReverse(Term x, Unify u) {
            return x == this;
        }

        @Override
        public final void appendTo(Appendable w) throws IOException {
            w.append(symChar);
        }

        @Override
        public final String toString() {
            return str;
        }

    }

    public static class InvalidPunctuationException extends RuntimeException {
        public InvalidPunctuationException(byte c) {
            super("Invalid punctuation: " + c);
        }
    }


}
