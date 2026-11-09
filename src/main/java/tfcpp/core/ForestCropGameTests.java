package tfcpp.core;

import java.util.ArrayList;
import java.util.List;
import net.dries007.tfc.common.blockentities.CropBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.crop.Crop;
import net.dries007.tfc.common.blocks.crop.CropBlock;
import net.dries007.tfc.common.blocks.crop.CropHelpers;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.dries007.tfc.common.blocks.wood.TFCLeavesBlock;
import net.dries007.tfc.common.blocks.wood.Wood;
import net.dries007.tfc.common.component.food.FoodCapability;
import net.dries007.tfc.common.items.Food;
import net.dries007.tfc.common.items.Powder;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.common.recipes.QuernRecipe;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.ecology.CropSoil;
import tfcpp.ecology.EcologyAttachments;
import tfcpp.ecology.FoodContamination;
import tfcpp.ecology.FoodEvents;
import tfcpp.ecology.FoodMetals;
import tfcpp.ecology.ForestHealth;
import tfcpp.ecology.LevelWaters;
import tfcpp.ecology.Pollutant;
import tfcpp.ecology.Pollution;
import tfcpp.ecology.RegionalAir;
import tfcpp.ecology.SoilCell;
import tfcpp.ecology.SoilConstants;
import tfcpp.ecology.SoilOrder;
import tfcpp.ecology.Waters;
import tfcpp.pneumatics.gas.BreathingState;
import tfcpp.pneumatics.gas.GasAttachments;

