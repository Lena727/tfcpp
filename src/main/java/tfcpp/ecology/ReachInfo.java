package tfcpp.ecology;

/**
 * What is fixed about one body of fresh water: a reach of a river, which is an edge of TFC's river graph, or the still water of one regional cell. Free of Minecraft types.
 * <p>
 * A reach is the river that a player stands in, as stage B's air is the air that a player breathes: its volume is the visible channel's cross-section times the game length of the edge, in blocks that are metres. How fast water travels through it is compressed like the wind of stage B: a block of the map stands for {@code metresPerBlock} metres of the real world, so water at 0.5 m/s takes {@code length * metresPerBlock / 0.5} seconds to run through. The effective discharge is the volume over that time, {@code cross-section * u / metresPerBlock}.
 * <p>
 * Ids are 62-bit numbers that are never 0: a river id is made from the coordinates of the two vertices of the edge, which are the same for every run of the same seed, and a lake id is the coordinates of the cell with bit 62 set. 0 stands for the sea, which is where a river ends.
 */
public final class ReachInfo
{
    /** The id of the sea, which is the drain of a river's mouth and takes in everything that is not kept. */
    public static final long SEA = 0L;
    private static final long LAKE_BIT = 1L << 62;
    private static final long MASK = LAKE_BIT - 1L;

    private final long id, drain;
    private final boolean still;
    private final double width, lengthBlocks, metresPerBlock, alkalinity;
    private final int cellX, cellZ;

    // Derived, once
    private final double discharge, residence, volume, surface;

    private ReachInfo(long id, long drain, boolean still, double width, double lengthBlocks, double metresPerBlock, double alkalinity, int cellX, int cellZ)
    {
        this.id = id;
        this.drain = drain;
        this.still = still;
        this.width = width;
        this.lengthBlocks = lengthBlocks;
        this.metresPerBlock = metresPerBlock;
        this.alkalinity = alkalinity;
        this.cellX = cellX;
        this.cellZ = cellZ;
        if (still)
        {
            this.discharge = 0.0;
            this.residence = Double.POSITIVE_INFINITY;
            this.volume = WaterConstants.LAKE_VOLUME;
            this.surface = WaterConstants.LAKE_AREA;
        }
        else
        {
            this.volume = Hydraulics.crossSection(width) * lengthBlocks;
            this.residence = lengthBlocks * metresPerBlock / WaterConstants.VELOCITY;
            this.discharge = volume / residence;
            this.surface = Hydraulics.visibleWidth(width) * lengthBlocks;
        }
    }

    /**
     * @param width the width TFC gives the edge: the radius of its channel, in blocks
     * @param lengthBlocks the game length of the edge in blocks, which are metres
     * @param metresPerBlock the metres of the real world that a block of the map stands for, 500 in a world of TFC's default temperature scale
     * @param alkalinity meq/L, from the rock of the catchment
     * @param drain the id of the next edge downstream, or {@link #SEA}
     * @param cellX the regional cell (128 blocks) where the middle of the edge is, which is where its weather and the fall from the air are read
     */
    public static ReachInfo river(long id, double width, double lengthBlocks, double metresPerBlock, double alkalinity, long drain, int cellX, int cellZ)
    {
        return new ReachInfo(id, drain, false, width, Math.max(lengthBlocks, 1.0), metresPerBlock, alkalinity, cellX, cellZ);
    }

    public static ReachInfo lake(int cellX, int cellZ, double alkalinity)
    {
        return new ReachInfo(lakeId(cellX, cellZ), SEA, true, 0.0, 0.0, 1.0, alkalinity, cellX, cellZ);
    }

    /** @return the id of the edge of a river, from the coordinates (in the grid of TFC's regions) of its two vertices; the same for every run of one seed */
    public static long riverId(double sourceX, double sourceZ, double drainX, double drainZ)
    {
        long h = 0x9E3779B97F4A7C15L;
        for (double value : new double[] {sourceX, sourceZ, drainX, drainZ})
        {
            h ^= Double.doubleToLongBits(value);
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 31;
        }
        h &= MASK;
        return h == 0L ? 1L : h;
    }

    public static long lakeId(int cellX, int cellZ)
    {
        return LAKE_BIT | ((cellX & 0x7FFFFFFFL) << 31) | (cellZ & 0x7FFFFFFFL);
    }

