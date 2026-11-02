package tfcpp.pneumatics.gas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Coal dust and the dust that lies settled: where it burns, how it moves, and what is left after it. */
class CoalDustTest
{
    private static final int DUST = Gas.COAL_DUST.ordinal();
    private static final int COAL = Gas.SETTLED_COAL_DUST.ordinal();
    private static final int ROCK = Gas.SETTLED_ROCK_DUST.ordinal();
    private static final int CH4 = Gas.METHANE.ordinal();
    private static final int CO = Gas.CARBON_MONOXIDE.ordinal();
    private static final int CO2 = Gas.CARBON_DIOXIDE.ordinal();

    /** A rectangular space of passable cells, with the same atmosphere exchange everywhere. */
    private static class Box implements GasVolume
    {
        final int width, height, depth;
        final Map<List<Integer>, float[]> cells = new HashMap<>();
        float atmosphere = 0;
        int changed;

        Box(int width, int height, int depth)
        {
            this.width = width;
            this.height = height;
            this.depth = depth;
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
        public void markChanged(int x, int y, int z)
        {
            changed++;
        }

        @Override
        public float atmosphereExchangeRate(int x, int y, int z)
        {
            return atmosphere;
        }

        void run(int seconds)
        {
            final GasSimulator simulator = new GasSimulator();
            for (int i = 0; i < seconds; i++)
            {
                for (List<Integer> pos : List.copyOf(cells.keySet()))
                {
                    simulator.stepCell(this, pos.get(0), pos.get(1), pos.get(2), 1f);
                }
            }
        }

        float total(int species)
        {
            return (float) cells.values().stream().mapToDouble(c -> c[species]).sum();
        }
    }

    private static float[] dust(float kilograms)
    {
        final float[] cell = new float[Gas.COUNT];
        cell[DUST] = kilograms;
        return cell;
    }

    private static float[] methane(float fraction)
    {
        final float[] cell = new float[Gas.COUNT];
        cell[CH4] = fraction * GasMath.CELL_MOLES;
        return cell;
    }

    /** A gallery along x: a methane pocket, then cells of settled dust. */
    private static Box gallery(int pocket, int length)
    {
        final Box box = new Box(length, 1, 1);
        for (int x = 0; x < pocket; x++)
        {
            box.createCell(x, 0, 0)[CH4] = 0.095f * GasMath.CELL_MOLES;
        }
        for (int x = pocket; x < length; x++)
        {
            box.createCell(x, 0, 0)[COAL] = 0.3f;
        }
        return box;
    }

    // Limits of flammability

    @Test
    void coalDustHasALowerLimitOf50GramsPerCubicMetre()
    {
        assertFalse(Flammability.isFlammable(dust(0.049f)));
        assertTrue(Flammability.isFlammable(dust(0.051f)));
    }

    @Test
    void coalDustHasAnUpperLimitOf2KilogramsPerCubicMetre()
    {
        assertTrue(Flammability.isFlammable(dust(1.99f)));
        assertFalse(Flammability.isFlammable(dust(2.01f)));
    }

    @Test
    void coalDustNeedsTwelvePercentOxygen()
    {
        final float[] thin = dust(0.1f);
        thin[Gas.FLUE_NITROGEN.ordinal()] = 0.40f * GasMath.CELL_MOLES;
        assertTrue(GasMath.oxygenFraction(thin) > 0.12f);
        assertTrue(Flammability.isFlammable(thin));

        final float[] stifled = dust(0.1f);
        stifled[Gas.FLUE_NITROGEN.ordinal()] = 0.45f * GasMath.CELL_MOLES;
        assertTrue(GasMath.oxygenFraction(stifled) < 0.12f);
        assertFalse(Flammability.isFlammable(stifled), "Blackdamp puts out a dust cloud as it does firedamp");
    }

