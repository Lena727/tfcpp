package tfcpp.metallurgy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TowerCondensationTest
{
    private static final float MOLES_PER_MILLIBUCKET = Sulfide.CINNABAR.metalMoles(1f);

    /** Gas left after this many towers have each had it for this long, one after another, as the ports of a network are stepped. */
    private static float afterTowers(float moles, int towers, float seconds)
    {
        float left = moles;
        for (int i = 0; i < towers; i++)
        {
            left -= TowerCondensation.dust(left, seconds);
        }
        return left;
    }

    @Test
    void aTowerCatchesAboutAnEighteenthOfTheFumeInASecond()
    {
        final float share = TowerCondensation.fraction(1f);
        assertEquals(1.0 - Math.exp(-0.05), share, 1e-6, "1 - exp(-0.05 t) for t = 1");
        assertTrue(share > 0.048f && share < 0.05f, "Just under 5%, got " + share);
        assertEquals(0f, TowerCondensation.fraction(0f), "No time, nothing caught");
        assertEquals(0f, TowerCondensation.fraction(-3f), "A calendar run backwards catches nothing");
    }

    @Test
    void theStepLengthDoesNotChangeWhatIsCaught()
    {
        final float oneStep = TowerCondensation.dust(10f, 60f);
        float steps = 10f;
        for (int i = 0; i < 60; i++)
        {
            steps -= TowerCondensation.dust(steps, 1f);
        }
        assertEquals(10f - oneStep, steps, 1e-3f, "Sixty one-second steps are one sixty-second step");

        float coarse = 10f;
        for (int i = 0; i < 6; i++)
        {
            coarse -= TowerCondensation.dust(coarse, 10f);
        }
        assertEquals(steps, coarse, 1e-3f, "And so are six steps of ten seconds");
    }

    @Test
    void nTowersCatchNTimesAsFast()
    {
        for (int towers = 1; towers <= 6; towers++)
        {
            final float left = afterTowers(10f, towers, 1f);
            final float expected = (float) (10.0 * Math.exp(-0.05 * towers));
            assertEquals(expected, left, 1e-4f, towers + " towers leave exp(-0.05 N) of the fume");
            // The fume dies away as exp(-rate t) with the rate N times a tower's: the rate read back from what is left is N times 0.05
            assertEquals(towers, -Math.log(left / 10.0) / TowerCondensation.CAPTURE_RATE, 5e-3, "N times the wall is N times the rate");
        }
        assertTrue(afterTowers(10f, 4, 1f) < afterTowers(10f, 1, 1f), "More towers leave less");
    }

    @Test
    void nothingIsCaughtThatIsNotThere()
    {
        assertEquals(0f, TowerCondensation.dust(0f, 1f));
        assertEquals(0f, TowerCondensation.dust(-1f, 1f));
        assertTrue(TowerCondensation.dust(3f, 1f) < 3f, "Never more than there is");
        assertTrue(TowerCondensation.dust(3f, 1e6f) <= 3f, "Even over a very long step");
    }

    @Test
    void dustSettlesOnlyBelowTwoHundredFiftyAndMercuryBelowOneHundred()
    {
        assertTrue(TowerCondensation.catchesDust(293f));
        assertTrue(TowerCondensation.catchesDust(522.9f));
        assertFalse(TowerCondensation.catchesDust(523f), "At 250 C the fume stays in the gas");
        assertFalse(TowerCondensation.catchesDust(900f));
        assertTrue(TowerCondensation.catchesMercury(372.9f));
        assertFalse(TowerCondensation.catchesMercury(373f), "At 100 C the mercury stays a vapour");
        assertFalse(TowerCondensation.catchesMercury(500f), "Between the two the oxides settle and the mercury goes on");
        assertTrue(TowerCondensation.catchesDust(373f) && !TowerCondensation.catchesMercury(373f));
    }

    @Test
    void mercuryTakesTheSameShareWhileThereIsRoom()
    {
        final float taken = TowerCondensation.mercury(2f, 1f, 0f, 100, MOLES_PER_MILLIBUCKET);
        assertEquals(2f * TowerCondensation.fraction(1f), taken, 1e-6f);
        assertEquals(0.0675f, MOLES_PER_MILLIBUCKET, 1e-3f, "A millibucket of mercury is 0.0675 mol");
    }

    @Test
    void aFullTankTakesNoMercuryAndAFractionOfAMillibucketIsCounted()
    {
        assertEquals(0f, TowerCondensation.mercury(5f, 1f, 0f, 0, MOLES_PER_MILLIBUCKET), "No room, no mercury");
        assertEquals(0f, TowerCondensation.mercury(5f, 1f, 0.5f, 0, MOLES_PER_MILLIBUCKET), "A fraction already counted does not make room");
        final float onlyHalf = TowerCondensation.mercury(50f, 1f, 0.5f, 1, MOLES_PER_MILLIBUCKET);
        assertEquals(0.5f * MOLES_PER_MILLIBUCKET, onlyHalf, 1e-6f, "One millibucket of room with half of one pending leaves half to take");
        assertEquals(0f, TowerCondensation.mercury(0f, 1f, 0f, 100, MOLES_PER_MILLIBUCKET));
    }

    @Test
    void aTowerFillsToOneHundredMillibucketsAndNoMoreAndLosesNoMercury()
    {
        float vapour = 100f * MOLES_PER_MILLIBUCKET * 3f; // Three tanks' worth
        final float start = vapour;
        float millibuckets = 0f;
        for (int second = 0; second < 5000; second++)
        {
            final float room = TowerCondensation.MERCURY_TANK_MILLIBUCKETS - (float) Math.floor(millibuckets);
            final float moles = TowerCondensation.mercury(vapour, 1f, millibuckets - (float) Math.floor(millibuckets), (int) room, MOLES_PER_MILLIBUCKET);
            vapour -= moles;
            millibuckets += moles / MOLES_PER_MILLIBUCKET;
            assertTrue(millibuckets <= 100.0001f, "Never past the tank, got " + millibuckets + " at " + second);
        }
        assertEquals(100f, millibuckets, 0.01f, "The tank ends full");
        assertEquals(start, vapour + millibuckets * MOLES_PER_MILLIBUCKET, 1e-2f, "What is not in the tank is still in the gas");
        assertTrue(vapour > 1.9f * 100f * MOLES_PER_MILLIBUCKET, "Two tanks' worth stays in the gas");
    }
}
