/*
 * tuProlog - Copyright (C) 2001-2007 aliCE team at deis.unibo.it
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package alice.tuprolog;

import java.util.*;

/**
 * Struct class represents both compound prolog term
 * and atom term (considered as 0-arity compound).
 */
public class Struct extends Term {
    private static final long serialVersionUID = 1L;

    /**
     * name of the structure
     */
    private String name;
    /**
     * args array
     */
    private Term[] subs;
    /**
     * arity
     */
    private int subCount;

    /**
     * to speedup hash map operation
     */
    private String key;
    /**
     * primitive behaviour
     */
    private transient PrologPrimitive primitive;
    /**
     * it indicates if the term is resolved
     */
    private boolean resolved;

    /**
     * Builds a Struct representing an atom
     */
    public Struct(String f) {
        this(f, 0);
    }

















































    /**
     * Builds a compound, with an array of arguments
     */
    public Struct(String f, Term... argList) {
        this(f, argList.length);
        for (int i = 0; i < argList.length; i++)
            if (argList[i] == null)
                throw new InvalidTermException("Arguments of a Struct cannot be null");
            else
                subs[i] = argList[i];
    }


    /**
     * Builds a structure representing an empty list
     */
    private Struct() {
        this("[]", 0);
        resolved = true;
    }


    /**
     * Builds a list providing head and tail
     */
    public Struct(Term h, Term t) {
        this(".", 2);
        subs[0] = h;
        subs[1] = t;
    }

    /**
     * Builds a list specifying the elements
     */
    public Struct(Term[] argList) {
        this(argList, 0);
    }

    private Struct(Term[] argList, int index) {
        this(".", 2);
        if (index < argList.length) {
            subs[0] = argList[index];
            subs[1] = new Struct(argList, index + 1);
        } else {
            
            name = "[]";
            subCount = 0;
            subs = null;
        }
    }

    /**
     * Builds a compound, with a linked list of arguments
     */
    Struct(String f, LinkedList<Term> al) {
        name = f;
        if (!al.isEmpty()) {
            this.subs = al.toArray(new Term[subCount = al.size()]);
        } else {
            this.subs = null;
        }
        key = name + '/' + subCount;
        resolved = false;
    }

    private Struct(int arity_) {
        subCount = arity_;
        subs = new Term[subCount];
    }

    private Struct(String name_, int arity) {
        if (name_ == null)
            throw new InvalidTermException("The functor of a Struct cannot be null");
        if (name_.isEmpty() && arity > 0)
            throw new InvalidTermException("The functor of a non-atom Struct cannot be an empty string");
        name = name_;
        key = name + '/' + arity;
        subCount = arity;
        if (subCount > 0) {
            subs = new Term[subCount];
        }
        resolved = false;
    }


