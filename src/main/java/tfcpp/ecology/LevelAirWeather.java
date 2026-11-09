package tfcpp.ecology;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.ClimateModel;
import net.dries007.tfc.util.tracker.WeatherHelpers;
import net.dries007.tfc.util.tracker.WorldTracker;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sky as TFC has it, asked without loading or generating a chunk and without building anything on the main thread.
 * <p>
 * <strong>Wind.</strong> TFC's wind is drawn from a generator seeded by the calendar day, then changed by the altitude, by whether it rains at the position, and, if it is strong, by one more random number that depends on the rain, so it is not exactly one wind for the whole map. Reading it asks the chunk at the position for its rainfall and biome, which loads the chunk, so it is asked only at a loaded position. Once for each calendar day, before any cell steps that day, it is read at the position of an online player, or at the spawn if no player is on or their chunk is not loaded, and that is the wind of the day for every cell that has no loaded anchor of its own. A cell that does have one (the place its smoke last came out) reads the wind there. TFC's wind is a surface wind, and smoke is carried through the mixing layer, so it is multiplied by {@link AirScale#MIXED_LAYER_WIND_FACTOR}.
 * <p>
 * <strong>Rain.</strong> Whether it precipitates at a place is TFC's {@code isPrecipitating(rain intensity, rainfall)}. The intensity is the same for the whole world and a pure function of the calendar tick. The rainfall of a place needs the chunk's data, and TFC's own call loads the chunk. So the average rainfall and its yearly variance of a cell are taken, in this order, from the data of a chunk of the cell that is already loaded (free), or else worked out by TFC's chunk data generator, a pure function of the seed that may have to build a region of the world, on a thread of its own. Until that comes back the cell is dry. The value is kept in the cell and saved with it. At the hour the rainfall comes from TFC's own formula. A world whose climate has no simulated rain (not TFC's) has no rain here.
 */
