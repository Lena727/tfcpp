package tfcpp.chemistry;

import org.junit.jupiter.api.Test;
import tfcpp.pneumatics.gas.Gas;

import static org.junit.jupiter.api.Assertions.*;

class DistillationTest
{
    /** 8 powders and 0.23 L of acid, 120 to 200 C, 60 s: 0.175 L of distillate, 0.2 mol of gas, 8 powders left. */
    private static final Distillation.Recipe NITRIC = recipe(8, 0.23f, 0f, 120f, 200f, 60f, 0.175f, 0f, 0f, 0.2f, 8);
    private static final Distillation.Stock ENOUGH = new Distillation.Stock(0.23f, 0f, 8, 0f);
    private static final Distillation.Room ROOM = new Distillation.Room(25.0, 25.0, 64);

    private static Distillation.Recipe recipe(int items, float litres, float reagent, float min, float max, float seconds, float out, float residue, float water, float oxides, int byproducts)
    {
        final float[] gas = new float[Gas.COUNT];
        gas[Gas.NITROGEN_OXIDES.ordinal()] = oxides;
        return new Distillation.Recipe(items, litres, reagent, min, max, seconds, out, residue, water, gas, byproducts);
    }

    /** Runs a whole batch in steps of the given length and adds up everything that came out. */
    private static double[] run(Distillation.Recipe recipe, double stepSeconds, float temperature)
    {
        final Distillation batch = new Distillation(recipe, 0.0, false);
        final double[] total = new double[6]; // output, residue, oxides, byproducts, litres taken, items taken
        for (int guard = 0; guard < 100_000; guard++)
        {
            final Distillation.Step step = batch.advance(stepSeconds, temperature, ENOUGH, ROOM);
            assertTrue(step.ran());
            total[0] += step.output();
            total[1] += step.residue();
            total[2] += step.gas()[Gas.NITROGEN_OXIDES.ordinal()];
            total[3] += step.byproducts();
            total[4] += step.litres();
            total[5] += step.items();
            if (step.finished())
            {
                return total;
            }
        }
        fail("The batch never finished");
        return total;
    }

