package tfcpp.pneumatics.gas;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

/**
 * A flame running through flammable gas, and the pressure it creates.
 * <p>
 * The flame burns every connected flammable cell by stoichiometry. Burning in a closed space raises the pressure by (gamma - 1) * Q / V, with gamma about 1.25 in hot combustion gas: for a stoichiometric mixture of methane this gives close to the 7 to 8 atm measured in closed vessels.
 * <p>
 * The pressure then spreads out through open space, shell by shell, the released energy being shared with all the volume reached so far. This gives the behaviour of real explosions without further assumptions: a blast stays strong far along a narrow gallery, fades quickly in a large cavern, and is hardest in a sealed room.
 * <p>
 * What remains is afterdamp: nitrogen, CO2 and, where oxygen ran short, CO. In real mine disasters this has killed more miners than the blast. Water from the flame condenses as the gas cools, so it is not tracked.
 */
public final class Deflagration
{
    /** gamma - 1 for hot combustion gas, with heat in kJ and volume in cubic metres. */
    private static final float PRESSURE_KPA_PER_KJ = 0.25f;
    /** Below this a blast no longer does anything: the weakest windows break from about 3 kPa. */
    private static final float MINIMUM_KPA = 3f;
    /** A shell open to the sky relieves far more pressure than its volume suggests, as the gas can leave upward. */
    private static final float OPEN_SKY_VOLUME = 16f;
    /** Even a lean flame quenches at its edges, leaving about this share of its carbon as CO. */
    private static final float FROZEN_CARBON_MONOXIDE = 0.02f;

    /** Heat per mole of carbon burned, half of it to CO and half to CO2: the sum of what it really makes, 252 kJ, or 20.9 kJ/g of carbon. */
    private static final float DUST_KJ_PER_CARBON_MOLE = 0.5f * (Combustion.CO_HEAT_KJ_PER_MOL + Combustion.CO2_HEAT_KJ_PER_MOL);
    /** Coal dust burns as carbon: 1 kg is 1000 g / 12.011 g/mol, or 83 mol. */
    private static final float CARBON_PER_DUST_UNIT = 83f;
    /** Half of the carbon burns to CO (half a mole of O2 each) and half to CO2 (one mole each): three quarters of a mole of O2 per mole of carbon. */
    private static final float OXYGEN_PER_DUST_CARBON = 0.75f;
    /** Coal in the settled dust above which a blast's own wind lifts it. At or below this the rock dust wins: a gallery needs at least 65% of incombustible dust, and exactly 65% is enough. */
    public static final float COAL_SHARE_THAT_RISES = 0.35f;
    /** A mixture made up as exactly 65% rock must not be lifted because of the rounding of a float. */
    public static final float COAL_SHARE_TOLERANCE = 1e-4f;

    private static final float METHANE_TO_CO_KJ = 519f, CO_TO_CO2_KJ = 283f, HYDROGEN_KJ = 242f, HYDROGEN_SULFIDE_KJ = 518f, AMMONIA_KJ = 317f;
    private static final int[][] SIDES = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    public record Shock(int x, int y, int z, float kilopascals, boolean burned) {}

    public record Result(int burnedCells, float kilojoules, List<Shock> shocks) {}

