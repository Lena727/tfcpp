package tfcpp.chemistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.dries007.tfc.common.capabilities.BlockCapabilities;
import net.minecraft.core.registries.Registries;
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

    public static final Supplier<BlockEntityType<CokeOvenBlockEntity>> COKE_OVEN_ENTITY = Metallurgy.BLOCK_ENTITIES.register("coke_oven", () -> BlockEntityType.Builder.of(CokeOvenBlockEntity::new, COKE_OVEN.get()).build(null));
    public static final Supplier<BlockEntityType<GasBurnerBlockEntity>> GAS_BURNER_ENTITY = Metallurgy.BLOCK_ENTITIES.register("gas_burner", () -> BlockEntityType.Builder.of(GasBurnerBlockEntity::new, GAS_BURNER.get()).build(null));
    public static final Supplier<BlockEntityType<LeadChamberBlockEntity>> LEAD_CHAMBER_ENTITY = Metallurgy.BLOCK_ENTITIES.register("lead_chamber", () -> BlockEntityType.Builder.of(LeadChamberBlockEntity::new, LEAD_CHAMBER.get()).build(null));
    public static final Supplier<BlockEntityType<SulfurBurnerBlockEntity>> SULFUR_BURNER_ENTITY = Metallurgy.BLOCK_ENTITIES.register("sulfur_burner", () -> BlockEntityType.Builder.of(SulfurBurnerBlockEntity::new, SULFUR_BURNER.get()).build(null));

    public static final Supplier<RecipeType<CarbonizationRecipe>> CARBONIZATION_RECIPE = Metallurgy.RECIPE_TYPES.register("carbonization", () -> RecipeType.simple(TFCpp.id("carbonization")));
    public static final Supplier<RecipeSerializer<CarbonizationRecipe>> CARBONIZATION_SERIALIZER = Metallurgy.RECIPE_SERIALIZERS.register("carbonization", CarbonizationRecipe.Serializer::new);

    public static void addToTab(CreativeModeTab.Output output)
    {
        output.accept(COKE.get());
        output.accept(LIGNITE_CHAR.get());
        output.accept(COKE_OVEN_ITEM.get());
        output.accept(GAS_BURNER_ITEM.get());
        output.accept(LEAD_CHAMBER_ITEM.get());
        output.accept(SULFUR_BURNER_ITEM.get());
    }

    public static void register(IEventBus modBus)
    {
        modBus.addListener(Chemistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(BlockCapabilities.HEAT, COKE_OVEN_ENTITY.get(), (entity, side) -> entity);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, COKE_OVEN_ENTITY.get(), (entity, side) -> entity.tanks());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LEAD_CHAMBER_ENTITY.get(), (entity, side) -> entity.outlet());
    }

    private Chemistry() {}
}
