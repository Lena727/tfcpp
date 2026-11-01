package tfcpp.chemistry;

import org.junit.jupiter.api.Test;
import tfcpp.pneumatics.gas.Combustion;
import tfcpp.pneumatics.gas.Flammability;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasMath;

import static org.junit.jupiter.api.Assertions.*;

class BurnerMathTest
{
    private static final int H2 = Gas.HYDROGEN.ordinal(), CH4 = Gas.METHANE.ordinal(), CO = Gas.CARBON_MONOXIDE.ordinal(), CO2 = Gas.CARBON_DIOXIDE.ordinal();
    private static final int H2S = Gas.HYDROGEN_SULFIDE.ordinal(), SO2 = Gas.SULFUR_DIOXIDE.ordinal(), NH3 = Gas.AMMONIA.ordinal(), N2 = Gas.FLUE_NITROGEN.ordinal();

    /** The gas that this many pieces of coal give. */
    private static float[] cokeOvenGas(double pieces)
    {
        final float[] gas = CarbonizationTable.COAL.yield().gas().clone();
        for (int i = 0; i < gas.length; i++)
        {
            gas[i] *= pieces;
        }
        return gas;
    }

    private static float[] gas(Object... pairs)
    {
        final float[] gases = new float[Gas.COUNT];
        for (int i = 0; i < pairs.length; i += 2)
        {
            gases[((Gas) pairs[i]).ordinal()] = (Float) pairs[i + 1];
        }
        return gases;
    }

    @Test
    void cokeOvenGasGivesFourHundredKilojoulesAMole()
    {
        final float perMole = BurnerMath.heatPerMole(cokeOvenGas(1), 0f);
        assertEquals(413f, perMole, 3f);
        assertEquals(BurnerMath.FULL_TEMPERATURE, BurnerMath.temperature(perMole));
    }

    @Test
    void aLeanMixtureBurnsCoolerInProportion()
    {
        assertEquals(BurnerMath.FULL_TEMPERATURE, BurnerMath.temperature(150f));
        assertEquals(20f + 1080f * 0.5f, BurnerMath.temperature(75f), 1e-3f);
        assertEquals(20f, BurnerMath.temperature(0f), 1e-3f);
        assertTrue(BurnerMath.temperature(30f) < BurnerMath.temperature(60f));
    }

    @Test
    void aStrongFlameHeatsToItsOwnTemperature()
    {
        assertEquals(1100f, BurnerMath.heatedTemperature(1100f, BurnerMath.FULL_HEAT_KILOWATTS), 1e-3f);
        assertEquals(1100f, BurnerMath.heatedTemperature(1100f, 400f), 1e-3f);
        assertEquals(560f, BurnerMath.heatedTemperature(560f, 1000f), 1e-3f);
    }

    @Test
    void aWeakFlameHeatsWithTheFourthRootOfItsPower()
    {
        // 12.5 kW is a sixteenth of 200 kW, and the fourth root of a sixteenth is a half
        assertEquals(20f + (1100f - 20f) * 0.5f, BurnerMath.heatedTemperature(1100f, 12.5f), 1e-2f);
        assertEquals(20f + (800f - 20f) * 0.5f, BurnerMath.heatedTemperature(800f, 12.5f), 1e-2f);
        assertTrue(BurnerMath.heatedTemperature(1100f, 50f) > BurnerMath.heatedTemperature(1100f, 25f));
        assertTrue(BurnerMath.heatedTemperature(1100f, 1f) > 20f);
    }

    @Test
    void noFlameHeatsNothing()
    {
        assertEquals(20f, BurnerMath.heatedTemperature(1100f, 0f), 1e-3f);
    }

    @Test
    void airInThePipeThinsTheHeatPerMole()
    {
        final float[] gas = cokeOvenGas(1);
        final float neat = BurnerMath.heatPerMole(gas, 0f);
        final float diluted = BurnerMath.heatPerMole(gas, GasMath.total(gas) * 2f);
        assertEquals(neat / 3f, diluted, 1e-3f);
    }

    @Test
    void powerLimitsHowMuchIsTakenEachSecond()
    {
        final float[] hydrogen = gas(Gas.HYDROGEN, 1000f);
        final float share = BurnerMath.share(hydrogen, 0f, 1f, 1e9f);
        assertEquals(400f / (1000f * 242f), share, 1e-6f);
        assertEquals(400f, share * BurnerMath.fuelKilojoules(hydrogen), 1e-2f);
        assertEquals(2f * share, BurnerMath.share(hydrogen, 0f, 2f, 1e9f), 1e-6f);
    }

    @Test
    void aSmallStoreIsAllTakenAtOnce()
    {
        assertEquals(1f, BurnerMath.share(gas(Gas.METHANE, 0.1f), 0f, 1f, 1e9f), 1e-6f);
    }