    @Test
    void firedampAndDustEachAtHalfTheirLimitBurnTogether()
    {
        final float[] hybrid = methane(0.025f);
        hybrid[DUST] = 0.025f;

        assertFalse(Flammability.isFlammable(methane(0.025f)), "2.5% methane alone is below its limit");
        assertFalse(Flammability.isFlammable(dust(0.025f)), "25 g/m3 of dust alone is below its limit");
        assertEquals(1f, Flammability.relativeToLowerLimitWithDust(hybrid), 1e-4f);
        assertTrue(Flammability.isFlammable(hybrid), "Together they are at the lower limit: this is what makes firedamp with dust so dangerous");

        final float[] less = methane(0.02f);
        less[DUST] = 0.02f;
        assertFalse(Flammability.isFlammable(less), "At 40% each the sum is 0.8");
    }

    @Test
    void dustDoesNotChangeWhatAFlameLampSenses()
    {
        final float[] cell = methane(0.02f);
        cell[DUST] = 0.5f;
        assertEquals(0.02f / 0.05f, Flammability.relativeToLowerLimit(cell), 1e-4f, "The lamp reads gas, not dust");
        assertTrue(Flammability.relativeToLowerLimitWithDust(cell) > 10f);
    }

    @Test
    void oxygenRequirementIsWeightedByShareOfTheLimits()
    {
        // Hydrogen burns down to 5% oxygen and dust only to 12%: at equal shares, the mixture needs 8.5%
        final float[] hydrogenAlone = new float[Gas.COUNT];
        hydrogenAlone[Gas.HYDROGEN.ordinal()] = 0.04f * GasMath.CELL_MOLES;
        hydrogenAlone[Gas.FLUE_NITROGEN.ordinal()] = 0.625f * GasMath.CELL_MOLES;
        final float oxygen = GasMath.oxygenFraction(hydrogenAlone);
        assertTrue(oxygen > 0.05f && oxygen < 0.085f, "Test mixture holds " + oxygen);
        assertTrue(Flammability.isFlammable(hydrogenAlone));

        final float[] withDust = hydrogenAlone.clone();
        withDust[DUST] = 0.05f;
        assertFalse(Flammability.isFlammable(withDust), "Dust in it raises the oxygen the mixture needs");
    }

    @Test
    void settledDustIsNotFuel()
    {
        final float[] cell = new float[Gas.COUNT];
        cell[COAL] = 1f;
        assertFalse(Flammability.isFlammable(cell));
        assertEquals(0f, Flammability.relativeToLowerLimitWithDust(cell));
    }

    // Burning

    @Test
    void afterdampOfADustExplosionIsHalfCarbonMonoxide()
    {
        final float[] cell = dust(0.1f); // 8.3 mol of carbon, 6.2 mol of oxygen of the 8.7 in the cell
        final float heat = Deflagration.burn(cell);

        assertEquals(0f, cell[DUST], 1e-6f, "The oxygen was enough for all of it");
        assertEquals(0.5f * 8.3f, cell[CO], 1e-3f);
        assertEquals(0.5f * 8.3f, cell[CO2], 1e-3f);
        assertEquals(8.3f, cell[CO] + cell[CO2], 1e-3f, "Carbon is conserved");
        assertEquals(8.3f * 252f, heat, 1f, "252 kJ per mole of carbon: half burns to CO, half to CO2");
        assertTrue(GasMath.ppm(cell[CO]) > 12800, "Afterdamp holds a rapidly fatal level of CO: " + GasMath.ppm(cell[CO]));
    }

    @Test
    void whenOxygenRunsOutTheRestOfTheDustStaysUnburned()
    {
        final float[] cell = dust(1f);
        final float oxygen = GasMath.oxygenFraction(cell) * GasMath.CELL_MOLES;
        final float heat = Deflagration.burn(cell);

        final float expectedBurned = oxygen / 0.75f / 83f;
        assertEquals(1f - expectedBurned, cell[DUST], 1e-4f);
        assertTrue(cell[DUST] > 0.8f, "Most of a kilogram of dust cannot burn in one cubic metre of air");
        assertEquals(83f * expectedBurned, cell[CO] + cell[CO2], 1e-2f, "Carbon is conserved");
        assertEquals(83f * expectedBurned * 252f, heat, 1f);
        assertEquals(0f, GasMath.oxygenFraction(cell), 1e-3f, "No oxygen is left");
    }

