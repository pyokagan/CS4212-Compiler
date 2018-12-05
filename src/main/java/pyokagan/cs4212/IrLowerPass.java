package pyokagan.cs4212;

import java.util.*;

/**
 * Lower IR into a form that models the ARM architecture.
 * This will not change control flow
 *
 * x = new Foo() -> x = malloc (sizeof Foo)
 * a / b -> __aeabi_idiv(a, b)
 * readln(x) -> x = readln_int(), x = readln_string(), x = readln_bool()
 * println(x) -> println_int(x), println_string(x), println_bool(x)
 * if (constant) goto -> goto
 * For other stmts, it depends on whether the constants can fit inside the same instruction or not.
 */
public class IrLowerPass {
    private final Ir.Meth meth;
    private final String READLN_STRING = "readln_string";
    private final String READLN_INT = "readln_int";
    private final String READLN_BOOL = "readln_bool";
    private final String PRINTLN_STRING = "puts";
    private final String PRINTLN_INT = "println_int";
    private final String PRINTLN_BOOL = "println_bool";
    private final String IDIV = "__aeabi_idiv";
    private final String MALLOC = "malloc";
    private int tmpCounter;

    private IrLowerPass(Ir.Meth meth) {
        this.meth = meth;
    }

    public static void run(Ir.Meth meth) {
        IrLowerPass lp = new IrLowerPass(meth);
        for (Ir.Block block : meth.blocks) {
            ArrayList<Ir.Stmt> outStmts = new ArrayList<>();
            for (Ir.Stmt stmt : block.stmts)
                lp.doStmt(outStmts, stmt);
            block.stmts = outStmts;
        }
    }

    private void doStmt(ArrayList<Ir.Stmt> outStmts, Ir.Stmt stmt) {
        if (stmt instanceof Ir.CmpStmt) {
            doCmpStmt(outStmts, (Ir.CmpStmt) stmt);
        } else if (stmt instanceof Ir.ReadlnStmt) {
            Ir.ReadlnStmt readlnStmt = (Ir.ReadlnStmt) stmt;

            // Convert to appropriate call depending on dst type
            if (readlnStmt.getDst().typ instanceof Ast.IntTyp) {
                doStmt(outStmts, new Ir.ExternCallStmt(readlnStmt.getDst(), READLN_INT, new ArrayList<>()));
            } else if (readlnStmt.getDst().typ instanceof Ast.StringTyp) {
                doStmt(outStmts, new Ir.ExternCallStmt(readlnStmt.getDst(), READLN_STRING, new ArrayList<>()));
            } else if (readlnStmt.getDst().typ instanceof Ast.BoolTyp) {
                doStmt(outStmts, new Ir.ExternCallStmt(readlnStmt.getDst(), READLN_BOOL, new ArrayList<>()));
            } else {
                throw new AssertionError("BUG: unsupported readln type");
            }
        } else if (stmt instanceof Ir.PrintlnStmt) {
            Ir.PrintlnStmt printlnStmt = (Ir.PrintlnStmt) stmt;

            // Convert to appropriate call depending on arg type
            ArrayList<Ir.Rval> args = new ArrayList<>();
            args.add(printlnStmt.rv);
            if (printlnStmt.rv.getTyp().isSubtypeOrEquals(new Ast.IntTyp())) {
                doStmt(outStmts, new Ir.ExternCallStmt(null, PRINTLN_INT, args));
            } else if (printlnStmt.rv.getTyp().isSubtypeOrEquals(new Ast.StringTyp())) {
                doStmt(outStmts, new Ir.ExternCallStmt(null, PRINTLN_STRING, args));
            } else if (printlnStmt.rv.getTyp().isSubtypeOrEquals(new Ast.BoolTyp())) {
                doStmt(outStmts, new Ir.ExternCallStmt(null, PRINTLN_BOOL, args));
            } else {
                throw new AssertionError("BUG: unsupported println type");
            }
        } else if (stmt instanceof Ir.BinaryStmt) {
            doBinaryStmt(outStmts, (Ir.BinaryStmt) stmt);
        } else if (stmt instanceof Ir.UnaryStmt) {
            doUnaryStmt(outStmts, (Ir.UnaryStmt) stmt);
        } else if (stmt instanceof Ir.FieldAssignStmt) {
            doFieldAssignStmt(outStmts, (Ir.FieldAssignStmt) stmt);
        } else if (stmt instanceof Ir.FieldAccessStmt) {
            doFieldAccessStmt(outStmts, (Ir.FieldAccessStmt) stmt);
        } else if (stmt instanceof Ir.CallStmt) {
            doCallStmt(outStmts, (Ir.CallStmt) stmt);
        } else if (stmt instanceof Ir.NewStmt) {
            // Convert to malloc()
            Ir.NewStmt newStmt = (Ir.NewStmt) stmt;

            int memSize = newStmt.data.fields.size() * 4;
            if (memSize > 0) {
                ArrayList<Ir.Rval> args = new ArrayList<>();
                args.add(new Ir.IntLitRval(newStmt.data.fields.size() * 4));
                doStmt(outStmts, new Ir.ExternCallStmt(newStmt.getDst(), MALLOC, args));
            } else {
                doStmt(outStmts, new Ir.AssignStmt(newStmt.getDst(), new Ir.NullLitRval()));
            }
        } else {
            outStmts.add(stmt);
        }
    }

