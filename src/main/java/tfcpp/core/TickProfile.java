package tfcpp.core;

import java.util.Arrays;

/**
 * How long each part of the mod takes in a server tick, over the last {@link #WINDOW_TICKS} ticks.
 * <p>
 * The point is to see the cost of a big base without a profiler attached, and to see it the way a player does: the mean says what the server pays all the time, while the 99th percentile and the maximum say how often and how badly a tick stalls. A mean alone hides exactly the stalls (a pipe network rebuilding, a second-boundary poll of every fire) that make a server lag.
 * <p>
 * Pure Java, with no Minecraft types, so that the arithmetic is tested without a server. Recording allocates nothing: every series is a fixed array that is written round and round. Reporting does allocate, as it sorts a copy, and runs only when someone asks.
 * <p>
 * Not thread safe. Everything it measures runs on the server thread.
 */
public final class TickProfile
{
    /** A minute of ticks: long enough that one stall does not dominate the mean, short enough to follow a change in load. */
    public static final int WINDOW_TICKS = 600;
    /** The series that adds up every section, tick by tick. A sum of the sections' means is the same, but a sum of their percentiles is not the percentile of the sum. */
    public static final int TOTAL = -1;

    private static final double NANOS_PER_MILLI = 1e6;
    private static final double RANK_TOLERANCE = 1e-9;

    private final String[] names;
    /** Nanoseconds per tick, one row for each section and a last row for the total. */
    private final long[][] window;
    /** What has been recorded in the tick that is still running. */
    private final long[] pending;
    /** The worst tick of each series since {@link #resetPeaks()}, which can be a good deal worse than anything left in the window. */
    private final long[] peaks;
    private int next;
    private int filled;

    public TickProfile(String... names)
    {
        this.names = names.clone();
        this.window = new long[names.length + 1][WINDOW_TICKS];
        this.pending = new long[names.length];
        this.peaks = new long[names.length + 1];
    }

    public int sectionCount()
    {
        return names.length;
    }

    public String name(int section)
    {
        return names[section];
    }

    /** Adds time to a section of the tick that is running. Several calls in one tick add up, which is how a section that runs once for each player is counted. */
    public void record(int section, long nanos)
    {
        pending[section] += nanos;
    }

    /** Closes the running tick. A section that recorded nothing in it counts as zero for that tick, as it really cost nothing. */
    public void endTick()
    {
        long sum = 0L;
        for (int section = 0; section < names.length; section++)
        {
            final long nanos = pending[section];
            window[section][next] = nanos;
            peaks[section] = Math.max(peaks[section], nanos);
            pending[section] = 0L;
            sum += nanos;
        }
        window[names.length][next] = sum;
        peaks[names.length] = Math.max(peaks[names.length], sum);

        next = (next + 1) % WINDOW_TICKS;
        if (filled < WINDOW_TICKS)
        {
            filled++;
        }
    }

    /** @return how many ticks the figures cover, which is less than the window until it has filled once */
    public int ticks()
    {
        return filled;
    }

    public double meanMillis(int section)
    {
        if (filled == 0)
        {
            return 0.0;
        }
        long sum = 0L;
        final long[] series = series(section);
        for (int i = 0; i < filled; i++)
        {
            sum += series[i];
        }
        return sum / (double) filled / NANOS_PER_MILLI;
    }

    /**
     * Nearest-rank percentile: the smallest tick that at least this share of the window is no slower than. With 600 ticks the 99th is the seventh slowest, so six stalls in a minute do not show in it and a seventh does.
     *
     * @param share between 0 (exclusive) and 1
     */
    public double percentileMillis(int section, double share)
    {
        if (filled == 0)
        {
            return 0.0;
        }
        final long[] sorted = Arrays.copyOf(series(section), filled);
        Arrays.sort(sorted);
        // The product of a share and a count that should be whole can come out a hair over it, which would pick the next tick up
        final int rank = (int) Math.ceil(share * filled - RANK_TOLERANCE);
        return sorted[Math.clamp(rank - 1, 0, filled - 1)] / NANOS_PER_MILLI;
    }

    public double maxMillis(int section)
    {
        long max = 0L;
        final long[] series = series(section);
        for (int i = 0; i < filled; i++)
        {
            max = Math.max(max, series[i]);
        }
        return max / NANOS_PER_MILLI;
    }

    /** @return the most recent tick that has been closed, which is what to read to see what one particular tick cost */
    public double lastMillis(int section)
    {
        if (filled == 0)
        {
            return 0.0;
        }
        return series(section)[Math.floorMod(next - 1, WINDOW_TICKS)] / NANOS_PER_MILLI;
    }

    /** @return the slowest tick of this series since the last {@link #resetPeaks()}, whether or not it is still in the window */
    public double peakMillis(int section)
    {
        return peaks[index(section)] / NANOS_PER_MILLI;
    }

    public void resetPeaks()
    {
        Arrays.fill(peaks, 0L);
    }

    /** Forgets everything, as at the start of a new measurement. */
    public void clear()
    {
        for (long[] series : window)
        {
            Arrays.fill(series, 0L);
        }
        Arrays.fill(pending, 0L);
        resetPeaks();
        next = 0;
        filled = 0;
    }

    private int index(int section)
    {
        return section == TOTAL ? names.length : section;
    }

    /** The window is written round and round, but the order of ticks does not matter to any figure, so the first {@code filled} entries are all of them. */
    private long[] series(int section)
    {
        return window[index(section)];
    }
}
