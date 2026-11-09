package tfcpp.ecology;

/**
 * Every number of the soil memory in one place, each with its source. Free of Minecraft types.
 * <p>
 * The soil is one layer, the root zone, 0.5 m deep and 650 kg/m2, and a chunk cell treats all of its 256 square metres alike.
 */
public final class SoilConstants
{
    /** Square metres of soil in one chunk cell. */
    public static final double AREA = 256.0;
    /** Metres. The rooting depth that critical loads are worked out for in the literature. */
    public static final double ROOT_ZONE_DEPTH = 0.5;
    /** Kilograms of soil in a square metre of root zone. */
    public static final double ROOT_ZONE_MASS = 650.0;
    /** Equivalents per square metre for each cmol(+)/kg of exchange capacity: 650 kg/m2 times 0.01 mol(+) per cmol. */
    public static final double CMOL_PER_KG_TO_EQ_PER_M2 = ROOT_ZONE_MASS / 100.0;

    // --- Ulrich's buffer ranges (Ulrich, B. 1983, "Soil acidity and its relation to acid deposition", in Effects of Accumulation of Air Pollutants in Forest Ecosystems) ---

    /**
     * Ulrich puts the carbonate range at pH 8.6 to 6.2. The soil here holds pH 8.0 for as long as any carbonate is left, which is calcite in water at the carbon dioxide of the air (about 8.3 for pure water, 8.0 in a soil with some CO2 of its own). Below it the carbonate is gone.
     */
    public static final double PH_CARBONATE_PLATEAU = 8.0;
    /**
     * Cation exchanger: the pH goes from 4.2 with no base cations on the exchange sites (base saturation 0) to 7.0 with the sites full of them (base saturation 1), as pH = 4.2 + 2.8 BS. Ulrich's silicate and exchanger ranges (6.2 to 4.2) are both in it: what he calls the silicate range is the top of this line, where weathering keeps up.
     */
    public static final double PH_EXCHANGER_BOTTOM = 4.2, PH_EXCHANGER_TOP = 7.0;
    /** Aluminium range: pH 4.2 to 3.0. Below it nothing more buffers the soil, and aluminium is mobile and kills roots. */
    public static final double PH_ALUMINIUM_BOTTOM = 3.0;

    /**
     * Equivalents per square metre that the carbonate holds in a soil that has it: 5% CaCO3 by mass in 0.5 m of soil at 1300 kg/m3 is 32.5 kg of CaCO3, 325 mol, and each mole takes two equivalents of acid. It is given to carbonate bedrock and to the Aridisol, and is more than any source of acid in the game comes near using up.
     */
    public static final double CARBONATE_FULL = 650.0;

    /** Aluminium range: the acid taken up while aluminium hydroxides dissolve. A GAME FIGURE, about a tenth of the aluminium in the root zone, and not a measurement; past it the pH stays at 3.0 and the acid goes on collecting. */
    public static final double ALUMINIUM_RANGE = 500.0;

    /**
     * Silicate weathering is a flux and not a stock: 0.01 eq/m2 a year, 0.1 keq/ha/yr, of base cations given to the exchanger. The range in the literature is 0.02 to 0.2 keq/ha/yr (UBA, Manual on Critical Loads, 2004, for mineral soils). It brings base saturation back up to where the soil started and not above it.
     */
    public static final double WEATHERING_PER_YEAR = 0.01;

    /** Days of TFC's calendar in a year, taken as the real one: the constants of the model are the real ones. */
    public static final double DAYS_PER_YEAR = 365.0;
    /** Ticks of the calendar in a day: {@code ICalendar.CALENDAR_TICKS_IN_DAY}, which does not depend on the length of a day that a server sets. */
    public static final long CALENDAR_TICKS_PER_DAY = 24_000L;

    /** Days. Coal tar in soil: PAH and phenols have a half-life of months, the order of 180 days for the lighter ones (Wilson and Jones, 1993, Environ. Pollut. 81). */
    public static final double TAR_HALF_LIFE_DAYS = 180.0;
    /** Days for the pathogens of sewage in soil to fall to a tenth: T90 of 21 days, the decimal reduction time of faecal bacteria in soil (Reddy et al., 1981, J. Environ. Qual. 10). */
    public static final double SEWAGE_T90_DAYS = 21.0;

    private SoilConstants() {}

    /** @return the fraction of a pollutant that breaks down in a day in soil, as a first-order rate in 1/day. Metals do not break down: leaching is not modelled in v1.5 */
    public static double decayPerDay(Pollutant pollutant)
    {
        return switch (pollutant)
        {
            case TAR -> Math.log(2.0) / TAR_HALF_LIFE_DAYS;
            case SEWAGE -> Math.log(10.0) / SEWAGE_T90_DAYS;
            default -> 0.0;
        };
    }
}
