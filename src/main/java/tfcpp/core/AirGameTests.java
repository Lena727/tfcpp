package tfcpp.core;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.chemistry.Chemistry;
import tfcpp.chemistry.CokeOvenBlockEntity;
import tfcpp.ecology.AirCadence;
import tfcpp.ecology.AirGrid;
import tfcpp.ecology.AirScale;
import tfcpp.ecology.AirSpecies;
import tfcpp.ecology.AirWeather;
import tfcpp.ecology.EcologyAttachments;
import tfcpp.ecology.LevelAirWeather;
import tfcpp.ecology.Pollutant;
import tfcpp.ecology.Pollution;
import tfcpp.ecology.RegionalAir;
import tfcpp.ecology.SoilOrder;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.gas.GasSimulator;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * Stage B of v1.5: the regional air. The weather is pinned with {@link RegionalAir#setWeather}, so that nothing depends on what the sky of the test world is doing, and every test has a batch of its own, because the regional air is one for the whole level and a test empties it first.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirGameTests
{
    private static final String TEMPLATE = "empty9";
    /** Chunks of the tests are in cells of the regional air that are 4 cells apart, in a row. */
    private static final int FIRST_CHUNK_X = 5000, CHUNK_Z = 5000, SLOT_CHUNKS = 32;

    private static LevelChunk chunk(ServerLevel level, int slot, int cellOffset)
    {
        final int chunkX = FIRST_CHUNK_X + slot * SLOT_CHUNKS + cellOffset * 8, chunkZ = CHUNK_Z;
        level.setChunkForced(chunkX, chunkZ, true);
        final LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        chunk.removeData(EcologyAttachments.CHUNK); // The test world is kept between runs, and these chunks with it
        chunk.removeData(tfcpp.pneumatics.gas.GasAttachments.CHUNK);
        return chunk;
    }

    private static void release(ServerLevel level, LevelChunk... chunks)
    {
        for (LevelChunk chunk : chunks)
        {
            level.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
        }
        RegionalAir.get(level).setWeather(null);
        RegionalAir.get(level).reset(); // Nothing of a test stays in the world for the next run
    }

    private static BlockPos spot(ServerLevel level, LevelChunk chunk, int above)
    {
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + above, z);
    }

    private static RegionalAir fresh(ServerLevel level, AirWeather weather)
    {
        final RegionalAir air = RegionalAir.get(level);
        air.reset();
        air.setWeather(weather);
        return air;
    }

    private static float[] gases(Gas gas, float moles)
    {
        final float[] gases = new float[Gas.COUNT];
        gases[gas.ordinal()] = moles;
        return gases;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_air_1")
    public static void sulfurDioxideReleasedUnderOpenSkyGoesIntoTheRegionalCellAndTheCarbonIsCounted(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 0, 0);
        try
        {
            final RegionalAir air = fresh(level, new AirWeather.Fixed(0, 0, false));
            final BlockPos at = spot(level, chunk, 6);
            final float[] flue = gases(Gas.SULFUR_DIOXIDE, 100f);
            flue[Gas.CARBON_DIOXIDE.ordinal()] = 30f;
            flue[Gas.CARBON_MONOXIDE.ordinal()] = 5f;
            flue[Gas.METHANE.ordinal()] = 2f;
            GasEmission.release(level, at, flue);

            final @Nullable AirGrid.Cell cell = air.cellAt(at.getX(), at.getZ());
            helper.assertTrue(cell != null && Math.abs(cell.moles(AirSpecies.SULFUR_DIOXIDE) - 100.0) < 1e-3, "The 100 mol of sulfur dioxide are in the regional cell, not discarded");
            helper.assertTrue(Math.abs(air.carbonMoles() - 35.0) < 1e-3, "30 mol of CO2 and 5 of CO are counted, got " + air.carbonMoles());
            helper.assertTrue(GasTracker.get(level).volume(level).cell(at.getX(), at.getY(), at.getZ()) == null, "and no cell of the gas grid was made for it");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_air_2")
    public static void aTallStackPutsItsSmokeIntoTheCellDownwindAndALowOneKeepsItsOwn(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 1, 0);
        try
        {
            final RegionalAir air = fresh(level, new AirWeather.Fixed(8, 1, false));
            final BlockPos low = spot(level, chunk, 8), tall = spot(level, chunk, 60);
            GasEmission.release(level, low, gases(Gas.SULFUR_DIOXIDE, 10f));
            helper.assertTrue(air.cellAt(low.getX(), low.getZ()).moles(AirSpecies.SULFUR_DIOXIDE) == 10.0, "A low outlet puts it over itself");
            helper.assertTrue(air.cellAt(low.getX() + 128, low.getZ()) == null || air.cellAt(low.getX() + 128, low.getZ()).moles(AirSpecies.SULFUR_DIOXIDE) == 0.0, "and nothing next door");
            GasEmission.release(level, tall, gases(Gas.SULFUR_DIOXIDE, 25f));
            final AirGrid.Cell next = air.cellAt(low.getX() + 128, low.getZ());
            helper.assertTrue(next != null && next.moles(AirSpecies.SULFUR_DIOXIDE) == 25.0, "A stack 60 m up puts it into the cell downwind");
            helper.assertTrue(air.cellAt(low.getX(), low.getZ()).moles(AirSpecies.SULFUR_DIOXIDE) == 10.0, "and none into its own");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_air_3")
    public static void afterHoursOfWindTheSoilOfADownwindChunkIsMoreAcidAndAnUntouchedOneStillClaimsItsShare(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk source = chunk(level, 2, 0), downwind = chunk(level, 2, 1), untouched = chunk(level, 2, 2);
        try
        {
            final RegionalAir air = fresh(level, new AirWeather.Fixed(10, 0, false));
            final BlockPos origin = spot(level, source, 6), there = spot(level, downwind, 6), far = spot(level, untouched, 6);
            Pollution.seed(level, there, SoilOrder.PODZOL, false);
            final double before = Pollution.view(level, there).total(Pollutant.ACIDITY);
            final long hour = AirCadence.hourOf(Pollution.now());
            air.stepTo(level, hour); // Dates the air
            GasEmission.release(level, origin, gases(Gas.SULFUR_DIOXIDE, 20_000f));
            helper.assertTrue(Pollution.view(level, there).total(Pollutant.ACIDITY) == before, "Nothing has fallen before an hour has passed");

            final long steps = air.stepTo(level, hour + 36);
            helper.assertTrue(steps == 36, "A day and a half of calendar is 36 steps, got " + steps);
            final double gained = Pollution.view(level, there).total(Pollutant.ACIDITY) - before;
            final double expected = air.cellAt(there.getX(), there.getZ()).cumulative(Pollutant.ACIDITY) * 256.0;
            helper.assertTrue(gained > 5.0, "The downwind soil has taken acid out of the air, got " + gained);
            helper.assertTrue(Math.abs(gained - expected) < expected * 0.02 + 0.5, "exactly its 256 square metres of what fell on the cell: " + expected + ", got " + gained);
            helper.assertTrue(!Pollution.isTouched(level, far), "No soil cell was made for a chunk just because something fell on it");
            helper.assertTrue(Pollution.view(level, far).total(Pollutant.ACIDITY) > 0.0, "but looking at it shows what it would claim");
            helper.assertTrue(Pollution.view(level, far).total(Pollutant.ACIDITY) == Pollution.view(level, far).total(Pollutant.ACIDITY), "and looking does not change it");
            Pollution.deposit(level, far, Pollutant.LEAD, 1.0);
            helper.assertTrue(Pollution.isTouched(level, far), "Touching it makes the cell, and the cell takes the whole history");
            helper.assertTrue(Pollution.view(level, far).total(Pollutant.ACIDITY) > 0.0, "with its acid");
            final AirGrid grid = air.grid();
            helper.assertTrue(Math.abs(grid.imbalance(AirSpecies.SULFUR_DIOXIDE)) < 1e-6 * 20_000, "The balance closes");
            helper.succeed();
        }
        finally
        {
            release(level, source, downwind, untouched);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_air_4")
    public static void aSealedRoomKeepsItsGasAndTakesNoneFromTheRegionalAir(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final RegionalAir air = fresh(level, new AirWeather.Fixed(0, 0, false));
        try
        {
            for (int x = 0; x <= 4; x++)
            {
                for (int y = 0; y <= 4; y++)
                {
                    for (int z = 0; z <= 4; z++)
                    {
                        helper.setBlock(x, y, z, x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4 ? Blocks.AIR : Blocks.OBSIDIAN);
                    }
                }
            }
            final BlockPos inside = helper.absolutePos(new BlockPos(2, 2, 2));
            air.grid().emit(inside.getX() >> 7, inside.getZ() >> 7, AirSpecies.SULFUR_DIOXIDE, 5000.0);
            final LevelGasVolume volume = GasTracker.get(level).volume(level);
            final float[] cell = volume.cellForEmission(inside.getX(), inside.getY(), inside.getZ());
            cell[Gas.SULFUR_DIOXIDE.ordinal()] = 3f;
            cell[Gas.CARBON_DIOXIDE.ordinal()] = 2f;
            final GasSimulator simulator = new GasSimulator();
            for (int i = 0; i < 20; i++)
            {
                simulator.stepCell(volume, inside.getX(), inside.getY(), inside.getZ(), 1f);
            }
            float sulfur = 0f, carbon = 0f;
            for (int x = 1; x <= 3; x++)
            {
                for (int y = 1; y <= 3; y++)
                {
                    for (int z = 1; z <= 3; z++)
                    {
                        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                        final @Nullable float[] part = volume.cell(pos.getX(), pos.getY(), pos.getZ());
                        if (part != null)
                        {
                            sulfur += part[Gas.SULFUR_DIOXIDE.ordinal()];
                            carbon += part[Gas.CARBON_DIOXIDE.ordinal()];
                        }
                    }
                }
            }
            helper.assertTrue(Math.abs(sulfur - 3f) < 1e-3f && Math.abs(carbon - 2f) < 1e-3f, "The room still holds its gas, and nothing from outside came in: " + sulfur + " and " + carbon);
            helper.assertTrue(air.cellAt(inside.getX(), inside.getZ()).moles(AirSpecies.SULFUR_DIOXIDE) == 5000.0, "and the regional air is as it was");
            helper.assertTrue(air.carbonMoles() == 0.0, "and no carbon dioxide was counted as let out");
            helper.succeed();
        }
        finally
        {
            release(level);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_air_5")
    public static void anOpenPitNextToSmogTakesItInFromTheRegionalCellAndLetsItsOwnGasOutIntoTheCounter(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 3, 0);
        try
        {
            final RegionalAir air = fresh(level, new AirWeather.Fixed(0, 0, false));
            final BlockPos at = spot(level, chunk, 2);
            air.grid().emit(at.getX() >> 7, at.getZ() >> 7, AirSpecies.SULFUR_DIOXIDE, 20_000.0);
            final double perMetre = air.cellAt(at.getX(), at.getZ()).molesPerCubicMetre(AirSpecies.SULFUR_DIOXIDE);
            final LevelGasVolume volume = GasTracker.get(level).volume(level);
            final float[] cell = volume.cellForEmission(at.getX(), at.getY(), at.getZ());
            cell[Gas.CARBON_DIOXIDE.ordinal()] = 4f; // Gas of its own that keeps the cell
            final GasSimulator simulator = new GasSimulator();
            for (int i = 0; i < 12; i++)
            {
                simulator.stepCell(volume, at.getX(), at.getY(), at.getZ(), 1f);
            }
            final float[] after = volume.cell(at.getX(), at.getY(), at.getZ());
            final double gained = after[Gas.SULFUR_DIOXIDE.ordinal()];
            helper.assertTrue(gained > 0.5 * perMetre && gained <= perMetre * 1.0001, "The cell relaxes towards the concentration outside, " + perMetre + ", and has " + gained);
            double everywhere = 0.0; // Some of it mixed on into the cells next to it
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values())
            {
                final @Nullable float[] next = volume.cell(at.getX() + side.getStepX(), at.getY() + side.getStepY(), at.getZ() + side.getStepZ());
                everywhere += next == null ? 0.0 : next[Gas.SULFUR_DIOXIDE.ordinal()];
            }
            helper.assertTrue(Math.abs((20_000.0 - air.cellAt(at.getX(), at.getZ()).moles(AirSpecies.SULFUR_DIOXIDE)) - gained - everywhere) < 1e-5 + 1e-3 * gained, "and everything it gained was taken from the regional cell: left " + air.cellAt(at.getX(), at.getZ()).moles(AirSpecies.SULFUR_DIOXIDE) + ", gained " + gained + ", next door " + everywhere + ", taken " + air.grid().taken(AirSpecies.SULFUR_DIOXIDE) + ", emitted " + air.grid().emitted(AirSpecies.SULFUR_DIOXIDE) + ", perMetre " + perMetre);
            helper.assertTrue(air.carbonMoles() > 1.0, "while its carbon dioxide went out into the counter, " + air.carbonMoles());
            helper.assertTrue(Math.abs(air.grid().taken(AirSpecies.SULFUR_DIOXIDE) - (air.grid().emitted(AirSpecies.SULFUR_DIOXIDE) - 20_000.0) - gained - everywhere) < 1e-5, "and the balance records what was drawn out, less what a room let back");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_air_6")
    public static void theCalendarJumpingByMoreThanAWeekFollowsAWeekAndPutsTheRestInTheGroundInPlace(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        try
        {
            final RegionalAir air = fresh(level, new AirWeather.Fixed(0, 0, false));
            final long hour = AirCadence.hourOf(Pollution.now());
            air.stepTo(level, hour);
            air.grid().emit(77777, 77777, AirSpecies.MERCURY_VAPOR, 400.0); // Mercury comes down slowly: some is left after a week
            final long steps = air.stepTo(level, hour + 5000);
            helper.assertTrue(steps == 168, "A week of steps, got " + steps);
            helper.assertTrue(air.forcedHours() == 5000 - 168, "and the rest counted, got " + air.forcedHours());
            helper.assertTrue(air.grid().inAir(AirSpecies.MERCURY_VAPOR) == 0.0, "Nothing is left in the air");
            helper.assertTrue(Math.abs(air.grid().imbalance(AirSpecies.MERCURY_VAPOR)) < 1e-6, "and the mercury is all accounted for");
            helper.assertTrue(air.grid().cellAt(77777, 77777).cumulative(Pollutant.MERCURY) > 0.0, "It is in the ground");

            final CompoundTag saved = air.save(new CompoundTag(), level.registryAccess());
            final RegionalAir loaded = RegionalAir.load(saved, level.registryAccess());
            helper.assertTrue(loaded.grid().cellAt(77777, 77777).cumulative(Pollutant.MERCURY) == air.grid().cellAt(77777, 77777).cumulative(Pollutant.MERCURY), "The fall comes back from saving");
            helper.assertTrue(loaded.forcedHours() == air.forcedHours() && loaded.grid().deposited(AirSpecies.MERCURY_VAPOR) == air.grid().deposited(AirSpecies.MERCURY_VAPOR), "with its totals");
            helper.succeed();
        }
        finally
        {
            release(level);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "tfcpp_air_7")
    public static void theSkyIsAskedWithoutLoadingAChunk(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelAirWeather sky = new LevelAirWeather(level);
        final AirGrid grid = new AirGrid();
        final int far = 900_000;
        final AirGrid.Cell cell = grid.cell(far >> 7, far >> 7);
        cell.setAnchor(far, 70, far);
        final AirWeather.Sample out = new AirWeather.Sample();
        sky.sample(cell, Pollution.now(), out);
        helper.assertTrue(level.getChunkSource().getChunkNow(far >> 4, far >> 4) == null, "Asking for the wind of a cell whose chunk is not loaded loaded no chunk");
        helper.assertTrue(out.windX == 0.0 && out.windZ == 0.0, "and with no wind ever found it is calm");
        helper.assertTrue(!cell.hasRain(), "No rainfall was worked out on the main thread");
        helper.assertTrue(!out.raining, "A world without TFC's rain has none");

        // At a loaded position it gives what TFC's own climate gives
        final LevelChunk chunk = chunk(level, 4, 0);
        try
        {
            final BlockPos at = spot(level, chunk, 1);
            final AirGrid.Cell near = grid.cell(at.getX() >> 7, at.getZ() >> 7);
            near.setAnchor(at.getX(), at.getY(), at.getZ());
            final long tick = Pollution.now();
            sky.sample(near, tick, out);
            final net.minecraft.world.phys.Vec2 wind = net.dries007.tfc.util.climate.Climate.get(level).getWind(level, new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ()), tick, net.dries007.tfc.util.calendar.Calendars.SERVER.getCalendarDaysInMonth());
            helper.assertTrue(Math.abs(out.windX - wind.x * 32.0) < 1e-4 && Math.abs(out.windZ - wind.y * 32.0) < 1e-4, "At a loaded place the wind is TFC's, times 32 m/s");
            helper.assertTrue(AirScale.metresPerBlock(LevelAirWeather.temperatureScale(level)) > 0, "and the scale of the map is read from the world");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "tfcpp_air_8")
    public static void aCokeOvenWithoutACondenserPutsItsTarAndLiquorIntoTheSoilOfItsChunk(GameTestHelper helper) throws ReflectiveOperationException
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 5, 0);
        final BlockPos pos = spot(level, chunk, 10);
        try
        {
            fresh(level, new AirWeather.Fixed(0, 0, false));
            Pollution.seed(level, pos, SoilOrder.PODZOL, false);
            level.setBlock(pos, Chemistry.COKE_OVEN.get().defaultBlockState(), 3);
            final CokeOvenBlockEntity oven = (CokeOvenBlockEntity) level.getBlockEntity(pos);
            oven.load(new ItemStack(item("tfc:ore/lignite"), 6));
            final java.lang.reflect.Field temperature = CokeOvenBlockEntity.class.getDeclaredField("temperature");
            temperature.setAccessible(true);
            temperature.setFloat(oven, 1000f);
            for (int i = 0; i < 20; i++)
            {
                oven.step(level, 60f);
            }
            helper.assertTrue(oven.tarTank().isEmpty() && oven.liquorTank().isEmpty(), "There is no condenser, so nothing was collected");
            final double tar = Pollution.view(level, pos).total(Pollutant.TAR);
            final double expectedTar = 6 * 0.24 * 1.19 + 6 * 0.4 * 0.002; // litres of tar and of liquor of 6 pieces of lignite, as in the batch test
            helper.assertTrue(Math.abs(tar - expectedTar) < expectedTar * 0.02, "6 pieces of lignite soak " + expectedTar + " kg of tar and phenol, got " + tar);
            helper.assertTrue(Pollution.view(level, pos).totalInput(Pollutant.ACIDITY) > 1.0, "and the ammonia of the liquor acidifies the soil");
            helper.assertTrue(RegionalAir.get(level).carbonMoles() > 30.0, "while its carbon dioxide goes into the counter");
            helper.succeed();
        }
        finally
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            release(level, chunk);
        }
    }

    private static void tower(ServerLevel level, int cx, int cz, int groundY, int height, boolean hollow)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (hollow && dx == 0 && dz == 0)
                {
                    continue;
                }
                for (int y = groundY; y < groundY + height; y++)
                {
                    level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_air_9")
    public static void aHollowBrickChimneyOverTwentyMetresIsATallStackAndAForgeOnAPillarIsNot(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 6, 0);
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        final int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        try
        {
            final RegionalAir air = fresh(level, new AirWeather.Fixed(8, 1, false));
            // A flue of 30 m in a 3 by 3 of brick, with the gas coming out at its foot
            tower(level, x, z, ground, 30, true);
            GasEmission.release(level, new BlockPos(x, ground, z), gases(Gas.SULFUR_DIOXIDE, 40f));
            final @Nullable AirGrid.Cell own = air.cellAt(x, z), next = air.cellAt(x + 128, z);
            helper.assertTrue(next != null && next.moles(AirSpecies.SULFUR_DIOXIDE) == 40.0, "Gas from the foot of a 30 m flue goes into the cell downwind");
            helper.assertTrue(own == null || own.moles(AirSpecies.SULFUR_DIOXIDE) == 0.0, "and not into its own");
            tower(level, x, z, ground, 30, false); // Fills the flue: now a solid pillar of 30 m, and a device on top of it
            level.setBlock(new BlockPos(x, ground + 30, z), Blocks.AIR.defaultBlockState(), 2);
            GasEmission.release(level, new BlockPos(x, ground + 30, z), gases(Gas.SULFUR_DIOXIDE, 7f));
            helper.assertTrue(air.cellAt(x, z) != null && air.cellAt(x, z).moles(AirSpecies.SULFUR_DIOXIDE) == 7.0, "A forge on top of a pillar is measured against the ground at its foot wall, so it is not a stack");
            helper.assertTrue(next.moles(AirSpecies.SULFUR_DIOXIDE) == 40.0, "and sent nothing downwind");
            // Hydrogen sulfide goes up as sulfur dioxide and chlorine as hydrochloric acid
            final float[] mixed = gases(Gas.HYDROGEN_SULFIDE, 3f);
            mixed[Gas.CHLORINE.ordinal()] = 2f;
            GasEmission.release(level, new BlockPos(x, ground + 30, z), mixed);
            helper.assertTrue(air.cellAt(x, z).moles(AirSpecies.SULFUR_DIOXIDE) == 10.0 && air.cellAt(x, z).moles(AirSpecies.HYDROGEN_CHLORIDE) == 4.0, "3 mol of H2S are 3 of SO2 and 2 of Cl2 are 4 of HCl");
            helper.succeed();
        }
        finally
        {
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    for (int y = ground; y < ground + 31; y++)
                    {
                        level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            release(level, chunk);
        }
    }

    private static Item item(String id)
    {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }
}
