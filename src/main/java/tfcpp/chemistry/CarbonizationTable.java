package tfcpp.chemistry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import tfcpp.core.LocalTime;
import tfcpp.pneumatics.gas.Gas;

/**
 * What a coke oven makes of each material, from the figures of dry distillation: the data generator writes the recipes from here, and the tests check them against the conservation of mass.
 * <p>
 * A piece of coal, lignite or coke weighs 3 kg, a log 5 kg, and so does a piece of charcoal.
 */
public final class CarbonizationTable
{
    private static final float HOUR = LocalTime.SECONDS_PER_DAY / 24f;
    private static final double COAL_PIECE_KILOGRAMS = 3.0, LOG_KILOGRAMS = 5.0;

    /**
     * @param name                the name of the recipe
     * @param pieceKilograms      mass of one piece of charge
     * @param solidId             the item that the solid residue is
     * @param solidPerPiece       pieces of residue for each piece of charge
     * @param solidPieceKilograms mass of one piece of residue
     * @param tarPerPiece         litres of tar for each piece of charge
     * @param liquorPerPiece      litres of liquor for each piece of charge
     * @param gas                 moles of each gas for each piece of charge
     */
    public record Row(String name, double pieceKilograms, float temperature, float seconds, String solidId, double solidPerPiece, double solidPieceKilograms,
                      Liquid tar, float tarPerPiece, Liquid liquor, float liquorPerPiece, Map<Gas, Float> gas)
    {
        public CarbonizationBatch.Yield yield()
        {
            final float[] moles = new float[Gas.COUNT];
            gas.forEach((species, amount) -> moles[species.ordinal()] = amount);
            return new CarbonizationBatch.Yield(seconds, temperature, solidPerPiece, tarPerPiece, liquorPerPiece, moles);
        }

        /** @return kilograms of gas that one piece of charge gives */
        public double gasKilograms()
        {
            double grams = 0.0;
            for (Map.Entry<Gas, Float> entry : gas.entrySet())
            {
                grams += entry.getValue() * entry.getKey().molarMass();
            }
            return grams / 1000.0;
        }

        /** @return kilograms of everything that one piece of charge becomes, with the solid at this yield */
        public double outputKilograms(double solidYield)
        {
            return solidYield * solidPieceKilograms + tarPerPiece * tar.kilogramsPerLitre() + liquorPerPiece * liquor.kilogramsPerLitre() + gasKilograms();
        }

        public double outputKilograms()
        {
            return outputKilograms(solidPerPiece);
        }
    }

    /** Coke from bituminous coal at the temperature of a coke oven, 950 to 1050 C. Seven tenths of a piece of coke for each piece of coal is as much as the tar, liquor and gas leave room for: at 0.75 more mass would come out than went in. */
    public static final Row COAL = new Row("coal", COAL_PIECE_KILOGRAMS, 950f, 18f * HOUR, "tfcpp:coke", 0.70, 3.0,
        Liquid.COAL_TAR, 0.10f, Liquid.AMMONIA_LIQUOR, 0.30f, gas(
            Gas.HYDROGEN, 21f, Gas.METHANE, 14f, Gas.CARBON_MONOXIDE, 3f, Gas.CARBON_DIOXIDE, 1f,
            Gas.FLUE_NITROGEN, 2f, Gas.AMMONIA, 0.3f, Gas.HYDROGEN_SULFIDE, 0.2f));

    /** Lignite chars below 700 C and does not cake, which is why its char is a poor fuel and no blast furnace charge. It is wet and gives more water than coal, but its nitrogen is half that of coal and the liquor holds about 0.76 mol of ammonia a litre, so 0.4 L is the same share of the nitrogen (about 17%) as the 0.30 L of coal. */
    public static final Row LIGNITE = new Row("lignite", COAL_PIECE_KILOGRAMS, 700f, 18f * HOUR, "tfcpp:lignite_char", 0.4, 3.0,
        Liquid.COAL_TAR, 0.24f, Liquid.AMMONIA_LIQUOR, 0.4f, gas(
            Gas.CARBON_DIOXIDE, 7f, Gas.CARBON_MONOXIDE, 3f, Gas.METHANE, 3f, Gas.HYDROGEN, 2f));

    /** Wood chars from 280 C, and is done by 400 C. About 30 percent of a log's mass remains as charcoal, and the rest is mostly water and carbon dioxide. */
    public static final Row WOOD = new Row("wood", LOG_KILOGRAMS, 400f, 12f * HOUR, "minecraft:charcoal", 0.5, 3.0,
        Liquid.WOOD_TAR, 0.25f, Liquid.PYROLIGNEOUS_LIQUOR, 2.0f, gas(
            Gas.CARBON_DIOXIDE, 16.5f, Gas.CARBON_MONOXIDE, 10f, Gas.METHANE, 3f, Gas.HYDROGEN, 0.6f));

    public static final List<Row> ROWS = List.of(COAL, LIGNITE, WOOD);

    private static Map<Gas, Float> gas(Object... pairs)
    {
        final Map<Gas, Float> gases = new EnumMap<>(Gas.class);
        for (int i = 0; i < pairs.length; i += 2)
        {
            gases.put((Gas) pairs[i], (Float) pairs[i + 1]);
        }
        return gases;
    }

    private CarbonizationTable() {}
}