/**
 * Stage D of v1.5: soil, crops and forests respond. Every test works in a chunk of its own, far from the others, in a region that nothing has fallen on. The blocks are built in the air above the ground.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForestCropGameTests
{
    private static final String TEMPLATE = "empty9";
    private static final int FIRST_CHUNK_X = 9000, CHUNK_Z = 9000, SLOT_CHUNKS = 16;
    private static final double DAY = SoilConstants.CALENDAR_TICKS_PER_DAY;
    /** Hours and ticks of a TFC year, the unit of the nature of the mod. */
    private static final long YEAR_HOURS = (long) (SoilConstants.daysPerYear() * 24.0), YEAR_TICKS = YEAR_HOURS * 1000L;

    private static LevelChunk chunk(ServerLevel level, int slot)
    {
        final int chunkX = FIRST_CHUNK_X + slot * SLOT_CHUNKS, chunkZ = CHUNK_Z;
        level.setChunkForced(chunkX, chunkZ, true);
        final LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        chunk.removeData(EcologyAttachments.CHUNK);
        chunk.removeData(EcologyAttachments.WATER_CHUNK);
        RegionalAir.get(level).clearCellAt(chunkX << 4, chunkZ << 4);
        return chunk;
    }

    private static void release(ServerLevel level, LevelChunk chunk)
    {
        level.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
        final LevelWaters waters = LevelWaters.get(level);
        waters.setLakeDetector(null);
        waters.reset();
        RegionalAir.get(level).clearCellAt(chunk.getPos().x << 4, chunk.getPos().z << 4);
    }

    private static BlockPos spot(ServerLevel level, LevelChunk chunk, int above)
    {
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + above, z);
    }

    private static void farmland(ServerLevel level, BlockPos farmland)
    {
        level.setBlock(farmland, TFCBlocks.SOIL.get(SoilBlockType.FARMLAND).get(SoilBlockType.Variant.ENTISOL).get().defaultBlockState(), 2);
        level.setBlock(farmland.west(), Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(farmland.west().below(), Blocks.STONE.defaultBlockState(), 2);
    }

    private static BlockState wheat(boolean ripe)
    {
        final CropBlock block = (CropBlock) TFCBlocks.CROPS.get(Crop.WHEAT).get();
        return block.defaultBlockState().setValue(block.getAgeProperty(), ripe ? block.getMaxAge() : 0);
    }

    private static void clear(ServerLevel level, BlockPos farmland)
    {
        for (BlockPos pos : BlockPos.betweenClosed(farmland.offset(-2, -1, -2), farmland.offset(2, 3, 2)))
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** A climate in which wheat grows: 12 C and 300 mm, wherever the crop stands. The flat world of the tests has none that suits it. */
    private static final net.dries007.tfc.util.climate.ClimateModel MILD = new net.dries007.tfc.util.climate.ClimateModel()
    {
        
        public net.dries007.tfc.util.climate.ClimateModelType<?> type()
        {
            return null;
        }

        
        public float getAverageTemperature(net.minecraft.world.level.LevelReader level, BlockPos pos)
        {
            return 12.0f;
        }

        
        public float getAverageRainfall(net.minecraft.world.level.LevelReader level, BlockPos pos)
        {
            return 300.0f;
        }
    };

    private static double growthOf(ServerLevel level, BlockPos crop)
    {
        final var tracker = net.dries007.tfc.util.tracker.WorldTracker.get(level);
        final var before = tracker.getClimateModel();
        tracker.setClimateModel(MILD);
        try
        {
            return growthIn(level, crop);
        }
        finally
        {
            tracker.setClimateModel(before);
        }
    }

    private static double growthIn(ServerLevel level, BlockPos crop)
    {
        final CropBlockEntity be = (CropBlockEntity) level.getBlockEntity(crop);
        final long now = Calendars.SERVER.getTicks();
        be.setLastGrowthTick(now - (long) (2 * DAY));
        final boolean alive = CropHelpers.growthTickStep(level, crop, level.getBlockState(crop), level.getRandom(), now - (long) (2 * DAY), now, be);
        return alive ? be.getGrowth() : -1.0;
    }

    // --- Crops ---------------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_forest_1")
    public static void aCropOnAcidSoilGrowsSlowerAndBelowPh4_5ItDies(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk neutral = chunk(level, 0), acid = chunk(level, 1);
        final BlockPos good = spot(level, neutral, 12), bad = spot(level, acid, 12);
        try
        {
            for (BlockPos farm : new BlockPos[] {good, bad})
            {
                farmland(level, farm);
                level.setBlock(farm.above(), wheat(false), 2);
            }
            Pollution.seed(level, good, SoilOrder.ENTISOL, false);
            final SoilCell soil = Pollution.seed(level, bad, SoilOrder.ENTISOL, false);
            Pollution.deposit(level, bad, Pollutant.ACIDITY, 34.8 * SoilConstants.AREA); // pH 6.5 down to 5.0
            helper.assertTrue(Math.abs(Pollution.view(level, bad).pH() - 5.0) < 0.05, "pH 5.0, " + Pollution.view(level, bad).pH());
            helper.assertTrue(Math.abs(CropSoil.at(level, bad.above()).growth() - 0.6) < 0.03, "growth multiplier 0.6 at pH 5.0, " + CropSoil.at(level, bad.above()).growth());
            helper.assertTrue(CropSoil.at(level, good.above()).growth() == 1.0, "and 1 on the good soil");

            final double fast = growthOf(level, good.above()), slow = growthOf(level, bad.above());
            helper.assertTrue(fast > 0.0, "The crop on neutral soil grew in this world: " + fast);
            helper.assertTrue(slow >= 0.0 && slow < 0.8 * fast, "and the one on acid soil grew less: " + slow + " against " + fast);

            Pollution.deposit(level, bad, Pollutant.ACIDITY, 16.0 * SoilConstants.AREA); // below 4.5
            helper.assertTrue(Pollution.view(level, bad).pH() < 4.5, "pH below 4.5: " + Pollution.view(level, bad).pH());
            helper.assertTrue(CropSoil.at(level, bad.above()).unhealthy(), "aluminium makes the step unhealthy");
            helper.assertTrue(growthOf(level, bad.above()) < 0.0, "and the crop dies");
            helper.assertTrue(!(level.getBlockState(bad.above()).getBlock() instanceof CropBlock), "it is a dead crop now");
            helper.assertTrue(growthOf(level, good.above()) >= 0.0, "the one on good soil lives");
            helper.succeed();
        }
        finally
        {
            clear(level, good);
            clear(level, bad);
            release(level, neutral);
            release(level, acid);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_forest_2")
    public static void sulfurDioxideInTheAirSlowsCropsAndAnAcuteHourKillsThem(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 2);
        final BlockPos farm = spot(level, chunk, 12);
        try
        {
            final var grid = RegionalAir.get(level).grid();
            final var region = grid.cell(chunk.getPos().x >> 3, chunk.getPos().z >> 3);
            final long hour = tfcpp.ecology.AirCadence.hourOf(Calendars.SERVER.getCalendarTicks());
            final var recordHour = (java.util.function.BiConsumer<Long, Double>) region::recordHour;
            for (long h = hour - YEAR_HOURS; h <= hour; h++)
            {
                recordHour.accept(h, 150.0); // a year at 150 ug/m3
            }
            helper.assertTrue(region.annualSo2(hour) > 80.0 && region.annualSo2(hour) < 100.0, "annual mean " + region.annualSo2(hour));
            final double multiplier = CropSoil.at(level, farm.above()).growth();
            helper.assertTrue(multiplier < 0.8 && multiplier > 0.3, "crops lose growth: " + multiplier);
            helper.assertTrue(!CropSoil.at(level, farm.above()).unhealthy(), "but a clean hour does not injure them");
            grid.emit(chunk.getPos().x >> 3, chunk.getPos().z >> 3, tfcpp.ecology.AirSpecies.SULFUR_DIOXIDE, tfcpp.ecology.AirScale.molesFor(2000.0, 64.07));
            helper.assertTrue(CropSoil.at(level, farm.above()).unhealthy(), "an hour above 1000 ug/m3 injures them at once");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_forest_10")
    public static void aNaturallyAcidSoilGrowsItsCropAtBaselineAndLimingItHelps(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 13);
        final BlockPos farm = spot(level, chunk, 12), crop = farm.above();
        try
        {
            Pollution.seed(level, farm, SoilOrder.PODZOL, false);
            helper.assertTrue(Pollution.view(level, farm).pH() < 5.0, "A podzol is acid: " + Pollution.view(level, farm).pH());
            helper.assertTrue(CropSoil.at(level, crop).growth() == 1.0 && !CropSoil.at(level, crop).unhealthy(), "and its crop grows at baseline, nothing dies");
            Pollution.lime(level, farm, 8.0 * SoilConstants.AREA);
            helper.assertTrue(CropSoil.at(level, crop).growth() > 1.0, "Liming it above its start helps: " + CropSoil.at(level, crop).growth());
            Pollution.deposit(level, farm, Pollutant.ACIDITY, 40.0 * SoilConstants.AREA);
            helper.assertTrue(Pollution.view(level, farm).pH() < 4.5 && CropSoil.at(level, crop).unhealthy(), "Acid that takes it under 4.5 and under its start kills");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    // --- Metals in food -----------------------------------------------------------------------------------------------------------------------------------------

    private static double leadOf(ItemStack stack)
    {
        final @Nullable double[] metals = FoodContamination.get(stack);
        return metals == null ? 0.0 : metals[FoodMetals.indexOf(Pollutant.LEAD)];
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "tfcpp_forest_3")
    public static void aCropFromLeadSoilCarriesLeadThroughTheKitchenAndEatingAddsToTheBody(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 3);
        final BlockPos farm = spot(level, chunk, 12), crop = farm.above();
        try
        {
            farmland(level, farm);
            Pollution.seed(level, farm, SoilOrder.ENTISOL, false);
            final double grams = 1000.0 * 650.0 / 1000.0 * SoilConstants.AREA; // 1000 mg/kg
            Pollution.deposit(level, farm, Pollutant.LEAD, grams);

            // Broken: the drops of the crop are marked, and the soil gives up what they carry
            level.setBlock(crop, wheat(true), 2);
            ((CropBlockEntity) level.getBlockEntity(crop)).setYield(1.0f);
            final int lootSize = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(crop), level, crop, level.getBlockEntity(crop)).size();
            final List<ItemEntity> drops = new ArrayList<>();
            final java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockDropsEvent> spy = event -> drops.addAll(event.getDrops());
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, spy);
            try
            {
                level.destroyBlock(crop, true);
            }
            finally
            {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(spy);
            }
            ItemStack grain = ItemStack.EMPTY;
            for (ItemEntity drop : drops)
            {
                if (FoodCapability.has(drop.getItem()) && leadOf(drop.getItem()) > 0.0)
                {
                    grain = drop.getItem().copyWithCount(1);
                }
            }
            helper.assertTrue(!grain.isEmpty(), "The food that the crop dropped carries lead, " + drops.size() + " drops, loot " + lootSize + ", state " + level.getBlockState(crop));
            // 0.01 x 1000 mg/kg x 0.04 kg = 0.4 mg = 400 ug, within the step of the scale
            helper.assertTrue(leadOf(grain) > 400.0 / 1.25 && leadOf(grain) < 400.0 * 1.25, "About 400 micrograms of lead an item: " + leadOf(grain));
            helper.assertTrue(Pollution.view(level, farm).totalHarvested(Pollutant.LEAD) > 0.0, "and the soil has given it up");
            helper.assertTrue(Pollution.view(level, farm).total(Pollutant.LEAD) < grams, "so there is less in it");
            helper.assertTrue(grain.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY, null, net.minecraft.world.item.TooltipFlag.NORMAL).stream().noneMatch(line -> line.getString().toLowerCase().contains("lead") || line.getString().toLowerCase().contains("metal")), "No tooltip tells of it");

            // Ground in a quern: the flour holds it
            final ItemStack input = new ItemStack(TFCItems.FOOD.get(Food.WHEAT_GRAIN).get(), 1);
            FoodContamination.set(input, new double[] {400.0, 0, 0, 0, 0, 0, 0});
            final QuernRecipe recipe = QuernRecipe.getRecipe(input);
            helper.assertTrue(recipe != null, "There is a quern recipe for grain");
            final ItemStack flour = recipe.getResult().getSingleStack(input);
            helper.assertTrue(FoodCapability.has(flour) && leadOf(flour) > 0.0, "Flour has the lead of its grain: " + flour);
            helper.assertTrue(Math.abs(leadOf(flour) * flour.getCount() - leadOf(input)) < leadOf(input) * 0.3, "all of it, shared among the pieces");

            // Cooked in a crafting recipe of many foods
            final @Nullable double[] crafted = carriesThroughSomeCraftingRecipe(level);
            helper.assertTrue(crafted != null && crafted[0] > 0.0, "A crafting recipe that makes food from contaminated food keeps its metal");
            helper.assertTrue(crafted[0] < crafted[1] * 400.0 * 1.5 && crafted[0] > crafted[1] * 400.0 / 1.5, "and it counts one item for each slot, though each slot held 32: " + crafted[0] + " micrograms from " + crafted[1] + " slots of 400 each");

            // Eaten: the burden of lead rises
            final ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            final BreathingState state = player.getData(GasAttachments.BREATHING);
            final float before = state.leadMg;
            final ItemStack eating = grain.copyWithCount(2);
            player.setItemInHand(InteractionHand.MAIN_HAND, eating);
            final ItemStack eatenCopy = eating.copy();
            final ItemStack result = eating.finishUsingItem(level, player);
            net.neoforged.neoforge.event.EventHooks.onItemUseFinish(player, eatenCopy, 0, result); // What LivingEntity does when a meal is eaten
            helper.assertTrue(Math.abs(state.leadMg - before - 0.1 * leadOf(grain) * 1e-3) < 1e-4, "10% of it joins the lead in the body: " + (state.leadMg - before));

            // Picked from a crop that keeps growing: the same
            final BlockPos farm2 = farm.east(3);
            farmland(level, farm2);
            final net.dries007.tfc.common.blocks.crop.CropBlock pepper = (net.dries007.tfc.common.blocks.crop.CropBlock) TFCBlocks.CROPS.get(Crop.RED_BELL_PEPPER).get();
            final BlockState ripe = pepper.defaultBlockState().setValue(pepper.getAgeProperty(), pepper.getMaxAge());
            level.setBlock(farm2.above(), ripe, 2);
            final CropBlockEntity pepperEntity = (CropBlockEntity) level.getBlockEntity(farm2.above());
            pepperEntity.setYield(1.0f);
            ((net.dries007.tfc.common.blocks.crop.IPickableCrop) pepper).getItemInteractionResult(ripe, level, farm2.above(), player, pepperEntity);
            boolean pickedLead = false;
            for (ItemStack held : player.getInventory().items)
            {
                pickedLead |= FoodCapability.has(held) && leadOf(held) > 0.0 && !held.is(grain.getItem());
            }
            helper.assertTrue(pickedLead, "Peppers picked from a crop that stays in the ground carry lead too");

            // Stacking: like and unlike
            final ItemStack a = grain.copy(), b = grain.copy(), clean = new ItemStack(grain.getItem());
            FoodCapability.setCreationDate(clean, FoodCapability.getRoundedCreationDate());
            FoodContamination.set(b, new double[] {leadOf(grain) * 1.05, 0, 0, 0, 0, 0, 0});
            helper.assertTrue(ItemStack.isSameItemSameComponents(a, b), "Items a few per cent apart are one stack");
            FoodContamination.set(b, new double[] {leadOf(grain) * 10.0, 0, 0, 0, 0, 0, 0});
            helper.assertTrue(!ItemStack.isSameItemSameComponents(a, b), "a tenfold difference is not");
            helper.succeed();
        }
        finally
        {
            clear(level, farm);
            release(level, chunk);
        }
    }

    /** @return {micrograms of lead in the output, slots that held contaminated food} of the first crafting recipe that makes food from it, with each food slot holding 32; or null if there is none */
    @Nullable
    private static double[] carriesThroughSomeCraftingRecipe(ServerLevel level)
    {
        for (var holder : level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING))
        {
            if (!(holder.value() instanceof net.dries007.tfc.common.recipes.AdvancedShapelessRecipe recipe))
            {
                continue;
            }
            final List<ItemStack> items = new ArrayList<>();
            boolean complete = true, food = false;
            int slots = 0;
            for (var ingredient : recipe.getIngredients())
            {
                final ItemStack[] options = ingredient.getItems();
                if (options.length == 0)
                {
                    complete = false;
                    break;
                }
                ItemStack pick = options[0].copyWithCount(1);
                if (FoodCapability.has(pick))
                {
                    pick = pick.copyWithCount(32);
                    FoodContamination.set(pick, new double[] {400.0, 0, 0, 0, 0, 0, 0});
                    food = true;
                    slots++;
                }
                items.add(pick);
            }
            if (!complete || !food || items.size() > 9)
            {
                continue;
            }
            final var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, pad(items));
            if (!recipe.matches(input, level))
            {
                continue;
            }
            final ItemStack out = recipe.assemble(input, level.registryAccess());
            if (FoodCapability.has(out))
            {
                return new double[] {leadOf(out) * out.getCount(), slots};
            }
        }
        return null;
    }

    private static List<ItemStack> pad(List<ItemStack> items)
    {
        final List<ItemStack> padded = new ArrayList<>(items);
        while (padded.size() < 9)
        {
            padded.add(ItemStack.EMPTY);
        }
        return padded;
    }

    // --- Forest --------------------------------------------------------------------------------------------------------------------------------------------------------

    private static int ticksUntilGone(ServerLevel level, BlockPos pos, int limit)
    {
        for (int i = 0; i < limit; i++)
        {
            final BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof TFCLeavesBlock))
            {
                return i;
            }
            state.randomTick(level, pos, level.getRandom());
        }
        return -1;
    }

    /** A leaf with a log under it, so that TFC's own decay by distance has no say. */
    private static void leafOnLog(ServerLevel level, BlockPos pos, BlockState leaf)
    {
        level.setBlock(pos.below(), TFCBlocks.WOODS.get(Wood.PINE).get(Wood.BlockType.LOG).get().defaultBlockState(), 2);
        level.setBlock(pos, leaf, 2);
    }

    /** The first position along a row of the chunk whose hash is under (or over) a value. */
    private static BlockPos hashed(ServerLevel level, LevelChunk chunk, int y, boolean under, double value)
    {
        for (int dx = 0; dx < 16; dx++)
        {
            for (int dz = 0; dz < 16; dz++)
            {
                final BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + dx, y, chunk.getPos().getMinBlockZ() + dz);
                final double hash = ForestHealth.positionHash(pos.asLong());
                if (under ? hash < value : hash > value)
                {
                    return pos;
                }
            }
        }
        throw new IllegalStateException("no such position");
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "tfcpp_forest_4")
    public static void leavesOfADyingStandFallOnRandomTicksAndOnlyThoseThatTheHashDooms(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk sick = chunk(level, 4), well = chunk(level, 5), mid = chunk(level, 6);
        final BlockPos sickPos = spot(level, sick, 12), wellPos = spot(level, well, 12), midPos = spot(level, mid, 12);
        final List<BlockPos> built = new ArrayList<>();
        try
        {
            final BlockState pine = TFCBlocks.WOODS.get(Wood.PINE).get(Wood.BlockType.LEAVES).get().defaultBlockState().setValue(TFCLeavesBlock.PERSISTENT, false);
            final long now = Calendars.SERVER.getCalendarTicks();
            Pollution.seed(level, sickPos, SoilOrder.ENTISOL, false).setForestHealth(0.0, 0.0, now, 0.0, 0.0);
            Pollution.seed(level, wellPos, SoilOrder.ENTISOL, false);
            Pollution.seed(level, midPos, SoilOrder.ENTISOL, false).setForestHealth(0.65, 0.65, now, 0.0, 0.0);
            helper.assertTrue(Pollution.forestHealth(level, sickPos, true) < 0.05, "The stand is nearly dead: " + Pollution.forestHealth(level, sickPos, true));
            helper.assertTrue(Pollution.forestHealth(level, wellPos, true) == 1.0, "and the other is well");

            // Dying stand: a leaf with a log beside it falls (any hash over about nothing)
            final BlockPos doomedInSick = hashed(level, sick, sickPos.getY(), false, 0.2);
            leafOnLog(level, doomedInSick, pine);
            built.add(doomedInSick);
            helper.assertTrue(ticksUntilGone(level, doomedInSick, 3000) >= 0, "A pine leaf in the dying stand falls, though it has a log beside it");

            // Healthy stand: the same leaf with a log stays
            leafOnLog(level, wellPos, pine);
            built.add(wellPos);
            helper.assertTrue(ticksUntilGone(level, wellPos, 3000) < 0, "A leaf of a healthy stand stays");
            helper.assertTrue(level.getBlockState(wellPos).getBlock() instanceof TFCLeavesBlock, "it is still there");

            // Stand at 0.65: the leaves whose hash is over 0.65 are the doomed, the others stay
            final BlockPos doomed = hashed(level, mid, midPos.getY(), false, 0.65), spared = hashed(level, mid, midPos.getY(), true, 0.6);
            leafOnLog(level, doomed, pine);
            leafOnLog(level, spared, pine);
            built.add(doomed);
            built.add(spared);
            helper.assertTrue(ticksUntilGone(level, doomed, 4000) >= 0, "In a stand at 0.65 a leaf with a hash over it falls");
            helper.assertTrue(ticksUntilGone(level, spared, 4000) < 0, "and one with a hash under it stays, so that about a third of the canopy goes");

            // A leaf placed by a hand stays
            level.setBlock(doomedInSick, pine.setValue(TFCLeavesBlock.PERSISTENT, true), 2);
            helper.assertTrue(ticksUntilGone(level, doomedInSick, 1500) < 0, "A persistent leaf does not fall");
            helper.succeed();
        }
        finally
        {
            for (BlockPos pos : built)
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2);
            }
            release(level, sick);
            release(level, well);
            release(level, mid);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "tfcpp_forest_5")
    public static void conifersSufferWhereBroadleavesDoNot(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 7);
        final BlockPos pos = spot(level, chunk, 12);
        try
        {
            final var region = RegionalAir.get(level).grid().cell(chunk.getPos().x >> 3, chunk.getPos().z >> 3);
            final long hour = tfcpp.ecology.AirCadence.hourOf(Calendars.SERVER.getCalendarTicks());
            final var recordHour = (java.util.function.BiConsumer<Long, Double>) region::recordHour;
            for (long h = hour - YEAR_HOURS * 10; h <= hour; h++)
            {
                recordHour.accept(h, 20.0); // ten TFC years at 20 ug/m3: over the critical level of conifers (10), and the annual mean never reaches that of broadleaves (20)
            }
            Pollution.seed(level, pos, SoilOrder.ENTISOL, false).setForestHealth(1.0, 1.0, Calendars.SERVER.getCalendarTicks() - 10 * YEAR_TICKS, 0.0, 0.0);
            final double conifer = Pollution.forestHealth(level, pos, true), broadleaf = Pollution.forestHealth(level, pos, false);
            helper.assertTrue(conifer < 0.9, "Conifers suffer at 20: " + conifer);
            helper.assertTrue(broadleaf == 1.0, "and broadleaves do not: " + broadleaf);
            helper.assertTrue(tfcpp.ecology.ForestTrees.isConifer(TFCBlocks.WOODS.get(Wood.SPRUCE).get(Wood.BlockType.LEAVES).get()), "TFC's flag: spruce leaves");
            helper.assertTrue(tfcpp.ecology.ForestTrees.isConifer(TFCBlocks.WOODS.get(Wood.PINE).get(Wood.BlockType.SAPLING).get()), "pine sapling");
            helper.assertTrue(!tfcpp.ecology.ForestTrees.isConifer(TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.LEAVES).get()), "but not oak");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }

    /**
     * Plants a sapling with a counter that says it has waited a hundred days, and gives it random ticks.
     *
     * @return {@code [grew (1 or 0), calendar ticks on its counter afterwards]}
     */
    private static long[] sapling(ServerLevel level, BlockPos pos, int ticks)
    {
        final BlockState sapling = TFCBlocks.WOODS.get(Wood.PINE).get(Wood.BlockType.SAPLING).get().defaultBlockState();
        level.setBlock(pos.below(), TFCBlocks.SOIL.get(SoilBlockType.DIRT).get(SoilBlockType.Variant.ENTISOL).get().defaultBlockState(), 2);
        level.setBlock(pos, sapling, 2);
        ((net.dries007.tfc.common.blockentities.TickCounterBlockEntity) level.getBlockEntity(pos)).setLastUpdateTick(Calendars.SERVER.getTicks() - 100L * (long) DAY);
        for (int i = 0; i < ticks; i++)
        {
            final BlockState state = level.getBlockState(pos);
            if (state.getBlock() != sapling.getBlock())
            {
                return new long[] {1L, -1L};
            }
            state.randomTick(level, pos, level.getRandom());
        }
        return new long[] {0L, ((net.dries007.tfc.common.blockentities.TickCounterBlockEntity) level.getBlockEntity(pos)).getTicksSinceUpdate()};
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "tfcpp_forest_6")
    public static void saplingsInADyingStandDoNotGrowAndTheirCounterIsReset(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk sick = chunk(level, 8), well = chunk(level, 12);
        final BlockPos pos = spot(level, sick, 12), wellPos = spot(level, well, 12);
        try
        {
            final long now = Calendars.SERVER.getCalendarTicks();
            Pollution.seed(level, pos, SoilOrder.ENTISOL, false).setForestHealth(0.0, 0.0, now, 0.0, 0.0);
            Pollution.seed(level, wellPos, SoilOrder.ENTISOL, false);

            final long[] stunted = sapling(level, pos, 2000);
            helper.assertTrue(stunted[0] == 0L, "It was still a sapling after 2000 random ticks in a dead stand");
            helper.assertTrue(stunted[1] >= 0L && stunted[1] < (long) DAY, "and its counter was reset by each tick that held it back: " + stunted[1]);

            // The same sapling in a healthy stand is not held back: it grows, or if the world has no room for its tree its counter keeps the hundred days it waited
            level.setDayTime(6000L);
            final long[] free = sapling(level, wellPos, 2000);
            helper.assertTrue(free[0] == 1L || free[1] >= 99L * (long) DAY, "A sapling of a healthy stand is let grow: grew " + free[0] + ", counter " + free[1]);
            helper.succeed();
        }
        finally
        {
            for (BlockPos at : new BlockPos[] {pos, wellPos})
            {
                for (BlockPos cleared : BlockPos.betweenClosed(at.offset(-6, -1, -6), at.offset(6, 14, 6)))
                {
                    level.setBlock(cleared, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            release(level, sick);
            release(level, well);
        }
    }

    // --- Lime and irrigation ------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "tfcpp_forest_7")
    public static void limeOnTheGroundRaisesThePhAndKeepsTheMetals(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 9);
        final BlockPos ground = spot(level, chunk, 12);
        try
        {
            level.setBlock(ground, TFCBlocks.SOIL.get(SoilBlockType.DIRT).get(SoilBlockType.Variant.ENTISOL).get().defaultBlockState(), 2);
            Pollution.seed(level, ground, SoilOrder.ENTISOL, false);
            Pollution.deposit(level, ground, Pollutant.ACIDITY, 60.0 * SoilConstants.AREA);
            Pollution.deposit(level, ground, Pollutant.LEAD, 500.0);
            final double before = Pollution.view(level, ground).pH();
            helper.assertTrue(before < 4.5, "Acid soil, pH " + before);

            final ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            for (Powder powder : new Powder[] {Powder.LIME, Powder.FLUX, Powder.WOOD_ASH})
            {
                final ItemStack stack = new ItemStack(TFCItems.POWDERS.get(powder).get(), 20);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                final double acidBefore = Pollution.view(level, ground).total(Pollutant.ACIDITY);
                player.setShiftKeyDown(false);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, ground, new BlockHitResult(Vec3.atCenterOf(ground), net.minecraft.core.Direction.UP, ground, false)));
                helper.assertTrue(stack.getCount() == 20 && Pollution.view(level, ground).total(Pollutant.ACIDITY) == acidBefore, powder + ": without sneaking nothing is spread");
                player.setShiftKeyDown(true);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, ground, new BlockHitResult(Vec3.atCenterOf(ground), net.minecraft.core.Direction.UP, ground, false)));
                helper.assertTrue(stack.isEmpty() || stack.getCount() == 0, powder + ": the whole stack was spread, " + stack.getCount() + " left");
                helper.assertTrue(Pollution.view(level, ground).total(Pollutant.ACIDITY) < acidBefore, powder + " neutralised acid");
            }
            helper.assertTrue(Pollution.view(level, ground).pH() > before, "The pH rose, though a few stacks are a few eq/m2 only: " + Pollution.view(level, ground).pH());
            helper.assertTrue(Math.abs(Pollution.view(level, ground).total(Pollutant.LEAD) - 500.0) < 1e-9, "and the lead is still there");

            // Wood ash on a field fertilises through TFC, and limes it too, a piece at a time
            player.setShiftKeyDown(false);
            level.setBlock(ground, TFCBlocks.SOIL.get(SoilBlockType.FARMLAND).get(SoilBlockType.Variant.ENTISOL).get().defaultBlockState(), 2);
            final ItemStack ash = new ItemStack(TFCItems.POWDERS.get(Powder.WOOD_ASH).get(), 10);
            player.setItemInHand(InteractionHand.MAIN_HAND, ash);
            final double acidFarm = Pollution.view(level, ground).total(Pollutant.ACIDITY);
            final boolean used = net.dries007.tfc.common.blocks.crop.CropHelpers.useFertilizer(level, player, InteractionHand.MAIN_HAND, ground);
            final int taken = 10 - ash.getCount();
            helper.assertTrue(used && taken >= 1, "TFC takes wood ash as a fertiliser: " + used + ", " + taken);
            helper.assertTrue(Math.abs((acidFarm - Pollution.view(level, ground).total(Pollutant.ACIDITY)) - taken * 0.5) < 1e-6, "and the soil is credited the neutralising value of each piece it took: " + (acidFarm - Pollution.view(level, ground).total(Pollutant.ACIDITY)));
            helper.succeed();
        }
        finally
        {
            level.setBlock(ground, Blocks.AIR.defaultBlockState(), 2);
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "tfcpp_forest_8")
    public static void irrigationFromAPollutedLakeMovesItsMetalIntoTheFieldAndOutOfTheLedger(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 10);
        final LevelWaters waters = LevelWaters.get(level);
        waters.reset();
        waters.setResolver(null, null);
        waters.setLakeDetector((l, c) -> true);
        final BlockPos farm = spot(level, chunk, 12), crop = farm.above(), water = farm.west();
        try
        {
            farmland(level, farm);
            level.setBlock(crop, wheat(false), 2);
            Pollution.seed(level, farm, SoilOrder.ENTISOL, false);
            final double[] load = new double[Pollutant.COUNT];
            load[Pollutant.LEAD.ordinal()] = 5000.0; // grams in the lake
            waters.discharge(level, water, Waters.Kind.STILL, load, false);
            final var body = Waters.at(level, water);
            helper.assertTrue(body != null && body.resolved(), "The water is a lake");
            final double perLitre = body.perLitre(Pollutant.LEAD);
            helper.assertTrue(perLitre > 0.0, "The lake holds lead: " + perLitre);
            final double inLedger = waters.ledger().inWater(Pollutant.LEAD), soilBefore = Pollution.view(level, farm).total(Pollutant.LEAD);

            final long now = Calendars.SERVER.getTicks();
            CropSoil.prepare(level, crop, now - (long) (YEAR_HOURS * 1000L), now); // a year of irrigation
            final double expected = perLitre * 500.0; // 500 L a year on one square metre
            final double gained = Pollution.view(level, farm).total(Pollutant.LEAD) - soilBefore;
            helper.assertTrue(Math.abs(gained - expected) < expected * 1e-6, "The soil gained " + gained + " g of lead, expected " + expected);
            helper.assertTrue(Math.abs(inLedger - waters.ledger().inWater(Pollutant.LEAD) - gained) < 1e-6, "and the lake lost the same");
            helper.assertTrue(Math.abs(waters.ledger().imbalance(Pollutant.LEAD)) < 1e-6, "the books close");
            helper.succeed();
        }
        finally
        {
            clear(level, farm);
            release(level, chunk);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_forest_9")
    public static void theAnnualMeanSurvivesSaving(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 11);
        try
        {
            final var air = RegionalAir.get(level);
            final var region = air.grid().cell(chunk.getPos().x >> 3, chunk.getPos().z >> 3);
            final long hour = tfcpp.ecology.AirCadence.hourOf(Calendars.SERVER.getCalendarTicks());
            final var recordHour = (java.util.function.BiConsumer<Long, Double>) region::recordHour;
            for (long h = hour - 2000; h <= hour; h++)
            {
                recordHour.accept(h, 60.0);
            }
            final double annual = region.annualSo2(hour);
            final CompoundTag tag = air.save(new CompoundTag(), level.registryAccess());
            final RegionalAir loaded = RegionalAir.load(tag, level.registryAccess());
            final var again = loaded.grid().cellAt(chunk.getPos().x >> 3, chunk.getPos().z >> 3);
            helper.assertTrue(again != null && Math.abs(again.annualSo2(hour) - annual) < 1e-9, "the same after loading: " + (again == null ? "none" : again.annualSo2(hour)) + " of " + annual);
            helper.assertTrue(again.annualSince() == region.annualSince(), "and since when");
            helper.succeed();
        }
        finally
        {
            release(level, chunk);
        }
    }
}
