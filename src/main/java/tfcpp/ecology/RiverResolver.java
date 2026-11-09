package tfcpp.ecology;

import java.util.function.LongPredicate;

/**
 * The river graph, asked about one chunk. It may take long (TFC may have to build a region of the world to answer), so it is never called on the main thread. Free of Minecraft types, so that tests give it a graph that is made up.
 */
public interface RiverResolver
{
    /**
     * @param known tells whether the asker already has the reach of an id (a set that is safe to read from another thread): the walk down the river stops at the first that it has
     * @return the water near the middle of the chunk
     */
    Resolution resolve(int chunkX, int chunkZ, LongPredicate known);
}
