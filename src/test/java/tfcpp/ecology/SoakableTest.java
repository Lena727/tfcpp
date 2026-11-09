package tfcpp.ecology;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tfcpp.chemistry.AcidVapour;
import tfcpp.chemistry.Liquid;

import static org.junit.jupiter.api.Assertions.*;

class SoakableTest
{
    private static double only(Soakable soakable, double millibuckets, Pollutant expected)
    {
        final double[] load = soakable.load(millibuckets);
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            if (pollutant != expected)
            {
                assertEquals(0.0, load[pollutant.ordinal()], 0.0, soakable + " adds no " + pollutant);
            }
        }
        return load[expected.ordinal()];
    }

    @Test
    void aLitreOfEachAcidHoldsTheEquivalentsThatItsStrengthAndDensityGive()
    {
        assertEquals(1550 * 0.65 / 98.079 * 2.0, only(Soakable.CHAMBER_ACID, 1.0, Pollutant.ACIDITY), 1e-9);
        assertEquals(20.5, only(Soakable.CHAMBER_ACID, 1.0, Pollutant.ACIDITY), 0.1);
        assertEquals(34.7, only(Soakable.SULFURIC_ACID, 1.0, Pollutant.ACIDITY), 0.1);
        assertEquals(21.1, only(Soakable.NITRIC_ACID, 1.0, Pollutant.ACIDITY), 0.1);
        assertEquals(10.2, only(Soakable.HYDROCHLORIC_ACID, 1.0, Pollutant.ACIDITY), 0.1);
    }

    @Test
    void tarIsItsMassAndLiquorIsItsPhenols()
    {
        assertEquals(1.19, only(Soakable.COAL_TAR, 1.0, Pollutant.TAR), 1e-9, "Coal tar is 1.19 kg/L");
        assertEquals(119.0, only(Soakable.COAL_TAR, 100.0, Pollutant.TAR), 1e-9);
    }

    @Test
    void metalsAreInGramsForAMillilitre()
    {
        assertEquals(13.53, only(Soakable.MERCURY, 1.0, Pollutant.MERCURY), 1e-9);
        assertEquals(1066.0, only(Soakable.LEAD, 100.0, Pollutant.LEAD), 1e-9, "An ingot of 100 mB is 100 ml of molten lead, 10.66 g/ml as in our fluid type");
        assertEquals(657.0, only(Soakable.ZINC, 100.0, Pollutant.ZINC), 1e-9, "Molten zinc is 6.57 g/ml");
        final double[] alloy = Soakable.ARSENICAL_COPPER.load(100.0);
        assertEquals(8.0 * 0.03 * 100.0, alloy[Pollutant.ARSENIC.ordinal()], 1e-9, "3% of the alloy is arsenic");
        assertEquals(8.0 * 0.97 * 100.0, alloy[Pollutant.COPPER.ordinal()], 1e-9, "and the rest is copper");
        assertEquals(800.0, alloy[Pollutant.ARSENIC.ordinal()] + alloy[Pollutant.COPPER.ordinal()], 1e-9, "all of the metal is accounted for");
    }

    @Test
    void woodTarPyroligneousLiquorAndTheAmmoniaOfLiquorAreRecorded()
    {
        assertEquals(1.05, only(Soakable.WOOD_TAR, 1.0, Pollutant.TAR), 1e-9, "Wood tar is 1.05 kg/L");
        assertEquals(0.015, only(Soakable.PYROLIGNEOUS_LIQUOR, 1.0, Pollutant.TAR), 1e-9, "15 g of soluble tar in a litre, and the acetic acid is not recorded");
        final double[] liquor = Soakable.AMMONIA_LIQUOR.load(1.0);
        assertEquals(0.002, liquor[Pollutant.TAR.ordinal()], 1e-9, "phenols");
        assertEquals(0.76, liquor[Pollutant.ACIDITY.ordinal()], 1e-9, "and an equivalent of acid for each mole of ammonia, which nitrifies");
    }

    @Test
    void loadIsInProportionToTheAmount()
    {
        for (Soakable soakable : Soakable.values())
        {
            final double[] one = soakable.load(1.0), seven = soakable.load(7.0);
            for (int i = 0; i < Pollutant.COUNT; i++)
            {
                assertEquals(one[i] * 7.0, seven[i], 1e-9, soakable + " " + Pollutant.VALUES[i]);
            }
        }
    }

    @Test
    void everyChemicalLiquidIsEitherRecordedOrNamedHereAsLeavingNoRecord()
    {
        // A new liquid has to be put in one list or the other on purpose
        final Set<Liquid> noRecord = EnumSet.of(Liquid.WOOD_SPIRIT);
        for (Liquid liquid : Liquid.values())
        {
            if (noRecord.contains(liquid))
            {
                assertNull(Soakable.of(liquid), liquid.name());
            }
            else
            {
                assertNotNull(Soakable.of(liquid), liquid.name());
            }
        }
    }

    @Test
    void aFlaskLetsOutWhatIsNotFume()
    {
        assertEquals(10.0, AcidVapour.unvapourised(Liquid.SULFURIC_ACID, 10.0), 1e-9, "Oil of vitriol gives no fume of the model");
        assertEquals(10.0, AcidVapour.unvapourised(Liquid.COAL_TAR, 10.0), 1e-9);
        final double nitric = AcidVapour.unvapourised(Liquid.NITRIC_ACID, 10.0);
        assertEquals(10.0 * (1.0 - 5.0 / 21.14), nitric, 0.02, "A quarter of the nitric acid fumes off");
        final double hydrochloric = AcidVapour.unvapourised(Liquid.HYDROCHLORIC_ACID, 10.0);
        assertTrue(hydrochloric >= 0.0 && hydrochloric < 0.5, "Almost all of spirit of salt fumes, got " + hydrochloric);
    }

    @Test
    void copperAndItsAlloysRecordTheCopperAndZincAndNickelTheyHold()
    {
        final double[] copper = Soakable.COPPER.load(100.0);
        assertEquals(802.0, copper[Pollutant.COPPER.ordinal()], 1e-9);
        final double[] brass = Soakable.BRASS.load(100.0);
        assertEquals(100.0 * 0.9 * 8.02, brass[Pollutant.COPPER.ordinal()], 1e-9);
        assertEquals(100.0 * 0.1 * 6.57, brass[Pollutant.ZINC.ordinal()], 1e-9);
        final double[] bronze = Soakable.BRONZE.load(100.0);
        assertEquals(100.0 * 0.9 * 8.02, bronze[Pollutant.COPPER.ordinal()], 1e-9, "The tin is not a pollutant here");
        assertEquals(0.0, bronze[Pollutant.ZINC.ordinal()], 0.0);
        assertEquals(781.0, Soakable.NICKEL.load(100.0)[Pollutant.NICKEL.ordinal()], 1e-9);
        final double[] bismuth = Soakable.BISMUTH_BRONZE.load(1.0);
        assertEquals(0.575 / 0.975 * 8.02, bismuth[Pollutant.COPPER.ordinal()], 1e-9, "The midpoints are made up to 1");
        assertTrue(Soakable.ROSE_GOLD.load(1.0)[Pollutant.COPPER.ordinal()] > 0.0);
        assertTrue(Soakable.STERLING_SILVER.load(1.0)[Pollutant.COPPER.ordinal()] > 0.0);
        assertTrue(Soakable.BLACK_BRONZE.load(1.0)[Pollutant.COPPER.ordinal()] > 0.0);
    }
}
