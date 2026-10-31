package tfcpp.pneumatics.gas;

/**
 * Air composition arithmetic for one cell. A cell is one cubic metre, holding {@link #CELL_MOLES} of gas at 1 atm and 20 C (from pV = nRT).
 */
public final class GasMath
{
    public static final float CELL_MOLES = 41.6f;
    public static final float AIR_MOLAR_MASS = 28.97f;
    public static final float OXYGEN_IN_AIR = 0.2095f;

    public static float total(float[] cell)
    {
        float total = 0;
        for (float amount : cell)
        {
            total += amount;
        }
        return total;
    }

    /** @return mole fraction of one species in a cell */
    public static float fraction(float moles)
    {
        return moles / CELL_MOLES;
    }

    public static float ppm(float moles)
    {
        return fraction(moles) * 1e6f;
    }

    /** @return oxygen mole fraction, which is what remains of the air that has not been displaced */
    public static float oxygenFraction(float[] cell)
    {
        return OXYGEN_IN_AIR * Math.max(0, 1 - fraction(total(cell)));
    }

    /** @return pressure in atm: a cell only goes over 1 atm once the added gas has displaced all of its air */
    public static float pressure(float[] cell)
    {
        return Math.max(1, fraction(total(cell)));
    }

    private GasMath() {}
}
