package tfcpp.pneumatics.gas;

import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

/**
 * Moves gas between neighbouring cells. Every transfer removes from one cell what it adds to another, so gas is only ever lost to the atmosphere, or when a cell becomes too dilute to matter.
 * <ul>
 *     <li><strong>Mixing</strong>: flow proportional to the concentration difference. Air in a room is turbulent, so this uses an eddy diffusivity near 0.1 m2/s, not the molecular 2e-5 m2/s.</li>
 *     <li><strong>Buoyancy</strong>: what drives it is the difference in density between two cells, g' = g * |rho1 - rho2| / rho. Where a denser cell lies over a lighter one they overturn at a speed proportional to sqrt(g' h), so gas pours down or rises as a body. Where a lighter cell lies over a denser one the layers are stable, and that same difference holds back mixing between them. A gas already mixed evenly into air has no difference to act on, and so never separates out again.</li>
 *     <li><strong>Pressure</strong>: a cell holding more than 1 atm pushes its contents out.</li>
 * </ul>
 */
public final class GasSimulator
{
    /** Smallest transfer that may open a new cell. At 10% mixing, gas keeps spreading into empty space down to about 5 ppm. */
    public static final float CREATE_CELL_MOLES = 2e-5f;
    public static final float REMOVE_CELL_MOLES = 1e-5f;

    private static final float MIXING_PER_SECOND = 0.1f;
    private static final float MAX_MIXING = 0.16f;
    /** At an exchange above one half the two cells trade places instead of evening out, which is a parcel of gas falling or rising as a body. A dense parcel does this, while a slight difference only stirs the air. */
    private static final float BUOYANCY_PER_SECOND = 2f;
    private static final float MAX_BUOYANCY = 0.95f;
    /** How strongly a stable density step holds back mixing across it. A difference of 0.1% in density cuts mixing to a third, which is why a layer of firedamp can lie along a roof for days, and CO2 can lie in a still cave for years. */
    private static final float STABLE_LAYER_DAMPING = 2000f;
    private static final float PRESSURE_PER_SECOND = 0.25f;
    /** A pair of cells evens out at twice this rate, so anything above 1/2 would overshoot and oscillate. */
    private static final float MAX_PRESSURE = 0.24f;

    private static final Gas[] CONDENSING = Arrays.stream(Gas.VALUES).filter(gas -> gas.depositionRate() > 0).toArray(Gas[]::new);
    private static final boolean[] MOBILE = new boolean[Gas.COUNT];

    static
    {
        for (Gas gas : Gas.VALUES)
        {
            MOBILE[gas.ordinal()] = gas.mobile();
        }
    }

    private static final int[][] SIDES = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    private final float[] flux = new float[Gas.COUNT];
    private float largestFlow;

    /** @return the largest transfer since this was last called, which tells whether anything is still happening */
    public float takeLargestFlow()
    {
        final float flow = largestFlow;
        largestFlow = 0;
        return flow;
    }

    /**
     * Steps one cell. A pair of existing cells is processed by whichever is lower in x, y and z, and a cell with no neighbour processes the transfer into that empty space itself.
     */
    public void stepCell(GasVolume volume, int x, int y, int z, float seconds)
    {
        final float[] cell = volume.cell(x, y, z);
        if (cell == null)
        {
            return;
        }

        if (!volume.isPassable(x, y, z))
        {
            evict(volume, cell, x, y, z);
            return;
        }

        final float exchange = volume.atmosphereExchangeRate(x, y, z);
        if (exchange > 0)
        {
            final float remaining = (float) Math.exp(-exchange * seconds);
            for (int i = 0; i < Gas.COUNT; i++)
            {
                if (MOBILE[i]) // Wind carries off airborne dust with the air, but not what has settled
                {
                    cell[i] *= remaining;
                }
            }
        }

        condense(volume, cell, x, y, z, seconds);

        exchange(volume, cell, x, y, z, x + 1, y, z, false, seconds);
        exchange(volume, cell, x, y, z, x, y, z + 1, false, seconds);
        exchange(volume, cell, x, y, z, x, y + 1, z, true, seconds);
        if (volume.cell(x - 1, y, z) == null) exchange(volume, cell, x, y, z, x - 1, y, z, false, seconds);
        if (volume.cell(x, y, z - 1) == null) exchange(volume, cell, x, y, z, x, y, z - 1, false, seconds);
        if (volume.cell(x, y - 1, z) == null) exchange(volume, cell, x, y, z, x, y - 1, z, true, seconds);

        // A cell is kept for as long as it holds anything: dust that has settled stays on its surfaces after the air above it is clean
        if (GasMath.mobileContent(cell) < REMOVE_CELL_MOLES && GasMath.settledContent(cell) < REMOVE_CELL_MOLES)
        {
            volume.removeCell(x, y, z);
        }
    }

