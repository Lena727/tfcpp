package tfcpp.metallurgy;

/**
 * What a condensation tower takes out of the gas that passes through it. No Minecraft in here, so that the rates can be tested.
 * <p>
 * Oxide fume of arsenic, antimony, zinc and lead turns to solid dust on any cool surface, and a tower is mostly surface: this is why the arsenic works of old ran their flues through long chambers of brick. Mercury boils at 357 C and so condenses to a liquid only when the gas is cooled well below that. Sulfur dioxide and the rest of the flue gas stay gases at any temperature the pipes see, and go on through.
 */
public final class TowerCondensation
{
    /** Rate constant for the dust and the mercury, per second. A tower with a metre of wall in the gas catches 5% of it each second, and the share caught over a time is {@code 1 - exp(-rate t)}, so the step between two calls does not change the result. */
    public static final float CAPTURE_RATE = 0.05f;
    /** Above this the oxide fume does not settle but is carried on with the gas: 250 C. */
    public static final float DUST_CEILING_KELVIN = 523f;
    /** Above this mercury stays a vapour: 100 C. */
    public static final float MERCURY_CEILING_KELVIN = 373f;
    /** The mercury a tower holds before the rest stays in the gas. */
    public static final int MERCURY_TANK_MILLIBUCKETS = 100;

    /** @return the share of what is in the gas that one tower catches in this time, when it is cold enough to catch it */
    public static float fraction(float seconds)
    {
        return seconds <= 0f ? 0f : 1f - (float) Math.exp(-CAPTURE_RATE * seconds);
    }

    /** @return {@code true} if gas at this temperature drops its oxide dust on the walls */
    public static boolean catchesDust(float kelvin)
    {
        return kelvin < DUST_CEILING_KELVIN;
    }

    /** @return {@code true} if gas at this temperature drops its mercury as a liquid */
    public static boolean catchesMercury(float kelvin)
    {
        return kelvin < MERCURY_CEILING_KELVIN;
    }

    /** @return moles of dust that one tower takes out of this many moles of fume in this time, once it is cold enough */
    public static float dust(float fumeMoles, float seconds)
    {
        return fumeMoles <= 0f ? 0f : fumeMoles * fraction(seconds);
    }

    /**
     * @param vapourMoles mercury vapour in the gas
     * @param pendingMillibuckets what the tower already took out of the gas and has not yet counted as a whole millibucket
     * @param roomMillibuckets free space in the tank, in whole millibuckets
     * @param molesPerMillibucket moles of mercury in one millibucket
     * @return moles of mercury that one tower takes out of the gas in this time: 5% of it, and no more than what still fits in the tank. A full tank takes none, and the vapour stays in the gas.
     */
    public static float mercury(float vapourMoles, float seconds, float pendingMillibuckets, int roomMillibuckets, float molesPerMillibucket)
    {
        if (vapourMoles <= 0f)
        {
            return 0f;
        }
        final float fits = Math.max(0f, roomMillibuckets - pendingMillibuckets) * molesPerMillibucket;
        return Math.min(vapourMoles * fraction(seconds), fits);
    }

    private TowerCondensation() {}
}