    @Test
    void methaneTakesItsOxygenBeforeDust()
    {
        final float[] cell = methane(0.095f);
        cell[DUST] = 0.5f;
        final float methane = cell[CH4];
        Deflagration.burn(cell);

        assertEquals(0f, cell[CH4], 1e-4f);
        assertEquals(methane + 83f * (0.5f - cell[DUST]), cell[CO] + cell[CO2], 0.05f, "Carbon of the gas and of the dust is all accounted for");
    }

    @Test
    void aRunWithNoDustIsAsItWas()
    {
        final Box room = new Box(5, 3, 5);
        for (int x = 0; x < 5; x++)
        {
            for (int y = 0; y < 3; y++)
            {
                for (int z = 0; z < 5; z++)
                {
                    room.createCell(x, y, z)[CH4] = 0.095f * GasMath.CELL_MOLES;
                }
            }
        }
        final Deflagration.Result result = Deflagration.run(room, 2, 1, 2, 4096, 16384);
        assertEquals(75, result.burnedCells());
        assertEquals(0, room.changed - 75, "Only the burned cells were marked");
    }

    // Raising dust ahead of the flame

    @Test
    void aPrimaryExplosionRaisesTheDustOfTheWholeGallery()
    {
        final Box gallery = gallery(5, 60);
        final Deflagration.Result result = Deflagration.run(gallery, 0, 0, 0, 4096, 262144);

        assertEquals(60, result.burnedCells(), "A pocket of 5 cells of firedamp has burned a gallery of 60");
        for (int x = 5; x < 60; x++)
        {
            assertEquals(0f, gallery.cell(x, 0, 0)[COAL], 1e-6f, "The dust at " + x + " was raised");
        }
        assertTrue(result.kilojoules() > 5 * 3170f + 55 * 2800f, "The dust gave far more heat than the gas that lit it: " + result.kilojoules());
    }

    @Test
    void aGalleryStoneDustedToSixtyFivePercentStopsTheFlame()
    {
        final Box gallery = gallery(5, 60);
        for (int x = 10; x < 15; x++)
        {
            gallery.cell(x, 0, 0)[COAL] = 0.3f * 0.34f; // 66% of limestone
            gallery.cell(x, 0, 0)[ROCK] = 0.3f * 0.66f;
        }

        final Deflagration.Result result = Deflagration.run(gallery, 0, 0, 0, 4096, 262144);

        assertEquals(10, result.burnedCells(), "The flame goes through the five cells of bare dust and stops at the dusted ones");
        for (int x = 10; x < 15; x++)
        {
            assertEquals(0.3f * 0.34f, gallery.cell(x, 0, 0)[COAL], 1e-6f, "Dust under rock dust is not lifted");
            assertEquals(0f, gallery.cell(x, 0, 0)[DUST]);
        }
        for (int x = 15; x < 60; x++)
        {
            assertEquals(0.3f, gallery.cell(x, 0, 0)[COAL], 1e-6f, "Beyond the barrier nothing happened");
            assertEquals(0f, gallery.cell(x, 0, 0)[DUST]);
        }
    }

    @Test
    void exactlySixtyFivePercentRockDustIsEnough()
    {
        final Box gallery = gallery(5, 60);
        for (int x = 10; x < 15; x++)
        {
            gallery.cell(x, 0, 0)[COAL] = 0.3f * 0.35f;
            gallery.cell(x, 0, 0)[ROCK] = 0.3f * 0.65f;
        }

        assertEquals(10, Deflagration.run(gallery, 0, 0, 0, 4096, 262144).burnedCells(), "65% of limestone is the norm, and it stops the flame");
        assertEquals(0.3f * 0.35f, gallery.cell(12, 0, 0)[COAL], 1e-6f);
    }

