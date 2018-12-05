package pyokagan.cs4212;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

public class ParserStmtFailTest {

    @Test
    public void emptyIfThenBlock() {
        assertParseFail("if (true) {} else { return; }");
    }

    @Test
    public void emptyIfElseBlock() {
        assertParseFail("if (true) { return; } else {}");
    }

    @Test
    public void notAnId() {
        assertParseFail("readln(32);");
        assertParseFail("readln(32Clazz);");
        assertParseFail("readln(Clazz);");
        assertParseFail("CName Varname;");
    }

    @Test
    public void readln_onlyOneArgumentAllowed() {
        assertParseFail("readln(ident1, ident2);");
    }

    @Test
    public void println_onlyOneArgumentAllowed() {
        assertParseFail("println(\"a\", \"b\");");
    }

    @Test
    public void bexpWithAexp() {
        assertParseFail("return true || 2;");
        assertParseFail("return true || 1 + 2;");
        assertParseFail("return true && 2;");
    }

    @Test
    public void aexpWithBexp() {
        assertParseFail("return 2 || true;");
        assertParseFail("return 1 + 2 || true;");
        assertParseFail("return 2 && true;");
    }

    @Test
    public void bexpWithSexp() {
        assertParseFail("return true || \"a\";");
        assertParseFail("return \"b\" || true;");
    }

    @Test
    public void unaryLnot_withNonBoolean() {
        assertParseFail("return !3;");
        assertParseFail("return !\"blah\";");
    }

    @Test
    public void unaryNeg_withNonBoolean() {
        assertParseFail("return -true;");
        assertParseFail("return -\"blah\";");
    }

    @Test
    public void others() {
        assertParseFail("return \"a\" >= 2;");
        assertParseFail("return 2 == \"a\";");
        assertParseFail("return 1 + \"a\";");
        assertParseFail("return 1 - \"a\";");
        assertParseFail("return 1 * \"a\"");
        assertParseFail("return 1 / \"a\"");
        assertParseFail("return 4 < 10 <= 60;");
        assertParseFail("return -;");
        assertParseFail("return !;");
        assertParseFail("return ();");
        assertParseFail(";"); // empty statements not allowed
    }

    @Test
    public void standaloneAtomsNotAllowed() {
        assertParseFail("true;");
        assertParseFail("this;");
        assertParseFail("(some(\"call\"));"); // By enclosing in parens, this becomes an atom
    }

    @Test
    public void reservedNames() {
        assertParseFail("return println(\"a\");");
        assertParseFail("return readln(a);");
        assertParseFail("CName main;");
        assertParseFail("return main();");
        assertParseFail("return myObj.main;");
        assertParseFail("main();");
    }

    @Test
    public void unterminated() {
        assertParseFail("/*"); // unterminated comment
        assertParseFail("return \""); // unterminated string
    }

    @Test
    public void illegalEscapeSequence() {
        assertParseFail("return \"a\\zb\";");
        assertParseFail("return \"\\800\";"); // not octal
        assertParseFail("return \"\\90\";"); // not octal
        assertParseFail("return \"\\xg\";"); // not hex
    }

    @Test
    public void invalidTypeName() {
        assertParseFail("smallLetters a;");
        assertParseFail("3Type a;");
        assertParseFail("CName;");
    }

    private static void assertParseFail(String stmt) {
        StringBuilder sb = new StringBuilder();
        sb.append("class Main {\n");
        sb.append("    Void main() {\n");
        sb.append(stmt);
        sb.append("}}");
        String x = sb.toString();
        StringReader reader = new StringReader(x);
        assertThrows(Exception.class, () -> Parser.parse(reader));
    }

}
