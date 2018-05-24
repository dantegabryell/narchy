package jcog.grammar.parse;

import jcog.grammar.parse.tokens.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PopAllMatchesTest extends AbstractParsingTest {

	private Parser parser;
	private Assembly result;

	@Test
	public void empty() {
		parser = new Empty();
		result = bestMatch("");
		assertTrue(result.popAllMatches().isEmpty());
		assertEquals(1, result.getStackSizesBeforeMatch().size());
	}

	public void terminal() {
		parser = new Word();
		result = bestMatch("allo");
		assertEquals(1, result.popAllMatches().size());
		assertEquals(1, result.getStackSizesBeforeMatch().size());

		parser = new Num();
		result = bestMatch("2.1");
		assertEquals(1, result.popAllMatches().size());
		assertEquals(1, result.getStackSizesBeforeMatch().size());

		parser = new Symbol("+");
		result = bestMatch("+");
		assertEquals(1, result.popAllMatches().size());
		assertEquals(1, result.getStackSizesBeforeMatch().size());

		parser = new Literal("abc");
		result = bestMatch("abc");
		assertEquals(1, result.popAllMatches().size());
		assertEquals(1, result.getStackSizesBeforeMatch().size());
	}

	public void sequence() {
		parser = new Sequence().get(new Literal("abc"));
		result = bestMatch("abc");
		List<Object> allMatches = result.popAllMatches();
		assertEquals(1, allMatches.size());
		assertEquals(1, result.getStackSizesBeforeMatch().size());

		parser = new Sequence().get(new Literal("abc")).get(new Word()).get(new Symbol(",").ok());
		result = bestMatch("abc hello,");
		allMatches = result.popAllMatches();
		assertEquals(2, allMatches.size());
		Assertions.assertEquals(new Token("abc"), allMatches.get(0));
		assertEquals(new Token("hello"), allMatches.get(1));
		assertEquals(1, result.getStackSizesBeforeMatch().size());
	}

	public void alternation() {
		parser = new Alternation().get(new Literal("abc")).get(new Literal("def"));
		result = bestMatch("abc");
		List<Object> allMatches = result.popAllMatches();
		assertEquals(1, allMatches.size());
		assertEquals(new Token("abc"), allMatches.get(0));
		assertEquals(1, result.getStackSizesBeforeMatch().size());
	}

	public void repetition() {
		parser = new Repetition(new Word());
		result = bestMatch("");
		assertEquals(0, result.popAllMatches().size());
		assertEquals(1, result.getStackSizesBeforeMatch().size());

		result = bestMatch("a b c d");
		List<Object> allMatches = result.popAllMatches();
		assertEquals(4, allMatches.size());
		assertEquals(new Token("a"), allMatches.get(0));
		assertEquals(new Token("d"), allMatches.get(3));
		assertEquals(1, result.getStackSizesBeforeMatch().size());
	}

	@Test
	public void repetitionOfSequence() {
		Parser seq = new Sequence().get(new Word());
		parser = new Repetition(seq);

		result = bestMatch("");
		assertEquals(0, result.popAllMatches().size());
		assertEquals(1, result.getStackSizesBeforeMatch().size());

		result = bestMatch("abc");
		assertEquals(1, result.popAllMatches().size());
		assertEquals(1, result.getStackSizesBeforeMatch().size());

		result = bestMatch("abc def");
		assertEquals(2, result.popAllMatches().size());
		assertEquals(1, result.getStackSizesBeforeMatch().size());
	}

	@Test
	public void sequenceOfRepetition() {
		Parser rep = new Repetition(new Word()).put(new IAssembler() {
			public void accept(Assembly a) {
				List<Object> list = a.popAllMatches();
				a.push(list);
			}
		});
		parser = new Sequence().get(new Symbol("(")).get(rep).get(new Symbol(")"));
		result = bestMatch("(a b c d)");
		List<Object> allMatches = result.popAllMatches();
		assertEquals(3, allMatches.size());
		List<Object> list = (List<Object>) allMatches.get(1);
		assertEquals(4, list.size());
	}

	@Test
	public void stackManipulatedByAssemblers() {
		Consumer<Assembly> deleteAssembler = new IAssembler() {
			public void accept(Assembly a) {
				a.pop();
			}
		};
		Consumer<Assembly> changeToStringAssembler = new IAssembler() {
			public void accept(Assembly a) {
				a.push(((Token) a.pop()).sval());
			}
		};
		parser = new Sequence().get(new Literal("abc").put(deleteAssembler)).get(new Word().put(changeToStringAssembler)).get(new Symbol(",").ok());
		result = bestMatch("abc hello,");
		List<Object> allMatches = result.popAllMatches();
		assertEquals(1, allMatches.size());
		assertEquals("hello", allMatches.get(0));
		assertEquals(1, result.getStackSizesBeforeMatch().size());
	}

	@Test
	public void commaSeparatedListInBrackets() {
		Consumer<Assembly> changeToStringAssembler = new IAssembler() {
			public void accept(Assembly a) {
				a.push(((Token) a.pop()).sval());
			}
		};
		Sequence commaList = (Sequence) new Sequence().get(new Word().put(changeToStringAssembler));
		Parser commaTerm = new Sequence().get(new Symbol(",").ok()).get(new Word().put(changeToStringAssembler));
		commaList.get(new Repetition(commaTerm));
		Parser content = new Alternation().get(new Empty()).get(commaList);
		parser = new Sequence().get(new Symbol("[").ok()).get(content).get(new Symbol("]").ok());

		result = bestMatch("[]");
		assertTrue(result.popAllMatches().isEmpty());

		result = bestMatch("[a]");
		List<Object> allMatches = result.popAllMatches();
		assertEquals(1, allMatches.size());
		assertEquals("a", allMatches.get(0));

		result = bestMatch("[a, b, c]");
		allMatches = result.popAllMatches();
		assertEquals(3, allMatches.size());
		assertEquals("a", allMatches.get(0));
		assertEquals("b", allMatches.get(1));
		assertEquals("c", allMatches.get(2));
	}

	@Override
	protected Parser getParser() {
		return parser;
	}

}
