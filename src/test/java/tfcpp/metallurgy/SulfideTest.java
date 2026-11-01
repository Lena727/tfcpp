package tfcpp.metallurgy;

import org.junit.jupiter.api.Test;
import tfcpp.pneumatics.gas.Gas;

import static org.junit.jupiter.api.Assertions.*;

class SulfideTest
{
    @Test
    void anIngotOfCopperIsAboutOneKilogram()
    {
        final float grams = Sulfide.TETRAHEDRITE.metalMoles(100f) * 63.55f;
        assertEquals(896f, grams, 1f);
    }

    @Test
    void tetrahedriteReleasesThirteenSulfursPerTwelveCoppers()
    {
        final float copper = Sulfide.TETRAHEDRITE.metalMoles(25f);
        final float[] fumes = Sulfide.TETRAHEDRITE.fumes(25f);

        assertEquals(copper * 13f / 12f, fumes[Gas.SULFUR_DIOXIDE.ordinal()], 1e-3f);
    }

    @Test
    void arsenicAndAntimonyAtomsAreConserved()
    {
        final float copper = Sulfide.TETRAHEDRITE.metalMoles(25f);
        final float[] fumes = Sulfide.TETRAHEDRITE.fumes(25f);

        // Cu12(As,Sb)4S13 holds 4 semimetal atoms per 12 copper, and each oxide molecule carries two of them
        final float semimetalAtoms = 2f * (fumes[Gas.ARSENIC_TRIOXIDE.ordinal()] + fumes[Gas.ANTIMONY_TRIOXIDE.ordinal()]);
        assertEquals(copper * 4f / 12f, semimetalAtoms, 1e-3f);
    }

    @Test
    void roastingSphaleriteTakesThreeOxygenPerTwoZinc()
    {
        final float zinc = Sulfide.SPHALERITE.metalMoles(25f);
        final float[] fumes = Sulfide.SPHALERITE.fumes(25f);

        assertEquals(zinc, fumes[Gas.SULFUR_DIOXIDE.ordinal()], 1e-3f);
        assertEquals(zinc * 1.5f * 3.773f, fumes[Gas.FLUE_NITROGEN.ordinal()], 0.02f);
    }

    @Test
    void openRoastingOfCinnabarLosesAllTheMercury()
    {
        final float[] fumes = Sulfide.CINNABAR.fumes(25f);
        assertEquals(Sulfide.CINNABAR.metalMoles(25f), fumes[Gas.MERCURY_VAPOR.ordinal()], 1e-4f);
    }

    @Test
    void onlyTetrahedriteCarriesArsenic()
    {
        for (Sulfide mineral : Sulfide.values())
        {
            final float arsenic = mineral.fumes(25f)[Gas.ARSENIC_TRIOXIDE.ordinal()];
            assertEquals(mineral == Sulfide.TETRAHEDRITE, arsenic > 0, mineral.name());
        }
    }

    @Test
    void galenaGivesOneSulfurForEachLeadAndSomeFume()
    {
        final float lead = Sulfide.GALENA.metalMoles(25f);
        assertEquals(25f * 11.34f / 207.2f, lead, 1e-4f);
        final float[] gases = Sulfide.GALENA.fumes(25f);
        assertEquals(lead, gases[tfcpp.pneumatics.gas.Gas.SULFUR_DIOXIDE.ordinal()], 1e-4f, "PbS holds one sulfur for each lead");
        assertEquals(0.03f * lead, gases[tfcpp.pneumatics.gas.Gas.LEAD_OXIDE_FUME.ordinal()], 1e-5f, "A few percent is lost as fume");
    }
}
