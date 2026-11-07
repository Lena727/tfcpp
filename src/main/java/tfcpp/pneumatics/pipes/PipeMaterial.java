package tfcpp.pneumatics.pipes;

import java.util.Locale;

import tfcpp.pneumatics.gas.Gas;

/**
 * What a pipe is made of. Strength comes from Barlow's formula with a safety factor already in the working strength. Corrosion is in shares of the wall per calendar day, in pure gas or a full pipe of liquid, and falls off in proportion below that.
 */
public enum PipeMaterial
{
    /** Bored logs and bound staves were the water mains of old cities. They char once hot. */
    WOOD(1.5f, 30f, 450f),
    /**
     * Fired clay, the chemical stoneware of the acid works. It is proof against sulfuric, nitric and hydrochloric acid at any strength, and only hydrofluoric acid and hot strong alkali attack its glaze. It is brittle and weak in tension, and its socket joints are weaker still: the 1.2 MPa is the working strength a few MPa of breaking strength leaves after the factor of safety a brittle body needs, and gives 3.5 atm in a wall of 15 mm, which is what chemical stoneware pipe is sold for.
     */
    CERAMIC(1.2f, 15f, 1400f),
    COPPER(70f, 4f, 700f),
    /** Lead is weak and creeps under load, and it melts at 327 C and softens at about 150 C. It is proof against sulfuric acid below about 70% and below 80 C, which is why chamber acid was made in lead, and nitric acid eats it. */
    LEAD(5f, 6f, 423f),
    WROUGHT_IRON(180f, 4f, 900f),
    STEEL(350f, 4f, 1000f);

    /** Joints sealed with hemp and tallow. These are what give way first, however strong the metal is, until rubber gaskets. */
    public static final float JOINT_LIMIT_ATM = 10f;

    /** The share of the rating of a new body at which the weight of its safety valve is set: four fifths. */
    public static final float SAFETY_VALVE_SET_SHARE = 0.8f;

    /** Above this, sulfuric acid and chamber acid dissolve the film of lead sulfate that holds them off lead: 80 C. */
    public static final float HOT_ACID_KELVIN = 353f;
    /** How much faster lead corrodes under hot sulfuric acid or chamber acid than under cool, once the film is gone. A game figure, not measured. */
    public static final float HOT_ACID_FACTOR = 20f;

    /** Above this lye and other strong alkali take the glaze off stoneware: 80 C. */
    public static final float HOT_ALKALI_KELVIN = 353f;
    /** Share of a stoneware wall lost per day to lye above {@link #HOT_ALKALI_KELVIN}. A game figure: slow, but not nothing. */
    public static final float HOT_ALKALI_CERAMIC = 0.05f;

    private final float strength, wall, maximumTemperature;

    PipeMaterial(float strength, float wall, float maximumTemperature)
    {
        this.strength = strength;
        this.wall = wall;
        this.maximumTemperature = maximumTemperature;
    }

