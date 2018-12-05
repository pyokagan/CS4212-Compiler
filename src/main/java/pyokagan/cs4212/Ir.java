package pyokagan.cs4212;

import java.util.*;
import java.math.*;

public class Ir {
    public interface Renderable {
        String render(int indent);
    }

    public static void doIndent(StringBuilder sb, int indent) {
        while (indent > 0) {
            sb.append("  ");
            indent--;
        }
    }

    public static class Prog {
        public ArrayList<Data> datas;
        public ArrayList<Meth> meths;

        public Prog(List<Data> datas, List<Meth> meths) {
            this.datas = new ArrayList<>(datas);
            this.meths = new ArrayList<>(meths);
        }

        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            for (Data data : datas) {
                sb.append(data.render(indent));
            }
            for (Meth meth : meths) {
                sb.append(meth.render(indent));
            }
            return sb.toString();
        }
    }

    public static class Data {
        public String cname;
        public ArrayList<DataField> fields;

        public Data(String cname, List<DataField> fields) {
            this.cname = cname;
            this.fields = new ArrayList<>(fields);
        }

        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("Data3 ").append(cname).append(" {\n");
            for (DataField dataField : fields) {
                sb.append(dataField.render(indent+1));
            }
            doIndent(sb, indent);
            sb.append("}\n");
            return sb.toString();
        }
    }

    public static class DataField {
        public Ast.Typ typ;
        public String name;

        public DataField(Ast.Typ typ, String name) {
            this.typ = typ;
            this.name = name;
        }

        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(typ.render(0, 0)).append(" ").append(name).append(";\n");
            return sb.toString();
        }
    }

    public static class PhiWeb {
        public boolean needStack; // Set to true if the vars need stack space
    }

    /**
     * Represents a local variable / argument / temporary.
     */
    public static class Var {
        public Ast.Typ typ;
        public String name;
        public int reg = -1;
        public PhiWeb phiWeb; // Phi web allocated to the var (PhiWebPass)

        public Var(Ast.Typ typ, String name) {
            this.typ = typ;
            this.name = name;
        }

        public String render() {
            if (reg >= 0)
                return name + " {r" + reg + "}";
            else
                return name;
        }

        @Override
        public String toString() {
            return "Var(" + name + ", " + reg + ")";
        }
    }

    public static class Meth {
        public Ast.Typ retTyp;
        public String name;
        public ArrayList<Var> args;
        public ArrayList<Var> locals;
        public ArrayList<Block> blocks;
        public ArrayList<Block> blocksPre; // Blocks in preorder
        public ArrayList<Block> blocksPo; // Blocks in postorder
        public ArrayList<Block> blocksRpo; // Blocks in reverse postorder
        public ArrayList<PhiWeb> phiWebs;

        public Meth(Ast.Typ retTyp, String name) {
            this.retTyp = retTyp;
            this.name = name;
        }

        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(retTyp.render(0, 0));
            sb.append(" ");
            sb.append(name);
            sb.append("(");
            int i = 0;
            for (Var v : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(v.typ.render(0, 0));
                sb.append(" ");
                sb.append(v.name);
            }
            sb.append(") {\n");
            for (Var v : locals) {
                doIndent(sb, indent + 1);
                sb.append(v.typ.render(0, 0));
                sb.append(" ");
                sb.append(v.name);
                sb.append(";\n");
            }
            sb.append("\n");
            for (Block block : blocks) {
                sb.append(block.render(indent + 1));
            }
            doIndent(sb, indent);
            sb.append("}\n");
            return sb.toString();
        }

        public String renderCfg() {
            StringBuilder sb = new StringBuilder();
            sb.append("digraph cfg {\n");
            for (Ir.Block b : blocks) {
                if (b.outgoingDirect != null)
                    sb.append("  ").append(b.label.name).append(" -> ").append(b.outgoingDirect.label.name).append(";\n");
                if (b.outgoingCond != null)
                    sb.append("  ").append(b.label.name).append(" -> ").append(b.outgoingCond.label.name).append(";\n");
            }
            sb.append("}\n");
            return sb.toString();
        }
    }

    public static class Block {
        public LabelStmt label; // Block label, null if it does not have one (yet)
        public ArrayList<Stmt> stmts;
        public ArrayList<Block> incoming = new ArrayList<>();
        public int postorderIndex;

        public Block outgoingDirect;
        public Block outgoingCond;

        public Block(List<Stmt> stmts) {
            this.stmts = new ArrayList<>(stmts);
        }

        public boolean isTerminal() {
            return !stmts.isEmpty() && stmts.get(stmts.size() - 1) instanceof Ir.ReturnStmt;
        }

        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            if (label != null)
                sb.append(label.render(indent));
            for (Stmt stmt : stmts) {
                sb.append(stmt.render(indent));
            }
            return sb.toString();
        }

        public List<Block> getOutgoing() {
            ArrayList<Block> out = new ArrayList<>();
            if (outgoingDirect != null)
                out.add(outgoingDirect);
            if (outgoingCond != null)
                out.add(outgoingCond);
            return out;
        }

        @Override
        public String toString() {
            return "Block(" + (label != null ? label.name : "???") + ")";
        }
    }

    public static abstract class Stmt {
        public abstract String render(int indent);
        public abstract List<Ir.Rval> getRvals();

        public ArrayList<Ir.Var> getUses() {
            ArrayList<Ir.Var> out = new ArrayList<>();
            for (Ir.Rval rv : getRvals()) {
                if (!(rv instanceof Ir.VarRval))
                    continue;
                out.add(((Ir.VarRval) rv).v);
            }
            return out;
        }

        public List<Ir.Var> getDefs() {
            return Collections.emptyList();
        }

        public void setDef(int i, Ir.Var v) {
            throw new IndexOutOfBoundsException();
        }
    }
    public static abstract class JumpStmt extends Stmt {
        public LabelStmt label;
    }
    public static class LabelStmt extends Stmt {
        public String name;

        public LabelStmt(String name) {
            this.name = name;
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Collections.emptyList();
        }

        @Override
        public String render(int indent) {
            return name + ":\n";
        }
    }
    public static class CmpStmt extends JumpStmt {
        public CondOp op;
        public Rval a;
        public Rval b;

        public CmpStmt(CondOp op, Rval a, Rval b, LabelStmt label) {
            this.op = op;
            this.a = a;
            this.b = b;
            this.label = label;
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Arrays.asList(a, b);
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("if (");
            sb.append(a.render());
            sb.append(" ");
            sb.append(op.getSym());
            sb.append(" ");
            sb.append(b.render());
            sb.append(") goto ");
            sb.append(label == null ? "[Null]" : label.name);
            sb.append(";\n");
            return sb.toString();
        }
    }
    public static class GotoStmt extends JumpStmt {
        public GotoStmt(LabelStmt label) {
            this.label = label;
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Collections.emptyList();
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("goto ").append(label == null ? "[Null]" : label.name).append(";\n");
            return sb.toString();
        }
    }
    public static class ReadlnStmt extends Stmt {
        private ArrayList<Ir.Var> defs = new ArrayList<>();

        public ReadlnStmt(Var dst) {
            defs.add(dst);
        }

        public Ir.Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Collections.emptyList();
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("readln(").append(defs.get(0).render()).append(");\n");
            return sb.toString();
        }
    }
    public static class PrintlnStmt extends Stmt {
        public Rval rv;

        public PrintlnStmt(Rval rv) {
            this.rv = rv;
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Arrays.asList(rv);
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("println(").append(rv.render()).append(");\n");
            return sb.toString();
        }
    }
    public static class BinaryStmt extends Stmt {
        private ArrayList<Ir.Var> defs = new ArrayList<>();
        public BinaryOp op;
        public Rval a;
        public Rval b;

        public BinaryStmt(Var dst, BinaryOp op, Rval a, Rval b) {
            defs.add(dst);
            this.op = op;
            this.a = a;
            this.b = b;
        }

        public Ir.Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Arrays.asList(a, b);
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(getDst().render()).append(" = ");
            sb.append(a.render());
            sb.append(" ").append(op.getSym()).append(" ");
            sb.append(b.render());
            sb.append(";\n");
            return sb.toString();
        }
    }
    public static class UnaryStmt extends Stmt {
        private ArrayList<Ir.Var> defs = new ArrayList<>();
        public Ir.UnaryOp op;
        public Rval a;

        public UnaryStmt(Var dst, Ir.UnaryOp op, Rval a) {
            defs.add(dst);
            this.op = op;
            this.a = a;
        }

        public Ir.Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Arrays.asList(a);
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(getDst().render()).append(" = ");
            sb.append(op.getSym()).append(" ");
            sb.append(a.render());
            sb.append(";\n");
            return sb.toString();
        }
    }
    public static class FieldAccessStmt extends Stmt {
        private ArrayList<Ir.Var> defs = new ArrayList<>();
        public Rval target;
        public String field;

        public FieldAccessStmt(Var dst, Rval target, String field) {
            defs.add(dst);
            this.target = target;
            this.field = field;
        }

        public Ir.Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Arrays.asList(target);
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(getDst().render()).append(" = ");
            sb.append(target.render()).append(".").append(field).append(";\n");
            return sb.toString();
        }
    }
    public static class FieldAssignStmt extends Stmt {
        public Var dst;
        public String field;
        public Rval src;

        public FieldAssignStmt(Var dst, String field, Rval src) {
            this.dst = dst;
            this.field = field;
            this.src = src;
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Arrays.asList(src);
        }

        @Override
        public ArrayList<Ir.Var> getUses() {
            ArrayList<Ir.Var> out = super.getUses();
            out.add(dst);
            return out;
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(dst.render()).append(".").append(field).append(" = ");
            sb.append(src.render()).append(";\n");
            return sb.toString();
        }
    }
    public static class AssignStmt extends Stmt {
        private ArrayList<Ir.Var> defs = new ArrayList<>();
        public Rval src;

        public AssignStmt(Var dst, Rval src) {
            defs.add(dst);
            this.src = src;
        }

        public Ir.Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Arrays.asList(src);
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(getDst().render()).append(" = ").append(src.render()).append(";\n");
            return sb.toString();
        }
    }
    public static class ReturnStmt extends Stmt {
        public Rval rv; // Nullable

        public ReturnStmt(Rval rv) {
            this.rv = rv;
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return rv == null ? Collections.emptyList() : Arrays.asList(rv);
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("return");
            if (rv != null) {
                sb.append(" ");
                sb.append(rv.render());
            }
            sb.append(";\n");
            return sb.toString();
        }
    }
    public static abstract class CallStmt extends Stmt {
        private ArrayList<Ir.Var> defs = new ArrayList<>();
        public ArrayList<Rval> args;

        protected CallStmt(Var dst) {
            defs.add(dst);
        }

        public Ir.Var getDst() {
            return defs.get(0);
        }

        public void setDst(Var v) {
            setDef(0, v);
        }

        @Override
        public List<Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Rval> getRvals() {
            return args;
        }
    }
    public static class MethCallStmt extends CallStmt {
        public Meth meth;

        public MethCallStmt(Var dst, Meth meth, ArrayList<Rval> args) {
            super(dst);
            this.meth = meth;
            this.args = args;
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            if (getDst() != null) {
                sb.append(getDst().render()).append(" = ");
            }
            sb.append(meth.name).append("(");
            int i = 0;
            for (Rval rv : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(rv.render());
            }
            sb.append(");\n");
            return sb.toString();
        }
    }
    public static class ExternCallStmt extends CallStmt {
        public String target;

        public ExternCallStmt(Var dst, String target, ArrayList<Rval> args) {
            super(dst);
            this.target = target;
            this.args = args;
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            if (getDst() != null) {
                sb.append(getDst().render()).append(" = ");
            }
            sb.append(target).append("(");
            int i = 0;
            for (Rval rv : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(rv.render());
            }
            sb.append(");\n");
            return sb.toString();
        }
    }
    public static class CallPrepStmt extends Stmt {
        public ArrayList<Ir.Var> defs = new ArrayList<>();
        public ArrayList<Ir.Var> srcs = new ArrayList<>();
        public int numArgs; // First numArgs defs are assigned registers r0-r3

        @Override
        public List<Ir.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Var v) {
            defs.set(i, v);
        }

        @Override
        public ArrayList<Ir.Var> getUses() {
            return new ArrayList<>(srcs);
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Collections.emptyList();
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            int i = 0;
            for (Var v : defs) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(v.render());
            }
            sb.append(" = ");
            i = 0;
            for (Var v : srcs) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(v.render());
            }
            sb.append(";\n");
            return sb.toString();
        }
    }
    public static class NewStmt extends Stmt {
        private ArrayList<Var> defs = new ArrayList<>();
        public Data data;

        public NewStmt(Var dst, Data data) {
            defs.add(dst);
            this.data = data;
        }

        public Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Collections.emptyList();
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(getDst().render()).append(" = ");
            sb.append("new ").append(data.cname).append("();\n");
            return sb.toString();
        }
    }
    public static class PhiStmt extends Stmt {
        private ArrayList<Var> defs = new ArrayList<>();
        public Var originalVar;
        public ArrayList<Var> args;
        public boolean memory; // True if the definition starts out spilled, and needs to then be reloaded

        public PhiStmt(Var dst, int size) {
            defs.add(dst);
            this.originalVar = dst;
            this.args = new ArrayList<>();
            for (int i = 0; i < size; i++)
                this.args.add(null);
        }

        public Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Collections.emptyList();
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(getDst().render()).append(" = ").append(memory ? "PHIMEM" : "PHI").append("(");
            int i = 0;
            for (Var v : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(v == null ? "null" : v.render());
            }
            sb.append(");");
            if (memory)
                sb.append(' ').append(getDst().phiWeb);
            sb.append('\n');
            return sb.toString();
        }
    }
    public static class SpillStmt extends Stmt {
        public Var v;

        public SpillStmt(Var v) {
            this.v = v;
        }

        @Override
        public ArrayList<Ir.Var> getUses() {
            ArrayList<Ir.Var> out = new ArrayList<>();
            out.add(v);
            return out;
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Collections.emptyList();
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("SPILL(").append(v.render()).append(", ").append(v.phiWeb).append(");\n");
            return sb.toString();
        }
    }
    public static class ReloadStmt extends Stmt {
        private ArrayList<Var> defs = new ArrayList<>();

        public ReloadStmt(Var dst) {
            defs.add(dst);
        }

        public Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Collections.emptyList();
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append(getDst().render()).append(" = RELOAD(").append(getDst().phiWeb).append(");\n");
            return sb.toString();
        }
    }
    public static class StackArgStmt extends Stmt {
        public Var v;
        public int idx; // Stack location -- 0 = 5th argument, 1 = 6th argument etc.

        public StackArgStmt(Var v, int idx) {
            this.v = v;
            this.idx = idx;
        }

        @Override
        public ArrayList<Ir.Var> getUses() {
            ArrayList<Ir.Var> out = new ArrayList<>();
            out.add(v);
            return out;
        }

        @Override
        public List<Ir.Rval> getRvals() {
            return Collections.emptyList();
        }

        @Override
        public String render(int indent) {
            StringBuilder sb = new StringBuilder();
            doIndent(sb, indent);
            sb.append("STACKARG(").append(v.render()).append(", ").append(idx).append(");\n");
            return sb.toString();
        }
    }

    public static abstract class Rval {
        public abstract String render();

        public abstract Ast.Typ getTyp();
    }
    public static class StringLitRval extends Rval {
        public final String str;

        public StringLitRval(String str) {
            this.str = str;
        }

        @Override
        public Ast.Typ getTyp() {
            return new Ast.StringTyp();
        }

        @Override
        public String render() {
            return "\"" + Ast.escape(str) + "\"";
        }
    }
    public static class IntLitRval extends Rval {
        public final int i;

        public IntLitRval(int i) {
            this.i = i;
        }

        @Override
        public Ast.Typ getTyp() {
            return new Ast.IntTyp();
        }

        @Override
        public String render() {
            return "" + i;
        }
    }
    public static class BoolLitRval extends Rval {
        public final boolean b;

        public BoolLitRval(boolean b) {
            this.b = b;
        }

        @Override
        public Ast.Typ getTyp() {
            return new Ast.BoolTyp();
        }

        @Override
        public String render() {
            return b == true ? "true" : "false";
        }
    }
    public static class NullLitRval extends Rval {
        @Override
        public Ast.Typ getTyp() {
            return new Ast.NullTyp();
        }

        @Override
        public String render() {
            return "NULL";
        }
    }
    public static class VarRval extends Rval {
        public Var v;

        public VarRval(Var v) {
            this.v = v;
        }

        @Override
        public Ast.Typ getTyp() {
            return v.typ;
        }

        @Override
        public String render() {
            return v.render();
        }
    }

    public static enum UnaryOp {
        NEG("-");

        private final String sym;

        private UnaryOp(String sym) {
            this.sym = sym;
        }

        public String getSym() {
            return sym;
        }
    }

    public static enum BinaryOp {
        PLUS("+"),
        MINUS("-"),
        MUL("*"),
        DIV("/"),
        RSB("RSB");

        private final String sym;

        private BinaryOp(String sym) {
            this.sym = sym;
        }

        public String getSym() {
            return sym;
        }
    }

    public static enum CondOp {
        LT("<"),
        GT(">"),
        LE("<="),
        GE(">="),
        EQ("=="),
        NE("!=");

        private final String sym;

        private CondOp(String sym) {
            this.sym = sym;
        }

        public String getSym() {
            return sym;
        }

        public CondOp flip() {
        switch (this) {
        case LT:
            return GT;
        case GT:
            return LT;
        case LE:
            return GE;
        case GE:
            return LE;
        case EQ:
            return EQ;
        case NE:
            return NE;
        default:
            throw new AssertionError("BUG");
        }
        }
    }
}
