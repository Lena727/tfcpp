package tfcpp.ecology;

/**
 * What the regional air asks the sky once an hour for each cell that holds smoke: which way and how fast the wind blows, and whether it is precipitating. The waters ask it too, for how hard it rains over a river. Free of Minecraft types, so that the transport is tested against a wind that is chosen.
 */
public interface AirWeather
{
    /**
     * Fills in the weather of a cell for the hour whose middle is this calendar tick. An implementation may keep the wind that it found in {@link AirGrid.Cell#setWind}, to fall back on when the sky cannot be asked.
     */
    void sample(AirGrid.Cell cell, long tick, Sample out);

    /**
     * Fills in only the rain over a regional cell that holds no smoke and is not in the grid, for the water: {@code out.rainIntensity} and {@code out.raining}. The wind of the answer is not to be used.
     */
    default void sampleRain(int cellX, int cellZ, long tick, Sample out)
    {
        sample(new AirGrid.Cell(cellX, cellZ), tick, out);
    }

    /** The answer. The wind is in metres per second, towards +x and towards +z. */
    final class Sample
    {
        public double windX, windZ;
        public boolean raining;
        /** TFC's rain intensity at the place, 0 to 1, which is 0 if it is not precipitating. */
        public float rainIntensity;
    }

    /** A wind that blows the same everywhere, and rain that is on or off everywhere, with the given intensity (1 if it is on and none is given). */
    record Fixed(double windX, double windZ, boolean raining, float intensity) implements AirWeather
    {
        public Fixed(double windX, double windZ, boolean raining)
        {
            this(windX, windZ, raining, raining ? 1f : 0f);
        }

        @Override
        public void sample(AirGrid.Cell cell, long tick, Sample out)
        {
            out.windX = windX;
            out.windZ = windZ;
            out.raining = raining;
            out.rainIntensity = raining ? intensity : 0f;
        }
    }
}
