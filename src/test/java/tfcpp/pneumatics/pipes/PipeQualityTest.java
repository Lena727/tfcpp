package tfcpp.pneumatics.pipes;

import org.junit.jupiter.api.Test;

import tfcpp.ecology.FoodMetals;
import tfcpp.health.WaterQuality;

import static org.junit.jupiter.api.Assertions.*;

/** The quality of the water in a pipe: mixing by volume, boiling, and the save. */
class PipeQualityTest
{
    private static final String WATER = "minecraft:water";

    private static WaterQuality dirty()
    {
        return new WaterQuality(1e-3, 1e-4, 0.0, new double[FoodMetals.METALS.length], 0.0);
    }

    @Test
    void waterMixesByVolumeInThePipe()
    {
        final PipeContents pipe = new PipeContents();
        pipe.addLiquid(WATER, 30f, PipePhysics.ROOM_TEMPERATURE);
        assertNull(pipe.quality);
        pipe.addLiquid(WATER, 10f, PipePhysics.ROOM_TEMPERATURE, dirty());
        assertEquals(2.5e-4, pipe.quality.faecal, 2.5e-4 * 1e-12, "a quarter of the volume is dirty: 1e-3 * 10 / 40, exactly");
        assertEquals(2.5e-5, pipe.quality.shedDysentery, 2.5e-5 * 1e-12);
        final PipeContents taken = pipe.take(0.5f);
        assertNotNull(taken.quality, "what is taken carries the quality");
        assertEquals(20f, taken.litres, 1e-4);
    }

    @Test
    void waterHeldAtTheBoilingPointForAMinuteIsBoiled()
    {
        final PipeContents pipe = new PipeContents();
        pipe.temperature = 380f;
        pipe.addLiquid(WATER, 10f, 380f, dirty());
        for (int second = 0; second < 59; second++)
        {
            pipe.watchBoiling(1f);
        }
        assertTrue(pipe.quality.hasPathogens(), "not yet");
        pipe.watchBoiling(1f);
        assertFalse(pipe.quality == null ? false : pipe.quality.hasPathogens(), "a minute of boiling");
        pipe.addLiquid(WATER, 10f, 380f, dirty());
        pipe.watchBoiling(1f);
        assertTrue(pipe.quality.hasPathogens(), "water that comes in has to boil a minute itself");
        final PipeContents cool = new PipeContents();
        cool.addLiquid(WATER, 10f, 300f, dirty());
        for (int second = 0; second < 600; second++)
        {
            cool.watchBoiling(1f);
        }
        assertTrue(cool.quality.hasPathogens(), "cool water is not boiled");
    }

    @Test
    void theQualitySurvivesASave()
    {
        final PipeContents pipe = new PipeContents();
        pipe.addLiquid(WATER, 10f, PipePhysics.ROOM_TEMPERATURE, dirty());
        final PipeContents back = PipeContents.load(pipe.save());
        assertNotNull(back.quality);
        assertEquals(pipe.quality.faecal, back.quality.faecal, 0.0, "kept exactly");
        assertEquals(pipe.quality.shedDysentery, back.quality.shedDysentery, 0.0);
    }

    @Test
    void waterPumpedFromOnePipeIntoAnotherKeepsItsQuality()
    {
        final PipeContents from = new PipeContents();
        from.addLiquid(WATER, 10f, PipePhysics.ROOM_TEMPERATURE, dirty());
        final PipeContents to = new PipeContents();
        // What the pump does between two networks: the liquid goes with the quality of the source
        to.addLiquid(from.liquid, 4f, from.temperature, from.quality);
        from.litres -= 4f;
        assertNotNull(to.quality);
        assertEquals(1e-3, to.quality.faecal, 1e-3 * 1e-12);
        assertEquals(1e-4, to.quality.shedDysentery, 1e-4 * 1e-12);
    }
}
