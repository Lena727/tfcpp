package tfcpp.health;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

import tfcpp.ecology.FoodMetals;

/**
 * What a volume of fresh water carries besides water: sewage and the share of it that came from people shedding each disease (litres of sewage in a litre of water), the metals of {@link FoodMetals#METALS} in micrograms per litre, and free acid in milliequivalents per litre. Free of Minecraft types, immutable.
 * <p>
 * It rides on a fluid in a hidden data component with its exact values ({@link Stored}). Only water taken from the world is rounded ({@link #quantised}: a logarithmic scale, steps of a factor of 2^(1/32), about 2 %), so that water of one place at nearly the same state is one fluid; a mixture keeps its exact concentrations, so that any number of small pours conserves what the water carries. Tanks and jugs merge waters whose values differ ({@link FluidQuality#mergeable}).
 */
public final class WaterQuality
{
    public static final WaterQuality CLEAN = new WaterQuality(0.0, 0.0, 0.0, new double[FoodMetals.METALS.length], 0.0);

    /** The floor of each quantity under which it is taken as none: 1e-9 litre of sewage per litre (a thousandth of a part per million), 1e-12 for the shed shares, 0.01 microgram per litre of a metal, 1e-4 milliequivalents per litre of acid. */
    private static final double FAECAL_FLOOR = 1e-9, SHED_FLOOR = 1e-12, METAL_FLOOR = 0.01, ACID_FLOOR = 1e-4;
    private static final double STEPS_PER_OCTAVE = 32.0;
    private static final int FIELDS = 4 + FoodMetals.METALS.length;

    /** Litres of sewage in a litre of the water. */
    public final double faecal;
    /** The part of that sewage which came from people shedding Shigella, and the part from people shedding S. Typhi. */
    public final double shedDysentery, shedTyphoid;
    /** Micrograms per litre of each of {@link FoodMetals#METALS}. */
    private final double[] metals;
    /** Milliequivalents per litre of free acid. */
    public final double acidity;

    /** The component: the exact value of each quantity, in the order sewage, shedding shares, acid, metals. A list, so that two with the same values are equal. */
    public record Stored(List<Double> values)
    {
    }

    public WaterQuality(double faecal, double shedDysentery, double shedTyphoid, double[] metals, double acidity)
    {
        this.faecal = Math.max(faecal, 0.0);
        this.shedDysentery = Math.max(shedDysentery, 0.0);
        this.shedTyphoid = Math.max(shedTyphoid, 0.0);
        this.metals = metals.clone();
        this.acidity = Math.max(acidity, 0.0);
    }

    /** @return micrograms per litre of the metal */
    public double metal(int index)
    {
        return metals[index];
    }

    public double[] metals()
    {
        return metals.clone();
    }

    public boolean hasPathogens()
    {
        return faecal > 0.0 || shedDysentery > 0.0 || shedTyphoid > 0.0;
    }

    public boolean isClean()
    {
        if (hasPathogens() || acidity > 0.0)
        {
            return false;
        }
        for (double metal : metals)
        {
            if (metal > 0.0)
            {
                return false;
            }
        }
        return true;
    }

    /** @return the same water after it has been boiled: the sewage is gone, being what carried the living organisms, and the metals and acid stay (boiling takes the water off and leaves them, which makes them the more concentrated; that is not counted) */
    public WaterQuality boiled()
    {
        return new WaterQuality(0.0, 0.0, 0.0, metals, acidity);
    }

    /** @return this and another water mixed, by volume: what a vessel holds when two kinds of water are put into it */
    public static WaterQuality mix(@Nullable WaterQuality first, double firstLitres, @Nullable WaterQuality second, double secondLitres)
    {
        final WaterQuality a = first == null ? CLEAN : first, b = second == null ? CLEAN : second;
        final double total = firstLitres + secondLitres;
        if (!(total > 0.0))
        {
            return a;
        }
        final double wa = firstLitres / total, wb = secondLitres / total;
        final double[] metals = new double[a.metals.length];
        for (int i = 0; i < metals.length; i++)
        {
            metals[i] = a.metals[i] * wa + b.metals[i] * wb;
        }
        return new WaterQuality(a.faecal * wa + b.faecal * wb, a.shedDysentery * wa + b.shedDysentery * wb, a.shedTyphoid * wa + b.shedTyphoid * wb, metals, a.acidity * wa + b.acidity * wb);
    }

