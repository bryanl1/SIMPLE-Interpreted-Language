/* Bryan Linares */
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.*;


/**
 * 
 * SIL grammar Interpreter in Java. 
 * 
 * Extends and Based on ANTLR style model and from lessons and tutorial at Crafting Interpreters by Nystrom,
 * craftinginterpreters.com by Bob Nystrom
 * and Concepts of Programming Languages by Sebesta
 * 
 * Implements LET, PRINT, PRINTLN, + and * and / Expressions for Unit 7
 * 
 * TODO Operator Precedence buggy, 
 * TODO IF THEN, POP PUSH, GOSUB, RET, COMMA in Println, capitalize all input
 * 
 * Reads file, Lexer function makes Tokens, Tokens are Parsed by Parser, and Statements executed
 * 
	FILESTREAM
	LEXER = new FILESTREAM
	TOKENS = new TokenStream(LEXER)
	PARSER = new PARSER(TOKENS)
	PARSER.execute
 * 
 * 
 * Bryan Linares
 * usage: SIL.java [filename]
 * 
 */

public class SIL {

    public static void main(String[] args) {

    	//if (args.length != 1) { System.out.println("Usage: SIL script.sil"); return;}
    	
    	//String script = args[0];
    	String script = "test.sil";
        String scriptcontent = fileStream(script);
        SIL sil = new SIL();
        sil.interpret(scriptcontent);
    }
    
    // Stream in File
    private static String fileStream(String source) {
        try {
            FileInputStream stream = new FileInputStream(source);
            //System.out.println(Charset.defaultCharset());
            try {
                InputStreamReader input = new InputStreamReader(stream, Charset.defaultCharset());
                Reader reader = new BufferedReader(input);
                
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[9999];
                int read;
                while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString().toUpperCase(); //just changed this to uppercase
            } finally {
                stream.close();
            }
        } catch (IOException ex) {
            return null;
        }
    }
    
    // Interpreter Program -------------------------------------------------------------

    private final Map<String, Value> variables; //variable values
    private final Map<String, Integer> labels;  //labels to get each
    private int currentStmt;                    //current statement executing
    private final BufferedReader textInput;
    private final ArrayList<String> callstack;
    private final Stack<Expression> stack;

    public SIL() {
        variables = new HashMap<String, Value>();
        labels = new HashMap<String, Integer>();
        textInput =new BufferedReader( new InputStreamReader(System.in));
        callstack = new ArrayList<String>();
        stack = new Stack<Expression>();
    }

    public void interpret(String source) {
        // Make the tokens from the string input
        List<Token> tokens = lexer(source);
        //System.out.println(tokens);
        // Parse each token into executable statements
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parse(labels);
        
        // Execute statements until all finished
        currentStmt = 0;
        while (currentStmt < statements.size()) {
            int thisStatement = currentStmt;
            currentStmt++;
            statements.get(thisStatement).execute();
        }
    }
    
    // Lexer is state machine building tokens by character
    //Default state checks char, goes to right state to add token completely
    //one by one character, build List of Token types
    private static List<Token> lexer(String source) {
        List<Token> tokens = new ArrayList<Token>();
        
        String token = "";
        LexerState state = LexerState.DEFAULT;
        
        String charTokens = "\n=+-*/<>!"; //single char tokens
        TokenType[] tokenTypes = { TokenType.NEWLINE, TokenType.EQUALS,
            TokenType.OPERATOR, TokenType.OPERATOR, TokenType.OPERATOR, TokenType.OPERATOR, 
            TokenType.OPERATOR, TokenType.OPERATOR, TokenType.OPERATOR, 
            //TokenType.COMMA
        };
        
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (state) {
            case DEFAULT:
                if (charTokens.indexOf(c) != -1) { //if char not found, add if possible
                    tokens.add(new Token(Character.toString(c), tokenTypes[charTokens.indexOf(c)]));
                    
                } else if (Character.isLetter(c)) { token += c; state = LexerState.ID;
                } else if (Character.isDigit(c)) {  token += c; state = LexerState.NUMBER;
                } else if (c == '"') {                          state = LexerState.STRING;
                }  break;
                
            case ID:
                if (Character.isLetterOrDigit(c)) {
                    token += c;
                } else {
                    tokens.add(new Token(token, TokenType.ID));
                    token = "";
                    state = LexerState.DEFAULT;
                    i--; 
                }
                break;
                
            case NUMBER:
                if (Character.isDigit(c)) {
                    token += c;
                } else {
                    tokens.add(new Token(token, TokenType.NUMBER));
                    token = "";
                    state = LexerState.DEFAULT;
                    i--; 
                }
                break;
                
            case STRING:
                if (c == '"') { //found second quote for string return back
                    tokens.add(new Token(token, TokenType.STRING));
                    token = "";
                    state = LexerState.DEFAULT;
                } else { //keep adding string till done
                    token += c;
                }
                break;
            }
        }
        
