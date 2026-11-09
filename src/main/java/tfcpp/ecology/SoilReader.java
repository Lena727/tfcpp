package tfcpp.ecology;

import java.util.HashMap;
import java.util.Map;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.settings.RockSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Reads what a chunk's soil starts as from TFC: the soil variant of the surface at the centre of the chunk, and whether the rock under it is a carbonate.
 */
public final class SoilReader
{
    /** The soil order that stands for a chunk with no soil at its surface: the middle of the road, young soil near neutral. */
    public static final SoilOrder DEFAULT_ORDER = SoilOrder.ENTISOL;
    /** How far below the surface to look for soil, in blocks: under sand, snow, trees, buildings and the like. */
    private static final int SEARCH_DEPTH = 32;

    private static @Nullable Map<Block, SoilOrder> orders;

    private SoilReader() {}

    private static Map<Block, SoilOrder> orders()
    {
        if (orders == null)
        {
            final Map<Block, SoilOrder> map = new HashMap<>();
            for (SoilBlockType type : SoilBlockType.VALUES)
            {
                TFCBlocks.SOIL.get(type).forEach((variant, id) ->
                {
                    final @Nullable SoilOrder order = SoilOrder.byName(variant.name());
                    if (order != null)
                    {
                        map.put(id.get(), order);
                    }
                });
            }
            orders = map;
        }
        return orders;
    }

    /** @return the soil order of the block, or {@code null} if it is not a soil of TFC's */
    @Nullable
    public static SoilOrder orderOf(Block block)
    {
        return orders().get(block);
    }

    /** @return the surface soil at the centre of this chunk, or {@link #DEFAULT_ORDER} if there is none within reach of the surface */
    public static SoilOrder surfaceOrder(ServerLevel level, ChunkPos chunk)
    {
        final int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
        final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int depth = 0; depth < SEARCH_DEPTH && pos.getY() >= level.getMinBuildHeight(); depth++)
        {
            final BlockState state = level.getBlockState(pos);
            final @Nullable SoilOrder order = state.is(BlockTags.LOGS) ? null : orderOf(state.getBlock()); // Under the wood of a tree, not in it
            if (order != null)
            {
                return order;
            }
            pos.move(0, -1, 0);
        }
        return DEFAULT_ORDER;
    }

    /** @return {@code true} if the rock at the surface at the centre of this chunk, which TFC's chunk data holds, is limestone, dolomite, chalk or marble. A chunk whose data is not complete is taken as not carbonate */
    public static boolean carbonateBedrock(LevelChunk chunk)
    {
        final ChunkData data = ChunkData.get(chunk);
        if (data.status() != ChunkData.Status.FULL)
        {
            return false;
        }
        final ChunkPos pos = chunk.getPos();
        return isCarbonate(data.getRockData().getSurfaceRock(pos.getMinBlockX() + 8, pos.getMinBlockZ() + 8));
    }

    /** @return {@code true} if the rock is one of the carbonates of TFC, found by its raw block */
    public static boolean isCarbonate(RockSettings rock)
    {
        for (Rock carbonate : new Rock[] {Rock.LIMESTONE, Rock.DOLOMITE, Rock.CHALK, Rock.MARBLE})
        {
            if (rock.raw() == TFCBlocks.ROCK_BLOCKS.get(carbonate).get(Rock.BlockType.RAW).get())
            {
                return true;
            }
        }
        return false;
    }

    /** @return the share of the 256 surface columns of a loaded chunk whose top block (by the MOTION_BLOCKING_NO_LEAVES heightmap) is not fluid */
    public static double landFraction(LevelChunk chunk)
    {
        int land = 0;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                // The heightmap says where the top block is; blocks put there since it was last brought up to date (a loaded chunk is kept so by the game, but not one that is being built in a test) are walked over
                int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int up = 0; up < 8 && !chunk.getBlockState(pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z)).isAir(); up++)
                {
                    y++;
                }
                pos.set(chunk.getPos().getMinBlockX() + x, y - 1, chunk.getPos().getMinBlockZ() + z);
                if (chunk.getFluidState(pos).isEmpty())
                {
                    land++;
                }
            }
        }
        return land / 256.0;
    }

    /** @return the soil of a chunk as it was before anything was put into it */
    public static SoilCell pristine(ServerLevel level, LevelChunk chunk, long nowTick)
    {
        final SoilCell cell = SoilCell.fresh(surfaceOrder(level, chunk.getPos()), carbonateBedrock(chunk), nowTick);
        cell.setLandFraction(landFraction(chunk));
        return cell;
    }
}
