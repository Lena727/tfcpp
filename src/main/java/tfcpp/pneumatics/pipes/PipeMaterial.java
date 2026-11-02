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
    /** Fired clay is proof against nearly everything, but it is brittle and its joints are weak. */
    CERAMIC(1.2f, 15f, 1400f),
    COPPER(70f, 4f, 700f),
    /** Lead is weak and creeps under load, but it is proof against sulfuric acid, which is why chamber acid was made in lead. It softens at about 150 C. */
    LEAD(5f, 6f, 423f),
    WROUGHT_IRON(180f, 4f, 900f),
    STEEL(350f, 4f, 1000f);

    /** Joints sealed with hemp and tallow. These are what give way first, however strong the metal is, until rubber gaskets. */
    public static final float JOINT_LIMIT_ATM = 10f;

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

    /** @return the pressure above atmospheric this holds, in atm */
    public float rating(float integrity, float temperature)
    {
        return temperature > maximumTemperature ? 0f : Math.min(JOINT_LIMIT_ATM, PipePhysics.barlow(strength, wall) * integrity);
    }

    /** @return share of the wall lost per calendar day in this gas, pure */
    public float corrosion(Gas gas)
    {
        return switch (this)
        {
            case WOOD -> switch (gas)
            {
                case CHLORINE, SULFUR_DIOXIDE -> 0.1f;
                default -> 0f;
            };
            case CERAMIC -> 0f; // Only hydrofluoric acid attacks it, which comes much later
            case COPPER -> switch (gas)
            {
                case AMMONIA -> 0.5f; // Dissolves as a complex, and cracks brass and copper under stress
                case HYDROGEN_SULFIDE -> 0.2f;
                case CHLORINE -> 0.3f;
                case SULFUR_DIOXIDE -> 0.05f;
                default -> 0f;
            };
            case WROUGHT_IRON, STEEL -> switch (gas)
            {
                case CHLORINE -> 0.05f; // Dry chlorine is held in steel: it is water that makes it attack
                case SULFUR_DIOXIDE -> 0.1f;
                case HYDROGEN_SULFIDE -> 0.1f;
                case CARBON_DIOXIDE -> 0.005f;
                default -> 0f;
            };
            // Sulfur dioxide takes nothing from lead, as the film of lead sulfate it makes protects the metal, and so the chamber process ran in lead
            case LEAD -> switch (gas)
            {
                case CHLORINE -> 0.1f;
                case HYDROGEN_SULFIDE -> 0.05f;
                default -> 0f;
            };
        };
    }

    /** @return share of the wall lost per calendar day in a full pipe of this liquid, by its id */
    public float corrosion(String liquid)
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
            case "minecraft:water", "tfc:spring_water", "tfc:limewater", "tfc:lye" -> this == WROUGHT_IRON ? 0.002f : this == STEEL ? 0.002f : 0f;
            default -> 0f;
        };
    }
}
