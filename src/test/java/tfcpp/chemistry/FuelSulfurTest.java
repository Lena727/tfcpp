package tfcpp.chemistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FuelSulfurTest
{
    private static final float SULFUR_GRAMS_PER_MOLE = 32.06f;

    @Test
    void coalGivesWhatOnePointFivePercentOfAThreeKilogramPieceHolds()
    {
        final float grams = FuelSulfur.sulfurDioxideMoles("tfc:ore/bituminous_coal") * SULFUR_GRAMS_PER_MOLE;
        assertEquals(0.015f * 3000f, grams, 1.5f);
        assertEquals(FuelSulfur.sulfurDioxideMoles("tfc:ore/bituminous_coal"), FuelSulfur.sulfurDioxideMoles("minecraft:coal"));
    }

    @Test
    void cokeAndLigniteGiveOnePercent()
    {
        assertEquals(0.01f * 3000f, FuelSulfur.sulfurDioxideMoles("tfcpp:coke") * SULFUR_GRAMS_PER_MOLE, 1.5f);
        assertEquals(0.9f, FuelSulfur.sulfurDioxideMoles("tfc:ore/lignite"));
    }

    @Test
    void ligniteCharHoldsAboutHalfTheSulfurOfTheLigniteItCameFromInLessThanHalfTheMass()
    {
        // A piece of lignite holds 0.9 mol, and gives 0.4 of a piece of char
        final float perPieceOfChar = FuelSulfur.sulfurDioxideMoles("tfcpp:lignite_char");
        assertEquals(1.0f, perPieceOfChar);
        assertEquals(0.5f, perPieceOfChar * 0.4f / FuelSulfur.sulfurDioxideMoles("tfc:ore/lignite"), 0.1f);
        assertTrue(perPieceOfChar > FuelSulfur.sulfurDioxideMoles("tfc:ore/lignite"));
    }

    @Test
    void woodAndCharcoalGiveNothingAndNeitherDoesAnythingUnknown()
    {
        assertEquals(0f, FuelSulfur.sulfurDioxideMoles("minecraft:charcoal"));
        assertEquals(0f, FuelSulfur.sulfurDioxideMoles("tfc:wood/log/oak"));
        assertEquals(0f, FuelSulfur.sulfurDioxideMoles("tfc:peat"));
    }

    @Test
    void coalGivesMoreThanCokeItMakes()
    {
        assertTrue(FuelSulfur.sulfurDioxideMoles("tfcpp:coke") < FuelSulfur.sulfurDioxideMoles("tfc:ore/bituminous_coal"));
    }
}