        return tokens;
    }

    // Token data type **********************************************

    private enum TokenType { ID, NUMBER, STRING, NEWLINE, EQUALS, OPERATOR, COMMA, EOF }
    private enum LexerState {    DEFAULT, ID, NUMBER, STRING }
    
    private static class Token {
        public Token(String text, TokenType type) {
            this.text = text;
            this.type = type;
        }
        
        public final String text;
        public final TokenType type;
    }
    
    
    // Parser object **********************************************

    private class Parser {
        public Parser(List<Token> tokens) {
            this.tokens = tokens;
            position = 0;
        }
        
        public List<Statement> parse(Map<String, Integer> labels) {
            List<Statement> statements = new ArrayList<Statement>();
            String lastLineNumber = "0";
            while (true) {
//            	int lineCount=0;
                while (isType(TokenType.NEWLINE)); //process newlines with no action

                if(isType(TokenType.NUMBER)) { ///process the line number, check if it is greater than last
                	
                		if( Integer.parseInt(last(1).text) <= Integer.parseInt(lastLineNumber)) {
                			throw new Error("Line number is not in ascending order");
                		}
                		labels.put(last(1).text, statements.size());
                		lastLineNumber = last(1).text;
                } else //do i need this else here? every line will have a line number

                if (isType(TokenType.ID, TokenType.EQUALS)) {
                    String name = last(2).text;
                    Expression value = expression();
                    statements.add(new AssignStatement(name, value));
                } else if (isString("PRINT")) {
                    statements.add(new PrintStatement(expression()));
                } else if (isString("PRINTLN")) {
                    statements.add(new PrintLNStatement(expression()));
                    //System.out.println("the following tokesn are "+last(0).text);
                    //if(isType(TokenType.COMMA))
                    	//statements.add(new PrintLNStatement(expression()));
                } else if (isString("LET")) {
                	
                	//{LET, ID, OPER_EQUALS, EXPRESSION}
                	//Check that the following Tokens are and ID and an Equals
                	//put some better checking here, different possible assign?
                	
                	if(!isType(TokenType.ID, TokenType.EQUALS)) break;
                	String name = last(2).text;
                    Expression value = expression();
                	//Expression value = new NumberValue(0);
                	
                    statements.add(new AssignStatement(name, value));
                } else if (isString("INTEGER")) {
                	//Do assignstatements with zero until no more found
                	//System.out.println("Found Integer statement");
                	
                	while(isType(TokenType.ID)) {
                		
                    	String name = last(1).text;
                    	Value value = new NumberValue(0); //initialize to zero
                    	
                    	//System.out.println("adding variable "+ last(1).text + " " +last(1).type);
                    	statements.add(new AssignStatement(name, value));
                	}
                	
                } else if (isString("INPUT")) {
                	String names = last(0).text;
                	while(isType(TokenType.ID)) {
                		//System.out.println("inputting for name:" + names);
                		names = names + " "+last(0).text;
                	}
                	statements.add(new InputStatement(names));
                	
                } else if (isString("GOTO")) {
                	//String label = "11";
                	isType(TokenType.NUMBER);//check and progress parse, if it's a line number next
                	// throw error on invalid line choice in function
                	String line_number = last(1).text;
                	//System.out.println("GOTO Exec!! "+ last(1).text);
                	statements.add(new GOTOStatement( line_number ));
                	
                } else if (isString("IF")) {
                	//FIXME working on this
                	Expression comparison = expression();
                	
                	System.out.println("Position before then !"+position);
                	
                	if (isString("THEN")){
                		//System.out.println("found THEN "+comparison.toString());
                	}
                	//if(isType(TokenType.ID));
                	//System.out.println("Position after !"+position);
                	//System.out.println("Putting position and stmt size "+ position +" "+statements.size()+1);
                	String label = Integer.toString(position*statements.size()+1);
                	labels.put(label,statements.size()+1);
                	String position = lastLineNumber;
                	statements.add(new IFTHENStatement( comparison, position, label));
                	//eat the whole statement until the end of the line?
            		System.out.println("THE IF THEN statement IS "+get(1).text);
            		
                } else if(isString("END")) {
                	statements.add(new ENDStatement() );
                	
                } else if(isString("GOSUB")) {
                	isType(TokenType.NUMBER);//check and progress parse, if it's a line number next
                	// throw error on invalid line choice in function
                	String line_number = last(1).text;
                	//System.out.println("GOTO Exec!! "+ last(1).text);
                	
                	statements.add(new GOSUBStatement( line_number ));
                	//if(isMarkedLine) {}
                	
                	//if(isType(TokenType.NUMBER))  //attempt to grab the next line number
                	System.out.println("THE NEXT LINE NUM IS "+get(1).text);
                	callstack.add(get(1).text);
                	//}
                }else if(isString("RET")) {
                	statements.add(new RETStatement() );	
                }else if(isString("PUSH")) {
                	Expression expression = expression();
                	statements.add(new PUSHStatement(expression) );	
                }else if(isString("POP")) {
                	String name = last(0).text;
                	//System.out.println("the variable is "+ name);
                	isType(TokenType.ID);
                	statements.add( new POPStatement(name) );	
                }
                
                else break; // until unknown token?
            }
            
            return statements;
        }
        
        /////////////////////////////Expr.g, continuosly tries to eval
        private Expression expression() {   return operator();  }
        
        private Expression operator() {
            Expression expression = atomic();
            
            // keep expr going until done
            while (isType(TokenType.OPERATOR) || isType(TokenType.EQUALS)) {
                char operator = last(1).text.charAt(0);
                Expression right = atomic();
                expression = new OperatorExpression(expression, operator, right);
            }
            
            return expression;
        }
        
        private Expression atomic() {
            if (isType(TokenType.ID)) {
                // A ID is the stored name of a variable
                return new VariableExpression(last(1).text);
            } else if (isType(TokenType.NUMBER)) {
                return new NumberValue(Integer.parseInt(last(1).text));
            } else if (isType(TokenType.STRING)) {
                return new StringValue(last(1).text);
            } 
            throw new Error("atomic() Not able to parse");
        }
        //////////////////////////////////////
        
        private boolean isType(TokenType type1, TokenType type2) {
        	if (get(0).type != type1) return false;  if (get(1).type != type2) return false;
            position += 2;
            return true;
        }
        
        private boolean isType(TokenType type) {
            if (get(0).type != type) return false;
            position++;
            return true;
        }

        private boolean isString(String name) {
            if (get(0).type != TokenType.ID) return false;  if (!get(0).text.equals(name)) return false;
            position++;
            return true;
        }
        
        //Parser navigation functions, index tokens needed
        private Token last(int offset) {   return tokens.get(position - offset);      }
        
        private Token get(int offset) {
            if (position + offset >= tokens.size()) {
                return new Token("", TokenType.EOF); //if trying to go beyond tokens, give EOF
            }
            return tokens.get(position + offset);
        }
        
        private final List<Token> tokens;
        private int position;
    }
    
    /*
     * Statements are Executed and Expressions Evaluated! Expr return Value
     */

    public interface Statement {    void execute();  }
    public interface Expression {   Value evaluate();  }
    
    public class PrintStatement implements Statement {
          public PrintStatement(Expression expression) { this.expression = expression; }
        
        public void execute() {
            System.out.print(expression.evaluate().toString());
        }

        private final Expression expression;
    }
    
    public class PrintLNStatement implements Statement {
        public PrintLNStatement(Expression expression) {
            this.expression = expression;
        }
        
        public void execute() {
            System.out.println(expression.evaluate().toString());
        }

        private final Expression expression;
    }
    
    
    public class InputStatement implements Statement {
        public InputStatement(String name) {
            this.name = name;
        }
        
        public void execute() {
            try {
            	//System.out.println("The received names are " + name + currentStmt);
            	
            	String name_inputs[] = name.split("\\W+");
            	for(String check: name_inputs) {
            		//System.out.println("testing!!!"+check);
            		if(!variables.containsKey(check))
            			throw new Error("Line Statement "+ currentStmt +" Missing Input, variable not found");
            	}
            	
                String input = textInput.readLine();
                String value_inputs[] = input.split("\\W+");
                
                if(name_inputs.length != value_inputs.length) {
                	throw new Error("Line Statement "+ currentStmt +" Missing Input, sizes mismatch");
                }
                
                for(int i =0; i< name_inputs.length;i++) {
                	
	                //System.out.println("Inputting into "+name_inputs[i]+" the value of "+ value_inputs[i]);
	                try {
	                		int value = Integer.parseInt(value_inputs[i]);
	                        variables.put(name_inputs[i], new NumberValue(value));
	                        //System.out.println("Put value into variables");
	                	
	                } catch (NumberFormatException e) {
	                	
	                    variables.put(name_inputs[i], new StringValue(value_inputs[i]));
	                    System.out.println("Error Put value into stringvars");
	                }
                
                }
                
            } catch (IOException e1) { //IOException from Buffered,
                // improper input
            	System.out.println("Input error");
            }
        }
        
        private final String name;
    }
    
    public class AssignStatement implements Statement {
        public AssignStatement(String name, Expression value) {
            this.name = name;
            this.value = value;
        }
        
        public void execute() {
            variables.put(name, value.evaluate());
        }

        private final String name;
        private final Expression value;
    }
    
    public class GOTOStatement implements Statement {
        public GOTOStatement(String line_number) {
            this.line_number = line_number;
        }
        
        public void execute() {
            if (labels.containsKey(line_number)) {
                currentStmt = labels.get(line_number).intValue();
            }
        }

        private final String line_number;
    }
    
    public class GOSUBStatement implements Statement {
        public GOSUBStatement(String line_number) {
            this.line_number = line_number;
        }
        
        public void execute() {
            if (labels.containsKey(line_number)) {
                currentStmt = labels.get(line_number).intValue();
                //pushed line number into Callstack in parsing step
                //callstack.push(line_number);
            } else throw new Error("Line number not found - "+ line_number);
            	
        }

        private final String line_number;
    }
    
    public class RETStatement implements Statement {
        public RETStatement() {
            //this.line_number = line_number;
        }
        
        public void execute() {
        	//TODO working here
        	//System.out.println("HELLO RET"+callstack.peek());
        	//System.out.println("The first elem is "+ callstack.get(0));
        	//System.out.println("The top elem is "+ callstack.get(callstack.size()-1));
        	//String line_number = callstack.pop();
        	//System.out.println(callstack);
        	String line_number = callstack.remove(callstack.size()-1);
        	
        	System.out.println("Hello"+callstack+labels.containsKey(line_number)+labels.get(line_number).intValue());
        	//callstack.pop();
        	
        	//System.out.println("Returning to line  "+ line_number);
        	//System.out.println("Returning to line "+callstack.peek().toString());
            if (labels.containsKey(line_number)) { //checking if line number
            	currentStmt = labels.get(line_number).intValue();//labels.get(line_number).intValue();
                //push line number into Callstack
                //callstack.push(labels.get(line_number).intValue());
            }
        }

        //private final String line_number;
    }
    
    public class IFTHENStatement implements Statement {
    	////Get the line number of the current line, mark the part right after the THEN
    	////go to that statement, 
        public IFTHENStatement(Expression condition, String position, String label) {
            this.condition = condition;
            this.position = position;
            this.label = label;
        }
        
        public void execute() {
        	//TODO make this work
        	//FIXME clean this up
        	//System.out.println("The condition is evaluated to "+condition.evaluate().toNumber());
        	//System.out.println("The position is "+ position);
        	//System.out.println("The corresponding stmt is "+ labels.get(position));
        	
        	//IF evals to true,
        	//labels.hashCode();
        	//labels.
        	for(String key: labels.keySet()) {
        		//System.out.println(key+" and "+ labels.get(key));
        	}
        		
            if (labels.containsKey(position)) {
                int value = condition.evaluate().toNumber();
            	
                System.out.println("Executing IFTHEN "+ value);

                if (value == 1) {
                	//currentStmt = labels.get(label).intValue();
                	//currentStmt = labels.get(generated_position).intValue();
                	//currentStmt = labels.get(position).intValue();
                    //currentStmt = currentStmt++;
                    //currentStmt = labels.get(position).intValue();
                } else {
                	//String nextHighestpositionKey = nextHighest(position);
                	//String nextHighestpositionKey=position;
                	//currentStmt = labels.get(nextHighestpositionKey).intValue();
                	//currentStmt = labels.get(nextHighestpositionKey).intValue();
                	//currentStmt = ;
                }
            }
        }
        
        //trying to find the next statement to jump to in the unordered map being used.
        String nextHighest(String key) {
        	int keyvalue = Integer.parseInt(key);
        	int next =0;//Integer.parseInt(key);
        	
        	for(String ikey: labels.keySet()) {
        		int current = Integer.parseInt(ikey);
        		System.out.println(ikey+" and "+ labels.get(ikey));
        		 
        		if(next!=keyvalue && (current-keyvalue)<next ){
        			next = current;
        		}
        	}
        	System.out.println("for the key "+key+" the next highest is "+ next);
        	return Integer.toString(next);
        }
        
        private final Expression condition; //the condition to check
        private final String position; //the position where after the THEN
        private final String label;   //the label for the THEN statement
    }
    
    public class ENDStatement implements Statement {
        public ENDStatement() {
            
        }
        
        public void execute() {
            System.exit(0);
        }

    }
    
    public class PUSHStatement implements Statement {
        public PUSHStatement(Expression expression) { this.expression = expression; }
      
      public void execute() {
    	  stack.push(expression.evaluate());
    	  //variables.put(name, expression.evaluate());
          //System.out.print(expression.evaluate().toString());
      }

      private final Expression expression;
  }
    
    public class POPStatement implements Statement {
        public POPStatement(String name) {
            this.name = name;
        }
        
        public void execute() {
        	Expression value = stack.pop();        	
        	System.out.println("Popping out value "+value.evaluate().toNumber());
        	System.out.println(variables.containsKey(name));
            variables.put(name, value.evaluate());
        }

        private final String name;
    }
    
    
    public class LetStatement implements Statement {
    	//Let needs LET ID EQUALS EXPRESSION
    	//testing
        public LetStatement(Expression expression) {
            this.expression = expression;
        }
        
        public void execute() {
            System.out.print(expression.evaluate().toString());
        }

        private final Expression expression;
    }
       
    public class VariableExpression implements Expression {
        public VariableExpression(String name) {
            this.name = name;
        }
        
        public Value evaluate() {
            if (variables.containsKey(name)) {
                return variables.get(name);
            }
            return new NumberValue(0);
        }
        
        private final String name;
    }
    
    public class OperatorExpression implements Expression {
        public OperatorExpression(Expression left, char operator, Expression right) {
                   this.left = left;  this.operator = operator; this.right = right;
        }
        
        public Value evaluate() {
            Value leftVal = left.evaluate();
            Value rightVal = right.evaluate();
            
            switch (operator) {
            
            case '+': return new NumberValue(leftVal.toNumber() + rightVal.toNumber());
            case '-': return new NumberValue(leftVal.toNumber() -  rightVal.toNumber());
            case '*': return new NumberValue(leftVal.toNumber() *  rightVal.toNumber());
            case '/': return new NumberValue(leftVal.toNumber() /  rightVal.toNumber());
            case '>': return new NumberValue(leftVal.toNumber() >  rightVal.toNumber()? 1:0 );
            case '<': return new NumberValue(leftVal.toNumber() <  rightVal.toNumber()? 1:0);
            case '!': return new NumberValue(leftVal.toNumber() !=  rightVal.toNumber()? 1:0);
            case '=': return new NumberValue(leftVal.toNumber() ==  rightVal.toNumber()? 1:0);
            }
            throw new Error("Evaluate() Unknown operator.");
        }
        
        private final Expression left;
        private final char operator;
        private final Expression right;
    }
    
    // Value types
    // all values have to be able to be expressed in a few ways, for casting etc
    //this way makes it easy to coerce later
    public interface Value extends Expression {
        String toString(); 
        int toNumber();
    }
    
    public class NumberValue implements Value {
        public NumberValue(int value) { this.value = value;   }
        
        @Override public String toString() { return Integer.toString(value); }
        public int toNumber() { return value; }
        public Value evaluate() { return this; }

        private final int value;
    }
    
    public class StringValue implements Value {
        public StringValue(String value) {  this.value = value;   }
        
        @Override public String toString() { return value; }
        public int toNumber() { return Integer.parseInt(value); }
        public Value evaluate() { return this; }

        private final String value;
    }
    
}
