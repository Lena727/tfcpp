package tfcpp.pneumatics.pipes;

/**
 * The gas laws and flow in a pipe, free of Minecraft types. Pressures here are in atmospheres unless named otherwise, volumes in cubic metres, and temperatures in kelvin.
 */
public final class PipePhysics
{
    public static final float GAS_CONSTANT = 8.314f;
    public static final float PASCALS_PER_ATM = 101325f;
    public static final float AIR_MOLAR_MASS = 28.97f;
    public static final float ROOM_TEMPERATURE = 293f;

    /** Pipe of 100 mm bore. A metre of it holds about 8 litres, and the fittings bring that to 10. */
    public static final float BORE = 0.1f;
    public static final float BORE_AREA = (float) (Math.PI * BORE * BORE / 4f);

    /** Darcy friction factor of a rough old pipe in turbulent flow. */
    private static final float FRICTION_FACTOR = 0.03f;
    /** Losses of the entry and exit of a port, in velocity heads. */
    private static final float PORT_LOSS = 1.5f;
    /** Gas cannot leave a pipe faster than the speed of sound, and the flow chokes there. */
    private static final float SPEED_OF_SOUND = 340f;

    /** Thermal expansion of water per kelvin, and its bulk modulus in atmospheres. A pipe full of liquid with nowhere to expand gains about 40 atm per kelvin of heating. */
    private static final float LIQUID_EXPANSION = 2.1e-4f;
    private static final float LIQUID_BULK_MODULUS = 21700f;
    /** Below this share of free space, gas no longer sets the pressure. */
    private static final float SOLID_LIQUID_FILL = 0.999f;

    /** Ratio of specific heats of air. */
    private static final float GAMMA = 1.4f;

    /** @return the volume of this much liquid at this temperature, in cubic metres */
    public static float liquidVolume(float litres, float temperature)
    {
        return litres / 1000f * (1f + LIQUID_EXPANSION * (temperature - ROOM_TEMPERATURE));
    }

    /** @return pressure in atm from pV = nRT, in the free space of a volume, plus the pressure of liquid with no room left to expand into */
    public static float pressure(float moles, float temperature, float volume, float liquidVolume)
    {
        final float free = Math.max(volume - liquidVolume, volume * (1f - SOLID_LIQUID_FILL));
        final float gas = moles * GAS_CONSTANT * temperature / (free * PASCALS_PER_ATM);
        final float squeezed = LIQUID_BULK_MODULUS * Math.max(0f, liquidVolume / volume - SOLID_LIQUID_FILL);
        return gas + squeezed;
    }

    /** @return moles of an ideal gas at this pressure */
    public static float moles(float pressure, float temperature, float volume)
    {
        return pressure * PASCALS_PER_ATM * volume / (GAS_CONSTANT * temperature);
    }

    /** @return the vapour pressure of water in atm, from the Antoine equation. It is 1 atm at the boiling point, and it is what makes a closed boiler dangerous. */
    public static float waterVapourPressure(float temperature)
    {
        final float celsius = Math.min(temperature - 273.15f, 374f);
        if (celsius <= 0f)
        {
            return 0f;
        }
        final double millimetres = celsius < 100f
            ? Math.pow(10, 8.07131 - 1730.63 / (233.426 + celsius))
            : Math.pow(10, 8.14019 - 1810.94 / (244.485 + celsius));
        return (float) (millimetres / 760.0);
    }

    /**
     * Speed of a fluid driven through a length of pipe by a pressure difference, from Darcy-Weisbach: dp = (f L / D + K) rho v2 / 2.
     *
     * @param pressureDifference in pascals
     * @param density            in kg/m3
     * @param length             in metres
     */
    public static float flowSpeed(float pressureDifference, float density, float length)
    {
        if (density <= 0f || pressureDifference <= 0f)
        {
            return 0f;
        }
        final float loss = FRICTION_FACTOR * length / BORE + PORT_LOSS;
        return Math.min((float) Math.sqrt(2f * pressureDifference / (density * loss)), SPEED_OF_SOUND);
    }

    /**
     * Energy in kJ released by gas expanding to atmospheric pressure. This is the isentropic work of a bursting vessel, the same that the American boiler codes use.
     *
     * @param pressure absolute, in atm
     */
    public static float burstEnergy(float pressure, float volume)
    {
        if (pressure <= 1f)
        {
            return 0f;
        }
        final float exponent = (GAMMA - 1f) / GAMMA;
        return pressure * PASCALS_PER_ATM * volume / (GAMMA - 1f) * (1f - (float) Math.pow(1f / pressure, exponent)) / 1000f;
    }

    /** The heat water takes to turn to steam, in kJ per litre. */
    public static final float WATER_LATENT_HEAT = 2257f;

    /** @return the density of steam in kg/m3 at this pressure in atm and this temperature, as an ideal gas of 18 g/mol */
    public static float steamDensity(float pressure, float temperature)
    {
        return pressure * PASCALS_PER_ATM * 0.018f / (GAS_CONSTANT * temperature);
    }

    /** @return the bursting pressure in atm of a thin wall from Barlow's formula, p = 2 S t / D, for strength in MPa and wall thickness in millimetres */
    public static float barlow(float strengthMegapascals, float wallMillimetres)
    {
        return 2f * strengthMegapascals * 1e6f * (wallMillimetres / 1000f) / BORE / PASCALS_PER_ATM;
    }

    /** @return torque in kN m of a piston of this swept volume per turn, working against this pressure difference in atm */
    public static float pistonTorque(float sweptVolume, float pressureDifference)
    {
        return Math.max(pressureDifference, 0f) * PASCALS_PER_ATM * sweptVolume / (2f * (float) Math.PI) / 1000f;
    }

    private PipePhysics() {}
}
