package tfcpp.chemistry;

import java.util.Map;

/**
 * The sulfur in solid fuel, which leaves a fire as sulfur dioxide: S + O2 -> SO2, one mole of gas for each mole of sulfur. A piece of fuel weighs 3 kg, so 1.5 percent of sulfur is 45 g, or 1.4 mol.
 * <p>
 * Lignite char concentrates about half of the lignite's sulfur into less than half of its mass, so a piece of it holds a little more than a piece of lignite. Wood and charcoal hold none that counts. Coal brings it from its pyrite and organic sulfur, and coke keeps about two thirds of it, as the rest left in the coke oven gas.
 */
public final class FuelSulfur
{
    private static final Map<String, Float> SULFUR_DIOXIDE_MOLES = Map.of(
        "minecraft:coal", 1.4f,
        "tfc:ore/bituminous_coal", 1.4f,
        "tfc:ore/lignite", 0.9f,
        "tfcpp:lignite_char", 1.0f,
        "tfcpp:coke", 0.9f
    );

    /**
     * @param itemId the registry id of the fuel, such as {@code tfc:ore/lignite}
     * @return moles of sulfur dioxide that one piece gives when it burns
     */
    public static float sulfurDioxideMoles(String itemId)
    {
        return SULFUR_DIOXIDE_MOLES.getOrDefault(itemId, 0f);
    }

    private FuelSulfur() {}
}
