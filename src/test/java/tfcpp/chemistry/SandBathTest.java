package tfcpp.chemistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SandBathTest
{
    @Test
    void aThousandDegreeFireUnderOneLayerGivesFiveHundredAndSixty()
    {
        assertEquals(560f, SandBath.passedUp(1100f, 1), 1e-3f);
    }

    @Test
    void underEightLayersItGivesAboutOneHundredAndFortyDegrees()
    {
        assertEquals(140f, SandBath.passedUp(1100f, 8), 1e-3f);
    }

    @Test
    void deeperSandPassesLessHeat()
    {
        float previous = Float.MAX_VALUE;
        for (int depth = SandBath.MIN_DEPTH; depth <= SandBath.MAX_DEPTH; depth++)
        {
            final float passed = SandBath.passedUp(900f, depth);
            assertTrue(passed < previous, "Depth " + depth);
            assertTrue(passed > SandBath.AMBIENT, "Never below the room while the fire is hotter");
            previous = passed;
        }
    }

    @Test
    void theSandNeverGivesMoreThanTheFireHas()
    {
        for (int depth = SandBath.MIN_DEPTH; depth <= SandBath.MAX_DEPTH; depth++)
        {
            assertTrue(SandBath.passedUp(700f, depth) < 700f);
        }
    }

    @Test
    void aFireNoHotterThanTheRoomGivesWhatItHas()
    {
        assertEquals(20f, SandBath.passedUp(20f, 4));
        assertEquals(5f, SandBath.passedUp(5f, 4), "A cold fire does not warm the vessel to the room's temperature");
    }

    @Test
    void aDepthOutsideTheRangeIsHeldToIt()
    {
        assertEquals(SandBath.passedUp(500f, 1), SandBath.passedUp(500f, 0));
        assertEquals(SandBath.passedUp(500f, 8), SandBath.passedUp(500f, 99));
    }
}