    @Test
    void sixtyFourPercentRockDustIsNot()
    {
        final Box gallery = gallery(5, 60);
        for (int x = 10; x < 15; x++)
        {
            gallery.cell(x, 0, 0)[COAL] = 0.3f * 0.36f;
            gallery.cell(x, 0, 0)[ROCK] = 0.3f * 0.64f;
        }

        assertEquals(60, Deflagration.run(gallery, 0, 0, 0, 4096, 262144).burnedCells());
    }

    @Test
    void thirtyPercentRockDustIsNotEnough()
    {
        final Box gallery = gallery(5, 60);
        for (int x = 10; x < 15; x++)
        {
            gallery.cell(x, 0, 0)[COAL] = 0.3f * 0.7f;
            gallery.cell(x, 0, 0)[ROCK] = 0.3f * 0.3f;
        }

        final Deflagration.Result result = Deflagration.run(gallery, 0, 0, 0, 4096, 262144);

        assertEquals(60, result.burnedCells(), "70% coal in the dust is well above the 35% that rises");
        assertEquals(0.3f * 0.3f, gallery.cell(12, 0, 0)[ROCK], 1e-6f, "The rock dust is not raised, only the coal");
    }

    @Test
    void raisedDustThatCannotBurnStaysInTheAir()
    {
        // 2 kg/m3 of settled dust is raised, and is above the upper limit: it cannot carry the flame, and it stays suspended
        final Box gallery = new Box(3, 1, 1);
        gallery.createCell(0, 0, 0)[CH4] = 0.095f * GasMath.CELL_MOLES;
        gallery.createCell(1, 0, 0)[COAL] = 3f;

        final Deflagration.Result result = Deflagration.run(gallery, 0, 0, 0, 4096, 16384);

        assertEquals(1, result.burnedCells());
        assertEquals(3f, gallery.cell(1, 0, 0)[DUST], 1e-6f);
        assertEquals(0f, gallery.cell(1, 0, 0)[COAL]);
    }

    @Test
    void dustLyingInTheStartingCellIsRaisedToo()
    {
        final float[] cell = methane(0.06f);
        cell[COAL] = 0.2f;
        final Box box = new Box(1, 1, 1);
        System.arraycopy(cell, 0, box.createCell(0, 0, 0), 0, Gas.COUNT);

        final Deflagration.Result result = Deflagration.run(box, 0, 0, 0, 4096, 16384);

        assertEquals(1, result.burnedCells());
        assertEquals(0f, box.cell(0, 0, 0)[COAL], 1e-6f);
        assertTrue(result.kilojoules() > 0.06f * GasMath.CELL_MOLES * 802f * 0.9f + 800f, "The dust burned with the gas: " + result.kilojoules());
    }

    @Test
    void flameLimitStillBoundsRaisingDust()
    {
        final Box gallery = gallery(1, 60);
        final Deflagration.Result result = Deflagration.run(gallery, 0, 0, 0, 10, 262144);

        assertEquals(10, result.burnedCells());
        assertEquals(0.3f, gallery.cell(30, 0, 0)[COAL], 1e-6f, "Dust further than the limit is left alone");
    }

    @Test
    void aBlastOnlyRaisesCoalNotRock()
    {
        final float[] cell = new float[Gas.COUNT];
        cell[COAL] = 0.4f;
        cell[ROCK] = 0.6f;
        assertTrue(Deflagration.raiseSettledCoalDust(cell), "40% coal rises");
        assertEquals(0.4f, cell[DUST]);
        assertEquals(0f, cell[COAL]);
        assertEquals(0.6f, cell[ROCK]);

        final float[] dusted = new float[Gas.COUNT];
        dusted[COAL] = 0.2f;
        dusted[ROCK] = 0.8f;
        assertFalse(Deflagration.raiseSettledCoalDust(dusted));

        final float[] sixtyFive = new float[Gas.COUNT];
        sixtyFive[COAL] = 0.35f;
        sixtyFive[ROCK] = 0.65f;
        assertFalse(Deflagration.raiseSettledCoalDust(sixtyFive), "Exactly 65% incombustible is enough");
        assertEquals(0.2f, dusted[COAL]);
        assertFalse(Deflagration.raiseSettledCoalDust(new float[Gas.COUNT]), "Nothing to raise, and no division by zero");
    }