    /** @return the water with this much more sewage (the contribution of a source) */
    public WaterQuality plus(double faecal, double shedDysentery, double shedTyphoid)
    {
        return new WaterQuality(this.faecal + faecal, this.shedDysentery + shedDysentery, this.shedTyphoid + shedTyphoid, metals, acidity);
    }

    // --- The component -----------------------------------------------------------------------------------------------------------------------------------------------------

    /** @return the code of a value on the scale of its floor, 0 for none */
    static int quantise(double value, double floor)
    {
        if (!(value >= floor))
        {
            return 0;
        }
        return 1 + (int) Math.round(STEPS_PER_OCTAVE * Math.log(value / floor) / Math.log(2.0));
    }

    static double dequantise(int code, double floor)
    {
        return code <= 0 ? 0.0 : floor * Math.pow(2.0, (code - 1) / STEPS_PER_OCTAVE);
    }

    private static double snap(double value, double floor)
    {
        return dequantise(quantise(value, floor), floor);
    }

    /**
     * @return this water as it is taken from the world: every quantity rounded to a step of 2^(1/32) (about 2 %) on a logarithmic scale, so that water taken from one place at nearly the same hour is one fluid and its containers stack. Only the source rounds; a mixture of waters keeps its exact concentrations, which is what makes a pour of any size conserve what it carries.
     */
    public WaterQuality quantised()
    {
        final double[] rounded = new double[metals.length];
        for (int i = 0; i < rounded.length; i++)
        {
            rounded[i] = snap(metals[i], METAL_FLOOR);
        }
        return new WaterQuality(snap(faecal, FAECAL_FLOOR), snap(shedDysentery, SHED_FLOOR), snap(shedTyphoid, SHED_FLOOR), rounded, snap(acidity, ACID_FLOOR));
    }

    /** @return the exact values of this water for the component, or {@code null} for water that has nothing in it, which carries no component and stacks with plain water. What is under a floor is none. */
    @Nullable
    public Stored encode()
    {
        final List<Double> values = new ArrayList<>(FIELDS);
        values.add(faecal >= FAECAL_FLOOR ? faecal : 0.0);
        values.add(shedDysentery >= SHED_FLOOR ? shedDysentery : 0.0);
        values.add(shedTyphoid >= SHED_FLOOR ? shedTyphoid : 0.0);
        values.add(acidity >= ACID_FLOOR ? acidity : 0.0);
        for (double metal : metals)
        {
            values.add(metal >= METAL_FLOOR ? metal : 0.0);
        }
        boolean any = false;
        for (double value : values)
        {
            any |= value > 0.0;
        }
        return any ? new Stored(List.copyOf(values)) : null;
    }

    public static WaterQuality decode(@Nullable Stored stored)
    {
        if (stored == null || stored.values().size() < FIELDS)
        {
            return CLEAN;
        }
        final List<Double> list = stored.values();
        final double[] metals = new double[FoodMetals.METALS.length];
        for (int i = 0; i < metals.length; i++)
        {
            metals[i] = list.get(4 + i);
        }
        return new WaterQuality(list.get(0), list.get(1), list.get(2), metals, list.get(3));
    }

    @Override
    public String toString()
    {
        return "WaterQuality[sewage %.3g L/L (shigella shedders %.3g, typhoid shedders %.3g), acid %.3g meq/L, metals %s ug/L]".formatted(faecal, shedDysentery, shedTyphoid, acidity, java.util.Arrays.toString(metals));
    }
}