    /** Burns one cell, leaving the products in it. @return the heat released in kJ */
    public static float burn(float[] cell)
    {
        final float oxygenBefore = GasMath.oxygenFraction(cell) * GasMath.CELL_MOLES;
        float oxygen = oxygenBefore;
        float heat = 0;

        // 2 H2 + O2 -> 2 H2O. Hydrogen has by far the fastest flame, so it takes its oxygen first.
        final float hydrogen = Math.min(cell[Gas.HYDROGEN.ordinal()], oxygen / 0.5f);
        cell[Gas.HYDROGEN.ordinal()] -= hydrogen;
        oxygen -= hydrogen * 0.5f;
        heat += hydrogen * HYDROGEN_KJ;

        // 2 H2S + 3 O2 -> 2 SO2 + 2 H2O
        final float sulfide = Math.min(cell[Gas.HYDROGEN_SULFIDE.ordinal()], oxygen / 1.5f);
        cell[Gas.HYDROGEN_SULFIDE.ordinal()] -= sulfide;
        cell[Gas.SULFUR_DIOXIDE.ordinal()] += sulfide;
        oxygen -= sulfide * 1.5f;
        heat += sulfide * HYDROGEN_SULFIDE_KJ;

        // 4 NH3 + 3 O2 -> 2 N2 + 6 H2O
        final float ammonia = Math.min(cell[Gas.AMMONIA.ordinal()], oxygen / 0.75f);
        cell[Gas.AMMONIA.ordinal()] -= ammonia;
        cell[Gas.FLUE_NITROGEN.ordinal()] += ammonia * 0.5f;
        oxygen -= ammonia * 0.75f;
        heat += ammonia * AMMONIA_KJ;

        // 2 CH4 + 3 O2 -> 2 CO + 4 H2O comes first in a real flame, and CO only burns out if there is oxygen left for it
        final float methane = Math.min(cell[Gas.METHANE.ordinal()], oxygen / 1.5f);
        cell[Gas.METHANE.ordinal()] -= methane;
        cell[Gas.CARBON_MONOXIDE.ordinal()] += methane;
        oxygen -= methane * 1.5f;
        heat += methane * METHANE_TO_CO_KJ;

        // 2 CO + O2 -> 2 CO2
        final float carbonMonoxide = Math.max(0, Math.min(cell[Gas.CARBON_MONOXIDE.ordinal()] - FROZEN_CARBON_MONOXIDE * methane, oxygen / 0.5f));
        cell[Gas.CARBON_MONOXIDE.ordinal()] -= carbonMonoxide;
        cell[Gas.CARBON_DIOXIDE.ordinal()] += carbonMonoxide;
        oxygen -= carbonMonoxide * 0.5f;
        heat += carbonMonoxide * CO_TO_CO2_KJ;

        // Coal dust burns last, with the oxygen the gases left. Carbon goes half to CO and half to CO2, and the CO is not burned out: in a dust explosion there is rarely oxygen left to do it, and this is why the afterdamp of one is deadly. What the oxygen does not reach stays in the air as dust.
        final float dust = cell[Gas.COAL_DUST.ordinal()];
        if (dust > 0 && oxygen > 0)
        {
            final float carbon = Math.min(dust * CARBON_PER_DUST_UNIT, oxygen / OXYGEN_PER_DUST_CARBON);
            final float burnedDust = carbon >= dust * CARBON_PER_DUST_UNIT ? dust : carbon / CARBON_PER_DUST_UNIT;
            cell[Gas.COAL_DUST.ordinal()] -= burnedDust;
            cell[Gas.CARBON_MONOXIDE.ordinal()] += carbon * 0.5f;
            cell[Gas.CARBON_DIOXIDE.ordinal()] += carbon * 0.5f;
            oxygen -= carbon * OXYGEN_PER_DUST_CARBON;
            heat += carbon * DUST_KJ_PER_CARBON_MOLE;
        }

        // The nitrogen that came with the oxygen stays behind
        cell[Gas.FLUE_NITROGEN.ordinal()] += (oxygenBefore - oxygen) * Combustion.NITROGEN_PER_OXYGEN;
        return heat;
    }

    /**
     * The wind of a blast lifts the dust that lies ahead of the flame. Settled coal dust goes into the air if it is more than 35% of all the settled dust in the cell, and is left lying if the rock dust is 65% or more. Nothing is lost: it moves from lying to airborne.
     *
     * @return {@code true} if dust was raised
     */
    public static boolean raiseSettledCoalDust(float[] cell)
    {
        final float coal = cell[Gas.SETTLED_COAL_DUST.ordinal()];
        if (coal <= 0 || coal / (coal + cell[Gas.SETTLED_ROCK_DUST.ordinal()]) <= COAL_SHARE_THAT_RISES + COAL_SHARE_TOLERANCE)
        {
            return false;
        }
        cell[Gas.COAL_DUST.ordinal()] += coal;
        cell[Gas.SETTLED_COAL_DUST.ordinal()] = 0;
        return true;
    }

