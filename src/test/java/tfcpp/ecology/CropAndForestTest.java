package tfcpp.ecology;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CropAndForestTest
{
    private static final double EPS = 1e-9;
    private static final long DAY = SoilConstants.CALENDAR_TICKS_PER_DAY;
    /** A TFC year in ticks: 96 days. */
    private static final long YEAR = (long) (SoilConstants.daysPerYear() * DAY);
    private static final long TICKS_PER_HOUR = AirScale.TICKS_PER_HOUR;

    // --- Crops -------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void acidityCostsGrowthBetweenPh5_5And4_5AndKillsBelow()
    {
        assertEquals(1.0, CropEffects.phMultiplier(7.0), EPS);
        assertEquals(1.0, CropEffects.phMultiplier(5.5), EPS);
        assertEquals(0.6, CropEffects.phMultiplier(5.0), EPS);
        assertEquals(0.2, CropEffects.phMultiplier(4.5), EPS);
        assertEquals(0.2, CropEffects.phMultiplier(3.0), EPS);
        assertFalse(CropEffects.aluminiumKills(4.5, 6.5));
        assertTrue(CropEffects.aluminiumKills(4.49, 6.5));
    }

    @Test
    void acidityIsRelativeToTheSoilsOwnStartAndAluminiumKillsOnlyBelowIt()
    {
        assertEquals(1.0, CropEffects.phMultiplier(4.8, 4.8), EPS, "A podzol at its natural pH grows its natural crop");
        assertEquals(1.0, CropEffects.phMultiplier(7.0, 7.0), EPS);
        assertEquals(1.0 / CropEffects.phMultiplier(4.8), CropEffects.phMultiplier(5.5, 4.8), EPS, "Liming a natural podzol helps: over 1");
        assertTrue(CropEffects.phMultiplier(5.5, 4.8) > 1.0);
        assertEquals(CropEffects.phMultiplier(4.6) / CropEffects.phMultiplier(6.5), CropEffects.phMultiplier(4.6, 6.5), EPS);
        assertFalse(CropEffects.aluminiumKills(4.4, 4.4), "A natural acid soil does not kill at baseline");
        assertFalse(CropEffects.aluminiumKills(4.4, 4.2), "nor one that is above its start");
        assertTrue(CropEffects.aluminiumKills(4.4, 4.8), "but one that has fallen under both 4.5 and its start does");
        assertFalse(CropEffects.aluminiumKills(4.8, 6.5), "and 4.5 is the line");
    }

    @Test
    void metalsFallLinearlyFromTheThresholdToZeroAtFiveTimesIt()
    {
        for (Pollutant metal : new Pollutant[] {Pollutant.COPPER, Pollutant.ZINC, Pollutant.NICKEL, Pollutant.ARSENIC})
        {
            final double t = CropEffects.threshold(metal);
            assertEquals(1.0, CropEffects.metalMultiplier(metal, t), EPS, metal.id());
            assertEquals(0.5, CropEffects.metalMultiplier(metal, 3.0 * t), EPS, metal.id());
            assertEquals(0.0, CropEffects.metalMultiplier(metal, 5.0 * t), EPS, metal.id());
            assertEquals(0.0, CropEffects.metalMultiplier(metal, 9.0 * t), EPS, metal.id());
        }
        assertEquals(1.0, CropEffects.metalMultiplier(Pollutant.LEAD, 1e6), EPS, "Lead and mercury are not phytotoxic at these levels");
        assertEquals(1.0, CropEffects.metalMultiplier(Pollutant.MERCURY, 1e6), EPS);
        assertEquals(40.0, CropEffects.threshold(Pollutant.ARSENIC), EPS);
    }

    @Test
    void sulfurDioxideCostsGrowthFromThe30UgCriticalLevelAndInjuresAboveAThousandAnHour()
    {
        assertEquals(1.0, CropEffects.so2Multiplier(30.0), EPS);
        assertEquals(0.5, CropEffects.so2Multiplier(150.0), EPS);
        assertEquals(0.75, CropEffects.so2Multiplier(90.0), EPS);
        assertFalse(CropEffects.so2Acute(1000.0));
        assertTrue(CropEffects.so2Acute(1000.1));
    }

    @Test
    void multipliersCombineByProductAndTheVerdictComesFromAluminiumOrAcuteSo2()
    {
        final double[] metals = new double[Pollutant.COUNT];
        metals[Pollutant.COPPER.ordinal()] = 300.0; // 0.5
        final CropEffects.Result result = CropEffects.combine(5.0, 5.0, metals, 90.0, 0.0); // 1 (at its start) * 0.5 * 0.75
        assertEquals(1.0 * 0.5 * 0.75, result.growth(), EPS);
        assertFalse(result.unhealthy());
        assertTrue(CropEffects.combine(4.4, 6.5, new double[Pollutant.COUNT], 0.0, 0.0).unhealthy());
        assertTrue(CropEffects.combine(7.0, 7.0, new double[Pollutant.COUNT], 0.0, 2000.0).unhealthy());
        assertEquals(1.0, CropEffects.combine(7.0, 7.0, new double[Pollutant.COUNT], 0.0, 0.0).growth(), EPS);
    }

    @Test
    void oneGramPerSquareMetreIsOnePointFiveFourMilligramsPerKilogram()
    {
        assertEquals(1000.0 / 650.0, CropEffects.mgPerKg(SoilConstants.AREA), 1e-9);
        assertEquals(1.538, CropEffects.mgPerKg(SoilConstants.AREA), 1e-3);
    }

    // --- Forest ------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void closedFormEqualsSteppingUnderConstantStress()
    {
        final double s = 3.0;
        double stepped = 0.9;
        for (int i = 0; i < 1000; i++)
        {
            stepped = ForestHealth.advance(stepped, 0.01, s);
        }
        assertEquals(ForestHealth.advance(0.9, 10.0, s), stepped, 1e-12);
        assertEquals(ForestHealth.equilibrium(s), ForestHealth.advance(0.3, 1e6, s), 1e-12);
    }

    private static double yearsToFall(double annual, boolean conifer, double below)
    {
        double h = 1.0;
        final double s = ForestHealth.stress(conifer, annual, 6.0, 0.0, 0.0);
        for (double t = 0.0; t < 100.0; t += 0.01)
        {
            if (h < below)
            {
                return t;
            }
            h = ForestHealth.advance(h, 0.01, s);
        }
        return Double.POSITIVE_INFINITY;
    }

    @Test
    void aConiferStandAt100UgIsDeadInAboutFiveYearsAndBroadleavesLastLonger()
    {
        final double conifer = yearsToFall(100.0, true, 0.2);
        assertTrue(conifer > 4.0 && conifer < 6.0, "conifers at 100 ug/m3: " + conifer);
        final double broadleaf = yearsToFall(100.0, false, 0.2);
        assertTrue(broadleaf > conifer, "broadleaf at 100: " + broadleaf);
        assertTrue(yearsToFall(150.0, true, 0.2) < conifer);
        assertEquals(1.0, ForestHealth.advance(1.0, 50.0, ForestHealth.stress(true, 10.0, 6.0, 0.0, 0.0)), EPS, "At the critical level nothing happens");
        assertTrue(ForestHealth.stress(true, 20.0, 6.0, 0, 0) > 0 && ForestHealth.stress(false, 20.0, 6.0, 0, 0) == 0.0, "20 ug/m3 harms conifers and not yet broadleaves");
    }

    @Test
    void aDeadStandNeedsThirtyYearsOfCleanAirToReachNinetyPercent()
    {
        assertEquals(0.9, ForestHealth.advance(0.0, 30.0, 0.0), 1e-9);
        assertTrue(ForestHealth.advance(0.0, 5.0, 0.0) < 0.35, "Slow, over decades");
    }

    @Test
    void soilAcidityAndMetalsStressAStandToo()
    {
        assertEquals(0.0, ForestHealth.stress(false, 0.0, 4.2, 100.0, 300.0), EPS);
        assertEquals(4.0, ForestHealth.stress(false, 0.0, 3.0, 0.0, 0.0), 1e-9);
        assertEquals(1.0, ForestHealth.stress(false, 0.0, 6.0, 200.0, 0.0), 1e-9);
        assertEquals(2.0, ForestHealth.stress(false, 0.0, 6.0, 0.0, 900.0), 1e-9);
        assertFalse(ForestHealth.leafMayFall(0.7, 0.99), "A stand at 0.7 loses nothing");
        assertTrue(ForestHealth.leafMayFall(0.5, 0.6) && !ForestHealth.leafMayFall(0.5, 0.4), "Under it, the leaves with a hash over the health are the doomed");
        assertTrue(ForestHealth.saplingsGrow(0.5) && !ForestHealth.saplingsGrow(0.49));
    }

    private static final long YEAR_HOURS = (long) (SoilConstants.daysPerYear() * 24.0);

    /** A region cell that has had this concentration for this many TFC years. */
    private static AirGrid.Cell regionAt(double micrograms, int years)
    {
        final AirGrid.Cell region = new AirGrid.Cell(0, 0);
        for (long hour = 1; hour <= years * YEAR_HOURS; hour++)
        {
            region.recordHour(hour, micrograms);
        }
        return region;
    }

    @Test
    void theSoilCellTakesTheMeanExcessFromTwoReadingsOfTheRegionsIntegralsAndSavesIt()
    {
        final AirGrid.Cell region = regionAt(100.0, 5);
        final long hours = 5 * YEAR_HOURS, now = hours * TICKS_PER_HOUR;
        final SoilCell cell = SoilCell.fresh(SoilOrder.ENTISOL, false, 0L);
        cell.setDrainage(Drainage.NONE);
        cell.advanceForest(now, region.excessIntegral(true, hours), region.excessIntegral(false, hours));
        final double conifer = cell.forestHealth(true), broadleaf = cell.forestHealth(false);
        assertTrue(conifer < broadleaf && broadleaf < 1.0, conifer + " " + broadleaf);
        // Exactly the closed form under the stress of the mean excess of the five years
        final double meanConifer = region.excessIntegral(true, hours) / hours;
        assertEquals(ForestHealth.advance(1.0, 5.0, ForestHealth.so2Stress(true, meanConifer)), conifer, 1e-12);
        // A region that has been quiet gives no damage, and the same look again changes nothing
        final SoilCell quiet = SoilCell.fresh(SoilOrder.ENTISOL, false, 0L);
        quiet.advanceForest(now, 0.0, 0.0);
        assertEquals(1.0, quiet.forestHealth(true), EPS);
        final SoilCell loaded = PollutionChunkData.load(PollutionChunkData.save(cell));
        assertEquals(conifer, loaded.forestHealth(true), EPS);
        assertEquals(broadleaf, loaded.forestHealth(false), EPS);
        final SoilCell copy = cell.copy();
        assertEquals(conifer, copy.forestHealth(true), EPS);
        cell.advanceForest(now, region.excessIntegral(true, hours), region.excessIntegral(false, hours));
        assertEquals(conifer, cell.forestHealth(true), EPS);
    }

    @Test
    void anEpisodeInAnUnloadedChunkCountsFromTheIntegralsAndASoilMadeLateStartsAtTheFirstHourOfTheSmoke()
    {
        // The smoke is over for three years, then it stops for seven: the soil is first looked at ten years after it began
        final AirGrid.Cell region = regionAt(100.0, 3);
        final long smokeHours = 3 * YEAR_HOURS, lookHour = 10 * YEAR_HOURS;
        final double seenAtTheLook = region.excessIntegral(true, lookHour);
        assertTrue(seenAtTheLook > region.excessIntegral(true, smokeHours) * 0.99, "the decay of the mean adds to it a little");
        final SoilCell late = SoilCell.fresh(SoilOrder.ENTISOL, false, lookHour * TICKS_PER_HOUR);
        late.startForestAt(region.annualSince() * TICKS_PER_HOUR);
        late.advanceForest(lookHour * TICKS_PER_HOUR, seenAtTheLook, region.excessIntegral(false, lookHour));
        assertTrue(late.forestHealth(true) < 0.9, "The episode is in the health: " + late.forestHealth(true));
        // A linear ramp from nothing to the present value would have missed it, because the air is clean again: the mean is lower than the peak
        assertTrue(region.annualSo2(lookHour) < 40.0, "the mean has fallen back: " + region.annualSo2(lookHour));
        // A soil that exists already sees only what happened since its last look
        final SoilCell known = SoilCell.fresh(SoilOrder.ENTISOL, false, smokeHours * TICKS_PER_HOUR);
        known.markSeen(region.excessIntegral(true, smokeHours), region.excessIntegral(false, smokeHours));
        known.advanceForest(lookHour * TICKS_PER_HOUR, seenAtTheLook, region.excessIntegral(false, lookHour));
        assertTrue(known.forestHealth(true) > late.forestHealth(true), "and it saw less");
        // A region cell that was forgotten and made again starts from zero: the soil takes all of what it shows
        final SoilCell again = SoilCell.fresh(SoilOrder.ENTISOL, false, 0L);
        again.markSeen(1e9, 1e9);
        again.advanceForest(lookHour * TICKS_PER_HOUR, 1000.0, 0.0);
        assertTrue(again.forestHealth(true) <= 1.0);
    }

    @Test
    void metalsInTheSoilStressTheForestThroughTheCell()
    {
        final SoilCell cell = SoilCell.fresh(SoilOrder.ENTISOL, false, 0L);
        cell.deposit(Pollutant.ZINC, SoilConstants.AREA * 650.0 * 3000.0 / 1000.0); // 3000 mg/kg
        assertEquals(3000.0, cell.mgPerKg(Pollutant.ZINC), 1e-6);
        cell.advanceForest(5 * YEAR, 0.0, 0.0);
        assertTrue(cell.forestHealth(false) < 0.9);
    }

    // --- Annual mean -------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void theAnnualMeanApproachesAConstantConcentrationWithATimeConstantOfAYear()
    {
        final AirGrid.Cell cell = new AirGrid.Cell(0, 0);
        for (long hour = 1; hour <= YEAR_HOURS; hour++)
        {
            cell.recordHour(hour, 100.0);
        }
        assertEquals(100.0 * (1.0 - Math.exp(-1.0)), cell.annualSo2(YEAR_HOURS), 1e-6);
        // Held for hours with no air it falls by e a year, with no step taken
        assertEquals(cell.annualSo2(YEAR_HOURS) / Math.E, cell.annualSo2(YEAR_HOURS + YEAR_HOURS), 1e-9);
        // A gap is the same as hours of zero
        final AirGrid.Cell stepped = new AirGrid.Cell(0, 0), gapped = new AirGrid.Cell(0, 0);
        stepped.recordHour(1L, 50.0);
        gapped.recordHour(1L, 50.0);
        for (long hour = 2; hour <= 500; hour++)
        {
            stepped.recordHour(hour, 0.0);
        }
        assertEquals(stepped.annualSo2(500L), gapped.annualSo2(500L), 1e-9);
        gapped.recordHour(501L, 20.0);
        stepped.recordHour(501L, 20.0);
        assertEquals(stepped.annualSo2(501L), gapped.annualSo2(501L), 1e-9);
    }

    @Test
    void theGridKeepsTheAnnualMeanOfSulfurDioxide()
    {
        final AirGrid grid = new AirGrid();
        final AirWeather weather = new AirWeather.Fixed(0.0, 0.0, false);
        final double c = AirScale.metresPerBlock(20_000);
        double peak = 0.0;
        for (int hour = 0; hour < 24 * 60; hour++)
        {
            grid.emit(0, 0, AirSpecies.SULFUR_DIOXIDE, 200.0);
            peak = Math.max(peak, grid.cellAt(0, 0).microgramsPerCubicMetre(AirSpecies.SULFUR_DIOXIDE));
            grid.step(weather, hour * 1000L + 500L, c);
        }
        final double annual = grid.cellAt(0, 0).annualSo2(24 * 60);
        assertTrue(annual > 0.0 && annual < peak, annual + " of " + peak);
        assertTrue(grid.cellAt(0, 0).annualSince() != Long.MIN_VALUE);
        // After the smoke stops the cell is kept while its annual mean is above the floor
        for (int hour = 24 * 60; hour < 24 * 60 + 2000; hour++)
        {
            grid.step(weather, hour * 1000L + 500L, c);
        }
        assertNotNull(grid.cellAt(0, 0));
    }

    // --- Food ----------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void anItemTakesTheTransferFactorTimesTheSoilTimesItsDryMass()
    {
        // Lead: 0.01 x 1000 mg/kg x 0.04 kg = 0.4 mg
        assertEquals(0.4e-3, FoodMetals.gramsPerItem(Pollutant.LEAD, 1000.0), 1e-12);
        assertEquals(0.3 * 100.0 * 0.04e-3, FoodMetals.gramsPerItem(Pollutant.ZINC, 100.0), 1e-12);
        assertEquals(0.0, FoodMetals.gramsPerItem(Pollutant.TAR, 100.0), EPS);
        for (Pollutant metal : FoodMetals.METALS)
        {
            assertTrue(FoodMetals.transferFactor(metal) > 0.0, metal.id());
        }
    }

    @Test
    void theGutTakesUpItsShareOfEachMetal()
    {
        assertEquals(0.1, FoodMetals.absorbedMilligrams(Pollutant.LEAD, 1000.0), EPS);
        assertEquals(0.9, FoodMetals.absorbedMilligrams(Pollutant.ARSENIC, 1000.0), EPS);
        assertEquals(0.07, FoodMetals.absorbedMilligrams(Pollutant.MERCURY, 1000.0), EPS);
        assertEquals(0.05, FoodMetals.absorbedMilligrams(Pollutant.ANTIMONY, 1000.0), EPS);
        assertEquals(0.0, FoodMetals.absorbedMilligrams(Pollutant.ZINC, 1000.0), EPS);
        assertEquals(0.0, FoodMetals.absorbedMilligrams(Pollutant.COPPER, 1000.0), EPS);
    }

    @Test
    void quantisationLetsItemsOfOneFieldStackAndKeepsCleanFoodClean()
    {
        assertEquals(0, FoodMetals.quantise(0.0));
        assertEquals(0, FoodMetals.quantise(0.5));
        assertEquals(FoodMetals.quantise(400.0), FoodMetals.quantise(410.0), "A few per cent apart is one stack");
        assertNotEquals(FoodMetals.quantise(400.0), FoodMetals.quantise(4000.0), "A tenfold difference is not");
        for (double micrograms : new double[] {3.0, 400.0, 12345.0})
        {
            final double back = FoodMetals.dequantise(FoodMetals.quantise(micrograms));
            assertTrue(back / micrograms < 1.2 && back / micrograms > 1.0 / 1.2, micrograms + " -> " + back);
        }
    }

    @Test
    void recipesShareTheTotalAmongTheirOutputs()
    {
        final double[][] items = {{100.0, 0, 0, 0, 0, 0, 0}, {300.0, 0, 0, 0, 0, 0, 0}};
        final double[] out = FoodMetals.share(items, new int[] {1, 3}, 2); // 100 + 900 micrograms into two loaves
        assertEquals(500.0, out[0], EPS);
        assertEquals(1000.0, out[0] * 2, EPS, "Nothing is made or lost");
    }

    // --- Lime ----------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void aPieceOfEachPowderNeutralisesWhatItsChemistryGives()
    {
        assertEquals(1.0, Liming.LIME_EQ_PER_PIECE, 1e-3, "burnt limestone keeps its moles");
        assertEquals(Liming.FLUX_EQ_PER_PIECE, Liming.LIME_EQ_PER_PIECE, EPS);
        assertEquals(1.0, Liming.FLUX_EQ_PER_PIECE, 1e-3);
        assertEquals(0.5, Liming.WOOD_ASH_EQ_PER_PIECE, EPS);
        assertEquals(Liming.LIME_EQ_PER_PIECE * 10, Liming.equivalents(Liming.LIME_EQ_PER_PIECE, 10), EPS);
    }

    @Test
    void limeNeutralisesAluminiumThenTheExchangerThenRaisesBaseSaturationAndLeavesMetalsAlone()
    {
        final SoilCell cell = SoilCell.fresh(SoilOrder.PODZOL, false, 0L);
        cell.setDrainage(Drainage.NONE);
        cell.deposit(Pollutant.ACIDITY, 100.0 * SoilConstants.AREA); // Past the exchanger: 8 * 6.5 = 52 eq/m2 of capacity
        cell.deposit(Pollutant.LEAD, 1000.0);
        final double acidBefore = cell.total(Pollutant.ACIDITY);
        final double phBefore = cell.pH();
        assertTrue(phBefore < 4.2);
        final double neutralisedBefore = cell.totalNeutralised(Pollutant.ACIDITY);
        cell.lime(30.0 * SoilConstants.AREA);
        assertEquals(acidBefore - 30.0 * SoilConstants.AREA, cell.total(Pollutant.ACIDITY), 1e-6);
        assertEquals(neutralisedBefore + 30.0 * SoilConstants.AREA, cell.totalNeutralised(Pollutant.ACIDITY), 1e-6, "The books say lime neutralised it");
        assertTrue(cell.pH() > phBefore);
        assertEquals(1000.0, cell.total(Pollutant.LEAD), EPS, "Metals do not leave");
        // A great deal more than the acid fills the exchanger and leaves free carbonate
        cell.lime(1e6);
        assertTrue(cell.carbonatePerM2() > 0.0);
        assertEquals(SoilConstants.PH_CARBONATE_PLATEAU, cell.pH(), EPS);
        assertTrue(cell.total(Pollutant.ACIDITY) <= 1e-6);
    }

    @Test
    void whatAPlantTakesUpLeavesTheSoilAndIsCounted()
    {
        final SoilCell cell = SoilCell.fresh(SoilOrder.ENTISOL, false, 0L);
        cell.deposit(Pollutant.LEAD, 50.0);
        assertEquals(20.0, cell.uptake(Pollutant.LEAD, 20.0), EPS);
        assertEquals(30.0, cell.uptake(Pollutant.LEAD, 99.0), EPS, "No more than there is");
        assertEquals(0.0, cell.total(Pollutant.LEAD), EPS);
        assertEquals(50.0, cell.totalHarvested(Pollutant.LEAD), EPS);
        assertEquals(cell.totalInput(Pollutant.LEAD), cell.total(Pollutant.LEAD) + cell.totalHarvested(Pollutant.LEAD) + cell.totalDecayed(Pollutant.LEAD) + cell.totalWashed(Pollutant.LEAD), EPS);
        final CompoundTag tag = PollutionChunkData.save(cell);
        assertEquals(50.0, PollutionChunkData.load(tag).totalHarvested(Pollutant.LEAD), EPS);
    }

    // --- The integrals, the hash, the lime that leaches, the sweep ---------------------------------------------------------------------------------------------------

    @Test
    void theIntegralsOfTheExcessAreTheAreaUnderTheAnnualMeanAndTheLazyDecayIsClosedForm()
    {
        final AirGrid.Cell cell = new AirGrid.Cell(0, 0);
        final double tau = AirScale.hoursPerYear();
        double numeric = 0.0, numeric20 = 0.0, previous = 0.0;
        for (long hour = 1; hour <= 2 * YEAR_HOURS; hour++)
        {
            cell.recordHour(hour, 60.0);
            final double now = cell.annualSo2(hour);
            numeric += AirGrid.Cell.lineExcess(previous, now, 10.0);
            numeric20 += AirGrid.Cell.lineExcess(previous, now, 20.0);
            previous = now;
        }
        assertEquals(numeric, cell.excessIntegral(true, 2 * YEAR_HOURS), 1e-6 * numeric);
        // The decay of the mean after the smoke stops: closed form against hourly numerical integration
        final long from = 2 * YEAR_HOURS;
        final double a0 = cell.annualSo2(from);
        double sum = 0.0;
        for (int step = 0; step < 4000; step++)
        {
            final double a = a0 * Math.exp(-step / tau), b = a0 * Math.exp(-(step + 1) / tau);
            sum += AirGrid.Cell.lineExcess(a, b, 10.0);
        }
        assertEquals(cell.excessIntegral(true, from) + sum, cell.excessIntegral(true, from + 4000), 1e-3 * sum);
        assertEquals(numeric20, cell.excessIntegral(false, from), 1e-6 * numeric20, "and the same over the level of broadleaves");
        // An hour that is in is not counted again
        final double before = cell.excessIntegral(true, from), annual = cell.annualSo2(from);
        cell.recordHour(from, 1e6);
        assertEquals(before, cell.excessIntegral(true, from), EPS);
        assertEquals(annual, cell.annualSo2(from), EPS);
    }

    @Test
    void theHashIsEvenSoAShareOneMinusHOfTheCanopyIsDoomed()
    {
        int over = 0;
        final int n = 200_000;
        for (int i = 0; i < n; i++)
        {
            final double hash = ForestHealth.positionHash(i * 7919L + 13L);
            assertTrue(hash >= 0.0 && hash < 1.0);
            if (hash > 0.65)
            {
                over++;
            }
        }
        assertEquals(0.35, over / (double) n, 0.01);
        assertEquals(ForestHealth.positionHash(123456789L), ForestHealth.positionHash(123456789L), "The same leaf every time");
    }

    @Test
    void limeAboveTheStartLeachesWithAHalfLifeOfThreeTfcYearsAndTheBooksClose()
    {
        final SoilCell cell = SoilCell.fresh(SoilOrder.PODZOL, false, 0L);
        cell.setDrainage(Drainage.NONE);
        final double startPh = cell.startPh();
        assertEquals(4.8, startPh, 1e-9);
        cell.lime(10.0 * SoilConstants.AREA); // a podzol has no acid to neutralise: all of it raises the saturation
        assertTrue(cell.pH() > startPh);
        assertEquals(10.0, cell.limeAppliedPerM2(), EPS);
        final double raised = cell.limeSurplusPerM2();
        assertEquals(10.0, raised, 1e-9);
        cell.advance(3.0 * SoilConstants.daysPerYear());
        // Weathering does not matter above the start: the surplus halves in three years
        assertEquals(raised / 2.0, cell.limeSurplusPerM2(), 1e-6);
        assertEquals(cell.limeAppliedPerM2(), cell.limeSurplusPerM2() + cell.limeLeachedPerM2() + cell.limeNeutralisedPerM2(), 1e-9, "applied = still there + leached + neutralised");
        cell.advance(60.0 * SoilConstants.daysPerYear());
        assertEquals(startPh, cell.pH(), 1e-3, "and in time the soil is where it began, not above it: lime must be renewed");
        assertEquals(cell.limeAppliedPerM2(), cell.limeSurplusPerM2() + cell.limeLeachedPerM2() + cell.limeNeutralisedPerM2(), 1e-9);
        // A step of many days equals as many of one
        final SoilCell once = SoilCell.fresh(SoilOrder.PODZOL, false, 0L), daily = SoilCell.fresh(SoilOrder.PODZOL, false, 0L);
        once.setDrainage(Drainage.NONE);
        daily.setDrainage(Drainage.NONE);
        once.lime(10.0 * SoilConstants.AREA);
        daily.lime(10.0 * SoilConstants.AREA);
        once.advance(200.0);
        for (int day = 0; day < 200; day++)
        {
            daily.advance(1.0);
        }
        assertEquals(once.limeSurplusPerM2(), daily.limeSurplusPerM2(), 1e-9);
    }

    @Test
    void acidThatMeetsLimeAboveTheStartIsCountedAsLimesWork()
    {
        final SoilCell cell = SoilCell.fresh(SoilOrder.PODZOL, false, 0L);
        cell.setDrainage(Drainage.NONE);
        cell.lime(10.0 * SoilConstants.AREA);
        cell.deposit(Pollutant.ACIDITY, 4.0 * SoilConstants.AREA);
        assertEquals(0.0, cell.total(Pollutant.ACIDITY), 1e-9, "the lime took it");
        assertEquals(4.0 * SoilConstants.AREA, cell.totalNeutralised(Pollutant.ACIDITY), 1e-6);
        assertEquals(cell.totalInput(Pollutant.ACIDITY), cell.total(Pollutant.ACIDITY) + cell.totalNeutralised(Pollutant.ACIDITY) + cell.totalWashed(Pollutant.ACIDITY), 1e-6);
        assertEquals(10.0, cell.limeSurplusPerM2() + cell.limeNeutralisedPerM2(), 1e-9);
        final SoilCell loaded = PollutionChunkData.load(PollutionChunkData.save(cell));
        assertEquals(cell.limeSurplusPerM2(), loaded.limeSurplusPerM2(), EPS);
        assertEquals(cell.limeAppliedPerM2(), loaded.limeAppliedPerM2(), EPS);
    }

    @Test
    void theDailySweepForgetsCellsWhoseMeanHasFallenUnderTheFloorAndKeepsThoseThatHoldSomething()
    {
        final AirGrid grid = new AirGrid();
        final long hour = 1000;
        final AirGrid.Cell smoky = grid.cell(0, 0), clean = grid.cell(5, 5), faded = grid.cell(9, 9);
        for (long h = 1; h <= hour; h++)
        {
            smoky.recordHour(h, 400.0);
        }
        clean.recordHour(hour, 0.0); // never above zero: nothing is kept
        faded.recordHour(hour, 1.0);
        final long years10 = hour + YEAR_HOURS;
        assertEquals(2, grid.sweep(years10), "the clean cell and the faded one");
        assertNotNull(grid.cellAt(0, 0), "A cell with a mean over the floor stays: " + smoky.annualSo2(years10));
        assertNull(grid.cellAt(5, 5));
        assertNull(grid.cellAt(9, 9), "and the faded one is forgotten");
    }
}
