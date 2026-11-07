package tfcpp.chemistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Mass and element balance of the numbers of batch 6 of the global review: superphosphate, ammonia from lignite. */
class ChemistryBalanceTest
{
    private static final double APATITE_MOLAR_MASS = 504.3; // Ca5(PO4)3F
    private static final double ACID_MOLAR_MASS = 98.08, ACID_KILOGRAMS_PER_LITRE_OF_CHAMBER_ACID = 1.55 * 0.65;
    private static final double POWDER_GRAMS = 50.0;
    /** Per two apatite: three Ca(H2PO4)2.H2O at 252.07 g/mol and seven CaSO4 at 136.14 g/mol. */
    private static final double SOLIDS_PER_TWO_APATITE = 3 * 252.07 + 7 * 136.14;

    @Test
    void superphosphateTakesTheAcidThatTheStoichiometryAsksFor()
    {
        // 2 Ca5(PO4)3F + 7 H2SO4 + 3 H2O -> 3 Ca(H2PO4)2.H2O + 7 CaSO4 + 2 HF: 3.5 mol of acid for each mole of apatite
        final DistillationTable.Row row = DistillationTable.SUPERPHOSPHATE;
        final double apatite = row.items() * POWDER_GRAMS / APATITE_MOLAR_MASS;
        final double acid = row.litres() * ACID_KILOGRAMS_PER_LITRE_OF_CHAMBER_ACID * 1000.0 / ACID_MOLAR_MASS;
        assertEquals(3.5, acid / apatite, 0.05, "mol of H2SO4 for each mol of apatite");
    }

    @Test
    void superphosphateGivesAsMuchSolidAsTheReactionMakesAndNoMore()
    {
        final DistillationTable.Row row = DistillationTable.SUPERPHOSPHATE;
        final double apatite = row.items() * POWDER_GRAMS / APATITE_MOLAR_MASS;
        final double solidGrams = apatite / 2.0 * SOLIDS_PER_TWO_APATITE;
        final double given = row.byproducts() * POWDER_GRAMS;
        assertTrue(given <= solidGrams + 1e-6, "No more powder than the solids of the reaction: " + given + " g from " + solidGrams + " g");
        assertTrue(given >= 0.93 * solidGrams, "And not a poor share of them: " + given + " g from " + solidGrams + " g");
        final double massIn = row.items() * POWDER_GRAMS + row.litres() * 1000.0 * 1.55;
        assertTrue(given < massIn, "Mass out is less than mass in");
    }

    @Test
    void superphosphateKeepsThePhosphorusAndOnlyMakesItAvailable()
    {
        final DistillationTable.Row row = DistillationTable.SUPERPHOSPHATE;
        // Phosphorus by mass: apatite holds 3 P in 504.3 g, superphosphate 6 P in the solids of two apatite
        final double phosphorusIn = row.items() * POWDER_GRAMS * 3 * 30.97 / APATITE_MOLAR_MASS;
        final double phosphorusOut = row.byproducts() * POWDER_GRAMS * 6 * 30.97 / SOLIDS_PER_TWO_APATITE;
        assertTrue(phosphorusOut <= phosphorusIn * 1.0001, "The phosphorus out is not more than the phosphorus in");
        // The fertiliser scale is availability to a crop, not content: the batch feeds four times what the apatite did
        final double fieldIn = row.items() * ChemistryData.RAW_APATITE_PHOSPHORUS;
        final double fieldOut = row.byproducts() * ChemistryData.SUPERPHOSPHATE_PHOSPHORUS;
        assertEquals(4.0, fieldOut / fieldIn, 0.15, "What it can feed a field, out over in");
    }

    @Test
    void ligniteGivesTheSameShareOfItsNitrogenAsCoalGives()
    {
        final double ammoniaPerLitre = 0.757; // mol N: the liquor that makes one powder of ammonium sulfate (0.378 mol) with a little acid
        final double coalNitrogen = 3000 * 0.015 / 14.007; // 1.5% N in a 3 kg piece
        final double ligniteNitrogen = 3000 * 0.0075 / 14.007; // about half as much
        final double coalShare = (CarbonizationTable.COAL.liquorPerPiece() * ammoniaPerLitre + 0.3) / coalNitrogen; // plus the 0.3 mol NH3 in the gas
        final double ligniteShare = CarbonizationTable.LIGNITE.liquorPerPiece() * ammoniaPerLitre / ligniteNitrogen;
        assertTrue(ligniteShare <= 1.2 * coalShare, "Lignite gives " + ligniteShare + " of its nitrogen against " + coalShare + " for coal");
        assertEquals(0.4f, CarbonizationTable.LIGNITE.liquorPerPiece(), 0f);
    }
}
