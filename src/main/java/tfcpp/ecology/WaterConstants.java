package tfcpp.ecology;

/**
 * Every number of the water memory in one place, each with its source. Free of Minecraft types.
 * <p>
 * A river edge of TFC is a well mixed reach, and the figures here make its flow, its volume and the fates of what is put into it. Where a number is a figure of the game and not a measurement it says so.
 */
public final class WaterConstants
{
    // --- Hydraulics: the visible river (see ReachInfo.Hydraulics for the channel) -----------------------------------------------------------------------------

    /** Metres per second: the velocity of a lowland river is 0.3 to 1 m/s, and the same for every reach (the exponent of velocity in Leopold and Maddock 1953, USGS Professional Paper 252, is 0.1: almost constant). It is the real-world speed: on the map it is compressed by the metres a block stands for, as the wind of stage B is. */
    public static final double VELOCITY = 0.5;

    /** Square metres of the still water of a cell: the whole 128 by 128 block cell. */
    public static final double LAKE_AREA = 128.0 * 128.0;
    /** Metres of depth of a lake. A GAME FIGURE for a shallow lake, which makes the volume of a cell {@code 128 * 128 * 5} = 81 920 m3. Counting the water blocks of the real lake would mean scanning chunks, and the lake is not the point of the model: the figure is nominal. A lake has no outflow here, so what is put into it accumulates. */
    public static final double LAKE_DEPTH = 5.0;
    public static final double LAKE_VOLUME = LAKE_AREA * LAKE_DEPTH;
    /** Days over which the alkalinity of a still water cell is renewed from its catchment: the flushing time of small lakes is months to years (Wetzel, Limnology), and a TFC year is taken (per-year figures follow the TFC calendar, see SoilConstants#daysPerYear). */
    public static double lakeAlkalinityRenewalDays()
    {
        return SoilConstants.daysPerYear();
    }

    // --- Fates in water ----------------------------------------------------------------------------------------------------------------------------------------------------

    /** Days for the pathogens of sewage to fall to a tenth: in sunlit river water the T90 of faecal indicator bacteria is of the order of 1 to 3 days (Crane and Moore 1986, Water Air Soil Pollut. 28, review of die-off in natural waters; Thomann and Mueller 1987), 2 days here. In a lake, deeper and clearer water and less light slow the die-off, and 10 days is a GAME FIGURE inside the ranges of that review. */
    public static final double SEWAGE_T90_RIVER_DAYS = 2.0, SEWAGE_T90_LAKE_DAYS = 10.0;
    /** Days. Phenols in natural water break down biologically in days to a week or two (Howard 1989, Handbook of Environmental Fate and Exposure Data); the heavier tar compounds are slower. 10 days is the figure of the game for the whole of what is counted as tar. */
    public static final double TAR_HALF_LIFE_DAYS = 10.0;

    /** The share of a metal in the water column that is bound to particles at any time. Metals partition between the dissolved and the particulate phase in rivers with Kd of 1e4 to 1e5 L/kg (lead is at the top, zinc and arsenic lower: Allison and Allison 2005, EPA/600/R-05/074), and with a few tens of mg/L of suspended solids that is half to nine tenths for lead and less for the others. One figure of 0.7 for every metal: a GAME SIMPLIFICATION. */
    public static final double PARTICLE_BOUND = 0.7;
    /** Per day: the rate at which the particle-bound metal settles to the bed, 0.1 per day, a particle velocity of 0.1 to 0.2 m/day over 1 to 2 m of water (fine silt, Stokes). Since the bound share is renewed by sorption at once, the rate of loss of the whole is {@code PARTICLE_BOUND * SETTLING_PER_DAY} = 0.07 per day. */
    public static final double SETTLING_PER_DAY = 0.1;
    /** Per day, in still water 5 m deep: fine silt settles at about 0.15 m a day (Stokes for 2-5 um particles, and the 0.1 to 1 m a day of Chapra 1997, Surface Water-Quality Modeling, ch. 22, for the small particles that carry metals), which over 5 m is 0.03 a day. A river is stirred and its flocs are large, so it keeps the faster rate above. */
    public static final double LAKE_SETTLING_PER_DAY = 0.03;
    /** Per day, of the sediment of the reach, during heavy rain: a GAME FIGURE for the scour of floods, which stir up the bed of a river (the sediment of mining rivers is a source for decades, Hudson-Edwards 2003, Mineralogical Magazine 67: floodplain and channel sediments of mine-affected rivers are the long-term source of their metals). */
    public static final double RESUSPENSION_PER_DAY = 0.01;
    /**
     * TFC's rain intensity at a place, rain intensity minus what the local rainfall takes away of it, runs from 0 to 1, and its mean over the hours of rain is about 0.5 (it is the average of two uniform numbers, one for the storm and one for the time from its middle). 0.7 is passed by about 18% of hours of rain, and by a twentieth of all hours in a wet climate: a storm and not a shower.
     */
    public static final double HEAVY_RAIN = 0.7;

