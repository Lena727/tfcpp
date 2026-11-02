package tfcpp.chemistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SulfurBurningTest
{
    /** A burner of 40 mol of gas with 20 mol of air, so 4.19 mol of oxygen, which is 10.5% of the gas. */
    private static final float GAS = 40f, AIR = 20f;

    @Test
    void aSmallBurnerTakesItsRate()
    {
        final SulfurBurning.Result burn = SulfurBurning.burn(100f, AIR, GAS, 1f);
        assertEquals(SulfurBurning.MOLES_PER_SECOND, burn.sulfurBurned(), 1e-6f, "2 g/s of sulfur");
        assertEquals(297f * SulfurBurning.MOLES_PER_SECOND, burn.heatKilojoules(), 1e-3f, "297 kJ for each mole");
    }

    @Test
    void sulfurCanRunOutFirst()
    {
        assertEquals(0.01f, SulfurBurning.burn(0.01f, AIR, GAS, 1f).sulfurBurned(), 1e-6f);
    }

    @Test
    void oxygenCanRunOutFirst()
    {
        // 0.3 mol of air holds 0.06285 mol of oxygen, which is 6.3% of 1 mol of gas, enough for 0.06285 mol of sulfur
        final SulfurBurning.Result burn = SulfurBurning.burn(100f, 0.3f, 1f, 10f);
        assertEquals(0.3f * 0.2095f, burn.sulfurBurned(), 1e-6f, "the rate would want 0.62 mol in ten seconds, but the oxygen allows less");
    }

    @Test
    void stopsBelowFivePercentOxygen()
    {
        // 20 mol of air holds 4.19 mol of O2, which is 4.2% of 100 mol of gas
        assertEquals(SulfurBurning.Result.NONE, SulfurBurning.burn(100f, 20f, 100f, 1f), "Below the least share the flame smoulders out");
        // 30 mol of air holds 6.29 mol of O2, which is 6.3%
        assertTrue(SulfurBurning.burn(100f, 30f, 100f, 1f).sulfurBurned() > 0f, "Above it, the sulfur burns");
    }

    @Test
    void oneSulfurDioxideForEachSulfur()
    {
        final SulfurBurning.Result burn = SulfurBurning.burn(100f, AIR, GAS, 1f);
        assertEquals(burn.sulfurBurned(), burn.sulfurDioxide(), 0f);
    }

    @Test
    void nitrogenAndOxygenMakeUpTheAirConsumed()
    {
        final SulfurBurning.Result burn = SulfurBurning.burn(100f, AIR, GAS, 1f);
        // The oxygen taken is one mole for each mole of sulfur, and the rest of the air is nitrogen
        assertEquals(burn.air(), burn.nitrogenLeftBehind() + burn.sulfurBurned(), 1e-5f, "nitrogen plus the oxygen burned is the air taken");
        assertEquals(0.7905f * burn.air(), burn.nitrogenLeftBehind(), 1e-6f);
    }

    @Test
    void noSulfurNoAirOrNoTimeMakesNothing()
    {
        assertEquals(SulfurBurning.Result.NONE, SulfurBurning.burn(0f, AIR, GAS, 1f));
        assertEquals(SulfurBurning.Result.NONE, SulfurBurning.burn(100f, 0f, GAS, 1f));
        assertEquals(SulfurBurning.Result.NONE, SulfurBurning.burn(100f, AIR, GAS, 0f));
    }
}
