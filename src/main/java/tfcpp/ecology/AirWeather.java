package tfcpp.ecology;

/**
 * What the regional air asks the sky once an hour for each cell that holds smoke: which way and how fast the wind blows, and whether it is precipitating. Free of Minecraft types, so that the transport is tested against a wind that is chosen.
 */
public interface AirWeather
{
    /**
     * Fills in the weather of a cell for the hour whose middle is this calendar tick. An implementation may keep the wind that it found in {@link AirGrid.Cell#setWind}, to fall back on when the sky cannot be asked.
     */
    void sample(AirGrid.Cell cell, long tick, Sample out);

    /** The answer. The wind is in metres per second, towards +x and towards +z. */
    final class Sample
    {
        public double windX, windZ;
        public boolean raining;
    }

    /** A wind that blows the same everywhere, and rain that is on or off everywhere. */
    record Fixed(double windX, double windZ, boolean raining) implements AirWeather
    {
        @Override
        public void sample(AirGrid.Cell cell, long tick, Sample out)
        {
            out.windX = windX;
            out.windZ = windZ;
            out.raining = raining;
        }
    }
}
