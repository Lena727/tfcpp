package tfcpp.geology;

import org.junit.jupiter.api.Test;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasMath;

import static org.junit.jupiter.api.Assertions.*;

class SeamGasTest
{
    private static final int METHANE = Gas.METHANE.ordinal();
    private static final int CARBON_DIOXIDE = Gas.CARBON_DIOXIDE.ordinal();
    private static final int NITROGEN = Gas.FLUE_NITROGEN.ordinal();

    @Test
    void gasFromACoalFaceNearTheSurfaceIsBlackdampAndNotFiredamp()
    {
        final float[] cell = new float[Gas.COUNT];
        SeamGas.addAdsorbed(cell, SeamGas.Rank.BITUMINOUS, 8f, 10f);

        assertEquals(0f, cell[METHANE], "Air has been into the coal that shallow, and its methane is gone");
        assertEquals(1.3f, cell[CARBON_DIOXIDE], 1e-4f, "It is blackdamp in the shares of the pockets there");
        assertEquals(8.7f, cell[NITROGEN], 1e-4f);
        assertEquals(10f, cell[CARBON_DIOXIDE] + cell[NITROGEN], 1e-4f, "and no gas is made or lost doing so");
    }

    @Test
    void gasFromADeepFaceIsTheGasOfTheRank()
    {
        final float[] bituminous = new float[Gas.COUNT];
        SeamGas.addAdsorbed(bituminous, SeamGas.Rank.BITUMINOUS, 60f, 10f);
        assertEquals(9.5f, bituminous[METHANE], 1e-4f);
        assertEquals(0.5f, bituminous[CARBON_DIOXIDE], 1e-4f);
        assertEquals(0f, bituminous[NITROGEN]);

        final float[] lignite = new float[Gas.COUNT];
        SeamGas.addAdsorbed(lignite, SeamGas.Rank.LIGNITE, 60f, 10f);
        assertEquals(2f, lignite[METHANE], 1e-4f);
        assertEquals(8f, lignite[CARBON_DIOXIDE], 1e-4f);
    }

    @Test
    void theBoundaryIsTheOneThePocketsUse()
    {
        final float[] above = new float[Gas.COUNT];
        SeamGas.addAdsorbed(above, SeamGas.Rank.BITUMINOUS, 14.9f, 1f);
        assertEquals(0f, above[METHANE], "Shallower than 15 m");

        final float[] at = new float[Gas.COUNT];
        SeamGas.addAdsorbed(at, SeamGas.Rank.BITUMINOUS, 15f, 1f);
        assertTrue(at[METHANE] > 0.9f, "From 15 m down");
    }

    @Test
    void blackdampTakesTheOxygenOutOfTheAir()
    {
        final float[] cell = new float[Gas.COUNT];
        SeamGas.addAdsorbed(cell, SeamGas.Rank.BITUMINOUS, 5f, 12f);

        assertTrue(GasMath.oxygenFraction(cell) < 0.16f, "A cell of it is air that puts a flame out and is dangerous to breathe, got " + GasMath.oxygenFraction(cell));
    }
}
