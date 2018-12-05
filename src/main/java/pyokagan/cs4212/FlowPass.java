package pyokagan.cs4212;

import java.util.*;

/**
 * Builds basic blocks and flow graph.
 */
public class FlowPass {

    private ArrayList<Ir.LabelStmt> blockLabels = new ArrayList<>();
    private ArrayList<Ir.Stmt> blockStmts = new ArrayList<>();
    private HashSet<Ir.LabelStmt> usedLabels = new HashSet<>();
    private HashMap<Ir.LabelStmt, Ir.Block> labelToBlock = new HashMap<>();
    private ArrayList<Ir.Block> blocks = new ArrayList<>();
    private ArrayList<Ir.Block> blocksPre = new ArrayList<>();
    private ArrayList<Ir.Block> blocksPo = new ArrayList<>();
    private ArrayList<Ir.Block> blocksRpo = new ArrayList<>();
    private HashSet<Ir.Block> visited;
    private int blockIndex;
    private int postorderIndex;

    public static void run(Ir.Meth meth) {
        FlowPass fp = new FlowPass();

        // Do a pass to figure out which labels are actually used
        // as the target of jumps.
        for (Ir.Block block : meth.blocks) {
            for (Ir.Stmt stmt : block.stmts) {
                if (stmt instanceof Ir.JumpStmt) {
                    Ir.LabelStmt label = ((Ir.JumpStmt) stmt).label;
                    fp.usedLabels.add(label);
                }
            }
        }

        // Add a starting block
        // This is needed in the case where the method consists of only one while-loop.
        fp.makeBlock();

        // Split into basic blocks
        for (Ir.Block block : meth.blocks) {
            if (block.label != null)
                fp.doLabel(block.label);

            for (Ir.Stmt stmt : block.stmts) {
                if (stmt instanceof Ir.LabelStmt) {
                    fp.doLabel((Ir.LabelStmt) stmt);
                } else {
                    fp.blockStmts.add(stmt);

                    if (stmt instanceof Ir.CmpStmt || stmt instanceof Ir.GotoStmt || stmt instanceof Ir.ReturnStmt)
                        fp.makeBlock();
                }
            }
        }

        if (!fp.blockStmts.isEmpty())
            fp.makeBlock();

        // Wire up the outgoing edges
        for (int i = 0; i < fp.blocks.size(); i++) {
            Ir.Block block = fp.blocks.get(i);
            if (block.stmts.isEmpty()) {
                // Fallthrough to next block -- add a goto stmt which makes that explicit
                Ir.Block nextBlock = fp.blocks.get(i + 1);
                block.stmts.add(new Ir.GotoStmt(nextBlock.label));
                block.outgoingDirect = nextBlock;
                block.outgoingCond = null;
                continue;
            }

            Ir.Stmt lastStmt = block.stmts.get(block.stmts.size() - 1);
            if (lastStmt instanceof Ir.CmpStmt) {
                // Conditional goto
                Ir.Block nextBlock = fp.blocks.get(i + 1);
                Ir.Block targetBlock = fp.labelToBlock.get(((Ir.JumpStmt) lastStmt).label);
                block.outgoingDirect = nextBlock;
                block.outgoingCond = targetBlock;

                ((Ir.JumpStmt) lastStmt).label = targetBlock.label; // Fixup label
            } else if (lastStmt instanceof Ir.GotoStmt) {
                // Unconditional goto
                Ir.Block targetBlock = fp.labelToBlock.get(((Ir.GotoStmt) lastStmt).label);
                block.outgoingDirect = targetBlock;
                block.outgoingCond = null;

                ((Ir.JumpStmt) lastStmt).label = targetBlock.label; // Fixup label
            } else if (lastStmt instanceof Ir.ReturnStmt) {
                // Termination -- no outgoing edges
            } else {
                // Fallthrough to next block -- add a goto stmt which makes that explicit
                Ir.Block nextBlock = fp.blocks.get(i + 1);
                block.stmts.add(new Ir.GotoStmt(nextBlock.label));
                block.outgoingDirect = nextBlock;
                block.outgoingCond = null;
            }
        }

        // Compute postorder and check for dead blocks
        fp.visited = new HashSet<>();
        fp.postorderIndex = 0;
        if (!fp.blocks.isEmpty()) {
            fp.dfs(fp.blocks.get(0));

            for (int i = fp.blocksPo.size() - 1; i >= 0; i--)
                fp.blocksRpo.add(fp.blocksPo.get(i));
        }

        // Remove dead blocks
        ArrayList<Ir.Block> newBlocks = new ArrayList<Ir.Block>();
        for (Ir.Block block : fp.blocks) {
            if (fp.visited.contains(block))
                newBlocks.add(block);
        }
        fp.blocks = newBlocks;

        // Wire up the incoming edges
        for (Ir.Block block : fp.blocks) {
            if (block.outgoingDirect != null)
                block.outgoingDirect.incoming.add(block);

            if (block.outgoingCond != null)
                block.outgoingCond.incoming.add(block);
        }

        // Re-number blocks
        int blockIndex = 0;
        for (Ir.Block block : fp.blocks) {
            block.label.name = "B" + (blockIndex++);
        }

        meth.blocks = fp.blocks;
        meth.blocksPre = fp.blocksPre;
        meth.blocksPo = fp.blocksPo;
        meth.blocksRpo = fp.blocksRpo;
    }

    private void makeBlock() {
        Ir.Block block = new Ir.Block(blockStmts);
        // Map the block's labels to the block
        for (Ir.LabelStmt label : blockLabels) {
            labelToBlock.put(label, block);
        }
        // Create a label for the block
        block.label = new Ir.LabelStmt("B" + (blockIndex++));
        blocks.add(block);

        blockStmts = new ArrayList<>();
        blockLabels = new ArrayList<>();
    }

    private void doLabel(Ir.LabelStmt label) {
        // Ignore labels that are not actually used
        if (!usedLabels.contains(label))
            return;

        if (!blockStmts.isEmpty())
            makeBlock();

        blockLabels.add(label);
    }

    private void dfs(Ir.Block block) {
        visited.add(block);
        blocksPre.add(block);
        if (block.outgoingDirect != null && !visited.contains(block.outgoingDirect))
            dfs(block.outgoingDirect);
        if (block.outgoingCond != null && !visited.contains(block.outgoingCond))
            dfs(block.outgoingCond);
        blocksPo.add(block);
        block.postorderIndex = postorderIndex++;
    }
}
