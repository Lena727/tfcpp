package tfcpp.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity;
import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.items.Powder;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.common.recipes.BlastFurnaceRecipe;
import net.dries007.tfc.common.recipes.BloomeryRecipe;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.common.recipes.InstantBarrelRecipe;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.data.FluidHeat;
import net.dries007.tfc.util.data.Fertilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.metallurgy.ConverterBath;
import tfcpp.metallurgy.ConverterBlockEntity;
import tfcpp.metallurgy.IronSmelting;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePhysics;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.Pipes;

/** Stage B of v1.0: phosphorus in the ore, the iron that carries it, and the basic converter that takes it out. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhosphorusGameTests
{
    private static final String TEMPLATE = "empty9";
    /** Apart from the others, as blast furnaces and converters fill the air and a batch shares the gas grid's budget for each tick. */
    private static final String BATCH = "tfcpp_phosphorus";
    private static final float GRAMS_PER_MOL_PHOSPHORUS = 30.974f;

    private static FakePlayer player(GameTestHelper helper)
    {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
    }

    private static Fluid phosphoricPig()
    {
        return Metallurgy.PHOSPHORIC_PIG_IRON.get();
    }

    private static Fluid pig()
    {
        return IronGameTests.metal(Metal.PIG_IRON);
    }

    private static Item limonite()
    {
        return IronGameTests.ore(Ore.LIMONITE, Ore.Grade.RICH);
    }

    private static Item hematite()
    {
        return IronGameTests.ore(Ore.HEMATITE, Ore.Grade.RICH);
    }

    private static Item lime()
    {
        return TFCItems.POWDERS.get(Powder.LIME).asItem();
    }

    private static float phosphorusGrams(ConverterBath bath)
    {
        return bath.metal * ConverterBath.KG_PER_MILLIBUCKET * 1000f * bath.phosphorus / 100f;
    }

    private static void lay(ConverterBlockEntity converter, FakePlayer player, Item item, int count)
    {
        final ItemStack stack = new ItemStack(item, count);
        for (int i = 0; i < count; i++)
        {
            converter.interact(stack, player, InteractionHand.MAIN_HAND);
        }
    }

    /** Lays eight basic bricks and throws in this much lime, into an empty vessel. */
    private static void lineBasic(ConverterBlockEntity converter, FakePlayer player, int limePowders)
    {
        lay(converter, player, Metallurgy.BASIC_BRICK.get(), 8);
        lay(converter, player, lime(), limePowders);
    }

    private static PipeNetwork networkAt(GameTestHelper helper)
    {
        final PipeNetwork network = PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(new BlockPos(3, 1, 4)));
        helper.assertTrue(network != null, "The vessel and converter should form a network");
        return network;
    }

    /** A converter holding this iron, with an air receiver beside it. */
    private static ConverterBlockEntity converterWithAir(GameTestHelper helper, Fluid iron, int millibuckets, float atmospheres)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, Metallurgy.CONVERTER.get());
        helper.setBlock(3, 1, 4, Pipes.PRESSURE_VESSEL.get());
        final ConverterBlockEntity converter = helper.getBlockEntity(pos);
        if (millibuckets > 0)
        {
            converter.tank().fill(new FluidStack(iron, millibuckets), IFluidHandler.FluidAction.EXECUTE);
        }
        converter.setTemperature(0f);
        helper.runAtTickTime(3, () -> networkAt(helper).contents().air = PipePhysics.moles(atmospheres, 293f, networkAt(helper).volume()));
        return converter;
    }

    // The blast furnace

    /** The furnace above a converter, as in a real works, with a charge of ore. */
    private static void furnaceOverConverter(GameTestHelper helper, List<Item> ores)
    {
        final BlockPos converterPos = new BlockPos(4, 1, 4), furnacePos = converterPos.above();
        helper.setBlock(converterPos, Metallurgy.CONVERTER.get());
        helper.setBlock(furnacePos, TFCBlocks.BLAST_FURNACE.get());
        helper.runAfterDelay(IronGameTests.ticksPastASecond(helper), () -> {
            final BlastFurnaceBlockEntity furnace = helper.getBlockEntity(furnacePos);
            final List<ItemStack> charge = new ArrayList<>();
            ores.forEach(ore -> charge.add(new ItemStack(ore)));
            try
            {
                IronGameTests.loadBlastFurnace(furnace, charge);
            }
            catch (ReflectiveOperationException e)
            {
                helper.fail("Could not load the furnace: " + e);
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = BATCH)
    public static void aBrownOreFurnaceGivesPhosphoricPigIron(GameTestHelper helper)
    {
        furnaceOverConverter(helper, List.of(limonite(), limonite()));
        helper.runAfterDelay(110, () -> {
            final ConverterBath bath = ((ConverterBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4))).bath();
            helper.assertTrue(bath.metal >= 65f, "Two pieces of rich ore should give 70 mB, got " + bath.metal);
            helper.assertTrue(Math.abs(bath.phosphorus - 1.5f) < 0.01f, "It should hold the 1.5% phosphorus of phosphoric pig iron, got " + bath.phosphorus);
            helper.assertTrue(bath.tapGrade() == ConverterBath.Grade.PHOSPHORIC_PIG_IRON, "It should tap as phosphoric pig iron, got " + bath.tapGrade());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = BATCH)
    public static void aHematiteFurnaceStillGivesOrdinaryPigIron(GameTestHelper helper)
    {
        furnaceOverConverter(helper, List.of(hematite(), hematite()));
        helper.runAfterDelay(110, () -> {
            final ConverterBath bath = ((ConverterBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4))).bath();
            helper.assertTrue(bath.metal >= 65f, "Two pieces of rich ore should give 70 mB, got " + bath.metal);
            helper.assertTrue(bath.phosphorus == 0f, "Hematite is poor in phosphorus, got " + bath.phosphorus);
            helper.assertTrue(bath.tapGrade() == ConverterBath.Grade.PIG_IRON, "It should tap as pig iron, got " + bath.tapGrade());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = BATCH)
    public static void aMixedChargeMakesWholeBathPhosphoric(GameTestHelper helper)
    {
        furnaceOverConverter(helper, List.of(hematite(), limonite(), hematite(), limonite()));
        helper.runAfterDelay(110, () -> {
            final ConverterBath bath = ((ConverterBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4))).bath();
            helper.assertTrue(bath.metal >= 60f, "A charge of four pieces should have run in, got " + bath.metal);
            helper.assertTrue(Math.abs(bath.phosphorus - 1.5f) < 0.01f, "All of it counts as phosphoric, not a half, got " + bath.phosphorus + "%");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void eitherOreIsAcceptedIntoEitherRecipe(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        final BlastFurnaceRecipe plain = (BlastFurnaceRecipe) recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "blast_furnace/pig_iron")).orElseThrow().value();
        final BlastFurnaceRecipe phosphoric = (BlastFurnaceRecipe) recipes.byKey(TFCpp.id("blast_furnace/phosphoric_pig_iron")).orElseThrow().value();
        for (BlastFurnaceRecipe recipe : List.of(plain, phosphoric))
        {
            helper.assertTrue(recipe.matchesInput(new ItemStack(limonite())), "Brown ore must be taken into " + recipe.outputFluid().getFluid());
            helper.assertTrue(recipe.matchesInput(new ItemStack(hematite())), "Hematite must be taken into " + recipe.outputFluid().getFluid());
            helper.assertFalse(recipe.matchesInput(new ItemStack(IronGameTests.ore(Ore.NATIVE_COPPER, Ore.Grade.RICH))), "Copper has nothing to do in a furnace of iron");
            helper.assertFalse(recipe.matchesInput(new ItemStack(Items.STICK)), "Neither has a stick");
        }
        helper.assertTrue(plain.outputFluid().getFluid() == pig() && phosphoric.outputFluid().getFluid() == phosphoricPig(), "Each recipe still gives its own iron");

        final BloomeryRecipe bloom = (BloomeryRecipe) recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "bloomery/raw_iron_bloom")).orElseThrow().value();
        helper.assertTrue(bloom.consumeInput(new ItemStack(limonite())) != null && bloom.consumeInput(new ItemStack(hematite())) != null, "A bloomery takes either ore into one bloom");
        helper.assertTrue(bloom.consumeInput(new ItemStack(IronGameTests.ore(Ore.NATIVE_COPPER, Ore.Grade.RICH))) == null, "And not copper");
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static FluidTank outputTank(BlastFurnaceBlockEntity furnace) throws ReflectiveOperationException
    {
        final Field tank = BlastFurnaceBlockEntity.class.getDeclaredField("outputFluidTank");
        tank.setAccessible(true);
        return (FluidTank) tank.get(furnace);
    }

    private static FluidStack inputFluid(BlastFurnaceBlockEntity furnace) throws ReflectiveOperationException
    {
        final Field fluid = BlastFurnaceBlockEntity.class.getDeclaredField("inputFluid");
        fluid.setAccessible(true);
        return (FluidStack) fluid.get(furnace);
    }

    /** A furnace with nothing below it, so the iron stays in its tank where it can be read. */
    private static BlastFurnaceBlockEntity bareFurnace(GameTestHelper helper, BlockPos pos, @Nullable FluidStack tankHolds, List<Item> ores)
    {
        helper.setBlock(pos, TFCBlocks.BLAST_FURNACE.get());
        final BlastFurnaceBlockEntity furnace = helper.getBlockEntity(pos);
        helper.runAfterDelay(IronGameTests.ticksPastASecond(helper), () -> {
            try
            {
                if (tankHolds != null)
                {
                    outputTank(furnace).fill(tankHolds, IFluidHandler.FluidAction.EXECUTE);
                }
                final List<ItemStack> charge = new ArrayList<>();
                ores.forEach(ore -> charge.add(new ItemStack(ore)));
                IronGameTests.loadBlastFurnace(furnace, charge);
            }
            catch (ReflectiveOperationException e)
            {
                helper.fail("Could not load the furnace: " + e);
            }
        });
        return furnace;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void twoOxidesMeltingInOneTickAreOneBathAndNothingIsLost(GameTestHelper helper)
    {
        final BlastFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(4, 1, 4), null, List.of(hematite(), limonite()));
        helper.runAfterDelay(IronGameTests.ticksPastASecond(helper) + 8, () -> {
            try
            {
                final FluidStack tank = outputTank(furnace).getFluid();
                helper.assertTrue(tank.getFluid() == phosphoricPig(), "The merged bath is phosphoric, got " + tank.getFluid());
                helper.assertTrue(tank.getAmount() == 70, "Both pieces of 35 mB must be in it, as TFC threw one away, got " + tank.getAmount());
                helper.assertTrue(inputFluid(furnace).isEmpty(), "Nothing should be left unconverted");
            }
            catch (ReflectiveOperationException e)
            {
                helper.fail(e.toString());
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void aPhosphoricMeltIsNotVoidedByPigIronAlreadyInTheTank(GameTestHelper helper)
    {
        final BlastFurnaceBlockEntity brownOnPig = bareFurnace(helper, new BlockPos(2, 1, 2), new FluidStack(pig(), 50), List.of(limonite()));
        final BlastFurnaceBlockEntity plainOnPhosphoric = bareFurnace(helper, new BlockPos(6, 1, 6), new FluidStack(phosphoricPig(), 50), List.of(hematite()));
        helper.runAfterDelay(IronGameTests.ticksPastASecond(helper) + 8, () -> {
            try
            {
                final FluidStack first = outputTank(brownOnPig).getFluid(), second = outputTank(plainOnPhosphoric).getFluid();
                helper.assertTrue(first.getFluid() == phosphoricPig() && first.getAmount() == 85, "Pig iron with a brown ore's iron is one phosphoric bath of 85 mB, got " + first.getAmount() + " of " + first.getFluid());
                helper.assertTrue(second.getFluid() == phosphoricPig() && second.getAmount() == 85, "Phosphoric iron taking in plain iron stays phosphoric and keeps all of it, got " + second.getAmount() + " of " + second.getFluid());
            }
            catch (ReflectiveOperationException e)
            {
                helper.fail(e.toString());
            }
            helper.succeed();
        });
    }

    // The bloomery

    private static int[] slagFrom(GameTestHelper helper, BloomeryBlockEntity bloomery, BloomeryRecipe recipe, List<Item> ores)
    {
        final int ordinaryBefore = IronGameTests.itemsAroundAbsolute(helper, bloomery.getExternalBlock(), Metallurgy.IRON_SLAG.get());
        final int phosphoricBefore = IronGameTests.itemsAroundAbsolute(helper, bloomery.getExternalBlock(), Metallurgy.PHOSPHORIC_IRON_SLAG.get());
        final List<ItemStack> charge = new ArrayList<>();
        ores.forEach(ore -> charge.add(new ItemStack(ore)));
        for (int i = 0; i < 4; i++)
        {
            charge.add(new ItemStack(Items.CHARCOAL));
        }
        IronSmelting.bloomeryFinished(bloomery, charge, recipe);
        return new int[] {IronGameTests.itemsAroundAbsolute(helper, bloomery.getExternalBlock(), Metallurgy.IRON_SLAG.get()) - ordinaryBefore,
            IronGameTests.itemsAroundAbsolute(helper, bloomery.getExternalBlock(), Metallurgy.PHOSPHORIC_IRON_SLAG.get()) - phosphoricBefore};
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void brownOreCannotWashItsPhosphorusOutThroughSlag(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, TFCBlocks.BLOOMERY.get().defaultBlockState());
        final BloomeryBlockEntity bloomery = helper.getBlockEntity(pos);
        final BloomeryRecipe recipe = (BloomeryRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("tfc", "bloomery/raw_iron_bloom")).orElseThrow().value();

        // 9 pieces of rich ore are 315 mB of iron: two blooms of 100 mB take 200 of it, and 115 mB is 4 pieces of slag
        final int[] hematiteOnly = slagFrom(helper, bloomery, recipe, List.of(hematite(), hematite(), hematite(), hematite(), hematite(), hematite(), hematite(), hematite(), hematite()));
        helper.assertTrue(hematiteOnly[0] == 4 && hematiteOnly[1] == 0, "Hematite leaves ordinary slag, got " + hematiteOnly[0] + " and " + hematiteOnly[1]);
        final int[] brownOnly = slagFrom(helper, bloomery, recipe, List.of(limonite(), limonite(), limonite(), limonite(), limonite(), limonite(), limonite(), limonite(), limonite()));
        helper.assertTrue(brownOnly[0] == 0 && brownOnly[1] == 4, "Brown ore leaves phosphoric slag, got " + brownOnly[0] + " and " + brownOnly[1]);
        final int[] mixed = slagFrom(helper, bloomery, recipe, List.of(hematite(), hematite(), hematite(), hematite(), hematite(), limonite(), limonite(), limonite(), limonite()));
        helper.assertTrue(mixed[0] == 0 && mixed[1] == 4, "Any brown ore in the charge makes all the slag phosphoric, got " + mixed[0] + " and " + mixed[1]);

        // And it melts back to what it came from, 25 mB for a piece
        final ItemStack slag = new ItemStack(Metallurgy.PHOSPHORIC_IRON_SLAG.get());
        final @Nullable HeatingRecipe melt = HeatingRecipe.getRecipe(slag);
        helper.assertTrue(melt != null, "Phosphoric slag should melt");
        final FluidStack fluid = melt.assembleFluid(slag);
        helper.assertTrue(fluid.getFluid() == Metallurgy.PHOSPHORIC_IRON_OXIDE.get() && fluid.getAmount() == IronSmelting.MILLIBUCKETS_PER_SLAG, "It should melt to 25 mB of phosphoric oxide, got " + fluid.getAmount() + " of " + fluid.getFluid());
        helper.assertTrue(IronSmelting.slagOf(true) == Metallurgy.PHOSPHORIC_IRON_SLAG.get() && IronSmelting.slagOf(false) == Metallurgy.IRON_SLAG.get(), "Slag of each kind");
        helper.succeed();
    }

    // The converter

    private static Fluid tappedFluid(GameTestHelper helper, ConverterBlockEntity converter)
    {
        final FakePlayer player = player(helper);
        final ItemStack mold = new ItemStack(TFCItems.MOLDS.get(Metal.ItemType.INGOT).asItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, mold);
        converter.interact(mold, player, InteractionHand.MAIN_HAND);
        final @Nullable var handler = player.getItemInHand(InteractionHand.MAIN_HAND).getCapability(Capabilities.FluidHandler.ITEM);
        helper.assertTrue(handler != null, "The mould should hold a fluid capability");
        return handler.getFluidInTank(0).getFluid();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void anAcidVesselTapsPhosphoricIronAsColdShortIron(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(2, 1, 2), other = new BlockPos(6, 1, 6);
        helper.setBlock(pos, Metallurgy.CONVERTER.get());
        helper.setBlock(other, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity blown = helper.getBlockEntity(pos), raw = helper.getBlockEntity(other);
        for (ConverterBlockEntity converter : List.of(blown, raw))
        {
            helper.assertFalse(converter.bath().basic, "A new vessel is lined with silica");
            converter.tank().fill(new FluidStack(phosphoricPig(), 200), IFluidHandler.FluidAction.EXECUTE);
            converter.bath().temperature = 1600f;
        }
        blown.bath().silicon = 0f;
        blown.bath().carbon = 0.1f; // As it is after a blow
        helper.assertTrue(tappedFluid(helper, blown) == Metallurgy.COLD_SHORT_IRON.get(), "The blown iron still holds its phosphorus, and is cold short");
        helper.assertTrue(tappedFluid(helper, raw) == phosphoricPig(), "Unblown iron with its carbon is still phosphoric pig iron and can be charged again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = BATCH)
    public static void blowingInAnAcidVesselKeepsEveryGramOfPhosphorus(GameTestHelper helper)
    {
        final ConverterBlockEntity converter = converterWithAir(helper, phosphoricPig(), 1000, 3f);
        converter.bath().silicon = 0f;
        converter.bath().carbon = 0.05f;
        converter.bath().temperature = 1600f;
        final float before = phosphorusGrams(converter.bath());
        helper.runAtTickTime(160, () -> {
            final ConverterBath bath = converter.bath();
            helper.assertTrue(bath.metal < 999f, "The blast should have burned something, metal is " + bath.metal);
            helper.assertTrue(Math.abs(before - phosphorusGrams(bath)) < 0.05f, "No phosphorus can leave an acid lining: " + before + " g before, " + phosphorusGrams(bath) + " g after");
            helper.assertTrue(bath.thomasSlagPhosphorus == 0f && bath.tapGrade() == ConverterBath.Grade.COLD_SHORT_IRON, "It is cold short iron, got " + bath.tapGrade());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = BATCH)
    public static void aBasicVesselWithLimeMakesCleanIronAndThomasSlag(GameTestHelper helper)
    {
        final ConverterBlockEntity converter = converterWithAir(helper, phosphoricPig(), 0, 3f);
        final FakePlayer player = player(helper);
        lineBasic(converter, player, 3);
        helper.assertTrue(converter.bath().basic && converter.bath().lime() == 3 * ConverterBath.LIME_MOLES_PER_POWDER, "Eight basic bricks and three powders of lime, got lime " + converter.bath().lime());
        converter.tank().fill(new FluidStack(phosphoricPig(), 1000), IFluidHandler.FluidAction.EXECUTE);
        converter.setTemperature(0f); // Settles the heat of the iron that has just arrived, which would otherwise be taken as cold
        final ConverterBath bath = converter.bath();
        bath.silicon = 0f;
        bath.carbon = 0.05f; // The blow has taken the carbon already
        bath.temperature = 1600f;
        final float before = phosphorusGrams(bath);

        helper.runAtTickTime(280, () -> {
            helper.assertTrue(bath.phosphorus < ConverterBath.PHOSPHORUS_LIMIT_PERCENT, "The afterblow should have taken the phosphorus below 0.1%, left " + bath.phosphorus);
            helper.assertTrue(bath.tapGrade().phosphorus() == 0f, "It should tap as clean iron, got " + bath.tapGrade());
            final int pieces = IronGameTests.itemsAround(helper, new BlockPos(4, 2, 4), Metallurgy.THOMAS_SLAG.get());
            final float moles = (before - phosphorusGrams(bath)) / GRAMS_PER_MOL_PHOSPHORUS;
            helper.assertTrue(pieces >= 1, "Two moles of phosphorus make a piece of Thomas slag, and " + moles + " mol burned, got " + pieces);
            helper.assertTrue(Math.abs(pieces * 2f + bath.thomasSlagPhosphorus - moles) < 0.15f, "Every mole must be in the slag: " + moles + " mol left the iron, " + pieces + " pieces and " + bath.thomasSlagPhosphorus + " mol kept");
            helper.assertTrue(bath.thomasSlagPhosphorus < 2f, "Whole pieces are ejected and the rest is kept, got " + bath.thomasSlagPhosphorus);
            helper.assertTrue(bath.silicaWithoutLime == 0f, "There was no silicon, so no silica");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = BATCH)
    public static void silicaWithNoLimeEatsABasicLining(GameTestHelper helper)
    {
        final ConverterBlockEntity converter = converterWithAir(helper, phosphoricPig(), 0, 3f);
        final FakePlayer player = player(helper);
        lineBasic(converter, player, 0);
        converter.tank().fill(new FluidStack(phosphoricPig(), 1000), IFluidHandler.FluidAction.EXECUTE);
        converter.setTemperature(0f);
        converter.bath().temperature = 1600f; // With the 1% silicon of pig iron and no lime
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(converter.bath().silicon < 1f, "The silicon should have started to burn, got " + converter.bath().silicon);
            helper.assertTrue(converter.liningWorn() > 60f, "Each mole of silica with no lime takes 60 seconds of lining, got " + converter.liningWorn());
            helper.assertTrue(converter.bath().silicaWithoutLime == 0f, "It is reset after each blow, got " + converter.bath().silicaWithoutLime);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void slagSkimmedFromAPhosphoricBathIsPhosphoric(GameTestHelper helper)
    {
        // Apart enough that each one's slag is not counted at the other
        final BlockPos phosphoricPos = new BlockPos(1, 1, 1), plainPos = new BlockPos(7, 1, 7);
        helper.setBlock(phosphoricPos, Metallurgy.CONVERTER.get());
        helper.setBlock(plainPos, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity phosphoric = helper.getBlockEntity(phosphoricPos), plain = helper.getBlockEntity(plainPos);
        phosphoric.bath().phosphorus = 1.5f;
        phosphoric.bath().slag = IronSmelting.MILLIBUCKETS_PER_SLAG; // One piece
        plain.bath().slag = IronSmelting.MILLIBUCKETS_PER_SLAG;
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(IronGameTests.itemsAround(helper, phosphoricPos.above(), Metallurgy.PHOSPHORIC_IRON_SLAG.get()) == 1, "A bath with 1.5% phosphorus should skim one piece of phosphoric slag");
            helper.assertTrue(IronGameTests.itemsAround(helper, phosphoricPos.above(), Metallurgy.IRON_SLAG.get()) == 0, "and no plain slag from it, as re-smelting it must keep the phosphorus");
            helper.assertTrue(IronGameTests.itemsAround(helper, plainPos.above(), Metallurgy.IRON_SLAG.get()) == 1, "A bath with no phosphorus should skim one piece of plain iron slag");
            helper.assertTrue(IronGameTests.itemsAround(helper, plainPos.above(), Metallurgy.PHOSPHORIC_IRON_SLAG.get()) == 0, "and no phosphoric slag from it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void phosphoricPigIronCannotBeAlloyedIntoSteel(GameTestHelper helper)
    {
        assertMix(helper, new BlockPos(1, 1, 1), List.of(pig(), IronGameTests.metal(Metal.WROUGHT_IRON)), List.of(10, 90), IronGameTests.metal(Metal.STEEL)); // The control
        assertMix(helper, new BlockPos(3, 1, 1), List.of(phosphoricPig(), IronGameTests.metal(Metal.WROUGHT_IRON)), List.of(10, 90), IronGameTests.metal(Metal.UNKNOWN));
        assertMix(helper, new BlockPos(5, 1, 1), List.of(Metallurgy.COLD_SHORT_IRON.get(), IronGameTests.metal(Metal.WROUGHT_IRON)), List.of(10, 90), IronGameTests.metal(Metal.UNKNOWN));
        assertMix(helper, new BlockPos(7, 1, 1), List.of(pig(), phosphoricPig(), IronGameTests.metal(Metal.WROUGHT_IRON)), List.of(5, 5, 90), IronGameTests.metal(Metal.UNKNOWN));
        helper.succeed();
    }

    private static void assertMix(GameTestHelper helper, BlockPos pos, List<Fluid> fluids, List<Integer> amounts, Fluid expected)
    {
        helper.setBlock(pos, TFCBlocks.CRUCIBLE.get());
        final @Nullable IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(handler != null, "A crucible should hold liquid");
        for (int i = 0; i < fluids.size(); i++)
        {
            helper.assertTrue(handler.fill(new FluidStack(fluids.get(i), amounts.get(i)), IFluidHandler.FluidAction.EXECUTE) == amounts.get(i), fluids.get(i) + " should go into a crucible");
        }
        final CrucibleBlockEntity crucible = helper.getBlockEntity(pos);
        final Fluid result = crucible.getAlloyResult().getFluid();
        helper.assertTrue(result == expected, fluids + " in " + amounts + " should make " + expected + ", got " + result);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void theLiningFollowsItsRules(GameTestHelper helper)
    {
        final BlockPos acidPos = new BlockPos(2, 1, 2), fullPos = new BlockPos(6, 1, 6);
        helper.setBlock(acidPos, Metallurgy.CONVERTER.get());
        helper.setBlock(fullPos, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity vessel = helper.getBlockEntity(acidPos), loaded = helper.getBlockEntity(fullPos);
        final FakePlayer player = player(helper);
        helper.assertFalse(vessel.bath().basic, "A new vessel is acid");

        // Half of a basic relining is still the acid lining, and refuses lime
        vessel.wearLining(5000f);
        final ItemStack bricks = new ItemStack(Metallurgy.BASIC_BRICK.get(), 8);
        for (int i = 1; i <= 7; i++)
        {
            vessel.interact(bricks, player, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(!vessel.bath().basic && vessel.basicBricks() == 7 && bricks.getCount() == 1, "Seven bricks do not make a basic lining, got " + vessel.basicBricks());
        helper.assertTrue(vessel.liningWorn() == 5000f, "And the old lining is as worn as before");
        final ItemStack powder = new ItemStack(lime(), 2);
        vessel.interact(powder, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(powder.getCount() == 2 && vessel.bath().lime() == 0f, "Lime would flux away a silica lining, and is refused");

        // The eighth makes it basic and new
        vessel.interact(bricks, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(vessel.bath().basic && vessel.basicBricks() == 0 && bricks.getCount() == 0, "The eighth brick finishes the lining");
        helper.assertTrue(vessel.liningWorn() == 0f, "And it is new, got " + vessel.liningWorn());

        // Lime goes in a basic vessel
        vessel.interact(powder, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(powder.getCount() == 1 && vessel.bath().lime() == ConverterBath.LIME_MOLES_PER_POWDER, "A powder of lime is 7.5 mol of CaO");

        // A basic lining takes basic bricks only
        vessel.wearLining(3000f);
        final ItemStack fire = new ItemStack(TFCItems.FIRE_BRICK.asItem(), 2);
        vessel.interact(fire, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(fire.getCount() == 2 && vessel.liningWorn() == 3000f, "Fire clay is refused by a basic lining");
        final ItemStack basic = new ItemStack(Metallurgy.BASIC_BRICK.get(), 2);
        vessel.interact(basic, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(basic.getCount() == 1 && vessel.liningWorn() == 1500f, "A basic brick repairs a basic lining by the same amount, got " + vessel.liningWorn());

        // Nothing is relined with iron in the vessel
        vessel.tank().fill(new FluidStack(pig(), 100), IFluidHandler.FluidAction.EXECUTE);
        vessel.interact(basic, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(basic.getCount() == 1 && vessel.liningWorn() == 1500f, "It has to be empty to be relined");

        // And an acid lining still takes fire bricks and nothing basic from them
        loaded.wearLining(3000f);
        loaded.interact(fire, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(fire.getCount() == 1 && loaded.liningWorn() == 1500f && !loaded.bath().basic, "Fire bricks work as before on an acid lining, got " + loaded.liningWorn());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aBasicVesselSurvivesSaveAndLoad(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity vessel = helper.getBlockEntity(pos);
        final FakePlayer player = player(helper);
        lineBasic(vessel, player, 2);
        vessel.tank().fill(new FluidStack(phosphoricPig(), 500), IFluidHandler.FluidAction.EXECUTE);
        final ConverterBath bath = vessel.bath();
        bath.phosphorus = 0.4f;
        bath.thomasSlagPhosphorus = 1.3f; // Part of a piece of slag
        bath.silicaWithoutLime = 0.2f;

        final var registries = helper.getLevel().registryAccess();
        final CompoundTag saved = vessel.saveWithoutMetadata(registries);
        final ConverterBlockEntity copy = new ConverterBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        copy.loadWithComponents(saved, registries);
        final ConverterBath back = copy.bath();
        helper.assertTrue(back.basic, "The lining stays basic");
        helper.assertTrue(Math.abs(back.lime() - bath.lime()) < 1e-4f && back.lime() > 14f, "The lime stays, got " + back.lime() + " of " + bath.lime());
        helper.assertTrue(back.thomasSlagPhosphorus == 1.3f, "Part of a piece of slag is kept, got " + back.thomasSlagPhosphorus);
        helper.assertTrue(back.phosphorus == 0.4f && back.silicaWithoutLime == 0.2f && back.metal == bath.metal, "The rest of the bath too");
        helper.assertTrue(back.tapGrade() == bath.tapGrade(), "And it taps as the same iron");

        // A lining half laid toward basic
        final BlockPos halfPos = new BlockPos(1, 1, 1);
        helper.setBlock(halfPos, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity half = helper.getBlockEntity(halfPos);
        lay(half, player, Metallurgy.BASIC_BRICK.get(), 3);
        final ConverterBlockEntity halfCopy = new ConverterBlockEntity(helper.absolutePos(halfPos), helper.getBlockState(halfPos));
        halfCopy.loadWithComponents(half.saveWithoutMetadata(registries), registries);
        helper.assertTrue(!halfCopy.bath().basic && halfCopy.basicBricks() == 3, "The bricks laid so far are kept, got " + halfCopy.basicBricks());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void thePhosphorusRecipesAllLoad(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        for (String name : List.of("blast_furnace/phosphoric_pig_iron", "casting/phosphoric_pig_iron_ingot", "casting/phosphoric_pig_iron_fire_ingot", "casting/cold_short_iron_ingot", "casting/cold_short_iron_fire_ingot",
            "heating/phosphoric_pig_iron_ingot", "heating/cold_short_iron_ingot", "heating/phosphoric_iron_slag", "heating/burnt_dolomite", "quern/dolomite", "quern/thomas_meal", "barrel/basic_brick"))
        {
            helper.assertTrue(recipes.byKey(TFCpp.id(name)).isPresent(), "Recipe tfcpp:" + name + " should load");
        }
        helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "bloomery/raw_iron_bloom")).isPresent(), "The bloom recipe should load");

        // Brown ore of every size is phosphoric, and the others are not
        for (String grade : List.of("small", "poor", "normal", "rich"))
        {
            IronGameTests.assertMelts(helper, BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tfc", "ore/" + grade + "_limonite")), Metallurgy.PHOSPHORIC_IRON_OXIDE.get(), 1565f);
            IronGameTests.assertMelts(helper, BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tfc", "ore/" + grade + "_hematite")), Metallurgy.IRON_OXIDE.get(), 1565f);
            IronGameTests.assertMelts(helper, BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tfc", "ore/" + grade + "_magnetite")), Metallurgy.IRON_OXIDE.get(), 1565f);
        }
        for (String grade : List.of("poor", "normal", "rich"))
        {
            final ItemStack ore = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tfc", "ore/" + grade + "_limonite")));
            final var plain = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tfc", "ore/" + grade + "_hematite")));
            helper.assertTrue(HeatingRecipe.getRecipe(ore).assembleFluid(ore).getAmount() == HeatingRecipe.getRecipe(plain).assembleFluid(plain).getAmount(), "Brown ore gives the same amount as hematite for the " + grade + " grade");
        }

        // The two new irons melt back to themselves, and cast
        IronGameTests.assertMelts(helper, Metallurgy.PHOSPHORIC_PIG_IRON_INGOT.get(), phosphoricPig(), 1200f); // As pig iron
        IronGameTests.assertMelts(helper, Metallurgy.COLD_SHORT_IRON_INGOT.get(), Metallurgy.COLD_SHORT_IRON.get(), 1535f); // As wrought iron
        for (Fluid fluid : List.of(phosphoricPig(), Metallurgy.COLD_SHORT_IRON.get(), Metallurgy.PHOSPHORIC_IRON_OXIDE.get()))
        {
            helper.assertTrue(FluidHeat.get(fluid) != null, fluid + " needs its melting data");
        }
        for (Item ingot : List.of(Metallurgy.PHOSPHORIC_PIG_IRON_INGOT.get(), Metallurgy.COLD_SHORT_IRON_INGOT.get()))
        {
            helper.assertTrue(HeatCapability.get(new ItemStack(ingot)) != null, ingot + " must hold heat");
        }

        // Dolomite, and what is made of it
        final ItemStack dolomite = new ItemStack(Metallurgy.DOLOMITE_POWDER.get());
        final @Nullable HeatingRecipe burn = HeatingRecipe.getRecipe(dolomite);
        helper.assertTrue(burn != null && burn.getTemperature() == 1500f && burn.assembleItem(dolomite).is(Metallurgy.BURNT_DOLOMITE.get()), "Ground dolomite burns to burnt dolomite at 1500 C");
        final RecipeHolder<?> brick = recipes.byKey(TFCpp.id("barrel/basic_brick")).orElseThrow();
        helper.assertTrue(brick.value() instanceof InstantBarrelRecipe barrel && barrel.getResultItem(helper.getLevel().registryAccess()).is(Metallurgy.BASIC_BRICK.get()), "A barrel of tar and burnt dolomite gives basic bricks");
        final @Nullable Fertilizer meal = Fertilizer.get(new ItemStack(Metallurgy.THOMAS_MEAL.get()));
        helper.assertTrue(meal != null && Math.abs(meal.getNutrient(net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType.PHOSPHOROUS) - 0.15f) < 1e-6f, "Thomas meal is a 0.15 phosphorus fertiliser");
        helper.succeed();
    }

    private PhosphorusGameTests() {}
}