    private void doCmpStmt(ArrayList<Ir.Stmt> outStmts, Ir.CmpStmt stmt) {
        if (ArmGen.isConstant(stmt.a) && ArmGen.isConstant(stmt.b)) {
            // Can be resolved at ArmGen time
            outStmts.add(stmt);
            return;
        }

        if (!ArmGen.isValidOperand2Const(stmt.b) && ArmGen.isValidOperand2Const(stmt.a)) {
            // Swap them so that the operand2 constant is the 2nd operand
            Ir.Rval tmp = stmt.a;
            stmt.a = stmt.b;
            stmt.b = tmp;
            stmt.op = stmt.op.flip();
        }

        if (!(stmt.a instanceof Ir.VarRval)) {
            Ir.Var tmp = genTemp(stmt.a.getTyp());
            doStmt(outStmts, new Ir.AssignStmt(tmp, stmt.a));
            stmt.a = new Ir.VarRval(tmp);
        }

        if (!ArmGen.isValidOperand2Const(stmt.b) && !(stmt.b instanceof Ir.VarRval)) {
            Ir.Var tmp = genTemp(stmt.b.getTyp());
            doStmt(outStmts, new Ir.AssignStmt(tmp, stmt.b));
            stmt.b = new Ir.VarRval(tmp);
        }

        outStmts.add(stmt);
    }

    private void doBinaryStmt(ArrayList<Ir.Stmt> outStmts, Ir.BinaryStmt stmt) {
        if (ArmGen.isConstant(stmt.a) && ArmGen.isConstant(stmt.b)
                && (stmt.op != Ir.BinaryOp.DIV || ArmGen.toIntConstant(stmt.b) != 0)) {
            // Can be resolved at ArmGen time (but only if we are not dividing by 0)
            outStmts.add(stmt);
            return;
        }

        // Handle DIV specially -- it does not have an equivalent ARM instruction
        if (stmt.op == Ir.BinaryOp.DIV) {
            ArrayList<Ir.Rval> args = new ArrayList<>();
            args.add(stmt.a);
            args.add(stmt.b);
            doStmt(outStmts, new Ir.ExternCallStmt(stmt.getDst(), IDIV, args));
            return;
        }

        if ((stmt.op == Ir.BinaryOp.PLUS || stmt.op == Ir.BinaryOp.MINUS || stmt.op == Ir.BinaryOp.RSB) && !ArmGen.isValidOperand2Const(stmt.b) && ArmGen.isValidOperand2Const(stmt.a)) {
            // Swap them so that the operand2 const is the 2nd operand
            Ir.Rval tmp = stmt.a;
            stmt.a = stmt.b;
            stmt.b = tmp;
            if (stmt.op == Ir.BinaryOp.MINUS)
                stmt.op = Ir.BinaryOp.RSB;
            else if (stmt.op == Ir.BinaryOp.RSB)
                stmt.op = Ir.BinaryOp.MINUS;
        }

        if (!(stmt.a instanceof Ir.VarRval)) {
            Ir.Var tmp = genTemp(stmt.a.getTyp());
            doStmt(outStmts, new Ir.AssignStmt(tmp, stmt.a));
            stmt.a = new Ir.VarRval(tmp);
        }

        if ((stmt.op == Ir.BinaryOp.MUL || !ArmGen.isValidOperand2Const(stmt.b)) && !(stmt.b instanceof Ir.VarRval)) {
            Ir.Var tmp = genTemp(stmt.b.getTyp());
            doStmt(outStmts, new Ir.AssignStmt(tmp, stmt.b));
            stmt.b = new Ir.VarRval(tmp);
        }

        outStmts.add(stmt);
    }

