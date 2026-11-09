package tfcpp.core;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.FirepitBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.CreativeRotationBlockEntity;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.blocks.wood.Wood;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.component.glass.GlassOperation;
import net.dries007.tfc.common.component.glass.GlassWorking;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.common.recipes.GlassworkingRecipe;
import net.dries007.tfc.util.data.Fertilizer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import tfcpp.chemistry.BorosilicateGlass;
import tfcpp.metallurgy.Hearth;
import tfcpp.chemistry.Distillation;
import tfcpp.chemistry.DistillationRecipe;
import tfcpp.chemistry.DistillationTable;
import tfcpp.chemistry.GasBurnerBlock;
import tfcpp.chemistry.GlassRetortBlockEntity;
import tfcpp.chemistry.SandBath;
import tfcpp.chemistry.SandBathBlock;
import tfcpp.chemistry.SandBathBlockEntity;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.instruments.GoldLeafIndicatorBlock;
import tfcpp.pneumatics.instruments.GoldLeafIndicatorBlockEntity;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.chemistry.ChamberProcess;
import tfcpp.chemistry.Chemistry;
import tfcpp.chemistry.CorrosiveSpill;
import tfcpp.chemistry.LeadChamberBlockEntity;
import tfcpp.chemistry.Liquid;
import tfcpp.chemistry.MarshApparatusItem;
import tfcpp.chemistry.PoisonTests;
import tfcpp.chemistry.SulfurBurnerBlockEntity;
import tfcpp.chemistry.SulfurBurning;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.pipes.PipeContents;
import tfcpp.pneumatics.pipes.PipeFittingBlock;
import tfcpp.pneumatics.pipes.PipePort;
import tfcpp.pneumatics.pipes.PipeSegment;
import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePhysics;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.Pipes;
import tfcpp.pneumatics.pipes.PistonPumpBlockEntity;

