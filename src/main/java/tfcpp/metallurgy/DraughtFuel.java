package tfcpp.metallurgy;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * What each fuel gives on the draught of a chimney alone. Porous charcoal burns readily on natural draught; bituminous coal first has to distil its volatiles; coke is unreactive and hardly keeps alight without a blast; raw lignite carries 30-60% of water, which has to boil away first.
 * <p>
 * The highest temperature, on a full blast, is the same for every fuel to within what its energy gives, and is {@link Draught#fullBlastTemperature} of the temperature TFC has for the fuel, so it needs no table.
 * This is a table in Java and not a data map: the temperatures TFC has for each fuel are themselves written by {@code MetallurgyData} and {@code ChemistryData} from Java, and the two belong together. {@code GuideDataTest} compares the burn temperatures here with the generated data.
 */
public final class DraughtFuel
{
    private static final float SAME_TEMPERATURE = 0.5f;

    /** @param burnTemperature what TFC has for the fuel, as the device sees it, in Celsius; @param natural what the fuel gives on the natural draught, in Celsius */
    public record Fuel(String item, float burnTemperature, float natural) {}

    public static final List<Fuel> FUELS = List.of(
        new Fuel("minecraft:charcoal", 1100f, 1000f),
        new Fuel("tfcpp:lignite_char", 1100f, 950f),
        new Fuel("tfc:ore/bituminous_coal", 1200f, 950f),
        new Fuel("minecraft:coal", 1200f, 950f),
        new Fuel("tfcpp:coke", 1250f, 850f),
        new Fuel("tfc:ore/lignite", 1000f, 750f));

    /** @return the fuel of this item, or {@code null} */
    @Nullable
    public static Fuel of(String itemId)
    {
        for (Fuel fuel : FUELS)
        {
            if (fuel.item().equals(itemId))
            {
                return fuel;
            }
        }
        return null;
    }

    /**
     * @param itemId          the item of the fuel taken last, if it is known that it is the one now burning, else {@code null}
     * @param burnTemperature what TFC has for the fuel now burning
     * @return the temperature on the natural draught. With no item known, the lowest of the fuels of the table that burn at this temperature in TFC: the forge does not know which one, and does not give it the benefit of the doubt. Only if there is none, as for wood or a fuel from another mod, it burns at TFC's temperature.
     */
    public static float natural(@Nullable String itemId, float burnTemperature)
    {
        final @Nullable Fuel known = itemId == null ? null : of(itemId);
        if (known != null)
        {
            return Math.min(known.natural(), burnTemperature);
        }
        float lowest = Float.MAX_VALUE;
        for (Fuel fuel : FUELS)
        {
            if (Math.abs(fuel.burnTemperature() - burnTemperature) < SAME_TEMPERATURE)
            {
                lowest = Math.min(lowest, fuel.natural());
            }
        }
        return lowest == Float.MAX_VALUE ? burnTemperature : lowest;
    }

    private DraughtFuel() {}
}
