package pyokagan.cs4212;

import java.util.*;
import java.io.*;

public class IrGen {
    // Outputs
    private ArrayList<Ir.Data> datas = new ArrayList<>();
    private ArrayList<Ir.Meth> meths = new ArrayList<>();

    // cname to Ir.Data map
    private HashMap<String, Ir.Data> dataMap = new HashMap<>();

    private IdentityHashMap<Ast.VarDecl, Ir.Var> varDeclToVarMap; // For args and locals
    private IdentityHashMap<Ast.VarDecl, String> varDeclToField; // For fields

    // Filed with ALL methods of ALL classes
    private IdentityHashMap<Ast.Meth, Ir.Meth> methMap = new IdentityHashMap<>();

    // The var used for "this"
    private Ir.Var thisVar;

    private ArrayList<Ir.Var> irLocals; // Locals and temporaries
    private int tmpCounter;
    private int labelCounter;

    public static void main(String[] args) throws Exception {
        Ast.Prog prog = Parser.parse(new BufferedReader(new FileReader(args[0])));
        try {
            StaticCheck.run(prog);
        } catch (StaticCheck.SemErrors e) {
            for (StaticCheck.SemError err : e.getErrors()) {
                System.err.println("error:" + err.location + ": " + err.getMessage());
            }
            System.exit(1);
        }
        Ir.Prog irProg = run(prog);
        System.out.print(irProg.render(0));
    }

    public static Ir.Prog run(Ast.Prog prog) {
        IrGen pass = new IrGen();

        // Create data
        for (Ast.Clazz clazz : prog.clazzes) {
            ArrayList<Ir.DataField> fields = new ArrayList<>();
            for (Ast.VarDecl varDecl : clazz.varDecls) {
                fields.add(new Ir.DataField(varDecl.typ, varDecl.name));
            }
            Ir.Data irData = new Ir.Data(clazz.cname, fields);
            pass.datas.add(irData);
            pass.dataMap.put(clazz.cname, irData);
        }

        // Fill in stub methods, while generating unique names for them
        for (Ast.Clazz clazz : prog.clazzes) {
            HashMap<String, Integer> seenNames = new HashMap<>();
            for (Ast.Meth meth : clazz.meths) {
                String name;
                if (meth.name == "main") {
                    name = "main";
                } else if (seenNames.containsKey(meth.name)) {
                    int ctr = seenNames.get(meth.name);
                    while (seenNames.containsKey(meth.name + "_" + ctr))
                        ctr++;
                    String newName = meth.name + "_" + ctr;
                    seenNames.put(newName, 0);
                    seenNames.put(meth.name, ctr + 1);
                    name = "%" + clazz.cname + "_" + newName;
                } else {
                    seenNames.put(meth.name, 0);
                    name = "%" + clazz.cname + "_" + meth.name;
                }

                pass.methMap.put(meth, new Ir.Meth(meth.retTyp, name));
            }
        }

        for (Ast.Clazz clazz : prog.clazzes) {
            pass.doClazz(clazz);
        }

        return new Ir.Prog(pass.datas, pass.meths);
    }

    private void doClazz(Ast.Clazz clazz) {
        // Fill in fields for ident translation
        varDeclToField = new IdentityHashMap<>();
        for (Ast.VarDecl varDecl : clazz.varDecls) {
            varDeclToField.put(varDecl, varDecl.name);
        }

        // Process methods
        for (Ast.Meth meth : clazz.meths) {
            Ir.Meth irMeth = methMap.get(meth);
            meths.add(irMeth);

            tmpCounter = 0;
            labelCounter = 0;
            varDeclToVarMap = new IdentityHashMap<>();
            ArrayList<Ir.Var> irArgs = new ArrayList<>();
            irLocals = new ArrayList<>();
            HashMap<String, Integer> seenNames = new HashMap<>();

            // This
            thisVar = new Ir.Var(new Ast.ClazzTyp(clazz.cname), "this");
            irArgs.add(thisVar);

            // Generate args
            for (Ast.VarDecl arg : meth.args) {
                Ir.Var irVar = new Ir.Var(arg.typ, arg.name);
                irArgs.add(irVar);
                varDeclToVarMap.put(arg, irVar);
                seenNames.put(arg.name, 0);
            }

            // Generate local vars, while renaming them if they clash
            for (Ast.VarDecl v : meth.vars) {
                String name = v.name;
                if (seenNames.containsKey(name)) {
                    int ctr = seenNames.get(name);
                    while (seenNames.containsKey(name + "__" + ctr))
                        ctr++;
                    String newName = name + "__" + ctr;
                    seenNames.put(newName, 0);
                    seenNames.put(name, ctr + 1);
                    name = newName;
                } else {
                    seenNames.put(name, 0);
                }

                Ir.Var irVar = new Ir.Var(v.typ, name);
                irLocals.add(irVar);
                varDeclToVarMap.put(v, irVar);
            }

            // Now that everything is filled up, we can now generate stmts

            ArrayList<Ir.Stmt> irStmts = new ArrayList<>();
            StmtChunk stmtChunk = doStmtBlock(meth.stmts);
            irStmts.addAll(stmtChunk.stmts);
            // If we have jumps, add a label at the end of the method
            if (!stmtChunk.nextjumps.isEmpty()) {
                Ir.LabelStmt label = genLabel();
                irStmts.add(label);
                backpatch(stmtChunk.nextjumps, label);
            }

            // Ensure that the method ends with a return
            if (!irStmts.isEmpty() && !(irStmts.get(irStmts.size() - 1) instanceof Ir.ReturnStmt)) {
                // Add a return
                if (!(meth.retTyp instanceof Ast.VoidTyp))
                    throw new AssertionError("BUG: control reaches end of method with non-void return type -- unsound type checking?");
                irStmts.add(new Ir.ReturnStmt(null));
            }

            // Complete!
            irMeth.args = irArgs;
            irMeth.locals = irLocals;
            irMeth.blocks = new ArrayList<>();
            irMeth.blocks.add(new Ir.Block(irStmts));
        }
    }

