package tfcpp.core;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity;
import net.dries007.tfc.common.blockentities.BloomBlockEntity;
import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.devices.BloomeryBlock;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.data.Drinkable;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.metallurgy.ConverterBath;
import tfcpp.metallurgy.ConverterBlockEntity;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.Breathing;
import tfcpp.pneumatics.gas.BreathingState;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.gas.Physiology;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.pneumatics.instruments.SentinelCageItem;
import tfcpp.pneumatics.instruments.SentinelData;
import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePhysics;
import tfcpp.pneumatics.pipes.PipeSegment;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.Pipes;
import tfcpp.pneumatics.pipes.PressureVesselBlockEntity;

/** Checks iron and steel, and everything that has to follow the length of a day, on a running server with TFC. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IronGameTests
{
    private static final String TEMPLATE = "empty9";
    /** Each of these changes the calendar for everything on the server, so each runs in a batch of its own. */
    private static final String BLOOMERY_BATCH = "tfcpp_calendar_bloomery", CLOCK_BATCH = "tfcpp_calendar_clock", CORROSION_BATCH = "tfcpp_calendar_corrosion", COOLING_BATCH = "tfcpp_calendar_cooling";
    private static final float DEFAULT_RATE = 20f / 24f;
    private static final float REAL_TIME_RATE = 20f / (24f * 60f);
    /** Apart from the others, as these fill the air and a batch shares the gas grid's budget for each tick. */
    private static final String IRON_BATCH = "tfcpp_iron";

    @AfterBatch(batch = BLOOMERY_BATCH)
    public static void afterBloomery(ServerLevel level)
    {
        Calendars.SERVER.setCalendarTickRate(DEFAULT_RATE);
    }

    @AfterBatch(batch = CLOCK_BATCH)
    public static void afterClock(ServerLevel level)
    {
        Calendars.SERVER.setCalendarTickRate(DEFAULT_RATE);
    }

    @AfterBatch(batch = CORROSION_BATCH)
    public static void afterCorrosion(ServerLevel level)
    {
        Calendars.SERVER.setCalendarTickRate(DEFAULT_RATE);
    }

    @AfterBatch(batch = COOLING_BATCH)
    public static void afterCooling(ServerLevel level)
    {
        Calendars.SERVER.setCalendarTickRate(DEFAULT_RATE);
    }

    private static Fluid metal(Metal metal)
    {
        return TFCFluids.METALS.get(metal).getSource();
    }

    private static Item ingot(Metal metal)
    {
        return TFCItems.METAL_ITEMS.get(metal).get(Metal.ItemType.INGOT).asItem();
    }

    private static Item ore(Ore ore, Ore.Grade grade)
    {
        return TFCItems.GRADED_ORES.get(ore).get(grade).asItem();
    }

    private static void assertMelts(GameTestHelper helper, Item item, Fluid fluid, float temperature)
    {
        final @Nullable HeatingRecipe recipe = HeatingRecipe.getRecipe(new ItemStack(item));
        helper.assertTrue(recipe != null, item + " should melt");
        final FluidStack melted = recipe.assembleFluid(new ItemStack(item));
        helper.assertTrue(melted.getFluid() == fluid, item + " should melt to " + fluid + ", got " + melted.getFluid());
        helper.assertTrue(recipe.getTemperature() == temperature, item + " should melt at " + temperature + ", got " + recipe.getTemperature());
    }

    @GameTest(template = TEMPLATE)
    public static void oreWithoutAFurnaceOnlyMeltsToOxide(GameTestHelper helper)
    {
        for (Ore mineral : List.of(Ore.HEMATITE, Ore.MAGNETITE, Ore.LIMONITE))
        {
            assertMelts(helper, ore(mineral, Ore.Grade.NORMAL), Metallurgy.IRON_OXIDE.get(), 1565f);
        }
        helper.assertTrue(FluidHeat.get(Metallurgy.IRON_OXIDE.get()) != null, "Iron oxide needs its melting data");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyIronMeltsBackToItself(GameTestHelper helper)
    {
        assertMelts(helper, ingot(Metal.WROUGHT_IRON), metal(Metal.WROUGHT_IRON), 1535f);
        assertMelts(helper, TFCItems.RAW_IRON_BLOOM.asItem(), metal(Metal.WROUGHT_IRON), 1535f);
        assertMelts(helper, TFCItems.REFINED_IRON_BLOOM.asItem(), metal(Metal.WROUGHT_IRON), 1535f);
        assertMelts(helper, ingot(Metal.PIG_IRON), metal(Metal.PIG_IRON), 1200f);
        assertMelts(helper, ingot(Metal.CAST_IRON), metal(Metal.CAST_IRON), 1200f);
        assertMelts(helper, ingot(Metal.HIGH_CARBON_STEEL), metal(Metal.HIGH_CARBON_STEEL), 1450f);
        assertMelts(helper, ingot(Metal.STEEL), metal(Metal.STEEL), 1500f);
        assertMelts(helper, TFCItems.METAL_ITEMS.get(Metal.STEEL).get(Metal.ItemType.PICKAXE).asItem(), metal(Metal.STEEL), 1500f);
        assertMelts(helper, TFCItems.METAL_ITEMS.get(Metal.WROUGHT_IRON).get(Metal.ItemType.PICKAXE).asItem(), metal(Metal.WROUGHT_IRON), 1535f);

        final FluidHeat steel = FluidHeat.get(metal(Metal.STEEL));
        helper.assertTrue(steel != null && steel.meltTemperature() == 1500f, "Steel must melt at 1500 C");
        final FluidHeat highCarbon = FluidHeat.get(metal(Metal.HIGH_CARBON_STEEL));
        helper.assertTrue(highCarbon != null && highCarbon.meltTemperature() == 1450f, "High carbon steel must melt at 1450 C");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aHammerNoLongerTakesCarbonOutOfIron(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        helper.assertFalse(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "anvil/metal/ingot/high_carbon_steel")).isPresent(), "Pig iron must not forge into steel");
        helper.assertFalse(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "anvil/metal/ingot/steel")).isPresent(), "High carbon steel must not forge into steel");
        helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("tfc", "anvil/metal/ingot/wrought_iron")).isPresent(), "Hammering slag out of a bloom is real and stays");
        helper.assertTrue(recipes.byKey(TFCpp.id("crafting/converter")).isPresent(), "The converter must be craftable");
        helper.succeed();
    }

    private static void assertMix(GameTestHelper helper, BlockPos pos, int pig, int wrought, Fluid expected)
    {
        helper.setBlock(pos, TFCBlocks.CRUCIBLE.get());
        final @Nullable IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(handler != null, "A crucible should hold liquid");
        handler.fill(new FluidStack(metal(Metal.PIG_IRON), pig), IFluidHandler.FluidAction.EXECUTE);
        handler.fill(new FluidStack(metal(Metal.WROUGHT_IRON), wrought), IFluidHandler.FluidAction.EXECUTE);
        final CrucibleBlockEntity crucible = helper.getBlockEntity(pos);
        final Fluid result = crucible.getAlloyResult().getFluid();
        helper.assertTrue(result == expected, pig + " pig iron and " + wrought + " wrought iron should make " + expected + ", got " + result);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void carbonSharesOutWhenIronIsMelted(GameTestHelper helper)
    {
        assertMix(helper, new BlockPos(1, 1, 1), 10, 90, metal(Metal.STEEL)); // 0.5% carbon
        assertMix(helper, new BlockPos(3, 1, 1), 30, 70, metal(Metal.HIGH_CARBON_STEEL)); // 1.4%
        assertMix(helper, new BlockPos(5, 1, 1), 70, 30, metal(Metal.CAST_IRON)); // 3%
        assertMix(helper, new BlockPos(7, 1, 1), 1, 99, metal(Metal.UNKNOWN)); // Too little carbon to count as steel, and no recipe can name wrought iron as its own alloy
        helper.succeed();
    }

    private static float carbon(GameTestHelper helper, int x, int y, int z)
    {
        return total(helper, x, y, z, Gas.CARBON_MONOXIDE) + total(helper, x, y, z, Gas.CARBON_DIOXIDE);
    }

    /** Everything of a gas in the column above this position. */
    private static float total(GameTestHelper helper, int x, int y, int z, Gas gas)
    {
        final BlockPos from = helper.absolutePos(new BlockPos(x, y, z));
        final LevelGasVolume volume = GasTracker.get(helper.getLevel()).volume(helper.getLevel());
        float total = 0f;
        for (int dx = -5; dx <= 5; dx++)
        {
            for (int dz = -5; dz <= 5; dz++)
            {
                for (int dy = -2; dy <= 40; dy++)
                {
                    final @Nullable float[] cell = volume.cell(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                    if (cell != null)
                    {
                        total += cell[gas.ordinal()];
                    }
                }
            }
        }
        return total;
    }

    private static int itemsAround(GameTestHelper helper, BlockPos pos, Item item)
    {
        return itemsAroundAbsolute(helper, helper.absolutePos(pos), item);
    }

    private static int itemsAroundAbsolute(GameTestHelper helper, BlockPos absolute, Item item)
    {
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3)))
        {
            if (entity.getItem().is(item))
            {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    /** With no walls around it, the bloom forming also brings the bloomery itself down, and that is not what this looks at. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BLOOMERY_BATCH)
    public static void aBloomeryLeavesSlagAndTheOxygenOfItsOre(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        Calendars.SERVER.setCalendarTickRate(DEFAULT_RATE);
        final BlockPos pos = new BlockPos(4, 1, 4);
        final BlockState bloomeryState = TFCBlocks.BLOOMERY.get().defaultBlockState();
        helper.setBlock(pos, bloomeryState);

        final BloomeryBlockEntity bloomery = helper.getBlockEntity(pos);
        final Item rich = ore(Ore.HEMATITE, Ore.Grade.RICH);
        for (int i = 0; i < 9; i++)
        {
            bloomery.getInputStacks().add(new ItemStack(rich)); // 315 mB of iron, enough for two blooms and 15 over
        }
        for (int i = 0; i < 4; i++)
        {
            bloomery.getInputStacks().add(new ItemStack(Items.CHARCOAL));
        }
        helper.assertTrue(bloomery.light(helper.getLevel().getBlockState(helper.absolutePos(pos))), "A bloomery with ore and charcoal should light");
        Calendars.SERVER.skipForwardBy(16000L); // More than the 14400 ticks of a bloomery's burn, at the default rate

        helper.runAfterDelay(30, () -> {
            final BlockPos internal = bloomery.getInternalBlockPos();
            helper.assertTrue(helper.getLevel().getBlockEntity(internal) instanceof BloomBlockEntity, "A bloom should have formed");
            final int slag = itemsAroundAbsolute(helper, bloomery.getExternalBlock(), Metallurgy.IRON_SLAG.get());
            helper.assertTrue(slag == 4, "115 mB that did not reach the blooms makes 4 pieces of slag, got " + slag);
            final float carbonDioxide = total(helper, 4, 1, 4, Gas.CARBON_DIOXIDE);
            final float expected = 200f * 7.87f / 55.845f * 1.5f;
            helper.assertTrue(carbonDioxide >= expected * 0.9f, "Taking the oxygen off the 200 mB of iron in two blooms makes about " + expected + " mol of CO2, got " + carbonDioxide);
            helper.succeed();
        });
    }

    @SuppressWarnings("unchecked")
    private static void loadBlastFurnace(BlastFurnaceBlockEntity furnace, List<ItemStack> inputs) throws ReflectiveOperationException
    {
        for (ItemStack stack : inputs)
        {
            HeatCapability.setTemperature(stack, 1600f);
        }
        final Field input = BlastFurnaceBlockEntity.class.getDeclaredField("inputStacks");
        input.setAccessible(true);
        ((List<ItemStack>) input.get(furnace)).addAll(inputs);
        final Field catalyst = BlastFurnaceBlockEntity.class.getDeclaredField("catalystStacks");
        catalyst.setAccessible(true);
        for (int i = 0; i < inputs.size(); i++)
        {
            ((List<ItemStack>) catalyst.get(furnace)).add(new ItemStack(TFCItems.POWDERS.get(net.dries007.tfc.common.items.Powder.FLUX).asItem()));
        }
        final Field temperature = BlastFurnaceBlockEntity.class.getDeclaredField("temperature");
        temperature.setAccessible(true);
        temperature.setFloat(furnace, 1600f);
    }

    /** The furnace throws out what it has no room for on a whole second, and this one has no chimney, so the charge has to melt before then. */
    private static long ticksPastASecond(GameTestHelper helper)
    {
        return 22 - helper.getLevel().getGameTime() % 20;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = IRON_BATCH)
    public static void aBlastFurnaceRunsPigIronIntoAConverterBelow(GameTestHelper helper)
    {
        final BlockPos converterPos = new BlockPos(4, 1, 4), furnacePos = converterPos.above();
        helper.setBlock(converterPos, Metallurgy.CONVERTER.get());
        helper.setBlock(furnacePos, TFCBlocks.BLAST_FURNACE.get());

        helper.runAfterDelay(ticksPastASecond(helper), () -> {
            final BlastFurnaceBlockEntity furnace = helper.getBlockEntity(furnacePos);
            final Item rich = ore(Ore.MAGNETITE, Ore.Grade.RICH);
            try
            {
                loadBlastFurnace(furnace, List.of(new ItemStack(rich), new ItemStack(rich)));
            }
            catch (ReflectiveOperationException e)
            {
                helper.fail("Could not load the furnace: " + e);
            }
        });
        // Soon after, as a blast from a neighbouring test can scatter loose items
        helper.runAfterDelay(ticksPastASecond(helper) + 4, () -> {
            final int slag = itemsAround(helper, furnacePos, Metallurgy.FURNACE_SLAG.get());
            helper.assertTrue(slag == 2, "Each piece of flux should leave a piece of slag, got " + slag);
        });
        helper.runAfterDelay(110, () -> {
            final ConverterBlockEntity converter = helper.getBlockEntity(converterPos);
            helper.assertTrue(converter.bath().metal >= 65f, "Two pieces of rich ore should give 70 mB of pig iron, got " + converter.bath().metal);
            helper.assertTrue(converter.bath().grade() == ConverterBath.Grade.PIG_IRON, "It should be pig iron, got " + converter.bath().grade());
            helper.assertTrue(converter.bath().temperature >= 1400f, "It should arrive hot, got " + converter.bath().temperature);
            final float carbonDioxide = total(helper, 4, 2, 4, Gas.CARBON_DIOXIDE);
            helper.assertTrue(carbonDioxide > 20f, "Reducing ore and burning limestone should give off CO2, got " + carbonDioxide);
            helper.succeed();
        });
    }

    /** A converter with an air receiver beside it, both open to the sky through a clear box. */
    private static ConverterBlockEntity converterWithAir(GameTestHelper helper, int millibuckets, float temperature, float atmospheres)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, Metallurgy.CONVERTER.get());
        helper.setBlock(3, 1, 4, Pipes.PRESSURE_VESSEL.get());
        final ConverterBlockEntity converter = helper.getBlockEntity(pos);
        converter.tank().fill(new FluidStack(metal(Metal.PIG_IRON), millibuckets), IFluidHandler.FluidAction.EXECUTE);
        converter.setTemperature(0f);
        converter.bath().temperature = temperature;
        helper.runAtTickTime(3, () -> networkAt(helper).contents().air = PipePhysics.moles(atmospheres, 293f, networkAt(helper).volume()));
        return converter;
    }

    private static PipeNetwork networkAt(GameTestHelper helper)
    {
        final PipeNetwork network = PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(new BlockPos(3, 1, 4)));
        helper.assertTrue(network != null, "The vessel and converter should form a network");
        return network;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = IRON_BATCH)
    public static void blowingBurnsCarbonAndConservesItAsGas(GameTestHelper helper)
    {
        final ConverterBlockEntity converter = converterWithAir(helper, 1000, 1400f, 3f);
        helper.runAtTickTime(100, () -> {
            final ConverterBath bath = converter.bath();
            helper.assertTrue(bath.carbon < ConverterBath.Grade.PIG_IRON.carbon() || bath.silicon < 1f, "The blast should have burned something, carbon " + bath.carbon + ", silicon " + bath.silicon);
            final float pressure = networkAt(helper).pressure();
            helper.assertTrue(pressure < 1.5f, "The receiver should have blown down to the head of the iron, at " + pressure);

            final float carbonGone = (1000f * ConverterBath.Grade.PIG_IRON.carbon() - bath.metal * bath.carbon) * ConverterBath.KG_PER_MILLIBUCKET * 10f / 12.011f;
            final float gas = carbon(helper, 4, 1, 4);
            helper.assertTrue(Math.abs(gas - carbonGone) < Math.max(0.5f, 0.05f * carbonGone), "Carbon must all still be somewhere: " + carbonGone + " mol left the iron and " + gas + " mol reached the air");

            final float oxygenUsed = (PipePhysics.moles(3f, 293f, networkAt(helper).volume()) - networkAt(helper).contents().air) * 0.2095f;
            final float siliconGone = (1000f * ConverterBath.Grade.PIG_IRON.silicon() - bath.metal * bath.silicon) * ConverterBath.KG_PER_MILLIBUCKET * 10f / 28.086f;
            helper.assertTrue(oxygenUsed >= carbonGone / 2f + siliconGone - 0.1f, "The oxygen spent must at least pay for what burned, " + oxygenUsed + " mol");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aBathTapsAsWhatItHasBecome(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity converter = helper.getBlockEntity(pos);
        converter.tank().fill(new FluidStack(metal(Metal.PIG_IRON), 200), IFluidHandler.FluidAction.EXECUTE);
        converter.bath().temperature = 1600f;
        converter.bath().silicon = 0f;
        converter.bath().carbon = 0.5f;

        final FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
        final ItemStack mold = new ItemStack(TFCItems.MOLDS.get(Metal.ItemType.INGOT).asItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, mold);
        converter.interact(mold, player, InteractionHand.MAIN_HAND);

        final ItemStack filled = player.getItemInHand(InteractionHand.MAIN_HAND);
        final @Nullable var handler = filled.getCapability(Capabilities.FluidHandler.ITEM);
        helper.assertTrue(handler != null && handler.getFluidInTank(0).getFluid() == metal(Metal.STEEL), "It should tap as steel, got " + (handler == null ? null : handler.getFluidInTank(0).getFluid()));
        helper.assertTrue(handler.getFluidInTank(0).getAmount() == 100, "A mould holds 100 mB");
        helper.assertTrue(Math.abs(converter.bath().metal - 100f) < 0.01f, "The rest stays in the vessel");
        final var heat = HeatCapability.get(filled);
        helper.assertTrue(heat != null && heat.getTemperature() > 300f, "The metal brings its heat to the mould, which takes much of it, got " + (heat == null ? 0f : heat.getTemperature()));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = IRON_BATCH)
    public static void ironThatHasSetCannotBeBlown(GameTestHelper helper)
    {
        final ConverterBlockEntity converter = converterWithAir(helper, 1000, 1000f, 3f);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(networkAt(helper).pressure() > 2.9f, "The tuyeres are sealed, but pressure fell to " + networkAt(helper).pressure());
            helper.assertTrue(converter.bath().carbon == ConverterBath.Grade.PIG_IRON.carbon(), "Nothing should burn");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = IRON_BATCH)
    public static void aWeakBlastCannotLiftTheIron(GameTestHelper helper)
    {
        final ConverterBlockEntity converter = converterWithAir(helper, 2000, 1400f, 1.3f); // Well above what bellows give, and still short of 1.7 atm
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(converter.bath().silicon == ConverterBath.Grade.PIG_IRON.silicon(), "Nothing should burn, silicon is at " + converter.bath().silicon);
            helper.succeed();
        });
    }

    /** In a batch of its own, as the blast would break into the rooms of whatever test was next to it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "tfcpp_steam")
    public static void waterInTheBlastLineBlowsTheVesselApart(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        converterWithAir(helper, 1000, 1400f, 1.1f);
        // Other networks in reach of the blast, as a network ended while they are being walked would crash the server
        final Block pipe = Pipes.PIPES.get(PipeMaterial.WROUGHT_IRON).get();
        for (int x = 1; x <= 7; x += 2)
        {
            helper.setBlock(x, 1, 6, pipe);
            helper.setBlock(x, 1, 2, pipe);
        }
        helper.runAtTickTime(4, () -> {
            final PipeNetwork network = networkAt(helper);
            network.contents().addLiquid("minecraft:water", 20f, 293f);
        });
        helper.runAtTickTime(70, () -> {
            helper.assertBlockNotPresent(Metallurgy.CONVERTER.get(), pos);
            final int ingots = itemsAround(helper, pos, ingot(Metal.PIG_IRON));
            helper.assertTrue(ingots > 0, "The iron should be thrown about as metal to collect, got " + ingots);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = IRON_BATCH)
    public static void aWornLiningBurnsThrough(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        final ConverterBlockEntity converter = converterWithAir(helper, 1000, 1400f, 3f);
        converter.wearLining(1e6f);
        helper.runAtTickTime(60, () -> {
            helper.assertBlockNotPresent(Metallurgy.CONVERTER.get(), pos);
            helper.assertTrue(itemsAround(helper, pos, ingot(Metal.PIG_IRON)) > 0, "The metal should run out, to be dug up afterward");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = IRON_BATCH)
    public static void aTraceOfWaterOnlyHisses(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        converterWithAir(helper, 1000, 1400f, 1.1f);
        helper.runAtTickTime(4, () -> networkAt(helper).contents().addLiquid("minecraft:water", 0.4f, 293f));
        helper.runAtTickTime(70, () -> {
            helper.assertBlockPresent(Metallurgy.CONVERTER.get(), pos);
            helper.assertTrue(networkAt(helper).contents().litres == 0f, "It should have flashed off");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = IRON_BATCH)
    public static void anEmptyVesselLetsTheBlastThrough(GameTestHelper helper)
    {
        converterWithAir(helper, 0, 20f, 3f);
        helper.runAtTickTime(250, () -> {
            helper.assertTrue(networkAt(helper).pressure() < 1.1f, "With no iron on them the tuyeres are just holes, at " + networkAt(helper).pressure());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void ironFromABlockBringsItsOwnHeat(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity converter = helper.getBlockEntity(pos);
        converter.tank().fill(new FluidStack(metal(Metal.PIG_IRON), 100), IFluidHandler.FluidAction.EXECUTE);
        converter.setTemperature(1400f);
        helper.assertTrue(converter.bath().temperature == 1400f, "An empty vessel takes the heat of what arrives, got " + converter.bath().temperature);

        converter.tank().fill(new FluidStack(metal(Metal.PIG_IRON), 100), IFluidHandler.FluidAction.EXECUTE);
        converter.setTemperature(1200f);
        helper.assertTrue(Math.abs(converter.bath().temperature - 1300f) < 0.01f, "Equal parts at 1400 and 1200 should come to 1300, got " + converter.bath().temperature);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void aWornVesselCanBeRelinedAndNotJustReplaced(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, Metallurgy.CONVERTER.get());
        final ConverterBlockEntity converter = helper.getBlockEntity(pos);
        converter.wearLining(4000f);
        helper.assertTrue(converter.isScrap(), "A vessel a third worn should not be worth carrying away");

        // A crumb left from tapping must not stop it being relined
        converter.tank().fill(new FluidStack(metal(Metal.PIG_IRON), 1), IFluidHandler.FluidAction.EXECUTE);
        converter.bath().metal = 100.4f;
        converter.bath().tap(100);
        helper.assertTrue(converter.bath().metal == 0f, "A crumb should count as empty");

        final FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
        final ItemStack bricks = new ItemStack(TFCItems.FIRE_BRICK.asItem(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, bricks);
        converter.interact(bricks, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(bricks.getCount() == 1 && converter.liningWorn() < 4000f, "A brick should go into the lining");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void corrosionOwedSurvivesARebuild(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        final Block pipe = Pipes.PIPES.get(PipeMaterial.WROUGHT_IRON).get();
        helper.setBlock(2, 1, 2, pipe);
        helper.setBlock(3, 1, 2, pipe);
        helper.runAtTickTime(5, () -> {
            final PipeTracker tracker = PipeTracker.get(helper.getLevel());
            final BlockPos absolute = helper.absolutePos(new BlockPos(2, 1, 2));
            final PipeSegment segment = tracker.networkAt(absolute).segments().getFirst();
            segment.corrodedUntil = 12345L;
            helper.setBlock(4, 1, 2, pipe); // Joining another pipe rebuilds the network
        });
        helper.runAtTickTime(15, () -> {
            final PipeSegment oldest = PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(new BlockPos(2, 1, 2))).segments().stream()
                .filter(segment -> segment.corrodedUntil == 12345L).findFirst().orElse(null);
            helper.assertTrue(oldest != null, "A rebuild must not forget what the wall still owes");
            helper.succeed();
        });
    }

    /** The rule of {@link LocalTime}: what happens in one place takes as long whatever a day takes. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = CLOCK_BATCH)
    public static void anAnimalStaysFedForTheSameTicksOnAnyCalendar(GameTestHelper helper)
    {
        Calendars.SERVER.setCalendarTickRate(REAL_TIME_RATE);
        final ItemStack cage = SentinelCageItem.withAnimal();
        final @Nullable SentinelData data = cage.get(Instruments.SENTINEL.get());
        helper.assertTrue(data != null && data.fedUntil() - Calendars.SERVER.getTicks() == 3 * LocalTime.TICKS_PER_DAY, "An animal stays fed for three days of ticks, on a real-time calendar as well");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1500, batch = CORROSION_BATCH)
    public static void pipesCorrodeAsFastOnAnyCalendar(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        Calendars.SERVER.setCalendarTickRate(REAL_TIME_RATE);
        final Block pipe = Pipes.PIPES.get(PipeMaterial.WROUGHT_IRON).get();
        helper.setBlock(2, 1, 2, pipe);
        helper.setBlock(3, 1, 2, pipe);

        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(new BlockPos(2, 1, 2)));
            helper.assertTrue(network != null, "The pipes should join");
            network.contents().addLiquid("tfc:salt_water", 15f, 293f);
        });
        helper.runAtTickTime(1300, () -> {
            final PipeNetwork network = PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(new BlockPos(2, 1, 2)));
            helper.assertTrue(network != null, "The pipes should still be whole");
            final float fill = network.contents().liquidVolume() / network.volume();
            final float expected = PipeMaterial.WROUGHT_IRON.corrosion("tfc:salt_water") * fill * 1200f / LocalTime.TICKS_PER_DAY;
            helper.assertTrue(expected > 0f, "Salt water must corrode iron");
            final float lost = 1f - network.segments().getFirst().integrity;
            helper.assertTrue(lost > expected * 0.5f && lost < expected * 2f, "The minute between checks should do " + expected + " of corrosion, got " + lost);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = COOLING_BATCH)
    public static void heatMovesAsFastOnAnyCalendar(GameTestHelper helper)
    {
        Calendars.SERVER.setCalendarTickRate(REAL_TIME_RATE);
        final BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Pipes.PRESSURE_VESSEL.get());

        for (int tick = 5; tick < 50; tick++)
        {
            helper.runAtTickTime(tick, () -> {
                final PressureVesselBlockEntity vessel = helper.getBlockEntity(pos);
                vessel.setTemperature(300f); // As a fire does every tick. Not much more, as this drum is sealed and would burst.
            });
        }
        helper.runAtTickTime(50, () -> {
            final float temperature = PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(pos)).contents().temperature;
            helper.assertTrue(temperature > 400f, "A fire should heat the drum to about halfway, as it loses heat to the air as readily as it takes it from the fire, got " + temperature);
        });
        helper.runAtTickTime(200, () -> {
            final float temperature = PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(pos)).contents().temperature;
            helper.assertTrue(temperature < 350f, "Without the fire the drum should soon be cold, got " + temperature);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void swallowedMercuryTakesItsTime(GameTestHelper helper)
    {
        final @Nullable Drinkable mercury = Drinkable.get(Metallurgy.MERCURY.get());
        helper.assertTrue(mercury != null, "Mercury in a jug must be something a person can drink");

        final FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
        final BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        mercury.onDrink(player, 100);
        final BreathingState state = player.getData(GasAttachments.BREATHING);
        helper.assertTrue(state.mercuryMg == 0f, "Nothing should be in the blood yet, got " + state.mercuryMg);
        helper.assertTrue(Physiology.BodyBurden.MERCURY.severity(state.swallowedMercuryMg) == 3, "A full jug should hold a fatal dose, got " + state.swallowedMercuryMg + " mg");

        Breathing.tick(player);
        helper.assertTrue(state.mercuryMg > 0f, "It should start to reach the blood");
        helper.assertTrue(Physiology.BodyBurden.MERCURY.severity(state.mercuryMg) == 0, "A person should feel nothing after a moment, got " + state.mercuryMg + " mg");

        final FakePlayer other = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test2"));
        final @Nullable Drinkable water = Drinkable.get(Fluids.WATER);
        helper.assertTrue(water != null, "Water should be drinkable");
        water.onDrink(other, 100);
        helper.assertTrue(other.getData(GasAttachments.BREATHING).mercuryMg == 0f, "Water must not poison anyone");
        helper.succeed();
    }

    private IronGameTests() {}
}