    @Test
    void closedRoomOfDustReachesThePressureOfARealDustExplosion()
    {
        final StringBuilder scan = new StringBuilder();
        float atOptimum = 0;
        for (float grams : new float[] {100, 150, 200, 300, 500, 1000, 1900})
        {
            final Box room = new Box(5, 3, 5);
            for (int x = 0; x < 5; x++)
            {
                for (int y = 0; y < 3; y++)
                {
                    for (int z = 0; z < 5; z++)
                    {
                        room.createCell(x, y, z)[DUST] = grams / 1000f;
                    }
                }
            }
            final Deflagration.Result result = Deflagration.run(room, 2, 1, 2, 4096, 16384);
            assertEquals(75, result.burnedCells());
            final float pressure = result.shocks().getFirst().kilopascals();
            scan.append(grams).append(" g/m3: ").append(pressure).append(" kPa; ");
            if (grams == 300)
            {
                atOptimum = pressure;
            }
        }
        System.out.println("Closed vessel of coal dust: " + scan);
        // Measured Pmax of bituminous coal dust in the 20 L sphere is 6 to 8 bar above ambient
        assertTrue(atOptimum > 600 && atOptimum < 850, "Coal dust at 300 g/m3 in a closed room: " + atOptimum + " kPa");
    }

    // Dust in the simulator

    @Test
    void dustDisplacesNoAirAndHasNoPressure()
    {
        final float[] cell = dust(1.5f);
        cell[COAL] = 2f;
        assertEquals(0f, GasMath.total(cell));
        assertEquals(GasMath.OXYGEN_IN_AIR, GasMath.oxygenFraction(cell), 1e-6f);
        assertEquals(1f, GasMath.pressure(cell));
    }

    @Test
    void dustMakesNoDensityCurrent()
    {
        assertEquals(0f, GasMath.relativeDensity(dust(2f)), "A particle falls by itself, and a cloud does not sink as a body");

        final float[] lying = new float[Gas.COUNT];
        lying[COAL] = 5f;
        lying[ROCK] = 5f;
        assertEquals(0f, GasMath.relativeDensity(lying));
    }

    @Test
    void aCloudOfDustSpreadsLikeAGasOfAirsDensity()
    {
        final Box cloud = new Box(1, 6, 1);
        cloud.createCell(0, 5, 0)[DUST] = 0.06f;
        final Box neutral = new Box(1, 6, 1);
        neutral.createCell(0, 5, 0)[Gas.CARBON_MONOXIDE.ordinal()] = 0.06f;
        cloud.run(6);
        neutral.run(6);

        float cloudLow = 0, neutralLow = 0;
        for (int y = 0; y < 3; y++)
        {
            final float[] c = cloud.cell(0, y, 0), n = neutral.cell(0, y, 0);
            cloudLow += c == null ? 0 : c[DUST] + c[COAL];
            neutralLow += n == null ? 0 : n[Gas.CARBON_MONOXIDE.ordinal()];
        }
        assertEquals(neutralLow, cloudLow, 0.1f * neutralLow + 1e-4f, "Nothing makes the dust sink faster than the air mixes it");
        assertEquals(0.06f, cloud.total(DUST) + cloud.total(COAL), 1e-5f, "Spreading loses nothing");
    }

    @Test
    void dustTakesAboutSeventySecondsToHalfSettle()
    {
        final Box cell = new Box(1, 1, 1);
        cell.createCell(0, 0, 0)[DUST] = 1f;
        cell.run(69);
        assertEquals(0.5f, cell.cell(0, 0, 0)[DUST], 0.01f);
    }

