package tfcpp.pneumatics.gas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import tfcpp.geology.SeamGas;
import tfcpp.pneumatics.instruments.DavyLamp;
import tfcpp.pneumatics.instruments.SentinelCageItem;

import static org.junit.jupiter.api.Assertions.*;

class DeflagrationTest
{
    private static class Box implements GasVolume
    {
        final int width, height, depth;
        final Map<List<Integer>, float[]> cells = new HashMap<>();

        Box(int width, int height, int depth)
        {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        void fill(int x, int y, int z, Gas gas, float fraction)
        {
            createCell(x, y, z)[gas.ordinal()] = fraction * GasMath.CELL_MOLES;
        }

        void fillAll(Gas gas, float fraction)
        {
            for (int x = 0; x < width; x++)
            {
                for (int y = 0; y < height; y++)
                {
                    for (int z = 0; z < depth; z++)
                    {
                        fill(x, y, z, gas, fraction);
                    }
                }
            }
        }

        @Override
        public float[] cell(int x, int y, int z)
        {
            return cells.get(List.of(x, y, z));
        }

        @Override
        public float[] createCell(int x, int y, int z)
        {
            return cells.computeIfAbsent(List.of(x, y, z), k -> new float[Gas.COUNT]);
        }

        @Override
        public void removeCell(int x, int y, int z)
        {
            cells.remove(List.of(x, y, z));
        }

        @Override
        public boolean isPassable(int x, int y, int z)
        {
            return x >= 0 && y >= 0 && z >= 0 && x < width && y < height && z < depth;
        }

        @Override
        public float atmosphereExchangeRate(int x, int y, int z)
        {
            return 0;
        }
    }

    private static float[] air(Gas gas, float fraction)
    {
        final float[] cell = new float[Gas.COUNT];
        cell[gas.ordinal()] = fraction * GasMath.CELL_MOLES;
        return cell;
    }

    @Test
    void methaneBurnsOnlyBetweenItsLimits()
    {
        assertFalse(Flammability.isFlammable(air(Gas.METHANE, 0.049f)));
        assertTrue(Flammability.isFlammable(air(Gas.METHANE, 0.051f)));
        assertTrue(Flammability.isFlammable(air(Gas.METHANE, 0.149f)));
        assertFalse(Flammability.isFlammable(air(Gas.METHANE, 0.151f)));
    }

    @Test
    void fuelsAddUpByLeChatelier()
    {
        final float[] mixture = air(Gas.METHANE, 0.03f);
        mixture[Gas.HYDROGEN.ordinal()] = 0.02f * GasMath.CELL_MOLES;
        assertEquals(0.03f / 0.05f + 0.02f / 0.04f, Flammability.relativeToLowerLimit(mixture), 1e-4f);
        assertTrue(Flammability.isFlammable(mixture), "Each fuel is below its own limit, but together they burn");
    }

    @Test
    void blackdampMakesFiredampInert()
    {
        final float[] cell = air(Gas.METHANE, 0.08f);
        cell[Gas.FLUE_NITROGEN.ordinal()] = 0.45f * GasMath.CELL_MOLES;
        assertTrue(GasMath.oxygenFraction(cell) < 0.12f);
        assertFalse(Flammability.isFlammable(cell), "Below 12% oxygen methane cannot carry a flame");
    }

    @Test
    void leanFlameBurnsAllTheMethaneToCarbonDioxide()
    {
        final float[] cell = air(Gas.METHANE, 0.07f);
        final float methane = cell[Gas.METHANE.ordinal()];
        final float heat = Deflagration.burn(cell);

        assertEquals(0f, cell[Gas.METHANE.ordinal()], 1e-4f);
        assertEquals(methane, cell[Gas.CARBON_DIOXIDE.ordinal()] + cell[Gas.CARBON_MONOXIDE.ordinal()], 1e-3f, "Carbon is conserved");
        assertTrue(cell[Gas.CARBON_MONOXIDE.ordinal()] < 0.03f * methane, "A lean flame leaves very little CO");
        assertEquals(802f * methane, heat, 0.05f * 802f * methane, "CH4 + 2 O2 gives 802 kJ/mol");
    }

