package tfcpp.core;

/**
 * Counts the ticks of TFC's calendar ({@code Calendars.SERVER.getTicks()}) for one device that works in steps, so that it does not work while the calendar stands still on an empty server, and does not lose a long jump of it, as when players sleep. See {@link LocalTime}. Free of Minecraft types, so that it can be run against a test.
 * <p>
 * Not saved: after a load the count starts again from the first tick, as it does for the other devices. A device that must be cooled by the time it spent unloaded keeps its own last tick.
 */
public final class TickClock
{
    private long last = -1L;
    private long nextAt;

    /**
     * @param now the calendar tick now
     * @param intervalTicks the ticks between steps
     * @param maxTicks the longest step to hand out. A jump of the calendar is cut to this
     * @param phase the ticks before the first step after the start, to spread many devices over the interval
     * @return the ticks the step is to cover, or 0 if it is not time yet. The first call, and one after the calendar was set back, only start the count
     */
    public long due(long now, int intervalTicks, long maxTicks, int phase)
    {
        if (last < 0L || now < last)
        {
            last = now;
            nextAt = now + phase;
            return 0L;
        }
        if (now < nextAt)
        {
            return 0L;
        }
        final long elapsed = Math.min(now - last, maxTicks);
        last = now;
        nextAt = now + intervalTicks;
        return elapsed;
    }

    /** The count starts again from the next call. */
    public void reset()
    {
        last = -1L;
    }
}
