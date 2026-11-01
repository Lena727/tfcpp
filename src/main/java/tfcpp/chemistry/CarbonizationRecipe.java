package tfcpp.chemistry;

import java.util.HashMap;
import java.util.Map;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import tfcpp.pneumatics.gas.Gas;

/**
 * What a charge of one material becomes when it is heated without air in a coke oven. Amounts are per piece of charge and are fractions: the oven keeps what does not come to a whole unit.
 *
 * @param input       the charge
 * @param temperature the temperature it needs, in Celsius
 * @param seconds     how long the whole charge takes while it is hot enough
 * @param solid       the solid that is left
 * @param tar         the tar that condenses
 * @param liquor      the watery liquor that condenses
 * @param gas         moles of each gas id that leave per piece
 */
public record CarbonizationRecipe(Ingredient input, float temperature, float seconds, Solid solid, Condensate tar, Condensate liquor, Map<String, Float> gas) implements Recipe<SingleRecipeInput>
{
    /** @param perPiece pieces of the item for each piece of charge */
    public record Solid(Item item, float perPiece)
    {
        static final Codec<Solid> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Solid::item),
            Codec.FLOAT.fieldOf("per_piece").forGetter(Solid::perPiece)
        ).apply(i, Solid::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Solid> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), Solid::item,
            ByteBufCodecs.FLOAT, Solid::perPiece,
            Solid::new
        );
    }

    /** @param litres litres for each piece of charge */
    public record Condensate(Fluid fluid, float litres)
    {
        static final Codec<Condensate> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(Condensate::fluid),
            Codec.FLOAT.fieldOf("litres").forGetter(Condensate::litres)
        ).apply(i, Condensate::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Condensate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.FLUID), Condensate::fluid,
            ByteBufCodecs.FLOAT, Condensate::litres,
            Condensate::new
        );
    }

    private static final Codec<Map<String, Float>> GASES = Codec.unboundedMap(Codec.STRING, Codec.FLOAT);
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Float>> GASES_STREAM = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT);

    public static final MapCodec<CarbonizationRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(CarbonizationRecipe::input),
        Codec.FLOAT.fieldOf("temperature").forGetter(CarbonizationRecipe::temperature),
        Codec.FLOAT.fieldOf("seconds").forGetter(CarbonizationRecipe::seconds),
        Solid.CODEC.fieldOf("solid").forGetter(CarbonizationRecipe::solid),
        Condensate.CODEC.fieldOf("tar").forGetter(CarbonizationRecipe::tar),
        Condensate.CODEC.fieldOf("liquor").forGetter(CarbonizationRecipe::liquor),
        GASES.fieldOf("gas").forGetter(CarbonizationRecipe::gas)
    ).apply(i, CarbonizationRecipe::new));

    /** Written by hand, as there are more fields than a composite codec takes. */
    public static final StreamCodec<RegistryFriendlyByteBuf, CarbonizationRecipe> STREAM_CODEC = StreamCodec.of(CarbonizationRecipe::write, CarbonizationRecipe::read);

    private static void write(RegistryFriendlyByteBuf buffer, CarbonizationRecipe recipe)
    {
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.input);
        buffer.writeFloat(recipe.temperature);
        buffer.writeFloat(recipe.seconds);
        Solid.STREAM_CODEC.encode(buffer, recipe.solid);
        Condensate.STREAM_CODEC.encode(buffer, recipe.tar);
        Condensate.STREAM_CODEC.encode(buffer, recipe.liquor);
        GASES_STREAM.encode(buffer, recipe.gas);
    }

    private static CarbonizationRecipe read(RegistryFriendlyByteBuf buffer)
    {
        final Ingredient input = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        final float temperature = buffer.readFloat();
        final float seconds = buffer.readFloat();
        final Solid solid = Solid.STREAM_CODEC.decode(buffer);
        final Condensate tar = Condensate.STREAM_CODEC.decode(buffer);
        final Condensate liquor = Condensate.STREAM_CODEC.decode(buffer);
        return new CarbonizationRecipe(input, temperature, seconds, solid, tar, liquor, GASES_STREAM.decode(buffer));
    }

    /** @return the balance of one piece of this charge, for {@link CarbonizationBatch} */
    public CarbonizationBatch.Yield yield()
    {
        final float[] moles = new float[Gas.COUNT];
        gas.forEach((id, amount) -> {
            final Gas species = Gas.byId(id);
            if (species != null)
            {
                moles[species.ordinal()] = amount;
            }
        });
        return new CarbonizationBatch.Yield(seconds, temperature, solid.perPiece(), tar.litres(), liquor.litres(), moles);
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
        return Chemistry.CARBONIZATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return Chemistry.CARBONIZATION_RECIPE.get();
    }

    public static final class Serializer implements RecipeSerializer<CarbonizationRecipe>
    {
        @Override
        public MapCodec<CarbonizationRecipe> codec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CarbonizationRecipe> streamCodec()
        {
            return STREAM_CODEC;
        }
    }
}
