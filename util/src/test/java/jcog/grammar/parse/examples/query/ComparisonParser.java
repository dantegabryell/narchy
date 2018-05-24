package jcog.grammar.parse.examples.query;

import jcog.grammar.parse.Alternation;
import jcog.grammar.parse.Parser;
import jcog.grammar.parse.Repetition;
import jcog.grammar.parse.Sequence;
import jcog.grammar.parse.examples.logic.ArithmeticAssembler;
import jcog.grammar.parse.examples.logic.AtomAssembler;
import jcog.grammar.parse.tokens.Num;
import jcog.grammar.parse.tokens.QuotedString;
import jcog.grammar.parse.tokens.Symbol;
import jcog.grammar.parse.tokens.Word;

/**
 * This utility class provides support to the Jaql 
 * parser, specifically for <code>expression()</code>
 * and <code>comparison()</code> subparsers.
 *
 * The grammar this class supports is:
 *
 * <blockquote><pre>	
 *     comparison = arg operator arg;
 *     arg        = expression | QuotedString;
 *     expression = term ('+' term | '-' term)*;
 *     term       = factor ('*' factor | '/' factor)*;
 *     factor     = '(' expression ')' | Num | variable;
 *     variable   = Word;
 *     operator   = "<" | ">" | "=" | "<=" | ">=" | "!=";
 * </pre></blockquote>
 * 
 * @author Steven J. Metsker
 *
 * @version 1.0 
 */
public class ComparisonParser {
	protected Sequence expression;
	protected Speller speller;

	/**
	 * Construct a ComparisonParser that will consult the
	 * given speller for the proper spelling of variable
	 * names.
	 */
	public ComparisonParser(Speller speller) {
		this.speller = speller;
	}

	/**
	 * Returns a parser that will recognize a comparison
	 * argument.
	 */
	public Parser arg() {

		// arg = expression | QuotedString;

		Alternation a = new Alternation();
		a.get(expression());
		a.get(new QuotedString().put(new AtomAssembler()));
		return a;
	}

	/**
	 * Returns a parser that will recognize a comparison.
	 */
	public Parser comparison() {
		Sequence s = new Sequence("comparison");
		s.get(arg());
		s.get(operator());
		s.get(arg());
		s.put(new ComparisonAssembler());
		return s;
	}

	/*
	 * Recognize '/' followed by a factor.
	 */
	protected Parser divideFactor() {
		Sequence s = new Sequence("divideFactor");
		s.get(new Symbol('/').ok());
		s.get(factor());
		s.put(new ArithmeticAssembler('/'));
		return s;
	}

	/**
	 * Returns a parser that will recognize an arithmetic
	 * expression.
	 */
	public Parser expression() {
		/*
		 * This use of a static variable avoids the infinite 
		 * recursion inherent in the language definition.
		 */
		if (expression == null) {

			// expression = term ('+' term | '-' term)*;
			expression = new Sequence("expression");
			expression.get(term());

			// second part
			Alternation a = new Alternation();
			a.get(plusTerm());
			a.get(minusTerm());
			expression.get(new Repetition(a));
		}
		return expression;
	}

	/*
	 * Recognize an expression in parens, or a number, or a
	 * variable.
	 */
	protected Parser factor() {
		// factor = '(' expression ')' | Num | variable;
		Alternation factor = new Alternation("factor");

		//  '(' expression ')'
		Sequence s = new Sequence();
		s.get(new Symbol('(').ok());
		s.get(expression());
		s.get(new Symbol(')').ok());
		factor.get(s);

		// Num | variable
		factor.get(new Num().put(new AtomAssembler()));
		factor.get(variable());

		return factor;
	}

	/*
	 * Recognize '-' followed by a term.
	 */
	protected Parser minusTerm() {
		Sequence s = new Sequence("minusTerm");
		s.get(new Symbol('-').ok());
		s.get(term());
		s.put(new ArithmeticAssembler('-'));
		return s;
	}

	/*
	 * Recognize an operator.
	 */
	protected Parser operator() {
		Alternation a = new Alternation("operator");
		a.get(new Symbol('<'));
		a.get(new Symbol('>'));
		a.get(new Symbol('='));
		a.get(new Symbol("<="));
		a.get(new Symbol(">="));
		a.get(new Symbol("!="));
		return a;
	}

	/*
	 * Recognize '+' followed by a term.
	 */
	protected Parser plusTerm() {
		Sequence s = new Sequence("plusTerm");
		s.get(new Symbol('+').ok());
		s.get(term());
		s.put(new ArithmeticAssembler('+'));
		return s;
	}

	/*
	 * Recognize a "term", per the language definition.
	 */
	protected Parser term() {
		// term = factor ('*' factor | '/' factor)*;
		Sequence term = new Sequence("term");

		// first part
		term.get(factor());

		// second part
		Alternation a = new Alternation();
		a.get(timesFactor());
		a.get(divideFactor());

		term.get(new Repetition(a));
		return term;
	}

	/*
	 * Recognize '*' followed by a factor.
	 */
	protected Parser timesFactor() {
		Sequence s = new Sequence("timesFactor");
		s.get(new Symbol('*').ok());
		s.get(factor());
		s.put(new ArithmeticAssembler('*'));
		return s;
	}

	/*
	 * Recognizes any word.
	 */
	protected Parser variable() {
		return new Word().put(new VariableAssembler(speller));
	}
}