    private static void condense(GasVolume volume, float[] cell, int x, int y, int z, float seconds)
    {
        for (Gas gas : CONDENSING)
        {
            final float excess = cell[gas.ordinal()] - gas.saturationMoles();
            if (excess > 0)
            {
                final float settled = excess * Math.min(gas.depositionRate() * seconds, 1f);
                cell[gas.ordinal()] -= settled;
                final Gas lands = gas.settlesInto();
                if (lands != null)
                {
                    cell[lands.ordinal()] += settled; // Dust is not lost from the cell, it lies down in it
                }
                else
                {
                    volume.deposit(x, y, z, gas, settled);
                }
            }
        }
    }

    /** A block was placed where gas was. Pushes the gas out to any side it can go, and if there is none it stays trapped in the block. */
    private void evict(GasVolume volume, float[] cell, int x, int y, int z)
    {
        for (int[] side : SIDES)
        {
            final int nx = x + side[0], ny = y + side[1], nz = z + side[2];
            if (volume.isPassable(nx, ny, nz))
            {
                @Nullable float[] to = volume.cell(nx, ny, nz);
                if (to == null)
                {
                    to = volume.createCell(nx, ny, nz);
                }
                if (to != null)
                {
                    for (int i = 0; i < Gas.COUNT; i++)
                    {
                        to[i] += cell[i];
                    }
                    volume.markChanged(nx, ny, nz);
                    volume.removeCell(x, y, z);
                    largestFlow = Float.MAX_VALUE;
                    return;
                }
            }
        }
    }

    private void exchange(GasVolume volume, float[] from, int x, int y, int z, int nx, int ny, int nz, boolean vertical, float seconds)
    {
        if (!volume.isPassable(nx, ny, nz))
        {
            return;
        }

        @Nullable float[] to = volume.cell(nx, ny, nz);

        final float mixing = vertical
            ? verticalExchange(ny > y ? from : to, ny > y ? to : from, seconds)
            : Math.min(MIXING_PER_SECOND * seconds, MAX_MIXING);
        final float totalFrom = GasMath.total(from);
        final float totalTo = to == null ? 0 : GasMath.total(to);
        final float pressureFlow = Math.min(PRESSURE_PER_SECOND * seconds, MAX_PRESSURE)
            * (Math.max(0, totalFrom - GasMath.CELL_MOLES) - Math.max(0, totalTo - GasMath.CELL_MOLES));

        float outgoing = 0;
        for (int i = 0; i < Gas.COUNT; i++)
        {
            final float there = to == null ? 0 : to[i];
            if (!MOBILE[i] || (from[i] == 0 && there == 0))
            {
                flux[i] = 0;
                continue;
            }

            float flow = mixing * (from[i] - there);
            flow += pressureFlow > 0
                ? pressureFlow * from[i] / totalFrom
                : (totalTo > 0 ? pressureFlow * there / totalTo : 0);

            flux[i] = Math.clamp(flow, -there, from[i]);
            outgoing += flux[i];
            largestFlow = Math.max(largestFlow, Math.abs(flux[i]));
        }

        if (to == null)
        {
            if (outgoing < CREATE_CELL_MOLES)
            {
                return;
            }
            to = volume.createCell(nx, ny, nz);
            if (to == null)
            {
                return;
            }
        }

        for (int i = 0; i < Gas.COUNT; i++)
        {
            from[i] -= flux[i];
            to[i] += flux[i];
        }
        if (nx >> 4 != x >> 4 || nz >> 4 != z >> 4)
        {
            volume.markChanged(nx, ny, nz);
        }
    }

    /** @return the fraction exchanged between a lower and an upper cell, either of which can hold plain air */
    private static float verticalExchange(@Nullable float[] lower, @Nullable float[] upper, float seconds)
    {
        final float mixing = Math.min(MIXING_PER_SECOND * seconds, MAX_MIXING);
        final float difference = GasMath.relativeDensity(lower) - GasMath.relativeDensity(upper);
        if (difference >= 0)
        {
            return mixing / (1 + STABLE_LAYER_DAMPING * difference);
        }
        return mixing + Math.min(BUOYANCY_PER_SECOND * (float) Math.sqrt(-difference) * seconds, MAX_BUOYANCY);
    }
}
