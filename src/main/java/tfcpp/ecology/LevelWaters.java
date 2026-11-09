package tfcpp.ecology;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.biome.BiomeSourceExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tfcpp.core.TickProfiler;

/**
 * The fresh waters of one level, saved with it: what rivers and lakes carry of what is put into them. See {@link WaterLedger} for the transport and {@link WaterConstants} for the numbers.
 * <p>
 * Nothing here ticks a block, loads a chunk to ask a question, or builds a region of TFC's world on the main thread. Which river is nearest to a chunk is TFC's graph, and asking for it may make TFC build a region, so it is worked out on a daemon thread of its own and handed back through a queue, as the rainfall of the regional air is. The answer is a pure function of the seed and is kept with the chunk ({@link WaterChunkData}). Until it is there, what is put into the water of that place waits in a ledger of pending inputs, counted as put in and kept with the world, and goes in when the answer arrives.
 * <p>
 * A place is a chunk, taken at its middle: the nearest edge of the river graph to the middle of the chunk is the river that the chunk's water belongs to, and the distance to it decides whether the chunk drains to it.
 */
public final class LevelWaters extends SavedData
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LevelWaters.class);
    private static final String NAME = "tfcpp_waters";
    private static final Factory<LevelWaters> FACTORY = new Factory<>(LevelWaters::new, LevelWaters::load);
    /** Most questions waiting for the other thread at once. */
    private static final int MAX_IN_FLIGHT = 64;
    private static @Nullable ExecutorService sharedExecutor;
    /** Set when the server stops: a question that is still queued then is not asked, and so no closed world's generator is used. */
    private static volatile boolean closed;

    /** What is known about the water near one chunk. */
    private record Found(long edgeId, double distance)
    {
    }

    private final WaterLedger ledger = new WaterLedger();
    /** The ids of the reaches that the ledger knows, readable from the other thread: it stops its walk down a river at the first of them. */
    private final Set<Long> knownIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Resolution> arrived = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> failed = new ConcurrentLinkedQueue<>();
    private final LongOpenHashSet inFlight = new LongOpenHashSet();
    /** Chunks whose question failed, with the calendar hour from which to ask again. */
    private final Long2LongOpenHashMap retryAfter = new Long2LongOpenHashMap();
    private final LongOpenHashSet loggedSoil = new LongOpenHashSet();
    /** Chunks whose soil holds something and drains to water, or may: the waters bring them up to the calendar once a day, so that runoff does not wait for a touch. */
    private final LongOpenHashSet watched = new LongOpenHashSet();
    /** For each regional cell, the square metres of land that the soil cells made in it claim the air's fall over: what the water of a lake may take is the rest of the cell. */
    private final Long2DoubleOpenHashMap landArea = new Long2DoubleOpenHashMap();
    private long lastRefreshDay = Long.MIN_VALUE;
    private long lastHour = Long.MIN_VALUE;
    private long foldedHours;
    private @Nullable RiverResolver resolver;
    private boolean resolverChosen;
    private @Nullable Executor executor;
    private BiPredicate<ServerLevel, LevelChunk> lakeDetector = LevelWaters::inLakeBiome;
    private BiPredicate<ServerLevel, LevelChunk> coastDetector = LevelWaters::inCoastBiome;
    private final AirWeather.Sample sample = new AirWeather.Sample();
    private final Long2DoubleOpenHashMap rainByCell = new Long2DoubleOpenHashMap();

    public static LevelWaters get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public WaterLedger ledger()
    {
        return ledger;
    }

    /** @return how many hours of the calendar were folded into coarser steps, because the clock was more than a week behind */
    public long foldedHours()
    {
        return foldedHours;
    }

    /** Forgets everything, for tests that need a world with nothing in its waters. */
    public void reset()
    {
        ledger.clear();
        knownIds.clear();
        inFlight.clear();
        arrived.clear();
        failed.clear();
        retryAfter.clear();
        loggedSoil.clear();
        landArea.clear();
        watched.clear();
        lastRefreshDay = Long.MIN_VALUE;
        lastHour = Long.MIN_VALUE;
        foldedHours = 0L;
        setDirty();
    }

    /** Replaces the river graph and the thread it is asked on, for tests that need a river that they choose. {@code null} gives TFC's back (or none, in a world that has no TFC rivers). */
    public void setResolver(@Nullable RiverResolver resolver, @Nullable Executor executor)
    {
        this.resolver = resolver;
        this.resolverChosen = resolver != null;
        this.executor = executor;
    }

    /** Replaces the way a chunk is found to be in a lake, for tests. {@code null} gives the biome back. */
    public void setLakeDetector(@Nullable BiPredicate<ServerLevel, LevelChunk> detector)
    {
        this.lakeDetector = detector == null ? LevelWaters::inLakeBiome : detector;
    }

    /** Replaces the way a chunk is found to be on the coast, for tests. {@code null} gives the biome back. */
    public void setCoastDetector(@Nullable BiPredicate<ServerLevel, LevelChunk> detector)
    {
        this.coastDetector = detector == null ? LevelWaters::inCoastBiome : detector;
    }

    /** Lets the chunks whose question failed be asked again at once, which the hour that they wait would otherwise do. */
    public void forgetFailures()
    {
        retryAfter.clear();
    }

    /** Stops asking: lets go of the river graph, which holds the generator of the world. Called when the server stops. */
    public void stop()
    {
        resolver = null;
        resolverChosen = true; // And is not chosen again
        executor = null;
        arrived.clear();
        failed.clear();
        inFlight.clear();
    }

    /** Stops the thread that the river graph is asked on, and lets nothing that is queued for it run. Called when the server stops; a new thread is made when one is needed again. */
    public static synchronized void shutdown()
    {
        closed = true;
        if (sharedExecutor != null)
        {
            sharedExecutor.shutdownNow();
            sharedExecutor = null;
        }
    }

    /** Allows the thread to be made again, when a server starts after one has stopped in the same process. */
    public static void restart()
    {
        closed = false;
    }

    // --- Finding the water of a place ------------------------------------------------------------------------------------------------------------------------------

    /** @return {@code true} if the biome is one of TFC's fresh lakes: its id has "lake" in it and is not an oceanic one, which is salt */
    public static boolean isLakeBiome(String namespace, String path)
    {
        return namespace.equals("tfc") && path.contains("lake") && !path.contains("oceanic");
    }

    /** @return {@code true} if the biome is on the coast: an ocean, a shore, a bay, an embayment, or an oceanic one */
    public static boolean isCoastBiome(String namespace, String path)
    {
        return namespace.equals("tfc") && (path.contains("ocean") || path.contains("shore") || path.contains("bay"));
    }

    private static @Nullable ResourceLocation biomeAt(ServerLevel level, LevelChunk chunk)
    {
        final ChunkPos pos = chunk.getPos();
        final int y = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, level.getSeaLevel()));
        final Biome biome = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getMinBlockX() + 8), QuartPos.fromBlock(y), QuartPos.fromBlock(pos.getMinBlockZ() + 8)).value();
        return level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
    }

    private static boolean inLakeBiome(ServerLevel level, LevelChunk chunk)
    {
        final @Nullable ResourceLocation id = biomeAt(level, chunk);
        return id != null && isLakeBiome(id.getNamespace(), id.getPath());
    }

    private static boolean inCoastBiome(ServerLevel level, LevelChunk chunk)
    {
        final @Nullable ResourceLocation id = biomeAt(level, chunk);
        return id != null && isCoastBiome(id.getNamespace(), id.getPath());
    }

    /** @return {@code true} if still fresh water in this chunk is a lake: only the lake biomes of TFC count, and any other still water (a puddle, a bucket of water, a pocket in a cave) is not a body of water in this model */
    public boolean isLake(ServerLevel level, LevelChunk chunk)
    {
        return lakeDetector.test(level, chunk);
    }

    private @Nullable RiverResolver resolverOf(ServerLevel level)
    {
        if (!resolverChosen)
        {
            resolverChosen = true;
            final ChunkGenerator generator = level.getChunkSource().getGenerator();
            if (generator instanceof ChunkGeneratorExtension extension && generator.getBiomeSource() instanceof BiomeSourceExtension biomes)
            {
                resolver = new TfcRiverResolver(biomes, extension.chunkDataGenerator(), AirScale.metresPerBlock(LevelAirWeather.temperatureScale(level)));
            }
        }
        return resolver;
    }

    private Executor executor()
    {
        if (executor != null)
        {
            return executor;
        }
        synchronized (LevelWaters.class)
        {
            if (sharedExecutor == null)
            {
                closed = false;
                sharedExecutor = Executors.newSingleThreadExecutor(runnable ->
                {
                    final Thread thread = new Thread(runnable, "tfcpp-rivers");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return sharedExecutor;
        }
    }

    private static long hourNow()
    {
        return AirCadence.hourOf(Calendars.SERVER.getCalendarTicks());
    }

    @Nullable
    private Found found(ServerLevel level, int chunkX, int chunkZ)
    {
        final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null)
        {
            return null;
        }
        final WaterChunkData data = chunk.getData(EcologyAttachments.WATER_CHUNK);
        return data.resolved() ? new Found(data.edge(), data.distance()) : null;
    }

    /**
     * Asks for the water near a chunk, unless it is asked for already or failed a short while ago. In a world with no TFC rivers the answer is that there are none, and it is there at once; otherwise it comes back through {@link #collect}.
     */
    private void request(ServerLevel level, int chunkX, int chunkZ)
    {
        final long key = AirGrid.key(chunkX, chunkZ);
        if (inFlight.contains(key) || retryAfter.getOrDefault(key, Long.MIN_VALUE) > hourNow())
        {
            return;
        }
        final @Nullable RiverResolver graph = resolverOf(level);
        if (graph == null)
        {
            apply(level, new Resolution(chunkX, chunkZ, ReachInfo.SEA, Double.POSITIVE_INFINITY, WaterConstants.SILICATE_ALKALINITY, List.of()));
            return;
        }
        if (inFlight.size() >= MAX_IN_FLIGHT)
        {
            return; // Asked again at the next step
        }
        inFlight.add(key);
        executor().execute(() ->
        {
            if (closed)
            {
                return; // The server is stopping: the generator that this graph holds is not to be used
            }
            try
            {
                arrived.add(graph.resolve(chunkX, chunkZ, knownIds::contains));
            }
            catch (Throwable e)
            {
                LOGGER.warn("Could not read TFC's river graph at chunk {}, {}: it will be asked again later", chunkX, chunkZ, e);
                failed.add(key);
            }
        });
        collect(level); // A graph asked on the spot (in tests) has answered already
    }

    /** Takes in the answers that the other thread has finished and lets what waited for them in. Called on the main thread. */
    public void collect(ServerLevel level)
    {
        @Nullable Long lost;
        while ((lost = failed.poll()) != null)
        {
            inFlight.remove(lost.longValue());
            retryAfter.put(lost.longValue(), hourNow() + 1L); // Not asked again within the hour
        }
        @Nullable Resolution result;
        while ((result = arrived.poll()) != null)
        {
            inFlight.remove(result.key());
            retryAfter.remove(result.key());
            apply(level, result);
        }
    }

    private void register(ReachInfo info)
    {
        ledger.register(info);
        knownIds.add(info.id());
    }

    private void apply(ServerLevel level, Resolution result)
    {
        for (ReachInfo info : result.chain())
        {
            register(info);
        }
        register(ReachInfo.lake(result.chunkX() >> 3, result.chunkZ() >> 3, result.lakeAlkalinity()));
        final long key = result.key();
        final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(result.chunkX(), result.chunkZ());
        if (chunk != null)
        {
            chunk.getData(EcologyAttachments.WATER_CHUNK).set(result.edgeId(), result.distanceBlocks());
            chunk.setUnsaved(true);
        }
        final long lake = ReachInfo.lakeId(result.chunkX() >> 3, result.chunkZ() >> 3);
        for (WaterRoute route : WaterRoute.VALUES)
        {
            for (boolean bed : new boolean[] {false, true})
            {
                final int channel = route.channel(bed);
                if (!ledger.hasPending(channel, key))
                {
                    continue;
                }
                final long target = route.target(result.edgeId(), result.distanceBlocks(), lake);
                if (target == WaterRoute.SOIL)
                {
                    final @Nullable double[] amounts = ledger.takePending(channel, key);
                    if (amounts != null)
                    {
                        toSoil(level, result.chunkX(), result.chunkZ(), amounts);
                    }
                }
                else
                {
                    ledger.movePending(channel, key, target);
                }
                setDirty();
            }
        }
    }

    /** River water that is not near any river of the graph is not a body of water here: what was let into it goes to the soil under it. */
    private void toSoil(ServerLevel level, int chunkX, int chunkZ, double[] amounts)
    {
        if (loggedSoil.add(AirGrid.key(chunkX, chunkZ)))
        {
            LOGGER.info("River water at chunk {}, {} is not near any river of the graph: what was let into it goes into the soil", chunkX, chunkZ);
        }
        final BlockPos at = new BlockPos((chunkX << 4) + 8, level.getMinBuildHeight(), (chunkZ << 4) + 8);
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            Pollution.deposit(level, at, pollutant, amounts[pollutant.ordinal()]);
        }
    }

    /** @return where the runoff of this chunk goes: a lake if it is in a lake biome, else a river if one is within 64 blocks, else the sea if it is on the coast, else nowhere; {@link Drainage#UNKNOWN} while the river graph is being asked */
    public Drainage classify(ServerLevel level, LevelChunk chunk)
    {
        collect(level);
        if (lakeDetector.test(level, chunk))
        {
            return Drainage.LAKE;
        }
        final int chunkX = chunk.getPos().x, chunkZ = chunk.getPos().z;
        @Nullable Found at = found(level, chunkX, chunkZ);
        if (at == null)
        {
            request(level, chunkX, chunkZ);
            at = found(level, chunkX, chunkZ);
            if (at == null)
            {
                return Drainage.UNKNOWN;
            }
        }
        if (at.edgeId() != ReachInfo.SEA && at.distance() <= WaterConstants.RUNOFF_REACH_BLOCKS)
        {
            return Drainage.RIVER;
        }
        return coastDetector.test(level, chunk) ? Drainage.SEA : Drainage.NONE;
    }

    // --- Inputs -----------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Something put into water at this position: salt water takes it to the sea, river water to the nearest edge, a lake or a spring to the still water of the cell. Molten metal and mercury sink to the bed.
     *
     * @param load for each pollutant, in its unit
     * @param sinks whether it goes to the bed and not into the water column
     */
    public void discharge(ServerLevel level, BlockPos waterPos, Waters.Kind kind, double[] load, boolean sinks)
    {
        collect(level);
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            final double amount = load[pollutant.ordinal()];
            if (!(amount > 0.0))
            {
                continue;
            }
            if (kind == Waters.Kind.SEA)
            {
                ledger.addToSea(pollutant, amount);
            }
            else
            {
                input(level, kind == Waters.Kind.RIVER ? WaterRoute.DISCHARGE_RIVER : WaterRoute.DISCHARGE_STILL, sinks, waterPos.getX() >> 4, waterPos.getZ() >> 4, pollutant, amount);
            }
        }
        setDirty();
    }

    /** What the soil of a chunk has given up to the water that it drains to. */
    public void runoff(ServerLevel level, ChunkPos chunk, Drainage drainage, double[] amounts)
    {
        collect(level);
        final WaterRoute route = drainage == Drainage.LAKE ? WaterRoute.RUNOFF_LAKE : WaterRoute.RUNOFF_RIVER;
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            final double amount = amounts[pollutant.ordinal()];
            if (!(amount > 0.0))
            {
                continue;
            }
            if (drainage == Drainage.SEA)
            {
                ledger.addToSea(pollutant, amount);
            }
            else
            {
                input(level, route, false, chunk.x, chunk.z, pollutant, amount);
            }
        }
        setDirty();
    }

    private void input(ServerLevel level, WaterRoute route, boolean bed, int chunkX, int chunkZ, Pollutant pollutant, double amount)
    {
        final long key = AirGrid.key(chunkX, chunkZ);
        for (int attempt = 0; attempt < 2; attempt++)
        {
            @Nullable Found at = found(level, chunkX, chunkZ);
            if (at == null)
            {
                request(level, chunkX, chunkZ);
                at = found(level, chunkX, chunkZ); // A world with no rivers answers at once
            }
            if (at == null)
            {
                break;
            }
            final long target = route.target(at.edgeId(), at.distance(), ReachInfo.lakeId(chunkX >> 3, chunkZ >> 3));
            if (target == WaterRoute.SOIL)
            {
                final double[] amounts = new double[Pollutant.COUNT];
                amounts[pollutant.ordinal()] = amount;
                toSoil(level, chunkX, chunkZ, amounts);
                return;
            }
            if (target == ReachInfo.SEA)
            {
                ledger.addToSea(pollutant, amount);
                return;
            }
            if (ledger.knows(target))
            {
                if (bed)
                {
                    ledger.addToBed(target, pollutant, amount);
                }
                else
                {
                    ledger.add(target, pollutant, amount);
                }
                return;
            }
            // The answer is kept with the chunk but the geometry is not here (it is kept only for reaches that hold something): ask again
            request(level, chunkX, chunkZ);
            if (!knownIds.contains(target))
            {
                break;
            }
        }
        ledger.addPending(route.channel(bed), key, pollutant, amount);
    }

    // --- Looking ---------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * The water that a route leads to from this chunk, and what it holds. Cheap: the mapping from chunk to body is kept with the chunk.
     *
     * @return {@code null} if the route leads to the soil: river water that is not near any river of the graph
     */
    @Nullable
    public WaterBody bodyFor(ServerLevel level, WaterRoute route, int chunkX, int chunkZ)
    {
        collect(level);
        @Nullable Found at = found(level, chunkX, chunkZ);
        if (at == null)
        {
            request(level, chunkX, chunkZ);
            at = found(level, chunkX, chunkZ);
            if (at == null)
            {
                return WaterBody.unresolved(route == WaterRoute.DISCHARGE_RIVER || route == WaterRoute.RUNOFF_RIVER ? WaterBody.Type.RIVER : WaterBody.Type.LAKE);
            }
        }
        final long target = route.target(at.edgeId(), at.distance(), ReachInfo.lakeId(chunkX >> 3, chunkZ >> 3));
        if (target == WaterRoute.SOIL)
        {
            return null;
        }
        return target == ReachInfo.SEA ? WaterBody.sea() : bodyOf(target);
    }

    /** @return the body of this id, with what it holds: nothing for one that holds nothing, which is also the case of a reach whose geometry is not kept because it holds nothing */
    public WaterBody bodyOf(long id)
    {
        final @Nullable ReachInfo info = ledger.info(id);
        final double[] perLitre = new double[Pollutant.COUNT], inWater = new double[Pollutant.COUNT], onBed = new double[Pollutant.COUNT];
        final WaterBody.Type type = ReachInfo.isLake(id) ? WaterBody.Type.LAKE : WaterBody.Type.RIVER;
        if (info == null)
        {
            return new WaterBody(type, id, true, null, perLitre, inWater, onBed);
        }
        final @Nullable WaterLedger.Reach reach = ledger.reach(id);
        if (reach != null)
        {
            for (Pollutant pollutant : Pollutant.VALUES)
            {
                perLitre[pollutant.ordinal()] = reach.perLitre(pollutant);
                inWater[pollutant.ordinal()] = reach.water(pollutant);
                onBed[pollutant.ordinal()] = reach.sediment(pollutant);
            }
        }
        return new WaterBody(type, id, true, info, perLitre, inWater, onBed);
    }

    // --- Time ------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Called every tick of the level: does nothing until an hour of the calendar has passed. */
    public void tick(ServerLevel level)
    {
        final long hour = hourNow();
        stepTo(level, hour);
        refreshWatched(level, Math.floorDiv(hour, 24L));
    }

    /** What the air has let fall on the reaches since they last looked: the same device as the soil's, over the surface of each reach. */
    private void claimAir(RegionalAir air)
    {
        for (ReachInfo info : ledger.infos())
        {
            final @Nullable AirGrid.Cell cell = air.grid().cellAt(info.cellX(), info.cellZ());
            if (cell == null)
            {
                continue;
            }
            for (Pollutant pollutant : RegionalAir.FALLING)
            {
                ledger.claimFall(info.id(), pollutant, cell.cumulative(pollutant), info.still() ? Math.max(0.0, WaterConstants.LAKE_AREA - landArea.get(AirGrid.key(info.cellX(), info.cellZ()))) : -1.0);
            }
        }
    }

    /**
     * Brings the waters up to a calendar hour, in the same cadence as the regional air: an hour at a time, and after a longer gap a week of steps of equal length that together span the gap, so that the cost is bounded and nothing is lost: the processes are closed form in each step, and the water is flushed through as far as it would have been.
     *
     * @return how many steps were taken
     */
    public long stepTo(ServerLevel level, long nowHour)
    {
        collect(level);
        retryPending(level);
        if (lastHour == Long.MIN_VALUE)
        {
            lastHour = nowHour;
            setDirty();
            return 0L;
        }
        final RegionalAir air = RegionalAir.get(level);
        if (nowHour > lastHour)
        {
            claimAir(air);
        }
        if (ledger.activeCount() == 0)
        {
            if (lastHour != nowHour)
            {
                lastHour = nowHour;
                setDirty();
            }
            return 0L;
        }
        final AirCadence.Plan plan = AirCadence.plan(lastHour, nowHour);
        if (plan.steps() == 0L)
        {
            if (plan.newLastHour() != lastHour)
            {
                lastHour = plan.newLastHour(); // The calendar was set back: the clock goes back with it, and nothing is done
                setDirty();
            }
            return 0L;
        }
        final long start = System.nanoTime();
        final long hours = nowHour - lastHour;
        final double hoursPerStep = (double) hours / plan.steps();
        final AirWeather sky = air.weather(level);
        if (sky instanceof LevelAirWeather real)
        {
            real.collect(air.grid()); // The rainfall that was worked out off the main thread comes in here
        }
        for (long step = 0; step < plan.steps(); step++)
        {
            final long tick = (long) ((lastHour + (step + 0.5) * hoursPerStep) * AirScale.TICKS_PER_HOUR);
            rainByCell.clear();
            ledger.step(hoursPerStep, info -> rainOver(sky, info, tick));
        }
        if (hours > plan.steps())
        {
            foldedHours += hours - plan.steps();
            LOGGER.info("The calendar jumped by {} hours: the waters were followed in {} steps of {} hours each", hours, plan.steps(), hoursPerStep);
        }
        lastHour = plan.newLastHour();
        setDirty();
        TickProfiler.PROFILE.record(TickProfiler.WATER, System.nanoTime() - start);
        return plan.steps();
    }

    private double rainOver(AirWeather sky, ReachInfo info, long tick)
    {
        final long key = AirGrid.key(info.cellX(), info.cellZ());
        if (rainByCell.containsKey(key))
        {
            return rainByCell.get(key);
        }
        sky.sampleRain(info.cellX(), info.cellZ(), tick, sample);
        final double intensity = sample.raining ? sample.rainIntensity : 0.0;
        rainByCell.put(key, intensity);
        return intensity;
    }

    /** Asks again for the places whose inputs wait and whose question was never sent, because the other thread had too many, or failed. */
    private void retryPending(ServerLevel level)
    {
        if (ledger.pendingCount() == 0)
        {
            return;
        }
        for (int channel = 0; channel < WaterLedger.CHANNELS; channel++)
        {
            for (long key : ledger.pendingKeys(channel))
            {
                if (found(level, (int) (key >> 32), (int) key) == null && !inFlight.contains(key))
                {
                    request(level, (int) (key >> 32), (int) key);
                }
            }
        }
    }

    /** Notes that a soil cell has been made in this chunk, with the area of land that it will claim the fall over: the lake of the cell takes only what is left of the cell. */
    public void registerLand(ChunkPos chunk, double squareMetres)
    {
        landArea.addTo(AirGrid.key(chunk.x >> 3, chunk.z >> 3), squareMetres);
        setDirty();
    }

    /** @return the square metres of land in a regional cell that soil cells claim the fall over */
    public double landAreaOf(int cellX, int cellZ)
    {
        return landArea.get(AirGrid.key(cellX, cellZ));
    }

    /** Notes that the soil of a chunk may run off to water, or stops noting it once it is known not to. */
    public void watch(ChunkPos chunk, Drainage drainage)
    {
        final boolean wanted = drainage == Drainage.UNKNOWN || drainage.drains();
        if (wanted ? watched.add(chunk.toLong()) : watched.remove(chunk.toLong()))
        {
            setDirty();
        }
    }

    /** @return how many chunks are watched for runoff */
    public int watchedCount()
    {
        return watched.size();
    }

    /** Once a calendar day brings the soil of the loaded watched chunks up to date, which hands the water what it has run off since. Chunks that are not loaded wait: their soil catches up when they are. */
    private void refreshWatched(ServerLevel level, long day)
    {
        if (day == lastRefreshDay || watched.isEmpty())
        {
            lastRefreshDay = day;
            return;
        }
        lastRefreshDay = day;
        for (long key : watched.toLongArray())
        {
            final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null)
            {
                Pollution.refresh(level, chunk);
            }
        }
    }

    // --- Saving ----------------------------------------------------------------------------------------------------------------------------------------------------------

    private static CompoundTag amounts(double[] values)
    {
        final CompoundTag tag = new CompoundTag();
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            if (values[pollutant.ordinal()] != 0.0)
            {
                tag.putDouble(pollutant.id(), values[pollutant.ordinal()]);
            }
        }
        return tag;
    }

    private static void readAmounts(CompoundTag tag, double[] into)
    {
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            into[pollutant.ordinal()] = tag.getDouble(pollutant.id()); // By name, so that a kind added to the enum later does not shift the others
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        tag.putLong("lastHour", lastHour);
        tag.putLong("foldedHours", foldedHours);
        tag.put("input", amounts(ledger.input));
        tag.put("fromAir", amounts(ledger.fromAir));
        tag.put("decayed", amounts(ledger.decayed));
        tag.put("neutralised", amounts(ledger.neutralised));
        tag.put("toSea", amounts(ledger.toSea));
        tag.put("dropped", amounts(ledger.dropped));

        // The geometry of the reaches that hold something, and of everything downstream of them: the water in them flows there
        final LongOpenHashSet needed = new LongOpenHashSet();
        for (WaterLedger.Reach reach : ledger.reaches())
        {
            @Nullable ReachInfo info = reach.info();
            while (info != null && needed.add(info.id()))
            {
                info = info.drain() == ReachInfo.SEA ? null : ledger.info(info.drain());
            }
        }
        final ListTag infos = new ListTag();
        for (long id : needed)
        {
            final ReachInfo info = ledger.info(id);
            if (info == null)
            {
                continue;
            }
            final CompoundTag entry = new CompoundTag();
            entry.putLong("id", info.id());
            entry.putLong("drain", info.drain());
            entry.putBoolean("still", info.still());
            entry.putDouble("width", info.width());
            entry.putDouble("length", info.lengthBlocks());
            entry.putDouble("scale", info.metresPerBlock());
            entry.putDouble("alkalinity", info.alkalinity());
            entry.putInt("cellX", info.cellX());
            entry.putInt("cellZ", info.cellZ());
            infos.add(entry);
        }
        tag.put("infos", infos);

        final ListTag reaches = new ListTag();
        for (WaterLedger.Reach reach : ledger.reaches())
        {
            final CompoundTag entry = new CompoundTag();
            entry.putLong("id", reach.info().id());
            entry.put("water", amounts(reach.water));
            entry.put("sediment", amounts(reach.sediment));
            entry.putDouble("reserve", reach.reserve);
            reaches.add(entry);
        }
        tag.put("reaches", reaches);

        final ListTag claimed = new ListTag();
        for (var entry : ledger.claimedAir.entrySet())
        {
            final CompoundTag item = new CompoundTag();
            item.putLong("id", entry.getKey());
            item.put("fall", amounts(entry.getValue()));
            claimed.add(item);
        }
        tag.put("claimed", claimed);

        final ListTag pending = new ListTag();
        for (int channel = 0; channel < WaterLedger.CHANNELS; channel++)
        {
            for (long key : ledger.pendingKeys(channel))
            {
                final CompoundTag entry = new CompoundTag();
                entry.putInt("channel", channel);
                entry.putLong("key", key);
                entry.put("amounts", amounts(ledger.pending(channel, key)));
                pending.add(entry);
            }
        }
        tag.put("pending", pending);
        tag.putLongArray("watched", watched.toLongArray());
        final ListTag land = new ListTag();
        for (var entry : landArea.long2DoubleEntrySet())
        {
            final CompoundTag item = new CompoundTag();
            item.putLong("cell", entry.getLongKey());
            item.putDouble("area", entry.getDoubleValue());
            land.add(item);
        }
        tag.put("land", land);
        return tag;
    }

    public static LevelWaters load(CompoundTag tag, HolderLookup.Provider registries)
    {
        final LevelWaters waters = new LevelWaters();
        waters.lastHour = tag.contains("lastHour") ? tag.getLong("lastHour") : Long.MIN_VALUE;
        waters.foldedHours = tag.getLong("foldedHours");
        for (long key : tag.getLongArray("watched"))
        {
            waters.watched.add(key);
        }
        for (Tag raw : tag.getList("land", Tag.TAG_COMPOUND))
        {
            waters.landArea.put(((CompoundTag) raw).getLong("cell"), ((CompoundTag) raw).getDouble("area"));
        }
        final WaterLedger ledger = waters.ledger;
        readAmounts(tag.getCompound("input"), ledger.input);
        readAmounts(tag.getCompound("fromAir"), ledger.fromAir);
        readAmounts(tag.getCompound("decayed"), ledger.decayed);
        readAmounts(tag.getCompound("neutralised"), ledger.neutralised);
        readAmounts(tag.getCompound("toSea"), ledger.toSea);
        readAmounts(tag.getCompound("dropped"), ledger.dropped);
        for (Tag raw : tag.getList("infos", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) raw;
            waters.register(entry.getBoolean("still")
                ? ReachInfo.lake(entry.getInt("cellX"), entry.getInt("cellZ"), entry.getDouble("alkalinity"))
                : ReachInfo.river(entry.getLong("id"), entry.getDouble("width"), entry.getDouble("length"), entry.getDouble("scale"), entry.getDouble("alkalinity"), entry.getLong("drain"), entry.getInt("cellX"), entry.getInt("cellZ")));
        }
        for (Tag raw : tag.getList("reaches", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) raw;
            if (!ledger.knows(entry.getLong("id")))
            {
                continue;
            }
            final WaterLedger.Reach reach = ledger.restoreReach(entry.getLong("id"));
            readAmounts(entry.getCompound("water"), reach.water);
            readAmounts(entry.getCompound("sediment"), reach.sediment);
            reach.reserve = entry.getDouble("reserve");
        }
        for (Tag raw : tag.getList("claimed", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) raw;
            final double[] values = new double[Pollutant.COUNT];
            readAmounts(entry.getCompound("fall"), values);
            ledger.claimedAir.put(entry.getLong("id"), values);
        }
        for (Tag raw : tag.getList("pending", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) raw;
            final double[] values = new double[Pollutant.COUNT];
            readAmounts(entry.getCompound("amounts"), values);
            ledger.restorePending(entry.getInt("channel"), entry.getLong("key"), values);
        }
        return waters;
    }
}
