package tfcpp.ecology;

import org.jetbrains.annotations.Nullable;

import tfcpp.chemistry.Liquid;

/**
 * The liquids that leave a record when they soak into the ground, and what each of them turns into in the soil. Free of Minecraft types. A liquid that is not here soaks away without a record.
 * <p>
 * The amounts are in millibuckets, and a millibucket is not the same volume for all of them. The scale of the chemical liquids is the one of the physics ledger, where 1 mB is 1 L. For mercury and the molten metals it is TFC's own, where 1 mB is 1 mL and an ingot of 100 mB is a real ingot of 100 mL.
 */
public enum Soakable
{
    COAL_TAR(Liquid.COAL_TAR),
    WOOD_TAR(Liquid.WOOD_TAR),
    PYROLIGNEOUS_LIQUOR(Liquid.PYROLIGNEOUS_LIQUOR),
    AMMONIA_LIQUOR(Liquid.AMMONIA_LIQUOR),
    CHAMBER_ACID(Liquid.CHAMBER_ACID),
    SULFURIC_ACID(Liquid.SULFURIC_ACID),
    NITRIC_ACID(Liquid.NITRIC_ACID),
    HYDROCHLORIC_ACID(Liquid.HYDROCHLORIC_ACID),
    MERCURY((Liquid) null),
    LEAD((Liquid) null),
    ZINC((Liquid) null),
    ARSENICAL_COPPER((Liquid) null),
    /** Pure copper and the alloys of TFC that hold it, by the midpoints of the ranges of TFC's own alloy recipes (volume fractions of the molten metals, made up to 1). Only the metals that are pollutants here are recorded: tin, silver, gold and bismuth are not. */
    COPPER(mix(1.0, 0, 0, 0, 0, 0, 0)),
    BRONZE(mix(0.90, 0, 0, 0.10, 0, 0, 0)),
    BISMUTH_BRONZE(mix(0.575, 0.25, 0, 0, 0.15, 0, 0)),
    BLACK_BRONZE(mix(0.60, 0, 0, 0, 0, 0.175, 0.175)),
    BRASS(mix(0.90, 0.10, 0, 0, 0, 0, 0)),
    ROSE_GOLD(mix(0.225, 0, 0, 0, 0, 0, 0.775)),
    STERLING_SILVER(mix(0.30, 0, 0, 0, 0, 0.70, 0)),
    NICKEL(mix(0, 0, 1.0, 0, 0, 0, 0));

    /**
     * Grams per millilitre of the metal as it runs out of a pipe, which is molten: lead from our fluid type (10 660 kg/m3), mercury from ours (13 530), zinc as the liquid metal (6.57, CRC Handbook), and arsenical copper from ours (8 000).
     * <p>
     * Molten metal that runs into the ground sets into a buried lump, which corrodes over years, as lead does at a shooting range. Stage A counts all of it as in the soil. Stage D will say how much of it is available to plants and water.
     */
    public static final double MERCURY_DENSITY = 13.53, LEAD_DENSITY = 10.66, ZINC_DENSITY = 6.57;
    /** Arsenical copper: grams per millilitre of the alloy as the fluid type has it (8.0), and the share of it that is arsenic. The arsenical bronzes of the early Bronze Age held 1 to 7% arsenic, and most of the tools some 2 to 4% (Lechtman 1996, J. Field Archaeol. 23), so 3%. The rest of the alloy is recorded as copper. */
    public static final double ARSENICAL_COPPER_DENSITY = 8.0, ARSENICAL_COPPER_ARSENIC_FRACTION = 0.03;

    /** Mass fractions of the acids in our own definitions: see the javadoc of each constant of {@link Liquid}. */
    public static final double CHAMBER_ACID_FRACTION = 0.65, SULFURIC_ACID_FRACTION = 0.93, NITRIC_ACID_FRACTION = 0.90, HYDROCHLORIC_ACID_FRACTION = 0.32;
    /** Grams per mole. */
    public static final double H2SO4_MOLAR_MASS = 98.079, HNO3_MOLAR_MASS = 63.012, HCL_MOLAR_MASS = 36.461;

    /** Grams of phenols in a litre of the gas liquor of a coke oven. Its other ingredients are water and ammonia, and the ammonia is counted below. Gas liquor holds 1 to 3 g/L of phenols (Ullmann's, Coal Tar and Pitch). */
    public static final double LIQUOR_PHENOLS_GRAMS_PER_LITRE = 2.0;
    /**
     * Moles of ammonia in a litre of that liquor, from our own coking data: the liquor of lignite holds about 0.76 mol/L (see {@code CarbonizationTable}), and coal's is of the same strength. Soil bacteria oxidise ammonium to nitrate, NH4+ + 2 O2 giving NO3- + H2O + 2 H+, and when the nitrate is leached away or taken up by roots the net gain is one proton for each ammonium (Ulrich 1983, and the nitrogen budgets of critical loads work), so a mole of ammonia is an equivalent of acid.
     */
    public static final double LIQUOR_AMMONIA_MOLES_PER_LITRE = 0.76;
    /** Grams of soluble tar and phenolics in a litre of crude pyroligneous liquor: some 1 to 2% of it by mass (Mathew and Zakaria 2015, Appl. Microbiol. Biotechnol. 99), taken as 15 g/L. Its acetic acid is not recorded: soil bacteria eat it in days, and it leaves no net acidity. */
    public static final double PYROLIGNEOUS_TAR_GRAMS_PER_LITRE = 15.0;
    // Wood spirit (methanol and acetone) has no entry: both are eaten by soil bacteria within days and leave nothing behind that this model counts.

