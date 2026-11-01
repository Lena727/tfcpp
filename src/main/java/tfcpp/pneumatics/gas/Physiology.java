package tfcpp.pneumatics.gas;

import tfcpp.core.LocalTime;

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
     * Half-time of washout in clean air, in days of slow time: real blood takes about 5 hours.
     * Uptake stays in real seconds, as that is the time a player has to react in.
     */
    private static final float WASHOUT_HALF_TIME_DAYS = 5f / 24f;

    /** Air breathed at light work, 20 litres per minute, in cubic metres per second. */
    private static final float BREATHING_RATE = 0.00033f;
    private static final float RETAINED_FRACTION = 0.8f;

    /** A poison the body clears slowly. Half-lives are in days of slow time, as a body is one place. */
    public enum BodyBurden
    {
        ARSENIC(4f, 20f, 70f, 150f), // Half-life about 4 days, fatal oral dose about 70-180 mg
        MERCURY(60f, 5f, 20f, 50f), // Half-life about 60 days, chronic tremor builds slowly
        ZINC_OXIDE(0.25f, 15f, 60f, Float.MAX_VALUE), // Metal fume fever: awful, passes within hours, not fatal
        LEAD(30f, 30f, 100f, 300f); // Half-life in blood about 30 days. Colic and weakness, then palsy of the wrists, and the bones keep it for years.

        private final float halfLifeDays, symptoms, disabled, fatal;

        BodyBurden(float halfLifeDays, float symptoms, float disabled, float fatal)
        {
            this.halfLifeDays = halfLifeDays;
            this.symptoms = symptoms;
            this.disabled = disabled;
            this.fatal = fatal;
        }

        /** @return the burden after breathing air with this many moles of the poison per cubic metre */
        public float step(float burden, float molesPerCubicMetre, float molarMass, float seconds)
        {
            final float inhaled = molesPerCubicMetre * molarMass * 1000f * BREATHING_RATE * RETAINED_FRACTION * seconds;
            return burden * (float) Math.pow(0.5, seconds / (halfLifeDays * LocalTime.SECONDS_PER_DAY)) + inhaled;
        }

        /** @return the burden, limited to below the fatal level */
        public float survivable(float burden)
        {
            return Math.min(burden, disabled);
        }

        /** @return from 0 (none) to 3 (fatal) */
        public int severity(float burden)
        {
            return Physiology.severity(burden, symptoms, disabled, fatal);
        }
    }

    /**
     * Mercury is 13.5 g/mL, and the gut takes in about 0.01% of liquid mercury. That is why a swallowed drop from a thermometer usually passes harmlessly, and also why a mouthful, at more than a kilogram of metal, does not.
     *
     * @return the dose in mg that the body takes up from swallowing this many mL of mercury
     */
    public static float swallowedMercury(float millilitres)
    {
        return millilitres * 13.5f * 1000f * 1e-4f;
    }

    /**
     * What has been swallowed is not in the blood yet. It is taken up on its way through the gut, over about a day of slow time, so a fatal mouthful feels like nothing at first and then gets steadily worse.
     *
     * @return how much of the dose waiting in the gut reaches the body in this time
     */
    public static float takenUpFromGut(float inGut, float seconds)
    {
        return inGut * (1f - (float) Math.exp(-seconds / LocalTime.SECONDS_PER_DAY));
    }

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
        final float next = current * (float) Math.pow(0.5, seconds / (WASHOUT_HALF_TIME_DAYS * LocalTime.SECONDS_PER_DAY));
        return Math.max(next, equilibrium);
    }

    /**
     * Severity of breathing a gas that attacks the airways or body directly, from 0 (none) to 3 (rapidly fatal).
     * CO, mercury and arsenic work through a dose carried by the body, see {@link BodyBurden}, and methane, hydrogen and nitrogen are only simple asphyxiants.
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
