package tfcpp.chemistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChamberProcessTest
{
    /** One cubic metre of chamber: 40 mol of gas with 7% SO2, plenty of nitrogen oxides, air with oxygen at 10%, and water and tank room to spare. */
    private static final float GAS = 40f, SO2 = 2.8f, OXIDES = 0.1f, AIR = 20f, WATER = 1000f, ROOM = 1000f;

    /** Gas of one mole, so that every share is large and a single limit decides the acid. */
    private static final float SMALL_GAS = 1f;

    /** Long enough that the rate would make far more acid than any one limit allows. */
    private static final float LONG = 100f;

    private static ChamberProcess.Result react(float sulfurDioxide, float oxides, float air, float water, float room, float seconds)
    {
        return ChamberProcess.react(sulfurDioxide, oxides, air, SMALL_GAS, water, room, seconds);
    }

    @Test
    void sevenPercentSulfurDioxideMakesAboutTwoAndAHalfKilogramsADay()
    {
        float moles = 0f;
        for (int second = 0; second < 1440; second++)
        {
            moles += ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, ROOM, 1f).acidMoles();
        }
        final float kilograms = moles * 98.08f / 1000f;
        assertTrue(kilograms >= 2.5f && kilograms <= 2.7f, "A cubic metre at 7% SO2 should make about 2.5 kg of H2SO4 in a day, got " + kilograms);
    }

    @Test
    void rateIsTheFittedConstantTimesTheSharesThatLimit()
    {
        assertEquals(0.0182f, ChamberProcess.rate(0.07f, 0.0025f, 0.105f), 1e-5f, "0.26 x 7%, with the oxides and oxygen past their full shares");
        assertEquals(0f, ChamberProcess.rate(0f, 0.01f, 0.1f), "No SO2, no rate");
        assertEquals(0f, ChamberProcess.rate(0.07f, 0f, 0.1f), "No oxides, no rate");
        assertEquals(0f, ChamberProcess.rate(0.07f, 0.01f, 0f), "No oxygen, no rate");
    }

    @Test
    void halfTheOxidesShareBelowFullGivesHalfTheRate()
    {
        final float full = ChamberProcess.rate(0.07f, ChamberProcess.NITROGEN_OXIDES_FULL, 0.1f);
        final float half = ChamberProcess.rate(0.07f, ChamberProcess.NITROGEN_OXIDES_FULL / 2f, 0.1f);
        assertEquals(full / 2f, half, 1e-9f);
    }

    @Test
    void eachMissingIngredientStopsTheChamberOnItsOwn()
    {
        assertEquals(0f, ChamberProcess.react(0f, OXIDES, AIR, GAS, WATER, ROOM, 1000f).acidMoles(), "No SO2, no acid");
        assertEquals(0f, ChamberProcess.react(SO2, 0f, AIR, GAS, WATER, ROOM, 1000f).acidMoles(), "No oxides, no acid: they carry the oxygen");
        assertEquals(0f, ChamberProcess.react(SO2, OXIDES, 0f, GAS, WATER, ROOM, 1000f).acidMoles(), "No air, no oxygen, no acid");
        assertEquals(0f, ChamberProcess.react(SO2, OXIDES, AIR, GAS, 0f, ROOM, 1000f).acidMoles(), "No water, no acid");
        assertEquals(0f, ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, 0f, 1000f).acidMoles(), "A full tank takes no more acid");
    }

    @Test
    void eachLimitCapsTheAcidOnItsOwn()
    {
        // With 1 mol of gas, 0.6 mol of air holds 0.1257 mol of oxygen, which is enough for 0.2514 mol of acid
        final float oxygenCap = 0.6f * ChamberProcess.OXYGEN_IN_AIR / ChamberProcess.OXYGEN_PER_MOLE;
        assertEquals(0.1f, react(0.1f, 0.5f, 0.6f, 1000f, 1000f, LONG).acidMoles(), 1e-5f, "SO2 is the limit");
        assertEquals(oxygenCap, react(0.5f, 0.5f, 0.6f, 1000f, 1000f, LONG).acidMoles(), 1e-5f, "oxygen is the limit");
        assertEquals(0.1f, react(0.5f, 0.5f, 0.6f, 0.1f * ChamberProcess.WATER_LITRES_PER_MOLE, 1000f, LONG).acidMoles(), 1e-4f, "water is the limit");
        assertEquals(0.1f, react(0.5f, 0.5f, 0.6f, 1000f, 0.1f * ChamberProcess.ACID_LITRES_PER_MOLE, LONG).acidMoles(), 1e-4f, "the room in the tank is the limit");
    }

    @Test
    void massIsConservedAtAboutOneHundredAndFiftyOneGramsAMole()
    {
        final ChamberProcess.Result step = ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, ROOM, 1000f);
        final float grams = step.sulfurDioxide() * 64.07f + step.air() * ChamberProcess.OXYGEN_IN_AIR * 32f + step.water() * 1000f;
        final float out = step.acidMoles() * 151f;
        assertEquals(out, grams, grams * 0.01f, "SO2, oxygen and water in, acid out: " + grams + " g in, " + out + " g out");
        assertEquals(out, step.acidLitres() * 1550f, out * 0.01f, "The acid weighs 1.55 kg/L, so its litres give the same mass");
        assertEquals(230f * step.acidMoles(), step.heatKilojoules(), 1e-3f * step.heatKilojoules());
    }

    @Test
    void stepSizeDoesNotChangeTheDayForAConstantMixture()
    {
        float fine = 0f;
        for (int second = 0; second < 1440; second++)
        {
            fine += ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, ROOM, 1f).acidMoles();
        }
        float coarse = 0f;
        for (int minute = 0; minute < 24; minute++)
        {
            coarse += ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, ROOM, 60f).acidMoles();
        }
        assertEquals(fine, coarse, fine * 1e-3f, "A minute at a time makes the same acid as a second at a time, while no limit is reached");
    }

    @Test
    void noGasOrNoTimeMakesNothing()
    {
        assertEquals(ChamberProcess.Result.NONE, ChamberProcess.react(SO2, OXIDES, AIR, 0f, WATER, ROOM, 1f));
        assertEquals(ChamberProcess.Result.NONE, ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, ROOM, 0f));
    }

    @Test
    void aChamberIsAtFullRateFromSixtyDegreesAndHalvesForEveryTenKelvinBelow()
    {
        assertEquals(1f, ChamberProcess.temperatureFactor(333.15f), 1e-6f);
        assertEquals(1f, ChamberProcess.temperatureFactor(353.15f), 1e-6f, "Above sixty it is no faster: the rate is fitted at sixty");
        assertEquals(0.5f, ChamberProcess.temperatureFactor(323.15f), 1e-5f, "Fifty degrees: half");
        assertEquals(0.25f, ChamberProcess.temperatureFactor(313.15f), 1e-5f);
        assertEquals(1f / 16f, ChamberProcess.temperatureFactor(293.15f), 1e-5f, "A room at twenty degrees is a sixteenth");
        assertEquals(0f, ChamberProcess.temperatureFactor(273.15f), "No mist in frost");
        assertEquals(0f, ChamberProcess.temperatureFactor(250f));
    }

    @Test
    void aColdChamberMakesProportionallyLessAcidAndNeverMoreThanWarm()
    {
        final float warm = ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, ROOM, 1f, 333.15f).acidMoles();
        final float cold = ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, ROOM, 1f, 293.15f).acidMoles();
        assertEquals(warm / 16f, cold, warm * 1e-4f, "At twenty degrees one sixteenth of the acid");
        assertEquals(0f, ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, ROOM, 1000f, 263.15f).acidMoles(), "Nothing at all in frost, however long");
        assertEquals(warm, ChamberProcess.react(SO2, OXIDES, AIR, GAS, WATER, ROOM, 1f).acidMoles(), 0f, "The call without a temperature is the fitted rate");
    }
}