    private StmtChunk doStmtBlock(List<Ast.Stmt> stmts) {
        ArrayList<Ir.Stmt> irStmts = new ArrayList<>();

        StmtChunk lastChunk = null;
        for (Ast.Stmt stmt : stmts) {
            StmtChunk stmtChunk = doStmt(stmt);
            if (lastChunk != null && !lastChunk.nextjumps.isEmpty()) {
                Ir.LabelStmt label = genLabel();
                irStmts.add(label);
                backpatch(lastChunk.nextjumps, label);
            }
            irStmts.addAll(stmtChunk.stmts);
            lastChunk = stmtChunk;
        }

        ArrayList<Ir.JumpStmt> nextjumps = lastChunk != null ? lastChunk.nextjumps : new ArrayList<>();
        return new StmtChunk(irStmts, nextjumps);
    }

    private StmtChunk doStmt(Ast.Stmt stmt) {
        ArrayList<Ir.Stmt> irStmts = new ArrayList<>();
        ArrayList<Ir.JumpStmt> nextjumps = new ArrayList<>();

        if (stmt instanceof Ast.IfStmt) {
            Ast.Expr cond = ((Ast.IfStmt) stmt).cond;
            List<Ast.Stmt> thenStmts = ((Ast.IfStmt) stmt).thenStmts;
            List<Ast.Stmt> elseStmts = ((Ast.IfStmt) stmt).elseStmts;

            CondChunk condChunk = doCond(cond, false);
            StmtChunk thenChunk = doStmtBlock(thenStmts);
            StmtChunk elseChunk = doStmtBlock(elseStmts);

            // Perform conditional check
            irStmts.addAll(condChunk.stmts);

            // If the conditional check has a true jump, add a label for the then chunk
            if (!condChunk.truejumps.isEmpty()) {
                Ir.LabelStmt trueLabel = genLabel();
                irStmts.add(trueLabel);
                backpatch(condChunk.truejumps, trueLabel);
            }

            // Then chunk
            irStmts.addAll(thenChunk.stmts);
            nextjumps.addAll(thenChunk.nextjumps);

            // If the then chunk falls through, add a goto at end of chunk
            if (stmtsFallThrough(thenChunk.stmts)) {
                Ir.GotoStmt thenGoto = new Ir.GotoStmt(null);
                irStmts.add(thenGoto);
                nextjumps.add(thenGoto);
            }

            // If the conditional check has a false jump, add a label for the else chunk
            if (!condChunk.falsejumps.isEmpty()) {
                Ir.LabelStmt falseLabel = genLabel();
                irStmts.add(falseLabel);
                backpatch(condChunk.falsejumps, falseLabel);
            }

            // Else chunk
            irStmts.addAll(elseChunk.stmts);
            nextjumps.addAll(elseChunk.nextjumps);

            return new StmtChunk(irStmts, nextjumps);
        } else if (stmt instanceof Ast.WhileStmt) {
            Ast.Expr cond = ((Ast.WhileStmt) stmt).cond;
            List<Ast.Stmt> stmts = ((Ast.WhileStmt) stmt).stmts;

            CondChunk condChunk = doCond(cond, false);
            StmtChunk stmtChunk = doStmtBlock(stmts);

            // First add a label for the top
            Ir.LabelStmt topLabel = genLabel();
            irStmts.add(topLabel);

            // Then perform conditional check
            irStmts.addAll(condChunk.stmts);

            // If the conditional check has a true jump, add a label
            if (!condChunk.truejumps.isEmpty()) {
                Ir.LabelStmt trueLabel = genLabel();
                irStmts.add(trueLabel);
                backpatch(condChunk.truejumps, trueLabel);
            }

            // Loop body
            irStmts.addAll(stmtChunk.stmts);

            // If loop body falls through, goto back to top
            if (stmtsFallThrough(stmtChunk.stmts))
                irStmts.add(new Ir.GotoStmt(topLabel)); // Go back to top

            nextjumps.addAll(condChunk.falsejumps);
            nextjumps.addAll(stmtChunk.nextjumps);

            return new StmtChunk(irStmts, nextjumps);
        } else if (stmt instanceof Ast.ReadlnStmt) {
            Ast.VarDecl resolvedV = ((Ast.ReadlnStmt) stmt).v;

            if (varDeclToVarMap.containsKey(resolvedV)) {
                // Is a local/arg. Can modify directly
                Ir.Var v = varDeclToVarMap.get(resolvedV);
                irStmts.add(new Ir.ReadlnStmt(v));
                return new StmtChunk(irStmts, nextjumps);
            } else if (varDeclToField.containsKey(resolvedV)) {
                // Is a field. Will need to readln to a temp, then assign it.
                Ir.Var dst = genTemp(resolvedV.typ);
                irStmts.add(new Ir.ReadlnStmt(dst));
                String fieldName = varDeclToField.get(resolvedV);
                irStmts.add(new Ir.FieldAssignStmt(thisVar, fieldName, new Ir.VarRval(dst)));
                return new StmtChunk(irStmts, nextjumps);
            } else {
                throw new AssertionError("BUG: no resolved VarDecl with ReadlnStmt?");
            }
        } else if (stmt instanceof Ast.PrintlnStmt) {
            Ast.Expr expr = ((Ast.PrintlnStmt) stmt).expr;

            RvalChunk rvalChunk = doRval(expr);
            irStmts.addAll(rvalChunk.stmts);
            irStmts.add(new Ir.PrintlnStmt(rvalChunk.rv));
            return new StmtChunk(irStmts, nextjumps);
        } else if (stmt instanceof Ast.VarAssignStmt) {
            Ast.VarDecl lhsVarDecl = ((Ast.VarAssignStmt) stmt).lhsVar;
            Ast.Expr rhs = ((Ast.VarAssignStmt) stmt).rhs;

            RvalChunk rhsChunk = doRval(rhs);
            irStmts.addAll(rhsChunk.stmts);

            if (varDeclToVarMap.containsKey(lhsVarDecl)) {
                // Is a local/arg. Can assign directly
                Ir.Var v = varDeclToVarMap.get(lhsVarDecl);
                irStmts.add(new Ir.AssignStmt(v, rhsChunk.rv));
                return new StmtChunk(irStmts, nextjumps);
            } else if (varDeclToField.containsKey(lhsVarDecl)) {
                // Is a field. Must do field assignment
                String fieldName = varDeclToField.get(lhsVarDecl);
                irStmts.add(new Ir.FieldAssignStmt(thisVar, fieldName, rhsChunk.rv));
                return new StmtChunk(irStmts, nextjumps);
            } else {
                throw new AssertionError("BUG: no resolved VarDecl with VarAssignStmt?");
            }
        } else if (stmt instanceof Ast.FieldAssignStmt) {
            Ast.Expr lhsExpr = ((Ast.FieldAssignStmt) stmt).lhsExpr;
            String lhsField = ((Ast.FieldAssignStmt) stmt).lhsField;
            Ast.Expr rhs = ((Ast.FieldAssignStmt) stmt).rhs;

            RvalChunk rhsChunk = doRval(rhs);
            irStmts.addAll(rhsChunk.stmts);

            RvalChunk lhsChunk = doRval(lhsExpr);
            irStmts.addAll(lhsChunk.stmts);

            Ir.Var lhsVar = ((Ir.VarRval) lhsChunk.rv).v;
            irStmts.add(new Ir.FieldAssignStmt(lhsVar, lhsField, rhsChunk.rv));
            return new StmtChunk(irStmts, nextjumps);
        } else if (stmt instanceof Ast.ReturnStmt) {
            Ast.Expr expr = ((Ast.ReturnStmt) stmt).expr;

            Ir.Rval rv = null;
            if (expr != null) {
                RvalChunk exprChunk = doRval(expr);
                irStmts.addAll(exprChunk.stmts);
                rv = exprChunk.rv;
            }

            irStmts.add(new Ir.ReturnStmt(rv));
            return new StmtChunk(irStmts, nextjumps);
        } else if (stmt instanceof Ast.CallStmt) {
            Ast.Expr target = ((Ast.CallStmt) stmt).target;
            List<Ast.Expr> args = ((Ast.CallStmt) stmt).args;
            Ast.Meth resolvedMeth = ((Ast.CallStmt) stmt).targetMeth;
            assert resolvedMeth != null;

            Ir.Meth irMeth = methMap.get(resolvedMeth);
            assert irMeth != null;
            ArrayList<Ir.Rval> argRvals = new ArrayList<>();

            if (target instanceof Ast.IdentExpr) {
                // Use our current "this"
                argRvals.add(new Ir.VarRval(thisVar));
            } else if (target instanceof Ast.DotExpr) {
                // Use the lhs of the dotexpr as the "this"
                Ast.Expr targetTarget = ((Ast.DotExpr) target).target;

                RvalChunk targetTargetChunk = doRval(targetTarget);
                irStmts.addAll(targetTargetChunk.stmts);
                argRvals.add(targetTargetChunk.rv);
            } else {
                throw new AssertionError("BUG");
            }

            // The rest of the args
            for (Ast.Expr argExpr : args) {
                RvalChunk argChunk = doRval(argExpr);
                irStmts.addAll(argChunk.stmts);
                argRvals.add(argChunk.rv);
            }

            irStmts.add(new Ir.MethCallStmt(null, irMeth, argRvals));
            return new StmtChunk(irStmts, nextjumps);
        } else {
            throw new AssertionError("BUG");
        }
    }

