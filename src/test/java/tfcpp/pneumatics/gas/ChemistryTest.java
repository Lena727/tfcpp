package tfcpp.pneumatics.gas;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class ChemistryTest
{
    @Test
    void burningCarbonConsumesOneMoleOfOxygenPerMoleOfCarbonDioxide()
    {
        final float[] cell = new float[Gas.COUNT];
        Combustion.addFlueGas(cell, 2f, 0f);

        assertEquals(2f, cell[Gas.CARBON_DIOXIDE.ordinal()], 1e-4f);

        final float oxygenLeft = GasMath.oxygenFraction(cell) * GasMath.CELL_MOLES;
        final float oxygenBefore = GasMath.OXYGEN_IN_AIR * GasMath.CELL_MOLES;
        assertEquals(2f, oxygenBefore - oxygenLeft, 1e-3f);
    }

    @Test
    void burningToCarbonMonoxideConsumesHalfAsMuchOxygen()
    {
        final float[] cell = new float[Gas.COUNT];
        Combustion.addFlueGas(cell, 2f, 1f);

        assertEquals(2f, cell[Gas.CARBON_MONOXIDE.ordinal()], 1e-4f);

        // 2 C + O2 -> 2 CO uses 1 mol of O2. It also makes one more mole of gas than it used, and at constant pressure that
        // extra mole pushes out one mole of air, carrying another 0.2095 mol of O2 with it.
        final float oxygenLeft = GasMath.oxygenFraction(cell) * GasMath.CELL_MOLES;
        final float oxygenBefore = GasMath.OXYGEN_IN_AIR * GasMath.CELL_MOLES;
        assertEquals(1f + GasMath.OXYGEN_IN_AIR, oxygenBefore - oxygenLeft, 1e-3f);
    }

    @Test
    void carbonBurnRateMatchesEnergyReleased()
    {
        // 39.35 kW of clean combustion burns 0.1 mol of carbon per second
        assertEquals(0.1f, Combustion.carbonRate(39.35f, 0f), 1e-4f);
        // Partial combustion releases much less per mole, so the same fire must burn more carbon
        assertTrue(Combustion.carbonRate(39.35f, 0.5f) > 0.1f);
    }

    @Test
    void starvedFiresMakeMoreCarbonMonoxide()
    {
        final float fresh = Combustion.carbonMonoxideShare(0.05f, GasMath.OXYGEN_IN_AIR);
        final float starved = Combustion.carbonMonoxideShare(0.05f, 0.13f);

        assertEquals(0.05f, fresh, 1e-5f);
        assertTrue(starved > 0.5f);
    }

    @Test
    void equilibriumCarboxyhemoglobinFollowsHaldane()
    {
        // Measured at equilibrium, with the oxygen of the alveoli and not that of the air breathed: about 14 to 16% at 100 ppm, 40 to 45% at 400 ppm
        final float at100 = Physiology.equilibriumCarboxyhemoglobin(100, 0.2095f);
        final float at400 = Physiology.equilibriumCarboxyhemoglobin(400, 0.2095f);
        assertTrue(at100 >= 14f && at100 <= 16f, "100 ppm should come to 14-16% COHb, got " + at100);
        assertTrue(at400 >= 40f && at400 <= 45f, "400 ppm should come to 40-45% COHb, got " + at400);
    }

    @Test
    void thinnerAirBindsMoreCarbonMonoxide()
    {
        assertTrue(Physiology.equilibriumCarboxyhemoglobin(100, 0.15f) > Physiology.equilibriumCarboxyhemoglobin(100, 0.2095f));
    }

    @Test
    void hydrogenSulfideIsSmeltFromTheFirstTracesAndNotWhereItParalysesTheSenseOfSmell()
    {
        assertFalse(Physiology.isNoticeable(Gas.HYDROGEN_SULFIDE, 0.01f), "Below the odour threshold");
        assertTrue(Physiology.isNoticeable(Gas.HYDROGEN_SULFIDE, 0.05f), "A few hundredths of a ppm stinks");
        assertTrue(Physiology.isNoticeable(Gas.HYDROGEN_SULFIDE, 10f));
        assertTrue(Physiology.isNoticeable(Gas.HYDROGEN_SULFIDE, 100f));
        assertFalse(Physiology.isNoticeable(Gas.HYDROGEN_SULFIDE, 101f), "Above 100 ppm the nerve of smell is paralysed and the worst air gives no warning");
        assertFalse(Physiology.isNoticeable(Gas.HYDROGEN_SULFIDE, 700f), "which is how the gas kills");
        assertEquals(3, Physiology.acuteSeverity(Gas.HYDROGEN_SULFIDE, 700f), "and 700 ppm still collapses a person in one breath");
    }

    @Test
    void aBlastFurnaceMakesTopGasOfTwentyToTwentyEightPercentCarbonMonoxide()
    {
        final float[] flue = new float[Gas.COUNT];
        Combustion.addFlueGas(flue, 1f, Combustion.BLAST_FURNACE_CARBON_MONOXIDE_SHARE);
        final float fraction = flue[Gas.CARBON_MONOXIDE.ordinal()] / GasMath.total(flue);
        assertTrue(fraction >= 0.20f && fraction <= 0.28f, "Real top gas is 20 to 28% CO, got " + fraction);
        assertEquals(0.75f, Combustion.BLAST_FURNACE_CARBON_MONOXIDE_SHARE);
    }

    @Test
    void aFireOnlyBurnsWhereThereIsOxygenForIt()
    {
        assertTrue(Combustion.sustainsFlame(GasMath.OXYGEN_IN_AIR));
        assertTrue(Combustion.sustainsFlame(Combustion.EXTINCTION_OXYGEN));
        assertFalse(Combustion.sustainsFlame(Combustion.EXTINCTION_OXYGEN - 0.001f), "Under 12% embers go out, and a fire that cannot go out has nothing to put into the air");
    }

    @Test
    void lethalCarbonMonoxideKillsWithinMinutes()
    {
        float blood = 0;
        int seconds = 0;
        while (blood < Physiology.COHB_FATAL && seconds < 3600)
        {
            blood = Physiology.stepCarboxyhemoglobin(blood, 12800, 0.2f, 1f);
            seconds++;
        }
        assertTrue(seconds >= 60 && seconds <= 180, "took " + seconds + " s");
    }

    @Test
    void lowCarbonMonoxideIsSlowAndBounded()
    {
        float blood = 0;
        for (int i = 0; i < 3600; i++)
        {
            blood = Physiology.stepCarboxyhemoglobin(blood, 100, 0.2095f, 1f);
        }
        assertTrue(blood < Physiology.COHB_FATAL);
        assertTrue(blood > 2f);
        assertTrue(blood <= Physiology.equilibriumCarboxyhemoglobin(100, 0.2095f) + 1e-3f);
    }

    @Test
    void freshAirClearsBlood()
    {
        float blood = 40;
        for (int i = 0; i < 300; i++)
        {
            blood = Physiology.stepCarboxyhemoglobin(blood, 0, 0.2095f, 1f);
        }
        assertEquals(20f, blood, 1f);
    }

    @Test
    void swallowedMercuryIsADoseByTheAmount()
    {
        assertEquals(1.35f, Physiology.swallowedMercury(1f), 0.01f, "A millilitre is 13.5 g, of which 0.01% is taken up");
    }

    /** How long until a swallowed dose reaches this level in the blood, breathing clean air. */
    private static int secondsUntil(int severity, float dose)
    {
        float gut = dose, blood = 0f;
        for (int second = 0; second < 100_000; second++)
        {
            final float taken = Physiology.takenUpFromGut(gut, 1f);
            gut -= taken;
            blood = Physiology.BodyBurden.MERCURY.step(blood + taken, 0f, 200.59f, 1f);
            if (Physiology.BodyBurden.MERCURY.severity(blood) >= severity)
            {
                return second;
            }
        }
        return -1;
    }

    @Test
    void aFatalMouthfulGetsWorseOverTime()
    {
        final float jug = Physiology.swallowedMercury(100f);
        final int symptoms = secondsUntil(1, jug), disabled = secondsUntil(2, jug), fatal = secondsUntil(3, jug);
        assertTrue(symptoms > 30 && symptoms < 120, "A tremor after a minute or so, got " + symptoms);
        assertTrue(disabled > symptoms * 2, "Then worse, got " + disabled);
        assertTrue(fatal > disabled * 2 && fatal < 1200, "And fatal after some minutes, got " + fatal);
    }

    @Test
    void aDropNeverReachesAHarmfulLevel()
    {
        assertEquals(-1, secondsUntil(1, Physiology.swallowedMercury(1f)));
    }

    @Test
    void leadBuildsUpFromFumeOverDaysOfWork()
    {
        // 20 mg/m3 of lead oxide, the air of an unventilated old smelting shed
        final float moles = 0.02f / 223.2f;
        float burden = 0f;
        for (int second = 0; second < 60 * 60; second++)
        {
            burden = Physiology.BodyBurden.LEAD.step(burden, moles, 223.2f, 1f);
        }
        assertTrue(burden > 15f && burden < 30f, "An hour of real time should approach the first symptoms, got " + burden);
        assertEquals(0, Physiology.BodyBurden.LEAD.severity(burden));
        assertEquals(3, Physiology.BodyBurden.LEAD.severity(300f));
        assertEquals(2, Physiology.BodyBurden.LEAD.severity(Physiology.BodyBurden.LEAD.survivable(1000f)), "Death does not cure it, but it does not kill again");
    }

    @Test
    void carbonMonoxideGivesNoWarning()
    {
        assertFalse(Physiology.isNoticeable(Gas.CARBON_MONOXIDE, 100_000));
        assertTrue(Physiology.isNoticeable(Gas.SULFUR_DIOXIDE, 5));
    }

    @Test
    void arsenicBuildsUpSlowlyAndUnnoticed()
    {
        // 1 mg/m3 of arsenic trioxide, a level that real smelter workers were exposed to
        final float moles = 0.001f / 197.84f;
        float burden = 0;
        for (int second = 0; second < 3600; second++)
        {
            burden = Physiology.BodyBurden.ARSENIC.step(burden, moles, 197.84f, 1f);
        }
        assertEquals(0, Physiology.BodyBurden.ARSENIC.severity(burden), "One hour must not yet cause symptoms, got " + burden);
        assertFalse(Physiology.isNoticeable(Gas.ARSENIC_TRIOXIDE, 1e6f));
    }

    @Test
    void deathDoesNotCurePoisonOrTrapAPlayerInALoop()
    {
        final BreathingState state = new BreathingState();
        state.carboxyhemoglobin = 80f;
        state.arsenicMg = 1000f;
        state.mercuryMg = 3f;

        final BreathingState respawned = state.afterDeath();

        assertEquals(0f, respawned.carboxyhemoglobin);
        assertEquals(3f, respawned.mercuryMg);
        assertTrue(respawned.arsenicMg > 0f, "Arsenic must stay in the body");
        assertTrue(Physiology.BodyBurden.ARSENIC.severity(respawned.arsenicMg) < 3, "It must not be enough to kill the player again");
    }

    @Test
    void savedChunkKeepsDeposits()
    {
        final GasChunkData data = new GasChunkData();
        data.addDeposit(Gas.ARSENIC_TRIOXIDE, 3f);

        final GasChunkData loaded = new GasChunkData();
        loaded.load(data.save());

        assertEquals(3f, loaded.deposit(Gas.ARSENIC_TRIOXIDE), "Deposits with no gas cells left must still survive");
    }

    @Test
    void savedChunkRoundTrips()
    {
        final GasChunkData data = new GasChunkData();
        final int key = GasChunkData.key(3, -40, 9);
        data.getOrCreate(key)[Gas.METHANE.ordinal()] = 7.5f;

        final CompoundTag tag = data.save();
        final GasChunkData loaded = new GasChunkData();
        loaded.load(tag);

        assertEquals(7.5f, loaded.get(key)[Gas.METHANE.ordinal()]);
        assertEquals(3, GasChunkData.keyX(key));
        assertEquals(-40, GasChunkData.keyY(key));
        assertEquals(9, GasChunkData.keyZ(key));
    }
}
