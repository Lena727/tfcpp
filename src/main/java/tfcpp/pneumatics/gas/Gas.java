package tfcpp.pneumatics.gas;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

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
    /** Nitrogen dioxide, the brown fume of the chamber, taken as NO2. It carries the oxygen to the sulfur dioxide in a lead chamber, and it is a dangerous gas in its own right. */
    NITROGEN_OXIDES(46.01f),
    /** Hydrogen chloride from the distillation of salt with acid. It fumes white as it takes up the moisture of the air and dissolves in water at once, to hydrochloric acid in the lungs. */
    HYDROGEN_CHLORIDE(36.46f),
    /** Air at 20 C can only hold about 13 mg/m3 of mercury vapour, or 1.6 ppm. Beyond that it condenses into droplets, and the droplets that lie on the surfaces give it off again, see {@link #evaporatesFromDeposit()}. */
    MERCURY_VAPOR(200.59f, 6.6e-5f, 0.05f, true),
    /** Arsenic and antimony oxides sublime in a furnace and condense to a fine white dust as soon as the fumes cool. */
    ARSENIC_TRIOXIDE(197.84f, 0f, 0.003f),
    ANTIMONY_TRIOXIDE(291.52f, 0f, 0.003f),
    /** Zinc vapour burns in air to a white smoke of zinc oxide. */
    ZINC_OXIDE_FUME(81.38f, 0f, 0.003f),
    /** Lead boils off a smelting hearth and burns in air to a fine smoke of oxide. */
    LEAD_OXIDE_FUME(223.2f, 0f, 0.003f),
    /**
     * Coal dust in the air. The unit of the cell is 1 kg of dust, not a mole. It takes no room from the air (a cloud of 2 kg/m3 of coal is only 0.15% of the volume it hangs in), and it adds nothing to the density of the air: a particle of 10 to 75 um falls by itself at a few mm/s up to 0.2 m/s, and a cloud makes no density current, which is why it hangs for minutes. It is carried by diffusion and by pressure flow like any gas, and falls out of the air into {@link #SETTLED_COAL_DUST} at 1% per second, about 70 s to half.
     */
    COAL_DUST(1000f, 0f, 0.01f, true, true),
    /** Coal dust lying on the floor, roof and walls of a cell. It is not a gas: nothing moves it but a blast or a person, and it takes no part in the air. Same unit as {@link #COAL_DUST}. */
    SETTLED_COAL_DUST(1000f, 0f, 0f, true, false),
    /** Limestone dust spread on a gallery. It does not burn, and a blast that finds enough of it among the coal dust raises nothing. Same unit as {@link #COAL_DUST}. */
    SETTLED_ROCK_DUST(1000f, 0f, 0f, true, false);

    public static final Gas[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final String id;
    private final float molarMass;
    private final float saturationMoles;
    private final float depositionRate;
    private final boolean particulate;
    private final boolean mobile;
    private final boolean evaporatesFromDeposit;
    private final boolean saturating;
    private final float createCellMoles;

    /** The smallest transfer that may open a new cell for a gas with no saturation of its own. At 10% mixing, such a gas keeps spreading into empty space down to about 5 ppm. A compile-time constant, as an enum constructor may not read any other static field. */
    public static final float ORDINARY_CREATE_CELL_MOLES = 2e-5f;
    /** What share of its saturation a gas must reach in a transfer to open a new cell, for a gas that has one. */
    private static final float SATURATING_CREATE_SHARE = 0.01f;

    Gas(float molarMass)
    {
        this(molarMass, Float.MAX_VALUE, 0f);
    }

    Gas(float molarMass, float saturationMoles, float depositionRate)
    {
        this(molarMass, saturationMoles, depositionRate, false, true, false);
    }

    Gas(float molarMass, float saturationMoles, float depositionRate, boolean evaporatesFromDeposit)
    {
        this(molarMass, saturationMoles, depositionRate, false, true, evaporatesFromDeposit);
    }

    Gas(float molarMass, float saturationMoles, float depositionRate, boolean particulate, boolean mobile)
    {
        this(molarMass, saturationMoles, depositionRate, particulate, mobile, false);
    }

    Gas(float molarMass, float saturationMoles, float depositionRate, boolean particulate, boolean mobile, boolean evaporatesFromDeposit)
    {
        this.id = name().toLowerCase(Locale.ROOT);
        this.molarMass = molarMass;
        this.saturationMoles = saturationMoles;
        this.depositionRate = depositionRate;
        this.particulate = particulate;
        this.mobile = mobile;
        this.evaporatesFromDeposit = evaporatesFromDeposit;
        // Zero means every trace condenses at once (the fumes), and the largest value means no saturation at all
        this.saturating = saturationMoles > 0f && saturationMoles < Float.MAX_VALUE;
        this.createCellMoles = saturating ? saturationMoles * SATURATING_CREATE_SHARE : ORDINARY_CREATE_CELL_MOLES;
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

    /**
     * @return {@code true} for dust of any kind. Its amounts are kilograms, not moles, so they are never part of the volume of gas in a cell, the pressure, or the oxygen left in the air.
     */
    public boolean isParticulate()
    {
        return particulate;
    }

    /**
     * @return {@code false} for settled dust, which stays where it is: the simulator does not move it between cells, the atmosphere does not carry it off, pipes and fans do not take it up, and nobody breathes it
     */
    public boolean mobile()
    {
        return mobile;
    }

    /**
     * @return {@code true} for a vapour that can only be present up to a saturation, and so has to be let spread at a level far below the one that matters for an ordinary gas: mercury at saturation is 1.6 ppm, and lies far below {@link #ORDINARY_CREATE_CELL_MOLES}
     */
    public boolean isSaturating()
    {
        return saturating;
    }

    /** @return the amount of this gas, in a transfer to an empty position, from which that position gets a cell of its own. A tenth of the saturation for a vapour that has one. */
    public float createCellMoles()
    {
        return createCellMoles;
    }

    /** @return the amount of this gas below which a cell holding nothing else is dropped. Half of what it takes to open one, so that a cell that has just been opened is not dropped again at once. */
    public float removeCellMoles()
    {
        return createCellMoles * 0.5f;
    }

    /**
     * @return {@code true} if what condenses out of this gas lies on the surfaces as a liquid film, and goes back into the air as the air is cleared, up to saturation. Mercury does. The fume of an oxide condenses to a dust that stays where it fell.
     */
    public boolean evaporatesFromDeposit()
    {
        return evaporatesFromDeposit;
    }

    /** @return the settled form that this falls out of the air as, if it is a suspended dust. What settles is moved there within the same cell, not removed as condensing vapour is. */
    @Nullable
    public Gas settlesInto()
    {
        return this == COAL_DUST ? SETTLED_COAL_DUST : null;
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
