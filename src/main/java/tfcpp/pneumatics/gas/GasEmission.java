package tfcpp.pneumatics.gas;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * How hot gas from a furnace enters the air. It rises as a plume until it hits a ceiling, then cools and spreads down from there, the way a smoke layer fills a room. Under open sky or up a chimney it is simply gone.
 */
public final class GasEmission
{
    public static final int SMOTHERED = Integer.MIN_VALUE;
    /** The share of a smothered device's gas that gets through its cover. The turf on a charcoal pit is not airtight, which is why a pit stinks of smoke for its whole burn, and why pits have killed people who slept beside them. */
    public static final float LEAK_SHARE = 0.1f;

    private static final int MAX_PLUME_RISE = 48;
    private static final int MAX_CHARGE_HEIGHT = 16;

    /** Releases gas from a device. If the device is sealed in solid blocks the gas stays in them, where it is released when a block is opened up. */
    public static void release(ServerLevel level, BlockPos device, float[] gases)
    {
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final int outlet = outletHeight(level, volume, device);
        if (outlet != SMOTHERED)
        {
            addToPlume(volume, device.getX(), outlet, device.getZ(), gases);
            return;
        }

        // Under a cover that is too thick, most of it stays where it is, and what leaks through comes out above the cover
        final int leak = leakHeight(level, volume, device);
        if (leak == SMOTHERED)
        {
            addToPlume(volume, device.getX(), device.getY(), device.getZ(), gases);
            return;
        }
        final float[] leaked = new float[Gas.COUNT];
        final float[] kept = new float[Gas.COUNT];
        for (int i = 0; i < Gas.COUNT; i++)
        {
            leaked[i] = gases[i] * LEAK_SHARE;
            kept[i] = gases[i] - leaked[i];
        }
        addToPlume(volume, device.getX(), leak, device.getZ(), leaked);
        addToPlume(volume, device.getX(), device.getY(), device.getZ(), kept);
    }

    /**
     * @return the height where a device's gas comes out: up through its own burning charge and around any crucible or retort sitting on it, and at most one covering block, which is how a charcoal pit vents through its earth. Anything thicker counts as {@link #SMOTHERED}, so a device can never send all of its gas into a separate room above it. For what leaks through such a cover see {@link #leakHeight}.
     */
    public static int outletHeight(ServerLevel level, LevelGasVolume volume, BlockPos pos)
    {
        return scan(level, volume, pos, false);
    }

    /**
     * @return the height of the first open position above a cover that is too thick for {@link #outletHeight}, within the same reach, or {@link #SMOTHERED} if there is none. {@link #LEAK_SHARE} of the gas of a smothered device comes out there: a charcoal pit's turf leaks. Nothing comes out if there is no open position to come out into.
     */
    public static int leakHeight(ServerLevel level, LevelGasVolume volume, BlockPos pos)
    {
        return scan(level, volume, pos, true);
    }

    private static int scan(ServerLevel level, LevelGasVolume volume, BlockPos pos, boolean pastCover)
    {
        // Asked of the chunk that is loaded and not of the level: the level would load a chunk that is not ready, and a device left in a demoted chunk would keep bringing it back up
        final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null)
        {
            return SMOTHERED;
        }
        final BlockPos.MutableBlockPos cursor = pos.mutable();
        boolean usedCover = false, smothered = false;
        for (int rise = 0; rise < MAX_CHARGE_HEIGHT; rise++)
        {
            cursor.setY(pos.getY() + rise);
            final boolean passable = volume.isPassable(cursor.getX(), cursor.getY(), cursor.getZ());
            if (passable && !stateAt(level, chunk, cursor.getX(), cursor.getY() + 1, cursor.getZ()).is(TFCTags.Blocks.CHARCOAL_FORGE_INVISIBLE))
            {
                return pastCover == smothered ? cursor.getY() : SMOTHERED;
            }
            if (smothered)
            {
                continue; // Already under too much cover: all that matters now is the first open position above it
            }
            final BlockState state = stateAt(level, chunk, cursor.getX(), cursor.getY(), cursor.getZ());
            final Block block = state.getBlock();
            if (!passable && block != TFCBlocks.MOLTEN.get() && block != TFCBlocks.BURNING_LOG_PILE.get() && !state.is(TFCTags.Blocks.CHARCOAL_FORGE_INVISIBLE) && rise > 0)
            {
                if (usedCover)
                {
                    if (!pastCover)
                    {
                        return SMOTHERED;
                    }
                    smothered = true;
                }
                usedCover = true;
            }
        }
        return SMOTHERED;
    }

    /** The block at a height in the column of a chunk. Out of the world it is air, as it is for the level. */
    private static BlockState stateAt(ServerLevel level, LevelChunk chunk, int x, int y, int z)
    {
        return y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight() ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(new BlockPos(x, y, z));
    }

    public static void addToPlume(LevelGasVolume volume, int x, int fromY, int z, float[] gases)
    {
        int y = fromY;
        for (int rise = 0; rise < MAX_PLUME_RISE; rise++)
        {
            if (volume.isOpenToSky(x, y, z))
            {
                return; // Goes straight into the atmosphere, without creating any cells
            }
            if (!volume.isPassable(x, y + 1, z))
            {
                break;
            }
            y++;
        }

        final @Nullable float[] cell = volume.cellForEmission(x, y, z);
        if (cell != null)
        {
            for (int i = 0; i < Gas.COUNT; i++)
            {
                cell[i] += gases[i];
            }
        }
    }

    private GasEmission() {}
}
