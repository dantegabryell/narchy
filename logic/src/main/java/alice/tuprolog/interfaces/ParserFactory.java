package alice.tuprolog.interfaces;

import alice.tuprolog.OperatorManager;
import alice.tuprolog.Parser;
import alice.tuprolog.Term;

import java.util.HashMap;

public class ParserFactory {
	
	/**
     * Creating a parser with default operator interpretation
     */
	public static IParser createParser(String theory) {
		return new Parser(theory);
	}
	
	/**
     * creating a parser with default operator interpretation
     */
    public static IParser createParser(String theory, HashMap<Term, Integer> mapping) {
    	return new Parser(theory, mapping);
    }    
	
	/**
     * creating a Parser specifing how to handle operators
     * and what text to parse
     */
    public static IParser createParser(OperatorManager op, String theory) {
    	return new Parser(op, theory);
    }
    
    /**
     * creating a Parser specifing how to handle operators
     * and what text to parse
     */
    public static IParser createParser(OperatorManager op, String theory, HashMap<Term, Integer> mapping) {
    	return new Parser(op, theory, mapping);
    }

}
