package tfcpp.metallurgy;

import org.junit.jupiter.api.Test;

import tfcpp.pneumatics.gas.Combustion;
import tfcpp.pneumatics.gas.GasMath;

import static org.junit.jupiter.api.Assertions.*;

class DraughtTest
{
    private static final float CHARCOAL_NATURAL = 1000f, CHARCOAL_TOP = Draught.fullBlastTemperature(1100f);

    private static float charcoal(float air, float oxygen)
    {
        return Draught.temperature(CHARCOAL_NATURAL, CHARCOAL_TOP, air, oxygen);
    }

    private static float fuel(String item, float burn, int blowers)
    {
        return Draught.temperature(DraughtFuel.natural(item, burn), Draught.fullBlastTemperature(burn), Draught.pipeAir(blowers), 1f);
    }

    @Test
    void theTopOfEachFuelIsWhatFullBellowsGaveBefore()
    {
        assertEquals(1600f, CHARCOAL_TOP, 1e-3f);
        assertEquals(1700f, Draught.fullBlastTemperature(1200f), 1e-3f); // coal
        assertEquals(1500f, Draught.fullBlastTemperature(1000f), 1e-3f); // lignite
        assertEquals(1750f, Draught.fullBlastTemperature(1250f), 1e-3f); // coke
        assertEquals(1600f, Draught.fullBlastTemperature(1100f), 1e-3f); // lignite char
    }

    @Test
    void charcoalOnTheNaturalDraughtOfFreshAirIsAThousandDegrees()
    {
        assertEquals(1000f, charcoal(0f, 1f), 2f);
    }

    @Test
    void oneBlowerGivesAboutTwelveHundredAndFourAndEachFuelItsOwn()
    {
        assertEquals(1203.7f, fuel("minecraft:charcoal", 1100f, 1), 2f);
        assertEquals(1228.2f, fuel("minecraft:coal", 1200f, 1), 2f);
        assertEquals(1216.7f, fuel("tfcpp:coke", 1250f, 1), 2f);
        assertEquals(1179.6f, fuel("tfcpp:lignite_char", 1100f, 1), 2f);
        assertEquals(1046.1f, fuel("tfc:ore/lignite", 1000f, 1), 2f);
    }

    @Test
    void oneBlowerReachesNeitherBorosilicateNorFireClay()
    {
        for (DraughtFuel.Fuel fuel : DraughtFuel.FUELS)
        {
            assertTrue(fuel(fuel.item(), fuel.burnTemperature(), 1) < 1250f, fuel.item());
        }
    }

    @Test
    void twoOrThreeBlowersReachBorosilicateAndFireClay()
    {
        assertEquals(1346.9f, fuel("minecraft:charcoal", 1100f, 2), 2f);
        assertEquals(1404.6f, fuel("minecraft:coal", 1200f, 2), 2f);
        assertEquals(1422.3f, fuel("tfcpp:coke", 1250f, 2), 2f);
        assertEquals(1333.9f, fuel("tfcpp:lignite_char", 1100f, 2), 2f);
        assertEquals(1219.4f, fuel("tfc:ore/lignite", 1000f, 2), 2f);
        assertTrue(fuel("minecraft:charcoal", 1100f, 2) > 1300f && fuel("minecraft:charcoal", 1100f, 3) > 1300f);
        assertEquals(1459.8f, fuel("minecraft:charcoal", 1100f, 3), 2f);
    }

    @Test
    void blowersAddUpToFourAndNoFurther()
    {
        assertEquals(0f, Draught.pipeAir(0), 1e-6f);
        assertEquals(0.22f, Draught.pipeAir(1), 1e-6f);
        assertEquals(0.88f, Draught.pipeAir(4), 1e-6f);
        assertEquals(0.88f, Draught.pipeAir(9), 1e-6f);
        assertEquals(0f, Draught.pipeAir(-1), 1e-6f);
        assertEquals(1554.1f, fuel("minecraft:charcoal", 1100f, 4), 2f);
    }

    @Test
    void aStrongWindStaysBelowTheMeltingOfCopper()
    {
        assertEquals(1055f, charcoal(Draught.MOST_WIND_AIR, 1f), 2f);
        assertTrue(charcoal(Draught.MOST_WIND_AIR, 1f) < 1080f);
    }

    @Test
    void fullBellowsGiveTheTopOfTheFuel()
    {
        assertEquals(1600f, charcoal(1f, 1f), 2f);
        assertEquals(1600f, charcoal(Draught.totalAir(1f, Draught.pipeAir(4), 0.05f), 1f), 2f, "More air than a full bellows gives nothing more");
    }

    @Test
    void sourcesOfAirDoNotAddToEachOtherButTheBestCounts()
    {
        assertEquals(0.44f, Draught.totalAir(0f, 0.44f, 0.05f), 1e-6f, "Wind adds nothing to blowers");
        assertEquals(0.5f, Draught.totalAir(0.5f, 0.22f, 0.05f), 1e-6f, "Nor blowers to a bellows");
        assertEquals(0.05f, Draught.totalAir(0f, 0f, 0.05f), 1e-6f);
        assertEquals(1f, Draught.totalAir(1f, 0.88f, 0.05f), 1e-6f);
        assertEquals(0.22f, Draught.totalAir(0.1f, 0.22f, 0.05f), 1e-6f);
    }