    public static boolean isLake(long id)
    {
        return (id & LAKE_BIT) != 0L;
    }

    public long id()
    {
        return id;
    }

    /** @return the id of the next reach downstream, {@link #SEA} if this is a mouth. A lake does not flow out: see {@link #still()} */
    public long drain()
    {
        return drain;
    }

    /** @return {@code true} for still water, which has no outflow and keeps whatever is put into it */
    public boolean still()
    {
        return still;
    }

    /** @return TFC's width of the edge, which is the radius of the channel, in blocks */
    public double width()
    {
        return width;
    }

    /** @return the game length of the edge in blocks, which are metres */
    public double lengthBlocks()
    {
        return lengthBlocks;
    }

    public double metresPerBlock()
    {
        return metresPerBlock;
    }

    /** @return the length that the edge stands for in the real world, metres */
    public double realLengthMetres()
    {
        return lengthBlocks * metresPerBlock;
    }

    /** @return meq/L, which is the same number as equivalents per cubic metre */
    public double alkalinity()
    {
        return alkalinity;
    }

    public int cellX()
    {
        return cellX;
    }

    public int cellZ()
    {
        return cellZ;
    }

    /** @return the effective discharge in cubic metres of game volume per second: the volume over the compressed residence. 0 for still water */
    public double discharge()
    {
        return discharge;
    }

    /** @return seconds that water takes to run through the reach in the compressed travel of the map, infinite for still water */
    public double residenceSeconds()
    {
        return residence;
    }

    /** @return cubic metres of game volume in the reach */
    public double volume()
    {
        return volume;
    }

    /** @return litres of water in the reach */
    public double litres()
    {
        return volume * 1000.0;
    }

    /** @return the square metres of water that the sky sees: the visible width of the channel times its game length, or the whole cell for still water */
    public double surfaceArea()
    {
        return surface;
    }

    /**
     * @return equivalents of acid per second that the water neutralises: for a river what its alkalinity brings through the reach (meq/L times m3/s is equivalents per second), for still water the renewal of its stock from the catchment
     */
    public double alkalinityFluxPerSecond()
    {
        return still ? alkalinityStock() / (WaterConstants.lakeAlkalinityRenewalDays() * WaterConstants.SECONDS_PER_DAY) : alkalinity * discharge;
    }

    /** @return equivalents of alkalinity in the volume of still water when it is full: meq/L is eq/m3 */
    public double alkalinityStock()
    {
        return alkalinity * volume;
    }

    /**
     * The visible river. TFC's river noise (RiverNoise, the floodplain sampler) makes the bed from the normalised distance {@code distFac = 0.8 r^2 / w^2}: the channel is wet where distFac is under 1, that is out to {@code r = w / sqrt(0.8) = 1.118 w}, so the visible channel is 2.236 w wide (a radius, as TFC calls the width). The bed there is {@code SEA_LEVEL - 2 - D + distFac * D}, with D = clampedMap(w^2, 8^2, 24^2, 3, 6), so the water is 2 + D(1 - distFac) deep: a parabolic section, 2 + D deep in the middle.
     * <p>
     * Its area is the integral, 4a(1 + D/3) with a the half width, and the mean depth is 2 + 2D/3 (4 m for w = 8, 6 m for w = 24). The noise that TFC adds to distFac is ignored.
     */
    public static final class Hydraulics
    {
        private Hydraulics() {}

        /** @return TFC's D, in blocks: 3 for the narrowest edge and 6 for the widest */
        public static double tfcDepth(double width)
        {
            final double t = (width * width - 64.0) / (576.0 - 64.0);
            return 3.0 + 3.0 * Math.min(1.0, Math.max(0.0, t));
        }

        /** @return the half width of the wet channel, blocks */
        public static double halfWidth(double width)
        {
            return width / Math.sqrt(0.8);
        }

        /** @return the width of the visible channel, blocks */
        public static double visibleWidth(double width)
        {
            return 2.0 * halfWidth(width);
        }

        /** @return the mean depth of the water over the visible channel, metres */
        public static double meanDepth(double width)
        {
            return 2.0 + 2.0 * tfcDepth(width) / 3.0;
        }

        /** @return square metres of the wet cross-section */
        public static double crossSection(double width)
        {
            return 4.0 * halfWidth(width) * (1.0 + tfcDepth(width) / 3.0);
        }
    }
}
