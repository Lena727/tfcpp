package tfcpp.pneumatics.gas;

import org.jetbrains.annotations.Nullable;

/**
 * The space gas moves through. Free of Minecraft types, so the simulation can run against a test volume.
 */
public interface GasVolume
{
    /** @return moles of each {@link Gas} in this cell, or {@code null} if it holds only air */
    @Nullable
    float[] cell(int x, int y, int z);

    /** @return a new empty cell, or {@code null} if the storage budget refuses one */
    @Nullable
    float[] createCell(int x, int y, int z);

    void removeCell(int x, int y, int z);

    /** @return {@code true} if gas can enter this position. Positions in unloaded chunks are not passable, which conserves gas across unloads. */
    boolean isPassable(int x, int y, int z);

    /** Called after gas changes in a cell belonging to storage other than the one being stepped, so that it gets saved. */
    default void markChanged(int x, int y, int z) {}

    /** @return the fraction of gas per second escaping to the atmosphere from this position */
    float atmosphereExchangeRate(int x, int y, int z);
}
