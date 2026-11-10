package tfcpp.health;

import java.util.List;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import tfcpp.core.TFCpp;
import tfcpp.metallurgy.Metallurgy;

/**
 * Sewage, drinking water and the diseases of dirty water (stage E of v1.5): the cesspit, the slow sand filter, night soil, the symptoms, and the hidden quality that water carries in a fluid stack. Registered into the same registers as the rest of the mod.
 */
public final class Hygiene
{
    /** A person dies of a fever. */
    public static final ResourceKey<DamageType> FEVER = ResourceKey.create(Registries.DAMAGE_TYPE, TFCpp.id("fever"));

    /** The quality of water: a hidden data component of a fluid stack, with no tooltip. */
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TFCpp.MOD_ID);

    public static final Supplier<DataComponentType<WaterQuality.Stored>> WATER_QUALITY = COMPONENTS.registerComponentType("water_quality", builder -> builder
        .persistent(Codec.DOUBLE.listOf().xmap(WaterQuality.Stored::new, WaterQuality.Stored::values))
        .networkSynchronized(ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()).map(WaterQuality.Stored::new, WaterQuality.Stored::values))
        .cacheEncoding());

    /** The pathogens that a piece of night soil still carries, as quantised litres of sewage: see {@link NightSoil}. */
    public static final Supplier<DataComponentType<NightSoil.Load>> NIGHT_SOIL_LOAD = COMPONENTS.registerComponentType("night_soil_load", builder -> builder
        .persistent(Codec.INT.listOf().xmap(NightSoil.Load::new, NightSoil.Load::codes))
        .networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).map(NightSoil.Load::new, NightSoil.Load::codes))
        .cacheEncoding());

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, TFCpp.MOD_ID);

    /** The effects are named for what a person feels, never for the disease. */
    public static final DeferredHolder<MobEffect, MobEffect> FEVER_EFFECT = EFFECTS.register("fever", () -> new Symptom(0xC9503A)
        .addAttributeModifier(Attributes.MOVEMENT_SPEED, TFCpp.id("effect.fever"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> DIARRHOEA_EFFECT = EFFECTS.register("diarrhoea", () -> new Symptom(0x8A7A3A));
    public static final DeferredHolder<MobEffect, MobEffect> ILLNESS_WEAKNESS_EFFECT = EFFECTS.register("illness_weakness", () -> new Symptom(0x7C8A8A)
        .addAttributeModifier(Attributes.ATTACK_DAMAGE, TFCpp.id("effect.illness_weakness"), -3.0, AttributeModifier.Operation.ADD_VALUE)
        .addAttributeModifier(Attributes.MOVEMENT_SPEED, TFCpp.id("effect.illness_weakness_speed"), -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private static final class Symptom extends MobEffect
    {
        Symptom(int color)
        {
            super(MobEffectCategory.HARMFUL, color);
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TFCpp.MOD_ID);

    /** The diseases of a person. Not copied on death: a respawned person is well, and has no immunity either. */
    public static final Supplier<AttachmentType<DiseaseState>> DISEASES = ATTACHMENTS.register("diseases", () -> AttachmentType.builder(DiseaseState::new)
        .serialize(DiseaseSerializer.INSTANCE)
        .build());

    /** The cesspits of a chunk. */
    public static final Supplier<AttachmentType<CesspitChunkData>> CESSPITS = ATTACHMENTS.register("cesspits", () -> AttachmentType.builder(CesspitChunkData::new)
        .serialize(CesspitChunkData.SERIALIZER)
        .build());

    /** A covered pit in the ground, lined with stone, for a household's waste. */
    public static final Supplier<Block> CESSPIT = Metallurgy.BLOCKS.register("cesspit", () -> new CesspitBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD)));
    /** A box of sand over gravel, through which water seeps slowly. */
    public static final Supplier<Block> SAND_FILTER = Metallurgy.BLOCKS.register("sand_filter", () -> new SandFilterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5f).sound(SoundType.WOOD)));

    public static final Supplier<Item> CESSPIT_ITEM = Metallurgy.ITEMS.register("cesspit", () -> new BlockItem(CESSPIT.get(), new Item.Properties()));
    public static final Supplier<Item> SAND_FILTER_ITEM = Metallurgy.ITEMS.register("sand_filter", () -> new BlockItem(SAND_FILTER.get(), new Item.Properties()));
    /** The contents of a pit, taken out in pieces of 20 litres: a fertiliser, and a danger to whoever drinks where it is spread. */
    public static final Supplier<Item> NIGHT_SOIL = Metallurgy.ITEMS.register("night_soil", () -> new Item(new Item.Properties()));

    public static final Supplier<BlockEntityType<CesspitBlockEntity>> CESSPIT_ENTITY = Metallurgy.BLOCK_ENTITIES.register("cesspit", () -> BlockEntityType.Builder.of(CesspitBlockEntity::new, CESSPIT.get()).build(null));
    public static final Supplier<BlockEntityType<SandFilterBlockEntity>> SAND_FILTER_ENTITY = Metallurgy.BLOCK_ENTITIES.register("sand_filter", () -> BlockEntityType.Builder.of(SandFilterBlockEntity::new, SAND_FILTER.get()).build(null));

    /** The effects that the symptoms of a disease apply, in the order of a list a test can check. */
    public static List<DeferredHolder<MobEffect, MobEffect>> symptoms()
    {
        return List.of(FEVER_EFFECT, DIARRHOEA_EFFECT, ILLNESS_WEAKNESS_EFFECT);
    }

    public static void addToTab(CreativeModeTab.Output output)
    {
        output.accept(CESSPIT_ITEM.get());
        output.accept(SAND_FILTER_ITEM.get());
        output.accept(NIGHT_SOIL.get());
    }

    public static void register(IEventBus modBus)
    {
        COMPONENTS.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(Hygiene::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SAND_FILTER_ENTITY.get(), (entity, side) -> entity.handler(side));
    }

    private Hygiene() {}
}
