package pyokagan.cs4212;

import java.util.*;

/**
 * Convert into SSA form.
 *
 * https://www.cs.rice.edu/~keith/EMBED/dom.pdf
 *https://pp.info.uni-karlsruhe.de/uploads/publikationen/braun13cc.pdf
 * Efficiently computing static single assignment form and the control dependence graph
 */
public class SsaPass {
    private final Ir.Meth meth;
    private final DomPass dom;
    private final DomFrontierPass domFrontiers;

    private HashMap<Ir.Var, Ir.Var> reachingDef = new HashMap<>();
    private HashMap<Ir.Var, Ir.Block> definition = new HashMap<>();
    private ArrayList<Ir.Var> newLocals = new ArrayList<>();
    private int localNum = 0;

    private SsaPass(Ir.Meth meth, DomPass domPass, DomFrontierPass domFrontierPass) {
        this.meth = meth;
        this.dom = domPass;
        this.domFrontiers = domFrontierPass;
    }

    public static void run(Ir.Meth meth, DomPass domPass, DomFrontierPass domFrontierPass) {
        SsaPass sp = new SsaPass(meth, domPass, domFrontierPass);
        sp.phiPass();
        sp.renamePass();
    }

    private void phiPass() {
        HashSet<Ir.Var> globalVars = new HashSet<>();
        for (Ir.Block block : meth.blocks) {
            HashSet<Ir.Var> varKill = new HashSet<>();

            for (Ir.Stmt stmt : block.stmts) {
                for (Ir.Var v : stmt.getUses()) {
                    if (!varKill.contains(v))
                        globalVars.add(v);
                }

                varKill.addAll(stmt.getDefs());
            }
        }

        for (Ir.Var v : meth.args) {
            if (globalVars.contains(v))
                phiPass(v);
        }
        for (Ir.Var v : meth.locals) {
            if (globalVars.contains(v))
                phiPass(v);
        }
    }

    private void phiPass(Ir.Var v) {
        HashSet<Ir.Block> F = new HashSet<>(); // set of basic blocks where phi was added
        HashSet<Ir.Block> W = new HashSet<>(); // set of basic blocks that contain definitions of v

        HashSet<Ir.Block> defs = new HashSet<>(); // set of basic blocks that actually contain definitions of v
        for (Ir.Block block : meth.blocks) {
            for (Ir.Stmt stmt : block.stmts) {
                for (Ir.Var dst : stmt.getDefs()) {
                    if (dst == v) {
                        defs.add(block);
                        break;
                    }
                }
            }
        }

        for (Ir.Block block : defs) {
            W.add(block);
        }

        while (!W.isEmpty()) {
            Ir.Block X = W.iterator().next();
            W.remove(X);
            for (Ir.Block Y : domFrontiers.getDomFrontier(X)) {
                if (F.contains(Y))
                    continue;

                Y.stmts.add(0, new Ir.PhiStmt(v, Y.incoming.size()));
                F.add(Y);
                if (!defs.contains(Y))
                    W.add(Y);
            }
        }
    }

    private void renamePass() {
        Ir.Block firstBlock = meth.blocks.get(0);
        for (Ir.Var v : meth.args) {
            reachingDef.put(v, v); // For args, is defined on function entry
            definition.put(v, firstBlock);
        }
        for (Ir.Var v : meth.locals) {
            reachingDef.put(v, null);
            definition.put(v, firstBlock);
        }

        for (Ir.Block block : dom.getPreorder()) {
            for (Ir.Stmt stmt : block.stmts) {
                // For each variable read
                for (Ir.Rval rv : stmt.getRvals()) {
                    doRval(rv, block);
                }

                if (stmt instanceof Ir.FieldAssignStmt) {
                    Ir.FieldAssignStmt fieldAssignStmt = (Ir.FieldAssignStmt) stmt;
                    fieldAssignStmt.dst = doUse(fieldAssignStmt.dst, block);
                }

                // For each variable defined
                for (int i = 0; i < stmt.getDefs().size(); i++) {
                    Ir.Var v = stmt.getDefs().get(i);
                    if (v == null)
                        continue;
                    updateReachingDef(v, block);
                    Ir.Var newVar = new Ir.Var(v.typ, v.name + "_" + (localNum++));
                    newLocals.add(newVar);
                    stmt.setDef(i, newVar);
                    definition.put(newVar, block);
                    reachingDef.put(newVar, reachingDef.get(v));
                    reachingDef.put(v, newVar);
                }
            }

            if (block.outgoingDirect != null) {
                for (Ir.Stmt stmt : block.outgoingDirect.stmts) {
                    if (!(stmt instanceof Ir.PhiStmt))
                        break;
                    handlePhi((Ir.PhiStmt) stmt, block, block.outgoingDirect);
                }
            }
            if (block.outgoingCond != null) {
                for (Ir.Stmt stmt : block.outgoingCond.stmts) {
                    if (!(stmt instanceof Ir.PhiStmt))
                        break;
                    handlePhi((Ir.PhiStmt) stmt, block, block.outgoingCond);
                }
            }
        }

        meth.locals = newLocals;
    }

    private void doRval(Ir.Rval v, Ir.Block currBlock) {
        if (!(v instanceof Ir.VarRval))
            return;
        Ir.VarRval rv = (Ir.VarRval) v;
        Ir.Var newVar = doUse(rv.v, currBlock);
        rv.v = newVar;
    }

    private Ir.Var doUse(Ir.Var use, Ir.Block currBlock) {
        updateReachingDef(use, currBlock);
        Ir.Var reach = reachingDef.get(use);
        if (reach == null) {
            reach = new Ir.Var(use.typ, use.name + "_" + (localNum++));
            newLocals.add(reach);
            definition.put(reach, currBlock);
            reachingDef.put(use, reach);
        }
        return reach;
    }

    private void updateReachingDef(Ir.Var v, Ir.Block currBlock) {
        Ir.Var r = reachingDef.get(v);
        while (!(r == null || isDominate(definition.get(r), currBlock)))
            r = reachingDef.get(r);
        reachingDef.put(v, r);
    }

    private void handlePhi(Ir.PhiStmt stmt, Ir.Block incomingBlock, Ir.Block phiBlock) {
        updateReachingDef(stmt.originalVar, incomingBlock);
        Ir.Var v = reachingDef.get(stmt.originalVar);
        // Objects.requireNonNull(v);
        if (v != null) {
            int idx = phiBlock.incoming.indexOf(incomingBlock);
            stmt.args.set(idx, v);
        }
    }

    private boolean isDominate(Ir.Block a, Ir.Block b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        while (b != null && b != a) {
            b = dom.getIdom(b);
        }

        return b == a;
    }
}