    private RvalChunk doRval(Ast.Expr expr) {
        if (expr instanceof Ast.StringLitExpr) {
            String str = ((Ast.StringLitExpr) expr).str;
            return new RvalChunk(new Ir.StringLitRval(str), new ArrayList<>());
        } else if (expr instanceof Ast.IntLitExpr) {
            int i = ((Ast.IntLitExpr) expr).i;
            return new RvalChunk(new Ir.IntLitRval(i), new ArrayList<>());
        } else if (expr instanceof Ast.BoolLitExpr) {
            boolean b = ((Ast.BoolLitExpr) expr).b;
            return new RvalChunk(new Ir.BoolLitRval(b), new ArrayList<>());
        } else if (expr instanceof Ast.NullLitExpr) {
            return new RvalChunk(new Ir.NullLitRval(), new ArrayList<>());
        } else if (expr instanceof Ast.IdentExpr) {
            Ast.VarDecl varDecl = ((Ast.IdentExpr) expr).v;
            if (varDeclToVarMap.containsKey(varDecl)) {
                // Local arg/var. Can just reference it directly
                Ir.Var v = varDeclToVarMap.get(varDecl);
                return new RvalChunk(new Ir.VarRval(v), new ArrayList<>());
            } else if (varDeclToField.containsKey(varDecl)) {
                // Need to do an additional field access
                ArrayList<Ir.Stmt> stmts = new ArrayList<>();
                Ir.Var dst = genTemp(expr.typ);
                stmts.add(new Ir.FieldAccessStmt(dst, new Ir.VarRval(thisVar), varDeclToField.get(varDecl)));
                return new RvalChunk(new Ir.VarRval(dst), stmts);
            } else {
                throw new AssertionError("BUG: no VarDecl tied to ident?");
            }
        } else if (expr instanceof Ast.ThisExpr) {
            return new RvalChunk(new Ir.VarRval(thisVar), new ArrayList<>());
        } else if (expr instanceof Ast.UnaryExpr) {
            Ast.UnaryOp op = ((Ast.UnaryExpr) expr).op;
            Ast.Expr exprexpr = ((Ast.UnaryExpr) expr).expr;

            switch (op) {
            case LNOT: {
                // Logical operator
                CondChunk condChunk = doCond(exprexpr, true);
                ArrayList<Ir.Stmt> stmts = new ArrayList<>();
                Ir.Var dst = genTemp(expr.typ);
                Ir.LabelStmt trueLabel = null, falseLabel = null;
                if (!condChunk.truejumps.isEmpty()) {
                    trueLabel = genLabel();
                    backpatch(condChunk.truejumps, trueLabel);
                }
                if (!condChunk.falsejumps.isEmpty()) {
                    falseLabel = genLabel();
                    backpatch(condChunk.falsejumps, falseLabel);
                }

                stmts.addAll(condChunk.stmts);

                if (trueLabel != null && falseLabel != null) {
                    Ir.LabelStmt restLabel = genLabel();
                    stmts.add(trueLabel);
                    stmts.add(new Ir.AssignStmt(dst, new Ir.BoolLitRval(true)));
                    stmts.add(new Ir.GotoStmt(restLabel));
                    stmts.add(falseLabel);
                    stmts.add(new Ir.AssignStmt(dst, new Ir.BoolLitRval(false)));
                    stmts.add(restLabel);
                } else if (trueLabel != null) {
                    stmts.add(trueLabel);
                    stmts.add(new Ir.AssignStmt(dst, new Ir.BoolLitRval(true)));
                } else if (falseLabel != null) {
                    stmts.add(falseLabel);
                    stmts.add(new Ir.AssignStmt(dst, new Ir.BoolLitRval(false)));
                } else {
                    throw new AssertionError("BUG");
                }

                return new RvalChunk(new Ir.VarRval(dst), stmts);
            }
            case NEG: {
                RvalChunk aChunk = doRval(exprexpr);
                Ir.Var dst = genTemp(expr.typ);
                ArrayList<Ir.Stmt> stmts = new ArrayList<>();
                stmts.addAll(aChunk.stmts);
                stmts.add(new Ir.UnaryStmt(dst, Ir.UnaryOp.NEG, aChunk.rv));
                return new RvalChunk(new Ir.VarRval(dst), stmts);
            }
            default:
                throw new AssertionError("BUG");
            }
        } else if (expr instanceof Ast.BinaryExpr) {
            Ast.BinaryOp op = ((Ast.BinaryExpr) expr).op;
            Ast.Expr lhs = ((Ast.BinaryExpr) expr).lhs;
            Ast.Expr rhs = ((Ast.BinaryExpr) expr).rhs;

            switch (op) {
            case LT:
            case GT:
            case LE:
            case GE:
            case EQ:
            case NE:
            case LAND:
            case LOR: {
                // Logical operators
                CondChunk condChunk = doCond(expr, false);
                ArrayList<Ir.Stmt> stmts = new ArrayList<>();
                Ir.Var dst = genTemp(new Ast.BoolTyp());
                Ir.LabelStmt trueLabel = null, falseLabel = null;
                if (!condChunk.truejumps.isEmpty()) {
                    trueLabel = genLabel();
                    backpatch(condChunk.truejumps, trueLabel);
                }
                if (!condChunk.falsejumps.isEmpty()) {
                    falseLabel = genLabel();
                    backpatch(condChunk.falsejumps, falseLabel);
                }

                stmts.addAll(condChunk.stmts);

                if (trueLabel != null && falseLabel != null) {
                    Ir.LabelStmt restLabel = genLabel();
                    stmts.add(trueLabel);
                    stmts.add(new Ir.AssignStmt(dst, new Ir.BoolLitRval(true)));
                    stmts.add(new Ir.GotoStmt(restLabel));
                    stmts.add(falseLabel);
                    stmts.add(new Ir.AssignStmt(dst, new Ir.BoolLitRval(false)));
                    stmts.add(restLabel);
                } else if (trueLabel != null) {
                    stmts.add(trueLabel);
                    stmts.add(new Ir.AssignStmt(dst, new Ir.BoolLitRval(true)));
                } else if (falseLabel != null) {
                    stmts.add(falseLabel);
                    stmts.add(new Ir.AssignStmt(dst, new Ir.BoolLitRval(false)));
                } else {
                    throw new AssertionError("BUG");
                }

                return new RvalChunk(new Ir.VarRval(dst), stmts);
            }
            default:
                Ir.BinaryOp irOp;

                switch (op) {
                case PLUS:
                    irOp = Ir.BinaryOp.PLUS;
                    break;
                case MINUS:
                    irOp = Ir.BinaryOp.MINUS;
                    break;
                case MUL:
                    irOp = Ir.BinaryOp.MUL;
                    break;
                case DIV:
                    irOp = Ir.BinaryOp.DIV;
                    break;
                default:
                    throw new AssertionError("BUG");
                }

                RvalChunk lhsChunk = doRval(lhs);
                RvalChunk rhsChunk = doRval(rhs);
                Ir.Var dst = genTemp(expr.typ);
                ArrayList<Ir.Stmt> stmts = new ArrayList<>();
                stmts.addAll(lhsChunk.stmts);
                stmts.addAll(rhsChunk.stmts);
                stmts.add(new Ir.BinaryStmt(dst, irOp, lhsChunk.rv, rhsChunk.rv));
                return new RvalChunk(new Ir.VarRval(dst), stmts);
            }
        } else if (expr instanceof Ast.DotExpr) {
            Ast.Expr target = ((Ast.DotExpr) expr).target;
            String ident = ((Ast.DotExpr) expr).ident;

            RvalChunk targetChunk = doRval(target);
            Ir.Var dst = genTemp(expr.typ);
            ArrayList<Ir.Stmt> stmts = new ArrayList<>();
            stmts.addAll(targetChunk.stmts);
            stmts.add(new Ir.FieldAccessStmt(dst, targetChunk.rv, ident));
            return new RvalChunk(new Ir.VarRval(dst), stmts);
        } else if (expr instanceof Ast.CallExpr) {
            Ast.Expr target = ((Ast.CallExpr) expr).target;
            List<Ast.Expr> args = ((Ast.CallExpr) expr).args;
            Ast.Meth resolvedMeth = ((Ast.CallExpr) expr).meth;
            assert resolvedMeth != null;

            Ir.Meth irMeth = methMap.get(resolvedMeth);
            assert irMeth != null;
            ArrayList<Ir.Stmt> stmts = new ArrayList<>();
            ArrayList<Ir.Rval> argRvals = new ArrayList<>();
            // Add "this" to argVars
            if (target instanceof Ast.IdentExpr) {
                // Use our current "this"
                argRvals.add(new Ir.VarRval(thisVar));
            } else if (target instanceof Ast.DotExpr) {
                // Use the lhs of the dotexpr as the "this"
                Ast.Expr targetTarget = ((Ast.DotExpr) target).target;

                RvalChunk targetTargetChunk = doRval(targetTarget);
                stmts.addAll(targetTargetChunk.stmts);
                argRvals.add(targetTargetChunk.rv);
            } else {
                throw new AssertionError("BUG");
            }
            // The rest of the args
            for (Ast.Expr argExpr : args) {
                RvalChunk argChunk = doRval(argExpr);
                stmts.addAll(argChunk.stmts);
                argRvals.add(argChunk.rv);
            }
            Ir.Var dst = genTemp(expr.typ);
            stmts.add(new Ir.MethCallStmt(dst, irMeth, argRvals));
            return new RvalChunk(new Ir.VarRval(dst), stmts);
        } else if (expr instanceof Ast.NewExpr) {
            String cname = ((Ast.NewExpr) expr).cname;
            Ir.Var dst = genTemp(expr.typ);
            ArrayList<Ir.Stmt> stmts = new ArrayList<>();
            stmts.add(new Ir.NewStmt(dst, dataMap.get(cname)));
            return new RvalChunk(new Ir.VarRval(dst), stmts);
        } else {
            throw new AssertionError("BUG");
        }
    }

