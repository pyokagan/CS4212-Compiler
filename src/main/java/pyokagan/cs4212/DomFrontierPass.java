package pyokagan.cs4212;

import java.util.*;

/**
 * Compute dom frontier.
 */
public class DomFrontierPass {
    private HashMap<Ir.Block, HashSet<Ir.Block>> domFrontiers = new HashMap<>();

    public DomFrontierPass(Ir.Meth meth, DomPass dom) {
        for (Ir.Block block : meth.blocks)
            domFrontiers.put(block, new HashSet<>());

        for (Ir.Block block : meth.blocks) {
            if (block.incoming.size() < 2)
                continue;

            Ir.Block topIdom = dom.getIdom(block);
            for (Ir.Block predecessor : block.incoming) {
                Ir.Block runner = predecessor;

                while (runner != topIdom) {
                    domFrontiers.get(runner).add(block);
                    runner = dom.getIdom(runner);
                }
            }
        }
    }

    public Set<Ir.Block> getDomFrontier(Ir.Block block) {
        return domFrontiers.get(block);
    }
}
