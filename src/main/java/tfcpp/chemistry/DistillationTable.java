package tfcpp.chemistry;

import java.util.List;
import java.util.Map;

import tfcpp.pneumatics.gas.Gas;

/**
 * What a glass retort makes of each charge, from the table of the design of stage F: the data generator writes the recipes from here, and the tests read their expected amounts from here.
 */
public final class DistillationTable
{
    /**
     * @param name           the name of the recipe
     * @param item           the powder that is charged, or {@code null} for a recipe that takes none
     * @param items          pieces of it
     * @param fluid          the liquid in the flask
     * @param litres         litres of it
     * @param reagent        a second liquid in the flask, or {@code null}
     * @param reagentLitres  litres of it
     * @param minTemperature the lowest working temperature, in Celsius
     * @param maxTemperature the highest working temperature, in Celsius
     * @param seconds        time of the batch while it is in range
     * @param output         the liquid that comes over into the receiver
     * @param outputLitres   litres of it
     * @param residue        a second liquid that stays in the flask, or {@code null}
     * @param residueLitres  litres of it
     * @param receiverWater  litres of water the receiver takes up
     * @param gas            moles of each gas the batch gives off
     * @param byproduct      the powder that is left, or {@code null}
     * @param byproducts     pieces of it
     */
    public record Row(String name, String item, int items, String fluid, float litres, String reagent, float reagentLitres, float minTemperature, float maxTemperature, float seconds,
                      String output, float outputLitres, String residue, float residueLitres, float receiverWater, Map<Gas, Float> gas, String byproduct, int byproducts)
    {
        public Distillation.Recipe recipe()
        {
            final float[] moles = new float[Gas.COUNT];
            gas.forEach((species, amount) -> moles[species.ordinal()] = amount);
            return new Distillation.Recipe(items, litres, reagentLitres, minTemperature, maxTemperature, seconds, outputLitres, residueLitres, receiverWater, moles, byproducts);
        }
    }

    private static String id(Liquid liquid)
    {
        return "tfcpp:" + liquid.id();
    }

    public static final Row CONCENTRATE_SULFURIC_ACID = new Row("concentrate_sulfuric_acid", null, 0, id(Liquid.CHAMBER_ACID), 1f, null, 0f, 280f, 350f, 60f,
        id(Liquid.SULFURIC_ACID), 0.59f, null, 0f, 0f, Map.of(), null, 0);

    public static final Row NITRIC_ACID = new Row("nitric_acid", "tfc:powder/saltpeter", 8, id(Liquid.SULFURIC_ACID), 0.23f, null, 0f, 120f, 200f, 60f,
        id(Liquid.NITRIC_ACID), 0.175f, null, 0f, 0f, Map.of(Gas.NITROGEN_OXIDES, 0.2f), "tfcpp:powder/potassium_bisulfate", 8);

    public static final Row HYDROCHLORIC_ACID = new Row("hydrochloric_acid", "tfc:powder/salt", 8, id(Liquid.SULFURIC_ACID), 0.39f, null, 0f, 150f, 300f, 60f,
        id(Liquid.HYDROCHLORIC_ACID), 0.67f, null, 0f, 0.53f, Map.of(), "tfcpp:powder/sodium_bisulfate", 8);

    public static final Row WOOD_SPIRIT = new Row("wood_spirit", null, 0, id(Liquid.PYROLIGNEOUS_LIQUOR), 1f, null, 0f, 70f, 100f, 30f,
        id(Liquid.WOOD_SPIRIT), 0.02f, "tfc:vinegar", 0.9f, 0f, Map.of(), null, 0);

    public static final Row SUPERPHOSPHATE = new Row("superphosphate", "tfcpp:powder/apatite", 8, id(Liquid.CHAMBER_ACID), 0.16f, null, 0f, 0f, 100f, 30f,
        null, 0f, null, 0f, 0f, Map.of(), "tfcpp:powder/superphosphate", 8);

    public static final Row AMMONIUM_SULFATE = new Row("ammonium_sulfate", null, 0, id(Liquid.AMMONIA_LIQUOR), 1f, id(Liquid.SULFURIC_ACID), 0.02f, 0f, 100f, 30f,
        null, 0f, null, 0f, 0f, Map.of(), "tfcpp:powder/ammonium_sulfate", 1);

    public static final List<Row> ROWS = List.of(CONCENTRATE_SULFURIC_ACID, NITRIC_ACID, HYDROCHLORIC_ACID, WOOD_SPIRIT, SUPERPHOSPHATE, AMMONIUM_SULFATE);

    private DistillationTable() {}
}
