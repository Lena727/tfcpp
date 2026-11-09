package tfcpp.ecology;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoilCellTest
{
    private static final double EPS = 1e-9;
    private static final double AREA = SoilConstants.AREA;
    private static final long DAY = SoilConstants.CALENDAR_TICKS_PER_DAY;

    private static SoilCell cell(SoilOrder order, boolean carbonate)
    {
        final SoilCell cell = SoilCell.fresh(order, carbonate, 0L);
        cell.setDrainage(Drainage.NONE); // These tests are about the soil alone: a chunk that drains nowhere
        return cell;
    }

    // --- Where a soil starts ---------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void everySoilStartsAtItsOwnPh()
    {
        for (SoilOrder order : SoilOrder.values())
        {
            assertEquals(order.startPh(), cell(order, false).pH(), EPS, order.name());
        }
    }

    @Test
    void onlyAnAridisolAndCarbonateBedrockHaveFreeCarbonate()
    {
        for (SoilOrder order : SoilOrder.values())
        {
            final SoilCell soil = cell(order, false), rock = cell(order, true);
            assertEquals(order == SoilOrder.ARIDISOL ? SoilConstants.CARBONATE_FULL : 0.0, soil.carbonatePerM2(), EPS, order.name());
            assertEquals(SoilConstants.CARBONATE_FULL, rock.carbonatePerM2(), EPS, order + " over carbonate rock");
            assertEquals(SoilConstants.PH_CARBONATE_PLATEAU, rock.pH(), EPS);
            assertEquals(SoilCell.Range.CARBONATE, rock.range());
            assertEquals(1.0, rock.baseSaturation(), EPS, "with the exchanger full of calcium");
        }
    }

    @Test
    void theExchangeCapacitiesAreTheOnesOfEachOrder()
    {
        final double[] cmol = {8, 5, 15, 25, 10, 15, 25, 10}; // Podzol, oxisol, alfisol, andisol, entisol, fluvisol, mollisol, aridisol
        for (SoilOrder order : SoilOrder.values())
        {
            assertEquals(cmol[order.ordinal()] * 6.5, cell(order, false).cecPerM2(), EPS, order.name());
        }
    }

    @Test
    void theStartingSaturationFollowsFromTheStartingPh()
    {
        assertEquals((4.8 - 4.2) / 2.8, cell(SoilOrder.PODZOL, false).baseSaturation(), EPS);
        assertEquals((6.0 - 4.2) / 2.8, cell(SoilOrder.ALFISOL, false).baseSaturation(), EPS);
        assertEquals(1.0, cell(SoilOrder.MOLLISOL, false).baseSaturation(), EPS, "pH 7.0 is a full exchanger");
        assertEquals(1.0, cell(SoilOrder.ARIDISOL, false).baseSaturation(), EPS, "and an Aridisol is capped at 1");
        assertEquals(SoilCell.Range.EXCHANGER, cell(SoilOrder.PODZOL, false).range());
        assertEquals(SoilCell.Range.CARBONATE, cell(SoilOrder.ARIDISOL, false).range());
    }

    @Test
    void theStartingBuffersTakeTensOfEquivalentsToDropOnePhUnit()
    {
        for (SoilOrder order : SoilOrder.values())
        {
            final SoilCell soil = cell(order, false);
            assertEquals(order.cecPerM2() / 2.8, soil.acidPerPhUnit(), EPS, order.name());
            assertTrue(soil.acidPerPhUnit() > 10.0 && soil.acidPerPhUnit() < 60.0, order + " " + soil.acidPerPhUnit());
        }
        // An Alfisol: 97.5 eq/m2 over 2.8 units
        final SoilCell alfisol = cell(SoilOrder.ALFISOL, false);
        assertEquals(34.8, alfisol.acidPerPhUnit(), 0.05);
        alfisol.deposit(Pollutant.ACIDITY, alfisol.acidPerPhUnit() * AREA);
        assertEquals(5.0, alfisol.pH(), 1e-9, "That much acid takes it from 6.0 to 5.0");
    }

    // --- Acid ----------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void carbonateHoldsThePhAtItsPlateauAndNeutralisesAcidForGood()
    {
        final SoilCell cell = cell(SoilOrder.MOLLISOL, true);
        cell.deposit(Pollutant.ACIDITY, 1000.0);
        assertEquals(SoilConstants.PH_CARBONATE_PLATEAU, cell.pH(), EPS, "pH does not move while carbonate is left");
        assertEquals(0.0, cell.total(Pollutant.ACIDITY), EPS);
        assertEquals(1000.0, cell.totalNeutralised(Pollutant.ACIDITY), EPS);
        assertEquals(SoilConstants.CARBONATE_FULL - 1000.0 / AREA, cell.carbonatePerM2(), EPS);
    }

    @Test
    void whenTheCarbonateIsGoneTheSoilEntersTheExchanger()
    {
        final SoilCell cell = cell(SoilOrder.ALFISOL, true);
        cell.deposit(Pollutant.ACIDITY, SoilConstants.CARBONATE_FULL * AREA + 0.5 * cell.acidPerPhUnit() * AREA);
        assertEquals(0.0, cell.carbonatePerM2(), 1e-9);
        assertEquals(SoilCell.Range.EXCHANGER, cell.range());
        assertEquals(7.0 - 0.5, cell.pH(), 1e-9, "Half a pH unit down from a full exchanger");
    }

    @Test
    void theRangesMeetWithoutAJumpInPh()
    {
        final SoilCell cell = cell(SoilOrder.ALFISOL, false);
        cell.deposit(Pollutant.ACIDITY, cell.baseSaturation() * cell.cecPerM2() * AREA);
        assertEquals(SoilConstants.PH_EXCHANGER_BOTTOM, cell.pH(), 1e-9, "Base saturation 0 is pH 4.2, where the aluminium range starts");
        cell.deposit(Pollutant.ACIDITY, SoilConstants.ALUMINIUM_RANGE * AREA);
        assertEquals(SoilConstants.PH_ALUMINIUM_BOTTOM, cell.pH(), 1e-9);
        cell.deposit(Pollutant.ACIDITY, 1e6);
        assertEquals(SoilConstants.PH_ALUMINIUM_BOTTOM, cell.pH(), 1e-9, "Past the last range the pH stays at its floor");
        assertTrue(cell.total(Pollutant.ACIDITY) > 1e6, "and the acid goes on collecting");
    }

    @Test
    void thePhNeverRisesWhileAcidIsAdded()
    {
        for (SoilOrder order : SoilOrder.values())
        {
            final SoilCell cell = cell(order, true);
            double last = cell.pH();
            for (int i = 0; i < 5000; i++)
            {
                cell.deposit(Pollutant.ACIDITY, 40.0 + i);
                assertTrue(cell.pH() <= last + EPS, order + " pH rose from " + last + " to " + cell.pH());
                last = cell.pH();
            }
            assertTrue(cell.pH() >= SoilConstants.PH_ALUMINIUM_BOTTOM - EPS);
        }
    }

    @Test
    void theSameSpillSoursAPodzolOverGraniteAndLeavesAMollisolOverLimestoneAlone()
    {
        final double spill = 5000.0; // About 140 L of oil of vitriol on one chunk, 19.5 eq/m2
        final SoilCell podzol = cell(SoilOrder.PODZOL, false), limestone = cell(SoilOrder.MOLLISOL, true);
        final double podzolBefore = podzol.pH();
        final double exchangerAcid = podzol.baseSaturation() * podzol.cecPerM2();
        podzol.deposit(Pollutant.ACIDITY, spill);
        limestone.deposit(Pollutant.ACIDITY, spill);
        assertEquals(4.8, podzolBefore, EPS);
        assertEquals(4.2 - 1.2 * (spill / AREA - exchangerAcid) / 500.0, podzol.pH(), 1e-9, "The 11 eq/m2 of its exchanger go, and the rest is aluminium");
        assertTrue(podzolBefore - podzol.pH() > 0.5);
        assertEquals(8.0, limestone.pH(), EPS, "On limestone the pH does not move at all");
        assertEquals(0.0, limestone.total(Pollutant.ACIDITY), EPS);
        assertTrue(podzol.total(Pollutant.ACIDITY) > 0.0);
    }

    // --- Weathering ----------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void weatheringGivesBaseCationsBackAtItsOwnRateAndNoMore()
    {
        final SoilCell cell = cell(SoilOrder.PODZOL, false);
        cell.deposit(Pollutant.ACIDITY, 0.5 * AREA);
        final double before = cell.baseSaturation();
        cell.advance(SoilConstants.DAYS_PER_YEAR);
        assertEquals(SoilConstants.WEATHERING_PER_YEAR, (cell.baseSaturation() - before) * cell.cecPerM2(), EPS, "0.01 eq/m2 in a year");
        assertEquals(SoilConstants.WEATHERING_PER_YEAR * AREA, cell.totalNeutralised(Pollutant.ACIDITY), EPS);
    }

    @Test
    void weatheringNeverTakesASoilAboveTheSaturationItStartedWith()
    {
        for (SoilOrder order : new SoilOrder[] {SoilOrder.PODZOL, SoilOrder.ALFISOL, SoilOrder.OXISOL, SoilOrder.MOLLISOL})
        {
            final SoilCell cell = cell(order, false);
            cell.deposit(Pollutant.ACIDITY, 0.3 * AREA);
            cell.advance(100.0 * SoilConstants.DAYS_PER_YEAR);
            assertEquals(order.startPh(), cell.pH(), 1e-9, order + " is healed back to where it started");
            cell.advance(1000.0 * SoilConstants.DAYS_PER_YEAR);
            assertEquals(order.startPh(), cell.pH(), 1e-9, order + " and no further");
            assertEquals(0.0, cell.total(Pollutant.ACIDITY), 1e-9);
        }
    }

    @Test
    void weatheringTakesTheAluminiumFirst()
    {
        final SoilCell cell = cell(SoilOrder.PODZOL, false);
        cell.deposit(Pollutant.ACIDITY, (cell.baseSaturation() * cell.cecPerM2() + 2.0) * AREA); // Down to saturation 0, 2 eq/m2 in aluminium
        assertEquals(SoilCell.Range.ALUMINIUM, cell.range());
        cell.advance(100.0 * SoilConstants.DAYS_PER_YEAR); // 1 eq/m2
        assertEquals(1.0, cell.aluminiumPerM2(), 1e-9);
        assertEquals(0.0, cell.baseSaturation(), 1e-9);
        cell.advance(100.0 * SoilConstants.DAYS_PER_YEAR);
        assertEquals(0.0, cell.aluminiumPerM2(), 1e-9);
        assertEquals(0.0, cell.baseSaturation(), 1e-9, "only what comes after that raises saturation");
    }

    // --- Decay ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void tarHasAHalfLifeOf180DaysAndSewageAT90OfTwentyOne()
    {
        final SoilCell cell = cell(SoilOrder.ENTISOL, false);
        cell.deposit(Pollutant.TAR, 100.0);
        cell.deposit(Pollutant.SEWAGE, 1000.0);
        cell.advance(180.0);
        assertEquals(50.0, cell.total(Pollutant.TAR), 1e-9);
        cell.advance(21.0); // To 201 days in all
        assertEquals(1000.0 * Math.pow(10.0, -201.0 / 21.0), cell.total(Pollutant.SEWAGE), 1e-9);
    }

    @Test
    void metalsAndAcidDoNotDecay()
    {
        final SoilCell cell = cell(SoilOrder.ENTISOL, false);
        final Pollutant[] metals = {Pollutant.LEAD, Pollutant.ARSENIC, Pollutant.MERCURY, Pollutant.ZINC, Pollutant.COPPER};
        for (Pollutant metal : metals)
        {
            cell.deposit(metal, 123.0);
        }
        cell.advance(100_000.0);
        for (Pollutant metal : metals)
        {
            assertEquals(123.0, cell.total(metal), EPS, metal.name());
            assertEquals(0.0, cell.totalDecayed(metal));
        }
    }

    // --- Catching up -------------------------------------------------------------------------------------------------------------------------------------------------------

    private static SoilCell loaded(SoilOrder order)
    {
        final SoilCell cell = cell(order, false);
        cell.deposit(Pollutant.ACIDITY, (cell.baseSaturation() * cell.cecPerM2() + 1.5) * AREA); // Into the aluminium range, so that weathering crosses a range
        cell.deposit(Pollutant.TAR, 40.0);
        cell.deposit(Pollutant.SEWAGE, 900.0);
        cell.deposit(Pollutant.LEAD, 77.0);
        return cell;
    }

    @Test
    void aStepOfManyDaysIsTheSameAsAsManyStepsOfOne()
    {
        for (int days : new int[] {1, 7, 21, 180, 400, 3650, 40_000})
        {
            final SoilCell once = loaded(SoilOrder.PODZOL), daily = loaded(SoilOrder.PODZOL);
            once.advance(days);
            for (int i = 0; i < days; i++)
            {
                daily.advance(1.0);
            }
            for (Pollutant pollutant : Pollutant.VALUES)
            {
                assertEquals(once.total(pollutant), daily.total(pollutant), 1e-7 * Math.max(1.0, Math.abs(once.total(pollutant))), pollutant + " after " + days + " days");
                assertEquals(once.totalDecayed(pollutant), daily.totalDecayed(pollutant), 1e-7 * Math.max(1.0, once.totalDecayed(pollutant)), pollutant + " decayed after " + days + " days");
            }
            assertEquals(once.pH(), daily.pH(), 1e-9, "pH after " + days + " days");
            assertEquals(once.totalNeutralised(Pollutant.ACIDITY), daily.totalNeutralised(Pollutant.ACIDITY), 1e-6, "neutralised after " + days + " days");
        }
    }

    @Test
    void catchingUpByTicksIsTheSameAsAdvancingByDays()
    {
        final SoilCell ticks = loaded(SoilOrder.OXISOL), days = loaded(SoilOrder.OXISOL);
        ticks.catchUp(37 * DAY);
        days.advance(37.0);
        assertEquals(37 * DAY, ticks.lastTick());
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            assertEquals(days.total(pollutant), ticks.total(pollutant), 1e-9, pollutant.name());
        }
    }

    @Test
    void aCalendarSetBackDoesNothingAndTheClockNeverGoesBack()
    {
        final SoilCell cell = loaded(SoilOrder.ENTISOL);
        cell.catchUp(10 * DAY);
        final double tar = cell.total(Pollutant.TAR);
        cell.catchUp(3 * DAY);
        assertEquals(tar, cell.total(Pollutant.TAR), 0.0, "Nothing happens when time runs backwards");
        assertEquals(10 * DAY, cell.lastTick(), "and the clock stays where it was");
    }

    @Test
    void aCalendarThatReadsAnEarlierDateAndThenTheRealOneDoesNotDecayTwice()
    {
        // The cell was saved on day 100. At a restart the spawn chunks load before TFC has read its saved calendar, which says day 0, and then the calendar reads day 150
        final SoilCell cell = SoilCell.fresh(SoilOrder.ENTISOL, false, 100 * DAY);
        cell.setDrainage(Drainage.NONE);
        cell.deposit(Pollutant.TAR, 100.0);
        cell.catchUp(0L);
        cell.catchUp(150 * DAY);
        assertEquals(100.0 * Math.pow(0.5, 50.0 / 180.0), cell.total(Pollutant.TAR), 1e-9, "Fifty days have passed and not two hundred");
        assertEquals(150 * DAY, cell.lastTick());

        final SoilCell control = SoilCell.fresh(SoilOrder.ENTISOL, false, 100 * DAY);
        control.setDrainage(Drainage.NONE);
        control.deposit(Pollutant.TAR, 100.0);
        control.catchUp(150 * DAY);
        assertEquals(control.total(Pollutant.TAR), cell.total(Pollutant.TAR), 0.0, "the same as a calendar that read the right date at once");
    }

    // --- Conservation ------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void everythingPutInIsPresentDecayedOrNeutralised()
    {
        for (boolean carbonate : new boolean[] {false, true})
        {
            for (SoilOrder order : SoilOrder.values())
            {
                final SoilCell cell = cell(order, carbonate);
                final java.util.Random random = new java.util.Random(order.ordinal() * 31L + (carbonate ? 1 : 0));
                for (int round = 0; round < 60; round++)
                {
                    for (Pollutant pollutant : Pollutant.VALUES)
                    {
                        if (random.nextInt(3) == 0)
                        {
                            cell.deposit(pollutant, random.nextDouble() * (pollutant == Pollutant.ACIDITY ? 8000.0 : 500.0));
                        }
                    }
                    cell.advance(random.nextDouble() * 400.0);
                    for (Pollutant pollutant : Pollutant.VALUES)
                    {
                        final double accounted = cell.total(pollutant) + cell.totalDecayed(pollutant) + cell.totalNeutralised(pollutant);
                        assertEquals(cell.totalInput(pollutant), accounted, 1e-7 * Math.max(1.0, cell.totalInput(pollutant)), order + " " + pollutant + " round " + round);
                    }
                }
            }
        }
    }

    @Test
    void nothingIsAddedByZeroOrNegativeAmounts()
    {
        final SoilCell cell = cell(SoilOrder.ENTISOL, false);
        cell.deposit(Pollutant.LEAD, 0.0);
        cell.deposit(Pollutant.LEAD, -5.0);
        cell.deposit(Pollutant.ACIDITY, Double.NaN);
        assertEquals(0.0, cell.total(Pollutant.LEAD));
        assertEquals(0.0, cell.totalInput(Pollutant.LEAD));
        assertEquals(SoilOrder.ENTISOL.startPh(), cell.pH(), EPS);
    }

    // --- Units ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void perSquareMetreIsTheTotalOver256()
    {
        final SoilCell cell = cell(SoilOrder.ENTISOL, false);
        cell.deposit(Pollutant.LEAD, 5120.0);
        assertEquals(20.0, cell.perM2(Pollutant.LEAD), EPS);
        assertEquals(256.0, SoilConstants.AREA);
    }

    // --- Saving --------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void aSavedCellComesBackTheSame()
    {
        final SoilCell cell = loaded(SoilOrder.PODZOL);
        cell.catchUp(500 * DAY);
        final SoilCell loaded = PollutionChunkData.load(PollutionChunkData.save(cell));
        assertEquals(cell.lastTick(), loaded.lastTick());
        assertEquals(cell.pH(), loaded.pH(), 0.0);
        assertEquals(cell.range(), loaded.range());
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            assertEquals(cell.total(pollutant), loaded.total(pollutant), 0.0, pollutant.name());
            assertEquals(cell.totalInput(pollutant), loaded.totalInput(pollutant), 0.0, pollutant.name());
            assertEquals(cell.totalDecayed(pollutant), loaded.totalDecayed(pollutant), 0.0, pollutant.name());
        }
        assertEquals(cell.totalNeutralised(Pollutant.ACIDITY), loaded.totalNeutralised(Pollutant.ACIDITY), 0.0);
        // The starting saturation is saved too: weathering of the loaded soil stops where the original's would
        cell.advance(100_000.0);
        loaded.advance(100_000.0);
        assertEquals(cell.pH(), loaded.pH(), 0.0);
    }

    @Test
    void aChunkWithNoCellSavesNothing()
    {
        final PollutionChunkData data = new PollutionChunkData();
        assertNull(PollutionChunkData.SERIALIZER.write(data, null));
        data.set(cell(SoilOrder.ENTISOL, false));
        final CompoundTag tag = PollutionChunkData.SERIALIZER.write(data, null);
        assertNotNull(tag);
    }

    @Test
    void aSoilTakesInTheFallFromTheAirOnceAndOnlyTheDifference()
    {
        final SoilCell cell = cell(SoilOrder.PODZOL, false);
        cell.absorb(Pollutant.ARSENIC, 0.5);
        assertEquals(0.5 * SoilConstants.AREA, cell.total(Pollutant.ARSENIC), 1e-9);
        cell.absorb(Pollutant.ARSENIC, 0.5);
        assertEquals(0.5 * SoilConstants.AREA, cell.total(Pollutant.ARSENIC), 1e-9, "The same counter again adds nothing");
        cell.absorb(Pollutant.ARSENIC, 0.75);
        assertEquals(0.75 * SoilConstants.AREA, cell.total(Pollutant.ARSENIC), 1e-9);
        cell.absorb(Pollutant.ARSENIC, 0.1);
        assertEquals(0.75 * SoilConstants.AREA, cell.total(Pollutant.ARSENIC), 1e-9, "A counter never runs back");
        final double before = cell.pH();
        cell.absorb(Pollutant.ACIDITY, 2.0);
        assertTrue(cell.pH() < before, "Acid from the air acidifies like any other");
        assertEquals(2.0 * SoilConstants.AREA, cell.totalInput(Pollutant.ACIDITY), 1e-9);
        final SoilCell saved = PollutionChunkData.load(PollutionChunkData.save(cell));
        assertEquals(0.75, saved.claimed(Pollutant.ARSENIC), 0.0, "What was taken in is saved with the soil");
        assertEquals(0.75, cell.copy().claimed(Pollutant.ARSENIC), 0.0);
    }

    @Test
    void aSoilThatWasNeverTouchedClaimsTheWholeHistoryWhenFirstTouched()
    {
        final SoilCell late = cell(SoilOrder.ENTISOL, false), early = cell(SoilOrder.ENTISOL, false);
        for (double counter : new double[] {0.1, 0.4, 0.9, 1.5})
        {
            early.absorb(Pollutant.LEAD, counter);
        }
        late.absorb(Pollutant.LEAD, 1.5);
        assertEquals(early.total(Pollutant.LEAD), late.total(Pollutant.LEAD), 1e-9, "The cumulative counter makes it exact");
    }
}
