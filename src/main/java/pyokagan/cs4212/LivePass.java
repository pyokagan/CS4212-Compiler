package pyokagan.cs4212;

import java.util.*;

/**
 * Global liveness analysis.
 */
public class LivePass {
    private final Ir.Meth meth;
    private HashMap<Ir.Block, HashSet<Ir.Var>> liveIns = new HashMap<>();
    private HashMap<Ir.Block, HashSet<Ir.Var>> liveOuts = new HashMap<>();
    private HashMap<Ir.Block, HashSet<Ir.Var>> blockUses = new HashMap<>();
    private HashMap<Ir.Block, HashSet<Ir.Var>> blockDefs = new HashMap<>();
    private HashMap<Ir.Block, HashMap<Ir.Var, Ir.Stmt>> lastUses = new HashMap<>();

    public LivePass(Ir.Meth meth) {
        this.meth = meth;

        for (Ir.Block block : meth.blocks) {
            liveIns.put(block, new HashSet<>());
            liveOuts.put(block, new HashSet<>());
        }

        // Compute blockUses and blockDefs
        for (Ir.Block block : meth.blocks) {
            HashSet<Ir.Var> blockUses = new HashSet<>();
            HashSet<Ir.Var> blockDefs = new HashSet<>();
            HashMap<Ir.Var, Ir.Stmt> blockLastUses = new HashMap<>();

            for (int i = block.stmts.size() - 1; i >= 0; i--) {
                Ir.Stmt stmt = block.stmts.get(i);

                for (Ir.Var v : stmt.getDefs())
                    blockUses.remove(v);

                // Compute stmt uses
                HashSet<Ir.Var> uses = new HashSet<>(stmt.getUses());
                if (stmt instanceof Ir.JumpStmt)
                    uses.addAll(getJumpUses(block));

                for (Ir.Var v : uses) {
                    if (!blockLastUses.containsKey(v))
                        blockLastUses.put(v, stmt);
                }
                blockUses.addAll(uses);
                blockDefs.addAll(stmt.getDefs());
            }

            this.blockUses.put(block, blockUses);
            this.blockDefs.put(block, blockDefs);
            this.lastUses.put(block, blockLastUses);
        }

        // Iteration
        boolean hasChange = true;
        while (hasChange) {
            hasChange = false;

            for (Ir.Block block : meth.blocksPo) {
                HashSet<Ir.Var> liveOut = liveOuts.get(block);
                HashSet<Ir.Var> liveIn = liveIns.get(block);

                // OUT[block] = union of IN[succ] + their PHI nodes
                for (Ir.Block succ : block.getOutgoing())
                    liveOut.addAll(liveIns.get(succ));
                liveOut.addAll(getJumpUses(block));

                // IN[block] = f(OUT[block])
                HashSet<Ir.Var> newLiveIn = new HashSet<>();
                newLiveIn.addAll(liveOut);
                newLiveIn.removeAll(blockDefs.get(block));
                newLiveIn.addAll(blockUses.get(block));
                if (!hasChange && !newLiveIn.equals(liveIn))
                    hasChange = true;
                liveIns.put(block, newLiveIn);
            }
        }
    }

    public Set<Ir.Var> getLiveIn(Ir.Block block) {
        return liveIns.get(block);
    }

    public Set<Ir.Var> getLiveOut(Ir.Block block) {
        return liveOuts.get(block);
    }

    public Ir.Stmt getLastUse(Ir.Block block, Ir.Var v) {
        return lastUses.get(block).get(v);
    }

    @Override
    public String toString() {
        return "LivePass(IN=" + liveIns + ", OUT=" + liveOuts + ")";
    }

    /**
     * Return union of vars that phi nodes in successive blocks use.
     */
    public static Set<Ir.Var> getJumpUses(Ir.Block block) {
        HashSet<Ir.Var> uses = new HashSet<>();
        for (Ir.Block succ : block.getOutgoing()) {
            int incomingIdx = succ.incoming.indexOf(block);
            for (Ir.Stmt stmt : succ.stmts) {
                if (!(stmt instanceof Ir.PhiStmt))
                    break;
                Ir.PhiStmt phiStmt = (Ir.PhiStmt) stmt;
                if (!phiStmt.memory && phiStmt.args.get(incomingIdx) != null)
                    uses.add(phiStmt.args.get(incomingIdx));
            }
        }
        return uses;
    }
}
