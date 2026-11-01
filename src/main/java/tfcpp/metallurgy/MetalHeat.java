package tfcpp.metallurgy;

import java.util.List;

/**
 * The heat that a TFC metal really takes up. TFC gives each metal a capacity chosen so that every ingot melts in about the same time, which makes bismuth harder to heat than copper when it is three times easier.
 * <p>
 * Here capacities come from density and specific heat, on one scale of energy for all of them. TFC has no way to hold a temperature while something melts, so the heat of melting is spread over the whole climb to the melting point: the total to melt an ingot comes out right, though the plateau itself does not show.
 */
public final class MetalHeat
{
    /** @param id          the name TFC gives the metal
     * @param density     g/cm3
     * @param specificHeat J/g/K
     * @param latentHeat  J/g
     * @param melt        C, as it is in this mod
     */
    public record Metal(String id, float density, float specificHeat, float latentHeat, float melt)
    {
        /** @return the heat capacity of an ingot in TFC's units, for one kelvin of the climb from room temperature to melting */
        public float ingotCapacity()
        {
            final float grams = INGOT_MILLILITRES * density;
            return grams * (specificHeat + latentHeat / (melt - ROOM_TEMPERATURE)) / JOULES_PER_UNIT;
        }

        /** @return the heat that melting an ingot takes from room temperature, in joules */
        public float joulesToMelt()
        {
            return INGOT_MILLILITRES * density * (specificHeat * (melt - ROOM_TEMPERATURE) + latentHeat);
        }

        /** @return specific heat of the liquid per millibucket in TFC's units, which TFC keeps at this share of the solid */
        public float liquidCapacity()
        {
            return ingotCapacity() * LIQUID_SHARE / MILLIBUCKETS_PER_INGOT;
        }
    }

    /** The joules in TFC's unit of heat that makes the audit's numbers agree: copper's own capacity of 2.857 against its real 345 J/K for 100 mL. */
    public static final float JOULES_PER_UNIT = 121f;
    private static final float INGOT_MILLILITRES = 100f;
    private static final float MILLIBUCKETS_PER_INGOT = 100f;
    private static final float ROOM_TEMPERATURE = 20f;
    /** TFC's own ratio of a molten ingot's capacity to a solid one. */
    private static final float LIQUID_SHARE = 0.3f;

    public static final Metal COPPER = new Metal("copper", 8.96f, 0.385f, 205f, 1080f);
    public static final Metal BISMUTH = new Metal("bismuth", 9.78f, 0.122f, 52f, 270f);
    public static final Metal TIN = new Metal("tin", 7.29f, 0.227f, 59f, 230f);
    /** Not in {@link #METALS}, as that lists what TFC has data for. */
    public static final Metal LEAD = new Metal("lead", 11.34f, 0.129f, 23f, 327f);
    public static final Metal WROUGHT_IRON = new Metal("wrought_iron", 7.87f, 0.449f, 247f, 1535f);

    private static final float STEEL_DENSITY = 7.85f, STEEL_SPECIFIC_HEAT = 0.49f, STEEL_LATENT_HEAT = 250f;

    /** Bronze and the rest are alloys, whose values are estimated from what they are made of. TFC's colored steels do not exist, and count as the steel they are made from. */
    public static final List<Metal> METALS = List.of(
        COPPER, BISMUTH, TIN, WROUGHT_IRON,
        new Metal("zinc", 7.14f, 0.388f, 112f, 420f),
        new Metal("silver", 10.49f, 0.235f, 105f, 961f),
        new Metal("gold", 19.3f, 0.129f, 64f, 1060f),
        new Metal("nickel", 8.91f, 0.444f, 298f, 1453f),
        new Metal("bronze", 8.8f, 0.38f, 180f, 950f),
        new Metal("bismuth_bronze", 8.9f, 0.36f, 170f, 985f),
        new Metal("black_bronze", 9.5f, 0.33f, 160f, 1070f),
        new Metal("brass", 8.5f, 0.38f, 168f, 930f),
        new Metal("rose_gold", 15f, 0.2f, 90f, 960f),
        new Metal("sterling_silver", 10.4f, 0.24f, 110f, 890f),
        new Metal("cast_iron", 7.2f, 0.46f, 200f, 1200f),
        new Metal("pig_iron", 7.2f, 0.46f, 200f, 1200f),
        new Metal("steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1500f),
        new Metal("high_carbon_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1450f),
        new Metal("weak_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1540f),
        new Metal("weak_blue_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1540f),
        new Metal("weak_red_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1540f),
        new Metal("black_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1485f),
        new Metal("blue_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1540f),
        new Metal("red_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1540f),
        new Metal("high_carbon_black_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1540f),
        new Metal("high_carbon_blue_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1540f),
        new Metal("high_carbon_red_steel", STEEL_DENSITY, STEEL_SPECIFIC_HEAT, STEEL_LATENT_HEAT, 1540f)
    );

    private MetalHeat() {}
}
