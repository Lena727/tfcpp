package tfcpp.health;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The course of the diseases over the calendar: incubation, illness, recovery, death and immunity. */
class DiseaseStateTest
{
    private static final long DAY = 24_000L, YEAR = 96 * DAY;

    /** A generator that always says the same number. */
    private record Fixed(double value) implements RandomGenerator
    {
        @Override
        public long nextLong()
        {
            return 0L;
        }

        @Override
        public double nextDouble()
        {
            return value;
        }
    }

    @Test
    void theTimesOfEachDiseaseAreInTheirRanges()
    {
        final SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 2000; i++)
        {
            final double[] dysentery = DiseaseState.durationsInDays(Disease.DYSENTERY, random), typhoid = DiseaseState.durationsInDays(Disease.TYPHOID, random);
            assertTrue(dysentery[0] >= 1.0 && dysentery[0] <= 3.0, "incubation 1 to 3 days");
            assertTrue(dysentery[1] >= 5.0 && dysentery[1] <= 7.0, "illness 5 to 7 days");
            assertEquals(0.0, dysentery[2], "no shedding after");
            assertTrue(typhoid[0] >= 7.0 && typhoid[0] <= 14.0, "incubation 7 to 14 days");
            assertTrue(typhoid[1] >= 21.0 && typhoid[1] <= 28.0, "illness 21 to 28 days");
            assertEquals(14.0, typhoid[2], "two weeks of shedding after");
        }
    }

    @Test
    void anInfectionRunsFromIncubationThroughIllnessToRecoveryByTheCalendarAlone()
    {
        final DiseaseState state = new DiseaseState();
        final long t0 = 1_000_000L;
        assertTrue(state.infect(Disease.TYPHOID, t0, new Fixed(0.5)));
        final DiseaseState.Course course = state.course(Disease.TYPHOID);
        assertNotNull(course);
        assertEquals(t0 + Math.round(10.5 * DAY), course.symptomsAt, "incubation of 7 + 7 * 0.5 days");
        assertEquals(course.symptomsAt + Math.round(24.5 * DAY), course.illnessEnds);
        assertEquals(course.illnessEnds + 14 * DAY, course.sheddingEnds);
        assertEquals(DiseaseState.Phase.INCUBATING, state.phase(Disease.TYPHOID, t0 + DAY));
        assertFalse(state.sheds(Disease.TYPHOID, t0 + DAY), "A person incubating does not shed");
        assertEquals(DiseaseState.Phase.ILL, state.phase(Disease.TYPHOID, course.symptomsAt));
        assertTrue(state.sheds(Disease.TYPHOID, course.symptomsAt + DAY), "Ill, and shedding");
        assertFalse(state.canCatch(Disease.TYPHOID, course.symptomsAt), "Cannot be infected twice");
        assertEquals(DiseaseState.Outcome.NONE, state.step(course.symptomsAt + 5 * DAY, 0.5, new Fixed(0.0), YEAR), "Nothing is decided before the illness ends");
        assertEquals(DiseaseState.Outcome.RECOVERED, state.step(course.illnessEnds, 0.5, new Fixed(0.99), YEAR));
        assertEquals(DiseaseState.Phase.RECOVERING, state.phase(Disease.TYPHOID, course.illnessEnds + DAY));
        assertTrue(state.sheds(Disease.TYPHOID, course.illnessEnds + 13 * DAY), "Sheds for two weeks after");
        assertFalse(state.sheds(Disease.TYPHOID, course.sheddingEnds));
        state.step(course.sheddingEnds, 0.5, new Fixed(0.99), YEAR);
        assertNull(state.course(Disease.TYPHOID), "Forgotten when it has run its course");
        assertTrue(state.isEmpty());
    }

    @Test
    void typhoidLeavesTwoYearsOfImmunityAndDysenteryNone()
    {
        final DiseaseState state = new DiseaseState();
        state.infect(Disease.TYPHOID, 0L, new Fixed(0.0));
        final DiseaseState.Course course = state.course(Disease.TYPHOID);
        assertNotNull(course);
        final long ends = course.illnessEnds, shed = course.sheddingEnds;
        state.step(shed + 1, 0.8, new Fixed(0.99), YEAR);
        assertTrue(state.isEmpty());
        assertEquals(ends + 2 * YEAR, state.immuneUntil(Disease.TYPHOID));
        assertFalse(state.canCatch(Disease.TYPHOID, shed + 1), "Immune");
        assertFalse(state.canCatch(Disease.TYPHOID, ends + 2 * YEAR - 1));
        assertTrue(state.canCatch(Disease.TYPHOID, ends + 2 * YEAR), "and not for ever");
        assertFalse(state.infect(Disease.TYPHOID, shed + 5, new Fixed(0.5)), "An immune person is not infected");

        state.infect(Disease.DYSENTERY, 0L, new Fixed(0.0));
        final long dysenteryEnds = state.course(Disease.DYSENTERY).illnessEnds;
        state.step(dysenteryEnds, 0.8, new Fixed(0.0), YEAR);
        assertTrue(state.isEmpty(), "Dysentery does not shed after it, so it is over at once");
        assertTrue(state.canCatch(Disease.DYSENTERY, dysenteryEnds), "and gives no immunity");
        assertEquals(0L, state.immuneUntil(Disease.DYSENTERY));
    }

    @Test
    void typhoidKillsWithTheChanceOfTheNutritionAndDysenteryDoesNotKillOnItsOwn()
    {
        assertEquals(0.075, DiseaseState.typhoidFatality(1.0), 1e-12);
        assertEquals(0.15, DiseaseState.typhoidFatality(0.5), 1e-12);
        assertEquals(0.225, DiseaseState.typhoidFatality(0.0), 1e-12);
        assertEquals(0.05, DiseaseState.typhoidFatality(1.5), 1e-12, "Never under 5 %");
        assertEquals(0.05, DiseaseState.typhoidFatality(2.0), 1e-12);
        assertEquals(0.25, DiseaseState.typhoidFatality(-1.0), 1e-12, "and never over 25 %");
        for (double nutrition = -1.0; nutrition <= 2.0; nutrition += 0.05)
        {
            final double p = DiseaseState.typhoidFatality(nutrition);
            assertTrue(p >= 0.05 && p <= 0.25, "Bounded at " + nutrition);
        }
        // The chance over many people
        final SplittableRandom random = new SplittableRandom(11);
        int died = 0;
        final int trials = 40_000;
        for (int i = 0; i < trials; i++)
        {
            final DiseaseState state = new DiseaseState();
            state.infect(Disease.TYPHOID, 0L, random);
            if (state.step(state.course(Disease.TYPHOID).illnessEnds, 0.5, random, YEAR) == DiseaseState.Outcome.DIED)
            {
                died++;
            }
        }
        assertEquals(0.15, died / (double) trials, 0.01);
        assertTrue(DiseaseState.typhoidFatality(0.8) < DiseaseState.typhoidFatality(0.4), "A better fed person dies less often");
        // Dysentery kills through the thirst of the game, and not by a roll
        final DiseaseState state = new DiseaseState();
        state.infect(Disease.DYSENTERY, 0L, random);
        assertEquals(DiseaseState.Outcome.RECOVERED, state.step(state.course(Disease.DYSENTERY).illnessEnds, 0.0, new Fixed(0.0), YEAR));
    }

    @Test
    void aPersonWhoWasAwayIsBroughtUpToTheCalendarAtOnce()
    {
        final DiseaseState state = new DiseaseState();
        state.infect(Disease.TYPHOID, 0L, new Fixed(0.5));
        // A year goes by while they are away: the illness has been decided and the shedding is over; immunity counts from the end of the illness
        final long ends = state.course(Disease.TYPHOID).illnessEnds;
        assertEquals(DiseaseState.Outcome.RECOVERED, state.step(YEAR, 0.9, new Fixed(0.99), YEAR));
        assertTrue(state.isEmpty());
        assertEquals(ends + 2 * YEAR, state.immuneUntil(Disease.TYPHOID));
    }

    @Test
    void aDeathThatHasNotLandedIsKeptAndNotDrawnAgainAndALivedThroughBlowIsARecovery()
    {
        final DiseaseState state = new DiseaseState();
        state.infect(Disease.TYPHOID, 0L, new Fixed(0.5));
        final long ends = state.course(Disease.TYPHOID).illnessEnds;
        assertEquals(DiseaseState.Outcome.DIED, state.step(ends, 0.5, new Fixed(0.0), YEAR), "the roll says death");
        assertFalse(state.course(Disease.TYPHOID).resolved, "but the course is not over until the blow has landed");
        assertEquals(0L, state.immuneUntil(Disease.TYPHOID));
        // The blow was turned aside (the invulnerability of a player who has just come in): the next step says the same, whatever it would roll now
        assertEquals(DiseaseState.Outcome.DIED, state.step(ends + 20, 0.5, new Fixed(0.99), YEAR), "the draw is kept");
        // The blow landed and the person lived (a totem): a recovery
        state.recover(Disease.TYPHOID, YEAR);
        assertTrue(state.course(Disease.TYPHOID).resolved);
        assertEquals(ends + 2 * YEAR, state.immuneUntil(Disease.TYPHOID));
        assertTrue(state.sheds(Disease.TYPHOID, ends + 1), "and the shedding of the tail goes on");
        assertEquals(DiseaseState.Outcome.NONE, state.step(ends + 40, 0.5, new Fixed(0.0), YEAR));
        assertTrue(state.course(Disease.TYPHOID) != null && state.course(Disease.TYPHOID).decided);
    }
}
