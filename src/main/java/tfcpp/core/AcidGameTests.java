package tfcpp.core;

import java.util.UUID;
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

    private AcidGameTests() {}
}
