package tfcpp.ecology;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * The soil orders of TFC's soil variants, with the pH that a soil of each order starts at and its cation exchange capacity. Free of Minecraft types: the variants are matched by name.
 * <p>
 * The pH values are the characteristic reaction of each order in the USDA Soil Taxonomy: Podzols are leached and acid, Oxisols are old and weathered, Alfisols and Andisols are moderately acid, Entisols and Fluvisols (young, alluvial) near neutral, Mollisols neutral under their grasses, and Aridisols alkaline, as little rain washes the carbonate out of them.
 * <p>
 * The capacity is in cmol(+)/kg of soil: Mollisols and Andisols are rich in humus and allophane (about 25), Alfisols and Fluvisols hold a clay of medium activity (15), Entisols and Aridisols are young and poor in clay (10), a Podzol is sandy and leached (8) and an Oxisol is kaolinite and iron oxide, which hold few cations (5). Typical values from the Soil Taxonomy and soil chemistry texts (Brady and Weil, The Nature and Properties of Soils).
 */
public enum SoilOrder
{
    PODZOL(4.8, 8.0, false),
    OXISOL(5.0, 5.0, false),
    ALFISOL(6.0, 15.0, false),
    ANDISOL(6.0, 25.0, false),
    ENTISOL(6.5, 10.0, false),
    FLUVISOL(6.5, 15.0, false),
    MOLLISOL(7.0, 25.0, false),
    ARIDISOL(8.0, 10.0, true);

    private final double startPh, cecCmolPerKg;
    private final boolean freeCarbonate;

    SoilOrder(double startPh, double cecCmolPerKg, boolean freeCarbonate)
    {
        this.startPh = startPh;
        this.cecCmolPerKg = cecCmolPerKg;
        this.freeCarbonate = freeCarbonate;
    }

    public double startPh()
    {
        return startPh;
    }

    /** @return cmol(+)/kg */
    public double cecCmolPerKg()
    {
        return cecCmolPerKg;
    }

    /** @return equivalents per square metre of the root zone */
    public double cecPerM2()
    {
        return cecCmolPerKg * SoilConstants.CMOL_PER_KG_TO_EQ_PER_M2;
    }

    /** @return {@code true} if the soil has free carbonate in it: only the Aridisol, in which too little rain falls to wash it out */
    public boolean hasFreeCarbonate()
    {
        return freeCarbonate;
    }

    /** @return the order of a TFC soil variant by its name, or {@code null} for one that is not known */
    @Nullable
    public static SoilOrder byName(String name)
    {
        try
        {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}
