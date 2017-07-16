// Outline of Parser for TinyPL - Part 1
import java.util.*;

public class Parser {
	public static void main(String[] args)  {
		System.out.println("Enter program and terminate with 'end'!\n");
		Lexer.lex();
		new Program();
		System.out.println("\nBytecodes:\n");
		Code.output();
	}
}

class Program {  // program ->  decls stmts end
	Program p;
	Decls d;
	Stmts s;
	
	public Program(){
		d = new Decls();
		s = new Stmts();
	}
}

class Decls {  // decls -> int idlist ';'
	Decls d;
	Idlist id;
	
	public Decls(){
		Lexer.lex(); //terminal for int
		id = new Idlist(); // idlist
		Lexer.lex(); //terminal for ;
	}
}

class Idlist { // idlist -> id [',' idlist ]
	String id_name;
	Idlist id;
	
	public Idlist(){
		id_name = Lexer.ident; //get variable name
		Code.addVariable(id_name); //store variable
		Lexer.lex(); //for id
		if (Lexer.nextToken == Token.COMMA){
			Lexer.lex(); //for comma
			id = new Idlist(); //idlist
		}
	}
}

class Stmt { // stmt -> assign ';' | cmpd | cond | loop
	Assign ass;
	Cmpd cmp;
	Cond cond;
	Loop loop;
	
	public Stmt(){
		switch (Lexer.nextToken) {
		case Token.ID: // assign (as assign begins with id)
			ass = new Assign();
			Lexer.lex(); //skip ;
			break;
		case Token.LEFT_BRACE: // '{' as cmpd begins with {
			cmp = new Cmpd();
			break;
		case Token.KEY_IF: // 'if' as cond begins with if
			cond = new Cond();
			break;
		case Token.KEY_FOR: // 'for' as loop begins with for
			loop = new Loop();
			break;
		default:
			break;
		}
	}
} 

class Stmts { // stmts -> stmt [ stmts ]
	Stmt st;
	Stmts sts;
	
	public Stmts(){
		st = new Stmt(); //stmt
		if(Lexer.nextToken == Token.ID || 
				Lexer.nextToken == Token.LEFT_BRACE || 
				Lexer.nextToken == Token.KEY_IF || 
				Lexer.nextToken == Token.KEY_FOR){
			sts = new Stmts(); //stmts
		}
	}
 
}

class Assign { // assign -> id '=' expr 
	Expr exp;
	String id_name;
	int var_num;
	
	public Assign(){
		id_name = Lexer.ident; //get variable name
		Lexer.lex(); //skip id
		Lexer.lex(); //skip =
		exp = new Expr();
		var_num = Code.variables.get(id_name);
		Code.gen(Code.store(var_num));
	}
}


class Cmpd { // cmpd -> '{' stmts '}'
	Stmts sts;
	
	public Cmpd(){
		Lexer.lex(); //skip {
		sts = new Stmts();
		Lexer.lex(); //skip }
	}
}

class Cond { //cond -> if '(' rel_exp ')' stmt [ else stmt ]
	Rel_exp rel_exp;
	Stmt stmt1, stmt2;
	int if_ptr ;
	int goto_ptr;
	
	public Cond(){
		Lexer.lex(); //Skip if
		Lexer.lex(); //Skip '('
		rel_exp = new Rel_exp();
		if_ptr = Code.codeptr - 1;
		Lexer.lex(); //Skip ')'
		stmt1 = new Stmt();
		if(Lexer.nextToken == Token.KEY_ELSE){
			goto_ptr = Code.codeptr;
			Code.gen(Code.gotoStmt()); // goto
			Code.backPatch(if_ptr, "n1", Integer.toString(Code.addr));
			Lexer.lex();
			stmt2 = new Stmt();
			Code.backPatch(goto_ptr, "n2", Integer.toString(Code.addr));
		}
		else{
			Code.backPatch(if_ptr, "n1", Integer.toString(Code.addr));
		}
	}
}