    @Test
    void pressureLimitsItToWhatWouldEvenThePipeWithTheRoom()
    {
        final float[] hydrogen = gas(Gas.HYDROGEN, 1000f);
        assertEquals(0.001f, BurnerMath.share(hydrogen, 0f, 1f, 1f), 1e-6f); // Less than the power would take
        assertEquals(0f, BurnerMath.share(hydrogen, 0f, 1f, 0f));
    }

    @Test
    void withoutFuelNothingIsTaken()
    {
        assertEquals(0f, BurnerMath.share(gas(Gas.CARBON_DIOXIDE, 50f, Gas.AMMONIA, 3f), 10f, 1f, 1e9f));
        assertEquals(0f, BurnerMath.share(new float[Gas.COUNT], 0f, 1f, 1e9f));
        assertEquals(0f, BurnerMath.share(cokeOvenGas(1), 0f, 0f, 1e9f));
    }

    @Test
    void carbonAndSulfurAreConservedAndFuelIsGone()
    {
        final float[] drawn = gas(Gas.METHANE, 2f, Gas.CARBON_MONOXIDE, 1f, Gas.CARBON_DIOXIDE, 0.5f, Gas.HYDROGEN_SULFIDE, 0.2f, Gas.HYDROGEN, 3f, Gas.AMMONIA, 0.1f);
        for (float oxygen : new float[] {GasMath.OXYGEN_IN_AIR, 0.15f, 0.125f})
        {
            final BurnerMath.Products products = BurnerMath.burn(drawn, oxygen);
            final float[] out = products.gases();
            assertEquals(3.5f, out[CO] + out[CO2], 1e-4f, "carbon at " + oxygen);
            assertEquals(0.2f, out[SO2], 1e-5f, "sulfur at " + oxygen);
            assertEquals(0.1f, out[NH3], 1e-6f);
            assertEquals(0f, out[H2]);
            assertEquals(0f, out[CH4]);
            assertEquals(0f, out[H2S]);
        }
    }

    @Test
    void methaneNeedsTwoMolesOfOxygenAndGivesItsCarbonAsDioxide()
    {
        final BurnerMath.Products clean = BurnerMath.burn(gas(Gas.METHANE, 10f), GasMath.OXYGEN_IN_AIR);
        final float share = Combustion.carbonMonoxideShare(0.01f, GasMath.OXYGEN_IN_AIR);
        assertEquals(10f * (4f - share) / 2f, clean.oxygen(), 1e-4f);
        assertEquals(10f * (1f - share), clean.gases()[CO2], 1e-4f);
        assertEquals(802f * 10f - 283f * 10f * share, clean.kilojoules(), 1e-2f);
    }

    @Test
    void hydrogenNeedsHalfAMoleAndLeavesWater()
    {
        final BurnerMath.Products products = BurnerMath.burn(gas(Gas.HYDROGEN, 8f), GasMath.OXYGEN_IN_AIR);
        assertEquals(4f, products.oxygen(), 1e-5f);
        assertEquals(8f * 242f, products.kilojoules(), 1e-2f);
        assertEquals(0f, products.gases()[CO] + products.gases()[CO2]);
    }

    @Test
    void aStarvedFlameLeavesCarbonMonoxideAndGivesLessHeat()
    {
        final float[] drawn = gas(Gas.METHANE, 5f, Gas.CARBON_MONOXIDE, 5f);
        final BurnerMath.Products clean = BurnerMath.burn(drawn, GasMath.OXYGEN_IN_AIR);
        final BurnerMath.Products starved = BurnerMath.burn(drawn, 0.125f);

        assertTrue(starved.gases()[CO] > clean.gases()[CO] * 10f);
        assertTrue(starved.kilojoules() < clean.kilojoules());
        assertTrue(starved.oxygen() < clean.oxygen());
    }

    @Test
    void theRoomLosesExactlyTheOxygenThatBurned()
    {
        // What is added to a cell displaces air, which carries 20.95 percent oxygen
        final float[] drawn = gas(Gas.METHANE, 6f, Gas.HYDROGEN, 4f);
        final BurnerMath.Products products = BurnerMath.burn(drawn, GasMath.OXYGEN_IN_AIR);
        assertEquals(products.oxygen(), GasMath.OXYGEN_IN_AIR * GasMath.total(products.gases()), 1e-3f);
    }

    @Test
    void sulfurDioxideFromHydrogenSulfideNeedsItsOxygenToo()
    {
        final BurnerMath.Products products = BurnerMath.burn(gas(Gas.HYDROGEN_SULFIDE, 2f), GasMath.OXYGEN_IN_AIR);
        assertEquals(3f, products.oxygen(), 1e-5f); // 1.5 O2 for each
        assertEquals(2f, products.gases()[SO2], 1e-6f);
    }

