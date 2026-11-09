package tfcpp.core;

import java.lang.reflect.Field;
import java.util.Optional;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.world.settings.RockSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.chemistry.AcidVapour;
import tfcpp.chemistry.Chemistry;
import tfcpp.chemistry.CokeOvenBlockEntity;
import tfcpp.chemistry.GlassRetortBlockEntity;
import tfcpp.chemistry.LeadChamberBlockEntity;
import tfcpp.chemistry.Liquid;
import tfcpp.ecology.EcologyAttachments;
import tfcpp.ecology.Pollutant;
import tfcpp.ecology.Pollution;
import tfcpp.ecology.PollutionChunkData;
import tfcpp.ecology.Soakable;
import tfcpp.ecology.SoilCell;
import tfcpp.ecology.SoilConstants;
import tfcpp.ecology.SoilOrder;
import tfcpp.ecology.SoilReader;
import tfcpp.metallurgy.CondensationTowerBlockEntity;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipePhysics;
import tfcpp.pneumatics.pipes.PipeSegment;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.PistonPumpBlockEntity;
import tfcpp.pneumatics.pipes.Pipes;

/**
 * Stage A of v1.5: the soil memory. Every test works in a chunk of its own, far from the test and from every other, because the memory is kept for a whole chunk and tests that run together would otherwise add to one another. The blocks are built in the air above the ground, so that nothing else is near them.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EcologyGameTests
{
    private static final String TEMPLATE = "empty9";
    private static final String BATCH = "tfcpp_ecology";
    private static final double DAY_TICKS = SoilConstants.CALENDAR_TICKS_PER_DAY;
    private static final int FIRST_CHUNK_X = 3000, CHUNK_Z = 3000;

    /** A chunk at fixed coordinates, whatever the origin of the test, which no other test uses: they are 8 chunks apart in a row, so that no two tests can share a chunk. Kept loaded until released. */
    private static LevelChunk farChunk(GameTestHelper helper, int slot)
    {
        final ServerLevel level = helper.getLevel();
        final int chunkX = FIRST_CHUNK_X + slot * 8, chunkZ = CHUNK_Z;
        level.setChunkForced(chunkX, chunkZ, true);
        tfcpp.ecology.RegionalAir.get(level).clearCellAt(chunkX << 4, chunkZ << 4); // Nothing has fallen on the site before this test
        final LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        chunk.removeData(EcologyAttachments.CHUNK); // The test world is kept between runs, and these chunks with it
        return chunk;
    }

    private static void release(ServerLevel level, LevelChunk chunk)
    {
        level.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
    }

    /** A place in the air high above the middle of the chunk. */
    private static BlockPos spot(ServerLevel level, LevelChunk chunk)
    {
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 10, z);
    }

    private static Fluid fluidOf(String id)
    {
        return BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
    }

    private static double acidityOf(ServerLevel level, BlockPos pos)
    {
        return Pollution.view(level, pos).total(Pollutant.ACIDITY);
    }

    // --- A burst pipe ----------------------------------------------------------------------------------------------------------------------------------------------------

    private static void burst(ServerLevel level, BlockPos hole, String fluid, float millibuckets)
    {
        final Block lead = Pipes.PIPES.get(PipeMaterial.LEAD).get();
        level.setBlock(hole, lead.defaultBlockState(), 3);
        level.setBlock(hole.east(), lead.defaultBlockState(), 3);
        final PipeTracker tracker = PipeTracker.get(level);
        final PipeSegment segment = tracker.segmentAt(hole);
        segment.integrity = 0f; // The wall is gone, so the next step bursts it
        segment.stored.addLiquid(fluid, millibuckets, PipePhysics.ROOM_TEMPERATURE);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
    public static void aBurstPipeOfAcidRaisesTheAcidityOfItsChunkByTheEquivalentsThatLeft(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 0);
        final BlockPos hole = spot(level, chunk);
        Pollution.seed(level, hole, SoilOrder.PODZOL, false);
        final double before = acidityOf(level, hole);
        final float millibuckets = 5f;
        burst(level, hole, "tfcpp:chamber_acid", millibuckets);

        helper.runAfterDelay(60, () -> {
            try
            {
                helper.assertTrue(level.getBlockState(hole).isAir(), "The burst pipe is gone");
                final double expected = millibuckets * Soakable.CHAMBER_ACID.load(1.0)[Pollutant.ACIDITY.ordinal()];
                final double gained = acidityOf(level, hole) - before;
                helper.assertTrue(expected > 100.0, "Five litres of chamber acid are about 100 equivalents, not " + expected);
                helper.assertTrue(Math.abs(gained - expected) < expected * 0.01, "The chunk gains the " + expected + " equivalents that left the pipe, got " + gained);
                helper.succeed();
            }
            finally
            {
                release(level, chunk);
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
    public static void aBurstPipeSoaksWhatIsLeftOfAWholeBlockOfWaterWithoutARecordAndAllOfAMetalWithOne(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk water = farChunk(helper, 1), mercury = farChunk(helper, 2);
        final BlockPos waterHole = spot(level, water), mercuryHole = spot(level, mercury);
        helper.assertTrue(!Pollution.isTouched(level, waterHole) && !Pollution.isTouched(level, mercuryHole), "Nothing has touched either chunk");
        burst(level, waterHole, "minecraft:water", 1500f);
        burst(level, mercuryHole, "tfcpp:mercury", 40f);

        helper.runAfterDelay(60, () -> {
            try
            {
                helper.assertTrue(!Pollution.isTouched(level, waterHole), "Water leaves no record, whether a block of it was put down or not");
                final SoilCell cell = Pollution.view(level, mercuryHole);
                final double expected = 40.0 * Soakable.MERCURY_DENSITY;
                helper.assertTrue(Math.abs(cell.total(Pollutant.MERCURY) - expected) < 0.01, "40 ml of mercury are " + expected + " g, got " + cell.total(Pollutant.MERCURY));
                helper.assertTrue(Pollution.isTouched(level, mercuryHole), "and the chunk keeps the record");
                helper.succeed();
            }
            finally
            {
                release(level, water);
                release(level, mercury);
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
    public static void aLiquidWithNoBlockOfItsOwnIsSoakedWholeEvenInWholeBlocks(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 3);
        final BlockPos hole = spot(level, chunk);
        Pollution.seed(level, hole, SoilOrder.PODZOL, false);
        burst(level, hole, "tfcpp:coal_tar", 2500f);

        helper.runAfterDelay(60, () -> {
            try
            {
                final double tar = Pollution.view(level, hole).total(Pollutant.TAR);
                final double expected = 2500.0 * Liquid.COAL_TAR.kilogramsPerLitre();
                helper.assertTrue(Math.abs(tar - expected) < expected * 0.01, "2500 L of coal tar are " + expected + " kg and all of it is in the soil, got " + tar);
                helper.succeed();
            }
            finally
            {
                release(level, chunk);
            }
        });
    }

    // --- The other places where liquid went into the ground ----------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aBrokenChamberCokeOvenAndFlaskPutTheirLiquidsIntoTheSoil(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 4);
        final BlockPos base = spot(level, chunk);
        try
        {
            Pollution.seed(level, base, SoilOrder.PODZOL, false);

            // A lead chamber, 50 L of acid and a part of a litre waiting to be counted
            final BlockPos chamberPos = base;
            level.setBlock(chamberPos, Chemistry.LEAD_CHAMBER.get().defaultBlockState(), 3);
            final LeadChamberBlockEntity chamber = (LeadChamberBlockEntity) level.getBlockEntity(chamberPos);
            chamber.tank().fill(new FluidStack(Chemistry.fluid(Liquid.CHAMBER_ACID), 50), IFluidHandler.FluidAction.EXECUTE);
            final double beforeChamber = acidityOf(level, base);
            chamber.spill(level);
            final double gotChamber = acidityOf(level, base) - beforeChamber;
            final double perLitre = Soakable.CHAMBER_ACID.load(1.0)[Pollutant.ACIDITY.ordinal()];
            helper.assertTrue(Math.abs(gotChamber - 50 * perLitre) < 50 * perLitre * 0.001, "50 L of acid from the chamber, got " + gotChamber + " of " + 50 * perLitre);

            // A coke oven with tar and liquor in its tanks
            final BlockPos ovenPos = base.east(3);
            level.setBlock(ovenPos, Chemistry.COKE_OVEN.get().defaultBlockState(), 3);
            final CokeOvenBlockEntity oven = (CokeOvenBlockEntity) level.getBlockEntity(ovenPos);
            oven.tarTank().fill(new FluidStack(Chemistry.fluid(Liquid.COAL_TAR), 20), IFluidHandler.FluidAction.EXECUTE);
            oven.liquorTank().fill(new FluidStack(Chemistry.fluid(Liquid.AMMONIA_LIQUOR), 100), IFluidHandler.FluidAction.EXECUTE);
            final double tarBefore = Pollution.view(level, base).total(Pollutant.TAR);
            level.destroyBlock(ovenPos, false);
            final double gotTar = Pollution.view(level, base).total(Pollutant.TAR) - tarBefore;
            final double expectedTar = 20 * 1.19 + 100 * 0.002;
            helper.assertTrue(Math.abs(gotTar - expectedTar) < 0.01, "20 L of coal tar and 100 L of liquor are " + expectedTar + " kg, got " + gotTar);

            // A flask with oil of vitriol in it, which gives no fume, and nitric acid, which gives a quarter of it
            final BlockPos flaskPos = base.east(6);
            level.setBlock(flaskPos, Chemistry.GLASS_RETORT.get().defaultBlockState(), 3);
            final GlassRetortBlockEntity flask = (GlassRetortBlockEntity) level.getBlockEntity(flaskPos);
            flask.input().fill(new FluidStack(Chemistry.fluid(Liquid.SULFURIC_ACID), 4), IFluidHandler.FluidAction.EXECUTE);
            flask.receiver().fill(new FluidStack(Chemistry.fluid(Liquid.NITRIC_ACID), 6), IFluidHandler.FluidAction.EXECUTE);
            final double flaskBefore = acidityOf(level, base);
            flask.spill(level);
            final double gotFlask = acidityOf(level, base) - flaskBefore;
            final double expectedFlask = 4 * Soakable.SULFURIC_ACID.load(1.0)[Pollutant.ACIDITY.ordinal()] + AcidVapour.unvapourised(Liquid.NITRIC_ACID, 6.0) * Soakable.NITRIC_ACID.load(1.0)[Pollutant.ACIDITY.ordinal()];
            helper.assertTrue(Math.abs(gotFlask - expectedFlask) < expectedFlask * 0.001, "4 L of oil of vitriol and what did not fume of 6 L of nitric acid: " + expectedFlask + ", got " + gotFlask);
            helper.assertTrue(gotFlask < 4 * 34.7 + 6 * 21.14 - 25.0, "The fume that went up is not counted twice");
            helper.succeed();
        }
        finally
        {
            for (int dx : new int[] {0, 3, 6})
            {
                level.setBlock(base.east(dx), Blocks.AIR.defaultBlockState(), 3);
            }
            release(level, chunk);
        }
    }

    // --- Soils -------------------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void theSameSpillDropsAPodzolOverGraniteFarMoreThanAMollisolOverLimestone(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk acid = farChunk(helper, 5), limestone = farChunk(helper, 6);
        final BlockPos acidPos = spot(level, acid), limestonePos = spot(level, limestone);
        try
        {
            final double podzolBefore = Pollution.seed(level, acidPos, SoilOrder.PODZOL, false).pH();
            final double limestoneBefore = Pollution.seed(level, limestonePos, SoilOrder.MOLLISOL, true).pH();
            final Fluid sulfuric = Chemistry.fluid(Liquid.SULFURIC_ACID);
            Pollution.soak(level, acidPos, sulfuric, 150);
            Pollution.soak(level, limestonePos, sulfuric, 150);
            final double podzolDrop = podzolBefore - Pollution.view(level, acidPos).pH();
            final double limestoneDrop = limestoneBefore - Pollution.view(level, limestonePos).pH();
            helper.assertTrue(podzolDrop > 0.3, "150 L of oil of vitriol sour the podzol, got a drop of " + podzolDrop);
            helper.assertTrue(podzolDrop > 5 * limestoneDrop, "and the limestone takes them: " + podzolDrop + " against " + limestoneDrop);
            helper.assertTrue(Pollution.view(level, limestonePos).total(Pollutant.ACIDITY) == 0.0, "None of the acid is left in the carbonate soil: it was neutralised");
            helper.succeed();
        }
        finally
        {
            release(level, acid);
            release(level, limestone);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void theSurfaceSoilAndTheRockAreReadFromTfc(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 7);
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        final BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 20, z); // A pillar of our own, above whatever grows there
        try
        {
            for (SoilOrder order : new SoilOrder[] {SoilOrder.PODZOL, SoilOrder.MOLLISOL, SoilOrder.ARIDISOL})
            {
                final SoilBlockType.Variant variant = SoilBlockType.Variant.valueOf(order.name());
                final BlockState state = TFCBlocks.SOIL.get(SoilBlockType.GRASS).get(variant).get().defaultBlockState();
                level.setBlock(top, state, 3);
                helper.assertTrue(SoilReader.surfaceOrder(level, chunk.getPos()) == order, "A " + order + " grass block at the surface is read as " + SoilReader.surfaceOrder(level, chunk.getPos()));
            }
            level.setBlock(top, Blocks.AIR.defaultBlockState(), 3);

            for (Rock rock : Rock.values())
            {
                final Block raw = TFCBlocks.ROCK_BLOCKS.get(rock).get(Rock.BlockType.RAW).get();
                final RockSettings settings = new RockSettings(raw, raw, raw, raw, raw, raw, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                final boolean carbonate = rock == Rock.LIMESTONE || rock == Rock.DOLOMITE || rock == Rock.CHALK || rock == Rock.MARBLE;
                helper.assertTrue(SoilReader.isCarbonate(settings) == carbonate, rock + (carbonate ? " is a carbonate" : " is not a carbonate"));
            }
            helper.succeed();
        }
        finally
        {
            level.setBlock(top, Blocks.AIR.defaultBlockState(), 3);
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void everyLiquidThatLeavesARecordIsFoundByItsFluid(GameTestHelper helper)
    {
        for (Liquid liquid : Liquid.values())
        {
            final @Nullable Soakable expected = Soakable.of(liquid);
            helper.assertTrue(Pollution.soakableOf(Chemistry.fluid(liquid)) == expected, liquid + " is " + expected);
            helper.assertTrue(Pollution.soakableOf(Chemistry.LIQUIDS.get(liquid).flowing.get()) == expected, "and so is its flowing form");
        }
        helper.assertTrue(Pollution.soakableOf(Metallurgy.MERCURY.get()) == Soakable.MERCURY, "mercury");
        helper.assertTrue(Pollution.soakableOf(Metallurgy.LEAD.get()) == Soakable.LEAD, "lead");
        helper.assertTrue(Pollution.soakableOf(Metallurgy.WORK_LEAD.get()) == Soakable.LEAD, "work lead is lead");
        helper.assertTrue(Pollution.soakableOf(Metallurgy.ARSENICAL_COPPER.get()) == Soakable.ARSENICAL_COPPER, "arsenical copper");
        helper.assertTrue(Pollution.soakableOf(TFCFluids.METALS.get(Metal.ZINC).getSource()) == Soakable.ZINC, "zinc");
        helper.assertTrue(Pollution.soakableOf(TFCFluids.METALS.get(Metal.COPPER).getSource()) == Soakable.COPPER, "pure copper");
        helper.assertTrue(Pollution.soakableOf(TFCFluids.METALS.get(Metal.BRASS).getSource()) == Soakable.BRASS, "brass");
        helper.assertTrue(Pollution.soakableOf(TFCFluids.METALS.get(Metal.NICKEL).getSource()) == Soakable.NICKEL, "nickel");
        helper.assertTrue(Pollution.soakableOf(TFCFluids.METALS.get(Metal.TIN).getSource()) == null, "tin is not a pollutant here");
        helper.assertTrue(Pollution.soakableOf(Fluids.WATER) == null && Pollution.soakableOf(fluidOf("tfc:salt_water")) == null, "water leaves none");
        helper.succeed();
    }

    // --- Memory --------------------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void nothingIsRecordedForAChunkThatWasNeverTouched(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 8);
        final BlockPos pos = spot(level, chunk);
        try
        {
            helper.assertTrue(!Pollution.isTouched(level, pos), "A new chunk has no cell");
            Pollution.view(level, pos);
            helper.assertTrue(!Pollution.isTouched(level, pos) && !chunk.hasData(EcologyAttachments.CHUNK), "Looking at one makes none");
            Pollution.deposit(level, pos, Pollutant.LEAD, 0.0);
            Pollution.soak(level, pos, Fluids.WATER, 1000);
            Pollution.soak(level, pos, FluidStack.EMPTY);
            helper.assertTrue(!Pollution.isTouched(level, pos), "Nothing, water or an empty stack make one");
            helper.assertTrue(PollutionChunkData.SERIALIZER.write(new PollutionChunkData(), level.registryAccess()) == null, "and a chunk with no cell saves nothing");

            Pollution.deposit(level, pos, Pollutant.LEAD, 10.0);
            helper.assertTrue(Pollution.isTouched(level, pos) && Pollution.view(level, pos).total(Pollutant.LEAD) == 10.0, "Putting lead into it makes one");
            helper.assertTrue(!Pollution.isTouched(level, pos.offset(16, 0, 0)), "and the chunk beside it is still clean");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aCellSurvivesSavingAndLoadingAndCatchesUpTheTimeItWasAway(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 9);
        final BlockPos pos = spot(level, chunk);
        try
        {
            Pollution.seed(level, pos, SoilOrder.PODZOL, false);
            Pollution.deposit(level, pos, Pollutant.TAR, 100.0);
            Pollution.deposit(level, pos, Pollutant.SEWAGE, 1000.0);
            Pollution.deposit(level, pos, Pollutant.LEAD, 500.0);
            Pollution.deposit(level, pos, Pollutant.ACIDITY, 2000.0);
            final SoilCell before = Pollution.view(level, pos);

            // Saved, then loaded again half a year later by the calendar
            final CompoundTag saved = PollutionChunkData.SERIALIZER.write(chunk.getData(EcologyAttachments.CHUNK), level.registryAccess());
            helper.assertTrue(saved != null, "A touched chunk saves its cell");
            saved.putLong("lastTick", saved.getLong("lastTick") - (long) (180 * DAY_TICKS));
            chunk.setData(EcologyAttachments.CHUNK, PollutionChunkData.SERIALIZER.read(chunk, saved, level.registryAccess()));
            helper.assertTrue(Math.abs(chunk.getData(EcologyAttachments.CHUNK).cell().total(Pollutant.TAR) - 100.0) < 1e-6, "Loading takes nothing by itself: the cell is as it was saved");

            final SoilCell after = Pollution.view(level, pos); // Looking at it brings it up to date, as touching it does
            Pollution.deposit(level, pos, Pollutant.LEAD, 1.0);
            helper.assertTrue(Math.abs(chunk.getData(EcologyAttachments.CHUNK).cell().total(Pollutant.TAR) - after.total(Pollutant.TAR)) < 1e-6, "and touching it keeps what looking at it showed");
            helper.assertTrue(Math.abs(after.total(Pollutant.TAR) - 50.0) < 0.01, "Tar has lost half in its half-life of 180 days, got " + after.total(Pollutant.TAR));
            helper.assertTrue(Math.abs(after.total(Pollutant.SEWAGE) - 1000.0 * Math.pow(10.0, -180.0 / 21.0)) < 0.01, "and the sewage has died out, got " + after.total(Pollutant.SEWAGE));
            helper.assertTrue(after.total(Pollutant.LEAD) == 500.0, "Lead is where it was");
            final double weathered = SoilConstants.WEATHERING_PER_YEAR * 180.0 / 365.0 * SoilConstants.AREA;
            helper.assertTrue(Math.abs((before.total(Pollutant.ACIDITY) - after.total(Pollutant.ACIDITY)) - weathered) < 0.01, "The acid is weathered at 0.01 eq/m2 a year: " + weathered + ", got " + (before.total(Pollutant.ACIDITY) - after.total(Pollutant.ACIDITY)));
            helper.assertTrue(Math.abs(after.pH() - before.pH()) < 0.01, "and the pH is where it was but for that, " + before.pH() + " and " + after.pH());
            helper.assertTrue(Math.abs(after.lastTick() - Pollution.now()) <= 1, "The cell is dated by the calendar now");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    // --- Hooks added in review ---------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
    public static void whatIsBeyondTheSixtyFourBlocksOfASpillSoaksIntoTheGround(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 20);
        final BlockPos hole = spot(level, chunk);
        final Fluid zinc = TFCFluids.METALS.get(Metal.ZINC).getSource();
        final float millibuckets = 70_000f;
        for (BlockPos pos : BlockPos.betweenClosed(hole.offset(-12, -12, -12), hole.offset(12, 12, 12)))
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); // Whatever an earlier run left here
        }
        burst(level, hole, "tfc:metal/zinc", millibuckets);

        helper.runAfterDelay(60, () -> {
            try
            {
                final BlockState block = zinc.defaultFluidState().createLegacyBlock();
                helper.assertTrue(!block.isAir(), "Molten zinc has a block of its own, which is what makes this a test of the cap");
                int placed = 0;
                for (BlockPos pos : BlockPos.betweenClosed(hole.offset(-10, -10, -10), hole.offset(10, 10, 10)))
                {
                    placed += level.getFluidState(pos).getType() == zinc ? 1 : 0; // The sources: what flows out of them is not another block put down
                }
                helper.assertTrue(placed == 64, "At most 64 blocks are put down, got " + placed);
                final double expected = (millibuckets - placed * 1000.0) * Soakable.ZINC_DENSITY;
                final double soaked = Pollution.view(level, hole).total(Pollutant.ZINC);
                helper.assertTrue(Math.abs(soaked - expected) < expected * 0.001, "and the 6 L beyond them soak in: " + expected + " g, got " + soaked);
                helper.succeed();
            }
            finally
            {
                release(level, chunk);
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void anOverflowThatPutsDownOneBlockSoaksExactlyOneBlockOfALiquidWithNoBlock(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 21);
        final BlockPos outlet = spot(level, chunk);
        try
        {
            Pollution.seed(level, outlet, SoilOrder.PODZOL, false);
            final double before = acidityOf(level, outlet);
            helper.assertTrue(PipeTracker.placeBlock(level, outlet, Chemistry.fluid(Liquid.CHAMBER_ACID)), "A block of acid is put down");
            final double perBlock = 1000.0 * Soakable.CHAMBER_ACID.load(1.0)[Pollutant.ACIDITY.ordinal()];
            helper.assertTrue(Math.abs(acidityOf(level, outlet) - before - perBlock) < perBlock * 1e-6, "and a thousand litres of it are in the soil, not more and not less: " + perBlock);
            helper.assertTrue(PipeTracker.placeBlock(level, outlet, Fluids.WATER), "A block of water is put down");
            helper.assertTrue(Math.abs(acidityOf(level, outlet) - before - perBlock) < perBlock * 1e-6, "and that one adds nothing");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aColdTowerPutsItsMercuryIntoTheSoilAndAPumpItsHeldLiquid(GameTestHelper helper) throws ReflectiveOperationException
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 22);
        final BlockPos base = spot(level, chunk);
        try
        {
            final BlockPos towerPos = base, pumpPos = base.east(4);
            level.setBlock(towerPos, Metallurgy.CONDENSATION_TOWER.get().defaultBlockState(), 3);
            final CondensationTowerBlockEntity tower = (CondensationTowerBlockEntity) level.getBlockEntity(towerPos);
            tower.tank().fill(new FluidStack(Metallurgy.MERCURY.get(), 30), IFluidHandler.FluidAction.EXECUTE);
            level.destroyBlock(towerPos, false);
            double mercury = Pollution.view(level, base).total(Pollutant.MERCURY);
            helper.assertTrue(Math.abs(mercury - 30 * Soakable.MERCURY_DENSITY) < 1e-6, "30 ml of cold mercury are " + 30 * Soakable.MERCURY_DENSITY + " g, got " + mercury);

            level.setBlock(pumpPos, Pipes.PISTON_PUMP.get().defaultBlockState(), 3);
            final PistonPumpBlockEntity pump = (PistonPumpBlockEntity) level.getBlockEntity(pumpPos);
            final Field litres = PistonPumpBlockEntity.class.getDeclaredField("heldLitres"), liquid = PistonPumpBlockEntity.class.getDeclaredField("heldLiquid");
            litres.setAccessible(true);
            liquid.setAccessible(true);
            litres.setFloat(pump, 25f);
            liquid.set(pump, "tfcpp:mercury");
            level.destroyBlock(pumpPos, false);
            mercury = Pollution.view(level, base).total(Pollutant.MERCURY) - mercury;
            helper.assertTrue(Math.abs(mercury - 25 * Soakable.MERCURY_DENSITY) < 1e-6, "and what the pump held between strokes goes the same way, got " + mercury);
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }
}
