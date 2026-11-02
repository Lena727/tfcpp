package tfcpp.metallurgy;

/**
 * The work lead of cupellation, free of Minecraft types. Galena gives lead that holds a little silver. In a hearth at about 1000 C an air blast oxidises the lead to litharge (PbO). Litharge is liquid at that heat, and the bone-ash floor soaks it up, so it is raked off. Silver does not oxidise at that heat, so it stays in the bath, and what is left at the end is the silver.
 * <p>
 * The blast gives only as much oxygen as the lead can take, and the ash can hold only so much litharge before it must be replaced. When the ash is full the blow stops, and the bath must be given more ash before it goes on.
 */
public final class CupellationBath
{
    /**
     * How fast the blast oxidises the lead. A game pace, not measured: at this rate a full charge of 1000 mB takes about 17 minutes.
     */
    public static final float OXIDATION_MILLIBUCKETS_PER_SECOND = 1f;

    /**
     * Litharge melts at 888 C and must stay liquid to run off the floor, so the hearth is held above that with a working margin. Below it the blast does nothing.
     */
    public static final float LEAST_TEMPERATURE = 960f;

    /**
     * Share of the oxidised lead that leaves as fume rather than as litharge. A hearth lost a few percent of its lead as fume, as in the galena entry of the ledger.
     */
    public static final float FUME_SHARE = 0.03f;

    /**
     * Millibuckets of silver per millibucket of work lead: 0.3% of the lead's mass, with densities of 11.34 g/cm3 for lead and 10.49 g/cm3 for silver, so 0.003 x 11.34 / 10.49 = 0.00324 mL of silver for each mL of lead.
     */
    public static final float SILVER_PER_LEAD = 0.00324f;

    /**
     * Moles of lead in one millibucket: the density of lead, 11.34 g/cm3, over its molar mass, 207.2 g/mol. One millibucket of metal is one millilitre.
     */
    public static final float MOLES_LEAD_PER_MILLIBUCKET = 11.34f / 207.2f;

    /**
     * Lead in one powder of 0.25 mol of PbO: 0.25 x 207.2 g of lead, over 11.34 g/cm3, is 4.57 mL.
     */
    public static final float LITHARGE_MILLIBUCKETS_PER_POWDER = 4.57f;

    /**
     * Lead that one powder of bone ash can serve before it is full. A game figure, not measured: a powder of ash soaks up this much litharge before it has to be replaced.
     */
    public static final float ASH_MILLIBUCKETS_PER_POWDER = 500f;

    /**
     * The most work lead one hearth takes at once. A game size, not a measurement.
     */
    public static final float CAPACITY_MILLIBUCKETS = 1000f;

    /**
     * Moles of O2 per mole of Pb: 2 Pb + O2 -> 2 PbO, so half a mole of oxygen for each mole of lead.
     */
    public static final float OXYGEN_PER_LEAD = 0.5f;

    /** The work lead still in the hearth, in millibuckets. */
    public float lead;
    /** The silver left behind by the lead, in millibuckets. */
    public float silver;
    /** Millibuckets of lead the bone-ash floor can still take up. Zero or less means it is full. */
    public float ashCapacity;
    /** Litharge in millibuckets of lead, which has been made but not yet counted into whole powders. */
    public float oxidisedForLitharge;

    /** What one stretch of blast gave. */
    public record Step(float leadOxidised, float fumeMoles, float oxygenMoles, int litharge)
    {
        public static final Step NONE = new Step(0f, 0f, 0f, 0);
    }

    /**
     * Takes as much work lead as fits below the capacity. Only whole millibuckets are poured.
     *
     * @return how much was taken, in millibuckets
     */
    public int add(int millibuckets)
    {
        if (millibuckets <= 0)
        {
            return 0;
        }
        final int room = (int) Math.max(CAPACITY_MILLIBUCKETS - lead, 0f);
        final int taken = Math.min(millibuckets, room);
        lead += taken;
        silver += taken * SILVER_PER_LEAD;
        return taken;
    }

    /** Adds one powder of bone ash to the floor. */
    public void addAsh()
    {
        ashCapacity += ASH_MILLIBUCKETS_PER_POWDER;
    }

    /** @return {@code true} when there is lead to oxidise but the floor is full, so the blow stops until more ash goes in */
    public boolean needsAsh()
    {
        return lead > 0f && ashCapacity <= 0f;
    }

    /** @return {@code true} when the lead is gone and silver is left in the hearth */
    public boolean isFinished()
    {
        return lead <= 0f && silver > 0f;
    }

    /**
     * Blows air through the hearth for a stretch of time. The lead oxidises only while the bath is hot enough to keep litharge liquid, and only as far as the ash can take it.
     *
     * @param seconds time of the blast
     * @param temperature in C
     * @return what that gave
     */
    public Step step(float seconds, float temperature)
    {
        if (temperature < LEAST_TEMPERATURE || seconds <= 0f || lead <= 0f || ashCapacity <= 0f)
        {
            return Step.NONE;
        }

        final float oxidised = Math.min(OXIDATION_MILLIBUCKETS_PER_SECOND * seconds, Math.min(lead, ashCapacity));
        lead -= oxidised;
        ashCapacity -= oxidised;
        if (lead < 0.001f)
        {
            // Less than a thousandth of a millibucket is rounding left over from the subtraction
            lead = 0f;
        }

        final float fumeMoles = oxidised * FUME_SHARE * MOLES_LEAD_PER_MILLIBUCKET;
        final float oxygenMoles = oxidised * MOLES_LEAD_PER_MILLIBUCKET * OXYGEN_PER_LEAD;

        oxidisedForLitharge += oxidised * (1f - FUME_SHARE);
        final int litharge = (int) (oxidisedForLitharge / LITHARGE_MILLIBUCKETS_PER_POWDER);
        oxidisedForLitharge -= litharge * LITHARGE_MILLIBUCKETS_PER_POWDER;

        return new Step(oxidised, fumeMoles, oxygenMoles, litharge);
    }

    /**
     * Takes the silver out in whole millibuckets, and leaves the fraction behind in the hearth.
     *
     * @return how many whole millibuckets of silver were taken
     */
    public int tapSilver()
    {
        final int tapped = (int) silver;
        silver -= tapped;
        return tapped;
    }
}
