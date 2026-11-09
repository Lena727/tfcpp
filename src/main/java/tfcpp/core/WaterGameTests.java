package tfcpp.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.chemistry.Chemistry;
import tfcpp.chemistry.Liquid;
import tfcpp.ecology.AirCadence;
import tfcpp.ecology.AirWeather;
import tfcpp.ecology.Drainage;
import tfcpp.ecology.EcologyAttachments;
import tfcpp.ecology.LevelWaters;
import tfcpp.ecology.Pollutant;
import tfcpp.ecology.Pollution;
import tfcpp.ecology.PollutionChunkData;
import tfcpp.ecology.ReachInfo;
import tfcpp.ecology.RegionalAir;
import tfcpp.ecology.Resolution;
import tfcpp.ecology.RiverResolver;
import tfcpp.ecology.Soakable;
import tfcpp.ecology.SoilCell;
import tfcpp.ecology.SoilConstants;
import tfcpp.ecology.SoilOrder;
import tfcpp.ecology.WaterBody;
import tfcpp.ecology.WaterConstants;
import tfcpp.ecology.WaterLedger;
import tfcpp.ecology.Waters;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * Stage C of v1.5: rivers and lakes carry what is put in them. The flat world of the tests has no TFC rivers, so what is exercised here with the real graph is only what the world without rivers answers (no river anywhere, so a river water block is still water, of the still kind, and soil drains to nothing). What a river graph does is tested with a graph that is made up, {@link FakeGraph}, which answers on the thread it is asked on; the real thing, TFC's partition and rocks, runs only in a TFC world and is not exercised here. Every test has a batch of its own, because the waters are one for the whole level and each test empties them first.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterGameTests
{
    private static final String TEMPLATE = "empty9";
    /** Chunks of the tests are 16 chunks apart in a row, which is two regional cells. */
    private static final int FIRST_CHUNK_X = 7000, CHUNK_Z = 7000, SLOT_CHUNKS = 16;
    private static final long FIRST_EDGE = 1001L, SECOND_EDGE = 1002L;

    /** A river of two edges, 1001 into 1002 into the sea, which every chunk is {@code distance} blocks from. */
    private static final class FakeGraph implements RiverResolver
    {
        final Set<Thread> threads = ConcurrentHashMap.newKeySet();
        final double distance;
        volatile int asked;

        FakeGraph(double distance)
        {
            this.distance = distance;
        }

        @Override
        public Resolution resolve(int chunkX, int chunkZ, java.util.function.LongPredicate known)
        {
            threads.add(Thread.currentThread());
            asked++;
            final List<ReachInfo> chain = new ArrayList<>();
            if (!known.test(FIRST_EDGE))
            {
                chain.add(ReachInfo.river(FIRST_EDGE, 8.0, 370.0, 500.0, 0.1, SECOND_EDGE, chunkX >> 3, chunkZ >> 3));
            }
            if (!known.test(SECOND_EDGE))
            {
                chain.add(ReachInfo.river(SECOND_EDGE, 10.0, 370.0, 500.0, 0.1, ReachInfo.SEA, chunkX >> 3, chunkZ >> 3));
            }
            return new Resolution(chunkX, chunkZ, FIRST_EDGE, distance, WaterConstants.SILICATE_ALKALINITY, chain);
        }
    }

    private static LevelChunk chunk(ServerLevel level, int slot)
    {
        final int chunkX = FIRST_CHUNK_X + slot * SLOT_CHUNKS, chunkZ = CHUNK_Z;
        level.setChunkForced(chunkX, chunkZ, true);
        final LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        chunk.removeData(EcologyAttachments.CHUNK); // The test world is kept between runs, and these chunks with it
        chunk.removeData(EcologyAttachments.WATER_CHUNK); // and so is what a graph said about the water near them
        RegionalAir.get(level).clearCellAt(chunkX << 4, chunkZ << 4);
        return chunk;
    }

    private static LevelWaters fresh(ServerLevel level)
    {
        final LevelWaters waters = LevelWaters.get(level);
        waters.reset();
        waters.setResolver(null, null);
        waters.setLakeDetector(null);
        waters.setCoastDetector(null);
        return waters;
    }

    private static void release(ServerLevel level, LevelChunk chunk)
    {
        level.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
        final LevelWaters waters = LevelWaters.get(level);
        waters.setResolver(null, null);
        waters.setLakeDetector(null);
        waters.reset();
        RegionalAir.get(level).setWeather(null);
    }

    /** A place a little above the ground at the middle of the chunk. */
    private static BlockPos spot(ServerLevel level, LevelChunk chunk, int above)
    {
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + above, z);
    }

    private static double acidPerLitre()
    {
        return Soakable.CHAMBER_ACID.load(1.0)[Pollutant.ACIDITY.ordinal()];
    }

    // --- Direct discharge -------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_1")
    public static void acidPouredFromAPipeEndOntoWaterEntersTheWaterLedgerAndNotTheSoil(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 0);
        final LevelWaters waters = fresh(level);
        waters.setLakeDetector((l, c) -> true); // The water here is a lake
        final BlockPos water = spot(level, chunk, 6), outlet = water.above();
        try
        {
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
            Pollution.seed(level, outlet, SoilOrder.PODZOL, false);
            final double soilBefore = Pollution.view(level, outlet).total(Pollutant.ACIDITY);

            helper.assertTrue(PipeTracker.placeBlock(level, outlet, Chemistry.fluid(Liquid.CHAMBER_ACID)), "A block of acid left the pipe end");
            final double expected = 1000.0 * acidPerLitre();
            helper.assertTrue(Math.abs(waters.ledger().input(Pollutant.ACIDITY) - expected) < expected * 1e-9, "The water ledger took in the 1000 L of acid, " + waters.ledger().input(Pollutant.ACIDITY) + " of " + expected);
            helper.assertTrue(Pollution.view(level, outlet).total(Pollutant.ACIDITY) == soilBefore, "and the soil did not");
            final long lake = ReachInfo.lakeId(chunk.getPos().x >> 3, chunk.getPos().z >> 3);
            final @Nullable WaterLedger.Reach reach = waters.ledger().reach(lake);
            helper.assertTrue(reach != null, "It is in the still water of the cell, since a flat world has no river");
            helper.assertTrue(Math.abs(reach.water(Pollutant.ACIDITY) - expected) < expected * 1e-9, "all of it");
            helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.ACIDITY)) < 1e-6, "and the books close");

            // Beside the water, and falling from a height, it is the same
            level.setBlock(outlet.above(5), Blocks.AIR.defaultBlockState(), 2);
            Pollution.soak(level, outlet.above(5), Chemistry.fluid(Liquid.CHAMBER_ACID), 10.0);
            Pollution.soak(level, water.east(), Chemistry.fluid(Liquid.CHAMBER_ACID), 10.0);
            helper.assertTrue(Math.abs(waters.ledger().input(Pollutant.ACIDITY) - expected - 20.0 * acidPerLitre()) < 1e-6, "A liquid that falls through air onto water, or spills sideways into it, is in the water too");
            helper.assertTrue(Pollution.view(level, outlet).total(Pollutant.ACIDITY) == soilBefore, "and none of it in the soil");

            // Away from water it still soaks into the ground
            final BlockPos dry = water.offset(8, 0, 8);
            Pollution.seed(level, dry, SoilOrder.PODZOL, false);
            final double dryBefore = Pollution.view(level, dry).total(Pollutant.ACIDITY);
            Pollution.soak(level, dry, Chemistry.fluid(Liquid.CHAMBER_ACID), 10.0);
            helper.assertTrue(Pollution.view(level, dry).total(Pollutant.ACIDITY) > dryBefore, "A spill onto dry ground goes into the soil as it did");
            helper.succeed();
        }
        finally
        {
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_2")
    public static void waterPouredOntoWaterRecordsNothing(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 1);
        final LevelWaters waters = fresh(level);
        waters.setLakeDetector((l, c) -> true);
        final BlockPos water = spot(level, chunk, 6);
        try
        {
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
            Pollution.soak(level, water.above(), Fluids.WATER, 5000.0);
            Pollution.soak(level, water, Fluids.WATER, 5000.0);
            final Fluid river = TFCFluids.RIVER_WATER.get();
            Pollution.soak(level, water, river, 5000.0);
            for (Pollutant pollutant : Pollutant.VALUES)
            {
                helper.assertTrue(waters.ledger().input(pollutant) == 0.0, "Nothing went into the ledger: " + pollutant);
            }
            helper.assertTrue(waters.ledger().activeCount() == 0 && waters.ledger().pendingCount() == 0, "and no reach holds anything");
            helper.assertTrue(!Pollution.isTouched(level, water), "and no soil was touched");
            helper.succeed();
        }
        finally
        {
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_3")
    public static void aChemicalLetIntoRiverWaterGoesToTheNearestEdgeAndSaltWaterTakesItToTheSea(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 2);
        final LevelWaters waters = fresh(level);
        final FakeGraph graph = new FakeGraph(30.0);
        waters.setResolver(graph, Runnable::run);
        final BlockPos water = spot(level, chunk, 6);
        try
        {
            final BlockState river = TFCBlocks.RIVER_WATER.get().defaultBlockState();
            level.setBlock(water, river, 2);
            final Soakable mercury = Soakable.MERCURY;
            Pollution.soak(level, water.above(), Metallurgy.MERCURY.get(), 500.0);
            final double expected = 500.0 * Soakable.MERCURY_DENSITY;
            helper.assertTrue(mercury.load(500.0)[Pollutant.MERCURY.ordinal()] == expected, "Mercury of 500 mL is 6.8 kg");
            final @Nullable WaterLedger.Reach reach = waters.ledger().reach(FIRST_EDGE);
            helper.assertTrue(reach != null && Math.abs(reach.sediment(Pollutant.MERCURY) - expected) < 1e-6, "It went to the bed of the nearest edge of the river, " + (reach == null ? "none" : reach.sediment(Pollutant.MERCURY)));
            helper.assertTrue(reach.water(Pollutant.MERCURY) == 0.0, "Mercury sinks: none of it is in the water column");
            Pollution.soak(level, water.above(), Chemistry.fluid(Liquid.CHAMBER_ACID), 10.0);
            helper.assertTrue(waters.ledger().reach(SECOND_EDGE) == null && waters.ledger().knows(SECOND_EDGE), "and the edge below it is known, and is still clean");
            helper.assertTrue(graph.asked == 1, "The graph was asked once for the chunk");

            // The query for the stages to come
            final @Nullable WaterBody body = Waters.at(level, water);
            helper.assertTrue(body != null && body.type() == WaterBody.Type.RIVER && body.resolved() && body.id() == FIRST_EDGE, "The body at the position is the edge");
            final double litres = waters.ledger().info(FIRST_EDGE).litres();
            helper.assertTrue(Math.abs(body.perLitre(Pollutant.ACIDITY) - 10.0 * acidPerLitre() / litres) < 1e-15, "with its acid per litre: " + body.perLitre(Pollutant.ACIDITY));
            helper.assertTrue(Math.abs(body.onBed()[Pollutant.MERCURY.ordinal()] - expected) < 1e-6, "and the mercury on its bed");
            helper.assertTrue(graph.asked == 1 && chunk.getData(EcologyAttachments.WATER_CHUNK).resolved(), "and asking again asks nothing: the mapping of the chunk is kept with the chunk");
            helper.assertTrue(Waters.at(level, water.above(3)) == null, "Where there is no water there is no body");

            // Salt water goes to the sea
            level.setBlock(water, TFCBlocks.SALT_WATER.get().defaultBlockState(), 2);
            Pollution.soak(level, water, Metallurgy.MERCURY.get(), 100.0);
            helper.assertTrue(Math.abs(waters.ledger().toSea(Pollutant.MERCURY) - 100.0 * Soakable.MERCURY_DENSITY) < 1e-6, "Salt water takes it straight to the sea");
            helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.MERCURY)) < 1e-6, "and the books close");
            helper.assertTrue(Waters.at(level, water).type() == WaterBody.Type.SEA, "The body of salt water is the sea");
            helper.succeed();
        }
        finally
        {
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        }
    }

    // --- Runoff from soil ---------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_4")
    public static void aPollutedSoilNextToARiverLosesRunoffToItOverCalendarTime(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 3);
        final LevelWaters waters = fresh(level);
        waters.setResolver(new FakeGraph(40.0), Runnable::run);
        final BlockPos at = spot(level, chunk, 3);
        try
        {
            Pollution.seed(level, at, SoilOrder.PODZOL, false);
            Pollution.deposit(level, at, Pollutant.LEAD, 5000.0);
            Pollution.deposit(level, at, Pollutant.SEWAGE, 2000.0);
            helper.assertTrue(chunk.getData(EcologyAttachments.CHUNK).cell().drainage() == Drainage.RIVER, "The chunk, 40 blocks from the river, is found to drain to it");
            helper.assertTrue(waters.ledger().input(Pollutant.LEAD) == 0.0, "Nothing has run off yet: no time has passed");

            // A year of the calendar passes
            final CompoundTag saved = PollutionChunkData.SERIALIZER.write(chunk.getData(EcologyAttachments.CHUNK), level.registryAccess());
            final CompoundTag cell = saved;
            cell.putLong("lastTick", cell.getLong("lastTick") - (long) (SoilConstants.daysPerYear() * SoilConstants.CALENDAR_TICKS_PER_DAY));
            chunk.setData(EcologyAttachments.CHUNK, PollutionChunkData.SERIALIZER.read(chunk, cell, level.registryAccess()));
            Pollution.refresh(level, chunk);

            final SoilCell soil = chunk.getData(EcologyAttachments.CHUNK).cell();
            final double leadRan = 5000.0 * (1.0 - Math.exp(-0.01));
            helper.assertTrue(Math.abs(soil.totalWashed(Pollutant.LEAD) - leadRan) < 1e-6, "A year at 1% takes " + leadRan + " g of the lead, got " + soil.totalWashed(Pollutant.LEAD));
            helper.assertTrue(Math.abs(waters.ledger().input(Pollutant.LEAD) - leadRan) < 1e-6, "and it is in the water ledger");
            final @Nullable WaterLedger.Reach reach = waters.ledger().reach(FIRST_EDGE);
            helper.assertTrue(reach != null && Math.abs(reach.water(Pollutant.LEAD) - leadRan) < 1e-6, "in the edge of the river");
            helper.assertTrue(Math.abs(soil.total(Pollutant.LEAD) + soil.totalWashed(Pollutant.LEAD) - 5000.0) < 1e-6, "so that soil and water together still hold all 5000 g");
            helper.assertTrue(waters.ledger().input(Pollutant.SEWAGE) > 0.0 && waters.ledger().input(Pollutant.SEWAGE) < 2000.0 * 0.3, "Sewage ran off too, less than 30% of it as most of it died on the way");
            helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.LEAD)) < 1e-6, "The water ledger balances");

            // A chunk far from any river loses nothing
            waters.setResolver(new FakeGraph(200.0), Runnable::run);
            waters.reset();
            final LevelChunk far = chunk(level, 4);
            final BlockPos farAt = spot(level, far, 3);
            Pollution.seed(level, farAt, SoilOrder.PODZOL, false);
            Pollution.deposit(level, farAt, Pollutant.LEAD, 5000.0);
            helper.assertTrue(far.getData(EcologyAttachments.CHUNK).cell().drainage() == Drainage.NONE, "A chunk 200 blocks from the nearest river drains to nothing");
            level.setChunkForced(far.getPos().x, far.getPos().z, false);
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_5")
    public static void aSoilInALakeBiomeRunsOffToTheStillWaterOfItsCellAndTheDailyRefreshDeliversIt(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 5);
        final LevelWaters waters = fresh(level);
        waters.setLakeDetector((l, c) -> true);
        final BlockPos at = spot(level, chunk, 3);
        try
        {
            Pollution.seed(level, at, SoilOrder.PODZOL, false);
            Pollution.deposit(level, at, Pollutant.TAR, 50.0);
            helper.assertTrue(chunk.getData(EcologyAttachments.CHUNK).cell().drainage() == Drainage.LAKE, "A chunk in a lake biome drains to the lake");
            helper.assertTrue(waters.watchedCount() == 1, "and is watched, so that the water does not have to wait for a touch");

            final CompoundTag cell = PollutionChunkData.SERIALIZER.write(chunk.getData(EcologyAttachments.CHUNK), level.registryAccess());
            cell.putLong("lastTick", cell.getLong("lastTick") - (long) (SoilConstants.daysPerYear() * SoilConstants.CALENDAR_TICKS_PER_DAY));
            chunk.setData(EcologyAttachments.CHUNK, PollutionChunkData.SERIALIZER.read(chunk, cell, level.registryAccess()));
            waters.tick(level); // The daily refresh, with nothing touching the soil
            helper.assertTrue(waters.ledger().input(Pollutant.TAR) > 0.0, "A day's refresh gave the lake the tar that ran off: " + waters.ledger().input(Pollutant.TAR));
            final @Nullable WaterLedger.Reach reach = waters.ledger().reach(ReachInfo.lakeId(chunk.getPos().x >> 3, chunk.getPos().z >> 3));
            helper.assertTrue(reach != null && reach.water(Pollutant.TAR) == waters.ledger().input(Pollutant.TAR), "all of it in the still water of the cell");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    // --- Off the main thread ------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "tfcpp_water_6")
    public static void theRiverGraphIsNeverAskedOnTheMainThreadAndWhatWaitsForItIsKept(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 6);
        final LevelWaters waters = fresh(level);
        final FakeGraph graph = new FakeGraph(10.0);
        final Thread main = Thread.currentThread();
        waters.setResolver(graph, null); // On the thread that the waters keep for the purpose
        final BlockPos water = spot(level, chunk, 6);
        level.setBlock(water, TFCBlocks.RIVER_WATER.get().defaultBlockState(), 2);
        final Fluid mercury = Metallurgy.MERCURY.get();
        Pollution.soak(level, water, mercury, 100.0);
        final double expected = 100.0 * Soakable.MERCURY_DENSITY;
        helper.assertTrue(waters.ledger().pendingCount() == 1 || waters.ledger().reach(FIRST_EDGE) != null, "The mercury is waiting, or the other thread was quick");
        helper.assertTrue(Math.abs(waters.ledger().input(Pollutant.MERCURY) - expected) < 1e-6, "and it is counted as put in either way");
        helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.MERCURY)) < 1e-6, "and the books close while it waits");

        helper.succeedWhen(() ->
        {
            waters.collect(level);
            helper.assertTrue(waters.ledger().pendingCount() == 0, "Waiting for the graph");
            helper.assertTrue(!graph.threads.isEmpty() && !graph.threads.contains(main), "The graph answered on another thread than the main one: " + graph.threads);
            helper.assertTrue(graph.threads.iterator().next().isDaemon(), "a daemon thread, which does not keep a server from stopping");
            final @Nullable WaterLedger.Reach reach = waters.ledger().reach(FIRST_EDGE);
            helper.assertTrue(reach != null && Math.abs(reach.sediment(Pollutant.MERCURY) - expected) < 1e-6, "and what waited went into the edge");
            helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.MERCURY)) < 1e-6, "with the books closed");
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_7")
    public static void inputsThatWaitAreSavedWithTheWorldAndGoInWhenTheAnswerComes(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 7);
        final LevelWaters waters = fresh(level);
        final Queue<Runnable> thread = new ArrayDeque<>();
        waters.setResolver(new FakeGraph(5.0), thread::add); // An executor that does nothing until it is told to
        final BlockPos water = spot(level, chunk, 6);
        try
        {
            level.setBlock(water, TFCBlocks.RIVER_WATER.get().defaultBlockState(), 2);
            final Fluid lead = Metallurgy.LEAD.get();
            Pollution.soak(level, water, lead, 300.0);
            Pollution.soak(level, water, lead, 100.0); // Same place, same wait
            final double expected = 400.0 * Soakable.LEAD_DENSITY;
            helper.assertTrue(waters.ledger().pendingCount() == 1 && thread.size() == 1, "Two inputs at one place wait together and ask once");
            helper.assertTrue(waters.ledger().activeCount() == 0, "and nothing is in a reach");

            final CompoundTag saved = waters.save(new CompoundTag(), level.registryAccess());
            final LevelWaters loaded = LevelWaters.load(saved, level.registryAccess());
            helper.assertTrue(loaded.ledger().pendingCount() == 1 && Math.abs(loaded.ledger().pendingTotal(Pollutant.LEAD) - expected) < 1e-6, "What waits is saved");
            helper.assertTrue(Math.abs(loaded.ledger().input(Pollutant.LEAD) - expected) < 1e-6 && Math.abs(loaded.ledger().imbalance(Pollutant.LEAD)) < 1e-6, "with the totals, which close");

            thread.poll().run(); // The graph answers
            waters.collect(level);
            helper.assertTrue(waters.ledger().pendingCount() == 0, "The answer lets it in");
            final @Nullable WaterLedger.Reach reach = waters.ledger().reach(FIRST_EDGE);
            helper.assertTrue(reach != null && Math.abs(reach.sediment(Pollutant.LEAD) - expected) < 1e-6, "all of it");
            helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.LEAD)) < 1e-6, "and the books close");

            final LevelWaters again = LevelWaters.load(waters.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            final WaterLedger.Reach back = again.ledger().reach(FIRST_EDGE);
            helper.assertTrue(back != null && back.sediment(Pollutant.LEAD) == reach.sediment(Pollutant.LEAD) && again.ledger().knows(SECOND_EDGE), "A reach comes back from saving with what it holds, and the edge below it");
            helper.assertTrue(again.ledger().info(FIRST_EDGE).drain() == SECOND_EDGE && again.ledger().info(FIRST_EDGE).volume() == waters.ledger().info(FIRST_EDGE).volume(), "and its geometry");
            helper.succeed();
        }
        finally
        {
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        }
    }

    // --- Time ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_8")
    public static void theWatersFollowTheCalendarAWeekAtATimeAndFoldALongerJump(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 8);
        final LevelWaters waters = fresh(level);
        waters.setResolver(new FakeGraph(5.0), Runnable::run);
        final BlockPos water = spot(level, chunk, 6);
        try
        {
            level.setBlock(water, TFCBlocks.RIVER_WATER.get().defaultBlockState(), 2);
            Pollution.soak(level, water, Chemistry.fluid(Liquid.CHAMBER_ACID), 10_000.0); // An acid, which neither settles nor breaks down: it goes down the river
            final long hour = AirCadence.hourOf(Pollution.now());
            helper.assertTrue(waters.stepTo(level, hour) == 0L, "The first call only sets the clock");
            helper.assertTrue(waters.stepTo(level, hour + 5) == 5L, "Five hours are five steps");
            helper.assertTrue(waters.stepTo(level, hour + 5) == 0L, "and no hour no step");
            helper.assertTrue(waters.stepTo(level, hour + 2) == 0L, "A calendar set back does nothing");
            final long steps = waters.stepTo(level, hour + 2 + 24 * 400);
            helper.assertTrue(steps == 168L, "A jump of 400 days is followed in a week of steps, got " + steps);
            helper.assertTrue(waters.foldedHours() == 24 * 400 - 168, "and the rest is counted, got " + waters.foldedHours());
            final double acid = 10_000.0 * acidPerLitre();
            helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.ACIDITY)) < 1e-6 * acid, "The books close");
            helper.assertTrue(waters.ledger().inWater(Pollutant.ACIDITY) < 1e-3 * acid, "The water column has been flushed of acid after 400 days");
            helper.assertTrue(waters.ledger().toSea(Pollutant.ACIDITY) > 0.5 * acid, "most of it went to the sea: " + waters.ledger().toSea(Pollutant.ACIDITY) / acid);
            helper.assertTrue(waters.ledger().neutralised(Pollutant.ACIDITY) > 0.0, "and the granite water used up some on the way: " + waters.ledger().neutralised(Pollutant.ACIDITY) / acid);
            helper.succeed();
        }
        finally
        {
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_9")
    public static void aStormStirsTheBedThroughTheRegionalAirsWeather(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 9);
        final LevelWaters waters = fresh(level);
        waters.setLakeDetector((l, c) -> true);
        final BlockPos water = spot(level, chunk, 6);
        try
        {
            RegionalAir.get(level).setWeather(new AirWeather.Fixed(0, 0, false));
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
            final Fluid lead = Metallurgy.LEAD.get();
            Pollution.soak(level, water, lead, 1000.0);
            final long hour = AirCadence.hourOf(Pollution.now());
            waters.stepTo(level, hour);
            waters.stepTo(level, hour + 24 * 60); // Two months of dry weather: the lead is on the bed
            final long lake = ReachInfo.lakeId(chunk.getPos().x >> 3, chunk.getPos().z >> 3);
            final double bedDry = waters.ledger().reach(lake).sediment(Pollutant.LEAD);
            helper.assertTrue(bedDry > 0.98 * 1000.0 * Soakable.LEAD_DENSITY, "The bed holds nearly all of it");

            RegionalAir.get(level).setWeather(new AirWeather.Fixed(0, 0, true, 0.9f));
            waters.stepTo(level, hour + 24 * 60 + 24 * 60);
            final double bedWet = waters.ledger().reach(lake).sediment(Pollutant.LEAD);
            helper.assertTrue(bedWet < bedDry * 0.95 && waters.ledger().reach(lake).water(Pollutant.LEAD) > 0.0, "Two months of heavy rain gave some of it back to the water: " + bedWet + " of " + bedDry);
            helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.LEAD)) < 1e-6, "and the books close");

            RegionalAir.get(level).setWeather(new AirWeather.Fixed(0, 0, true, 0.5f)); // A shower
            final double bedBefore = waters.ledger().reach(lake).sediment(Pollutant.LEAD);
            waters.stepTo(level, hour + 24 * 60 + 24 * 60 + 24 * 30);
            helper.assertTrue(waters.ledger().reach(lake).sediment(Pollutant.LEAD) >= bedBefore, "Rain below the heavy threshold takes nothing from the bed");
            helper.succeed();
        }
        finally
        {
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        }
    }

    // --- Review changes -----------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_10")
    public static void aPuddleAndRiverWaterFarFromAnyRiverSendTheSpillToTheSoilUnderThem(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 10);
        final LevelWaters waters = fresh(level);
        final BlockPos water = spot(level, chunk, 6);
        try
        {
            // Still water that is not in a lake biome is a puddle
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
            Pollution.seed(level, water, SoilOrder.PODZOL, false);
            final double before = Pollution.view(level, water).total(Pollutant.ACIDITY);
            Pollution.soak(level, water.above(), Chemistry.fluid(Liquid.CHAMBER_ACID), 10.0);
            helper.assertTrue(waters.ledger().input(Pollutant.ACIDITY) == 0.0, "A puddle takes nothing into the water ledger");
            helper.assertTrue(Pollution.view(level, water).total(Pollutant.ACIDITY) > before, "the acid goes to the soil");

            // River water where the graph has no river near: a bucket of it put down far from any river
            waters.setResolver(new FakeGraph(300.0), Runnable::run);
            level.setBlock(water, TFCBlocks.RIVER_WATER.get().defaultBlockState(), 2);
            final double mid = Pollution.view(level, water).total(Pollutant.ACIDITY);
            Pollution.soak(level, water.above(), Chemistry.fluid(Liquid.CHAMBER_ACID), 10.0);
            helper.assertTrue(waters.ledger().input(Pollutant.ACIDITY) == 0.0 && waters.ledger().pendingCount() == 0, "River water 300 blocks from the nearest edge is no river of the graph: nothing in the water ledger");
            helper.assertTrue(Math.abs(Pollution.view(level, water).total(Pollutant.ACIDITY) - mid - 10.0 * acidPerLitre()) < 1e-6, "and all of it in the soil");
            helper.assertTrue(Waters.at(level, water) == null, "and the query finds no body there");
            helper.succeed();
        }
        finally
        {
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_11")
    public static void aSpringIsAStillCellWhateverTheBiome(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 11);
        final LevelWaters waters = fresh(level);
        final BlockPos water = spot(level, chunk, 6);
        try
        {
            level.setBlock(water, TFCBlocks.SPRING_WATER.get().defaultBlockState(), 2);
            Pollution.soak(level, water.above(), Chemistry.fluid(Liquid.CHAMBER_ACID), 10.0);
            final long lake = ReachInfo.lakeId(chunk.getPos().x >> 3, chunk.getPos().z >> 3);
            helper.assertTrue(waters.ledger().reach(lake) != null && waters.ledger().reach(lake).water(Pollutant.ACIDITY) > 0.0, "Acid let into a hot spring is in the still cell of the spring, though no lake biome is near");
            helper.succeed();
        }
        finally
        {
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_12")
    public static void whatTheAirLetsFallOnALakeIsTakenByItsWaterAsAnInputOfItsOwn(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 12);
        final LevelWaters waters = fresh(level);
        final RegionalAir air = RegionalAir.get(level);
        try
        {
            air.reset();
            air.setWeather(new AirWeather.Fixed(0, 0, false));
            final int cellX = chunk.getPos().x >> 3, cellZ = chunk.getPos().z >> 3;
            final ReachInfo lake = ReachInfo.lake(cellX, cellZ, 0.1);
            waters.ledger().register(lake);
            final long hour = AirCadence.hourOf(Pollution.now());
            air.stepTo(level, hour);
            air.grid().emit(cellX, cellZ, tfcpp.ecology.AirSpecies.SULFUR_DIOXIDE, 2000.0);
            air.stepTo(level, hour + 100); // Dry fall for a hundred hours
            final double perM2 = air.cellAt(cellX << 7, cellZ << 7).cumulative(Pollutant.ACIDITY);
            helper.assertTrue(perM2 > 0.0, "Acid has fallen on the cell: " + perM2);
            waters.stepTo(level, hour);
            waters.stepTo(level, hour + 100);
            final double expected = perM2 * lake.surfaceArea();
            helper.assertTrue(Math.abs(waters.ledger().fromAir(Pollutant.ACIDITY) - expected) < expected * 1e-9, "The lake took its area's share of the fall, " + expected + " eq, got " + waters.ledger().fromAir(Pollutant.ACIDITY));
            helper.assertTrue(waters.ledger().input(Pollutant.ACIDITY) == 0.0, "and counted it apart from the inputs, so that the air ledger and the water ledger are not added up twice");
            helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.ACIDITY)) < 1e-6, "The books of the water close with it");
            final double claimed = waters.ledger().fromAir(Pollutant.ACIDITY);
            waters.stepTo(level, hour + 110);
            helper.assertTrue(Math.abs(waters.ledger().fromAir(Pollutant.ACIDITY) - claimed) < 1e-9 || waters.ledger().fromAir(Pollutant.ACIDITY) >= claimed, "Only what falls afterwards is taken again");
            helper.assertTrue(waters.ledger().fromAir(Pollutant.ACIDITY) <= air.cellAt(cellX << 7, cellZ << 7).cumulative(Pollutant.ACIDITY) * lake.surfaceArea() + 1e-6, "and never more than the air let fall on the lake");
            helper.succeed();
        }
        finally
        {
            air.setWeather(null);
            air.reset();
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_water_13")
    public static void aGraphThatFailsIsNotStuckInFlightAndIsAskedAgainLater(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 13);
        final LevelWaters waters = fresh(level);
        final int[] asked = new int[1];
        final FakeGraph good = new FakeGraph(10.0);
        waters.setResolver((chunkX, chunkZ, known) ->
        {
            if (++asked[0] == 1)
            {
                throw new AssertionError("The graph fell over"); // An Error, not an Exception
            }
            return good.resolve(chunkX, chunkZ, known);
        }, Runnable::run);
        try
        {
            helper.assertTrue(waters.classify(level, chunk) == Drainage.UNKNOWN, "The failure leaves the chunk unknown");
            helper.assertTrue(waters.classify(level, chunk) == Drainage.UNKNOWN && asked[0] == 1, "and it is not asked again at once");
            waters.forgetFailures(); // The hour that it waits goes by
            helper.assertTrue(waters.classify(level, chunk) == Drainage.RIVER && asked[0] == 2, "The next ask finds the river: the chunk was not stuck in flight");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_water_14")
    public static void aChunkHalfUnderLakeWaterClaimsHalfTheFallToItsSoilAndSoilAndWaterNeverClaimMoreThanFell(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 14);
        final LevelWaters waters = fresh(level);
        waters.setLakeDetector((l, c) -> true);
        final RegionalAir air = RegionalAir.get(level);
        final int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        final java.util.List<BlockPos> placed = new ArrayList<>();
        try
        {
            for (int x = 0; x < 8; x++)
            {
                for (int z = 0; z < 16; z++)
                {
                    final BlockPos top = new BlockPos(minX + x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, minX + x, minZ + z), minZ + z);
                    level.setBlock(top, Blocks.WATER.defaultBlockState(), 2);
                    placed.add(top);
                }
            }
            Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)); // The test world is not ticking: bring the heightmap up to the water
            air.reset();
            air.setWeather(new AirWeather.Fixed(0, 0, false));
            final int cellX = chunk.getPos().x >> 3, cellZ = chunk.getPos().z >> 3;
            final long hour = AirCadence.hourOf(Pollution.now());
            air.stepTo(level, hour);
            air.grid().emit(cellX, cellZ, tfcpp.ecology.AirSpecies.SULFUR_DIOXIDE, 2000.0);
            air.stepTo(level, hour + 100);
            final double perM2 = air.cellAt(cellX << 7, cellZ << 7).cumulative(Pollutant.ACIDITY);
            helper.assertTrue(perM2 > 0.0, "Acid has fallen on the cell");

            final BlockPos at = new BlockPos(minX + 8, level.getMinBuildHeight(), minZ + 8);
            Pollution.deposit(level, at, Pollutant.LEAD, 1.0); // Makes the soil cell, which takes in what fell on its land
            final SoilCell soil = chunk.getData(EcologyAttachments.CHUNK).cell();
            helper.assertTrue(Math.abs(soil.landFraction() - 0.5) < 1e-9, "Half of the chunk's columns are water, got " + soil.landFraction());
            final double soilShare = soil.totalInput(Pollutant.ACIDITY);
            helper.assertTrue(Math.abs(soilShare - perM2 * 128.0) < perM2 * 128.0 * 1e-9, "The soil claims the fall over 128 square metres of land, not 256: " + soilShare + " of " + perM2 * 256.0);

            waters.ledger().register(ReachInfo.lake(cellX, cellZ, 0.1));
            waters.stepTo(level, hour);
            waters.stepTo(level, hour + 100);
            final double fallen = perM2 * 128.0 * 128.0;
            final double claimed = soilShare + waters.ledger().fromAir(Pollutant.ACIDITY);
            helper.assertTrue(waters.ledger().fromAir(Pollutant.ACIDITY) > 0.0, "The lake took its share");
            helper.assertTrue(claimed <= fallen * (1.0 + 1e-9), "Soil and water together claim no more than fell on the cell: " + claimed + " of " + fallen);
            helper.succeed();
        }
        finally
        {
            for (BlockPos pos : placed)
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            air.setWeather(null);
            air.reset();
            release(level, chunk);
        }
    }
}
