package tfcpp.pneumatics.gas;

/**
 * Combustion of carbon in air. C + O2 -> CO2 releases 393.5 kJ/mol; C + 1/2 O2 -> CO releases 110.5 kJ/mol.
 * <p>
 * Air carries 3.77 mol of nitrogen per mole of oxygen. Burning leaves that nitrogen behind as flue gas, so adding the products to
 * a cell displaces exactly the air whose oxygen was consumed, and {@link GasMath#oxygenFraction} falls by the right amount.
 */
public final class Combustion
{
    public static final float CO2_HEAT_KJ_PER_MOL = 393.5f;
    public static final float CO_HEAT_KJ_PER_MOL = 110.5f;
    public static final float NITROGEN_PER_OXYGEN = (1 - GasMath.OXYGEN_IN_AIR) / GasMath.OXYGEN_IN_AIR;

    /** Below this oxygen fraction carbon stops burning cleanly. */
    public static final float CLEAN_BURN_OXYGEN = 0.19f;
    /** Below this oxygen fraction embers can no longer sustain a fire. */
    public static final float EXTINCTION_OXYGEN = 0.12f;
    /**
     * The share of the carbon of a blast furnace that leaves as CO. The charge is reduced by CO, and the top gas of a real furnace is 20 to 28% CO by volume: with this share the flue gas, its nitrogen included, comes to about 22%.
     */
    public static final float BLAST_FURNACE_CARBON_MONOXIDE_SHARE = 0.75f;

    /** @return {@code true} if air with this oxygen fraction can still keep a fire burning, and so put anything into it */
    public static boolean sustainsFlame(float oxygenFraction)
    {
        return oxygenFraction >= EXTINCTION_OXYGEN;
    }

    /**
     * @param kilowatts thermal power of the fire
     * @return moles of carbon burned per second, taking the partial burn to CO into account
     */
    public static float carbonRate(float kilowatts, float carbonMonoxideShare)
    {
        return kilowatts / (CO2_HEAT_KJ_PER_MOL * (1 - carbonMonoxideShare) + CO_HEAT_KJ_PER_MOL * carbonMonoxideShare);
    }

    /** @return the share of carbon leaving as CO: starved fires turn most of their carbon into CO */
    public static float carbonMonoxideShare(float cleanShare, float oxygenFraction)
    {
        final float starvation = Math.clamp((CLEAN_BURN_OXYGEN - oxygenFraction) / (CLEAN_BURN_OXYGEN - EXTINCTION_OXYGEN), 0f, 1f);
        return cleanShare + (0.8f - Math.min(cleanShare, 0.8f)) * starvation;
    }

    /** Adds the flue gas of burning {@code carbonMoles} of carbon to a cell. */
    public static void addFlueGas(float[] cell, float carbonMoles, float carbonMonoxideShare)
    {
        final float carbonMonoxide = carbonMoles * carbonMonoxideShare;
        final float carbonDioxide = carbonMoles - carbonMonoxide;
        final float oxygen = carbonDioxide + 0.5f * carbonMonoxide;

        cell[Gas.CARBON_MONOXIDE.ordinal()] += carbonMonoxide;
        cell[Gas.CARBON_DIOXIDE.ordinal()] += carbonDioxide;
        cell[Gas.FLUE_NITROGEN.ordinal()] += oxygen * NITROGEN_PER_OXYGEN;
    }

    private Combustion() {}
}
