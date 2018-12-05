package pyokagan.cs4212;

import java.util.*;
import java.io.*;

public class StaticCheck {

    private HashMap<String, ClazzDescr> clazzdescrs = new HashMap<>();

    private StaticCheck() {}

    public static void run(Ast.Prog prog) throws SemErrors {
        StaticCheck pass = new StaticCheck();
        pass.initialize(prog);
        ArrayList<SemError> errors = new ArrayList<>();
        for (Ast.Clazz clazz : prog.clazzes) {
            try {
                pass.doClazz(clazz);
            } catch (SemErrors se) {
                errors.addAll(se.getErrors());
            }
        }
        if (!errors.isEmpty())
            throw new SemErrors(errors);
    }

    public static void main(String[] args) throws Exception {
        Ast.Prog prog = Parser.parse(new BufferedReader(new FileReader(args[0])));
        try {
            run(prog);
        } catch (SemErrors e) {
            for (SemError err : e.getErrors()) {
                System.err.println("error:" + err.location + ": " + err.getMessage());
            }
            System.exit(1);
        }
    }

    private void initialize(Ast.Prog prog) throws SemErrors {
        ArrayList<SemError> errors = new ArrayList<>();

        // Fill in class names (check for duplicate class names)
        for (Ast.Clazz clazz : prog.clazzes) {
            // Check for duplicate class name
            if (hasClazz(clazz.cname)) {
                errors.add(new SemError(clazz,
                            "duplicate class name " + clazz.cname
                            + ", originally defined in "
                            + clazzdescrs.get(clazz.cname).clazz.getLocation()));
                continue;
            }

            ClazzDescr descr = new ClazzDescr(clazz);
            clazzdescrs.put(clazz.cname, descr);
        }

        if (!errors.isEmpty())
            throw new SemErrors(errors);

        // Fill in class fields and methods
        // (check for duplicate class field and method names)
        for (Ast.Clazz clazz : prog.clazzes) {
            ClazzDescr descr = clazzdescrs.get(clazz.cname);

            // Process class fields
            for (Ast.VarDecl varDecl : clazz.varDecls) {
                // Validate field type
                if (!isValidType(varDecl.typ)) {
                    errors.add(new SemError(varDecl,
                                "invalid field type " + varDecl.typ.render(0, 0)));
                }

                // Check for duplicate field names
                if (descr.fields.containsKey(varDecl.name)) {
                    errors.add(new SemError(varDecl,
                                "duplicate field name " + varDecl.name
                                + ", originally declared in "
                                + descr.fields.get(varDecl.name).getLocation()));
                } else {
                    descr.fields.put(varDecl.name, varDecl);
                }
            }

            // Process class methods
            HashMap<String, HashMap<ArrayList<Ast.Typ>, Ast.Meth>> seenMeths = new HashMap<>();
            for (Ast.Meth meth : clazz.meths) {
                // Validate argument types and check for duplicate argument names
                HashMap<String, Ast.VarDecl> argNames = new HashMap<>();
                ArrayList<Ast.Typ> argTyps = new ArrayList<>();
                for (Ast.VarDecl varDecl : meth.args) {
                    if (argNames.containsKey(varDecl.name)) {
                        errors.add(new SemError(varDecl,
                                    "duplicate argument name " + varDecl.name
                                    + ", originally declared in "
                                    + argNames.get(varDecl.name).getLocation()));
                    }

                    if (!isValidType(varDecl.typ)) {
                        errors.add(new SemError(varDecl,
                                    "invalid argument type " + varDecl.typ.render(0, 0)));
                    }

                    argNames.put(varDecl.name, varDecl);
                    argTyps.add(varDecl.typ);
                }

                // Check for duplicate (methname, argtypes)
                // NOTE: We can use a HashMap here because Null is not exposed to the user
                if (seenMeths.containsKey(meth.name) && seenMeths.get(meth.name).containsKey(argTyps)) {
                    errors.add(new SemError(meth,
                                "duplicate method " + meth.name
                                + ", originally defined in "
                                + seenMeths.get(meth.name).get(argTyps).getLocation()));
                    continue;
                }

                if (!seenMeths.containsKey(meth.name))
                    seenMeths.put(meth.name, new HashMap<>());
                seenMeths.get(meth.name).put(argTyps, meth);
                descr.addMethod(meth.name, meth);
            }
        }

        if (!errors.isEmpty())
            throw new SemErrors(errors);
    }

