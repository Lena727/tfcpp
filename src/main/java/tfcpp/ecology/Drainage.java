package tfcpp.ecology;

/**
 * Where the runoff of a chunk of soil goes, which is a fact of the place and does not change. Free of Minecraft types.
 */
public enum Drainage
{
    /** Not found out yet: the answer is being worked out off the main thread. */
    UNKNOWN,
    /** No water near enough: what the soil loses stays out of reach. */
    NONE,
    /** Within 64 blocks of a river by the distance to its fractal. */
    RIVER,
    /** In a lake biome: the still water of its regional cell. */
    LAKE,
    /** No river within reach, but the chunk is on the coast: what runs off goes to the sea. */
    SEA;

    /** @return {@code true} if the runoff of the chunk goes to water that is kept track of, or to the sea */
    public boolean drains()
    {
        return this == RIVER || this == LAKE || this == SEA;
    }

    /** @return {@code true} unless the chunk is known to drain to nothing: while it is not known, the soil lets its runoff run and holds it */
    public boolean mayDrain()
    {
        return this != NONE;
    }
}
