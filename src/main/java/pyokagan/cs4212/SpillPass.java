package pyokagan.cs4212;

import java.util.*;

/**
 * Performs spilling to ensure that register pressure at each statement does not exceed k
 *
 * http://compilers.cs.uni-saarland.de/projects/ssara/hack_ssara_ssa09.pdf
 */
public class SpillPass {

    private static int NUM_REGISTERS = 13;

    private final Ir.Meth meth;
    private final DomPass domPass;
    private final DomFrontierPass domFrontierPass;
    private HashMap<Ir.Var, Ir.Block> defLocations = new HashMap<>();
    private HashMap<Ir.Block, BlockInfo> blockInfos = new HashMap<>();
    private HashSet<Ir.Var> modifiedVars = new HashSet<>();

    private SpillPass(Ir.Meth meth, DomPass domPass, DomFrontierPass domFrontierPass) {
        this.meth = meth;
        this.domPass = domPass;
        this.domFrontierPass = domFrontierPass;
        for (Ir.Block block : meth.blocks) {
            blockInfos.put(block, new BlockInfo());
        }
    }

    public static void run(Ir.Meth meth, DomPass domPass, DomFrontierPass domFrontierPass) {
        SpillPass sp = new SpillPass(meth, domPass, domFrontierPass);
        sp.computeDefLocations();
        sp.computeNextUse();
        sp.computeWexit(NUM_REGISTERS);
        sp.doSpilling(NUM_REGISTERS);
        // Reconstruct SSA
        for (Ir.Var v : sp.modifiedVars) {
            SsaReconstructPass.run(meth, v, domPass, domFrontierPass);
        }
    }

    private void computeDefLocations() {
        Ir.Block firstBlock = meth.blocks.get(0);
        for (Ir.Var v : meth.args) {
            defLocations.put(v, firstBlock);
        }
        for (Ir.Block block : meth.blocks) {
            for (Ir.Stmt stmt : block.stmts) {
                for (Ir.Var def : stmt.getDefs()) {
                    if (defLocations.containsKey(def))
                        throw new AssertionError("BUG: multiple defs of " + def + ", last loc in " + defLocations.get(def));
                    defLocations.put(def, block);
                }
            }
        }
    }

    private void computeNextUse() {
        boolean hasChange = true;
        while (hasChange) {
            hasChange = false;

            for (Ir.Block block : meth.blocksPo) {
                BlockInfo blockInfo = blockInfos.get(block);

                // Compute OUT[B]
                for (Ir.Block succ : block.getOutgoing()) {
                    // Add nextUseIns
                    for (Map.Entry<Ir.Var, Integer> entry : blockInfos.get(succ).nextUseIns.entrySet()) {
                        Ir.Var k = entry.getKey();
                        int v = entry.getValue();

                        if (!blockInfo.nextUseOuts.containsKey(k) || v < blockInfo.nextUseOuts.get(k)) {
                            blockInfo.nextUseOuts.put(k, v);
                        }
                    }
                }

                HashSet<Ir.Var> definedOrSeen = new HashSet<>();
                for (int i = 0; i < block.stmts.size(); i++) {
                    Ir.Stmt stmt = block.stmts.get(i);
                    HashSet<Ir.Var> uses = new HashSet<>(stmt.getUses());
                    if (stmt instanceof Ir.JumpStmt)
                        uses.addAll(LivePass.getJumpUses(block));
                    for (Ir.Var use : uses) {
                        // Ignore vars already defined or seen
                        if (definedOrSeen.contains(use))
                            continue;

                        // Record down first usage of the var
                        if (!blockInfo.nextUseIns.containsKey(use) || i < blockInfo.nextUseIns.get(use)) {
                            hasChange = true;
                            blockInfo.nextUseIns.put(use, i);
                        }
                        definedOrSeen.add(use);
                    }

                    for (Ir.Var def : stmt.getDefs()) {
                        if (def != null)
                            definedOrSeen.add(def);
                    }
                }

                // Calculate the rest
                for (Map.Entry<Ir.Var, Integer> entry : blockInfo.nextUseOuts.entrySet()) {
                    Ir.Var k = entry.getKey();
                    if (definedOrSeen.contains(k))
                        continue; // Already handled in the loop above
                    int newV = entry.getValue() + block.stmts.size();
                    if (!blockInfo.nextUseIns.containsKey(k) || newV != blockInfo.nextUseIns.get(k)) {
                        hasChange = true;
                        blockInfo.nextUseIns.put(k, newV);
                    }
                }
            }
        }
    }

