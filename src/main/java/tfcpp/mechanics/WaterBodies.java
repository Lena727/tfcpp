package tfcpp.mechanics;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.event.level.block.CreateFluidSourceEvent;

/**
 * Two blocks of water do not make a third. A hole in a pool stays a hole, as the water has to come from somewhere.
 * <p>
 * The exception is a large body of open water, which in the real world is fed by its whole catchment: a hole in a lake is too small to show, as the level falls by less than the rain puts back. A sealed tank has no catchment, however large it is.
 */
public final class WaterBodies
{
    /** A body with this many square metres open to the sky counts as fed by its catchment. */
    public static final int FED_BY_CATCHMENT = 256;
    /** The most blocks looked at for one check. A body this deep with little surface is not taken as a lake. */
    private static final int MAX_WALKED = 4096;

    /** A flow asks about each source beside each block it looks at, so one walk answers for all the water it saw, for a few seconds. */
    private static final int MEMORY_TICKS = 100;
    private static final Map<Level, Memory> MEMORIES = new WeakHashMap<>();

    private static final class Memory
    {
        final Long2BooleanOpenHashMap known = new Long2BooleanOpenHashMap();
        long clearedAt;
    }

    /** The game asks this about a source next to a block that may fill in: a source that says no does not count towards the two it needs. */
    public static void onCreateFluidSource(CreateFluidSourceEvent event)
    {
        if (event.canConvert() && !isFed(event.getLevel(), event.getPos(), event.getFluidState().getType()))
        {
            event.setCanConvert(false);
        }
    }

    private static boolean isFed(Level level, BlockPos pos, Fluid fluid)
    {
        final Memory memory = MEMORIES.computeIfAbsent(level, key -> new Memory());
        if (level.getGameTime() - memory.clearedAt >= MEMORY_TICKS)
        {
            memory.known.clear();
            memory.clearedAt = level.getGameTime();
        }
        if (memory.known.containsKey(pos.asLong()))
        {
            return memory.known.get(pos.asLong());
        }

        final Set<BlockPos> walked = new HashSet<>();
        final boolean fed = walk(level, pos, fluid, FED_BY_CATCHMENT, walked);
        for (BlockPos seen : walked)
        {
            memory.known.put(seen.asLong(), fed);
        }
        return fed;
    }

    /** @return {@code true} if this position joins a connected body of liquid with at least this many source blocks open to the sky */
    public static boolean isFedByCatchment(Level level, BlockPos start, Fluid fluid, int openSurface)
    {
        return walk(level, start, fluid, openSurface, new HashSet<>());
    }

    private static boolean walk(Level level, BlockPos start, Fluid fluid, int openSurface, Set<BlockPos> seen)
    {
        final Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);

        int open = 0;
        while (!queue.isEmpty() && seen.size() < MAX_WALKED)
        {
            final BlockPos current = queue.poll();
            for (Direction direction : Direction.values())
            {
                final BlockPos next = current.relative(direction);
                if (level.isLoaded(next) && seen.add(next) && level.getFluidState(next).isSourceOfType(fluid))
                {
                    if (next.getY() + 1 >= level.getHeight(Heightmap.Types.WORLD_SURFACE, next.getX(), next.getZ()) && ++open >= openSurface)
                    {
                        return true;
                    }
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private WaterBodies() {}
}