    /** Grams per millilitre of molten metal, CRC Handbook near the melting points. Kept in a class of their own so that the constants of the enum can use them. */
    private static final class Density
    {
        static final double COPPER = 8.02, ZINC = 6.57, NICKEL = 7.81, TIN = 6.99, BISMUTH = 10.05, SILVER = 9.32, GOLD = 17.31;
    }

    private final @Nullable Liquid liquid;
    /** For a metal of TFC: grams of copper, zinc and nickel in a millilitre of it. */
    private final double copperPerMl, zincPerMl, nickelPerMl;

    Soakable(@Nullable Liquid liquid)
    {
        this.liquid = liquid;
        this.copperPerMl = 0.0;
        this.zincPerMl = 0.0;
        this.nickelPerMl = 0.0;
    }

    Soakable(double[] grams)
    {
        this.liquid = null;
        this.copperPerMl = grams[0];
        this.zincPerMl = grams[1];
        this.nickelPerMl = grams[2];
    }

    /** @return grams of copper, zinc and nickel in a millilitre of an alloy of these volume fractions (which are made up to 1) */
    private static double[] mix(double copper, double zinc, double nickel, double tin, double bismuth, double silver, double gold)
    {
        final double total = copper + zinc + nickel + tin + bismuth + silver + gold;
        return new double[] {copper / total * Density.COPPER, zinc / total * Density.ZINC, nickel / total * Density.NICKEL};
    }

    /** @return {@code true} for mercury and the molten metals: a liquid that, let into water, freezes to shot or sinks and goes to the bed and not into the water column */
    public boolean sinks()
    {
        return liquid == null;
    }

    /** @return the record that a chemical liquid leaves in the soil, or {@code null} if it leaves none */
    @Nullable
    public static Soakable of(Liquid liquid)
    {
        for (Soakable soakable : values())
        {
            if (soakable.liquid == liquid)
            {
                return soakable;
            }
        }
        return null;
    }

    /** @return the equivalents of acid in a litre of an acid with this strength, which is its mass of acid divided by the molar mass, for each mole that gives up this many protons */
    public static double equivalentsPerLitre(Liquid acid, double massFraction, double molarMass, int protons)
    {
        return acid.density() * massFraction / molarMass * protons;
    }

    /**
     * @param millibuckets how much soaked in
     * @return the amount of each {@link Pollutant} that it adds, indexed by ordinal
     */
    public double[] load(double millibuckets)
    {
        final double[] result = new double[Pollutant.COUNT];
        switch (this)
        {
            case COAL_TAR -> result[Pollutant.TAR.ordinal()] = millibuckets * Liquid.COAL_TAR.kilogramsPerLitre();
            case WOOD_TAR -> result[Pollutant.TAR.ordinal()] = millibuckets * Liquid.WOOD_TAR.kilogramsPerLitre();
            case PYROLIGNEOUS_LIQUOR -> result[Pollutant.TAR.ordinal()] = millibuckets * PYROLIGNEOUS_TAR_GRAMS_PER_LITRE / 1000.0;
            case AMMONIA_LIQUOR ->
            {
                result[Pollutant.TAR.ordinal()] = millibuckets * LIQUOR_PHENOLS_GRAMS_PER_LITRE / 1000.0;
                result[Pollutant.ACIDITY.ordinal()] = millibuckets * LIQUOR_AMMONIA_MOLES_PER_LITRE;
            }
            case CHAMBER_ACID -> result[Pollutant.ACIDITY.ordinal()] = millibuckets * equivalentsPerLitre(Liquid.CHAMBER_ACID, CHAMBER_ACID_FRACTION, H2SO4_MOLAR_MASS, 2);
            case SULFURIC_ACID -> result[Pollutant.ACIDITY.ordinal()] = millibuckets * equivalentsPerLitre(Liquid.SULFURIC_ACID, SULFURIC_ACID_FRACTION, H2SO4_MOLAR_MASS, 2);
            case NITRIC_ACID -> result[Pollutant.ACIDITY.ordinal()] = millibuckets * equivalentsPerLitre(Liquid.NITRIC_ACID, NITRIC_ACID_FRACTION, HNO3_MOLAR_MASS, 1);
            case HYDROCHLORIC_ACID -> result[Pollutant.ACIDITY.ordinal()] = millibuckets * equivalentsPerLitre(Liquid.HYDROCHLORIC_ACID, HYDROCHLORIC_ACID_FRACTION, HCL_MOLAR_MASS, 1);
            case MERCURY -> result[Pollutant.MERCURY.ordinal()] = millibuckets * MERCURY_DENSITY;
            case LEAD -> result[Pollutant.LEAD.ordinal()] = millibuckets * LEAD_DENSITY;
            case ZINC -> result[Pollutant.ZINC.ordinal()] = millibuckets * ZINC_DENSITY;
            case ARSENICAL_COPPER ->
            {
                result[Pollutant.ARSENIC.ordinal()] = millibuckets * ARSENICAL_COPPER_DENSITY * ARSENICAL_COPPER_ARSENIC_FRACTION;
                result[Pollutant.COPPER.ordinal()] = millibuckets * ARSENICAL_COPPER_DENSITY * (1.0 - ARSENICAL_COPPER_ARSENIC_FRACTION);
            }
            case COPPER, BRONZE, BISMUTH_BRONZE, BLACK_BRONZE, BRASS, ROSE_GOLD, STERLING_SILVER, NICKEL ->
            {
                result[Pollutant.COPPER.ordinal()] = millibuckets * copperPerMl;
                result[Pollutant.ZINC.ordinal()] = millibuckets * zincPerMl;
                result[Pollutant.NICKEL.ordinal()] = millibuckets * nickelPerMl;
            }
        }
        return result;
    }
}
