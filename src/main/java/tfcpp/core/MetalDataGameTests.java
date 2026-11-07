package tfcpp.core;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.component.fluid.FluidContainerInfo;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.FluidAlloy;
import net.dries007.tfc.util.Metal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import tfcpp.geology.Ores;
import tfcpp.metallurgy.ConverterBath;
import tfcpp.metallurgy.ConverterBlockEntity;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.metallurgy.RetortBlockEntity;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.instruments.Instruments;

/** Checks the data of batch 5 of the global review: what mixes with what, what iron oxide casts into, what arsenical copper makes, and the devices that carry them. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MetalDataGameTests
{
    private static final String TEMPLATE = "empty9";

    private static Fluid fluid(String id)
    {
        return BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
    }

    private static Item item(String id)
    {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    /** @return what TFC's crucible makes of this much of one metal and this much of another */
    private static Fluid mixOf(GameTestHelper helper, String first, int firstAmount, String second, int secondAmount)
    {
        final FluidAlloy alloy = FluidAlloy.empty();
        final FluidContainerInfo anything = new FluidContainerInfo()
        {
            @Override
            public boolean canContainFluid(Fluid input)
            {
                return true;
            }

            @Override
            public int fluidCapacity()
            {
                return 100000;
            }
        };
        alloy.fill(new FluidStack(fluid(first), firstAmount), IFluidHandler.FluidAction.EXECUTE, anything);
        alloy.fill(new FluidStack(fluid(second), secondAmount), IFluidHandler.FluidAction.EXECUTE, anything);
        return alloy.getResult(helper.getLevel()).getFluid();
    }

    private static void assertMix(GameTestHelper helper, String first, String second, String expected)
    {
        for (int share : new int[] {1, 30, 50, 70, 99})
        {
            final Fluid result = mixOf(helper, first, share, second, 100 - share);
            helper.assertTrue(result == fluid(expected), share + " parts of " + first + " with " + (100 - share) + " of " + second + " should be " + expected + ", got " + BuiltInRegistries.FLUID.getKey(result));
        }
    }

    @GameTest(template = TEMPLATE)
    public static void aCleanMetalWithItsDirtierFormIsTheDirtierMetal(GameTestHelper helper)
    {
        assertMix(helper, "tfc:metal/copper", "tfcpp:metal/arsenical_copper", "tfcpp:metal/arsenical_copper");
        assertMix(helper, "tfcpp:metal/lead", "tfcpp:metal/work_lead", "tfcpp:metal/work_lead");
        assertMix(helper, "tfc:metal/pig_iron", "tfcpp:metal/phosphoric_pig_iron", "tfcpp:metal/phosphoric_pig_iron");
        assertMix(helper, "tfc:metal/cast_iron", "tfcpp:metal/phosphoric_pig_iron", "tfcpp:metal/cold_short_iron");
        assertMix(helper, "tfc:metal/steel", "tfcpp:metal/phosphoric_pig_iron", "tfcpp:metal/cold_short_iron");
        assertMix(helper, "tfc:metal/wrought_iron", "tfcpp:metal/cold_short_iron", "tfcpp:metal/cold_short_iron");
        assertMix(helper, "tfc:metal/pig_iron", "tfcpp:metal/cold_short_iron", "tfcpp:metal/cold_short_iron");
        assertMix(helper, "tfcpp:metal/phosphoric_pig_iron", "tfcpp:metal/cold_short_iron", "tfcpp:metal/cold_short_iron");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ironOfDifferentCarbonIsWhatTheLeastCarbonMakesOfIt(GameTestHelper helper)
    {
        assertMix(helper, "tfc:metal/steel", "tfc:metal/wrought_iron", "tfc:metal/wrought_iron");
        assertMix(helper, "tfc:metal/high_carbon_steel", "tfc:metal/steel", "tfc:metal/steel");
        assertMix(helper, "tfc:metal/cast_iron", "tfc:metal/high_carbon_steel", "tfc:metal/high_carbon_steel");
        assertMix(helper, "tfc:metal/pig_iron", "tfc:metal/cast_iron", "tfc:metal/cast_iron");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aMixTfcAlreadyKnewIsStillWhatItWas(GameTestHelper helper)
    {
        final Fluid bronze = mixOf(helper, "tfc:metal/copper", 90, "tfc:metal/tin", 10);
        helper.assertTrue(bronze == fluid("tfc:metal/bronze"), "Copper and tin still make bronze, got " + BuiltInRegistries.FLUID.getKey(bronze));
        final Fluid arsenicalBronze = mixOf(helper, "tfcpp:metal/arsenical_copper", 90, "tfc:metal/tin", 10);
        helper.assertTrue(arsenicalBronze == fluid("tfc:metal/bronze"), "Arsenical copper and tin make bronze, got " + BuiltInRegistries.FLUID.getKey(arsenicalBronze));
        final Fluid steel = mixOf(helper, "tfc:metal/pig_iron", 20, "tfc:metal/wrought_iron", 80);
        helper.assertTrue(steel == fluid("tfc:metal/steel"), "Pig iron and wrought iron still make steel, got " + BuiltInRegistries.FLUID.getKey(steel));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ironOxideCastsIntoPiecesOfSlagAndMeltsBackToTheSame(GameTestHelper helper)
    {
        for (String kind : List.of("iron", "phosphoric_iron"))
        {
            final Fluid oxide = fluid("tfcpp:metal/" + kind + "_oxide");
            final Item slag = item("tfcpp:slag/" + kind);
            final var recipes = helper.getLevel().getRecipeManager();
            final @Nullable RecipeHolder<?> casting = recipes.byKey(TFCpp.id("casting/" + kind + "_oxide_slag")).orElse(null);
            helper.assertTrue(casting != null, "Oxide must cast into slag");
            final ItemStack result = casting.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(result.is(slag), "It gives " + kind + " slag, got " + result);

            final ItemStack mold = new ItemStack(TFCItems.MOLDS.get(Metal.ItemType.INGOT).asItem());
            final var handler = mold.getCapability(Capabilities.FluidHandler.ITEM);
            helper.assertTrue(handler != null && handler.fill(new FluidStack(oxide, 100), IFluidHandler.FluidAction.SIMULATE) == 100, "An ingot mould must take " + kind + " oxide");

            final ItemStack piece = new ItemStack(slag);
            final @Nullable HeatingRecipe melt = HeatingRecipe.getRecipe(piece);
            helper.assertTrue(melt != null && melt.assembleFluid(piece).getFluid() == oxide, "A piece must melt back to the same oxide");
            final int pieces = Math.round(100f / melt.assembleFluid(piece).getAmount());
            helper.assertTrue(pieces == 4 && result.getCount() == pieces, "A mould of 100 mB makes the pieces that melt back to those 100 mB, got " + result.getCount() + " and " + pieces);
            helper.assertTrue(recipes.byKey(TFCpp.id("casting/" + kind + "_oxide_slag_fire")).isPresent(), "A fire ingot mould must take it as well");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void arsenicalCopperWeldsIntoADoubleIngotAndMakesACopperAnvil(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.byKey(TFCpp.id("welding/arsenical_copper_double_ingot")).isPresent(), "Two arsenical ingots must weld");

        final ItemStack doubled = new ItemStack(Metallurgy.ARSENICAL_COPPER_DOUBLE_INGOT.get());
        final ItemStack none = ItemStack.EMPTY;
        final CraftingInput grid = CraftingInput.of(3, 3, List.of(doubled, doubled, doubled, none, doubled, none, doubled, doubled, doubled));
        final @Nullable RecipeHolder<?> anvil = recipes.getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElse(null);
        helper.assertTrue(anvil != null, "Seven arsenical double ingots must make an anvil");
        final ItemStack made = ((net.minecraft.world.item.crafting.CraftingRecipe) anvil.value()).assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(made.is(item("tfc:metal/anvil/copper")), "It is a copper anvil");
        helper.assertTrue(made.has(Metallurgy.ARSENICAL.get()), "Made of arsenical copper, so it is marked as that");

        final ItemStack pureDouble = new ItemStack(item("tfc:metal/double_ingot/copper"));
        final CraftingInput pure = CraftingInput.of(3, 3, List.of(pureDouble, pureDouble, pureDouble, none, pureDouble, none, pureDouble, pureDouble, pureDouble));
        final var pureAnvil = recipes.getRecipeFor(RecipeType.CRAFTING, pure, helper.getLevel());
        helper.assertTrue(pureAnvil.isPresent(), "Copper's own anvil must still be made of its own double ingots");
        helper.assertFalse(pureAnvil.get().value().assemble(pure, helper.getLevel().registryAccess()).has(Metallurgy.ARSENICAL.get()), "And is not marked");

        final @Nullable HeatingRecipe melt = HeatingRecipe.getRecipe(doubled);
        helper.assertTrue(melt != null && melt.assembleFluid(doubled).getFluid() == Metallurgy.ARSENICAL_COPPER.get() && melt.assembleFluid(doubled).getAmount() == 200, "The double ingot melts back to its 200 mB of arsenical copper");
        final var rod = recipes.byKey(TFCpp.id("anvil/arsenical/metal/rod/copper"));
        helper.assertTrue(rod.isPresent(), "Arsenical copper must be hammered into rods as copper is");
        final ItemStack rodResult = rod.get().value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(rodResult.is(item("tfc:metal/rod/copper")) && rodResult.has(Metallurgy.ARSENICAL.get()), "The rod is the copper rod, marked, got " + rodResult);
        final var pureRod = recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "anvil/metal/rod/copper"));
        helper.assertTrue(pureRod.isPresent() && !pureRod.get().value().getResultItem(helper.getLevel().registryAccess()).has(Metallurgy.ARSENICAL.get()), "Pure copper's own rod is not marked");
        helper.assertTrue(recipes.byKey(TFCpp.id("anvil/arsenical/metal/sheet/copper")).isPresent(), "And from its double ingot into sheets");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void copperMeltsToWhatItWasMadeOf(GameTestHelper helper)
    {
        for (String part : List.of("rod", "sheet", "double_sheet", "anvil", "axe_head", "pickaxe", "hammer"))
        {
            final Item copper = item("tfc:metal/" + part + "/copper");
            final ItemStack plain = new ItemStack(copper);
            final @Nullable HeatingRecipe plainRecipe = HeatingRecipe.getRecipe(plain);
            helper.assertTrue(plainRecipe != null && plainRecipe.assembleFluid(plain).getFluid() == fluid("tfc:metal/copper"), "A plain copper " + part + " melts to copper exactly as in TFC");
            final ItemStack mark = new ItemStack(copper);
            mark.set(Metallurgy.ARSENICAL.get(), net.minecraft.util.Unit.INSTANCE);
            final @Nullable HeatingRecipe markedRecipe = HeatingRecipe.getRecipe(mark);
            helper.assertTrue(markedRecipe != null && markedRecipe.assembleFluid(mark).getFluid() == Metallurgy.ARSENICAL_COPPER.get(), "A copper " + part + " made of arsenical copper melts to that");
            helper.assertTrue(markedRecipe.assembleFluid(mark).getAmount() == plainRecipe.assembleFluid(plain).getAmount(), "And to as much");
        }
        final var recipes = helper.getLevel().getRecipeManager();
        final var cast = recipes.byKey(TFCpp.id("casting/arsenical/copper_axe_head"));
        helper.assertTrue(cast.isPresent() && cast.get().value().getResultItem(helper.getLevel().registryAccess()).has(Metallurgy.ARSENICAL.get()), "A head cast of arsenical copper is marked");
        final var castPure = recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "casting/copper_axe_head"));
        helper.assertTrue(castPure.isPresent() && !castPure.get().value().getResultItem(helper.getLevel().registryAccess()).has(Metallurgy.ARSENICAL.get()), "TFC's own casting of copper is not");
        final var weld = recipes.byKey(TFCpp.id("welding/arsenical/metal/double_sheet/copper_0"));
        helper.assertTrue(weld.isPresent() && weld.get().value().getResultItem(helper.getLevel().registryAccess()).has(Metallurgy.ARSENICAL.get()), "A weld of a marked sheet is marked");
        for (String part : List.of("ingot", "double_ingot"))
        {
            final ItemStack stack = new ItemStack(item("tfc:metal/" + part + "/copper"));
            final @Nullable HeatingRecipe recipe = HeatingRecipe.getRecipe(stack);
            helper.assertTrue(recipe != null && recipe.assembleFluid(stack).getFluid() == fluid("tfc:metal/copper"), "A copper " + part + " is its own item and stays pure");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nothingInTheGameNeedsRedstoneAndNothingMakesIt(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        for (var holder : recipes.getRecipes())
        {
            for (Ingredient ingredient : holder.value().getIngredients())
            {
                helper.assertFalse(ingredient.test(new ItemStack(Items.REDSTONE)), holder.id() + " still needs redstone, which can no longer be made");
            }
            final ItemStack result = holder.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertFalse(result.is(Items.REDSTONE) || result.is(Items.REDSTONE_BLOCK), holder.id() + " makes redstone from nothing the world has");
        }
        for (String name : List.of("piston", "repeater", "comparator", "clock", "redstone_torch", "observer", "dispenser"))
        {
            helper.assertTrue(recipes.byKey(ResourceLocation.withDefaultNamespace(name)).isPresent(), "The recipe of the " + name + " must still be there, now of brass clockwork");
        }
        helper.assertTrue(recipes.byKey(ResourceLocation.withDefaultNamespace("redstone")).isEmpty(), "A block of redstone must not turn back into the dust");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void castIronIsCastAndNotForgedOrWelded(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        for (String name : List.of("anvil/metal/rod/cast_iron", "anvil/metal/sheet/cast_iron", "welding/metal/double_ingot/cast_iron", "welding/metal/double_sheet/cast_iron"))
        {
            helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", name)).isEmpty(), name + " must be gone: cast iron breaks under a hammer");
        }
        for (String name : List.of("crafting/stove", "crafting/fireproof_door", "crafting/fire_brick_shelf"))
        {
            final var holder = recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", name));
            helper.assertTrue(holder.isPresent(), name + " must still be craftable");
            final boolean needsCastIron = holder.get().value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(item("tfc:metal/sheet/cast_iron"))) || ingredient.test(new ItemStack(item("tfc:metal/rod/cast_iron"))));
            helper.assertFalse(needsCastIron, name + " needs what can no longer be made");
        }
        helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "casting/cast_iron_ingot")).isPresent(), "Cast iron still casts");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void workLeadBeatenFlatIsStillWorkLead(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        final var anvil = recipes.byKey(TFCpp.id("anvil/work_lead_sheet"));
        helper.assertTrue(anvil.isPresent(), "A work lead ingot makes a sheet");
        helper.assertTrue(anvil.get().value().getResultItem(helper.getLevel().registryAccess()).is(Metallurgy.WORK_LEAD_SHEET.get()), "The sheet of work lead is not lead's");
        final var lead = recipes.byKey(TFCpp.id("anvil/lead_sheet"));
        helper.assertTrue(lead.isPresent() && lead.get().value().getResultItem(helper.getLevel().registryAccess()).is(Metallurgy.LEAD_SHEET.get()), "A lead ingot still makes lead's");

        final ItemStack sheet = new ItemStack(Metallurgy.WORK_LEAD_SHEET.get());
        final @Nullable HeatingRecipe melt = HeatingRecipe.getRecipe(sheet);
        helper.assertTrue(melt != null && melt.assembleFluid(sheet).getFluid() == Metallurgy.WORK_LEAD.get() && melt.assembleFluid(sheet).getAmount() == 100, "A work lead sheet runs back to 100 mB of work lead, silver and all");
        helper.succeed();
    }

    // The converter

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aBrokenConverterTakesItsLiningWithItAndAFreshOneStaysFresh(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity fresh = helper.getBlockEntity(pos);
        final BlockState state = helper.getBlockState(pos);
        final List<ItemStack> freshDrops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), fresh);
        helper.assertTrue(freshDrops.size() == 1 && freshDrops.getFirst().get(DataComponents.CUSTOM_DATA) == null, "A new vessel drops as a plain item, so that they stack");

        fresh.wearLining(3000f);
        fresh.bath().basic = true;
        final List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), fresh);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(Metallurgy.CONVERTER_ITEM.get()), "A worn vessel drops too, got " + drops);
        final @Nullable CustomData data = drops.getFirst().get(DataComponents.CUSTOM_DATA);
        helper.assertTrue(data != null && data.copyTag().getFloat("lining") == 3000f && data.copyTag().getBoolean("basic"), "The lining is on the item, got " + data);

        // Set down again, it is the same vessel
        final BlockPos other = new BlockPos(6, 1, 6);
        helper.setBlock(other, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity placed = helper.getBlockEntity(other);
        helper.assertTrue(placed.liningWorn() == 0f && !placed.bath().basic, "A new vessel starts new");
        placed.applyComponentsFromItemStack(drops.getFirst());
        helper.assertTrue(placed.liningWorn() == 3000f && placed.bath().basic, "A vessel set down again keeps the lining it had: " + placed.liningWorn() + ", basic " + placed.bath().basic);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void tappingIntoAVesselOfGalenaFumesAtTheConverter(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity converter = helper.getBlockEntity(pos);
        converter.tank().fill(new FluidStack(fluid("tfc:metal/steel"), 1000), IFluidHandler.FluidAction.EXECUTE);
        converter.bath().temperature = 1700f;
        converter.bath().carbon = 0.3f;
        converter.bath().silicon = 0f;

        final ItemStack vessel = new ItemStack(TFCItems.VESSEL.get());
        final @Nullable IItemHandler slots = vessel.getCapability(Capabilities.ItemHandler.ITEM);
        helper.assertTrue(slots != null && slots.insertItem(0, new ItemStack(Ores.PIECES.get("rich_galena").get(), 4), false).isEmpty(), "A vessel takes galena");

        final FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
        player.setItemInHand(InteractionHand.MAIN_HAND, vessel);
        converter.interact(vessel, player, InteractionHand.MAIN_HAND);

        final LevelGasVolume volume = GasTracker.get(helper.getLevel()).volume(helper.getLevel());
        final BlockPos absolute = helper.absolutePos(pos);
        float sulfur = 0f;
        for (int dy = 0; dy < 48; dy++)
        {
            final @Nullable float[] cell = volume.cell(absolute.getX(), absolute.getY() + dy, absolute.getZ());
            sulfur += cell == null ? 0f : cell[Gas.SULFUR_DIOXIDE.ordinal()];
        }
        final ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        final @Nullable var heat = net.dries007.tfc.common.component.heat.HeatCapability.get(held);
        helper.assertTrue(sulfur > 0f, "Galena melting in the vessel that the converter heats must give off its sulfur at the converter, got " + sulfur + " mol, the vessel in hand is at " + (heat == null ? -1f : heat.getTemperature()) + " C");
        helper.succeed();
    }

    // The retort

    @GameTest(template = TEMPLATE)
    public static void theTankOfARetortGivesLiquidAndTakesNone(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Metallurgy.RETORT.get());
        final RetortBlockEntity retort = helper.getBlockEntity(pos);
        retort.tank().fill(new FluidStack(Metallurgy.MERCURY.get(), 100), IFluidHandler.FluidAction.EXECUTE);

        final @Nullable IFluidHandler outside = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(outside != null, "A retort can be tapped from outside");
        helper.assertTrue(outside.fill(new FluidStack(Metallurgy.MERCURY.get(), 100), IFluidHandler.FluidAction.EXECUTE) == 0, "Nothing may be poured into the retort tank from outside");
        helper.assertTrue(outside.fill(new FluidStack(fluid("minecraft:water"), 100), IFluidHandler.FluidAction.EXECUTE) == 0, "Not water either");
        helper.assertTrue(retort.tank().getFluidAmount() == 100, "The tank is as it was");
        helper.assertTrue(outside.drain(30, IFluidHandler.FluidAction.EXECUTE).getAmount() == 30 && retort.tank().getFluidAmount() == 70, "But it can be drained");
        helper.succeed();
    }

    /** A powder of litharge holds 4.568 mL of lead, so eight of them hold 36.5 mL, and not the 32 of eight times four. */
    @GameTest(template = TEMPLATE, timeoutTicks = 1800)
    public static void aStackOfLithargeLosesNoLeadToRounding(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Metallurgy.RETORT.get());
        final RetortBlockEntity retort = helper.getBlockEntity(pos);
        retort.load(new ItemStack(Instruments.LEAD_OXIDE.get(), 8), 1f);
        helper.onEachTick(() -> retort.setTemperature(900f));

        helper.runAfterDelay(1500, /* A retort heats at about 1 C per tick, and each piece takes a second */ () -> {
            helper.assertTrue(retort.tank().getFluid().getFluid() == Metallurgy.LEAD.get() && retort.tank().getFluidAmount() == 36, "Eight powders hold 36.5 mL of lead and the tank 36 whole millibuckets of it, got " + retort.tank().getFluidAmount());
            helper.succeed();
        });
    }

    /** 50 g of ZnO is 0.614 mol and gives 5.63 mL of zinc, so eight powders give 45 whole millibuckets and a hundredth left over. */
    @GameTest(template = TEMPLATE, timeoutTicks = 2100)
    public static void zincOxideCaughtInATowerIsReducedToZincInAClosedRetort(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Metallurgy.RETORT.get());
        helper.setBlock(pos.east(), Metallurgy.RETORT_CONDENSER.get());
        final RetortBlockEntity retort = helper.getBlockEntity(pos);
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(TFCpp.id("retort/zinc_oxide_powder")).isPresent(), "The recipe of the captured oxide must load");
        retort.load(new ItemStack(Instruments.ZINC_OXIDE.get(), 8), 8f);
        helper.onEachTick(() -> retort.setTemperature(1100f));

        helper.runAfterDelay(1800, /* A retort heats at about 1 C per tick to the 1000 C of zinc, and each piece takes a second */ () -> {
            helper.assertTrue(retort.tank().getFluid().getFluid() == fluid("tfc:metal/zinc") && retort.tank().getFluidAmount() == 45, "Eight powders hold 45.0 mL of zinc, got " + retort.tank().getFluidAmount() + " of " + BuiltInRegistries.FLUID.getKey(retort.tank().getFluid().getFluid()));
            helper.assertTrue(totalOf(helper, Gas.ZINC_OXIDE_FUME) == 0f, "Nothing escapes through a condenser");
            helper.assertTrue(totalOf(helper, Gas.CARBON_MONOXIDE) > 0.05f, "ZnO + C gives CO, got " + totalOf(helper, Gas.CARBON_MONOXIDE));
            helper.succeed();
        });
    }

    private static float totalOf(GameTestHelper helper, Gas gas)
    {
        float sum = 0f;
        for (int x = 0; x < 9; x++)
        {
            for (int y = 0; y < 5; y++)
            {
                for (int z = 0; z < 9; z++)
                {
                    final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    final @Nullable float[] cell = GasTracker.get(helper.getLevel()).volume(helper.getLevel()).cell(pos.getX(), pos.getY(), pos.getZ());
                    sum += cell == null ? 0f : cell[gas.ordinal()];
                }
            }
        }
        return sum;
    }

    private static ItemStack markedCopper(String path)
    {
        final ItemStack stack = new ItemStack(item("tfc:metal/" + path + "/copper"));
        stack.set(Metallurgy.ARSENICAL.get(), net.minecraft.util.Unit.INSTANCE);
        return stack;
    }

    @GameTest(template = TEMPLATE)
    public static void aToolMadeOfAMarkedHeadAndAStickIsMarkedAndMeltsBackToArsenicalCopper(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        final ItemStack stick = new ItemStack(Items.STICK);
        for (String tool : List.of("pickaxe", "axe", "hammer", "shovel", "hoe", "chisel", "javelin", "mace", "propick"))
        {
            final ItemStack head = markedCopper(tool + "_head");
            final CraftingInput grid = CraftingInput.of(1, 2, List.of(head, stick));
            final var found = recipes.getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
            helper.assertTrue(found.isPresent(), "A marked " + tool + " head and a stick must still make a tool");
            final ItemStack made = found.get().value().assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(made.is(item("tfc:metal/" + tool + "/copper")), "It is the copper " + tool + ", got " + made);
            helper.assertTrue(made.has(Metallurgy.ARSENICAL.get()), "The " + tool + " from a marked head carries the mark, or it would melt to pure copper");
            final @Nullable HeatingRecipe melt = HeatingRecipe.getRecipe(made);
            helper.assertTrue(melt != null && melt.assembleFluid(made).getFluid() == Metallurgy.ARSENICAL_COPPER.get(), "And melts back to arsenical copper");

            final ItemStack plainHead = new ItemStack(item("tfc:metal/" + tool + "_head/copper"));
            final CraftingInput plain = CraftingInput.of(1, 2, List.of(plainHead, stick));
            final var plainFound = recipes.getRecipeFor(RecipeType.CRAFTING, plain, helper.getLevel());
            helper.assertTrue(plainFound.isPresent(), "A plain copper " + tool + " head makes the tool as in TFC");
            final ItemStack plainMade = plainFound.get().value().assemble(plain, helper.getLevel().registryAccess());
            helper.assertFalse(plainMade.has(Metallurgy.ARSENICAL.get()), "A tool from a plain head is not marked");
            final @Nullable HeatingRecipe plainMelt = HeatingRecipe.getRecipe(plainMade);
            helper.assertTrue(plainMelt != null && plainMelt.assembleFluid(plainMade).getFluid() == fluid("tfc:metal/copper"), "And melts to copper");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyCraftingOfACopperItemFromACopperPartKeepsTheMark(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        // The other things crafted of a part of copper that melt back to copper: the knife, saw and scythe, the lamp, the grate, a block of sheets, the armour of horses and the fishing rod
        for (String kind : List.of("knife", "saw", "scythe", "sword", "fishing_rod", "lamp", "grate", "block", "horse_armor"))
        {
            final var variant = recipes.byKey(TFCpp.id("crafting/arsenical/" + kind + "/copper"));
            helper.assertTrue(variant.isPresent(), "A recipe of the marked part for the copper " + kind + " must exist");
            helper.assertTrue(variant.get().value().getResultItem(helper.getLevel().registryAccess()).has(Metallurgy.ARSENICAL.get()), "And its result is marked");
            final var tfc = recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "crafting/metal/" + kind + "/copper"));
            helper.assertTrue(tfc.isPresent() && !tfc.get().value().getResultItem(helper.getLevel().registryAccess()).has(Metallurgy.ARSENICAL.get()), "TFC's own recipe of the " + kind + " stays and is not marked");
        }
        // A block of marked sheets: four sheets, a hammer and a plank, as TFC has it
        final ItemStack sheet = markedCopper("sheet");
        final ItemStack hammer = new ItemStack(item("tfc:metal/hammer/bronze"));
        final ItemStack plank = new ItemStack(item("tfc:wood/planks/oak"));
        final ItemStack none = ItemStack.EMPTY;
        final CraftingInput block = CraftingInput.of(3, 3, List.of(none, sheet, hammer, sheet.copy(), plank, sheet.copy(), none, sheet.copy(), none));
        final var found = recipes.getRecipeFor(RecipeType.CRAFTING, block, helper.getLevel());
        helper.assertTrue(found.isPresent(), "A block of marked sheets must be made");
        final ItemStack made = found.get().value().assemble(block, helper.getLevel().registryAccess());
        helper.assertTrue(made.is(item("tfc:metal/block/copper")) && made.getCount() == 8 && made.has(Metallurgy.ARSENICAL.get()), "Eight blocks, marked, got " + made);
        helper.succeed();
    }

    private MetalDataGameTests() {}
}
