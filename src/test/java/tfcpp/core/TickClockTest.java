package tfcpp.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TickClockTest
{
    @Test
    void aCalendarThatStandsStillNeverComesDue()
    {
        final TickClock clock = new TickClock();
        assertEquals(0L, clock.due(1000L, 20, 100, 0));
        for (int gameTick = 0; gameTick < 10_000; gameTick++)
        {
            assertEquals(0L, clock.due(1000L, 20, 100, 0), "The calendar is at 1000 for ever, and so is the device");
        }
    }

    @Test
    void itComesDueOnceEveryIntervalOfTheCalendar()
    {
        final TickClock clock = new TickClock();
        int steps = 0;
        long covered = 0;
        for (long now = 500; now < 500 + 200; now++)
        {
            final long elapsed = clock.due(now, 20, 100, 0);
            if (elapsed > 0)
            {
                steps++;
                covered += elapsed;
            }
        }
        assertTrue(steps >= 9 && steps <= 10, "About ten steps in two hundred ticks: " + steps);
        assertTrue(covered >= 170 && covered <= 200, "The steps add up to the ticks that passed: " + covered);
    }

    @Test
    void aLongJumpIsCutToTheCap()
    {
        final TickClock clock = new TickClock();
        clock.due(0L, 20, 100, 0);
        assertEquals(100L, clock.due(24_000L, 20, 100, 0), "A night's sleep is one step of the longest length");
        assertEquals(0L, clock.due(24_001L, 20, 100, 0));
    }

    @Test
    void aCalendarSetBackOnlyStartsTheCountAgain()
    {
        final TickClock clock = new TickClock();
        clock.due(5_000L, 20, 100, 20);
        assertEquals(0L, clock.due(100L, 20, 100, 20));
        assertEquals(0L, clock.due(110L, 20, 100, 20));
        assertEquals(20L, clock.due(120L, 20, 100, 20), "Counted from the new time, not from the old one that was far ahead");
    }

    @Test
    void thePhaseSpreadsDevicesOverTheInterval()
    {
        final TickClock early = new TickClock(), late = new TickClock();
        early.due(0L, 20, 100, 3);
        late.due(0L, 20, 100, 17);
        long earlyAt = -1, lateAt = -1;
        for (long now = 1; now < 40; now++)
        {
            if (earlyAt < 0 && early.due(now, 20, 100, 3) > 0) earlyAt = now;
            if (lateAt < 0 && late.due(now, 20, 100, 17) > 0) lateAt = now;
        }
        assertEquals(3L, earlyAt);
        assertEquals(17L, lateAt);
    }
}
