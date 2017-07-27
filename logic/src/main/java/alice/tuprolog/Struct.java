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
    private Term[] arg;
    /**
	 * arity
	 */
    private int arity;
    /**
	 * to speedup hash map operation
	 */
    private String predicateIndicator;
    /**
	 * primitive behaviour
	 */
    private transient PrimitiveInfo primitive;
    /**
	 * it indicates if the term is resolved
	 */
    private boolean resolved;
    
    /**
     * Builds a Struct representing an atom
     */
    public Struct(String f) {
        this(f,0);
    }
    
    /**
     * Builds a compound, with one argument
     */
    public Struct(String f, Term at0) {
        this(f, new Term[] {at0});
    }
    
    /**
     * Builds a compound, with two arguments
     */
    public Struct(String f, Term at0, Term at1) {
        this(f, new Term[] {at0, at1});
    }
    
    /**
     * Builds a compound, with three arguments
     */
    public Struct(String f, Term at0, Term at1, Term at2) {
        this(f, new Term[] {at0, at1, at2});
    }
    
    /**
     * Builds a compound, with four arguments
     */
    public Struct(String f, Term at0, Term at1, Term at2, Term at3) {
        this(f, new Term[] {at0, at1, at2, at3});
    }
    
    /**
     * Builds a compound, with five arguments
     */
    public Struct(String f, Term at0, Term at1, Term at2, Term at3, Term at4) {
        this(f, new Term[] {at0, at1, at2, at3, at4});
    }
    
    /**
     * Builds a compound, with six arguments
     */
    public Struct(String f, Term at0, Term at1, Term at2, Term at3, Term at4, Term at5) {
        this(f, new Term[] {at0, at1, at2, at3, at4, at5});
    }
    
    /**
     * Builds a compound, with seven arguments
     */
    public Struct(String f, Term at0, Term at1, Term at2, Term at3, Term at4, Term at5, Term at6) {
        this(f, new Term[] {at0, at1, at2, at3, at4, at5, at6});
    }
    
    /**
     * Builds a compound, with an array of arguments
     */
    public Struct(String f, Term[] argList) {
        this(f, argList.length);
        for (int i = 0; i < argList.length; i++)
            if (argList[i] == null)
                throw new InvalidTermException("Arguments of a Struct cannot be null");
            else
                arg[i] = argList[i];
    }
    
    
    /**
     * Builds a structure representing an empty list
     */
    public Struct() {
        this("[]", 0);
        resolved = true;
    }
    
    
    /**
     * Builds a list providing head and tail
     */
    public Struct(Term h,Term t) {
        this(".",2);
        arg[0] = h;
        arg[1] = t;
    }
    
    /**
     * Builds a list specifying the elements
     */
    public Struct(Term[] argList) {
        this(argList,0);
    }
    
    private Struct(Term[] argList, int index) {
        this(".",2);
        if (index<argList.length) {
            arg[0] = argList[index];
            arg[1] = new Struct(argList,index+1);
        } else {
            // build an empty list
            name = "[]";
            arity = 0;
            arg = null;
        }
    }
    
    /**
     * Builds a compound, with a linked list of arguments
     */
    Struct(String f, LinkedList<Term> al) {
        name = f;
        arity = al.size();
        if (arity > 0) {
            arg = new Term[arity];
            for(int c = 0;c < arity;c++)
                arg[c] = al.removeFirst();
        }
        predicateIndicator = name + '/' + arity;
        resolved = false;
    }
    
    private Struct(int arity_) {
        arity = arity_;
        arg = new Term[arity];
    }
    
    private Struct(String name_,int arity_) {
        if (name_ == null)
            throw new InvalidTermException("The functor of a Struct cannot be null");
        if (name_.isEmpty() && arity_ > 0)
            throw new InvalidTermException("The functor of a non-atom Struct cannot be an empty string");
        name = name_;
        arity = arity_;
        if (arity > 0) {
            arg = new Term[arity];
        }
        predicateIndicator = name + '/' + arity;
        resolved = false;
    }
    

    /**
	 * @return
	 */
    String getPredicateIndicator() {
        return predicateIndicator;
    }
    
    /**
	 * Gets the number of elements of this structure
	 */
    public int getArity() {
        return arity;
    }
    
    /**
	 * Gets the functor name  of this structure
	 */
    public String name() {
        return name;
    }
    
    /**
     * Gets the i-th element of this structure
     *
     * No bound check is done
     */
    public Term term(int index) {
        return arg[index];
    }
    
    /**
     * Sets the i-th element of this structure
     *
     * (Only for internal service)
     */
    void setArg(int index, Term argument) {
        arg[index] = argument;
    }
    
    /**
     * Gets the i-th element of this structure
     *
     * No bound check is done. It is equivalent to
     * <code>getArg(index).getTerm()</code>
     */
    public Term getTerm(int index) {
            if (!(arg[index] instanceof Var))
                return arg[index];
            return arg[index].term();
    }
    
    
    // checking type and properties of the Term
    
    /** is this term a prolog numeric term? */
    @Override
    public boolean isNumber() {
        return false;
    }
    
    /** is this term a struct  */
    @Override
    public boolean isStruct() {
        return true;
    }
    
    /** is this term a variable  */
    @Override
    public boolean isVar() {
        return false;
    }
    
    
    // check type services
    
    @Override
    public boolean isAtomic() {
        return  arity == 0;
    }
    
    @Override
    public boolean isCompound() {
        return arity > 0;
    }
    
    @Override
    public boolean isAtom() {
        return (arity == 0 || isEmptyList());
    }
    
    @Override
    public boolean isList() {
        return (name.equals(".") && arity == 2 && arg[1].isList()) || isEmptyList();
    }
    
    @Override
    public boolean isGround() {
        for (int i=0; i<arity; i++) {
            if (!arg[i].isGround()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check is this struct is clause or directive
     */
    public boolean isClause() {
        return(name.equals(":-") && arity > 1 && arg[0].term() instanceof Struct);
        //return(name.equals(":-") && arity == 2 && arg[0].getTerm() instanceof Struct);
    }    
    
    @Override
    public Term term() {
        return this;
    }
    
    //
    
    /**
     * Gets an argument inside this structure, given its name
     * 
     * @param name name of the structure 
     * @return the argument or null if not found
     */
    public Struct term(String name) {
        if (arity == 0) {
            return null;
        }
        for (int i=0; i<arg.length; i++) {
            if (arg[i] instanceof Struct) {
                Struct s = (Struct) arg[i];
                if (s.name().equals(name)) {
                    return s;
                }
            }
        }
        for (int i=0; i<arg.length; i++) {
            if (arg[i] instanceof Struct) {
                Struct s = (Struct)arg[i];
                Struct sol = s.term(name);
                if (sol!=null) {
                    return sol;
                }
            }
        }
        return null;
    }
    
    
    //
    
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
            int tarity = ts.arity;
            if (arity > tarity) {
                return true;
            } else if (arity == tarity) {
            	if (name.compareTo(ts.name) > 0) {
                    return true;
                } else if (name.compareTo(ts.name) == 0) {
                    for (int c = 0;c < arity;c++) {
                    	if (arg[c].isGreater(ts.arg[c])) {
                            return true;
                        } else if (!arg[c].isEqual(ts.arg[c])) {
                            return false;
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
            int tarity = ts.arity;
            if (arity > tarity) {
                return true;
            } else if (arity == tarity) {
            	//System.out.println("Compare di "+name+" con "+ts.name);
                if (name.compareTo(ts.name) > 0) {
                    return true;
                } else if (name.compareTo(ts.name) == 0) {
                    for (int c = 0;c < arity;c++) {
                    	//System.out.println("Compare di "+arg[c]+" con "+ts.arg[c]);
                        if (arg[c].isGreaterRelink(ts.arg[c],vorder)) {
                            return true;
                        } else if (!arg[c].isEqual(ts.arg[c])) {
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
        if (t instanceof Struct) {
            Struct ts = (Struct) t;
            if (arity == ts.arity && name.equals(ts.name)) {
                for (int c = 0;c < arity;c++) {
                    if (!arg[c].isEqual(ts.arg[c])) {
                        return false;
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
    
    //
    
    
    /**
     * Gets a copy of this structure
     * @param vMap is needed for register occurence of same variables
     */
    @Override
    Term copy(AbstractMap<Var,Var> vMap, int idExecCtx) {
        Struct t = new Struct(arity);
        t.resolved  = resolved;
        t.name      = name;
        t.predicateIndicator   = predicateIndicator;
        t.primitive = primitive;
        final int arity = this.arity;
        Term[] targ = t.arg;
        Term[] arg = this.arg;
        for (int c = 0;c < arity;c++) {
            targ[c] = arg[c].copy(vMap, idExecCtx);
        }
        return t;
    }
    
    
    /**
     * Gets a copy of this structure
     * @param vMap is needed for register occurence of same variables
     */
    @Override
    Term copy(AbstractMap<Var,Var> vMap, AbstractMap<Term,Var> substMap) {
        Struct t = new Struct(arity);
        t.resolved  = false;
        t.name      = name;
        t.predicateIndicator   = predicateIndicator;
        t.primitive = null;
        Term[] thatArg = t.arg;
        Term[] thisArg = this.arg;
        final int arity = this.arity;
        for (int c = 0;c < arity;c++) {
            thatArg[c] = thisArg[c].copy(vMap, substMap);
        }
        return t;
    }
    
    
    /**
     * resolve term
     */
    @Override
    long resolveTerm(long count) {
        return resolved ? count : resolveTerm(new LinkedList<>(), count);
    }
    
    
    /**
     * Resolve name of terms
     * @param vl list of variables resolved
     * @param count start timestamp for variables of this term
     * @return next timestamp for other terms
     */
    long resolveTerm(LinkedList<Var> vl,long count) {
        long newcount=count;

        Term[] arg = this.arg;
        int arity = this.arity;
        for (int c = 0;c < arity;c++) {
            Term term= arg[c];
            if (term!=null) {
                //--------------------------------
                // we want to resolve only not linked variables:
                // so linked variables must get the linked term
                term=term.term();
                //--------------------------------
                if (term instanceof Var) {
                    Var t = (Var) term;
                    t.setTimestamp(newcount++);
                    if (!t.isAnonymous()) {
                        // searching a variable with the same name in the list
                        String name= t.getName();
                        Iterator<Var> it = vl.iterator();
                        Var found = null;
                        while (it.hasNext()) {
                            Var vn = it.next();
                            if (name.equals(vn.getName())) {
                                found=vn;
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
                    newcount = ( (Struct) term ).resolveTerm(vl,newcount);
                }
            }
        }
        resolved = true;
        return newcount;
    }
    
    // services for list structures
    
    /**
     * Is this structure an empty list?
     */
    @Override
    public boolean isEmptyList() {
        return arity == 0 && name.equals("[]");
    }
    
    /**
     * Gets the head of this structure, which is supposed to be a list.
     * 
     * <p>
     * Gets the head of this structure, which is supposed to be a list.
     * If the callee structure is not a list, throws an <code>UnsupportedOperationException</code>
     * </p>
     */
    public Term listHead() {
        if (!isList())
            throw new UnsupportedOperationException("The structure " + this + " is not a list.");
        return arg[0].term();
    }
    
    /**
     * Gets the tail of this structure, which is supposed to be a list.
     * 
     * <p>
     * Gets the tail of this structure, which is supposed to be a list.
     * If the callee structure is not a list, throws an <code>UnsupportedOperationException</code>
     * </p>
     */
    public Struct listTail() {
        if (!isList())
            throw new UnsupportedOperationException("The structure " + this + " is not a list.");
        return (Struct) arg[1].term() ;
    }
    
    /**
     * Gets the number of elements of this structure, which is supposed to be a list.
     * 
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
            t = (Struct) t.arg[1].term();
        }
        return count;
    }
    
    /**
     * Gets an iterator on the elements of this structure, which is supposed to be a list.
     * 
     * <p>
     * Gets an iterator on the elements of this structure, which is supposed to be a list.
     * If the callee structure is not a list, throws an <code>UnsupportedOperationException</code>
     * </p>
     */
    public Iterator<? extends Term> listIterator() {
        if (!isList())
            throw new UnsupportedOperationException("The structure " + this + " is not a list.");
        return new StructIterator(this);
    }
    
    // hidden services
    
    /**
     * Gets a list Struct representation, with the functor as first element.
     */
    Struct toList() {
        Struct t = new Struct();
        Term[] arg = this.arg;
        for(int c = arity - 1;c >= 0;c--) {
            t = new Struct(arg[c].term(),t);
        }
        return new Struct(new Struct(name),t);
    }
    
    
    /**
     * Gets a flat Struct from this structure considered as a List
     *
     * If this structure is not a list, null object is returned
     */
    Struct fromList() {
        Term ft = arg[0].term();
        if (!ft.isAtom()) {
            return null;
        }
        Struct at = (Struct) arg[1].term();
        LinkedList<Term> al = new LinkedList<>();
        while (!at.isEmptyList()) {
            if (!at.isList()) {
                return null;
            }
            al.addLast(at.getTerm(0));
            at = (Struct) at.getTerm(1);
        }
        return new Struct(((Struct) ft).name, al);
    }
    
    
    /**
     * Appends an element to this structure supposed to be a list
     */
    public void append(Term t) {
        if (isEmptyList()) {
            name = ".";
            arity = 2;
                        predicateIndicator = name + '/' + arity; /* Added by Paolo Contessi */
            arg = new Term[arity];
            arg[0] = t; arg[1] = new Struct();
        } else if (arg[1].isList()) {
            ((Struct) arg[1]).append(t);
        } else {
            arg[1] = t;
        }
    }
    
    
    /**
     * Inserts (at the head) an element to this structure supposed to be a list
     */
    void insert(Term t) {
        Struct co=new Struct();
        co.arg[0]=arg[0];
        co.arg[1]=arg[1];
        arg[0] = t;
        arg[1] = co;
    }
    
    //
    
    /**
     * Try to unify two terms
     * @param t the term to unify
     * @param vl1 list of variables unified
     * @param vl2 list of variables unified
     * @return true if the term is unifiable with this one
     */
    @Override
    boolean unify(List<Var> vl1,List<Var> vl2,Term t) {
        // In fase di unificazione bisogna annotare tutte le variabili della struct completa.
        t = t.term();
        if (t instanceof Struct) {
            Struct ts = (Struct) t;
            final int arity = this.arity;
            if ( arity == ts.arity && name.equals(ts.name)) {
                Term[] arg = this.arg;
                Term[] tsarg = ts.arg;
                for (int c = 0; c < arity;c++) {
                    if (!arg[c].unify(vl1,vl2, tsarg[c])) {
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
    
    
    /** dummy method */
    @Override
    public void free() {}
    
    //
    
    /**
	 * Set primitive behaviour associated at structure
	 */
    void setPrimitive(PrimitiveInfo b) {
        primitive = b;
    }
    
    /**
	 * Get primitive behaviour associated at structure
	 */
    public PrimitiveInfo getPrimitive() {
        return primitive;
    }

    
    /**
     * Check if this term is a primitive struct
     */
    public boolean isPrimitive() {
        return primitive != null;
    }
    
    //
    
    /**
     * Gets the string representation of this structure
     *
     * Specific representations are provided for lists and atoms.
     * Names starting with upper case letter are enclosed in apices.
     */
    public String toString() {
        // empty list case
        if (isEmptyList()) return "[]";
        // list case
        if (name.equals(".") && arity == 2) {
            return ('[' + toString0() + ']');
        } else if (name.equals("{}")) {
            return ('{' + toString0_bracket() + '}');
        } else {
            String s = (Parser.isAtom(name) ? name : '\'' + name + '\'');
            if (arity > 0) {
                s = s + '(';
                for (int c = 1;c < arity;c++) {
                    s = s + (!(arg[c - 1] instanceof Var) ? arg[c - 1].toString() : ((Var) arg[c - 1]).toStringFlattened()) + ',';
                }
                s = s + (!(arg[arity - 1] instanceof Var) ? arg[arity - 1].toString() : ((Var) arg[arity - 1]).toStringFlattened()) + ')';
            }
            return s;
        }
    }
    
    private String toString0() {
        Term h = arg[0].term();
        Term t = arg[1].term();
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
        if (arity == 0) {
            return "";
        } else if (arity==1 && !((arg[0] instanceof Struct) && ((Struct)arg[0]).name().equals(","))){
            return arg[0].term().toString();
        } else {
            // comma case 
            Term head = ((Struct)arg[0]).getTerm(0);
            Term tail = ((Struct)arg[0]).getTerm(1);
            StringBuilder buf = new StringBuilder(head.toString());
            while (tail instanceof Struct && ((Struct)tail).name().equals(",")){
                head = ((Struct)tail).getTerm(0);
                buf.append(',').append(head);
                tail = ((Struct)tail).getTerm(1);
            }
            buf.append(',').append(tail);
            return buf.toString();
            //    return arg[0]+","+((Struct)arg[1]).toString0_bracket();
        }
    }
    
    private String toStringAsList(OperatorManager op) {
        Term h = arg[0];
        Term t = arg[1].term();
        if (t.isList()) {
            Struct tl = (Struct)t;
            if (tl.isEmptyList()){
                return h.toStringAsArgY(op,0);
            }
            return (h.toStringAsArgY(op,0) + ',' + tl.toStringAsList(op));
        } else {
            return (h.toStringAsArgY(op,0) + '|' + t.toStringAsArgY(op,0));
        }
    }
    
    @Override
    String toStringAsArg(OperatorManager op, int prio, boolean x) {

        if (name.equals(".") && arity == 2) {
            return arg[0].isEmptyList() ? "[]" : '[' + toStringAsList(op) + ']';
        } else if (name.equals("{}")) {
            return('{' + toString0_bracket() + '}');
        }

        int p = 0;
        if (arity == 2) {
            if ((p = op.opPrio(name,"xfx")) >= OperatorManager.OP_LOW) {
                return(
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                        arg[0].toStringAsArgX(op,p) +
                                ' ' + name + ' ' +
                        arg[1].toStringAsArgX(op,p) +
                        ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name,"yfx")) >= OperatorManager.OP_LOW) {
                return(
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                        arg[0].toStringAsArgY(op,p) +
                                ' ' + name + ' ' +
                        arg[1].toStringAsArgX(op,p) +
                        ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name,"xfy")) >= OperatorManager.OP_LOW) {
                return !name.equals(",") ? ((x ? p >= prio : p > prio) ? "(" : "") +
                        arg[0].toStringAsArgX(op, p) +
                        ' ' + name + ' ' +
                        arg[1].toStringAsArgY(op, p) +
                        ((x ? p >= prio : p > prio) ? ")" : "") : ((x ? p >= prio : p > prio) ? "(" : "") +
                        arg[0].toStringAsArgX(op, p) +
                        //",\n\t"+
                        ',' +
                        arg[1].toStringAsArgY(op, p) +
                        ((x ? p >= prio : p > prio) ? ")" : "");
            }
        }
        else if (arity == 1) {
            if ((p = op.opPrio(name,"fx")) >= OperatorManager.OP_LOW) {
                return(
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                        name + ' ' +
                        arg[0].toStringAsArgX(op,p) +
                        ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name,"fy")) >= OperatorManager.OP_LOW) {
                return(
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                        name + ' ' +
                        arg[0].toStringAsArgY(op,p) +
                        ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name,"xf")) >= OperatorManager.OP_LOW) {
                return(
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                        arg[0].toStringAsArgX(op,p) +
                                ' ' + name + ' ' +
                        ((x ? p >= prio : p > prio) ? ")" : ""));
            }
            if ((p = op.opPrio(name,"yf")) >= OperatorManager.OP_LOW) {
                return(
                        ((x ? p >= prio : p > prio) ? "(" : "") +
                        arg[0].toStringAsArgY(op,p) +
                                ' ' + name + ' ' +
                        ((x ? p >= prio : p > prio) ? ")" : ""));
            }
        }
        String v = (Parser.isAtom(name) ? name : '\'' + name + '\'');
        if (arity == 0) {
            return v;
        }
        v = v + '(';
        for (p = 1;p < arity;p++) {
            v = v + arg[p - 1].toStringAsArgY(op,0) + ',';
        }
        v = v + arg[arity - 1].toStringAsArgY(op,0);
        v = v + ')';
        return v;
    }
    
    @Override
    public Term iteratedGoalTerm() {
        return ((arity == 2) && name.equals("^")) ?
                getTerm(1).iteratedGoalTerm() : super.iteratedGoalTerm();
    }
    
    /*Castagna 06/2011*/
    @Override
	public void accept(TermVisitor tv) {
		tv.visit(this);
	}
    /**/
    
}