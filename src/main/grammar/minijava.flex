package pyokagan.cs4212;

import java_cup.runtime.Symbol;
import java_cup.runtime.ComplexSymbolFactory;
import java_cup.runtime.ComplexSymbolFactory.Location;

%%
%public
%class Lexer
%cup
%implements sym
%char
%line
%column

%{
    private StringBuilder string = new StringBuilder();
    private ComplexSymbolFactory symbolFactory;

    public Lexer(java.io.Reader in, ComplexSymbolFactory sf) {
    	this(in);
        symbolFactory = sf;
    }

    private Symbol symbol(String name, int sym) {
        Location left = new Location(yyline + 1, yycolumn + 1, yychar);
        Location right = new Location(yyline + 1, yycolumn + yylength(), yychar + yylength());
        return symbolFactory.newSymbol(name, sym, left, right);
    }

    private Symbol symbol(String name, int sym, Object val) {
        Location left = new Location(yyline + 1, yycolumn + 1, yychar);
        Location right = new Location(yyline + 1, yycolumn + yylength(), yychar + yylength());
        return symbolFactory.newSymbol(name, sym, left, right, val);
    }
%}

%eofval{
     return symbolFactory.newSymbol("EOF", EOF, new Location(yyline+1,yycolumn+1,yychar), new Location(yyline+1,yycolumn+1,yychar+1));
%eofval}

IntLiteral = [0-9]+
new_line = \r|\n|\r\n
white_space = {new_line} | [ \t\f]
Ident = [a-z][a-zA-Z0-9_]*
CName = [A-Z][a-zA-Z0-9_]*

%state STRINGLIT
%state MULTILINE_COMMENT
%state SINGLELINE_COMMENT

%%

<YYINITIAL>{
    /* keywords */
    "if" { return symbol("if", IF); }
    "else" { return symbol("else", ELSE); }
    "class" { return symbol("class", CLASS); }
    "while" { return symbol("while", WHILE); }
    "readln" { return symbol("readln", READLN); }
    "println" { return symbol("println", PRINTLN); }
    "return" { return symbol("return", RETURN); }
    "this" { return symbol("this", THIS); }
    "new" { return symbol("new", NEW); }
    "main" { return symbol("main", MAIN); }
    "Void" { return symbol("Void", VOID); }
    "Int" { return symbol("Int", INT); }
    "Bool" { return symbol("Bool", BOOL); }
    "String" { return symbol("String", STRING); }

    /* literals */
    "true" { return symbol("TRUE", TRUE); }
    "false" { return symbol("FALSE", FALSE); }
    "null" { return symbol("NULL", NULL); }
    {IntLiteral}      { return symbol("Intconst",INTEGER_LITERAL, new Integer(Integer.parseInt(yytext()))); }
    \"   { yybegin(STRINGLIT); string.setLength(0); }

    /* identifiers */
    {Ident}     { return symbol("Ident:" + yytext(), IDENT, yytext()); }
    {CName}     { return symbol("CName:" + yytext(), CNAME, yytext()); }

    /* separators */
    "("               { return symbol("(",LPAREN); }
    ")"               { return symbol(")",RPAREN); }
    "{"               { return symbol("{", LBRACE); }
    "}"               { return symbol("}", RBRACE); }
    ";"               { return symbol(";", SEMICOLON); }
    "."               { return symbol(".", DOT); }
    ","               { return symbol(",", COMMA); }

    /* operators */
    "+"               { return symbol("+",PLUS); }
    "-"               { return symbol("+",MINUS); }
    "*"               { return symbol("+",TIMES); }
    "/"               { return symbol("+",DIV); }
    "<"               { return symbol("<", LT); }
    ">"               { return symbol(">", GT); }
    "<="              { return symbol("<=", LEQ); }
    ">="              { return symbol(">=", GEQ); }
    "=="              { return symbol("==", EQ); }
    "="               { return symbol("=", ASSIGN); }
    "!="              { return symbol("!=", NEQ); }
    "!"               { return symbol("!", NOT); }
    "||"              { return symbol("||", OROR); }
    "&&"              { return symbol("&&", ANDAND); }

    /* comments */
    "/*"              { yybegin(MULTILINE_COMMENT); }
    "//"              { yybegin(SINGLELINE_COMMENT); }

    {white_space}     { /* ignore */ }
}

<STRINGLIT> {
    \" { yybegin(YYINITIAL); return symbol("STRING_LITERAL", STRING_LITERAL, string.toString()); }
    [^\n\r\"\\]+                   { string.append( yytext() ); }
    \\t                            { string.append('\t'); }
    \\n                            { string.append('\n'); }

    \\r                            { string.append('\r'); }
    \\b                            { string.append('\b'); }
    \\\"                           { string.append('\"'); }
    \\\\                           { string.append('\\'); }
    \\[0-3]?[0-7]?[0-7]            { char val = (char) Integer.parseInt(yytext().substring(1), 8); string.append(val); }
    \\x[0-9a-f]?[0-9a-f]           { char val = (char) Integer.parseInt(yytext().substring(2), 16); string.append(val); }
    \\. { throw new LexException("Illegal escape sequence \"" + yytext() + "\"", yyline, yycolumn); }
    {new_line} { throw new LexException("Unterminated string at end of line", yyline, yycolumn); }
}

<MULTILINE_COMMENT> {
    "*/"                           { yybegin(YYINITIAL); }
    .                              { /* ignore */ }
    {new_line}                     { /* ignore */ }
}

<MULTILINE_COMMENT><<EOF>> {
    throw new LexException("Unterminated multiline comment at end of file.", yyline, yycolumn);
}

<SINGLELINE_COMMENT> {
    {new_line}                     { yybegin(YYINITIAL); }
    .                              { /* ignore */ }
}

/* error fallback */
[^]              {  throw new LexException("Illegal character <"+ yytext()+">", yyline, yycolumn);
                 }
