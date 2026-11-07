package tfcpp.chemistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BorosilicateGlassTest
{
    @Test
    void anotherBatchIsWorkedAsTfcSaysAndAStepWithNoMinimumIsToo()
    {
        assertTrue(BorosilicateGlass.workable(true, true, false, true, 600f), "Silica glass on a brass pipe at 600 C: TFC answer stands");
        assertFalse(BorosilicateGlass.workable(false, true, false, false, 300f));
        assertTrue(BorosilicateGlass.workable(true, false, true, true, 20f), "Sawing the glass off needs no heat at all");
    }

    @Test
    void borosilicateIsWorkedOnlyAboveItsWorkingPointOnACeramicPipe()
    {
        assertFalse(BorosilicateGlass.workable(true, true, true, false, 600f), "TFC would say yes at 480 C, and the glass is stiff");
        assertFalse(BorosilicateGlass.workable(true, true, true, false, 1250f), "At the working point, not above it");
        assertTrue(BorosilicateGlass.workable(false, true, true, false, 1251f), "Above it, whatever TFC said");
    }

    @Test
    void aBrassPipeCannotWorkItAtAnyTemperatureItSurvives()
    {
        for (float temperature = 20f; temperature <= 1500f; temperature += 10f)
        {
            assertFalse(BorosilicateGlass.workable(true, true, true, true, temperature), "At " + temperature);
        }
        assertTrue(BorosilicateGlass.WORKING_TEMPERATURE > BorosilicateGlass.BRASS_MELTING_POINT, "The glass is worked above the melting point of brass");
    }
}
