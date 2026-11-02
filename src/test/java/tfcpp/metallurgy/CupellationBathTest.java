package tfcpp.metallurgy;

import org.junit.jupiter.api.Test;

import tfcpp.metallurgy.CupellationBath.Step;

import static org.junit.jupiter.api.Assertions.*;

class CupellationBathTest
{
    private static final float HOT = 1000f;
    private static final double MOLES_PER_MILLIBUCKET = CupellationBath.MOLES_LEAD_PER_MILLIBUCKET;

    /** A full charge of work lead, with enough ash for all of it. */
    private static CupellationBath fullCharge()
    {
        final CupellationBath bath = new CupellationBath();
        bath.add(1000);
        bath.addAsh();
        bath.addAsh();
        return bath;
    }

    /** What a whole blow gave, summed over every step. */
    private record Run(double fume, double oxygen, int powders, double leftover)
    {
        double litharge()
        {
            return powders * (double) CupellationBath.LITHARGE_MILLIBUCKETS_PER_POWDER + leftover;
        }
    }

    /** Blows until the lead is gone. The guard makes a broken bath fail rather than hang. */
    private static Run blowToEnd(CupellationBath bath, float seconds)
    {
        double fume = 0.0, oxygen = 0.0;
        int powders = 0;
        int guard = 0;
        while (bath.lead > 0f && guard++ < 100000)
        {
            final Step step = bath.step(seconds, HOT);
            fume += step.fumeMoles();
            oxygen += step.oxygenMoles();
            powders += step.litharge();
        }
        return new Run(fume, oxygen, powders, bath.oxidisedForLitharge);
    }

    @Test
    void addStopsAtCapacityAndReturnsWhatItTook()
    {
        final CupellationBath bath = new CupellationBath();
        assertEquals(800, bath.add(800));
        assertEquals(800f, bath.lead, 1e-4f);
        assertEquals(200, bath.add(500), "Only 200 mB fit below the capacity of 1000");
        assertEquals(1000f, bath.lead, 1e-4f);
        assertEquals(0, bath.add(100), "A full hearth takes nothing");
        assertEquals(1000f, bath.lead, 1e-4f);
    }

    @Test
    void silverComesWithTheLeadAtThreePointTwoFourPerThousand()
    {
        final CupellationBath bath = new CupellationBath();
        bath.add(1000);
        assertEquals(3.24f, bath.silver, 0.001f);
    }

    @Test
    void aColdHearthDoesNothing()
    {
        final CupellationBath bath = fullCharge();
        assertSame(Step.NONE, bath.step(10f, 959f));
        assertEquals(1000f, bath.lead, 0f);
        assertEquals(1000f, bath.ashCapacity, 0f);
        assertEquals(0f, bath.oxidisedForLitharge, 0f);
        assertEquals(3.24f, bath.silver, 0.001f);
    }

    @Test
    void withoutAshNothingHappens()
    {
        final CupellationBath bath = new CupellationBath();
        bath.add(1000);
        assertTrue(bath.needsAsh());
        assertSame(Step.NONE, bath.step(10f, HOT));
        assertEquals(1000f, bath.lead, 0f);
        assertTrue(bath.needsAsh());
    }

    @Test
    void tenSecondsAtOneThousandDegreesOxidisesTenMillibuckets()
    {
        final CupellationBath bath = fullCharge();
        final Step step = bath.step(10f, HOT);
        assertEquals(10f, step.leadOxidised(), 1e-5f);
        assertEquals(990f, bath.lead, 1e-4f);
    }

    @Test
    void onePowderOfAshStopsTheBlowAfterFiveHundredMillibuckets()
    {
        final CupellationBath bath = new CupellationBath();
        bath.add(1000);
        bath.addAsh();
        final Step step = bath.step(1000f, HOT);
        assertEquals(500f, step.leadOxidised(), 1e-4f);
        assertEquals(500f, bath.lead, 1e-4f);
        assertTrue(bath.needsAsh());
        assertSame(Step.NONE, bath.step(1000f, HOT), "The floor is full, so nothing more oxidises");
        assertEquals(500f, bath.lead, 1e-4f);
    }

    @Test
    void aFullRunBalancesFumeAndLitharge()
    {
        final CupellationBath bath = fullCharge();
        final Run run = blowToEnd(bath, 1f);
        assertEquals(0f, bath.lead, 0f);
        final double fumeExpected = 0.03 * 1000 * MOLES_PER_MILLIBUCKET;
        assertEquals(fumeExpected, run.fume(), fumeExpected * 0.005, "Three per cent of the lead leaves as fume");
        assertEquals(970.0, run.litharge(), 970.0 * 0.005, "The rest of the lead is litharge");
        assertEquals(212, run.powders(), "970 mB of litharge is 212 powders of 4.57 mB, with the rest left over");
    }

    @Test
    void stepSizeDoesNotChangeTheOutcome()
    {
        final Run fine = blowToEnd(fullCharge(), 1f);
        final Run coarse = blowToEnd(fullCharge(), 7f);
        assertEquals(fine.fume(), coarse.fume(), fine.fume() * 0.005);
        assertEquals(fine.oxygen(), coarse.oxygen(), fine.oxygen() * 0.005);
        assertEquals(fine.litharge(), coarse.litharge(), fine.litharge() * 0.005);
    }

    @Test
    void oxidationNeverTouchesTheSilver()
    {
        final CupellationBath bath = fullCharge();
        final float silver = bath.silver;
        for (int i = 0; i < 300; i++)
        {
            bath.step(1f, HOT);
        }
        assertEquals(700f, bath.lead, 1e-4f);
        assertEquals(silver, bath.silver, 0f, "Silver does not oxidise");
    }

    @Test
    void finishedWhenTheLeadIsGoneAndTapGivesTheWholeSilver()
    {
        final CupellationBath bath = fullCharge();
        assertFalse(bath.isFinished());
        blowToEnd(bath, 1f);
        assertTrue(bath.isFinished());
        assertEquals(3, bath.tapSilver());
        assertEquals(0.24f, bath.silver, 0.001f, "The fraction stays in the hearth");
    }

    @Test
    void oxygenIsHalfTheMolesOfLead()
    {
        final Run run = blowToEnd(fullCharge(), 1f);
        final double leadMoles = 1000 * MOLES_PER_MILLIBUCKET;
        assertEquals(0.5 * leadMoles, run.oxygen(), 0.5 * leadMoles * 0.001);
    }
}