/**
 * Stage E of v1.0: the lead chamber that makes sulfuric acid from sulfur dioxide, air, water and the oxides of nitrogen, the sulfur burner that makes the sulfur dioxide, and what the acid does once it has made it: it is pumped out, and it splashes whoever is by it when it runs out.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AcidGameTests
{
    private static final String TEMPLATE = "empty9";
    /** The chemistry on its own: the calendar stands still, and nothing here explodes. */
    private static final String BATCH = "tfcpp_acid";
    /** Apart from the others, as this sets the calendar for the corrosion of the pipes. */
    private static final String TIME_BATCH = "tfcpp_acid_time";
    /** Apart from the others, as these break blocks and hurt players. */
    private static final String BURST_BATCH = "tfcpp_acid_burst";
    private static final BlockPos CHAMBER = new BlockPos(2, 1, 2);
    private static final String WATER = "minecraft:water";
    private static final String ACID = "tfcpp:chamber_acid";
    /** The rate IronGameTests keeps the calendar at, for the corrosion of the pipes. */
    private static final float REAL_TIME_RATE = 20f / (24f * 60f);

    /** A test player that is in the level, so that a splash finds it, and records the damage it is dealt instead of taking it. */
    private static final class TestPlayer extends FakePlayer
    {
        @Nullable DamageSource lastDamage;

        TestPlayer(GameTestHelper helper)
        {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
        }

        @Override
        public boolean hurt(DamageSource source, float amount)
        {
            lastDamage = source;
            return true;
        }
    }

    /** A player stood at this place in the test, and added to the level so that it is found by the splash. */
    private static TestPlayer playerAt(GameTestHelper helper, BlockPos pos)
    {
        final TestPlayer player = new TestPlayer(helper);
        final BlockPos absolute = helper.absolutePos(pos);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    private static FakePlayer player(GameTestHelper helper)
    {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
    }

    @Nullable
    private static PipeNetwork networkAt(GameTestHelper helper, BlockPos pos)
    {
        return PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(pos));
    }

    private static Fluid acid()
    {
        return Chemistry.fluid(Liquid.CHAMBER_ACID);
    }

    private static Block ceramicPipe()
    {
        return Pipes.PIPES.get(PipeMaterial.CERAMIC).get();
    }

    private static Item saltpeter()
    {
        return TFCItems.ORE_POWDERS.get(Ore.SALTPETER).asItem();
    }

    private static Item sulfur()
    {
        return TFCItems.ORE_POWDERS.get(Ore.SULFUR).asItem();
    }

    /**
     * Puts the gas, air and water of a network into it, as a test wants them, and leaves its temperature as it was. The water comes in at room temperature, and 20 L of it outweighs the gas in heat capacity by seventy to one, so it would drag the network to room temperature for as long as it takes the next tick's {@link #keepAt} to pin it again. A step of the network falling in that one tick works the chamber at 20 C instead of the frost the test pinned: the chance is one in twenty, and it was the flake of {@code aChamberInFrostMakesNoAcid...}.
     */
    private static void charge(PipeNetwork network, float air, float sulfurDioxide, float oxides, float water)
    {
        final PipeContents contents = network.contents();
        final float temperature = contents.temperature;
        contents.air = air;
        contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] = sulfurDioxide;
        contents.gases[Gas.NITROGEN_OXIDES.ordinal()] = oxides;
        if (water > 0f)
        {
            contents.addLiquid(WATER, water, PipePhysics.ROOM_TEMPERATURE);
        }
        contents.temperature = temperature;
    }

    /** @return litres of acid per second that the chamber process makes in this network, for the gas it holds now and the blocks of chamber in it */
    private static float litresPerSecond(PipeNetwork network, int chambers, float kelvin)
    {
        final PipeContents contents = network.contents();
        final float moles = contents.gasMoles();
        final float rate = ChamberProcess.rate(
            contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] / moles,
            contents.gases[Gas.NITROGEN_OXIDES.ordinal()] / moles,
            contents.air * ChamberProcess.OXYGEN_IN_AIR / moles);
        return chambers * rate * ChamberProcess.ACID_LITRES_PER_MOLE * ChamberProcess.temperatureFactor(kelvin);
    }

    /** Holds the network at this position at this temperature, in Kelvin, every tick: a chamber works at its temperature, and a network cools towards the weather. */
    private static void keepAt(GameTestHelper helper, BlockPos pos, float kelvin)
    {
        helper.onEachTick(() -> {
            final @Nullable PipeNetwork network = networkAt(helper, pos);
            if (network != null)
            {
                network.contents().temperature = kelvin;
            }
        });
    }

    /** A chamber at the warmth the process is fitted at and above. */
    private static final float WARM = 345f;

    /** @return litres of acid the chamber has made and holds, counting the fraction that is still waiting to be whole */
    private static float acidOf(LeadChamberBlockEntity chamber)
    {
        return chamber.tank().getFluidAmount() + chamber.pendingMillibuckets();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH)
    public static void aChamberGainsAcidAtTheRateOfTheProcess(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        final float[] expected = new float[1];
        keepAt(helper, CHAMBER, WARM);
        helper.runAtTickTime(5, () -> {
            final @Nullable PipeNetwork network = networkAt(helper, CHAMBER);
            helper.assertTrue(network != null, "The chamber should form a network");
            charge(network, 37.6f, 2.9f, 0.5f, 20f); // 7% sulfur dioxide of 41 mol, with oxides and water
            expected[0] = litresPerSecond(network, 1, WARM) * 10f;
        });
        helper.runAtTickTime(205, () -> {
            final @Nullable PipeNetwork network = networkAt(helper, CHAMBER);
            helper.assertTrue(network != null, "The network should still be there");
            final float made = acidOf(chamber);
            helper.assertTrue(made > 0f, "The chamber should make acid, got " + made);
            helper.assertTrue(Math.abs(made - expected[0]) <= 0.2f * expected[0], "About " + expected[0] + " L in ten seconds of steps, got " + made);

            // Every mole of acid takes one of sulfur dioxide, so the gas must have lost exactly what the tank holds
            final float lost = 2.9f - network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()];
            final float moles = made / ChamberProcess.ACID_LITRES_PER_MOLE;
            helper.assertTrue(Math.abs(lost - moles) < 0.01f * moles + 1e-4f, "One mole of sulfur dioxide for each mole of acid: lost " + lost + ", made " + moles);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH)
    public static void noOxidesMeansNoAcid(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        helper.runAtTickTime(5, () -> charge(networkAt(helper, CHAMBER), 37.6f, 2.9f, 0f, 20f));
        helper.runAtTickTime(205, () -> {
            helper.assertTrue(acidOf(chamber) == 0f, "Without oxides the carrier is missing, and no acid forms, got " + acidOf(chamber));
            helper.assertTrue(Math.abs(networkAt(helper, CHAMBER).contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] - 2.9f) < 1e-4f, "The sulfur dioxide stays in the gas");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH)
    public static void noWaterMeansNoAcid(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        helper.runAtTickTime(5, () -> charge(networkAt(helper, CHAMBER), 37.6f, 2.9f, 0.5f, 0f));
        helper.runAtTickTime(205, () -> {
            helper.assertTrue(acidOf(chamber) == 0f, "Without water no acid forms, got " + acidOf(chamber));
            helper.assertTrue(Math.abs(networkAt(helper, CHAMBER).contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] - 2.9f) < 1e-4f, "The sulfur dioxide stays in the gas");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH)
    public static void noSulfurDioxideMeansNoAcid(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        helper.runAtTickTime(5, () -> charge(networkAt(helper, CHAMBER), 37.6f, 0f, 0.5f, 20f));
        helper.runAtTickTime(205, () -> {
            helper.assertTrue(acidOf(chamber) == 0f, "Without sulfur dioxide there is nothing to make acid of, got " + acidOf(chamber));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH)
    public static void twoChambersInOneNetworkMakeAboutTwiceTheAcid(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        helper.setBlock(CHAMBER.east(), Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity one = helper.getBlockEntity(CHAMBER), two = helper.getBlockEntity(CHAMBER.east());
        final float[] expected = new float[1];
        keepAt(helper, CHAMBER, WARM);
        helper.runAtTickTime(5, () -> {
            final @Nullable PipeNetwork network = networkAt(helper, CHAMBER);
            helper.assertTrue(network != null && network == networkAt(helper, CHAMBER.east()), "Two chambers side by side make one network");
            charge(network, 75.2f, 5.8f, 1f, 40f); // The same shares as the chamber on its own, in twice the volume
            expected[0] = litresPerSecond(network, 2, WARM) * 10f;
        });
        helper.runAtTickTime(205, () -> {
            final float made = acidOf(one) + acidOf(two);
            helper.assertTrue(one.tank().getFluidAmount() > 0 || one.pendingMillibuckets() > 0f, "The first chamber makes acid");
            helper.assertTrue(two.tank().getFluidAmount() > 0 || two.pendingMillibuckets() > 0f, "The second makes acid too");
            helper.assertTrue(Math.abs(made - expected[0]) <= 0.2f * expected[0], "About twice as much as one: " + expected[0] + " L, got " + made);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void saltpeterAddsHalfAMoleAndUsesOnePowder(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        final ItemStack saltpetre = new ItemStack(saltpeter(), 2);
        final FakePlayer player = player(helper);
        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = networkAt(helper, CHAMBER);
            helper.assertTrue(network != null, "The chamber should form a network");
            final float before = network.contents().gases[Gas.NITROGEN_OXIDES.ordinal()];
            helper.assertTrue(chamber.interact(saltpetre, player, InteractionHand.MAIN_HAND), "A powder of saltpeter is taken");
            helper.assertTrue(saltpetre.getCount() == 1, "One powder is used, got " + saltpetre.getCount());
            final float added = network.contents().gases[Gas.NITROGEN_OXIDES.ordinal()] - before;
            helper.assertTrue(Math.abs(added - 0.5f) < 1e-5f, "Half a mole of oxides for each powder, got " + added);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH)
    public static void aBurnerFedSulfurFillsTheNetworkWithSulfurDioxideAndEatsItsAir(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.SULFUR_BURNER.get());
        helper.setBlock(CHAMBER.east(), ceramicPipe());
        final SulfurBurnerBlockEntity burner = helper.getBlockEntity(CHAMBER);
        final FakePlayer player = player(helper);
        final ItemStack powders = new ItemStack(sulfur(), 2);
        helper.assertTrue(burner.addPowder(powders, player) && burner.addPowder(powders, player), "Two powders of sulfur go in");
        helper.assertTrue(powders.isEmpty(), "Both powders are used");
        helper.assertTrue(burner.light(), "A flame lights the sulfur");

        final float[] start = new float[3]; // sulfur, sulfur dioxide and air at the first step the test sees
        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = networkAt(helper, CHAMBER);
            helper.assertTrue(network != null && network == networkAt(helper, CHAMBER.east()), "The burner and its pipe make one network");
            start[0] = burner.sulfurMoles();
            start[1] = network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()];
            start[2] = network.contents().air;
        });
        helper.runAtTickTime(205, () -> {
            final PipeNetwork network = networkAt(helper, CHAMBER);
            final float burned = start[0] - burner.sulfurMoles();
            final float steps = burned / SulfurBurning.MOLES_PER_SECOND;
            helper.assertTrue(steps >= 8f && steps <= 12f, "About ten seconds of burning, got " + steps + " steps");
            final float made = network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] - start[1];
            helper.assertTrue(Math.abs(made - burned) < 1e-4f, "One mole of sulfur dioxide for each mole of sulfur, got " + made + " from " + burned);
            final float airUsed = start[2] - network.contents().air;
            helper.assertTrue(Math.abs(airUsed - burned / ChamberProcess.OXYGEN_IN_AIR) < 1e-3f, "The air gives its oxygen to the sulfur, got " + airUsed + " mol of air");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 460, batch = BATCH)
    public static void aBurnerStopsWhenItsOxygenIsGoneAndKeepsItsSulfur(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.SULFUR_BURNER.get());
        helper.setBlock(CHAMBER.east(), ceramicPipe());
        final SulfurBurnerBlockEntity burner = helper.getBlockEntity(CHAMBER);
        final FakePlayer player = player(helper);
        final ItemStack powders = new ItemStack(sulfur(), 2);
        burner.addPowder(powders, player);
        burner.addPowder(powders, player);
        burner.light();

        // Two moles of air: 0.42 mol of oxygen, which is less than the sulfur would take
        helper.runAtTickTime(5, () -> charge(networkAt(helper, CHAMBER), 2f, 0f, 0f, 0f));
        final float[] atTen = new float[2];
        helper.runAtTickTime(205, () -> {
            atTen[0] = networkAt(helper, CHAMBER).contents().gases[Gas.SULFUR_DIOXIDE.ordinal()];
            atTen[1] = burner.sulfurMoles();
            helper.assertTrue(atTen[0] > 0.1f, "Some sulfur dioxide was made first, got " + atTen[0]);
        });
        helper.runAtTickTime(405, () -> {
            final PipeContents contents = networkAt(helper, CHAMBER).contents();
            final float moles = contents.gasMoles();
            helper.assertTrue(contents.air * ChamberProcess.OXYGEN_IN_AIR / moles < SulfurBurning.LEAST_OXYGEN_SHARE, "The oxygen is used up below the share a burn needs");
            helper.assertTrue(Math.abs(contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] - atTen[0]) < 1e-4f, "It stops, so no more sulfur dioxide, got " + contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] + " after " + atTen[0]);
            helper.assertTrue(contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] <= 0.2095f * 2f + 1e-3f, "Never more than the oxygen there was");
            helper.assertTrue(Math.abs(burner.sulfurMoles() - atTen[1]) < 1e-4f && burner.sulfurMoles() > 2 * SulfurBurning.MOLES_PER_POWDER - 1f, "The sulfur is kept in the burner, got " + burner.sulfurMoles());
            helper.assertFalse(burner.isLit(), "The flame smouldered out with the oxygen: the sulfur has to be lit again");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = BATCH)
    public static void sulfurInAColdPipeWaitsForAFlameAndThenKeepsBurningOnItsOwn(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.SULFUR_BURNER.get());
        helper.setBlock(CHAMBER.east(), ceramicPipe());
        final SulfurBurnerBlockEntity burner = helper.getBlockEntity(CHAMBER);
        final FakePlayer player = player(helper);
        final ItemStack powders = new ItemStack(sulfur(), 2);
        burner.addPowder(powders, player);
        burner.addPowder(powders, player);
        final float start = burner.sulfurMoles();

        // A pipe at 20 C is well short of the 250 C at which sulfur lights by itself
        keepAt(helper, CHAMBER, 293.15f);
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(burner.sulfurMoles() == start && !burner.isLit(), "Sulfur in a cold pipe does not burn, got " + burner.sulfurMoles() + " of " + start);
            final FakePlayer smith = player(helper);
            smith.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
            useAsPlayer(helper, smith, CHAMBER);
            helper.assertTrue(burner.isLit(), "Flint and steel in the hand lights it");
            helper.assertTrue(smith.getMainHandItem().getDamageValue() == 1, "And the steel wears");
        });
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(start - burner.sulfurMoles() > 0.2f && burner.isLit(), "Once lit it burns in the cold pipe, got " + (start - burner.sulfurMoles()) + " mol and lit " + burner.isLit());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = BATCH)
    public static void sulfurInAPipeAboveTwoHundredFiftyDegreesLightsByItself(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.SULFUR_BURNER.get());
        helper.setBlock(CHAMBER.east(), ceramicPipe());
        final SulfurBurnerBlockEntity burner = helper.getBlockEntity(CHAMBER);
        final ItemStack powders = new ItemStack(sulfur(), 1);
        burner.addPowder(powders, player(helper));
        final float start = burner.sulfurMoles();
        keepAt(helper, CHAMBER, 600f);
        helper.runAtTickTime(150, () -> {
            helper.assertTrue(burner.isLit() && burner.sulfurMoles() < start, "In a pipe at 257 C sulfur lights without a flame, got " + burner.sulfurMoles() + " of " + start);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH)
    public static void aChamberInFrostMakesNoAcidAndOneAtRoomTemperatureASixteenth(GameTestHelper helper)
    {
        final BlockPos far = new BlockPos(6, 1, 6);
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        helper.setBlock(far, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity frozen = helper.getBlockEntity(CHAMBER), cool = helper.getBlockEntity(far);
        keepAt(helper, CHAMBER, 263.15f);
        keepAt(helper, far, 293.15f);
        final float[] expected = new float[1];
        helper.runAtTickTime(5, () -> {
            charge(networkAt(helper, CHAMBER), 37.6f, 2.9f, 0.5f, 20f);
            charge(networkAt(helper, far), 37.6f, 2.9f, 0.5f, 20f);
            expected[0] = litresPerSecond(networkAt(helper, far), 1, 293.15f) * 10f;
        });
        helper.runAtTickTime(205, () -> {
            helper.assertTrue(acidOf(frozen) == 0f, "A chamber in frost makes nothing, got " + acidOf(frozen));
            final float made = acidOf(cool);
            helper.assertTrue(made > 0f && Math.abs(made - expected[0]) <= 0.25f * expected[0], "At 20 C about a sixteenth of the warm rate, " + expected[0] + " L, got " + made);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aBurnerRefusesSulfurWhenFull(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.SULFUR_BURNER.get());
        final SulfurBurnerBlockEntity burner = helper.getBlockEntity(CHAMBER);
        final FakePlayer player = player(helper);
        final ItemStack powders = new ItemStack(sulfur(), 65);
        for (int i = 0; i < 64; i++)
        {
            helper.assertTrue(burner.addPowder(powders, player), "Powder " + (i + 1) + " goes in");
        }
        helper.assertTrue(!burner.addPowder(powders, player), "The sixty-fifth does not");
        helper.assertTrue(powders.getCount() == 1, "And it is not used, got " + powders.getCount());
        helper.assertTrue(Math.abs(burner.sulfurMoles() - 64 * SulfurBurning.MOLES_PER_POWDER) < 1e-2f, "Sixty-four powders of sulfur, got " + burner.sulfurMoles());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH)
    public static void aSmallPlantMakesAcidWithNoGasAddedByTheTest(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.SULFUR_BURNER.get());
        helper.setBlock(CHAMBER.east(), Chemistry.LEAD_CHAMBER.get());
        final SulfurBurnerBlockEntity burner = helper.getBlockEntity(CHAMBER);
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER.east());
        final FakePlayer player = player(helper);
        final ItemStack powders = new ItemStack(sulfur(), 4);
        for (int i = 0; i < 4; i++)
        {
            burner.addPowder(powders, player);
        }
        burner.light();
        keepAt(helper, CHAMBER, WARM);
        final ItemStack saltpetre = new ItemStack(saltpeter(), 1);
        final float[] start = new float[2];
        helper.runAtTickTime(5, () -> {
            final @Nullable PipeNetwork network = networkAt(helper, CHAMBER);
            helper.assertTrue(network != null && network == networkAt(helper, CHAMBER.east()), "The burner and the chamber make one network");
            network.contents().addLiquid(WATER, 20f, PipePhysics.ROOM_TEMPERATURE); // The air is what the network has, and the burner makes the rest
            helper.assertTrue(chamber.interact(saltpetre, player, InteractionHand.MAIN_HAND) && saltpetre.isEmpty(), "One powder of saltpeter goes into the chamber");
            start[0] = burner.sulfurMoles();
            start[1] = network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()];
        });
        helper.runAtTickTime(205, () -> {
            final PipeNetwork network = networkAt(helper, CHAMBER);
            final float made = acidOf(chamber);
            helper.assertTrue(made > 0f, "Acid appears from the sulfur the burner made, got " + made);

            // The acid made takes exactly the sulfur dioxide the burner made and the gas does not keep: nothing is lost on the way
            final float burned = start[0] - burner.sulfurMoles();
            final float sulfurDioxideGained = network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] - start[1];
            final float acidMoles = made / ChamberProcess.ACID_LITRES_PER_MOLE;
            final float consumed = burned - sulfurDioxideGained;
            helper.assertTrue(Math.abs(acidMoles - consumed) < 0.01f * consumed + 1e-3f, "Every mole of acid is a mole of sulfur dioxide: " + acidMoles + " mol of acid, " + consumed + " mol taken from the gas");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void acidIsPumpedOutThroughAPistonPumpIntoLeadPipe(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        final BlockPos pump = CHAMBER.east(), outlet = CHAMBER.east(2);
        helper.setBlock(pump, Pipes.PISTON_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        // A block of pipe holds 10 litres, so three of them take the 8 mB that the chamber holds without bursting
        for (int i = 0; i < 3; i++)
        {
            helper.setBlock(outlet.east(i), Pipes.PIPES.get(PipeMaterial.LEAD).get());
        }
        chamber.tank().fill(new FluidStack(acid(), 8), IFluidHandler.FluidAction.EXECUTE);

        // One sweep of the piston is 20 litres, more than the chamber holds, so it empties the tank
        helper.runAtTickTime(10, () -> {
            final PistonPumpBlockEntity piston = helper.getBlockEntity(pump);
            piston.pump(helper.getLevel(), PistonPumpBlockEntity.SWEPT_VOLUME);
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(chamber.tank().isEmpty(), "The pump takes the acid out of the tank, got " + chamber.tank().getFluidAmount());
            final @Nullable PipeNetwork lead = networkAt(helper, outlet);
            helper.assertTrue(lead != null, "The lead pipe should form a network");
            helper.assertTrue(ACID.equals(lead.contents().liquid), "The lead pipe carries the acid, got " + lead.contents().liquid);
            helper.assertTrue(Math.abs(lead.contents().litres - 8f) < 1e-3f, "All 8 litres arrive, got " + lead.contents().litres);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aJugRefusesChamberAcid(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        chamber.tank().fill(new FluidStack(acid(), 10), IFluidHandler.FluidAction.EXECUTE);
        final FakePlayer player = player(helper);
        final ItemStack jug = new ItemStack(TFCItems.JUG.asItem());
        helper.assertTrue(chamber.canUse(jug), "A jug is a container, so the chamber tries it");
        helper.assertTrue(!chamber.interact(jug, player, InteractionHand.MAIN_HAND), "TFC's jug refuses chamber acid");
        helper.assertTrue(chamber.tank().getFluidAmount() == 10, "And the chamber keeps its acid, got " + chamber.tank().getFluidAmount());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1500, batch = TIME_BATCH)
    public static void ironPipeCorrodesFasterThanLeadUnderChamberAcid(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        Calendars.SERVER.setCalendarTickRate(REAL_TIME_RATE);
        final Block iron = Pipes.PIPES.get(PipeMaterial.WROUGHT_IRON).get(), lead = Pipes.PIPES.get(PipeMaterial.LEAD).get();
        final BlockPos ironPos = new BlockPos(2, 1, 2), leadPos = new BlockPos(2, 1, 6);
        helper.setBlock(ironPos, iron);
        helper.setBlock(ironPos.east(), iron);
        helper.setBlock(leadPos, lead);
        helper.setBlock(leadPos.east(), lead);

        helper.runAtTickTime(5, () -> {
            helper.assertTrue(networkAt(helper, ironPos) != null && networkAt(helper, leadPos) != null, "Both pipes should join");
            networkAt(helper, ironPos).contents().addLiquid(ACID, 15f, PipePhysics.ROOM_TEMPERATURE);
            networkAt(helper, leadPos).contents().addLiquid(ACID, 15f, PipePhysics.ROOM_TEMPERATURE);
        });
        helper.runAtTickTime(1300, () -> {
            final @Nullable PipeNetwork ironNetwork = networkAt(helper, ironPos), leadNetwork = networkAt(helper, leadPos);
            helper.assertTrue(ironNetwork != null && leadNetwork != null, "The pipes should still be whole");
            final float fill = ironNetwork.contents().liquidVolume() / ironNetwork.volume();
            final float expected = PipeMaterial.WROUGHT_IRON.corrosion(ACID) * fill * 1200f / LocalTime.TICKS_PER_DAY;
            final float lostIron = 1f - ironNetwork.segments().getFirst().integrity;
            final float lostLead = 1f - leadNetwork.segments().getFirst().integrity;
            helper.assertTrue(expected > 0f, "Chamber acid must corrode iron");
            helper.assertTrue(lostIron > expected * 0.5f && lostIron < expected * 2f, "The minute between checks should do " + expected + " of corrosion to iron, got " + lostIron);
            helper.assertTrue(lostLead * 10f < lostIron, "Lead holds the cool acid: it loses " + lostLead + " against the iron's " + lostIron);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BURST_BATCH)
    public static void aBrokenChamberSplashesThePlayerBeside(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        chamber.tank().fill(new FluidStack(acid(), 50), IFluidHandler.FluidAction.EXECUTE);
        final TestPlayer near = playerAt(helper, new BlockPos(2, 1, 4)); // Two blocks away
        final TestPlayer far = playerAt(helper, new BlockPos(7, 1, 7));  // Beyond the reach of a splash

        helper.runAtTickTime(2, () -> helper.getLevel().destroyBlock(helper.absolutePos(CHAMBER), false));
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(near.lastDamage != null && near.lastDamage.is(CorrosiveSpill.CORROSIVE), "The acid burns the player by it");
            helper.assertTrue(far.lastDamage == null, "The player further off is not touched");
            near.discard();
            far.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = BURST_BATCH)
    public static void aBurstPipeOfAcidSplashesThePlayerBeside(GameTestHelper helper)
    {
        final BlockPos hole = new BlockPos(2, 1, 2), other = new BlockPos(3, 1, 2);
        helper.setBlock(hole, Pipes.PIPES.get(PipeMaterial.LEAD).get());
        helper.setBlock(other, Pipes.PIPES.get(PipeMaterial.LEAD).get());
        // The wall of the first pipe is gone, so the next step bursts it: the acid is put into the pipes before they join
        final PipeTracker tracker = PipeTracker.get(helper.getLevel());
        tracker.segmentAt(helper.absolutePos(hole)).integrity = 0f;
        tracker.segmentAt(helper.absolutePos(hole)).stored.addLiquid(ACID, 5f, PipePhysics.ROOM_TEMPERATURE);

        final TestPlayer near = playerAt(helper, new BlockPos(2, 1, 4));
        final TestPlayer far = playerAt(helper, new BlockPos(7, 1, 7));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(helper.getBlockState(hole).isAir(), "The burst pipe is gone");
            helper.assertTrue(near.lastDamage != null && near.lastDamage.is(CorrosiveSpill.CORROSIVE), "The acid that came out splashes the player by it");
            helper.assertTrue(far.lastDamage == null, "The player further off is not touched");
            near.discard();
            far.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aChamberAndBurnerSurviveSaveAndLoad(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        chamber.tank().fill(new FluidStack(acid(), 37), IFluidHandler.FluidAction.EXECUTE);
        final var registries = helper.getLevel().registryAccess();

        // A fraction of a millibucket that is waiting is a state of its own, which only a save can put in
        final CompoundTag tag = chamber.saveWithoutMetadata(registries);
        tag.putDouble("acidCarry", 0.4);
        chamber.loadWithComponents(tag, registries);
        helper.assertTrue(Math.abs(chamber.pendingMillibuckets() - 0.4f) < 1e-6f, "The carried fraction is loaded, got " + chamber.pendingMillibuckets());

        final LeadChamberBlockEntity copy = new LeadChamberBlockEntity(helper.absolutePos(CHAMBER), helper.getBlockState(CHAMBER));
        copy.loadWithComponents(chamber.saveWithoutMetadata(registries), registries);
        helper.assertTrue(copy.tank().getFluidAmount() == 37 && copy.tank().getFluid().getFluid() == acid(), "The acid is kept, got " + copy.tank().getFluidAmount());
        helper.assertTrue(Math.abs(copy.pendingMillibuckets() - 0.4f) < 1e-6f, "And the part of a millibucket, got " + copy.pendingMillibuckets());
        copy.loadWithComponents(new CompoundTag(), registries);
        helper.assertTrue(copy.tank().isEmpty() && copy.pendingMillibuckets() == 0f, "Loading a blank tag clears it");

        final BlockPos burnerPos = new BlockPos(5, 1, 5);
        helper.setBlock(burnerPos, Chemistry.SULFUR_BURNER.get());
        final SulfurBurnerBlockEntity burner = helper.getBlockEntity(burnerPos);
        final FakePlayer player = player(helper);
        burner.addPowder(new ItemStack(sulfur(), 2), player);
        burner.addPowder(new ItemStack(sulfur(), 2), player);
        burner.light();
        final SulfurBurnerBlockEntity burnerCopy = new SulfurBurnerBlockEntity(helper.absolutePos(burnerPos), helper.getBlockState(burnerPos));
        burnerCopy.loadWithComponents(burner.saveWithoutMetadata(registries), registries);
        helper.assertTrue(Math.abs(burnerCopy.sulfurMoles() - 2 * SulfurBurning.MOLES_PER_POWDER) < 1e-5f, "The sulfur is kept, got " + burnerCopy.sulfurMoles());
        helper.assertTrue(burnerCopy.isLit(), "And the flame");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void theChamberAndBurnerRecipesLoad(GameTestHelper helper)
    {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(TFCpp.id("crafting/lead_chamber")).isPresent(), "The lead chamber must be craftable");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(TFCpp.id("crafting/sulfur_burner")).isPresent(), "The sulfur burner must be craftable");
        helper.succeed();
    }

    // --- Stage F: the glass retort, the sand bath and the carboy -----------------------------------------------------------------------------------------------------------

    /** Glass chemistry on its own: the calendar stands still, so only the test steps a retort. */
    private static final String GLASS_BATCH = "tfcpp_glass";
    /** Apart from the others, as these crack flasks, break blocks and hurt players. */
    private static final String GLASS_BURST_BATCH = "tfcpp_glass_burst";
    private static final BlockPos RETORT = new BlockPos(2, 1, 2);

    private static Fluid fluidOf(String id)
    {
        return BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
    }

    private static Item itemOf(String id)
    {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    /** A hollow stone box, air inside from (1,1,1) to (size,height,size), so that the gas a retort lets out stays where the test can count it. */
    private static void sealedBox(GameTestHelper helper, int size, int height)
    {
        for (int x = 0; x <= size + 1; x++)
        {
            for (int y = 0; y <= height + 1; y++)
            {
                for (int z = 0; z <= size + 1; z++)
                {
                    final boolean inside = x > 0 && x <= size && y > 0 && y <= height && z > 0 && z <= size;
                    helper.setBlock(x, y, z, inside ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
    }

    private static float totalInBox(GameTestHelper helper, int size, int height, Gas gas)
    {
        final LevelGasVolume volume = GasTracker.get(helper.getLevel()).volume(helper.getLevel());
        float total = 0f;
        for (int x = 1; x <= size; x++)
        {
            for (int y = 1; y <= height; y++)
            {
                for (int z = 1; z <= size; z++)
                {
                    final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    final @Nullable float[] cell = volume.cell(pos.getX(), pos.getY(), pos.getZ());
                    if (cell != null)
                    {
                        total += cell[gas.ordinal()];
                    }
                }
            }
        }
        return total;
    }

    /** A retort in a sealed box, with a condenser beside it if asked. */
    private static GlassRetortBlockEntity retortInBox(GameTestHelper helper, boolean condenser)
    {
        sealedBox(helper, 5, 4);
        helper.setBlock(RETORT, Chemistry.GLASS_RETORT.get());
        if (condenser)
        {
            helper.setBlock(RETORT.east(), Metallurgy.RETORT_CONDENSER.get());
        }
        return helper.getBlockEntity(RETORT);
    }

    /** Charges a retort for a recipe of the table, as a person would: liquids poured from above, powder into the slot. */
    private static void charge(GameTestHelper helper, GlassRetortBlockEntity retort, DistillationTable.Row row, boolean withWater)
    {
        final IFluidHandler above = retort.fluids(Direction.UP);
        helper.assertTrue(above != null && above.fill(new FluidStack(fluidOf(row.fluid()), 5), IFluidHandler.FluidAction.EXECUTE) == 5, "Five litres of " + row.fluid() + " go in from above");
        if (row.reagent() != null)
        {
            helper.assertTrue(above.fill(new FluidStack(fluidOf(row.reagent()), 1), IFluidHandler.FluidAction.EXECUTE) == 1, "A litre of " + row.reagent() + " goes into the other tank");
        }
        if (row.item() != null)
        {
            helper.assertTrue(retort.interact(new ItemStack(itemOf(row.item()), row.items()), player(helper), InteractionHand.MAIN_HAND), "The powder goes into the slot");
        }
        if (withWater)
        {
            retort.water().fill(new FluidStack(Fluids.WATER, 5), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static double inReceiver(GlassRetortBlockEntity retort, String fluid)
    {
        final Fluid wanted = fluidOf(fluid);
        final double whole = !retort.receiver().isEmpty() && retort.receiver().getFluid().getFluid() == wanted ? retort.receiver().getFluidAmount() : 0.0;
        return whole + retort.pending(wanted);
    }

    private static double inResidue(GlassRetortBlockEntity retort, String fluid)
    {
        final Fluid wanted = fluidOf(fluid);
        final double whole = !retort.residue().isEmpty() && retort.residue().getFluid().getFluid() == wanted ? retort.residue().getFluidAmount() : 0.0;
        return whole + retort.pending(wanted);
    }

    /**
     * Heats a charged retort with a provided heat, every tick, until it is in the working range of its recipe, and runs the whole batch in one stretch of time. The calendar stands still, so it is the test that steps the retort.
     */
    private static void runRow(GameTestHelper helper, GlassRetortBlockEntity retort, DistillationTable.Row row, Runnable afterwards)
    {
        final float heat = (row.minTemperature() + row.maxTemperature()) / 2f;
        final boolean[] done = new boolean[1];
        helper.onEachTick(() -> {
            retort.setTemperature(heat);
            if (!done[0] && retort.getTemperature() >= row.minTemperature() && retort.getTemperature() <= row.maxTemperature())
            {
                done[0] = true;
                retort.step(helper.getLevel(), row.seconds() + 10f);
                afterwards.run();
                helper.succeed();
            }
        });
    }

    private static void assertNoGas(GameTestHelper helper, Gas... gases)
    {
        for (Gas gas : gases)
        {
            helper.assertTrue(totalInBox(helper, 5, 4, gas) < 1e-3f, "No " + gas.id() + " should be in the room, got " + totalInBox(helper, 5, 4, gas));
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void aRetortConcentratesChamberAcidByBoilingTheWaterOffAndKeepsTheStrongAcidInTheFlask(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.CONCENTRATE_SULFURIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(row.output() == null && row.residue().equals("tfcpp:sulfuric_acid"), "The table keeps the strong acid as the residue, and nothing comes over");
            helper.assertTrue(Math.abs(inResidue(retort, row.residue()) - 0.59) < 1e-3, "0.59 L of sulfuric acid stays in the flask, got " + inResidue(retort, row.residue()));
            helper.assertTrue(retort.receiver().isEmpty(), "And none is in the receiver");
            helper.assertTrue(Math.abs(retort.input().litres() - 4.0) < 1e-3, "A litre of chamber acid is taken, got " + retort.input().litres());
            helper.assertTrue(retort.product().isEmpty() && !retort.isActive(), "No powder is left, and the batch is over");
            assertNoGas(helper, Gas.NITROGEN_OXIDES, Gas.HYDROGEN_CHLORIDE);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void aRetortDistilsNitricAcidFromSaltpeterAndOilOfVitriol(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.NITRIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(Math.abs(inReceiver(retort, row.output()) - 0.175) < 1e-3, "0.175 L of nitric acid, got " + inReceiver(retort, row.output()));
            helper.assertTrue(Math.abs(retort.input().litres() - (5.0 - 0.23)) < 1e-3, "0.23 L of oil of vitriol is taken, got " + retort.input().litres());
            helper.assertTrue(retort.charge().isEmpty(), "The eight powders of saltpeter are taken");
            helper.assertTrue(retort.product().is(Chemistry.POTASSIUM_BISULFATE.get()) && retort.product().getCount() == 8, "Eight powders of potassium bisulfate are left, got " + retort.product());
            helper.assertTrue(Math.abs(totalInBox(helper, 5, 4, Gas.NITROGEN_OXIDES) - 0.2f) < 0.01f, "0.2 mol of nitrous fumes in the room, got " + totalInBox(helper, 5, 4, Gas.NITROGEN_OXIDES));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void aRetortDistilsHydrochloricAcidIntoTheWaterOfTheReceiver(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.HYDROCHLORIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, true);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(Math.abs(inReceiver(retort, row.output()) - 0.67) < 1e-3, "0.67 L of hydrochloric acid, got " + inReceiver(retort, row.output()));
            helper.assertTrue(Math.abs(retort.water().litres() - (5.0 - 0.53)) < 1e-3, "0.53 L of the receiver's water is taken up, got " + retort.water().litres());
            helper.assertTrue(Math.abs(retort.input().litres() - (5.0 - 0.39)) < 1e-3, "0.39 L of oil of vitriol is taken, got " + retort.input().litres());
            helper.assertTrue(retort.product().is(Chemistry.SODIUM_BISULFATE.get()) && retort.product().getCount() == 8, "Eight powders of sodium bisulfate are left, got " + retort.product());
            assertNoGas(helper, Gas.HYDROGEN_CHLORIDE);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void aRetortSeparatesWoodSpiritFromVinegar(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.WOOD_SPIRIT;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(Math.abs(inReceiver(retort, row.output()) - 0.02) < 1e-3, "0.02 L of wood spirit comes over, got " + inReceiver(retort, row.output()));
            helper.assertTrue(Math.abs(inResidue(retort, row.residue()) - 0.9) < 1e-3, "0.9 L of vinegar stays in the flask, got " + inResidue(retort, row.residue()));
            helper.assertTrue(Math.abs(retort.input().litres() - 4.0) < 1e-3, "A litre of pyroligneous liquor is taken, got " + retort.input().litres());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void aRetortMakesSuperphosphateFromApatiteAndChamberAcid(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.SUPERPHOSPHATE;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(retort.product().is(Chemistry.SUPERPHOSPHATE.get()) && retort.product().getCount() == row.byproducts(), "Thirteen powders of superphosphate, got " + retort.product());
            helper.assertTrue(Math.abs(retort.input().litres() - (5.0 - row.litres())) < 1e-3, "0.27 L of chamber acid is taken, got " + retort.input().litres());
            helper.assertTrue(retort.charge().isEmpty() && retort.receiver().isEmpty(), "The apatite is taken and nothing comes over");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void aRetortMakesAmmoniumSulfateFromLiquorAndAcid(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.AMMONIUM_SULFATE;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(retort.product().is(Chemistry.AMMONIUM_SULFATE.get()) && retort.product().getCount() == 1, "One powder of ammonium sulfate, got " + retort.product());
            helper.assertTrue(Math.abs(retort.input().litres() - 4.0) < 1e-3, "A litre of liquor is taken, got " + retort.input().litres());
            helper.assertTrue(Math.abs(retort.reagent().litres() - (1.0 - 0.02)) < 1e-3, "0.02 L of oil of vitriol is taken, got " + retort.reagent().litres());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void withoutACondenserNitricAcidGoesIntoTheRoomAsFumes(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.NITRIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, false);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(retort.receiver().isEmpty() && retort.pending(fluidOf(row.output())) == 0.0, "Nothing is caught");
            final float expected = 0.2f + 5f * 0.175f;
            helper.assertTrue(Math.abs(totalInBox(helper, 5, 4, Gas.NITROGEN_OXIDES) - expected) < 0.01f, "The recipe's 0.2 mol and 5 mol for each litre that would have condensed, " + expected + ", got " + totalInBox(helper, 5, 4, Gas.NITROGEN_OXIDES));
            helper.assertTrue(retort.product().getCount() == 8, "The powder is left all the same");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void concentratingNeedsNoCondenserAndLosesNoAcidToTheRoom(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.CONCENTRATE_SULFURIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, false);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(Math.abs(inResidue(retort, row.residue()) - 0.59) < 1e-3, "The strong acid is in the flask all the same, got " + inResidue(retort, row.residue()));
            helper.assertTrue(retort.receiver().isEmpty(), "Nothing is caught, as nothing came over");
            for (Gas gas : Gas.VALUES)
            {
                helper.assertTrue(totalInBox(helper, 5, 4, gas) < 1e-3f, "Only steam goes off, and the gas of the mod has none, but there is " + gas.id());
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void withoutACondenserHydrochloricAcidAllGoesToTheAir(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.HYDROCHLORIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, false);
        charge(helper, retort, row, true);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(retort.receiver().isEmpty(), "Nothing is caught");
            helper.assertTrue(Math.abs(totalInBox(helper, 5, 4, Gas.HYDROGEN_CHLORIDE) - 6.7f) < 0.01f, "10 mol of hydrogen chloride for each litre of acid, 6.7, got " + totalInBox(helper, 5, 4, Gas.HYDROGEN_CHLORIDE));
            helper.assertTrue(Math.abs(retort.water().litres() - 5.0) < 1e-3, "No receiver was joined, so its water is not used, got " + retort.water().litres());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = GLASS_BATCH)
    public static void withoutWaterInTheReceiverHydrochloricAcidAllGoesToTheAir(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.HYDROCHLORIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(retort.receiver().isEmpty() && retort.pending(fluidOf(row.output())) == 0.0, "With nothing to take it up, no acid forms");
            helper.assertTrue(Math.abs(totalInBox(helper, 5, 4, Gas.HYDROGEN_CHLORIDE) - 6.7f) < 0.01f, "All of it goes into the air, 6.7 mol, got " + totalInBox(helper, 5, 4, Gas.HYDROGEN_CHLORIDE));
            helper.assertTrue(retort.product().getCount() == 8, "The salt cake is left all the same");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void aFlaskTooColdOrTooHotKeepsItsChargeAndAStartedBatchKeepsItsPlace(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.NITRIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        helper.runAtTickTime(2, () -> {
            retort.warmTo(100f);
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(retort.lastReason() == Distillation.Reason.COLD && !retort.isActive(), "Below 120 C nothing starts");
            helper.assertTrue(Math.abs(retort.input().litres() - 5.0) < 1e-6 && retort.charge().getCount() == 8, "And nothing is taken");

            retort.warmTo(210f);
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(retort.lastReason() == Distillation.Reason.HOT && !retort.isActive(), "Above 200 C nothing starts either");
            helper.assertTrue(Math.abs(retort.input().litres() - 5.0) < 1e-6 && retort.charge().getCount() == 8, "And nothing is taken");

            retort.warmTo(150f);
            retort.step(helper.getLevel(), 20f);
            helper.assertTrue(retort.isActive() && Math.abs(retort.progress() - 20.0) < 1e-6, "In range the batch runs, got " + retort.progress());
            final double madeSoFar = inReceiver(retort, row.output());
            helper.assertTrue(Math.abs(madeSoFar - 0.175 / 3.0) < 1e-3, "A third of the time gives a third of the acid, got " + madeSoFar);

            retort.warmTo(250f);
            retort.step(helper.getLevel(), 100f);
            retort.warmTo(60f);
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(retort.isActive() && Math.abs(retort.progress() - 20.0) < 1e-6, "Time out of range does not count, got " + retort.progress());
            helper.assertTrue(Math.abs(inReceiver(retort, row.output()) - madeSoFar) < 1e-9, "And brings nothing over");

            retort.warmTo(150f);
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(!retort.isActive(), "Back in range it finishes");
            helper.assertTrue(Math.abs(inReceiver(retort, row.output()) - 0.175) < 1e-3, "With exactly the acid of one batch, got " + inReceiver(retort, row.output()));
            helper.assertTrue(Math.abs(retort.input().litres() - (5.0 - 0.23)) < 1e-3, "And exactly one batch of inputs used, got " + retort.input().litres());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void aFlaskFullOfResidueHoldsTheBatchBackAndTakesNothing(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.CONCENTRATE_SULFURIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        retort.residue().fill(new FluidStack(fluidOf("tfcpp:sulfuric_acid"), 25), IFluidHandler.FluidAction.EXECUTE);
        helper.runAtTickTime(2, () -> {
            retort.warmTo(300f);
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(retort.lastReason() == Distillation.Reason.NO_ROOM && !retort.isActive(), "A flask full of the strong acid stops the batch before it starts");
            helper.assertTrue(Math.abs(retort.input().litres() - 5.0) < 1e-6, "And the chamber acid is not taken");

            retort.residue().setFluid(new FluidStack(fluidOf("tfcpp:nitric_acid"), 3));
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(retort.lastReason() == Distillation.Reason.NO_ROOM, "Another liquid in the flask stops it too");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = GLASS_BATCH)
    public static void theRetortTakesLiquidFromAboveAndGivesItFromBelow(GameTestHelper helper)
    {
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        final var level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(RETORT);
        final @Nullable IFluidHandler above = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
        final @Nullable IFluidHandler below = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.DOWN);
        helper.assertTrue(above != null && below != null, "The top and the bottom have handlers");
        helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.NORTH) != null, "The sides are the receiver's, and have one");

        helper.assertTrue(above.fill(new FluidStack(acid(), 3), IFluidHandler.FluidAction.EXECUTE) == 3, "Chamber acid goes in from above");
        helper.assertTrue(above.fill(new FluidStack(fluidOf("tfcpp:coal_tar"), 3), IFluidHandler.FluidAction.EXECUTE) == 0, "A liquid no recipe takes is refused");
        helper.assertTrue(retort.input().fluid() == acid() && retort.reagent().isEmpty(), "The tar was refused, got " + retort.reagent().fluid());
        helper.assertTrue(below.fill(new FluidStack(acid(), 3), IFluidHandler.FluidAction.EXECUTE) == 0, "Nothing is poured in from below");
        helper.assertTrue(above.drain(3, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Nothing is drawn out from above");

        retort.receiver().fill(new FluidStack(fluidOf("tfcpp:sulfuric_acid"), 4), IFluidHandler.FluidAction.EXECUTE);
        final FluidStack drawn = below.drain(10, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(drawn.getAmount() == 4 && drawn.getFluid() == fluidOf("tfcpp:sulfuric_acid"), "The receiver runs out of the bottom, got " + drawn);
        helper.assertTrue(retort.input().wholeMillibuckets() == 3, "And the flask's own liquid stays in, got " + retort.input().wholeMillibuckets());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160, batch = GLASS_BATCH)
    public static void aSandBathPassesOnlyAShareOfTheHeatUp(GameTestHelper helper)
    {
        final BlockPos deep = new BlockPos(1, 1, 1), shallow = new BlockPos(5, 1, 5);
        helper.setBlock(deep, Chemistry.SAND_BATH.get().defaultBlockState().setValue(SandBathBlock.DEPTH, 8));
        helper.setBlock(shallow, Chemistry.SAND_BATH.get().defaultBlockState().setValue(SandBathBlock.DEPTH, 1));
        helper.setBlock(deep.above(), Chemistry.GLASS_RETORT.get());
        helper.setBlock(shallow.above(), Chemistry.GLASS_RETORT.get());
        final SandBathBlockEntity deepBath = helper.getBlockEntity(deep), shallowBath = helper.getBlockEntity(shallow);
        final GlassRetortBlockEntity deepRetort = helper.getBlockEntity(deep.above()), shallowRetort = helper.getBlockEntity(shallow.above());
        deepBath.warmTo(540f); // Stands for a fire that has had a long time to bring the sand up, as the flask would crack before the shallow one got to 560
        shallowBath.warmTo(540f);
        helper.onEachTick(() -> {
            deepBath.setTemperature(540f);
            shallowBath.setTemperature(540f);
        });
        helper.runAtTickTime(130, () -> {
            helper.assertTrue(Math.abs(deepRetort.getTemperature() - SandBath.passedUp(540f, 8)) < 1.5f, "The flask on the deep bath settles at about " + SandBath.passedUp(540f, 8) + " C, got " + deepRetort.getTemperature());
            helper.assertTrue(shallowRetort.getTemperature() > deepRetort.getTemperature() + 10f && shallowRetort.getTemperature() < SandBath.passedUp(540f, 1), "The flask on the shallow bath is on its way to " + SandBath.passedUp(540f, 1) + " C, got " + shallowRetort.getTemperature());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void anEmptyHandSetsTheDepthOfASandBathAndReadsItsTemperature(GameTestHelper helper)
    {
        helper.setBlock(RETORT, Chemistry.SAND_BATH.get().defaultBlockState().setValue(SandBathBlock.DEPTH, 7));
        final FakePlayer player = player(helper);
        helper.runAtTickTime(2, () -> {
            final BlockPos pos = helper.absolutePos(RETORT);
            final var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false);
            helper.getBlockState(RETORT).useWithoutItem(helper.getLevel(), player, hit);
            helper.assertTrue(helper.getBlockState(RETORT).getValue(SandBathBlock.DEPTH) == 8, "An empty hand adds a layer");
            helper.getBlockState(RETORT).useWithoutItem(helper.getLevel(), player, hit);
            helper.assertTrue(helper.getBlockState(RETORT).getValue(SandBathBlock.DEPTH) == 8, "Eight is the most");
            player.setShiftKeyDown(true);
            helper.getBlockState(RETORT).useWithoutItem(helper.getLevel(), player, hit);
            helper.assertTrue(helper.getBlockState(RETORT).getValue(SandBathBlock.DEPTH) == 7, "Crouching takes a layer off");
            helper.assertTrue(helper.getBlockEntity(RETORT) instanceof SandBathBlockEntity, "And the tray keeps its block entity through the change");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void aCarboyHoldsTwentyFiveLitresOfWhatItMayHoldAndRefusesTheRest(GameTestHelper helper)
    {
        final ItemStack carboy = new ItemStack(Chemistry.GLASS_CARBOY.get());
        helper.assertTrue(carboy.getMaxStackSize() == 1, "A carboy does not stack");
        final IFluidHandlerItem handler = carboy.getCapability(Capabilities.FluidHandler.ITEM);
        helper.assertTrue(handler != null && handler.getTankCapacity(0) == 25, "It holds 25 mB");

        for (Liquid liquid : Liquid.values())
        {
            helper.assertTrue(handler.fill(new FluidStack(Chemistry.fluid(liquid), 1), IFluidHandler.FluidAction.SIMULATE) == 1, "It takes " + liquid.id());
        }
        helper.assertTrue(handler.fill(new FluidStack(fluidOf("tfc:vinegar"), 1), IFluidHandler.FluidAction.SIMULATE) == 1, "It takes vinegar");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 1), IFluidHandler.FluidAction.SIMULATE) == 1, "It takes water");
        for (String refused : new String[] {"tfc:salt_water", "tfc:brine", "tfc:olive_oil", "tfc:spring_water", "minecraft:lava", "tfcpp:mercury", "tfc:metal/copper"})
        {
            helper.assertTrue(handler.fill(new FluidStack(fluidOf(refused), 1), IFluidHandler.FluidAction.SIMULATE) == 0, "It refuses " + refused);
        }

        helper.assertTrue(handler.fill(new FluidStack(Chemistry.fluid(Liquid.SULFURIC_ACID), 40), IFluidHandler.FluidAction.EXECUTE) == 25, "Only 25 of 40 go in");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 1), IFluidHandler.FluidAction.EXECUTE) == 0, "A carboy of acid takes no water on top of it");
        helper.assertTrue(handler.getFluidInTank(0).getAmount() == 25, "It holds 25");
        final FluidStack out = handler.drain(10, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(out.getAmount() == 10 && handler.getFluidInTank(0).getAmount() == 15, "It gives 10 back");
        helper.assertTrue(carboy.getHoverName().getString().length() > 0, "And has a name");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void aCarboyIsNotFilledFromTFCsFluidBlocksWithAnUnexpectedLiquid(GameTestHelper helper)
    {
        final FakePlayer player = player(helper);
        final BlockPos salt = new BlockPos(2, 1, 2), lava = new BlockPos(4, 1, 2), water = new BlockPos(6, 1, 2);
        helper.setBlock(salt, TFCBlocks.SALT_WATER.get());
        helper.setBlock(lava, Blocks.LAVA);
        helper.setBlock(water, Blocks.WATER);

        for (BlockPos pos : new BlockPos[] {salt, lava, water})
        {
            final ItemStack carboy = new ItemStack(Chemistry.GLASS_CARBOY.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, carboy);
            final BlockPos absolute = helper.absolutePos(pos);
            final var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
            FluidHelpers.transferBetweenWorldAndItem(carboy, helper.getLevel(), hit, player, InteractionHand.MAIN_HAND, false, false, false);
            final FluidStack held = FluidHelpers.getContainedFluid(player.getItemInHand(InteractionHand.MAIN_HAND));
            if (pos.equals(water))
            {
                helper.assertTrue(held.isEmpty() || held.getFluid() == Fluids.WATER, "From water it takes only water, got " + held);
            }
            else
            {
                helper.assertTrue(held.isEmpty(), "From " + helper.getBlockState(pos).getBlock().getName().getString() + " it takes nothing, got " + held);
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void aCarboyDrawsAcidFromALeadChamber(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        chamber.tank().fill(new FluidStack(acid(), 10), IFluidHandler.FluidAction.EXECUTE);
        final FakePlayer player = player(helper);
        final ItemStack carboy = new ItemStack(Chemistry.GLASS_CARBOY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, carboy);

        helper.assertTrue(chamber.canUse(carboy), "A carboy is a container, so the chamber tries it");
        helper.assertTrue(chamber.interact(carboy, player, InteractionHand.MAIN_HAND), "The chamber fills the carboy");
        final FluidStack held = FluidHelpers.getContainedFluid(player.getItemInHand(InteractionHand.MAIN_HAND));
        helper.assertTrue(held.getFluid() == acid() && held.getAmount() == 10, "All ten litres are in the carboy, got " + held);
        helper.assertTrue(chamber.tank().isEmpty(), "And none stay in the chamber, got " + chamber.tank().getFluidAmount());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = GLASS_BATCH)
    public static void chamberAcidIsConcentratedAndThenMadeIntoNitricAcid(GameTestHelper helper)
    {
        sealedBox(helper, 7, 4);
        final BlockPos first = new BlockPos(2, 1, 2), second = new BlockPos(5, 1, 5), chamberPos = new BlockPos(3, 1, 5);
        helper.setBlock(chamberPos, Chemistry.LEAD_CHAMBER.get());
        helper.setBlock(first, Chemistry.GLASS_RETORT.get());
        helper.setBlock(first.east(), Metallurgy.RETORT_CONDENSER.get());
        helper.setBlock(second, Chemistry.GLASS_RETORT.get());
        helper.setBlock(second.east(), Metallurgy.RETORT_CONDENSER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(chamberPos);
        final GlassRetortBlockEntity concentrator = helper.getBlockEntity(first), nitrator = helper.getBlockEntity(second);
        final FakePlayer player = player(helper);
        chamber.tank().fill(new FluidStack(acid(), 8), IFluidHandler.FluidAction.EXECUTE);

        helper.runAtTickTime(2, () -> {
            // The chamber's acid into a carboy, and from the carboy into the first retort
            final ItemStack carboyA = new ItemStack(Chemistry.GLASS_CARBOY.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, carboyA);
            helper.assertTrue(chamber.interact(carboyA, player, InteractionHand.MAIN_HAND), "The chamber fills a carboy");
            final ItemStack filledA = player.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(concentrator.canUse(filledA) && concentrator.interact(filledA, player, InteractionHand.MAIN_HAND), "The carboy is poured into the retort");
            helper.assertTrue(concentrator.input().wholeMillibuckets() == 8 && FluidHelpers.getContainedFluid(player.getItemInHand(InteractionHand.MAIN_HAND)).isEmpty(), "All eight litres went in, got " + concentrator.input().wholeMillibuckets());

            // Four batches of a litre each, at the heat of the table, give 2.36 L of oil of vitriol: two whole litres in the flask, left there by the water that went off
            concentrator.warmTo(315f);
            for (int batch = 0; batch < 4; batch++)
            {
                concentrator.step(helper.getLevel(), 100f);
            }
            helper.assertTrue(Math.abs(concentrator.input().litres() - 4.0) < 1e-3, "Four litres of chamber acid are used, got " + concentrator.input().litres());
            helper.assertTrue(concentrator.residue().getFluidAmount() == 2 && concentrator.residue().getFluid().getFluid() == fluidOf("tfcpp:sulfuric_acid"), "Two whole litres of oil of vitriol are in the flask, got " + concentrator.residue().getFluidAmount());
            helper.assertTrue(Math.abs(inResidue(concentrator, "tfcpp:sulfuric_acid") - 4 * 0.59) < 1e-3, "And the rest of the 2.36 L waits as a fraction, got " + inResidue(concentrator, "tfcpp:sulfuric_acid"));
            helper.assertTrue(concentrator.receiver().isEmpty(), "Nothing came over into the receiver");

            // The oil of vitriol into a second carboy, and into the second retort with fifty-six powders of saltpeter
            final ItemStack carboyB = new ItemStack(Chemistry.GLASS_CARBOY.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, carboyB);
            helper.assertTrue(concentrator.interact(carboyB, player, InteractionHand.MAIN_HAND), "The flask is tapped into a carboy");
            final ItemStack filledB = player.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(FluidHelpers.getContainedFluid(filledB).getAmount() == 2, "Two litres are tapped");
            helper.assertTrue(nitrator.interact(filledB, player, InteractionHand.MAIN_HAND), "The second carboy is poured into the second retort");
            helper.assertTrue(nitrator.input().wholeMillibuckets() == 2 && nitrator.input().fluid() == fluidOf("tfcpp:sulfuric_acid"), "Two litres of oil of vitriol are in it");
            helper.assertTrue(nitrator.interact(new ItemStack(saltpeter(), 56), player, InteractionHand.MAIN_HAND), "Fifty-six powders of saltpeter go into the slot");

            nitrator.warmTo(160f);
            for (int batch = 0; batch < 7; batch++)
            {
                nitrator.step(helper.getLevel(), 100f);
            }
            helper.assertTrue(nitrator.charge().isEmpty(), "Seven batches of eight use all fifty-six powders");
            helper.assertTrue(Math.abs(nitrator.input().litres() - (2.0 - 7 * 0.23)) < 1e-3, "Seven times 0.23 L of oil of vitriol, got " + nitrator.input().litres());
            helper.assertTrue(nitrator.receiver().getFluidAmount() == 1 && nitrator.receiver().getFluid().getFluid() == fluidOf("tfcpp:nitric_acid"), "A whole litre of nitric acid in the receiver, got " + nitrator.receiver().getFluidAmount());
            helper.assertTrue(Math.abs(inReceiver(nitrator, "tfcpp:nitric_acid") - 7 * 0.175) < 1e-3, "Of the 1.225 L the seven batches made, got " + inReceiver(nitrator, "tfcpp:nitric_acid"));
            helper.assertTrue(nitrator.product().is(Chemistry.POTASSIUM_BISULFATE.get()) && nitrator.product().getCount() == 56, "And fifty-six powders of potassium bisulfate, got " + nitrator.product());
            helper.assertTrue(Math.abs(totalInBox(helper, 7, 4, Gas.NITROGEN_OXIDES) - 7 * 0.2f) < 0.02f, "With 1.4 mol of nitrous fumes in the room, got " + totalInBox(helper, 7, 4, Gas.NITROGEN_OXIDES));

            // The nitric acid into a carboy: the end of the chain
            final ItemStack carboyC = new ItemStack(Chemistry.GLASS_CARBOY.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, carboyC);
            helper.assertTrue(nitrator.interact(carboyC, player, InteractionHand.MAIN_HAND), "The nitric acid is tapped");
            final FluidStack nitric = FluidHelpers.getContainedFluid(player.getItemInHand(InteractionHand.MAIN_HAND));
            helper.assertTrue(nitric.getFluid() == fluidOf("tfcpp:nitric_acid") && nitric.getAmount() == 1, "A litre of nitric acid in a carboy, got " + nitric);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = GLASS_BATCH)
    public static void aRetortSurvivesSaveAndLoadWithABatchRunning(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.NITRIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, true);
        helper.runAtTickTime(2, () -> {
            retort.warmTo(150f);
            retort.step(helper.getLevel(), 30f);
            helper.assertTrue(retort.isActive(), "The batch is running");
            final var registries = helper.getLevel().registryAccess();
            final GlassRetortBlockEntity copy = new GlassRetortBlockEntity(helper.absolutePos(RETORT), helper.getBlockState(RETORT));
            copy.loadWithComponents(retort.saveWithoutMetadata(registries), registries);
            helper.assertTrue(copy.isActive() && Math.abs(copy.progress() - 30.0) < 1e-9, "The batch and its progress are kept, got " + copy.progress());
            helper.assertTrue(Math.abs(copy.input().litres() - retort.input().litres()) < 1e-9 && copy.input().fluid() == retort.input().fluid(), "The float litres of the flask are kept, got " + copy.input().litres());
            helper.assertTrue(Math.abs(copy.water().litres() - 5.0) < 1e-9, "And the water");
            helper.assertTrue(copy.receiver().getFluidAmount() == retort.receiver().getFluidAmount(), "And the receiver");
            helper.assertTrue(Math.abs(copy.pending(fluidOf(row.output())) - retort.pending(fluidOf(row.output()))) < 1e-9 && copy.pending(fluidOf(row.output())) > 0.0, "And the fraction of a millibucket that waits, got " + copy.pending(fluidOf(row.output())));
            helper.assertTrue(copy.charge().isEmpty() && retort.charge().isEmpty(), "The powder has been taken by the batch");

            // The copy goes on where the first stopped, with no second set of inputs
            copy.warmTo(150f);
            final double inputBefore = copy.input().litres();
            copy.setLevel(helper.getLevel());
            copy.step(helper.getLevel(), 100f);
            helper.assertTrue(!copy.isActive() && Math.abs(copy.input().litres() - inputBefore) < 1e-9, "It finishes without taking inputs again");
            helper.assertTrue(Math.abs(inReceiver(copy, row.output()) - 0.175) < 1e-3, "With the acid of one batch, got " + inReceiver(copy, row.output()));
            helper.assertTrue(copy.product().getCount() == 8, "And the eight powders");
            copy.loadWithComponents(new CompoundTag(), registries);
            helper.assertTrue(copy.input().isEmpty() && copy.receiver().isEmpty() && copy.product().isEmpty() && !copy.isActive(), "Loading a blank tag clears it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = GLASS_BURST_BATCH)
    public static void aFlaskHeatedPastFiveHundredAndFiftyCracksAndLetsItsContentsOut(GameTestHelper helper)
    {
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        retort.receiver().fill(new FluidStack(fluidOf("tfcpp:hydrochloric_acid"), 10), IFluidHandler.FluidAction.EXECUTE);
        retort.input().fill(new FluidStack(fluidOf("tfcpp:sulfuric_acid"), 4), IFluidHandler.FluidAction.EXECUTE);
        retort.interact(new ItemStack(itemOf("tfc:powder/salt"), 5), player(helper), InteractionHand.MAIN_HAND);
        final TestPlayer near = playerAt(helper, new BlockPos(2, 1, 4));
        retort.warmTo(540f);
        helper.onEachTick(() -> retort.setTemperature(700f));

        helper.runAtTickTime(40, () -> {
            helper.assertTrue(helper.getBlockState(RETORT).isAir(), "The flask is gone");
            helper.assertTrue(near.lastDamage != null && near.lastDamage.is(CorrosiveSpill.CORROSIVE), "The acid burns the player by it");
            helper.assertTrue(Math.abs(totalInBox(helper, 5, 4, Gas.HYDROGEN_CHLORIDE) - 100f) < 0.5f, "10 L of spirit of salt fume as 100 mol of hydrogen chloride, got " + totalInBox(helper, 5, 4, Gas.HYDROGEN_CHLORIDE));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(RETORT)).inflate(2), entity -> entity.getItem().is(Chemistry.GLASS_RETORT_ITEM.get())).isEmpty(), "Cracked glass drops nothing");
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(RETORT)).inflate(2), entity -> entity.getItem().is(itemOf("tfc:powder/salt"))).isEmpty(), "But the powder lies where it fell");
            near.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BURST_BATCH)
    public static void aFlaskAt549DoesNotCrackAndOneBrokenByHandSpillsTheSame(GameTestHelper helper)
    {
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        retort.receiver().fill(new FluidStack(fluidOf("tfcpp:nitric_acid"), 6), IFluidHandler.FluidAction.EXECUTE);
        final TestPlayer near = playerAt(helper, new BlockPos(2, 1, 4));
        retort.warmTo(549f);
        helper.runAtTickTime(10, () -> {
            retort.warmTo(549f);
            helper.assertTrue(!helper.getBlockState(RETORT).isAir(), "Just below the limit the glass holds");
            helper.getLevel().destroyBlock(helper.absolutePos(RETORT), true);
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(near.lastDamage != null && near.lastDamage.is(CorrosiveSpill.CORROSIVE), "Broken by hand it splashes the player by it");
            helper.assertTrue(Math.abs(totalInBox(helper, 5, 4, Gas.NITROGEN_OXIDES) - 30f) < 0.5f, "6 L of nitric acid fume as 30 mol of nitrous fumes, got " + totalInBox(helper, 5, 4, Gas.NITROGEN_OXIDES));
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(RETORT)).inflate(2), entity -> entity.getItem().is(Chemistry.GLASS_RETORT_ITEM.get())).isEmpty(), "A retort broken by hand drops itself");
            near.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void theGlassRecipesLoadAndTheBatchWorksOnABlowpipe(GameTestHelper helper)
    {
        final var manager = helper.getLevel().getRecipeManager();
        for (DistillationTable.Row row : DistillationTable.ROWS)
        {
            final var holder = manager.byKey(TFCpp.id("glass_distillation/" + row.name()));
            helper.assertTrue(holder.isPresent() && holder.get().value() instanceof DistillationRecipe, "The distillation recipe " + row.name() + " must load");
        }
        for (String id : new String[] {"crafting/borosilicate_glass_batch", "crafting/sand_bath", "glassworking/glass_carboy", "glassworking/glass_retort"})
        {
            helper.assertTrue(manager.byKey(TFCpp.id(id)).isPresent(), "The recipe " + id + " must load");
        }

        // The batch goes on a blowpipe as any batch does, and the steps of the recipe give the vessels
        final ItemStack batch = new ItemStack(Chemistry.BOROSILICATE_GLASS_BATCH.get());
        helper.assertTrue(batch.is(net.dries007.tfc.common.TFCTags.Items.GLASS_BATCHES), "The batch is a glass batch of TFC");
        final var carboyOperations = List.of(GlassOperation.BLOW.get(), GlassOperation.BLOW.get(), GlassOperation.ROLL.get(), GlassOperation.SAW.get());
        final var retortOperations = List.of(GlassOperation.BLOW.get(), GlassOperation.STRETCH.get(), GlassOperation.PINCH.get(), GlassOperation.SAW.get());
        for (var entry : List.of(java.util.Map.entry(carboyOperations, Chemistry.GLASS_CARBOY.get()), java.util.Map.entry(retortOperations, Chemistry.GLASS_RETORT_ITEM.get())))
        {
            final ItemStack pipe = GlassWorking.createWithBatch(new ItemStack(TFCItems.BLOWPIPE_WITH_GLASS.asItem()), batch);
            for (GlassOperation operation : entry.getKey())
            {
                GlassWorking.apply(pipe, operation);
            }
            final @Nullable GlassworkingRecipe recipe = GlassworkingRecipe.get(helper.getLevel(), pipe);
            helper.assertTrue(recipe != null && recipe.getResultItem(helper.getLevel().registryAccess()).is(entry.getValue()), "The steps give " + entry.getValue());
        }
        helper.succeed();
    }

    private static ItemStack pipeWith(Item pipe, ItemStack batch, float celsius)
    {
        final ItemStack stack = GlassWorking.createWithBatch(new ItemStack(pipe), batch);
        net.dries007.tfc.common.component.heat.HeatCapability.setTemperature(stack, celsius);
        return stack;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void borosilicateIsWorkedFromTwelveFiftyOnACeramicPipeAndNeverOnBrass(GameTestHelper helper)
    {
        final ItemStack ours = new ItemStack(Chemistry.BOROSILICATE_GLASS_BATCH.get());
        final ItemStack silica = new ItemStack(TFCItems.SILICA_GLASS_BATCH.asItem());
        final GlassOperation blow = GlassOperation.BLOW.get(), saw = GlassOperation.SAW.get();
        final Item ceramic = TFCItems.CERAMIC_BLOWPIPE_WITH_GLASS.asItem(), brass = TFCItems.BLOWPIPE_WITH_GLASS.asItem();

        helper.assertFalse(blow.hasRequiredTemperature(pipeWith(ceramic, ours, 1000f)), "At 1000 C TFC alone would say yes at 480, and borosilicate is still stiff");
        helper.assertFalse(blow.hasRequiredTemperature(pipeWith(ceramic, ours, 1249f)), "Just short of the working point");
        helper.assertTrue(blow.hasRequiredTemperature(pipeWith(ceramic, ours, 1300f)), "From 1250 C it is worked on a ceramic pipe");
        helper.assertFalse(blow.hasRequiredTemperature(pipeWith(brass, ours, 1300f)), "A brass pipe melts at 930 C and cannot work it at any heat");
        helper.assertFalse(blow.hasRequiredTemperature(pipeWith(brass, ours, 700f)), "Not cold either");
        helper.assertTrue(blow.hasRequiredTemperature(pipeWith(brass, silica, 600f)), "Silica glass on brass is worked at 600 C as in TFC");
        helper.assertTrue(blow.hasRequiredTemperature(pipeWith(ceramic, silica, 600f)), "And on ceramic");
        helper.assertFalse(blow.hasRequiredTemperature(pipeWith(brass, silica, 300f)), "And not below its own 480 C");
        helper.assertTrue(saw.hasRequiredTemperature(pipeWith(ceramic, ours, 25f)), "Sawing needs no heat of either glass");

        // The batch is not put on a brass pipe by hand or by crafting
        final var recipes = helper.getLevel().getRecipeManager();
        final var brassRecipe = recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "crafting/blowpipe_with_glass"));
        final var ceramicRecipe = recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "crafting/ceramic_blowpipe_with_glass"));
        helper.assertTrue(brassRecipe.isPresent() && ceramicRecipe.isPresent(), "Both pipe recipes load");
        final ItemStack brassPipe = new ItemStack(TFCItems.BLOWPIPE.asItem()), ceramicPipe = new ItemStack(TFCItems.CERAMIC_BLOWPIPE.asItem());
        helper.assertFalse(((net.minecraft.world.item.crafting.CraftingRecipe) brassRecipe.get().value()).matches(net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(ours, brassPipe)), helper.getLevel()), "Borosilicate and a brass pipe make nothing");
        helper.assertTrue(((net.minecraft.world.item.crafting.CraftingRecipe) brassRecipe.get().value()).matches(net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(silica, brassPipe)), helper.getLevel()), "Silica and a brass pipe still do");
        helper.assertTrue(((net.minecraft.world.item.crafting.CraftingRecipe) ceramicRecipe.get().value()).matches(net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(ours, ceramicPipe)), helper.getLevel()), "Borosilicate and a ceramic pipe do");

        final FakePlayer player = player(helper);
        final net.minecraft.world.inventory.Slot brassSlot = new net.minecraft.world.inventory.Slot(new net.minecraft.world.SimpleContainer(brassPipe.copy()), 0, 0, 0);
        final var brassClick = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.ItemStackedOnOtherEvent(ours.copy(), brassSlot.getItem(), brassSlot, net.minecraft.world.inventory.ClickAction.SECONDARY, player, net.minecraft.world.entity.SlotAccess.NULL));
        helper.assertTrue(brassClick.isCanceled() && brassSlot.getItem().is(TFCItems.BLOWPIPE.asItem()), "Clicking the batch on a brass pipe is refused and the pipe stays as it was, got " + brassSlot.getItem());
        final net.minecraft.world.inventory.Slot ceramicSlot = new net.minecraft.world.inventory.Slot(new net.minecraft.world.SimpleContainer(ceramicPipe.copy()), 0, 0, 0);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.ItemStackedOnOtherEvent(ours.copy(), ceramicSlot.getItem(), ceramicSlot, net.minecraft.world.inventory.ClickAction.SECONDARY, player, net.minecraft.world.entity.SlotAccess.NULL));
        helper.assertTrue(ceramicSlot.getItem().is(TFCItems.CERAMIC_BLOWPIPE_WITH_GLASS.asItem()) && BorosilicateGlass.carriesOurs(ceramicSlot.getItem()), "On a ceramic pipe TFC takes it as any batch");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void theBorosilicateBatchIsTwoSandsAndBoraxAndNoLime(GameTestHelper helper)
    {
        final var holder = helper.getLevel().getRecipeManager().byKey(TFCpp.id("crafting/borosilicate_glass_batch"));
        helper.assertTrue(holder.isPresent(), "The batch recipe loads");
        final var recipe = holder.get().value();
        helper.assertTrue(recipe.getIngredients().size() == 3, "Borax and two sands, got " + recipe.getIngredients().size());
        helper.assertFalse(recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(TFCItems.POWDERS.get(net.dries007.tfc.common.items.Powder.LIME).asItem()))), "No lime in borosilicate");
        helper.assertTrue(recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(BuiltInRegistries.ITEM.get(TFCpp.id("powder/borax"))))), "And borax");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void ligniteCharIsAFuelOfTheForgeAndThePitButNotOfABlastFurnace(GameTestHelper helper)
    {
        final ItemStack charStack = new ItemStack(Chemistry.LIGNITE_CHAR.get());
        helper.assertTrue(charStack.is(net.dries007.tfc.common.TFCTags.Items.FORGE_FUEL), "A forge burns lignite char");
        helper.assertTrue(charStack.is(net.dries007.tfc.common.TFCTags.Items.FIREPIT_FUEL), "A firepit does too");
        helper.assertFalse(charStack.is(net.dries007.tfc.common.TFCTags.Items.BLAST_FURNACE_FUEL), "A blast furnace does not take it: it crumbles and carries its sulfur");
        final @Nullable var fuel = net.dries007.tfc.util.data.Fuel.get(charStack);
        helper.assertTrue(fuel != null && fuel.temperature() == 1100f && fuel.duration() == 1500, "It burns at 1100 C for 1500 ticks, got " + (fuel == null ? "nothing" : fuel.temperature() + " C, " + fuel.duration()));
        final @Nullable var coal = net.dries007.tfc.util.data.Fuel.get(new ItemStack(Items.COAL));
        helper.assertTrue(coal != null && fuel.temperature() < coal.temperature() && fuel.duration() < coal.duration(), "Cooler and shorter than coal");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void superphosphateAndAmmoniumSulfateAreFertilisers(GameTestHelper helper)
    {
        final @Nullable Fertilizer phosphate = Fertilizer.get(new ItemStack(Chemistry.SUPERPHOSPHATE.get()));
        helper.assertTrue(phosphate != null && Math.abs(phosphate.phosphorus() - 0.12f) < 1e-6f && phosphate.nitrogen() == 0f && phosphate.potassium() == 0f, "Superphosphate is phosphorus 0.12, got " + phosphate);
        final @Nullable Fertilizer sulfate = Fertilizer.get(new ItemStack(Chemistry.AMMONIUM_SULFATE.get()));
        helper.assertTrue(sulfate != null && Math.abs(sulfate.nitrogen() - 0.1f) < 1e-6f && sulfate.phosphorus() == 0f && sulfate.potassium() == 0f, "Ammonium sulfate is nitrogen 0.1, got " + sulfate);
        final @Nullable Fertilizer apatite = Fertilizer.get(new ItemStack(Metallurgy.APATITE_POWDER.get()));
        helper.assertTrue(apatite != null && phosphate.phosphorus() > apatite.phosphorus(), "Made soluble, the phosphate feeds more than the raw rock");
        helper.succeed();
    }

    // --- Stage G: the marsh apparatus and the gold leaf indicator ----------------------------------------------------------------------------------------------------------

    /** Its own batch, as these put mercury and arsenic into the air of a test. */
    private static final String MARSH_BATCH = "tfcpp_marsh";
    private static final BlockPos LEAF = new BlockPos(2, 1, 2);

    private static ItemStack carboyOf(Liquid liquid, int millibuckets)
    {
        final ItemStack carboy = new ItemStack(Chemistry.GLASS_CARBOY.get());
        carboy.getCapability(Capabilities.FluidHandler.ITEM).fill(new FluidStack(Chemistry.fluid(liquid), millibuckets), IFluidHandler.FluidAction.EXECUTE);
        return carboy;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = MARSH_BATCH)
    public static void aCarboyChargesTheApparatusForEightTestsWithOneMillibucket(GameTestHelper helper)
    {
        final TestPlayer player = playerAt(helper, new BlockPos(6, 1, 6));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Chemistry.MARSH_APPARATUS.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, carboyOf(Liquid.SULFURIC_ACID, 25));
        Chemistry.MARSH_APPARATUS.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        final int tests = MarshApparatusItem.testsLeft(player.getMainHandItem());
        helper.assertTrue(tests == MarshApparatusItem.TESTS_PER_CHARGE, "A charge gives eight tests, got " + tests);
        final FluidStack left = player.getOffhandItem().getCapability(Capabilities.FluidHandler.ITEM).getFluidInTank(0);
        helper.assertTrue(left.getAmount() == 24 && left.getFluid() == Chemistry.fluid(Liquid.SULFURIC_ACID), "Exactly one millibucket comes out of the carboy, got " + left.getAmount());
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = MARSH_BATCH)
    public static void aTestUsesOneAndAnApparatusIsNotChargedWithoutAcid(GameTestHelper helper)
    {
        final TestPlayer player = playerAt(helper, new BlockPos(6, 1, 6));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Chemistry.MARSH_APPARATUS.get()));
        player.getMainHandItem().set(Chemistry.MARSH_TESTS.get(), 3);
        Chemistry.MARSH_APPARATUS.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(MarshApparatusItem.testsLeft(player.getMainHandItem()) == 2, "A test uses one, got " + MarshApparatusItem.testsLeft(player.getMainHandItem()));

        // Empty, with no acid in the other hand: nothing is charged
        player.getMainHandItem().set(Chemistry.MARSH_TESTS.get(), 0);
        Chemistry.MARSH_APPARATUS.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(MarshApparatusItem.testsLeft(player.getMainHandItem()) == 0, "With no acid in the other hand nothing is charged");

        // A carboy of any other liquid is no reagent either
        player.setItemInHand(InteractionHand.OFF_HAND, carboyOf(Liquid.WOOD_SPIRIT, 25));
        Chemistry.MARSH_APPARATUS.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(MarshApparatusItem.testsLeft(player.getMainHandItem()) == 0, "Only sulfuric or chamber acid charges it");
        helper.assertTrue(player.getOffhandItem().getCapability(Capabilities.FluidHandler.ITEM).getFluidInTank(0).getAmount() == 25, "A carboy of the wrong liquid gives up none of it");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = MARSH_BATCH)
    public static void theAirAtTheEyeGivesADenseMirrorWithArsenicAndNoneWhenClean(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final TestPlayer poisoned = playerAt(helper, new BlockPos(2, 1, 6));
        final TestPlayer clean = playerAt(helper, new BlockPos(6, 1, 6));
        final BlockPos eye = BlockPos.containing(poisoned.getEyePosition());
        // 0.0002 mol/m3 of arsenic trioxide is about 40 mg/m3, well above the dense mirror of 20
        final @Nullable float[] cell = GasTracker.get(level).volume(level).cellForEmission(eye.getX(), eye.getY(), eye.getZ());
        helper.assertTrue(cell != null, "The air at the eye must have a cell");
        cell[Gas.ARSENIC_TRIOXIDE.ordinal()] = 0.0002f;

        helper.assertTrue(MarshApparatusItem.sample(level, poisoned, ItemStack.EMPTY) == PoisonTests.Mirror.STRONG, "Arsenic in the air at the eye gives a dense mirror");
        helper.assertTrue(MarshApparatusItem.sample(level, poisoned, carboyOf(Liquid.SULFURIC_ACID, 25)) == PoisonTests.Mirror.STRONG, "A carboy in the other hand is never the sample: the air is tested");
        helper.assertTrue(MarshApparatusItem.sample(level, clean, ItemStack.EMPTY) == PoisonTests.Mirror.NONE, "Clean air gives no mirror");
        poisoned.discard();
        clean.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = MARSH_BATCH)
    public static void aPowderInTheOtherHandGivesADenseMirrorAndAnOrdinaryItemNone(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final TestPlayer player = playerAt(helper, new BlockPos(6, 1, 6));
        helper.assertTrue(MarshApparatusItem.sample(level, player, new ItemStack(Instruments.ARSENIC_TRIOXIDE.get())) == PoisonTests.Mirror.STRONG, "White arsenic gives a dense mirror");
        helper.assertTrue(MarshApparatusItem.sample(level, player, new ItemStack(Instruments.ANTIMONY_TRIOXIDE.get())) == PoisonTests.Mirror.STRONG, "Antimony oxide gives the same mirror");
        helper.assertTrue(MarshApparatusItem.sample(level, player, new ItemStack(BuiltInRegistries.ITEM.get(TFCpp.id("metal/ingot/arsenical_copper")))) == PoisonTests.Mirror.FAINT, "Arsenical copper gives a faint mirror");
        helper.assertTrue(MarshApparatusItem.sample(level, player, new ItemStack(Items.DIRT)) == PoisonTests.Mirror.NONE, "An ordinary item gives none");
        player.discard();
        helper.succeed();
    }

    private static float arsenicAround(GameTestHelper helper, TestPlayer player)
    {
        final ServerLevel level = helper.getLevel();
        final BlockPos eye = BlockPos.containing(player.getEyePosition());
        float sum = 0f;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -2; dy <= 60; dy++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    final @Nullable float[] cell = GasTracker.get(level).volume(level).cell(eye.getX() + dx, eye.getY() + dy, eye.getZ() + dz);
                    sum += cell == null ? 0f : cell[Gas.ARSENIC_TRIOXIDE.ordinal()];
                }
            }
        }
        return sum;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = MARSH_BATCH)
    public static void aTestOfASampleLetsOutItsArsineAndATestOfTheAirAddsNone(GameTestHelper helper)
    {
        final TestPlayer sampler = playerAt(helper, new BlockPos(2, 1, 2));
        sampler.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Chemistry.MARSH_APPARATUS.get()));
        sampler.getMainHandItem().set(Chemistry.MARSH_TESTS.get(), 2);
        sampler.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Instruments.ARSENIC_TRIOXIDE.get()));
        Chemistry.MARSH_APPARATUS.get().use(helper.getLevel(), sampler, InteractionHand.MAIN_HAND);
        final float let = arsenicAround(helper, sampler);
        helper.assertTrue(Math.abs(let - PoisonTests.STRONG_PINCH_MOLES) < 1e-7f, "A test of white arsenic lets out a pinch of arsenic, " + PoisonTests.STRONG_PINCH_MOLES + " mol, got " + let);

        final TestPlayer airTester = playerAt(helper, new BlockPos(7, 1, 7));
        airTester.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Chemistry.MARSH_APPARATUS.get()));
        airTester.getMainHandItem().set(Chemistry.MARSH_TESTS.get(), 2);
        Chemistry.MARSH_APPARATUS.get().use(helper.getLevel(), airTester, InteractionHand.MAIN_HAND);
        helper.assertTrue(arsenicAround(helper, airTester) == 0f, "A test of clean air lets nothing out");

        final TestPlayer plain = playerAt(helper, new BlockPos(7, 1, 2));
        plain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Chemistry.MARSH_APPARATUS.get()));
        plain.getMainHandItem().set(Chemistry.MARSH_TESTS.get(), 2);
        plain.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT));
        Chemistry.MARSH_APPARATUS.get().use(helper.getLevel(), plain, InteractionHand.MAIN_HAND);
        helper.assertTrue(arsenicAround(helper, plain) == 0f, "A sample with no arsenic lets none out");

        helper.assertTrue(MarshApparatusItem.messageKey(PoisonTests.Mirror.STRONG, true).equals("tfcpp.marsh.sample_strong") && MarshApparatusItem.messageKey(PoisonTests.Mirror.STRONG, false).equals("tfcpp.marsh.strong"), "A sample has its own message, which says nothing of the air");
        sampler.discard();
        airTester.discard();
        plain.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = MARSH_BATCH)
    public static void aLeafWhitensThroughItsStagesInMercuryAndKeepsItsDose(GameTestHelper helper)
    {
        helper.setBlock(LEAF, Instruments.GOLD_LEAF_INDICATOR.get());
        final ServerLevel level = helper.getLevel();
        final BlockPos at = helper.absolutePos(LEAF);
        final GoldLeafIndicatorBlockEntity leaf = helper.getBlockEntity(LEAF);
        // 0.00005 mol/m3 of mercury is about 10 mg/m3: a dose of about 0.17 each second, so stage 3 takes some 600 seconds of it
        final @Nullable float[] cell = GasTracker.get(level).volume(level).cellForEmission(at.getX(), at.getY(), at.getZ());
        helper.assertTrue(cell != null, "The leaf must have a cell");
        cell[Gas.MERCURY_VAPOR.ordinal()] = 0.00005f;

        for (int second = 1; second <= 700; second++)
        {
            leaf.breathe(level);
            final int stage = helper.getBlockState(LEAF).getValue(GoldLeafIndicatorBlock.STAGE);
            helper.assertTrue(stage == PoisonTests.leafStage(leaf.dose()), "The stage follows the dose, at second " + second + " with dose " + leaf.dose());
        }
        helper.assertTrue(helper.getBlockState(LEAF).getValue(GoldLeafIndicatorBlock.STAGE) == 3, "The leaf is white after 700 seconds in mercury, got dose " + leaf.dose());

        // A white leaf reads no more air
        final float dose = leaf.dose();
        cell[Gas.MERCURY_VAPOR.ordinal()] = 0.01f;
        leaf.breathe(level);
        helper.assertTrue(leaf.dose() == dose, "A leaf that is white takes up no more mercury");

        final var registries = level.registryAccess();
        final GoldLeafIndicatorBlockEntity copy = new GoldLeafIndicatorBlockEntity(at, helper.getBlockState(LEAF));
        copy.loadWithComponents(leaf.saveWithoutMetadata(registries), registries);
        helper.assertTrue(copy.dose() == dose, "The dose is kept through a save and a load, got " + copy.dose());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = MARSH_BATCH)
    public static void aLeafInCleanAirDoesNotMove(GameTestHelper helper)
    {
        final BlockPos clean = new BlockPos(6, 1, 6);
        helper.setBlock(clean, Instruments.GOLD_LEAF_INDICATOR.get());
        final GoldLeafIndicatorBlockEntity leaf = helper.getBlockEntity(clean);
        for (int second = 0; second < 100; second++)
        {
            leaf.breathe(helper.getLevel());
        }
        helper.assertTrue(leaf.dose() == 0f && helper.getBlockState(clean).getValue(GoldLeafIndicatorBlock.STAGE) == 0, "A leaf in clean air stays bright");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = MARSH_BATCH)
    public static void aCleanLeafComesUpAsANewOneAndAWhitenedOneAsNothing(GameTestHelper helper)
    {
        final BlockPos at = helper.absolutePos(new BlockPos(6, 1, 6));
        final Block leaf = Instruments.GOLD_LEAF_INDICATOR.get();
        final List<ItemStack> clean = Block.getDrops(leaf.defaultBlockState(), helper.getLevel(), at, null);
        helper.assertTrue(clean.size() == 1 && clean.get(0).is(Instruments.GOLD_LEAF_INDICATOR_ITEM.get()), "A clean leaf comes up as a new one");
        final List<ItemStack> whitened = Block.getDrops(leaf.defaultBlockState().setValue(GoldLeafIndicatorBlock.STAGE, 1), helper.getLevel(), at, null);
        helper.assertTrue(whitened.isEmpty(), "A whitened leaf comes up as nothing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = MARSH_BATCH)
    public static void theMarshAndLeafRecipesLoad(GameTestHelper helper)
    {
        final var manager = helper.getLevel().getRecipeManager();
        helper.assertTrue(manager.byKey(TFCpp.id("crafting/marsh_apparatus")).isPresent(), "The marsh apparatus must be craftable");
        helper.assertTrue(manager.byKey(TFCpp.id("crafting/gold_leaf_indicator")).isPresent(), "The gold leaf indicator must be craftable");
        helper.succeed();
    }

    // --- Batch 4 of the review: fittings, the pump, the retort and the chamber --------------------------------------------------------------------------------------------------

    /** Apart from the others, as it runs the calendar fast to bring a whole plant through its work. */
    private static final String PLANT_BATCH = "tfcpp_acid_plant";
    /** Calendar ticks to each game tick: it is coprime to the twenty of a second, so a worker's turn comes up once in every second. */
    private static final float PLANT_RATE = 3f;
    private static final float DEFAULT_RATE = 20f / 24f;

    @AfterBatch(batch = PLANT_BATCH)
    public static void afterPlant(ServerLevel level)
    {
        Calendars.SERVER.setCalendarTickRate(DEFAULT_RATE);
    }

    /** Uses the block the way the game does for a person who stands there: through the game mode, which is what decides whether a block is used at all when the person is crouching. */
    private static void useAsPlayer(GameTestHelper helper, FakePlayer player, BlockPos pos)
    {
        final BlockPos absolute = helper.absolutePos(pos);
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static BlockState fittingState(Supplier<Block> block, Direction facing, boolean open)
    {
        return block.get().defaultBlockState().setValue(PipeFittingBlock.FACING, facing).setValue(PipeFittingBlock.OPEN, open);
    }

    private static final List<Map<PipeMaterial, Supplier<Block>>> FITTING_KINDS = List.of(Pipes.VALVES, Pipes.VENTS, Pipes.TAPS, Pipes.SAFETY_VALVES);
    private static final List<String> FITTING_NAMES = List.of("pipe_valve", "pipe_vent", "pipe_tap", "safety_valve");

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void everyFittingComesInIronLeadAndStonewareWithItsOwnMaterialAndRecipe(GameTestHelper helper)
    {
        for (int kind = 0; kind < FITTING_KINDS.size(); kind++)
        {
            for (PipeMaterial material : Pipes.FITTING_MATERIALS)
            {
                final String name = Pipes.fittingName(FITTING_NAMES.get(kind), material);
                final Supplier<Block> block = FITTING_KINDS.get(kind).get(material);
                helper.assertTrue(block != null, name + " is registered");
                helper.assertTrue(BuiltInRegistries.BLOCK.getKey(block.get()).equals(TFCpp.id(name)), name + " has its name, got " + BuiltInRegistries.BLOCK.getKey(block.get()));
                helper.assertTrue(((PipeFittingBlock) block.get()).material(block.get().defaultBlockState()) == material, name + " is of " + material);
                helper.assertTrue(BuiltInRegistries.ITEM.containsKey(TFCpp.id(name)), name + " has an item");
                helper.assertTrue(helper.getLevel().getRecipeManager().byKey(TFCpp.id("crafting/" + name)).isPresent(), name + " has a recipe");
            }
        }
        helper.assertTrue(Pipes.FITTING_MATERIALS.containsAll(List.of(PipeMaterial.WROUGHT_IRON, PipeMaterial.LEAD, PipeMaterial.CERAMIC)), "Iron, lead and stoneware are all there");
        helper.assertTrue(Pipes.VALVE.get() == Pipes.VALVES.get(PipeMaterial.WROUGHT_IRON).get(), "The old names are the iron ones");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1500, batch = TIME_BATCH)
    public static void aValveOfStonewareStandsChamberAcidAndOneOfLeadHoldsItWhereIronDoesNot(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        Calendars.SERVER.setCalendarTickRate(REAL_TIME_RATE);
        final PipeMaterial[] materials = {PipeMaterial.WROUGHT_IRON, PipeMaterial.LEAD, PipeMaterial.CERAMIC};
        final BlockPos[] starts = {new BlockPos(1, 1, 1), new BlockPos(1, 1, 4), new BlockPos(1, 1, 7)};
        for (int i = 0; i < materials.length; i++)
        {
            helper.setBlock(starts[i], Pipes.PIPES.get(materials[i]).get());
            helper.setBlock(starts[i].east(), fittingState(Pipes.VALVES.get(materials[i]), Direction.EAST, true));
        }
        helper.runAtTickTime(5, () -> {
            for (BlockPos start : starts)
            {
                helper.assertTrue(networkAt(helper, start) != null && networkAt(helper, start) == networkAt(helper, start.east()), "The pipe and its open valve join");
                networkAt(helper, start).contents().addLiquid(ACID, 8f, PipePhysics.ROOM_TEMPERATURE); // Not more than the 15 litres of the run: a full run is compressed liquid
            }
        });
        helper.runAtTickTime(1300, () -> {
            final float[] lost = new float[materials.length];
            for (int i = 0; i < materials.length; i++)
            {
                final @Nullable PipeNetwork network = networkAt(helper, starts[i].east());
                helper.assertTrue(network != null, "The run of " + materials[i] + " is whole");
                float integrity = -1f;
                for (PipeSegment segment : network.segments())
                {
                    if (segment.pos.equals(helper.absolutePos(starts[i].east())))
                    {
                        integrity = segment.integrity;
                    }
                }
                helper.assertTrue(integrity >= 0f, "The valve is a segment of its run");
                lost[i] = 1f - integrity;
                if (materials[i] == PipeMaterial.WROUGHT_IRON)
                {
                    final float expected = PipeMaterial.WROUGHT_IRON.corrosion(ACID) * network.contents().liquidVolume() / network.volume() * 1200f / LocalTime.TICKS_PER_DAY;
                    helper.assertTrue(lost[i] > expected * 0.5f && lost[i] < expected * 2f, "An iron valve in chamber acid wears by " + expected + " in the minute, got " + lost[i]);
                }
            }
            helper.assertTrue(lost[1] * 10f < lost[0], "The lead valve holds the cool acid: it loses " + lost[1] + " against the iron's " + lost[0]);
            helper.assertTrue(lost[2] == 0f, "The stoneware valve loses nothing, got " + lost[2]);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aSafetyValveOfStonewareLiftsBeforeItsBodyBurstsAndAnIronOneHoldsAtTheSamePressure(GameTestHelper helper)
    {
        final BlockPos iron = new BlockPos(1, 1, 1), ceramic = new BlockPos(1, 1, 5);
        helper.setBlock(iron, Pipes.PIPES.get(PipeMaterial.WROUGHT_IRON).get());
        helper.setBlock(iron.east(), fittingState(Pipes.SAFETY_VALVES.get(PipeMaterial.WROUGHT_IRON), Direction.UP, false));
        helper.setBlock(ceramic, Pipes.PIPES.get(PipeMaterial.CERAMIC).get());
        helper.setBlock(ceramic.east(), fittingState(Pipes.SAFETY_VALVES.get(PipeMaterial.CERAMIC), Direction.UP, false));
        helper.runAtTickTime(5, () -> {
            for (BlockPos start : new BlockPos[] {iron, ceramic})
            {
                final PipeNetwork network = networkAt(helper, start);
                network.contents().temperature = PipePhysics.ROOM_TEMPERATURE;
                network.contents().air = PipePhysics.moles(4.2f, PipePhysics.ROOM_TEMPERATURE, network.volume()); // 3.2 atm above the air outside
                final BlockPos valve = helper.absolutePos(start.east());
                final BlockState state = helper.getLevel().getBlockState(valve);
                ((PipePort) state.getBlock()).stepPort(network, helper.getLevel(), valve, state, 1f);
            }
            helper.assertFalse(helper.getBlockState(iron.east()).getValue(PipeFittingBlock.OPEN), "At 3.2 atm gauge an iron valve set for 8 stays shut");
            helper.assertTrue(helper.getBlockState(ceramic.east()).getValue(PipeFittingBlock.OPEN), "A stoneware valve is set for 2.8 and lifts, below the 3.5 its body holds");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aClickWithAnItemInTheHandDoesNotWorkTheBlocksThatTakeAnEmptyHand(GameTestHelper helper)
    {
        final List<Block> blocks = List.of(Chemistry.GAS_BURNER.get(), Chemistry.LEAD_CHAMBER.get(), Chemistry.SULFUR_BURNER.get(), Chemistry.COKE_OVEN.get(), Metallurgy.CONDENSATION_TOWER.get(),
            Metallurgy.CONVERTER.get(), Metallurgy.CUPEL_HEARTH.get(), Metallurgy.RETORT.get(), Instruments.SETTLING_BAFFLE.get(), Pipes.MANOMETER.get(),
            Pipes.VALVE.get(), Pipes.TAP.get(), Pipes.VENT.get(), Pipes.VALVES.get(PipeMaterial.LEAD).get(), Pipes.TAPS.get(PipeMaterial.CERAMIC).get());
        final FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK)); // A pipe is held to be put down beside the block
        for (int i = 0; i < blocks.size(); i++)
        {
            final BlockPos pos = new BlockPos(1 + i % 7, 1, 1 + 2 * (i / 7));
            helper.setBlock(pos, blocks.get(i));
            final BlockPos absolute = helper.absolutePos(pos);
            final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
            final BlockState before = helper.getLevel().getBlockState(absolute);
            helper.assertTrue(before.useWithoutItem(helper.getLevel(), player, hit) == net.minecraft.world.InteractionResult.PASS, blocks.get(i).getName().getString() + " lets a click with an item through");
            helper.assertTrue(helper.getLevel().getBlockState(absolute) == before, blocks.get(i).getName().getString() + " is not worked by it");
        }

        // An open gas burner is the one that fills a room: it is turned by an empty hand and not by a click that places a block
        final BlockPos burner = new BlockPos(1, 1, 7);
        helper.setBlock(burner, Chemistry.GAS_BURNER.get());
        useAsPlayer(helper, player, burner);
        helper.assertFalse(helper.getBlockState(burner).getValue(GasBurnerBlock.OPEN), "A click with a stick through the game mode does not open the burner");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        useAsPlayer(helper, player, burner);
        helper.assertTrue(helper.getBlockState(burner).getValue(GasBurnerBlock.OPEN), "An empty hand turns the wheel");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aPumpTakesOnlyFromTheTankOfAChamberAndNothingFromItsWaterOrGas(GameTestHelper helper)
    {
        helper.setBlock(CHAMBER, Chemistry.LEAD_CHAMBER.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER);
        final BlockPos pump = CHAMBER.east(), outlet = CHAMBER.east(2);
        helper.setBlock(pump, Pipes.PISTON_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        for (int i = 0; i < 3; i++)
        {
            helper.setBlock(outlet.east(i), Pipes.PIPES.get(PipeMaterial.LEAD).get());
        }
        final float[] before = new float[2];
        helper.runAtTickTime(5, () -> {
            final PipeNetwork chamberNetwork = networkAt(helper, CHAMBER);
            chamberNetwork.contents().addLiquid(WATER, 3f, PipePhysics.ROOM_TEMPERATURE); // The process water of the chamber
            chamberNetwork.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] = 0.5f;
            before[0] = chamberNetwork.contents().litres;
            before[1] = chamberNetwork.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()];
        });
        helper.runAtTickTime(10, () -> {
            final PistonPumpBlockEntity piston = helper.getBlockEntity(pump);
            helper.assertTrue(chamber.tank().isEmpty(), "The tank is empty");
            piston.pump(helper.getLevel(), PistonPumpBlockEntity.SWEPT_VOLUME);
            final PipeNetwork out = networkAt(helper, outlet);
            final PipeNetwork chamberNetwork = networkAt(helper, CHAMBER);
            helper.assertTrue(out.contents().litres == 0f, "With nothing in the tank the pump draws no liquid into the acid line, got " + out.contents().litres + " L of " + out.contents().liquid);
            helper.assertTrue(out.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] == 0f, "Nor the gas of the chamber, got " + out.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()]);
            helper.assertTrue(chamberNetwork.contents().litres == before[0] && chamberNetwork.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] == before[1], "The chamber keeps its water and gas");

            chamber.tank().fill(new FluidStack(acid(), 2), IFluidHandler.FluidAction.EXECUTE);
            piston.pump(helper.getLevel(), PistonPumpBlockEntity.SWEPT_VOLUME);
            helper.assertTrue(ACID.equals(out.contents().liquid) && Math.abs(out.contents().litres - 2f) < 1e-3f, "With acid in the tank the stroke takes exactly that, got " + out.contents().litres + " L of " + out.contents().liquid);
            helper.assertTrue(chamberNetwork.contents().litres == before[0], "And still none of the water");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void aCrouchingEmptyHandTakesTheChargeAndTheProductOutOfARetortThroughTheGameMode(GameTestHelper helper)
    {
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        final DistillationTable.Row row = DistillationTable.NITRIC_ACID;
        final FakePlayer player = player(helper);
        helper.runAtTickTime(2, () -> {
            retort.fluids(Direction.UP).fill(new FluidStack(fluidOf(row.fluid()), 5), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(retort.interact(new ItemStack(itemOf(row.item()), 11), player(helper), InteractionHand.MAIN_HAND), "Eleven powders of saltpeter go into the slot");
            retort.warmTo(150f);
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(retort.product().getCount() == 8 && retort.charge().getCount() == 3, "A batch takes eight and leaves three that no batch will take: the slot is stuck with them, got " + retort.charge().getCount());

            player.setShiftKeyDown(false);
            useAsPlayer(helper, player, RETORT);
            helper.assertTrue(retort.charge().getCount() == 3 && player.getInventory().isEmpty(), "A plain click with an empty hand only reads the retort");

            player.setShiftKeyDown(true);
            useAsPlayer(helper, player, RETORT);
            helper.assertTrue(retort.charge().isEmpty() && retort.product().isEmpty(), "Crouching takes both out");
            helper.assertTrue(player.getInventory().countItem(itemOf(row.item())) == 3 && player.getInventory().countItem(Chemistry.POTASSIUM_BISULFATE.get()) == 8, "Three of saltpeter and eight of potassium bisulfate are in the hands of the person, nothing lost");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void aCrouchingCarboyTipsTheFlaskOutAndRinsesTheLastFilmSoTheNextRecipeCanRun(GameTestHelper helper)
    {
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        final FakePlayer player = player(helper);
        helper.runAtTickTime(2, () -> {
            final ItemStack carboy = new ItemStack(Chemistry.GLASS_CARBOY.get());
            helper.assertTrue(carboy.doesSneakBypassUse(helper.getLevel(), helper.absolutePos(RETORT), player), "A carboy is used on a retort even by a person who is crouching");
            helper.assertFalse(carboy.doesSneakBypassUse(helper.getLevel(), helper.absolutePos(RETORT).above(3), player), "And on nothing else");

            // Leftover that no batch takes: three litres and two fifths of acid in the flask
            retort.input().set(acid(), 3.4);
            player.setItemInHand(InteractionHand.MAIN_HAND, carboy);
            player.setShiftKeyDown(true);
            useAsPlayer(helper, player, RETORT);
            final FluidStack held = FluidHelpers.getContainedFluid(player.getItemInHand(InteractionHand.MAIN_HAND));
            helper.assertTrue(held.getFluid() == acid() && held.getAmount() == 3, "The carboy takes the three whole litres, got " + held);
            helper.assertTrue(Math.abs(retort.input().litres() - 0.4) < 1e-6, "And the film of 0.4 L stays, which no container can take, got " + retort.input().litres());

            // The film cannot be poured out, and with it there the flask holds chamber acid for ever
            useAsPlayer(helper, player, RETORT);
            helper.assertTrue(retort.input().isEmpty(), "A second crouching use rinses it out, got " + retort.input().litres());

            // And the flask now does what another recipe says
            final DistillationTable.Row row = DistillationTable.NITRIC_ACID;
            retort.fluids(Direction.UP).fill(new FluidStack(fluidOf(row.fluid()), 2), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(retort.interact(new ItemStack(itemOf(row.item()), 8), player(helper), InteractionHand.MAIN_HAND), "The powder goes in");
            retort.warmTo(150f);
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(retort.product().getCount() == 8, "A batch of nitric acid runs in the cleared flask, got " + retort.product());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void aCarboyOnlyTakesALiquidItCanHoldWhenTippingOutAFlask(GameTestHelper helper)
    {
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        final FakePlayer player = player(helper);
        helper.runAtTickTime(2, () -> {
            retort.input().set(acid(), 5.0);
            final ItemStack carboy = new ItemStack(Chemistry.GLASS_CARBOY.get());
            carboy.getCapability(Capabilities.FluidHandler.ITEM).fill(new FluidStack(fluidOf("tfcpp:nitric_acid"), 4), IFluidHandler.FluidAction.EXECUTE);
            player.setItemInHand(InteractionHand.MAIN_HAND, carboy);
            player.setShiftKeyDown(true);
            useAsPlayer(helper, player, RETORT);
            helper.assertTrue(Math.abs(retort.input().litres() - 5.0) < 1e-6, "A carboy of nitric acid takes no chamber acid, and the flask keeps it all, got " + retort.input().litres());
            final FluidStack held = FluidHelpers.getContainedFluid(player.getItemInHand(InteractionHand.MAIN_HAND));
            helper.assertTrue(held.getFluid() == fluidOf("tfcpp:nitric_acid") && held.getAmount() == 4, "And the carboy keeps its own, got " + held);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void waterPipedToTheSideOfTheRetortGoesIntoTheReceiverWaterAndNothingElseDoes(GameTestHelper helper)
    {
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        final BlockPos tap = RETORT.west(), pipe = RETORT.west(2);
        helper.setBlock(tap, fittingState(Pipes.TAPS.get(PipeMaterial.WROUGHT_IRON), Direction.EAST, true));
        helper.setBlock(pipe, Pipes.PIPES.get(PipeMaterial.WROUGHT_IRON).get());
        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = networkAt(helper, pipe);
            helper.assertTrue(network != null, "The pipe is a network");
            network.contents().addLiquid(WATER, 3f, PipePhysics.ROOM_TEMPERATURE);
            network.flowThroughTap(helper.getLevel(), helper.absolutePos(tap), Direction.EAST, 5f);
            helper.assertTrue(Math.abs(retort.water().litres() - 3.0) < 1e-6, "The water from the side tap is in the receiver's water, got " + retort.water().litres());
            helper.assertTrue(retort.input().isEmpty() && retort.reagent().isEmpty(), "Not in the flask");

            final var level = helper.getLevel();
            final BlockPos retortPos = helper.absolutePos(RETORT);
            for (Direction side : Direction.Plane.HORIZONTAL)
            {
                final @Nullable IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, retortPos, side);
                helper.assertTrue(handler != null, "Every side is open");
                helper.assertTrue(handler.fill(new FluidStack(acid(), 1), IFluidHandler.FluidAction.EXECUTE) == 0, "No acid is poured in from the side");
                helper.assertTrue(handler.fill(new FluidStack(fluidOf("tfcpp:coal_tar"), 1), IFluidHandler.FluidAction.EXECUTE) == 0, "Nor any other liquid");
            }
            retort.receiver().fill(new FluidStack(fluidOf("tfcpp:nitric_acid"), 4), IFluidHandler.FluidAction.EXECUTE);
            final FluidStack drawn = level.getCapability(Capabilities.FluidHandler.BLOCK, retortPos, Direction.NORTH).drain(10, IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(drawn.getAmount() == 4 && drawn.getFluid() == fluidOf("tfcpp:nitric_acid"), "What has condensed runs out of the side too, got " + drawn);
            helper.assertTrue(Math.abs(retort.water().litres() - 3.0) < 1e-6, "And the water stays");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1500, batch = TIME_BATCH)
    public static void aLeadChamberWearsUnderItsAcidInProportionToItsFillAndTwentyTimesFasterAboveEightyDegrees(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        Calendars.SERVER.setCalendarTickRate(REAL_TIME_RATE);
        final BlockPos cool = new BlockPos(2, 1, 2), hot = new BlockPos(2, 1, 6), empty = new BlockPos(6, 1, 2), full = new BlockPos(6, 1, 6);
        for (BlockPos pos : new BlockPos[] {cool, hot, empty, full})
        {
            helper.setBlock(pos, Chemistry.LEAD_CHAMBER.get());
        }
        helper.runAtTickTime(2, () -> {
            ((LeadChamberBlockEntity) helper.getBlockEntity(cool)).tank().fill(new FluidStack(acid(), 50), IFluidHandler.FluidAction.EXECUTE);
            ((LeadChamberBlockEntity) helper.getBlockEntity(hot)).tank().fill(new FluidStack(acid(), 50), IFluidHandler.FluidAction.EXECUTE);
            ((LeadChamberBlockEntity) helper.getBlockEntity(full)).tank().fill(new FluidStack(acid(), 100), IFluidHandler.FluidAction.EXECUTE);
        });
        helper.onEachTick(() -> {
            // The gas of the networks is held at a temperature, as a network cools to the weather with a time of about nine seconds
            for (BlockPos pos : new BlockPos[] {cool, empty, full})
            {
                final @Nullable PipeNetwork network = networkAt(helper, pos);
                if (network != null)
                {
                    network.contents().temperature = PipePhysics.ROOM_TEMPERATURE;
                }
            }
            final @Nullable PipeNetwork network = networkAt(helper, hot);
            if (network != null)
            {
                network.contents().temperature = 400f;
            }
        });
        helper.runAtTickTime(1300, () -> {
            final float days = 1200f / LocalTime.TICKS_PER_DAY;
            final float rate = PipeMaterial.LEAD.corrosion(ACID, PipePhysics.ROOM_TEMPERATURE);
            final float lostCool = 1f - networkAt(helper, cool).segments().getFirst().integrity;
            final float lostHot = 1f - networkAt(helper, hot).segments().getFirst().integrity;
            final float lostEmpty = 1f - networkAt(helper, empty).segments().getFirst().integrity;
            final float lostFull = 1f - networkAt(helper, full).segments().getFirst().integrity;
            helper.assertTrue(lostEmpty == 0f, "A chamber with no acid in its tank does not wear, got " + lostEmpty);
            helper.assertTrue(lostCool > rate * 0.5f * 0.5f * days && lostCool < rate * 0.5f * 2f * days, "Half full and cool it wears by " + rate * 0.5f * days + " in the minute, got " + lostCool);
            helper.assertTrue(Math.abs(lostFull - 2f * lostCool) < 0.1f * lostFull, "A full tank wears twice as fast as a half full one, got " + lostFull + " against " + lostCool);
            helper.assertTrue(Math.abs(lostHot - PipeMaterial.HOT_ACID_FACTOR * lostCool) < 0.1f * lostHot, "Above 80 C the film is gone and it wears " + PipeMaterial.HOT_ACID_FACTOR + " times as fast, got " + lostHot + " against " + lostCool);
            helper.succeed();
        });
    }

    /**
     * The whole of the acid plant in one test, made only of what a person would build and do: the shaft turns the pump, the pump draws chamber acid from the tank of a chamber into a lead line, a lead tap lets it into a retort, and a charcoal fire under a sand bath concentrates it. The test puts in nothing of the physics but the acid in the tank, the water of the chamber's network and the charcoal.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 4200, batch = PLANT_BATCH)
    public static void aWholePlantTakesChamberAcidFromTheChamberToConcentratedAcidInTheFlask(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        Calendars.SERVER.setCalendarTickRate(PLANT_RATE);
        final BlockPos chamberPos = new BlockPos(1, 5, 2), pumpPos = new BlockPos(2, 5, 2), axlePos = new BlockPos(2, 5, 3), motorPos = new BlockPos(2, 5, 4);
        final BlockPos firePos = new BlockPos(6, 1, 2), bathPos = firePos.above(), retortPos = bathPos.above(), tapPos = retortPos.above(), topPipe = tapPos.above();
        final Block axle = TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.AXLE).get();

        // The shaft: a motor that turns at the speed of a full water wheel, and an axle to the side of the pump
        helper.setBlock(motorPos, TFCBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        final CreativeRotationBlockEntity motor = helper.getBlockEntity(motorPos);
        for (int step = 0; step < CreativeRotationBlockEntity.MAX_STEPS; step++)
        {
            motor.incrementSpeed();
        }
        helper.setBlock(axlePos, axle.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        helper.setBlock(pumpPos, Pipes.PISTON_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));

        // The chamber behind the pump, and a lead line from the pump to the tap over the retort
        helper.setBlock(chamberPos, Chemistry.LEAD_CHAMBER.get());
        for (int x = 3; x <= topPipe.getX(); x++)
        {
            helper.setBlock(new BlockPos(x, 5, 2), Pipes.PIPES.get(PipeMaterial.LEAD).get());
        }
        helper.setBlock(tapPos, fittingState(Pipes.TAPS.get(PipeMaterial.LEAD), Direction.DOWN, false));

        // The fire, the sand bath, and the retort with no condenser: it boils water off
        helper.setBlock(firePos, TFCBlocks.FIREPIT.get());
        helper.setBlock(bathPos, Chemistry.SAND_BATH.get());
        helper.setBlock(retortPos, Chemistry.GLASS_RETORT.get());
        final LeadChamberBlockEntity chamber = helper.getBlockEntity(chamberPos);
        final GlassRetortBlockEntity retort = helper.getBlockEntity(retortPos);
        final FirepitBlockEntity firepit = helper.getBlockEntity(firePos);
        final SandBathBlockEntity bath = helper.getBlockEntity(bathPos);
        final FakePlayer player = player(helper);
        final BlockPos absoluteFire = helper.absolutePos(firePos);

        final boolean[] done = new boolean[1], received = new boolean[1];
        final float[] peak = new float[1];
        final UUID blower = UUID.randomUUID();
        helper.onEachTick(() -> ((Hearth) firepit).tfcpp$draught().blow(blower, helper.getLevel().getGameTime())); // The person at the fire, blowing through the pipe
        helper.runAtTickTime(2, () -> {
            for (int slot = 0; slot < 4; slot++)
            {
                firepit.getInventory().setStackInSlot(slot, new ItemStack(Items.CHARCOAL));
            }
            helper.assertTrue(firepit.light(helper.getLevel().getBlockState(absoluteFire)), "The firepit takes the charcoal and lights");
            // A charcoal firepit on natural draught stands at 1000 C, and the sand at its default depth of 4 passes up only 216 C of that, short of the 280 C the acid needs. With a person blowing through a pipe it stands at 1204 C, and the person crouches and takes a layer off: depth 3 gives 316 C, which wind does not change, as it adds nothing to a blower
            player.setShiftKeyDown(true);
            useAsPlayer(helper, player, bathPos);
            player.setShiftKeyDown(false);
            helper.assertTrue(helper.getBlockState(bathPos).getValue(SandBathBlock.DEPTH) == 3, "Crouching with an empty hand takes a layer of sand off");
            chamber.tank().fill(new FluidStack(acid(), 2), IFluidHandler.FluidAction.EXECUTE); // What the chamber has made
            final PipeNetwork chamberNetwork = networkAt(helper, chamberPos);
            helper.assertTrue(chamberNetwork != null, "The chamber is a network of its own");
            chamberNetwork.contents().addLiquid(WATER, 3f, PipePhysics.ROOM_TEMPERATURE); // Its process water: it must not be drawn into the acid line
            // The tap is opened by hand, the way a person does it
            useAsPlayer(helper, player, tapPos);
            helper.assertTrue(helper.getBlockState(tapPos).getValue(PipeFittingBlock.OPEN), "The hand wheel of the tap opens it");
        });

        // The acid goes from the tank through the pump, the line and the tap into the flask, the water of the chamber stays, and nothing is left on the way
        helper.runAtTickTime(250, () -> {
            final PipeNetwork line = networkAt(helper, topPipe);
            helper.assertTrue(line != null && line != networkAt(helper, chamberPos), "The line is a network of its own");
            helper.assertTrue(chamber.tank().isEmpty(), "The pump has drawn the tank empty, got " + chamber.tank().getFluidAmount());
            helper.assertTrue(Math.abs(networkAt(helper, chamberPos).contents().litres - 3f) < 1e-3f, "The water of the chamber's network was not drawn, got " + networkAt(helper, chamberPos).contents().litres);
            helper.assertTrue(line.contents().litres < 1e-3f, "The line is empty again: the tap let it all through, got " + line.contents().litres + " L of " + line.contents().liquid);
            helper.assertTrue(retort.input().fluid() == acid() && Math.abs(retort.input().litres() - 2.0) < 1e-3, "Two litres of chamber acid are in the flask, got " + retort.input().litres());
            helper.assertTrue(retort.reagent().isEmpty() && retort.water().isEmpty(), "And nothing else is in the retort");
        });

        // The sand bath brings the flask to the working range, and two batches of a minute each boil the water off
        helper.onEachTick(() -> {
            peak[0] = Math.max(peak[0], retort.getTemperature());
            received[0] |= !retort.input().isEmpty();
            if (received[0] && !done[0] && retort.input().isEmpty() && !retort.isActive())
            {
                done[0] = true;
                helper.assertTrue(helper.getBlockState(retortPos).is(Chemistry.GLASS_RETORT.get()), "The flask did not crack");
                helper.assertTrue(Math.abs(inResidue(retort, "tfcpp:sulfuric_acid") - 2 * 0.59) < 1e-3, "Two batches leave 1.18 L of oil of vitriol in the flask, got " + inResidue(retort, "tfcpp:sulfuric_acid"));
                helper.assertTrue(retort.receiver().isEmpty() && retort.product().isEmpty(), "And nothing came over");
                helper.assertTrue(peak[0] <= DistillationTable.CONCENTRATE_SULFURIC_ACID.maxTemperature(), "The flask stayed in the working range, peak " + peak[0]);
                helper.succeed();
            }
        });
        helper.runAtTickTime(4150, () -> helper.assertTrue(done[0], "The plant did not finish: flask " + Math.round(retort.getTemperature()) + " C (peak " + Math.round(peak[0]) + "), sand " + Math.round(bath.getTemperature()) + " C, fire " + Math.round(firepit.getTemperature()) + " C, "
            + retort.input().litres() + " L of chamber acid left, batch " + retort.isActive() + " at " + retort.progress() + " s, last reason " + retort.lastReason() + ", residue " + inResidue(retort, "tfcpp:sulfuric_acid")));
    }

    private AcidGameTests() {}
}
