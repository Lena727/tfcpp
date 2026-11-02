package tfcpp.pneumatics.gas;

import org.jetbrains.annotations.Nullable;

/**
 * Air composition arithmetic for one cell. A cell is one cubic metre, holding {@link #CELL_MOLES} of gas at 1 atm and 20 C (from pV = nRT).
 */
public final class GasMath
{
    public static final float CELL_MOLES = 41.6f;
    public static final float AIR_MOLAR_MASS = 28.97f;
    public static final float OXYGEN_IN_AIR = 0.2095f;

    /** Fraction of the gas at a position carried off each second by the wind right at the top of an open column, which is the surface of the ground. */
    public static final float OPEN_AIR_EXCHANGE_RATE = 0.3f;
    /** The depth in an open column over which the exchange with the atmosphere falls to 1/e. Air in a shaft is still, and only what diffuses and what the wind sucks out of the mouth moves it. */
    public static final float OPEN_AIR_DEPTH_SCALE = 3f;

    private static final boolean[] PARTICULATE = new boolean[Gas.COUNT];
    private static final boolean[] MOBILE = new boolean[Gas.COUNT];
    /** Below this a gas with a saturation of its own is not worth a cell, and the sum over every gas is not what decides. */
    private static final float[] OWN_REMOVE_FLOOR = new float[Gas.COUNT];

    static
    {
        for (Gas gas : Gas.VALUES)
        {
            PARTICULATE[gas.ordinal()] = gas.isParticulate();
            MOBILE[gas.ordinal()] = gas.mobile();
            OWN_REMOVE_FLOOR[gas.ordinal()] = gas.isSaturating() ? gas.removeCellMoles() : Float.MAX_VALUE;
        }
    }

    /**
     * @param depth metres below the top of the open column the position is in, that is below the surface around it
     * @return the fraction of the gas at the position that the atmosphere carries off each second: 0.3 at the surface, and a third of that three metres down. A well or a bell pit holds a heavy gas for the same reason a still cave does, and a shaft is not a chimney.
     */
    public static float openAirExchange(float depth)
    {
        return OPEN_AIR_EXCHANGE_RATE * (float) Math.exp(-Math.max(depth, 0f) / OPEN_AIR_DEPTH_SCALE);
    }

    /** @return moles of gas in a cell. Dust is not gas: it takes no room from the air, and does not push on a wall. */
    public static float total(float[] cell)
    {
        float total = 0;
        for (int i = 0; i < cell.length; i++)
        {
            if (!PARTICULATE[i])
            {
                total += cell[i];
            }
        }
        return total;
    }

    /** @return the amounts of everything in a cell that can move, in moles and kilograms of airborne dust together. Only used to decide whether a cell is worth keeping. */
    public static float mobileContent(float[] cell)
    {
        float total = 0;
        for (int i = 0; i < cell.length; i++)
        {
            if (MOBILE[i])
            {
                total += cell[i];
            }
        }
        return total;
    }

    /**
     * @return {@code true} if a cell holds enough that can move to be kept: the sum over every species reaches the ordinary floor, or one vapour with a saturation of its own reaches the floor that is scaled to that
     */
    public static boolean holdsMobileContent(float[] cell)
    {
        float total = 0;
        for (int i = 0; i < cell.length; i++)
        {
            if (MOBILE[i])
            {
                if (cell[i] >= OWN_REMOVE_FLOOR[i])
                {
                    return true;
                }
                total += cell[i];
            }
        }
        return total >= GasSimulator.REMOVE_CELL_MOLES;
    }

    /** @return the kilograms of settled dust in a cell */
    public static float settledContent(float[] cell)
    {
        float total = 0;
        for (int i = 0; i < cell.length; i++)
        {
            if (!MOBILE[i])
            {
                total += cell[i];
            }
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

    /** @return how much denser a cell is than plain air, as a fraction of the density of air. Whatever gas it holds has displaced that much air. A cell of {@code null} is plain air. */
    public static float relativeDensity(@Nullable float[] cell)
    {
        if (cell == null)
        {
            return 0;
        }
        float excess = 0;
        for (int i = 0; i < cell.length; i++)
        {
            // Dust, lying or flying, makes no current: a particle falls through the air by itself, and the cloud does not sink as a body
            if (MOBILE[i] && !PARTICULATE[i])
            {
                excess += cell[i] * (Gas.VALUES[i].molarMass() - AIR_MOLAR_MASS);
            }
        }
        return excess / (CELL_MOLES * AIR_MOLAR_MASS);
    }

    /** @return pressure in atm: a cell only goes over 1 atm once the added gas has displaced all of its air */
    public static float pressure(float[] cell)
    {
        return Math.max(1, fraction(total(cell)));
    }

    private GasMath() {}
}