    private CondChunk doCond(Ast.Expr expr, boolean negate) {
        ArrayList<Ir.Stmt> stmts = new ArrayList<>();
        ArrayList<Ir.JumpStmt> truejumps = new ArrayList<>();
        ArrayList<Ir.JumpStmt> falsejumps = new ArrayList<>();

        if (expr instanceof Ast.StringLitExpr || expr instanceof Ast.ThisExpr) {
            // Always true (or false)
            Ir.GotoStmt gotoStmt = new Ir.GotoStmt(null);
            stmts.add(gotoStmt);
            if (negate)
                falsejumps.add(gotoStmt);
            else
                truejumps.add(gotoStmt);
            return new CondChunk(stmts, truejumps, falsejumps);
        } else if (expr instanceof Ast.IntLitExpr) {
            int i = ((Ast.IntLitExpr) expr).i;
            Ir.GotoStmt gotoStmt = new Ir.GotoStmt(null);
            stmts.add(gotoStmt);
            // Always true/false dependending on value
            if (i == 0) {
                if (negate)
                    truejumps.add(gotoStmt);
                else
                    falsejumps.add(gotoStmt);
            } else {
                if (negate)
                    falsejumps.add(gotoStmt);
                else
                    truejumps.add(gotoStmt);
            }
            return new CondChunk(stmts, truejumps, falsejumps);
        } else if (expr instanceof Ast.BoolLitExpr) {
            boolean b = ((Ast.BoolLitExpr) expr).b;
            Ir.GotoStmt gotoStmt = new Ir.GotoStmt(null);
            stmts.add(gotoStmt);
            // Always true/false dependending on value
            if (!b) {
                if (negate)
                    truejumps.add(gotoStmt);
                else
                    falsejumps.add(gotoStmt);
            } else {
                if (negate)
                    falsejumps.add(gotoStmt);
                else
                    truejumps.add(gotoStmt);
            }
            return new CondChunk(stmts, truejumps, falsejumps);
        } else if (expr instanceof Ast.NullLitExpr) {
            // Always false
            Ir.GotoStmt gotoStmt = new Ir.GotoStmt(null);
            stmts.add(gotoStmt);
            if (negate)
                truejumps.add(gotoStmt);
            else
                falsejumps.add(gotoStmt);
            return new CondChunk(stmts, truejumps, falsejumps);
        } else if (expr instanceof Ast.IdentExpr || expr instanceof Ast.DotExpr || expr instanceof Ast.CallExpr || expr instanceof Ast.NewExpr) {
            // Depends on the value
            RvalChunk rvalChunk = doRval(expr);
            stmts.addAll(rvalChunk.stmts);
            Ir.CmpStmt tstStmt = new Ir.CmpStmt(Ir.CondOp.NE, rvalChunk.rv, new Ir.IntLitRval(0), null);
            stmts.add(tstStmt);
            if (negate)
                falsejumps.add(tstStmt);
            else
                truejumps.add(tstStmt);
            Ir.GotoStmt gotoStmt = new Ir.GotoStmt(null);
            stmts.add(gotoStmt);
            if (negate)
                truejumps.add(gotoStmt);
            else
                falsejumps.add(gotoStmt);
            return new CondChunk(stmts, truejumps, falsejumps);
        } else if (expr instanceof Ast.UnaryExpr) {
            Ast.UnaryOp op = ((Ast.UnaryExpr) expr).op;
            Ast.Expr exprExpr = ((Ast.UnaryExpr) expr).expr;

            switch (op) {
            case LNOT:
                return doCond(exprExpr, !negate);
            default:
                // Non-logical operator: Depends on the value
                RvalChunk rvalChunk = doRval(expr);
                stmts.addAll(rvalChunk.stmts);
                Ir.CmpStmt tstStmt = new Ir.CmpStmt(Ir.CondOp.NE, rvalChunk.rv, new Ir.IntLitRval(0), null);
                stmts.add(tstStmt);
                if (negate)
                    falsejumps.add(tstStmt);
                else
                    truejumps.add(tstStmt);
                Ir.GotoStmt gotoStmt = new Ir.GotoStmt(null);
                stmts.add(gotoStmt);
                if (negate)
                    truejumps.add(gotoStmt);
                else
                    falsejumps.add(gotoStmt);
                return new CondChunk(stmts, truejumps, falsejumps);
            }
        } else if (expr instanceof Ast.BinaryExpr) {
            Ast.BinaryOp op = ((Ast.BinaryExpr) expr).op;
            Ast.Expr lhs = ((Ast.BinaryExpr) expr).lhs;
            Ast.Expr rhs = ((Ast.BinaryExpr) expr).rhs;

            // Negate the operator
            if (negate) {
                switch (op) {
                case LAND:
                    op = Ast.BinaryOp.LOR;
                    break;
                case LOR:
                    op = Ast.BinaryOp.LAND;
                    break;
                case LT:
                    op = Ast.BinaryOp.GE;
                    break;
                case GT:
                    op = Ast.BinaryOp.LE;
                    break;
                case LE:
                    op = Ast.BinaryOp.GT;
                    break;
                case GE:
                    op = Ast.BinaryOp.LT;
                    break;
                case EQ:
                    op = Ast.BinaryOp.NE;
                    break;
                case NE:
                    op = Ast.BinaryOp.EQ;
                    break;
                }
            }

            switch (op) {
            case LAND: { // &&
                CondChunk lhsChunk = doCond(lhs, negate);
                CondChunk rhsChunk = doCond(rhs, negate);
                // Create a label for rhs
                Ir.LabelStmt rhsLabel = genLabel();
                backpatch(lhsChunk.truejumps, rhsLabel);
                stmts.addAll(lhsChunk.stmts);
                stmts.add(rhsLabel);
                stmts.addAll(rhsChunk.stmts);

                truejumps.addAll(rhsChunk.truejumps);
                falsejumps.addAll(lhsChunk.falsejumps);
                falsejumps.addAll(rhsChunk.falsejumps);
                return new CondChunk(stmts, truejumps, falsejumps);
            }
            case LOR: { // ||
                CondChunk lhsChunk = doCond(lhs, negate);
                CondChunk rhsChunk = doCond(rhs, negate);
                // Create a label for rhs
                Ir.LabelStmt rhsLabel = genLabel();
                backpatch(lhsChunk.falsejumps, rhsLabel);
                stmts.addAll(lhsChunk.stmts);
                stmts.add(rhsLabel);
                stmts.addAll(rhsChunk.stmts);

                truejumps.addAll(lhsChunk.truejumps);
                truejumps.addAll(rhsChunk.truejumps);
                falsejumps.addAll(rhsChunk.falsejumps);
                return new CondChunk(stmts, truejumps, falsejumps);
            }
            case LT:
            case GT:
            case LE:
            case GE:
            case EQ:
            case NE: {
                Ir.CondOp condOp;

                switch (op) {
                case LT:
                    condOp = Ir.CondOp.LT;
                    break;
                case GT:
                    condOp = Ir.CondOp.GT;
                    break;
                case LE:
                    condOp = Ir.CondOp.LE;
                    break;
                case GE:
                    condOp = Ir.CondOp.GE;
                    break;
                case EQ:
                    condOp = Ir.CondOp.EQ;
                    break;
                case NE:
                    condOp = Ir.CondOp.NE;
                    break;
                default:
                    throw new AssertionError("BUG");
                }

                // NOTE: we don't need to negate because the condOp is already negated
                RvalChunk lhsChunk = doRval(lhs);
                RvalChunk rhsChunk = doRval(rhs);
                stmts.addAll(lhsChunk.stmts);
                stmts.addAll(rhsChunk.stmts);
                Ir.CmpStmt cmpStmt = new Ir.CmpStmt(condOp, lhsChunk.rv, rhsChunk.rv, null);
                stmts.add(cmpStmt);
                truejumps.add(cmpStmt);
                Ir.GotoStmt gotoStmt = new Ir.GotoStmt(null);
                stmts.add(gotoStmt);
                falsejumps.add(gotoStmt);
                return new CondChunk(stmts, truejumps, falsejumps);
            }
            default: {
                // Non-logical operator: Depends on the value
                RvalChunk rvalChunk = doRval(expr);
                stmts.addAll(rvalChunk.stmts);
                Ir.CmpStmt tstStmt = new Ir.CmpStmt(Ir.CondOp.NE, rvalChunk.rv, new Ir.IntLitRval(0), null);
                stmts.add(tstStmt);
                if (negate)
                    falsejumps.add(tstStmt);
                else
                    truejumps.add(tstStmt);
                Ir.GotoStmt gotoStmt = new Ir.GotoStmt(null);
                stmts.add(gotoStmt);
                if (negate)
                    truejumps.add(gotoStmt);
                else
                    falsejumps.add(gotoStmt);
                return new CondChunk(stmts, truejumps, falsejumps);
            }
            }
        } else {
            throw new AssertionError("BUG");
        }
    }

