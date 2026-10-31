package tfcpp.mechanics;

/**
 * Torque on a shaft, in kN m. TFC's network only knows a speed, so anything could be driven by anything for free. Here each source has a torque that falls as it speeds up, and the shaft runs where that meets the load.
 * <p>
 * A wheel or a sail is pushed by the difference between the speed of the stream and its own, so its torque falls in a straight line from the stall torque at rest to nothing at free speed. Power is torque times speed, which makes the best working point half of free speed, as Smeaton measured on model water wheels in 1759.
 */
public final class ShaftPhysics
{
    public static final float TICKS_PER_SECOND = 20f;

    /** Power at the best working point of an undershot wheel at full flow. Mills of this kind gave between 2 and 5 hp. */
    private static final float WATER_WHEEL_KILOWATTS = 3f;
    /** The speed TFC gives a wheel at full flow, one turn in four seconds. */
    private static final float WATER_WHEEL_FULL_SPEED = 2f * (float) Math.PI / 4f;
    /**
     * Power of a five-bladed mill at the wind that TFC turns it at full speed, about 8 m/s. With blades of 6 m the swept area is 113 m2, and at a power coefficient of 0.15 for a traditional mill, 1/2 rho A v3 Cp gives about 5 kW.
     */
    private static final float WINDMILL_KILOWATTS_PER_BLADE = 1f;

    /** Friction of a shaft in its bearings, which adds up at about 1 to 2% of a working load per bearing. */
    public static final float BEARING_TORQUE = 0.01f;
    /** A millstone takes about 2 kW at the speed of a wheel, and TFC has no gearing. */
    public static final float QUERN_TORQUE = 1.3f;
    /** A helve hammer of 250 kg, lifted half a metre once a turn, takes 1.2 kJ a turn. */
    public static final float TRIP_HAMMER_TORQUE = 0.2f;
    public static final float POWER_LOOM_TORQUE = 0.2f;
    public static final float BELLOWS_TORQUE = 0.2f;

    private static final float WATER_KILONEWTONS_PER_CUBIC_METRE_METRE = 9.81f;
    /** Volume a pump lifts per turn of its crank, a piston of about 30 cm by 1.4 m. */
    public static final float PUMP_CUBIC_METRES_PER_TURN = 0.1f;

    /** Fans move air in proportion to their speed: about 4 m3/s at the full speed of a water wheel, for a small wooden mine fan. */
    private static final float FAN_CUBIC_METRES_PER_RADIAN = 2.5f;
    /** Pressure, and so torque, goes with the square of speed. This takes about 1 kW at the full speed of a water wheel. */
    private static final float FAN_TORQUE_PER_SPEED_SQUARED = 0.26f;

    /** @return the torque at rest of a wheel with this free speed, in radians per tick. Force on a blade goes with the square of the flow. */
    public static float waterWheelStallTorque(float freeSpeedPerTick)
    {
        final float flow = Math.abs(freeSpeedPerTick) * TICKS_PER_SECOND / WATER_WHEEL_FULL_SPEED;
        return stallTorque(WATER_WHEEL_KILOWATTS * flow * flow * flow, flow * WATER_WHEEL_FULL_SPEED);
    }

    /**
     * @param freeSpeedPerTick the speed in this wind, in radians per tick
     * @param calmSpeedPerTick the speed of this number of blades in TFC's reference wind
     */
    public static float windmillStallTorque(float freeSpeedPerTick, float calmSpeedPerTick, int blades)
    {
        if (calmSpeedPerTick <= 0)
        {
            return 0;
        }
        final float wind = Math.abs(freeSpeedPerTick) / calmSpeedPerTick;
        return stallTorque(WINDMILL_KILOWATTS_PER_BLADE * blades * wind * wind * wind, Math.abs(freeSpeedPerTick) * TICKS_PER_SECOND);
    }

    /** @return the stall torque for a best power at half of this free speed, in radians per second */
    private static float stallTorque(float kilowatts, float freeSpeed)
    {
        return freeSpeed <= 0 ? 0 : 4f * kilowatts / freeSpeed;
    }

    /** @return the share of its free speed a source runs at under this load, which is zero once the load exceeds what it can start against */
    public static float speedFactor(float loadTorque, float stallTorque)
    {
        return stallTorque <= 0 ? 1f : Math.clamp(1f - loadTorque / stallTorque, 0f, 1f);
    }

    /** @return the torque of lifting water this high with a pump of this stroke. The work per turn is rho g H V, over one turn of 2 pi radians. */
    public static float pumpTorque(float headMetres)
    {
        return WATER_KILONEWTONS_PER_CUBIC_METRE_METRE * Math.max(headMetres, 1f) * PUMP_CUBIC_METRES_PER_TURN / (2f * (float) Math.PI);
    }

    /** @return cubic metres pumped in this time at this speed, in radians per second */
    public static float pumpedVolume(float speed, float seconds)
    {
        return PUMP_CUBIC_METRES_PER_TURN * Math.abs(speed) * seconds / (2f * (float) Math.PI);
    }

    public static float fanFlow(float speed)
    {
        return FAN_CUBIC_METRES_PER_RADIAN * Math.abs(speed);
    }

    public static float fanTorque(float speed)
    {
        return FAN_TORQUE_PER_SPEED_SQUARED * speed * speed;
    }

    private ShaftPhysics() {}
}
