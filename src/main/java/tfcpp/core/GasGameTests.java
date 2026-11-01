package tfcpp.core;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.RotatingBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.WaterWheelBlockEntity;
import net.dries007.tfc.common.blocks.DirectionPropertyBlock;
import net.dries007.tfc.common.blocks.rotation.FluidPumpBlock;
import net.dries007.tfc.common.blocks.rotation.WaterWheelBlock;
import net.dries007.tfc.common.blocks.wood.Wood;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.util.rotation.Node;
import net.dries007.tfc.common.blockentities.LampBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.common.blocks.devices.LampBlock;
import net.dries007.tfc.common.blocks.devices.PitKilnBlock;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.component.heat.HeatDefinition;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.geology.SeamGas;
import tfcpp.mechanics.MassPump;
import tfcpp.mechanics.MineFanBlockEntity;
import tfcpp.mechanics.ShaftPhysics;
import tfcpp.mechanics.ShaftSource;
import tfcpp.mechanics.ShaftTracker;
import tfcpp.mechanics.WaterBodies;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.metallurgy.RetortBlockEntity;
import tfcpp.metallurgy.Sulfide;
import tfcpp.pneumatics.gas.Breathing;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.gas.GasIgnition;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.gas.Physiology;
import tfcpp.pneumatics.instruments.DavyLamp;
import tfcpp.pneumatics.pipes.PipeChunkData;
import tfcpp.pneumatics.pipes.PipeFittingBlock;
import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePhysics;
import tfcpp.pneumatics.pipes.PipeSegment;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.Pipes;
import tfcpp.pneumatics.pipes.PistonPumpBlockEntity;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.pneumatics.instruments.SentinelCageItem;
import tfcpp.pneumatics.instruments.SettlingBaffleBlockEntity;

