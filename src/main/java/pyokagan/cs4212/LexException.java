package pyokagan.cs4212;

public class LexException extends RuntimeException {
    public LexException(String message, int yyline, int yycolumn) {
        super("error at line " + (yyline + 1) + " column " + (yycolumn + 1) + ": " + message);
    }
}