    final static Struct EmptyList = new Struct() {
        @Override
        public void append(Term t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isList() {
            return true;
        }

        @Override
        public boolean isConstant() {
            return true;
        }

        @Override
        public boolean isEmptyList() {
            return true;
        }

        @Override
        public int listSize() {
            return 0;
        }

        @Override
        public boolean isAtomic() {
            return true;
        }
    };

    public static Struct emptyList() {
        return EmptyList;
    }

    public static Struct emptyListMutable() {
        return new Struct();
    }


    /**
     * @return
     */
    final String key() {
        return key;
    }

    /**
     * arity: Gets the number of elements of this structure
     */
    public final int subs() {
        return subCount;
    }

    /**
     * Gets the functor name  of this structure
     */
    public final String name() {
        return name;
    }

    /**
     * Gets the i-th element of this structure
     * <p>
     * No bound check is done
     */
    public final Term sub(int index) {
        return subs[index];
    }

    /**
     * Sets the i-th element of this structure
     * <p>
     * (Only for internal service)
     */
    void setSub(int index, Term argument) {
        subs[index] = argument;
    }

    /**
     * Gets the i-th element of this structure
     * <p>
     * No bound check is done. It is equivalent to
     * <code>getArg(index).getTerm()</code>
     */
    public Term subResolve(int index) {
        Term s = subs[index];
        return s instanceof Var ? s.term() : s;
    }


    


    

    @Override
    public boolean isAtom() {
        return subCount == 0;
    }

    @Override
    public boolean isCompound() {
        return subCount > 0;
    }

    @Override
    public boolean isAtomic() {
        return isAtom() || isEmptyList();
    }

    @Override
    public boolean isList() {
        return (subCount == 2 && name.equals(".") && subs[1].isList()) || isEmptyList();
    }

    @Override
    public boolean isGround() {
        Term[] a = this.subs;
        for (int i = 0; i < subCount; i++) {
            if (!a[i].isGround()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check is this struct is clause or directive
     */
    public boolean isClause() {
        return subCount > 1 && name.equals(":-") && subs[0].term() instanceof Struct;
        
    }

    @Override
    public Term term() {
        return this;
    }

    

    /**
     * Gets an argument inside this structure, given its name
     *
     * @param name name of the structure
     * @return the argument or null if not found
     */
    public Struct sub(String name) {
        if (subCount == 0) {
            return null;
        }
        for (int i = 0; i < subCount; i++) {
            if (subs[i] instanceof Struct) {
                Struct s = (Struct) subs[i];
                if (s.name().equals(name)) {
                    return s;
                }
            }
        }
        for (int i = 0; i < subCount; i++) {
            if (subs[i] instanceof Struct) {
                Struct s = (Struct) subs[i];
                Struct sol = s.sub(name);
                if (sol != null) {
                    return sol;
                }
            }
        }
        return null;
    }


    

    /**
     * Test if a term is greater than other
     */
    @Override
    public boolean isGreater(Term t) {
        t = t.term();
        if (!(t instanceof Struct)) {
            return true;
        } else {
            Struct ts = (Struct) t;
            int tarity = ts.subCount;
            if (subCount > tarity) {
                return true;
            } else if (subCount == tarity) {
                int nc = name.compareTo(ts.name);
                if (nc > 0) {
                    return true;
                } else if (nc == 0) {
                    Term[] bb = ts.subs;
                    if (this.subs != bb) {
                        for (int c = 0; c < subCount; c++) {
                            Term a = this.subs[c];
                            Term b = bb[c];
                            if (a == b) continue;
                            if (a.isGreater(b)) {
                                return true;
                            } else if (!a.equals(b)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isGreaterRelink(Term t, ArrayList<String> vorder) {
        t = t.term();
        if (!(t instanceof Struct)) {
            return true;
        } else {
            Struct ts = (Struct) t;
            int tarity = ts.subCount;
            if (subCount > tarity) {
                return true;
            } else if (subCount == tarity) {
                
                if (name.compareTo(ts.name) > 0) {
                    return true;
                } else if (name.compareTo(ts.name) == 0) {
                    for (int c = 0; c < subCount; c++) {
                        
                        if (subs[c].isGreaterRelink(ts.subs[c], vorder)) {
                            return true;
                        } else if (!subs[c].isEqual(ts.subs[c])) {
                            return false;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Test if a term is equal to other
     */
    @Override
    public boolean isEqual(Term t) {
        t = t.term();

        if (t == this) return true;

        if (t instanceof Struct) {
            Struct ts = (Struct) t;
            if (subCount == ts.subCount && name.equals(ts.name)) { 
                if (this.subs != ts.subs) {
                    for (int c = 0; c < subCount; c++) {
                        if (!subs[c].equals(ts.subs[c])) {
                            return false;
                        }
                    }
                    
                }
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    


    public boolean isConstant() {
        if (isAtomic()) return true;
        for (Term x : subs) {
            if (x instanceof Var)
                return false;
            if ((x instanceof Struct) && (!((Struct) x).isConstant()))
                return false;
        }
        return true;
    }

    /**
     * Gets a copy of this structure
     *
     * @param vMap is needed for register occurence of same variables
     */
    @Override
    Term copy(Map<Var, Var> vMap, int idExecCtx) {


        if (!(vMap instanceof IdentityHashMap) && isConstant())
            return this;

        final int arity = this.subCount;
        Term[] targ = new Term[arity];
        Term[] arg = this.subs;

        for (int c = 0; c < arity; c++) {
            Term x = arg[c];
            Term y = x.copy(vMap, idExecCtx);



            targ[c] = y;
        }



        Struct t = new Struct(name, targ);
        t.resolved = resolved;
        t.key = key;
        t.primitive = primitive;

        return t;
    }


    /**
     * Gets a copy of this structure
     *
     * @param vMap     is needed for register occurence of same variables
     * @param substMap
     */
    @Override
    Term copy(Map<Var, Var> vMap, Map<Term, Var> substMap) {

        if (!(vMap instanceof IdentityHashMap) && isConstant())
            return this;

        Struct t = new Struct(name, subCount);
        t.resolved = false;
        t.key = key;
        t.primitive = null;
        Term[] thatArg = t.subs;
        Term[] thisArg = this.subs;
        final int arity = this.subCount;
        if (substMap == null)
            substMap = new IdentityHashMap<>();
        for (int c = 0; c < arity; c++) {
            Term tc = thisArg[c];
            Term yc;
            if (tc instanceof Var || (tc instanceof Struct && (!((Struct) tc).isConstant())))
                yc = tc.copy(vMap, substMap);
            else
                yc = tc;
            thatArg[c] = yc;
        }
        return t;
    }


    /**
     * resolve term
     */
    @Override
    void resolveTerm(long count) {
        if (!resolved && subCount > 0)
            resolveTerm(new LinkedList<>(), count);
    }


    /**
     * Resolve name of terms
     *
     * @param vl    list of variables resolved
     * @param count start timestamp for variables of this term
     * @return next timestamp for other terms
     */
    void resolveTerm(LinkedList<Var> vl, final long count) {

        Term[] arg = this.subs;
        int arity = this.subCount;
        for (int c = 0; c < arity; c++) {
            Term term = arg[c];
            if (term != null) {
                
                
                
                term = term.term();
                
                if (term instanceof Var) {
                    Var t = (Var) term;
                    t.setTimestamp(count);
                    if (!t.isAnonymous()) {
                        
                        String name = t.name();
                        Iterator<Var> it = vl.iterator();
                        Var found = null;
                        while (it.hasNext()) {
                            Var vn = it.next();
                            if (name.equals(vn.name())) {
                                found = vn;
                                break;
                            }
                        }
                        if (found != null) {
                            arg[c] = found;
                        } else {
                            vl.add(t);
                        }
                    }
                } else if (term instanceof Struct) {
                    ((Struct) term).resolveTerm(vl, count);
                }
            }
        }
        resolved = true;
    }

    

    /**
     * Is this structure an empty list?
     */
    @Override
    public boolean isEmptyList() {
        return subCount == 0 && name.equals("[]");
    }

    /**
     * Gets the head of this structure, which is supposed to be a list.
     * <p>
     * <p>
     * Gets the head of this structure, which is supposed to be a list.
     * If the callee structure is not a list, throws an <code>UnsupportedOperationException</code>
     * </p>
     */
    public Term listHead() {
        if (!isList())
            throw new UnsupportedOperationException("The structure " + this + " is not a list.");
        return subs[0].term();
    }

    /**
     * use with caution
     */
    public Term[] subArrayShared() {
        return subs;
    }

    /**
     * Gets the tail of this structure, which is supposed to be a list.
     * <p>
     * <p>
     * Gets the tail of this structure, which is supposed to be a list.
     * If the callee structure is not a list, throws an <code>UnsupportedOperationException</code>
     * </p>
     */
    public Struct listTail() {
        if (!isList())
            throw new UnsupportedOperationException("The structure " + this + " is not a list.");
        return (Struct) subs[1].term();
    }

    /**
     * Gets the number of elements of this structure, which is supposed to be a list.
     * <p>
     * <p>
     * Gets the number of elements of this structure, which is supposed to be a list.
     * If the callee structure is not a list, throws an <code>UnsupportedOperationException</code>
     * </p>
     */
    public int listSize() {
        if (!isList())
            throw new UnsupportedOperationException("The structure " + this + " is not a list.");
        Struct t = this;
        int count = 0;
        while (!t.isEmptyList()) {
            count++;
            t = (Struct) t.subs[1].term();
        }
        return count;
    }

    /**
     * Gets an iterator on the elements of this structure, which is supposed to be a list.
     * <p>
     * <p>
     * Gets an iterator on the elements of this structure, which is supposed to be a list.
     * If the callee structure is not a list, throws an <code>UnsupportedOperationException</code>
     * </p>
     */
    public Iterator<Term> listIterator() {
        if (!isList())
            throw new UnsupportedOperationException("The structure " + this + " is not a list.");
        return new StructIterator(this);
    }

    

    /**
     * Gets a list Struct representation, with the functor as first element.
     */
    Struct toList() {
        Struct t = emptyList();
        Term[] arg = this.subs;
        for (int c = subCount - 1; c >= 0; c--) {
            t = new Struct(arg[c].term(), t);
        }
        return new Struct(new Struct(name), t);
    }


    /**
     * Gets a flat Struct from this structure considered as a List
     * <p>
     * If this structure is not a list, null object is returned
     */
    Struct fromList() {
        Term ft = subs[0].term();
        if (!ft.isAtomic()) {
            return null;
        }
        Struct at = (Struct) subs[1].term();
        LinkedList<Term> al = new LinkedList<>();
        while (!at.isEmptyList()) {
            if (!at.isList()) {
                return null;
            }
            al.addLast(at.subResolve(0));
            at = (Struct) at.subResolve(1);
        }
        return new Struct(((Struct) ft).name, al);
    }


    /**
     * Appends an element to this structure supposed to be a list
     */
    public void append(Term t) {
        if (isEmptyList()) {
            subCount = 2;
            subs = new Term[] { t, emptyListMutable() };
            name = ".";
            key = "./2"; /* Added by Paolo Contessi */
        } else if (subs[1].isList()) {
            ((Struct) subs[1]).append(t);
        } else {
            subs[1] = t;
        }
    }













    

    /**
     * Try to unify two terms
     *
     * @param t   the term to unify
     * @param vl1 list of variables unified
     * @param vl2 list of variables unified
     * @return true if the term is unifiable with this one
     */
    @Override
    boolean unify(List<Var> vl1, List<Var> vl2, Term t) {
        if (this == t) return true;

        t = t.term(); 

        if (this == t) return true;

        if (t instanceof Struct) {
            Struct ts = (Struct) t;
            final int arity = this.subCount;
            if (arity == ts.subCount && name.equals(ts.name)) {
                Term[] arg = this.subs;
                Term[] tsarg = ts.subs;
                for (int c = 0; c < arity; c++) {
                    if (!arg[c].unify(vl1, vl2, tsarg[c])) {
                        return false;
                    }
                }
                return true;
            }
        } else if (t instanceof Var) {
            return t.unify(vl2, vl1, this);
        }
        return false;
    }


    

    /**
     * Set primitive behaviour associated at structure
     */
    void setPrimitive(PrologPrimitive b) {
        primitive = b;
    }

    /**
     * Get primitive behaviour associated at structure
     */
    public PrologPrimitive getPrimitive() {
        return primitive;
    }


    /**
     * Check if this term is a primitive struct
     */
    public boolean isPrimitive() {
        return primitive != null;
    }

    

    /**
     * Gets the string representation of this structure
     * <p>
     * Specific representations are provided for lists and atoms.
     * Names starting with upper case letter are enclosed in apices.
     */
    public String toString() {

        switch (name) {
            case "[]":
                if (subCount == 0) return "[]"; 
                break;
            case ".":
                if (subCount == 2) return '[' + toString0() + ']';
                break;
            case "{}":
                return '{' + toString0_bracket() + '}';
        }
        String s = (Parser.isAtom(name) ? name : '\'' + name + '\'');
        if (subCount > 0) {
            s = s + '(';
            for (int c = 1; c < subCount; c++) {
                s = s + (!(subs[c - 1] instanceof Var) ? subs[c - 1].toString() : ((Var) subs[c - 1]).toStringFlattened()) + ',';
            }
            s = s + (!(subs[subCount - 1] instanceof Var) ? subs[subCount - 1].toString() : ((Var) subs[subCount - 1]).toStringFlattened()) + ')';
        }
        return s;
    }

    private String toString0() {
        Term h = subs[0].term();
        Term t = subs[1].term();
        if (t.isList()) {
            Struct tl = (Struct) t;
            if (tl.isEmptyList()) {
                return h.toString();
            }
            return (h instanceof Var ? ((Var) h).toStringFlattened() : h.toString()) + ',' + tl.toString0();
        } else {
            String h0 = h instanceof Var ? ((Var) h).toStringFlattened() : h.toString();
            String t0 = t instanceof Var ? ((Var) t).toStringFlattened() : t.toString();
            return (h0 + '|' + t0);
        }
    }

    private String toString0_bracket() {
        if (subCount == 0) {
            return "";
        } else if (subCount == 1 && !((subs[0] instanceof Struct) && ((Struct) subs[0]).name().equals(","))) {
            return subs[0].term().toString();
        } else {
            
            Term head = ((Struct) subs[0]).subResolve(0);
            Term tail = ((Struct) subs[0]).subResolve(1);
            StringBuilder buf = new StringBuilder(head.toString());
            while (tail instanceof Struct && ((Struct) tail).name().equals(",")) {
                head = ((Struct) tail).subResolve(0);
                buf.append(',').append(head);
                tail = ((Struct) tail).subResolve(1);
            }
            buf.append(',').append(tail);
            return buf.toString();
            
        }
    }

    private String toStringAsList(OperatorManager op) {
        Term h = subs[0];
        Term t = subs[1].term();
        if (t.isList()) {
            Struct tl = (Struct) t;
            if (tl.isEmptyList()) {
                return h.toStringAsArgY(op, 0);
            }
            return (h.toStringAsArgY(op, 0) + ',' + tl.toStringAsList(op));
        } else {
            return (h.toStringAsArgY(op, 0) + '|' + t.toStringAsArgY(op, 0));
        }
    }

    @Override
    String toStringAsArg(OperatorManager op, int prio, boolean x) {

        if (name.equals(".") && subCount == 2) {
            return subs[0].isEmptyList() ? "[]" : '[' + toStringAsList(op) + ']';
        } else if (name.equals("{}")) {
            return ('{' + toString0_bracket() + '}');
        }

        int p = 0;
        if (subCount == 2) {
            if ((p = op.opPrio(name, "xfx")) >= OperatorManager.OP_LOW) {
                return (
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                                subs[0].toStringAsArgX(op, p) +
                                ' ' + name + ' ' +
                                subs[1].toStringAsArgX(op, p) +
                                ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name, "yfx")) >= OperatorManager.OP_LOW) {
                return (
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                                subs[0].toStringAsArgY(op, p) +
                                ' ' + name + ' ' +
                                subs[1].toStringAsArgX(op, p) +
                                ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name, "xfy")) >= OperatorManager.OP_LOW) {
                return !name.equals(",") ? ((x ? p >= prio : p > prio) ? "(" : "") +
                        subs[0].toStringAsArgX(op, p) +
                        ' ' + name + ' ' +
                        subs[1].toStringAsArgY(op, p) +
                        ((x ? p >= prio : p > prio) ? ")" : "") : ((x ? p >= prio : p > prio) ? "(" : "") +
                        subs[0].toStringAsArgX(op, p) +
                        
                        ',' +
                        subs[1].toStringAsArgY(op, p) +
                        ((x ? p >= prio : p > prio) ? ")" : "");
            }
        } else if (subCount == 1) {
            if ((p = op.opPrio(name, "fx")) >= OperatorManager.OP_LOW) {
                return (
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                                name + ' ' +
                                subs[0].toStringAsArgX(op, p) +
                                ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name, "fy")) >= OperatorManager.OP_LOW) {
                return (
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                                name + ' ' +
                                subs[0].toStringAsArgY(op, p) +
                                ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name, "xf")) >= OperatorManager.OP_LOW) {
                return (
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                                subs[0].toStringAsArgX(op, p) +
                                ' ' + name + ' ' +
                                ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name, "yf")) >= OperatorManager.OP_LOW) {
                return (
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                                subs[0].toStringAsArgY(op, p) +
                                ' ' + name + ' ' +
                                ((x ? p >= prio : p > prio) ? ")" : ""));
            }
        }
        String v = (Parser.isAtom(name) ? name : '\'' + name + '\'');
        if (subCount == 0) {
            return v;
        }
        v = v + '(';
        for (p = 1; p < subCount; p++) {
            v = v + subs[p - 1].toStringAsArgY(op, 0) + ',';
        }
        v = v + subs[subCount - 1].toStringAsArgY(op, 0);
        v = v + ')';
        return v;
    }

    @Override
    public Term iteratedGoalTerm() {
        return ((subCount == 2) && name.equals("^")) ?
                subResolve(1).iteratedGoalTerm() : super.iteratedGoalTerm();
    }

    /**/

    /**
     * This class represents an iterator through the arguments of a Struct list.
     *
     * @see Struct
     */
    static class StructIterator implements Iterator<Term>, java.io.Serializable {
        Struct list;

        StructIterator(Struct t) {
            this.list = t;
        }

        @Override
        public boolean hasNext() {
            return !list.isEmptyList();
        }

        @Override
        public Term next() {
            if (list.isEmptyList())
                throw new NoSuchElementException();
            
            
            
            Term head = list.subResolve(0);
            list = (Struct) list.subResolve(1);
            return head;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
}