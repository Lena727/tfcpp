package tfcpp.chemistry;

/**
 * Burning sulfur to sulfur dioxide, free of Minecraft types: S + O2 -> SO2. The burner takes one mole of oxygen for each mole of sulfur and gives one of SO2, and the heat of 297 kJ per mole of sulfur burned is what keeps the flame going. The nitrogen of the air goes through unchanged and is left behind as the gas of the flue.
 */
public final class SulfurBurning
{
    /** Sulfur in one powder, 50 g, which is the size of a powder in the mod. */
    public static final float GRAMS_PER_POWDER = 50f;

    /** Moles of sulfur in one powder: 50 g over the molar mass of S, 32.07 g/mol. */
    public static final float MOLES_PER_POWDER = 50f / 32.07f;

    /** Moles of sulfur that a small burner takes per second: 2 g/s over the molar mass of S, 32.07 g/mol. */
    public static final float MOLES_PER_SECOND = 2f / 32.07f;

    /** Heat released by S(s) + O2 -> SO2, which is 296.8 kJ per mole of S, rounded up. */
    public static final float HEAT_KILOJOULES_PER_MOLE = 297f;

    /**
     * Oxygen share below which the burn does not go on: the gas cannot bring enough oxygen to the sulfur to keep it burning, and the flame smoulders out. A figure for a small burner, not measured.
     */
    public static final float LEAST_OXYGEN_SHARE = 0.05f;

    /**
     * Temperature at which sulfur lights by itself: it autoignites at about 232 C, and 250 C is that with a little to spare, 523.15 K. Cooler than this, a burner must be lit by a flame; once it burns, its own heat keeps it going.
     */
    public static final float AUTOIGNITION_KELVIN = 523.15f;

    /** Share of nitrogen in dry air, which is the rest of the air. */
    private static final float NITROGEN_IN_AIR = 0.7905f;

    /** What one stretch of burning gave. */
    public record Result(float sulfurBurned, float sulfurDioxide, float air, float nitrogenLeftBehind, float heatKilojoules)
    {
        public static final Result NONE = new Result(0f, 0f, 0f, 0f, 0f);
    }

    private SulfurBurning()
    {
    }

    /**
     * Whether sulfur is on fire: a flame already burning goes on, a cold burner does not light itself, and a hot pipe lights it.
     *
     * @param lit whether it burned in the last step or was lit by a flame
     * @param temperatureKelvin temperature of the pipe it is in
     * @return {@code true} if the sulfur burns this step
     */
    public static boolean burning(boolean lit, float temperatureKelvin)
    {
        return lit || temperatureKelvin >= AUTOIGNITION_KELVIN;
    }

    /**
     * Burns sulfur for a stretch of time. Nothing burns below the oxygen share, and otherwise the burn is the smallest of the rate, the sulfur there is, and the oxygen there is.
     *
     * @param sulfurMoles moles of sulfur available to burn
     * @param air moles of air in the burner
     * @param gasMoles moles of all the gas in the burner
     * @param seconds time of the burn
     * @return what that gave
     */
    public static Result burn(float sulfurMoles, float air, float gasMoles, float seconds)
    {
        if (sulfurMoles <= 0f || air <= 0f || gasMoles <= 0f || seconds <= 0f)
        {
            return Result.NONE;
        }

        final float oxygen = air * ChamberProcess.OXYGEN_IN_AIR;
        if (oxygen / gasMoles < LEAST_OXYGEN_SHARE)
        {
            return Result.NONE;
        }

        final float burned = Math.min(Math.min(MOLES_PER_SECOND * seconds, sulfurMoles), oxygen); // One mole of O2 for each mole of S
        final float airUsed = burned / ChamberProcess.OXYGEN_IN_AIR;
        return new Result(burned, burned, airUsed, airUsed * NITROGEN_IN_AIR, burned * HEAT_KILOJOULES_PER_MOLE);
    }
}
