package tfcpp.chemistry;

import org.junit.jupiter.api.Test;
import tfcpp.pneumatics.gas.Gas;

import static org.junit.jupiter.api.Assertions.*;

class CarbonizationBatchTest
{
    private static final CarbonizationTable.Row COAL = CarbonizationTable.COAL;

    private static CarbonizationBatch batch(CarbonizationTable.Row row, int pieces)
    {
        return new CarbonizationBatch(row.yield(), pieces, 0.0);
    }

    /** Adds the steps of a whole run together: gases, then solid, tar and liquor. */
    private static double[] totals(CarbonizationTable.Row row, int pieces, double stepSeconds)
    {
        final CarbonizationBatch batch = batch(row, pieces);
        final double[] total = new double[Gas.COUNT + 3];
        while (!batch.isComplete())
        {
            final CarbonizationBatch.Step step = batch.advance(stepSeconds, true);
            for (int i = 0; i < Gas.COUNT; i++)
            {
                total[i] += step.gas()[i];
            }
            total[Gas.COUNT] += step.solid();
            total[Gas.COUNT + 1] += step.tar();
            total[Gas.COUNT + 2] += step.liquor();
        }
        return total;
    }

    @Test
    void halfTheTimeGivesHalfOfEverything()
    {
        final CarbonizationBatch batch = batch(COAL, 10);
        final CarbonizationBatch.Step half = batch.advance(COAL.seconds() / 2.0, true);

        assertEquals(10 * 0.70 / 2.0, half.solid(), 1e-9);
        assertEquals(10 * 0.10 / 2.0, half.tar(), 1e-6);
        assertEquals(10 * 0.30 / 2.0, half.liquor(), 1e-6);
        assertEquals(10 * 21.0 / 2.0, half.gas()[Gas.HYDROGEN.ordinal()], 1e-6);
        assertEquals(0.5, batch.fraction(), 1e-12);
    }

    @Test
    void nothingChangesWhileColdAndTheBatchIsNotStarted()
    {
        final CarbonizationBatch batch = batch(COAL, 64);
        final CarbonizationBatch.Step step = batch.advance(600.0, false);

        assertTrue(step.isEmpty());
        assertEquals(0.0, batch.progress());
        assertFalse(batch.started());
    }

    @Test
    void aColdSpellKeepsTheProgressMade()
    {
        final CarbonizationBatch batch = batch(COAL, 5);
        batch.advance(100.0, true);
        assertTrue(batch.started());
        batch.advance(500.0, false);
        batch.advance(100.0, true);
        assertEquals(200.0, batch.progress(), 1e-9);
    }

    @Test
    void aBatchNeverGivesMoreThanThePerPieceTableWhateverTheSteps()
    {
        for (double stepSeconds : new double[] {1.0, 7.3, 60.0, 1e6})
        {
            final double[] total = totals(COAL, 64, stepSeconds);
            assertEquals(64 * 0.70, total[Gas.COUNT], 1e-9, "solid, step " + stepSeconds);
            assertEquals(64 * 0.10, total[Gas.COUNT + 1], 1e-6, "tar, step " + stepSeconds);
            assertEquals(64 * 0.30, total[Gas.COUNT + 2], 1e-6, "liquor, step " + stepSeconds);
            assertEquals(64 * 21.0, total[Gas.HYDROGEN.ordinal()], 1e-6, "hydrogen, step " + stepSeconds);
            assertEquals(64 * 0.2, total[Gas.HYDROGEN_SULFIDE.ordinal()], 1e-6, "hydrogen sulfide, step " + stepSeconds);
        }
    }

    @Test
    void afterTheEndThereIsNothingMore()
    {
        final CarbonizationBatch batch = batch(CarbonizationTable.WOOD, 3);
        batch.advance(1e9, true);
        assertTrue(batch.isComplete());
        assertTrue(batch.advance(60.0, true).isEmpty());
    }

    @Test
    void gasesTheTableDoesNotMentionAreNeverMade()
    {
        final double[] total = totals(CarbonizationTable.WOOD, 8, 60.0);
        assertEquals(0.0, total[Gas.AMMONIA.ordinal()]);
        assertEquals(0.0, total[Gas.SULFUR_DIOXIDE.ordinal()]);
        assertEquals(0.0, total[Gas.HYDROGEN_SULFIDE.ordinal()]);
    }

    @Test
    void savedProgressContinuesWhereItStopped()
    {
        final CarbonizationBatch first = batch(COAL, 4);
        first.advance(300.0, true);
        final CarbonizationBatch loaded = new CarbonizationBatch(COAL.yield(), 4, first.progress());
        assertEquals(first.peek(60.0, true).solid(), loaded.peek(60.0, true).solid(), 1e-12);
    }

    @Test
    void sevenTenthsOfAPieceOfCokeAccumulatesInsteadOfRoundingEachBatch()
    {
        final double solidPerBatch = totals(COAL, 5, 60.0)[Gas.COUNT]; // Five pieces of coal: 3.5 of coke
        assertEquals(3.5, solidPerBatch, 1e-9);
        final Remainder remainder = new Remainder();
        int made = 0;
        for (int batchNumber = 0; batchNumber < 4; batchNumber++)
        {
            remainder.add(solidPerBatch);
            final int whole = remainder.whole();
            remainder.take(whole);
            made += whole;
            assertTrue(remainder.pending() >= 0.0 && remainder.pending() < 1.0);
        }
        assertEquals(14, made); // 4 x 3.5, where rounding each batch down would have given 12
        assertEquals(0.0, remainder.pending(), 1e-9);
    }

    @Test
    void firstBatchLeavesHalfBehind()
    {
        final Remainder remainder = new Remainder();
        remainder.add(5 * 0.70);
        assertEquals(3, remainder.whole());
        remainder.take(3);
        assertEquals(0.5, remainder.pending(), 1e-9);
    }

    @Test
    void tenTimesATenthIsAWholeMillibucketDespiteFloatNoise()
    {
        final Remainder remainder = new Remainder();
        for (int i = 0; i < 10; i++)
        {
            remainder.add(0.1f);
        }
        assertEquals(1, remainder.whole());
    }

    @Test
    void aRemainderNeverTakesMoreThanItHolds()
    {
        final Remainder remainder = new Remainder(0.4);
        remainder.take(3);
        assertEquals(0.0, remainder.pending());
    }

    @Test
    void noMaterialGivesMoreMassThanItTakes()
    {
        for (CarbonizationTable.Row row : CarbonizationTable.ROWS)
        {
            assertTrue(row.outputKilograms() <= row.pieceKilograms(), row.name() + " gives " + row.outputKilograms() + " kg from " + row.pieceKilograms() + " kg");
        }
    }

    @Test
    void theGasMassComesFromTheMolarMassesOfTheGases()
    {
        // 21 H2, 14 CH4, 3 CO, 1 CO2, 2 N2, 0.3 NH3 and 0.2 H2S from a piece of coal
        assertEquals(0.4629, COAL.gasKilograms(), 1e-3);
    }
}
