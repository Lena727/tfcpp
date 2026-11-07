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

    /** Gravity, in m/s2. */
    public static final float GRAVITY = 9.81f;
    /** The atmosphere holds up 10.33 m of water and no more: past this the pressure of the air at the surface cannot push liquid up to a pump. */
    public static final float MAX_SUCTION_METRES_OF_WATER = PASCALS_PER_ATM / (1000f * GRAVITY);
    /** One mB, the least any container can be given and a tap can give, in litres: for water and the chemical liquids 1 mB is 1 L (the scale of the physics ledger). */
    public static final float WHOLE_MILLILITRE = 1f;

    /** @return the pressure in pascals of a column of liquid of this density and this height; negative for a negative height, which is a column that would have to be lifted */
    public static float head(float density, float height)
    {
        return density * GRAVITY * height;
    }

    /**
     * @param pressure absolute pressure of the gas over the surface of the liquid, in atm
     * @param density of the liquid, in kg/m3
     * @return the greatest height in metres to which that pressure pushes this liquid up to a pump on the other side, which is a vacuum
     */
    public static float suctionLift(float pressure, float density)
    {
        return density <= 0f ? 0f : Math.max(pressure, 0f) * PASCALS_PER_ATM / (density * GRAVITY);
    }

    /**
     * Where the top of the liquid is in a network whose room is laid out in layers, one for each level, filled from the bottom as water is.
     *
     * @param layerVolumes cubic metres in each level, the lowest first
     * @param lowest       the level of the first layer, in blocks
     * @param liquidVolume cubic metres of liquid
     * @return the height of the surface in blocks, the bottom of the lowest level being its level
     */
    public static float surfaceHeight(float[] layerVolumes, float lowest, float liquidVolume)
    {
        float left = Math.max(liquidVolume, 0f);
        for (int layer = 0; layer < layerVolumes.length; layer++)
        {
            if (left <= layerVolumes[layer])
            {
                return lowest + layer + (layerVolumes[layer] > 0f ? left / layerVolumes[layer] : 0f);
            }
            left -= layerVolumes[layer];
        }
        return lowest + layerVolumes.length;
    }

    /** Saturated water, rounded from the steam tables of Cengel and Boles: temperature in C, internal energy of the liquid in kJ/kg, entropy of the liquid in kJ/(kg K). */
    private static final float[] SATURATED_CELSIUS = {100f, 120f, 140f, 160f, 180f, 200f, 220f, 240f, 260f, 280f, 300f, 320f, 340f, 360f, 374f};
    private static final float[] SATURATED_ENERGY = {418.9f, 503.5f, 588.7f, 674.8f, 761.4f, 850.6f, 940.9f, 1033.2f, 1128.4f, 1228f, 1332f, 1444.6f, 1570.3f, 1725.2f, 2029.6f};
    private static final float[] SATURATED_ENTROPY = {1.3069f, 1.5276f, 1.7391f, 1.9427f, 2.1381f, 2.3305f, 2.5174f, 2.7015f, 2.8839f, 3.067f, 3.2548f, 3.448f, 3.6594f, 3.9147f, 4.4298f};
    /** At 1 atm: the entropy of evaporation in kJ/(kg K) and the internal energy of evaporation in kJ/kg. */
    private static final float ENTROPY_OF_EVAPORATION = 6.048f, ENERGY_OF_EVAPORATION = 2087f;

    private static float interpolate(float[] values, float celsius)
    {
        final int last = SATURATED_CELSIUS.length - 1;
        if (celsius >= SATURATED_CELSIUS[last])
        {
            return values[last];
        }
        int below = 0;
        while (celsius >= SATURATED_CELSIUS[below + 1])
        {
            below++;
        }
        final float share = (celsius - SATURATED_CELSIUS[below]) / (SATURATED_CELSIUS[below + 1] - SATURATED_CELSIUS[below]);
        return values[below] + share * (values[below + 1] - values[below]);
    }

    /**
     * The flash of hot water that finds itself at 1 atm, as when a boiler bursts: the liquid held at saturation expands isentropically to the atmosphere, and the energy it gives up is the internal energy it has over the wet steam it ends as. This is the work of the bursting of Brode, and the same that the codes for boilers use. Water below its boiling point at 1 atm has none.
     *
     * @param litres      of water, each of them a kilogram
     * @param temperature of the water, in kelvin
     * @return kilojoules
     */
    public static float flashEnergy(float litres, float temperature)
    {
        final float celsius = temperature - 273.15f;
        if (litres <= 0f || celsius <= SATURATED_CELSIUS[0])
        {
            return 0f;
        }
        final float steamShare = Math.clamp((interpolate(SATURATED_ENTROPY, celsius) - SATURATED_ENTROPY[0]) / ENTROPY_OF_EVAPORATION, 0f, 1f);
        final float finalEnergy = SATURATED_ENERGY[0] + steamShare * ENERGY_OF_EVAPORATION;
        return litres * Math.max(interpolate(SATURATED_ENERGY, celsius) - finalEnergy, 0f);
    }

    /** The least flash that scalds anyone, in kJ, and the flash at which a scald takes its largest share of health. A game figure, not measured. */
    public static final float SCALD_MINIMUM_KILOJOULES = 50f;
    private static final float SCALD_FULL_KILOJOULES = 20000f;
    private static final float SCALD_LEAST_SHARE = 0.05f, SCALD_MOST_SHARE = 0.6f;

    /** @return the share of full health that steam from a flash of this energy takes from anyone it reaches */
    public static float scaldShare(float flashKilojoules)
    {
        return Math.clamp(flashKilojoules / SCALD_FULL_KILOJOULES, SCALD_LEAST_SHARE, SCALD_MOST_SHARE);
    }

    /** @return the boiling point in kelvin of water at this pressure in atm, from the Antoine equation turned round, which is also the lowest that boiling water can be cooled to by letting steam out against that pressure */
    public static float saturationTemperature(float pressure)
    {
        final double millimetres = Math.max(pressure, 1e-4f) * 760.0;
        final double celsius = millimetres < 760.0
            ? 1730.63 / (8.07131 - Math.log10(millimetres)) - 233.426
            : 1810.94 / (8.14019 - Math.log10(millimetres)) - 244.485;
        return 273.15f + (float) Math.clamp(celsius, 0.0, 374.0);
    }

    /** What a tap does with the flow it is owed: the whole millilitres to give now, and the part of one it carries over. */
    public record Drip(int whole, float credit) {}

    /**
     * A tap gives liquid in whole millilitres, and a flow of less than one a second would never give any at all if each second were counted by itself. The fraction is carried to the next second.
     *
     * @param credit    carried over from the seconds before, less than one
     * @param flow      what the pressure would drive through in this second, in litres
     * @param available litres in the pipe
     */
    public static Drip drip(float credit, float flow, float available)
    {
        final float want = credit + Math.min(Math.max(flow, 0f), Math.max(available, 0f));
        final int whole = Math.min((int) want, (int) Math.max(available, 0f));
        return new Drip(whole, Math.min(want - whole, 0.999f));
    }

    /** @return the part of a millilitre to carry over once the receiver took this many of the whole ones that were owed */
    public static float creditAfter(Drip owed, int taken)
    {
        return Math.min(Math.max(owed.credit() + owed.whole() - taken, 0f), 0.999f);
    }

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
