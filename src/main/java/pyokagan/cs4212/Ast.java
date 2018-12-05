package pyokagan.cs4212;

import java.util.*;
import java.math.*;

public class Ast {
    public interface Renderable {
        String render(int indent, int prec);
    }

    public static void doIndent(StringBuilder sb, int indent) {
        while (indent > 0) {
            sb.append("  ");
            indent--;
        }
    }

    public static class Location {
        public final int line;
        public final int column;

        public Location(int line, int column) {
            this.line = line;
            this.column = column;
        }

        @Override
        public String toString() {
            return "{\"line\": " + line + ", \"column\": " + column + "}";
        }
    }

    public interface Locatable {
        Location getLocation();
        void setLocation(Location location);
    }

    public static class Prog implements Renderable {
        public final List<Clazz> clazzes;

        public Prog(List<Clazz> clazzes) {
            this.clazzes = Collections.unmodifiableList(new ArrayList<>(clazzes));
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            for (Clazz clazz : clazzes) {
                sb.append(clazz.render(indent, 0));
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\": \"Prog\", \"clazzes\": [");
            int i = 0;
            for (Clazz clazz : clazzes) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(clazz.toString());
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static class Clazz implements Renderable, Locatable {
        public final String cname;
        public final List<VarDecl> varDecls;
        public final List<Meth> meths;
        private Location location;

        public Clazz(String cname, List<VarDecl> varDecls, List<Meth> meths) {
            this.cname = cname;
            this.varDecls = Collections.unmodifiableList(new ArrayList<>(varDecls));
            this.meths = Collections.unmodifiableList(new ArrayList<>(meths));
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("class ").append(cname).append(" {\n");
            for (VarDecl varDecl : varDecls) {
                doIndent(sb, indent + 1);
                sb.append(varDecl.typ.render(0, 0));
                sb.append(" ");
                sb.append(varDecl.name);
                sb.append(";\n");
            }
            for (Meth meth : meths) {
                sb.append(meth.render(indent + 1, 0));
            }
            doIndent(sb, indent);
            sb.append("}\n");
            return sb.toString();
        }

        @Override
        public Location getLocation() {
            return location;
        }

        @Override
        public void setLocation(Location location) {
            this.location = location;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\": \"Clazz\", \"cname\": \"");
            sb.append(escape(cname));
            sb.append("\", \"varDecls\": [");
            int i = 0;
            for (VarDecl varDecl : varDecls) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(varDecl.toString());
            }
            sb.append("], \"meths\": [");
            i = 0;
            for (Meth meth : meths) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(meth.toString());
            }
            sb.append("], \"location\": ");
            sb.append(location);
            sb.append("}");
            return sb.toString();
        }
    }

    public static class VarDecl implements Locatable {
        public final Typ typ;
        public final String name;
        private Location location;

        public VarDecl(Typ typ, String name) {
            this.typ = typ;
            this.name = name;
        }

        @Override
        public Location getLocation() {
            return location;
        }

        @Override
        public void setLocation(Location location) {
            this.location = location;
        }

        @Override
        public String toString() {
            return "{\"t\": \"VarDecl\", \"typ\": " + typ + ", \"name\": \"" + escape(name) + "\", \"location\": " + location + "}";
        }
    }

    public static class Meth implements Renderable, Locatable {
        public final Typ retTyp;
        public final String name;
        public final List<VarDecl> args;
        public final List<VarDecl> vars;
        public final List<Stmt> stmts;
        private Location location;

        public Meth(Typ retTyp, String name, List<VarDecl> args, List<VarDecl> vars, List<Stmt> stmts) {
            this.retTyp = retTyp;
            this.name = name;
            this.args = Collections.unmodifiableList(new ArrayList<>(args));
            this.vars = Collections.unmodifiableList(new ArrayList<>(vars));
            this.stmts = Collections.unmodifiableList(new ArrayList<>(stmts));
        }

        @Override
        public Location getLocation() {
            return location;
        }

        @Override
        public void setLocation(Location location) {
            this.location = location;
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(retTyp.render(0, prec)).append(" ").append(name).append("(");
            int i = 0;
            for (VarDecl varDecl : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(varDecl.typ.render(0, 0));
                sb.append(" ");
                sb.append(varDecl.name);
            }
            sb.append(") {\n");
            for (VarDecl varDecl : vars) {
                doIndent(sb, indent + 1);
                sb.append(varDecl.typ.render(0, 0));
                sb.append(" ");
                sb.append(varDecl.name);
                sb.append(";\n");
            }
            for (Stmt stmt : stmts) {
                sb.append(stmt.render(indent + 1, 0));
            }
            doIndent(sb, indent);
            sb.append("}\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\": \"Meth\", \"retTyp\": ");
            sb.append(retTyp);
            sb.append(", \"name\": \"");
            sb.append(escape(name));
            sb.append("\", \"args\": [");
            int i = 0;
            for (VarDecl varDecl : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(varDecl);
            }
            sb.append("], \"vars\": [");
            i = 0;
            for (VarDecl varDecl : vars) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(varDecl);
            }
            sb.append("], \"stmts\": [");
            i = 0;
            for (Stmt stmt : stmts) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(stmt);
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static abstract class Typ implements Renderable {
        public abstract boolean isSubtypeOrEquals(Typ x);
    }
    public static class IntTyp extends Typ {
        @Override
        public String render(int indent, int prec) {
            return "Int";
        }

        @Override
        public String toString() {
            return "\"*Int*\"";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return true;
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }
    public static class BoolTyp extends Typ {
        @Override
        public String render(int indent, int prec) {
            return "Bool";
        }

        @Override
        public String toString() {
            return "\"*Bool*\"";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return true;
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }
    public static class StringTyp extends Typ {
        @Override
        public String render(int indent, int prec) {
            return "String";
        }

        @Override
        public String toString() {
            return "\"*String*\"";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return true;
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }
    public static class VoidTyp extends Typ {
        @Override
        public String render(int indent, int prec) {
            return "Void";
        }

        @Override
        public String toString() {
            return "\"*Void*\"";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return true;
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }
    public static class NullTyp extends Typ {
        @Override
        public String render(int indent, int prec) {
            return "*Null*";
        }

        @Override
        public String toString() {
            return "\"*Null*\"";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return true;
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            if (equals(o))
                return true;

            return o instanceof StringTyp || o instanceof ClazzTyp;
        }
    }
    public static class ClazzTyp extends Typ {
        public final String cname;

        public ClazzTyp(String cname) {
            this.cname = cname;
        }

        @Override
        public String render(int indent, int prec) {
            return cname;
        }

        @Override
        public String toString() {
            return "\"" + escape(cname) + "\"";
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), cname);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return cname.equals(((ClazzTyp)o).cname);
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }
    public static class FuncTyp extends Typ {
        public final List<Typ> argTyps;
        public final Typ retTyp;
        public final Ast.Meth meth;

        public FuncTyp(List<Typ> argTyps, Typ retTyp, Ast.Meth meth) {
            this.argTyps = Collections.unmodifiableList(new ArrayList<>(argTyps));
            this.retTyp = retTyp;
            this.meth = meth;
        }

        public FuncTyp(Ast.Meth meth) {
            ArrayList<Ast.Typ> argTyps = new ArrayList<>();
            for (Ast.VarDecl varDecl : meth.args)
                argTyps.add(varDecl.typ);

            this.argTyps = Collections.unmodifiableList(argTyps);
            this.retTyp = meth.retTyp;
            this.meth = meth;
        }

        public boolean canCallWith(List<Typ> argTyps) {
            if (argTyps.size() != this.argTyps.size())
                return false;
            for (int i = 0; i < this.argTyps.size(); i++) {
                if (!argTyps.get(i).isSubtypeOrEquals(this.argTyps.get(i)))
                    return false;
            }
            return true;
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            sb.append("[");
            for (Typ argTyp : argTyps) {
                if (i++ > 0)
                    sb.append(" * ");
                sb.append(argTyp);
            }
            sb.append(" -> ");
            sb.append(retTyp);
            sb.append(" ]");
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"argTyps\": [");
            int i = 0;
            for (Typ argTyp : argTyps) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(argTyp);
            }
            sb.append("], \"retTyp\": ");
            sb.append(retTyp);
            sb.append("}");
            return sb.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(argTyps, retTyp);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return argTyps.equals(((FuncTyp) o).argTyps) &&
                retTyp.equals(((FuncTyp) o).retTyp);
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }
    public static class PolyFuncTyp extends Typ {
        public final List<FuncTyp> funcTyps;

        public PolyFuncTyp(List<FuncTyp> funcTyps) {
            this.funcTyps = Collections.unmodifiableList(new ArrayList<>(funcTyps));
        }

        public List<FuncTyp> candidates(List<Typ> argTyps) {
            ArrayList<FuncTyp> out = new ArrayList<>();
            for (FuncTyp funcTyp : funcTyps) {
                if (funcTyp.canCallWith(argTyps))
                    out.add(funcTyp);
            }
            return out;
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (FuncTyp funcTyp : funcTyps) {
                if (i++ > 0)
                    sb.append(" | ");
                sb.append(funcTyp.render(0, 0));
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int i = 0;
            for (FuncTyp funcTyp : funcTyps) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(funcTyp);
            }
            sb.append("]");
            return sb.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(funcTyps);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return funcTyps.equals(((PolyFuncTyp)o).funcTyps);
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }

    public static enum UnaryOp {
        NEG("-", "NEG", 5), // Unary minus
        LNOT("!", "LNOT", 5); // Logical Not (!)

        private final String sym;
        private final String ident;
        private final int prec;

        private UnaryOp(String sym, String ident, int prec) {
            this.sym = sym;
            this.ident = ident;
            this.prec = prec;
        }

        public String getSym() {
            return sym;
        }

        public int getPrec() {
            return prec;
        }

        @Override
        public String toString() {
            return "\"" + ident + "\"";
        }
    }

    public static enum BinaryOp {
        PLUS("+", "PLUS", 3), // Arithmetic +
        MINUS("-", "MINUS", 3), // Arithmetic -
        MUL("*", "MUL", 4), // Arithmetic *
        DIV("/", "DIV", 4), // Arithmetic /
        LT("<", "LT", 2), // <
        GT(">", "GT", 2), // >
        LE("<=", "LE", 2), // <=
        GE(">=", "GE", 2), // ?=
        EQ("==", "EQ", 2), // ==
        NE("!=", "NE", 2), // !=
        LAND("&&", "LAND", 1), // Logical &&
        LOR("||", "LOR", 0); // Logical ||

        private final String sym;
        private final String ident;
        private final int prec;

        private BinaryOp(String sym, String ident, int prec) {
            this.sym = sym;
            this.ident = ident;
            this.prec = prec;
        }

        public String getSym() {
            return sym;
        }

        public int getPrec() {
            return prec;
        }

        @Override
        public String toString() {
            return "\"" + ident + "\"";
        }
    }

    public static abstract class Expr implements Renderable, Locatable {
        private Location location;
        public Typ typ;

        @Override
        public void setLocation(Location location) {
            this.location = location;
        }

        @Override
        public Location getLocation() {
            return location;
        }
    }
    public static class StringLitExpr extends Expr {
        public final String str;

        public StringLitExpr(String str) {
            this.str = str;
        }

        @Override
        public String render(int indent, int prec) {
            return "\"" + escape(str) + "\"";
        }

        @Override
        public String toString() {
            return "{\"t\": \"StringLitExpr\", \"str\": \"" + escape(str) + "\"}";
        }
    }
    public static class IntLitExpr extends Expr {
        public final int i;

        public IntLitExpr(int i) {
            this.i = i;
        }

        @Override
        public String render(int indent, int prec) {
            return "" + i;
        }

        @Override
        public String toString() {
            return "{\"t\": \"IntLitExpr\", \"i\": " + i + "}";
        }
    }
    public static class BoolLitExpr extends Expr {
        public final boolean b;

        public BoolLitExpr(boolean b) {
            this.b = b;
        }

        @Override
        public String render(int indent, int prec) {
            return b ? "true" : "false";
        }

        @Override
        public String toString() {
            return "{\"t\": \"BoolLitExpr\", \"b\": " + b + "}";
        }
    }
    public static class NullLitExpr extends Expr {
        @Override
        public String render(int indent, int prec) {
            return "null";
        }

        @Override
        public String toString() {
            return "{\"t\": \"NullLitExpr\"}";
        }
    }
    public static class IdentExpr extends Expr {
        public final String name;
        public VarDecl v; // Resolved VarDecl

        public IdentExpr(String name) {
            this.name = name;
        }

        @Override
        public String render(int indent, int prec) {
            return name;
        }

        @Override
        public String toString() {
            return "{\"t\": \"IdentExpr\", \"name\": \"" + escape(name) + "\"}";
        }
    }
    public static class ThisExpr extends Expr {
        @Override
        public String render(int indent, int prec) {
            return "this";
        }

        @Override
        public String toString() {
            return "{\"t\": \"ThisExpr\"}";
        }
    }
    public static class UnaryExpr extends Expr {
        public final UnaryOp op;
        public final Expr expr;

        public UnaryExpr(UnaryOp op, Expr expr) {
            this.op = op;
            this.expr = expr;
        }

        @Override
        public String render(int indent, int prec) {
            int myPrec = op.getPrec();
            return doParen(op.getSym() + expr.render(0, myPrec), myPrec, prec);
        }

        @Override
        public String toString() {
            return "{\"t\": \"UnaryExpr\", \"op\": " + op + ", \"expr\": " + expr + "}";
        }
    }
    public static class BinaryExpr extends Expr {
        public final BinaryOp op;
        public final Expr lhs;
        public final Expr rhs;

        public BinaryExpr(BinaryOp op, Expr lhs, Expr rhs) {
            this.op = op;
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public String render(int indent, int prec) {
            int myPrec = op.getPrec();
            return doParen(lhs.render(0, myPrec) + " " + op.getSym() + " " + rhs.render(0, myPrec), myPrec, prec);
        }

        @Override
        public String toString() {
            return "{\"t\": \"BinaryExpr\", \"op\": " + op + ", \"lhs\": " + lhs + ", \"rhs\": " + rhs + "}";
        }
    }
    public static class DotExpr extends Expr {
        public final Expr target;
        public final String ident;

        public DotExpr(Expr target, String ident) {
            this.target = target;
            this.ident = ident;
        }

        @Override
        public String render(int indent, int prec) {
            return doParen(target.render(0, 6) + "." + ident, 6, prec);
        }

        @Override
        public String toString() {
            return "{\"t\": \"DotExpr\", \"target\": " + target + ", \"ident\": \"" + escape(ident) + "\"}";
        }
    }
    public static class CallExpr extends Expr {
        public final Expr target;
        public final List<Expr> args;
        public Meth meth; // Resolved meth

        public CallExpr(Expr target, List<Expr> args) {
            this.target = target;
            this.args = Collections.unmodifiableList(new ArrayList<>(args));
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            sb.append(target.render(0, 6)).append("(");
            int i = 0;
            for (Expr expr : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(expr.render(0, 0));
            }
            sb.append(")");
            return doParen(sb.toString(), 6, prec);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\": \"CallExpr\", \"target\": ");
            sb.append(target);
            sb.append(", \"args\": [");
            int i = 0;
            for (Expr expr : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(expr);
            }
            sb.append("]}");
            return sb.toString();
        }
    }
    public static class NewExpr extends Expr {
        public final String cname;

        public NewExpr(String cname) {
            this.cname = cname;
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            sb.append("new ").append(cname).append("()");
            return doParen(sb.toString(), 6, prec);
        }

        @Override
        public String toString() {
            return "{\"t\": \"NewExpr\", \"cname\": \"" + escape(cname) + "\"}";
        }

    }

    public static abstract class Stmt implements Renderable, Locatable {
        private Location location;

        @Override
        public void setLocation(Location location) {
            this.location = location;
        }

        @Override
        public Location getLocation() {
            return location;
        }
    }
    public static class IfStmt extends Stmt {
        public final Expr cond;
        public final List<Stmt> thenStmts;
        public final List<Stmt> elseStmts;

        public IfStmt(Expr cond, List<Stmt> thenStmts, List<Stmt> elseStmts) {
            this.cond = cond;
            this.thenStmts = Collections.unmodifiableList(new ArrayList<>(thenStmts));
            this.elseStmts = Collections.unmodifiableList(new ArrayList<>(elseStmts));
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("if (").append(cond.render(0, 0)).append(") {\n");
            for (Stmt stmt : thenStmts) {
                sb.append(stmt.render(indent + 1, 0));
            }
            doIndent(sb, indent);
            sb.append("} else {\n");
            for (Stmt stmt : elseStmts) {
                sb.append(stmt.render(indent + 1, 0));
            }
            doIndent(sb, indent);
            sb.append("}\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\": \"IfStmt\", \"cond\": ");
            sb.append(cond);
            sb.append(", \"thenStmts\": [");
            int i = 0;
            for (Stmt stmt : thenStmts) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(stmt);
            }
            sb.append("], \"elseStmts\": [");
            i = 0;
            for (Stmt stmt : elseStmts) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(stmt);
            }
            sb.append("]}");
            return sb.toString();
        }
    }
    public static class WhileStmt extends Stmt {
        public final Expr cond;
        public final List<Stmt> stmts;

        public WhileStmt(Expr cond, List<Stmt> stmts) {
            this.cond = cond;
            this.stmts = Collections.unmodifiableList(new ArrayList<>(stmts));
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("while (").append(cond.render(0, 0)).append(") {\n");
            for (Stmt stmt : stmts) {
                sb.append(stmt.render(indent + 1, 0));
            }
            doIndent(sb, indent);
            sb.append("}\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\": \"WhileStmt\", \"cond\": ");
            sb.append(cond);
            sb.append(", \"stmts\": [");
            int i = 0;
            for (Stmt stmt : stmts) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(stmt);
            }
            sb.append("]}");
            return sb.toString();
        }
    }
    public static class ReadlnStmt extends Stmt {
        public final String ident;
        public VarDecl v; // Resolved ident

        public ReadlnStmt(String ident) {
            this.ident = ident;
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("readln(").append(ident).append(");\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            return "{\"t\": \"ReadlnStmt\", \"ident\": \"" + escape(ident) + "\"}";
        }
    }
    public static class PrintlnStmt extends Stmt {
        public final Expr expr;

        public PrintlnStmt(Expr expr) {
            this.expr = expr;
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("println(").append(expr.render(0, 0)).append(");\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            return "{\"t\": \"PrintlnStmt\", \"expr\": " + expr + "}";
        }
    }
    public static class VarAssignStmt extends Stmt {
        public final String lhs;
        public final Expr rhs;
        public VarDecl lhsVar; // Resolved lhs ident

        public VarAssignStmt(String lhs, Expr rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(lhs).append(" = ").append(rhs.render(0, 0)).append(";\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            return "{\"t\": \"VarAssignStmt\", \"lhs\": " + escape(lhs) + ", \"rhs\": " + rhs + "}";
        }
    }
    public static class FieldAssignStmt extends Stmt {
        public final Expr lhsExpr;
        public final String lhsField;
        public final Expr rhs;

        public FieldAssignStmt(Expr lhsExpr, String lhsField, Expr rhs) {
            this.lhsExpr = lhsExpr;
            this.lhsField = lhsField;
            this.rhs = rhs;
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(lhsExpr.render(0, 6)).append(".").append(lhsField);
            sb.append(" = ");
            sb.append(rhs.render(0, 0)).append(";\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            return "{\"t\": \"FieldAssignStmt\", \"lhsExpr\": " + lhsExpr + ", \"lhsField\": " + escape(lhsField) + ", \"rhs\": " + rhs + "}";
        }
    }
    public static class ReturnStmt extends Stmt {
        public final Expr expr;

        public ReturnStmt(Expr expr) {
            this.expr = expr; // Nullable
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("return");
            if (expr != null) {
                sb.append(' ').append(expr.render(0, 0));
            }
            sb.append(";\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\": \"ReturnStmt\"");
            if (expr != null) {
                sb.append(", \"expr\": ");
                sb.append(expr);
            }
            sb.append('}');
            return sb.toString();
        }
    }
    public static class CallStmt extends Stmt {
        public final Expr target;
        public final List<Expr> args;
        public Meth targetMeth; // Resolved target meth

        public CallStmt(Expr target, List<Expr> args) {
            this.target = target;
            this.args = Collections.unmodifiableList(new ArrayList<>(args));
        }

        @Override
        public String render(int indent, int prec) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(target.render(0, 6)).append("(");
            int i = 0;
            for (Expr arg : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(arg.render(0, 0));
            }
            sb.append(");\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\": \"CallStmt\", \"target\": ");
            sb.append(target);
            sb.append(", \"args\": [");
            int i = 0;
            for (Expr arg : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(arg);
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static String escape(String str) {
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (c == '\'')
                sb.append("\\'");
            else if (c == '\"')
                sb.append("\\\"");
            else if (c == '\r')
                sb.append("\\r");
            else if (c == '\n')
                sb.append("\\n");
            else if (c == '\t')
                sb.append("\\t");
            else if (c == '\\')
                sb.append("\\\\");
            else if (c == '\b')
                sb.append("\\b");
            else if (c < 32 || c >= 127)
                sb.append(String.format("\\x%02x", (int)c));
            else
                sb.append(c);
        }
        return sb.toString();
    }

    public static String doParen(String out, int requiredPrec, int currentPrec) {
        return currentPrec >= requiredPrec ? "(" + out + ")" : out;
    }
}
