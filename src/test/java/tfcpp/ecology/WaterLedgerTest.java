package tfcpp.ecology;

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterLedgerTest
{
    private static final double C = AirScale.metresPerBlock(20_000);
    /** A typical edge of TFC: 2.7 grid cells and a little wiggle, some 370 blocks. */
    private static final double EDGE_BLOCKS = 370.0;
    private static final Pollutant ACID = Pollutant.ACIDITY, LEAD = Pollutant.LEAD, SEWAGE = Pollutant.SEWAGE, TAR = Pollutant.TAR;

    /** A river of n edges, 1 .. n, each draining to the next, the last to the sea. The width goes up by 2 each edge, as it does in TFC. */
    private static WaterLedger chain(int n, double firstWidth, double alkalinity, double edgeBlocks)
    {
        final WaterLedger ledger = new WaterLedger();
        for (int i = 1; i <= n; i++)
        {
            ledger.register(ReachInfo.river(i, Math.min(24.0, firstWidth + 2.0 * (i - 1)), edgeBlocks, C, alkalinity, i == n ? ReachInfo.SEA : i + 1, 0, 0));
        }
        return ledger;
    }

    private static void run(WaterLedger ledger, int hours, WaterLedger.RainSource rain)
    {
        for (int hour = 0; hour < hours; hour++)
        {
            ledger.step(1.0, rain);
        }
    }

    private static void assertBalanced(WaterLedger ledger)
    {
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            final double scale = Math.max(1.0, ledger.input(pollutant));
            assertEquals(0.0, ledger.imbalance(pollutant), 1e-9 * scale, pollutant + " must balance");
        }
    }

    private static double perLitre(WaterLedger ledger, long id, Pollutant pollutant)
    {
        final WaterLedger.Reach reach = ledger.reach(id);
        return reach == null ? 0.0 : reach.perLitre(pollutant);
    }

    @Test
    void aReachIsTheVisibleRiverWithTheCompressedTravelOfTheMap()
    {
        // TFC: the wet channel is 1.118 w each side of the middle (distFac = 0.8 r^2/w^2 under 1), with 2 + D(1 - distFac) of water, D = clampedMap(w^2, 64, 576, 3, 6)
        assertEquals(3.0, ReachInfo.Hydraulics.tfcDepth(8.0), 1e-12);
        assertEquals(6.0, ReachInfo.Hydraulics.tfcDepth(24.0), 1e-12);
        assertEquals(3.0 + 3.0 * (196.0 - 64.0) / 512.0, ReachInfo.Hydraulics.tfcDepth(14.0), 1e-12);
        assertEquals(3.0, ReachInfo.Hydraulics.tfcDepth(4.0), 1e-12, "clamped");
        assertEquals(2.0 * 8.0 / Math.sqrt(0.8), ReachInfo.Hydraulics.visibleWidth(8.0), 1e-12);
        assertEquals(17.89, ReachInfo.Hydraulics.visibleWidth(8.0), 0.01, "A visible channel of 2.236 w");
        assertEquals(4.0, ReachInfo.Hydraulics.meanDepth(8.0), 1e-12, "4 m deep on average at 8");
        assertEquals(6.0, ReachInfo.Hydraulics.meanDepth(24.0), 1e-12, "and 6 at 24");
        // The cross-section agrees with a numerical integral of TFC's bed
        for (double w : new double[] {8.0, 14.0, 24.0})
        {
            double area = 0.0;
            final double a = w / Math.sqrt(0.8), d = ReachInfo.Hydraulics.tfcDepth(w);
            final int n = 200_000;
            for (int i = 0; i < n; i++)
            {
                final double r = -a + (i + 0.5) * 2.0 * a / n, dist = 0.8 * r * r / (w * w);
                area += Math.max(0.0, 2.0 + d * (1.0 - dist)) * 2.0 * a / n;
            }
            assertEquals(area, ReachInfo.Hydraulics.crossSection(w), area * 1e-6, "w = " + w);
        }
        assertEquals(71.55, ReachInfo.Hydraulics.crossSection(8.0), 0.01);
        final ReachInfo edge = ReachInfo.river(7, 8.0, EDGE_BLOCKS, C, 0.1, ReachInfo.SEA, 0, 0);
        // The volume is game volume: cross-section times 370 blocks, which are metres
        assertEquals(ReachInfo.Hydraulics.crossSection(8.0) * 370.0, edge.volume(), 1e-6);
        // Travel is compressed like the wind: 370 blocks at 500 m a block is 185 km, which at half a metre a second is 4.28 days
        assertEquals(370_000.0, edge.residenceSeconds(), 1e-6);
        assertEquals(edge.volume() / edge.residenceSeconds(), edge.discharge(), 1e-12, "The effective discharge is the volume over the residence");
        assertEquals(ReachInfo.Hydraulics.crossSection(8.0) * 0.5 / 500.0, edge.discharge(), 1e-12, "which is cross-section times u over the metres to a block");
        assertEquals(ReachInfo.Hydraulics.visibleWidth(8.0) * 370.0, edge.surfaceArea(), 1e-6, "The sky sees the visible width times the game length");
        final ReachInfo lake = ReachInfo.lake(3, 4, 3.0);
        assertTrue(lake.still() && lake.residenceSeconds() == Double.POSITIVE_INFINITY && lake.discharge() == 0.0);
        assertEquals(128.0 * 128.0 * 5.0, lake.volume(), 1e-9, "A lake cell is 128 by 128 by 5 m, 81 920 m3");
        assertEquals(128.0 * 128.0, lake.surfaceArea(), 1e-9);
        assertTrue(ReachInfo.isLake(lake.id()) && !ReachInfo.isLake(edge.id()) && lake.id() != ReachInfo.SEA);
        assertNotEquals(ReachInfo.riverId(1.5, 2.5, 3.5, 4.5), ReachInfo.riverId(1.5, 2.5, 3.5, 4.75));
        assertEquals(ReachInfo.riverId(1.5, 2.5, 3.5, 4.5), ReachInfo.riverId(1.5, 2.5, 3.5, 4.5), "An id is the same for the same vertices, which is what makes it stable");
    }

    @Test
    void aPulseTravelsDownAChainAndIsDilutedWhereAnotherRiverJoins()
    {
        // Two tributaries (1 and 2, 8 m wide) join into 3 (10 m), which runs to the sea through 4 (12 m). The pulse is acid in water with no alkalinity: it neither decays nor settles
        final WaterLedger ledger = new WaterLedger();
        ledger.register(ReachInfo.river(1, 8.0, EDGE_BLOCKS, C, 0.0, 3, 0, 0));
        ledger.register(ReachInfo.river(2, 8.0, EDGE_BLOCKS, C, 0.0, 3, 0, 0));
        ledger.register(ReachInfo.river(3, 10.0, EDGE_BLOCKS, C, 0.0, 4, 0, 0));
        ledger.register(ReachInfo.river(4, 12.0, EDGE_BLOCKS, C, 0.0, ReachInfo.SEA, 0, 0));
        ledger.add(1, ACID, 1000.0);
        final double[] peak = new double[5];
        final int[] peakHour = new int[5];
        for (int hour = 1; hour <= 150 * 24; hour++)
        {
            ledger.step(1.0, null);
            for (int id = 1; id <= 4; id++)
            {
                final double c = perLitre(ledger, id, ACID);
                if (c > peak[id])
                {
                    peak[id] = c;
                    peakHour[id] = hour;
                }
            }
            assertEquals(0.0, ledger.imbalance(ACID), 1e-9, "Every hour the books close");
        }
        assertTrue(peak[1] > peak[3] && peak[3] > peak[4], "The pulse falls as it goes down: " + peak[1] + ", " + peak[3] + ", " + peak[4]);
        assertEquals(0.0, peak[2], 0.0, "Nothing went up the other tributary");
        assertTrue(peakHour[1] < peakHour[3] && peakHour[3] < peakHour[4], "and arrives later the further down it is: " + peakHour[3] + ", " + peakHour[4]);
        // The join: the pulse enters a reach of 1.25 times the volume (10 m wide, a deeper river), so it is diluted at least that much
        final double volumeRatio = ledger.info(3).volume() / ledger.info(1).volume();
        assertTrue(peak[3] < peak[1] / volumeRatio * 1.001, "The join dilutes it by the volumes: " + volumeRatio);
        assertEquals(1000.0, ledger.toSea(ACID) + ledger.inWater(ACID) + ledger.dropped(ACID), 1e-6, "All of it is in the water or at the sea, less the dust that was too little to keep");
        assertTrue(ledger.toSea(ACID) > 990.0, "and nearly all at the sea after five months");
    }

    @Test
    void theAlkalinityOfCarbonateRockNeutralisesAcidAndThatOfGraniteDoesNot()
    {
        // 1e5 equivalents of acid (some 3000 L of vitriol) into an 8 m river. The effective discharge is 0.0716 m3/s, so the alkalinity that comes through is 0.21 eq/s of carbonate water (3 meq/L), 0.0072 eq/s of granite water (0.1 meq/L): 18 500 and 620 eq a day, and the acid lingers for days in each reach
        final WaterLedger limestone = chain(3, 8.0, WaterConstants.CARBONATE_ALKALINITY, EDGE_BLOCKS), granite = chain(3, 8.0, WaterConstants.SILICATE_ALKALINITY, EDGE_BLOCKS);
        limestone.add(1, ACID, 1.0e5);
        granite.add(1, ACID, 1.0e5);
        run(limestone, 24 * 30, null);
        run(granite, 24 * 30, null);
        System.out.printf("1e5 eq of acid in an 8 m river: limestone neutralises %.0f and passes %.0f to the sea, granite neutralises %.0f and passes %.0f%n", limestone.neutralised(ACID), limestone.toSea(ACID), granite.neutralised(ACID), granite.toSea(ACID));
        assertEquals(1.0e5, limestone.neutralised(ACID), 1.0, "The limestone river uses up all of it");
        assertEquals(0.0, limestone.toSea(ACID), 1.0, "and none reaches the sea as acid");
        assertTrue(granite.neutralised(ACID) < 6.0e4, "The granite river uses up only part of it: " + granite.neutralised(ACID));
        assertTrue(granite.toSea(ACID) > 4.0e4, "and the rest goes down to the sea: " + granite.toSea(ACID));
        assertBalanced(limestone);
        assertBalanced(granite);
    }

    @Test
    void aSteadyAcidThatIsLessThanTheAlkalinityFluxIsAllUsedUpAndAMoreIsNot()
    {
        final double flux = ReachInfo.river(1, 8.0, EDGE_BLOCKS, C, 0.1, ReachInfo.SEA, 0, 0).alkalinityFluxPerSecond(); // eq/s
        assertEquals(0.1 * ReachInfo.river(1, 8.0, EDGE_BLOCKS, C, 0.1, ReachInfo.SEA, 0, 0).discharge(), flux, 1e-12, "meq/L times m3/s is equivalents per second");
        final WaterLedger below = chain(1, 8.0, 0.1, EDGE_BLOCKS), above = chain(1, 8.0, 0.1, EDGE_BLOCKS);
        for (int hour = 0; hour < 24 * 20; hour++)
        {
            below.add(1, ACID, flux * 3600.0 * 0.5);
            above.add(1, ACID, flux * 3600.0 * 2.0);
            below.step(1.0, null);
            above.step(1.0, null);
        }
        assertEquals(0.0, below.toSea(ACID) + below.inWater(ACID), flux * 3600.0 * 0.1, "Half the alkalinity flux of acid is used up as it comes");
        assertTrue(above.toSea(ACID) + above.inWater(ACID) > 0.4 * above.input(ACID), "Twice the flux leaves most of its excess in the river");
        assertBalanced(below);
        assertBalanced(above);
    }

    @Test
    void stillWaterNeutralisesAgainstItsStockAndRecoversSlowly()
    {
        final WaterLedger ledger = new WaterLedger();
        final ReachInfo granite = ReachInfo.lake(0, 0, 0.1), limestone = ReachInfo.lake(1, 0, 3.0);
        ledger.register(granite);
        ledger.register(limestone);
        // The stock is the alkalinity times the volume: 8192 eq for granite, 2.46e5 for limestone
        assertEquals(8192.0, granite.alkalinityStock(), 1e-6);
        ledger.add(granite.id(), ACID, 3.0e4);
        ledger.add(limestone.id(), ACID, 3.0e4);
        run(ledger, 24 * 10, null);
        final WaterLedger.Reach acidLake = ledger.reach(granite.id());
        assertNotNull(acidLake);
        assertTrue(acidLake.water(ACID) > 2.0e4, "The granite lake is left with the acid its stock could not take: " + acidLake.water(ACID));
        assertTrue(acidLake.perLitre(ACID) * 1e6 > 100.0, "which is more than 100 microequivalents a litre");
        assertTrue(ledger.reach(limestone.id()) == null || ledger.reach(limestone.id()).water(ACID) < 1e-6, "while the limestone lake took all of it");
        assertTrue(ledger.reach(limestone.id()).reserve() < limestone.alkalinityStock(), "and its stock is the lower for it, and comes back over the months");
        final double before = ledger.reach(limestone.id()).reserve();
        run(ledger, 24 * 100, null);
        assertTrue(ledger.reach(limestone.id()) == null || ledger.reach(limestone.id()).reserve() > before, "The stock is renewed by the catchment");
        assertBalanced(ledger);
    }

    @Test
    void metalsSettleToTheBedAtTheRateOfTheBoundShare()
    {
        // A lake keeps its water, so that only settling acts. 0.7 * 0.03 = 0.021 a day
        final WaterLedger ledger = new WaterLedger();
        final ReachInfo lake = ReachInfo.lake(0, 0, 0.1);
        ledger.register(lake);
        ledger.add(lake.id(), LEAD, 1000.0);
        run(ledger, 24 * 30, null);
        final double water = ledger.reach(lake.id()).water(LEAD), bed = ledger.reach(lake.id()).sediment(LEAD);
        assertEquals(1000.0 * Math.exp(-0.021 * 30.0), water, 1000.0 * 1e-3, "The water column of a lake loses 2.1% a day");
        assertEquals(1000.0, water + bed, 1e-9, "and the bed has what it lost");
        run(ledger, 24 * 365 * 10, null);
        assertTrue(ledger.reach(lake.id()).sediment(LEAD) > 999.99, "With no rain the bed keeps it for ever: ten years later it holds all of it");
        assertBalanced(ledger);
    }

    @Test
    void heavyRainStirsTheBedAndTheTwoPoolsFindTheirBalance()
    {
        final WaterLedger ledger = new WaterLedger();
        final ReachInfo lake = ReachInfo.lake(0, 0, 0.1);
        ledger.register(lake);
        ledger.add(lake.id(), LEAD, 1000.0);
        run(ledger, 24 * 200, null);
        final double bedBefore = ledger.reach(lake.id()).sediment(LEAD);
        // 400 days of heavy rain: dW/dt = -a W + b S, with a = 0.021 and b = 0.01 a day, ends at W = T b/(a+b) = 322.6 g
        final WaterLedger.RainSource storm = info -> 1.0;
        run(ledger, 24 * 400, storm);
        final double water = ledger.reach(lake.id()).water(LEAD);
        assertTrue(ledger.reach(lake.id()).sediment(LEAD) < bedBefore, "The bed gave some back");
        assertEquals(1000.0 * 0.01 / 0.031, water, 1000.0 * 2e-3, "to the balance of settling and scour: " + water);
        // A shower is not a storm
        final WaterLedger quiet = new WaterLedger();
        quiet.register(lake);
        quiet.add(lake.id(), LEAD, 1000.0);
        run(quiet, 24 * 200, info -> WaterConstants.HEAVY_RAIN - 0.01);
        assertEquals(bedBefore, quiet.reach(lake.id()).sediment(LEAD), 1e-9, "Rain below the heavy threshold moves nothing");
        assertBalanced(ledger);
    }

    @Test
    void sewageAndTarBreakDownAtTheirRates()
    {
        final WaterLedger ledger = new WaterLedger();
        final ReachInfo lake = ReachInfo.lake(0, 0, 0.1);
        ledger.register(lake);
        ledger.add(lake.id(), SEWAGE, 1000.0);
        ledger.add(lake.id(), TAR, 10.0);
        run(ledger, 24 * 10, null);
        assertEquals(100.0, ledger.reach(lake.id()).water(SEWAGE), 0.1, "T90 of 10 days in a lake: a tenth left");
        assertEquals(5.0, ledger.reach(lake.id()).water(TAR), 0.005, "A half-life of 10 days");
        assertEquals(900.0, ledger.decayed(SEWAGE), 0.1);
        // In a river, sewage dies in 2 days, if the river is so long that nothing leaves in that time
        final WaterLedger river = new WaterLedger();
        river.register(ReachInfo.river(1, 8.0, 1.0e6, C, 0.1, ReachInfo.SEA, 0, 0));
        river.add(1, SEWAGE, 1000.0);
        run(river, 24 * 2, null);
        assertEquals(100.0, river.reach(1).water(SEWAGE), 0.1, "T90 of 2 days in a river");
        assertBalanced(ledger);
        assertBalanced(river);
    }

    @Test
    void aSteadyDischargeSettlesToTheConcentrationThatTheBalanceOfAReachSays()
    {
        // A mine puts 100 g of lead a day into edge 1 of a river of four edges (8, 10, 12, 14 m wide). In a steady state a reach passes on 1/(1 + k tau) of what comes in, k = 0.07 a day the settling rate of the whole, tau the residence
        final WaterLedger ledger = chain(4, 8.0, 0.1, EDGE_BLOCKS);
        for (int hour = 0; hour < 24 * 400; hour++)
        {
            ledger.add(1, LEAD, 100.0 / 24.0);
            ledger.step(1.0, null);
        }
        double flux = 100.0; // g a day entering the reach
        final double k = WaterConstants.PARTICLE_BOUND * WaterConstants.SETTLING_PER_DAY;
        for (int id = 1; id <= 4; id++)
        {
            final ReachInfo info = ledger.info(id);
            final double tauDays = info.residenceSeconds() / 86_400.0;
            final double expected = flux / (info.discharge() * 86_400.0 * 1000.0 * (1.0 + k * tauDays)); // g a litre
            final double got = perLitre(ledger, id, LEAD);
            assertEquals(expected, got, expected * 0.02, "Reach " + id + ": " + got * 1e6 + " ug/L against " + expected * 1e6);
            flux /= 1.0 + k * tauDays;
        }
        final double threeBelow = perLitre(ledger, 4, LEAD) * 1e6;
        System.out.printf("Three edges below a mine that puts 100 g a day of lead into the river: %.4f ug/L; %.1f g/day pass on to the sea; settled so far %.0f g%n", threeBelow, flux, ledger.onBed(LEAD));
        assertTrue(threeBelow > 1.0 && threeBelow < 10.0, "of the order of a few micrograms a litre: " + threeBelow);
        assertBalanced(ledger);
    }

    @Test
    void everythingThatGoesInIsAccountedForAfterManyRandomInputsAndStorms()
    {
        final Random random = new Random(7);
        final WaterLedger ledger = new WaterLedger();
        // A little tree: 1,2 -> 5; 3,4 -> 6; 5,6 -> 7 -> sea; and two lakes
        final long[][] edges = {{1, 5, 8}, {2, 5, 8}, {3, 6, 8}, {4, 6, 8}, {5, 7, 12}, {6, 7, 12}, {7, ReachInfo.SEA, 16}};
        for (long[] edge : edges)
        {
            ledger.register(ReachInfo.river(edge[0], edge[2], 100 + 100 * random.nextInt(4), C, random.nextBoolean() ? 3.0 : 0.1, edge[1], 0, 0));
        }
        final long lake = ReachInfo.lakeId(0, 0);
        ledger.register(ReachInfo.lake(0, 0, 0.1));
        for (int round = 0; round < 400; round++)
        {
            final int inputs = random.nextInt(4);
            for (int i = 0; i < inputs; i++)
            {
                final Pollutant pollutant = Pollutant.VALUES[random.nextInt(Pollutant.COUNT)];
                final long target = random.nextInt(8) == 0 ? lake : 1 + random.nextInt(7);
                ledger.add(target, pollutant, random.nextDouble() * (pollutant == ACID ? 5000.0 : 200.0));
            }
            if (random.nextInt(10) == 0)
            {
                ledger.addToSea(LEAD, 3.0);
            }
            final boolean storm = random.nextInt(3) == 0;
            ledger.step(1.0 + random.nextInt(30), info -> storm ? 0.9 : 0.0);
            assertBalanced(ledger);
        }
        run(ledger, 24 * 365, null);
        assertBalanced(ledger);
    }

    @Test
    void aStepThatIsLongerThanTheResidenceIsCutIntoSubstepsAndKeepsTheBooksAndThePositivity()
    {
        // 15 blocks of river is 7.5 km, 15 000 s or 4 hours of residence: a step of 24 hours passes it through six times over
        final WaterLedger coarse = chain(5, 8.0, 0.0, 15.0), fine = chain(5, 8.0, 0.0, 15.0);
        coarse.add(1, LEAD, 1000.0);
        fine.add(1, LEAD, 1000.0);
        coarse.step(24.0, null);
        for (int hour = 0; hour < 24; hour++)
        {
            fine.step(1.0, null);
        }
        assertBalanced(coarse);
        for (WaterLedger.Reach reach : coarse.reaches())
        {
            for (Pollutant pollutant : Pollutant.VALUES)
            {
                assertTrue(reach.water(pollutant) >= 0.0 && reach.sediment(pollutant) >= 0.0, "Nothing goes negative");
            }
        }
        System.out.printf("Coarse step of 24 h against 24 of 1 h: %.1f g and %.1f g at the sea of 1000%n", coarse.toSea(LEAD), fine.toSea(LEAD));
        assertEquals(fine.toSea(LEAD), coarse.toSea(LEAD), 0.1 * fine.toSea(LEAD) + 1.0, "A coarse step agrees with 24 fine ones to the order of the scheme: " + coarse.toSea(LEAD) + " against " + fine.toSea(LEAD));
    }

    @Test
    void whatWaitsForItsReachIsCountedAndGoesInOrToTheSea()
    {
        final WaterLedger ledger = chain(2, 8.0, 0.1, EDGE_BLOCKS);
        ledger.addPending(0, 42L, LEAD, 10.0);
        ledger.addPending(0, 42L, ACID, 5.0);
        ledger.addPending(2, 43L, LEAD, 4.0);
        assertEquals(14.0, ledger.input(LEAD), 1e-12, "It is counted as put in");
        assertEquals(14.0, ledger.pendingTotal(LEAD), 1e-12);
        assertEquals(0.0, ledger.imbalance(LEAD), 1e-12);
        assertEquals(2, ledger.pendingCount());
        ledger.movePending(0, 42L, 2L);
        ledger.movePending(2, 43L, ReachInfo.SEA);
        assertEquals(10.0, ledger.reach(2).water(LEAD), 1e-12);
        assertEquals(4.0, ledger.toSea(LEAD), 1e-12);
        assertEquals(0, ledger.pendingCount());
        assertBalanced(ledger);
        ledger.movePending(0, 42L, 1L); // Nothing waits there now
        assertBalanced(ledger);
    }

    @Test
    void dustBelowTheFloorIsDroppedAndCounted()
    {
        final WaterLedger ledger = chain(1, 8.0, 0.1, EDGE_BLOCKS);
        ledger.add(1, LEAD, 1e-9);
        ledger.step(1.0, null);
        assertEquals(1e-9, ledger.dropped(LEAD) + ledger.toSea(LEAD), 1e-15);
        assertEquals(0, ledger.activeCount(), "A reach that holds nothing is forgotten");
        assertBalanced(ledger);
    }

    @Test
    void theRulesOfWhereAnInputGoes()
    {
        final long edge = 77, lake = ReachInfo.lakeId(1, 2);
        assertEquals(edge, WaterRoute.DISCHARGE_RIVER.target(edge, 100.0, lake), "River water takes the nearest edge within 128 blocks");
        assertEquals(WaterRoute.SOIL, WaterRoute.DISCHARGE_RIVER.target(edge, 130.0, lake), "River water far from any river of the graph is no body of water: the spill goes to the soil");
        assertEquals(WaterRoute.SOIL, WaterRoute.DISCHARGE_RIVER.target(ReachInfo.SEA, Double.POSITIVE_INFINITY, lake));
        assertEquals(4, WaterRoute.DISCHARGE_RIVER.channel(true) - WaterRoute.DISCHARGE_RIVER.channel(false), "The bed has a list of its own");
        assertTrue(LevelWaters.isCoastBiome("tfc", "ocean") && LevelWaters.isCoastBiome("tfc", "shore") && LevelWaters.isCoastBiome("tfc", "oceanic_mountain_lake") && !LevelWaters.isCoastBiome("tfc", "plains"));
        assertEquals(lake, WaterRoute.DISCHARGE_STILL.target(edge, 1.0, lake), "Still water is a lake whatever river is near");
        assertEquals(edge, WaterRoute.RUNOFF_RIVER.target(edge, 60.0, lake));
        assertEquals(ReachInfo.SEA, WaterRoute.RUNOFF_RIVER.target(edge, 70.0, lake), "Runoff from soil beyond 64 blocks goes nowhere");
        assertEquals(lake, WaterRoute.RUNOFF_LAKE.target(ReachInfo.SEA, Double.POSITIVE_INFINITY, lake));
        assertTrue(LevelWaters.isLakeBiome("tfc", "lake") && LevelWaters.isLakeBiome("tfc", "rift_lake") && LevelWaters.isLakeBiome("tfc", "mountain_lake"));
        assertFalse(LevelWaters.isLakeBiome("tfc", "oceanic_mountain_lake"), "Salt");
        assertFalse(LevelWaters.isLakeBiome("tfc", "plains") || LevelWaters.isLakeBiome("minecraft", "lake"));
    }

    @Test
    void theWaterBodyReadsTheContentPerLitre()
    {
        final WaterBody body = new WaterBody(WaterBody.Type.RIVER, 5, true, null, new double[Pollutant.COUNT], new double[Pollutant.COUNT], new double[Pollutant.COUNT]);
        assertEquals(7.0, body.pH(), 0.0, "Water with no free acid is neutral");
        body.perLitre()[ACID.ordinal()] = 1e-4;
        assertEquals(4.0, body.pH(), 1e-9, "100 microequivalents of strong acid a litre is pH 4");
        body.perLitre()[LEAD.ordinal()] = 5e-6;
        assertEquals(5.0, body.microgramsPerLitre(LEAD), 1e-9);
    }


    @Test
    void moltenMetalAndMercuryGoStraightToTheBedAndOnlyAHeavyRainStirsThemUp()
    {
        final WaterLedger ledger = chain(2, 8.0, 0.1, EDGE_BLOCKS);
        ledger.addToBed(1, LEAD, 500.0);
        assertEquals(500.0, ledger.reach(1).sediment(LEAD), 0.0);
        assertEquals(0.0, ledger.reach(1).water(LEAD), 0.0, "None of it is in the water column");
        assertEquals(500.0, ledger.input(LEAD), 0.0);
        run(ledger, 24 * 30, null);
        assertEquals(500.0, ledger.reach(1).sediment(LEAD), 1e-9, "Dry weather moves nothing");
        assertEquals(0.0, ledger.toSea(LEAD), 0.0);
        run(ledger, 24 * 30, info -> 0.9);
        assertTrue(ledger.reach(1).sediment(LEAD) < 500.0 && ledger.toSea(LEAD) + ledger.inWater(LEAD) + ledger.reach(2).sediment(LEAD) > 0.0, "A storm lifts some of it, and it goes on down the river");
        assertBalanced(ledger);
        // Waiting on the bed list goes to the bed
        ledger.addPending(WaterRoute.DISCHARGE_RIVER.channel(true), 9L, Pollutant.MERCURY, 40.0);
        ledger.movePending(WaterRoute.DISCHARGE_RIVER.channel(true), 9L, 2L);
        assertEquals(40.0, ledger.reach(2).sediment(Pollutant.MERCURY), 0.0);
        assertBalanced(ledger);
        // What waited and turns out to be soil leaves the books
        ledger.addPending(0, 10L, LEAD, 7.0);
        final double before = ledger.input(LEAD);
        assertEquals(7.0, ledger.takePending(0, 10L)[LEAD.ordinal()], 0.0);
        assertEquals(before - 7.0, ledger.input(LEAD), 1e-12, "It is not counted as put in");
        assertBalanced(ledger);
    }

    @Test
    void theAirsFallOnAReachIsATakenAsAnInputOfItsOwnOverTheVisibleSurface()
    {
        final WaterLedger ledger = chain(1, 8.0, 3.0, EDGE_BLOCKS);
        final ReachInfo info = ledger.info(1);
        ledger.claimFall(1, LEAD, 0.002); // 2 mg a square metre has fallen on the cell since the world began
        assertEquals(0.002 * info.surfaceArea(), ledger.fromAir(LEAD), 1e-12, "The reach takes the surface of the visible channel, 17.9 m by 370 m");
        assertEquals(0.0, ledger.input(LEAD), 0.0, "and it is not one of the inputs");
        assertBalanced(ledger);
        ledger.claimFall(1, LEAD, 0.002);
        assertEquals(0.002 * info.surfaceArea(), ledger.fromAir(LEAD), 1e-12, "Looking again takes nothing more");
        ledger.claimFall(1, LEAD, 0.003);
        assertEquals(0.003 * info.surfaceArea(), ledger.fromAir(LEAD), 1e-12, "and later only the difference");
        assertEquals(0.003, ledger.claimedFall(1, LEAD), 0.0);
        ledger.claimFall(1, LEAD, 0.001);
        assertEquals(0.003 * info.surfaceArea(), ledger.fromAir(LEAD), 1e-12, "A counter that is lower takes nothing");
        run(ledger, 24 * 30, null);
        assertBalanced(ledger);
        ledger.claimFall(99, LEAD, 1.0); // A reach that is not known takes nothing
        assertBalanced(ledger);
    }

    @Test
    void theFloorIsRelativeToTheVolumeOfTheReach()
    {
        final WaterLedger ledger = chain(1, 8.0, 0.1, EDGE_BLOCKS);
        final double litres = ledger.info(1).litres();
        final double floor = WaterConstants.floorPerLitre(LEAD) * litres;
        assertEquals(1e-12 * litres, floor, 1e-18);
        ledger.add(1, LEAD, floor * 0.5);
        ledger.step(1.0, null);
        assertEquals(0, ledger.activeCount(), "Half the floor is dust");
        assertEquals(floor * 0.5, ledger.dropped(LEAD) + ledger.toSea(LEAD), 1e-18);
        final WaterLedger kept = chain(1, 8.0, 0.1, EDGE_BLOCKS);
        kept.addToBed(1, LEAD, floor * 2.0);
        kept.step(1.0, null);
        assertEquals(1, kept.activeCount(), "Twice the floor on a bed is kept");
        assertBalanced(ledger);
        assertBalanced(kept);
    }

    @Test
    void aLakeSettlesMoreSlowlyThanARiver()
    {
        final WaterLedger lake = new WaterLedger(), river = new WaterLedger();
        lake.register(ReachInfo.lake(0, 0, 0.1));
        river.register(ReachInfo.river(1, 8.0, 1.0e6, C, 0.1, ReachInfo.SEA, 0, 0)); // So long that nothing flows out in the time
        lake.add(ReachInfo.lakeId(0, 0), LEAD, 1000.0);
        river.add(1, LEAD, 1000.0);
        run(lake, 24 * 10, null);
        run(river, 24 * 10, null);
        assertEquals(1000.0 * Math.exp(-0.7 * 0.03 * 10.0), lake.reach(ReachInfo.lakeId(0, 0)).water(LEAD), 0.5);
        assertEquals(1000.0 * Math.exp(-0.7 * 0.1 * 10.0), river.reach(1).water(LEAD), 0.5);
        assertTrue(lake.reach(ReachInfo.lakeId(0, 0)).water(LEAD) > river.reach(1).water(LEAD));
    }

    @Test
    void catchingUpAYearAfterACalendarJumpWithFiftyActiveReachesIsCheap()
    {
        // The waters follow a jump of more than a week in 168 steps that together span it: a year is 8760 hours in steps of 52 hours
        final Random random = new Random(11);
        final WaterLedger ledger = new WaterLedger();
        long id = 1;
        for (int river = 0; river < 5; river++)
        {
            for (int edge = 0; edge < 10; edge++, id++)
            {
                ledger.register(ReachInfo.river(id, 8.0 + edge * 1.5, 250 + random.nextInt(250), C, random.nextBoolean() ? 3.0 : 0.1, edge == 9 ? ReachInfo.SEA : id + 1, (int) id % 5, 0));
                ledger.add(id, LEAD, 100.0);
                ledger.add(id, ACID, 800.0);
                ledger.add(id, SEWAGE, 50.0);
                ledger.addToBed(id, LEAD, 30.0);
            }
        }
        assertEquals(50, ledger.activeCount());
        final WaterLedger.RainSource rain = info -> info.cellX() % 2 == 0 ? 0.9 : 0.0;
        final long start = System.nanoTime();
        for (int step = 0; step < 168; step++)
        {
            ledger.step(8760.0 / 168.0, rain);
        }
        final double millis = (System.nanoTime() - start) / 1e6;
        System.out.printf("Catch-up of a year (168 steps of 52 hours) with 50 active reaches: %.3f ms%n", millis);
        assertTrue(millis < 50.0, "A year of catch-up with 50 reaches takes under 50 ms, took " + millis);
        assertBalanced(ledger);
    }
    @Test
    void costOfAStepWithFiveHundredActiveEdges()
    {
        // 25 rivers of 20 edges each, all of them holding lead, acid and sewage, with a bed under each
        final Random random = new Random(3);
        final WaterLedger ledger = new WaterLedger();
        long id = 1;
        for (int river = 0; river < 25; river++)
        {
            final long first = id;
            for (int edge = 0; edge < 20; edge++, id++)
            {
                ledger.register(ReachInfo.river(id, 8.0 + edge % 9 * 2.0, 250 + random.nextInt(250), C, random.nextBoolean() ? 3.0 : 0.1, edge == 19 ? ReachInfo.SEA : id + 1, (int) id % 7, 0));
            }
            for (long at = first; at < id; at++)
            {
                ledger.add(at, LEAD, 100.0);
                ledger.add(at, ACID, 500.0);
                ledger.add(at, SEWAGE, 50.0);
            }
        }
        assertEquals(500, ledger.activeCount());
        final int[] calls = new int[1];
        final WaterLedger.RainSource rain = info ->
        {
            calls[0]++;
            return info.cellX() % 2 == 0 ? 0.9 : 0.0;
        };
        for (int warm = 0; warm < 50; warm++)
        {
            ledger.step(1.0, rain);
            for (long at = 1; at <= 500; at += 3)
            {
                ledger.add(at, LEAD, 10.0); // Keeps all 500 active
            }
        }
        final int rounds = 500;
        long total = 0;
        long worst = 0;
        for (int round = 0; round < rounds; round++)
        {
            for (long at = 1; at <= 500; at += 3)
            {
                ledger.add(at, LEAD, 10.0);
            }
            final long start = System.nanoTime();
            ledger.step(1.0, rain);
            final long took = System.nanoTime() - start;
            total += took;
            worst = Math.max(worst, took);
        }
        final double mean = total / (double) rounds / 1e6;
        System.out.printf("Step of the waters with %d active edges: mean %.4f ms, worst %.4f ms%n", ledger.activeCount(), mean, worst / 1e6);
        assertTrue(mean < 5.0, "A step of 500 edges is cheap: " + mean + " ms");
        assertEquals(500, ledger.activeCount());
        assertBalanced(ledger);
    }
}