    @Test
    void airborneDustSettlesOnePercentEachSecondAndIsNotLost()
    {
        final Box cell = new Box(1, 1, 1);
        cell.createCell(0, 0, 0)[DUST] = 1f;
        cell.run(100);

        final float[] after = cell.cell(0, 0, 0);
        assertEquals((float) Math.pow(0.99, 100), after[DUST], 1e-4f);
        assertEquals(1f, after[DUST] + after[COAL], 1e-5f, "Dust that settles is moved, not removed");

        cell.run(3000);
        assertNotNull(cell.cell(0, 0, 0), "A cell with dust lying in it is kept after its air is clear");
        assertEquals(1f, cell.cell(0, 0, 0)[COAL], 1e-4f);
    }

    @Test
    void settledDustNeverMovesAndIsNotVented()
    {
        final Box room = new Box(3, 1, 1);
        room.atmosphere = 0.3f;
        final float[] middle = room.createCell(1, 0, 0);
        middle[COAL] = 1f;
        middle[ROCK] = 0.5f;
        middle[CO2] = 30f;

        room.run(600);

        assertTrue(room.total(CO2) < 0.01f, "The gas was vented, and spread to the cells beside it on its way");
        assertEquals(1f, room.cell(1, 0, 0)[COAL], 0f, "All of it where it lay");
        assertEquals(0.5f, room.cell(1, 0, 0)[ROCK], 0f);
        assertEquals(1f, room.total(COAL), 0f);
        assertEquals(0.5f, room.total(ROCK), 0f);
        assertNotNull(room.cell(1, 0, 0), "The cell stays for as long as dust lies in it");
    }

    @Test
    void windCarriesOffDustInTheAirButNotDustOnTheFloor()
    {
        final Box room = new Box(3, 1, 1);
        room.atmosphere = 0.3f;
        final float[] middle = room.createCell(1, 0, 0);
        middle[COAL] = 1f;
        middle[DUST] = 1f;

        room.run(600);

        assertEquals(0f, room.total(DUST), 1e-4f, "What was in the air is gone");
        assertTrue(room.cell(1, 0, 0)[COAL] >= 1f, "What lay on the floor is still there, and a little more settled before the wind took the rest");
        assertTrue(room.total(COAL) < 1.2f, "Most of the dust in the air went with the wind: " + room.total(COAL));
    }

    @Test
    void settledDustIsNotShiftedByPressure()
    {
        final Box room = new Box(3, 1, 1);
        final float[] middle = room.createCell(1, 0, 0);
        middle[COAL] = 2f;
        middle[Gas.FLUE_NITROGEN.ordinal()] = 200f; // Far above 1 atm: the gas is forced out of the cell
        room.run(120);

        assertEquals(2f, room.cell(1, 0, 0)[COAL], 1e-6f);
        assertTrue(room.cell(0, 0, 0)[Gas.FLUE_NITROGEN.ordinal()] > 10f);
        assertEquals(0f, room.cell(0, 0, 0)[COAL]);
    }

    @Test
    void dustIsConservedThroughSettlingAndRaising()
    {
        final Box cell = new Box(1, 1, 1);
        final float[] contents = cell.createCell(0, 0, 0);
        contents[DUST] = 0.7f;
        contents[COAL] = 0.3f;
        cell.run(40);

        float all = cell.total(DUST) + cell.total(COAL);
        assertEquals(1f, all, 1e-5f);

        Deflagration.raiseSettledCoalDust(cell.cell(0, 0, 0));
        all = cell.total(DUST) + cell.total(COAL);
        assertEquals(1f, all, 1e-5f);
        assertEquals(0f, cell.total(COAL));
    }

    // Saving

    @Test
    void aChunkWithOnlySettledDustIsKept()
    {
        final GasChunkData data = new GasChunkData();
        final int key = GasChunkData.key(4, 12, 7);
        final float[] cell = data.getOrCreate(key);
        cell[COAL] = 0.8f;
        cell[ROCK] = 0.2f;
        cell[DUST] = 0.05f;

        assertNotNull(GasChunkData.SERIALIZER.write(data, null), "Settled dust is worth saving by itself");
        final GasChunkData loaded = new GasChunkData();
        loaded.load(data.save());
        assertEquals(0.8f, loaded.get(key)[COAL]);
        assertEquals(0.2f, loaded.get(key)[ROCK]);
        assertEquals(0.05f, loaded.get(key)[DUST]);
    }

