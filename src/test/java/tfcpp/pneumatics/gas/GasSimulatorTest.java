package tfcpp.pneumatics.gas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GasSimulatorTest
{
    @Test
    void evenMixtureNeverSeparates()
    {
        final Room room = new Room(1, 6, 1);
        for (int y = 0; y < 6; y++)
        {
            room.createCell(0, y, 0)[Gas.METHANE.ordinal()] = 0.09f * GasMath.CELL_MOLES;
        }

        final GasSimulator simulator = new GasSimulator();
        for (int second = 0; second < 600; second++)
        {
            for (int y = 0; y < 6; y++)
            {
                simulator.stepCell(room, 0, y, 0, 1f);
            }
        }

        assertEquals(room.cell(0, 0, 0)[Gas.METHANE.ordinal()], room.cell(0, 5, 0)[Gas.METHANE.ordinal()], 1e-2f, "Air stays mixed: gravity cannot unmix a uniform gas");
    }

    @Test
    void stableLayerLastsMuchLongerThanOpenMixing()
    {
        final Room layered = new Room(1, 2, 1);
        layered.createCell(0, 1, 0)[Gas.METHANE.ordinal()] = 0.5f * GasMath.CELL_MOLES;

        final Room level = new Room(2, 1, 1);
        level.createCell(1, 0, 0)[Gas.METHANE.ordinal()] = 0.5f * GasMath.CELL_MOLES;

        final GasSimulator simulator = new GasSimulator();
        for (int second = 0; second < 30; second++)
        {
            simulator.stepCell(layered, 0, 0, 0, 1f);
            simulator.stepCell(layered, 0, 1, 0, 1f);
            simulator.stepCell(level, 0, 0, 0, 1f);
            simulator.stepCell(level, 1, 0, 0, 1f);
        }

        final float[] below = layered.cell(0, 0, 0);
        final float mixedAcross = below == null ? 0 : below[Gas.METHANE.ordinal()];
        assertTrue(mixedAcross < 0.5f * level.cell(0, 0, 0)[Gas.METHANE.ordinal()], "A layer under a roof should mix far more slowly than gas spreading sideways: " + mixedAcross);
    }

    /** A sealed rectangular room, optionally with an open roof. */
    private static class Room implements GasVolume
    {
        final int width, height, depth;
        final Map<List<Integer>, float[]> cells = new HashMap<>();
        float roofExchange = 0;

        Room(int width, int height, int depth)
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
        public float atmosphereExchangeRate(int x, int y, int z)
        {
            return y == height - 1 ? roofExchange : 0;
        }

        void add(int x, int y, int z, Gas gas, float moles)
        {
            createCell(x, y, z)[gas.ordinal()] += moles;
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

        float total(Gas gas)
        {
            return (float) cells.values().stream().mapToDouble(c -> c[gas.ordinal()]).sum();
        }

        float layer(int y, Gas gas)
        {
            return (float) cells.entrySet().stream()
                .filter(e -> e.getKey().get(1) == y)
                .mapToDouble(e -> e.getValue()[gas.ordinal()])
                .sum();
        }
    }

    @Test
    void sealedRoomConservesGas()
    {
        final Room room = new Room(6, 4, 6);
        room.add(2, 1, 2, Gas.CARBON_DIOXIDE, 30f);
        room.add(4, 3, 1, Gas.METHANE, 12f);
        room.run(600);

        assertEquals(30f, room.total(Gas.CARBON_DIOXIDE), 0.05f);
        assertEquals(12f, room.total(Gas.METHANE), 0.05f);
    }

    @Test
    void heavyGasPoolsLowAndLightGasCollectsHigh()
    {
        final Room room = new Room(5, 5, 5);
        room.add(1, 2, 1, Gas.SULFUR_DIOXIDE, 40f);
        room.add(3, 2, 3, Gas.HYDROGEN, 40f);
        room.run(120);

        assertTrue(room.layer(0, Gas.SULFUR_DIOXIDE) > 2 * room.layer(4, Gas.SULFUR_DIOXIDE), "SO2 should sink: " + room.layer(0, Gas.SULFUR_DIOXIDE) + " at the floor, " + room.layer(4, Gas.SULFUR_DIOXIDE) + " at the roof");
        assertTrue(room.layer(4, Gas.HYDROGEN) > 2 * room.layer(0, Gas.HYDROGEN), "H2 should rise");
    }

    @Test
    void sinkingScalesWithMolarMass()
    {
        final Room heavy = new Room(3, 6, 3);
        heavy.add(1, 5, 1, Gas.CHLORINE, 30f);
        heavy.run(20);

        final Room neutral = new Room(3, 6, 3);
        neutral.add(1, 5, 1, Gas.CARBON_MONOXIDE, 30f);
        neutral.run(20);

        assertTrue(heavy.layer(0, Gas.CHLORINE) > 2 * neutral.layer(0, Gas.CARBON_MONOXIDE), "chlorine should reach the floor before CO, which is almost as dense as air");
    }

    @Test
    void openRoofVentsEverything()
    {
        final Room room = new Room(3, 3, 3);
        room.roofExchange = 0.3f;
        room.add(1, 2, 1, Gas.CARBON_DIOXIDE, 10f);
        room.run(1200);

        assertTrue(room.total(Gas.CARBON_DIOXIDE) < 0.01f);
    }

    @Test
    void overpressureSpreadsThroughTheRoom()
    {
        final Room room = new Room(5, 1, 5);
        room.add(2, 0, 2, Gas.FLUE_NITROGEN, 400f);
        room.run(300);

        final float[] center = room.cell(2, 0, 2);
        final float[] corner = room.cell(0, 0, 0);
        assertNotNull(corner);
        assertEquals(GasMath.total(center), GasMath.total(corner), 2f);
    }

    @Test
    void amountsNeverGoNegative()
    {
        final Room room = new Room(4, 4, 4);
        room.add(0, 0, 0, Gas.HYDROGEN, 500f);
        room.add(3, 3, 3, Gas.CHLORINE, 500f);
        room.run(100);

        for (float[] cell : room.cells.values())
        {
            for (float amount : cell)
            {
                assertTrue(amount >= 0);
            }
        }
    }

    @Test
    void pressureStaysStableAtTheLongestStep()
    {
        final Room room = new Room(2, 1, 1);
        room.add(0, 0, 0, Gas.FLUE_NITROGEN, 500f);
        final GasSimulator simulator = new GasSimulator();
        for (int i = 0; i < 40; i++)
        {
            simulator.stepCell(room, 0, 0, 0, 5f);
        }

        assertEquals(500f, room.total(Gas.FLUE_NITROGEN), 0.1f);
        assertEquals(GasMath.total(room.cell(0, 0, 0)), GasMath.total(room.cell(1, 0, 0)), 2f);
    }

    @Test
    void gasKeepsSpreadingAtLowConcentration()
    {
        final Room room = new Room(5, 1, 1);
        room.add(0, 0, 0, Gas.CARBON_MONOXIDE, 0.01f); // 240 ppm
        room.run(120);

        assertNotNull(room.cell(4, 0, 0), "CO at dangerous levels must not stop at a boundary of its own making");
    }

    @Test
    void placedBlockPushesGasOut()
    {
        final boolean[] filled = {false};
        final Room room = new Room(3, 1, 1)
        {
            @Override
            public boolean isPassable(int x, int y, int z)
            {
                return super.isPassable(x, y, z) && !(filled[0] && x == 1);
            }
        };
        room.add(1, 0, 0, Gas.CARBON_DIOXIDE, 5f);
        filled[0] = true;
        room.run(1);

        assertNull(room.cell(1, 0, 0));
        assertEquals(5f, room.total(Gas.CARBON_DIOXIDE), 1e-3f);
    }

    @Test
    void condensedDustIsAccountedFor()
    {
        final float[] settled = {0};
        final Room room = new Room(3, 3, 3)
        {
            @Override
            public void deposit(int x, int y, int z, Gas gas, float moles)
            {
                settled[0] += moles;
            }
        };
        room.add(1, 1, 1, Gas.ARSENIC_TRIOXIDE, 2f);
        room.run(300);

        assertTrue(settled[0] > 0.5f, "Fumes must settle out");
        assertEquals(2f, room.total(Gas.ARSENIC_TRIOXIDE) + settled[0], 0.02f);
    }

    @Test
    void mercuryVapourLingersAtSaturationWhileTheExcessCondenses()
    {
        final Room room = new Room(1, 1, 1);
        room.add(0, 0, 0, Gas.MERCURY_VAPOR, 0.1f);
        room.run(600);

        assertEquals(Gas.MERCURY_VAPOR.saturationMoles(), room.total(Gas.MERCURY_VAPOR), 1e-5f);
    }

    @Test
    void wallsStopGas()
    {
        final Room room = new Room(2, 1, 1)
        {
            @Override
            public boolean isPassable(int x, int y, int z)
            {
                return x == 0 && y == 0 && z == 0;
            }
        };
        room.add(0, 0, 0, Gas.CARBON_DIOXIDE, 5f);
        room.run(100);

        assertNull(room.cell(1, 0, 0));
        assertEquals(5f, room.total(Gas.CARBON_DIOXIDE), 1e-3f);
    }

    @Test
    void aRoundingErrorBelowZeroInACellDoesNotBreakTheStep()
    {
        // A negative amount saved by an older version, or left by a rounding step: Math.clamp throws when its minimum is above its maximum
        final Room room = new Room(2, 1, 1);
        room.createCell(0, 0, 0)[Gas.METHANE.ordinal()] = -1e-9f;
        room.createCell(0, 0, 0)[Gas.CARBON_DIOXIDE.ordinal()] = 3f;

        final GasSimulator simulator = new GasSimulator();
        assertDoesNotThrow(() -> simulator.stepCell(room, 0, 0, 0, 1f));
        assertDoesNotThrow(() -> simulator.stepCell(room, 0, 0, 0, 1f));
    }

    /** Two chunks side by side, the way the tracker steps them: a chunk that is asleep is not stepped at all, and markChanged wakes it. */
    private static final class TwoChunks implements GasVolume
    {
        final Map<List<Integer>, float[]> cells = new HashMap<>();
        boolean westAwake;
        int wakeCalls;

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
            return y == 0 && z == 0 && x >= -16 && x < 16;
        }

        @Override
        public float atmosphereExchangeRate(int x, int y, int z)
        {
            return 0;
        }

        @Override
        public void markChanged(int x, int y, int z)
        {
            if (x < 0)
            {
                westAwake = true;
                wakeCalls++;
            }
        }

        /** One second: the east chunk (x from 0) is always awake, the west one (x below 0) only when something has woken it. */
        void second(GasSimulator simulator)
        {
            for (List<Integer> pos : List.copyOf(cells.keySet()))
            {
                if (pos.get(0) >= 0 || westAwake)
                {
                    simulator.stepCell(this, pos.get(0), pos.get(1), pos.get(2), 1f);
                }
            }
        }
    }

    @Test
    void gasCrossesIntoTheExistingCellOfAChunkThatIsAsleep()
    {
        final TwoChunks world = new TwoChunks();
        world.createCell(-1, 0, 0)[Gas.SETTLED_COAL_DUST.ordinal()] = 2f; // The west chunk holds nothing but settled dust, so it sleeps
        world.createCell(0, 0, 0)[Gas.CARBON_DIOXIDE.ordinal()] = 20f;

        final GasSimulator simulator = new GasSimulator();
        for (int second = 0; second < 30; second++)
        {
            world.second(simulator);
        }

        final float[] across = world.cell(-1, 0, 0);
        assertNotNull(across);
        assertTrue(across[Gas.CARBON_DIOXIDE.ordinal()] > 0.5f, "The cell across the chunk line must take its share of the gas: " + across[Gas.CARBON_DIOXIDE.ordinal()]);
        assertTrue(world.westAwake, "The sleeping chunk must have been woken");
        final float total = (float) world.cells.values().stream().mapToDouble(c -> c[Gas.CARBON_DIOXIDE.ordinal()]).sum();
        assertEquals(20f, total, 1e-3f, "Nothing is made or lost on the way");
        assertEquals(2f, across[Gas.SETTLED_COAL_DUST.ordinal()], 1e-6f, "The dust that lay there stays");
    }

    @Test
    void thinGasDoesNotKeepWakingTheChunkAcross()
    {
        final TwoChunks world = new TwoChunks();
        world.createCell(-1, 0, 0)[Gas.SETTLED_COAL_DUST.ordinal()] = 2f;
        world.createCell(0, 0, 0)[Gas.CARBON_DIOXIDE.ordinal()] = GasSimulator.REMOVE_CELL_MOLES / 10f;

        final GasSimulator simulator = new GasSimulator();
        for (int second = 0; second < 5; second++)
        {
            world.second(simulator);
        }
        assertEquals(0, world.wakeCalls, "A trace that would be dropped from the cell anyway is not worth waking a chunk for");
    }
}
