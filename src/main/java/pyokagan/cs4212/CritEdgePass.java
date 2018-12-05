package pyokagan.cs4212;

import java.util.*;

/**
 * Breaks critical edges.
 */
public class CritEdgePass {
    private boolean modified; // Set to true if the CFG was modified
    private ArrayList<Ir.Block> blocksPre = new ArrayList<>();
    private ArrayList<Ir.Block> blocksPo = new ArrayList<>();
    private ArrayList<Ir.Block> blocksRpo = new ArrayList<>();
    private HashSet<Ir.Block> visited;
    private int postorderIndex;

    private CritEdgePass(Ir.Meth meth) {
        ArrayList<Ir.Block> newBlocks = new ArrayList<>();
        int blockIndex = meth.blocks.size();
        for (Ir.Block block : meth.blocks) {
            if (block.outgoingCond == null)
                continue; // We only care about outgoingCond
            if (block.outgoingCond.incoming.size() < 2)
                continue; // Not a critical edge

            Ir.Block nextBlock = block.outgoingCond;
            int nextBlockIncomingIdx = nextBlock.incoming.indexOf(block);

            // Break the critical edge by inserting a block in between
            Ir.Block newBlock = new Ir.Block(Arrays.asList(new Ir.GotoStmt(nextBlock.label)));
            newBlock.label = new Ir.LabelStmt("B" + (blockIndex++));
            newBlock.outgoingDirect = nextBlock;
            newBlock.outgoingCond = null;
            newBlock.incoming.add(block);

            // Wire them up
            block.outgoingCond = newBlock;
            Ir.JumpStmt jumpStmt = (Ir.JumpStmt) block.stmts.get(block.stmts.size() - 1);
            jumpStmt.label = newBlock.label;
            nextBlock.incoming.set(nextBlockIncomingIdx, newBlock);
            newBlocks.add(newBlock);
        }
        meth.blocks.addAll(newBlocks);

        if (newBlocks.isEmpty())
            return; // Nothing else to be done

        modified = true;

        // Re-compute preorder, postorder, reverse postorder
        visited = new HashSet<>();
        postorderIndex = 0;
        if (!meth.blocks.isEmpty()) {
            dfs(meth.blocks.get(0));

            for (int i = blocksPo.size() - 1; i >= 0; i--)
                blocksRpo.add(blocksPo.get(i));
        }

        // Check for dead blocks
        for (Ir.Block block : meth.blocks) {
            if (!visited.contains(block))
                throw new AssertionError(block + " died after CritEdgePass!");
        }

        meth.blocksPre = blocksPre;
        meth.blocksPo = blocksPo;
        meth.blocksRpo = blocksRpo;
    }

    public static boolean run(Ir.Meth meth) {
        CritEdgePass p = new CritEdgePass(meth);
        return p.modified;
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
