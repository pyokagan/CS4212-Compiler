package pyokagan.cs4212;

import java.util.*;

/**
 * Reconstruct SSA form.
 */
public class SsaReconstructPass {

    // Mapping from statement that (re-)defines v to its new var
    private HashMap<Ir.Stmt, Ir.Var> newVars = new HashMap<>();

    // Dominance frontier of all blocks that (re-)defines v
    private HashSet<Ir.Block> domFrontier = new HashSet<>();

    private Ir.Meth meth;
    private Ir.Var V;
    private int varIdx = 0;
    private DomPass domPass;
    private DomFrontierPass domFrontierPass;

    private SsaReconstructPass(Ir.Meth meth, Ir.Var V, DomPass domPass, DomFrontierPass domFrontierPass) {
        this.meth = meth;
        this.V = V;
        this.domPass = domPass;
        this.domFrontierPass = domFrontierPass;

        // Scan through all blocks looking for all definitions of V
        int idx = 0;
        for (Ir.Block block : meth.blocks) {
            boolean hasDef = false;

            for (Ir.Stmt stmt : block.stmts) {
                for (Ir.Var def : stmt.getDefs()) {
                    if (def != V)
                        continue;

                    // Create a new var
                    Ir.Var newVar = new Ir.Var(V.typ, V.name + "_" + (varIdx++));
                    newVar.phiWeb = V.phiWeb;
                    meth.locals.add(newVar);
                    newVars.put(stmt, newVar);
                    hasDef = true;
                    break;
                }
            }

            if (hasDef) {
                // Add dominance frontiers
                domFrontier.addAll(domFrontierPass.getDomFrontier(block));
            }
        }

        // Scan through all blocks looking for uses of V
        // and rewrite them
        for (Ir.Block block : meth.blocks) {
            for (int j = 0; j < block.stmts.size(); j++) {
                Ir.Stmt stmt = block.stmts.get(j);

                if (stmt instanceof Ir.PhiStmt) {
                    Ir.PhiStmt phiStmt = (Ir.PhiStmt) stmt;
                    for (int i = 0; i < phiStmt.args.size(); i++) {
                        if (phiStmt.args.get(i) != V)
                            continue; // Not the use we are looking for

                        Ir.Var newV = findDefFromBottom(block.incoming.get(i));
                        phiStmt.args.set(i, newV);
                    }
                } else {
                    Ir.Var newV = null;

                    for (Ir.Rval rv : stmt.getRvals()) {
                        if (!(rv instanceof Ir.VarRval))
                            continue;
                        Ir.VarRval vrv = (Ir.VarRval) rv;
                        if (vrv.v != V)
                            continue; // Not the use we are looking for
                        if (newV == null)
                            newV = findDefFromBottom(block, j - 1);
                        vrv.v = newV;
                    }

                    if (stmt instanceof Ir.FieldAssignStmt) {
                        Ir.FieldAssignStmt fieldAssignStmt = (Ir.FieldAssignStmt) stmt;
                        if (fieldAssignStmt.dst == V) {
                            if (newV == null)
                                newV = findDefFromBottom(block, j - 1);
                            fieldAssignStmt.dst = newV;
                        }
                    }

                    if (stmt instanceof Ir.SpillStmt) {
                        Ir.SpillStmt spillStmt = (Ir.SpillStmt) stmt;
                        if (spillStmt.v == V) {
                            if (newV == null)
                                newV = findDefFromBottom(block, j - 1);
                            spillStmt.v = newV;
                        }
                    }

                    if (stmt instanceof Ir.StackArgStmt) {
                        Ir.StackArgStmt stackArgStmt = (Ir.StackArgStmt) stmt;
                        if (stackArgStmt.v == V) {
                            if (newV == null)
                                newV = findDefFromBottom(block, j - 1);
                            stackArgStmt.v = newV;
                        }
                    }

                    if (stmt instanceof Ir.CallPrepStmt) {
                        Ir.CallPrepStmt callPrepStmt = (Ir.CallPrepStmt) stmt;
                        for (int i = 0; i < callPrepStmt.srcs.size(); i++) {
                            Ir.Var x = callPrepStmt.srcs.get(i);
                            if (x == V) {
                                if (newV == null)
                                    newV = findDefFromBottom(block, j - 1);
                                callPrepStmt.srcs.set(i, newV);
                            }
                        }
                    }
                }
            }
        }

        // Finally update newVars
        for (Map.Entry<Ir.Stmt, Ir.Var> entry : newVars.entrySet()) {
            Ir.Stmt defStmt = entry.getKey();
            for (int i = 0; i < defStmt.getDefs().size(); i++) {
                if (defStmt.getDefs().get(i) == V) {
                    defStmt.setDef(i, entry.getValue());
                    break;
                }
            }
        }

        // Remove original var from locals
        meth.locals.remove(V);
    }

    private Ir.Var findDefFromBottom(Ir.Block block) {
        return findDefFromBottom(block, block.stmts.size() - 1);
    }

    private Ir.Var findDefFromBottom(Ir.Block block, int startIdx) {
        // Scan through block backwards
        for (int i = startIdx; i >= 0; i--) {
            Ir.Stmt stmt = block.stmts.get(i);
            for (Ir.Var def : stmt.getDefs()) {
                if (def == V)
                    return newVars.get(stmt);
            }
        }

        // Failed to find definition, search through predecessors
        return findDefFromTop(block);
    }

    private Ir.Var findDefFromTop(Ir.Block block) {
        if (domFrontier.contains(block)) {
            Ir.Var newVar = new Ir.Var(V.typ, V.name + "_" + (varIdx++));
            newVar.phiWeb = V.phiWeb;
            Ir.PhiStmt newPhiStmt = new Ir.PhiStmt(V, block.incoming.size());
            block.stmts.add(0, newPhiStmt);
            meth.locals.add(newVar);
            newVars.put(newPhiStmt, newVar);
            domFrontier.addAll(domFrontierPass.getDomFrontier(block));
            for (int i = 0; i < block.incoming.size(); i++) {
                Ir.Block pred = block.incoming.get(i);
                Ir.Var newV = findDefFromBottom(pred);
                newPhiStmt.args.set(i, newV);
            }
            return newVar;
        } else {
            Ir.Block idom = domPass.getIdom(block);
            if (idom == null)
                return V; // Don't replace -- the definition comes from an argument
            else
                // Search in immediate dominator
                return findDefFromBottom(idom);
        }
    }

    public static void run(Ir.Meth meth, Ir.Var V, DomPass domPass, DomFrontierPass domFrontierPass) {
        new SsaReconstructPass(meth, V, domPass, domFrontierPass);
    }
}
