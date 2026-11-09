package tfcpp.ecology;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import net.dries007.tfc.world.biome.BiomeSourceExtension;
import net.dries007.tfc.world.chunkdata.ChunkDataGenerator;
import net.dries007.tfc.world.region.RiverEdge;
import net.dries007.tfc.world.region.Units;
import org.jetbrains.annotations.Nullable;

/**
 * TFC's river graph, asked about the chunk. The edges are found by {@code BiomeSourceExtension.getPartition(x, z).rivers()}, which is a pure function of the seed but may have to build a region of the world, so this runs only on a thread of its own. The rock of the catchment is TFC's surface rock at the middle of the edge, from the chunk data generator, which is a pure function of the seed too.
 */
public final class TfcRiverResolver implements RiverResolver
{
    /** A walk down the river that is longer than this has a loop in it, which TFC's graph does not have. */
    private static final int MAX_WALK = 10_000;

    private final BiomeSourceExtension biomes;
    private final ChunkDataGenerator chunkData;
    private final double metresPerBlock;

    public TfcRiverResolver(BiomeSourceExtension biomes, ChunkDataGenerator chunkData, double metresPerBlock)
    {
        this.biomes = biomes;
        this.chunkData = chunkData;
        this.metresPerBlock = metresPerBlock;
    }

    @Override
    public Resolution resolve(int chunkX, int chunkZ, LongPredicate known)
    {
        final int blockX = (chunkX << 4) + 8, blockZ = (chunkZ << 4) + 8;
        final double gridX = Units.blockToGridExact(blockX), gridZ = Units.blockToGridExact(blockZ);
        @Nullable RiverEdge nearest = null;
        double nearestSquared = Double.MAX_VALUE;
        for (RiverEdge edge : biomes.getPartition(blockX, blockZ).rivers())
        {
            final double squared = edge.fractal().intersectDistance(gridX, gridZ);
            if (squared < nearestSquared)
            {
                nearestSquared = squared;
                nearest = edge;
            }
        }
        final List<ReachInfo> chain = new ArrayList<>();
        @Nullable RiverEdge edge = nearest;
        for (int walked = 0; edge != null && walked < MAX_WALK; walked++)
        {
            final long id = idOf(edge);
            if (known.test(id))
            {
                break; // From here down the asker has it already
            }
            chain.add(infoOf(edge));
            edge = edge.drainEdge();
        }
        final double distance = nearest == null ? Double.POSITIVE_INFINITY : Math.sqrt(nearestSquared) * Units.GRID_WIDTH_IN_BLOCK;
        return new Resolution(chunkX, chunkZ, nearest == null ? ReachInfo.SEA : idOf(nearest), distance, alkalinityAt(((chunkX >> 3) << 7) + 64, ((chunkZ >> 3) << 7) + 64), chain);
    }

    /** @return the id of an edge: made from its two vertices, which is the same for every run of one seed */
    public static long idOf(RiverEdge edge)
    {
        return ReachInfo.riverId(edge.source().x(), edge.source().y(), edge.drain().x(), edge.drain().y());
    }

    private ReachInfo infoOf(RiverEdge edge)
    {
        // The length of the edge is the length of its fractal polyline, in grid units of 128 blocks, and the middle is the point half way along it
        final double[] points = edge.fractal().segments;
        double length = 0.0;
        for (int i = 0; i + 3 < points.length; i += 2)
        {
            length += Math.hypot(points[i + 2] - points[i], points[i + 3] - points[i + 1]);
        }
        double along = length / 2.0, midX = points[0], midZ = points[1];
        for (int i = 0; i + 3 < points.length; i += 2)
        {
            final double piece = Math.hypot(points[i + 2] - points[i], points[i + 3] - points[i + 1]);
            if (along <= piece && piece > 0.0)
            {
                final double t = along / piece;
                midX = points[i] + (points[i + 2] - points[i]) * t;
                midZ = points[i + 1] + (points[i + 3] - points[i + 1]) * t;
                break;
            }
            along -= piece;
        }
        final int blockX = (int) Math.floor(midX * Units.GRID_WIDTH_IN_BLOCK), blockZ = (int) Math.floor(midZ * Units.GRID_WIDTH_IN_BLOCK);
        final @Nullable RiverEdge drain = edge.drainEdge();
        return ReachInfo.river(idOf(edge), edge.width, length * Units.GRID_WIDTH_IN_BLOCK, metresPerBlock, alkalinityAt(blockX, blockZ),
            drain == null ? ReachInfo.SEA : idOf(drain), blockX >> 7, blockZ >> 7);
    }

    /** @return meq/L from the surface rock at a block: the water of a catchment of carbonate rock is hard, and that of granite is not */
    private double alkalinityAt(int blockX, int blockZ)
    {
        return WaterConstants.alkalinityOf(SoilReader.isCarbonate(chunkData.generateSurfaceRock(blockX, blockZ)));
    }
}
