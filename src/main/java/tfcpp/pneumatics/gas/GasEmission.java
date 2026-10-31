package tfcpp.pneumatics.gas;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * How hot gas from a furnace enters the air. It rises as a plume until it hits a ceiling, then cools and spreads down from there, the way a smoke layer fills a room. Under open sky or up a chimney it is simply gone.
 */
public final class GasEmission
{
    public static final int SMOTHERED = Integer.MIN_VALUE;

    private static final int MAX_PLUME_RISE = 48;
    private static final int MAX_CHARGE_HEIGHT = 16;

    /** Releases gas from a device. If the device is sealed in solid blocks the gas stays in them, where it is released when a block is opened up. */
    public static void release(ServerLevel level, BlockPos device, float[] gases)
    {
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final int outlet = outletHeight(level, volume, device);
        addToPlume(volume, device.getX(), outlet == SMOTHERED ? device.getY() : outlet, device.getZ(), gases);
    }

    /**
     * @return the height where a device's gas comes out: up through its own burning charge and around any crucible or retort sitting on it, and at most one covering block, which is how a charcoal pit vents through its earth. Anything thicker counts as {@link #SMOTHERED}, so a device can never leak into a separate room above it.
     */
    public static int outletHeight(ServerLevel level, LevelGasVolume volume, BlockPos pos)
    {
        final BlockPos.MutableBlockPos cursor = pos.mutable();
        boolean usedCover = false;
        for (int rise = 0; rise < MAX_CHARGE_HEIGHT; rise++)
        {
            cursor.setY(pos.getY() + rise);
            final boolean passable = volume.isPassable(cursor.getX(), cursor.getY(), cursor.getZ());
            if (passable && !level.getBlockState(cursor.above()).is(TFCTags.Blocks.CHARCOAL_FORGE_INVISIBLE))
            {
                return cursor.getY();
            }
            final BlockState state = level.getBlockState(cursor);
            final Block block = state.getBlock();
            if (!passable && block != TFCBlocks.MOLTEN.get() && block != TFCBlocks.BURNING_LOG_PILE.get() && !state.is(TFCTags.Blocks.CHARCOAL_FORGE_INVISIBLE) && rise > 0)
            {
                if (usedCover)
                {
                    return SMOTHERED;
                }
                usedCover = true;
            }
        }
        return SMOTHERED;
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