public final class LevelAirWeather implements AirWeather
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LevelAirWeather.class);
    private static final long TICKS_PER_DAY = ICalendar.CALENDAR_TICKS_IN_DAY;
    /** Most rainfall lookups waiting for the other thread at once. A cell that finds the queue full is dry this hour, and asks again at the next. */
    private static final int MAX_PENDING = 64;
    private static final int CHUNKS_IN_CELL = (int) AirScale.CELL_BLOCKS / 16;
    private static @Nullable ExecutorService executor;

    private record Result(int cellX, int cellZ, float average, float variance)
    {
    }

    private final ServerLevel level;
    private final ConcurrentLinkedQueue<Result> arrived = new ConcurrentLinkedQueue<>();
    private final LongOpenHashSet pending = new LongOpenHashSet();
    /** Cells that the waters ask the rain of and that are not in the air grid: kept here with their rainfall, so that a river that no smoke has touched can be asked too. */
    private final Long2ObjectOpenHashMap<AirGrid.Cell> rainCells = new Long2ObjectOpenHashMap<>();
    private volatile boolean generatorFailed;
    private double lastX, lastZ, dayX, dayZ;
    private long lastDay = Long.MIN_VALUE, preparedDay = Long.MIN_VALUE;
    private boolean dayValid;

    public LevelAirWeather(ServerLevel level)
    {
        this.level = level;
    }

    private static synchronized ExecutorService executor()
    {
        if (executor == null)
        {
            executor = Executors.newSingleThreadExecutor(runnable ->
            {
                final Thread thread = new Thread(runnable, "tfcpp-rainfall");
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }

    /** @return the temperature scale of the world, or 0 if it has none that TFC reads */
    public static int temperatureScale(ServerLevel level)
    {
        final ChunkGenerator generator = level.getChunkSource().getGenerator();
        return generator instanceof ChunkGeneratorExtension extension ? extension.settings().temperatureScale() : 0;
    }

    /** Takes in the rainfalls that the other thread has finished. Called on the main thread, before the cells step. */
    public void collect(AirGrid grid)
    {
        @Nullable Result result;
        while ((result = arrived.poll()) != null)
        {
            pending.remove(AirGrid.key(result.cellX(), result.cellZ()));
            final @Nullable AirGrid.Cell cell = grid.cellAt(result.cellX(), result.cellZ());
            final @Nullable AirGrid.Cell kept = rainCells.get(AirGrid.key(result.cellX(), result.cellZ()));
            if (!Float.isNaN(result.average()))
            {
                if (cell != null)
                {
                    cell.setRain(result.average(), result.variance());
                }
                if (kept != null)
                {
                    kept.setRain(result.average(), result.variance());
                }
            }
        }
    }

    @Override
    public void sample(AirGrid.Cell cell, long tick, Sample out)
    {
        final long day = Math.floorDiv(tick, TICKS_PER_DAY);
        if (day != preparedDay)
        {
            prepareDay(day, tick);
        }
        windInto(cell, day, tick, out);
        final float intensity = precipitation(cell, tick);
        out.raining = intensity > 0f;
        out.rainIntensity = intensity;
    }

    @Override
    public void sampleRain(int cellX, int cellZ, long tick, Sample out)
    {
        final AirGrid.Cell cell = rainCells.computeIfAbsent(AirGrid.key(cellX, cellZ), key -> new AirGrid.Cell(cellX, cellZ));
        final float intensity = precipitation(cell, tick);
        out.raining = intensity > 0f;
        out.rainIntensity = intensity;
    }

    /** Reads the wind of a new day once, at the position of an online player or else at the spawn, if its chunk is loaded. */
    private void prepareDay(long day, long tick)
    {
        preparedDay = day;
        dayValid = false;
        @Nullable BlockPos at = null;
        for (ServerPlayer player : level.players())
        {
            final BlockPos pos = player.blockPosition();
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null)
            {
                at = pos;
                break;
            }
        }
        if (at == null)
        {
            final BlockPos spawn = level.getSharedSpawnPos();
            if (level.getChunkSource().getChunkNow(spawn.getX() >> 4, spawn.getZ() >> 4) != null)
            {
                at = spawn;
            }
        }
        if (at != null)
        {
            final double[] wind = windAt(at.getX(), at.getZ(), tick);
            dayX = wind[0];
            dayZ = wind[1];
            lastX = dayX;
            lastZ = dayZ;
            lastDay = day;
            dayValid = true;
        }
    }

    /** @return the wind of TFC at a loaded column, at the height of the ground there, in metres per second at the height that smoke is carried at */
    private double[] windAt(int x, int z, long tick)
    {
        final LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        final int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        final Vec2 wind = Climate.get(level).getWind(level, new BlockPos(x, y, z), tick, Calendars.SERVER.getCalendarDaysInMonth());
        final double scale = AirScale.WIND_STRENGTH_TO_METRES_PER_SECOND * AirScale.MIXED_LAYER_WIND_FACTOR;
        return new double[] {wind.x * scale, wind.y * scale};
    }

    private void windInto(AirGrid.Cell cell, long day, long tick, Sample out)
    {
        boolean found = false;
        if (cell.hasAnchor() && level.getChunkSource().getChunkNow(cell.anchorX() >> 4, cell.anchorZ() >> 4) != null)
        {
            final double[] wind = windAt(cell.anchorX(), cell.anchorZ(), tick);
            cell.setWind(wind[0], wind[1], day);
            found = true;
        }
        if (!found)
        {
            if (dayValid)
            {
                cell.setWind(dayX, dayZ, day);
            }
            else if (!cell.hasWind() && lastDay != Long.MIN_VALUE)
            {
                cell.setWind(lastX, lastZ, lastDay); // Nothing could be asked today: the last wind that was found
            }
        }
        out.windX = cell.hasWind() ? cell.windX() : 0.0;
        out.windZ = cell.hasWind() ? cell.windZ() : 0.0;
    }

    /** @return TFC's rain intensity at the place, 0 to 1 (the intensity of the storm less what the rainfall of the place takes off it), if it is precipitating there; 0 if it is not, or cannot be told yet */
    private float precipitation(AirGrid.Cell cell, long tick)
    {
        final ClimateModel model = Climate.get(level);
        if (!model.supportsRain() || !WorldTracker.get(level).isWeatherEnabled())
        {
            return 0f;
        }
        final float intensity = model.getRain(tick);
        if (!(intensity > 0f))
        {
            return 0f; // The same for the whole world, and the cheap answer nearly all of the time
        }
        if (!cell.hasRain() && !findInLoadedChunk(cell))
        {
            request(cell);
            return 0f;
        }
        return Math.max(0f, WeatherHelpers.calculateRealRainIntensity(intensity, instantRainfall(cell.rainAverage(), cell.rainVariance(), tick)));
    }

    /** TFC's own formula for the rainfall at a time of the year, from the average and the variance of a place. */
    public static float instantRainfall(float average, float variance, long tick)
    {
        final float fractionOfYear = ICalendar.getFractionOfYear(tick, Calendars.SERVER.getCalendarDaysInMonth());
        return variance == 0f ? average : Helpers.triangle(variance * average, average, 1f, fractionOfYear + 0.75f);
    }

    /** @return {@code true} if a chunk of the cell is loaded and has its climate, and the cell now has the rainfall of the one nearest to its centre */
    private boolean findInLoadedChunk(AirGrid.Cell cell)
    {
        if (!(level.getChunkSource().getGenerator() instanceof ChunkGeneratorExtension))
        {
            return false;
        }
        final int firstChunkX = cell.x * CHUNKS_IN_CELL, firstChunkZ = cell.z * CHUNKS_IN_CELL;
        @Nullable LevelChunk best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = 0; dx < CHUNKS_IN_CELL; dx++)
        {
            for (int dz = 0; dz < CHUNKS_IN_CELL; dz++)
            {
                final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(firstChunkX + dx, firstChunkZ + dz);
                if (chunk == null)
                {
                    continue;
                }
                final ChunkData data = ChunkData.get(chunk);
                if (data.status() != ChunkData.Status.PARTIAL && data.status() != ChunkData.Status.FULL)
                {
                    continue;
                }
                final double distance = Math.pow(dx - (CHUNKS_IN_CELL - 1) / 2.0, 2) + Math.pow(dz - (CHUNKS_IN_CELL - 1) / 2.0, 2);
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    best = chunk;
                }
            }
        }
        if (best == null)
        {
            return false;
        }
        final ChunkData data = ChunkData.get(best);
        final int x = best.getPos().getMinBlockX() + 8, z = best.getPos().getMinBlockZ() + 8;
        cell.setRain(data.getAverageRainfall(x, z), data.getRainVariance(x, z));
        return true;
    }

    /** Asks the other thread for the rainfall of a cell, if it is not asked for already. */
    private void request(AirGrid.Cell cell)
    {
        if (generatorFailed || pending.size() >= MAX_PENDING || !(level.getChunkSource().getGenerator() instanceof ChunkGeneratorExtension extension))
        {
            return;
        }
        if (!pending.add(AirGrid.key(cell.x, cell.z)))
        {
            return;
        }
        final int cellX = cell.x, cellZ = cell.z;
        final int blockX = cellX * (int) AirScale.CELL_BLOCKS + (int) AirScale.CELL_BLOCKS / 2, blockZ = cellZ * (int) AirScale.CELL_BLOCKS + (int) AirScale.CELL_BLOCKS / 2;
        executor().execute(() ->
        {
            try
            {
                final ChunkData data = extension.chunkDataGenerator().createAndGenerate(new ChunkPos(blockX >> 4, blockZ >> 4));
                arrived.add(new Result(cellX, cellZ, data.getAverageRainfall(blockX, blockZ), data.getRainVariance(blockX, blockZ)));
            }
            catch (RuntimeException e)
            {
                LOGGER.warn("Could not read the rainfall of the world's generator, regional air will not rain", e);
                generatorFailed = true;
                arrived.add(new Result(cellX, cellZ, Float.NaN, Float.NaN));
            }
        });
    }
}
