package tfcpp.ecology;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

/**
 * The regional air: a sparse grid of cells 128 blocks on a side, each holding moles of the species of {@link AirSpecies}, moved by wind and mixing and brought down by dry and wet deposition. Free of Minecraft types.
 * <p>
 * Every step is conservative: what leaves one cell enters another or comes down, and what is too dilute to keep is moved to a background counter, so that for each species {@code emitted + returned = in the air + deposited + background + taken}, where taken is what the gas grid of rooms drew out of the air and returned is the part of what rooms let out that they had taken before, so that {@code emitted} is real production. The fall is counted by pollutant as well: what species deposited is on cells or in {@code scattered}, the fall of cells that were forgotten.
 * <p>
 * Transport is donor-cell upwind: in a substep a cell passes to its downwind neighbours the share {@code |v| dt / (C * 128)} of its content, along x and along z by the components of the wind, and evens out with each of its four neighbours at the rate of the eddy diffusivity. Everything is computed from the content at the start of the substep and the substep is cut so that no cell gives away more than half of what it holds, which keeps every amount positive (the Courant number stays at or below 1/2).
 * <p>
 * The fringe of a plume is not allowed to grow cells without end: what would go into an empty neighbour in less than the background floor goes to the background at once, and at the end of each step the cells that held air and now hold nothing, and whose fall is negligible, are forgotten. Only the cells touched in the step are looked at, never the whole map.
 */
public final class AirGrid
{
    /** The most of a cell that may leave it in one substep. */
    public static final double MAX_OUTFLOW = 0.5;
    public static final int MAX_SUBSTEPS = 256;

    private static final double[] ZERO = new double[AirSpecies.COUNT];
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final double[] FLOOR = new double[AirSpecies.COUNT];

    /**
     * The fall per square metre under which a cell that holds no air is forgotten, and its fall goes to {@code scattered}. The share that a chunk would claim of it is below these: about a twentieth of the smallest yearly critical load of acid (0.02 eq/m2), and a small part of a thousandth of the metals in a square metre of ordinary soil. In the unit of the pollutant.
     */
    private static final double[] NEGLIGIBLE_FALL = new double[Pollutant.COUNT];

    static
    {
        for (AirSpecies species : AirSpecies.VALUES)
        {
            FLOOR[species.ordinal()] = AirScale.molesFor(species.backgroundMicrograms(), species.molarMass());
        }
        NEGLIGIBLE_FALL[Pollutant.ACIDITY.ordinal()] = 1e-3;
        NEGLIGIBLE_FALL[Pollutant.LEAD.ordinal()] = 1e-3;
        NEGLIGIBLE_FALL[Pollutant.ZINC.ordinal()] = 1e-3;
        NEGLIGIBLE_FALL[Pollutant.ARSENIC.ordinal()] = 1e-4;
        NEGLIGIBLE_FALL[Pollutant.ANTIMONY.ordinal()] = 1e-4;
        NEGLIGIBLE_FALL[Pollutant.MERCURY.ordinal()] = 1e-5;
    }

    /** One regional cell. */
    public static final class Cell
    {
        public final int x, z;
        final double[] air = new double[AirSpecies.COUNT];
        private final double[] scratch = new double[AirSpecies.COUNT];
        /** Everything of each pollutant that has come down on a square metre of the cell since the world began, in the unit of the pollutant. Never goes down: soil takes its share from it. */
        final double[] cumulative = new double[Pollutant.COUNT];
        boolean hasAir;
        private long snapshot;
        private long weatherStep = -1L;
        private boolean raining;
        double windX, windZ;
        long windDay = Long.MIN_VALUE;
        boolean hasAnchor;
        int anchorX, anchorY, anchorZ;
        /** The average rainfall and its yearly variance at the cell, found once and saved with it. */
        boolean hasRain;
        float rainAverage, rainVariance;

        Cell(int x, int z)
        {
            this.x = x;
            this.z = z;
        }

