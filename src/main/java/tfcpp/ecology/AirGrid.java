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
    /** ug/m3: a cell whose annual mean of SO2 is below this and which holds nothing is forgotten (half the clean-air background of SO2). */
    static final double ANNUAL_KEEP = 0.5;
    /** The critical levels of annual SO2 for conifers and broadleaves, ug/m3 (UNECE): see ForestHealth. */
    static final double[] EXCESS_LEVELS = {ForestHealth.SO2_CRITICAL_CONIFER, ForestHealth.SO2_CRITICAL_BROADLEAF};

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
        /** Annual mean of the SO2 concentration in ug/m3, an exponential moving average with a time constant of one calendar year, as of {@link #annualHour}; and the first hour it was above zero. UNECE critical levels are annual means. */
        double annualSo2;
        long annualHour = Long.MIN_VALUE, annualSince = Long.MIN_VALUE;
        /** Integrals of the excess of the annual mean over 10 and over 20 ug/m3, in ug/m3 hours, as of {@link #annualHour}. */
        final double[] excess = new double[2];
        private double so2Mark;
        private long so2MarkStep = -1L;

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

        /** @return the annual mean of SO2 in ug/m3 as of this calendar hour: it falls by e per TFC year after the last update, for the cell held no air in the hours between */
        public double annualSo2(long hour)
        {
            if (annualHour == Long.MIN_VALUE || annualSo2 <= 0.0)
            {
                return 0.0;
            }
            return annualSo2 * Math.exp(-Math.max(0L, hour - annualHour) / AirScale.hoursPerYear());
        }

        /** @return the hour the annual mean first rose above zero in this cell, or {@code Long.MIN_VALUE} */
        public long annualSince()
        {
            return annualSince;
        }

        /**
         * @return the integral over time, in ug/m3 times hours since the cell began to keep it, of the excess of the annual mean over the critical level of conifers (10 ug/m3) or of broadleaves (20), as of this hour. The part since the last update is the closed form of the exponential decay of the mean. A soil takes the difference of two readings and has the exact mean excess over the gap, whatever happened in it.
         */
        public double excessIntegral(boolean conifer, long hour)
        {
            if (annualHour == Long.MIN_VALUE)
            {
                return 0.0;
            }
            final int i = conifer ? 0 : 1;
            return excess[i] + decayExcess(annualSo2, Math.max(0L, hour - annualHour), EXCESS_LEVELS[i], AirScale.hoursPerYear());
        }

        /** The integral of max(0, A - c) for A decaying exponentially from a0 with time constant tau over g hours. */
        static double decayExcess(double a0, double g, double c, double tau)
        {
            if (!(a0 > c) || !(g > 0.0))
            {
                return 0.0;
            }
            final double above = tau * Math.log(a0 / c); // Hours until the mean is down to c
            final double m = Math.min(g, above);
            return a0 * tau * -Math.expm1(-m / tau) - c * m;
        }

        /** The integral of max(0, A - c) over one hour for A going in a line from a to b. */
        static double lineExcess(double a, double b, double c)
        {
            final double hi = Math.max(a, b), lo = Math.min(a, b);
            if (!(hi > c))
            {
                return 0.0;
            }
            if (lo >= c)
            {
                return 0.5 * (a + b) - c;
            }
            final double share = (hi - c) / (hi - lo);
            return 0.5 * (hi - c) * share;
        }

        /** Takes the mean concentration of the hour that ends at the start of {@code hour} into the annual mean and its integrals. An hour that is already in is not counted twice. */
        public void recordHour(long hour, double micrograms)
        {
            if (annualHour == Long.MIN_VALUE)
            {
                if (!(micrograms > 0.0))
                {
                    return;
                }
                annualHour = hour - 1L;
                annualSince = hour - 1L;
            }
            if (hour <= annualHour)
            {
                return;
            }
            final double tau = AirScale.hoursPerYear();
            final long quiet = hour - annualHour - 1L; // Hours with nothing recorded: the mean only decays
            final double before = annualSo2 * Math.exp(-quiet / tau);
            final double after = before * Math.exp(-1.0 / tau) + micrograms * -Math.expm1(-1.0 / tau);
            for (int i = 0; i < EXCESS_LEVELS.length; i++)
            {
                excess[i] += decayExcess(annualSo2, quiet, EXCESS_LEVELS[i], tau) + lineExcess(before, after, EXCESS_LEVELS[i]);
            }
            annualSo2 = after;
            annualHour = hour;
        }

        /** Sets the annual mean as it was saved. */
        void restoreAnnual(double value, long hour, long since, double excessConifer, double excessBroadleaf)
        {
            annualSo2 = value;
            annualHour = hour;
            annualSince = since;
            excess[0] = excessConifer;
            excess[1] = excessBroadleaf;
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
        private boolean forgettable(long hour)
        {
            if (hasAir || hasAnchor || annualSo2(hour == Long.MIN_VALUE ? annualHour : hour) >= ANNUAL_KEEP)
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
    /** The calendar hour that the grid was last stepped or swept to; what the lazy annual means are read at when a cell is looked at for forgetting. */
    private long clockHour = Long.MIN_VALUE;
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
        final AirSpecies so2 = AirSpecies.SULFUR_DIOXIDE;
        for (Cell cell : active)
        {
            cell.so2Mark = cell.microgramsPerCubicMetre(so2);
            cell.so2MarkStep = stepId;
        }
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
        final long hourIndex = Math.floorDiv(middleTick, AirScale.TICKS_PER_HOUR) + 1L;
        clockHour = hourIndex;
        for (Cell cell : touched)
        {
            final double start = cell.so2MarkStep == stepId ? cell.so2Mark : 0.0;
            cell.recordHour(hourIndex, 0.5 * (start + cell.microgramsPerCubicMetre(so2)));
        }
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

    /**
     * Forgets, for the whole map, the cells that hold no air, have no anchor, whose annual mean of SO2 has fallen under the floor and whose fall is not worth keeping. Run once a day of the calendar: a cell that smoke has left is looked at by no step, and without this it would stay as long as the world does.
     *
     * @return how many cells were forgotten
     */
    public int sweep(long hour)
    {
        clockHour = Math.max(clockHour, hour);
        final ArrayList<Cell> gone = new ArrayList<>();
        for (Cell cell : cells.values())
        {
            if (cell.forgettable(hour))
            {
                gone.add(cell);
            }
        }
        for (Cell cell : gone)
        {
            for (Pollutant pollutant : Pollutant.VALUES)
            {
                scattered[pollutant.ordinal()] += cell.cumulative[pollutant.ordinal()] * AirScale.CELL_AREA;
            }
            cells.remove(key(cell.x, cell.z));
        }
        return gone.size();
    }

    /** Forgets the cells among these that hold nothing worth keeping, and counts their fall as scattered. */
    private void forget(ArrayList<Cell> candidates)
    {
        for (Cell cell : candidates)
        {
            if (cell.forgettable(clockHour) && cells.get(key(cell.x, cell.z)) == cell)
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
