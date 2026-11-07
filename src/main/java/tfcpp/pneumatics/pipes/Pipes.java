package tfcpp.pneumatics.pipes;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.dries007.tfc.common.capabilities.BlockCapabilities;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import tfcpp.metallurgy.Metallurgy;

public final class Pipes
{
    public static final Map<PipeMaterial, Supplier<Block>> PIPES = new EnumMap<>(PipeMaterial.class);
    public static final Map<PipeMaterial, Supplier<Item>> PIPE_ITEMS = new EnumMap<>(PipeMaterial.class);

    static
    {
        Arrays.stream(PipeMaterial.values()).forEach(material -> {
            final Supplier<Block> block = Metallurgy.BLOCKS.register("pipe/" + material.id(), () -> new PipeBlock(material, pipeProperties(material)));
            PIPES.put(material, block);
            PIPE_ITEMS.put(material, Metallurgy.ITEMS.register("pipe/" + material.id(), () -> new BlockItem(block.get(), new Item.Properties())));
        });
    }

    /**
     * What a fitting can be made of: iron, which is what the first ones were and keeps its names, lead for sulfuric acid and stoneware for every strong acid at any heat. Each kind of fitting is registered in each of them, the iron one under the plain name and the others with the material after it.
     */
    public static final List<PipeMaterial> FITTING_MATERIALS = List.of(PipeMaterial.WROUGHT_IRON, PipeMaterial.LEAD, PipeMaterial.CERAMIC);

    public static final Map<PipeMaterial, Supplier<Block>> VALVES = fittings("pipe_valve", PipeFittingBlock.Valve::new);
    public static final Map<PipeMaterial, Supplier<Block>> VENTS = fittings("pipe_vent", PipeFittingBlock.Vent::new);
    public static final Map<PipeMaterial, Supplier<Block>> TAPS = fittings("pipe_tap", PipeFittingBlock.Tap::new);
    public static final Map<PipeMaterial, Supplier<Block>> SAFETY_VALVES = fittings("safety_valve", PipeFittingBlock.SafetyValve::new);
    public static final Supplier<Block> VALVE = VALVES.get(PipeMaterial.WROUGHT_IRON);
    public static final Supplier<Block> VENT = VENTS.get(PipeMaterial.WROUGHT_IRON);
    public static final Supplier<Block> TAP = TAPS.get(PipeMaterial.WROUGHT_IRON);
    public static final Supplier<Block> SAFETY_VALVE = SAFETY_VALVES.get(PipeMaterial.WROUGHT_IRON);
    public static final Supplier<Block> MANOMETER = fitting("mercury_manometer", ManometerBlock::new);
    public static final Supplier<Block> PRESSURE_VESSEL = fitting("pressure_vessel", PressureVesselBlock::new);
    public static final Supplier<Block> PISTON_PUMP = fitting("piston_pump", PistonPumpBlock::new);

    public static final Supplier<BlockEntityType<PressureVesselBlockEntity>> PRESSURE_VESSEL_ENTITY = Metallurgy.BLOCK_ENTITIES.register("pressure_vessel", () -> BlockEntityType.Builder.of(PressureVesselBlockEntity::new, PRESSURE_VESSEL.get()).build(null));
    public static final Supplier<BlockEntityType<PistonPumpBlockEntity>> PISTON_PUMP_ENTITY = Metallurgy.BLOCK_ENTITIES.register("piston_pump", () -> BlockEntityType.Builder.of(PistonPumpBlockEntity::new, PISTON_PUMP.get()).build(null));

    public static final Map<PipeMaterial, Supplier<Item>> VALVE_ITEMS = fittingItems("pipe_valve", VALVES);
    public static final Map<PipeMaterial, Supplier<Item>> VENT_ITEMS = fittingItems("pipe_vent", VENTS);
    public static final Map<PipeMaterial, Supplier<Item>> TAP_ITEMS = fittingItems("pipe_tap", TAPS);
    public static final Map<PipeMaterial, Supplier<Item>> SAFETY_VALVE_ITEMS = fittingItems("safety_valve", SAFETY_VALVES);
    private static final Supplier<Item> MANOMETER_ITEM = item("mercury_manometer", MANOMETER);
    private static final Supplier<Item> PRESSURE_VESSEL_ITEM = item("pressure_vessel", PRESSURE_VESSEL);
    private static final Supplier<Item> PISTON_PUMP_ITEM = item("piston_pump", PISTON_PUMP);

