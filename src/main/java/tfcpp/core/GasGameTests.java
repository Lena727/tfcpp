package tfcpp.core;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
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
        addGas(helper, 2, 4, 2, Gas.SULFUR_DIOXIDE, 20f);

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(Math.abs(totalInBox(helper, 3, 4, Gas.SULFUR_DIOXIDE) - 20f) < 0.1f, "SO2 must be conserved in a sealed room");

            final float[] floor = cell(helper, 2, 1, 2), roof = cell(helper, 2, 4, 2);
            helper.assertTrue(floor != null && roof != null && floor[Gas.SULFUR_DIOXIDE.ordinal()] > roof[Gas.SULFUR_DIOXIDE.ordinal()], "SO2 must collect at the floor");
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
