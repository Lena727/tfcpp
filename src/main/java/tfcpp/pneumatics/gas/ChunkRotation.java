package tfcpp.pneumatics.gas;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;

/**
 * The chunks of a level that hold gas, in the order they take their turn. Free of Minecraft types, so that the rules can be run against a test.
 * <p>
 * A chunk can be absent without having been unloaded: when its player goes away it is demoted, keeps all its data, and comes back later without any load event. So an absent chunk is not forgotten but parked, and looked at again from time to time. Only an unload takes a chunk off for good.
 */
public final class ChunkRotation
{
    /** What a visit to a chunk decided. */
    public enum Outcome
    {
        /** It still has gas to move: it goes to the back of the queue. */
        KEEP,
        /** There is nothing left that moves: it comes off the queue until something wakes it. */
        DROP
    }

    @FunctionalInterface
    public interface Visitor
    {
        Outcome visit(long chunk);
    }

    private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
    private final LongLinkedOpenHashSet parked = new LongLinkedOpenHashSet();

    public void activate(long chunk)
    {
        parked.remove(chunk);
        queue.add(chunk);
    }

    /** The chunk has been unloaded, and its data with it. */
    public void deactivate(long chunk)
    {
        queue.remove(chunk);
        parked.remove(chunk);
    }

    public int activeCount()
    {
        return queue.size();
    }

    public int parkedCount()
    {
        return parked.size();
    }

    public boolean isParked(long chunk)
    {
        return parked.contains(chunk);
    }

    public boolean isQueued(long chunk)
    {
        return queue.contains(chunk);
    }

    /**
     * Gives the chunks at the front of the queue their turn.
     *
     * @param recheckParked whether to look again at the parked chunks first
     * @param maxVisits the most chunks that may be visited
     * @param present whether a chunk is loaded and ready now
     * @param hasBudget whether there is work left in the budget of this tick
     * @return how many chunks were visited. Parking a chunk that is absent is not a visit
     */
    public int tick(boolean recheckParked, int maxVisits, LongPredicate present, BooleanSupplier hasBudget, Visitor visitor)
    {
        if (recheckParked && !parked.isEmpty())
        {
            for (long chunk : new LongArrayList(parked))
            {
                if (present.test(chunk))
                {
                    parked.remove(chunk);
                    queue.add(chunk);
                }
            }
        }

        int visits = 0;
        int examined = queue.size();
        while (visits < maxVisits && examined-- > 0 && hasBudget.getAsBoolean())
        {
            final long chunk = queue.firstLong();
            if (!present.test(chunk))
            {
                queue.removeFirstLong();
                parked.add(chunk);
                continue;
            }
            visits++;
            queue.removeFirstLong();
            if (visitor.visit(chunk) == Outcome.KEEP)
            {
                queue.add(chunk); // To the back
            }
        }
        return visits;
    }
}
