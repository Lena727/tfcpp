package tfcpp.pneumatics.gas;

/**
 * What breathing a gas does to a player, based on occupational exposure data. Thresholds are in parts per million of air. Severity 2 starts at the NIOSH IDLH level, which is survivable for long enough to escape, and severity 3 at the level that drops a person within a few breaths.
 */
public final class Physiology
{
    /** Haldane's constant: haemoglobin binds CO about 246 times more strongly than O2. */
    private static final float HALDANE = 246f;
    /** Empirical uptake at light work: %COHb per second per ppm. Gives about 100 s to 50% at 12800 ppm, matching the 1-3 minute fatality data. */
    private static final float UPTAKE = 0.16f / 3600f;
    /**
     * Half-time of washout in clean air, in real seconds. Real blood takes about 5 hours, which here means five hours of TFC's calendar (300 s).
     * Uptake stays in real seconds, as that is the time a player has to react in.
     */
    private static final float WASHOUT_HALF_TIME = 300f;

    public static final float COHB_SYMPTOMS = 20f;
    public static final float COHB_DISABLED = 35f;
    public static final float COHB_FATAL = 50f;

    public static float equilibriumCarboxyhemoglobin(float carbonMonoxidePpm, float oxygenFraction)
    {
        final float ratio = HALDANE * carbonMonoxidePpm * 1e-6f / Math.max(oxygenFraction, 0.01f);
        return 100f * ratio / (1 + ratio);
    }

    /** @return the blood's carboxyhemoglobin percentage after {@code seconds} of breathing this air */
    public static float stepCarboxyhemoglobin(float current, float carbonMonoxidePpm, float oxygenFraction, float seconds)
    {
        final float equilibrium = equilibriumCarboxyhemoglobin(carbonMonoxidePpm, oxygenFraction);
        if (current < equilibrium)
        {
            final float next = current + UPTAKE * carbonMonoxidePpm * (1 - current / equilibrium) * seconds;
            return Math.min(next, equilibrium);
        }
        final float next = current * (float) Math.pow(0.5, seconds / WASHOUT_HALF_TIME);
        return Math.max(next, equilibrium);
    }

    /**
     * Severity of breathing a gas that attacks the airways or body directly, from 0 (none) to 3 (rapidly fatal).
     * CO, mercury and arsenic work slowly through a dose carried by the body, and methane, hydrogen and nitrogen are only simple asphyxiants.
     */
    public static int acuteSeverity(Gas gas, float ppm)
    {
        return switch (gas)
        {
            case CARBON_DIOXIDE -> severity(ppm, 40_000, 100_000, 170_000); // IDLH 4%, unconscious in minutes at 10%, within a few breaths from 17%
            case SULFUR_DIOXIDE -> severity(ppm, 20, 100, 1000); // IDLH 100 ppm
            case HYDROGEN_SULFIDE -> severity(ppm, 50, 100, 700); // IDLH 100 ppm, collapse in one breath near 700 ppm
            case CHLORINE -> severity(ppm, 3, 10, 1000); // IDLH 10 ppm, fatal within minutes at 400 ppm, and in a few breaths at 1000 ppm
            case AMMONIA -> severity(ppm, 100, 300, 5000); // IDLH 300 ppm
            default -> 0;
        };
    }

    /** Severity of oxygen starvation, from 0 (none) to 3 (rapidly fatal). Normal air holds 20.95%. */
    public static int hypoxiaSeverity(float oxygenFraction)
    {
        if (oxygenFraction < 0.06f) return 3; // Collapse in a few breaths
        if (oxygenFraction < 0.10f) return 2; // Fatal within minutes
        if (oxygenFraction < 0.16f) return 1;
        return 0;
    }

    /** @return {@code true} if a person would notice this gas by smell or irritation. CO, methane, hydrogen and nitrogen have no odour at all. */
    public static boolean isNoticeable(Gas gas, float ppm)
    {
        return switch (gas)
        {
            case SULFUR_DIOXIDE -> ppm >= 1;
            case HYDROGEN_SULFIDE -> ppm >= 0.5f;
            case AMMONIA -> ppm >= 5;
            case CHLORINE -> ppm >= 0.5f;
            default -> false;
        };
    }

    private static int severity(float ppm, float mild, float serious, float fatal)
    {
        if (ppm >= fatal) return 3;
        if (ppm >= serious) return 2;
        if (ppm >= mild) return 1;
        return 0;
    }

    private Physiology() {}
}
