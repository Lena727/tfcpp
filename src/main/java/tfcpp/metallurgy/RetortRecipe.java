package tfcpp.metallurgy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import tfcpp.pneumatics.gas.Gas;

/**
 * One item of charge heated in a closed retort.
 *
 * @param input       the charge
 * @param carbon      moles of carbon this takes from the retort's reductant, zero if it needs no reducing
 * @param temperature the temperature the reaction needs
 * @param result      the liquid that condenses, as long as there is a condenser
 * @param vapor       what escapes in its place if there is no condenser, in moles of each gas id
 * @param byproducts  what always leaves through the retort's vent, in moles of each gas id
 */
public record RetortRecipe(Ingredient input, float carbon, float temperature, FluidStack result, Map<String, Float> vapor, Map<String, Float> byproducts) implements Recipe<SingleRecipeInput>
{
    private static final Codec<Map<String, Float>> GASES = Codec.unboundedMap(Codec.STRING, Codec.FLOAT);
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Float>> GASES_STREAM = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT);

    public static final MapCodec<RetortRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(RetortRecipe::input),
        Codec.FLOAT.optionalFieldOf("carbon", 0f).forGetter(RetortRecipe::carbon),
        Codec.FLOAT.fieldOf("temperature").forGetter(RetortRecipe::temperature),
        FluidStack.CODEC.fieldOf("result").forGetter(RetortRecipe::result),
        GASES.fieldOf("vapor").forGetter(RetortRecipe::vapor),
        GASES.optionalFieldOf("byproducts", Map.of()).forGetter(RetortRecipe::byproducts)
    ).apply(i, RetortRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RetortRecipe> STREAM_CODEC = StreamCodec.composite(
        Ingredient.CONTENTS_STREAM_CODEC, RetortRecipe::input,
        ByteBufCodecs.FLOAT, RetortRecipe::carbon,
        ByteBufCodecs.FLOAT, RetortRecipe::temperature,
        FluidStack.STREAM_CODEC, RetortRecipe::result,
        GASES_STREAM, RetortRecipe::vapor,
        GASES_STREAM, RetortRecipe::byproducts,
        RetortRecipe::new
    );

    /** Adds a map of gases to an array indexed by {@link Gas}. */
    static void addTo(float[] gases, Map<String, Float> amounts)
    {
        amounts.forEach((id, moles) -> {
            final Gas gas = Gas.byId(id);
            if (gas != null)
            {
                gases[gas.ordinal()] += moles;
            }
        });
    }

    @Override
    public boolean matches(SingleRecipeInput recipeInput, Level level)
    {
        return input.test(recipeInput.item());
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
        return Metallurgy.RETORT_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return Metallurgy.RETORT_RECIPE.get();
    }

    public static final class Serializer implements RecipeSerializer<RetortRecipe>
    {
        @Override
        public MapCodec<RetortRecipe> codec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RetortRecipe> streamCodec()
        {
            return STREAM_CODEC;
        }
    }
}