class Loop{ //loop -> for '(' [assign] ';' [rel_exp] ';' [assign] ')' ( stmt | '{' stmts '}' )
	Assign ass1, ass2;
	Rel_exp rel;
	Stmt stmt;
	Stmts stmts;
	int addr1,addr2;
	String[] temp_code;
	int temp_codeptr;
	int if_ptr ;
	int goto_ptr;
	boolean isCond;
	
	public Loop(){
		isCond = false;
		temp_code = new String[100];
		temp_codeptr = 0;
		Lexer.lex(); //skip for
		Lexer.lex(); //Skip '('
		if(Lexer.nextToken == Token.ID){ //assignment
			ass1 = new Assign();
		}
		Lexer.lex(); //Skip ;
		goto_ptr = Code.addr;
		if(Lexer.nextToken != Token.SEMICOLON){
			isCond = true;
			rel = new Rel_exp();
			if_ptr = Code.codeptr - 1;
		}
		Lexer.lex(); //Skip ;
		
		//*******Store below part ***********
		Code.tempMode = true;
		addr1 = Code.addr;
		Code.temp_code = temp_code;
		Code.temp_codeptr = temp_codeptr;
		
		if(Lexer.nextToken == Token.ID){ //assignment
			ass2 = new Assign();
		}
		
		Code.gen(Code.gotoStmt(goto_ptr)); // goto
		
		Code.tempMode = false;
		addr2 = Code.addr;
		Code.addr = addr1;
		temp_code = Code.temp_code;
		temp_codeptr = Code.temp_codeptr;
		//*******Store above part ***********
		
		Lexer.lex(); //Skip ')'
		
		if(Lexer.nextToken == Token.LEFT_BRACE){
			Lexer.lex(); //Skip '{'
			stmts = new Stmts();
			Lexer.lex(); //Skip '}'
		}
		else
			stmt = new Stmt();
		
		Code.copyFromTemp(addr2,temp_code,temp_codeptr);
		if(isCond)
			Code.backPatch(if_ptr, "n1", Integer.toString(Code.addr));
	}
}

class Rel_exp{ //rel_exp -> expr ('<' | '>' | '==' | '!= ') expr
	Expr expr1, expr2;
	int op;
	
	public Rel_exp(){
		expr1 = new Expr();
		op = Lexer.nextToken;
		Lexer.lex(); //Skip the symbol
		expr2 = new Expr();
		Code.gen(Code.signcode(op));
	}
}

class Expr { // expr -> term  [ ('+' | '-') expr ]
	Term t;
	Expr e;
	char op;

	public Expr() {
		t = new Term(); //term
		if (Lexer.nextToken == Token.ADD_OP || Lexer.nextToken == Token.SUB_OP) {
			op = Lexer.nextChar;
			Lexer.lex();
			e = new Expr();
			Code.gen(Code.opcode(op));	 
		}
	}
}

class Term { // term -> factor [ ('*' | '/') term ]
	Factor f;
	Term t;
	char op;

	public Term() {
		f = new Factor();
		if (Lexer.nextToken == Token.MULT_OP || Lexer.nextToken == Token.DIV_OP) {
			op = Lexer.nextChar;
			Lexer.lex();
			t = new Term();
			Code.gen(Code.opcode(op));
			}
	}
}

class Factor { // factor -> int_lit | id | '(' expr ')'
	Expr e;
	int i;
	int var_num;
	String id_name;

	public Factor() {
		switch (Lexer.nextToken) {
		case Token.INT_LIT: // int_lit
			i = Lexer.intValue;
			Code.gen(Code.intcode(i));
			Lexer.lex();
			break;
		case Token.ID: // id
			id_name = Lexer.ident;
			var_num = Code.variables.get(id_name);
			Code.gen(Code.load(var_num));
			Lexer.lex();
			break;
		case Token.LEFT_PAREN: // '('
			Lexer.lex();
			e = new Expr();
			Lexer.lex(); // skip over ')'
			break;
		default:
			break;
		}
	}
}

