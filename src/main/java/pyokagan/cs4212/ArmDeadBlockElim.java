package pyokagan.cs4212;

import java.util.*;

/**
 * Eliminates blocks that are not exported and never jumped to.
 */
public class ArmDeadBlockElim {
    private HashMap<String, Arm.Block> nameToBlock = new HashMap<>();
    private HashMap<Arm.Block, HashSet<Arm.Block>> outgoing = new HashMap<>();
    private HashSet<Arm.Block> visited = new HashSet<>();

    public static void run(Arm.Prog prog) {
        ArmDeadBlockElim p = new ArmDeadBlockElim();

        // Build nameToBlock
        for (Arm.Block block : prog.textBlocks) {
            p.nameToBlock.put(block.name, block);
        }
        for (Arm.Block block : prog.dataBlocks) {
            p.nameToBlock.put(block.name, block);
        }

        // Build outgoing edge adj list
        for (Arm.Block block : prog.textBlocks) {
            p.doBlock(block);
        }
        for (Arm.Block block : prog.dataBlocks) {
            p.doBlock(block);
        }

        // DFS time
        for (String glob : prog.globals) {
            if (!p.nameToBlock.containsKey(glob))
                continue;
            Arm.Block block = p.nameToBlock.get(glob);
            if (p.visited.contains(block))
                continue;
            p.dfs(block);
        }

        // Build new block list without dead blocks
        ArrayList<Arm.Block> newBlocks = new ArrayList<>();
        for (Arm.Block block : prog.textBlocks) {
            if (!p.visited.contains(block))
                continue;
            newBlocks.add(block);
        }
        prog.textBlocks.clear();
        prog.textBlocks.addAll(newBlocks);

        newBlocks.clear();
        for (Arm.Block block : prog.dataBlocks) {
            if (!p.visited.contains(block))
                continue;
            newBlocks.add(block);
        }
        prog.dataBlocks.clear();
        prog.dataBlocks.addAll(newBlocks);
    }

    private void doBlock(Arm.Block block) {
        HashSet<Arm.Block> blockOutgoing = new HashSet<>();
        outgoing.put(block, blockOutgoing);
        for (Arm.Instr instr : block.instrs) {
            if (instr instanceof Arm.BInstr) {
                Arm.BInstr bInstr = (Arm.BInstr) instr;
                if (nameToBlock.containsKey(bInstr.label)) {
                    Arm.Block dst = nameToBlock.get(bInstr.label);
                    blockOutgoing.add(dst);
                }
            } else if (instr instanceof Arm.BLInstr) {
                Arm.BLInstr blInstr = (Arm.BLInstr) instr;
                if (nameToBlock.containsKey(blInstr.label)) {
                    Arm.Block dst = nameToBlock.get(blInstr.label);
                    blockOutgoing.add(dst);
                }
            } else if (instr instanceof Arm.LdrLabelInstr) {
                Arm.LdrLabelInstr ldrLabelInstr = (Arm.LdrLabelInstr) instr;
                if (nameToBlock.containsKey(ldrLabelInstr.label)) {
                    Arm.Block dst = nameToBlock.get(ldrLabelInstr.label);
                    blockOutgoing.add(dst);
                }
            }
        }
    }

    private void dfs(Arm.Block x) {
        visited.add(x);
        for (Arm.Block dst : outgoing.get(x)) {
            if (!visited.contains(dst))
                dfs(dst);
        }
    }
}