        public double moles(AirSpecies species)
        {
            return air[species.ordinal()];
        }

        public double microgramsPerCubicMetre(AirSpecies species)
        {
            return AirScale.microgramsPerCubicMetre(air[species.ordinal()], species.molarMass());
        }

        /** @return moles of the species in one cubic metre of the cell */
        public double molesPerCubicMetre(AirSpecies species)
        {
            return air[species.ordinal()] / AirScale.CELL_VOLUME;
        }

        public double cumulative(Pollutant pollutant)
        {
            return cumulative[pollutant.ordinal()];
        }

        public boolean hasAir()
        {
            return hasAir;
        }

        public boolean hasWind()
        {
            return windDay != Long.MIN_VALUE;
        }

        public double windX()
        {
            return windX;
        }

        public double windZ()
        {
            return windZ;
        }

        public long windDay()
        {
            return windDay;
        }

        /** Remembers the wind that was last found for the cell, in metres per second, and the calendar day it was found on. */
        public void setWind(double windX, double windZ, long day)
        {
            this.windX = windX;
            this.windZ = windZ;
            this.windDay = day;
        }

        public boolean hasAnchor()
        {
            return hasAnchor;
        }

        public int anchorX()
        {
            return anchorX;
        }

        public int anchorY()
        {
            return anchorY;
        }

        public int anchorZ()
        {
            return anchorZ;
        }

        public void setAnchor(int x, int y, int z)
        {
            hasAnchor = true;
            anchorX = x;
            anchorY = y;
            anchorZ = z;
        }

        public boolean hasRain()
        {
            return hasRain;
        }

        public float rainAverage()
        {
            return rainAverage;
        }

        public float rainVariance()
        {
            return rainVariance;
        }

        public void setRain(float average, float variance)
        {
            hasRain = true;
            rainAverage = average;
            rainVariance = variance;
        }

        /** @return whether the cell holds no air, has no anchor that the wind of a stack is found by, and a fall that is not worth keeping */
        private boolean forgettable()
        {
            if (hasAir || hasAnchor)
            {
                return false;
            }
            for (Pollutant pollutant : Pollutant.VALUES)
            {
                if (cumulative[pollutant.ordinal()] > 0.0 && cumulative[pollutant.ordinal()] >= NEGLIGIBLE_FALL[pollutant.ordinal()])
                {
                    return false;
                }
            }
            return true;
        }
    }

    private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
    private final ArrayList<Cell> active = new ArrayList<>();
    /** The cells that held air at any time of the step that is running: the only ones that can be forgotten at its end. */
    private final ArrayList<Cell> touched = new ArrayList<>();
    private boolean stepping;
    final double[] emitted = new double[AirSpecies.COUNT];
    final double[] taken = new double[AirSpecies.COUNT];
    final double[] returned = new double[AirSpecies.COUNT];
    final double[] deposited = new double[AirSpecies.COUNT];
    final double[] depositedWet = new double[AirSpecies.COUNT];
    final double[] background = new double[AirSpecies.COUNT];
    /** Fall of forgotten cells, in the unit of each pollutant for a whole cell, that is on no cell any more. */
    final double[] scattered = new double[Pollutant.COUNT];
    private final AirWeather.Sample sample = new AirWeather.Sample();
    private final double[] flux = new double[AirSpecies.COUNT];
    private long stepId, snapshotId;
    private long stepTick;
    private @Nullable AirWeather weather;

