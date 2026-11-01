package tfcpp.metallurgy;

import org.junit.jupiter.api.Test;

import tfcpp.metallurgy.MetalHeat.Metal;

import static org.junit.jupiter.api.Assertions.*;

class MetalHeatTest
{
    private static float ticksToMelt(Metal metal)
    {
        return metal.ingotCapacity() * (metal.melt() - 20f) / 2.8f; // TFC puts 2.8 units a tick into a slot
    }

    @Test
    void capacityIsSpreadSoThatTheTotalToMeltIsRight()
    {
        for (Metal metal : MetalHeat.METALS)
        {
            final float joules = metal.ingotCapacity() * MetalHeat.JOULES_PER_UNIT * (metal.melt() - 20f);
            assertEquals(metal.joulesToMelt(), joules, metal.joulesToMelt() * 1e-3f, metal.id());
        }
    }

    @Test
    void meltingAnIngotOfCopperTakesWhatARealOneDoes()
    {
        // 896 g x (0.385 J/g/K x 1060 K + 205 J/g) is about 550 kJ
        assertEquals(550_000f, MetalHeat.COPPER.joulesToMelt(), 10_000f);
    }

    @Test
    void lowMeltingMetalsMeltFarFasterThanCopper()
    {
        assertTrue(ticksToMelt(MetalHeat.TIN) < ticksToMelt(MetalHeat.COPPER) / 4f, "Tin melts in a campfire in minutes");
        assertTrue(ticksToMelt(MetalHeat.BISMUTH) < ticksToMelt(MetalHeat.COPPER) / 4f);
        assertTrue(ticksToMelt(MetalHeat.WROUGHT_IRON) > ticksToMelt(MetalHeat.COPPER), "Iron takes more than copper");
    }

    @Test
    void bismuthNoLongerTakesMoreHeatThanCopper()
    {
        final float real = MetalHeat.BISMUTH.ingotCapacity() / MetalHeat.COPPER.ingotCapacity();
        assertTrue(real < 1f, "TFC has this at 2.5, got " + real);
    }

    @Test
    void heatOfMeltingMattersMostInLowMeltingMetals()
    {
        final float tin = MetalHeat.TIN.latentHeat() / (MetalHeat.TIN.specificHeat() * (MetalHeat.TIN.melt() - 20f));
        final float iron = MetalHeat.WROUGHT_IRON.latentHeat() / (MetalHeat.WROUGHT_IRON.specificHeat() * (MetalHeat.WROUGHT_IRON.melt() - 20f));
        assertTrue(tin > 1f, "Tin takes more to melt than to heat, got " + tin);
        assertEquals(0.36f, iron, 0.02f, "and iron about a third as much");
    }

    @Test
    void liquidsKeepTFCsRatioToSolids()
    {
        assertEquals(MetalHeat.COPPER.ingotCapacity() * 0.3f / 100f, MetalHeat.COPPER.liquidCapacity(), 1e-6f);
    }

    @Test
    void namesAreNotRepeated()
    {
        assertEquals(MetalHeat.METALS.size(), MetalHeat.METALS.stream().map(Metal::id).distinct().count());
    }
}
