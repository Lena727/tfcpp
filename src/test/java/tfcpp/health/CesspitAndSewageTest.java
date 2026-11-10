package tfcpp.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The cesspit, its leak to the ground and to the water near it, and how much waste people and animals make. */
class CesspitAndSewageTest
{
    @Test
    void aDayTakesOnePercentOfTheContentsIntoTheGround()
    {
        final CesspitMath.State full = new CesspitMath.State(1000.0, 1000.0, 100.0, 0.0);
        final CesspitMath.Step day = CesspitMath.advance(full, 1.0);
        assertEquals(990.0, day.state().volume(), 1e-9);
        assertEquals(10.0, day.leakedVolume(), 1e-9);
        // The infective part: what is left, what leaked and what died add up to what was there
        final double k = CesspitMath.leakRate() + CesspitMath.decayRate();
        final double diedShare = CesspitMath.decayRate() / k * (1.0 - Math.exp(-k));
        assertEquals(full.active(), day.state().active() + day.leakedActive() + diedShare * full.active(), 1e-9);
        assertEquals(full.shedDysentery(), day.state().shedDysentery() + day.leakedDysentery() + diedShare * full.shedDysentery(), 1e-9);
        assertTrue(day.leakedActive() > 0.0 && day.leakedActive() < 10.0, "A little less than a hundredth of the infective part goes, as some has died already");
        assertEquals(0.0, day.leakedTyphoid());
    }

    @Test
    void theInfectiveContentsDieByTheirT90InThePit()
    {
        final CesspitMath.State full = new CesspitMath.State(1000.0, 1000.0, 0.0, 0.0);
        final CesspitMath.Step sixty = CesspitMath.advance(full, 60.0);
        final double expected = Math.exp(-(CesspitMath.leakRate() + CesspitMath.decayRate()) * 60.0);
        assertEquals(1000.0 * expected, sixty.state().active(), 1e-9);
        // With the decay alone the infective part would be a tenth in 60 days: T90 of 60 days
        assertEquals(0.1, Math.exp(-CesspitMath.decayRate() * 60.0), 1e-12);
        // The closed form composes: two halves of a stretch are the stretch
        final CesspitMath.Step a = CesspitMath.advance(full, 30.0);
        final CesspitMath.Step b = CesspitMath.advance(a.state(), 30.0);
        assertEquals(sixty.state().active(), b.state().active(), 1e-9);
        assertEquals(sixty.leakedActive(), a.leakedActive() + b.leakedActive(), 1e-9);
        assertEquals(sixty.state().volume(), b.state().volume(), 1e-9);
    }

    @Test
    void aFullPitStopsAcceptingAndEmptyingTakesWasteOutInProportion()
    {
        final CesspitMath.State nearlyFull = new CesspitMath.State(990.0, 500.0, 0.0, 0.0);
        assertEquals(10.0, CesspitMath.room(nearlyFull), 1e-12);
        assertEquals(0.0, CesspitMath.room(new CesspitMath.State(1000.0, 0.0, 0.0, 0.0)));
        final CesspitMath.State added = CesspitMath.add(CesspitMath.State.EMPTY, 40.0, 40.0, 0.0);
        assertEquals(40.0, added.volume());
        assertEquals(40.0, added.active(), "Fresh waste is all infective");
        final CesspitMath.State taken = CesspitMath.take(new CesspitMath.State(1000.0, 400.0, 40.0, 4.0), 250.0);
        assertEquals(750.0, taken.volume(), 1e-9);
        assertEquals(300.0, taken.active(), 1e-9);
        assertEquals(30.0, taken.shedDysentery(), 1e-9);
        assertEquals(3.0, taken.shedTyphoid(), 1e-9);
        assertEquals(CesspitMath.State.EMPTY, CesspitMath.take(CesspitMath.State.EMPTY, 20.0));
        assertEquals(20.0, CesspitMath.LITRES_PER_NIGHT_SOIL);
    }

