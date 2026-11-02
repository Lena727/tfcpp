package tfcpp.chemistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import net.dries007.tfc.common.capabilities.BlockCapabilities;
import net.dries007.tfc.common.component.fluid.FluidContainerHandler;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
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
import org.jetbrains.annotations.Nullable;

import tfcpp.core.TFCpp;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.pipes.PipePhysics;

/**
 * Coal chemistry: dry distillation of coal, lignite and wood, the liquids that it gives and the burner that uses its gas. Registered into the same registers as the rest of the mod, as the mod is one system.
 */
public final class Chemistry
{
    /** Items that light a gas burner. They are the same as the ones that start a fire. */
    public static final TagKey<Item> LIGHTS_BURNER = TagKey.create(Registries.ITEM, TFCpp.id("lights_gas_burner"));

    /** Liquids that eat wood and iron, and splash anyone who is by them when they run out of a pipe or a chamber. */
    public static final TagKey<Fluid> CORROSIVE = TagKey.create(Registries.FLUID, TFCpp.id("corrosive"));

    /** Liquids that a glass carboy holds: the chemical liquids of the mod, vinegar and fresh water. Nothing else, and no liquid of TFC's blocks that is not named here. */
    public static final TagKey<Fluid> USABLE_IN_CARBOY = TagKey.create(Registries.FLUID, TFCpp.id("usable_in_carboy"));

    /**
     * One chemical liquid: its fluid type and both fluids, with no block and no bucket, as these are only ever handled in tanks.
     */
    public static final class ChemicalFluid
    {
        public final Liquid liquid;
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;

        private ChemicalFluid(Liquid liquid)
        {
            this.liquid = liquid;
            this.type = Metallurgy.FLUID_TYPES.register(liquid.id(), () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid_type.tfcpp." + liquid.id()).density(liquid.density()).viscosity(liquid.fluidTypeViscosity()).temperature(Math.round(PipePhysics.ROOM_TEMPERATURE))
                .canConvertToSource(false).canDrown(false).canSwim(false).canPushEntity(false)));
            this.source = Metallurgy.FLUIDS.register(liquid.id(), () -> new BaseFlowingFluid.Source(properties()));
            this.flowing = Metallurgy.FLUIDS.register("flowing_" + liquid.id(), () -> new BaseFlowingFluid.Flowing(properties()));
        }

