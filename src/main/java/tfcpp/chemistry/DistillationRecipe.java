package tfcpp.chemistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;

/**
 * What a glass retort makes of a charge: one batch of a powder and one or two liquids, held in a working range of temperature for a time. Amounts are for the whole batch, in litres and moles, and are fractions.
 *
 * @param item           the powder that is charged, if the recipe takes one
 * @param items          pieces of it
 * @param fluid          the liquid in the flask, and how much of it
 * @param reagent        a second liquid in the flask, if the recipe takes one
 * @param minTemperature the lowest temperature at which it runs, in Celsius
 * @param maxTemperature the highest
 * @param seconds        time of the whole batch while it is in range
 * @param output         the liquid that comes over into the receiver
 * @param residue        a second liquid that is left in the flask
 * @param receiverWater  litres of water that the receiver takes up, 0 if none
 * @param gas            moles of each gas id that the batch gives off
 * @param byproduct      the powder that is left when the batch ends
 */
public record DistillationRecipe(Optional<Ingredient> item, int items, CarbonizationRecipe.Condensate fluid, Optional<CarbonizationRecipe.Condensate> reagent, float minTemperature, float maxTemperature,
                                 float seconds, Optional<CarbonizationRecipe.Condensate> output, Optional<CarbonizationRecipe.Condensate> residue, float receiverWater, Map<String, Float> gas,
                                 Optional<Byproduct> byproduct) implements Recipe<SingleRecipeInput>
{
    /** @param count pieces of the item that a batch leaves */
    public record Byproduct(Item item, int count)
    {
        static final Codec<Byproduct> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Byproduct::item),
            Codec.INT.fieldOf("count").forGetter(Byproduct::count)
        ).apply(i, Byproduct::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Byproduct> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM), Byproduct::item,
            ByteBufCodecs.INT, Byproduct::count,
            Byproduct::new
        );
    }

    private static final Codec<Map<String, Float>> GASES = Codec.unboundedMap(Codec.STRING, Codec.FLOAT);
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Float>> GASES_STREAM = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT);

    public static final MapCodec<DistillationRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Ingredient.CODEC_NONEMPTY.optionalFieldOf("item").forGetter(DistillationRecipe::item),
        Codec.INT.optionalFieldOf("items", 0).forGetter(DistillationRecipe::items),
        CarbonizationRecipe.Condensate.CODEC.fieldOf("fluid").forGetter(DistillationRecipe::fluid),
        CarbonizationRecipe.Condensate.CODEC.optionalFieldOf("reagent").forGetter(DistillationRecipe::reagent),
        Codec.FLOAT.fieldOf("min_temperature").forGetter(DistillationRecipe::minTemperature),
        Codec.FLOAT.fieldOf("max_temperature").forGetter(DistillationRecipe::maxTemperature),
        Codec.FLOAT.fieldOf("seconds").forGetter(DistillationRecipe::seconds),
        CarbonizationRecipe.Condensate.CODEC.optionalFieldOf("output").forGetter(DistillationRecipe::output),
        CarbonizationRecipe.Condensate.CODEC.optionalFieldOf("residue").forGetter(DistillationRecipe::residue),
        Codec.FLOAT.optionalFieldOf("receiver_water", 0f).forGetter(DistillationRecipe::receiverWater),
        GASES.optionalFieldOf("gas", Map.of()).forGetter(DistillationRecipe::gas),
        Byproduct.CODEC.optionalFieldOf("byproduct").forGetter(DistillationRecipe::byproduct)
    ).apply(i, DistillationRecipe::new));

    /** Written by hand, as there are more fields than a composite codec takes. */
    public static final StreamCodec<RegistryFriendlyByteBuf, DistillationRecipe> STREAM_CODEC = StreamCodec.of(DistillationRecipe::write, DistillationRecipe::read);

    private static void write(RegistryFriendlyByteBuf buffer, DistillationRecipe recipe)
    {
        ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC).encode(buffer, recipe.item);
        buffer.writeVarInt(recipe.items);
        CarbonizationRecipe.Condensate.STREAM_CODEC.encode(buffer, recipe.fluid);
        ByteBufCodecs.optional(CarbonizationRecipe.Condensate.STREAM_CODEC).encode(buffer, recipe.reagent);
        buffer.writeFloat(recipe.minTemperature);
        buffer.writeFloat(recipe.maxTemperature);
        buffer.writeFloat(recipe.seconds);
        ByteBufCodecs.optional(CarbonizationRecipe.Condensate.STREAM_CODEC).encode(buffer, recipe.output);
        ByteBufCodecs.optional(CarbonizationRecipe.Condensate.STREAM_CODEC).encode(buffer, recipe.residue);
        buffer.writeFloat(recipe.receiverWater);
        GASES_STREAM.encode(buffer, recipe.gas);
        ByteBufCodecs.optional(Byproduct.STREAM_CODEC).encode(buffer, recipe.byproduct);
    }

    private static DistillationRecipe read(RegistryFriendlyByteBuf buffer)
    {
        final Optional<Ingredient> item = ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC).decode(buffer);
        final int items = buffer.readVarInt();
        final CarbonizationRecipe.Condensate fluid = CarbonizationRecipe.Condensate.STREAM_CODEC.decode(buffer);
        final Optional<CarbonizationRecipe.Condensate> reagent = ByteBufCodecs.optional(CarbonizationRecipe.Condensate.STREAM_CODEC).decode(buffer);
        final float min = buffer.readFloat();
        final float max = buffer.readFloat();
        final float seconds = buffer.readFloat();
        final Optional<CarbonizationRecipe.Condensate> output = ByteBufCodecs.optional(CarbonizationRecipe.Condensate.STREAM_CODEC).decode(buffer);
        final Optional<CarbonizationRecipe.Condensate> residue = ByteBufCodecs.optional(CarbonizationRecipe.Condensate.STREAM_CODEC).decode(buffer);
        final float water = buffer.readFloat();
        final Map<String, Float> gas = GASES_STREAM.decode(buffer);
        return new DistillationRecipe(item, items, fluid, reagent, min, max, seconds, output, residue, water, gas, ByteBufCodecs.optional(Byproduct.STREAM_CODEC).decode(buffer));
    }

    /** @return the balance of this recipe, for {@link Distillation} */
    public Distillation.Recipe batch()
    {
        final float[] moles = new float[Gas.COUNT];
        gas.forEach((id, amount) -> {
            final Gas species = Gas.byId(id);
            if (species != null)
            {
                moles[species.ordinal()] = amount;
            }
        });
        return new Distillation.Recipe(items, fluid.litres(), reagent.map(CarbonizationRecipe.Condensate::litres).orElse(0f), minTemperature, maxTemperature, seconds,
            output.map(CarbonizationRecipe.Condensate::litres).orElse(0f), residue.map(CarbonizationRecipe.Condensate::litres).orElse(0f), receiverWater, moles, byproduct.map(Byproduct::count).orElse(0));
    }

    /** @return the same batch run in a receiver with no water in it: nothing takes up the hydrogen chloride, which goes into the air instead of the acid, and nothing comes over */
    public Distillation.Recipe unabsorbed()
    {
        final Distillation.Recipe batch = batch();
        final float[] moles = batch.gas().clone();
        output.ifPresent(product -> {
            final @Nullable Liquid liquid = Chemistry.liquidOf(product.fluid());
            if (liquid != null)
            {
                final float[] vapour = AcidVapour.of(liquid, product.litres());
                for (int i = 0; i < Gas.COUNT; i++)
                {
                    moles[i] += vapour[i];
                }
            }
        });
        return new Distillation.Recipe(batch.items(), batch.inputLitres(), batch.reagentLitres(), batch.minTemperature(), batch.maxTemperature(), batch.seconds(), 0f, batch.residueLitres(), 0f, moles, batch.byproducts());
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level)
    {
        return item.map(ingredient -> ingredient.test(input.item())).orElse(input.item().isEmpty());
    }

    /** @return {@code true} if this recipe takes exactly these liquids, in either tank: a flask with a stray liquid in it does not do what the recipe says */
    public boolean takes(Fluid first, Fluid second)
    {
        final Fluid wanted = fluid.fluid();
        final Fluid extra = reagent.map(CarbonizationRecipe.Condensate::fluid).orElse(null);
        if (extra == null)
        {
            return (first == wanted && second == null) || (second == wanted && first == null);
        }
        return (first == wanted && second == extra) || (first == extra && second == wanted);
    }

    /**
     * @param first  the liquid in one input tank, {@code null} if empty
     * @param second the liquid in the other
     * @param stack  the powder in the slot
     * @return the recipe that these make, if any
     */
    @Nullable
    public static RecipeHolder<DistillationRecipe> find(Level level, @Nullable Fluid first, @Nullable Fluid second, ItemStack stack)
    {
        final SingleRecipeInput input = new SingleRecipeInput(stack);
        for (RecipeHolder<DistillationRecipe> holder : level.getRecipeManager().getAllRecipesFor(Chemistry.DISTILLATION_RECIPE.get()))
        {
            if (holder.value().takes(first, second) && holder.value().matches(input, level))
            {
                return holder;
            }
        }
        return null;
    }

    /** @return {@code true} if some recipe takes this liquid into the flask, as its main liquid or its second one */
    public static boolean takesLiquid(Level level, Fluid candidate)
    {
        for (RecipeHolder<DistillationRecipe> holder : level.getRecipeManager().getAllRecipesFor(Chemistry.DISTILLATION_RECIPE.get()))
        {
            final DistillationRecipe recipe = holder.value();
            if (recipe.fluid.fluid() == candidate || recipe.reagent.map(extra -> extra.fluid() == candidate).orElse(false))
            {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} if some recipe charges this powder */
    public static boolean isCharge(Level level, ItemStack stack)
    {
        for (RecipeHolder<DistillationRecipe> holder : level.getRecipeManager().getAllRecipesFor(Chemistry.DISTILLATION_RECIPE.get()))
        {
            if (holder.value().item.map(ingredient -> ingredient.test(stack)).orElse(false))
            {
                return true;
            }
        }
        return false;
    }

    /** @return the stack of the powder that a batch of this recipe takes, for putting back what has not been used */
    public ItemStack chargeStack(int count)
    {
        return item.map(ingredient -> {
            final ItemStack[] options = ingredient.getItems();
            return options.length == 0 ? ItemStack.EMPTY : options[0].copyWithCount(count);
        }).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput recipeInput, HolderLookup.Provider registries)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height)
    {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial()
    {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return Chemistry.DISTILLATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return Chemistry.DISTILLATION_RECIPE.get();
    }

    public static final class Serializer implements RecipeSerializer<DistillationRecipe>
    {
        @Override
        public MapCodec<DistillationRecipe> codec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DistillationRecipe> streamCodec()
        {
            return STREAM_CODEC;
        }
    }
}
