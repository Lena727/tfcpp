package tfcpp.pneumatics.pipes;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
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

    public static final Supplier<Block> VALVE = fitting("pipe_valve", PipeFittingBlock.Valve::new);
    public static final Supplier<Block> VENT = fitting("pipe_vent", PipeFittingBlock.Vent::new);
    public static final Supplier<Block> TAP = fitting("pipe_tap", PipeFittingBlock.Tap::new);
    public static final Supplier<Block> SAFETY_VALVE = fitting("safety_valve", PipeFittingBlock.SafetyValve::new);
    public static final Supplier<Block> MANOMETER = fitting("mercury_manometer", ManometerBlock::new);
    public static final Supplier<Block> PRESSURE_VESSEL = fitting("pressure_vessel", PressureVesselBlock::new);
    public static final Supplier<Block> PISTON_PUMP = fitting("piston_pump", PistonPumpBlock::new);

    public static final Supplier<BlockEntityType<PressureVesselBlockEntity>> PRESSURE_VESSEL_ENTITY = Metallurgy.BLOCK_ENTITIES.register("pressure_vessel", () -> BlockEntityType.Builder.of(PressureVesselBlockEntity::new, PRESSURE_VESSEL.get()).build(null));
    public static final Supplier<BlockEntityType<PistonPumpBlockEntity>> PISTON_PUMP_ENTITY = Metallurgy.BLOCK_ENTITIES.register("piston_pump", () -> BlockEntityType.Builder.of(PistonPumpBlockEntity::new, PISTON_PUMP.get()).build(null));

    private static final Supplier<Item> VALVE_ITEM = item("pipe_valve", VALVE);
    private static final Supplier<Item> VENT_ITEM = item("pipe_vent", VENT);
    private static final Supplier<Item> TAP_ITEM = item("pipe_tap", TAP);
    private static final Supplier<Item> SAFETY_VALVE_ITEM = item("safety_valve", SAFETY_VALVE);
    private static final Supplier<Item> MANOMETER_ITEM = item("mercury_manometer", MANOMETER);
    private static final Supplier<Item> PRESSURE_VESSEL_ITEM = item("pressure_vessel", PRESSURE_VESSEL);
    private static final Supplier<Item> PISTON_PUMP_ITEM = item("piston_pump", PISTON_PUMP);

    private static Supplier<Block> fitting(String name, Function<BlockBehaviour.Properties, Block> factory)
    {
        return Metallurgy.BLOCKS.register(name, () -> factory.apply(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));
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
            default -> properties.mapColor(MapColor.METAL).sound(SoundType.METAL);
        };
    }

    public static void addToTab(CreativeModeTab.Output output)
    {
        PIPE_ITEMS.values().forEach(item -> output.accept(item.get()));
        output.accept(VALVE_ITEM.get());
        output.accept(VENT_ITEM.get());
        output.accept(TAP_ITEM.get());
        output.accept(SAFETY_VALVE_ITEM.get());
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
