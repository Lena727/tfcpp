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
        assertEquals(Grade.PHOSPHORIC_PIG_IRON, Grade.ofFluid("tfcpp:metal/phosphoric_pig_iron"));
        assertEquals(Grade.COLD_SHORT_IRON, Grade.ofFluid("tfcpp:metal/cold_short_iron"));
        assertNull(Grade.ofFluid("tfc:metal/copper"));
    }

    // Phosphorus

    private static final float PHOSPHORUS_GRAMS_PER_MOL = 30.974f;
    private static final float SILICON_GRAMS_PER_MOL = 28.086f;
    /** Plenty for the 5.6 mol of silicon and 7.7 mol of phosphorus that 2000 mL of phosphoric pig iron holds: 26.5 mol of CaO. */
    private static final float ENOUGH_LIME = 4 * ConverterBath.LIME_MOLES_PER_POWDER;

    private static ConverterBath phosphoric(boolean basic, float lime)
    {
        final ConverterBath bath = new ConverterBath();
        bath.basic = basic;
        bath.addLime(lime); // Into an empty vessel, so it is held until the iron is poured
        bath.add(2000f, Grade.PHOSPHORIC_PIG_IRON, 1400f);
        return bath;
    }

    private static float phosphorusGrams(ConverterBath bath)
    {
        return grams(bath, bath.phosphorus);
    }

    /** Blows well past the end of the carbon and then on for this many seconds more. */
    private static void blowThrough(ConverterBath bath, int extraSeconds)
    {
        int guard = 0;
        while (bath.carbon > 0.2f && guard++ < 20000) second(bath, 0.4f);
        for (int i = 0; i < extraSeconds; i++) second(bath, 0.4f);
    }

    @Test
    void phosphorusCountsOnlyInTheGradesThatCarryIt()
    {
        assertEquals(1.5f, Grade.PHOSPHORIC_PIG_IRON.phosphorus());
        assertEquals(4f, Grade.PHOSPHORIC_PIG_IRON.carbon());
        assertEquals(1f, Grade.PHOSPHORIC_PIG_IRON.silicon());
        assertEquals(1.5f, Grade.COLD_SHORT_IRON.phosphorus());
        assertTrue(Grade.COLD_SHORT_IRON.carbon() <= 0.2f);
        for (float carbon = 0f; carbon < 6f; carbon += 0.01f)
        {
            final Grade grade = Grade.ofCarbon(carbon);
            assertEquals(0f, grade.phosphorus(), "Naming iron by carbon must not hand out " + grade);
        }
    }

    @Test
    void phosphorusMixesByWeight()
    {
        final ConverterBath bath = new ConverterBath();
        bath.add(100f, Grade.PHOSPHORIC_PIG_IRON, 1400f);
        bath.add(300f, Grade.WROUGHT_IRON, 1600f);
        assertEquals(1.5f * 100f / 400f, bath.phosphorus, 1e-4f);
        assertEquals(1f * 100f / 400f, bath.silicon, 1e-4f);
    }

    @Test
    void morePhosphorusThanTenthOfAPercentMakesColdShortIronWhateverTheCarbon()
    {
        final ConverterBath bath = new ConverterBath();
        bath.add(100f, Grade.STEEL, 1600f);
        assertEquals(Grade.STEEL, bath.tapGrade());
        bath.phosphorus = ConverterBath.PHOSPHORUS_LIMIT_PERCENT;
        assertEquals(Grade.STEEL, bath.tapGrade(), "Exactly at the limit is not over it");
        bath.phosphorus = 0.11f;
        assertEquals(Grade.COLD_SHORT_IRON, bath.tapGrade());
        bath.carbon = 2.09f;
        assertEquals(Grade.COLD_SHORT_IRON, bath.tapGrade(), "Below the carbon of cast iron, phosphorus makes it cold short");
        bath.carbon = 2.1f;
        assertEquals(Grade.PHOSPHORIC_PIG_IRON, bath.tapGrade(), "Iron that still holds the carbon of pig iron is phosphoric pig iron, which a converter can take again");
        bath.carbon = 3.5f;
        assertEquals(Grade.PHOSPHORIC_PIG_IRON, bath.tapGrade());
        assertEquals(Grade.CAST_IRON, bath.grade(), "grade() still goes by carbon alone");
    }

    @Test
    void phosphorusFreeIronIsUntouchedByTheLining()
    {
        final ConverterBath plain = pigIron(2000f, 1400f);
        final ConverterBath lined = new ConverterBath();
        lined.basic = true;
        lined.addLime(ENOUGH_LIME);
        lined.add(2000f, Grade.PIG_IRON, 1400f);
        assertTrue(lined.temperature < plain.temperature, "The cold lime took heat from the iron poured on it");
        for (int i = 0; i < 1500; i++)
        {
            final Blow a = second(plain, 0.4f), b = second(lined, 0.4f);
            assertEquals(a, b, "second " + i);
        }
        assertEquals(plain.metal, lined.metal, 0f);
        assertEquals(plain.carbon, lined.carbon, 0f);
        assertEquals(0f, lined.phosphorus);
        assertEquals(0f, lined.thomasSlagPhosphorus);
        assertEquals(Grade.WROUGHT_IRON, lined.tapGrade());
    }

    @Test
    void anAcidLiningKeepsAllThePhosphorus()
    {
        final ConverterBath bath = phosphoric(false, 0f);
        final float before = phosphorusGrams(bath);
        blowThrough(bath, 300);
        assertTrue(bath.carbon < 0.2f);
        assertEquals(before, phosphorusGrams(bath), 0.01f, "Not a gram of phosphorus leaves an acid converter");
        assertTrue(bath.phosphorus >= Grade.PHOSPHORIC_PIG_IRON.phosphorus(), "It is only more concentrated as iron burns: " + bath.phosphorus);
        assertEquals(Grade.COLD_SHORT_IRON, bath.tapGrade(), "Bessemer's licensees poured brittle iron");
        assertEquals(0f, bath.thomasSlagPhosphorus);
        assertEquals(0f, bath.silicaWithoutLime, "An acid lining is made of silica, so silica needs no lime");
    }

    @Test
    void anAcidLiningNeverShowsTheAfterblow()
    {
        final ConverterBath bath = phosphoric(false, ENOUGH_LIME);
        int guard = 0;
        while (bath.carbon > 0.2f && guard++ < 20000) second(bath, 0.4f);
        for (int i = 0; i < 300; i++)
        {
            second(bath, 0.4f);
            assertNotEquals(Flame.AFTERBLOW, bath.flame());
        }
        assertEquals(Flame.DROPPED, bath.flame());
    }

    @Test
    void aBasicLiningWithEnoughLimeBurnsThePhosphorusOutAfterTheCarbon()
    {
        final ConverterBath bath = phosphoric(true, ENOUGH_LIME);
        final float before = phosphorusGrams(bath);
        boolean sawAfterblow = false;
        int seconds = 0;
        while (bath.phosphorus > 0.05f && seconds++ < 7200)
        {
            final float carbonBefore = bath.carbon;
            final Flame flame = bath.flame();
            final Blow blow = second(bath, 0.4f);
            if (blow.phosphorusBurned() > 0f)
            {
                assertTrue(carbonBefore <= 0.06f + 1e-6f, "Phosphorus burned with " + carbonBefore + "% carbon still competing");
                assertTrue(bath.silicon < 0.02f);
                assertEquals(Flame.AFTERBLOW, flame);
                sawAfterblow = true;
            }
        }
        assertTrue(sawAfterblow);
        assertTrue(bath.phosphorus < ConverterBath.PHOSPHORUS_LIMIT_PERCENT, "Left with " + bath.phosphorus + "% in " + seconds + " s");
        assertEquals(Grade.WROUGHT_IRON, bath.tapGrade(), "Dephosphorised iron is iron again");
        assertEquals(before, phosphorusGrams(bath) + bath.thomasSlagPhosphorus * PHOSPHORUS_GRAMS_PER_MOL, 0.5f, "What left the metal is in the slag");
        assertEquals(0f, bath.silicaWithoutLime, "There was lime for the silicon too");
        // Silicon took 2 CaO a mole, phosphorus 2 CaO a mole
        final float siliconMoles = 2000f * ConverterBath.KG_PER_MILLIBUCKET * 1000f * 0.01f / SILICON_GRAMS_PER_MOL;
        assertEquals(ENOUGH_LIME - 2f * siliconMoles - 2f * bath.thomasSlagPhosphorus, bath.lime(), 0.05f);
    }

    @Test
    void anAfterblowIsHotterThanTheBlowBeforeIt()
    {
        // Same bath without phosphorus for comparison: the 745 kJ a mole of phosphorus gives is the reason the Thomas process works
        final ConverterBath dirty = phosphoric(true, ENOUGH_LIME);
        blowThrough(dirty, 0);
        final float atDrop = dirty.temperature;
        int guard = 0;
        while (dirty.phosphorus > 0.1f && guard++ < 7200) second(dirty, 0.4f);
        assertTrue(dirty.temperature > atDrop + 20f, "From " + atDrop + " to " + dirty.temperature);
    }

    @Test
    void theHeatOfPhosphorusIsSevenHundredAndFortyFiveKilojoulesAMole()
    {
        // Carbon already on the floor, no silicon, no gas carrying heat off (blast at the bath's own temperature)
        final ConverterBath bath = new ConverterBath();
        bath.basic = true;
        bath.addLime(100f);
        bath.metal = 1000f;
        bath.carbon = 0.03f;
        bath.phosphorus = 1f;
        bath.temperature = 1500f;
        final float capacity = 1000f * ConverterBath.KG_PER_MILLIBUCKET * 0.82f + 5f;
        final Blow blow = bath.blow(1f, 4.77f, 1500f);
        // 1 mol of oxygen burns 1 / 1.25 = 0.8 mol of phosphorus
        assertEquals(0.8f, blow.phosphorusBurned(), 1e-4f);
        assertEquals(1f, blow.oxygenUsed(), 1e-4f);
        assertEquals(0f, blow.ironBurned(), 1e-6f, "All the oxygen went to the phosphorus");
        assertEquals(1500f + 0.8f * 745f / capacity, bath.temperature, 0.05f);
        assertEquals(0.8f, bath.thomasSlagPhosphorus, 1e-4f);
        assertEquals(100f - 1.6f, bath.lime(), 1e-4f);
        assertEquals(7900f * 0.01f - 0.8f * PHOSPHORUS_GRAMS_PER_MOL, phosphorusGrams(bath), 0.01f);
    }

    @Test
    void aBasicLiningShortOfLimeLeavesPhosphorusInTheMetal()
    {
        final float lime = 14f;
        final ConverterBath bath = phosphoric(true, lime);
        final float siliconMoles = 2000f * ConverterBath.KG_PER_MILLIBUCKET * 1000f * 0.01f / SILICON_GRAMS_PER_MOL;
        blowThrough(bath, 600);
        assertEquals(0f, bath.lime(), 1e-3f, "All the lime is used");
        assertEquals((lime - 2f * siliconMoles) / 2f, bath.thomasSlagPhosphorus, 0.01f, "Only what the lime left over could take");
        assertTrue(bath.phosphorus > 0.5f, "Most phosphorus stays, got " + bath.phosphorus);
        assertEquals(Grade.COLD_SHORT_IRON, bath.tapGrade());
        assertEquals(Flame.DROPPED, bath.flame(), "With no lime there is no afterblow, and the iron burns");
        assertTrue(bath.slag > 100f, "The blast went on to burn iron, slag " + bath.slag);
    }

    @Test
    void aBasicLiningWithNoLimeAtAllLosesNoPhosphorusAndEatsItsLining()
    {
        final ConverterBath bath = phosphoric(true, 0f);
        final float before = phosphorusGrams(bath);
        blowThrough(bath, 200);
        assertEquals(before, phosphorusGrams(bath), 0.01f);
        assertEquals(0f, bath.thomasSlagPhosphorus);
        final float siliconMoles = 2000f * ConverterBath.KG_PER_MILLIBUCKET * 1000f * 0.01f / SILICON_GRAMS_PER_MOL;
        assertEquals(siliconMoles, bath.silicaWithoutLime, 0.01f, "All the silica found nothing to take it");
    }

    @Test
    void siliconTakesTwoMolesOfLimeAMoleAndOnlyWhatThereIs()
    {
        final ConverterBath bath = phosphoric(true, 3f);
        final Blow blow = bath.blow(2f, 9.5f, 20f);
        assertEquals(2f, blow.oxygenUsed(), 1e-3f);
        assertEquals(0f, bath.lime(), 1e-6f);
        assertEquals(2f - 1.5f, bath.silicaWithoutLime, 1e-5f, "2 mol of silicon burned, 1.5 of them took 3 mol of lime");
    }

    @Test
    void noBlowEverMakesMetalAndPhosphorusIsAccountedFor()
    {
        final ConverterBath bath = phosphoric(true, ENOUGH_LIME);
        final float gramsBefore = bath.metal * ConverterBath.KG_PER_MILLIBUCKET * 1000f;
        final float phosphorusBefore = phosphorusGrams(bath);
        float previous = bath.metal;
        for (int i = 0; i < 2500; i++)
        {
            second(bath, 0.4f);
            assertTrue(bath.metal <= previous + 1e-3f, "Mass rose at second " + i + ": " + previous + " -> " + bath.metal);
            previous = bath.metal;
        }
        assertTrue(bath.metal < 2000f);
        assertTrue(bath.metal * ConverterBath.KG_PER_MILLIBUCKET * 1000f < gramsBefore);
        assertEquals(phosphorusBefore, phosphorusGrams(bath) + bath.thomasSlagPhosphorus * PHOSPHORUS_GRAMS_PER_MOL, 0.5f);
        assertTrue(bath.lime() >= 0f && bath.phosphorus >= 0f && bath.silicon >= 0f && bath.carbon >= 0f);
    }

    @Test
    void theAfterblowFlameWatchesTheLimeAndThePhosphorus()
    {
        final ConverterBath bath = phosphoric(true, ENOUGH_LIME);
        assertEquals(Flame.SPARKS, bath.flame());
        blowThrough(bath, 0);
        int guard = 0;
        while (bath.flame() == Flame.DROPPED && guard++ < 7200) second(bath, 0.4f);
        assertEquals(Flame.AFTERBLOW, bath.flame());
        while (bath.flame() == Flame.AFTERBLOW && guard++ < 7200) second(bath, 0.4f);
        assertEquals(Flame.DROPPED, bath.flame(), "The white smoke thins as the phosphorus runs out");
    }

    @Test
    void limeThrownInColdTakesHeatFromTheBath()
    {
        final ConverterBath bath = new ConverterBath();
        bath.metal = 1000f;
        bath.temperature = 1500f;
        final float capacity = 1000f * ConverterBath.KG_PER_MILLIBUCKET * 0.82f + 5f; // 11.478 kJ/K
        bath.addLime(ConverterBath.LIME_MOLES_PER_POWDER);
        // 7.5 mol of CaO at 0.048 kJ/mol/K is 0.36 kJ/K, and the mixture settles between the two
        final float expected = (capacity * 1500f + 0.36f * 20f) / (capacity + 0.36f);
        assertEquals(expected, bath.temperature, 0.01f);
        assertEquals(1455.0f, bath.temperature, 0.1f);
        assertEquals(7.5f, bath.lime(), 1e-6f);
        bath.addLime(0f);
        bath.addLime(-3f);
        assertEquals(7.5f, bath.lime(), 1e-6f, "Lime cannot be taken out by adding a negative amount");
    }

    @Test
    void limeInAnEmptyVesselPaysItsHeatWhenIronIsPoured()
    {
        final ConverterBath bath = new ConverterBath();
        bath.addLime(ConverterBath.LIME_MOLES_PER_POWDER);
        assertEquals(0f, bath.temperature, "There is no bath to cool yet");
        bath.add(1000f, Grade.PIG_IRON, 1500f);
        final float capacity = 1000f * ConverterBath.KG_PER_MILLIBUCKET * 0.82f + 5f;
        assertEquals((capacity * 1500f + 0.36f * 20f) / (capacity + 0.36f), bath.temperature, 0.01f);
        // And it is only paid once
        final float temperature = bath.temperature;
        bath.add(1000f, Grade.PIG_IRON, temperature);
        assertEquals(temperature, bath.temperature, 1e-3f);
    }

    @Test
    void whatLimeIsLeftWhenTheVesselEmptiesGoesWithTheSlag()
    {
        final ConverterBath bath = phosphoric(true, ENOUGH_LIME);
        bath.tap(1000);
        assertEquals(ENOUGH_LIME, bath.lime(), 1e-3f, "Half the iron is still there, and so is the lime");
        bath.tap(2000);
        assertEquals(0f, bath.metal);
        assertEquals(0f, bath.lime(), "The next charge must not find lime from the last");
    }

    @Test
    void tappingTheLastOfTheMetalClearsPhosphorusToo()
    {
        final ConverterBath bath = phosphoric(true, 0f);
        bath.metal = 0.5f;
        bath.tap(10);
        assertEquals(0f, bath.metal);
        assertEquals(0f, bath.phosphorus);
    }
}