    @Test
    void coalGasBurnsInTheRoomsAirOnItsOwnWeightOfOxygen()
    {
        final BurnerMath.Products products = BurnerMath.burn(cokeOvenGas(1), GasMath.OXYGEN_IN_AIR);
        // 21 H2 (10.5) + 14 CH4 (about 27.9) + 3 CO (1.5) + 0.2 H2S (0.3) moles of oxygen
        assertEquals(40.2f, products.oxygen(), 0.2f);
    }

    @Test
    void cokeOvenGasPureOrInAirTooRichIsNotFlammable()
    {
        final float[] neat = cokeOvenGas(1);
        assertFalse(Flammability.isFlammable(BurnerMath.asCell(neat, 0f)));
        assertFalse(Flammability.isFlammable(BurnerMath.asCell(new float[Gas.COUNT], 400f)));
    }

    @Test
    void cokeOvenGasMixedWithAirBurns()
    {
        final float[] gas = cokeOvenGas(1);
        final float total = GasMath.total(gas);
        assertTrue(Flammability.isFlammable(BurnerMath.asCell(gas, total * 9f)), "10 percent in air");
        assertFalse(Flammability.isFlammable(BurnerMath.asCell(gas, total * 0.5f)), "two thirds gas is above the upper limit");
        assertFalse(Flammability.isFlammable(BurnerMath.asCell(gas, total * 200f)), "far too lean");
    }

    @Test
    void cellKeepsTheSharesOfAPipe()
    {
        final float[] cell = BurnerMath.asCell(gas(Gas.METHANE, 1f), 9f);
        assertEquals(0.1f, GasMath.fraction(cell[CH4]), 1e-5f);
    }

    @Test
    void anExplosionBurnsEverythingTheAirHasOxygenFor()
    {
        // Methane at 9.5 percent in air is about the right mixture to use up all the oxygen
        final float[] gases = gas(Gas.METHANE, 9.5f);
        final BurnerMath.Explosion explosion = BurnerMath.deflagrate(gases, 19f / GasMath.OXYGEN_IN_AIR);

        assertEquals(9.5f * 802f, explosion.kilojoules(), 2f);
        assertEquals(0f, gases[CH4], 0.1f);
        assertEquals(9.5f, gases[CO2], 0.1f);
        assertEquals(19f / GasMath.OXYGEN_IN_AIR, explosion.airUsed(), 0.1f);
    }

    @Test
    void aRichMixtureLeavesFuelBehindAndBurnsOnlyWhatOxygenAllows()
    {
        final float[] gases = gas(Gas.METHANE, 50f);
        final BurnerMath.Explosion explosion = BurnerMath.deflagrate(gases, 50f);

        final float oxygen = 50f * GasMath.OXYGEN_IN_AIR;
        assertEquals(oxygen / 2f * 802f, explosion.kilojoules(), 1f);
        assertEquals(50f - oxygen / 2f, gases[CH4], 1e-3f);
        assertEquals(50f, explosion.airUsed(), 1e-3f);
    }

    @Test
    void carbonAndSulfurAreConservedInAnExplosionToo()
    {
        final float[] gases = gas(Gas.METHANE, 1f, Gas.CARBON_MONOXIDE, 1f, Gas.HYDROGEN_SULFIDE, 0.5f, Gas.AMMONIA, 0.5f, Gas.HYDROGEN, 2f);
        BurnerMath.deflagrate(gases, 400f);
        assertEquals(2f, gases[CO2], 1e-5f);
        assertEquals(0.5f, gases[SO2], 1e-5f);
        assertEquals(0f, gases[NH3] + gases[H2] + gases[CH4] + gases[CO] + gases[H2S], 1e-5f);
    }

    @Test
    void anAirOnlyPipeHasNothingToExplode()
    {
        final BurnerMath.Explosion explosion = BurnerMath.deflagrate(new float[Gas.COUNT], 100f);
        assertEquals(0f, explosion.kilojoules());
        assertEquals(0f, explosion.airUsed());
    }

    @Test
    void peakPressureRisesWithEnergyPerVolumeUpToTheRealLimit()
    {
        assertEquals(40f, BurnerMath.peakKilopascals(100f, 1f), 1e-3f); // (gamma - 1) E / V
        assertEquals(20f, BurnerMath.peakKilopascals(100f, 2f), 1e-3f);
        assertEquals(BurnerMath.MAXIMUM_EXPLOSION_KILOPASCALS, BurnerMath.peakKilopascals(1e6f, 1f));
        assertEquals(0f, BurnerMath.peakKilopascals(100f, 0f));
    }
}
