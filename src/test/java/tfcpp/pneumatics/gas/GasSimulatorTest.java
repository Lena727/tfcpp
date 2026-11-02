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

    // G6: the exchange with the atmosphere falls off down an open shaft

    @Test
    void openAirExchangeFallsWithDepthBelowTheSurface()
    {
        assertEquals(0.3f, GasMath.openAirExchange(0f), 1e-6f, "At the surface the wind carries off 30% a second");
        assertEquals(0.3f / (float) Math.E, GasMath.openAirExchange(3f), 1e-6f, "Three metres down it is a third of that");
        assertEquals(0.3f * (float) Math.exp(-4), GasMath.openAirExchange(12f), 1e-6f);
        assertEquals(0.3f, GasMath.openAirExchange(-2f), 1e-6f, "Above the surface is the surface");
        assertTrue(GasMath.openAirExchange(12f) < 0.01f);
    }

    @Test
    void heavyGasCollectsAtTheBottomOfAnOpenShaftAndKills()
    {
        final int blocks = 13;
        final Room shaft = new Room(1, blocks, 1)
        {
            @Override
            public float atmosphereExchangeRate(int x, int y, int z)
            {
                return GasMath.openAirExchange(blocks - 1 - y); // The top cell is at the surface
            }
        };
        shaft.add(0, 0, 0, Gas.CARBON_DIOXIDE, 12f); // A well with the damp of a cellar at the bottom of it
        shaft.run(60);

        final float[] bottom = shaft.cell(0, 0, 0);
        assertNotNull(bottom);
        final float fraction = bottom[Gas.CARBON_DIOXIDE.ordinal()] / GasMath.CELL_MOLES;
        assertTrue(fraction * 1e6f > 170_000f, "It must still be a gas that drops a man in a few breaths after a minute, got " + fraction * 100f + "%");
        assertTrue(shaft.total(Gas.CARBON_DIOXIDE) > 8f, "and most of it is still there");
    }

    @Test
    void theSameGasOnTheSurfaceIsCarriedAwayAtOnce()
    {
        final Room surface = new Room(1, 1, 1)
        {
            @Override
            public float atmosphereExchangeRate(int x, int y, int z)
            {
                return GasMath.openAirExchange(0f);
            }
        };
        surface.add(0, 0, 0, Gas.CARBON_DIOXIDE, 12f);
        surface.run(60);

        assertTrue(surface.total(Gas.CARBON_DIOXIDE) < 1e-3f, "The wind clears a surface in a minute");
    }

    // G7: a vapour with a saturation spreads at that scale

    @Test
    void mercuryAtSaturationReachesTheNeighbouringCell()
    {
        final Room room = new Room(3, 1, 1);
        room.add(0, 0, 0, Gas.MERCURY_VAPOR, Gas.MERCURY_VAPOR.saturationMoles());
        final GasSimulator simulator = new GasSimulator();
        simulator.stepCell(room, 0, 0, 0, 1f);

        final float[] next = room.cell(1, 0, 0);
        assertNotNull(next, "Mercury at saturation, 1.6 ppm, must be able to spread into empty air");
        assertTrue(next[Gas.MERCURY_VAPOR.ordinal()] > 0);

        simulator.stepCell(room, 1, 0, 0, 1f);
        assertNotNull(room.cell(1, 0, 0), "and the cell it opened is not thrown away on its next step");
    }

    @Test
    void anOrdinaryTraceStillStopsAtTheOrdinaryFloor()
    {
        final Room room = new Room(3, 1, 1);
        room.add(0, 0, 0, Gas.CARBON_MONOXIDE, 1.5e-5f); // A tenth of it is under the floor of 2e-5 for opening a cell
        final GasSimulator simulator = new GasSimulator();
        simulator.stepCell(room, 0, 0, 0, 1f);

        assertNull(room.cell(1, 0, 0), "The floor for gases without a saturation of their own is unchanged");
    }

    @Test
    void createAndRemoveFloorsFollowTheSaturation()
    {
        assertEquals(Gas.MERCURY_VAPOR.saturationMoles() * 0.01f, Gas.MERCURY_VAPOR.createCellMoles(), 0f);
        assertEquals(Gas.MERCURY_VAPOR.createCellMoles() / 2f, Gas.MERCURY_VAPOR.removeCellMoles(), 0f);
        assertEquals(GasSimulator.CREATE_CELL_MOLES, Gas.CARBON_MONOXIDE.createCellMoles(), 0f);
        assertEquals(GasSimulator.REMOVE_CELL_MOLES, Gas.CARBON_MONOXIDE.removeCellMoles(), 0f);
        assertEquals(GasSimulator.CREATE_CELL_MOLES, Gas.ARSENIC_TRIOXIDE.createCellMoles(), 0f, "A fume that condenses at once has no saturation to scale by");
        assertTrue(Gas.MERCURY_VAPOR.isSaturating() && !Gas.ARSENIC_TRIOXIDE.isSaturating() && !Gas.METHANE.isSaturating());
    }

    @Test
    void aCellOfMercuryAtItsFloorIsKeptAndOneBelowItIsNot()
    {
        final float[] kept = new float[Gas.COUNT];
        kept[Gas.MERCURY_VAPOR.ordinal()] = Gas.MERCURY_VAPOR.removeCellMoles() * 1.01f;
        assertTrue(GasMath.holdsMobileContent(kept), "More than a twentieth of saturation is worth a cell");
        assertTrue(GasMath.mobileContent(kept) < GasSimulator.REMOVE_CELL_MOLES, "though that is under the ordinary floor");

        final float[] dilute = new float[Gas.COUNT];
        dilute[Gas.MERCURY_VAPOR.ordinal()] = Gas.MERCURY_VAPOR.removeCellMoles() * 0.9f;
        assertFalse(GasMath.holdsMobileContent(dilute));
    }

    // G9: a film of mercury gives its vapour off again

    @Test
    void aFlushedRoomOfMercuryRefillsFromTheFilmOnTheFloor()
    {
        final float saturation = Gas.MERCURY_VAPOR.saturationMoles();
        final FilmRoom room = new FilmRoom(3, 1, 1);
        room.add(1, 0, 0, Gas.MERCURY_VAPOR, 0.01f);
        room.run(900);
        final float film = room.film(Gas.MERCURY_VAPOR);
        assertTrue(film > 0.005f, "The excess lay down on the floor: " + film);
        assertEquals(0.01f, film + room.total(Gas.MERCURY_VAPOR), 1e-4f, "and nothing was lost doing so");

        room.cells.clear(); // A fan, a draught from a door: the air is clean
        assertEquals(0f, room.total(Gas.MERCURY_VAPOR));
        room.keepFilmCellsAlive(); // What the step of a chunk does for the places where something lies
        room.run(400);

        final float refilled = room.total(Gas.MERCURY_VAPOR);
        assertFalse(room.sites.isEmpty());
        assertTrue(refilled > 0.9f * room.sites.size() * saturation, "Every place where it lay must come back to near saturation, " + room.sites.size() + " cells of " + saturation + " each, got " + refilled);
        assertEquals(film, room.film(Gas.MERCURY_VAPOR) + refilled, 1e-5f, "and every mole of it came out of the film");
    }

    @Test
    void theFilmKeepsTheRoomNearSaturationForAsLongAsItLasts()
    {
        final float saturation = Gas.MERCURY_VAPOR.saturationMoles();
        final FilmRoom room = new FilmRoom(1, 1, 1);
        room.depositFilm(0, 0, 0, Gas.MERCURY_VAPOR, 0.01f);
        room.keepFilmCellsAlive();
        room.run(300);
        assertEquals(saturation, room.total(Gas.MERCURY_VAPOR), saturation * 0.01f, "A closed room with a film in it is at saturation");

        room.cells.get(List.of(0, 0, 0))[Gas.MERCURY_VAPOR.ordinal()] = 0f; // Cleared again
        room.run(300);
        assertEquals(saturation, room.total(Gas.MERCURY_VAPOR), saturation * 0.01f);
        assertTrue(room.film(Gas.MERCURY_VAPOR) < 0.01f);
    }

    @Test
    void aFilmThatIsUsedUpStopsGivingOffVapour()
    {
        final FilmRoom room = new FilmRoom(1, 1, 1);
        room.depositFilm(0, 0, 0, Gas.MERCURY_VAPOR, 2e-5f); // Under a third of what a cell can hold
        room.keepFilmCellsAlive();
        room.run(600);

        assertEquals(0f, room.film(Gas.MERCURY_VAPOR), 1e-9f, "There was not enough to saturate the room, and all of it went into the air");
        assertEquals(2e-5f, room.total(Gas.MERCURY_VAPOR), 1e-7f);
        assertFalse(room.holdsFilm(0, 0, 0));
    }

    @Test
    void theDustOfAnOxideFumeStaysWhereItFell()
    {
        final FilmRoom room = new FilmRoom(3, 3, 3);
        room.add(1, 1, 1, Gas.ARSENIC_TRIOXIDE, 2f);
        room.run(300);
        final float settled = room.film(Gas.ARSENIC_TRIOXIDE);
        assertTrue(settled > 0.5f, "The fume settled");

        room.cells.clear();
        room.run(300);
        assertEquals(0f, room.total(Gas.ARSENIC_TRIOXIDE), "Dust does not give its fume off again when the air is clear");
        assertEquals(settled, room.film(Gas.ARSENIC_TRIOXIDE), 0f);
        assertTrue(Gas.MERCURY_VAPOR.evaporatesFromDeposit() && !Gas.ARSENIC_TRIOXIDE.evaporatesFromDeposit() && !Gas.LEAD_OXIDE_FUME.evaporatesFromDeposit());
    }

    // G11: what counts as a change

    @Test
    void aRoomAtEquilibriumChangesNothing()
    {
        final Room room = new Room(3, 1, 3);
        for (int x = 0; x < 3; x++)
        {
            for (int z = 0; z < 3; z++)
            {
                room.add(x, 0, z, Gas.CARBON_DIOXIDE, 2f);
            }
        }
        final GasSimulator simulator = new GasSimulator();
        for (List<Integer> pos : List.copyOf(room.cells.keySet()))
        {
            simulator.stepCell(room, pos.get(0), pos.get(1), pos.get(2), 1f);
        }
        assertTrue(simulator.takeLargestChange() < GasSimulator.CHANGE_THRESHOLD_MOLES, "An even room is at rest: " + simulator.takeLargestChange());
    }

    @Test
    void aRoomWhereGasIsMovingIsNotAtRest()
    {
        final Room room = new Room(3, 1, 1);
        room.add(0, 0, 0, Gas.CARBON_DIOXIDE, 5f);
        final GasSimulator simulator = new GasSimulator();
        simulator.stepCell(room, 0, 0, 0, 1f);
        assertTrue(simulator.takeLargestChange() > GasSimulator.CHANGE_THRESHOLD_MOLES);
        assertEquals(0f, simulator.takeLargestChange(), "and the count starts again once it is read");
    }

    @Test
    void gasCarriedOffByTheAtmosphereCountsAsAChange()
    {
        final Room room = new Room(1, 1, 1);
        room.roofExchange = 0.3f;
        room.add(0, 0, 0, Gas.CARBON_DIOXIDE, 1f);
        final GasSimulator simulator = new GasSimulator();
        simulator.stepCell(room, 0, 0, 0, 1f);

        assertTrue(simulator.takeLargestChange() > 0.2f, "A cell that is only losing gas through its roof is not at rest, though nothing moved to a neighbour");
    }

    @Test
    void condensingCountsAsAChange()
    {
        final Room room = new Room(1, 1, 1);
        room.add(0, 0, 0, Gas.MERCURY_VAPOR, 0.01f);
        final GasSimulator simulator = new GasSimulator();
        simulator.stepCell(room, 0, 0, 0, 1f);
        assertTrue(simulator.takeLargestChange() > GasSimulator.CHANGE_THRESHOLD_MOLES);
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

    /** A room that keeps what condenses in it, and where, the way the data of a chunk does. */
    private static class FilmRoom extends Room
    {
        final float[] film = new float[Gas.COUNT];
        final java.util.Set<List<Integer>> sites = new java.util.HashSet<>();

        FilmRoom(int width, int height, int depth)
        {
            super(width, height, depth);
        }

        float film(Gas gas)
        {
            return film[gas.ordinal()];
        }

        void depositFilm(int x, int y, int z, Gas gas, float moles)
        {
            deposit(x, y, z, gas, moles);
        }

        @Override
        public void deposit(int x, int y, int z, Gas gas, float moles)
        {
            film[gas.ordinal()] += moles;
            if (gas.evaporatesFromDeposit())
            {
                sites.add(List.of(x, y, z));
            }
        }

        @Override
        public float evaporate(int x, int y, int z, Gas gas, float wanted)
        {
            if (!sites.contains(List.of(x, y, z)))
            {
                return 0;
            }
            final float taken = Math.min(wanted, film[gas.ordinal()]);
            film[gas.ordinal()] -= taken;
            return taken;
        }

        @Override
        public boolean holdsFilm(int x, int y, int z)
        {
            return sites.contains(List.of(x, y, z)) && film[Gas.MERCURY_VAPOR.ordinal()] > 0;
        }

        /** The pass that a chunk makes before its cells, for the places where a film lies. */
        void keepFilmCellsAlive()
        {
            for (List<Integer> site : sites)
            {
                createCell(site.get(0), site.get(1), site.get(2));
            }
        }
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
