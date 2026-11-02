package tfcpp.chemistry;

/**
 * The sand bath of a laboratory, free of Minecraft types. A tray of sand between a fire and a glass vessel levels the heat out: the deeper the sand, the more of the fire's excess over the room it holds back, so that glass sees a few hundred degrees from a fire of a thousand.
 */
public final class SandBath
{
    /** The temperature of the room, to which the sand gives the rest of the heat: 20 C. */
    public static final float AMBIENT = 20f;

    public static final int MIN_DEPTH = 1, MAX_DEPTH = 8;

    /**
     * Heat that passes through the sand falls off with its thickness, in the manner of a wall: the drop across the layer from the fire to the vessel is {@code depth} parts to every one that reaches the vessel.
     *
     * @param fire  the temperature of the fire below, in Celsius
     * @param depth the depth of the sand, from 1 to 8
     * @return the temperature of the sand under the vessel, in Celsius. A fire no hotter than the room gives nothing.
     */
    public static float passedUp(float fire, int depth)
    {
        if (fire <= AMBIENT)
        {
            return fire;
        }
        final int clamped = Math.clamp(depth, MIN_DEPTH, MAX_DEPTH);
        return AMBIENT + (fire - AMBIENT) / (1 + clamped);
    }

    private SandBath() {}
}
