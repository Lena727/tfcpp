package tfcpp.chemistry;

import java.util.Locale;

/**
 * The chemical liquids of the mod. Adding one here registers its fluid type and both fluids, with no fluid block, as {@link Chemistry} walks this list.
 */
public enum Liquid
{
    /** High-temperature coal tar is the heaviest thing that comes out of coke oven gas: 1.19 kg/L, and a few hundred times as thick as water at 20 C. */
    COAL_TAR(1190, 200f, 0xFF1B1713, true),
    /** Pine and hardwood tar are close to water in weight and almost as thick as treacle at 20 C. */
    WOOD_TAR(1050, 150f, 0xFF3A2414, true),
    /** The gas liquor of a coke oven: water with a few percent of dissolved ammonia, phenols and salts. */
    AMMONIA_LIQUOR(1010, 1.1f, 0xFFB9A874, false),
    /** Crude wood vinegar: water with some 6 to 10 percent of acetic acid, methanol and acetone. */
    PYROLIGNEOUS_LIQUOR(1020, 1.4f, 0xFF8B5A2B, false);

    private final int density;
    private final float viscosity;
    private final int tint;
    private final boolean tar;

    Liquid(int density, float viscosity, int tint, boolean tar)
    {
        this.density = density;
        this.viscosity = viscosity;
        this.tint = tint;
        this.tar = tar;
    }

    public String id()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return kg/m3 at 20 C, which is the same as g/L */
    public int density()
    {
        return density;
    }

    /** @return kg/L at 20 C */
    public double kilogramsPerLitre()
    {
        return density / 1000.0;
    }

    /** @return dynamic viscosity at 20 C in mPa s, as the references give it */
    public float millipascalSeconds()
    {
        return viscosity;
    }

    /** @return viscosity on the scale of NeoForge's fluid types, where water is 1000 */
    public int fluidTypeViscosity()
    {
        return Math.round(viscosity * 1000f);
    }

    /** @return the colour that the client draws this in, as ARGB */
    public int tint()
    {
        return tint;
    }

    /** @return {@code true} if this is a tar: it is heavier than the water of the liquors, and settles out of them to a tank of its own */
    public boolean isTar()
    {
        return tar;
    }
}