    /**
     * Meq/L, the alkalinity that water brings from its catchment. Carbonate rock (limestone, dolomite, chalk, marble) gives waters of 2 to 5 meq/L (the hardwater rivers of the limestone country; Stumm and Morgan, Aquatic Chemistry), 3 here. Silicate rock, granite and the like, gives dilute waters of 0.02 to 0.2 meq/L, and lakes under 0.2 meq/L are the ones that acid rain killed in Scandinavia and on the Canadian Shield (Henriksen 1979, Nature 278), 0.1 here.
     */
    public static final double CARBONATE_ALKALINITY = 3.0, SILICATE_ALKALINITY = 0.1;

    // --- Runoff from soil and the places that count as water ---------------------------------------------------------------------------------------------------------------

    /** Blocks: a chunk whose centre is this near a river (by the distance to its fractal) drains to it. A river edge is not further than this from the water of a discharge that it is given. */
    public static final double RUNOFF_REACH_BLOCKS = 64.0, DISCHARGE_REACH_BLOCKS = 128.0;

    /**
     * Share of the soluble load of the soil that a year of runoff takes to the water, for a chunk that drains to it. GAME FIGURES, with the ranges of the literature: sewage 30% (the loss of manure and faecal bacteria to streams from a bare or sodden field is 1 to 40% a year, and the bacteria are the first to go with the first storms); tar 5% (phenols and light tar are soluble, heavy tar stays); metals 1% (lead and zinc leave soils at a few hundredths to one percent a year, being held by organic matter and oxides); acid from the aluminium range only, 10%, since only a soil below pH 4.2 gives its acid to streams (the acid streams of the Adirondacks carry aluminium out of soils below that pH, Cronan and Schofield 1979, Science 204).
     */
    public static final double SEWAGE_RUNOFF_PER_YEAR = 0.30, TAR_RUNOFF_PER_YEAR = 0.05, METAL_RUNOFF_PER_YEAR = 0.01, ALUMINIUM_ACID_RUNOFF_PER_YEAR = 0.10;

    public static final double SECONDS_PER_DAY = 86_400.0, SECONDS_PER_HOUR = 3600.0, HOURS_PER_DAY = 24.0;
    /** The largest share of a reach that may flow on in one substep: more than that and a substep is cut shorter, as in the air grid. */
    public static final double MAX_OUTFLOW_PER_SUBSTEP = 0.5;
    public static final int MAX_SUBSTEPS = 256;

    private WaterConstants() {}

    /** @return {@code true} for the metals, which settle and do not break down */
    public static boolean isMetal(Pollutant pollutant)
    {
        return switch (pollutant)
        {
            case LEAD, ARSENIC, MERCURY, ZINC, COPPER, ANTIMONY, NICKEL -> true;
            default -> false;
        };
    }

    /** @return the share of the soluble soil load that goes to the water in a year, 0 for acid, which goes as the acid of the aluminium range and is worked out with the weathering */
    public static double runoffPerYear(Pollutant pollutant)
    {
        return switch (pollutant)
        {
            case SEWAGE, SHED_DYSENTERY, SHED_TYPHOID -> SEWAGE_RUNOFF_PER_YEAR;
            case TAR -> TAR_RUNOFF_PER_YEAR;
            case ACIDITY -> 0.0;
            default -> METAL_RUNOFF_PER_YEAR;
        };
    }

    /** @return the first-order rate of breakdown in water, 1/day, of the kinds that break down there */
    public static double decayPerDay(Pollutant pollutant, boolean still)
    {
        return switch (pollutant)
        {
            case SEWAGE, SHED_DYSENTERY, SHED_TYPHOID -> Math.log(10.0) / (still ? SEWAGE_T90_LAKE_DAYS : SEWAGE_T90_RIVER_DAYS);
            case TAR -> Math.log(2.0) / TAR_HALF_LIFE_DAYS;
            default -> 0.0;
        };
    }

    /** @return the settling rate of the water column to the bed, per day, for the bound share */
    public static double settlingPerDay(boolean still)
    {
        return still ? LAKE_SETTLING_PER_DAY : SETTLING_PER_DAY;
    }

    /**
     * @return the content per litre, in the unit of the pollutant, under which what is left of it in a reach is dropped as nothing. The same mass for all: a picogram a litre (1e-12 g/L, 1e-15 kg/L for tar), which in a reach of 3e7 L is 0.03 mg, far below anything that could harm. For acid 1e-12 eq/L, and for sewage 1e-12 L/L, which is one part in 1e12 of raw sewage.
     */
    public static double floorPerLitre(Pollutant pollutant)
    {
        return pollutant == Pollutant.TAR ? 1e-15 : 1e-12;
    }

    /** @return the alkalinity of the water of a catchment of this rock, meq/L */
    public static double alkalinityOf(boolean carbonateRock)
    {
        return carbonateRock ? CARBONATE_ALKALINITY : SILICATE_ALKALINITY;
    }
}
