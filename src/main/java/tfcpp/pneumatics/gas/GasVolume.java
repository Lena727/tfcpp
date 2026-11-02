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

    /** Gas has condensed out of the air onto surfaces at this position. */
    default void deposit(int x, int y, int z, Gas gas, float moles) {}

    /**
     * A liquid film on the surfaces at this position gives off vapour of this gas.
     *
     * @param wanted the most that the air here can take, in moles
     * @return what was taken from the film, no more than {@code wanted}. A volume that keeps no film takes nothing.
     */
    default float evaporate(int x, int y, int z, Gas gas, float wanted)
    {
        return 0;
    }

    /** @return {@code true} if a film still lies here and will give off vapour, which keeps the cell here alive even when the air in it is clean */
    default boolean holdsFilm(int x, int y, int z)
    {
        return false;
    }

    /** @return the fraction of gas per second escaping to the atmosphere from this position */
    float atmosphereExchangeRate(int x, int y, int z);
}
