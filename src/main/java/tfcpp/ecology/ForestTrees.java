package tfcpp.ecology;

import net.dries007.tfc.common.blocks.wood.Wood;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/**
 * Which trees are the sensitive ones: the single place that decides it. TFC marks its conifers itself ({@code Wood.isConifer}: douglas fir, pine, sequoia, spruce, white cedar) and this takes that flag, so that a wood added to TFC later is sorted by TFC's own word. A block is matched by the last part of its registry path ({@code tfc:wood/leaves/pine}, {@code tfc:wood/sapling/spruce}). The sensitivity itself is in {@link ForestHealth}: a conifer takes 10 ug/m3 as its critical level where a broadleaf takes 20.
 */
public final class ForestTrees
{
    private ForestTrees() {}

    /** What each block was found to be: the lookup by name is done once per block, not on every random tick. */
    private static final java.util.Map<Block, Boolean> CONIFERS = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isConifer(Block block)
    {
        return CONIFERS.computeIfAbsent(block, ForestTrees::lookUp);
    }

    private static boolean lookUp(Block block)
    {
        final String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        final String name = path.substring(path.lastIndexOf('/') + 1);
        for (Wood wood : Wood.VALUES)
        {
            if (wood.getSerializedName().equals(name))
            {
                return wood.isConifer();
            }
        }
        return false;
    }
}
