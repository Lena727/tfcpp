package tfcpp.pneumatics.pipes;

import org.junit.jupiter.api.Test;

import tfcpp.pneumatics.gas.Gas;

import static org.junit.jupiter.api.Assertions.*;

class PipeContentsTest
{
    private static PipeContents holding(String liquid, float litres)
    {
        final PipeContents contents = new PipeContents();
        contents.liquid = liquid;
        contents.litres = litres;
        return contents;
    }

    @Test
    void aClashOfLiquidsGivesBackTheLesserAndDeletesNothing()
    {
        final PipeContents water = holding("minecraft:water", 300f), acid = holding("tfcpp:chamber_acid", 40f);
        final PipeContents rejected = water.add(acid);

        assertNotNull(rejected, "The lesser is handed back, to be spilled");
        assertEquals("tfcpp:chamber_acid", rejected.liquid);
        assertEquals(40f, rejected.litres, 1e-4f);
        assertEquals("minecraft:water", water.liquid);
        assertEquals(300f, water.litres, 1e-4f);
        assertEquals(0f, acid.litres, "What was offered is empty");
    }

    @Test
    void theGreaterWinsWhicheverSideItCameFrom()
    {
        final PipeContents acid = holding("tfcpp:chamber_acid", 40f), water = holding("minecraft:water", 300f);
        final PipeContents rejected = acid.add(water);

        assertNotNull(rejected);
        assertEquals("tfcpp:chamber_acid", rejected.liquid, "It is the acid, though the acid was here first");
        assertEquals(40f, rejected.litres, 1e-4f);
        assertEquals("minecraft:water", acid.liquid);
        assertEquals(300f, acid.litres, 1e-4f);
    }

    @Test
    void noPathLosesLiquidInAJoin()
    {
        final float total = 300f + 40f + 12f;
        final PipeContents a = holding("minecraft:water", 300f), b = holding("tfcpp:chamber_acid", 40f), c = holding("minecraft:water", 12f);
        float rejectedLitres = 0f;
        for (PipeContents other : new PipeContents[] {b, c})
        {
            final PipeContents rejected = a.add(other);
            rejectedLitres += rejected == null ? 0f : rejected.litres;
        }
        assertEquals(total, a.litres + rejectedLitres, 1e-3f, "Everything is either in the pipe or handed back");
    }

    @Test
    void sameLiquidAddsUpAndNothingIsHandedBack()
    {
        final PipeContents a = holding("minecraft:water", 5f), b = holding("minecraft:water", 7f);
        assertNull(a.add(b));
        assertEquals(12f, a.litres, 1e-4f);
        final PipeContents empty = new PipeContents();
        assertNull(empty.add(holding("tfc:brine", 3f)));
        assertEquals("tfc:brine", empty.liquid);
        assertEquals(3f, empty.litres, 1e-4f);
    }

    @Test
    void runoffIsKeptThroughSplittingJoiningAndSaving()
    {
        final PipeContents contents = holding("tfcpp:sulfuric_acid", 100f);
        contents.runoff = 400f;
        contents.runoffLiquid = "tfcpp:sulfuric_acid";

        final PipeContents share = contents.share(0.25f);
        assertEquals(100f, share.runoff, 1e-3f);
        assertEquals(400f, contents.runoff, 1e-3f, "A share is a copy");

        final PipeContents taken = contents.take(0.5f);
        assertEquals(200f, taken.runoff, 1e-3f);
        assertEquals(200f, contents.runoff, 1e-3f);

        assertNull(contents.add(taken));
        assertEquals(400f, contents.runoff, 1e-3f);
        assertEquals("tfcpp:sulfuric_acid", contents.runoffLiquid);
        assertEquals(0f, taken.runoff);

        final PipeContents loaded = PipeContents.load(contents.save());
        assertEquals(400f, loaded.runoff, 1e-3f);
        assertEquals("tfcpp:sulfuric_acid", loaded.runoffLiquid);
        assertEquals(0f, PipeContents.load(new PipeContents().save()).runoff);
    }

    @Test
    void runoffTakesNoRoomAndAddsNoPressure()
    {
        final PipeContents contents = PipeContents.atmospheric(1f);
        final float before = contents.pressure(1f);
        contents.runoff = 900f;
        contents.runoffLiquid = "minecraft:water";
        assertEquals(before, contents.pressure(1f), 1e-6f);
        assertEquals(0f, contents.liquidVolume());
    }

    @Test
    void aFilmOfAnotherLiquidDoesNotKeepAnotherOut()
    {
        final PipeContents film = holding("tfcpp:chamber_acid", 0.4f);
        assertTrue(film.takesLiquid("minecraft:water"));
        assertTrue(film.addLiquid("minecraft:water", 5f, 293f));
        assertEquals("minecraft:water", film.liquid);
        assertEquals(5f, film.litres, 1e-4f, "The film is washed out ahead of the water");

        final PipeContents more = holding("tfcpp:chamber_acid", 3f);
        assertFalse(more.takesLiquid("minecraft:water"), "but a millilitre or more keeps it out");
        assertFalse(more.addLiquid("minecraft:water", 5f, 293f));
        assertEquals(3f, more.litres, 1e-4f);
        assertEquals("tfcpp:chamber_acid", more.liquid);
    }

    @Test
    void brineBoilsAsWaterDoes()
    {
        final PipeContents water = holding("minecraft:water", 100f), brine = holding("tfc:brine", 100f);
        water.temperature = brine.temperature = 400f;
        assertTrue(brine.isWater());
        assertEquals(water.pressure(1f), brine.pressure(1f), 1e-5f, "Brine has the vapour pressure of water");
        assertTrue(brine.freezingPoint() < water.freezingPoint(), "but it does not freeze with it");
        assertFalse(holding("tfcpp:sulfuric_acid", 100f).isWater(), "Acids are left for the table of Antoine");
    }

    @Test
    void gasStillJoinsWhenThereIsNoLiquid()
    {
        final PipeContents a = new PipeContents(), b = new PipeContents();
        a.gases[Gas.METHANE.ordinal()] = 1f;
        b.gases[Gas.METHANE.ordinal()] = 2f;
        assertNull(a.add(b));
        assertEquals(3f, a.gases[Gas.METHANE.ordinal()], 1e-6f);
    }
}
