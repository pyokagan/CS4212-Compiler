package pyokagan.cs4212;

import java.util.*;

/**
 * Removes labels that are never jumped to by joining blocks together.
 */
public class ArmLabelOpt {
    public static void run(Arm.Prog prog) {
        if (prog.textBlocks.isEmpty())
            return;

        // Compute set of used labels
        HashSet<String> usedLabels = new HashSet<>();
        usedLabels.addAll(prog.globals);
        for (Arm.Block block : prog.textBlocks) {
            for (Arm.Instr instr : block.instrs) {
                if (instr instanceof Arm.BInstr)
                    usedLabels.add(((Arm.BInstr) instr).label);
                else if (instr instanceof Arm.BLInstr)
                    usedLabels.add(((Arm.BLInstr) instr).label);
                else if (instr instanceof Arm.LdrLabelInstr)
                    usedLabels.add(((Arm.LdrLabelInstr) instr).label);
            }
        }

        // Remove unused labels by joining blocks
        ArrayList<Arm.Block> newBlocks = new ArrayList<>();
        newBlocks.add(prog.textBlocks.get(0));
        for (int i = 1; i < prog.textBlocks.size(); i++) {
            Arm.Block block = prog.textBlocks.get(i);
            if (usedLabels.contains(block.name)) {
                newBlocks.add(block);
            } else {
                // Join block
                Arm.Block lastBlock = newBlocks.get(newBlocks.size() - 1);
                lastBlock.instrs.addAll(block.instrs);
            }
        }
        prog.textBlocks.clear();
        prog.textBlocks.addAll(newBlocks);
    }
}