    private void minAlgorithm(Ir.Block block, HashSet<Ir.Var> W, HashSet<Ir.Var> S, int K, boolean dry) {
        // W: variables in a register. |W| < k
        // S: variables that have already been spilled

        int i = 0;
        // Handle phi stmts first
        ArrayList<Ir.PhiStmt> phiStmts = new ArrayList<>();
        for (; i < block.stmts.size() && block.stmts.get(i) instanceof Ir.PhiStmt; i++) {
            phiStmts.add((Ir.PhiStmt) block.stmts.get(i));
        }
        if (dry) {
            // Greedily allocate registers to phiStmts for now
            for (Ir.PhiStmt phiStmt : phiStmts)
                W.addAll(phiStmt.getDefs());
            limit(block, W, S, i, K, dry);
        } else {
            int memoryPressure = phiStmts.size();
            // If memoryPressure > K, some of the incoming vars are already spilled.
            for (Ir.PhiStmt phiStmt : phiStmts) {
                boolean isMemory = false;
                for (int j = 0; j < block.incoming.size(); j++) {
                    Ir.Block pred = block.incoming.get(j);
                    BlockInfo predInfo = blockInfos.get(pred);
                    if (!predInfo.Wexit.contains(phiStmt.args.get(j)))
                        isMemory = true;
                }
                phiStmt.memory = isMemory;
                if (isMemory) {
                    memoryPressure--;
                } else {
                    W.addAll(phiStmt.getDefs());
                }
            }

            if (memoryPressure > K)
                throw new AssertionError("BUG: memoryPressure > K");

            limit(block, W, S, i, K, dry);
        }

        // Handle the rest of the stmts
        for (; i < block.stmts.size(); i++) {
            Ir.Stmt stmt = block.stmts.get(i);

            HashSet<Ir.Var> R = new HashSet<>();
            R.addAll(stmt.getUses());
            // Ensure that the vars used by successive phi nodes are loaded into registers
            //if (stmt instanceof Ir.JumpStmt)
            //    R.addAll(LivePass.getJumpUses(block));
            R.removeAll(W);
            for (Ir.Var use : R) {
                W.add(use);
                S.add(use);
            }
            int m = K;
            if (stmt instanceof Ir.CallStmt)
                m -= 4; // r0, r1, r2, r3 will be overwritten by the called function
            else
                m -= stmt.getDefs().size();
            ArrayList<Ir.SpillStmt> spillStmts = limit(block, W, S, i, m, dry);
            block.stmts.addAll(i, spillStmts);
            i += spillStmts.size();
            for (Ir.Var def : stmt.getDefs()) {
                W.add(Objects.requireNonNull(def));
            }

            // Add reloads for vars in R in front of insn
            if (!dry) {
                for (Ir.Var v : R) {
                    block.stmts.add(i, new Ir.ReloadStmt(v));
                    v.phiWeb.needStack = true;
                    modifiedVars.add(v);
                    i++;
                }
            }
        }
    }

    private HashMap<Ir.Var, Integer> computeNextUseDist(Ir.Block block, int insnIdx) {
        HashMap<Ir.Var, Integer> nextUseDist = new HashMap<>();
        BlockInfo blockInfo = blockInfos.get(block);

        for (int i = insnIdx; i < block.stmts.size(); i++) {
            Ir.Stmt stmt = block.stmts.get(i);
            HashSet<Ir.Var> uses = new HashSet<>(stmt.getUses());
            if (stmt instanceof Ir.JumpStmt)
                uses.addAll(LivePass.getJumpUses(block));
            for (Ir.Var use : uses) {
                if (!nextUseDist.containsKey(use))
                    nextUseDist.put(use, i - insnIdx);
            }
        }
        for (Map.Entry<Ir.Var, Integer> entry : blockInfo.nextUseOuts.entrySet()) {
            Ir.Var k = entry.getKey();
            int v = entry.getValue();
            if (!nextUseDist.containsKey(k))
                nextUseDist.put(k, block.stmts.size() - insnIdx + v);
        }
        return nextUseDist;
    }

