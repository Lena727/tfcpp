package tfcpp.metallurgy;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.Combustion;

/**
 * Sulfide minerals and what heating them in air gives off, by stoichiometry. All amounts are moles per mole of the useful metal.
 * <p>
 * Metal volumes follow TFC's own scale, where a 100 mB ingot is a real ingot of 100 ml, about 0.9 kg of copper.
 */
public enum Sulfide
{
    /** Cu12(As,Sb)4S13. The arsenic and antimony sites are taken as half each, as natural tetrahedrite ranges between the two. */
    TETRAHEDRITE(8.96f, 63.55f, 13f / 12f, 2f / 12f, 2f / 12f, 0f, 0f),
    /** 2 ZnS + 3 O2 -> 2 ZnO + 2 SO2 */
    SPHALERITE(7.14f, 65.38f, 1f, 0f, 0f, 0f, 0.5f),
    /** Bi2S3 burning to bismuth and SO2 */
    BISMUTHINITE(9.78f, 208.98f, 1.5f, 0f, 0f, 0f, 0f),
    /** HgS + O2 -> Hg + SO2. Mercury boils at 357 C, so in an open furnace all of it goes up as vapour. */
    CINNABAR(13.53f, 200.59f, 1f, 0f, 0f, 1f, 0f);

    private final float density, molarMass;
    private final float sulfur, arsenic, antimony, mercury, oxideOxygen;

    Sulfide(float density, float molarMass, float sulfur, float arsenic, float antimony, float mercury, float oxideOxygen)
    {
        this.density = density;
        this.molarMass = molarMass;
        this.sulfur = sulfur;
        this.arsenic = arsenic;
        this.antimony = antimony;
        this.mercury = mercury;
        this.oxideOxygen = oxideOxygen;
    }

    /** @return moles of metal in this many millibuckets, where 1 mB is 1 ml */
    public float metalMoles(float milliBuckets)
    {
        return milliBuckets * density / molarMass;
    }

    /** @return moles of each {@link Gas} released by heating ore holding this much metal in air. */
    public float[] fumes(float milliBuckets)
    {
        final float metal = metalMoles(milliBuckets);
        final float[] gases = new float[Gas.COUNT];

        gases[Gas.SULFUR_DIOXIDE.ordinal()] = metal * sulfur;
        gases[Gas.ARSENIC_TRIOXIDE.ordinal()] = metal * arsenic / 2f; // 4 As + 3 O2 -> 2 As2O3
        gases[Gas.ANTIMONY_TRIOXIDE.ordinal()] = metal * antimony / 2f;
        gases[Gas.MERCURY_VAPOR.ordinal()] = metal * mercury;

        final float oxygen = metal * (sulfur + 0.75f * (arsenic + antimony) + oxideOxygen);
        gases[Gas.FLUE_NITROGEN.ordinal()] = oxygen * Combustion.NITROGEN_PER_OXYGEN;
        return gases;
    }
}