    /** Lights the gas at this position. Nothing happens unless the cell is flammable, after any dust lying in it has been raised. */
    public static Result run(GasVolume volume, int x, int y, int z, int maxBurnedCells, int maxShockedCells)
    {
        final float[] start = volume.cell(x, y, z);
        if (start == null)
        {
            return new Result(0, 0, List.of());
        }
        if (raiseSettledCoalDust(start))
        {
            volume.markChanged(x, y, z);
        }
        if (!Flammability.isFlammable(start))
        {
            return new Result(0, 0, List.of());
        }

        final LongOpenHashSet seen = new LongOpenHashSet();
        final List<int[]> burned = new ArrayList<>();
        seen.add(BlockPos.asLong(x, y, z));
        burned.add(new int[] {x, y, z});

        float heat = 0, openVolume = 0;
        for (int i = 0; i < burned.size(); i++)
        {
            final int[] pos = burned.get(i);
            final float[] cell = volume.cell(pos[0], pos[1], pos[2]);
            heat += burn(cell);
            volume.markChanged(pos[0], pos[1], pos[2]);
            openVolume += cellVolume(volume, pos[0], pos[1], pos[2]);

            for (int[] side : SIDES)
            {
                if (burned.size() >= maxBurnedCells)
                {
                    break;
                }
                final int nx = pos[0] + side[0], ny = pos[1] + side[1], nz = pos[2] + side[2];
                final float[] next = volume.cell(nx, ny, nz);
                if (next == null || !volume.isPassable(nx, ny, nz))
                {
                    continue;
                }
                // The flame is preceded by wind, which lifts the dust ahead of it. Dust that is raised and does not burn stays in the air.
                if (raiseSettledCoalDust(next))
                {
                    volume.markChanged(nx, ny, nz);
                }
                if (Flammability.isFlammable(next) && seen.add(BlockPos.asLong(nx, ny, nz)))
                {
                    burned.add(new int[] {nx, ny, nz});
                }
            }
        }

        final List<Shock> shocks = new ArrayList<>();
        final float flamePressure = PRESSURE_KPA_PER_KJ * heat / openVolume;
        for (int[] pos : burned)
        {
            shocks.add(new Shock(pos[0], pos[1], pos[2], flamePressure, true));
        }

        List<int[]> shell = burned;
        while (!shell.isEmpty() && shocks.size() < maxShockedCells)
        {
            final List<int[]> next = new ArrayList<>();
            for (int[] pos : shell)
            {
                for (int[] side : SIDES)
                {
                    final int nx = pos[0] + side[0], ny = pos[1] + side[1], nz = pos[2] + side[2];
                    if (volume.isPassable(nx, ny, nz) && seen.add(BlockPos.asLong(nx, ny, nz)))
                    {
                        next.add(new int[] {nx, ny, nz});
                        openVolume += cellVolume(volume, nx, ny, nz);
                    }
                }
            }

            final float pressure = PRESSURE_KPA_PER_KJ * heat / openVolume;
            if (pressure < MINIMUM_KPA)
            {
                break;
            }
            for (int[] pos : next)
            {
                shocks.add(new Shock(pos[0], pos[1], pos[2], pressure, false));
            }
            shell = next;
        }
        return new Result(burned.size(), heat, shocks);
    }

    /**
     * The shock of a vessel bursting at this position: nothing burns, but the same shells of open space share the energy.
     *
     * @param maximumKilopascals the pressure it burst at, which it cannot exceed however small the first shell
     */
    public static Result blast(GasVolume volume, int x, int y, int z, float kilojoules, float maximumKilopascals, int maxShockedCells)
    {
        final LongOpenHashSet seen = new LongOpenHashSet();
        final List<int[]> first = new ArrayList<>();
        first.add(new int[] {x, y, z});
        seen.add(BlockPos.asLong(x, y, z));

        float openVolume = cellVolume(volume, x, y, z);
        final List<Shock> shocks = new ArrayList<>();
        shocks.add(new Shock(x, y, z, Math.min(PRESSURE_KPA_PER_KJ * kilojoules / openVolume, maximumKilopascals), false));

        List<int[]> shell = first;
        while (!shell.isEmpty() && shocks.size() < maxShockedCells)
        {
            final List<int[]> next = new ArrayList<>();
            for (int[] pos : shell)
            {
                for (int[] side : SIDES)
                {
                    final int nx = pos[0] + side[0], ny = pos[1] + side[1], nz = pos[2] + side[2];
                    if (volume.isPassable(nx, ny, nz) && seen.add(BlockPos.asLong(nx, ny, nz)))
                    {
                        next.add(new int[] {nx, ny, nz});
                        openVolume += cellVolume(volume, nx, ny, nz);
                    }
                }
            }
            final float pressure = Math.min(PRESSURE_KPA_PER_KJ * kilojoules / openVolume, maximumKilopascals);
            if (pressure < MINIMUM_KPA)
            {
                break;
            }
            for (int[] pos : next)
            {
                shocks.add(new Shock(pos[0], pos[1], pos[2], pressure, false));
            }
            shell = next;
        }
        return new Result(0, kilojoules, shocks);
    }

    private static float cellVolume(GasVolume volume, int x, int y, int z)
    {
        return volume.atmosphereExchangeRate(x, y, z) > 0 ? OPEN_SKY_VOLUME : 1f;
    }

    private Deflagration() {}
}
