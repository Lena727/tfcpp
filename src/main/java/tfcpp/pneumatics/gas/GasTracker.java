package tfcpp.pneumatics.gas;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import tfcpp.core.ServerConfig;

/**
 * Schedules the gas of one level. Only chunks that actually hold gas are visited, each at most once per second, within a budget per tick. Without any gas in a level this costs nothing.
 */
public final class GasTracker
{
    private static final int STEP_INTERVAL_TICKS = 20;
    private static final float MAX_STEP_SECONDS = 5f;
    /** Air that has settled needs far fewer updates, so a chunk backs off to this interval while nothing is moving. */
    private static final int MAX_STEP_INTERVAL_TICKS = 100;
    private static final float SETTLED_FLOW_MOLES = 1e-3f;

    private final LongLinkedOpenHashSet activeChunks = new LongLinkedOpenHashSet();
    private final Long2ObjectMap<FireSource> fires = new Long2ObjectOpenHashMap<>();
    private final GasSimulator simulator = new GasSimulator();

    private LevelGasVolume volume;
    private long firesLastPolled;

    public static GasTracker get(ServerLevel level)
    {
        return level.getData(GasAttachments.TRACKER);
    }

    public LevelGasVolume volume(ServerLevel level)
    {
        if (volume == null)
        {
            volume = new LevelGasVolume(level, this);
        }
        return volume;
    }

    public void activate(long chunkPos)
    {
        activeChunks.add(chunkPos);
    }

    public void deactivate(long chunkPos)
    {
        activeChunks.remove(chunkPos);
    }

    public int activeChunkCount()
    {
        return activeChunks.size();
    }

    public int fireCount()
    {
        return fires.size();
    }

    public void onBlockEntityLoaded(BlockEntity entity)
    {
        final FireSource fire = FireSource.of(entity);
        if (fire != null)
        {
            fires.put(entity.getBlockPos().asLong(), fire);
        }
    }

    public void onBlockEntityUnloaded(BlockEntity entity)
    {
        final long key = entity.getBlockPos().asLong();
        final FireSource fire = fires.get(key);
        if (fire != null && fire.entity() == entity)
        {
            fires.remove(key);
        }
    }

    public void tick(ServerLevel level)
    {
        final LevelGasVolume volume = volume(level);
        final long tick = level.getGameTime();
        if (tick - firesLastPolled >= STEP_INTERVAL_TICKS)
        {
            firesLastPolled = tick;
            FireSource.pollAll(level, volume, fires.values());
        }

        int chunksLeft = Math.min(ServerConfig.GAS_CHUNKS_PER_TICK.getAsInt(), activeChunks.size());
        int cellsLeft = ServerConfig.GAS_CELLS_PER_TICK.getAsInt();
        while (chunksLeft-- > 0 && cellsLeft > 0)
        {
            final long chunkPos = activeChunks.firstLong();
            final LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos));
            if (chunk == null || !chunk.hasData(GasAttachments.CHUNK))
            {
                activeChunks.removeFirstLong();
                continue;
            }

            final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
            if (tick - data.lastStepTick < data.stepIntervalTicks)
            {
                activeChunks.addAndMoveToLast(chunkPos);
                continue;
            }

            final float seconds = Math.min((tick - data.lastStepTick) / 20f, MAX_STEP_SECONDS);
            data.lastStepTick = tick;
            cellsLeft -= volume.stepChunk(simulator, chunk, data, seconds);
            data.stepIntervalTicks = simulator.takeLargestFlow() < SETTLED_FLOW_MOLES
                ? Math.min(data.stepIntervalTicks * 2, MAX_STEP_INTERVAL_TICKS)
                : STEP_INTERVAL_TICKS;

            activeChunks.removeFirstLong();
            if (!data.isEmpty())
            {
                activeChunks.addAndMoveToLast(chunkPos);
            }
        }
    }
}