    @Test
    void richFlameLeavesCarbonMonoxide()
    {
        final float[] cell = air(Gas.METHANE, 0.14f);
        final float methane = cell[Gas.METHANE.ordinal()];
        Deflagration.burn(cell);

        assertTrue(cell[Gas.CARBON_MONOXIDE.ordinal()] > 0.3f * methane, "Without the oxygen to finish burning, much of the carbon stays as CO: " + cell[Gas.CARBON_MONOXIDE.ordinal()]);
        assertEquals(methane, cell[Gas.METHANE.ordinal()] + cell[Gas.CARBON_MONOXIDE.ordinal()] + cell[Gas.CARBON_DIOXIDE.ordinal()], 1e-3f, "Carbon is conserved");
        assertTrue(GasMath.ppm(cell[Gas.CARBON_MONOXIDE.ordinal()]) > 12800, "Afterdamp holds a rapidly fatal level of CO");
    }

    @Test
    void closedRoomReachesMeasuredPressure()
    {
        final Box room = new Box(5, 3, 5);
        room.fillAll(Gas.METHANE, 0.095f);

        final Deflagration.Result result = Deflagration.run(room, 2, 1, 2, 4096, 16384);

        assertEquals(75, result.burnedCells());
        final float pressure = result.shocks().getFirst().kilopascals();
        assertTrue(pressure > 650 && pressure < 850, "Closed vessel tests of stoichiometric methane reach 7 to 8 atm above ambient, got " + pressure + " kPa");
    }

    @Test
    void blastCarriesFurtherAlongAGalleryThanInACavern()
    {
        final Box gallery = new Box(200, 2, 2);
        final Box cavern = new Box(61, 61, 61);
        for (int x = 0; x < 10; x++)
        {
            for (int y = 0; y < 2; y++)
            {
                for (int z = 0; z < 2; z++)
                {
                    gallery.fill(x, y, z, Gas.METHANE, 0.095f);
                    cavern.fill(x + 26, y + 30, z + 30, Gas.METHANE, 0.095f);
                }
            }
        }

        final Deflagration.Result inGallery = Deflagration.run(gallery, 0, 0, 0, 4096, 262144);
        final Deflagration.Result inCavern = Deflagration.run(cavern, 26, 30, 30, 4096, 262144);
        assertEquals(inGallery.kilojoules(), inCavern.kilojoules(), 1f);

        final float galleryAt = inGallery.shocks().stream().filter(s -> s.x() == 30).findFirst().orElseThrow().kilopascals();
        final float cavernAt = inCavern.shocks().stream().filter(s -> s.x() == 56 && s.y() == 30 && s.z() == 30).findFirst().map(Deflagration.Shock::kilopascals).orElse(0f);
        assertTrue(galleryAt > 100 && cavernAt < galleryAt / 5, "20 m from the gas: gallery " + galleryAt + " kPa, cavern " + cavernAt + " kPa");
    }

    @Test
    void flameDoesNotJumpThroughUnflammableAir()
    {
        final Box room = new Box(9, 1, 1);
        room.fill(0, 0, 0, Gas.METHANE, 0.095f);
        room.fill(1, 0, 0, Gas.METHANE, 0.095f);
        room.fill(7, 0, 0, Gas.METHANE, 0.095f);

        assertEquals(2, Deflagration.run(room, 0, 0, 0, 4096, 16384).burnedCells());
        assertTrue(room.cell(7, 0, 0)[Gas.METHANE.ordinal()] > 0);
    }

    @Test
    void airWithoutFuelDoesNothing()
    {
        final Box room = new Box(3, 3, 3);
        room.fill(1, 1, 1, Gas.CARBON_DIOXIDE, 0.5f);
        assertEquals(0, Deflagration.run(room, 1, 1, 1, 4096, 16384).burnedCells());
        assertEquals(0, Deflagration.run(room, 0, 0, 0, 4096, 16384).burnedCells());
    }

