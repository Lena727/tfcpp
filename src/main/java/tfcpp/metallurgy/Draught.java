package tfcpp.metallurgy;

import tfcpp.pneumatics.gas.Combustion;
import tfcpp.pneumatics.gas.GasMath;

/**
 * The temperature of a hearth, free of Minecraft types. Real fuels all burn at about 2000 C in the right amount of air: what sets the temperature of a hearth is the air it is given, and its fuel matters at a low supply, through how readily it burns.
 * <p>
 * A hearth loses its heat mostly by radiation, which goes as T^4, and the heat released is proportional to the air burned through. So the fourth power of the temperature, and not the temperature, is what air adds to:
 * <pre>T^4 = Tnat^4 + (Tmax^4 - Tnat^4) * min(1, air)</pre>
 * where {@code Tnat} is what the fuel gives on the draught of the chimney alone and {@code Tmax} what it gives on all the air that a bellows can push. The oxygen of the room then scales the same quantity, since a flame with less oxygen releases less heat: {@code T^4 *= s}.
 * <p>
 * This is how the blast furnace is not treated: it has a blast physics of its own, tuned in {@code FurnaceHeatMixins}.
 */
public final class Draught
{
    /** The kelvin offset of a Celsius temperature. */
    public static final float KELVIN = 273.15f;
    /** What a blast adds to burning fuel at most, in Celsius: a real forge on charcoal reaches 1400-1600 C, and more would make steel easy. */
    public static final float MOST_FROM_A_BLAST = 500f;
    /** The room, in Celsius: a fire never cools a hearth below it. */
    public static final float AMBIENT = 20f;
    /** TFC's own air of a bellows is 4 C for each tick, and {@link #MOST_FROM_A_BLAST} is reached at this many. */
    public static final int FULL_BELLOWS_AIR_TICKS = 125;
    /** One person blowing through a pipe: the lungs give about a fifth of what a bellows does. Blowers add up, to {@link #MOST_BLOWERS}: rows of men blowing into one hearth are in Egyptian tomb paintings. */
    public static final float BLOWER_AIR = 0.22f;
    public static final int MOST_BLOWERS = 4;
    /** The most that wind gives to a hearth under the open sky, as a share of full bellows. Wind across an open hearth feeds it only a little, as it cools the surface about as much as it feeds it: only a furnace built to catch the wind gains much. */
    public static final float MOST_WIND_AIR = 0.05f;
    /** A wind of this speed, in metres a second, gives the most: 10 m/s is a fresh breeze. */
    public static final float WIND_SPEED_FOR_FULL_AIR = 200f * MOST_WIND_AIR;
    /** TFC's wind vector of length 1 is a wind of this many metres a second. */
    public static final float METRES_PER_SECOND_AT_FULL_WIND = 32f;
    /** The most a rich oxygen supply can make of the heat of a flame. */
    public static final float MOST_OXYGEN_FACTOR = 1.5f;

    /** @return the air of a bellows as a share of the full supply, from the air TFC counts in ticks */
    public static float bellowsAir(int airTicks)
    {
        return Math.clamp(airTicks / (float) FULL_BELLOWS_AIR_TICKS, 0f, 1f);
    }

    /** @return the air of wind, from the speed of the wind in TFC's units. It blows only where the hearth sees the sky. */
    public static float windAir(float windVectorLength)
    {
        return Math.clamp(windVectorLength * METRES_PER_SECOND_AT_FULL_WIND / WIND_SPEED_FOR_FULL_AIR * MOST_WIND_AIR, 0f, MOST_WIND_AIR);
    }

    /** @return the air of people blowing into one hearth: each gives {@link #BLOWER_AIR}, and the lungs of more than {@link #MOST_BLOWERS} do not fit round one hearth */
    public static float pipeAir(int blowers)
    {
        return BLOWER_AIR * Math.clamp(blowers, 0, MOST_BLOWERS);
    }

    /** @return the air of the best source, as a share of full bellows. The sources do not add to each other: a gust across a hearth that bellows are already driving adds nothing, and only people blowing add up among themselves, see {@link #pipeAir}. */
    public static float totalAir(float bellows, float pipes, float wind)
    {
        return Math.min(1f, Math.max(bellows, Math.max(pipes, wind)));
    }

    /**
     * @param oxygenFraction the oxygen share of the gas the fire draws from: flame temperature depends on that fraction and not on the total pressure
     * @return 1 for the air of the sea level, 0 where oxygen is down to what keeps a fire alight
     */
    public static float oxygenFactor(float oxygenFraction)
    {
        final float extinction = Combustion.EXTINCTION_OXYGEN;
        return Math.clamp((oxygenFraction - extinction) / (GasMath.OXYGEN_IN_AIR - extinction), 0f, MOST_OXYGEN_FACTOR);
    }

    /** @return the highest temperature that fuel burning at {@code burnTemperature} reaches in a full blast, in Celsius */
    public static float fullBlastTemperature(float burnTemperature)
    {
        return burnTemperature + Math.min(MOST_FROM_A_BLAST, burnTemperature / 2f);
    }

    /**
     * @param natural        the temperature on the natural draught, in Celsius
     * @param full           the temperature on full bellows, in Celsius
     * @param air            the air in addition to the natural draught, as a share of full bellows
     * @param oxygenFactor   see {@link #oxygenFactor}
     * @return the temperature the hearth moves towards, in Celsius
     */
    public static float temperature(float natural, float full, float air, float oxygenFactor)
    {
        final double low = Math.pow(natural + KELVIN, 4);
        final double high = Math.pow(Math.max(full, natural) + KELVIN, 4);
        final double fourth = (low + (high - low) * Math.min(1f, Math.max(0f, air))) * oxygenFactor;
        return Math.max(AMBIENT, (float) (Math.pow(fourth, 0.25) - KELVIN));
    }

    private Draught() {}
}