    private void doClazz(Ast.Clazz clazz) throws SemErrors {
        // [CDecl]
        ClazzDescr clazzdescr = getClazz(clazz.cname);

        // Set up env
        // Fields and methods are guaranteed to have unique names,
        // although a field can have the same name as a method.
        // In that case, the field overrides the method.
        Env env = new Env();
        // Methods first, so that fields override methods
        for (String methodName : clazzdescr.getMethodNames())
            env.put(methodName, clazzdescr.getMethodTyp(methodName), null);

        // Fields next
        for (String fieldName : clazzdescr.getFieldNames())
            env.put(fieldName, clazzdescr.getFieldTyp(fieldName), clazzdescr.getFieldVarDecl(fieldName));

        // Finally, "this", which overrides everything
        env.put("this", new Ast.ClazzTyp(clazz.cname), null);

        // Now type-check methods
        ArrayList<SemError> errors = new ArrayList<>();
        for (Ast.Meth meth : clazz.meths) {
            try {
                doMeth(meth, env);
            } catch (SemErrors se) {
                errors.addAll(se.getErrors());
            }
        }

        if (!errors.isEmpty())
            throw new SemErrors(errors);
    }

    private void doMeth(Ast.Meth meth, Env env) throws SemErrors {
        // [MDecl]
        ArrayList<SemError> errors = new ArrayList<>();

        // Environment setup
        // We already know argument types are valid and that argument names are unique
        Env methEnv = new Env(env);
        for (Ast.VarDecl varDecl : meth.args) {
            methEnv.put(varDecl.name, varDecl.typ, varDecl);
        }
        // Ret overrides everything
        methEnv.put("Ret", meth.retTyp, null);
        // [Block] environment setup
        for (Ast.VarDecl varDecl : meth.vars) {
            if (!isValidType(varDecl.typ)) {
                errors.add(new SemError(varDecl, "invalid variable type " + varDecl.typ.render(0, 0)));
                continue;
            }
            methEnv.put(varDecl.name, varDecl.typ, varDecl);
        }

        if (!errors.isEmpty())
            throw new SemErrors(errors);

        Ast.Typ bodyTyp = doStmtBlock(meth.stmts, methEnv, meth);
        if (!bodyTyp.isSubtypeOrEquals(meth.retTyp))
            throw new SemErrors(meth, "type of return body is not assignable to " + meth.retTyp.render(0, 0) + ": " + bodyTyp.render(0, 0));
    }

    private Ast.Typ doStmtBlock(List<? extends Ast.Stmt> stmts, Env env, Ast.Locatable loc) throws SemErrors {
        // [Block] without locals
        ArrayList<SemError> errors = new ArrayList<>();
        Ast.Typ lastTyp = null;
        for (Ast.Stmt stmt : stmts) {
            // [Seq]
            try {
                lastTyp = doStmt(stmt, env);
            } catch (SemErrors err) {
                errors.addAll(err.getErrors());
            }
        }

        if (!errors.isEmpty())
            throw new SemErrors(errors);

        if (lastTyp == null)
            throw new SemErrors(loc, "Empty statement blocks are not allowed");

        return lastTyp;
    }