    @Test
    void nothingRunsBelowTheRangeAndTheBatchDoesNotStart()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);
        final Distillation.Step step = batch.advance(10.0, 119.9f, ENOUGH, ROOM);

        assertFalse(step.ran());
        assertEquals(Distillation.Reason.COLD, step.reason());
        assertFalse(batch.active(), "A cold flask must not take its inputs");
        assertEquals(0.0, step.litres());
        assertEquals(0, step.items());
    }

    @Test
    void nothingRunsAboveTheRange()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);
        final Distillation.Step step = batch.advance(10.0, 200.1f, ENOUGH, ROOM);

        assertFalse(step.ran());
        assertEquals(Distillation.Reason.HOT, step.reason());
        assertFalse(batch.active());
    }

    @Test
    void bothEndsOfTheRangeRun()
    {
        assertTrue(new Distillation(NITRIC, 0.0, false).advance(1.0, 120f, ENOUGH, ROOM).ran());
        assertTrue(new Distillation(NITRIC, 0.0, false).advance(1.0, 200f, ENOUGH, ROOM).ran());
    }

    @Test
    void aBatchInProgressKeepsItsPlaceWhenItGoesOutOfRange()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);
        batch.advance(30.0, 150f, ENOUGH, ROOM);
        assertEquals(30.0, batch.progress(), 1e-12);

        assertFalse(batch.advance(30.0, 100f, ENOUGH, ROOM).ran());
        assertFalse(batch.advance(30.0, 250f, ENOUGH, ROOM).ran());
        assertEquals(30.0, batch.progress(), 1e-12, "Time out of range must not count");
        assertTrue(batch.active());
    }

    @Test
    void halfTheTimeGivesHalfOfEverything()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);
        final Distillation.Step half = batch.advance(30.0, 150f, ENOUGH, ROOM);

        assertEquals(0.175 / 2.0, half.output(), 1e-6);
        assertEquals(0.2 / 2.0, half.gas()[Gas.NITROGEN_OXIDES.ordinal()], 1e-6);
        assertEquals(0, half.byproducts(), "The powder is left in the flask only when the batch ends");
        assertFalse(half.finished());
        assertEquals(0.5, batch.fraction(), 1e-12);
    }

    @Test
    void theInputsAreTakenAtTheStartAndOnlyThen()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);
        final Distillation.Step first = batch.advance(10.0, 150f, ENOUGH, ROOM);
        final Distillation.Step second = batch.advance(10.0, 150f, new Distillation.Stock(0f, 0f, 0, 0f), ROOM);

        assertEquals(0.23, first.litres(), 1e-6);
        assertEquals(8, first.items());
        assertTrue(second.ran(), "A started batch needs no more inputs");
        assertEquals(0.0, second.litres());
        assertEquals(0, second.items());
    }

    @Test
    void theWholeBatchGivesTheSameWhateverTheStepSize()
    {
        final double[] coarse = run(NITRIC, 60.0, 150f);
        for (double step : new double[] {20.0, 5.0, 1.0, 0.37, 0.05})
        {
            final double[] fine = run(NITRIC, step, 150f);
            assertArrayEquals(coarse, fine, 1e-9, "Step of " + step + " s");
        }
        assertEquals(0.175, coarse[0], 1e-6);
        assertEquals(0.2, coarse[2], 1e-6);
        assertEquals(8.0, coarse[3]);
        assertEquals(0.23, coarse[4], 1e-6);
        assertEquals(8.0, coarse[5]);
    }

    @Test
    void aLongStepGivesNoMoreThanTheBatchHas()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);
        final Distillation.Step step = batch.advance(100_000.0, 150f, ENOUGH, ROOM);

        assertTrue(step.finished());
        assertEquals(0.175, step.output(), 1e-6);
        assertEquals(8, step.byproducts());
        assertFalse(batch.active());
        assertEquals(Distillation.Reason.NONE, step.reason());
    }

    @Test
    void aSecondBatchStartsFromNothingAndGivesNoMore()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);
        double output = batch.advance(100.0, 150f, ENOUGH, ROOM).output();
        output += batch.advance(100.0, 150f, ENOUGH, ROOM).output();

        assertEquals(2 * 0.175, output, 1e-6, "Two batches, each taking its own inputs, give twice the output and no more");
    }

    @Test
    void aMissingInputKeepsTheBatchFromStarting()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);

        final Distillation.Step noLiquid = batch.advance(10.0, 150f, new Distillation.Stock(0.2f, 0f, 8, 0f), ROOM);
        assertEquals(Distillation.Reason.MISSING_LIQUID, noLiquid.reason());
        final Distillation.Step noPowder = batch.advance(10.0, 150f, new Distillation.Stock(0.23f, 0f, 7, 0f), ROOM);
        assertEquals(Distillation.Reason.MISSING_ITEMS, noPowder.reason());
        assertFalse(noLiquid.ran() || noPowder.ran());
        assertFalse(batch.active());
        assertEquals(0.0, batch.progress());
    }

    @Test
    void aSecondLiquidIsAnInputToo()
    {
        final Distillation.Recipe salt = recipe(0, 1f, 0.02f, 0f, 100f, 30f, 0f, 0f, 0f, 0f, 1);
        final Distillation batch = new Distillation(salt, 0.0, false);

        final Distillation.Step dry = batch.advance(10.0, 50f, new Distillation.Stock(1f, 0.019f, 0, 0f), ROOM);
        assertEquals(Distillation.Reason.MISSING_REAGENT, dry.reason());
        assertFalse(batch.active());

        final Distillation.Step wet = batch.advance(10.0, 50f, new Distillation.Stock(1f, 0.02f, 0, 0f), ROOM);
        assertTrue(wet.ran());
        assertEquals(0.02, wet.reagent(), 1e-6);
        assertEquals(1.0, wet.litres(), 1e-6);
    }

    @Test
    void aMissingReceiverWaterKeepsAnAbsorbingBatchFromStarting()
    {
        final Distillation.Recipe hcl = recipe(8, 0.39f, 0f, 150f, 300f, 60f, 0.67f, 0f, 0.53f, 0f, 8);
        final Distillation batch = new Distillation(hcl, 0.0, false);

        final Distillation.Step dry = batch.advance(10.0, 200f, new Distillation.Stock(0.39f, 0f, 8, 0.52f), ROOM);
        assertEquals(Distillation.Reason.MISSING_WATER, dry.reason());
        assertFalse(batch.active());

        final Distillation.Step wet = batch.advance(10.0, 200f, new Distillation.Stock(0.39f, 0f, 8, 0.53f), ROOM);
        assertTrue(wet.ran());
        assertEquals(0.53, wet.water(), 1e-6);
    }

    @Test
    void aFullOutputHoldsTheBatchBack()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);

        final Distillation.Step full = batch.advance(30.0, 150f, ENOUGH, new Distillation.Room(0.0, 25.0, 64));
        assertEquals(Distillation.Reason.NO_ROOM, full.reason());
        assertFalse(batch.active(), "A batch must not take its inputs when its first stretch has nowhere to go");

        batch.advance(30.0, 150f, ENOUGH, ROOM);
        final Distillation.Step stuck = batch.advance(30.0, 150f, ENOUGH, new Distillation.Room(0.0, 25.0, 64));
        assertEquals(Distillation.Reason.NO_ROOM, stuck.reason());
        assertEquals(30.0, batch.progress(), 1e-12);
    }

    @Test
    void aStretchThatJustFitsRuns()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);
        assertTrue(batch.advance(30.0, 150f, ENOUGH, new Distillation.Room(0.0875, 0.0, 0)).ran());
    }

    @Test
    void theLastStretchNeedsRoomForThePowderToo()
    {
        final Distillation batch = new Distillation(NITRIC, 0.0, false);
        final Distillation.Step step = batch.advance(100.0, 150f, ENOUGH, new Distillation.Room(25.0, 25.0, 7));

        assertEquals(Distillation.Reason.NO_ROOM, step.reason());
        assertFalse(batch.active());
    }

    @Test
    void aResidueNeedsItsOwnRoom()
    {
        final Distillation.Recipe spirit = recipe(0, 1f, 0f, 70f, 100f, 30f, 0.02f, 0.9f, 0f, 0f, 0);
        final Distillation batch = new Distillation(spirit, 0.0, false);

        assertEquals(Distillation.Reason.NO_ROOM, batch.advance(30.0, 80f, new Distillation.Stock(1f, 0f, 0, 0f), new Distillation.Room(25.0, 0.1, 0)).reason());
        final Distillation.Step step = batch.advance(30.0, 80f, new Distillation.Stock(1f, 0f, 0, 0f), ROOM);
        assertEquals(0.02, step.output(), 1e-6);
        assertEquals(0.9, step.residue(), 1e-6);
    }

    @Test
    void theFlaskCracksOnlyAboveFiveHundredAndFifty()
    {
        assertFalse(Distillation.cracks(550f));
        assertTrue(Distillation.cracks(550.1f));
        assertFalse(Distillation.cracks(20f));
    }

    @Test
    void aSavedBatchResumesWhereItWas()
    {
        final Distillation first = new Distillation(NITRIC, 0.0, false);
        first.advance(24.0, 150f, ENOUGH, ROOM);
        final Distillation resumed = new Distillation(NITRIC, first.progress(), first.active());
        final Distillation.Step rest = resumed.advance(100.0, 150f, new Distillation.Stock(0f, 0f, 0, 0f), ROOM);

        assertTrue(rest.finished());
        assertEquals(0.175 * (1.0 - 24.0 / 60.0), rest.output(), 1e-6);
        assertEquals(0, rest.items(), "A resumed batch has already taken its inputs");
    }
}