    private Ir.Var genTemp(Ast.Typ typ) {
        String name = "%t" + tmpCounter;
        tmpCounter++;
        Ir.Var v = new Ir.Var(typ, name);
        irLocals.add(v);
        return v;
    }

    private Ir.LabelStmt genLabel() {
        String name = "L" + labelCounter++;
        Ir.LabelStmt l = new Ir.LabelStmt(name);
        return l;
    }

    private static void backpatch(List<Ir.JumpStmt> jumps, Ir.LabelStmt label) {
        for (Ir.JumpStmt jump : jumps)
            jump.label = label;
    }

    private static boolean stmtsFallThrough(List<? extends Ir.Stmt> stmts) {
        if (stmts.isEmpty())
            return true;

        Ir.Stmt lastStmt = stmts.get(stmts.size() - 1);
        if (lastStmt instanceof Ir.ReturnStmt || lastStmt instanceof Ir.GotoStmt)
            return false;

        return true;
    }

    private static class RvalChunk {
        private final Ir.Rval rv;
        private final ArrayList<Ir.Stmt> stmts;

        private RvalChunk(Ir.Rval rv, ArrayList<Ir.Stmt> stmts) {
            this.rv = rv;
            this.stmts = stmts;
        }
    }

    private static class CondChunk {
        private final ArrayList<Ir.Stmt> stmts;
        private final ArrayList<Ir.JumpStmt> truejumps;
        private final ArrayList<Ir.JumpStmt> falsejumps;

        private CondChunk(ArrayList<Ir.Stmt> stmts, ArrayList<Ir.JumpStmt> truejumps, ArrayList<Ir.JumpStmt> falsejumps) {
            this.stmts = stmts;
            this.truejumps = truejumps;
            this.falsejumps = falsejumps;
        }
    }

    private static class StmtChunk {
        private final ArrayList<Ir.Stmt> stmts;
        private final ArrayList<Ir.JumpStmt> nextjumps;

        private StmtChunk(ArrayList<Ir.Stmt> stmts, ArrayList<Ir.JumpStmt> nextjumps) {
            this.stmts = stmts;
            this.nextjumps = nextjumps;
        }
    }
}
