/*
 * tuProlog - Copyright (C) 2001-2002  aliCE team at deis.unibo.it
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

import java.io.*;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * BNF for tuProlog
 *
 * part 1: Lexer
 *      digit ::= 0 .. 9
 *      lc_letter ::= a .. z
 *      uc_letter ::= A .. Z | _
 *      symbol ::= \ | $ | & | ^ | @ | # | . | , | : | ; | = | < | > | + | - | * | / | ~

 *      letter ::= digit | lc_letter | uc_letter
 *      integer ::= { digit }+
 *      float ::= { digit }+ . { digit }+ [ E|e [ +|- ] { digit }+ ]
 *                                                                           
 *      atom ::= lc_letter { letter }* | !
 *      variable ::= uc_letter { letter }*
 *
 * from the super class, the super.nextToken() returns and updates the following relevant fields:
 * - if the next token is a collection of wordChars,
 * the type returned is TT_WORD and the value is put into the field sval.
 * - if the next token is an ordinary char,
 * the type returned is the same as the unicode int value of the ordinary character
 * - other characters should be handled as ordinary characters.
 */
public class Tokenizer extends StreamTokenizer implements Serializable {
	private static final long serialVersionUID = 1L;
    static final int TYPEMASK = 0x00FF;
    static final int ATTRMASK = 0xFF00;
    static final int LPAR = 0x0001;
    static final int RPAR = 0x0002;
    static final int LBRA = 0x0003;
    static final int RBRA = 0x0004;
    static final int BAR = 0x0005;
    static final int INTEGER = 0x0006;
    static final int FLOAT = 0x0007;
    static final int ATOM = 0x0008;
    static final int VARIABLE = 0x0009;
    static final int SQ_SEQUENCE = 0x000A;
    static final int DQ_SEQUENCE = 0x000B;
    static final int END = 0x000D;
    static final int LBRA2 = 0x000E;
    static final int RBRA2 = 0x000F;
    static final int FUNCTOR = 0x0100;
    static final int OPERATOR = 0x0200;
    static final int EOF = 0x1000;

    static final char[] GRAPHIC_CHARS = {'\\', '$', '&', '?', '^', '@', '#', '.', ',', ':', ';', '=', '<', '>', '+', '-', '*', '/', '~'};
    static {
        Arrays.sort(Tokenizer.GRAPHIC_CHARS);  
    }

    /*Castagna 06/2011*/
    /* Francesco Fabbri
     * 15/04/2011
     * Fix line number issue (always -1)
     */
    
    
    private int tokenOffset;
    private int tokenStart;
    private int tokenLength;
    private String text;
    /**/
    
    
    private final Deque<Token> tokenList = new ArrayDeque<>();

    
    private PushBack pushBack2;

    public Tokenizer(String text) {
        this(new StringReader(text));
        /*Castagna 06/2011*/
        this.text = text;
        this.tokenOffset = -1;
        /**/
    }
    /**
     * creating a tokenizer for the source stream
     */
    public Tokenizer(Reader text) {
        super(text);

        
        resetSyntax();

        
        wordChars('a', 'z');
        wordChars('A', 'Z');
        wordChars('_', '_');
        wordChars('0', '9'); 
        

        ordinaryChar('!');

        
        ordinaryChar('\\');
        ordinaryChar('$');
        ordinaryChar('&');
        ordinaryChar('^');
        ordinaryChar('@');
        ordinaryChar('#');
        ordinaryChar(',');
        ordinaryChar('.');
        ordinaryChar(':');
        ordinaryChar(';');
        ordinaryChar('=');
        ordinaryChar('<');
        ordinaryChar('>');
        ordinaryChar('+');
        ordinaryChar('-');
        ordinaryChar('*');
        ordinaryChar('/');
        ordinaryChar('~');

        
        ordinaryChar('\''); 
        ordinaryChar('\"'); 

        
        ordinaryChar('%');
        
        
    }

    /**
     * reads next available token
     */
    /*Castagna 06/2011*/public/**/ Token readToken() throws InvalidTermException, IOException {
        return !tokenList.isEmpty() ? tokenList.removeFirst() : readNextToken();
    }

    /**
     * puts back token to be read again
     */
    void unreadToken(Token token) {
        tokenList.addFirst(token);
    }

