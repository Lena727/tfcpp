package tfcpp.health;

import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import tfcpp.ecology.Waters;

/**
 * The quality of the water in a fluid stack: read, written, and mixed where two stacks of water that differ only in it meet. Fresh water only carries it; every other fluid is left as it is.
 */
public final class FluidQuality
{
    private FluidQuality() {}

    /** @return whether this fluid is fresh water, which is the only kind that carries a quality */
    public static boolean carries(Fluid fluid)
    {
        final @Nullable Waters.Kind kind = Waters.kindOf(fluid.defaultFluidState());
        return kind != null && kind != Waters.Kind.SEA;
    }

    /** @return the quality of the water in the stack, clean if it carries none */
    public static WaterQuality get(FluidStack stack)
    {
        return stack.isEmpty() ? WaterQuality.CLEAN : WaterQuality.decode(stack.get(Hygiene.WATER_QUALITY.get()));
    }

    /** Sets the quality of the water in the stack (in place); water with nothing in it carries no component. */
    public static void set(FluidStack stack, WaterQuality quality)
    {
        final WaterQuality.Stored codes = quality.encode();
        if (codes == null)
        {
            stack.remove(Hygiene.WATER_QUALITY.get());
        }
        else
        {
            stack.set(Hygiene.WATER_QUALITY.get(), codes);
        }
    }

    /** @return a copy of the stack with this quality */
    public static FluidStack with(FluidStack stack, WaterQuality quality)
    {
        final FluidStack copy = stack.copy();
        if (carries(copy.getFluid()))
        {
            set(copy, quality);
        }
        return copy;
    }

    /** @return whether two stacks are water of the same kind that differ only in what they carry, so that they can be mixed (and, when their stored values are the same, are simply the same fluid and need no help) */
    public static boolean mergeable(FluidStack a, FluidStack b)
    {
        if (a.isEmpty() || b.isEmpty() || a.getFluid() != b.getFluid() || !carries(a.getFluid()))
        {
            return false;
        }
        final WaterQuality.Stored first = a.get(Hygiene.WATER_QUALITY.get()), second = b.get(Hygiene.WATER_QUALITY.get());
        if (java.util.Objects.equals(first, second))
        {
            return false; // The same: nothing to do, and the ordinary path is the faster
        }
        final FluidStack strippedA = a.copyWithAmount(1), strippedB = b.copyWithAmount(1);
        strippedA.remove(Hygiene.WATER_QUALITY.get());
        strippedB.remove(Hygiene.WATER_QUALITY.get());
        return FluidStack.isSameFluidSameComponents(strippedA, strippedB);
    }

    /** @return the quality of {@code held} mixed with {@code added} litres of {@code incoming}, to be set on the stack that holds the sum */
    public static WaterQuality mixed(FluidStack held, int heldAmount, FluidStack incoming, int addedAmount)
    {
        return WaterQuality.mix(get(held), heldAmount, get(incoming), addedAmount);
    }

    /** @return the exact values of a quality as a list tag for a save, or {@code null} for water with nothing in it */
    @Nullable
    public static ListTag toTag(@Nullable WaterQuality quality)
    {
        final WaterQuality.Stored stored = quality == null ? null : quality.encode();
        if (stored == null)
        {
            return null;
        }
        final ListTag list = new ListTag();
        for (double value : stored.values())
        {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    public static WaterQuality fromTag(ListTag list)
    {
        final java.util.List<Double> values = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++)
        {
            values.add(list.getDouble(i));
        }
        return WaterQuality.decode(new WaterQuality.Stored(values));
    }

    /**
     * Water of this quality runs into the ground at this position: what it carries goes into the soil (sewage and the shares of the shedders in litres, metals in grams, acid in equivalents).
     *
     * @param litres how much water, on the scale of the tanks of the mod (1 mB is a litre)
     */
    public static void soak(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, WaterQuality quality, double litres)
    {
        if (!(litres > 0.0))
        {
            return;
        }
        tfcpp.ecology.Pollution.deposit(level, pos, tfcpp.ecology.Pollutant.SEWAGE, quality.faecal * litres);
        tfcpp.ecology.Pollution.deposit(level, pos, tfcpp.ecology.Pollutant.SHED_DYSENTERY, quality.shedDysentery * litres);
        tfcpp.ecology.Pollution.deposit(level, pos, tfcpp.ecology.Pollutant.SHED_TYPHOID, quality.shedTyphoid * litres);
        tfcpp.ecology.Pollution.deposit(level, pos, tfcpp.ecology.Pollutant.ACIDITY, quality.acidity * litres / 1000.0);
        for (int i = 0; i < tfcpp.ecology.FoodMetals.METALS.length; i++)
        {
            tfcpp.ecology.Pollution.deposit(level, pos, tfcpp.ecology.FoodMetals.METALS[i], quality.metal(i) * litres / 1e6);
        }
    }

    /** The tag type of {@link #toTag}. */
    public static final int TAG_TYPE = Tag.TAG_DOUBLE;
}