    @Test
    void aChunkWithNothingThatMovesGoesToSleepUntilSomethingIsAddedToIt()
    {
        final GasChunkData data = new GasChunkData();
        data.getOrCreate(GasChunkData.key(1, 1, 1))[COAL] = 1f;
        assertTrue(data.isActive(), "A new cell may hold something that moves");

        data.setMobileContent(false);
        assertFalse(data.isActive());
        assertFalse(data.isEmpty(), "It is asleep and not empty: it is still saved");

        data.getOrCreate(GasChunkData.key(2, 1, 1));
        assertTrue(data.isActive());

        data.setMobileContent(false);
        data.wake();
        assertTrue(data.isActive());

        final GasChunkData loaded = new GasChunkData();
        loaded.load(data.save());
        assertTrue(loaded.isActive(), "A chunk that has just been loaded is looked at once");
    }

    @Test
    void sleepIsDecidedOverTheCellsAsTheyAreAfterAStep()
    {
        final GasChunkData data = new GasChunkData();
        data.getOrCreate(GasChunkData.key(1, 1, 1))[COAL] = 1f;
        assertFalse(data.holdsMobileContent());

        data.getOrCreate(GasChunkData.key(2, 1, 1))[CH4] = 5f; // A cell made by a step, which was not there when it began
        assertTrue(data.holdsMobileContent());

        data.getOrCreate(GasChunkData.key(2, 1, 1))[CH4] = 1e-6f;
        assertFalse(data.holdsMobileContent(), "A trace that the simulator would remove does not keep the chunk awake");
    }

    @Test
    void worldsSavedBeforeDustStillLoad()
    {
        final int key = GasChunkData.key(2, 5, 3);
        final ListTag species = new ListTag();
        species.add(StringTag.valueOf("methane"));
        species.add(StringTag.valueOf("carbon_dioxide"));
        species.add(StringTag.valueOf("lead_oxide_fume"));

        final CompoundTag tag = new CompoundTag();
        tag.put("species", species);
        tag.putIntArray("keys", new int[] {key});
        tag.putIntArray("amounts", new int[] {Float.floatToRawIntBits(1.5f), Float.floatToRawIntBits(2.5f), Float.floatToRawIntBits(0.25f)});
        tag.putIntArray("deposits", new int[] {0, 0, Float.floatToRawIntBits(4f)});

        final GasChunkData loaded = new GasChunkData();
        loaded.load(tag);

        final float[] cell = loaded.get(key);
        assertNotNull(cell);
        assertEquals(1.5f, cell[CH4]);
        assertEquals(2.5f, cell[CO2]);
        assertEquals(0.25f, cell[Gas.LEAD_OXIDE_FUME.ordinal()]);
        assertEquals(0f, cell[DUST]);
        assertEquals(0f, cell[COAL]);
        assertEquals(0f, cell[ROCK]);
        assertEquals(4f, loaded.deposit(Gas.LEAD_OXIDE_FUME));
    }

    @Test
    void speciesHaveTheRightKindsOfBehaviour()
    {
        assertTrue(Gas.COAL_DUST.isParticulate() && Gas.COAL_DUST.mobile());
        assertTrue(Gas.SETTLED_COAL_DUST.isParticulate() && !Gas.SETTLED_COAL_DUST.mobile());
        assertTrue(Gas.SETTLED_ROCK_DUST.isParticulate() && !Gas.SETTLED_ROCK_DUST.mobile());
        assertEquals(Gas.SETTLED_COAL_DUST, Gas.COAL_DUST.settlesInto());
        assertNull(Gas.MERCURY_VAPOR.settlesInto());
        for (Gas gas : Gas.VALUES)
        {
            assertTrue(gas.mobile() || gas.isParticulate(), gas + " is immobile and yet counts as a gas");
        }
    }
}
