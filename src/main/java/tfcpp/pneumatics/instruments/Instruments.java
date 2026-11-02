package tfcpp.pneumatics.instruments;

import java.util.function.Supplier;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.capabilities.ItemCapabilities;
import net.dries007.tfc.common.items.LampBlockItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import tfcpp.core.TFCpp;
import tfcpp.mechanics.MineFanBlock;
import tfcpp.mechanics.MineFanBlockEntity;
import tfcpp.metallurgy.Metallurgy;

/** What a miner carries to read the air, and what catches the dust from it. */
public final class Instruments
{
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TFCpp.MOD_ID);

    public static final TagKey<EntityType<?>> SENTINEL_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, TFCpp.id("sentinel_animals"));

    public static final Supplier<DataComponentType<SentinelData>> SENTINEL = COMPONENTS.register("sentinel", () -> DataComponentType.<SentinelData>builder().persistent(SentinelData.CODEC).networkSynchronized(SentinelData.STREAM_CODEC).build());

    /** Dimmer than an open lamp, as the gauze blocks about a third of the light. Miners complained about this from the start. */
    private static final int DAVY_LAMP_LIGHT = 9;

    public static final Supplier<Block> DAVY_LAMP = Metallurgy.BLOCKS.register("davy_lamp", () -> new DavyLampBlock(ExtendedProperties.of(MapColor.METAL)
        .noOcclusion().sound(SoundType.LANTERN).strength(4f).randomTicks()
        .lightLevel(state -> state.getValue(DavyLampBlock.LIT) ? DAVY_LAMP_LIGHT : 0)
        .blockEntity(TFCBlockEntities.LAMP)));
    public static final Supplier<Block> SETTLING_BAFFLE = Metallurgy.BLOCKS.register("settling_baffle", () -> new SettlingBaffleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    /** A leaf of gold that mercury vapour whitens. It is thin and breaks in the hand, as a leaf does. */
    public static final Supplier<Block> GOLD_LEAF_INDICATOR = Metallurgy.BLOCKS.register("gold_leaf_indicator", () -> new GoldLeafIndicatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.3f).sound(SoundType.METAL).noOcclusion()));

    public static final Supplier<Block> MINE_FAN = Metallurgy.BLOCKS.register("mine_fan", () -> new MineFanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final Supplier<Item> DAVY_LAMP_ITEM = Metallurgy.ITEMS.register("davy_lamp", () -> new LampBlockItem(DAVY_LAMP.get(), new Item.Properties().stacksTo(1)));
    public static final Supplier<Item> MINE_FAN_ITEM = Metallurgy.ITEMS.register("mine_fan", () -> new BlockItem(MINE_FAN.get(), new Item.Properties()));
    public static final Supplier<Item> WIRE_GAUZE = Metallurgy.ITEMS.register("wire_gauze", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> ANIMAL_CAGE = Metallurgy.ITEMS.register("animal_cage", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> SENTINEL_CAGE = Metallurgy.ITEMS.register("sentinel_cage", () -> new SentinelCageItem(new Item.Properties()));
    public static final Supplier<Item> SETTLING_BAFFLE_ITEM = Metallurgy.ITEMS.register("settling_baffle", () -> new BlockItem(SETTLING_BAFFLE.get(), new Item.Properties()));
    public static final Supplier<Item> GOLD_LEAF_INDICATOR_ITEM = Metallurgy.ITEMS.register("gold_leaf_indicator", () -> new BlockItem(GOLD_LEAF_INDICATOR.get(), new Item.Properties()));
    public static final Supplier<Item> ARSENIC_TRIOXIDE = Metallurgy.ITEMS.register("powder/arsenic_trioxide", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> ANTIMONY_TRIOXIDE = Metallurgy.ITEMS.register("powder/antimony_trioxide", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> ZINC_OXIDE = Metallurgy.ITEMS.register("powder/zinc_oxide", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> LEAD_OXIDE = Metallurgy.ITEMS.register("powder/lead_oxide", () -> new Item(new Item.Properties()));

    public static final Supplier<BlockEntityType<SettlingBaffleBlockEntity>> SETTLING_BAFFLE_ENTITY = Metallurgy.BLOCK_ENTITIES.register("settling_baffle", () -> BlockEntityType.Builder.of(SettlingBaffleBlockEntity::new, SETTLING_BAFFLE.get()).build(null));

    public static final Supplier<BlockEntityType<GoldLeafIndicatorBlockEntity>> GOLD_LEAF_INDICATOR_ENTITY = Metallurgy.BLOCK_ENTITIES.register("gold_leaf_indicator", () -> BlockEntityType.Builder.of(GoldLeafIndicatorBlockEntity::new, GOLD_LEAF_INDICATOR.get()).build(null));

    public static final Supplier<BlockEntityType<MineFanBlockEntity>> MINE_FAN_ENTITY = Metallurgy.BLOCK_ENTITIES.register("mine_fan", () -> BlockEntityType.Builder.of(MineFanBlockEntity::new, MINE_FAN.get()).build(null));

    public static void addToTab(CreativeModeTab.Output output)
    {
        output.accept(MINE_FAN_ITEM.get());
        output.accept(DAVY_LAMP_ITEM.get());
        output.accept(WIRE_GAUZE.get());
        output.accept(ANIMAL_CAGE.get());
        output.accept(SETTLING_BAFFLE_ITEM.get());
        output.accept(GOLD_LEAF_INDICATOR_ITEM.get());
        output.accept(ARSENIC_TRIOXIDE.get());
        output.accept(ANTIMONY_TRIOXIDE.get());
        output.accept(ZINC_OXIDE.get());
        output.accept(LEAD_OXIDE.get());
    }

    public static void register(IEventBus modBus)
    {
        COMPONENTS.register(modBus);
        modBus.addListener(Instruments::registerCapabilities);
        modBus.addListener(Instruments::addLampBlock);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerItem(ItemCapabilities.FLUID, ItemCapabilities::forLamp, DAVY_LAMP_ITEM.get());
    }

    /** The lamp is TFC's own block entity, so it keeps TFC's handling of fuel and lighting. */
    private static void addLampBlock(BlockEntityTypeAddBlocksEvent event)
    {
        event.modify(TFCBlockEntities.LAMP.get(), DAVY_LAMP.get());
    }

    private Instruments() {}
}