/** Checks the parts that only exist inside a running server: mixins, TFC block entities, chunk storage and blocks stopping gas. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasGameTests
{
    private static final String TEMPLATE = "empty9";

    /** Builds a hollow stone box, with an air interior from (1,1,1) to (size,height,size) relative to the test. */
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

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void gasStaysInASealedRoomAndSinks(GameTestHelper helper)
    {
        sealedBox(helper, 3, 4, Blocks.STONE);
        addGas(helper, 2, 4, 2, Gas.SULFUR_DIOXIDE, 200f); // Enough to lie in layers: a trace of it just mixes evenly through a room, as it does in reality

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(Math.abs(totalInBox(helper, 3, 4, Gas.SULFUR_DIOXIDE) - 200f) < 1f, "SO2 must be conserved in a sealed room");

            final float[] floor = cell(helper, 2, 1, 2), roof = cell(helper, 2, 4, 2);
            // A cell with nothing left in it is freed, so a roof with no cell at all is the clearest result of everything having sunk
            final float atFloor = floor == null ? 0f : floor[Gas.SULFUR_DIOXIDE.ordinal()], atRoof = roof == null ? 0f : roof[Gas.SULFUR_DIOXIDE.ordinal()];
            helper.assertTrue(atFloor > atRoof, "SO2 must collect at the floor, got " + atFloor + " there and " + atRoof + " at the roof");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void closedDoorHoldsGasBackAndOpenDoorLetsItThrough(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2, Blocks.STONE);

        // A wall across x = 2, with a door in it
        for (int z = 1; z <= 3; z++)
        {
            helper.setBlock(2, 1, z, Blocks.STONE);
            helper.setBlock(2, 2, z, Blocks.STONE);
        }
        final BlockPos door = new BlockPos(2, 1, 2);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState());
        helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

        addGas(helper, 1, 1, 2, Gas.CARBON_DIOXIDE, 10f);

        helper.runAfterDelay(80, () -> {
            helper.assertTrue(cell(helper, 3, 1, 2) == null, "Gas must not pass a closed door");
            helper.setBlock(door, helper.getBlockState(door).setValue(DoorBlock.OPEN, true));
            helper.setBlock(door.above(), helper.getBlockState(door.above()).setValue(DoorBlock.OPEN, true));
        });
        helper.runAfterDelay(240, () -> {
            helper.assertTrue(cell(helper, 3, 1, 2) != null, "Gas must pass an open door");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, skyAccess = true)
    public static void gasUnderOpenSkyDisperses(GameTestHelper helper)
    {
        final BlockPos above = new BlockPos(4, 6, 4);
        addGas(helper, above.getX(), above.getY(), above.getZ(), Gas.CARBON_DIOXIDE, 0.05f);

        helper.runAfterDelay(400, () -> {
            final float[] cell = cell(helper, above.getX(), above.getY(), above.getZ());
            helper.assertTrue(cell == null || cell[Gas.CARBON_DIOXIDE.ordinal()] < 0.001f, "The atmosphere must carry gas away");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void forgeNoLongerNeedsToSeeTheSky(GameTestHelper helper)
    {
        final Block insulation = TFCBlocks.ROCK_BLOCKS.get(Rock.GRANITE).get(Rock.BlockType.BRICKS).get();

        sealedBox(helper, 3, 3, insulation);
        final BlockPos forge = new BlockPos(2, 1, 2);
        for (BlockPos side : new BlockPos[] {forge.north(), forge.south(), forge.east(), forge.west()})
        {
            helper.setBlock(side, insulation);
        }

        final BlockPos absolute = helper.absolutePos(forge);
        helper.assertFalse(volume(helper).isOpenToSky(absolute.getX(), absolute.getY() + 1, absolute.getZ()), "The test room should be roofed");
        helper.assertTrue(CharcoalForgeBlock.isValid(helper.getLevel(), absolute), "A roofed, insulated forge must be valid");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void charcoalPitFillsARoomWithFlueGas(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true); // The TFC calendar stands still on an empty server, and fires burn by the calendar
        sealedBox(helper, 3, 3, Blocks.STONE);
        helper.setBlock(2, 1, 2, TFCBlocks.BURNING_LOG_PILE.get());

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(GasTracker.get(helper.getLevel()).fireCount() > 0, "The pit must be tracked as a fire");
            final float carbonMonoxide = totalInBox(helper, 3, 3, Gas.CARBON_MONOXIDE);
            helper.assertTrue(carbonMonoxide > 0f, "A burning pit must release CO, got " + carbonMonoxide);
            final float[] roof = cell(helper, 2, 3, 2);
            helper.assertTrue(roof != null && GasMath.oxygenFraction(roof) < GasMath.OXYGEN_IN_AIR, "Burning must use up oxygen");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void breathingCarbonMonoxideBindsToBlood(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.STONE);
        final TestPlayer player = survivalPlayerAt(helper, 2, 1, 2);
        fillMouth(helper, player, Gas.CARBON_MONOXIDE, 0.6f); // About 14400 ppm

        for (int second = 0; second < 30; second++)
        {
            Breathing.tick(player);
        }

        final float blood = player.getData(GasAttachments.BREATHING).carboxyhemoglobin;
        helper.assertTrue(blood > 10f && blood < Physiology.COHB_FATAL, "30 s at about 14400 ppm should give roughly 19% COHb, got " + blood);
        helper.assertTrue(player.lastDamage == null, "CO must not hurt until it is too late");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void airWithoutOxygenSuffocates(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.STONE);
        final TestPlayer player = survivalPlayerAt(helper, 2, 1, 2);
        fillMouth(helper, player, Gas.FLUE_NITROGEN, GasMath.CELL_MOLES);

        Breathing.tick(player);

        helper.assertTrue(player.lastDamage != null && player.lastDamage.is(Breathing.ASPHYXIATION), "Breathing pure nitrogen must suffocate");
        helper.assertTrue(player.lastDamage.is(DamageTypeTags.BYPASSES_ARMOR), "Armour must not protect against a lack of oxygen");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void meltingTetrahedriteGivesOffFumesAndArsenicalCopper(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.STONE);
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, TFCBlocks.CRUCIBLE.get());
        final CrucibleBlockEntity crucible = helper.getBlockEntity(pos);

        final ItemStack ore = new ItemStack(TFCItems.GRADED_ORES.get(Ore.TETRAHEDRITE).get(Ore.Grade.NORMAL).get());
        HeatCapability.setTemperature(ore, 1200f);
        crucible.getInventory().setStackInSlot(0, ore);
        crucible.setAndUpdateSlots(0);
        crucible.getInventory().setTemperature(1200f);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(crucible.getInventory().getStackInSlot(0).isEmpty(), "The ore should have melted");
            helper.assertTrue(crucible.getAlloyResult().getFluid() == Metallurgy.ARSENICAL_COPPER.get(), "Tetrahedrite must give arsenical copper, got " + crucible.getAlloyResult().getFluid());

            final float sulfurDioxide = totalInBox(helper, 3, 3, Gas.SULFUR_DIOXIDE);
            final float expected = Sulfide.TETRAHEDRITE.fumes(25f)[Gas.SULFUR_DIOXIDE.ordinal()];
            helper.assertTrue(Math.abs(sulfurDioxide - expected) < 0.05f, "Expected " + expected + " mol of SO2, got " + sulfurDioxide);
            helper.assertTrue(totalInBox(helper, 3, 3, Gas.ARSENIC_TRIOXIDE) > 0f, "Arsenic must come off as well");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1400)
    public static void retortWithCondenserMakesZinc(GameTestHelper helper)
    {
        final RetortBlockEntity retort = retortIn(helper, true);

        helper.runAfterDelay(1200, /* A retort heats at about 1 C per tick */ () -> {
            helper.assertTrue(retort.tank().getFluidAmount() == 25, "Expected 25 mB of zinc, got " + retort.tank().getFluidAmount());
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.ZINC_OXIDE_FUME) == 0f, "Nothing should have escaped");
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.CARBON_MONOXIDE) > 0f, "Reducing with carbon gives CO");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1400)
    public static void retortWithoutCondenserLosesTheZincAsSmoke(GameTestHelper helper)
    {
        final RetortBlockEntity retort = retortIn(helper, false);

        helper.runAfterDelay(1200, /* A retort heats at about 1 C per tick */ () -> {
            helper.assertTrue(retort.tank().isEmpty(), "Nothing can condense");
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.ZINC_OXIDE_FUME) > 0f, "The zinc must escape and burn");
            helper.succeed();
        });
    }

    private static RetortBlockEntity retortIn(GameTestHelper helper, boolean condenser)
    {
        sealedBox(helper, 5, 3, Blocks.STONE);
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Metallurgy.RETORT.get());
        if (condenser)
        {
            helper.setBlock(pos.east(), Metallurgy.RETORT_CONDENSER.get());
        }
        final RetortBlockEntity retort = helper.getBlockEntity(pos);
        retort.load(new ItemStack(Metallurgy.ZINC_CALCINES.get("normal").get()), 10f);
        helper.onEachTick(() -> retort.setTemperature(1100f));
        return retort;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void forgeVentsAroundAVesselOnTopOfIt(GameTestHelper helper)
    {
        sealedBox(helper, 3, 5, Blocks.STONE);
        final BlockPos fire = new BlockPos(2, 1, 2);
        helper.setBlock(fire, TFCBlocks.FIREPIT.get());
        helper.setBlock(fire.above(), Metallurgy.RETORT.get());

        final BlockPos absolute = helper.absolutePos(fire);
        final int outlet = GasEmission.outletHeight(helper.getLevel(), volume(helper), absolute);
        helper.assertTrue(outlet == absolute.getY() + 2, "Gas must come out above the retort, got height " + (outlet - absolute.getY()));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void impossibleRecipesAreGoneAndCastIronIsFixed(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        helper.assertFalse(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "quern/redstone")).isPresent(), "Cinnabar must not grind to redstone");
        helper.assertFalse(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "quern/ore/gypsum")).isPresent(), "Limestone must not grind to gypsum");
        helper.assertTrue(recipes.byKey(TFCpp.id("retort/zinc_normal")).isPresent(), "The zinc retort recipe must load");
        helper.assertTrue(recipes.byKey(TFCpp.id("retort/mercury")).isPresent(), "The mercury retort recipe must load");
        helper.assertTrue(recipes.byKey(TFCpp.id("crafting/retort")).isPresent(), "The retort must be craftable");
        helper.assertTrue(recipes.byKey(TFCpp.id("crafting/retort_condenser")).isPresent(), "The condenser must be craftable");
        helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "crafting/thermometer")).isPresent(), "The thermometer must be craftable");
        helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "casting/copper_pickaxe_head")).isPresent(), "Patched copper casting must load");
        helper.assertTrue(recipes.byKey(TFCpp.id("alloy/arsenical_bronze")).isPresent(), "The arsenical bronze alloy must load");

        final FluidHeat castIron = FluidHeat.get(TFCFluids.METALS.get(Metal.CAST_IRON).getSource());
        helper.assertTrue(castIron != null && castIron.meltTemperature() == 1200f, "Cast iron must melt at 1200 C");
        helper.assertTrue(FluidHeat.get(Metallurgy.ARSENICAL_COPPER.get()) != null, "Arsenical copper needs its melting data");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void aDeadFireDoesNotKeepARetortHot(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Metallurgy.RETORT.get());
        final RetortBlockEntity retort = helper.getBlockEntity(pos);

        final boolean[] burning = {true};
        helper.onEachTick(() -> retort.setTemperature(burning[0] ? 300f : 0f));
        helper.runAfterDelay(320, () -> {
            helper.assertTrue(retort.getTemperature() >= 300f, "The retort should have reached the fire's temperature, got " + retort.getTemperature());
            burning[0] = false;
        });
        helper.runAfterDelay(1000, () -> {
            helper.assertTrue(retort.getTemperature() == 0f, "The retort must cool once the fire is dead, got " + retort.getTemperature());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void aStackOfContainersFillsOneAtATime(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Metallurgy.RETORT.get());
        final RetortBlockEntity retort = helper.getBlockEntity(pos);
        retort.tank().fill(new FluidStack(Metallurgy.MERCURY.get(), 1000), IFluidHandler.FluidAction.EXECUTE);

        final ItemStack bottles = new ItemStack(TFCItems.SILICA_GLASS_BOTTLE.get(), 4);
        final TestPlayer player = survivalPlayerAt(helper, 2, 2, 2);
        retort.interact(bottles, player, InteractionHand.MAIN_HAND);

        final int taken = 1000 - retort.tank().getFluidAmount();
        final FluidStack inFirstBottle = player.getInventory().items.stream()
            .map(stack -> stack.getCapability(Capabilities.FluidHandler.ITEM))
            .filter(handler -> handler != null)
            .map(handler -> handler.getFluidInTank(0))
            .filter(fluid -> !fluid.isEmpty())
            .findFirst().orElse(FluidStack.EMPTY);
        helper.assertTrue(bottles.getCount() == 3, "One bottle must be taken from the stack, left " + bottles.getCount());
        helper.assertTrue(taken > 0 && taken == inFirstBottle.getAmount(), "The tank must lose exactly what the bottle gained: lost " + taken + ", bottle has " + inFirstBottle.getAmount());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nothingIsLeftNeedingRedstoneAndCastIronCanStillBeWelded(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        for (var holder : recipes.getRecipes())
        {
            if (holder.id().getNamespace().equals("tfc"))
            {
                for (Ingredient ingredient : holder.value().getIngredients())
                {
                    helper.assertFalse(ingredient.test(new ItemStack(Items.REDSTONE)), holder.id() + " still needs redstone, which can no longer be made");
                }
            }
        }

        final HeatDefinition castIron = HeatCapability.getDefinition(new ItemStack(TFCItems.METAL_ITEMS.get(Metal.CAST_IRON).get(Metal.ItemType.INGOT).get()));
        helper.assertTrue(castIron != null && castIron.weldingTemperature() < 1200f, "Cast iron must be weldable below its melting point");

        final HeatingRecipe tool = HeatingRecipe.getRecipe(new ItemStack(TFCItems.METAL_ITEMS.get(Metal.COPPER).get(Metal.ItemType.AXE_HEAD).get()));
        helper.assertTrue(tool != null && tool.getDisplayOutputFluid().getFluid() == Metallurgy.ARSENICAL_COPPER.get(), "Remelting a cast tool must not purify it");
        helper.succeed();
    }

    private static void fillBox(GameTestHelper helper, int size, int height, Gas gas, float fraction)
    {
        for (int x = 1; x <= size; x++)
        {
            for (int y = 1; y <= height; y++)
            {
                for (int z = 1; z <= size; z++)
                {
                    addGas(helper, x, y, z, gas, fraction * GasMath.CELL_MOLES);
                }
            }
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void torchLightsFiredampAndTheBlastBreaksGlass(GameTestHelper helper)
    {
        sealedBox(helper, 5, 3, Blocks.OBSIDIAN);
        helper.setBlock(1, 1, 1, Blocks.GLASS);
        helper.setBlock(3, 1, 3, Blocks.TORCH);
        fillBox(helper, 5, 3, Gas.METHANE, 0.095f);

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 1, 1));
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.METHANE) < 10f, "The methane should have burned, apart from what the glass pushed out into a mixture too rich to burn");
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.CARBON_DIOXIDE) > 100f, "The flame should leave CO2 behind");
            helper.assertTrue(totalInBox(helper, 5, 3, Gas.FLUE_NITROGEN) > 500f, "The flame should leave nitrogen behind");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void torchDoesNotLightAirBelowTheLimit(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 2, Blocks.TORCH);
        fillBox(helper, 3, 3, Gas.METHANE, 0.03f);

        helper.runAtTickTime(80, () -> {
            helper.assertTrue(totalInBox(helper, 3, 3, Gas.CARBON_DIOXIDE) == 0f, "3% methane cannot burn");
            helper.assertBlockPresent(Blocks.TORCH, new BlockPos(2, 1, 2));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void onlyOpenFlamesLightGas(GameTestHelper helper)
    {
        final BlockState lit = Instruments.DAVY_LAMP.get().defaultBlockState().setValue(LampBlock.LIT, true);
        helper.assertFalse(GasIgnition.isOpenFlame(lit), "The gauze must keep a Davy lamp from lighting gas");
        helper.assertTrue(GasIgnition.isOpenFlame(Blocks.TORCH.defaultBlockState()), "A torch is a flame");
        helper.assertTrue(GasIgnition.isOpenFlame(TFCBlocks.TORCH.get().defaultBlockState()), "A TFC torch is a flame");
        helper.assertFalse(GasIgnition.isOpenFlame(TFCBlocks.DEAD_TORCH.get().defaultBlockState()), "A dead torch is not");
        helper.assertTrue(GasIgnition.isOpenFlame(Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true)), "A lit campfire is a flame");
        helper.assertFalse(GasIgnition.isOpenFlame(Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false)), "A dead campfire is not");
        helper.assertFalse(GasIgnition.isOpenFlame(TFCBlocks.CHARCOAL_FORGE.get().defaultBlockState()), "A cold forge is not");
        helper.assertTrue(GasIgnition.isOpenFlame(TFCBlocks.CHARCOAL_FORGE.get().defaultBlockState().setValue(CharcoalForgeBlock.HEAT, 3)), "A hot forge is a flame");

        helper.assertFalse(GasIgnition.isOpenFlame(TFCBlocks.FIREBOX.get().defaultBlockState()), "An unlit firebox is not");
        helper.assertFalse(GasIgnition.isOpenFlame(TFCBlocks.PIT_KILN.get().defaultBlockState()), "An unlit pit kiln is not");
        helper.assertTrue(GasIgnition.isOpenFlame(TFCBlocks.PIT_KILN.get().defaultBlockState().setValue(PitKilnBlock.STAGE, PitKilnBlock.LIT)), "A burning pit kiln is a flame");

        helper.setBlock(1, 1, 1, Blocks.STONE);
        helper.setBlock(1, 2, 1, lit);
        helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 2, 1)) instanceof LampBlockEntity, "A Davy lamp must use the TFC lamp block entity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void carriedTorchLightsGas(GameTestHelper helper)
    {
        sealedBox(helper, 3, 4, Blocks.OBSIDIAN);
        fillBox(helper, 3, 4, Gas.METHANE, 0.09f);
        final TestPlayer player = survivalPlayerAt(helper, 2, 1, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.TORCH));
        Breathing.tick(player);

        helper.succeedWhen(() -> helper.assertTrue(totalInBox(helper, 3, 4, Gas.METHANE) < 1f, "Carrying a torch into firedamp must light it"));
    }

    private static ItemStack fuelledLamp(GameTestHelper helper)
    {
        final ItemStack lamp = new ItemStack(Instruments.DAVY_LAMP_ITEM.get());
        final IFluidHandlerItem handler = lamp.getCapability(Capabilities.FluidHandler.ITEM);
        helper.assertTrue(handler != null, "A Davy lamp must hold fuel");
        final Fluid tallow = BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("tfc", "tallow"));
        final int filled = handler.fill(new FluidStack(tallow, 100), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(filled == 100, "A Davy lamp must take the fuels of a TFC lamp, took " + filled);
        return handler.getContainer();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void lampOnlyLightsGasOnceTheGauzeIsHot(GameTestHelper helper)
    {
        sealedBox(helper, 3, 4, Blocks.OBSIDIAN);
        fillBox(helper, 3, 4, Gas.METHANE, 0.09f);
        final TestPlayer player = survivalPlayerAt(helper, 2, 1, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, fuelledLamp(helper));

        for (int second = 0; second < 60; second++)
        {
            DavyLamp.tickHeld(player);
        }

        helper.runAtTickTime(40, () -> {
            helper.assertTrue(totalInBox(helper, 3, 4, Gas.METHANE) > 100f, "A minute inside the gauze must not light the gas");
            for (int second = 0; second < 60; second++)
            {
                DavyLamp.tickHeld(player);
            }
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(totalInBox(helper, 3, 4, Gas.METHANE) < 1f, "Two minutes inside the gauze must light it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void placedLampLightsGasOnceItsGauzeIsHot(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.OBSIDIAN);
        final BlockPos lamp = new BlockPos(2, 1, 2);
        helper.setBlock(lamp, Instruments.DAVY_LAMP.get().defaultBlockState().setValue(LampBlock.LIT, true));
        final LampBlockEntity entity = (LampBlockEntity) helper.getBlockEntity(lamp);
        entity.resetCounter(); // As placing it by hand does: otherwise it counts as burning since the start of the world
        entity.getSidedFluidInventory(null).fill(new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("tfc", "tallow")), 100), IFluidHandler.FluidAction.EXECUTE);
        fillBox(helper, 3, 3, Gas.METHANE, 0.09f);

        helper.runAtTickTime(30, () -> helper.assertTrue(totalInBox(helper, 3, 3, Gas.METHANE) > 50f, "A lit lamp must not light the gas right away"));
        helper.runAtTickTime(31, () -> {
            final BlockPos absolute = helper.absolutePos(lamp);
            for (int i = 0; i < 40; i++)
            {
                helper.getLevel().getBlockState(absolute).randomTick(helper.getLevel(), absolute, helper.getLevel().random);
            }
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(totalInBox(helper, 3, 3, Gas.CARBON_DIOXIDE) > 20f, "The gauze must fail in the end");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void fanCarriesGasOutOfARoom(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2, Blocks.OBSIDIAN);
        // The fan stands in the roof, draws from the room below it, and blows sideways into the open air
        helper.setBlock(2, 3, 2, Instruments.MINE_FAN.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(3, 2, 2, Blocks.OBSIDIAN);
        helper.setBlock(3, 3, 2, Blocks.AIR);
        addGas(helper, 1, 1, 1, Gas.CARBON_DIOXIDE, 10f);

        final MineFanBlockEntity fan = (MineFanBlockEntity) helper.getBlockEntity(new BlockPos(2, 3, 2));
        for (int second = 0; second < 30; second++)
        {
            helper.assertTrue(fan.moveAir(helper.getLevel(), 1.57f, 1f) || second > 0, "The fan should find the gas");
        }
        final float left = totalInBox(helper, 3, 2, Gas.CARBON_DIOXIDE);
        helper.assertTrue(left < 8f, "Half a minute of a fan should clear the room, " + left + " of 10 mol is left");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void twoSourcesDoNotMakeAThird(GameTestHelper helper)
    {
        sealedBox(helper, 3, 1, Blocks.OBSIDIAN);
        helper.setBlock(1, 2, 1, Blocks.AIR);
        for (int z = 1; z <= 3; z++)
        {
            helper.setBlock(1, 1, z, z == 2 ? Blocks.AIR : Blocks.WATER);
            helper.setBlock(2, 1, z, Blocks.OBSIDIAN);
        }
        helper.runAtTickTime(60, () -> {
            helper.assertFalse(helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(1, 1, 2))).isSource(), "Water must not appear from nothing");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void onlyALargeBodyOfWaterFillsItself(GameTestHelper helper)
    {
        final BlockPos hole = new BlockPos(0, 1, 0);
        for (int x = 0; x < 9; x++)
        {
            for (int z = 0; z < 9; z++)
            {
                helper.setBlock(x, 1, z, Blocks.WATER);
            }
        }
        helper.setBlock(hole, Blocks.AIR);
        helper.assertFalse(WaterBodies.isFedByCatchment(helper.getLevel(), helper.absolutePos(hole), Fluids.WATER, WaterBodies.FED_BY_CATCHMENT), "80 m2 of water is a pond");
        helper.assertTrue(WaterBodies.isFedByCatchment(helper.getLevel(), helper.absolutePos(hole), Fluids.WATER, 64), "It has 64 m2 open to the sky");

        for (int x = 0; x < 9; x++)
        {
            for (int z = 0; z < 9; z++)
            {
                helper.setBlock(x, 2, z, Blocks.OBSIDIAN);
            }
        }
        helper.assertFalse(WaterBodies.isFedByCatchment(helper.getLevel(), helper.absolutePos(hole), Fluids.WATER, 1), "Under a roof it has no catchment, however large it is");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void pumpMovesWaterAndDoesNotMakeIt(GameTestHelper helper)
    {
        final BlockPos pump = new BlockPos(2, 2, 2), pipe = new BlockPos(2, 1, 2), intake = new BlockPos(2, 0, 2), outlet = new BlockPos(2, 2, 1);
        helper.setBlock(1, 0, 2, Blocks.OBSIDIAN);
        helper.setBlock(3, 0, 2, Blocks.WATER); // A pool of two blocks, so that the second has to run in to the pipe
        helper.setBlock(4, 0, 2, Blocks.OBSIDIAN);
        helper.setBlock(3, 0, 1, Blocks.OBSIDIAN);
        helper.setBlock(3, 0, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 0, 1, Blocks.OBSIDIAN);
        helper.setBlock(2, 0, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 1, Blocks.OBSIDIAN);
        helper.setBlock(intake, Blocks.WATER);

        final BlockState pumpState = TFCBlocks.STEEL_PUMP.get().defaultBlockState().setValue(FluidPumpBlock.FACING, Direction.NORTH);
        final BlockState pipeState = FluidHelpers.fillWithFluid(TFCBlocks.STEEL_PIPE.get().defaultBlockState().setValue(DirectionPropertyBlock.getProperty(Direction.DOWN), true).setValue(DirectionPropertyBlock.getProperty(Direction.UP), true), Fluids.WATER);
        helper.assertTrue(pipeState != null, "A pipe should hold water");
        helper.setBlock(pipe, pipeState);
        helper.setBlock(pump, pumpState);

        helper.assertTrue(MassPump.moveOneBlock(helper.getLevel(), helper.absolutePos(pump), pumpState), "The pump should find water at the end of its pipe");
        helper.assertFalse(helper.getLevel().getFluidState(helper.absolutePos(intake)).isSource(), "The water must leave the intake");
        helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(outlet)).isSource(), "The water must arrive at the outlet");

        helper.runAtTickTime(30, () -> {
            helper.setBlock(2, 2, 0, Blocks.OBSIDIAN);
            helper.setBlock(1, 2, 1, Blocks.OBSIDIAN);
            helper.setBlock(3, 2, 1, Blocks.OBSIDIAN);
            helper.setBlock(1, 1, 1, Blocks.AIR);
            helper.setBlock(2, 2, 1, Blocks.AIR); // Clearing the outlet: what happens to the first block is not what this checks
            helper.assertTrue(MassPump.moveOneBlock(helper.getLevel(), helper.absolutePos(pump), pumpState), "The rest of the pool must run in and be pumped too");
            helper.assertFalse(helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(3, 0, 2))).isSource(), "The second source must be the one taken");
        });
        helper.runAtTickTime(60, () -> {
            helper.setBlock(2, 2, 1, Blocks.AIR);
            helper.assertFalse(MassPump.moveOneBlock(helper.getLevel(), helper.absolutePos(pump), pumpState), "With the pool dry there is nothing left to pump");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void machinesLoadTheirNetwork(GameTestHelper helper)
    {
        final Block axle = TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.AXLE).get();
        helper.setBlock(1, 1, 2, TFCBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        helper.setBlock(2, 1, 2, axle.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        helper.setBlock(3, 1, 2, Instruments.MINE_FAN.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));

        helper.runAtTickTime(45, () -> {
            final RotatingBlockEntity fan = (RotatingBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 2));
            final long network = fan.getRotationNode().network();
            helper.assertTrue(network != Node.NO_NETWORK, "The fan should join the shaft behind it");
            final float torque = ShaftTracker.get(helper.getLevel()).networkTorque(network);
            helper.assertTrue(torque >= 2 * ShaftPhysics.BEARING_TORQUE, "Shaft and fan should both load the network, got " + torque);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void sourcesTakeTheirSpeedFromTheLoad(GameTestHelper helper)
    {
        helper.setBlock(2, 3, 2, TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.WATER_WHEEL).get().defaultBlockState().setValue(WaterWheelBlock.AXIS, Direction.Axis.X));
        final BlockEntity wheel = helper.getBlockEntity(new BlockPos(2, 3, 2));
        helper.assertTrue(wheel instanceof ShaftSource, "The water wheel mixin should be applied");
        final ShaftSource source = (ShaftSource) wheel;
        source.tfcpp$setSpeedFactor(0.5f);
        helper.assertTrue(source.tfcpp$speedFactor() == 0.5f, "The factor should be kept");
        final CompoundTag saved = wheel.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.getFloat("tfcppSpeedFactor") == 0.5f, "The factor should be saved, and with that synced");

        // The same code runs on both sides, so this is what a shaft on a client does too
        saved.putFloat("targetSpeed", WaterWheelBlockEntity.MAX_SPEED);
        wheel.loadWithComponents(saved, helper.getLevel().registryAccess());
        final WaterWheelBlockEntity water = (WaterWheelBlockEntity) wheel;
        for (int tick = 0; tick < 500; tick++)
        {
            WaterWheelBlockEntity.clientTick(helper.getLevel(), wheel.getBlockPos(), wheel.getBlockState(), water);
        }
        final float speed = water.getRotationNode().rotation().speed();
        helper.assertTrue(Math.abs(speed - 0.5f * WaterWheelBlockEntity.MAX_SPEED) < 1e-3f, "A wheel at half load should settle at half speed, got " + speed + " of " + WaterWheelBlockEntity.MAX_SPEED);
        helper.assertTrue(Math.abs(source.tfcpp$stallTorque() - ShaftPhysics.waterWheelStallTorque(WaterWheelBlockEntity.MAX_SPEED)) < 1e-3f, "Its stall torque comes from the free speed, not the slowed one");
        helper.succeed();
    }

    private static PipeTracker pipes(GameTestHelper helper)
    {
        return PipeTracker.get(helper.getLevel());
    }

    @Nullable
    private static PipeNetwork networkAt(GameTestHelper helper, int x, int y, int z)
    {
        return pipes(helper).networkAt(helper.absolutePos(new BlockPos(x, y, z)));
    }

    private static Block ironPipe()
    {
        return Pipes.PIPES.get(PipeMaterial.WROUGHT_IRON).get();
    }

    private static BlockState fitting(Supplier<Block> block, Direction facing, boolean open)
    {
        return block.get().defaultBlockState().setValue(PipeFittingBlock.FACING, facing).setValue(PipeFittingBlock.OPEN, open);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void pipesJoinAndAClosedValveParts(GameTestHelper helper)
    {
        helper.setBlock(1, 1, 2, ironPipe());
        helper.setBlock(2, 1, 2, fitting(Pipes.VALVE, Direction.EAST, true));
        helper.setBlock(3, 1, 2, ironPipe());

        helper.runAtTickTime(5, () -> {
            final PipeNetwork joined = networkAt(helper, 1, 1, 2);
            helper.assertTrue(joined != null && joined == networkAt(helper, 3, 1, 2), "An open valve should join the two pipes");
            helper.assertTrue(Math.abs(joined.pressure() - 1f) < 0.01f, "New pipe is full of air at 1 atm, got " + joined.pressure());
            joined.contents().air *= 2f;
        });
        helper.runAtTickTime(10, () -> {
            final BlockPos valve = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.getLevel().setBlockAndUpdate(valve, fitting(Pipes.VALVE, Direction.EAST, false));
            pipes(helper).onConnectionChanged(helper.getLevel(), valve);
        });
        helper.runAtTickTime(15, () -> {
            final PipeNetwork left = networkAt(helper, 1, 1, 2), right = networkAt(helper, 3, 1, 2);
            helper.assertTrue(left != null && right != null && left != right, "A closed valve should part them");
            helper.assertTrue(Math.abs(left.pressure() - 2f) < 0.05f && Math.abs(right.pressure() - 2f) < 0.05f, "Each side keeps the pressure it had");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aVentLetsPressureOutIntoTheRoom(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(2, 1, 2, fitting(Pipes.VENT, Direction.SOUTH, true));

        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = networkAt(helper, 2, 1, 1);
            helper.assertTrue(network != null, "The vessel and vent should form a network");
            network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] = 40f;
        });
        helper.runAtTickTime(70, () -> {
            final PipeNetwork network = networkAt(helper, 2, 1, 1);
            helper.assertTrue(network.pressure() < 1.2f, "The vessel should have blown down, it is at " + network.pressure());
            final float total = totalInBox(helper, 3, 2, Gas.SULFUR_DIOXIDE) + network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()];
            helper.assertTrue(Math.abs(total - 40f) < 0.5f, "The gas must all still be somewhere, found " + total);
            helper.assertTrue(totalInBox(helper, 3, 2, Gas.SULFUR_DIOXIDE) > 10f, "Some of it should be in the room");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aVesselBurstsAndEverythingComesOut(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 2, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(1, 1, 1, Blocks.GLASS);

        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = networkAt(helper, 2, 1, 2);
            helper.assertTrue(network != null, "A vessel is a network by itself");
            network.contents().gases[Gas.CARBON_DIOXIDE.ordinal()] = PipePhysics.moles(15f, 293f, 0.5f);
        });
        helper.runAtTickTime(40, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 2));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 1, 1));
            final float released = totalInBox(helper, 3, 2, Gas.CARBON_DIOXIDE);
            helper.assertTrue(Math.abs(released - PipePhysics.moles(15f, 293f, 0.5f)) < 1f, "All the gas should be in the room, found " + released);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aSafetyValveKeepsAVesselFromBursting(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(2, 1, 2, fitting(Pipes.SAFETY_VALVE, Direction.SOUTH, false));

        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = networkAt(helper, 2, 1, 1);
            helper.assertTrue(network != null, "The vessel and valve should form a network");
            network.contents().air = PipePhysics.moles(9.5f, 293f, network.volume());
        });
        helper.runAtTickTime(60, () -> {
            final PipeNetwork network = networkAt(helper, 2, 1, 1);
            helper.assertTrue(network != null, "The vessel must not have burst");
            final float gauge = network.pressure() - 1f;
            helper.assertTrue(gauge < 7.5f && gauge > 5f, "The valve should have let it down to where it seats, near 7 atm, got " + gauge);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aVentLetsGasOutAndKeepsTheWater(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(2, 1, 2, fitting(Pipes.VENT, Direction.SOUTH, true));

        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = networkAt(helper, 2, 1, 1);
            network.contents().liquid = "minecraft:water";
            network.contents().litres = 100f;
            network.contents().air = PipePhysics.moles(3f, 293f, 0.5f);
        });
        helper.runAtTickTime(60, () -> {
            final PipeNetwork network = networkAt(helper, 2, 1, 1);
            helper.assertTrue(network.pressure() < 1.2f, "The gas should be out, it is at " + network.pressure());
            helper.assertTrue(Math.abs(network.contents().litres - 100f) < 0.01f, "All the water should still be there, found " + network.contents().litres);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aSafetyValveLetsSteamOutOfABoiler(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(2, 1, 2, fitting(Pipes.SAFETY_VALVE, Direction.SOUTH, false));

        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = networkAt(helper, 2, 1, 1);
            network.contents().liquid = "minecraft:water";
            network.contents().litres = 100f;
            network.contents().air = 0f;
            network.contents().temperature = 455f; // About 10 atm of steam, above where the valve lifts and below where the joints go
        });
        helper.runAtTickTime(60, () -> {
            final PipeNetwork network = networkAt(helper, 2, 1, 1);
            helper.assertTrue(network != null, "The boiler must not have burst");
            helper.assertTrue(network.contents().litres < 100f, "It should have lost water as steam");
            helper.assertTrue(network.contents().temperature < 455f, "Boiling should have cooled it, it is at " + network.contents().temperature);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void aChunkThatLoadsLaterJoinsTheNetworkBesideIt(GameTestHelper helper)
    {
        helper.setBlock(1, 1, 1, ironPipe());
        helper.setBlock(2, 1, 1, ironPipe());

        helper.runAtTickTime(5, () -> {
            final PipeSegment far = pipes(helper).segmentAt(helper.absolutePos(new BlockPos(2, 1, 1)));
            final PipeNetwork network = networkAt(helper, 1, 1, 1);
            helper.assertTrue(network == far.network, "The two should have joined");

            // The state a chunk load leaves things in: one part already a network, and a segment beside it waiting
            pipes(helper).onConnectionChanged(helper.getLevel(), far.pos);
        });
        helper.runAtTickTime(10, () -> {
            final PipeNetwork network = networkAt(helper, 1, 1, 1);
            helper.assertTrue(network != null && network == networkAt(helper, 2, 1, 1), "They should form one network again");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void aManometerIsBlownOutByTooMuchPressure(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(2, 1, 2, Pipes.MANOMETER.get());

        helper.runAtTickTime(5, () -> networkAt(helper, 2, 1, 1).contents().air = PipePhysics.moles(5f, 293f, 0.5f));
        helper.runAtTickTime(40, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 2));
            helper.assertTrue(totalInBox(helper, 3, 2, Gas.MERCURY_VAPOR) > 0f, "The mercury should be in the room");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void pistonPumpCompressesAirAndLoadsTheShaft(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 2, Pipes.PISTON_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());

        helper.runAtTickTime(5, () -> {
            final PistonPumpBlockEntity pump = (PistonPumpBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
            final PipeNetwork vessel = networkAt(helper, 2, 1, 1);
            helper.assertTrue(vessel != null, "The vessel should be a network");
            final float before = pump.pump(helper.getLevel(), 0.5f);
            helper.assertTrue(Math.abs(vessel.pressure() - 2f) < 0.05f, "Half a cubic metre of air into half a cubic metre should give 2 atm, got " + vessel.pressure());
            final float after = pump.pump(helper.getLevel(), 0.5f);
            helper.assertTrue(after > before + 0.2f, "The pump should work harder against the pressure: " + before + " then " + after);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void pistonPumpTakesWaterAwayAndTapPutsItInABarrel(GameTestHelper helper)
    {
        helper.setBlock(3, 0, 2, Blocks.WATER);
        helper.setBlock(3, 1, 2, Pipes.PISTON_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(3, 1, 3, Blocks.WATER);
        helper.setBlock(3, 0, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(4, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(3, 1, 4, Blocks.OBSIDIAN);
        helper.setBlock(3, 1, 1, ironPipe());
        // TFC's barrel only takes liquid in from the top
        helper.setBlock(3, 2, 1, ironPipe());
        helper.setBlock(2, 2, 1, ironPipe());
        helper.setBlock(2, 1, 1, fitting(Pipes.TAP, Direction.DOWN, true));
        helper.setBlock(2, 0, 1, TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.BARREL).get());

        helper.runAtTickTime(5, () -> {
            final PistonPumpBlockEntity pump = (PistonPumpBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 2));
            pump.pump(helper.getLevel(), 0.002f);
            helper.assertFalse(helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(3, 1, 3))).isSource(), "The source block must be gone");
            final PipeNetwork network = networkAt(helper, 3, 1, 1);
            helper.assertTrue(network != null && network.contents().litres > 1.5f, "The water should be in the pipe");
            helper.assertTrue(network.contents().liquid.equals("minecraft:water"), "and it should be water");
        });
        helper.runAtTickTime(30, () -> {
            final IFluidHandler barrel = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(new BlockPos(2, 0, 1)), Direction.UP);
            helper.assertTrue(barrel != null, "The barrel should hold liquid");
            helper.assertTrue(barrel.getFluidInTank(0).getAmount() >= 1, "The tap should have let water into the barrel, it holds " + barrel.getFluidInTank(0).getAmount());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 30)
    public static void pipesKeepTheirShareThroughSavingAndLoading(GameTestHelper helper)
    {
        helper.setBlock(1, 1, 1, ironPipe());
        helper.setBlock(2, 1, 1, ironPipe());

        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = networkAt(helper, 1, 1, 1);
            network.contents().gases[Gas.METHANE.ordinal()] = 3f;

            // The two pipes can fall on either side of a chunk border, wherever the test happens to be placed
            final LevelChunk first = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
            final LevelChunk second = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(2, 1, 1)));
            float total = 0f;
            for (LevelChunk chunk : first == second ? List.of(first) : List.of(first, second))
            {
                final PipeChunkData loaded = new PipeChunkData();
                loaded.load(chunk.getData(GasAttachments.PIPE_CHUNK).save());
                for (PipeSegment segment : loaded.segments())
                {
                    total += segment.stored.gases[Gas.METHANE.ordinal()];
                }
            }
            helper.assertTrue(Math.abs(total - 3f) < 0.01f, "The saved segments should hold all of it, not more or less, got " + total);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pipeRecipesLoad(GameTestHelper helper)
    {
        for (String recipe : List.of("pipe/wood", "pipe/ceramic", "pipe/copper", "pipe/wrought_iron", "pipe/steel", "pipe_valve", "pipe_vent", "pipe_tap", "safety_valve", "mercury_manometer", "piston_pump", "pressure_vessel"))
        {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(TFCpp.id("crafting/" + recipe)).isPresent(), "Missing recipe " + recipe);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void miningCoalLetsGasOut(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.OBSIDIAN);
        // The surface is taken from columns around the position, so this puts about 30 m of rock over the seam
        helper.setBlock(10, 32, 2, Blocks.STONE);

        final BlockPos coal = new BlockPos(2, 1, 2);
        final BlockPos neighbour = new BlockPos(2, 2, 2);
        helper.setBlock(coal, TFCBlocks.BITUMINOUS_COAL.get());
        helper.setBlock(neighbour, TFCBlocks.BITUMINOUS_COAL.get());

        final BlockPos absolute = helper.absolutePos(coal);
        final float expected = SeamGas.fastMoles(SeamGas.Rank.BITUMINOUS, SeamGas.depth(helper.getLevel(), absolute));
        helper.assertTrue(expected > 5f, "The test seam should be deep enough to hold gas, got " + expected);

        final TestPlayer player = survivalPlayerAt(helper, 1, 1, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TFCItems.METAL_ITEMS.get(Metal.BRONZE).get(Metal.ItemType.PICKAXE).get()));
        helper.assertTrue(player.gameMode.destroyBlock(absolute), "The block should break");
        final float released = totalInBox(helper, 3, 3, Gas.METHANE);
        helper.assertTrue(released >= expected * 0.9f, "Breaking coal should release its fast gas: " + released + " of " + expected);

        helper.runAtTickTime(100, () -> {
            helper.assertTrue(totalInBox(helper, 3, 3, Gas.METHANE) > released, "The exposed face should keep bleeding");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void bafflesCatchDustWithoutLosingIt(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 2, Instruments.SETTLING_BAFFLE.get());
        addGas(helper, 2, 2, 2, Gas.ARSENIC_TRIOXIDE, 1f);

        helper.runAtTickTime(160, () -> {
            final SettlingBaffleBlockEntity baffle = (SettlingBaffleBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
            final float caught = baffle.collected(Gas.ARSENIC_TRIOXIDE);
            helper.assertTrue(caught > 0.1f, "The plates should catch dust from the air: " + caught);

            final float total = caught + totalInBox(helper, 3, 3, Gas.ARSENIC_TRIOXIDE) + helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(2, 2, 2))).getData(GasAttachments.CHUNK).deposit(Gas.ARSENIC_TRIOXIDE);
            helper.assertTrue(total < 1.01f, "Catching dust must not create any: " + total);

            final TestPlayer player = survivalPlayerAt(helper, 1, 1, 1);
            baffle.scrape(player);
            final int powder = player.getInventory().countItem(Instruments.ARSENIC_TRIOXIDE.get());
            helper.assertTrue(powder == (int) (caught / (SettlingBaffleBlockEntity.MOLES_PER_POWDER * (1 + SettlingBaffleBlockEntity.STIRRED_UP))), "Scraping should give whole items of powder, and pay for what it stirs up, got " + powder);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void sentinelDiesInPoisonedAir(GameTestHelper helper)
    {
        final TestPlayer player = survivalPlayerAt(helper, 2, 1, 2);
        fillMouth(helper, player, Gas.CARBON_MONOXIDE, 0.2f); // About 4800 ppm, which takes a person some minutes to feel
        player.getInventory().setItem(0, SentinelCageItem.withAnimal());

        for (int second = 0; second < 60; second++)
        {
            final ItemStack cage = player.getInventory().getItem(0);
            cage.getItem().inventoryTick(cage, helper.getLevel(), player, 0, false);
        }

        helper.assertTrue(player.getInventory().getItem(0).is(Instruments.ANIMAL_CAGE.get()), "The animal should be dead after a minute, leaving the cage");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void instrumentRecipesLoad(GameTestHelper helper)
    {
        for (String recipe : List.of("wire_gauze", "davy_lamp", "animal_cage", "settling_baffle"))
        {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(TFCpp.id("crafting/" + recipe)).isPresent(), "Missing recipe " + recipe);
        }
        helper.succeed();
    }

    private static void fillMouth(GameTestHelper helper, ServerPlayer player, Gas gas, float moles)
    {
        final BlockPos mouth = BlockPos.containing(player.getEyePosition());
        final float[] cell = volume(helper).createCell(mouth.getX(), mouth.getY(), mouth.getZ());
        helper.assertTrue(cell != null, "Could not create a gas cell");
        cell[gas.ordinal()] += moles;
    }

    private static TestPlayer survivalPlayerAt(GameTestHelper helper, int x, int y, int z)
    {
        final TestPlayer player = new TestPlayer(helper);
        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    /** Not a mock player, as joining one would make TFC sync data over a connection that does not exist. */
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

    private GasGameTests() {}
}