    private void doUnaryStmt(ArrayList<Ir.Stmt> outStmts, Ir.UnaryStmt stmt) {
        if (ArmGen.isConstant(stmt.a)) {
            // Can be resolved at ArmGen time
            outStmts.add(stmt);
            return;
        }

        if (!(stmt.a instanceof Ir.VarRval)) {
            Ir.Var tmp = genTemp(stmt.a.getTyp());
            doStmt(outStmts, new Ir.AssignStmt(tmp, stmt.a));
            stmt.a = new Ir.VarRval(tmp);
        }

        outStmts.add(stmt);
    }

    private void doFieldAccessStmt(ArrayList<Ir.Stmt> outStmts, Ir.FieldAccessStmt stmt) {
        // We are using immediate-offset LDR, so target MUST be a var
        if (stmt.target instanceof Ir.VarRval) {
            // Everything is OK
            outStmts.add(stmt);
            return;
        }

        Ir.Var tmp = genTemp(stmt.target.getTyp());
        doStmt(outStmts, new Ir.AssignStmt(tmp, stmt.target));
        stmt.target = new Ir.VarRval(tmp);
        doStmt(outStmts, stmt);
    }

    private void doFieldAssignStmt(ArrayList<Ir.Stmt> outStmts, Ir.FieldAssignStmt stmt) {
        // We are using immediate-offset STR, so src MUST be a var
        if (stmt.src instanceof Ir.VarRval) {
            // Everything is OK
            outStmts.add(stmt);
            return;
        }

        Ir.Var tmp = genTemp(stmt.src.getTyp());
        doStmt(outStmts, new Ir.AssignStmt(tmp, stmt.src));
        stmt.src = new Ir.VarRval(tmp);
        doStmt(outStmts, stmt);
    }

    private void doCallStmt(ArrayList<Ir.Stmt> outStmts, Ir.CallStmt stmt) {
        // First 4 args must be in vars (registers).
        // Remove the rest of the args and store them via StackArgStmt
        for (int i = stmt.args.size() - 1; i >= 4; i--) {
            Ir.Rval rv = stmt.args.get(i);
            Ir.Var v;
            if (!(rv instanceof Ir.VarRval)) {
                // Must convert into a var first
                v = genTemp(rv.getTyp());
                doStmt(outStmts, new Ir.AssignStmt(v, rv));
            } else {
                v = ((Ir.VarRval) rv).v;
            }
            doStmt(outStmts, new Ir.StackArgStmt(v, i - 4));
            stmt.args.remove(i);
        }

        // Create vars for the rest of the arguments
        for (int i = 0; i < stmt.args.size(); i++) {
            Ir.Rval rv = stmt.args.get(i);
            if (rv instanceof Ir.VarRval)
                continue; // All OK
            Ir.Var tmp = genTemp(rv.getTyp());
            doStmt(outStmts, new Ir.AssignStmt(tmp, rv));
            stmt.args.set(i, new Ir.VarRval(tmp));
        }

        // calls MUST have a return value
        if (stmt.getDst() == null) {
            stmt.setDst(genTemp(new Ast.VoidTyp()));
        }

        outStmts.add(stmt);
    }

    private Ir.Var genTemp(Ast.Typ typ) {
        String name = "%lt" + tmpCounter;
        tmpCounter++;
        Ir.Var v = new Ir.Var(typ, name);
        meth.locals.add(v);
        return v;
    }



}
