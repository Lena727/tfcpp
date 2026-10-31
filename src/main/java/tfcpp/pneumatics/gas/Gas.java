package tfcpp.pneumatics.gas;

import java.util.Locale;

/**
 * Gas species tracked by the grid. Ordinary air is implicit: a cell only stores what was added to it, and that displaces air.
 * <p>
 * {@link #FLUE_NITROGEN} is the nitrogen left behind when combustion removes oxygen from air. It is how oxygen depletion is
 * represented: see {@link Combustion}.
 */
public enum Gas
{
    CARBON_MONOXIDE(28.01f),
    CARBON_DIOXIDE(44.01f),
    FLUE_NITROGEN(28.01f),
    SULFUR_DIOXIDE(64.07f),
    METHANE(16.04f),
    HYDROGEN(2.016f),
    HYDROGEN_SULFIDE(34.08f),
    AMMONIA(17.03f),
    CHLORINE(70.90f),
    /** Air at 20 C can only hold about 13 mg/m3 of mercury vapour, or 1.6 ppm. Beyond that it condenses into droplets. */
    MERCURY_VAPOR(200.59f, 6.6e-5f, 0.05f),
    /** Arsenic and antimony oxides sublime in a furnace and condense to a fine white dust as soon as the fumes cool. */
    ARSENIC_TRIOXIDE(197.84f, 0f, 0.003f),
    ANTIMONY_TRIOXIDE(291.52f, 0f, 0.003f),
    /** Zinc vapour burns in air to a white smoke of zinc oxide. */
    ZINC_OXIDE_FUME(81.38f, 0f, 0.003f);

    public static final Gas[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final String id;
    private final float molarMass;
    private final float saturationMoles;
    private final float depositionRate;

    Gas(float molarMass)
    {
        this(molarMass, Float.MAX_VALUE, 0f);
    }

    Gas(float molarMass, float saturationMoles, float depositionRate)
    {
        this.id = name().toLowerCase(Locale.ROOT);
        this.molarMass = molarMass;
        this.saturationMoles = saturationMoles;
        this.depositionRate = depositionRate;
    }

    public String id()
    {
        return id;
    }

    /** @return molar mass in g/mol */
    public float molarMass()
    {
        return molarMass;
    }

    /** @return moles of this gas a cell can hold before the excess starts to condense */
    public float saturationMoles()
    {
        return saturationMoles;
    }

    /** @return fraction per second of the excess over saturation settling out of the air */
    public float depositionRate()
    {
        return depositionRate;
    }

    public static Gas byId(String id)
    {
        for (Gas gas : VALUES)
        {
            if (gas.id.equals(id))
            {
                return gas;
            }
        }
        return null;
    }
}
