package tfcpp.mechanics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Who may walk the open space this tick, when the walks of a level share a budget of cells. Free of Minecraft types, so that it can be run against a test.
 * <p>
 * A walk is paid for only when it is made. A worker that asks and finds the budget spent is put at the end of a queue, and is served first at the next tick, before any worker whose second has just come: so no worker can wait for ever behind others that are always in the same slot of the second.
 *
 * @param <T> the kind of worker, compared by identity
 */
public final class WalkTurns<T>
{
    private final LinkedHashSet<IdentityKey<T>> waiting = new LinkedHashSet<>();
    private int cellsLeft;

    /** Starts a tick with this budget. */
    public void startTick(int budget)
    {
        cellsLeft = budget;
    }

    /** @return the workers that were refused, oldest first, which are due again this tick. They are no longer waiting: if one still wants to walk, it asks again. */
    public List<T> takeWaiting()
    {
        final List<T> due = new ArrayList<>(waiting.size());
        for (IdentityKey<T> key : waiting)
        {
            due.add(key.value);
        }
        waiting.clear();
        return due;
    }

    /**
     * Asks for a walk of this many cells.
     *
     * @return {@code true} if it may walk, and the cells are spent. {@code false} puts the worker in the queue
     */
    public boolean request(T worker, int cells)
    {
        if (cellsLeft >= cells)
        {
            cellsLeft -= cells;
            return true;
        }
        waiting.add(new IdentityKey<>(worker));
        return false;
    }

    /** The worker has gone, and must not be served. */
    public void forget(T worker)
    {
        waiting.remove(new IdentityKey<>(worker));
    }

    public boolean isWaiting(T worker)
    {
        return waiting.contains(new IdentityKey<>(worker));
    }

    public int waitingCount()
    {
        return waiting.size();
    }

    public int cellsLeft()
    {
        return cellsLeft;
    }

    private record IdentityKey<T>(T value)
    {
        @Override
        public boolean equals(Object other)
        {
            return other instanceof IdentityKey<?> key && key.value == value;
        }

        @Override
        public int hashCode()
        {
            return System.identityHashCode(value);
        }
    }
}
