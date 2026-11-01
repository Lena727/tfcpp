package tfcpp.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TickProfileTest
{
    private static final long MILLI = 1_000_000L;

    private static TickProfile twoSections()
    {
        return new TickProfile("a", "b");
    }

    private static void tick(TickProfile profile, long aNanos, long bNanos)
    {
        profile.record(0, aNanos);
        profile.record(1, bNanos);
        profile.endTick();
    }

    @Test
    void meanIsTheAverageOverTheTicksSeenSoFar()
    {
        final TickProfile profile = twoSections();
        tick(profile, 2 * MILLI, 0);
        tick(profile, 4 * MILLI, 0);
        tick(profile, 6 * MILLI, 0);

        assertEquals(3, profile.ticks());
        assertEquals(4.0, profile.meanMillis(0), 1e-9, "A window that is not full is averaged over what it holds, and not diluted by empty ticks");
    }

    @Test
    void severalRecordsInOneTickAddUp()
    {
        final TickProfile profile = twoSections();
        profile.record(1, MILLI);
        profile.record(1, 2 * MILLI);
        profile.record(1, 3 * MILLI);
        profile.endTick();

        assertEquals(1, profile.ticks());
        assertEquals(6.0, profile.maxMillis(1), 1e-9, "Breathing for each player is one record each, and one cost for the tick");
    }

    @Test
    void aSectionNotRecordedInATickCountsAsZero()
    {
        final TickProfile profile = twoSections();
        for (int i = 0; i < 10; i++)
        {
            profile.record(0, MILLI);
            if (i == 0)
            {
                profile.record(1, 10 * MILLI);
            }
            profile.endTick();
        }

        assertEquals(1.0, profile.meanMillis(1), 1e-9, "10 ms in one tick out of ten is 1 ms on average");
        assertEquals(0.0, profile.percentileMillis(1, 0.5), 1e-9, "Most ticks cost nothing");
        assertEquals(10.0, profile.maxMillis(1), 1e-9);
    }

    @Test
    void percentileIsTheNearestRank()
    {
        final TickProfile profile = twoSections();
        // 1 ms, 2 ms, ... 100 ms, in a shuffled order that the sort has to undo
        for (int i = 0; i < 100; i++)
        {
            tick(profile, ((i * 37) % 100 + 1) * MILLI, 0);
        }

        assertEquals(99.0, profile.percentileMillis(0, 0.99), 1e-9, "The 99th of 100 ticks is the 99th slowest-from-the-bottom");
        assertEquals(50.0, profile.percentileMillis(0, 0.5), 1e-9);
        assertEquals(100.0, profile.percentileMillis(0, 1.0), 1e-9);
        assertEquals(1.0, profile.percentileMillis(0, 0.001), 1e-9, "Never below the first rank");
    }

    @Test
    void sixStallsInAFullWindowDoNotMoveTheNinetyNinthPercentileButASeventhDoes()
    {
        final TickProfile profile = twoSections();
        for (int i = 0; i < TickProfile.WINDOW_TICKS; i++)
        {
            tick(profile, i < 6 ? 100 * MILLI : MILLI, 0);
        }
        assertEquals(1.0, profile.percentileMillis(0, 0.99), 1e-9, "1 % of 600 ticks is six");

        final TickProfile worse = twoSections();
        for (int i = 0; i < TickProfile.WINDOW_TICKS; i++)
        {
            tick(worse, i < 7 ? 100 * MILLI : MILLI, 0);
        }
        assertEquals(100.0, worse.percentileMillis(0, 0.99), 1e-9);
    }

    @Test
    void maxIsTheSlowestTickInTheWindow()
    {
        final TickProfile profile = twoSections();
        tick(profile, MILLI, 0);
        tick(profile, 7 * MILLI, 0);
        tick(profile, 3 * MILLI, 0);

        assertEquals(7.0, profile.maxMillis(0), 1e-9);
    }

    @Test
    void theWindowRollsOverAndOldTicksFallOut()
    {
        final TickProfile profile = twoSections();
        tick(profile, 500 * MILLI, 0); // A stall at the very start
        for (int i = 0; i < TickProfile.WINDOW_TICKS - 1; i++)
        {
            tick(profile, MILLI, 0);
        }
        assertEquals(TickProfile.WINDOW_TICKS, profile.ticks());
        assertEquals(500.0, profile.maxMillis(0), 1e-9, "Still the oldest tick of a full window");

        tick(profile, MILLI, 0);

        assertEquals(TickProfile.WINDOW_TICKS, profile.ticks(), "The window does not grow");
        assertEquals(1.0, profile.maxMillis(0), 1e-9, "The stall has left the window");
        assertEquals(1.0, profile.meanMillis(0), 1e-9);
        assertEquals(500.0, profile.peakMillis(0), 1e-9, "The peak is not forgotten with the window: a stall is wanted even after it has rolled out");
    }

    @Test
    void theTotalIsTheSumOfTheSectionsTickByTick()
    {
        final TickProfile profile = twoSections();
        tick(profile, 4 * MILLI, 0);
        tick(profile, 0, 4 * MILLI);
        tick(profile, MILLI, MILLI);

        assertEquals(10.0 / 3.0, profile.meanMillis(TickProfile.TOTAL), 1e-9, "Totals of 4, 4 and 2 ms");
        assertEquals(4.0, profile.maxMillis(TickProfile.TOTAL), 1e-9, "The total is as slow as its slowest tick, and not the sum of each section's slowest, which would be 8 ms");
        assertEquals(4.0, profile.percentileMillis(TickProfile.TOTAL, 0.99), 1e-9);
    }

    @Test
    void peaksCanBeResetToWatchOneEvent()
    {
        final TickProfile profile = twoSections();
        tick(profile, 9 * MILLI, 0);
        profile.resetPeaks();
        tick(profile, 2 * MILLI, 0);

        assertEquals(2.0, profile.peakMillis(0), 1e-9);
        assertEquals(9.0, profile.maxMillis(0), 1e-9, "The window itself is not touched");
    }

    @Test
    void theLastTickIsTheOneMostRecentlyClosedEvenAfterTheWindowWraps()
    {
        final TickProfile profile = twoSections();
        assertEquals(0.0, profile.lastMillis(0), "Nothing closed yet");

        tick(profile, 3 * MILLI, MILLI);
        assertEquals(3.0, profile.lastMillis(0), 1e-9);
        assertEquals(4.0, profile.lastMillis(TickProfile.TOTAL), 1e-9);

        for (int i = 0; i < TickProfile.WINDOW_TICKS; i++)
        {
            tick(profile, (i + 1) * 1000L, 0);
        }
        assertEquals(TickProfile.WINDOW_TICKS * 1000L / 1e6, profile.lastMillis(0), 1e-9, "The write position has gone all the way round and back");
    }

    @Test
    void anEmptyProfileReportsZeroAndClearStartsOver()
    {
        final TickProfile profile = twoSections();
        assertEquals(0.0, profile.meanMillis(0));
        assertEquals(0.0, profile.percentileMillis(0, 0.99));
        assertEquals(0.0, profile.maxMillis(TickProfile.TOTAL));

        tick(profile, MILLI, MILLI);
        profile.record(0, MILLI); // Part way through a tick
        profile.clear();
        profile.endTick();

        assertEquals(1, profile.ticks());
        assertEquals(0.0, profile.maxMillis(TickProfile.TOTAL), "Nothing of the old window, nor of the half-recorded tick, survives");
    }

    @Test
    void sectionsKeepTheirNames()
    {
        final TickProfile profile = twoSections();
        assertEquals(2, profile.sectionCount());
        assertEquals("a", profile.name(0));
        assertEquals("b", profile.name(1));
    }
}