    Token readNextToken() throws IOException, InvalidTermException {
        Tokenizer other = this;
        while (true) {
            int typea;
            String svala;
            if (other.pushBack2 != null) {
                typea = other.pushBack2.typea;
                svala = other.pushBack2.svala;
                other.pushBack2 = null;
            } else {
            /*Castagna 06/2011*/
                
                typea = other.tokenConsume();
            /**/
                svala = other.sval;
            }

            
            
            
            while (Tokenizer.isWhite(typea)) {
        	/*Castagna 06/2011*/
                
                typea = other.tokenConsume();
            /**/
                svala = other.sval;
            }

            
            
            if (typea == '%') {
                do {
            	/*Castagna 06/2011*/
                    
                    typea = other.tokenConsume();
                /**/
                } while (typea != '\r' && typea != '\n' && typea != TT_EOF);
            /*Castagna 06/2011*/
                
                other.tokenPushBack();  
            /**/
                continue;
            }

            
            if (typea == '/') {
        	/*Castagna 06/2011*/
                
                int typeb = other.tokenConsume();
        	/**/
                if (typeb == '*') {
                    do {
                        typea = '*';
                	/*Castagna 06/2011*/
                        
                        typeb = other.tokenConsume();
                        if (typea == -1 && typeb == -1)
                            throw new InvalidTermException("Invalid multi-line comment statement");
                    /**/
                    } while (typea != '*' || typeb != '/');
                    continue;
                } else {
            	/*Castagna 06/2011*/
                    
                    other.tokenPushBack();
            	/**/
                }
            }

        /*Castagna 06/2011*/
            
            other.tokenStart = other.tokenOffset - other.tokenLength + 1;
        /**/

            
            if (typea == TT_EOF) return new Token("", Tokenizer.EOF);
            if (typea == '(') return new Token("(", Tokenizer.LPAR);
            if (typea == ')') return new Token(")", Tokenizer.RPAR);
            if (typea == '{') return new Token("{", Tokenizer.LBRA2);
            if (typea == '}') return new Token("}", Tokenizer.RBRA2);
            if (typea == '[') return new Token("[", Tokenizer.LBRA);
            if (typea == ']') return new Token("]", Tokenizer.RBRA);
            if (typea == '|') return new Token("|", Tokenizer.BAR);

            if (typea == '!') return new Token("!", Tokenizer.ATOM);
            if (typea == ',') return new Token(",", Tokenizer.OPERATOR);

            if (typea == '.') { 
        	/*Castagna 06/2011*/
                
                int typeb = other.tokenConsume();
        	/**/
                if (Tokenizer.isWhite(typeb) || typeb == '%' || typeb == StreamTokenizer.TT_EOF)
                    return new Token(".", Tokenizer.END);
                else
            	/*Castagna 06/2011*/
                    
                    other.tokenPushBack();
            	/**/
            }

            boolean isNumber = false;

            
            if (typea == TT_WORD) {
                char firstChar = svala.charAt(0);
                
                if (Character.isUpperCase(firstChar) || '_' == firstChar)
                    return new Token(svala, Tokenizer.VARIABLE);

                else if (firstChar >= '0' && firstChar <= '9')    
                    isNumber = true;                            

                else {                                            
            	/*Castagna 06/2011*/
                    
                    
                    int typeb = other.tokenConsume();                    
                    other.tokenPushBack();                            
            	/**/
                    if (typeb == '(')
                        return new Token(svala, Tokenizer.ATOM | Tokenizer.FUNCTOR);
                    if (Tokenizer.isWhite(typeb))
                        return new Token(svala, Tokenizer.ATOM | Tokenizer.OPERATOR);
                    return new Token(svala, Tokenizer.ATOM);
                }
            }

            
            if (typea == '\'' || typea == '\"' || typea == '`') {
                int qType = typea;
                StringBuilder quote = new StringBuilder();
                while (true) { 
            	/*Castagna 06/2011*/
                    
                    typea = other.tokenConsume();
            	/**/
                    svala = other.sval;
                    
                    if (typea == '\\') {
                	/*Castagna 06/2011*/
                        
                        int typeb = other.tokenConsume();
                	/**/
                        if (typeb == '\n') 
                            continue;
                        if (typeb == '\r') {
                    	/*Castagna 06/2011*/
                            
                            int typec = other.tokenConsume();
                    	/**/
                            if (typec == '\n')
                                continue; 
                    	/*Castagna 06/2011*/
                            
                            other.tokenPushBack();
                    	/**/
                            continue; 
                        }
                    /*Castagna 06/2011*/
                        
                        other.tokenPushBack(); 
                    /**/
                    }
                    
                    if (typea == qType) {
                	/*Castagna 06/2011*/
                        
                        int typeb = other.tokenConsume();
                	/**/
                        if (typeb == qType) { 
                            quote.append((char) qType);
                            continue;
                        } else {
                    	/*Castagna 06/2011*/
                            
                            other.tokenPushBack();
                    	/**/
                            break; 
                        }
                    }
                    if (typea == '\n' || typea == '\r')
                	/*Castagna 06/2011*/
                        
                        throw new InvalidTermException("Line break in quote not allowed");
                	/**/

                    if (svala != null)
                        quote.append(svala);
                    else
                /*Castagna 06/2011*/ {
                        if (typea < 0)
                            throw new InvalidTermException("Invalid string");

                        quote.append((char) typea);
                    }
                /**/
                }

                String quoteBody = quote.toString();

                qType = qType == '\'' ? SQ_SEQUENCE : qType == '\"' ? DQ_SEQUENCE : SQ_SEQUENCE;
                if (qType == SQ_SEQUENCE) {
                    if (Parser.isAtom(quoteBody))
                        qType = ATOM;
                /*Castagna 06/2011*/
                    
                    
                    int typeb = other.tokenConsume(); 
                    other.tokenPushBack();                    
                /**/
                    if (typeb == '(')
                        return new Token(quoteBody, qType | FUNCTOR);
                }
                return new Token(quoteBody, qType);
            }

            
            if (Arrays.binarySearch(Tokenizer.GRAPHIC_CHARS, (char) typea) >= 0) {

            /*Castagna 06/2011*/
                
                
        	/**/
                StringBuilder symbols = new StringBuilder();
                int typeb = typea;
                
                while (Arrays.binarySearch(Tokenizer.GRAPHIC_CHARS, (char) typeb) >= 0) {
                    symbols.append((char) typeb);
                /*Castagna 06/2011*/
                    
                    typeb = other.tokenConsume();
                /**/
                    
                }
            /*Castagna 06/2011*/
                
                other.tokenPushBack();
            /**/

                












                return new Token(symbols.toString(), Tokenizer.OPERATOR);
            }

            
            if (isNumber) {
                try { 

                    
                    if (svala.startsWith("0")) {
                        if (svala.indexOf('b') == 1)
                            return new Token(String.valueOf(java.lang.Long.parseLong(svala.substring(2), 2)), Tokenizer.INTEGER); 
                        if (svala.indexOf('o') == 1)
                            return new Token(String.valueOf(java.lang.Long.parseLong(svala.substring(2), 8)), Tokenizer.INTEGER); 
                        if (svala.indexOf('x') == 1)
                            return new Token(String.valueOf(java.lang.Long.parseLong(svala.substring(2), 16)), Tokenizer.INTEGER); 
                    }

                    
                /*Castagna 06/2011*/
                    
                    int typeb = other.tokenConsume();
                /**/
                    String svalb = other.sval;

                    
                    if (typeb != '.' && typeb != '\'') { 
                	 /*Castagna 06/2011*/
                        
                        other.tokenPushBack(); 
                	/**/
                        return new Token(String.valueOf(java.lang.Long.parseLong(svala)), Tokenizer.INTEGER);
                    }

                    
                    if (typeb == '\'' && "0".equals(svala)) {
                	 /*Castagna 06/2011*/
                        
                        int typec = other.tokenConsume(); 
                	/**/
                        String svalc = other.sval;
                        int intVal;
                        if ((intVal = isCharacterCodeConstantToken(typec, svalc)) != -1)
                            return new Token(String.valueOf(intVal), Tokenizer.INTEGER);

                        
                    /*Castagna 06/2011*/
                        
                        throw new InvalidTermException("Character code constant starting with 0'<X> cannot be recognized.");
                    /**/
                    }

                    
                    java.lang.Long.parseLong(svala); 

                    
                    if (typeb != '.')
                	 /*Castagna 06/2011*/
                        
                        throw new InvalidTermException("A number starting with 0-9 cannot be rcognized as an int and does not have a fraction '.'");
                	/**/

                    
                /*Castagna 06/2011*/
                    
                    int typec = other.tokenConsume();
                /**/
                    String svalc = other.sval;

                    
                    if (typec != TT_WORD) { 
                	/*Castagna 06/2011*/
                        
                        other.tokenPushBack(); 
                	/**/
                        other.pushBack2 = new PushBack(typeb, svalb); 
                        return new Token(svala, INTEGER); 
                    }

                    
                    int exponent = svalc.indexOf('E');
                    if (exponent == -1)
                        exponent = svalc.indexOf('e');

                    if (exponent >= 1) {                                  
                        if (exponent == svalc.length() - 1) {             
                    	/*Castagna 06/2011*/
                            
                            int typeb2 = other.tokenConsume();
                    	/**/
                            if (typeb2 == '+' || typeb2 == '-') {
                        	/*Castagna 06/2011*/
                                
                                int typec2 = other.tokenConsume();
                        	/**/
                                String svalc2 = other.sval;
                                if (typec2 == TT_WORD) {
                                    
                                    java.lang.Long.parseLong(svalc.substring(0, exponent));
                                    Integer.parseInt(svalc2);
                                    return new Token(svala + '.' + svalc + (char) typeb2 + svalc2, Tokenizer.FLOAT);
                                }
                            }
                        }
                    }
                    
                    java.lang.Double.parseDouble(svala + '.' + svalc);
                    return new Token(svala + '.' + svalc, Tokenizer.FLOAT);

                } catch (NumberFormatException e) {
                    
            	/*Castagna 06/2011*/
                    
                    throw new InvalidTermException("A term starting with 0-9 cannot be parsed as a number");
            	/**/
                }
            }
            throw new InvalidTermException("Unknown Unicode character: " + typea + "  (" + svala + ')');
        }
    }