    public static long key(int x, int z)
    {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    @Nullable
    public Cell cellAt(int x, int z)
    {
        return cells.get(key(x, z));
    }

    public Cell cell(int x, int z)
    {
        return cells.computeIfAbsent(key(x, z), k -> new Cell(x, z));
    }

    public Collection<Cell> cells()
    {
        return cells.values();
    }

    public int cellCount()
    {
        return cells.size();
    }

    /** @return how many cells hold something in the air now */
    public int activeCount()
    {
        return active.size();
    }

    /** Puts a cell that was read from storage into the grid. */
    void restore(Cell cell)
    {
        cells.put(key(cell.x, cell.z), cell);
        if (cell.hasAir)
        {
            active.add(cell);
        }
    }

    /** Forgets everything, for tests that need a grid with nothing in it. */
    public void clear()
    {
        cells.clear();
        active.clear();
        for (double[] totals : new double[][] {emitted, taken, returned, deposited, depositedWet, background, scattered})
        {
            Arrays.fill(totals, 0.0);
        }
    }

    /** Forgets one cell and everything about it, for tests that need a place with nothing in its air. */
    public void remove(int x, int z)
    {
        final @Nullable Cell cell = cells.remove(key(x, z));
        if (cell != null && cell.hasAir)
        {
            active.remove(cell);
        }
    }

    private void markAir(Cell cell)
    {
        if (!cell.hasAir)
        {
            cell.hasAir = true;
            active.add(cell);
            if (stepping)
            {
                touched.add(cell);
            }
        }
    }

    // --- Moving in and out -----------------------------------------------------------------------------------------------------------------------------------------------

    /** Puts gas into the air of a cell: smoke from a chimney. */
    public void emit(int x, int z, AirSpecies species, double moles)
    {
        if (!(moles > 0.0))
        {
            return;
        }
        final Cell cell = cell(x, z);
        cell.air[species.ordinal()] += moles;
        emitted[species.ordinal()] += moles;
        markAir(cell);
    }

    /**
     * Gas that a room let out into the open air. What a room had drawn from the air before counts as returned, and only the rest as emitted, so that the production of the world is not inflated by gas that went into a pit and out again.
     */
    public void emitFromRoom(int x, int z, AirSpecies species, double moles)
    {
        if (!(moles > 0.0))
        {
            return;
        }
        final int i = species.ordinal();
        final double back = Math.min(moles, Math.max(0.0, taken[i] - returned[i]));
        final Cell cell = cell(x, z);
        cell.air[i] += moles;
        returned[i] += back;
        emitted[i] += moles - back;
        markAir(cell);
    }

    /** @return what was taken out of the air of a cell, no more than {@code wanted} and no more than there is: gas that flowed into a room */
    public double take(Cell cell, AirSpecies species, double wanted)
    {
        final int i = species.ordinal();
        final double got = Math.min(Math.max(wanted, 0.0), cell.air[i]);
        if (got > 0.0)
        {
            cell.air[i] -= got;
            taken[i] += got;
        }
        return got;
    }

    /** Puts everything that is in the air into the ground of the cell it is over. Used when the calendar has jumped further than the air can be followed. */
    public void depositInPlace()
    {
        final ArrayList<Cell> were = new ArrayList<>(active);
        for (Cell cell : active)
        {
            for (AirSpecies species : AirSpecies.VALUES)
            {
                final int i = species.ordinal();
                final double amount = cell.air[i];
                if (amount > 0.0)
                {
                    cell.air[i] = 0.0;
                    land(cell, species, amount, 0.0);
                }
            }
            cell.hasAir = false;
        }
        active.clear();
        forget(were);
    }

    private void land(Cell cell, AirSpecies species, double amount, double wetShare)
    {
        final int i = species.ordinal();
        deposited[i] += amount;
        depositedWet[i] += amount * wetShare;
        cell.cumulative[species.pollutant().ordinal()] += amount * species.amountPerMole() / AirScale.CELL_AREA;
    }

    // --- Totals ----------------------------------------------------------------------------------------------------------------------------------------------------------

    public double emitted(AirSpecies species)
    {
        return emitted[species.ordinal()];
    }

    public double taken(AirSpecies species)
    {
        return taken[species.ordinal()];
    }

    public double returned(AirSpecies species)
    {
        return returned[species.ordinal()];
    }

    public double deposited(AirSpecies species)
    {
        return deposited[species.ordinal()];
    }

    public double depositedWet(AirSpecies species)
    {
        return depositedWet[species.ordinal()];
    }

    public double background(AirSpecies species)
    {
        return background[species.ordinal()];
    }

    /** @return what the fall of forgotten cells came to, in the unit of the pollutant for whole cells */
    public double scatteredFall(Pollutant pollutant)
    {
        return scattered[pollutant.ordinal()];
    }

    /** @return everything of a pollutant that has fallen and lies on a cell now, in its unit for whole cells */
    public double fallOnCells(Pollutant pollutant)
    {
        double sum = 0.0;
        for (Cell cell : cells.values())
        {
            sum += cell.cumulative[pollutant.ordinal()] * AirScale.CELL_AREA;
        }
        return sum;
    }

    /** @return the fall that the species counted, less what lies on cells and what was scattered: zero but for rounding */
    public double fallImbalance(Pollutant pollutant)
    {
        double counted = 0.0;
        for (AirSpecies species : AirSpecies.VALUES)
        {
            if (species.pollutant() == pollutant)
            {
                counted += deposited[species.ordinal()] * species.amountPerMole();
            }
        }
        return counted - fallOnCells(pollutant) - scattered[pollutant.ordinal()];
    }

    /** @return moles of the species in the air of every cell */
    public double inAir(AirSpecies species)
    {
        double sum = 0.0;
        for (Cell cell : active)
        {
            sum += cell.air[species.ordinal()];
        }
        return sum;
    }

    /** @return emitted and returned minus everything accounted for, which is zero but for rounding */
    public double imbalance(AirSpecies species)
    {
        return emitted(species) + returned(species) - inAir(species) - deposited(species) - background(species) - taken(species);
    }

    // --- One hour --------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Lets one hour of the calendar pass.
     *
     * @param weather asked once for each cell that holds air
     * @param middleTick the calendar tick in the middle of the hour, which is where the weather is read
     * @param metresPerBlock the scale of the map, see {@link AirScale#metresPerBlock}
     * @return how many substeps it took
     */
    public int step(AirWeather weather, long middleTick, double metresPerBlock)
    {
        if (active.isEmpty())
        {
            return 0;
        }
        this.weather = weather;
        this.stepTick = middleTick;
        stepId++;
        touched.clear();
        touched.addAll(active);
        stepping = true;
        final double crossing = 1.0 / (metresPerBlock * AirScale.CELL_BLOCKS);
        final double mixing = AirScale.mixingPerSecond(metresPerBlock);

        double remaining = AirScale.SECONDS_PER_HOUR;
        int substeps = 0;
        while (remaining > 1e-9 && substeps < MAX_SUBSTEPS)
        {
            double fastest = 0.0;
            for (Cell cell : active)
            {
                ensureWeather(cell);
                fastest = Math.max(fastest, (Math.abs(cell.windX) + Math.abs(cell.windZ)) * crossing + 4.0 * mixing);
            }
            double dt = fastest > 0.0 ? Math.min(remaining, MAX_OUTFLOW / fastest) : remaining;
            if (substeps == MAX_SUBSTEPS - 1)
            {
                dt = remaining;
            }
            transport(dt, crossing, mixing);
            remaining -= dt;
            substeps++;
        }
        deposit();
        drop();
        stepping = false;
        forget(touched);
        touched.clear();
        this.weather = null;
        return substeps;
    }

    private void ensureWeather(Cell cell)
    {
        if (cell.weatherStep != stepId)
        {
            cell.weatherStep = stepId;
            sample.windX = 0.0;
            sample.windZ = 0.0;
            sample.raining = false;
            weather.sample(cell, stepTick, sample);
            cell.windX = sample.windX;
            cell.windZ = sample.windZ;
            cell.raining = sample.raining;
        }
    }

    private void transport(double dt, double crossing, double mixing)
    {
        snapshotId++;
        for (Cell cell : active)
        {
            System.arraycopy(cell.air, 0, cell.scratch, 0, AirSpecies.COUNT);
            cell.snapshot = snapshotId;
        }
        final int count = active.size();
        for (int index = 0; index < count; index++)
        {
            final Cell cell = active.get(index);
            final double[] old = cell.scratch;
            final double alongX = Math.abs(cell.windX) * crossing, alongZ = Math.abs(cell.windZ) * crossing;
            final int towardsX = cell.windX > 0.0 ? 1 : -1, towardsZ = cell.windZ > 0.0 ? 1 : -1;
            for (int[] direction : DIRECTIONS)
            {
                final double carried = direction[0] != 0 ? (direction[0] == towardsX ? alongX : 0.0) : (direction[1] == towardsZ ? alongZ : 0.0);
                final @Nullable Cell neighbour = cells.get(key(cell.x + direction[0], cell.z + direction[1]));
                final double[] there = neighbour != null && neighbour.snapshot == snapshotId ? neighbour.scratch : ZERO;
                final boolean empty = neighbour == null || !neighbour.hasAir;
                boolean any = false;
                for (int i = 0; i < AirSpecies.COUNT; i++)
                {
                    double moved = dt * (carried * old[i] + Math.max(0.0, mixing * (old[i] - there[i])));
                    if (empty && moved > 0.0 && moved < FLOOR[i])
                    {
                        // Into empty air, and less than would be kept: it is background, and makes no cell
                        cell.air[i] -= moved;
                        background[i] += moved;
                        moved = 0.0;
                    }
                    flux[i] = moved;
                    any |= moved > 0.0;
                }
                if (!any)
                {
                    continue;
                }
                final Cell to = neighbour != null ? neighbour : cell(cell.x + direction[0], cell.z + direction[1]);
                markAir(to);
                for (int i = 0; i < AirSpecies.COUNT; i++)
                {
                    cell.air[i] -= flux[i];
                    to.air[i] += flux[i];
                }
            }
        }
    }

    private void deposit()
    {
        for (Cell cell : active)
        {
            ensureWeather(cell);
            for (AirSpecies species : AirSpecies.VALUES)
            {
                final int i = species.ordinal();
                if (cell.air[i] <= 0.0)
                {
                    continue;
                }
                final double dry = species.dryVelocity() / AirScale.MIXING_HEIGHT;
                final double wet = cell.raining ? species.wetScavenging() : 0.0;
                final double rate = dry + wet;
                final double amount = cell.air[i] * -Math.expm1(-rate * AirScale.SECONDS_PER_HOUR);
                cell.air[i] -= amount;
                land(cell, species, amount, rate > 0.0 ? wet / rate : 0.0);
            }
        }
    }

    /** Moves what is too dilute to the background. */
    private void drop()
    {
        int kept = 0;
        for (int index = 0; index < active.size(); index++)
        {
            final Cell cell = active.get(index);
            boolean any = false;
            for (int i = 0; i < AirSpecies.COUNT; i++)
            {
                if (cell.air[i] < FLOOR[i])
                {
                    background[i] += cell.air[i];
                    cell.air[i] = 0.0;
                }
                any |= cell.air[i] > 0.0;
            }
            cell.hasAir = any;
            if (any)
            {
                active.set(kept++, cell);
            }
        }
        while (active.size() > kept)
        {
            active.remove(active.size() - 1);
        }
    }

    /** Forgets the cells among these that hold nothing worth keeping, and counts their fall as scattered. */
    private void forget(ArrayList<Cell> candidates)
    {
        for (Cell cell : candidates)
        {
            if (cell.forgettable() && cells.get(key(cell.x, cell.z)) == cell)
            {
                for (Pollutant pollutant : Pollutant.VALUES)
                {
                    scattered[pollutant.ordinal()] += cell.cumulative[pollutant.ordinal()] * AirScale.CELL_AREA;
                }
                cells.remove(key(cell.x, cell.z));
            }
        }
    }
}