    private static Supplier<Block> fitting(String name, Function<BlockBehaviour.Properties, Block> factory)
    {
        return Metallurgy.BLOCKS.register(name, () -> factory.apply(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));
    }

    /** @return the registry name of a fitting of this material: the iron one has the plain name */
    public static String fittingName(String name, PipeMaterial material)
    {
        return material == PipeMaterial.WROUGHT_IRON ? name : name + "_" + material.id();
    }

    private static Map<PipeMaterial, Supplier<Block>> fittings(String name, BiFunction<PipeMaterial, BlockBehaviour.Properties, Block> factory)
    {
        final Map<PipeMaterial, Supplier<Block>> made = new EnumMap<>(PipeMaterial.class);
        FITTING_MATERIALS.forEach(material -> made.put(material, Metallurgy.BLOCKS.register(fittingName(name, material), () -> factory.apply(material, fittingProperties(material)))));
        return made;
    }

    private static Map<PipeMaterial, Supplier<Item>> fittingItems(String name, Map<PipeMaterial, Supplier<Block>> blocks)
    {
        final Map<PipeMaterial, Supplier<Item>> made = new EnumMap<>(PipeMaterial.class);
        blocks.forEach((material, block) -> made.put(material, item(fittingName(name, material), block)));
        return made;
    }

    private static BlockBehaviour.Properties fittingProperties(PipeMaterial material)
    {
        final BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().strength(3f, 6f).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK);
        return switch (material)
        {
            case CERAMIC -> properties.strength(2f, 4f).mapColor(MapColor.TERRACOTTA_ORANGE).sound(SoundType.STONE); // Brittle, as the pipe is
            case LEAD -> properties.strength(1.5f, 3f).mapColor(MapColor.METAL).sound(SoundType.METAL);
            default -> properties.mapColor(MapColor.METAL).sound(SoundType.METAL);
        };
    }

    private static Supplier<Item> item(String name, Supplier<Block> block)
    {
        return Metallurgy.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static BlockBehaviour.Properties pipeProperties(PipeMaterial material)
    {
        final BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().strength(2f, 4f).noOcclusion().pushReaction(PushReaction.BLOCK);
        return switch (material)
        {
            case WOOD -> properties.mapColor(MapColor.WOOD).sound(SoundType.WOOD);
            case CERAMIC -> properties.mapColor(MapColor.TERRACOTTA_ORANGE).sound(SoundType.STONE);
            case LEAD -> properties.strength(1.5f, 3f).mapColor(MapColor.METAL).sound(SoundType.METAL); // Lead is softer than copper, at about 1.5 on Mohs' scale against 3
            default -> properties.mapColor(MapColor.METAL).sound(SoundType.METAL);
        };
    }

    public static void addToTab(CreativeModeTab.Output output)
    {
        PIPE_ITEMS.values().forEach(item -> output.accept(item.get()));
        for (Map<PipeMaterial, Supplier<Item>> items : List.of(VALVE_ITEMS, VENT_ITEMS, TAP_ITEMS, SAFETY_VALVE_ITEMS))
        {
            items.values().forEach(item -> output.accept(item.get()));
        }
        output.accept(MANOMETER_ITEM.get());
        output.accept(PRESSURE_VESSEL_ITEM.get());
        output.accept(PISTON_PUMP_ITEM.get());
    }

    public static void register(IEventBus modBus)
    {
        modBus.addListener(Pipes::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(BlockCapabilities.HEAT, PRESSURE_VESSEL_ENTITY.get(), (entity, side) -> entity);
    }

    private Pipes() {}
}
