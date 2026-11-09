package tfcpp.ecology;

/**
 * What lime, crushed limestone and wood ash do to an acid soil. Free of Minecraft types.
 * <p>
 * A powder item is 50 g (the scale of the project). Lime is burnt limestone and keeps its moles (CaCO3 to CaO), so a piece of lime is 28 g of CaO, 0.5 mol, 2 eq per mol: 1.0 eq a piece, the same as flux. Flux, crushed limestone CaCO3 (100.09 g/mol, 2 eq/mol): 1.0 eq a piece. Flux that is ground from borax is counted as limestone too: one item, and the game cannot tell its sources (a known limitation). Wood ash about 0.5 eq a piece: ash is 25-50 % as effective as lime by weight, the neutralising value being the carbonate and oxide of its potash and calcium (Demeyer et al. 2001, Bioresour. Technol. 77; Ohno 1992).
 * <p>
 * Real liming is 3-10 t/ha, 0.3-1 kg/m2, so a chunk of 256 m2 takes tens to hundreds of kilograms, hundreds to thousands of pieces. Spreaders come in a later version. Metals do not leave the soil by liming: only acid and aluminium are neutralised.
 */
public final class Liming
{
    public static final double GRAMS_PER_PIECE = 50.0;
    public static final double LIME_EQ_PER_PIECE = 2.0 * GRAMS_PER_PIECE / 100.09;
    public static final double FLUX_EQ_PER_PIECE = 2.0 * GRAMS_PER_PIECE / 100.09;
    public static final double WOOD_ASH_EQ_PER_PIECE = 0.5;

    private Liming() {}

    public static double equivalents(double eqPerPiece, int pieces)
    {
        return eqPerPiece * Math.max(0, pieces);
    }
}
