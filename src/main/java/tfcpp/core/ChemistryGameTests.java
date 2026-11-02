package tfcpp.core;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.chemistry.Chemistry;
import tfcpp.chemistry.CokeOvenBlockEntity;
import tfcpp.chemistry.GasBurnerBlock;
import tfcpp.chemistry.Liquid;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasIgnition;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.pipes.PipeFittingBlock;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePhysics;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.Pipes;
import tfcpp.pneumatics.pipes.PistonPumpBlockEntity;

/** Checks the coke oven, the gas burner and the sulfur in fuel on a running server with TFC. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemistryGameTests
{
    private static final String TEMPLATE = "empty9";
    /** Apart from the others, as these put a lot of gas into the air. */
    private static final String BATCH = "tfcpp_chemistry";

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

    private static float totalInBox(GameTestHelper helper, int size, int height, Gas gas)
    {
        final LevelGasVolume volume = GasTracker.get(helper.getLevel()).volume(helper.getLevel());
        float total = 0;
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

    private static Item item(String id)
    {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static FakePlayer player(GameTestHelper helper, int x, int y, int z)
    {
        final FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    @Nullable
    private static PipeNetwork networkAt(GameTestHelper helper, BlockPos pos)
    {
        return PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(pos));
    }

    private static BlockState burner(boolean open, boolean lit)
    {
        return Chemistry.GAS_BURNER.get().defaultBlockState().setValue(GasBurnerBlock.OPEN, open).setValue(GasBurnerBlock.LIT, lit);
    }

    /** A vessel beside a burner, holding this gas and nothing else but what is given. */
    private static PipeNetwork fuelSupply(GameTestHelper helper, BlockPos vessel)
    {
        final PipeNetwork network = networkAt(helper, vessel);
        helper.assertTrue(network != null, "The vessel and burner should form a network");
        return network;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 900, batch = BATCH)
    public static void cokeOvenBatchGivesSolidLiquidsAndGasInThePipe(GameTestHelper helper)
    {
        final BlockPos ovenPos = new BlockPos(2, 1, 2);
        helper.setBlock(ovenPos, Chemistry.COKE_OVEN.get());
        helper.setBlock(ovenPos.east(), Metallurgy.RETORT_CONDENSER.get());
        for (int x = 1; x <= 7; x++)
        {
            helper.setBlock(x, 2, 2, Pipes.PRESSURE_VESSEL.get()); // Room for the gas, as an oven's walls hold only a few atmospheres
        }
        final CokeOvenBlockEntity oven = helper.getBlockEntity(ovenPos);
        oven.load(new ItemStack(item("tfc:ore/lignite"), 6));
        helper.onEachTick(() -> oven.setTemperature(1000f));

        helper.runAtTickTime(760, () -> {
            helper.assertTrue(oven.getTemperature() >= 700f, "The oven should be hot enough for lignite, got " + oven.getTemperature());
            for (int i = 0; i < 20; i++)
            {
                oven.step(helper.getLevel(), 60f); // A batch of 18 hours is 1080 seconds
            }
            helper.assertTrue(oven.charge().isEmpty() && oven.progress() == 0.0, "The batch should be finished");
            helper.assertTrue(oven.product().is(Chemistry.LIGNITE_CHAR.get()) && oven.product().getCount() == 2, "6 x 0.4 of char is 2 whole pieces, got " + oven.product());
            helper.assertTrue(oven.tarTank().getFluidAmount() == 1 && oven.tarTank().getFluid().getFluid() == Chemistry.fluid(Liquid.COAL_TAR), "6 x 0.24 L of tar is 1 mB, got " + oven.tarTank().getFluidAmount());
            helper.assertTrue(oven.liquorTank().getFluidAmount() == 6 && oven.liquorTank().getFluid().getFluid() == Chemistry.fluid(Liquid.AMMONIA_LIQUOR), "6 x 1 L of liquor is 6 mB, got " + oven.liquorTank().getFluidAmount());

            final PipeNetwork network = networkAt(helper, ovenPos);
            helper.assertTrue(network != null && network.segments().size() == 8, "The oven and its seven vessels should be one network");
            final float[] gases = network.contents().gases;
            helper.assertTrue(Math.abs(gases[Gas.CARBON_DIOXIDE.ordinal()] - 42f) < 0.5f, "6 x 7 mol of CO2 should be in the pipe, got " + gases[Gas.CARBON_DIOXIDE.ordinal()]);
            helper.assertTrue(Math.abs(gases[Gas.METHANE.ordinal()] - 18f) < 0.5f, "6 x 3 mol of CH4 should be in the pipe, got " + gases[Gas.METHANE.ordinal()]);
            helper.assertTrue(Math.abs(gases[Gas.HYDROGEN.ordinal()] - 12f) < 0.5f, "6 x 2 mol of H2 should be in the pipe, got " + gases[Gas.HYDROGEN.ordinal()]);
            helper.assertTrue(network.pressure() > 1.3f, "Hot gas should have raised the pressure, it is " + network.pressure());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1100, batch = BATCH)
    public static void cokeOvenWithoutAPipeLetsItsGasIntoTheRoom(GameTestHelper helper)
    {
        sealedBox(helper, 5, 4, Blocks.STONE);
        final BlockPos ovenPos = new BlockPos(2, 1, 2);
        helper.setBlock(ovenPos, Chemistry.COKE_OVEN.get());
        final CokeOvenBlockEntity oven = helper.getBlockEntity(ovenPos);
        oven.load(new ItemStack(item("tfc:ore/bituminous_coal"), 8));
        helper.onEachTick(() -> oven.setTemperature(1100f));

        helper.runAtTickTime(980, () -> {
            helper.assertTrue(oven.getTemperature() >= 950f, "The oven should be hot enough for coal, got " + oven.getTemperature());
            for (int i = 0; i < 20; i++)
            {
                oven.step(helper.getLevel(), 60f);
            }
            helper.assertTrue(oven.product().is(Chemistry.COKE.get()) && oven.product().getCount() == 5, "8 x 0.70 of coke is 5.6, which is 5 whole, got " + oven.product());
            helper.assertTrue(oven.tarTank().isEmpty() && oven.liquorTank().isEmpty(), "Without a condenser the vapours are lost");
            final float hydrogen = totalInBox(helper, 5, 4, Gas.HYDROGEN);
            helper.assertTrue(Math.abs(hydrogen - 8 * 21f) < 1f, "8 x 21 mol of H2 should be in the room, got " + hydrogen);
            final float methane = totalInBox(helper, 5, 4, Gas.METHANE);
            helper.assertTrue(Math.abs(methane - 8 * 14f) < 1f, "8 x 14 mol of CH4 should be in the room, got " + methane);
            final float sulfide = totalInBox(helper, 5, 4, Gas.HYDROGEN_SULFIDE);
            helper.assertTrue(Math.abs(sulfide - 8 * 0.2f) < 0.1f, "8 x 0.2 mol of H2S should be in the room, got " + sulfide);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 150, batch = BATCH)
    public static void aColdOvenDoesNothing(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.STONE);
        final BlockPos ovenPos = new BlockPos(2, 1, 2);
        helper.setBlock(ovenPos, Chemistry.COKE_OVEN.get());
        helper.setBlock(ovenPos.east(), Metallurgy.RETORT_CONDENSER.get());
        final CokeOvenBlockEntity oven = helper.getBlockEntity(ovenPos);
        oven.load(new ItemStack(item("tfc:ore/bituminous_coal"), 8));

        helper.runAtTickTime(100, () -> {
            oven.step(helper.getLevel(), 600f);
            helper.assertTrue(oven.progress() == 0.0 && !oven.isLocked(), "A cold charge must not change");
            helper.assertTrue(oven.charge().getCount() == 8 && oven.product().isEmpty(), "Nothing may be taken from or made of it");
            helper.assertTrue(oven.tarTank().isEmpty() && oven.liquorTank().isEmpty(), "Nothing may condense");
            helper.assertTrue(totalInBox(helper, 3, 3, Gas.HYDROGEN) == 0f && totalInBox(helper, 3, 3, Gas.METHANE) == 0f, "Nothing may come out");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600, batch = BATCH)
    public static void aBatchSurvivesSavingAndIsLockedOnceStarted(GameTestHelper helper)
    {
        final BlockPos ovenPos = new BlockPos(2, 1, 2);
        helper.setBlock(ovenPos, Chemistry.COKE_OVEN.get());
        helper.setBlock(ovenPos.east(), Metallurgy.RETORT_CONDENSER.get());
        final CokeOvenBlockEntity oven = helper.getBlockEntity(ovenPos);
        final Item log = item("tfc:wood/log/oak");
        oven.load(new ItemStack(log, 8));
        helper.onEachTick(() -> oven.setTemperature(450f));

        helper.runAtTickTime(450, () -> {
            helper.assertTrue(oven.getTemperature() >= 400f, "The oven should be hot enough for wood, got " + oven.getTemperature());
            oven.step(helper.getLevel(), 360f); // Half of 12 hours
            helper.assertTrue(oven.isLocked(), "A batch that has begun is locked");

            final FakePlayer player = player(helper, 2, 1, 4);
            final ItemStack more = new ItemStack(log, 4);
            oven.interact(more, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(more.getCount() == 4 && oven.charge().getCount() == 8, "Nothing may be added to a locked batch");

            final var registries = helper.getLevel().registryAccess();
            final BlockEntity loaded = BlockEntity.loadStatic(oven.getBlockPos(), oven.getBlockState(), oven.saveWithFullMetadata(registries), registries);
            helper.assertTrue(loaded instanceof CokeOvenBlockEntity, "The oven should load back");
            final CokeOvenBlockEntity copy = (CokeOvenBlockEntity) loaded;
            helper.assertTrue(ItemStack.matches(copy.charge(), oven.charge()), "The charge must survive");
            helper.assertTrue(copy.progress() == oven.progress() && copy.progress() >= 360.0, "The progress must survive, got " + copy.progress() + " of " + oven.progress());
            helper.assertTrue(copy.tarTank().getFluidAmount() == oven.tarTank().getFluidAmount() && copy.tarTank().getFluidAmount() >= 1, "The tar must survive");
            helper.assertTrue(copy.liquorTank().getFluidAmount() == oven.liquorTank().getFluidAmount() && copy.liquorTank().getFluidAmount() >= 8, "The liquor must survive");
            helper.assertTrue(copy.getTemperature() == oven.getTemperature(), "The temperature must survive");
            helper.assertTrue(copy.isLocked(), "and so must the lock");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void anOvenTakesSixtyFourPiecesOfOneKind(GameTestHelper helper)
    {
        final BlockPos ovenPos = new BlockPos(2, 1, 2);
        helper.setBlock(ovenPos, Chemistry.COKE_OVEN.get());
        final CokeOvenBlockEntity oven = helper.getBlockEntity(ovenPos);
        final FakePlayer player = player(helper, 2, 1, 4);

        final ItemStack first = new ItemStack(item("tfc:ore/bituminous_coal"), 60);
        oven.interact(first, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(first.isEmpty() && oven.charge().getCount() == 60, "All 60 should go in, got " + oven.charge().getCount());

        final ItemStack second = new ItemStack(item("tfc:ore/bituminous_coal"), 10);
        oven.interact(second, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(oven.charge().getCount() == 64 && second.getCount() == 6, "Only 4 more fit, got " + oven.charge().getCount() + " with " + second.getCount() + " left");

        final ItemStack lignite = new ItemStack(item("tfc:ore/lignite"), 3);
        oven.interact(lignite, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(lignite.getCount() == 3 && oven.charge().getCount() == 64, "Another kind must not go in");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aPumpAndABucketDrainBothTanks(GameTestHelper helper)
    {
        final BlockPos ovenPos = new BlockPos(3, 1, 3);
        helper.setBlock(ovenPos, Chemistry.COKE_OVEN.get());
        helper.setBlock(3, 1, 2, Pipes.PISTON_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(3, 1, 1, Pipes.PRESSURE_VESSEL.get());
        final CokeOvenBlockEntity oven = helper.getBlockEntity(ovenPos);
        oven.tarTank().fill(new FluidStack(Chemistry.fluid(Liquid.COAL_TAR), 20), IFluidHandler.FluidAction.EXECUTE);
        oven.liquorTank().fill(new FluidStack(Chemistry.fluid(Liquid.AMMONIA_LIQUOR), 100), IFluidHandler.FluidAction.EXECUTE);

        helper.runAtTickTime(5, () -> {
            final @Nullable IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(ovenPos), Direction.NORTH);
            helper.assertTrue(handler != null && handler.getTanks() == 2, "The oven should show two tanks");
            helper.assertTrue(handler.fill(new FluidStack(Chemistry.fluid(Liquid.COAL_TAR), 10), IFluidHandler.FluidAction.EXECUTE) == 0, "Nothing is pumped in");

            final PistonPumpBlockEntity pump = (PistonPumpBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 2));
            pump.pump(helper.getLevel(), 0.01f);
            final PipeNetwork vessel = networkAt(helper, new BlockPos(3, 1, 1));
            helper.assertTrue(vessel != null && vessel.contents().liquid.equals("tfcpp:coal_tar") && vessel.contents().litres > 9f, "The pump should have taken tar from the oven");
            helper.assertTrue(oven.tarTank().getFluidAmount() == 10, "The tar tank should have lost 10 mB, it holds " + oven.tarTank().getFluidAmount());

            oven.tarTank().drain(1000, IFluidHandler.FluidAction.EXECUTE);
            final FluidStack next = handler.drain(5, IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(next.getFluid() == Chemistry.fluid(Liquid.AMMONIA_LIQUOR) && next.getAmount() == 5, "With the tar gone the liquor comes next");

            final FakePlayer player = player(helper, 2, 1, 4);
            final ItemStack bucket = new ItemStack(TFCItems.WOODEN_BUCKET.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
            oven.interact(bucket, player, InteractionHand.MAIN_HAND);
            final var held = player.getItemInHand(InteractionHand.MAIN_HAND).getCapability(Capabilities.FluidHandler.ITEM);
            helper.assertTrue(held != null && held.getFluidInTank(0).getFluid() == Chemistry.fluid(Liquid.AMMONIA_LIQUOR), "A wooden bucket should take the liquor");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 250, batch = BATCH)
    public static void aBurnerHeatsWhatStandsOnIt(GameTestHelper helper)
    {
        final BlockPos burnerPos = new BlockPos(2, 1, 2);
        helper.setBlock(burnerPos, burner(true, true));
        helper.setBlock(burnerPos.above(), Chemistry.COKE_OVEN.get());
        helper.setBlock(burnerPos.west(), Pipes.PRESSURE_VESSEL.get());
        final CokeOvenBlockEntity oven = helper.getBlockEntity(burnerPos.above());

        helper.onEachTick(() -> {
            // Keep the supply up: pure hydrogen, with no air in it, at 1.3 atm
            final PipeNetwork network = networkAt(helper, burnerPos.west());
            if (network != null)
            {
                network.contents().air = 0f;
                network.contents().gases[Gas.HYDROGEN.ordinal()] = PipePhysics.moles(1.3f, network.contents().temperature, network.volume());
            }
            // A burner with no gas yet goes out, and this one is meant to stay lit throughout
            if (!helper.getBlockState(burnerPos).getValue(GasBurnerBlock.LIT))
            {
                helper.setBlock(burnerPos, burner(true, true));
            }
        });
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(oven.getTemperature() > 100f, "The burner should have heated the oven above it, it is at " + oven.getTemperature());
            helper.assertBlockPresent(Chemistry.GAS_BURNER.get(), burnerPos);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void aClosedBurnerTakesAndGivesNothing(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.OBSIDIAN);
        final BlockPos burnerPos = new BlockPos(2, 1, 2);
        helper.setBlock(burnerPos, burner(false, false));
        helper.setBlock(burnerPos.north(), Pipes.PRESSURE_VESSEL.get());

        final float[] filled = new float[1];
        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = fuelSupply(helper, burnerPos.north());
            network.contents().air = 0f;
            filled[0] = PipePhysics.moles(1.5f, network.contents().temperature, network.volume());
            network.contents().gases[Gas.HYDROGEN.ordinal()] = filled[0];
        });
        helper.runAtTickTime(90, () -> {
            final PipeNetwork network = fuelSupply(helper, burnerPos.north());
            helper.assertTrue(Math.abs(network.contents().gases[Gas.HYDROGEN.ordinal()] - filled[0]) < 0.01f, "A closed burner takes nothing, the pipe holds " + network.contents().gases[Gas.HYDROGEN.ordinal()] + " of " + filled[0]);
            helper.assertTrue(totalInBox(helper, 3, 3, Gas.HYDROGEN) == 0f, "and lets nothing out");
            helper.assertTrue(totalInBox(helper, 3, 3, Gas.CARBON_DIOXIDE) == 0f && totalInBox(helper, 3, 3, Gas.FLUE_NITROGEN) == 0f, "or gives off anything");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void anOpenBurnerWithNoFlameFillsTheRoomWithGas(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.OBSIDIAN);
        final BlockPos burnerPos = new BlockPos(2, 1, 2);
        helper.setBlock(burnerPos, burner(true, false));
        helper.setBlock(burnerPos.north(), Pipes.PRESSURE_VESSEL.get());

        final float[] filled = new float[1];
        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = fuelSupply(helper, burnerPos.north());
            network.contents().air = 0f;
            filled[0] = PipePhysics.moles(1.5f, network.contents().temperature, network.volume());
            network.contents().gases[Gas.HYDROGEN.ordinal()] = filled[0];
        });
        helper.runAtTickTime(90, () -> {
            final PipeNetwork network = fuelSupply(helper, burnerPos.north());
            final float inRoom = totalInBox(helper, 3, 3, Gas.HYDROGEN);
            helper.assertTrue(inRoom > 1f, "The gas should be coming into the room, found " + inRoom);
            helper.assertTrue(Math.abs(inRoom + network.contents().gases[Gas.HYDROGEN.ordinal()] - filled[0]) < 0.1f, "and it is the pipe's gas, not more: " + inRoom + " in the room, " + network.contents().gases[Gas.HYDROGEN.ordinal()] + " in the pipe, of " + filled[0]);
            helper.assertTrue(totalInBox(helper, 3, 3, Gas.CARBON_DIOXIDE) == 0f, "Nothing is burning");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void aFlameGoesOutWhenItsGasStops(GameTestHelper helper)
    {
        final BlockPos burnerPos = new BlockPos(2, 1, 2);
        helper.setBlock(burnerPos, burner(true, true));
        helper.setBlock(burnerPos.north(), Pipes.PRESSURE_VESSEL.get());

        helper.runAtTickTime(60, () -> {
            helper.assertFalse(helper.getLevel().getBlockState(helper.absolutePos(burnerPos)).getValue(GasBurnerBlock.LIT), "With only air in the pipe the flame cannot stay lit");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void theRecipesOfTheOvenAndBurnerLoadAndTheLightersExist(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        for (String recipe : new String[] {"crafting/coke_oven", "crafting/gas_burner", "carbonization/coal", "carbonization/lignite", "carbonization/wood", "quern/coke"})
        {
            helper.assertTrue(recipes.byKey(TFCpp.id(recipe)).isPresent(), "Missing recipe " + recipe);
        }
        for (String id : new String[] {"minecraft:flint_and_steel", "tfc:firestarter", "tfc:flint_and_pyrite"})
        {
            final ResourceLocation location = ResourceLocation.parse(id);
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(location), "No item " + id);
            helper.assertTrue(new ItemStack(BuiltInRegistries.ITEM.get(location)).is(Chemistry.LIGHTS_BURNER), id + " must be in the tag that lights a burner");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aBurnerIsLitByFlintAndSteelAndIsAnOpenFlame(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, burner(false, false));
        final BlockPos absolute = helper.absolutePos(pos);
        final FakePlayer player = player(helper, 2, 1, 4);
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        final ServerLevel level = helper.getLevel();

        final ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        level.getBlockState(absolute).useItemOn(flint, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertFalse(level.getBlockState(absolute).getValue(GasBurnerBlock.LIT), "A shut burner has no gas to light");

        level.getBlockState(absolute).useWithoutItem(level, player, hit);
        helper.assertTrue(level.getBlockState(absolute).getValue(GasBurnerBlock.OPEN), "The hand wheel should open it");
        helper.assertFalse(GasIgnition.isOpenFlame(level.getBlockState(absolute)), "Open but unlit is no flame");

        level.getBlockState(absolute).useItemOn(flint, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(level.getBlockState(absolute).getValue(GasBurnerBlock.LIT), "Flint and steel should light it");
        helper.assertTrue(flint.getDamageValue() == 1, "and wear");
        helper.assertTrue(GasIgnition.isOpenFlame(level.getBlockState(absolute)), "A lit burner is an open flame to the gas around it");

        level.getBlockState(absolute).useWithoutItem(level, player, hit);
        helper.assertFalse(level.getBlockState(absolute).getValue(GasBurnerBlock.LIT), "Shutting the gas puts it out");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void gasThatCanBurnInThePipeSendsTheFlameBackAndTheBurnerGivesWay(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.OBSIDIAN);
        final BlockPos burnerPos = new BlockPos(2, 1, 2);
        helper.setBlock(burnerPos, burner(true, false));
        helper.setBlock(burnerPos.north(), Pipes.PRESSURE_VESSEL.get());

        helper.runAtTickTime(5, () -> {
            // Lit only once there is a mixture in the pipe, as a flame with no gas goes out
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(burnerPos), burner(true, true));
            final PipeNetwork network = fuelSupply(helper, burnerPos.north());
            final float moles = PipePhysics.moles(1.3f, network.contents().temperature, network.volume());
            network.contents().air = moles * 0.92f;
            network.contents().gases[Gas.METHANE.ordinal()] = moles * 0.08f; // Inside the limits of flammability
        });
        helper.runAtTickTime(70, () -> {
            helper.assertBlockPresent(Blocks.AIR, burnerPos);
            final PipeNetwork network = networkAt(helper, burnerPos.north());
            helper.assertTrue(network == null || network.contents().gases[Gas.METHANE.ordinal()] < 0.5f, "The methane in the pipe should have burned");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aForgeBurningCoalGivesSulfurDioxide(GameTestHelper helper)
    {
        final float released = burnInForge(helper, Items.COAL);
        helper.assertTrue(Math.abs(released - 1.4f) < 0.01f, "A piece of coal should give 1.4 mol of SO2, got " + released);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aForgeBurningCharcoalGivesNone(GameTestHelper helper)
    {
        helper.assertTrue(burnInForge(helper, Items.CHARCOAL) == 0f, "Charcoal holds no sulfur");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aFirepitTakingCokeAlsoGivesSulfurDioxide(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.STONE);
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, TFCBlocks.FIREPIT.get());
        final var firepit = (net.dries007.tfc.common.blockentities.FirepitBlockEntity) helper.getBlockEntity(pos);
        firepit.getInventory().setStackInSlot(0, new ItemStack(Chemistry.COKE.get()));
        firepit.light(helper.getLevel().getBlockState(helper.absolutePos(pos)));
        final float released = totalInBox(helper, 3, 3, Gas.SULFUR_DIOXIDE);
        helper.assertTrue(Math.abs(released - 0.9f) < 0.01f, "A piece of coke should give 0.9 mol of SO2, got " + released);
        helper.succeed();
    }

    /** @return moles of sulfur dioxide in the room after a forge has taken one piece of this fuel to burn */
    private static float burnInForge(GameTestHelper helper, Item fuel)
    {
        final Block insulation = TFCBlocks.ROCK_BLOCKS.get(Rock.GRANITE).get(Rock.BlockType.BRICKS).get();
        sealedBox(helper, 3, 3, insulation);
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, TFCBlocks.CHARCOAL_FORGE.get());
        for (BlockPos side : new BlockPos[] {pos.north(), pos.south(), pos.east(), pos.west()})
        {
            helper.setBlock(side, insulation);
        }
        final CharcoalForgeBlockEntity forge = helper.getBlockEntity(pos);
        forge.getInventory().setStackInSlot(CharcoalForgeBlockEntity.SLOT_FUEL_MIN, new ItemStack(fuel));
        forge.light(helper.getLevel().getBlockState(helper.absolutePos(pos)));
        return totalInBox(helper, 3, 3, Gas.SULFUR_DIOXIDE);
    }

    private ChemistryGameTests() {}
}
