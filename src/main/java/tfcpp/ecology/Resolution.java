package tfcpp.ecology;

import java.util.List;

/**
 * What is known about the water near one chunk, worked out off the main thread from TFC's river graph. Free of Minecraft types.
 *
 * @param edgeId the river edge nearest to the middle of the chunk, or {@link ReachInfo#SEA} if the graph has none
 * @param distanceBlocks the distance to it, in blocks, by the fractal of the edge
 * @param lakeAlkalinity meq/L of the water that the rock at the chunk gives to still water there
 * @param chain the edge and the edges downstream of it down to the mouth, as far as the ones that the asker already knew; their geometry and the alkalinity of their catchments
 */
public record Resolution(int chunkX, int chunkZ, long edgeId, double distanceBlocks, double lakeAlkalinity, List<ReachInfo> chain)
{
    public long key()
    {
        return AirGrid.key(chunkX, chunkZ);
    }
}
