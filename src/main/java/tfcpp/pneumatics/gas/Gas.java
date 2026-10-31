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
    MERCURY_VAPOR(200.59f),
    ARSENIC_TRIOXIDE(197.84f);

    public static final Gas[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final String id;
    private final float molarMass;

    Gas(float molarMass)
    {
        this.id = name().toLowerCase(Locale.ROOT);
        this.molarMass = molarMass;
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
