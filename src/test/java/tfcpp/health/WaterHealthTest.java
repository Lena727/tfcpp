package tfcpp.health;

import org.junit.jupiter.api.Test;

import tfcpp.ecology.FoodMetals;
import tfcpp.ecology.Pollutant;

import static org.junit.jupiter.api.Assertions.*;

/** The dose-response, the quality that water carries, the sand filter and boiling. */
class WaterHealthTest
{
    @Test
    void theMedianDoseInfectsHalfOfThoseWhoSwallowIt()
    {
        for (Disease disease : Disease.VALUES)
        {
            assertEquals(0.5, Organisms.infectionProbability(disease, Organisms.n50(disease)), 1e-12, disease + " at its N50");
            assertEquals(0.0, Organisms.infectionProbability(disease, 0.0), 0.0);
            assertTrue(Organisms.infectionProbability(disease, 10) < Organisms.infectionProbability(disease, 1000), "more organisms, more risk");
            assertTrue(Organisms.infectionProbability(disease, 1e12) > Organisms.infectionProbability(disease, 1e9), "and still more risk for a still larger dose");
        }
        // The curves are flat in the tail (alpha well under 1): a dose of 1e12 organisms is 0.993 for Shigella and only 0.872 for S. Typhi, worked by hand:
        // Shigella beta = 1120 / (2^(1/0.21) - 1) = 42.9, P = 1 - (1 + 1e12/42.9)^-0.21 = 1 - exp(-0.21 * ln 2.33e10) = 1 - exp(-5.01) = 0.9933
        // S. Typhi beta = 3.6e6 / (2^(1/0.1086) - 1) = 6098, P = 1 - exp(-0.1086 * ln 1.64e8) = 1 - exp(-2.055) = 0.872
        assertEquals(0.9933, Organisms.infectionProbability(Disease.DYSENTERY, 1e12), 2e-4);
        assertEquals(0.872, Organisms.infectionProbability(Disease.TYPHOID, 1e12), 1e-3);
        // The parameters themselves, and two points of the curve worked by hand: 750 organisms infect 45.8 % of those who swallow them with Shigella, and 1e5 organisms 26.7 % with S. Typhi (Hornick's volunteers, whom the fit follows: about a quarter at that dose)
        assertEquals(0.21, Organisms.alpha(Disease.DYSENTERY));
        assertEquals(1120.0, Organisms.n50(Disease.DYSENTERY));
        assertEquals(0.1086, Organisms.alpha(Disease.TYPHOID));
        assertEquals(3.6e6, Organisms.n50(Disease.TYPHOID));
        assertEquals(42.9, Organisms.beta(Disease.DYSENTERY), 0.1);
        assertEquals(6098.0, Organisms.beta(Disease.TYPHOID), 10.0);
        assertEquals(0.458, Organisms.infectionProbability(Disease.DYSENTERY, 750.0), 1e-3);
        assertEquals(0.267, Organisms.infectionProbability(Disease.TYPHOID, 1e5), 1e-3);
        // Low doses: the risk is alpha * dose / beta, as the approximation says
        final double risk = Organisms.infectionProbability(Disease.DYSENTERY, 1e-3);
        assertEquals(Organisms.alpha(Disease.DYSENTERY) * 1e-3 / Organisms.beta(Disease.DYSENTERY), risk, risk * 1e-3);
        // Typhoid needs far more organisms than shigellosis for the same chance at a low dose
        assertTrue(Organisms.infectionProbability(Disease.TYPHOID, 10) < Organisms.infectionProbability(Disease.DYSENTERY, 10) / 10.0);
    }

    @Test
    void organismsInALitreAreTheEndemicOnesPlusTheShedders()
    {
        assertEquals(100.0, Organisms.perLitre(Disease.DYSENTERY, 1.0, 0.0), 1e-9);
        assertEquals(10.0, Organisms.perLitre(Disease.TYPHOID, 1.0, 0.0), 1e-9);
        assertEquals(1e6, Organisms.perLitre(Disease.DYSENTERY, 1.0, 1.0), 1e-6);
        assertEquals(100.0 * 0.5 + 1e6 * 0.5, Organisms.perLitre(Disease.DYSENTERY, 1.0, 0.5), 1e-6);
        assertEquals(0.0, Organisms.perLitre(Disease.DYSENTERY, 0.0, 0.0));
        assertEquals(1e5 * 1e-6, Organisms.perLitre(Disease.TYPHOID, 1e-6, 1e-6), 1e-9);
        assertEquals(Organisms.perLitre(Disease.TYPHOID, 1e-6, 1e-6), Organisms.perLitre(Disease.TYPHOID, 1e-6, 5e-6), 1e-12, "A shedders' share that is larger than the sewage is clamped to it");
    }

