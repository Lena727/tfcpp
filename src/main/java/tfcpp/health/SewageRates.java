package tfcpp.health;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * How much waste people and livestock make. Free of Minecraft types.
 * <p>
 * Litres a calendar day of raw sewage (urine and faeces together, in the way that manure is counted, by wet mass at a density of about 1 kg/L).
 */
public final class SewageRates
{
    /** A person: urine 1.2 L and faeces 0.15 kg a day, about 1.5 L in all (Feachem et al., Sanitation and Disease, 1983, table: adults in rural low-income settings 100 to 400 g of wet faeces and 1 to 1.3 L of urine a day; Rose et al., 2015, Crit. Rev. Environ. Sci. Technol. 45, a median of 128 g of faeces and 1.42 L of urine). */
    public static final double PERSON_LITRES_PER_DAY = 1.5;
    /** A person with dysentery: the stool of a diarrhoea is of the order of 1 L a day (a stool over 200 g counts as diarrhoea, and a severe one is several times that), with the 1.2 L of urine: 2.2 L. */
    public static final double DYSENTERY_LITRES_PER_DAY = 2.2;

    /** Litres a day of manure by kind of animal (wet manure, faeces and urine, ASABE D384.2 Manure Production and Characteristics, 2005: a dairy cow of 600 to 700 kg 68 kg a day, a beef cow about 45, a horse 500 kg 23 kg, a market pig 6 kg, a sheep 2 to 3 kg, a laying hen 0.12 kg): cattle 50, horse 25, pig 5, sheep, goat and alpaca 3, birds 0.1. Other livestock by weight: a yak 40, a musk ox 25 (smaller than cattle), a camel 25, a donkey and a mule 12 (half a horse). */
    private static final Map<String, Double> ANIMALS = Map.ofEntries(
        Map.entry("cow", 50.0), Map.entry("yak", 40.0), Map.entry("musk_ox", 25.0),
        Map.entry("horse", 25.0), Map.entry("donkey", 12.0), Map.entry("mule", 12.0), Map.entry("dromedary_camel", 25.0), Map.entry("bactrian_camel", 25.0),
        Map.entry("pig", 5.0),
        Map.entry("sheep", 3.0), Map.entry("goat", 3.0), Map.entry("alpaca", 3.0),
        Map.entry("chicken", 0.1), Map.entry("duck", 0.1), Map.entry("quail", 0.1));

    private SewageRates() {}

    /** @return litres a day for the animal of this entity type (its path, as {@code cow} for {@code tfc:cow}), or 0 for one that makes none that counts */
    public static double animalLitresPerDay(@Nullable String entityPath)
    {
        return entityPath == null ? 0.0 : ANIMALS.getOrDefault(entityPath, 0.0);
    }

    /** @return litres made in a stretch of calendar ticks at this rate per day */
    public static double litres(double perDay, long calendarTicks)
    {
        return perDay * Math.max(0L, calendarTicks) / 24_000.0;
    }

    /** The longest stretch that is counted at once, in calendar ticks: a player who sleeps through the night or comes back after a week does not leave the waste of the days that the world did not see; two days. */
    public static final long MAX_CATCH_UP_TICKS = 48_000L;
}
