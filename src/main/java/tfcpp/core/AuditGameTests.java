package tfcpp.core;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dries007.tfc.common.blockentities.PitKilnBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.common.items.Powder;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.data.FluidHeat;
import net.dries007.tfc.util.data.Fuel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import tfcpp.metallurgy.MeltContext;
import tfcpp.metallurgy.MetalHeat;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/** Checks the corrections from the audit of TFC's physics: ceramics, fire, heat, gunpowder and the rest. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AuditGameTests
{
    private static final String TEMPLATE = "empty9";

    private static Item item(String id)
    {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static float firesAt(GameTestHelper helper, String unfired)
    {
        final @Nullable HeatingRecipe recipe = HeatingRecipe.getRecipe(new ItemStack(item(unfired)));
        helper.assertTrue(recipe != null, unfired + " should fire");
        return recipe.getTemperature();
    }

    @GameTest(template = TEMPLATE)
    public static void potteryFiresLowerThanFireClay(GameTestHelper helper)
    {
        for (String pottery : List.of("tfc:ceramic/unfired_jug", "tfc:ceramic/unfired_vessel", "tfc:ceramic/unfired_brick", "tfc:ceramic/unfired_pot", "tfc:ceramic/unfired_flower_pot", "tfc:ceramic/unfired_blowpipe"))
        {
            final float temperature = firesAt(helper, pottery);
            helper.assertTrue(temperature == 850f, pottery + " should fire at 850 C, got " + temperature);
        }
        for (String fireClay : List.of("tfc:ceramic/unfired_fire_brick", "tfc:ceramic/unfired_crucible", "tfc:ceramic/unfired_channel", "tfc:ceramic/unfired_mold_table", "tfc:ceramic/unfired_fire_ingot_mold"))
        {
            final float temperature = firesAt(helper, fireClay);
            helper.assertTrue(temperature == 1300f, fireClay + " should fire at 1300 C, got " + temperature);
        }
        helper.succeed();
    }

    /** One each of a pot, fire clay, tin and a brick of raw clay, in a kiln near the end of its burn. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aPitKilnFiresPotteryAndNotFireClayAndLosesNoMetal(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, TFCBlocks.PIT_KILN.get());
        final PitKilnBlockEntity kiln = helper.getBlockEntity(pos);
        final IItemHandlerModifiable inventory = kiln.getInventory();

        final Item tin = TFCItems.METAL_ITEMS.get(Metal.TIN).get(Metal.ItemType.INGOT).asItem();
        inventory.setStackInSlot(0, new ItemStack(item("tfc:ceramic/unfired_jug")));
        inventory.setStackInSlot(1, new ItemStack(item("tfc:ceramic/unfired_fire_brick")));
        inventory.setStackInSlot(2, new ItemStack(tin));
        kiln.updateCache();
        try
        {
            final Field lit = PitKilnBlockEntity.class.getDeclaredField("litTick");
            lit.setAccessible(true);
            lit.setLong(kiln, Calendars.SERVER.getTicks() - 7200L);
        }
        catch (ReflectiveOperationException e)
        {
            helper.fail("Could not set the kiln alight: " + e);
        }
        kiln.cookContents();

        helper.assertTrue(inventory.getStackInSlot(0).is(item("tfc:ceramic/jug")), "A jug should have fired, got " + inventory.getStackInSlot(0));
        helper.assertTrue(inventory.getStackInSlot(1).is(item("tfc:ceramic/unfired_fire_brick")), "Fire clay should need more than a pit, got " + inventory.getStackInSlot(1));
        helper.assertTrue(inventory.getStackInSlot(2).is(tin), "Tin should still be there, got " + inventory.getStackInSlot(2));
        final var heat = HeatCapability.get(inventory.getStackInSlot(1));
        helper.assertTrue(heat != null && Math.abs(heat.getTemperature() - 900f) < 1f, "A pit gets to about 900 C, got " + (heat == null ? 0f : heat.getTemperature()));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fireIsAsHotAsARealOne(GameTestHelper helper)
    {
        final @Nullable Fuel charcoal = Fuel.get(new ItemStack(Items.CHARCOAL));
        helper.assertTrue(charcoal != null && charcoal.temperature() == 1190f, "Charcoal without a blast should reach 1190 C");
        final float blown = HeatCapability.targetDeviceTemp(charcoal.temperature(), 1000, false);
        helper.assertTrue(blown == 1640f, "Charcoal with a blast should reach 1640 C, got " + blown);
        helper.assertTrue(blown >= 1565f, "A blast furnace must still be able to melt its charge");
        helper.assertTrue(blown >= 1535f, "and a crucible over a forge must still be able to melt wrought iron");
        helper.assertTrue(HeatCapability.targetDeviceTemp(charcoal.temperature(), 0, false) < 1300f, "Fire clay should need a blast");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void capacitiesCameFromTheScale(GameTestHelper helper)
    {
        for (Metal metal : List.of(Metal.COPPER, Metal.TIN, Metal.BISMUTH, Metal.STEEL))
        {
            final ItemStack stack = new ItemStack(TFCItems.METAL_ITEMS.get(metal).get(Metal.ItemType.INGOT).asItem());
            final var heat = HeatCapability.get(stack);
            final float expected = MetalHeat.METALS.stream().filter(m -> m.id().equals(metal.getSerializedName())).findFirst().orElseThrow().ingotCapacity();
            helper.assertTrue(heat != null && Math.abs(heat.getHeatCapacity() - expected) < 0.01f, metal + " should have a capacity of " + expected + ", got " + (heat == null ? 0f : heat.getHeatCapacity()));
        }
        final FluidHeat copper = FluidHeat.get(TFCFluids.METALS.get(Metal.COPPER).getSource());
        helper.assertTrue(copper != null && Math.abs(copper.specificHeatCapacity() - MetalHeat.COPPER.liquidCapacity()) < 1e-4f, "Molten copper should follow the same scale");
        final FluidHeat sterling = FluidHeat.get(TFCFluids.METALS.get(Metal.STERLING_SILVER).getSource());
        helper.assertTrue(sterling != null && sterling.meltTemperature() == 890f, "Sterling silver should melt near 890 C");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aKilnRunsAChanceRecipeOnce(GameTestHelper helper) throws ReflectiveOperationException
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, TFCBlocks.PIT_KILN.get());
        final PitKilnBlockEntity kiln = helper.getBlockEntity(pos);
        final IItemHandlerModifiable inventory = kiln.getInventory();
        for (int slot = 0; slot < 4; slot++)
        {
            inventory.setStackInSlot(slot, new ItemStack(item("tfc:kaolin_clay")));
        }
        kiln.updateCache();
        final Field lit = PitKilnBlockEntity.class.getDeclaredField("litTick");
        lit.setAccessible(true);
        lit.setLong(kiln, Calendars.SERVER.getTicks() - 7200L);

        kiln.cookContents();
        final List<ItemStack> first = java.util.stream.IntStream.range(0, 4).mapToObj(slot -> inventory.getStackInSlot(slot).copy()).toList();
        for (int tick = 0; tick < 200; tick++)
        {
            kiln.cookContents();
        }
        for (int slot = 0; slot < 4; slot++)
        {
            helper.assertTrue(ItemStack.isSameItem(first.get(slot), inventory.getStackInSlot(slot)) && first.get(slot).getCount() == inventory.getStackInSlot(slot).getCount(), "Slot " + slot + " should keep what it first made, " + first.get(slot) + " became " + inventory.getStackInSlot(slot));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aFireboxShelfKeepsMetalThatMelts(GameTestHelper helper) throws ReflectiveOperationException
    {
        final ItemStack ingot = new ItemStack(TFCItems.METAL_ITEMS.get(Metal.TIN).get(Metal.ItemType.INGOT).asItem());
        final var heat = HeatCapability.get(ingot);
        helper.assertTrue(heat != null, "An ingot should hold heat");
        heat.setTemperature(800f);

        final var inventory = new net.neoforged.neoforge.items.ItemStackHandler(1);
        inventory.setStackInSlot(0, ingot);
        final var method = net.dries007.tfc.common.blockentities.FireboxBlockEntity.class.getDeclaredMethod("tryApplyRecipe", net.neoforged.neoforge.items.IItemHandler.class, int.class, ItemStack.class, net.dries007.tfc.common.component.heat.IHeat.class);
        method.setAccessible(true);
        helper.assertTrue(!(boolean) method.invoke(null, inventory, 0, ingot, heat), "Tin should not be converted to nothing");
        helper.assertTrue(ingot.getCount() == 1, "The ingot should still be there");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aBloomFollowsTheMetalItMeltsTo(GameTestHelper helper)
    {
        final var bloom = HeatCapability.get(new ItemStack(TFCItems.RAW_IRON_BLOOM.asItem()));
        helper.assertTrue(bloom != null && Math.abs(bloom.getHeatCapacity() - MetalHeat.WROUGHT_IRON.ingotCapacity()) < 0.01f, "A bloom holds as much iron as an ingot, and should heat like one, got " + (bloom == null ? 0f : bloom.getHeatCapacity()));
        helper.succeed();
    }

    private static ItemStack gunpowderFrom(GameTestHelper helper, List<Item> ingredients)
    {
        final List<ItemStack> stacks = ingredients.stream().map(ItemStack::new).toList();
        final CraftingInput input = CraftingInput.of(3, 3, java.util.stream.Stream.concat(stacks.stream(), java.util.stream.Stream.generate(() -> ItemStack.EMPTY)).limit(9).toList());
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }

    @GameTest(template = TEMPLATE)
    public static void gunpowderIsMostlySaltpeterAndHoldsItsWeight(GameTestHelper helper)
    {
        final Item saltpeter = item("tfc:powder/saltpeter"), sulfur = item("tfc:powder/sulfur"), charcoal = item("tfc:powder/charcoal"), graphite = item("tfc:powder/graphite");

        final ItemStack plain = gunpowderFrom(helper, List.of(saltpeter, saltpeter, saltpeter, saltpeter, saltpeter, saltpeter, sulfur, charcoal));
        helper.assertTrue(plain.is(Items.GUNPOWDER) && plain.getCount() == 8, "6 saltpeter, 1 sulfur and 1 charcoal should make 8, got " + plain);

        final ItemStack glazed = gunpowderFrom(helper, List.of(saltpeter, saltpeter, saltpeter, saltpeter, saltpeter, saltpeter, sulfur, charcoal, graphite));
        helper.assertTrue(glazed.is(Items.GUNPOWDER) && glazed.getCount() == 9, "Graphite only adds its own weight, got " + glazed);

        final ItemStack old = gunpowderFrom(helper, List.of(saltpeter, saltpeter, sulfur, charcoal));
        helper.assertTrue(old.isEmpty(), "The old mix of half saltpeter should be gone, got " + old);
        helper.succeed();
    }

    /** @return the recipe's own file as the server loaded it */
    private static JsonObject recipeFile(GameTestHelper helper, String path)
    {
        final var resource = helper.getLevel().getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("tfc", "recipe/" + path + ".json"));
        helper.assertTrue(resource.isPresent(), path + " should exist");
        try (var reader = resource.get().openAsReader())
        {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
        catch (IOException e)
        {
            helper.fail("Could not read " + path + ": " + e);
            return new JsonObject();
        }
    }

    /** @return the degrees that a millibucket of this takes off a hot item */
    private static float cooling(GameTestHelper helper, String recipe)
    {
        for (var modifier : recipeFile(helper, "barrel/" + recipe).getAsJsonObject("output_item").getAsJsonArray("modifiers"))
        {
            if (modifier.getAsJsonObject().get("type").getAsString().equals("tfc:add_heat"))
            {
                return -modifier.getAsJsonObject().get("temperature").getAsFloat();
            }
        }
        helper.fail(recipe + " should cool");
        return 0f;
    }

    @GameTest(template = TEMPLATE)
    public static void boraxIsAFluxButNotLime(GameTestHelper helper)
    {
        final Item borax = Metallurgy.BORAX_FLUX.get();
        helper.assertTrue(HeatingRecipe.getRecipe(new ItemStack(borax)) == null, "Borax must not burn to lime");
        helper.assertTrue(new ItemStack(borax).is(net.dries007.tfc.common.TFCTags.Items.WELDING_FLUX), "Borax is a good welding flux");
        helper.assertTrue(recipeFile(helper, "barrel/limewater").getAsJsonObject("input_item").get("item").getAsString().equals("tfc:powder/lime"), "Lime water should only come from burned lime");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void waterCoolsFasterThanOil(GameTestHelper helper)
    {
        helper.assertTrue(cooling(helper, "water_cooling") > cooling(helper, "olive_oil_cooling"), "Water should take more heat than olive oil");
        helper.assertTrue(cooling(helper, "salt_water_cooling") > cooling(helper, "canola_oil_cooling"), "Salt water should take more heat than canola oil");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void alloysHaveTheirRealProportions(GameTestHelper helper)
    {
        final var brass = recipeFile(helper, "alloy/brass").getAsJsonArray("contents");
        boolean found = false;
        for (var content : brass)
        {
            if (content.getAsJsonObject().get("fluid").getAsString().equals("tfc:metal/zinc"))
            {
                found = content.getAsJsonObject().get("min").getAsFloat() == 0.28f;
            }
        }
        helper.assertTrue(found, "Brass should hold at least 28% zinc");
        helper.assertTrue(HeatingRecipe.getRecipe(new ItemStack(TFCItems.METAL_ITEMS.get(Metal.STERLING_SILVER).get(Metal.ItemType.INGOT).asItem())).getTemperature() == 890f, "A sterling silver ingot should melt at its new temperature");
        final var tallow = recipeFile(helper, "pot/tallow").getAsJsonObject("fluid_output");
        helper.assertTrue(tallow.get("amount").getAsInt() < 1000, "Tallow should not take all the water's place");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void burningLimeGivesOffItsCarbonDioxide(GameTestHelper helper)
    {
        final BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        final ItemStack flux = new ItemStack(TFCItems.POWDERS.get(Powder.FLUX).asItem());
        final @Nullable HeatingRecipe recipe = HeatingRecipe.getRecipe(flux);
        helper.assertTrue(recipe != null, "Limestone should burn");
        MeltContext.enter(helper.getLevel(), pos);
        final ItemStack lime = recipe.assembleItem(flux);
        MeltContext.exit();
        helper.assertTrue(lime.is(TFCItems.POWDERS.get(Powder.LIME).asItem()), "It should give lime, got " + lime);

        final LevelGasVolume volume = GasTracker.get(helper.getLevel()).volume(helper.getLevel());
        float carbonDioxide = 0f;
        for (int y = 0; y < 48; y++)
        {
            final @Nullable float[] cell = volume.cell(pos.getX(), pos.getY() + y, pos.getZ());
            carbonDioxide += cell == null ? 0f : cell[Gas.CARBON_DIOXIDE.ordinal()];
        }
        helper.assertTrue(Math.abs(carbonDioxide - 7.5f) < 0.1f, "A powder of 0.75 kg of limestone gives off 7.5 mol of CO2, got " + carbonDioxide);
        helper.succeed();
    }

    private AuditGameTests() {}
}
