package tfcpp.pneumatics.gas;

/**
 * When a mixture of gases in air can carry a flame. Limits are volume fractions in air at 20 C and 1 atm, from the Bureau of Mines tables (Coward and Jones, Zabetakis). Mixtures of fuels follow Le Chatelier's rule.
 * <p>
 * A flame also needs oxygen: adding inert gas to a flammable mixture stops it burning at the minimum oxygen concentration, which is why blackdamp makes firedamp safer.
 */
public final class Flammability
{
    private record Fuel(Gas gas, float lower, float upper, float minimumOxygen) {}

    private static final Fuel[] FUELS = {
        new Fuel(Gas.METHANE, 0.05f, 0.15f, 0.12f),
        new Fuel(Gas.HYDROGEN, 0.04f, 0.75f, 0.05f),
        new Fuel(Gas.CARBON_MONOXIDE, 0.125f, 0.74f, 0.055f),
        new Fuel(Gas.HYDROGEN_SULFIDE, 0.043f, 0.46f, 0.075f),
        new Fuel(Gas.AMMONIA, 0.15f, 0.28f, 0.15f)
    };

    /** The most dilute a fuel can be and still matter: 4% in air is the lowest lower limit of any fuel. */
    private static final float MINIMUM_FUEL_MOLES = 0.04f * GasMath.CELL_MOLES;

    /** @return the mixture's fuel content as a fraction of its lower limit. At 1 or more, and below its upper limit, it can burn. */
    public static float relativeToLowerLimit(float[] cell)
    {
        float sum = 0;
        for (Fuel fuel : FUELS)
        {
            sum += GasMath.fraction(cell[fuel.gas.ordinal()]) / fuel.lower;
        }
        return sum;
    }

    public static boolean isFlammable(float[] cell)
    {
        float fuelMoles = 0, upperSum = 0, oxygenNeed = 0;
        for (Fuel fuel : FUELS)
        {
            final float moles = cell[fuel.gas.ordinal()];
            fuelMoles += moles;
            upperSum += GasMath.fraction(moles) / fuel.upper;
            oxygenNeed += moles * fuel.minimumOxygen;
        }
        return fuelMoles >= MINIMUM_FUEL_MOLES
            && relativeToLowerLimit(cell) >= 1
            && upperSum <= 1
            && GasMath.oxygenFraction(cell) >= oxygenNeed / fuelMoles;
    }

    private Flammability() {}
}
