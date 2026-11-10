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
    /** Antimony, from the fume of tetrahedrite and of its smelting: as toxic as arsenic at a third of the dose. */
    ANTIMONY("g"),
    NICKEL("g"),
    /** Coal tar and the phenols of gas liquor, in kilograms. */
    TAR("kg"),
    /** Raw sewage, in litres of it: the amount of pathogens is counted by what it was diluted from. */
    SEWAGE("L"),
    /** The part of the sewage that came from people who were shedding Shigella, in litres of it: a share of {@link #SEWAGE}, which dies out with it, and carries the pathogens at the concentration of an ill person's stool and not the endemic one. Kept apart for each disease, since a person with dysentery does not shed typhoid. */
    SHED_DYSENTERY("L"),
    /** As {@link #SHED_DYSENTERY}, for people shedding Salmonella Typhi. */
    SHED_TYPHOID("L");

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

    /** @return {@code true} for the kinds that are sewage or a part of it: they die out and run off as sewage does */
    public boolean isSewage()
    {
        return this == SEWAGE || this == SHED_DYSENTERY || this == SHED_TYPHOID;
    }

    /** @return the unit that totals of this kind are kept in */
    public String unit()
    {
        return unit;
    }
}
