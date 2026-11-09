package tfcpp.ecology;

import org.jetbrains.annotations.Nullable;

/**
 * A body of water and what it holds now, as the later stages ask for it: drinking (stage E), irrigation (stage D) and colour (stage F). Free of Minecraft types.
 *
 * @param type a reach of a river, the still water of a regional cell, or the sea
 * @param id the id of the reach, {@link ReachInfo#SEA} for the sea
 * @param resolved {@code false} while the river graph is still being asked about the place: the figures are then zero and mean nothing
 * @param info the geometry, or {@code null} for the sea and for water that is not resolved
 * @param perLitre for each pollutant, in its unit in a litre of the water: grams of a metal, equivalents of acid, kilograms of tar, litres of sewage
 * @param inWater for each pollutant, the whole reach's content of the water column
 * @param onBed for each pollutant, the whole reach's content of its bed
 */
public record WaterBody(Type type, long id, boolean resolved, @Nullable ReachInfo info, double[] perLitre, double[] inWater, double[] onBed)
{
    public enum Type
    {
        RIVER, LAKE, SEA
    }

    public static WaterBody sea()
    {
        return new WaterBody(Type.SEA, ReachInfo.SEA, true, null, new double[Pollutant.COUNT], new double[Pollutant.COUNT], new double[Pollutant.COUNT]);
    }

    public static WaterBody unresolved(Type type)
    {
        return new WaterBody(type, ReachInfo.SEA, false, null, new double[Pollutant.COUNT], new double[Pollutant.COUNT], new double[Pollutant.COUNT]);
    }

    /** @return the amount of the pollutant in a litre of the water, in the unit of the pollutant */
    public double perLitre(Pollutant pollutant)
    {
        return perLitre[pollutant.ordinal()];
    }

    /** @return micrograms in a litre, for a metal */
    public double microgramsPerLitre(Pollutant pollutant)
    {
        return perLitre[pollutant.ordinal()] * 1e6;
    }

    /** @return the pH that the free acid in the water would give if nothing else buffered it, and 7.0 for water with none; the acid that is left after the alkalinity has been used up is strong acid, so pH is minus the logarithm of its equivalents per litre. Rounded to the range of water, 1 to 7 */
    public double pH()
    {
        final double free = perLitre[Pollutant.ACIDITY.ordinal()];
        if (!(free > 1e-7))
        {
            return 7.0;
        }
        return Math.max(1.0, -Math.log10(free));
    }
}