    @Test
    void waterQualityMixesByVolumeAndKeepsWhatBoilingCannotRemove()
    {
        final double[] metals = new double[FoodMetals.METALS.length];
        metals[FoodMetals.indexOf(Pollutant.LEAD)] = 40.0;
        final WaterQuality dirty = new WaterQuality(1e-3, 2e-4, 0.0, metals, 0.5);
        final WaterQuality mixed = WaterQuality.mix(dirty, 1.0, WaterQuality.CLEAN, 3.0);
        assertEquals(2.5e-4, mixed.faecal, 1e-15);
        assertEquals(5e-5, mixed.shedDysentery, 1e-15);
        assertEquals(10.0, mixed.metal(FoodMetals.indexOf(Pollutant.LEAD)), 1e-12);
        assertEquals(0.125, mixed.acidity, 1e-12);
        assertEquals(1e-3, WaterQuality.mix(dirty, 5.0, null, 0.0).faecal, 1e-15, "Nothing added, nothing changes");
        assertEquals(1e-3, WaterQuality.mix(dirty, 2.0, dirty, 3.0).faecal, 1e-15, "The same water mixed with itself");

        final WaterQuality boiled = dirty.boiled();
        assertFalse(boiled.hasPathogens(), "Boiling kills what lives in the sewage");
        assertEquals(40.0, boiled.metal(FoodMetals.indexOf(Pollutant.LEAD)), 1e-12, "and leaves the lead");
        assertEquals(0.5, boiled.acidity, 1e-12, "and the acid");
        assertFalse(boiled.isClean());
        assertTrue(WaterQuality.CLEAN.isClean());
        assertTrue(WaterQuality.CLEAN.boiled().isClean());
    }

    @Test
    void waterTakenFromTheWorldIsRoundedToAStepSoThatOnePlaceIsOneFluid()
    {
        final double[] metals = new double[FoodMetals.METALS.length];
        metals[FoodMetals.indexOf(Pollutant.ARSENIC)] = 12.0;
        final WaterQuality water = new WaterQuality(3.3e-5, 1.1e-7, 0.0, metals, 0.8);
        final WaterQuality taken = water.quantised();
        final double step = Math.pow(2.0, 1.0 / 64.0) * 1.0001; // Half a step of 2^(1/32) either way
        assertTrue(taken.faecal / water.faecal < step && water.faecal / taken.faecal < step, "Within half a step: " + taken.faecal);
        final double arsenic = taken.metal(FoodMetals.indexOf(Pollutant.ARSENIC));
        assertTrue(arsenic / 12.0 < step && 12.0 / arsenic < step);
        assertEquals(0.0, taken.shedTyphoid);
        // Water a fraction of a per cent off is the same water at the source: one fluid
        final WaterQuality near = new WaterQuality(3.3e-5 * 1.002, 1.1e-7 * 0.998, 0.0, metals, 0.8 * 1.001);
        assertEquals(taken.encode(), near.quantised().encode());
        assertEquals(taken.encode(), taken.quantised().encode(), "Rounding twice is rounding once");
        assertNull(WaterQuality.CLEAN.quantised().encode(), "Clean water carries no component and stacks with plain water");
        assertNull(new WaterQuality(1e-12, 0, 0, new double[FoodMetals.METALS.length], 1e-6).encode(), "Under the floors is clean");
    }