    /*Castagna 06/2011*/
    /* Francesco Fabbri
     * 15/04/2011
     * Fix line number issue (always -1)
     */
    
    @Override
    public int lineno() {
    	return offsetToRowColumn(tokenOffset)[0];
    }
    
    public int tokenOffset() {
    	return tokenOffset;
    }
    
    public int tokenStart() {
    	return tokenStart;
    }
    
    public int[] offsetToRowColumn(int offset) {
    	if (text == null || text.length() <= 0)
    		return new int[] { super.lineno(), -1 };
    	
    	String newText = removeTrailing(text,tokenOffset);
    	int lno = 0;
    	int lastNewline = -1;
    	
    	for (int i=0; i<newText.length() && i<offset; i++) {
    		if (newText.charAt(i) == '\n') {
    			lno++;
    			lastNewline = i;
    		}
    	}
    	return new int[] { lno+1, offset-lastNewline };
    }
    
    /**
     * Marco Prati 
     * 19/04/11
     * 
     * remove Trailing spaces from last token, where
     * tokenizer stopped itself to correct the offset
     * 
     */
    static String removeTrailing(String input, int tokenOffset){

        try {
	    	char c = input.charAt(tokenOffset-1);
            String out = input;
            int i = tokenOffset;
            while(c == '\n'){
	    		out=input.substring(0, i);
	    		i--;
	        	c = input.charAt(i);
	    	}
	    	out=out.concat(input.substring(tokenOffset));
	    	return out;
    	}
    	catch (Exception e) { return input; }
    }
        
