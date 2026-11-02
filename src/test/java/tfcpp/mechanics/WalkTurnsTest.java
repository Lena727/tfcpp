package tfcpp.mechanics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WalkTurnsTest
{
    private static final int BUDGET = 2048, REACH = 512;

    private static final class Fan
    {
        boolean wantsToWalk = true;
    }

    /** Runs the tracker's rule for one tick: the refused first, then the fans whose slot it is. */
    private static void tick(WalkTurns<Fan> turns, List<Fan> inSlot, Set<Fan> walked)
    {
        turns.startTick(BUDGET);
        final List<Fan> refused = turns.takeWaiting();
        final Set<Fan> served = new HashSet<>(refused);
        for (Fan fan : refused)
        {
            work(turns, fan, walked);
        }
        for (Fan fan : inSlot)
        {
            if (!served.contains(fan))
            {
                work(turns, fan, walked);
            }
        }
    }

    private static void work(WalkTurns<Fan> turns, Fan fan, Set<Fan> walked)
    {
        if (fan.wantsToWalk && turns.request(fan, REACH))
        {
            walked.add(fan);
        }
    }

    @Test
    void manyFansInOneSlotAllGetATurnWithinABoundedTime()
    {
        final WalkTurns<Fan> turns = new WalkTurns<>();
        final List<Fan> fans = new ArrayList<>();
        for (int i = 0; i < 13; i++)
        {
            fans.add(new Fan());
        }

        // All thirteen come up in the same tick of the second. A tick pays for four walks.
        final Set<Fan> walked = new HashSet<>();
        tick(turns, fans, walked);
        assertEquals(BUDGET / REACH, walked.size());
        final int ticksNeeded = (int) Math.ceil(13.0 / (BUDGET / REACH));
        for (int later = 1; later < ticksNeeded; later++)
        {
            tick(turns, List.of(), walked);
        }

        assertEquals(13, walked.size(), "Every fan walked within " + ticksNeeded + " ticks, and the last was not left for ever");
        assertEquals(0, turns.waitingCount());
    }

    @Test
    void theFanRefusedFirstIsServedBeforeOnesWhoseSecondHasJustCome()
    {
        final WalkTurns<Fan> turns = new WalkTurns<>();
        final List<Fan> early = new ArrayList<>(), late = new ArrayList<>();
        for (int i = 0; i < 6; i++)
        {
            early.add(new Fan());
            late.add(new Fan());
        }

        final Set<Fan> first = new HashSet<>();
        tick(turns, early, first); // Four walk, two are refused
        final Set<Fan> second = new HashSet<>();
        tick(turns, late, second); // The two refused go first, then two of the new ones

        assertTrue(second.contains(early.get(4)) && second.contains(early.get(5)), "The refused are served first");
        assertEquals(4, second.size());
    }

    @Test
    void aFanThatDoesNotWalkPaysNothing()
    {
        // The walk is paid for only when it is made: a fan with clean air or standing still never asks
        final WalkTurns<Fan> turns = new WalkTurns<>();
        final List<Fan> fans = new ArrayList<>();
        for (int i = 0; i < 20; i++)
        {
            final Fan fan = new Fan();
            fan.wantsToWalk = i == 19;
            fans.add(fan);
        }
        final Set<Fan> walked = new HashSet<>();
        tick(turns, fans, walked);

        assertEquals(Set.of(fans.get(19)), walked, "The twentieth fan walks at once, behind nineteen that did not need to");
        assertEquals(BUDGET - REACH, turns.cellsLeft());
    }

    @Test
    void aFanThatWentAwayIsNotServed()
    {
        final WalkTurns<Fan> turns = new WalkTurns<>();
        final Fan fan = new Fan();
        turns.startTick(0);
        assertFalse(turns.request(fan, REACH));
        assertTrue(turns.isWaiting(fan));

        turns.forget(fan);

        assertEquals(List.of(), turns.takeWaiting());
    }

    @Test
    void workersAreComparedByIdentity()
    {
        final WalkTurns<Object> turns = new WalkTurns<>();
        final Object a = new String("same"), b = new String("same");
        turns.startTick(0);
        turns.request(a, 1);
        turns.request(b, 1);
        assertEquals(2, turns.waitingCount());
    }
}