    @Test
    void seamsHoldMoreGasWithDepth()
    {
        final float shallow = SeamGas.adsorbedVolume(SeamGas.Rank.BITUMINOUS, 20);
        final float deep = SeamGas.adsorbedVolume(SeamGas.Rank.BITUMINOUS, 200);
        assertTrue(shallow > 1f && shallow < 4f, "Shallow coal holds a few m3/t: " + shallow);
        assertEquals(12.5f, deep, 0.1f, "At the Langmuir pressure a seam is half full");
        assertTrue(SeamGas.adsorbedVolume(SeamGas.Rank.BITUMINOUS, 100000) < 25f);
        assertEquals(0f, SeamGas.adsorbedVolume(SeamGas.Rank.BITUMINOUS, -5));
        assertTrue(SeamGas.adsorbedVolume(SeamGas.Rank.LIGNITE, 30) < SeamGas.adsorbedVolume(SeamGas.Rank.BITUMINOUS, 30));
    }

    @Test
    void miningABlockAtSeamDepthIsDangerousOnlyWithoutVentilation()
    {
        final float moles = SeamGas.fastMoles(SeamGas.Rank.BITUMINOUS, 25);
        final float blocksToLowerLimitIn60Cubic = 0.05f * 60f * GasMath.CELL_MOLES / moles;
        assertTrue(blocksToLowerLimitIn60Cubic > 4 && blocksToLowerLimitIn60Cubic < 20, "A dead-end drift of 60 m3 reaches 5% after about ten blocks, got " + blocksToLowerLimitIn60Cubic);
    }

    @Test
    void lampReadsTheAir()
    {
        assertEquals(DavyLamp.Reading.STEADY, DavyLamp.read(null));
        assertEquals(DavyLamp.Reading.STEADY, DavyLamp.read(air(Gas.METHANE, 0.005f)));
        assertEquals(DavyLamp.Reading.PALE_CAP, DavyLamp.read(air(Gas.METHANE, 0.015f)));
        assertEquals(DavyLamp.Reading.BLUE_CAP, DavyLamp.read(air(Gas.METHANE, 0.025f)));
        assertEquals(DavyLamp.Reading.TALL_CAP, DavyLamp.read(air(Gas.METHANE, 0.04f)));
        assertEquals(DavyLamp.Reading.FILLED, DavyLamp.read(air(Gas.METHANE, 0.08f)));
        assertEquals(DavyLamp.Reading.OUT, DavyLamp.read(air(Gas.METHANE, 0.3f)));
        assertEquals(DavyLamp.Reading.DIM, DavyLamp.read(air(Gas.FLUE_NITROGEN, 0.17f)));
        assertEquals(DavyLamp.Reading.OUT, DavyLamp.read(air(Gas.FLUE_NITROGEN, 0.3f)), "Blackdamp puts the flame out while a person can still breathe");
        assertEquals(DavyLamp.Reading.STEADY, DavyLamp.read(air(Gas.CARBON_MONOXIDE, 0.002f)), "A lethal level of CO shows nothing on the flame");
    }

    @Test
    void gauzeFailsAfterBurningInsideForMinutes()
    {
        float gauze = 0;
        for (int second = 0; second < 119; second++)
        {
            gauze = DavyLamp.stepGauze(gauze, DavyLamp.Reading.FILLED, 1f);
        }
        assertFalse(DavyLamp.gauzeFailed(gauze));
        assertTrue(DavyLamp.gauzeFailed(DavyLamp.stepGauze(gauze, DavyLamp.Reading.FILLED, 1f)));
        assertEquals(gauze - 1f, DavyLamp.stepGauze(gauze, DavyLamp.Reading.STEADY, 1f), 1e-4f, "It cools in clean air");
    }

