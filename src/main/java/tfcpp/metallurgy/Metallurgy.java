package tfcpp.metallurgy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dries007.tfc.common.capabilities.BlockCapabilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import tfcpp.chemistry.Chemistry;
import tfcpp.core.TFCpp;
import tfcpp.geology.Ores;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.pneumatics.pipes.Pipes;

public final class Metallurgy
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TFCpp.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TFCpp.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TFCpp.MOD_ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, TFCpp.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, TFCpp.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, TFCpp.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, TFCpp.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TFCpp.MOD_ID);

    public static final Supplier<Item> LEAD_INGOT = ITEMS.register("metal/ingot/lead", () -> new Item(new Item.Properties()));
    /** Lead from galena, with its silver still in it, until cupellation takes the silver out. Plain lead comes only from reducing litharge. */
    public static final Supplier<Item> WORK_LEAD_INGOT = ITEMS.register("metal/ingot/work_lead", () -> new Item(new Item.Properties()));
    /** Lead beaten flat in the cold, as lead does not harden when it is worked: it recrystallises at room temperature. */
    public static final Supplier<Item> LEAD_SHEET = ITEMS.register("metal/sheet/lead", () -> new Item(new Item.Properties()));
    /** Calcium phosphate left when bone is burned. It is the floor of a cupellation hearth, which takes up litharge and holds it. */
    public static final Supplier<Item> BONE_ASH = ITEMS.register("powder/bone_ash", () -> new Item(new Item.Properties()));
    /** Raw rock phosphate. It feeds a field slowly, as little of it dissolves until it meets acid, which comes with chemistry. */
    public static final Supplier<Item> APATITE_POWDER = ITEMS.register("powder/apatite", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> ARSENICAL_COPPER_INGOT = ITEMS.register("metal/ingot/arsenical_copper", () -> new Item(new Item.Properties()));
    /** Zinc oxide left by roasting sphalerite, one for each grade of ore, as a single piece must carry all the zinc of the ore it came from. */
    public static final Map<String, Supplier<Item>> ZINC_CALCINES = Stream.of("small", "poor", "normal", "rich").collect(Collectors.toMap(
        grade -> grade,
        grade -> ITEMS.register("calcine/" + grade + "_zinc", () -> new Item(new Item.Properties())),
        (a, b) -> a,
        LinkedHashMap::new
    ));
    /** Iron silicate from a bloomery or a converter, rich enough to smelt again. */
    public static final Supplier<Item> IRON_SLAG = ITEMS.register("slag/iron", () -> new Item(new Item.Properties()));
    /** Calcium silicate from a blast furnace, with almost no iron in it. */
    public static final Supplier<Item> FURNACE_SLAG = ITEMS.register("slag/furnace", () -> new Item(new Item.Properties()));
    /** Ground borax. Melts onto iron and keeps air from a weld, and later makes glass that stands heat, but it is not limestone and cannot be burned to lime. */
    public static final Supplier<Item> BORAX_FLUX = ITEMS.register("powder/borax", () -> new Item(new Item.Properties()));
    /** Slag from a bloomery that smelted brown ore. A real tap cinder from phosphoric ore is very phosphoric, so melting it again gives phosphoric oxide and washes nothing clean. */
    public static final Supplier<Item> PHOSPHORIC_IRON_SLAG = ITEMS.register("slag/phosphoric_iron", () -> new Item(new Item.Properties()));
    /** Basic slag from a converter on a dolomite lining, 4CaO.P2O5 with silicates. Ground, it is the first phosphate fertiliser made from iron. */
    public static final Supplier<Item> THOMAS_SLAG = ITEMS.register("slag/thomas", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> THOMAS_MEAL = ITEMS.register("powder/thomas_meal", () -> new Item(new Item.Properties()));
    /** Ground dolomite, CaMg(CO3)2. Burned hard at 1500 C it loses its CO2 and sinters, and does not slake. */
    public static final Supplier<Item> DOLOMITE_POWDER = ITEMS.register("powder/dolomite", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> BURNT_DOLOMITE = ITEMS.register("powder/burnt_dolomite", () -> new Item(new Item.Properties()));
    /** Burnt dolomite pressed with tar, which is how Thomas and Gilchrist's lining was made: bonded with tar, and not fired. */
    public static final Supplier<Item> BASIC_BRICK = ITEMS.register("basic_brick", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> PHOSPHORIC_PIG_IRON_INGOT = ITEMS.register("metal/ingot/phosphoric_pig_iron", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> COLD_SHORT_IRON_INGOT = ITEMS.register("metal/ingot/cold_short_iron", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> GLASS_BULB = ITEMS.register("glass_bulb", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> MERCURY_BULB = ITEMS.register("mercury_bulb", () -> new Item(new Item.Properties()));

    public static final Supplier<FluidType> ARSENICAL_COPPER_TYPE = FLUID_TYPES.register("metal/arsenical_copper", () -> new FluidType(FluidType.Properties.create()
        .descriptionId("fluid_type.tfcpp.metal.arsenical_copper").density(8000).viscosity(6000).temperature(1320).canConvertToSource(false).canDrown(false).canSwim(false).canPushEntity(false)));
    public static final Supplier<FluidType> LEAD_TYPE = FLUID_TYPES.register("metal/lead", () -> new FluidType(FluidType.Properties.create()
        .descriptionId("fluid_type.tfcpp.metal.lead").density(10660).viscosity(3000).temperature(600).canConvertToSource(false).canDrown(false).canSwim(false).canPushEntity(false)));
    /** Lead from galena. It melts and pours as lead does, and is only different in what it holds. */
    public static final Supplier<FluidType> WORK_LEAD_TYPE = FLUID_TYPES.register("metal/work_lead", () -> new FluidType(FluidType.Properties.create()
        .descriptionId("fluid_type.tfcpp.metal.work_lead").density(10660).viscosity(3000).temperature(600).canConvertToSource(false).canDrown(false).canSwim(false).canPushEntity(false)));
    /** Iron ore melted with nothing to reduce it. It is still an oxide: only a furnace burning carbon around it can take the oxygen away. */
    public static final Supplier<FluidType> IRON_OXIDE_TYPE = FLUID_TYPES.register("metal/iron_oxide", () -> new FluidType(FluidType.Properties.create()
        .descriptionId("fluid_type.tfcpp.metal.iron_oxide").density(5000).viscosity(6000).temperature(1838).canConvertToSource(false).canDrown(false).canSwim(false).canPushEntity(false)));
    /** Brown iron ore melted with nothing to reduce it. It is iron oxide, with the phosphorus of the ore still in it. */
    public static final Supplier<FluidType> PHOSPHORIC_IRON_OXIDE_TYPE = FLUID_TYPES.register("metal/phosphoric_iron_oxide", () -> new FluidType(FluidType.Properties.create()
        .descriptionId("fluid_type.tfcpp.metal.phosphoric_iron_oxide").density(5000).viscosity(6000).temperature(1838).canConvertToSource(false).canDrown(false).canSwim(false).canPushEntity(false)));
    public static final Supplier<FluidType> PHOSPHORIC_PIG_IRON_TYPE = FLUID_TYPES.register("metal/phosphoric_pig_iron", () -> new FluidType(FluidType.Properties.create()
        .descriptionId("fluid_type.tfcpp.metal.phosphoric_pig_iron").density(7200).viscosity(5000).temperature(1473).canConvertToSource(false).canDrown(false).canSwim(false).canPushEntity(false)));
    public static final Supplier<FluidType> COLD_SHORT_IRON_TYPE = FLUID_TYPES.register("metal/cold_short_iron", () -> new FluidType(FluidType.Properties.create()
        .descriptionId("fluid_type.tfcpp.metal.cold_short_iron").density(7800).viscosity(6000).temperature(1808).canConvertToSource(false).canDrown(false).canSwim(false).canPushEntity(false)));
    /** Mercury is liquid at room temperature, and dense enough that a person floats on it. */
    public static final Supplier<FluidType> MERCURY_TYPE = FLUID_TYPES.register("mercury", () -> new FluidType(FluidType.Properties.create()
        .descriptionId("fluid_type.tfcpp.mercury").density(13530).viscosity(1500).temperature(293).canConvertToSource(false).canDrown(false).canSwim(false)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ARSENICAL_COPPER = FLUIDS.register("metal/arsenical_copper", () -> new BaseFlowingFluid.Source(arsenicalCopperProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_ARSENICAL_COPPER = FLUIDS.register("metal/flowing_arsenical_copper", () -> new BaseFlowingFluid.Flowing(arsenicalCopperProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LEAD = FLUIDS.register("metal/lead", () -> new BaseFlowingFluid.Source(leadProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_LEAD = FLUIDS.register("metal/flowing_lead", () -> new BaseFlowingFluid.Flowing(leadProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> WORK_LEAD = FLUIDS.register("metal/work_lead", () -> new BaseFlowingFluid.Source(workLeadProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_WORK_LEAD = FLUIDS.register("metal/flowing_work_lead", () -> new BaseFlowingFluid.Flowing(workLeadProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> IRON_OXIDE = FLUIDS.register("metal/iron_oxide", () -> new BaseFlowingFluid.Source(ironOxideProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_IRON_OXIDE = FLUIDS.register("metal/flowing_iron_oxide", () -> new BaseFlowingFluid.Flowing(ironOxideProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PHOSPHORIC_IRON_OXIDE = FLUIDS.register("metal/phosphoric_iron_oxide", () -> new BaseFlowingFluid.Source(phosphoricIronOxideProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_PHOSPHORIC_IRON_OXIDE = FLUIDS.register("metal/flowing_phosphoric_iron_oxide", () -> new BaseFlowingFluid.Flowing(phosphoricIronOxideProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PHOSPHORIC_PIG_IRON = FLUIDS.register("metal/phosphoric_pig_iron", () -> new BaseFlowingFluid.Source(phosphoricPigIronProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_PHOSPHORIC_PIG_IRON = FLUIDS.register("metal/flowing_phosphoric_pig_iron", () -> new BaseFlowingFluid.Flowing(phosphoricPigIronProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COLD_SHORT_IRON = FLUIDS.register("metal/cold_short_iron", () -> new BaseFlowingFluid.Source(coldShortIronProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_COLD_SHORT_IRON = FLUIDS.register("metal/flowing_cold_short_iron", () -> new BaseFlowingFluid.Flowing(coldShortIronProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> MERCURY = FLUIDS.register("mercury", () -> new BaseFlowingFluid.Source(mercuryProperties()));
    private static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_MERCURY = FLUIDS.register("flowing_mercury", () -> new BaseFlowingFluid.Flowing(mercuryProperties()));

    public static final Supplier<Block> RETORT = BLOCKS.register("retort", () -> new RetortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final Supplier<Block> RETORT_CONDENSER = BLOCKS.register("retort_condenser", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final Supplier<Block> CONVERTER = BLOCKS.register("converter", () -> new ConverterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 8f).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    /** A fire-brick hearth over a fire, where work lead is cupelled to silver. */
    public static final Supplier<Block> CUPEL_HEARTH = BLOCKS.register("cupel_hearth", () -> new CupelHearthBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    /** A fire-clay chamber in a pipe network, where the fume of the roasting hearth cools and drops its dust. */
    public static final Supplier<Block> CONDENSATION_TOWER = BLOCKS.register("condensation_tower", () -> new CondensationTowerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final Supplier<Item> RETORT_ITEM = ITEMS.register("retort", () -> new BlockItem(RETORT.get(), new Item.Properties()));
    public static final Supplier<Item> RETORT_CONDENSER_ITEM = ITEMS.register("retort_condenser", () -> new BlockItem(RETORT_CONDENSER.get(), new Item.Properties()));

    public static final Supplier<Item> CONVERTER_ITEM = ITEMS.register("converter", () -> new BlockItem(CONVERTER.get(), new Item.Properties()));
    public static final Supplier<Item> CUPEL_HEARTH_ITEM = ITEMS.register("cupel_hearth", () -> new BlockItem(CUPEL_HEARTH.get(), new Item.Properties()));
    public static final Supplier<Item> CONDENSATION_TOWER_ITEM = ITEMS.register("condensation_tower", () -> new BlockItem(CONDENSATION_TOWER.get(), new Item.Properties()));

    public static final Supplier<BlockEntityType<ConverterBlockEntity>> CONVERTER_ENTITY = BLOCK_ENTITIES.register("converter", () -> BlockEntityType.Builder.of(ConverterBlockEntity::new, CONVERTER.get()).build(null));
    public static final Supplier<BlockEntityType<RetortBlockEntity>> RETORT_ENTITY = BLOCK_ENTITIES.register("retort", () -> BlockEntityType.Builder.of(RetortBlockEntity::new, RETORT.get()).build(null));
    public static final Supplier<BlockEntityType<CupelHearthBlockEntity>> CUPEL_HEARTH_ENTITY = BLOCK_ENTITIES.register("cupel_hearth", () -> BlockEntityType.Builder.of(CupelHearthBlockEntity::new, CUPEL_HEARTH.get()).build(null));
    public static final Supplier<BlockEntityType<CondensationTowerBlockEntity>> CONDENSATION_TOWER_ENTITY = BLOCK_ENTITIES.register("condensation_tower", () -> BlockEntityType.Builder.of(CondensationTowerBlockEntity::new, CONDENSATION_TOWER.get()).build(null));

    public static final Supplier<RecipeType<RetortRecipe>> RETORT_RECIPE = RECIPE_TYPES.register("retort", () -> RecipeType.simple(TFCpp.id("retort")));
    public static final Supplier<RecipeSerializer<RetortRecipe>> RETORT_SERIALIZER = RECIPE_SERIALIZERS.register("retort", RetortRecipe.Serializer::new);

    static
    {
        TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tfcpp"))
            .icon(() -> new ItemStack(RETORT_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(ARSENICAL_COPPER_INGOT.get());
                output.accept(LEAD_INGOT.get());
                output.accept(WORK_LEAD_INGOT.get());
                output.accept(LEAD_SHEET.get());
                output.accept(BONE_ASH.get());
                output.accept(APATITE_POWDER.get());
                Ores.addToTab(output);
                ZINC_CALCINES.values().forEach(calcine -> output.accept(calcine.get()));
                output.accept(IRON_SLAG.get());
                output.accept(FURNACE_SLAG.get());
                output.accept(PHOSPHORIC_IRON_SLAG.get());
                output.accept(THOMAS_SLAG.get());
                output.accept(THOMAS_MEAL.get());
                output.accept(DOLOMITE_POWDER.get());
                output.accept(BURNT_DOLOMITE.get());
                output.accept(BASIC_BRICK.get());
                output.accept(PHOSPHORIC_PIG_IRON_INGOT.get());
                output.accept(COLD_SHORT_IRON_INGOT.get());
                output.accept(BORAX_FLUX.get());
                output.accept(GLASS_BULB.get());
                output.accept(MERCURY_BULB.get());
                output.accept(RETORT_ITEM.get());
                output.accept(RETORT_CONDENSER_ITEM.get());
                output.accept(CONVERTER_ITEM.get());
                output.accept(CUPEL_HEARTH_ITEM.get());
                output.accept(CONDENSATION_TOWER_ITEM.get());
                Instruments.addToTab(output);
                Pipes.addToTab(output);
                Chemistry.addToTab(output);
            })
            .build());
    }

    private static BaseFlowingFluid.Properties arsenicalCopperProperties()
    {
        return new BaseFlowingFluid.Properties(ARSENICAL_COPPER_TYPE, ARSENICAL_COPPER, FLOWING_ARSENICAL_COPPER);
    }

    private static BaseFlowingFluid.Properties leadProperties()
    {
        return new BaseFlowingFluid.Properties(LEAD_TYPE, LEAD, FLOWING_LEAD);
    }

    private static BaseFlowingFluid.Properties workLeadProperties()
    {
        return new BaseFlowingFluid.Properties(WORK_LEAD_TYPE, WORK_LEAD, FLOWING_WORK_LEAD);
    }

    private static BaseFlowingFluid.Properties ironOxideProperties()
    {
        return new BaseFlowingFluid.Properties(IRON_OXIDE_TYPE, IRON_OXIDE, FLOWING_IRON_OXIDE);
    }

    private static BaseFlowingFluid.Properties phosphoricIronOxideProperties()
    {
        return new BaseFlowingFluid.Properties(PHOSPHORIC_IRON_OXIDE_TYPE, PHOSPHORIC_IRON_OXIDE, FLOWING_PHOSPHORIC_IRON_OXIDE);
    }

    private static BaseFlowingFluid.Properties phosphoricPigIronProperties()
    {
        return new BaseFlowingFluid.Properties(PHOSPHORIC_PIG_IRON_TYPE, PHOSPHORIC_PIG_IRON, FLOWING_PHOSPHORIC_PIG_IRON);
    }

    private static BaseFlowingFluid.Properties coldShortIronProperties()
    {
        return new BaseFlowingFluid.Properties(COLD_SHORT_IRON_TYPE, COLD_SHORT_IRON, FLOWING_COLD_SHORT_IRON);
    }

    private static BaseFlowingFluid.Properties mercuryProperties()
    {
        return new BaseFlowingFluid.Properties(MERCURY_TYPE, MERCURY, FLOWING_MERCURY);
    }

    public static void register(IEventBus modBus)
    {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(Metallurgy::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(BlockCapabilities.HEAT, RETORT_ENTITY.get(), (entity, side) -> entity);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, RETORT_ENTITY.get(), (entity, side) -> entity.tank());
        event.registerBlockEntity(BlockCapabilities.HEAT, CONVERTER_ENTITY.get(), (entity, side) -> entity);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CONVERTER_ENTITY.get(), (entity, side) -> entity.tank());
        event.registerBlockEntity(BlockCapabilities.HEAT, CUPEL_HEARTH_ENTITY.get(), (entity, side) -> entity);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CUPEL_HEARTH_ENTITY.get(), (entity, side) -> entity.tank());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CONDENSATION_TOWER_ENTITY.get(), (entity, side) -> entity.outlet());
    }

    private Metallurgy() {}
}
