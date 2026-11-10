package tfcpp.health;

import org.junit.jupiter.api.Test;

import tfcpp.ecology.AirScale;
import tfcpp.ecology.Pollutant;
import tfcpp.ecology.ReachInfo;
import tfcpp.ecology.WaterLedger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The figures of the Broad Street mechanism: what a town does to the water below it, and what a drink of that water does to a person. The numbers of the design document come from here.
 */
class SanitationScenarioTest
{
    private static final double C = AirScale.metresPerBlock(20_000);

    private static WaterLedger river(int edges)
    {
        final WaterLedger ledger = new WaterLedger();
        for (int i = 1; i <= edges; i++)
        {
            ledger.register(ReachInfo.river(i, Math.min(24.0, 8.0 + 2.0 * (i - 1)), 370.0, C, 0.1, i == edges ? ReachInfo.SEA : i + 1, 0, 0));
        }
        return ledger;
    }

    private static WaterQuality qualityOf(WaterLedger ledger, long id)
    {
        final WaterLedger.Reach reach = ledger.reach(id);
        assertNotNull(reach, "The reach holds something");
        return new WaterQuality(reach.perLitre(Pollutant.SEWAGE), reach.perLitre(Pollutant.SHED_DYSENTERY), reach.perLitre(Pollutant.SHED_TYPHOID), new double[tfcpp.ecology.FoodMetals.METALS.length], 0.0);
    }

    /** A town of five whose waste all goes into the first reach (a privy over the stream, as towns had them), one of them shedding typhoid: the chance that one drink of half a litre, taken 1 km (three reaches' worth of mixing: the third reach) below, infects. */
    @Test
    void aTownOfFiveOnARiverInfectsAlmostNoOneBelowItButTheFiguresAreThere()
    {
        final WaterLedger ledger = river(4);
        for (int day = 0; day < 60; day++)
        {
            for (int hour = 0; hour < 24; hour++)
            {
                ledger.add(1, Pollutant.SEWAGE, 5 * SewageRates.PERSON_LITRES_PER_DAY / 24.0);
                ledger.add(1, Pollutant.SHED_TYPHOID, SewageRates.PERSON_LITRES_PER_DAY / 24.0);
                ledger.step(1.0, null);
            }
        }
        final WaterQuality at = qualityOf(ledger, 3);
        final double typhoid = Organisms.infectionProbability(Disease.TYPHOID, at, 0.5), dysentery = Organisms.infectionProbability(Disease.DYSENTERY, at, 0.5);
        System.out.printf("SCENARIO river: sewage %.3e L/L, typhoid shedders %.3e L/L in the third reach; one drink: typhoid %.3e, dysentery %.3e%n", at.faecal, at.shedTyphoid, typhoid, dysentery);
        // The river is the ledger's own (26 500 m3 a reach, T90 of 2 days); the figures are what it gives, pinned to 5 %
        assertEquals(3.55e-9, at.faecal, 3.55e-9 * 0.05, "sewage in the third reach");
        assertEquals(7.11e-10, at.shedTyphoid, 7.11e-10 * 0.05);
        assertEquals(6.33e-10, typhoid, 6.33e-10 * 0.05, "A river this size dilutes a town of five to nothing");
        assertEquals(8.7e-10, dysentery, 8.7e-10 * 0.05);

        // The same sewage in a pond: a lake of a 128 x 128 cell and 5 m of depth is 82 million litres, still nothing; a barrel is what it takes
        final double barrel = 1000.0; // litres
        final double fraction = 7.5 / barrel; // one day of the town's waste in a barrel of well water
        final double typhoidBarrel = Organisms.infectionProbability(Disease.TYPHOID, new WaterQuality(fraction, 0, 1.5 / barrel, new double[tfcpp.ecology.FoodMetals.METALS.length], 0.0), 0.5);
        System.out.printf("SCENARIO barrel: one day of a town's waste in 1000 L, one drink: typhoid %.3e%n", typhoidBarrel);
        // By hand: 7.5 L of sewage in 1000 L, 1.5 L of it from the carrier: 10 * 6e-3 + 1e5 * 1.5e-3 = 150.06 organisms a litre, 75.03 in a drink of half a litre;
        // beta = 3.6e6 / (2^(1/0.1086) - 1) = 6097, P = 1 - (1 + 75.03 / 6097)^-0.1086 = 1.327e-3
        assertEquals(1.327e-3, typhoidBarrel, 1.327e-3 * 0.05, "A barrel fouled by one day of a town with a typhoid carrier");

        // A well 15 blocks from a full pit that holds a shedder's waste (a tenth of it): half a litre
        final double well = Organisms.infectionProbability(Disease.TYPHOID, new WaterQuality(1000.0 * CesspitMath.groundwaterFactor(15.0), 0.0, 100.0 * CesspitMath.groundwaterFactor(15.0), new double[tfcpp.ecology.FoodMetals.METALS.length], 0.0), 0.5);
        System.out.printf("SCENARIO well: 15 blocks from a full pit with a tenth shedders' waste, one drink: typhoid %.3e%n", well);
        // By hand: 1000 * 1e-6 / (1 + 15/5) = 2.5e-4 L of sewage a litre, 2.5e-5 of it the carrier's: 10 * 2.25e-4 + 1e5 * 2.5e-5 = 2.502 organisms a litre, 1.251 in a drink; P = 2.23e-5
        assertEquals(2.228e-5, well, 2.228e-5 * 0.05);
    }

