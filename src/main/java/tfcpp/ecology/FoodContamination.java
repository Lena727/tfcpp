package tfcpp.ecology;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import net.dries007.tfc.common.component.food.FoodCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import tfcpp.core.TFCpp;

/**
 * The metals that a food item carries, kept in a hidden data component: it has no tooltip, and players are shown nothing of it (principle of v2.0, truth inside, instruments outside). See {@link FoodMetals} for the numbers and for how stacks stay stackable.
 */
public final class FoodContamination
{
    /** Quantised micrograms per item, one code for each of {@link FoodMetals#METALS}. A list, so that two stacks with the same codes are equal and stack. */
    public record Metals(List<Integer> codes)
    {
        public static final Codec<Metals> CODEC = Codec.INT.listOf().xmap(Metals::new, Metals::codes);
        public static final StreamCodec<io.netty.buffer.ByteBuf, Metals> STREAM_CODEC = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).map(Metals::new, Metals::codes);
    }

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TFCpp.MOD_ID);

    public static final Supplier<DataComponentType<Metals>> METALS = COMPONENTS.registerComponentType("food_metals", builder -> builder.persistent(Metals.CODEC).networkSynchronized(Metals.STREAM_CODEC).cacheEncoding());

    private FoodContamination() {}

    /** @return micrograms of each of {@link FoodMetals#METALS} in one item of the stack, or {@code null} if it carries none */
    @Nullable
    public static double[] get(ItemStack stack)
    {
        final @Nullable Metals metals = stack.get(METALS.get());
        if (metals == null)
        {
            return null;
        }
        final double[] out = new double[FoodMetals.METALS.length];
        for (int i = 0; i < out.length && i < metals.codes().size(); i++)
        {
            out[i] = FoodMetals.dequantise(metals.codes().get(i));
        }
        return out;
    }

    /** Sets what one item of the stack carries, in micrograms, quantised; none at all removes the component, so that the stack is clean food again. */
    public static void set(ItemStack stack, double[] micrograms)
    {
        final List<Integer> codes = new ArrayList<>(micrograms.length);
        boolean any = false;
        for (double value : micrograms)
        {
            final int code = FoodMetals.quantise(value);
            any |= code > 0;
            codes.add(code);
        }
        if (any)
        {
            stack.set(METALS.get(), new Metals(List.copyOf(codes)));
        }
        else
        {
            stack.remove(METALS.get());
        }
    }

    /**
     * A crop has been harvested from the soil at this position: each food item in the stack takes up the metals of the soil by the transfer factors, and the soil gives them up.
     *
     * @return {@code true} if the stack was marked
     */
    public static boolean harvest(ServerLevel level, BlockPos pos, ItemStack stack)
    {
        if (stack.isEmpty() || !FoodCapability.has(stack))
        {
            return false;
        }
        final @Nullable SoilCell soil = Pollution.soilForCrop(level, pos);
        if (soil == null)
        {
            return false;
        }
        final double[] perItem = new double[FoodMetals.METALS.length];
        boolean any = false;
        for (int i = 0; i < perItem.length; i++)
        {
            perItem[i] = FoodMetals.gramsPerItem(FoodMetals.METALS[i], soil.mgPerKg(FoodMetals.METALS[i])) * 1e6;
            any |= perItem[i] >= FoodMetals.FLOOR_MICROGRAMS;
        }
        if (!any)
        {
            return false;
        }
        set(stack, perItem);
        final double[] stored = get(stack), grams = new double[perItem.length];
        if (stored == null)
        {
            return false;
        }
        for (int i = 0; i < grams.length; i++)
        {
            grams[i] = stored[i] * 1e-6 * stack.getCount();
        }
        Pollution.uptake(level, pos, grams);
        return true;
    }

    /** A craft takes one item from each slot that holds any, whatever the size of the stack in it: the metal that goes into the output is that of one item of each. */
    public static void carryOnePerSlot(Iterable<ItemStack> slots, ItemStack output)
    {
        final List<ItemStack> ones = new ArrayList<>();
        for (ItemStack stack : slots)
        {
            if (!stack.isEmpty())
            {
                ones.add(stack.copyWithCount(1));
            }
        }
        carry(ones, output);
    }

    /**
     * A recipe has made {@code output} from these inputs: what they carried is added up and shared among the items of the output, so that a loaf holds what its grain held.
     */
    public static void carry(Iterable<ItemStack> inputs, ItemStack output)
    {
        if (output.isEmpty() || !FoodCapability.has(output))
        {
            return;
        }
        final List<double[]> per = new ArrayList<>();
        final List<Integer> counts = new ArrayList<>();
        for (ItemStack input : inputs)
        {
            final @Nullable double[] metals = get(input);
            if (metals != null)
            {
                per.add(metals);
                counts.add(input.getCount());
            }
        }
        if (per.isEmpty())
        {
            return;
        }
        final int[] countArray = counts.stream().mapToInt(Integer::intValue).toArray();
        set(output, FoodMetals.share(per.toArray(new double[0][]), countArray, output.getCount()));
    }
}
