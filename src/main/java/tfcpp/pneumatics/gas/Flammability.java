package tfcpp.pneumatics.gas;

/**
 * When a mixture of gases and dust in air can carry a flame. Limits are volume fractions in air at 20 C and 1 atm, from the Bureau of Mines tables (Coward and Jones, Zabetakis). Mixtures of fuels follow Le Chatelier's rule.
 * <p>
 * Coal dust has a lower limit of 50 g/m3 and an upper of 2 kg/m3, which in the unit of a cell (1 kg of dust) is 0.05 and 2. Added to the sum by the same rule, a little dust makes a lean mixture of firedamp explosive, and a little firedamp does the same for a cloud of dust too thin to burn: this is the hybrid effect, and the reason a small outburst of gas in a dusty gallery has set off so many disasters.
 * <p>
 * A flame also needs oxygen: adding inert gas to a flammable mixture stops it burning at the minimum oxygen concentration, which is why blackdamp makes firedamp safer. With several fuels the minimum is taken as the mean of theirs, each counted by its share of the sum of Le Chatelier's terms.
 */
public final class Flammability
{
    /** Limits are fractions of the gas in a cell. For dust that is kilograms per cell divided by the cell's moles, so the same arithmetic serves both. */
    private record Fuel(Gas gas, float lower, float upper, float minimumOxygen) {}

    private static final Fuel[] FUELS = {
        new Fuel(Gas.METHANE, 0.05f, 0.15f, 0.12f),
        new Fuel(Gas.HYDROGEN, 0.04f, 0.75f, 0.05f),
        new Fuel(Gas.CARBON_MONOXIDE, 0.125f, 0.74f, 0.055f),
        new Fuel(Gas.HYDROGEN_SULFIDE, 0.043f, 0.46f, 0.075f),
        new Fuel(Gas.AMMONIA, 0.15f, 0.28f, 0.15f),
        new Fuel(Gas.COAL_DUST, 0.05f / GasMath.CELL_MOLES, 2f / GasMath.CELL_MOLES, 0.12f)
    };

    /** Mixtures sitting exactly on a limit, such as two fuels each at half of theirs, must not be lost to the rounding of a float. */
    private static final float LIMIT_TOLERANCE = 1e-5f;

    /** @return the fuel gases' content as a fraction of their lower limit, which is what a flame lamp can sense. Dust is not in it: see {@link #relativeToLowerLimitWithDust}. */
    public static float relativeToLowerLimit(float[] cell)
    {
        return sum(cell, false);
    }

    /** @return the mixture's fuel content, gases and coal dust together, as a fraction of its lower limit. At 1 or more, and below its upper limit, it can burn. */
    public static float relativeToLowerLimitWithDust(float[] cell)
    {
        return sum(cell, true);
    }

    private static float sum(float[] cell, boolean withDust)
    {
        float sum = 0;
        for (Fuel fuel : FUELS)
        {
            if (withDust || !fuel.gas.isParticulate())
            {
                sum += GasMath.fraction(cell[fuel.gas.ordinal()]) / fuel.lower;
            }
        }
        return sum;
    }

    public static boolean isFlammable(float[] cell)
    {
        float lowerSum = 0, upperSum = 0, oxygenNeed = 0;
        for (Fuel fuel : FUELS)
        {
            final float fraction = GasMath.fraction(cell[fuel.gas.ordinal()]);
            final float share = fraction / fuel.lower;
            lowerSum += share;
            upperSum += fraction / fuel.upper;
            oxygenNeed += share * fuel.minimumOxygen;
        }
        // The lower sum of 1 alone is enough: it already rules out gas too dilute to matter, which the earlier cut-off in moles did only for gases
        return lowerSum >= 1 - LIMIT_TOLERANCE
            && upperSum <= 1
            && GasMath.oxygenFraction(cell) >= oxygenNeed / lowerSum;
    }

    private Flammability() {}
}
