package tfcpp.core;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.rotation.CreativeRotationBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.wood.Wood;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.metallurgy.CupellationBath;
import tfcpp.metallurgy.CondensationTowerBlockEntity;
import tfcpp.metallurgy.CupelHearthBlockEntity;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.metallurgy.RetortBlockEntity;
import tfcpp.metallurgy.Sulfide;
import tfcpp.metallurgy.TowerCondensation;
import tfcpp.pneumatics.gas.Combustion;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.instruments.DustStore;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.Pipes;
import tfcpp.pneumatics.pipes.PistonPumpBlockEntity;

/** Stage D of v1.0: cupellation, which takes the silver out of work lead in a hearth over a fire, and the condensation tower that catches the fume of the roasting before it. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LeadGameTests
{
    private static final String TEMPLATE = "empty9";
    /** Apart from the others, as these tests set the calendar and fill the gas grid of a room. */
    private static final String BATCH = "tfcpp_lead";
    /** The rate IronGameTests keeps the calendar at: about 24 game ticks to a calendar second. */
    private static final float CALENDAR_RATE = 20f / 24f;
    /** Game ticks waited for: about ten calendar seconds at this rate. */
    private static final long WAIT = 240;
    private static final BlockPos HEARTH = new BlockPos(2, 1, 2);
    private static final BlockPos OTHER = new BlockPos(4, 1, 4);

    private static FakePlayer player(GameTestHelper helper)
    {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
    }

    /** Builds a hollow box with an air interior from (1,1,1) to (size,height,size) relative to the test. */
    private static void sealedBox(GameTestHelper helper, int size, int height, Block wall)
    {
        for (int x = 0; x <= size + 1; x++)
        {
            for (int y = 0; y <= height + 1; y++)
            {
                for (int z = 0; z <= size + 1; z++)
                {
                    final boolean inside = x > 0 && x <= size && y > 0 && y <= height && z > 0 && z <= size;
                    helper.setBlock(x, y, z, inside ? Blocks.AIR : wall);
                }
            }
        }
    }

    private static LevelGasVolume volume(GameTestHelper helper)
    {
        return GasTracker.get(helper.getLevel()).volume(helper.getLevel());
    }

    @Nullable
    private static float[] cell(GameTestHelper helper, int x, int y, int z)
    {
        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        return volume(helper).cell(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void addGas(GameTestHelper helper, int x, int y, int z, Gas gas, float moles)
    {
        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        final float[] existing = volume(helper).cell(pos.getX(), pos.getY(), pos.getZ());
        final float[] cell = existing != null ? existing : volume(helper).createCell(pos.getX(), pos.getY(), pos.getZ());
        helper.assertTrue(cell != null, "Could not create a gas cell");
        cell[gas.ordinal()] += moles;
    }

    private static float totalInBox(GameTestHelper helper, int size, int height, Gas gas)
    {
        float total = 0;
        for (int x = 1; x <= size; x++)
        {
            for (int y = 1; y <= height; y++)
            {
                for (int z = 1; z <= size; z++)
                {
                    final float[] cell = cell(helper, x, y, z);
                    if (cell != null)
                    {
                        total += cell[gas.ordinal()];
                    }
                }
            }
        }
        return total;
    }

    private static int droppedItems(GameTestHelper helper, BlockPos pos, Item item)
    {
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(3)))
        {
            if (entity.getItem().is(item))
            {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    private static ItemStack filledMold(Fluid fluid, int millibuckets)
    {
        final ItemStack mold = new ItemStack(TFCItems.MOLDS.get(Metal.ItemType.INGOT).asItem());
        final IFluidHandlerItem handler = mold.getCapability(Capabilities.FluidHandler.ITEM);
        handler.fill(new FluidStack(fluid, millibuckets), IFluidHandler.FluidAction.EXECUTE);
        return handler.getContainer();
    }

    private static int heldAmount(ItemStack stack)
    {
        return stack.getCapability(Capabilities.FluidHandler.ITEM).getFluidInTank(0).getAmount();
    }

    private static Fluid silver()
    {
        return TFCFluids.METALS.get(Metal.SILVER).getSource();
    }

    /** Places an empty hearth, which the test then fills. */
    private static CupelHearthBlockEntity hearthAt(GameTestHelper helper, BlockPos pos)
    {
        helper.setBlock(pos, Metallurgy.CUPEL_HEARTH.get());
        return helper.getBlockEntity(pos);
    }

    /** Loads the hearth at this temperature, as a saved hearth would be, so that it does not have to warm up first. */
    private static void setHeat(GameTestHelper helper, CupelHearthBlockEntity hearth, float celsius)
    {
        final var registries = helper.getLevel().registryAccess();
        final CompoundTag tag = hearth.saveWithoutMetadata(registries);
        tag.putFloat("temperature", celsius);
        tag.putFloat("targetTemperature", celsius);
        hearth.loadWithComponents(tag, registries);
    }

    /** A hearth over a fire at this heat. The fire is kept on with a report every tick, as a fire reports to the blocks above it. */
    private static CupelHearthBlockEntity hotHearth(GameTestHelper helper, BlockPos pos, float celsius)
    {
        final CupelHearthBlockEntity hearth = hearthAt(helper, pos);
        setHeat(helper, hearth, celsius);
        helper.onEachTick(() -> hearth.setTemperature(celsius));
        return hearth;
    }

    /** A hearth loaded again from what this one saves, as after a restart. */
    private static CupelHearthBlockEntity reloaded(GameTestHelper helper, BlockPos pos, CupelHearthBlockEntity hearth)
    {
        final var registries = helper.getLevel().registryAccess();
        final CupelHearthBlockEntity copy = new CupelHearthBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        copy.loadWithComponents(hearth.saveWithoutMetadata(registries), registries);
        return copy;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void leadOxidisesAboutAMillibucketASecondOverAHotHearth(GameTestHelper helper)
    {
        Calendars.SERVER.setCalendarTickRate(CALENDAR_RATE);
        Calendars.SERVER.setPlayersLoggedOn(true); // The TFC calendar stands still on an empty server, and so does this blast
        sealedBox(helper, 5, 3, Blocks.STONE);
        final CupelHearthBlockEntity hearth = hotHearth(helper, HEARTH, 1100f);
        hearth.bath().add(100);
        hearth.bath().addAsh();
        final long start = Calendars.SERVER.getTicks();

        helper.runAfterDelay(WAIT, () -> {
            final float seconds = (Calendars.SERVER.getTicks() - start) / 20f;
            final float oxidised = 100f - hearth.bath().lead;
            helper.assertTrue(seconds >= 5f, "The calendar should have run on for five seconds, got " + seconds);
            helper.assertTrue(Math.abs(oxidised - seconds) <= 1.5f, "About 1 mB a second, so about " + seconds + " mB, got " + oxidised);
            helper.assertTrue(droppedItems(helper, HEARTH, Instruments.LEAD_OXIDE.get()) > 0, "Litharge should be raked off as items");
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.LEAD_OXIDE_FUME) > 0f, "The lead fume must reach the air");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void aColdHearthDoesNothing(GameTestHelper helper)
    {
        Calendars.SERVER.setCalendarTickRate(CALENDAR_RATE);
        Calendars.SERVER.setPlayersLoggedOn(true);
        sealedBox(helper, 5, 3, Blocks.STONE);
        final CupelHearthBlockEntity hearth = hotHearth(helper, HEARTH, 500f);
        hearth.bath().add(100);
        hearth.bath().addAsh();
        final long start = Calendars.SERVER.getTicks();

        helper.runAfterDelay(WAIT, () -> {
            // The calendar must have run on, or the test would pass for nothing
            helper.assertTrue(Calendars.SERVER.getTicks() - start >= 100L, "The calendar should have run on");
            helper.assertTrue(hearth.bath().lead == 100f, "Below the melting of litharge nothing oxidises, got " + hearth.bath().lead);
            helper.assertTrue(droppedItems(helper, HEARTH, Instruments.LEAD_OXIDE.get()) == 0, "No litharge when cold");
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.LEAD_OXIDE_FUME) == 0f, "No fume when cold");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void aHearthWithNoAshDoesNothing(GameTestHelper helper)
    {
        Calendars.SERVER.setCalendarTickRate(CALENDAR_RATE);
        Calendars.SERVER.setPlayersLoggedOn(true);
        sealedBox(helper, 5, 3, Blocks.STONE);
        final CupelHearthBlockEntity hearth = hotHearth(helper, HEARTH, 1100f);
        hearth.bath().add(100);
        final long start = Calendars.SERVER.getTicks();
        helper.assertTrue(hearth.bath().needsAsh(), "Lead with no ash needs ash");

        helper.runAfterDelay(WAIT, () -> {
            helper.assertTrue(Calendars.SERVER.getTicks() - start >= 100L, "The calendar should have run on");
            helper.assertTrue(hearth.bath().lead == 100f, "With the floor full of litharge the blast stops, got " + hearth.bath().lead);
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.LEAD_OXIDE_FUME) == 0f, "No blast, no fume");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void plainLeadIsRefusedByHandAndByCapability(GameTestHelper helper)
    {
        final CupelHearthBlockEntity hearth = hearthAt(helper, HEARTH);
        final FakePlayer player = player(helper);
        final ItemStack mold = filledMold(Metallurgy.LEAD.get(), 100);
        hearth.interact(mold, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(hearth.bath().lead == 0f, "Plain lead must not go in by hand, got " + hearth.bath().lead);
        helper.assertTrue(heldAmount(mold) == 100, "The mould must keep its lead, got " + heldAmount(mold));
        helper.assertTrue(hearth.tank().fill(new FluidStack(Metallurgy.LEAD.get(), 100), IFluidHandler.FluidAction.EXECUTE) == 0, "Nor by its capability");
        helper.assertTrue(hearth.bath().lead == 0f, "The capability must not have taken it either");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aFullRunLeavesItsSilverAndTheMouldGetsWholeMillibuckets(GameTestHelper helper)
    {
        final CupelHearthBlockEntity hearth = hotHearth(helper, HEARTH, 1100f);
        final FakePlayer player = player(helper);
        final ServerLevel level = helper.getLevel();

        // TFC denies draining a mould, as it is not molten, so a held mould of work lead cannot be poured in by hand. It keeps its lead.
        final ItemStack heldMould = filledMold(Metallurgy.WORK_LEAD.get(), 100);
        hearth.interact(heldMould, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(hearth.bath().lead == 0f, "A mould is not drained, so nothing is poured by hand, got " + hearth.bath().lead);
        helper.assertTrue(heldAmount(heldMould) == 100, "The mould keeps its lead, got " + heldAmount(heldMould));

        // Work lead through the capability, to the full 1000 mB
        helper.assertTrue(hearth.tank().fill(new FluidStack(Metallurgy.WORK_LEAD.get(), 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "A full hearth of work lead");
        helper.assertTrue(hearth.tank().fill(new FluidStack(Metallurgy.WORK_LEAD.get(), 1), IFluidHandler.FluidAction.EXECUTE) == 0, "A full hearth takes no more");

        // Ash for all of it, two powders of 500 mB each
        final ItemStack ash = new ItemStack(Metallurgy.BONE_ASH.get(), 2);
        hearth.interact(ash, player, InteractionHand.MAIN_HAND);
        hearth.interact(ash, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ash.isEmpty(), "Both powders of ash are used");

        // The whole charge oxidises in one blast, as if the fire had run for a thousand seconds
        hearth.step(level, 1000f);
        final float silver = hearth.bath().silver;
        helper.assertTrue(hearth.bath().lead == 0f, "All the lead oxidises, got " + hearth.bath().lead);
        helper.assertTrue(Math.abs(silver - 1000f * CupellationBath.SILVER_PER_LEAD) < 0.001f, "Silver is 0.00324 mB per mB of lead, got " + silver);
        helper.assertTrue(hearth.blicked(), "The last litharge has cleared, so the blick is seen");

        // A mould takes whole millibuckets of silver, and the fraction stays in the hearth
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TFCItems.MOLDS.get(Metal.ItemType.INGOT).asItem()));
        hearth.interact(player.getItemInHand(InteractionHand.MAIN_HAND), player, InteractionHand.MAIN_HAND);
        final ItemStack mould = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(heldAmount(mould) == 3, "Three whole millibuckets go into the mould, got " + heldAmount(mould));
        helper.assertTrue(mould.getCapability(Capabilities.FluidHandler.ITEM).getFluidInTank(0).getFluid() == silver(), "And it is silver");
        helper.assertTrue(Math.abs(hearth.bath().silver - (silver - 3f)) < 0.001f, "The fraction stays behind, got " + hearth.bath().silver);

        // With the lead gone, a step does nothing, and the blick is not seen again
        final float left = hearth.bath().silver;
        hearth.step(level, 10f);
        helper.assertTrue(hearth.bath().silver == left, "A step with no lead changes nothing");
        helper.assertTrue(hearth.blicked(), "The blick stays seen");

        // New lead makes a new charge, and so a new blick when it is cupelled
        hearth.tank().fill(new FluidStack(Metallurgy.WORK_LEAD.get(), 5), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(!hearth.blicked(), "New lead clears the blick");
        hearth.bath().addAsh();
        hearth.step(level, 10f);
        helper.assertTrue(hearth.blicked(), "The new charge is cupelled and seen again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void theTankTakesWorkLeadInAndSilverOutOnly(GameTestHelper helper)
    {
        final CupelHearthBlockEntity hearth = hearthAt(helper, HEARTH);
        setHeat(helper, hearth, 1100f);
        final IFluidHandler tank = hearth.tank();
        helper.assertTrue(tank.fill(new FluidStack(Metallurgy.LEAD.get(), 50), IFluidHandler.FluidAction.EXECUTE) == 0, "Plain lead is refused");
        helper.assertTrue(tank.fill(new FluidStack(Metallurgy.WORK_LEAD.get(), 400), IFluidHandler.FluidAction.EXECUTE) == 400, "Work lead is taken");
        helper.assertTrue(tank.drain(new FluidStack(silver(), 1), IFluidHandler.FluidAction.EXECUTE).isEmpty(), "No silver comes out while lead is still in the hearth");

        hearth.bath().addAsh();
        hearth.bath().addAsh();
        hearth.step(helper.getLevel(), 400f);
        helper.assertTrue(Math.abs(hearth.bath().silver - 400f * CupellationBath.SILVER_PER_LEAD) < 0.001f, "Silver of the lead, got " + hearth.bath().silver);
        final FluidStack taken = tank.drain(new FluidStack(silver(), 5), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(taken.getFluid() == silver() && taken.getAmount() == 1, "Only whole millibuckets come out, got " + taken.getAmount());
        helper.assertTrue(tank.drain(new FluidStack(Metallurgy.WORK_LEAD.get(), 1), IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Work lead is never drained");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aHearthSurvivesSaveAndLoadWithItsBlick(GameTestHelper helper)
    {
        final CupelHearthBlockEntity hearth = hotHearth(helper, HEARTH, 1100f);
        hearth.bath().add(40);
        hearth.bath().addAsh();
        hearth.step(helper.getLevel(), 3f);
        final CupelHearthBlockEntity copy = reloaded(helper, HEARTH, hearth);
        final CupellationBath bath = hearth.bath(), back = copy.bath();
        helper.assertTrue(back.lead == bath.lead && back.silver == bath.silver && back.ashCapacity == bath.ashCapacity && back.oxidisedForLitharge == bath.oxidisedForLitharge, "The bath is kept exactly");
        helper.assertTrue(copy.getTemperature() == hearth.getTemperature(), "The temperature is kept, got " + copy.getTemperature());
        helper.assertTrue(!copy.blicked(), "No blick yet");

        // A finished hearth keeps its blick, so that it is not seen again after a restart
        final CupelHearthBlockEntity done = hotHearth(helper, OTHER, 1100f);
        done.bath().add(10);
        done.bath().addAsh();
        done.step(helper.getLevel(), 10f);
        helper.assertTrue(done.blicked(), "The small charge is cupelled");
        helper.assertTrue(reloaded(helper, OTHER, done).blicked(), "The blick is kept over a reload");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void breakingTheHearthSetsWorkLeadIntoIngotsAndBurnsTheRest(GameTestHelper helper)
    {
        sealedBox(helper, 5, 3, Blocks.STONE);
        hearthAt(helper, HEARTH).bath().add(250);
        helper.getLevel().destroyBlock(helper.absolutePos(HEARTH), false);

        helper.runAfterDelay(1, () -> {
            final int ingots = droppedItems(helper, HEARTH, Metallurgy.WORK_LEAD_INGOT.get());
            helper.assertTrue(ingots == 2, "250 mB of work lead is two ingots, got " + ingots);
            helper.assertTrue(droppedItems(helper, HEARTH, Metallurgy.LEAD_INGOT.get()) == 0, "Work lead must not come out as plain lead");
            final float expected = 50f * CupellationBath.MOLES_LEAD_PER_MILLIBUCKET;
            final float fume = totalInBox(helper, 5, 3, Gas.LEAD_OXIDE_FUME);
            helper.assertTrue(Math.abs(fume - expected) < 0.05f, "The 50 mB short of an ingot burns as " + expected + " mol of litharge smoke, got " + fume);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void breakingTheHearthSetsSilverIntoIngotsAndLosesTheSpatter(GameTestHelper helper)
    {
        sealedBox(helper, 5, 3, Blocks.STONE);
        hearthAt(helper, HEARTH).bath().silver = 150f;
        helper.getLevel().destroyBlock(helper.absolutePos(HEARTH), false);

        helper.runAfterDelay(1, () -> {
            // 150 mB is one whole ingot, and the 50 mB short of a second stays on the ruin, lost
            final int ingots = droppedItems(helper, HEARTH, TFCItems.METAL_ITEMS.get(Metal.SILVER).get(Metal.ItemType.INGOT).get());
            helper.assertTrue(ingots == 1, "150 mB of silver is one ingot, got " + ingots);
            helper.assertTrue(droppedItems(helper, HEARTH, Metallurgy.WORK_LEAD_INGOT.get()) == 0, "No lead in it");
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.LEAD_OXIDE_FUME) == 0f, "Silver makes no fume");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void theHearthAndItsSheetRecipeLoad(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.byKey(TFCpp.id("crafting/cupel_hearth")).isPresent(), "The hearth must be craftable");
        helper.assertTrue(recipes.byKey(TFCpp.id("heating/lead_sheet")).isPresent(), "A lead sheet must melt back to lead");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void aHearthInAirWithNoOxygenDoesNothing(GameTestHelper helper)
    {
        Calendars.SERVER.setCalendarTickRate(CALENDAR_RATE);
        Calendars.SERVER.setPlayersLoggedOn(true);
        sealedBox(helper, 5, 3, Blocks.STONE);
        // Every cell of the room is full of flue nitrogen, so the air has no oxygen left in it anywhere
        for (int x = 1; x <= 5; x++)
        {
            for (int y = 1; y <= 3; y++)
            {
                for (int z = 1; z <= 5; z++)
                {
                    if (x != HEARTH.getX() || y != HEARTH.getY() || z != HEARTH.getZ())
                    {
                        addGas(helper, x, y, z, Gas.FLUE_NITROGEN, 2f * GasMath.CELL_MOLES);
                    }
                }
            }
        }
        final CupelHearthBlockEntity hearth = hotHearth(helper, HEARTH, 1100f);
        hearth.bath().add(100);
        hearth.bath().addAsh();
        final float[] above = cell(helper, 2, 2, 2);
        helper.assertTrue(above != null && GasMath.oxygenFraction(above) < Combustion.EXTINCTION_OXYGEN, "The air over the hearth must be short of oxygen");
        final long start = Calendars.SERVER.getTicks();

        helper.runAfterDelay(WAIT, () -> {
            helper.assertTrue(Calendars.SERVER.getTicks() - start >= 100L, "The calendar should have run on");
            helper.assertTrue(hearth.bath().lead == 100f, "Without oxygen the lead does not oxidise, got " + hearth.bath().lead);
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.LEAD_OXIDE_FUME) == 0f, "No blast, no fume");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void theBlastCountsCalendarTicksOnly(GameTestHelper helper)
    {
        // Within one call of the game nothing else runs, so the calendar stands still for all of this
        final CupelHearthBlockEntity hearth = hearthAt(helper, HEARTH);
        setHeat(helper, hearth, 1100f);
        hearth.bath().add(100);
        hearth.bath().addAsh();
        final ServerLevel level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(HEARTH);
        final BlockState state = level.getBlockState(pos);

        for (int i = 0; i < 50; i++)
        {
            CupelHearthBlockEntity.serverTick(level, pos, state, hearth);
        }
        helper.assertTrue(hearth.bath().lead == 100f, "A calendar that stands still gives no step, got " + hearth.bath().lead);

        // Now the calendar is given by hand: a step at each 20 ticks, nothing for a repeat, a set back calendar starts again, and a long gap is cut to five seconds
        final long now = Calendars.SERVER.getTicks();
        hearth.tickClock(level, now + 19);
        helper.assertTrue(hearth.bath().lead == 100f, "Nineteen ticks are not a step");
        hearth.tickClock(level, now + 20);
        helper.assertTrue(hearth.bath().lead == 99f, "Twenty ticks are one second, got " + hearth.bath().lead);
        hearth.tickClock(level, now + 20);
        helper.assertTrue(hearth.bath().lead == 99f, "The same tick again is no step");
        hearth.tickClock(level, now - 5);
        helper.assertTrue(hearth.bath().lead == 99f, "A calendar set back is no step");
        hearth.tickClock(level, now - 5 + 1000);
        helper.assertTrue(hearth.bath().lead == 94f, "A long gap is cut to five seconds, got " + hearth.bath().lead);
        helper.succeed();
    }

    // ---------------------------------------------------------------- the condensation tower

    private static final BlockPos TOWER = new BlockPos(2, 1, 2);
    private static final float START = 4f;
    /** What a roasting hearth puts in a small room in a few minutes: 20 mol of sulfur dioxide is 1.3 kg. */
    private static final float ROASTED = 20f;

    private static Block ceramicPipe()
    {
        return Pipes.PIPES.get(PipeMaterial.CERAMIC).get();
    }

    @Nullable
    private static PipeNetwork networkAt(GameTestHelper helper, BlockPos pos)
    {
        return PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(pos));
    }

    /** A tower with a length of ceramic pipe east of it, which is a network of two blocks. */
    private static void towerWithPipe(GameTestHelper helper, BlockPos tower)
    {
        helper.setBlock(tower, Metallurgy.CONDENSATION_TOWER.get());
        helper.setBlock(tower.east(), ceramicPipe());
    }

    private static CondensationTowerBlockEntity towerAt(GameTestHelper helper, BlockPos pos)
    {
        return helper.getBlockEntity(pos);
    }

    /** Keeps the gas of the network at this temperature, as a furnace or a cold wall would, as the network on its own cools towards the weather. */
    private static void holdAt(GameTestHelper helper, BlockPos pos, float kelvin)
    {
        helper.onEachTick(() -> {
            final @Nullable PipeNetwork network = networkAt(helper, pos);
            if (network != null)
            {
                network.contents().temperature = kelvin;
            }
        });
    }

    /** @return the whole number of one-second steps that took a gas from {@code start} to {@code left} at the rate of one tower */
    private static float stepsFor(float left, float start)
    {
        return (float) (-Math.log(left / start) / TowerCondensation.CAPTURE_RATE);
    }

    private static float gasIn(PipeNetwork network, Gas gas)
    {
        return network.contents().gases[gas.ordinal()];
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void aTowerCatchesArsenicAtItsRateAndLetsSulfurDioxidePass(GameTestHelper helper)
    {
        towerWithPipe(helper, TOWER);
        helper.runAtTickTime(5, () -> {
            final @Nullable PipeNetwork network = networkAt(helper, TOWER);
            helper.assertTrue(network != null && network == networkAt(helper, TOWER.east()), "The tower and its pipe should form one network");
            network.contents().gases[Gas.ARSENIC_TRIOXIDE.ordinal()] = START;
            network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] = 3f;
        });

        helper.runAtTickTime(200, () -> {
            final PipeNetwork network = networkAt(helper, TOWER);
            final CondensationTowerBlockEntity tower = towerAt(helper, TOWER);
            final float left = gasIn(network, Gas.ARSENIC_TRIOXIDE), caught = tower.dust().collected(Gas.ARSENIC_TRIOXIDE);
            helper.assertTrue(Math.abs(left + caught - START) < 1e-3f, "What the tower caught and what is left must be what there was, got " + caught + " and " + left);

            // Every second of the network is one step of the rate, so the gas that is left says how many steps it has seen
            final float steps = stepsFor(left, START);
            helper.assertTrue(steps >= 5f && steps <= 11f, "About nine seconds of steps in 195 ticks, got " + steps);
            helper.assertTrue(Math.abs(steps - Math.round(steps)) < 0.02f, "A whole number of steps at 1 - exp(-0.05) a second, got " + steps);

            helper.assertTrue(Math.abs(gasIn(network, Gas.SULFUR_DIOXIDE) - 3f) < 1e-4f, "Sulfur dioxide goes through untouched, got " + gasIn(network, Gas.SULFUR_DIOXIDE));
            helper.assertTrue(tower.dust().collected(Gas.SULFUR_DIOXIDE) == 0f, "And nothing of it is kept");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void twoTowersCatchTwiceAsFast(GameTestHelper helper)
    {
        // Two towers in one network, and one tower alone beside them, all given the same gas
        helper.setBlock(1, 1, 2, Metallurgy.CONDENSATION_TOWER.get());
        helper.setBlock(2, 1, 2, ceramicPipe());
        helper.setBlock(3, 1, 2, Metallurgy.CONDENSATION_TOWER.get());
        towerWithPipe(helper, new BlockPos(1, 1, 6));
        helper.runAtTickTime(5, () -> {
            final PipeNetwork pair = networkAt(helper, new BlockPos(1, 1, 2)), single = networkAt(helper, new BlockPos(1, 1, 6));
            helper.assertTrue(pair != null && pair == networkAt(helper, new BlockPos(3, 1, 2)) && single != null && pair != single, "A network of two towers, and another of one");
            pair.contents().gases[Gas.ZINC_OXIDE_FUME.ordinal()] = START;
            single.contents().gases[Gas.ZINC_OXIDE_FUME.ordinal()] = START;
        });
        helper.runAtTickTime(200, () -> {
            final float pair = gasIn(networkAt(helper, new BlockPos(1, 1, 2)), Gas.ZINC_OXIDE_FUME), single = gasIn(networkAt(helper, new BlockPos(1, 1, 6)), Gas.ZINC_OXIDE_FUME);
            final float caught = towerAt(helper, new BlockPos(1, 1, 2)).dust().collected(Gas.ZINC_OXIDE_FUME) + towerAt(helper, new BlockPos(3, 1, 2)).dust().collected(Gas.ZINC_OXIDE_FUME);
            helper.assertTrue(Math.abs(pair + caught - START) < 1e-3f, "Nothing lost between the two towers, got " + caught + " and " + pair);
            // The two towers act on the gas one after another in each step, so the rate is twice that of the one
            final float stepsPair = stepsFor(pair, START) / 2f, stepsSingle = stepsFor(single, START);
            helper.assertTrue(stepsSingle >= 5f && Math.abs(stepsPair - stepsSingle) < 1.1f, "The same steps with twice the rate, got " + stepsPair + " and " + stepsSingle);
            helper.assertTrue(pair < single, "Two towers leave less in the gas than one, got " + pair + " and " + single);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void aTowerCatchesNothingFromGasAboveTwoHundredFiftyDegrees(GameTestHelper helper)
    {
        final BlockPos hot = TOWER, cool = new BlockPos(2, 1, 6);
        towerWithPipe(helper, hot);
        towerWithPipe(helper, cool);
        holdAt(helper, hot, 620f);
        holdAt(helper, cool, 450f); // 177 C is cool enough for the oxides, and shows that the network was stepped at all
        helper.runAtTickTime(5, () -> {
            for (BlockPos pos : new BlockPos[] {hot, cool})
            {
                final PipeNetwork network = networkAt(helper, pos);
                for (Gas gas : DustStore.GASES)
                {
                    network.contents().gases[gas.ordinal()] = 2f;
                }
                network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] = 2f;
            }
        });
        helper.runAtTickTime(200, () -> {
            final PipeNetwork network = networkAt(helper, hot);
            final CondensationTowerBlockEntity tower = towerAt(helper, hot);
            for (Gas gas : DustStore.GASES)
            {
                helper.assertTrue(tower.dust().collected(gas) == 0f, "Nothing of " + gas + " settles from gas at 347 C, got " + tower.dust().collected(gas));
                helper.assertTrue(gasIn(network, gas) == 2f, gas + " stays in the gas, got " + gasIn(network, gas));
            }

            final CondensationTowerBlockEntity coolTower = towerAt(helper, cool);
            for (Gas gas : DustStore.GASES)
            {
                helper.assertTrue(coolTower.dust().collected(gas) > 0.3f, "The cooler network was stepped, and its " + gas + " settled, got " + coolTower.dust().collected(gas));
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void mercuryCondensesBelowOneHundredDegreesAndNotAbove(GameTestHelper helper)
    {
        final BlockPos cold = TOWER, warm = new BlockPos(2, 1, 5), flooded = new BlockPos(6, 1, 2);
        for (BlockPos pos : new BlockPos[] {cold, warm, flooded})
        {
            towerWithPipe(helper, pos);
        }
        holdAt(helper, cold, 300f);
        holdAt(helper, warm, 450f); // Cool enough for the oxides, too warm for the mercury
        holdAt(helper, flooded, 300f);
        helper.runAtTickTime(5, () -> {
            gasOf(helper, cold)[Gas.MERCURY_VAPOR.ordinal()] = 1f;
            gasOf(helper, warm)[Gas.MERCURY_VAPOR.ordinal()] = 1f;
            gasOf(helper, warm)[Gas.ARSENIC_TRIOXIDE.ordinal()] = 1f;
            gasOf(helper, flooded)[Gas.MERCURY_VAPOR.ordinal()] = 60f;
        });
        helper.runAtTickTime(200, () -> {
            final float perMillibucket = Sulfide.CINNABAR.metalMoles(1f);
            final CondensationTowerBlockEntity coldTower = towerAt(helper, cold);
            final int liquid = coldTower.tank().getFluidAmount();
            helper.assertTrue(liquid > 0 && coldTower.tank().getFluid().getFluid() == Metallurgy.MERCURY.get(), "Below 100 C the mercury runs down as a liquid, got " + liquid + " mB");
            final float coldTotal = gasOf(helper, cold)[Gas.MERCURY_VAPOR.ordinal()] + (liquid + coldTower.pendingMillibuckets()) * perMillibucket;
            helper.assertTrue(Math.abs(coldTotal - 1f) < 1e-3f, "The mercury must all still be somewhere, got " + coldTotal + " mol");

            final CondensationTowerBlockEntity warmTower = towerAt(helper, warm);
            helper.assertTrue(warmTower.tank().isEmpty() && warmTower.pendingMillibuckets() == 0f, "Above 100 C none condenses");
            helper.assertTrue(Math.abs(gasOf(helper, warm)[Gas.MERCURY_VAPOR.ordinal()] - 1f) < 1e-5f, "The vapour goes on with the gas");
            helper.assertTrue(warmTower.dust().collected(Gas.ARSENIC_TRIOXIDE) > 0.1f, "Though the oxide settles at that heat, which also shows the network was stepped");

            // A tank holds 100 mB, and mercury that does not fit stays in the gas
            final CondensationTowerBlockEntity floodedTower = towerAt(helper, flooded);
            helper.assertTrue(floodedTower.tank().getFluidAmount() == TowerCondensation.MERCURY_TANK_MILLIBUCKETS, "A full tank is 100 mB, got " + floodedTower.tank().getFluidAmount());
            final float expectedLeft = 60f - TowerCondensation.MERCURY_TANK_MILLIBUCKETS * perMillibucket;
            helper.assertTrue(Math.abs(gasOf(helper, flooded)[Gas.MERCURY_VAPOR.ordinal()] - expectedLeft) < 0.02f, "The rest stays in the gas: " + expectedLeft + " mol, got " + gasOf(helper, flooded)[Gas.MERCURY_VAPOR.ordinal()]);
            helper.succeed();
        });
    }

    private static float[] gasOf(GameTestHelper helper, BlockPos pos)
    {
        return networkAt(helper, pos).contents().gases;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void scrapingATowerGivesPowderAndStirsSomeIntoTheAirAtThePlayer(GameTestHelper helper)
    {
        sealedBox(helper, 5, 3, Blocks.STONE);
        helper.setBlock(TOWER, Metallurgy.CONDENSATION_TOWER.get());
        final CondensationTowerBlockEntity tower = towerAt(helper, TOWER);
        tower.dust().add(Gas.ARSENIC_TRIOXIDE, 2f);
        tower.dust().add(Gas.ZINC_OXIDE_FUME, 0.1f); // Less than one powder, so it stays

        final FakePlayer player = player(helper);
        final BlockPos spot = helper.absolutePos(new BlockPos(4, 1, 4));
        player.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);

        // An empty hand without crouching only reads the tower
        useEmptyHand(helper, TOWER, player);
        helper.assertTrue(tower.dust().collected(Gas.ARSENIC_TRIOXIDE) == 2f, "Reading the tower takes nothing from it");
        helper.assertTrue(player.getInventory().countItem(Instruments.ARSENIC_TRIOXIDE.get()) == 0, "And gives no powder");

        player.setShiftKeyDown(true);
        useEmptyHand(helper, TOWER, player);
        final int powders = (int) (2f / (DustStore.MOLES_PER_POWDER * (1 + DustStore.STIRRED_UP)));
        helper.assertTrue(powders == 7, "Seven whole powders of the two moles");
        helper.assertTrue(player.getInventory().countItem(Instruments.ARSENIC_TRIOXIDE.get()) == powders, "Crouching scrapes it, and gives whole powders, got " + player.getInventory().countItem(Instruments.ARSENIC_TRIOXIDE.get()));
        helper.assertTrue(player.getInventory().countItem(Instruments.ZINC_OXIDE.get()) == 0, "A tenth of a mole is not a powder");
        final float taken = powders * DustStore.MOLES_PER_POWDER;
        helper.assertTrue(Math.abs(tower.dust().collected(Gas.ARSENIC_TRIOXIDE) - (2f - taken * (1 + DustStore.STIRRED_UP))) < 1e-4f, "The tower pays for what was stirred up as well, left " + tower.dust().collected(Gas.ARSENIC_TRIOXIDE));
        helper.assertTrue(tower.dust().collected(Gas.ZINC_OXIDE_FUME) == 0.1f, "The little zinc oxide stays");

        final float stirred = totalInBox(helper, 5, 3, Gas.ARSENIC_TRIOXIDE);
        helper.assertTrue(Math.abs(stirred - taken * DustStore.STIRRED_UP) < 1e-3f, "A twentieth of the powder is in the air, got " + stirred);
        final float[] atPlayer = cell(helper, 4, 2, 4);
        helper.assertTrue(atPlayer != null && atPlayer[Gas.ARSENIC_TRIOXIDE.ordinal()] > 0.99f * stirred, "And it is round the player's head, got " + (atPlayer == null ? 0f : atPlayer[Gas.ARSENIC_TRIOXIDE.ordinal()]));

        // Too little left is said, and nothing happens
        tower.scrape(player);
        helper.assertTrue(player.getInventory().countItem(Instruments.ARSENIC_TRIOXIDE.get()) == powders, "No second powder from what is left");
        helper.succeed();
    }

    private static void useEmptyHand(GameTestHelper helper, BlockPos pos, FakePlayer player)
    {
        final BlockPos absolute = helper.absolutePos(pos);
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        helper.getLevel().getBlockState(absolute).useWithoutItem(helper.getLevel(), player, hit);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aTowerTapsItsMercuryIntoABulbAndAJug(GameTestHelper helper)
    {
        helper.setBlock(TOWER, Metallurgy.CONDENSATION_TOWER.get());
        final CondensationTowerBlockEntity tower = towerAt(helper, TOWER);
        tower.tank().fill(new FluidStack(Metallurgy.MERCURY.get(), 30), IFluidHandler.FluidAction.EXECUTE);
        final FakePlayer player = player(helper);

        final ItemStack bulbs = new ItemStack(Metallurgy.GLASS_BULB.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, bulbs);
        helper.assertTrue(tower.canUse(bulbs) && tower.interact(bulbs, player, InteractionHand.MAIN_HAND), "A glass bulb is filled");
        helper.assertTrue(tower.tank().getFluidAmount() == 30 - RetortBlockEntity.MERCURY_BULB_MILLIBUCKETS, "A bulb takes 5 mB, got " + tower.tank().getFluidAmount());
        helper.assertTrue(bulbs.getCount() == 1 && player.getInventory().countItem(Metallurgy.MERCURY_BULB.get()) == 1, "One bulb became a bulb of mercury");

        final ItemStack jug = new ItemStack(TFCItems.JUG.asItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, jug);
        final int before = tower.tank().getFluidAmount();
        helper.assertTrue(tower.canUse(jug) && tower.interact(jug, player, InteractionHand.MAIN_HAND), "A jug is filled");
        final ItemStack filled = player.getItemInHand(InteractionHand.MAIN_HAND);
        final int inJug = heldAmount(filled);
        helper.assertTrue(inJug > 0 && filled.getCapability(Capabilities.FluidHandler.ITEM).getFluidInTank(0).getFluid() == Metallurgy.MERCURY.get(), "Mercury went into the jug, got " + inJug);
        helper.assertTrue(tower.tank().getFluidAmount() == before - inJug, "And came out of the tank by as much");
        tower.tank().fill(new FluidStack(Metallurgy.MERCURY.get(), 10), IFluidHandler.FluidAction.EXECUTE);

        // Seen from outside the tank can be drained, and nothing can be poured into it
        helper.assertTrue(tower.outlet().fill(new FluidStack(Metallurgy.MERCURY.get(), 10), IFluidHandler.FluidAction.EXECUTE) == 0, "Nothing is poured in from outside");
        helper.assertTrue(tower.outlet().drain(1, IFluidHandler.FluidAction.EXECUTE).getAmount() == 1, "But mercury runs out");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aTowerSurvivesSaveAndLoad(GameTestHelper helper)
    {
        helper.setBlock(TOWER, Metallurgy.CONDENSATION_TOWER.get());
        final CondensationTowerBlockEntity tower = towerAt(helper, TOWER);
        tower.dust().add(Gas.ARSENIC_TRIOXIDE, 1.5f);
        tower.dust().add(Gas.ANTIMONY_TRIOXIDE, 0.75f);
        tower.dust().add(Gas.ZINC_OXIDE_FUME, 0.3f);
        tower.dust().add(Gas.LEAD_OXIDE_FUME, 0.1f);
        tower.tank().fill(new FluidStack(Metallurgy.MERCURY.get(), 37), IFluidHandler.FluidAction.EXECUTE);

        // A fraction of a millibucket that is waiting is a state of its own, which only a save can put in
        final var registries = helper.getLevel().registryAccess();
        final CompoundTag tag = tower.saveWithoutMetadata(registries);
        tag.putDouble("mercuryCarry", 0.4);
        tower.loadWithComponents(tag, registries);
        helper.assertTrue(Math.abs(tower.pendingMillibuckets() - 0.4f) < 1e-6f, "The carried fraction is loaded, got " + tower.pendingMillibuckets());

        final CondensationTowerBlockEntity copy = new CondensationTowerBlockEntity(helper.absolutePos(TOWER), helper.getBlockState(TOWER));
        copy.loadWithComponents(tower.saveWithoutMetadata(registries), registries);
        for (Gas gas : DustStore.GASES)
        {
            helper.assertTrue(copy.dust().collected(gas) == tower.dust().collected(gas) && copy.dust().collected(gas) > 0f, gas + " is kept exactly, got " + copy.dust().collected(gas));
        }
        helper.assertTrue(copy.tank().getFluidAmount() == 37 && copy.tank().getFluid().getFluid() == Metallurgy.MERCURY.get(), "The mercury is kept, got " + copy.tank().getFluidAmount());
        helper.assertTrue(Math.abs(copy.pendingMillibuckets() - 0.4f) < 1e-6f, "And the part of a millibucket, got " + copy.pendingMillibuckets());

        // A blank tag loads as an empty tower, whatever it held before
        copy.loadWithComponents(new CompoundTag(), registries);
        helper.assertTrue(copy.tank().isEmpty() && copy.pendingMillibuckets() == 0f && copy.dust().collected(Gas.ARSENIC_TRIOXIDE) == 0f, "Loading a blank tag clears it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void breakingATowerDropsPowderAndLetsMercuryEvaporate(GameTestHelper helper)
    {
        sealedBox(helper, 5, 3, Blocks.STONE);
        helper.setBlock(TOWER, Metallurgy.CONDENSATION_TOWER.get());
        final CondensationTowerBlockEntity tower = towerAt(helper, TOWER);
        tower.dust().add(Gas.ARSENIC_TRIOXIDE, 0.6f); // Two powders and a tenth of a mole
        tower.tank().fill(new FluidStack(Metallurgy.MERCURY.get(), 50), IFluidHandler.FluidAction.EXECUTE);
        helper.getLevel().destroyBlock(helper.absolutePos(TOWER), false);

        helper.runAfterDelay(1, () -> {
            helper.assertTrue(droppedItems(helper, TOWER, Instruments.ARSENIC_TRIOXIDE.get()) == 2, "Two whole powders drop, got " + droppedItems(helper, TOWER, Instruments.ARSENIC_TRIOXIDE.get()));
            // Dust settles out of the air at once, so what has blown away is the air and the settled part of it together
            final float blown = totalInBox(helper, 5, 3, Gas.ARSENIC_TRIOXIDE) + helper.getLevel().getChunkAt(helper.absolutePos(TOWER)).getData(GasAttachments.CHUNK).deposit(Gas.ARSENIC_TRIOXIDE);
            helper.assertTrue(Math.abs(blown - 0.1f) < 1e-3f, "The rest blows away as dust, got " + blown);
            final float expected = Sulfide.CINNABAR.metalMoles(50f);
            // Mercury vapour over what the air can hold at once settles out as liquid on the floor, which the chunk keeps count of
            final float vapour = totalInBox(helper, 5, 3, Gas.MERCURY_VAPOR) + helper.getLevel().getChunkAt(helper.absolutePos(TOWER)).getData(GasAttachments.CHUNK).deposit(Gas.MERCURY_VAPOR);
            helper.assertTrue(Math.abs(vapour - expected) < 0.01f * expected, "50 mB of mercury evaporates into the room as " + expected + " mol, got " + vapour);
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.MERCURY_VAPOR) > 0f, "And some of it is in the air");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void theTowerAndItsRecipeLoad(GameTestHelper helper)
    {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(TFCpp.id("crafting/condensation_tower")).isPresent(), "The tower must be craftable");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = BATCH)
    public static void roastedGasIsDrawnFromTheRoomIntoAPipeNetwork(GameTestHelper helper)
    {
        // A sealed room with a roasting hearth in it, and a piston pump on a shaft that draws the room's air into a tower. The pump is a free-standing block: its inlet is the air at its back.
        sealedBox(helper, 5, 3, Blocks.STONE);
        final Block axle = TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.AXLE).get();
        helper.setBlock(4, 1, 2, TFCBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        // A creative motor stands still until it is stepped up, and at the top step it turns at the speed of a full water wheel
        final CreativeRotationBlockEntity motor = helper.getBlockEntity(new BlockPos(4, 1, 2));
        for (int step = 0; step < CreativeRotationBlockEntity.MAX_STEPS; step++)
        {
            motor.incrementSpeed();
        }
        helper.setBlock(3, 1, 2, axle.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        helper.setBlock(2, 1, 2, Pipes.PISTON_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(2, 1, 1, Metallurgy.CONDENSATION_TOWER.get());

        helper.runAtTickTime(10, () -> {
            // The furnace's own way of putting gas into a room, beside the pump's inlet
            final float[] roasted = new float[Gas.COUNT];
            roasted[Gas.SULFUR_DIOXIDE.ordinal()] = ROASTED;
            GasEmission.release(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 4)), roasted);
        });
        helper.runAtTickTime(450, () -> {
            final PistonPumpBlockEntity pump = helper.getBlockEntity(new BlockPos(2, 1, 2));
            helper.assertTrue(pump.getRotationNode().rotation() != null && pump.getRotationNode().rotation().speed() != 0f, "The shaft should be turning the pump");
            final PipeNetwork network = networkAt(helper, new BlockPos(2, 1, 1));
            helper.assertTrue(network != null, "The tower should be a network");
            final float inPipe = gasIn(network, Gas.SULFUR_DIOXIDE), inRoom = totalInBox(helper, 5, 3, Gas.SULFUR_DIOXIDE);
            helper.assertTrue(inPipe > 0.05f, "Sulfur dioxide must have moved from the room into the network, got " + inPipe + " mol");
            helper.assertTrue(inRoom < ROASTED - 0.05f, "And the room must have lost it, got " + inRoom + " mol");
            final float deposited = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(2, 1, 2))).getData(GasAttachments.CHUNK).deposit(Gas.SULFUR_DIOXIDE);
            helper.assertTrue(Math.abs(inPipe + inRoom + deposited - ROASTED) < 0.1f, "None is made or lost on the way: " + inPipe + " in the pipe, " + inRoom + " in the room and " + deposited + " settled");
            helper.assertTrue(towerAt(helper, new BlockPos(2, 1, 1)).dust().collected(Gas.SULFUR_DIOXIDE) == 0f, "The tower lets it through");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aMoltenMouldOfWorkLeadPoursIntoTheHearth(GameTestHelper helper)
    {
        final CupelHearthBlockEntity hearth = hearthAt(helper, HEARTH);
        final FakePlayer player = player(helper);

        // A mould of work lead that is hot enough to be molten, which is the only kind TFC lets be drained
        final ItemStack mould = filledMold(Metallurgy.WORK_LEAD.get(), 100);
        HeatCapability.setTemperature(mould, 700f);
        player.setItemInHand(InteractionHand.MAIN_HAND, mould);
        hearth.interact(mould, player, InteractionHand.MAIN_HAND);

        helper.assertTrue(hearth.bath().lead == 100f, "The molten work lead is poured in, got " + hearth.bath().lead);
        final ItemStack back = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(heldAmount(back) == 0, "And the mould is empty, got " + heldAmount(back));
        helper.assertTrue(!hearth.blicked(), "A new charge has not been blicked");

        // Plain lead is refused even molten
        final ItemStack plain = filledMold(Metallurgy.LEAD.get(), 100);
        HeatCapability.setTemperature(plain, 700f);
        hearth.interact(plain, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(hearth.bath().lead == 100f && heldAmount(plain) == 100, "Molten plain lead stays in its mould");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aMouldAlreadyHoldingSilverTakesMoreFromTheHearth(GameTestHelper helper)
    {
        final CupelHearthBlockEntity hearth = hotHearth(helper, HEARTH, 1100f);
        final FakePlayer player = player(helper);
        hearth.bath().silver = 6.5f; // The lead is all gone

        final ItemStack mould = filledMold(silver(), 2);
        HeatCapability.setTemperature(mould, 1000f);
        player.setItemInHand(InteractionHand.MAIN_HAND, mould);
        hearth.interact(mould, player, InteractionHand.MAIN_HAND);

        final ItemStack back = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(heldAmount(back) == 8, "Six whole millibuckets join the two already there, got " + heldAmount(back));
        helper.assertTrue(back.getCapability(Capabilities.FluidHandler.ITEM).getFluidInTank(0).getFluid() == silver(), "It is still silver");
        helper.assertTrue(Math.abs(hearth.bath().silver - 0.5f) < 1e-4f, "And the half millibucket stays in the hearth, got " + hearth.bath().silver);
        helper.succeed();
    }

    private LeadGameTests() {}
}
