package pyokagan.cs4212;

/**
 * Converts jumps into fall-throughs.
 */
public class ArmFallthruOpt {
    public static void run(Arm.Prog prog) {
        for (int i = 0; i < prog.textBlocks.size() - 1; i++) {
            Arm.Block firstBlock = prog.textBlocks.get(i);
            Arm.Block secondBlock = prog.textBlocks.get(i+1);
            Arm.Instr lastInstr = firstBlock.instrs.get(firstBlock.instrs.size() - 1);
            if (!(lastInstr instanceof Arm.BInstr))
                continue;
            Arm.BInstr bInstr = (Arm.BInstr) lastInstr;
            if (!bInstr.label.equals(secondBlock.name) || bInstr.cond != Arm.Cond.AL)
                continue;
            firstBlock.instrs.remove(firstBlock.instrs.size() - 1);
        }
    }
}
