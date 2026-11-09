package tfcpp.ecology;

import java.util.Locale;

/**
 * What a soil can remember. Free of Minecraft types. To add a kind, add it here with its unit and, if it breaks down in soil, give it a rate in {@link SoilConstants#decayPerDay(Pollutant)}.
 */
public enum Pollutant
{
    /** Acid that the soil has not neutralised yet, in equivalents of H+. */
    ACIDITY("eq"),
    LEAD("g"),
    ARSENIC("g"),
    MERCURY("g"),
    ZINC("g"),
    COPPER("g"),
    /** Coal tar and the phenols of gas liquor, in kilograms. */
    TAR("kg"),
    /** Raw sewage, in litres of it: the amount of pathogens is counted by what it was diluted from. */
    SEWAGE("L");

    public static final Pollutant[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final String unit;

    Pollutant(String unit)
    {
        this.unit = unit;
    }

    public String id()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the unit that totals of this kind are kept in */
    public String unit()
    {
        return unit;
    }
}
