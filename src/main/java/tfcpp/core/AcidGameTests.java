package tfcpp.core;

import java.util.UUID;
import java.util.List;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
import tfcpp.chemistry.Distillation;
import tfcpp.chemistry.DistillationRecipe;
import tfcpp.chemistry.DistillationTable;
import tfcpp.chemistry.GlassRetortBlockEntity;
import tfcpp.chemistry.SandBath;
import tfcpp.chemistry.SandBathBlock;
import tfcpp.chemistry.SandBathBlockEntity;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.chemistry.ChamberProcess;
import tfcpp.chemistry.Chemistry;
import tfcpp.chemistry.CorrosiveSpill;
import tfcpp.chemistry.LeadChamberBlockEntity;
import tfcpp.chemistry.Liquid;
import tfcpp.chemistry.SulfurBurnerBlockEntity;
import tfcpp.chemistry.SulfurBurning;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.pipes.PipeContents;
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

    /** Puts the gas, air and water of a network into it, as a test wants them. */
    private static void charge(PipeNetwork network, float air, float sulfurDioxide, float oxides, float water)
    {
        final PipeContents contents = network.contents();
        contents.air = air;
        contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] = sulfurDioxide;
        contents.gases[Gas.NITROGEN_OXIDES.ordinal()] = oxides;
        if (water > 0f)
        {
            contents.addLiquid(WATER, water, PipePhysics.ROOM_TEMPERATURE);
        }
    }

    /** @return litres of acid per second that the chamber process makes in this network, for the gas it holds now and the blocks of chamber in it */
    private static float litresPerSecond(PipeNetwork network, int chambers)
    {
        final PipeContents contents = network.contents();
        final float moles = contents.gasMoles();
        final float rate = ChamberProcess.rate(
            contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] / moles,
            contents.gases[Gas.NITROGEN_OXIDES.ordinal()] / moles,
            contents.air * ChamberProcess.OXYGEN_IN_AIR / moles);
        return chambers * rate * ChamberProcess.ACID_LITRES_PER_MOLE;
    }

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
        helper.runAtTickTime(5, () -> {
            final @Nullable PipeNetwork network = networkAt(helper, CHAMBER);
            helper.assertTrue(network != null, "The chamber should form a network");
            charge(network, 37.6f, 2.9f, 0.5f, 20f); // 7% sulfur dioxide of 41 mol, with oxides and water
            expected[0] = litresPerSecond(network, 1) * 10f;
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
        helper.runAtTickTime(5, () -> {
            final @Nullable PipeNetwork network = networkAt(helper, CHAMBER);
            helper.assertTrue(network != null && network == networkAt(helper, CHAMBER.east()), "Two chambers side by side make one network");
            charge(network, 75.2f, 5.8f, 1f, 40f); // The same shares as the chamber on its own, in twice the volume
            expected[0] = litresPerSecond(network, 2) * 10f;
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
        final SulfurBurnerBlockEntity burnerCopy = new SulfurBurnerBlockEntity(helper.absolutePos(burnerPos), helper.getBlockState(burnerPos));
        burnerCopy.loadWithComponents(burner.saveWithoutMetadata(registries), registries);
        helper.assertTrue(Math.abs(burnerCopy.sulfurMoles() - 2 * SulfurBurning.MOLES_PER_POWDER) < 1e-5f, "The sulfur is kept, got " + burnerCopy.sulfurMoles());
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
    public static void aRetortConcentratesChamberAcidIntoOilOfVitriol(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.CONCENTRATE_SULFURIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(Math.abs(inReceiver(retort, row.output()) - 0.59) < 1e-3, "0.59 L of sulfuric acid, got " + inReceiver(retort, row.output()));
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
            helper.assertTrue(retort.product().is(Chemistry.SUPERPHOSPHATE.get()) && retort.product().getCount() == 8, "Eight powders of superphosphate, got " + retort.product());
            helper.assertTrue(Math.abs(retort.input().litres() - (5.0 - 0.16)) < 1e-3, "0.16 L of chamber acid is taken, got " + retort.input().litres());
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
    public static void withoutACondenserOilOfVitriolGivesOnlySteam(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.CONCENTRATE_SULFURIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, false);
        charge(helper, retort, row, false);
        runRow(helper, retort, row, () -> {
            helper.assertTrue(retort.receiver().isEmpty(), "Nothing is caught");
            for (Gas gas : Gas.VALUES)
            {
                helper.assertTrue(totalInBox(helper, 5, 4, gas) < 1e-3f, "Only steam goes into the room, but there is " + gas.id());
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
    public static void aFullReceiverHoldsTheBatchBackAndTakesNothing(GameTestHelper helper)
    {
        final DistillationTable.Row row = DistillationTable.CONCENTRATE_SULFURIC_ACID;
        final GlassRetortBlockEntity retort = retortInBox(helper, true);
        charge(helper, retort, row, false);
        retort.receiver().fill(new FluidStack(fluidOf("tfcpp:sulfuric_acid"), 25), IFluidHandler.FluidAction.EXECUTE);
        helper.runAtTickTime(2, () -> {
            retort.warmTo(300f);
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(retort.lastReason() == Distillation.Reason.NO_ROOM && !retort.isActive(), "A full receiver stops the batch before it starts");
            helper.assertTrue(Math.abs(retort.input().litres() - 5.0) < 1e-6, "And the acid is not taken");

            retort.receiver().setFluid(new FluidStack(fluidOf("tfcpp:nitric_acid"), 3));
            retort.step(helper.getLevel(), 100f);
            helper.assertTrue(retort.lastReason() == Distillation.Reason.NO_ROOM, "Another liquid in the receiver stops it too");
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
        helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.NORTH) == null, "The sides have none");

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

            // Four batches of a litre each, at the heat of the table, give 2.36 L of oil of vitriol: two whole litres in the receiver
            concentrator.warmTo(315f);
            for (int batch = 0; batch < 4; batch++)
            {
                concentrator.step(helper.getLevel(), 100f);
            }
            helper.assertTrue(Math.abs(concentrator.input().litres() - 4.0) < 1e-3, "Four litres of chamber acid are used, got " + concentrator.input().litres());
            helper.assertTrue(concentrator.receiver().getFluidAmount() == 2 && concentrator.receiver().getFluid().getFluid() == fluidOf("tfcpp:sulfuric_acid"), "Two whole litres of oil of vitriol are in the receiver, got " + concentrator.receiver().getFluidAmount());
            helper.assertTrue(Math.abs(inReceiver(concentrator, "tfcpp:sulfuric_acid") - 4 * 0.59) < 1e-3, "And the rest of the 2.36 L waits as a fraction, got " + inReceiver(concentrator, "tfcpp:sulfuric_acid"));

            // The oil of vitriol into a second carboy, and into the second retort with fifty-six powders of saltpeter
            final ItemStack carboyB = new ItemStack(Chemistry.GLASS_CARBOY.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, carboyB);
            helper.assertTrue(concentrator.interact(carboyB, player, InteractionHand.MAIN_HAND), "The receiver is tapped into a carboy");
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

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = GLASS_BATCH)
    public static void superphosphateAndAmmoniumSulfateAreFertilisers(GameTestHelper helper)
    {
        final @Nullable Fertilizer phosphate = Fertilizer.get(new ItemStack(Chemistry.SUPERPHOSPHATE.get()));
        helper.assertTrue(phosphate != null && Math.abs(phosphate.phosphorus() - 0.2f) < 1e-6f && phosphate.nitrogen() == 0f && phosphate.potassium() == 0f, "Superphosphate is phosphorus 0.2, got " + phosphate);
        final @Nullable Fertilizer sulfate = Fertilizer.get(new ItemStack(Chemistry.AMMONIUM_SULFATE.get()));
        helper.assertTrue(sulfate != null && Math.abs(sulfate.nitrogen() - 0.1f) < 1e-6f && sulfate.phosphorus() == 0f && sulfate.potassium() == 0f, "Ammonium sulfate is nitrogen 0.1, got " + sulfate);
        final @Nullable Fertilizer apatite = Fertilizer.get(new ItemStack(Metallurgy.APATITE_POWDER.get()));
        helper.assertTrue(apatite != null && phosphate.phosphorus() > apatite.phosphorus(), "Made soluble, the phosphate feeds more than the raw rock");
        helper.succeed();
    }

    private AcidGameTests() {}
}
