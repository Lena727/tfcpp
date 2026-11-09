package tfcpp.chemistry;

import tfcpp.ecology.Soakable;
import tfcpp.pneumatics.gas.Gas;

/**
 * What a liquid becomes in the air of a room when nothing keeps it in: a retort without its condenser, or a flask that has cracked. Free of Minecraft types.
 * <p>
 * Sulfuric acid hardly boils off at room temperature, and its vapour is steam and acid mist, which no gas of the mod stands for. The other two fume.
 */
public final class AcidVapour
{
    /** Moles of nitrogen dioxide per litre of nitric acid that fume off: a litre holds 21 mol of acid, and about a quarter of it escapes in the time that a flask takes to empty. A game figure, not measured. */
    public static final float NITROGEN_OXIDES_PER_LITRE = 5f;

    /** Moles of hydrogen chloride per litre of hydrochloric acid: 32% by mass at 1.16 kg/L is 10.2 mol/L, and spirit of salt fumes all of it when it is heated or poured out hot. */
    public static final float HYDROGEN_CHLORIDE_PER_LITRE = 10f;

    /**
     * @param liquid the liquid that is let go
     * @param litres how much of it
     * @return moles of each gas that it gives, indexed by {@link Gas#ordinal()}
     */
    public static float[] of(Liquid liquid, double litres)
    {
        final float[] gases = new float[Gas.COUNT];
        switch (liquid)
        {
            case NITRIC_ACID -> gases[Gas.NITROGEN_OXIDES.ordinal()] = (float) (litres * NITROGEN_OXIDES_PER_LITRE);
            case HYDROCHLORIC_ACID -> gases[Gas.HYDROGEN_CHLORIDE.ordinal()] = (float) (litres * HYDROGEN_CHLORIDE_PER_LITRE);
            default -> {} // Sulfuric and chamber acid: steam only. Tar, liquors and spirit are not gases of the mod.
        }
        return gases;
    }

    /**
     * @param liquid the liquid that is let go
     * @param litres how much of it
     * @return the litres of it that do not go up as fume and are left on the floor: all of a liquid with no vapour in the model, and for the two fuming acids the share of their acid that the fume does not account for
     */
    public static double unvapourised(Liquid liquid, double litres)
    {
        final double fumed = switch (liquid)
        {
            case NITRIC_ACID -> NITROGEN_OXIDES_PER_LITRE / Soakable.equivalentsPerLitre(Liquid.NITRIC_ACID, Soakable.NITRIC_ACID_FRACTION, Soakable.HNO3_MOLAR_MASS, 1);
            case HYDROCHLORIC_ACID -> HYDROGEN_CHLORIDE_PER_LITRE / Soakable.equivalentsPerLitre(Liquid.HYDROCHLORIC_ACID, Soakable.HYDROCHLORIC_ACID_FRACTION, Soakable.HCL_MOLAR_MASS, 1);
            default -> 0.0;
        };
        return litres * (1.0 - Math.min(fumed, 1.0));
    }

    private AcidVapour() {}
}