    @Test
    void theSheddersShareDiesAndFlowsAsASubsetOfTheSewage()
    {
        final WaterLedger ledger = river(3);
        ledger.add(1, Pollutant.SEWAGE, 100.0);
        ledger.add(1, Pollutant.SHED_TYPHOID, 40.0);
        for (int hour = 0; hour < 48; hour++)
        {
            ledger.step(1.0, null);
        }
        final double sewage = ledger.inWater(Pollutant.SEWAGE), shed = ledger.inWater(Pollutant.SHED_TYPHOID);
        assertEquals(0.4, shed / sewage, 1e-9, "The shedders' share keeps its proportion: both die and flow alike");
        assertTrue(sewage < 100.0 * 0.5, "and a good part of it has died in two days: T90 of 2 days in a river");
        assertEquals(0.0, ledger.imbalance(Pollutant.SHED_TYPHOID), 1e-9, "and the ledger of the shedders' share balances");
    }

    @Test
    void theSandFilterTurnsADangerousDrinkIntoAnHarmlessOneAndBoilingDoesToo()
    {
        final double barrel = 1000.0;
        final WaterQuality foul = new WaterQuality(7.5 / barrel, 1.5 / barrel, 0.0, new double[tfcpp.ecology.FoodMetals.METALS.length], 0.0);
        final double raw = Organisms.infectionProbability(Disease.DYSENTERY, foul, 0.5);
        final double ripe = Organisms.infectionProbability(Disease.DYSENTERY, Filtration.filter(foul, Filtration.RIPE_REMOVAL), 0.5);
        final double young = Organisms.infectionProbability(Disease.DYSENTERY, Filtration.filter(foul, Filtration.UNRIPE_REMOVAL), 0.5);
        final double boiled = Organisms.infectionProbability(Disease.DYSENTERY, foul.boiled(), 0.5);
        System.out.printf("SCENARIO filter: dysentery from one drink of foul water raw %.3e, unripe filter %.3e, ripe filter %.3e, boiled %.3e%n", raw, young, ripe, boiled);
        // By hand: 100 * 6e-3 + 1e6 * 1.5e-3 = 1500.6 Shigella a litre, 750.3 in a drink: P = 1 - (1 + 750.3 / 42.9)^-0.21 = 0.458
        // (beta = 1120 / (2^(1/0.21) - 1) = 42.9); an unripe filter leaves half of the organisms, 375: 0.380; a ripe one a hundredth, 7.5: 0.0333
        assertEquals(0.458, raw, 0.458 * 0.05);
        assertEquals(0.380, young, 0.380 * 0.05);
        assertEquals(0.0333, ripe, 0.0333 * 0.05);
        assertEquals(0.0, boiled);
    }
}
