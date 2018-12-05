package pyokagan.cs4212;

import java.util.*;

/**
 * Computes dom information for a method.
 *
 * A Simple, Fast Dominance Algorithm
 * https://www.cs.rice.edu/~keith/EMBED/dom.pdf
 */
public class DomPass {
    // Mapping of block to idom block
    private HashMap<Ir.Block, Ir.Block> idom = new HashMap<>();

    // Mapping of block to its children in dom tree
    private HashMap<Ir.Block, ArrayList<Ir.Block>> idomChilds = new HashMap<>();

    // Traversal of dom tree in pre-order
    private ArrayList<Ir.Block> idomPreorder = new ArrayList<>();

    public DomPass(Ir.Meth meth) {
        if (meth.blocks.isEmpty())
            return;

        Ir.Block firstBlock = meth.blocks.get(0);
        idom.put(firstBlock, null);

        boolean changed = true;
        while (changed) {
            changed = false;

            for (Ir.Block block : meth.blocksRpo) {
                if (block == firstBlock)
                    continue;

                Ir.Block new_idom = null;
                for (Ir.Block otherIncoming : block.incoming) {
                    if (!idom.containsKey(otherIncoming))
                        continue;

                    if (new_idom == null)
                        new_idom = otherIncoming;
                    else
                        new_idom = idomIntersect(otherIncoming, new_idom);
                }

                if (!idom.containsKey(block) || idom.get(block) != new_idom) {
                    idom.put(block, new_idom);
                    changed = true;
                }
            }
        }

        // Compute child list
        for (Ir.Block block : meth.blocks)
            idomChilds.put(block, new ArrayList<>());
        for (Ir.Block block : meth.blocks) {
            Ir.Block parent = idom.get(block);
            if (parent != null)
                idomChilds.get(parent).add(block);
        }

        // Compute idom tree traversal in preorder
        HashSet<Ir.Block> visited = new HashSet<>();
        idomDfs(meth.blocks.get(0), visited);
    }

    private Ir.Block idomIntersect(Ir.Block b1, Ir.Block b2) {
        Ir.Block finger1 = b1;
        Ir.Block finger2 = b2;
        while (finger1 != finger2) {
            if (finger2 == null)
                finger1 = idom.get(finger1);
            else if (finger1 == null)
                finger2 = idom.get(finger2);
            else if (finger1.postorderIndex < finger2.postorderIndex)
                finger1 = idom.get(finger1);
            else if (finger2.postorderIndex < finger1.postorderIndex)
                finger2 = idom.get(finger2);
        }
        return finger1;
    }

    private void idomDfs(Ir.Block block, HashSet<Ir.Block> visited) {
        visited.add(block);
        idomPreorder.add(block);
        for (Ir.Block child : idomChilds.get(block)) {
            if (!visited.contains(child))
                idomDfs(child, visited);
        }
    }

    /**
     * Returns true if a dominates b
     */
    public boolean isDominate(Ir.Block a, Ir.Block b) {
        while (b != null) {
            if (a == b)
                return true;
            b = getIdom(b);
        }
        return false;
    }

    /**
     * Returns the immediate dominator of block, or null if it is the root of the dom tree.
     */
    public Ir.Block getIdom(Ir.Block block) {
        return idom.get(block);
    }

    /**
     * Returns traversal of the idom tree in preorder.
     */
    public List<Ir.Block> getPreorder() {
        return Collections.unmodifiableList(idomPreorder);
    }
}