    @Test
    void sentinelWarnsLongBeforeAPersonIsInDanger()
    {
        final float ppm = 800f;
        float animal = 0, person = 0;
        int seconds = 0;
        while (animal < tfcpp.pneumatics.gas.Physiology.COHB_SYMPTOMS && seconds < 3600)
        {
            animal = SentinelCageItem.stepCarboxyhemoglobin(animal, ppm, GasMath.OXYGEN_IN_AIR);
            person = Physiology.stepCarboxyhemoglobin(person, ppm, GasMath.OXYGEN_IN_AIR, 1f);
            seconds++;
        }
        assertTrue(seconds < 120, "At 800 ppm the animal collapses within two minutes, took " + seconds);
        assertTrue(person < 5f, "while the person has barely started: " + person);
    }

    /**
     * Oxygen that runs out exactly at the hydrogen sulfide step, or at the ammonia step. The oxygen a fuel takes is a division by a share and a multiplication back, and it can be left one rounding step below zero, from which the next fuel burns the other way and makes a negative amount of gas. The sweep varies the oxygen in fine steps, and counts the cells where the naive arithmetic really does go below zero, so that it cannot pass by never reaching the case.
     */
    @Test
    void oxygenThatRunsOutAtASulfideOrAmmoniaStepNeverMakesGasNegative()
    {
        int sulfideStep = 0, ammoniaStep = 0;
        for (int sulfide = 0; sulfide < 200; sulfide++)
        {
            for (int filler = 0; filler < 100; filler++)
            {
                final float[] cell = new float[Gas.COUNT];
                cell[Gas.HYDROGEN_SULFIDE.ordinal()] = 1f + sulfide * 0.0431f; // From less to more than the oxygen can burn
                cell[Gas.AMMONIA.ordinal()] = 15f; // Always more than is left, so that the oxygen runs out at this step
                // No methane, CO or nitrogen to start with: a negative amount of burning takes from them, and leaves them below zero
                cell[Gas.CARBON_DIOXIDE.ordinal()] = filler * 0.05f;

                // The same arithmetic as the burning, to see whether the rounding goes below zero in this cell
                final float oxygen = GasMath.oxygenFraction(cell) * GasMath.CELL_MOLES;
                final float burnedSulfide = Math.min(cell[Gas.HYDROGEN_SULFIDE.ordinal()], oxygen / 1.5f);
                final float afterSulfide = oxygen - burnedSulfide * 1.5f;
                final float afterAmmonia = afterSulfide - Math.min(cell[Gas.AMMONIA.ordinal()], afterSulfide / 0.75f) * 0.75f;
                if (afterSulfide < 0f) sulfideStep++;
                if (afterAmmonia < 0f) ammoniaStep++;

                Deflagration.burn(cell);

                for (Gas gas : Gas.VALUES)
                {
                    assertTrue(cell[gas.ordinal()] >= 0f, gas + " went below zero: " + cell[gas.ordinal()] + " with " + oxygen + " mol of oxygen");
                }
            }
        }
        assertTrue(sulfideStep > 0 && ammoniaStep > 0, "The sweep must reach cells where the oxygen is left below zero by the sulfide step and by the ammonia step: " + sulfideStep + " and " + ammoniaStep);
    }

    @Test
    void aSulfideAndAmmoniaWithJustEnoughOxygenBurnClean()
    {
        // 1.5 mol of oxygen per mol of H2S and 0.75 per mol of NH3: pick amounts that use the oxygen up to the last mole
        final float oxygen = GasMath.OXYGEN_IN_AIR * GasMath.CELL_MOLES * (1 - 0.1f);
        final float[] cell = new float[Gas.COUNT];
        cell[Gas.HYDROGEN_SULFIDE.ordinal()] = oxygen / 3f / 1.5f; // A third of the oxygen
        cell[Gas.AMMONIA.ordinal()] = oxygen / 3f / 0.75f * 3f; // Wants more than all that is left
        cell[Gas.FLUE_NITROGEN.ordinal()] = 0f;

        final float heat = Deflagration.burn(cell);

        assertTrue(heat > 0f);
        for (Gas gas : Gas.VALUES)
        {
            assertTrue(cell[gas.ordinal()] >= 0f, gas + " went below zero: " + cell[gas.ordinal()]);
        }
    }
}