    /**
     * Read a token from the stream, and increase tokenOffset
     * @return the readed token
     * @throws IOException
     */
    private int tokenConsume() throws IOException {
    	int t = super.nextToken();
    	tokenLength = (sval == null ? 1 : sval.length());
    	tokenOffset += tokenLength;
    	return t;
    }
    
    /**
     * Push back the last readed token
     */
    private void tokenPushBack() {
        super.pushBack();
        tokenOffset -= tokenLength;
    }
    /**/
    
    /**
     *
     *
     * @param typec
     * @param svalc
     * @return the intValue of the next character token, -1 if invalid
     * todo needs a lookahead if typec is \
     */
    private static int isCharacterCodeConstantToken(int typec, String svalc) {
        if (svalc != null) {
            int sl = svalc.length();
            if (sl == 1)
                return svalc.charAt(0);
            if (sl > 1) {





                return -1;
            }
        }
        if (typec == ' ' ||                       
            Arrays.binarySearch(GRAPHIC_CHARS, (char)typec) >= 0)  

            return typec;

        return -1;
    }

    private static boolean isWhite(int type) {
        return type == ' ' || type == '\r' || type == '\n' || type == '\t' || type == '\f';
    }

    /**
     * used to implement lookahead for two tokens, super.pushBack() only handles one pushBack..
     */
    private static class PushBack {
        final int typea;
        final String svala;

        PushBack(int i, String s) {
            typea = i;
            svala = s;
        }
    }
}