    @Test
    void aMixtureKeepsItsExactConcentrationsAndTheComponentStoresThemExactly()
    {
        final double[] metals = new double[FoodMetals.METALS.length];
        final int lead = FoodMetals.indexOf(Pollutant.LEAD);
        metals[lead] = 100.0;
        final WaterQuality foul = new WaterQuality(1e-2, 1e-3, 2e-4, metals, 0.0);
        // 100 pours of 10 L of clean water into 1000 L of foul: every one of them is far less than half a step of the concentration
        WaterQuality held = foul;
        double litres = 1000.0;
        for (int pour = 0; pour < 100; pour++)
        {
            held = WaterQuality.mix(held, litres, WaterQuality.CLEAN, 10.0);
            litres += 10.0;
            held = WaterQuality.decode(held.encode()); // as the tank stores it between two pours
        }
        assertEquals(2000.0, litres);
        assertEquals(1e-2 * 1000.0, held.faecal * litres, 1e-9 * 10.0, "the sewage is conserved");
        assertEquals(1e-3 * 1000.0, held.shedDysentery * litres, 1e-9 * 1.0);
        assertEquals(2e-4 * 1000.0, held.shedTyphoid * litres, 1e-9 * 0.2);
        assertEquals(100.0 * 1000.0, held.metal(lead) * litres, 1e-9 * 1e5, "and so is a metal");
        assertEquals(1e-2 / 2.0, held.faecal, 1e-9 * 1e-2, "the concentration halves when the volume doubles");
        assertEquals(foul.faecal, WaterQuality.decode(foul.encode()).faecal, 0.0, "The component keeps the exact value");
    }

    @Test
    void aRipeFilterTakesOutNinetyNineInAHundredAndANewOneHalf()
    {
        final double[] metals = new double[FoodMetals.METALS.length];
        metals[FoodMetals.indexOf(Pollutant.LEAD)] = 100.0;
        metals[FoodMetals.indexOf(Pollutant.ARSENIC)] = 100.0;
        final WaterQuality in = new WaterQuality(1e-3, 1e-4, 1e-5, metals, 2.0);
        final WaterQuality ripe = Filtration.filter(in, Filtration.RIPE_REMOVAL), young = Filtration.filter(in, Filtration.UNRIPE_REMOVAL);
        assertEquals(1e-5, ripe.faecal, 1e-18);
        assertEquals(1e-6, ripe.shedDysentery, 1e-19);
        assertEquals(1e-7, ripe.shedTyphoid, 1e-20);
        assertEquals(5e-4, young.faecal, 1e-18, "A filter that has not ripened takes out half");
        assertEquals(50.0, ripe.metal(FoodMetals.indexOf(Pollutant.LEAD)), 1e-9, "Half of the lead, which travels on particles");
        assertEquals(80.0, ripe.metal(FoodMetals.indexOf(Pollutant.ARSENIC)), 1e-9, "a fifth of the arsenic, which is mostly dissolved");
        assertEquals(2.0, ripe.acidity, 1e-12, "and none of the acid");
        assertTrue(young.metal(FoodMetals.indexOf(Pollutant.LEAD)) > ripe.metal(FoodMetals.indexOf(Pollutant.LEAD)));
        // 99 % is two logs: the typhoid organisms of the same water fall by a hundred
        assertEquals(Organisms.perLitre(Disease.TYPHOID, in.faecal, in.shedTyphoid) * 0.01, Organisms.perLitre(Disease.TYPHOID, ripe.faecal, ripe.shedTyphoid), 1e-9);
    }

    @Test
    void waterGoesThroughAtTwoHundredLitresAnHourAndTheFilterRipensAfterThreeDaysOfRunning()
    {
        // 200 L an hour, 4800 L a day
        final Filtration.Throughput hour = Filtration.throughput(10.0, 1.0, 5000.0, 1000.0);
        assertEquals(200.0, hour.total(), 1e-9);
        assertEquals(0.0, hour.unripeLitres(), 1e-9, "A ripe filter has no unripe water");
        // It cannot pass what is not there, or what has no room
        assertEquals(50.0, Filtration.throughput(10.0, 1.0, 50.0, 1000.0).total(), 1e-9);
        assertEquals(30.0, Filtration.throughput(10.0, 1.0, 5000.0, 30.0).total(), 1e-9);
        // A new filter: the first 3 days of running (14 400 L) are unripe water
        final Filtration.Throughput first = Filtration.throughput(0.0, 100.0, 1e9, 1e9);
        assertEquals(14_400.0, first.unripeLitres(), 1e-6);
        assertEquals(200.0 * 100.0 - 14_400.0, first.ripeLitres(), 1e-6);
        assertEquals(3.0, Filtration.daysRun(0.0, 14_400.0), 1e-12);
        final Filtration.Throughput almost = Filtration.throughput(2.5, 100.0, 1e9, 1e9);
        assertEquals(0.5 * 24.0 * 200.0, almost.unripeLitres(), 1e-6, "Half a day to go");
    }

    @Test
    void aMinuteOfBoilingIsWhatTheWhoAsks()
    {
        assertEquals(60.0, Filtration.BOIL_SECONDS);
        assertEquals(100.0, Filtration.BOILING_CELSIUS);
    }
}
