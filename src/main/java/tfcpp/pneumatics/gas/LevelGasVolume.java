package tfcpp.pneumatics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import tfcpp.core.ServerConfig;

/**
 * Connects the simulation to a level. Gas never crosses into an unloaded chunk, so nothing is lost or duplicated when chunks unload: the gas simply waits.
 */
public final class LevelGasVolume implements GasVolume
{
    /** Fraction of the gas at an open position carried off by the atmosphere each second. */
    private static final float OPEN_AIR_EXCHANGE_RATE = 0.3f;

    private static final TagKey<Block> GLASS_PANES = Tags.Blocks.GLASS_PANES;

    private final ServerLevel level;
    private final GasTracker tracker;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelGasVolume(ServerLevel level, GasTracker tracker)
    {
        this.level = level;
        this.tracker = tracker;
    }

    @Nullable
    private LevelChunk chunk(int x, int z)
    {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4);
    }

    @Override
    @Nullable
    public float[] cell(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk == null || !chunk.hasData(GasAttachments.CHUNK))
        {
            return null;
        }
        return chunk.getData(GasAttachments.CHUNK).get(GasChunkData.key(x, y, z));
    }

    @Override
    @Nullable
    public float[] createCell(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk == null)
        {
            return null;
        }
        if (chunk.getData(GasAttachments.CHUNK).size() >= ServerConfig.GAS_CELLS_PER_CHUNK.getAsInt())
        {
            return null;
        }
        return createCell(chunk, x, y, z);
    }

    /**
     * @return the cell at this position, created if needed. For new gas entering the world: the cell budget limits how far gas spreads, and must not make it vanish.
     */
    @Nullable
    public float[] cellForEmission(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        return chunk == null ? null : createCell(chunk, x, y, z);
    }

    private float[] createCell(LevelChunk chunk, int x, int y, int z)
    {
        tracker.activate(chunk.getPos().toLong());
        chunk.setUnsaved(true);
        return chunk.getData(GasAttachments.CHUNK).getOrCreate(GasChunkData.key(x, y, z));
    }

    @Override
    public void markChanged(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk != null)
        {
            chunk.setUnsaved(true);
        }
    }

    @Override
    public void removeCell(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk != null && chunk.hasData(GasAttachments.CHUNK))
        {
            chunk.getData(GasAttachments.CHUNK).remove(GasChunkData.key(x, y, z));
            chunk.setUnsaved(true);
        }
    }

    @Override
    public void deposit(int x, int y, int z, Gas gas, float moles)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk != null)
        {
            chunk.getData(GasAttachments.CHUNK).addDeposit(gas, moles);
            chunk.setUnsaved(true);
        }
    }

    @Override
    public boolean isPassable(int x, int y, int z)
    {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight())
        {
            return false;
        }
        final LevelChunk chunk = chunk(x, z);
        if (chunk == null)
        {
            return false;
        }
        cursor.set(x, y, z);
        return isPassable(chunk.getBlockState(cursor), cursor);
    }

    private boolean isPassable(BlockState state, BlockPos pos)
    {
        if (state.isAir())
        {
            return true;
        }
        if (!state.getFluidState().isEmpty())
        {
            return false; // Gas does not displace liquid, this is also what makes water a seal
        }
        final Block block = state.getBlock();
        if (block instanceof DoorBlock)
        {
            return state.getValue(DoorBlock.OPEN);
        }
        if (block instanceof TrapDoorBlock)
        {
            return state.getValue(TrapDoorBlock.OPEN);
        }
        if (block instanceof SlabBlock || block instanceof StairBlock)
        {
            return false; // Not worth a cell of their own: a roof of slabs or stairs is a roof
        }
        return !state.is(GLASS_PANES) && !state.isCollisionShapeFullBlock(level, pos);
    }

    @Override
    public float atmosphereExchangeRate(int x, int y, int z)
    {
        return isOpenToSky(x, y, z) ? OPEN_AIR_EXCHANGE_RATE : 0;
    }

    /** Uses the heightmap and not sky light, which lags behind block changes. Leaves do not hold gas in. */
    public boolean isOpenToSky(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        return chunk != null && y >= chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    public int stepChunk(GasSimulator simulator, LevelChunk chunk, GasChunkData data, float seconds)
    {
        final int originX = chunk.getPos().getMinBlockX(), originZ = chunk.getPos().getMinBlockZ();
        final int[] keys = data.keys();
        for (int key : keys)
        {
            simulator.stepCell(this, originX + GasChunkData.keyX(key), GasChunkData.keyY(key), originZ + GasChunkData.keyZ(key), seconds);
        }
        if (keys.length > 0)
        {
            chunk.setUnsaved(true);
        }
        return keys.length;
    }
}
