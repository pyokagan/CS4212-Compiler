package pyokagan.cs4212;

import java.util.*;

/**
 * Optimizes jumps-to-jumps.
 */
public class ArmJumpOpt {
    // Mapping from a block label (if it is a jump-to-jump) to its target
    HashMap<String, String> targetMap = new HashMap<>();

    public static void run(Arm.Prog prog) {
        ArmJumpOpt p = new ArmJumpOpt();

        // Discover all jump-to-jump blocks
        for (Arm.Block block : prog.textBlocks) {
            if (!isJumpToJump(block))
                continue;

            String dst = getJumpTarget(block);
            p.targetMap.put(block.name, p.getJumpDst(dst));
        }

        // Rewrite stmts
        for (Arm.Block block : prog.textBlocks) {
            for (Arm.Instr instr : block.instrs) {
                if (!(instr instanceof Arm.BInstr))
                    continue;
                Arm.BInstr bInstr = (Arm.BInstr) instr;
                bInstr.label = p.getJumpDst(bInstr.label);
            }
        }
    }

    private static boolean isJumpToJump(Arm.Block block) {
        if (block.instrs.isEmpty())
            return false;

        Arm.Instr firstInstr = block.instrs.get(0);
        if (!(firstInstr instanceof Arm.BInstr))
            return false;

        Arm.BInstr bInstr = (Arm.BInstr) firstInstr;
        if (bInstr.cond != Arm.Cond.AL)
            return false;

        return true;
    }

    private static String getJumpTarget(Arm.Block block) {
        Arm.BInstr bInstr = (Arm.BInstr) block.instrs.get(0);
        return bInstr.label;
    }

    private String getJumpDst(String label) {
        if (!targetMap.containsKey(label))
            return label;

        String t = getJumpDst(targetMap.get(label));
        targetMap.put(label, t);
        return t;
    }
}
