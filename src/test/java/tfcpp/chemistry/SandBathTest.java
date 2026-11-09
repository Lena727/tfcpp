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

    /** What a charcoal fire of each air supply passes up at each depth, and which recipe window each falls in: the acids page of the guide. */
    private static boolean in(float temperature, float low, float high)
    {
        return temperature >= low && temperature <= high;
    }

    private static boolean works(float fire, int depth, float low, float high)
    {
        return in(SandBath.passedUp(fire, depth), low, high);
    }

    private static String depths(float fire, float low, float high)
    {
        final StringBuilder depths = new StringBuilder();
        for (int depth = SandBath.MIN_DEPTH; depth <= SandBath.MAX_DEPTH; depth++)
        {
            depths.append(works(fire, depth, low, high) ? depth : "-");
        }
        return depths.toString();
    }

    @Test
    void theDepthsOfTheGuideForEachAirSupply()
    {
        final float natural = tfcpp.metallurgy.Draught.temperature(1000f, 1600f, 0f, 1f);
        final float one = tfcpp.metallurgy.Draught.temperature(1000f, 1600f, tfcpp.metallurgy.Draught.pipeAir(1), 1f);
        final float two = tfcpp.metallurgy.Draught.temperature(1000f, 1600f, tfcpp.metallurgy.Draught.pipeAir(2), 1f);
        final float bellows = tfcpp.metallurgy.Draught.temperature(1000f, 1600f, 1f, 1f);
        assertEquals(216f, SandBath.passedUp(natural, 4), 1f);
        assertEquals(265f, SandBath.passedUp(natural, 3), 1f);
        assertEquals(347f, SandBath.passedUp(natural, 2), 1f);
        assertEquals(316f, SandBath.passedUp(one, 3), 1f);
        assertEquals(257f, SandBath.passedUp(one, 4), 1f);
        assertEquals(285f, SandBath.passedUp(two, 4), 1f);
        assertEquals(336f, SandBath.passedUp(bellows, 4), 1f);
        assertEquals(283f, SandBath.passedUp(bellows, 5), 1f);
        // Oil of vitriol, 280 to 350 C
        assertEquals("-2------", depths(natural, 280f, 350f));
        assertEquals("--3-----", depths(one, 280f, 350f));
        assertEquals("---4----", depths(two, 280f, 350f));
        assertEquals("---45---", depths(bellows, 280f, 350f));
        // Aqua fortis, 120 to 200 C
        assertEquals("----5678", depths(natural, 120f, 200f));
        assertEquals("-----678", depths(one, 120f, 200f));
        assertEquals("------78", depths(two, 120f, 200f));
        assertEquals("-------8", depths(bellows, 120f, 200f));
        // Spirit of salt, 150 to 300 C
        assertEquals("--3456--", depths(natural, 150f, 300f));
        assertEquals("---45678", depths(one, 150f, 300f));
        assertEquals("---45678", depths(two, 150f, 300f));
        assertEquals("----5678", depths(bellows, 150f, 300f));
        // The most that wind gives to an unblown fire pushes depth 2 out of the window of oil of vitriol
        assertFalse(works(tfcpp.metallurgy.Draught.temperature(1000f, 1600f, tfcpp.metallurgy.Draught.MOST_WIND_AIR, 1f), 2, 280f, 350f));
    }
}
