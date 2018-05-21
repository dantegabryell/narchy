package jcog.grammar.parse;

import jcog.grammar.parse.tokens.Token;
import jcog.grammar.parse.tokens.TokenAssembly;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public abstract class AbstractParsingTest {

	TokenAssembly assembly;

	protected void assertCompleteMatch(String text) {
		assertNotNull(completeMatch(text));
	}

	protected Assembly completeMatch(String text) {
		assembly = new TokenAssembly(text);
		return getParser().completeMatch(assembly);
	}

	protected void assertNoCompleteMatch(String text) {
		assertNull(completeMatch(text));
	}

	protected Assembly bestMatch(String text) {
		assembly = new TokenAssembly(text);
		Assembly bestMatch = getParser().bestMatch(assembly);
		return bestMatch;
	}

	abstract protected Parser getParser();

	protected Object popValueFromAssembly(Assembly result) {
		return ((Token) result.getStack().pop()).value();
	}

	protected void assertNoMatch(String text) {
		assertNull(bestMatch(text));
	}

}