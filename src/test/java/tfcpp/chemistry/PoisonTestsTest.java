package tfcpp.chemistry;

import org.junit.jupiter.api.Test;

import tfcpp.chemistry.PoisonTests.Mirror;
import tfcpp.pneumatics.gas.Gas;

import static org.junit.jupiter.api.Assertions.*;

class PoisonTestsTest
{
    /** Moles per m3 of an oxide that make this many mg/m3, from its molar mass in g/mol. */
    private static float moles(float milligramsPerCubicMetre, float molarMass)
    {
        return milligramsPerCubicMetre / (molarMass * 1000f);
    }

    @Test
    void theMolarMassesAgreeWithTheGasEnum()
    {
        assertEquals(Gas.ARSENIC_TRIOXIDE.molarMass(), PoisonTests.AS2O3_MOLAR_MASS, 1e-4f);
        assertEquals(Gas.ANTIMONY_TRIOXIDE.molarMass(), PoisonTests.SB2O3_MOLAR_MASS, 1e-4f);
        assertEquals(Gas.MERCURY_VAPOR.molarMass(), PoisonTests.HG_MOLAR_MASS, 1e-4f);
    }

    @Test
    void aSubMilligramConcentrationShowsNoMirror()
    {
        assertEquals(Mirror.NONE, PoisonTests.marsh(moles(0.9f, PoisonTests.AS2O3_MOLAR_MASS), 0f));
    }

    @Test
    void aMilligramShowsAFaintMirror()
    {
        assertEquals(Mirror.FAINT, PoisonTests.marsh(moles(1.01f, PoisonTests.AS2O3_MOLAR_MASS), 0f));
    }

    @Test
    void aMirrorStaysFaintBelowTwentyMilligrams()
    {
        assertEquals(Mirror.FAINT, PoisonTests.marsh(moles(19f, PoisonTests.AS2O3_MOLAR_MASS), 0f));
    }

    @Test
    void twentyMilligramsShowsADenseMirror()
    {
        assertEquals(Mirror.STRONG, PoisonTests.marsh(moles(21f, PoisonTests.AS2O3_MOLAR_MASS), 0f));
    }

    @Test
    void arsenicAndAntimonyAreSummed()
    {
        // Half a milligram of each is one milligram in all, which is a faint mirror, and eleven of each is a dense one
        assertEquals(Mirror.FAINT, PoisonTests.marsh(moles(0.6f, PoisonTests.AS2O3_MOLAR_MASS), moles(0.6f, PoisonTests.SB2O3_MOLAR_MASS)));
        assertEquals(Mirror.STRONG, PoisonTests.marsh(moles(11f, PoisonTests.AS2O3_MOLAR_MASS), moles(11f, PoisonTests.SB2O3_MOLAR_MASS)));
    }

    @Test
    void arsenicAndAntimonyGiveTheSameMirror()
    {
        // As in the real test, the mirror cannot say which of the two poisons it is
        for (float mg : new float[] {2f, 25f})
        {
            assertEquals(PoisonTests.marsh(moles(mg, PoisonTests.AS2O3_MOLAR_MASS), 0f), PoisonTests.marsh(0f, moles(mg, PoisonTests.SB2O3_MOLAR_MASS)), "At " + mg + " mg/m3");
        }
    }

    @Test
    void mercuryConvertsFromMolesToMilligrams()
    {
        assertEquals(200.59f, PoisonTests.mercuryMilligrams(1e-3f), 1e-3f);
        assertEquals(0f, PoisonTests.mercuryMilligrams(0f), 0f);
    }

    @Test
    void anHourIsSixtySecondsOfRealTime()
    {
        assertEquals(60f, PoisonTests.HOUR_SECONDS, 0f);
    }

    @Test
    void oneMilligramForAnHourIsOneDose()
    {
        assertEquals(1f, PoisonTests.addDose(0f, 1f, 60f), 1e-6f);
        assertEquals(1f, PoisonTests.addDose(0f, 60f, 1f), 1e-6f);
    }

    @Test
    void theDoseAddsUpOverSeconds()
    {
        float dose = 0f;
        for (int second = 0; second < 120; second++)
        {
            dose = PoisonTests.addDose(dose, 3f, 1f);
        }
        assertEquals(6f, dose, 1e-4f);
    }

    @Test
    void aLeafStageChangesAtEachDose()
    {
        assertEquals(0, PoisonTests.leafStage(0.99f));
        assertEquals(1, PoisonTests.leafStage(1f));
        assertEquals(1, PoisonTests.leafStage(9.99f));
        assertEquals(2, PoisonTests.leafStage(10f));
        assertEquals(2, PoisonTests.leafStage(99.9f));
        assertEquals(3, PoisonTests.leafStage(100f));
        assertEquals(3, PoisonTests.leafStage(1000f));
    }

    @Test
    void aTestLetsOutTheArsineOfItsSampleAndNothingForNoMirror()
    {
        assertEquals(0f, PoisonTests.pinchMoles(Mirror.NONE));
        assertTrue(PoisonTests.pinchMoles(Mirror.STRONG) > PoisonTests.pinchMoles(Mirror.FAINT) && PoisonTests.pinchMoles(Mirror.FAINT) > 0f);
        // 2.5e-5 mol of As2O3 at 197.84 g/mol is about 5 mg: a pinch, not a flask of it
        assertEquals(5f, PoisonTests.STRONG_PINCH_MOLES * Gas.ARSENIC_TRIOXIDE.molarMass() * 1000f, 0.1f, "milligrams of oxide from a strong sample");
    }
}