class Code {
	static String[] code = new String[100];
	static String[] temp_code = new String[100];
	static int temp_codeptr = 0;
	static int addr = 0;
	static int prev_addr = 0;
	static boolean tempMode = false;
	static int codeptr = 0;
	static public int var_count = 0;
	static public HashMap<String,Integer> variables = new HashMap<String,Integer>();
	
	public static void gen(String s) {
		if(tempMode){
			temp_code[temp_codeptr] = s;
			temp_codeptr++;
		}
		else{
			code[codeptr] = s;
			codeptr++;
		}
	}
	
	public static void copyFromTemp(int final_addr, String[] temp_code1, int temp_codeptr1){
		int diff = Code.addr - Integer.parseInt(temp_code1[0].substring(0, temp_code1[0].indexOf(':')));
		String cur_addr,new_addr;
		for(int i=0; i<temp_codeptr1; i++){
			cur_addr = temp_code1[i].substring(0, temp_code1[i].indexOf(':'));
			new_addr = Integer.toString(Integer.parseInt(cur_addr) + diff);
			code[codeptr] = temp_code1[i].replaceAll(cur_addr, new_addr);
			codeptr++;
		}
		Code.addr = final_addr + diff;
	}
	
	public static void backPatch(int ptr, String from, String to){
		code[ptr] = code[ptr].replaceFirst(from, to);
	}
	
	public static void addVariable(String iden){
		variables.put(iden,var_count);
		var_count++;
	}
	
	public static String gotoStmt(){
		String res = "";
		res = addr + ": goto n2";
		addr = addr + 3;
		return res;
	}
	
	public static String gotoStmt(int dest){
		String res = "";
		res = addr + ": goto " + dest;
		addr = addr + 3;
		return res;
	}
	
	public static String store(int i){
		String res = "";
		if (i < 4) {
			res = addr + ": istore_" + i;
			addr++;
			return res;
		}
		res = addr + ": istore " + i;
		addr = addr + 2;
		return res;
	}
	
	public static String load(int i){
		String res = "";
		if (i < 4) {
			res = addr + ": iload_" + i;
			addr++;
			return res;
		}
		res = addr + ": iload " + i;
		addr = addr + 2;
		return res;
	}
	
	public static String intcode(int i) {
		String res = "";
		if (i > 127) {
			res = addr + ": sipush " + i;
			addr = addr + 3;
			return res;
		}
		if (i > 5) {
			res = addr + ": bipush " + i;
			addr = addr + 2;
			return res;
		}
		res = addr + ": iconst_" + i;
		addr++;
		return res;
	}
	
	public static String opcode(char op) {
		String res = "";
		switch(op) {
			case '+' : {
				res = addr + ": iadd";
				addr++;
				return res;
			}
			case '-':  {
				res = addr + ": isub";
				addr++;
				return res;
			}
			case '*':  {
				res = addr + ": imul";
				addr++;
				return res;
			}
			case '/':  {
				res = addr + ": idiv";
				addr++;
				return res;
			}
			default: {
				res = addr + ": ";
				addr++;
				return res;
			}
		}
	}
	
	public static String signcode(int op) {
		String res = "";
		switch(op) {
			case Token.LESSER_OP : {
				res = addr + ": if_icmpge n1";
				addr = addr + 3;
				return res;
			}
			case Token.GREATER_OP:  {
				res = addr + ": if_icmple n1";
				addr = addr + 3;
				return res;
			}
			case Token.EQ_OP:  {
				res = addr + ": if_icmpne n1";
				addr = addr + 3;
				return res;
			}
			case Token.NOT_EQ:  {
				res = addr + ": if_icmpeq n1";
				addr = addr + 3;
				return res;
			}
			default: {
				res = addr + ": ";
				addr = addr + 3;
				return res;
			}
		}
	}
	
	public static void output() {
		for (int i=0; i<codeptr; i++)
			System.out.println(code[i]);
		System.out.println(addr + ": return");
	}
	
 
}


    