    private Ast.Typ doStmt(Ast.Stmt stmt, Env env) throws SemErrors {
        if (stmt instanceof Ast.IfStmt) {
            // [Cond]
            Ast.Expr cond = ((Ast.IfStmt) stmt).cond;
            List<Ast.Stmt> thenStmts = ((Ast.IfStmt) stmt).thenStmts;
            List<Ast.Stmt> elseStmts = ((Ast.IfStmt) stmt).elseStmts;
            ArrayList<SemError> errors = new ArrayList<>();

            try {
                Ast.Typ condTyp = doExpr(cond, env);
                if (!condTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemError(cond, "IfStmt: not assignable to Bool: " + condTyp.render(0, 0));
            } catch (SemError se) {
                errors.add(se);
            }

            Ast.Typ thenStmtsTyp = null, elseStmtsTyp = null;
            try {
                thenStmtsTyp = doStmtBlock(thenStmts, env, stmt);
            } catch (SemErrors se) {
                errors.addAll(se.getErrors());
            }

            try {
                elseStmtsTyp = doStmtBlock(elseStmts, env, stmt);
            } catch (SemErrors se) {
                errors.addAll(se.getErrors());
            }

            if (!errors.isEmpty())
                throw new SemErrors(errors);

            if (!(thenStmtsTyp.isSubtypeOrEquals(elseStmtsTyp) || elseStmtsTyp.isSubtypeOrEquals(thenStmtsTyp)))
                throw new SemErrors(stmt, "IfStmt: type of thenBlock (" + thenStmtsTyp.render(0, 0) + ") does not match elseBlock (" + elseStmtsTyp.render(0, 0) + ")");

            // Pick the more specific type
            return thenStmtsTyp.isSubtypeOrEquals(elseStmtsTyp) ? elseStmtsTyp : thenStmtsTyp;
        } else if (stmt instanceof Ast.WhileStmt) {
            // [While]
            Ast.Expr cond = ((Ast.WhileStmt) stmt).cond;
            List<Ast.Stmt> thenStmts = ((Ast.WhileStmt) stmt).stmts;

            ArrayList<SemError> errors = new ArrayList<>();
            try {
                Ast.Typ condTyp = doExpr(cond, env);
                if (!condTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemError(cond, "WhileStmt: not assignable to Bool: " + condTyp.render(0, 0));
            } catch (SemError se) {
                errors.add(se);
            }

            Ast.Typ thenStmtsTyp = null;
            try {
                thenStmtsTyp = doStmtBlock(thenStmts, env, stmt);
            } catch (SemErrors se) {
                errors.addAll(se.getErrors());
            }

            if (!errors.isEmpty())
                throw new SemErrors(errors);

            assert thenStmtsTyp != null;
            return thenStmtsTyp;
        } else if (stmt instanceof Ast.ReadlnStmt) {
            // [Read]
            String ident = ((Ast.ReadlnStmt) stmt).ident;

            if (!env.contains(ident))
                throw new SemErrors(stmt, "ReadlnStmt: unknown symbol: " + ident);

            Ast.Typ identTyp = env.get(ident);
            // NOTE: Can't use subtyping here -- we need the exact types
            if (!(identTyp instanceof Ast.IntTyp || identTyp instanceof Ast.BoolTyp || identTyp instanceof Ast.StringTyp))
                throw new SemErrors(stmt, "ReadlnStmt: type of " + ident + " is not Int, Bool or String. Is: " + identTyp.render(0, 0));
            ((Ast.ReadlnStmt) stmt).v = env.getVarDecl(ident);

            return new Ast.VoidTyp();
        } else if (stmt instanceof Ast.PrintlnStmt) {
            // [Print]
            Ast.Expr expr = ((Ast.PrintlnStmt) stmt).expr;
            Ast.Typ exprTyp;
            try {
                exprTyp = doExpr(expr, env);
            } catch (SemError se) {
                throw new SemErrors(Arrays.asList(se));
            }

            if (!(exprTyp.isSubtypeOrEquals(new Ast.IntTyp()) || exprTyp.isSubtypeOrEquals(new Ast.BoolTyp()) || exprTyp.isSubtypeOrEquals(new Ast.StringTyp())))
                throw new SemErrors(stmt, "PrintlnStmt: type of expr is not assignable to Int, Bool or String. Is: " + exprTyp.render(0, 0));

            return new Ast.VoidTyp();
        } else if (stmt instanceof Ast.VarAssignStmt) {
            // [VarAss]
            String lhs = ((Ast.VarAssignStmt) stmt).lhs;
            Ast.Expr rhs = ((Ast.VarAssignStmt) stmt).rhs;

            ArrayList<SemError> errors = new ArrayList<>();
            Ast.Typ lhsTyp = null;
            if (env.contains(lhs))
                lhsTyp = env.get(lhs);
            else
                errors.add(new SemError(stmt, "VarAssignStmt: unknown symbol: " + lhs));

            Ast.Typ rhsTyp = null;
            try {
                rhsTyp = doExpr(rhs, env);
            } catch (SemError se) {
                errors.add(se);
            }

            if (!errors.isEmpty())
                throw new SemErrors(errors);

            assert lhsTyp != null;
            assert rhsTyp != null;
            if (!rhsTyp.isSubtypeOrEquals(lhsTyp))
                throw new SemErrors(stmt, "VarAssignStmt: type of rhs (" + rhsTyp.render(0, 0) + ") is not assignable to lhs (" + lhsTyp.render(0, 0) + ")");

            ((Ast.VarAssignStmt) stmt).lhsVar = env.getVarDecl(lhs);
            return new Ast.VoidTyp();
        } else if (stmt instanceof Ast.FieldAssignStmt) {
            // [FdAss]
            Ast.Expr lhsExpr = ((Ast.FieldAssignStmt) stmt).lhsExpr;
            String lhsField = ((Ast.FieldAssignStmt) stmt).lhsField;
            Ast.Expr rhsExpr = ((Ast.FieldAssignStmt) stmt).rhs;

            ArrayList<SemError> errors = new ArrayList<>();
            Ast.Typ lhsExprTyp, lhsFieldTyp = null;
            try {
                lhsExprTyp = doExpr(lhsExpr, env);
                if (!(lhsExprTyp instanceof Ast.ClazzTyp))
                    throw new SemError(lhsExpr, "field access in FieldAssignStmt: expected class type, got: " + lhsExprTyp.render(0, 0));

                String cname = ((Ast.ClazzTyp) lhsExprTyp).cname;
                ClazzDescr clazz = getClazz(cname);

                // Lookup field based on name
                if (!clazz.hasField(lhsField))
                    throw new SemError(lhsExpr, "field access in FieldAssignStmt: no such field in " + cname + ": " + lhsField);

                lhsFieldTyp = clazz.getFieldTyp(lhsField);
            } catch (SemError se) {
                errors.add(se);
            }

            Ast.Typ rhsTyp = null;
            try {
                rhsTyp = doExpr(rhsExpr, env);
            } catch (SemError se) {
                errors.add(se);
            }

            if (!errors.isEmpty())
                throw new SemErrors(errors);

            assert lhsFieldTyp != null;
            assert rhsTyp != null;
            if (!rhsTyp.isSubtypeOrEquals(lhsFieldTyp))
                throw new SemErrors(stmt, "FieldAssignStmt: type of rhs (" + rhsTyp.render(0, 0) + ") is not assignable to lhs (" + lhsFieldTyp.render(0, 0) + ")");

            return new Ast.VoidTyp();
        } else if (stmt instanceof Ast.ReturnStmt) {
            Ast.Expr expr = ((Ast.ReturnStmt) stmt).expr; // Nullable
            Ast.Typ retTyp = env.get("Ret");
            assert retTyp != null;
            if (retTyp instanceof Ast.VoidTyp) {
                // [Ret-Void]
                if (expr != null)
                    throw new SemErrors(stmt, "cannot return a value in a method returning Void");

                return new Ast.VoidTyp();
            } else {
                // [Ret-T]
                if (expr == null)
                    throw new SemErrors(stmt, "must return a value in a method returning non-Void");

                Ast.Typ exprTyp;
                try {
                    exprTyp = doExpr(expr, env);
                } catch (SemError se) {
                    throw new SemErrors(Arrays.asList(se));
                }

                if (!exprTyp.isSubtypeOrEquals(retTyp))
                    throw new SemErrors(expr, "return: " + exprTyp.render(0, 0) + " is not assignable to " + retTyp.render(0, 0));

                return retTyp;
            }
        } else if (stmt instanceof Ast.CallStmt) {
            Ast.Expr target = ((Ast.CallStmt) stmt).target;
            List<Ast.Expr> args = ((Ast.CallStmt) stmt).args;

            // We cheat by converting it into an expression, since they have identical semantics
            Ast.CallExpr callExpr = new Ast.CallExpr(target, args);
            callExpr.setLocation(stmt.getLocation());
            Ast.Typ callTyp;
            try {
                callTyp = doExpr(callExpr, env);
            } catch (SemError se) {
                throw new SemErrors(Arrays.asList(se));
            }

            ((Ast.CallStmt) stmt).targetMeth = callExpr.meth;
            return callTyp;
        } else {
            throw new AssertionError("BUG: This is what happens when Java doesn't support ADTs :-(");
        }
    }

    private Ast.Typ doExpr(Ast.Expr expr, Env env) throws SemError {
        if (expr instanceof Ast.StringLitExpr) {
            // [Strings]
            expr.typ = new Ast.StringTyp();
            return expr.typ;
        } else if (expr instanceof Ast.IntLitExpr) {
            // [Integers]
            expr.typ = new Ast.IntTyp();
            return expr.typ;
        } else if (expr instanceof Ast.BoolLitExpr) {
            // [Booleans]
            expr.typ = new Ast.BoolTyp();
            return expr.typ;
        } else if (expr instanceof Ast.NullLitExpr) {
            expr.typ = new Ast.NullTyp();
            return expr.typ;
        } else if (expr instanceof Ast.IdentExpr) {
            // [Id]
            String name = ((Ast.IdentExpr) expr).name;
            if (env.contains(name)) {
                expr.typ = env.get(name);
                ((Ast.IdentExpr) expr).v = env.getVarDecl(name);
                return expr.typ;
            } else
                throw new SemError(expr, "unknown symbol: " + name);
        } else if (expr instanceof Ast.ThisExpr) {
            // [Id]
            if (env.contains("this")) {
                expr.typ = env.get("this");
                return expr.typ;
            } else
                throw new SemError(expr, "unknown symbol: this");
        } else if (expr instanceof Ast.UnaryExpr) {
            Ast.UnaryOp op = ((Ast.UnaryExpr) expr).op;
            Ast.Expr a = ((Ast.UnaryExpr) expr).expr;
            Ast.Typ aTyp = doExpr(a, env);

            switch (op) {
            case NEG:
                if (!aTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemError(expr, "NEG: expected assignable to Int, got: " + aTyp.render(0, 0));
                expr.typ = new Ast.IntTyp();
                return expr.typ;
            case LNOT:
                if (!aTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemError(expr, "LNOT: expected assignable to Bool, got: " + aTyp.render(0, 0));
                expr.typ = new Ast.BoolTyp();
                return expr.typ;
            default:
                throw new AssertionError("BUG");
            }
        } else if (expr instanceof Ast.BinaryExpr) {
            Ast.BinaryOp op = ((Ast.BinaryExpr) expr).op;
            Ast.Expr lhs = ((Ast.BinaryExpr) expr).lhs;
            Ast.Expr rhs = ((Ast.BinaryExpr) expr).rhs;
            Ast.Typ lhsTyp = doExpr(lhs, env);
            Ast.Typ rhsTyp = doExpr(rhs, env);

            switch (op) {
            case PLUS:
            case MINUS:
            case MUL:
            case DIV:
                // [Arith]
                if (!lhsTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemError(expr, "" + op + ": expected assignable to Int on lhs, got: " + lhsTyp.render(0, 0));
                if (!rhsTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemError(expr, "" + op + ": expected assignable to Int on rhs, got: " + rhsTyp.render(0, 0));
                expr.typ = new Ast.IntTyp();
                return expr.typ;
            case LT:
            case GT:
            case LE:
            case GE:
                // [Rel]
                if (!lhsTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemError(expr, "" + op + ": expected assignable to Int on lhs, got: " + lhsTyp.render(0, 0));
                if (!rhsTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemError(expr, "" + op + ": expected assignable to Int on rhs, got: " + rhsTyp.render(0, 0));
                expr.typ = new Ast.BoolTyp();
                return expr.typ;
            case EQ:
            case NE:
                // [Rel] enhanced for subtyping
                // lhs is assignable to rhs or rhs is assignable to lhs
                if (!(lhsTyp.isSubtypeOrEquals(rhsTyp) || rhsTyp.isSubtypeOrEquals(lhsTyp)))
                    throw new SemError(expr, "" + op + ": expected LHS/RHS types to be compatible, got lhs: " + lhsTyp.render(0, 0) + " and rhs: " + rhsTyp.render(0, 0));
                expr.typ = new Ast.BoolTyp();
                return expr.typ;
            case LAND:
            case LOR:
                // [Bool]
                if (!lhsTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemError(expr, "" + op + ": expected assignable to Bool on lhs, got: " + lhsTyp.render(0, 0));
                if (!rhsTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemError(expr, "" + op + ": expected assignable to Bool on rhs, got: " + rhsTyp.render(0, 0));
                expr.typ = new Ast.BoolTyp();
                return expr.typ;
            default:
                throw new AssertionError("BUG");
            }
        } else if (expr instanceof Ast.DotExpr) {
            // [Field]
            Ast.Expr target = ((Ast.DotExpr) expr).target;
            Ast.Typ targetTyp = doExpr(target, env);
            String ident = ((Ast.DotExpr) expr).ident;

            if (!(targetTyp instanceof Ast.ClazzTyp))
                throw new SemError(expr, "field access: expected class type, got: " + targetTyp.render(0, 0));

            String cname = ((Ast.ClazzTyp) targetTyp).cname;
            ClazzDescr clazz = getClazz(cname);
            if (!clazz.hasField(ident))
                throw new SemError(expr, "field access: no such field in " + cname + ": " + ident);

            expr.typ = clazz.getFieldTyp(ident);
            return expr.typ;
        } else if (expr instanceof Ast.CallExpr) {
            Ast.Expr target = ((Ast.CallExpr) expr).target;
            List<Ast.Expr> args = ((Ast.CallExpr) expr).args;
            if (target instanceof Ast.IdentExpr) {
                // [LocalCall]
                String targetName = ((Ast.IdentExpr) target).name;

                if (!env.contains(targetName))
                    throw new SemError(target, "unknown symbol: " + targetName);

                Ast.Typ targetTyp = env.get(targetName);
                if (!(targetTyp instanceof Ast.PolyFuncTyp))
                    throw new SemError(expr, "call: expected method, got: " + targetTyp.render(0, 0));

                // Construct argTyps of call
                ArrayList<Ast.Typ> argTyps = new ArrayList<>();
                for (Ast.Expr arg : args) {
                    argTyps.add(doExpr(arg, env));
                }

                // Lookup method based on signature, taking into account subtyping
                List<Ast.FuncTyp> candidates = ((Ast.PolyFuncTyp) targetTyp).candidates(argTyps);
                if (candidates.isEmpty())
                    throw new SemError(expr, "no matching method signature for " + renderArgTyps(argTyps) + ". Candidates are: " + targetTyp.render(0, 0));
                else if (candidates.size() > 1)
                    throw new SemError(expr, "ambiguous method call for " + renderArgTyps(argTyps) + ". Matching candidates are: " + new Ast.PolyFuncTyp(candidates).render(0, 0));

                Ast.FuncTyp matchingTyp = candidates.get(0);
                expr.typ = matchingTyp.retTyp;
                ((Ast.CallExpr) expr).meth = matchingTyp.meth;
                return expr.typ;
            } else if (target instanceof Ast.DotExpr) {
                // [GlobalCall]
                // IMPORTANT: Note we are doing _method_ access here,
                // as opposed to field access in the default handling of DotExpr

                Ast.Expr targetTarget = ((Ast.DotExpr) target).target;
                String targetIdent = ((Ast.DotExpr) target).ident;
                Ast.Typ targetTargetTyp = doExpr(targetTarget, env);

                if (!(targetTargetTyp instanceof Ast.ClazzTyp))
                    throw new SemError(target, "method access: expected class type, got: " + targetTargetTyp.render(0, 0));

                String cname = ((Ast.ClazzTyp) targetTargetTyp).cname;
                ClazzDescr clazz = getClazz(cname);

                // Lookup method based on name
                if (!clazz.hasMethod(targetIdent))
                    throw new SemError(target, "method access: no such method in class " + cname + ": " + targetIdent);

                // Construct argTyps of call
                ArrayList<Ast.Typ> argTyps = new ArrayList<>();
                for (Ast.Expr arg : args) {
                    argTyps.add(doExpr(arg, env));
                }

                // Lookup method based on signature, taking into account subtyping
                Ast.PolyFuncTyp methodTyp = clazz.getMethodTyp(targetIdent);
                List<Ast.FuncTyp> candidates = methodTyp.candidates(argTyps);
                if (candidates.isEmpty())
                    throw new SemError(target, "method access: no matching method signature for " + cname + "." + targetIdent + " with " + renderArgTyps(argTyps) + ". Candidates are: " + methodTyp.render(0, 0));
                else if (candidates.size() > 1)
                    throw new SemError(target, "method access: too many candidates for " + cname + "." + targetIdent + " with " + renderArgTyps(argTyps) + ". Matching candidates are: " + new Ast.PolyFuncTyp(candidates).render(0, 0));

                Ast.FuncTyp matchingMethodTyp = candidates.get(0);
                expr.typ = matchingMethodTyp.retTyp;
                ((Ast.CallExpr) expr).meth = matchingMethodTyp.meth;
                return expr.typ;
            } else {
                throw new SemError(expr, "call: target expression cannot be called");
            }
        } else if (expr instanceof Ast.NewExpr) {
            // [New]
            if (!hasClazz(((Ast.NewExpr) expr).cname))
                throw new SemError(expr, "no such class: " + ((Ast.NewExpr) expr).cname);
            expr.typ = new Ast.ClazzTyp(((Ast.NewExpr) expr).cname);
            return expr.typ;
        } else {
            throw new AssertionError("BUG: This is what happens when Java doesn't support ADTs :-(");
        }
    }

    private static String renderArgTyps(List<? extends Ast.Typ> argTyps) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        sb.append("[");
        for (Ast.Typ argTyp : argTyps) {
            if (i++ > 0)
                sb.append(" * ");
            sb.append(argTyp);
        }
        sb.append(" ]");
        return sb.toString();
    }

    /**
     * Returns true if typ is in the set of type classes (T)
     */
    private boolean isValidType(Ast.Typ typ) {
        if (typ instanceof Ast.ClazzTyp) {
            return hasClazz(((Ast.ClazzTyp)typ).cname);
        } else {
            return true;
        }
    }

    private boolean hasClazz(String cname) {
        return clazzdescrs.containsKey(cname);
    }

    private ClazzDescr getClazz(String cname) {
        return clazzdescrs.get(cname);
    }

    private static class ClazzDescr {
        private final Ast.Clazz clazz;
        private HashMap<String, Ast.VarDecl> fields = new HashMap<>();
        private HashMap<String, ArrayList<Ast.FuncTyp>> meths = new HashMap<>();

        private ClazzDescr(Ast.Clazz clazz) {
            this.clazz = clazz;
        }

        private boolean hasField(String name) {
            return fields.containsKey(name);
        }

        private void addField(Ast.VarDecl varDecl) {
            fields.put(varDecl.name, varDecl);
        }

        private Set<String> getFieldNames() {
            return fields.keySet();
        }

        private Ast.Typ getFieldTyp(String name) {
            return fields.get(name).typ;
        }

        private Ast.VarDecl getFieldVarDecl(String name) {
            return fields.get(name);
        }

        private boolean hasMethod(String name) {
            return meths.containsKey(name);
        }

        private void addMethod(String name, Ast.Meth meth) {
            if (!meths.containsKey(meth.name))
                meths.put(meth.name, new ArrayList<>());

            meths.get(meth.name).add(new Ast.FuncTyp(meth));
        }

        private Ast.PolyFuncTyp getMethodTyp(String name) {
            return new Ast.PolyFuncTyp(meths.get(name));
        }

        private Set<String> getMethodNames() {
            return meths.keySet();
        }
    }

    private static class Env {
        private final Env parent;
        private final HashMap<String, Ast.Typ> names = new HashMap<>();
        private final HashMap<String, Ast.VarDecl> varDecls = new HashMap<>();

        private Env() {
            this(null);
        }

        private Env(Env parent) {
            this.parent = parent;
        }

        private void put(String name, Ast.Typ typ, Ast.VarDecl varDecl) {
            names.put(name, typ);
            varDecls.put(name, varDecl);
        }

        private boolean contains(String name) {
            if (names.containsKey(name))
                return true;
            else if (parent != null)
                return parent.contains(name);
            else
                return false;
        }

        private Ast.Typ get(String name) {
            if (names.containsKey(name))
                return names.get(name);
            else
                return parent.get(name);
        }

        private Ast.VarDecl getVarDecl(String name) {
            if (varDecls.containsKey(name))
                return varDecls.get(name);
            else
                return parent.getVarDecl(name);
        }
    }

    public static class SemError extends Exception {
        public final Ast.Location location;

        public SemError(Ast.Locatable loc, String message) {
            super(message);
            this.location = loc.getLocation();
        }
    }

    public static class SemErrors extends Exception {
        private ArrayList<SemError> errors;

        public SemErrors(List<SemError> errors) {
            super("sem errors failed");
            this.errors = new ArrayList<>(errors);
        }

        public SemErrors(Ast.Locatable loc, String message) {
            this(Arrays.asList(new SemError(loc, message)));
        }

        public List<SemError> getErrors() {
            return Collections.unmodifiableList(errors);
        }
    }
}
