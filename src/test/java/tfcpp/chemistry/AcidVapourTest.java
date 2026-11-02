package tfcpp.chemistry;

import org.junit.jupiter.api.Test;
import tfcpp.pneumatics.gas.Gas;

import static org.junit.jupiter.api.Assertions.*;

class AcidVapourTest
{
    @Test
    void sulfuricAcidGivesNothingButSteam()
    {
        for (float moles : AcidVapour.of(Liquid.SULFURIC_ACID, 10.0))
        {
            assertEquals(0f, moles);
        }
        for (float moles : AcidVapour.of(Liquid.CHAMBER_ACID, 10.0))
        {
            assertEquals(0f, moles);
        }
    }

    @Test
    void nitricAcidFumesFiveMolesOfOxidesPerLitre()
    {
        final float[] gas = AcidVapour.of(Liquid.NITRIC_ACID, 0.175);
        assertEquals(0.875f, gas[Gas.NITROGEN_OXIDES.ordinal()], 1e-6f);
        assertEquals(0f, gas[Gas.HYDROGEN_CHLORIDE.ordinal()]);
    }

    @Test
    void hydrochloricAcidFumesTenMolesOfChlorideAndNoMoreThanItHolds()
    {
        final float[] gas = AcidVapour.of(Liquid.HYDROCHLORIC_ACID, 0.67);
        assertEquals(6.7f, gas[Gas.HYDROGEN_CHLORIDE.ordinal()], 1e-5f);

        final double litres = 1.0;
        final double heldMoles = Liquid.HYDROCHLORIC_ACID.kilogramsPerLitre() * litres * 0.32 * 1000.0 / Gas.HYDROGEN_CHLORIDE.molarMass();
        assertTrue(AcidVapour.of(Liquid.HYDROCHLORIC_ACID, litres)[Gas.HYDROGEN_CHLORIDE.ordinal()] <= heldMoles * 1.02, "A litre cannot give off more chloride than the 32% acid holds");
    }

    @Test
    void theOtherLiquidsGiveNoGas()
    {
        for (Liquid liquid : new Liquid[] {Liquid.WOOD_SPIRIT, Liquid.COAL_TAR, Liquid.AMMONIA_LIQUOR})
        {
            for (float moles : AcidVapour.of(liquid, 5.0))
            {
                assertEquals(0f, moles);
            }
        }
    }
}
