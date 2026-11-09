package tfcpp.ecology;

/**
 * The scales of the regional air, in one place with the reasoning for each. Free of Minecraft types.
 * <p>
 * Two scales of space meet here. What the air holds is counted in game volume, where a block is a metre: a regional cell is a column 128 by 128 blocks, the grid of TFC's regions, as high as the boundary layer that smoke is mixed through, and a concentration is moles over that volume, so that it is the figure a player breathes and a tree feels. But the climate of TFC is squeezed: from the pole of cold to the pole of heat is 20 km where the Earth has 10 000. So how far smoke travels in a day is counted in the squeezed scale: a block of the map is {@link #metresPerBlock} metres of the real world. Wind speeds are real metres per second, and divided by that number they become blocks per second on the map.
 */
public final class AirScale
{
    /** Blocks along a side of a regional cell: {@code Units.GRID_WIDTH_IN_BLOCK} of TFC. */
    public static final double CELL_BLOCKS = 128.0;
    /** Square metres of game area under a regional cell. */
    public static final double CELL_AREA = CELL_BLOCKS * CELL_BLOCKS;
    /** Metres. The height that smoke is mixed through by day: the boundary layer, 0.5 to 2 km (Stull, An Introduction to Boundary Layer Meteorology, 1988). */
    public static final double MIXING_HEIGHT = 1000.0;
    /** Cubic metres of game volume in a regional cell. */
    public static final double CELL_VOLUME = CELL_AREA * MIXING_HEIGHT;

    /** Metres from the pole to the equator on Earth: 10 000 km. TFC's temperature scale is the distance between its poles of temperature in blocks, and this is divided by it. */
    public static final double POLE_TO_EQUATOR_METRES = 10_000_000.0;
    /** The scale of TFC's default world (20 km), used when a world has none: a world with a constant temperature has a temperature scale of 0. */
    public static final int DEFAULT_TEMPERATURE_SCALE = 20_000;

    /** Metres per second of a wind of strength 1 in TFC: its own comment says 32 m/s, 115 km/h. */
    public static final double WIND_STRENGTH_TO_METRES_PER_SECOND = 32.0;
    /**
     * What TFC's wind is multiplied by to get the wind that carries smoke through the mixing layer. TFC's wind is a surface wind, and the wind at the height of a plume, 100 to 500 m, is stronger: the power law with an exponent of 0.2 to 0.3 for open terrain gives 1.5 to 2 times the 10 m wind there, and 1.5 is the low end (Stull, An Introduction to Boundary Layer Meteorology, 1988, ch. 9; Seinfeld and Pandis ch. 16).
     */
    public static final double MIXED_LAYER_WIND_FACTOR = 1.5;
    /** The eddy diffusivity of the boundary layer for horizontal mixing on the scale of a regional cell, m2/s in the real world: 1e4 for scales of tens to hundreds of kilometres (Okubo 1971, Deep-Sea Research 18; Seinfeld and Pandis ch. 18). */
    public static final double EDDY_DIFFUSIVITY = 1.0e4;

    /** Calendar ticks and seconds in an hour of TFC's calendar: a day is 24 000 ticks, which stand for 86 400 s. */
    public static final long TICKS_PER_HOUR = 1000L;
    public static final double SECONDS_PER_HOUR = 3600.0;
    /** Hours in a TFC calendar year (96 days by default): the time constant of the annual means. */
    public static double hoursPerYear()
    {
        return SoilConstants.daysPerYear() * 24.0;
    }

    /** An emission whose outlet stands this much above the ground around it is a tall stack. */
    public static final double TALL_STACK_METRES = 20.0;

    /** The concentration of an acid gas under which it is background: 1 microgram per cubic metre. */
    public static final double ACID_GAS_BACKGROUND_MICROGRAMS = 1.0;
    /** The concentration of a metal fume or of mercury under which it is background: 0.01 microgram per cubic metre. */
    public static final double BACKGROUND_MICROGRAMS_PER_CUBIC_METRE = 0.01;

    private AirScale() {}

    /** @return the metres of the real world that a block of the map stands for, which is 500 in a world of the default temperature scale */
    public static double metresPerBlock(int temperatureScale)
    {
        return POLE_TO_EQUATOR_METRES / (temperatureScale > 0 ? temperatureScale : DEFAULT_TEMPERATURE_SCALE);
    }

    /** @return the share of a cell that a wind carries past its edge each second, for a wind of this many metres per second blowing across it */
    public static double crossingPerSecond(double metresPerSecond, double metresPerBlock)
    {
        return Math.abs(metresPerSecond) / (metresPerBlock * CELL_BLOCKS);
    }

    /** @return the share of the difference between two neighbouring cells that mixing evens out each second: the diffusivity of the real world turned into the squeezed map, K over C squared, divided by the side of a cell squared */
    public static double mixingPerSecond(double metresPerBlock)
    {
        return EDDY_DIFFUSIVITY / (metresPerBlock * metresPerBlock * CELL_BLOCKS * CELL_BLOCKS);
    }

    /** @return micrograms per cubic metre of a gas whose molar mass is given, from its moles in a whole regional cell */
    public static double microgramsPerCubicMetre(double moles, double molarMass)
    {
        return moles * molarMass * 1e6 / CELL_VOLUME;
    }

    /** @return moles in a whole regional cell for a concentration in micrograms per cubic metre */
    public static double molesFor(double microgramsPerCubicMetre, double molarMass)
    {
        return microgramsPerCubicMetre * CELL_VOLUME / (molarMass * 1e6);
    }

    /** @return whether an outlet is a tall stack, given its height and the height of the ground around it */
    public static boolean isTallStack(double outletY, double groundY)
    {
        return outletY - groundY > TALL_STACK_METRES;
    }

    /**
     * @return the offset in cells {dx, dz} of the neighbour that a wind blows towards, along the stronger of its two components, or {0, 0} if it is calm. The historical tall stacks sent their smoke past the ground at their foot to the next district downwind.
     */
    public static int[] downwind(double windX, double windZ)
    {
        if (windX == 0.0 && windZ == 0.0)
        {
            return new int[] {0, 0};
        }
        return Math.abs(windX) >= Math.abs(windZ) ? new int[] {windX > 0 ? 1 : -1, 0} : new int[] {0, windZ > 0 ? 1 : -1};
    }
}
