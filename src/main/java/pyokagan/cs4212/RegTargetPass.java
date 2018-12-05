package pyokagan.cs4212;

import java.util.*;

/**
 * Inserts register targeting stmts before calls.
 */
public class RegTargetPass {
    private final Ir.Meth meth;
    private HashSet<Ir.Var> modifiedVars = new HashSet<>();
    private int tmpCounter;

    private LivePass livePass;

    private RegTargetPass(Ir.Meth meth, DomPass domPass, DomFrontierPass domFrontierPass) {
        this.meth = meth;
        this.livePass = new LivePass(meth);

        for (Ir.Block block : meth.blocks) {
            HashSet<Ir.Var> liveVars = new HashSet<>();
            liveVars.addAll(livePass.getLiveOut(block));

            for (int i = block.stmts.size() - 1; i >= 0; i--) {
                Ir.Stmt stmt = block.stmts.get(i);

                HashSet<Ir.Var> liveVarsAfterStmt = new HashSet<>(liveVars);
                for (Ir.Var v : stmt.getDefs())
                    liveVars.remove(v);
                for (Ir.Var v : stmt.getUses())
                    liveVars.add(v);

                if (stmt instanceof Ir.CallStmt) {
                    Ir.CallStmt callStmt = (Ir.CallStmt) stmt;
                    Ir.CallPrepStmt callPrepStmt = new Ir.CallPrepStmt();
                    callPrepStmt.numArgs = callStmt.args.size();
                    HashSet<Ir.Var> callArgs = new HashSet<>();

                    // Generate new args for call args
                    for (int j = 0; j < callStmt.args.size(); j++) {
                        Ir.Rval rv = callStmt.args.get(j);
                        if (!(rv instanceof Ir.VarRval))
                            throw new AssertionError("BUG: arg of call is not VarRval. Was IrLowerPass run?");
                        Ir.VarRval vrv = (Ir.VarRval) rv;
                        Ir.Var tmp = genTemp(vrv.v.typ);
                        callPrepStmt.defs.add(tmp);
                        callPrepStmt.srcs.add(vrv.v);
                        callArgs.add(vrv.v);
                        callStmt.args.set(j, new Ir.VarRval(tmp));
                    }

                    // Fill in the rest of variables live before and after the call
                    HashSet<Ir.Var> liveVarsBeforeAndAfterStmt = new HashSet<>(liveVars);
                    liveVarsBeforeAndAfterStmt.retainAll(liveVarsAfterStmt);
                    for (Ir.Var v : liveVarsBeforeAndAfterStmt) {
                        callPrepStmt.defs.add(v);
                        callPrepStmt.srcs.add(v);
                        modifiedVars.add(v);
                    }

                    block.stmts.add(i, callPrepStmt);
                    // No need i++ here, we don't need to see callPrepStmt again
                }
            }
        }

        // Reconstruct SSA
        for (Ir.Var v : modifiedVars) {
            SsaReconstructPass.run(meth, v, domPass, domFrontierPass);
        }
    }

    public static void run(Ir.Meth meth, DomPass domPass, DomFrontierPass domFrontierPass) {
        new RegTargetPass(meth, domPass, domFrontierPass);
    }

    private Ir.Var genTemp(Ast.Typ typ) {
        String name = "%rtp" + tmpCounter;
        tmpCounter++;
        Ir.Var v = new Ir.Var(typ, name);
        meth.locals.add(v);
        return v;
    }
}
