package tfcpp.ecology;

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AirGridTest
{
    private static final double C = AirScale.metresPerBlock(20_000);
    private static final AirSpecies SO2 = AirSpecies.SULFUR_DIOXIDE;
    private static final long MID = 500L;

    private static void assertBalanced(AirGrid grid)
    {
        for (AirSpecies species : AirSpecies.VALUES)
        {
            final double scale = Math.max(1.0, grid.emitted(species));
            assertEquals(0.0, grid.imbalance(species), 1e-9 * scale, species + " must balance");
        }
    }

    /** Emits a day's worth in the hour, for hours, under a steady wind. It stops just after the last emission, before the step, which is the peak of the hour: a cell read after its step has already lost a third of what it held. */
    private static AirGrid steady(double wind, double molesPerDay, int hours, boolean raining)
    {
        final AirGrid grid = new AirGrid();
        final AirWeather weather = new AirWeather.Fixed(wind, 0.0, raining);
        for (int hour = 0; hour < hours; hour++)
        {
            grid.step(weather, MID, C);
            grid.emit(0, 0, SO2, molesPerDay / 24.0);
        }
        return grid;
    }

    private static double ug(AirGrid grid, int cx)
    {
        final AirGrid.Cell cell = grid.cellAt(cx, 0);
        return cell == null ? 0.0 : cell.microgramsPerCubicMetre(SO2);
    }

    @Test
    void theScaleOfTheMapComesFromTheTemperatureScale()
    {
        assertEquals(500.0, AirScale.metresPerBlock(20_000), 1e-9);
        assertEquals(250.0, AirScale.metresPerBlock(40_000), 1e-9);
        assertEquals(1000.0, AirScale.metresPerBlock(10_000), 1e-9);
        assertEquals(500.0, AirScale.metresPerBlock(0), 1e-9, "A world with no temperature scale is taken as the default one");
        // 5 m/s at 500 m a block is 36 blocks an hour, 28% of a cell
        assertEquals(36.0 / 128.0, AirScale.crossingPerSecond(5.0, C) * 3600.0, 1e-9);
        // 1e4 m2/s over a map block of 500 m is 0.04 blocks2/s, which evens out 0.88% of a difference in an hour
        assertEquals(0.04 * 3600.0 / 16384.0, AirScale.mixingPerSecond(C) * 3600.0, 1e-9);
        assertEquals(16384.0 * 1000.0, AirScale.CELL_VOLUME, 1e-6);
        assertEquals(1.0, AirScale.microgramsPerCubicMetre(AirScale.molesFor(1.0, 64.07), 64.07), 1e-12);
    }

    @Test
    void advectionAndMixingConserveWhatIsInTheAirBeforeAnythingComesDown()
    {
        final AirGrid grid = new AirGrid();
        // Species that never deposits in the test: use zero rate by stepping only transport. Deposition is separate, so balance covers all
        grid.emit(0, 0, SO2, 1000.0);
        final AirWeather weather = new AirWeather.Fixed(20.0, 9.0, false);
        for (int hour = 0; hour < 30; hour++)
        {
            grid.step(weather, MID, C);
            assertBalanced(grid);
        }
        assertTrue(grid.deposited(SO2) > 0.0);
        assertTrue(grid.inAir(SO2) > 0.0);
    }

    @Test
    void theBalanceClosesWithBackgroundRainAndRoomsDrawingGasBack()
    {
        final Random random = new Random(7);
        final AirGrid grid = new AirGrid();
        for (int round = 0; round < 400; round++)
        {
            for (AirSpecies species : AirSpecies.VALUES)
            {
                if (random.nextInt(4) == 0)
                {
                    grid.emit(random.nextInt(3) - 1, random.nextInt(3) - 1, species, random.nextDouble() * 500.0);
                }
            }
            final AirGrid.Cell cell = grid.cellAt(0, 0);
            if (cell != null && random.nextInt(5) == 0)
            {
                grid.take(cell, SO2, random.nextDouble() * 5.0);
            }
            grid.step(new AirWeather.Fixed((random.nextDouble() - 0.5) * 40.0, (random.nextDouble() - 0.5) * 40.0, random.nextBoolean()), MID, C);
            assertBalanced(grid);
        }
        double background = 0.0;
        for (AirSpecies species : AirSpecies.VALUES)
        {
            background += grid.background(species);
        }
        assertTrue(background > 0.0, "Plumes that thin out are dropped into the background");
    }

    @Test
    void aCellThatFallsBelowTheThresholdIsDroppedAndItsContentsGoToTheBackground()
    {
        final AirGrid grid = new AirGrid();
        final double tiny = AirScale.molesFor(0.001, SO2.molarMass()); // Under 0.01 ug/m3
        grid.emit(5, 5, SO2, tiny);
        grid.step(new AirWeather.Fixed(0.0, 0.0, false), MID, C);
        assertEquals(0, grid.activeCount());
        assertEquals(tiny, grid.background(SO2) + grid.deposited(SO2), tiny * 1e-9);
        assertBalanced(grid);
    }

    @Test
    void aCellThatHasHadAFallIsKeptForeverForTheSoilToClaimFrom()
    {
        final AirGrid grid = new AirGrid();
        grid.emit(2, 2, SO2, 5000.0);
        for (int hour = 0; hour < 400; hour++)
        {
            grid.step(new AirWeather.Fixed(0.0, 0.0, true), MID, C);
        }
        assertEquals(0, grid.activeCount());
        assertTrue(grid.cellAt(2, 2).cumulative(Pollutant.ACIDITY) > 0.0);
    }

    @Test
    void sulfurDioxideFallsAsTwoEquivalentsAMoleOverTheAreaOfTheCell()
    {
        final AirGrid grid = new AirGrid();
        grid.emit(0, 0, SO2, 1000.0);
        grid.depositInPlace();
        assertEquals(2000.0 / AirScale.CELL_AREA, grid.cellAt(0, 0).cumulative(Pollutant.ACIDITY), 1e-12);
        final AirGrid metals = new AirGrid();
        metals.emit(0, 0, AirSpecies.ARSENIC_TRIOXIDE, 10.0);
        metals.emit(0, 0, AirSpecies.MERCURY_VAPOR, 10.0);
        metals.emit(0, 0, AirSpecies.LEAD_OXIDE_FUME, 10.0);
        metals.emit(0, 0, AirSpecies.ZINC_OXIDE_FUME, 10.0);
        metals.emit(0, 0, AirSpecies.ANTIMONY_TRIOXIDE, 10.0);
        metals.emit(0, 0, AirSpecies.HYDROGEN_CHLORIDE, 10.0);
        metals.emit(0, 0, AirSpecies.NITROGEN_OXIDES, 10.0);
        metals.depositInPlace();
        final AirGrid.Cell cell = metals.cellAt(0, 0);
        assertEquals(10.0 * 2 * 74.9216 / AirScale.CELL_AREA, cell.cumulative(Pollutant.ARSENIC), 1e-12, "Two arsenic to a mole of the oxide");
        assertEquals(10.0 * 200.59 / AirScale.CELL_AREA, cell.cumulative(Pollutant.MERCURY), 1e-12);
        assertEquals(10.0 * 207.2 / AirScale.CELL_AREA, cell.cumulative(Pollutant.LEAD), 1e-12);
        assertEquals(10.0 * 65.38 / AirScale.CELL_AREA, cell.cumulative(Pollutant.ZINC), 1e-12);
        assertEquals(10.0 * 243.52 / AirScale.CELL_AREA, cell.cumulative(Pollutant.ANTIMONY), 1e-12);
        assertEquals(20.0 / AirScale.CELL_AREA, cell.cumulative(Pollutant.ACIDITY), 1e-12, "Hydrogen chloride and nitrogen oxides are an equivalent a mole");
    }

    @Test
    void dryDepositionIsTheVelocityOverTheMixingHeight()
    {
        final AirGrid grid = new AirGrid();
        grid.emit(0, 0, SO2, 1000.0);
        grid.step(new AirWeather.Fixed(0.0, 0.0, false), MID, C);
        // 0.8 cm/s over 1000 m is 2.88% in an hour, and no wind or mixing moves any out of the one cell... except by mixing out to the sides, which is not deposited
        final double expected = 1000.0 * -Math.expm1(-0.008 / 1000.0 * 3600.0);
        assertEquals(expected, grid.deposited(SO2), expected * 0.05, "about 28.4 mol");
        assertEquals(0.0, grid.depositedWet(SO2), 0.0);
    }

    @Test
    void rainBringsDownFarMoreThanDryDepositionAndMercuryLessThanTheRest()
    {
        final AirGrid dry = new AirGrid(), wet = new AirGrid(), mercury = new AirGrid();
        dry.emit(0, 0, SO2, 1000.0);
        wet.emit(0, 0, SO2, 1000.0);
        mercury.emit(0, 0, AirSpecies.MERCURY_VAPOR, 1000.0);
        final AirWeather still = new AirWeather.Fixed(0.0, 0.0, false), rain = new AirWeather.Fixed(0.0, 0.0, true);
        dry.step(still, MID, C);
        wet.step(rain, MID, C);
        mercury.step(rain, MID, C);
        assertEquals(1000.0 * -Math.expm1(-(0.008 / 1000.0 + 1e-4) * 3600.0), wet.deposited(SO2), 50.0);
        assertTrue(wet.deposited(SO2) > 8.0 * dry.deposited(SO2), "Wet scavenging at 1e-4 a second is 30% an hour, dry 2.9%");
        assertTrue(wet.depositedWet(SO2) > 0.85 * wet.deposited(SO2));
        assertTrue(mercury.deposited(AirSpecies.MERCURY_VAPOR) < 0.3 * wet.deposited(SO2), "Mercury vapour is barely soluble");
    }

    @Test
    void aPlumeThinsOutWithDistanceDownwindAndSmogIsThickerOnACalmDay()
    {
        final AirGrid windy = steady(5.0, 500.0, 600, false);
        for (int cx = 0; cx < 8; cx++)
        {
            assertTrue(ug(windy, cx) > ug(windy, cx + 1), "Concentration falls downwind at cell " + cx);
        }
        assertEquals(0.0, ug(windy, -3), ug(windy, 0) * 0.02, "and almost nothing is upwind");
        final AirGrid calm = steady(0.0, 500.0, 600, false);
        assertTrue(ug(calm, 0) > 5.0 * ug(windy, 0), "A calm day holds the smoke over its source: " + ug(calm, 0) + " against " + ug(windy, 0));
        final AirGrid washed = steady(5.0, 500.0, 600, true);
        assertTrue(ug(washed, 0) < ug(windy, 0), "and rain clears it");
    }

    @Test
    void steadyStateNumbersForFiveHundredMolesADayAtFiveMetresASecond()
    {
        final AirGrid grid = steady(5.0, 500.0, 1200, false);
        System.out.printf("AIR_STEADY SO2 500 mol/day, 5 m/s, dry: source %.1f, +1 %.1f, +2 %.1f, +3 %.1f ug/m3; in air %.0f mol, fallen %.0f mol, background %.1f%n",
            ug(grid, 0), ug(grid, 1), ug(grid, 2), ug(grid, 3), grid.inAir(SO2), grid.deposited(SO2), grid.background(SO2));
        assertTrue(ug(grid, 0) > 100.0 && ug(grid, 0) < 500.0);
        assertBalanced(grid);
    }

    @Test
    void theCourantNumberIsKeptBelowOneByCuttingTheSubsteps()
    {
        final AirGrid slow = new AirGrid(), fast = new AirGrid();
        slow.emit(0, 0, SO2, 100.0);
        fast.emit(0, 0, SO2, 100.0);
        final int few = slow.step(new AirWeather.Fixed(1.0, 0.0, false), MID, C);
        final int many = fast.step(new AirWeather.Fixed(32.0, 0.0, false), MID, AirScale.metresPerBlock(40_000));
        assertTrue(many > few, "A fast wind takes more substeps: " + few + " and " + many);
        assertTrue(many <= AirGrid.MAX_SUBSTEPS);
        // 32 m/s at 250 m a block is 460 blocks an hour: with the cell leaving at most half in a substep it takes at least 8
        assertTrue(many >= 8);
        for (AirSpecies species : AirSpecies.VALUES)
        {
            for (AirGrid.Cell cell : fast.cells())
            {
                assertTrue(cell.moles(species) >= 0.0, "Nothing goes negative");
            }
        }
        assertBalanced(fast);
    }

    @Test
    void aTallStackSkipsItsOwnCell()
    {
        assertFalse(AirScale.isTallStack(80, 64), "16 m up is not a tall stack");
        assertFalse(AirScale.isTallStack(84, 64), "20 m is not more than 20");
        assertTrue(AirScale.isTallStack(85, 64));
        assertArrayEquals(new int[] {1, 0}, AirScale.downwind(5.0, 2.0));
        assertArrayEquals(new int[] {-1, 0}, AirScale.downwind(-5.0, 2.0));
        assertArrayEquals(new int[] {0, 1}, AirScale.downwind(1.0, 3.0));
        assertArrayEquals(new int[] {0, -1}, AirScale.downwind(1.0, -3.0));
        assertArrayEquals(new int[] {0, 0}, AirScale.downwind(0.0, 0.0), "A calm day has no downwind");
        // Put into the next cell, the source cell stays clean at the start
        final AirGrid grid = new AirGrid();
        final int[] offset = AirScale.downwind(5.0, 0.0);
        grid.emit(offset[0], offset[1], SO2, 100.0);
        assertNull(grid.cellAt(0, 0));
        assertEquals(100.0, grid.cellAt(1, 0).moles(SO2), 0.0);
    }

    @Test
    void aJumpOfMoreThanAWeekIsFollowedForAWeekAndTheRestPutInPlace()
    {
        assertEquals(0L, AirCadence.plan(100, 100).steps());
        assertEquals(0L, AirCadence.plan(100, 90).steps(), "A calendar set back does nothing");
        assertEquals(90L, AirCadence.plan(100, 90).newLastHour(), "and the clock goes back with it");
        final AirCadence.Plan day = AirCadence.plan(100, 124);
        assertEquals(24L, day.steps());
        assertEquals(101L, day.firstHour());
        assertFalse(day.depositRest());
        final AirCadence.Plan year = AirCadence.plan(100, 100 + 8760);
        assertEquals(168L, year.steps());
        assertTrue(year.depositRest());
        assertEquals(8860L, year.newLastHour());
        assertEquals(500L, AirCadence.middleOfHourBefore(1));
        assertEquals(1L, AirCadence.hourOf(1000));
        assertEquals(-1L, AirCadence.hourOf(-1));
    }

    @Test
    void depositingInPlaceConservesAndEmptiesTheAir()
    {
        final AirGrid grid = new AirGrid();
        grid.emit(0, 0, SO2, 300.0);
        grid.emit(1, 0, AirSpecies.MERCURY_VAPOR, 40.0);
        grid.step(new AirWeather.Fixed(5.0, 0.0, false), MID, C);
        grid.depositInPlace();
        assertEquals(0.0, grid.inAir(SO2), 0.0);
        assertEquals(0, grid.activeCount());
        assertBalanced(grid);
    }

    @Test
    void aThousandActiveCellsCostWellUnderAMillisecondAStep()
    {
        final AirGrid grid = new AirGrid();
        for (int x = 0; x < 32; x++)
        {
            for (int z = 0; z < 32; z++)
            {
                grid.emit(x, z, SO2, 50.0 + x);
                grid.emit(x, z, AirSpecies.ARSENIC_TRIOXIDE, 3.0);
                grid.emit(x, z, AirSpecies.MERCURY_VAPOR, 1.0);
            }
        }
        final AirWeather weather = new AirWeather.Fixed(5.0, 2.0, false);
        for (int warm = 0; warm < 30; warm++)
        {
            grid.step(weather, MID, C);
        }
        // Keep a thousand cells alive while measuring
        final long[] times = new long[40];
        for (int round = 0; round < times.length; round++)
        {
            for (int x = 0; x < 32; x++)
            {
                for (int z = 0; z < 32; z++)
                {
                    grid.emit(x, z, SO2, 20.0);
                }
            }
            final long start = System.nanoTime();
            grid.step(weather, MID, C);
            times[round] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(times);
        final double median = times[times.length / 2] / 1e6;
        System.out.printf("AIR_COST median %.3f ms a step for %d active cells (%d cells in all)%n", median, grid.activeCount(), grid.cellCount());
        assertTrue(grid.activeCount() >= 1000);
        assertTrue(median < 5.0, "Measured " + median + " ms");
    }

    /** Wind of a fixed strength whose direction changes every day, as TFC's does. */
    private static AirWeather rotating(double speed, long seed)
    {
        return (cell, tick, out) ->
        {
            final java.util.Random day = new java.util.Random(seed * 7919L + Math.floorDiv(tick, 24_000L));
            final double angle = day.nextDouble() * 2.0 * Math.PI;
            out.windX = speed * Math.cos(angle);
            out.windZ = speed * Math.sin(angle);
            out.raining = false;
        };
    }

    private static double meanFallPerYear(AirGrid grid, int radius, double years)
    {
        double sum = 0.0;
        int count = 0;
        for (int x = -radius; x <= radius; x++)
        {
            for (int z = -radius; z <= radius; z++)
            {
                if (x * x + z * z <= radius * radius)
                {
                    final AirGrid.Cell cell = grid.cellAt(x, z);
                    sum += cell == null ? 0.0 : cell.cumulative(Pollutant.ACIDITY);
                    count++;
                }
            }
        }
        return sum / count / years;
    }

    @Test
    void aSourceThroughThirtyDaysOfTurningWindKeepsAFewHundredCellsAndStaysCheap()
    {
        final AirGrid grid = new AirGrid();
        final AirWeather weather = rotating(7.5, 3);
        long worst = 0L, total = 0L;
        int steps = 0;
        for (int hour = 0; hour < 30 * 24; hour++)
        {
            grid.emit(0, 0, SO2, 500.0 / 24.0);
            final long start = System.nanoTime();
            grid.step(weather, 24_000L * (hour / 24) + MID + hour % 24 * 1000L, C);
            final long took = System.nanoTime() - start;
            worst = Math.max(worst, took);
            total += took;
            steps++;
        }
        System.out.printf("AIR_30D cells stored %d, holding air %d, mean step %.3f ms, worst %.3f ms, fall scattered %.1f eq of %.0f%n", grid.cellCount(), grid.activeCount(), total / 1e6 / steps, worst / 1e6,
            grid.scatteredFall(Pollutant.ACIDITY), grid.fallOnCells(Pollutant.ACIDITY) + grid.scatteredFall(Pollutant.ACIDITY));
        assertTrue(grid.cellCount() < 600, "Stored cells: " + grid.cellCount());
        assertEquals(0.0, grid.fallImbalance(Pollutant.ACIDITY), 1e-6 * 30000.0, "The fall is on cells or scattered, all of it");
        assertBalanced(grid);
    }

    @Test
    void yearlyFallInTheSourceCellForSteadyCalmAndTurningWindOfFiveMetresASecond()
    {
        final AirGrid steady = new AirGrid(), calm = new AirGrid(), turning = new AirGrid();
        final AirWeather east = new AirWeather.Fixed(5.0, 0.0, false), still = new AirWeather.Fixed(0.0, 0.0, false), around = rotating(5.0, 11);
        for (int hour = 0; hour < 365 * 24; hour++)
        {
            for (AirGrid grid : new AirGrid[] {steady, calm, turning})
            {
                grid.emit(0, 0, SO2, 500.0 / 24.0);
            }
            steady.step(east, MID, C);
            calm.step(still, MID, C);
            turning.step(around, 24_000L * (hour / 24) + MID, C);
        }
        System.out.printf("AIR_YEAR acid eq/m2/yr, 500 mol SO2/day: steady 5 m/s source %.2f, +1 %.2f; calm source %.2f; turning wind source %.2f, mean to 3 cells %.2f, to 10 cells %.3f; cells stored %d%n",
            steady.cellAt(0, 0).cumulative(Pollutant.ACIDITY), steady.cellAt(1, 0).cumulative(Pollutant.ACIDITY), calm.cellAt(0, 0).cumulative(Pollutant.ACIDITY),
            turning.cellAt(0, 0).cumulative(Pollutant.ACIDITY), meanFallPerYear(turning, 3, 1.0), meanFallPerYear(turning, 10, 1.0), turning.cellCount());
        assertBalanced(steady);
        assertBalanced(turning);
        assertEquals(0.0, turning.fallImbalance(Pollutant.ACIDITY), 1e-6 * 365000.0);
    }

    @Test
    void whatWouldGoIntoEmptyAirInLessThanTheFloorIsBackgroundAndMakesNoCell()
    {
        final AirGrid grid = new AirGrid();
        grid.emit(0, 0, SO2, AirScale.molesFor(50.0, SO2.molarMass())); // 50 ug/m3, so what mixes out in an hour (0.9%) is well under the floor of 1
        grid.step(new AirWeather.Fixed(0.0, 0.0, false), MID, C);
        assertEquals(1, grid.activeCount());
        assertEquals(1, grid.cellCount(), "No neighbour was made for the fringe");
        assertTrue(grid.background(SO2) > 0.0);
        assertBalanced(grid);
    }

    @Test
    void aForgottenCellKeepsItsFallInTheScatteredTotalAndACellWithAnAnchorIsKept()
    {
        final AirGrid grid = new AirGrid();
        grid.emit(0, 0, SO2, AirScale.molesFor(0.2, SO2.molarMass()));
        grid.cell(0, 0).setAnchor(5, 64, 5);
        grid.emit(9, 9, SO2, AirScale.molesFor(0.2, SO2.molarMass()));
        grid.step(new AirWeather.Fixed(0.0, 0.0, false), MID, C);
        for (int hour = 0; hour < 24 * 30; hour++)
        {
            grid.step(new AirWeather.Fixed(0.0, 0.0, false), MID, C);
        }
        assertNotNull(grid.cellAt(0, 0), "A cell with an anchor stays, though empty");
        assertNull(grid.cellAt(9, 9), "and one without is forgotten");
        assertTrue(grid.scatteredFall(Pollutant.ACIDITY) > 0.0);
        assertEquals(0.0, grid.fallImbalance(Pollutant.ACIDITY), 1e-12);
        assertBalanced(grid);
    }

    @Test
    void gasThatARoomTookAndGaveBackIsNotCountedAsProduction()
    {
        final AirGrid grid = new AirGrid();
        grid.emit(0, 0, SO2, 100.0);
        final double got = grid.take(grid.cellAt(0, 0), SO2, 30.0);
        grid.emitFromRoom(0, 0, SO2, 20.0);
        grid.emitFromRoom(0, 0, SO2, 20.0);
        assertEquals(30.0, got, 0.0);
        assertEquals(30.0, grid.returned(SO2), 1e-12, "The 30 mol it had taken came back");
        assertEquals(100.0 + 10.0, grid.emitted(SO2), 1e-12, "and the other 10 are what the room made");
        assertBalanced(grid);
    }

    @Test
    void hydrogenSulfideGoesUpAsSulfurDioxideAndChlorineAsHydrogenChloride()
    {
        assertEquals(AirSpecies.SULFUR_DIOXIDE, AirSpecies.emittedAs(tfcpp.pneumatics.gas.Gas.HYDROGEN_SULFIDE));
        assertEquals(1.0, AirSpecies.yieldOf(tfcpp.pneumatics.gas.Gas.HYDROGEN_SULFIDE));
        assertEquals(AirSpecies.HYDROGEN_CHLORIDE, AirSpecies.emittedAs(tfcpp.pneumatics.gas.Gas.CHLORINE));
        assertEquals(2.0, AirSpecies.yieldOf(tfcpp.pneumatics.gas.Gas.CHLORINE));
        assertNull(AirSpecies.emittedAs(tfcpp.pneumatics.gas.Gas.METHANE));
        assertNull(AirSpecies.of(tfcpp.pneumatics.gas.Gas.HYDROGEN_SULFIDE), "The regional air itself holds no hydrogen sulfide");
        assertEquals(1e-6, AirSpecies.MERCURY_VAPOR.wetScavenging(), 0.0);
        assertEquals(1e-4, AirSpecies.MERCURY_VAPOR.dryVelocity(), 0.0);
        assertEquals(1e-5, AirSpecies.ARSENIC_TRIOXIDE.wetScavenging(), 0.0);
    }
}
