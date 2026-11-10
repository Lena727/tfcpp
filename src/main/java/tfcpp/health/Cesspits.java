package tfcpp.health;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/** Finds the cesspits near a place, in the chunks that are loaded: a lookup never loads or makes a chunk. */
public final class Cesspits
{
    private Cesspits() {}

    static void register(ServerLevel level, BlockPos pos)
    {
        final LevelChunk chunk = level.getChunkAt(pos);
        if (chunk.getData(Hygiene.CESSPITS).add(pos.asLong()))
        {
            chunk.setUnsaved(true);
        }
    }

    static void unregister(ServerLevel level, BlockPos pos)
    {
        final LevelChunk chunk = level.getChunkAt(pos);
        if (chunk.hasData(Hygiene.CESSPITS) && chunk.getData(Hygiene.CESSPITS).remove(pos.asLong()))
        {
            chunk.setUnsaved(true);
        }
    }

    /** What a visitor does with a pit that it found: the pit and its position. */
    @FunctionalInterface
    public interface Visitor
    {
        void visit(BlockPos pos, CesspitBlockEntity pit);
    }

    /** Calls the visitor for each cesspit in the loaded chunks within {@code reach} blocks horizontally of the position. */
    public static void around(ServerLevel level, BlockPos center, int reach, Visitor visitor)
    {
        final int x0 = (center.getX() - reach) >> 4, x1 = (center.getX() + reach) >> 4, z0 = (center.getZ() - reach) >> 4, z1 = (center.getZ() + reach) >> 4;
        for (int cx = x0; cx <= x1; cx++)
        {
            for (int cz = z0; cz <= z1; cz++)
            {
                final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null || !chunk.hasData(Hygiene.CESSPITS))
                {
                    continue;
                }
                for (long packed : chunk.getData(Hygiene.CESSPITS).positions())
                {
                    final BlockPos pos = BlockPos.of(packed);
                    final int dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
                    if (dx * dx + dz * dz <= reach * reach && chunk.getBlockEntity(pos) instanceof CesspitBlockEntity pit)
                    {
                        visitor.visit(pos, pit);
                    }
                }
            }
        }
    }

    /** @return the nearest cesspit within the reach of a person's waste (16 blocks across, 4 up or down), or {@code null} */
    @Nullable
    public static CesspitBlockEntity nearestForWaste(ServerLevel level, BlockPos at)
    {
        final CesspitBlockEntity[] best = new CesspitBlockEntity[1];
        final double[] bestDistance = {Double.MAX_VALUE};
        around(level, at, CesspitMath.REACH_HORIZONTAL, (pos, pit) ->
        {
            final double distance = pos.distSqr(at);
            if (Math.abs(pos.getY() - at.getY()) <= CesspitMath.REACH_VERTICAL && distance < bestDistance[0] && CesspitMath.room(pit.state()) > 0.0)
            {
                best[0] = pit;
                bestDistance[0] = distance;
            }
        });
        return best[0];
    }

    /**
     * What the pits near a water block add to its water: litres of sewage per litre, in all and from each kind of shedder. A pit counts if it is within 30 blocks across and the surface of the water is not more than 2 blocks above the base of the pit (the lid is a block above it) (the water table does not run uphill: a well above a latrine is safe from it, below it is not), and its infective contents are as they are now. Reads only.
     *
     * @return {@code {sewage, shedding Shigella, shedding S. Typhi}}
     */
    public static double[] groundwaterAt(ServerLevel level, BlockPos water)
    {
        return groundwater(level, water, 0.0, null);
    }

    /**
     * As {@link #groundwaterAt}, for water that is taken away: {@code litres} of it carry off from the pits what they hold, and the pits lose it ({@link CesspitMath#draw}). What the water carries is {@code groundwaterAt * scale} for each kind, so that a caller that rounds the figure first takes out of the pits what the water carries; a pit that holds less gives what it has.
     *
     * @param scale for each of the three kinds, the share of the read figure that the water takes
     * @return per litre, what the water took: {@code {sewage, shedding Shigella, shedding S. Typhi}}
     */
    public static double[] drawGroundwater(ServerLevel level, BlockPos water, double litres, double[] scale)
    {
        return groundwater(level, water, litres, scale);
    }

    private static double[] groundwater(ServerLevel level, BlockPos water, double litres, double[] scale)
    {
        final double[] sum = new double[3];
        around(level, water, CesspitMath.GROUNDWATER_REACH, (pos, pit) ->
        {
            if (water.getY() > pos.getY() - 1 + 2) // The base of the pit is a block under its lid
            {
                return;
            }
            final double factor = CesspitMath.groundwaterFactor(Math.sqrt(pos.distSqr(water)));
            if (litres > 0.0)
            {
                final CesspitMath.Draw draw = pit.drawInfective(level, factor, litres, scale);
                sum[0] += draw.active() / litres;
                sum[1] += draw.shedDysentery() / litres;
                sum[2] += draw.shedTyphoid() / litres;
            }
            else
            {
                final CesspitMath.State now = pit.catchUp(level);
                sum[0] += now.active() * factor;
                sum[1] += now.shedDysentery() * factor;
                sum[2] += now.shedTyphoid() * factor;
            }
        });
        return sum;
    }
}