    private ArrayList<Ir.SpillStmt> limit(Ir.Block block, HashSet<Ir.Var> W, HashSet<Ir.Var> S, int insnIdx, int m, boolean dry) {
        if (W.size() <= m)
            return new ArrayList<>(); // No need to perform any spilling

        final HashMap<Ir.Var, Integer> nextUseDist = computeNextUseDist(block, insnIdx);

        // Sort according to next use distance
        ArrayList<Ir.Var> sortedW = new ArrayList<>(W);
        Collections.sort(sortedW, new Comparator<Ir.Var>() {
            public int compare(Ir.Var a, Ir.Var b) {
                return nextUseDist.getOrDefault(a, Integer.MAX_VALUE) - nextUseDist.getOrDefault(b, Integer.MAX_VALUE);
            }
        });

        ArrayList<Ir.SpillStmt> spillStmts = new ArrayList<>();

        for (int i = m; i < sortedW.size(); i++) {
            Ir.Var v = sortedW.get(i);
            if (!dry && !S.contains(v) && nextUseDist.getOrDefault(v, Integer.MAX_VALUE) < Integer.MAX_VALUE) {
                spillStmts.add(new Ir.SpillStmt(v));
                v.phiWeb.needStack = true;
                modifiedVars.add(v);
            }
            S.remove(v);
        }

        W.clear();
        for (int i = 0; i < m; i++) {
            W.add(sortedW.get(i));
        }

        return spillStmts;
    }

    /**
     * Compute Wexit for each block so we can compute the Wentry
     */
    private void computeWexit(int K) {
        Ir.Block firstBlock = meth.blocks.get(0);
        for (Ir.Block block : meth.blocks) {
            BlockInfo blockInfo = blockInfos.get(block);
            HashSet<Ir.Var> W = new HashSet<>();
            // First 4 arguments of arguments are already in registers
            if (block == firstBlock) {
                for (int i = 0; i < 4 && i < meth.args.size(); i++)
                    W.add(meth.args.get(i));
            }
            HashSet<Ir.Var> S = new HashSet<>();
            minAlgorithm(block, W, S, K, true);
            blockInfo.Wexit = W;
            // NOTE: We don't have full W info at this point, so S will be inaccurate.
            // Set it to an empty set. Otherwise, redundant spills will be added.
            blockInfo.Sexit = new HashSet<>();
        }
    }

    private void doSpilling(int K) {
        if (meth.blocks.isEmpty())
            return;

        Ir.Block firstBlock = meth.blocks.get(0);

        for (Ir.Block block : meth.blocksRpo) {
            BlockInfo blockInfo = blockInfos.get(block);
            // First 4 arguments of arguments are already in registers
            HashSet<Ir.Var> initial = new HashSet<>();
            if (block == firstBlock) {
                for (int i = 0; i < 4 && i < meth.args.size(); i++)
                    initial.add(meth.args.get(i));
            }
            blockInfo.Wentry = computeWentryUsual(block, K, initial);
            blockInfo.Sentry = computeSentry(block);
            // Run spilling algo to compute Wexit and Sexit
            HashSet<Ir.Var> W = new HashSet<>(blockInfo.Wentry);
            HashSet<Ir.Var> S = new HashSet<>(blockInfo.Sentry);
            minAlgorithm(block, W, S, K, false);
            blockInfo.Wexit = W;
            blockInfo.Sexit = S;
        }

        // Insert spill/reload coupling
        for (Ir.Block block : meth.blocks) {
            BlockInfo blockInfo = blockInfos.get(block);

            for (Ir.Block pred : block.incoming) {
                BlockInfo predInfo = blockInfos.get(pred);

                // Temporarily remove last stmt
                Ir.Stmt lastStmt = pred.stmts.remove(pred.stmts.size() - 1);

                // All vars in ((block.Sentry - pred.Sexit) + block.phiMem) intersect pred.Wexit must be spilled
                HashSet<Ir.Var> needSpill = new HashSet<>(blockInfo.Sentry);
                needSpill.removeAll(predInfo.Sexit);
                int incomingIdx = block.incoming.indexOf(pred);
                for (Ir.Stmt stmt : block.stmts) {
                    if (!(stmt instanceof Ir.PhiStmt))
                        break;
                    Ir.PhiStmt phiStmt = (Ir.PhiStmt) stmt;
                    if (phiStmt.memory && phiStmt.args.get(incomingIdx) != null)
                        needSpill.add(phiStmt.args.get(incomingIdx));
                }
                needSpill.retainAll(predInfo.Wexit);
                for (Ir.Var v : needSpill) {
                    pred.stmts.add(new Ir.SpillStmt(v));
                }

                // All vars in block.Wentry - pred.Wexit must be reloaded
                HashSet<Ir.Var> needReload = new HashSet<>(blockInfo.Wentry);
                needReload.removeAll(predInfo.Wexit);
                for (Ir.Var v : needReload) {
                    pred.stmts.add(new Ir.ReloadStmt(v));
                    modifiedVars.add(v);
                }

                // Add back last stmt
                pred.stmts.add(lastStmt);
            }
        }
    }

