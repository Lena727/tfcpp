package tfcpp.pneumatics.gas;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
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

    private final ChunkRotation rotation = new ChunkRotation();
    private final Long2ObjectMap<FireSource> fires = new Long2ObjectOpenHashMap<>();
    private final GasSimulator simulator = new GasSimulator();
    private final LongArrayFIFOQueue ignitions = new LongArrayFIFOQueue();

    private LevelGasVolume volume;
    private long firesLastPolled;
    private long parkedLastChecked;
    private long lastCalendarTick = Long.MIN_VALUE;

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
        rotation.activate(chunkPos);
    }

    public void deactivate(long chunkPos)
    {
        rotation.deactivate(chunkPos);
    }

    /** Lights the gas here at the end of this tick, once no cells are being walked. */
    public void queueIgnition(BlockPos pos)
    {
        ignitions.enqueue(pos.asLong());
    }

    public int activeChunkCount()
    {
        return rotation.activeCount();
    }

    /** @return how many chunks with gas are loaded but not ready, and wait to be looked at again */
    public int parkedChunkCount()
    {
        return rotation.parkedCount();
    }

    public ChunkRotation rotation()
    {
        return rotation;
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

    /**
     * Runs on the ticks of TFC's calendar, which stop on an empty server and jump forward when players sleep, and not on game time: gas in the chunks a server keeps loaded must not go on moving, and fires must not go on putting out smoke, while the fuel in them is not burning. A step that follows a long wait is cut to {@link #MAX_STEP_SECONDS}.
     */
    public void tick(ServerLevel level)
    {
        final long now = Calendars.SERVER.getTicks();
        final boolean calendarMoved = now != lastCalendarTick;
        lastCalendarTick = now;
        if (calendarMoved)
        {
            run(level, now);
        }

        while (!ignitions.isEmpty())
        {
            final BlockPos pos = BlockPos.of(ignitions.dequeueLong());
            GasIgnition.ignite(level, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    private void run(ServerLevel level, long now)
    {
        final LevelGasVolume volume = volume(level);
        if (now < firesLastPolled)
        {
            firesLastPolled = now; // The calendar was set back
        }
        if (now - firesLastPolled >= STEP_INTERVAL_TICKS)
        {
            firesLastPolled = now;
            FireSource.pollAll(level, volume, fires.values());
        }

        final boolean recheckParked = now < parkedLastChecked || now - parkedLastChecked >= STEP_INTERVAL_TICKS;
        if (recheckParked)
        {
            parkedLastChecked = now;
        }

        final int[] cellsLeft = {ServerConfig.GAS_CELLS_PER_TICK.getAsInt()};
        rotation.tick(
            recheckParked,
            ServerConfig.GAS_CHUNKS_PER_TICK.getAsInt(),
            chunkPos -> isReady(level, chunkPos),
            () -> cellsLeft[0] > 0,
            chunkPos -> stepChunk(level, volume, now, chunkPos, cellsLeft)
        );
    }

    /** @return whether the chunk is loaded as a full chunk now. One that has been demoted is not, though it has not been unloaded. */
    private static boolean isReady(ServerLevel level, long chunkPos)
    {
        return level.getChunkSource().getChunkNow(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos)) != null;
    }

    private ChunkRotation.Outcome stepChunk(ServerLevel level, LevelGasVolume volume, long now, long chunkPos, int[] cellsLeft)
    {
        final LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos));
        if (chunk == null)
        {
            return ChunkRotation.Outcome.KEEP; // Gone between the check and the visit
        }
        if (!chunk.hasData(GasAttachments.CHUNK))
        {
            return ChunkRotation.Outcome.DROP;
        }

        final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
        if (now < data.lastStepTick)
        {
            data.lastStepTick = now; // The calendar was set back: the wait starts again from here
            return ChunkRotation.Outcome.KEEP;
        }
        if (now - data.lastStepTick < data.stepIntervalTicks)
        {
            return ChunkRotation.Outcome.KEEP;
        }

        final float seconds = Math.min((now - data.lastStepTick) / 20f, MAX_STEP_SECONDS);
        data.lastStepTick = now;
        cellsLeft[0] -= volume.stepChunk(simulator, chunk, data, seconds);
        data.stepIntervalTicks = simulator.takeLargestFlow() < SETTLED_FLOW_MOLES
            ? Math.min(data.stepIntervalTicks * 2, MAX_STEP_INTERVAL_TICKS)
            : STEP_INTERVAL_TICKS;

        return data.isActive() ? ChunkRotation.Outcome.KEEP : ChunkRotation.Outcome.DROP;
    }
}