    @Test
    void cokeOnTheNaturalDraughtBarelyKeepsAlight()
    {
        assertEquals(850f, fuel("tfcpp:coke", 1250f, 0), 2f);
        assertEquals(1750f, Draught.temperature(850f, 1750f, 1f, 1f), 2f);
    }

    @Test
    void eachFuelOfTheTableHasItsOwnNaturalTemperature()
    {
        assertEquals(1000f, DraughtFuel.natural("minecraft:charcoal", 1100f));
        assertEquals(950f, DraughtFuel.natural("tfcpp:lignite_char", 1100f));
        assertEquals(950f, DraughtFuel.natural("tfc:ore/bituminous_coal", 1200f));
        assertEquals(850f, DraughtFuel.natural("tfcpp:coke", 1250f));
        assertEquals(750f, DraughtFuel.natural("tfc:ore/lignite", 1000f));
    }

    @Test
    void withNoFuelKnownTheLowestFuelOfThatTemperatureIsTaken()
    {
        assertEquals(950f, DraughtFuel.natural(null, 1100f), "Charcoal and lignite char both burn at 1100 in TFC: the lower counts");
        assertEquals(950f, DraughtFuel.natural(null, 1200f));
        assertEquals(850f, DraughtFuel.natural(null, 1250f));
        assertEquals(750f, DraughtFuel.natural(null, 1000f));
        assertEquals(950f, DraughtFuel.natural("somemod:peat", 1100f), "An unknown item is no better than none");
    }

    @Test
    void anUnknownFuelBurnsAtWhatTfcHasForIt()
    {
        assertEquals(650f, DraughtFuel.natural(null, 650f));
        assertEquals(650f, DraughtFuel.natural("somemod:peat", 650f));
        assertEquals(975f, Draught.temperature(650f, Draught.fullBlastTemperature(650f), 1f, 1f), 2f);
    }

    @Test
    void airRaisesTheTemperatureByTheFourthPowerNotLinearly()
    {
        final float half = charcoal(0.5f, 1f);
        assertTrue(half > (1000f + 1600f) / 2f - 1f && half < 1600f);
        assertEquals(1000f, charcoal(-1f, 1f), 2f, "No negative air");
    }

    @Test
    void bellowsAirIsFullAtTheTicksWhereTfcReachesTheCap()
    {
        assertEquals(1f, Draught.bellowsAir(Draught.FULL_BELLOWS_AIR_TICKS), 1e-6f);
        assertEquals(1f, Draught.bellowsAir(600), 1e-6f);
        assertEquals(0f, Draught.bellowsAir(0), 1e-6f);
        assertEquals(0.5f, Draught.bellowsAir(62), 0.01f);
        assertEquals(4f * Draught.FULL_BELLOWS_AIR_TICKS, Draught.MOST_FROM_A_BLAST);
    }

    @Test
    void windGivesNothingInACalmAndAtMostATwentiethOfTheBellows()
    {
        assertEquals(0f, Draught.windAir(0f), 1e-6f);
        assertEquals(Draught.MOST_WIND_AIR, Draught.windAir(1f), 1e-6f);
        assertEquals(0.05f, Draught.MOST_WIND_AIR, 1e-6f);
        assertEquals(Draught.MOST_WIND_AIR, Draught.windAir(10f / 32f), 1e-5f, "10 m/s is the speed that gives the most");
        assertEquals(Draught.MOST_WIND_AIR / 2f, Draught.windAir(5f / 32f), 1e-5f);
    }

    @Test
    void theOxygenFactorIsOneInFreshAirAndLowerWhereTheRoomIsStuffy()
    {
        assertEquals(1f, Draught.oxygenFactor(GasMath.OXYGEN_IN_AIR), 1e-6f);
        final float factor = Draught.oxygenFactor(0.165f);
        assertEquals(0.5028f, factor, 1e-3f);
        final float cooler = charcoal(0f, factor);
        assertEquals(799f, cooler, 2f);
        assertTrue(cooler < 1000f);
        assertEquals(0f, Draught.oxygenFactor(Combustion.EXTINCTION_OXYGEN), 1e-6f);
        assertEquals(20f, charcoal(0f, 0f), 1e-3f, "A fire never cools a hearth below the room");
    }

    @Test
    void anAirThatIsBarelyStuffyBringsNaturalCharcoalBelowAThousand()
    {
        assertTrue(charcoal(0f, Draught.oxygenFactor(0.205f)) < 1000f, "Natural charcoal is a thousand degrees only in fresh air");
    }

    @Test
    void richOxygenHelpsButOnlyToAPoint()
    {
        assertEquals(Draught.MOST_OXYGEN_FACTOR, Draught.oxygenFactor(0.6f), 1e-6f);
    }

    @Test
    void thePipeAndBellowsTablesAreOnTheSameScale()
    {
        assertEquals(1000f, DraughtFuel.natural("minecraft:charcoal", 1100f) + 0f, 1e-6f);
        assertEquals(Draught.fullBlastTemperature(1100f), Draught.temperature(1000f, 1600f, 1f, 1f), 2f);
    }
}