    private HashSet<Ir.Var> computeWentryUsual(Ir.Block block, int K, Set<Ir.Var> initial) {
        HashMap<Ir.Var, Integer> freq = new HashMap<>();
        HashSet<Ir.Var> take = new HashSet<>(initial);
        HashSet<Ir.Var> cand = new HashSet<>();

        for (Ir.Block pred : block.incoming) {
            BlockInfo predBlockInfo = blockInfos.get(pred);
            for (Ir.Var v : predBlockInfo.Wexit) {
                if (take.contains(v))
                    continue;

                int currentFreq = freq.getOrDefault(v, 0);
                freq.put(v, ++currentFreq);
                cand.add(v);

                if (currentFreq == block.incoming.size()) {
                    cand.remove(v);
                    take.add(v);
                }
            }
        }

        final HashMap<Ir.Var, Integer> nextUseDist = computeNextUseDist(block, 0);
        ArrayList<Ir.Var> sortedCand = new ArrayList<>(cand);
        Collections.sort(sortedCand, new Comparator<Ir.Var>() {
            public int compare(Ir.Var a, Ir.Var b) {
                return nextUseDist.getOrDefault(a, Integer.MAX_VALUE) - nextUseDist.getOrDefault(b, Integer.MAX_VALUE);
            }
        });

        int extra = K - take.size();
        for (int i = 0; i < extra && i < sortedCand.size(); i++) {
            Ir.Var v = sortedCand.get(i);
            // Only add if the var def location dominates all of our predecessors
            boolean dominates = true;
            Ir.Block defLocation = defLocations.get(v);
            if (defLocation == null) {
                continue; // Var not even defined once
            }
            for (Ir.Block pred : block.incoming) {
                if (!domPass.isDominate(defLocation, pred))
                    dominates = false;
            }
            if (dominates && nextUseDist.getOrDefault(v, Integer.MAX_VALUE) < Integer.MAX_VALUE)
                take.add(v);
        }

        return take;
    }

    private HashSet<Ir.Var> computeSentry(Ir.Block block) {
        BlockInfo blockInfo = blockInfos.get(block);

        HashSet<Ir.Var> S = new HashSet<>();
        for (Ir.Block pred : block.incoming) {
            BlockInfo predBlockInfo = blockInfos.get(pred);
            S.addAll(predBlockInfo.Sexit);
        }

        // Remove vars not in Wentry
        S.retainAll(blockInfo.Wentry);
        return S;
    }

    private static class BlockInfo {
        private HashMap<Ir.Var, Integer> nextUseIns = new HashMap<>();
        private HashMap<Ir.Var, Integer> nextUseOuts = new HashMap<>();
        private HashSet<Ir.Var> Wexit = new HashSet<>(); // Variables in registers on exit from the block
        private HashSet<Ir.Var> Sexit = new HashSet<>(); // Variables that have been spilled on exit from the block
        private HashSet<Ir.Var> Wentry = new HashSet<>(); // Variables in registers on entry from the block
        private HashSet<Ir.Var> Sentry = new HashSet<>(); // Variables that have been spilled on entry into the block
    }
}
