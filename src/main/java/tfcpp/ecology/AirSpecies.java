package tfcpp.ecology;

import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;

/**
 * The gases and fumes that the regional air of stage B carries, and what each of them becomes when it comes down. Free of Minecraft types.
 * <p>
 * Every other gas of the grid is either too light to stay (hydrogen, methane, ammonia), too inert (nitrogen, carbon dioxide and monoxide) or local (dust), and leaves the model at the edge of the room, with carbon dioxide and monoxide counted in the world's counter. Ammonia would come down as acid too, once nitrified, and is left for later.
 * <p>
 * Dry deposition is a velocity: a gas is taken up by the ground at {@code vd} metres per second, so in a layer of height H a fraction {@code vd / H} of it comes down every second (Seinfeld and Pandis, Atmospheric Chemistry and Physics, 3rd ed., chapter 19: SO2 0.3 to 1 cm/s over vegetation, HCl about 1 cm/s because it dissolves at once, NO2 0.1 to 0.5 cm/s, fine particles 0.1 to 0.5 cm/s, elemental mercury vapour is taken up very slowly, 0.01 cm/s, Zhang et al. 2012). Wet deposition is a scavenging rate {@code lambda} in 1/s while it rains: about 1e-4 for the soluble gases, 1e-5 for the oxide fumes, which are fine particles that raindrops take up poorly (Seinfeld and Pandis chapter 20, scavenging coefficients of 1e-5 to 1e-4 for accumulation-mode particles), and 1e-6 for elemental mercury, which lives for months in the air: what rain removes is its oxidised form, a small share (Schroeder and Munthe 1998, Atmos. Environ. 32).
 */
public enum AirSpecies
{
    /** Two equivalents a mole: it becomes sulfuric acid in the drop or in the soil water. */
    SULFUR_DIOXIDE(Gas.SULFUR_DIOXIDE, Pollutant.ACIDITY, 2.0, 0.008, 1e-4),
    /** As nitric acid, one equivalent a mole. */
    NITROGEN_OXIDES(Gas.NITROGEN_OXIDES, Pollutant.ACIDITY, 1.0, 0.003, 1e-4),
    HYDROGEN_CHLORIDE(Gas.HYDROGEN_CHLORIDE, Pollutant.ACIDITY, 1.0, 0.01, 1e-4),
    /** A mole of the oxide carries two of arsenic. */
    ARSENIC_TRIOXIDE(Gas.ARSENIC_TRIOXIDE, Pollutant.ARSENIC, 2.0 * 74.9216, 0.002, 1e-5),
    ANTIMONY_TRIOXIDE(Gas.ANTIMONY_TRIOXIDE, Pollutant.ANTIMONY, 2.0 * 121.760, 0.002, 1e-5),
    LEAD_OXIDE_FUME(Gas.LEAD_OXIDE_FUME, Pollutant.LEAD, 207.2, 0.002, 1e-5),
    ZINC_OXIDE_FUME(Gas.ZINC_OXIDE_FUME, Pollutant.ZINC, 65.38, 0.002, 1e-5),
    MERCURY_VAPOR(Gas.MERCURY_VAPOR, Pollutant.MERCURY, 200.59, 0.0001, 1e-6);

    public static final AirSpecies[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private static final AirSpecies[] BY_GAS = new AirSpecies[Gas.COUNT];

    static
    {
        for (AirSpecies species : VALUES)
        {
            BY_GAS[species.gas.ordinal()] = species;
        }
    }

    private final Gas gas;
    private final Pollutant pollutant;
    private final double amountPerMole;
    private final double dryVelocity;
    private final double wetScavenging;

    /**
     * @param amountPerMole how much of the pollutant a mole of the gas puts into the ground, in the unit of the pollutant: equivalents of acid, or grams of the metal
     * @param dryVelocity metres per second
     * @param wetScavenging per second, while it rains
     */
    AirSpecies(Gas gas, Pollutant pollutant, double amountPerMole, double dryVelocity, double wetScavenging)
    {
        this.gas = gas;
        this.pollutant = pollutant;
        this.amountPerMole = amountPerMole;
        this.dryVelocity = dryVelocity;
        this.wetScavenging = wetScavenging;
    }

    /**
     * @return the species that a gas that goes out into the open comes to in the air, which is its own for the species of the regional air, sulfur dioxide for hydrogen sulfide (it oxidises within one to two days, a mole of SO2 for each mole of H2S, Seinfeld and Pandis ch. 6), and hydrogen chloride for chlorine (two moles of HCl for each of Cl2: it takes hydrogen from water and organic matter in the air within hours), or {@code null} if it is not carried
     */
    @Nullable
    public static AirSpecies emittedAs(Gas gas)
    {
        return switch (gas)
        {
            case HYDROGEN_SULFIDE -> SULFUR_DIOXIDE;
            case CHLORINE -> HYDROGEN_CHLORIDE;
            default -> of(gas);
        };
    }

    /** @return the moles of {@link #emittedAs} that a mole of this gas comes to */
    public static double yieldOf(Gas gas)
    {
        return gas == Gas.CHLORINE ? 2.0 : 1.0;
    }

    /** @return the species that a gas of the grid is carried as, or {@code null} if the regional air does not carry it */
    @Nullable
    public static AirSpecies of(Gas gas)
    {
        return BY_GAS[gas.ordinal()];
    }

    public Gas gas()
    {
        return gas;
    }

    public Pollutant pollutant()
    {
        return pollutant;
    }

    public double amountPerMole()
    {
        return amountPerMole;
    }

    /** @return metres per second */
    public double dryVelocity()
    {
        return dryVelocity;
    }

    /** @return per second, while it rains */
    public double wetScavenging()
    {
        return wetScavenging;
    }

    /**
     * @return the concentration below which the species in a cell is counted into the background and dropped, in micrograms per cubic metre: 1 for the acid gases, which is the clean-air background of sulfur dioxide over land (0.5 to 5, WHO 2021 air quality guidelines background), and 0.01 for the fumes and mercury, whose clean-air levels are nanograms (EPA, Air Quality Criteria for Lead and for Particulate Matter)
     */
    public double backgroundMicrograms()
    {
        return pollutant == Pollutant.ACIDITY ? AirScale.ACID_GAS_BACKGROUND_MICROGRAMS : AirScale.BACKGROUND_MICROGRAMS_PER_CUBIC_METRE;
    }

    /** @return grams per mole of the gas */
    public double molarMass()
    {
        return gas.molarMass();
    }
}
