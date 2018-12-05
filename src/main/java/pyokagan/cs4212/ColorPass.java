package pyokagan.cs4212;

import java.util.*;

/**
 * Performs register coloring.
 */
public class ColorPass {
    private static int NUM_REGISTERS = 13;
    private static int NUM_ARG_REGISTERS = 4;
    private final Ir.Meth meth;
    private DomPass domPass;
    private LivePass livePass;

    private ColorPass(Ir.Meth meth, DomPass domPass, LivePass livePass) {
        this.meth = meth;
        this.domPass = domPass;
        this.livePass = livePass;

        // First un-assign all registers
        for (Ir.Var v : meth.args) {
            v.reg = -1;
        }
        for (Ir.Var v : meth.locals) {
            v.reg = -1;
        }

        // Pre-assign registers for arguments
        for (int i = 0; i < NUM_ARG_REGISTERS && i < meth.args.size(); i++) {
            Ir.Var arg = meth.args.get(i);
            arg.reg = i;
        }

        for (Ir.Block block : domPass.getPreorder()) {
            boolean[] assigned = new boolean[NUM_REGISTERS];
            Ir.Var[] assignedVar = new Ir.Var[NUM_REGISTERS];

            // All variables live in have already been colored
            for (Ir.Var v : livePass.getLiveIn(block)) {
                if (v.reg < 0)
                    throw new AssertionError("" + v + " is live into " + block + " but has no allocated register!");
                if (assigned[v.reg])
                    throw new AssertionError("Vars " + assignedVar[v.reg] + " and " + v + " assigned into the same register " + v.reg + " in " + block + ", livein: " + livePass.getLiveIn(block) + ", incoming: " + block.incoming);
                assigned[v.reg] = true;
                assignedVar[v.reg] = v;
            }

            ArrayList<Ir.Var> prevDefs = new ArrayList<>();
            for (Ir.Stmt stmt : block.stmts) {
                // Check if the defs of the previous statement were defined-but-not-used
                // BUT only once we reach the end of the phi stmt block
                if (!(stmt instanceof Ir.PhiStmt)) {
                    for (Ir.Var lastDef : prevDefs) {
                        if (!livePass.getLiveOut(block).contains(lastDef) && livePass.getLastUse(block, lastDef) == null) {
                            assigned[lastDef.reg] = false;
                            assignedVar[lastDef.reg] = null;
                        }
                    }
                    prevDefs.clear();
                }

                for (Ir.Var use : stmt.getUses()) {
                    if (!livePass.getLiveOut(block).contains(use) && livePass.getLastUse(block, use) == stmt) {
                        assigned[use.reg] = false;
                        assignedVar[use.reg] = null;
                    }
                }

                // For CallPrepStmts, the way we assign registers is different
                if (stmt instanceof Ir.CallPrepStmt) {
                    Ir.CallPrepStmt callPrepStmt = (Ir.CallPrepStmt) stmt;

                    // All registers unassigned
                    for (int i = 0; i < assigned.length; i++) {
                        assigned[i] = false;
                        assignedVar[i] = null;
                    }

                    // Assign registers r0-r3 to numArgs of CallPrepStmt
                    for (int i = 0; i < stmt.getDefs().size() && i < callPrepStmt.numArgs; i++) {
                        Ir.Var def = stmt.getDefs().get(i);
                        def.reg = i;
                        assigned[i] = true;
                        assignedVar[i] = def;
                    }

                    HashSet<Ir.Var> assignedSrcs = new HashSet<>();

                    // Next, we try to greedily assign the same registers back to the RHS
                    // (Only if they are not in r0-r3)
                    for (int i = callPrepStmt.numArgs; i < callPrepStmt.srcs.size(); i++) {
                        Ir.Var src = callPrepStmt.srcs.get(i);
                        Ir.Var def = callPrepStmt.defs.get(i);
                        if (src.reg >= 4 && !assigned[src.reg]) {
                            def.reg = src.reg;
                            assigned[src.reg] = true;
                            assignedVar[src.reg] = def;
                            assignedSrcs.add(src);
                        }
                    }

                    // Now assign the rest
                    for (int i = callPrepStmt.numArgs; i < callPrepStmt.srcs.size(); i++) {
                        Ir.Var src = callPrepStmt.srcs.get(i);
                        Ir.Var def = callPrepStmt.defs.get(i);
                        if (assignedSrcs.contains(src))
                            continue;

                        int useReg = -1;
                        // Start searching from r4 onwards -- r0-r3 will be clobbered by the call
                        for (int j = 4; j < assigned.length; j++) {
                            if (assigned[j])
                                continue;
                            def.reg = j;
                            useReg = j;
                            assigned[j] = true;
                            assignedVar[j] = def;
                            break;
                        }

                        if (useReg < 0)
                            throw new AssertionError("Could not find suitable slot for " + def + ", slots are: " + Arrays.asList(assignedVar));
                    }
                } else if (!(stmt instanceof Ir.PhiStmt) || !((Ir.PhiStmt) stmt).memory) {
                    for (Ir.Var def : stmt.getDefs()) {
                        if (def == null)
                            continue;

                        if (def.reg >= 0)
                            throw new AssertionError("BUG: saw def of " + def + " but reg already assigned!");

                        int useReg = -1;
                        for (int i = 0; i < assigned.length; i++) {
                            if (!assigned[i]) {
                                def.reg = i;
                                useReg = i;
                                assigned[i] = true;
                                assignedVar[i] = def;
                                break;
                            }
                        }

                        if (useReg < 0)
                            throw new AssertionError("Could not find available slot for " + def + ", slots are: " + Arrays.asList(assignedVar));
                    }
                }

                if (!(stmt instanceof Ir.PhiStmt) || !((Ir.PhiStmt) stmt).memory)
                    prevDefs.addAll(stmt.getDefs());
            }
        }
    }

    public static void run(Ir.Meth meth, DomPass domPass, LivePass livePass) {
        ColorPass cp = new ColorPass(meth, domPass, livePass);
    }

}
