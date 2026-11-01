package tfcpp.metallurgy;

import org.junit.jupiter.api.Test;

import tfcpp.metallurgy.ConverterBath.Blow;
import tfcpp.metallurgy.ConverterBath.Flame;
import tfcpp.metallurgy.ConverterBath.Grade;

import static org.junit.jupiter.api.Assertions.*;

class ConverterBathTest
{
    private static final float OXYGEN_IN_AIR = 0.2095f;
    private static final float CARBON_GRAMS_PER_MOL = 12.011f;

    private static ConverterBath pigIron(float millibuckets, float temperature)
    {
        final ConverterBath bath = new ConverterBath();
        bath.add(millibuckets, Grade.PIG_IRON, temperature);
        return bath;
    }

    /** One second of blast at this many moles of air per second, with the vessel losing heat. */
    private static Blow second(ConverterBath bath, float airPerSecond)
    {
        final Blow blow = bath.blow(airPerSecond * OXYGEN_IN_AIR, airPerSecond, 20f);
        bath.cool(1f);
        return blow;
    }

    private static float grams(ConverterBath bath, float percent)
    {
        return bath.metal * ConverterBath.KG_PER_MILLIBUCKET * 1000f * percent / 100f;
    }

    @Test
    void carbonSetsTheMeltingPoint()
    {
        assertEquals(1538f, ConverterBath.liquidus(0f), 0.1f);
        assertEquals(1147f, ConverterBath.liquidus(4.3f), 2f, "the eutectic of iron and carbon");
        assertTrue(ConverterBath.liquidus(0.5f) > 1480f, "steel melts far above cast iron");
    }

    @Test
    void gradesFollowCarbon()
    {
        assertEquals(Grade.WROUGHT_IRON, Grade.ofCarbon(0.05f));
        assertEquals(Grade.STEEL, Grade.ofCarbon(0.5f));
        assertEquals(Grade.HIGH_CARBON_STEEL, Grade.ofCarbon(1.5f));
        assertEquals(Grade.CAST_IRON, Grade.ofCarbon(3f));
        assertEquals(Grade.PIG_IRON, Grade.ofCarbon(4.3f));
    }

    @Test
    void eachMoleOfOxygenBurnsTwoOfCarbon()
    {
        final ConverterBath bath = pigIron(2000f, 1400f);
        bath.silicon = 0f;
        final float before = grams(bath, bath.carbon);
        final Blow blow = bath.blow(1f, 4.77f, 20f);
        assertEquals(2f, blow.carbonBurned(), 1e-3f, "C + 1/2 O2 -> CO");
        assertEquals(1f, blow.oxygenUsed(), 1e-3f);
        assertEquals(before - 2f * CARBON_GRAMS_PER_MOL, grams(bath, bath.carbon), 0.5f);
    }

    @Test
    void siliconBurnsBeforeCarbon()
    {
        final ConverterBath bath = pigIron(2000f, 1400f);
        final float carbon = bath.carbon;
        assertEquals(Flame.SPARKS, bath.flame());
        assertEquals(0f, bath.blow(1f, 4.77f, 20f).carbonBurned(), 1e-6f, "No carbon burns while silicon lasts");
        assertTrue(bath.carbon >= carbon);
        assertTrue(bath.silicon < Grade.PIG_IRON.silicon());
    }

    @Test
    void oxygenIsNeverLostAndAllTheIronIsAccountedFor()
    {
        final ConverterBath bath = pigIron(2000f, 1400f);
        final Blow blow = bath.blow(40f, 40f / OXYGEN_IN_AIR, 20f);
        assertEquals(40f, blow.oxygenUsed(), 0.5f, "Every mole of oxygen has to react with something");
        final float iron = 2000f * (1f - (Grade.PIG_IRON.carbon() + Grade.PIG_IRON.silicon()) / 100f);
        final float ironLeft = bath.metal * (1f - (bath.carbon + bath.silicon) / 100f);
        assertEquals(iron, ironLeft + bath.slag, 0.1f, "Iron only ever leaves as slag");
    }

    @Test
    void aFullBlowWithOnePumpHeatsTheBathByAboutARealAmount()
    {
        // About 0.2 mol of air a second is one piston pump on one water wheel
        final ConverterBath bath = pigIron(2000f, 1350f);
        int seconds = 0;
        while (bath.carbon > 0.5f && seconds < 7200)
        {
            second(bath, 0.2f);
            seconds++;
        }
        assertTrue(seconds > 300 && seconds < 1500, "A blow takes minutes, took " + seconds + " s");
        final float rise = bath.temperature - 1350f;
        assertTrue(rise > 50f && rise < 400f, "Real blows gain up to about 350 K, got " + rise);
        assertEquals(Grade.STEEL, bath.grade());
    }

    @Test
    void morePumpsMakeAHotterBlow()
    {
        final ConverterBath slow = pigIron(2000f, 1350f), fast = pigIron(2000f, 1350f);
        while (slow.carbon > 0.5f) second(slow, 0.2f);
        while (fast.carbon > 0.5f) second(fast, 0.8f);
        assertTrue(fast.temperature > slow.temperature + 50f, "A blow that takes a quarter of the time loses a quarter of the heat: " + fast.temperature + " against " + slow.temperature);
    }

