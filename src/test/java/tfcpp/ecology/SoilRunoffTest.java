package tfcpp.ecology;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoilRunoffTest
{
    private static final double AREA = SoilConstants.AREA;

    private static SoilCell polluted(Drainage drainage)
    {
        final SoilCell cell = SoilCell.fresh(SoilOrder.PODZOL, false, 0L);
        cell.setDrainage(drainage);
        cell.deposit(Pollutant.SEWAGE, 1000.0);
        cell.deposit(Pollutant.TAR, 100.0);
        cell.deposit(Pollutant.LEAD, 500.0);
        cell.deposit(Pollutant.ACIDITY, 34.7 * 140.0); // 140 L of vitriol: takes this podzol well into the aluminium range
        return cell;
    }

    @Test
    void aSoilThatDrainsToNothingLosesNothingToWater()
    {
        for (Drainage drainage : new Drainage[] {Drainage.NONE})
        {
            final SoilCell cell = polluted(drainage);
            cell.advance(365.0);
            assertNull(cell.takeOutbox(), drainage.name());
            for (Pollutant pollutant : Pollutant.VALUES)
            {
                assertEquals(0.0, cell.totalWashed(pollutant), 0.0);
            }
        }
    }

    @Test
    void whileItIsNotKnownWhereASoilDrainsItsRunoffIsHeldAndNotCountedAsLost()
    {
        final SoilCell cell = polluted(Drainage.UNKNOWN);
        cell.advance(365.0);
        assertTrue(cell.holdsOutbox());
        final double held = cell.totalWashed(Pollutant.LEAD);
        assertEquals(500.0 * (1.0 - Math.exp(-0.01)), held, 1e-9, "The runoff is worked out as for a draining chunk");
        assertEquals(0.0, cell.totalDecayed(Pollutant.LEAD), 0.0, "and is not counted as decayed");
        final PollutionChunkData data = new PollutionChunkData();
        data.set(cell);
        final SoilCell loaded = PollutionChunkData.load(PollutionChunkData.save(cell));
        assertTrue(loaded.holdsOutbox() && loaded.takeOutbox()[Pollutant.LEAD.ordinal()] == held, "It is saved with the cell");
        cell.returnOutbox(); // It turns out to drain nowhere
        assertFalse(cell.holdsOutbox());
        assertEquals(500.0, cell.total(Pollutant.LEAD), 1e-9, "Everything is back in the soil");
        assertEquals(0.0, cell.totalWashed(Pollutant.LEAD), 1e-12);
        assertEquals(cell.totalInput(Pollutant.ACIDITY), cell.total(Pollutant.ACIDITY) + cell.totalNeutralised(Pollutant.ACIDITY) + cell.totalWashed(Pollutant.ACIDITY), 1e-6, "also for the acid");
        final SoilCell coast = polluted(Drainage.SEA);
        coast.advance(365.0);
        assertNotNull(coast.takeOutbox(), "A coastal chunk hands its runoff on, to the sea");
    }

    @Test
    void aSoilThatDrainsToAriverLosesTheFiguresOfTheYearAndTheBooksClose()
    {
        final SoilCell cell = polluted(Drainage.RIVER);
        cell.advance(365.0);
        final double[] out = cell.takeOutbox();
        assertNotNull(out);
        // Metals: 1% a year, and they do not decay
        assertEquals(500.0 * (1.0 - Math.exp(-0.01)), out[Pollutant.LEAD.ordinal()], 1e-9);
        assertEquals(500.0 * Math.exp(-0.01), cell.total(Pollutant.LEAD), 1e-9);
        // Sewage breaks down (T90 21 days) and runs off (30% a year) together: the runoff takes its share of what leaves, which is small beside the breakdown
        final double sewageRate = Math.log(10.0) / 21.0 + 0.30 / 365.0;
        final double sewageLost = 1000.0 * -Math.expm1(-sewageRate * 365.0);
        assertEquals(sewageLost * (0.30 / 365.0) / sewageRate, out[Pollutant.SEWAGE.ordinal()], 1e-9);
        // Everything is somewhere: held + decayed + washed = put in
        for (Pollutant pollutant : new Pollutant[] {Pollutant.SEWAGE, Pollutant.TAR, Pollutant.LEAD})
        {
            assertEquals(cell.totalInput(pollutant), cell.total(pollutant) + cell.totalDecayed(pollutant) + cell.totalWashed(pollutant), 1e-9, pollutant.name());
            assertEquals(cell.totalWashed(pollutant), out[pollutant.ordinal()], 1e-9, "and what is washed was handed on");
        }
        // Acid: put in = held + neutralised + washed, with the acid that left coming from the aluminium range
        final double acid = Pollutant.ACIDITY.ordinal() >= 0 ? cell.totalInput(Pollutant.ACIDITY) : 0.0;
        assertEquals(acid, cell.total(Pollutant.ACIDITY) + cell.totalNeutralised(Pollutant.ACIDITY) + cell.totalWashed(Pollutant.ACIDITY), 1e-6);
        assertTrue(cell.totalWashed(Pollutant.ACIDITY) > 0.0, "The podzol was in the aluminium range, and gave acid to the stream");
        assertNull(cell.takeOutbox(), "The outbox is taken once");
    }

    @Test
    void acidThatIsNotHeldAsAluminiumDoesNotRunOff()
    {
        final SoilCell cell = SoilCell.fresh(SoilOrder.MOLLISOL, false, 0L);
        cell.setDrainage(Drainage.LAKE);
        cell.deposit(Pollutant.ACIDITY, 20.0 * AREA); // Well inside the exchanger: it stays on the exchange sites
        assertEquals(0.0, cell.aluminiumPerM2(), 0.0);
        cell.advance(3650.0);
        assertEquals(0.0, cell.totalWashed(Pollutant.ACIDITY), 0.0, "A soil above pH 4.2 gives no acid to streams");
    }

    @Test
    void theAluminiumAcidLosesTenPercentAYearAgainstWeathering()
    {
        final SoilCell cell = SoilCell.fresh(SoilOrder.PODZOL, false, 0L);
        cell.setDrainage(Drainage.RIVER);
        cell.deposit(Pollutant.ACIDITY, 100.0 * AREA); // 100 eq/m2: base cations all gone, the rest is aluminium
        final double before = cell.aluminiumPerM2();
        assertTrue(before > 50.0);
        cell.advance(365.0);
        final double w = SoilConstants.WEATHERING_PER_YEAR, r = WaterConstants.ALUMINIUM_ACID_RUNOFF_PER_YEAR;
        // da/dt = -r a - w, a year long
        final double expected = (before + w / r) * Math.exp(-r) - w / r;
        assertEquals(expected, cell.aluminiumPerM2(), 1e-9);
        assertEquals((before - expected - w) * AREA, cell.totalWashed(Pollutant.ACIDITY), 1e-6, "What left is what the aluminium lost, less what weathering took");
    }

    @Test
    void oneLongStepIsTheSameAsManyShortOnes()
    {
        final SoilCell once = polluted(Drainage.RIVER), often = polluted(Drainage.RIVER);
        once.advance(730.0);
        for (int day = 0; day < 730; day++)
        {
            often.advance(1.0);
        }
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            assertEquals(once.total(pollutant), often.total(pollutant), 1e-7 * Math.max(1.0, once.total(pollutant)), pollutant.name());
            assertEquals(once.totalWashed(pollutant), often.totalWashed(pollutant), 1e-7 * Math.max(1.0, once.totalWashed(pollutant)), pollutant.name() + " washed");
        }
        assertEquals(once.aluminiumPerM2(), often.aluminiumPerM2(), 1e-9);
    }

    @Test
    void theDrainageAndWhatWasWashedAreSavedAndCopied()
    {
        final SoilCell cell = polluted(Drainage.LAKE);
        cell.advance(100.0);
        final SoilCell loaded = PollutionChunkData.load(PollutionChunkData.save(cell));
        assertEquals(Drainage.LAKE, loaded.drainage());
        assertEquals(cell.totalWashed(Pollutant.LEAD), loaded.totalWashed(Pollutant.LEAD), 0.0);
        assertEquals(cell.totalWashed(Pollutant.ACIDITY), loaded.totalWashed(Pollutant.ACIDITY), 0.0);
        assertEquals(Drainage.LAKE, cell.copy().drainage());
        final CompoundTag old = PollutionChunkData.save(cell);
        old.remove("drainage");
        assertEquals(Drainage.UNKNOWN, PollutionChunkData.load(old).drainage(), "A cell saved without it does not know where it drains");
        cell.setDrainage(Drainage.UNKNOWN);
        assertEquals(Drainage.LAKE, cell.drainage(), "Once found, it stays found");
    }

    @Test
    void aSoilClaimsTheFallOnItsLandOnlyAndSoilAndWaterNeverClaimMoreThanFell()
    {
        final SoilCell full = SoilCell.fresh(SoilOrder.PODZOL, false, 0L), half = SoilCell.fresh(SoilOrder.PODZOL, false, 0L);
        full.setDrainage(Drainage.NONE);
        half.setDrainage(Drainage.NONE);
        half.setLandFraction(0.5);
        full.absorb(Pollutant.LEAD, 0.01);
        half.absorb(Pollutant.LEAD, 0.01);
        assertEquals(0.01 * AREA, full.totalInput(Pollutant.LEAD), 1e-12);
        assertEquals(0.5 * full.totalInput(Pollutant.LEAD), half.totalInput(Pollutant.LEAD), 1e-12, "A chunk half under water claims half the fall to its soil");
        assertEquals(0.5, PollutionChunkData.load(PollutionChunkData.save(half)).landFraction(), 0.0, "and keeps the figure");

        // A regional cell of 64 chunks: 32 of them land, 32 of them lake. The lake takes the rest of the cell
        final double cumulative = 0.004;
        double soil = 0.0;
        for (int chunk = 0; chunk < 64; chunk++)
        {
            final SoilCell cell = SoilCell.fresh(SoilOrder.PODZOL, false, 0L);
            cell.setLandFraction(chunk < 32 ? 1.0 : 0.0);
            cell.absorb(Pollutant.LEAD, cumulative);
            soil += cell.totalInput(Pollutant.LEAD);
        }
        final WaterLedger ledger = new WaterLedger();
        ledger.register(ReachInfo.lake(0, 0, 0.1));
        ledger.claimFall(ReachInfo.lakeId(0, 0), Pollutant.LEAD, cumulative, WaterConstants.LAKE_AREA - 32 * AREA);
        final double fallen = cumulative * WaterConstants.LAKE_AREA;
        assertEquals(fallen, soil + ledger.fromAir(Pollutant.LEAD), 1e-9, "Together they claim exactly what fell on a cell that is all land and lake");
        assertTrue(soil + ledger.fromAir(Pollutant.LEAD) <= fallen * (1.0 + 1e-12));
    }
}