    public String id()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the material of this id, as {@link #id()} gives it, or wrought iron for one that is not known */
    public static PipeMaterial byId(String id)
    {
        for (PipeMaterial material : values())
        {
            if (material.id().equals(id))
            {
                return material;
            }
        }
        return WROUGHT_IRON;
    }

    /** @return the pressure above atmospheric this holds, in atm */
    public float rating(float integrity, float temperature)
    {
        return temperature > maximumTemperature ? 0f : Math.min(JOINT_LIMIT_ATM, PipePhysics.barlow(strength, wall) * integrity);
    }

    /**
     * A safety valve has to lift below what its own body holds, or it would never lift: the weight is set to this share of the rating of a new body, up to the pressure that the valve is made for.
     *
     * @param ceiling the gauge pressure in atm that a valve of iron is set at, and that none is set above
     * @return the gauge pressure in atm at which a safety valve with a body of this material lifts
     */
    public float safetyValveSetting(float ceiling)
    {
        return Math.min(ceiling, rating(1f, PipePhysics.ROOM_TEMPERATURE) * SAFETY_VALVE_SET_SHARE);
    }

    /** @return share of the wall lost per calendar day in this gas, pure */
    public float corrosion(Gas gas)
    {
        return switch (this)
        {
            case WOOD -> switch (gas)
            {
                case CHLORINE, SULFUR_DIOXIDE -> 0.1f;
                case NITROGEN_OXIDES -> 0.05f; // Wet oxides make nitric acid, which chars wood
                default -> 0f;
            };
            case CERAMIC -> 0f; // Only hydrofluoric acid attacks it, which comes much later
            case COPPER -> switch (gas)
            {
                case AMMONIA -> 0.5f; // Dissolves as a complex, and cracks brass and copper under stress
                case HYDROGEN_SULFIDE -> 0.2f;
                case CHLORINE -> 0.3f;
                case SULFUR_DIOXIDE -> 0.05f;
                case NITROGEN_OXIDES -> 0.1f; // Wet oxides make nitric acid, which dissolves copper
                case HYDROGEN_CHLORIDE -> 0.1f; // Dry hydrogen chloride hardly touches copper: it is the moist gas that does
                default -> 0f;
            };
            case WROUGHT_IRON, STEEL -> switch (gas)
            {
                case CHLORINE -> 0.05f; // Dry chlorine is held in steel: it is water that makes it attack
                case SULFUR_DIOXIDE -> 0.1f;
                case HYDROGEN_SULFIDE -> 0.1f;
                case CARBON_DIOXIDE -> 0.005f;
                case NITROGEN_OXIDES -> 0.05f; // Nitric acid passivates iron, so the wet oxides attack it far less than copper
                case HYDROGEN_CHLORIDE -> 0.3f; // With the damp of the air it is hydrochloric acid, and chloride keeps iron from passivating
                default -> 0f;
            };
            // Sulfur dioxide takes nothing from lead, as the film of lead sulfate it makes protects the metal, and so the chamber process ran in lead. The oxides of the chamber take nothing either.
            case LEAD -> switch (gas)
            {
                case CHLORINE -> 0.1f;
                case HYDROGEN_SULFIDE -> 0.05f;
                case HYDROGEN_CHLORIDE -> 0.05f; // Lead chloride is a poor film, but a thin one
                default -> 0f;
            };
        };
    }

    /** @return share of the wall lost per calendar day in a full pipe of this liquid, by its id, at room temperature */
    public float corrosion(String liquid)
    {
        return corrosion(liquid, PipePhysics.ROOM_TEMPERATURE);
    }

    /**
     * @param liquid the id of the liquid in the pipe
     * @param kelvin the temperature of the liquid
     * @return share of the wall lost per calendar day in a full pipe of this liquid, at that temperature
     */
    public float corrosion(String liquid, float kelvin)
    {
        return switch (liquid)
        {
            case "tfcpp:mercury" -> this == COPPER ? 1f : 0f; // Mercury dissolves copper. It was always carried in iron flasks.
            case "tfc:salt_water", "tfc:brine" -> this == WROUGHT_IRON || this == STEEL ? 0.03f : this == COPPER ? 0.005f : 0f;
            case "tfc:vinegar" -> switch (this)
            {
                case LEAD -> 0.1f; // Vinegar dissolves lead, which is what poisoned the wine of Rome
                case WROUGHT_IRON, STEEL, COPPER -> 0.05f;
                default -> 0f;
            };
            // Hot strong alkali dissolves the glaze of stoneware and then the body; cold or weak, it does nothing to it
            case "tfc:lye" -> this == WROUGHT_IRON || this == STEEL ? 0.002f : this == CERAMIC && kelvin > HOT_ALKALI_KELVIN ? HOT_ALKALI_CERAMIC : 0f;
            case "minecraft:water", "tfc:spring_water", "tfc:limewater" -> this == WROUGHT_IRON ? 0.002f : this == STEEL ? 0.002f : 0f;
            // Chamber acid is about 65% sulfuric acid. Strong sulfuric and nitric acids passivate iron, which is why steel carried oil of vitriol, and dilute acid ate it. Lead holds chamber acid under a film of sulfate.
            case "tfcpp:chamber_acid" -> switch (this)
            {
                case WOOD -> 0.5f;
                case CERAMIC -> 0f;
                case COPPER -> 0.2f;
                case WROUGHT_IRON, STEEL -> 0.5f;
                case LEAD -> leadUnderAcid(0.001f, kelvin);
            };
            case "tfcpp:sulfuric_acid" -> switch (this)
            {
                case WOOD -> 1.0f;
                case CERAMIC -> 0f;
                case COPPER -> 0.1f;
                case WROUGHT_IRON, STEEL -> 0.01f; // Strong acid passivates iron
                case LEAD -> leadUnderAcid(0.1f, kelvin);
            };
            case "tfcpp:nitric_acid" -> switch (this)
            {
                case WOOD -> 1.0f;
                case CERAMIC -> 0f;
                case COPPER -> 2.0f;
                case WROUGHT_IRON, STEEL -> 0.02f; // Strong nitric acid passivates iron
                case LEAD -> 0.5f;
            };
            case "tfcpp:hydrochloric_acid" -> switch (this)
            {
                case WOOD -> 0.02f;
                case CERAMIC -> 0f;
                case COPPER -> 0.1f;
                case WROUGHT_IRON, STEEL -> 2.0f; // Chloride ions keep iron from passivating
                case LEAD -> 0.1f;
            };
            default -> 0f;
        };
    }

    /**
     * Lead under sulfuric acid and chamber acid: the film of lead sulfate holds, until the acid is hot enough to dissolve the film.
     *
     * @param rate the rate of cool acid
     * @param kelvin the temperature of the acid
     * @return the rate of the acid at that temperature
     */
    private static float leadUnderAcid(float rate, float kelvin)
    {
        return kelvin > HOT_ACID_KELVIN ? rate * HOT_ACID_FACTOR : rate;
    }
}
