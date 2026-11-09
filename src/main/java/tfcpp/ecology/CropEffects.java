package tfcpp.ecology;

/**
 * What soil and air do to a growing crop, as multipliers on TFC's growth for one step. Free of Minecraft types.
 * <p>
 * Every number has its source. Where the figure is a game calibration it says so.
 */
public final class CropEffects
{
    /** Soil pH at and above which acidity costs a crop nothing, and the pH where growth is down to {@link #ACID_FLOOR}. Most field crops tolerate pH 5.5 to 7.5 (Brady and Weil, The Nature and Properties of Soils: liming target pH 6-6.5, yield loss of acid-sensitive crops starts under 5.5). */
    public static final double PH_FULL = 5.5, PH_LOW = 4.5;
    public static final double ACID_FLOOR = 0.2;
    /** Below this pH exchangeable aluminium is toxic to roots and a crop does not survive it (Kochian 2004, Annu. Rev. Plant Biol. 55: Al toxicity limits crops in soils under pH 5, strongly under 4.5). */
    public static final double PH_ALUMINIUM_KILL = 4.5;

    /** The multiple of the threshold at which growth is zero. Phytotoxic levels in soil, mg/kg: Cu 100, Zn 300, Ni 100, As 40 (Kabata-Pendias, Trace Elements in Soils and Plants, 4th ed.; Kloke 1980; Alloway 2013, Heavy Metals in Soils). Lead and mercury are held in soil as insoluble forms and are rarely phytotoxic at the levels a game makes: no effect. */
    public static final double ZERO_GROWTH_MULTIPLE = 5.0;

    /** Annual mean SO2 in micrograms per cubic metre: UNECE critical level for crops 30 (UNECE Mapping Manual 2017, ch. 3; WHO Air Quality Guidelines for Europe 2000, ch. 9). Growth is 0.5 at 150: a GAME CALIBRATION through the experimental yield losses of 10-30 % at 50-100 ug/m3 in open-top chamber studies (Heck et al. 1988). */
    public static final double SO2_CRITICAL = 30.0, SO2_HALF = 150.0;
    /** Hourly mean over which leaves are injured at once: 1000 ug/m3 (acute foliar injury at about 1 ppm for an hour in sensitive crops, WHO 2000, ch. 9). */
    public static final double SO2_ACUTE = 1000.0;

    /** Litres of irrigation water per square metre in a year: a typical irrigation depth of 500 mm (FAO Irrigation and Drainage Paper 56: seasonal crop water needs of 300-1000 mm where rain does not supply it). */
    public static final double IRRIGATION_LITRES_PER_M2_YEAR = 500.0;

    /** @return the phytotoxic threshold of a metal in mg/kg of dry soil, infinity for one with none */
    public static double threshold(Pollutant metal)
    {
        return switch (metal)
        {
            case COPPER -> 100.0;
            case ZINC -> 300.0;
            case NICKEL -> 100.0;
            case ARSENIC -> 40.0;
            default -> Double.POSITIVE_INFINITY;
        };
    }

    private CropEffects() {}

    /** @return 1 at pH 5.5 and above, falling in a line to 0.2 at 4.5; below 4.5 it stays at 0.2 (the crop is then killed, see {@link #aluminiumKills}) */
    public static double phMultiplier(double ph)
    {
        if (ph >= PH_FULL)
        {
            return 1.0;
        }
        if (ph <= PH_LOW)
        {
            return ACID_FLOOR;
        }
        return ACID_FLOOR + (1.0 - ACID_FLOOR) * (ph - PH_LOW) / (PH_FULL - PH_LOW);
    }

    /** @return the acidity multiplier relative to the soil's own start: f(pH)/f(start). A soil that is naturally acid gives its natural crop growth, and liming it above its start raises the multiplier over 1 (a real decision for a podzol). */
    public static double phMultiplier(double ph, double startPh)
    {
        return phMultiplier(ph) / phMultiplier(startPh);
    }

    /** @return whether aluminium kills the crop: the pH has fallen below 4.5 AND below the soil's start (a natural acid soil does not kill at baseline) */
    public static boolean aluminiumKills(double ph, double startPh)
    {
        return ph < PH_ALUMINIUM_KILL && ph < startPh - 1e-9;
    }

    /** @return the growth multiplier for one metal at this concentration in mg/kg: 1 up to the threshold, 0 at five times it */
    public static double metalMultiplier(Pollutant metal, double mgPerKg)
    {
        final double t = threshold(metal);
        if (!(mgPerKg > t))
        {
            return 1.0;
        }
        return Math.max(0.0, 1.0 - (mgPerKg - t) / (t * (ZERO_GROWTH_MULTIPLE - 1.0)));
    }

    /** @return the multiplier for all the metals together: the product of each */
    public static double metalsMultiplier(double[] mgPerKgByPollutant)
    {
        double product = 1.0;
        for (Pollutant metal : Pollutant.VALUES)
        {
            product *= metalMultiplier(metal, mgPerKgByPollutant[metal.ordinal()]);
        }
        return product;
    }

    /** @return 1 up to 30 ug/m3 of annual mean, falling in a line to 0.5 at 150 and on to zero at 270 */
    public static double so2Multiplier(double annualMicrograms)
    {
        if (!(annualMicrograms > SO2_CRITICAL))
        {
            return 1.0;
        }
        return Math.max(0.0, 1.0 - 0.5 * (annualMicrograms - SO2_CRITICAL) / (SO2_HALF - SO2_CRITICAL));
    }

    /** @return whether an hourly concentration injures the leaves at once */
    public static boolean so2Acute(double hourlyMicrograms)
    {
        return hourlyMicrograms > SO2_ACUTE;
    }

    /** @return milligrams per kilogram of dry soil for grams in the whole cell of 256 m2 over 650 kg/m2: 1 g/m2 is 1.54 mg/kg */
    public static double mgPerKg(double gramsInCell)
    {
        return gramsInCell / SoilConstants.AREA / SoilConstants.ROOT_ZONE_MASS * 1000.0;
    }

    /**
     * @param ph pH of the root zone
     * @param startPh the pH the soil started at
     * @param metals mg/kg for each pollutant (other slots ignored)
     * @param annualSo2 annual mean in ug/m3
     * @param hourlySo2 present concentration in ug/m3
     * @return the growth multiplier of a step and whether the step is unhealthy
     */
    public static Result combine(double ph, double startPh, double[] metals, double annualSo2, double hourlySo2)
    {
        final double growth = phMultiplier(ph, startPh) * metalsMultiplier(metals) * so2Multiplier(annualSo2);
        return new Result(growth, aluminiumKills(ph, startPh) || so2Acute(hourlySo2));
    }

    public record Result(double growth, boolean unhealthy)
    {
        public static final Result NEUTRAL = new Result(1.0, false);
    }
}
