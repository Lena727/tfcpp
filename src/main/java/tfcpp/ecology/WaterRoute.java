package tfcpp.ecology;

/**
 * The ways that something comes into the fresh waters, each with its own lists of inputs that wait for their reach to be found (one for the water column and one for the bed), and the rule that says which reach it goes to once the river nearest to the place is known. Free of Minecraft types.
 */
public enum WaterRoute
{
    /** A liquid let out into river water: the nearest edge if one is within 128 blocks. If there is none the water is not a river of the graph (a bucket of river water put down far from any river), and the spill goes to the soil under it. */
    DISCHARGE_RIVER(true, WaterConstants.DISCHARGE_REACH_BLOCKS, true),
    /** A liquid let out into a lake or a spring: the still water of the cell. */
    DISCHARGE_STILL(false, 0.0, false),
    /** Runoff from soil that drains to a river: the nearest edge, which the soil was found to be within 64 blocks of. */
    RUNOFF_RIVER(true, WaterConstants.RUNOFF_REACH_BLOCKS, false),
    /** Runoff from soil in a lake biome. */
    RUNOFF_LAKE(false, 0.0, false);

    public static final WaterRoute[] VALUES = values();
    /** The answer of {@link #target} for a spill that is not in water after all and goes to the soil. */
    public static final long SOIL = -1L;

    private final boolean wantsEdge, soilFallback;
    private final double reach;

    WaterRoute(boolean wantsEdge, double reach, boolean soilFallback)
    {
        this.wantsEdge = wantsEdge;
        this.reach = reach;
        this.soilFallback = soilFallback;
    }

    /** @return the list for what goes into the water column; the list for the bed is {@code channel() + VALUES.length}, see {@link #channel(boolean)} */
    public int channel()
    {
        return ordinal();
    }

    public int channel(boolean bed)
    {
        return ordinal() + (bed ? VALUES.length : 0);
    }

    /**
     * @param edgeId the nearest river edge to the place, or {@link ReachInfo#SEA} if there is none
     * @param distanceBlocks the distance to it
     * @param lakeId the still water of the cell that the place is in
     * @return the reach that the input goes to; {@link #SOIL} for a river water spill that is not near any river of the graph; {@link ReachInfo#SEA} for runoff from soil that no river was near after all
     */
    public long target(long edgeId, double distanceBlocks, long lakeId)
    {
        if (wantsEdge)
        {
            if (edgeId != ReachInfo.SEA && distanceBlocks <= reach)
            {
                return edgeId;
            }
            return soilFallback ? SOIL : ReachInfo.SEA;
        }
        return lakeId;
    }
}