    @Test
    void aPitPutsSewageIntoTheGroundWaterThatFallsWithDistance()
    {
        assertEquals(1e-6, CesspitMath.groundwaterFactor(0.0), 1e-18);
        assertEquals(2.5e-7, CesspitMath.groundwaterFactor(15.0), 1e-18, "A quarter at 15 m: the rule of the well from a latrine");
        assertTrue(CesspitMath.groundwaterFactor(30.0) < CesspitMath.groundwaterFactor(15.0));
        // A full pit of fresh waste, a well 15 blocks away: 0.25 mL of sewage in a litre
        assertEquals(2.5e-4, 1000.0 * CesspitMath.groundwaterFactor(15.0), 1e-15);
        // If a tenth of that waste is from a shedder, a drink of half a litre carries typhoid organisms
        final double typhoid = Organisms.perLitre(Disease.TYPHOID, 2.5e-4, 2.5e-5) * 0.5;
        assertEquals(1e5 * 2.5e-5 * 0.5 + 10.0 * 2.25e-4 * 0.5, typhoid, 1e-9);
        assertTrue(Organisms.infectionProbability(Disease.TYPHOID, typhoid) > 0.0);
        assertEquals(16, CesspitMath.REACH_HORIZONTAL);
        assertEquals(4, CesspitMath.REACH_VERTICAL);
        assertEquals(30, CesspitMath.GROUNDWATER_REACH);
    }

    @Test
    void aPersonMakesOneAndAHalfLitresADayAndAnAnimalByItsKind()
    {
        assertEquals(1.5, SewageRates.PERSON_LITRES_PER_DAY, 0.0);
        assertEquals(0.075, SewageRates.litres(SewageRates.PERSON_LITRES_PER_DAY, 1200), 1e-12, "A minute of ticks");
        assertEquals(1.5, SewageRates.litres(1.5, 24_000), 1e-12);
        assertEquals(0.0, SewageRates.litres(1.5, -5));
        assertEquals(50.0, SewageRates.animalLitresPerDay("cow"));
        assertEquals(25.0, SewageRates.animalLitresPerDay("horse"));
        assertEquals(5.0, SewageRates.animalLitresPerDay("pig"));
        for (String small : new String[] {"sheep", "goat", "alpaca"})
        {
            assertEquals(3.0, SewageRates.animalLitresPerDay(small), small);
        }
        for (String bird : new String[] {"chicken", "duck", "quail"})
        {
            assertEquals(0.1, SewageRates.animalLitresPerDay(bird), bird);
        }
        assertEquals(0.0, SewageRates.animalLitresPerDay("wolf"));
        assertEquals(0.0, SewageRates.animalLitresPerDay(null));
        assertTrue(SewageRates.DYSENTERY_LITRES_PER_DAY > SewageRates.PERSON_LITRES_PER_DAY);
        assertEquals(33.3, SewageRates.animalLitresPerDay("cow") / SewageRates.PERSON_LITRES_PER_DAY, 0.1, "A cow makes as much as 33 people");
    }

    @Test
    void waterThatIsDrawnNearAPitCannotCarryAwayMoreThanThePitHeld()
    {
        CesspitMath.State pit = new CesspitMath.State(1000.0, 800.0, 80.0, 8.0);
        final double factor = CesspitMath.groundwaterFactor(5.0); // 5e-7
        double carriedSewage = 0.0, carriedShigella = 0.0;
        for (int draw = 0; draw < 200; draw++)
        {
            final CesspitMath.Draw taken = CesspitMath.draw(pit, factor, 1e7); // ten million litres at a draw: far more than the pit's share
            carriedSewage += taken.active();
            carriedShigella += taken.shedDysentery();
            pit = taken.state();
            assertTrue(pit.active() >= 0.0 && pit.shedDysentery() >= 0.0 && pit.shedTyphoid() >= 0.0);
        }
        assertEquals(800.0, carriedSewage, 1e-9, "everything that was infective and no more");
        assertEquals(80.0, carriedShigella, 1e-9);
        assertEquals(1000.0, pit.volume(), 0.0, "the waste stays, the water took only the pathogens");
        // A small draw takes its proportion: 1000 L of water at 5 blocks from 800 infective litres carries 800 * 5e-7 * 1000 = 0.4 L
        assertEquals(0.4, CesspitMath.draw(new CesspitMath.State(1000.0, 800.0, 0.0, 0.0), factor, 1000.0).active(), 1e-12);
    }

    @Test
    void nightSoilBecomesSafeByBeingKeptWithTheT90OfAPit()
    {
        assertEquals(1.0, NightSoil.decayed(1.0, 0.0), 0.0);
        assertEquals(0.1, NightSoil.decayed(1.0, CesspitMath.PATHOGEN_T90_DAYS), 1e-12, "a tenth after 60 days");
        assertEquals(0.01, NightSoil.decayed(1.0, 2.0 * CesspitMath.PATHOGEN_T90_DAYS), 1e-12);
        assertEquals(1.0, NightSoil.decayed(1.0, -3.0), 0.0, "a calendar that goes back does not make the load grow");
    }
}