        private BaseFlowingFluid.Properties properties()
        {
            return new BaseFlowingFluid.Properties(type, source, flowing);
        }
    }

    public static final Map<Liquid, ChemicalFluid> LIQUIDS = new EnumMap<>(Liquid.class);

    static
    {
        for (Liquid liquid : Liquid.values())
        {
            LIQUIDS.put(liquid, new ChemicalFluid(liquid));
        }
    }

    /** The source fluid of a liquid. */
    public static Fluid fluid(Liquid liquid)
    {
        return LIQUIDS.get(liquid).source.get();
    }

    /** @return the chemical liquid that this fluid is, or {@code null} for any other fluid */
    @Nullable
    public static Liquid liquidOf(Fluid fluid)
    {
        for (ChemicalFluid chemical : LIQUIDS.values())
        {
            if (chemical.source.get() == fluid || chemical.flowing.get() == fluid)
            {
                return chemical.liquid;
            }
        }
        return null;
    }

    /** Dry distillation leaves a fuel that has lost its volatile matter, so that it burns hotter and cleaner, and cakes into lumps that do not collapse under a load of ore. */
    public static final Supplier<Item> COKE = Metallurgy.ITEMS.register("coke", () -> new Item(new Item.Properties()));
    /** Lignite chars without caking: this is a weak, crumbling fuel. */
    public static final Supplier<Item> LIGNITE_CHAR = Metallurgy.ITEMS.register("lignite_char", () -> new Item(new Item.Properties()));

    public static final Supplier<Block> COKE_OVEN = Metallurgy.BLOCKS.register("coke_oven", () -> new CokeOvenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final Supplier<Block> GAS_BURNER = Metallurgy.BLOCKS.register("gas_burner", () -> new GasBurnerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)
        .lightLevel(state -> state.getValue(GasBurnerBlock.LIT) ? GasBurnerBlock.LIGHT_LEVEL : 0)));

    public static final Supplier<Item> COKE_OVEN_ITEM = Metallurgy.ITEMS.register("coke_oven", () -> new BlockItem(COKE_OVEN.get(), new Item.Properties()));
    public static final Supplier<Item> GAS_BURNER_ITEM = Metallurgy.ITEMS.register("gas_burner", () -> new BlockItem(GAS_BURNER.get(), new Item.Properties()));

    /** A chamber of lead sheet in a pipe, where sulfur dioxide, air, water and oxides make sulfuric acid. Lead is what the acid cannot eat while it is cool. */
    public static final Supplier<Block> LEAD_CHAMBER = Metallurgy.BLOCKS.register("lead_chamber", () -> new LeadChamberBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    /** A fire-clay trough in a pipe, where sulfur is burnt in the air of the pipe to sulfur dioxide. */
    public static final Supplier<Block> SULFUR_BURNER = Metallurgy.BLOCKS.register("sulfur_burner", () -> new SulfurBurnerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final Supplier<Item> LEAD_CHAMBER_ITEM = Metallurgy.ITEMS.register("lead_chamber", () -> new BlockItem(LEAD_CHAMBER.get(), new Item.Properties()));
    public static final Supplier<Item> SULFUR_BURNER_ITEM = Metallurgy.ITEMS.register("sulfur_burner", () -> new BlockItem(SULFUR_BURNER.get(), new Item.Properties()));

    /** The data components that the mod's chemistry keeps on its items. */
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TFCpp.MOD_ID);
    /** Tests left in a marsh apparatus. A new one has none: its acid is what starts it. */
    public static final Supplier<DataComponentType<Integer>> MARSH_TESTS = COMPONENTS.register("marsh_tests", () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** A flask of glass and zinc, which tests a sample or the air for the white poisons of a furnace. */
    public static final Supplier<Item> MARSH_APPARATUS = Metallurgy.ITEMS.register("marsh_apparatus", () -> new MarshApparatusItem(new Item.Properties()));

    /** Potassium hydrogen sulfate, the cake that is left in the flask when nitre is distilled with oil of vitriol. */
    public static final Supplier<Item> POTASSIUM_BISULFATE = Metallurgy.ITEMS.register("powder/potassium_bisulfate", () -> new Item(new Item.Properties()));
    /** Sodium hydrogen sulfate, the salt cake that is left when salt is distilled with oil of vitriol. */
    public static final Supplier<Item> SODIUM_BISULFATE = Metallurgy.ITEMS.register("powder/sodium_bisulfate", () -> new Item(new Item.Properties()));
    /** Rock phosphate treated with sulfuric acid, which is soluble, and so feeds a field in a season where raw apatite takes years. */
    public static final Supplier<Item> SUPERPHOSPHATE = Metallurgy.ITEMS.register("powder/superphosphate", () -> new Item(new Item.Properties()));
    /** Ammonia of the gas liquor taken up by sulfuric acid: the first synthetic nitrogen fertiliser from a coke oven. */
    public static final Supplier<Item> AMMONIUM_SULFATE = Metallurgy.ITEMS.register("powder/ammonium_sulfate", () -> new Item(new Item.Properties()));
    /** Sand, lime and borax, the charge of a glass that stands heat and acid. It is worked on a blowpipe as the batches of TFC are. */
    public static final Supplier<Item> BOROSILICATE_GLASS_BATCH = Metallurgy.ITEMS.register("borosilicate_glass_batch", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> GLASS_CARBOY = Metallurgy.ITEMS.register("glass_carboy", () -> new GlassCarboyItem(new Item.Properties()));

    /** A retort and receiver of borosilicate glass, in which acids are distilled. It cracks when it is too hot. */
    public static final Supplier<Block> GLASS_RETORT = Metallurgy.BLOCKS.register("glass_retort", () -> new GlassRetortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.5f).sound(SoundType.GLASS)));
    /** An iron tray of sand under a glass vessel, that holds back part of the fire's heat. */
    public static final Supplier<Block> SAND_BATH = Metallurgy.BLOCKS.register("sand_bath", () -> new SandBathBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5f, 4f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final Supplier<Item> GLASS_RETORT_ITEM = Metallurgy.ITEMS.register("glass_retort", () -> new BlockItem(GLASS_RETORT.get(), new Item.Properties()));
    public static final Supplier<Item> SAND_BATH_ITEM = Metallurgy.ITEMS.register("sand_bath", () -> new BlockItem(SAND_BATH.get(), new Item.Properties()));

    public static final Supplier<BlockEntityType<GlassRetortBlockEntity>> GLASS_RETORT_ENTITY = Metallurgy.BLOCK_ENTITIES.register("glass_retort", () -> BlockEntityType.Builder.of(GlassRetortBlockEntity::new, GLASS_RETORT.get()).build(null));
    public static final Supplier<BlockEntityType<SandBathBlockEntity>> SAND_BATH_ENTITY = Metallurgy.BLOCK_ENTITIES.register("sand_bath", () -> BlockEntityType.Builder.of(SandBathBlockEntity::new, SAND_BATH.get()).build(null));

    public static final Supplier<BlockEntityType<CokeOvenBlockEntity>> COKE_OVEN_ENTITY = Metallurgy.BLOCK_ENTITIES.register("coke_oven", () -> BlockEntityType.Builder.of(CokeOvenBlockEntity::new, COKE_OVEN.get()).build(null));
    public static final Supplier<BlockEntityType<GasBurnerBlockEntity>> GAS_BURNER_ENTITY = Metallurgy.BLOCK_ENTITIES.register("gas_burner", () -> BlockEntityType.Builder.of(GasBurnerBlockEntity::new, GAS_BURNER.get()).build(null));
    public static final Supplier<BlockEntityType<LeadChamberBlockEntity>> LEAD_CHAMBER_ENTITY = Metallurgy.BLOCK_ENTITIES.register("lead_chamber", () -> BlockEntityType.Builder.of(LeadChamberBlockEntity::new, LEAD_CHAMBER.get()).build(null));
    public static final Supplier<BlockEntityType<SulfurBurnerBlockEntity>> SULFUR_BURNER_ENTITY = Metallurgy.BLOCK_ENTITIES.register("sulfur_burner", () -> BlockEntityType.Builder.of(SulfurBurnerBlockEntity::new, SULFUR_BURNER.get()).build(null));

    public static final Supplier<RecipeType<CarbonizationRecipe>> CARBONIZATION_RECIPE = Metallurgy.RECIPE_TYPES.register("carbonization", () -> RecipeType.simple(TFCpp.id("carbonization")));
    public static final Supplier<RecipeSerializer<CarbonizationRecipe>> CARBONIZATION_SERIALIZER = Metallurgy.RECIPE_SERIALIZERS.register("carbonization", CarbonizationRecipe.Serializer::new);

    public static final Supplier<RecipeType<DistillationRecipe>> DISTILLATION_RECIPE = Metallurgy.RECIPE_TYPES.register("glass_distillation", () -> RecipeType.simple(TFCpp.id("glass_distillation")));
    public static final Supplier<RecipeSerializer<DistillationRecipe>> DISTILLATION_SERIALIZER = Metallurgy.RECIPE_SERIALIZERS.register("glass_distillation", DistillationRecipe.Serializer::new);

    public static void addToTab(CreativeModeTab.Output output)
    {
        output.accept(COKE.get());
        output.accept(LIGNITE_CHAR.get());
        output.accept(COKE_OVEN_ITEM.get());
        output.accept(GAS_BURNER_ITEM.get());
        output.accept(LEAD_CHAMBER_ITEM.get());
        output.accept(SULFUR_BURNER_ITEM.get());
        output.accept(BOROSILICATE_GLASS_BATCH.get());
        output.accept(GLASS_CARBOY.get());
        output.accept(GLASS_RETORT_ITEM.get());
        output.accept(SAND_BATH_ITEM.get());
        output.accept(MARSH_APPARATUS.get());
        output.accept(POTASSIUM_BISULFATE.get());
        output.accept(SODIUM_BISULFATE.get());
        output.accept(SUPERPHOSPHATE.get());
        output.accept(AMMONIUM_SULFATE.get());
    }

    public static void register(IEventBus modBus)
    {
        COMPONENTS.register(modBus);
        modBus.addListener(Chemistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(BlockCapabilities.HEAT, COKE_OVEN_ENTITY.get(), (entity, side) -> entity);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, COKE_OVEN_ENTITY.get(), (entity, side) -> entity.tanks());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LEAD_CHAMBER_ENTITY.get(), (entity, side) -> entity.outlet());
        event.registerBlockEntity(BlockCapabilities.HEAT, GLASS_RETORT_ENTITY.get(), (entity, side) -> entity);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, GLASS_RETORT_ENTITY.get(), (entity, side) -> entity.fluids(side));
        event.registerBlockEntity(BlockCapabilities.HEAT, SAND_BATH_ENTITY.get(), (entity, side) -> entity);
        // The carboy keeps its contents in TFC's fluid component, and is handled as TFC's own containers are
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new FluidContainerHandler(stack, ((GlassCarboyItem) stack.getItem()).containerInfo()), GLASS_CARBOY.get());
    }

    private Chemistry() {}
}