    @Test
    void aWeakBlastFreezesTheBath()
    {
        // Half a pump's worth, say a windmill in light wind
        final ConverterBath bath = pigIron(2000f, 1300f);
        int seconds = 0;
        while (bath.isMolten() && bath.carbon > 0.5f && seconds < 20000)
        {
            second(bath, 0.05f);
            seconds++;
        }
        assertFalse(bath.isMolten(), "The iron should set in the vessel before it becomes steel, at " + bath.temperature + " C with " + bath.carbon + "% carbon");
    }

    @Test
    void overblowingBurnsTheIronItself()
    {
        final ConverterBath bath = pigIron(2000f, 1400f);
        while (bath.flame() != Flame.DROPPED) second(bath, 0.4f);
        final float atDrop = bath.metal;
        for (int i = 0; i < 300; i++)
        {
            second(bath, 0.4f);
        }
        assertTrue(bath.carbon >= 0.029f, "Air cannot take the last of the carbon out, got " + bath.carbon);
        assertTrue(bath.metal < atDrop - 10f, "Blowing on burns the iron away: " + atDrop + " -> " + bath.metal);
    }

    @Test
    void aBlowLosesAboutWhatARealOneLoses()
    {
        final ConverterBath bath = pigIron(2000f, 1400f);
        while (bath.flame() != Flame.DROPPED) second(bath, 0.4f);
        final float lost = 1f - bath.metal / 2000f;
        assertTrue(lost > 0.03f && lost < 0.15f, "Real blows lost 5-15% of the charge, got " + lost);
    }

    @Test
    void theFlameTellsTheStageOfTheBlow()
    {
        final ConverterBath bath = pigIron(1000f, 1400f);
        assertEquals(Flame.SPARKS, bath.flame());
        while (bath.silicon > 0.02f) second(bath, 0.4f);
        assertEquals(Flame.BOILING, bath.flame());
        while (bath.carbon > 1f) second(bath, 0.4f);
        assertEquals(Flame.SHORTENING, bath.flame());
        while (bath.carbon > 0.2f) second(bath, 0.4f);
        assertEquals(Flame.DROPPED, bath.flame());
    }

    @Test
    void mixingIsByWeight()
    {
        final ConverterBath bath = new ConverterBath();
        bath.add(100f, Grade.PIG_IRON, 1400f);
        bath.add(300f, Grade.WROUGHT_IRON, 1600f);
        assertEquals((Grade.PIG_IRON.carbon() * 100f + Grade.WROUGHT_IRON.carbon() * 300f) / 400f, bath.carbon, 1e-4f);
        assertEquals(1550f, bath.temperature, 0.01f);
        assertEquals(400f, bath.metal, 1e-4f);
        assertEquals(Grade.HIGH_CARBON_STEEL, bath.grade());
    }

    @Test
    void anIdleBathCoolsTowardTheAir()
    {
        final ConverterBath bath = pigIron(2000f, 1400f);
        bath.cool(600f);
        assertTrue(bath.temperature < 1320f && bath.temperature > 1200f, "Ten minutes idle, now " + bath.temperature);
        bath.cool(1e7f);
        assertEquals(ConverterBath.AMBIENT, bath.temperature, 0.5f);
    }

    @Test
    void tappingStopsAtWholeMillibuckets()
    {
        final ConverterBath bath = pigIron(150.5f, 1400f);
        assertEquals(100, bath.tap(100));
        assertEquals(50, bath.tap(100));
        assertEquals(0f, bath.metal, "Half a millibucket cannot be poured, and a vessel holding it counts as empty");
        assertEquals(0.5f, bath.slag, 1e-3f, "It stays on the lining");
    }

    @Test
    void pouringIronBackNeverGainsCarbon()
    {
        for (float carbon = 0.03f; carbon < 5f; carbon += 0.01f)
        {
            final Grade grade = Grade.ofCarbon(carbon);
            assertTrue(grade.carbon() <= carbon + 2e-3f, grade + " counts as " + grade.carbon() + "% and must not count as more than the " + carbon + "% it was named from");
        }
    }

    @Test
    void ironPouredByTheDropKeepsItsName()
    {
        final ConverterBath bath = new ConverterBath();
        for (int i = 0; i < 2000; i++)
        {
            bath.add(1f, Grade.PIG_IRON, 1400f);
        }
        assertEquals(Grade.PIG_IRON, bath.grade(), "Rounding must not turn it into something else, at " + bath.carbon);
    }

    @Test
    void anEmptyBathDoesNothing()
    {
        final ConverterBath bath = new ConverterBath();
        assertSame(Blow.NONE, bath.blow(1f, 4.77f, 20f));
        assertFalse(bath.isMolten());
    }

    @Test
    void onlyIronFamilyFluidsAreKnown()
    {
        assertEquals(Grade.PIG_IRON, Grade.ofFluid("tfc:metal/pig_iron"));
        assertEquals(Grade.WROUGHT_IRON, Grade.ofFluid("tfc:metal/wrought_iron"));
        assertNull(Grade.ofFluid("tfc:metal/copper"));
    }
}
