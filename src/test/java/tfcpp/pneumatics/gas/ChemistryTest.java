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
        // Known values: about 10% at 100 ppm, about half of haemoglobin bound near 800-1000 ppm
        assertEquals(10.5f, Physiology.equilibriumCarboxyhemoglobin(100, 0.2095f), 1f);
        assertEquals(54f, Physiology.equilibriumCarboxyhemoglobin(1000, 0.2095f), 2f);
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
    void carbonMonoxideGivesNoWarning()
    {
        assertFalse(Physiology.isNoticeable(Gas.CARBON_MONOXIDE, 100_000));
        assertTrue(Physiology.isNoticeable(Gas.SULFUR_DIOXIDE, 5));
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
