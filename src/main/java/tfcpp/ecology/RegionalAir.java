package tfcpp.ecology;

import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tfcpp.core.TickProfiler;
import tfcpp.pneumatics.gas.Atmosphere;
import tfcpp.pneumatics.gas.Gas;

/**
 * The regional air of one level, saved with it: smoke that has gone up and out of the reach of rooms, carried over the map by wind and brought down on the ground as acid and metals. See {@link AirGrid} for the transport and {@link AirScale} for the scales.
 * <p>
 * Nothing here ticks a block or loads a chunk. Once an hour of TFC's calendar the grid takes a step over the cells that hold something, and falls leave a counter on the cell that soil takes its share from when the soil is touched or looked at.
 */
public final class RegionalAir extends SavedData
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionalAir.class);
    private static final String NAME = "tfcpp_regional_air";
    private static final Factory<RegionalAir> FACTORY = new Factory<>(RegionalAir::new, RegionalAir::load);

    /** The pollutants that fall out of the air. */
    public static final Pollutant[] FALLING = {Pollutant.ACIDITY, Pollutant.LEAD, Pollutant.ARSENIC, Pollutant.MERCURY, Pollutant.ZINC, Pollutant.ANTIMONY};

    /** How far out from a flue to look for the ground that its wall stands on, in blocks. */
    private static final int WALL_REACH = 8;

    private final AirGrid grid = new AirGrid();
    private double carbonMoles;
    private long lastHour = Long.MIN_VALUE;
    private long lastSweepDay = Long.MIN_VALUE;
    private long forcedHours;
    private @Nullable AirWeather weather;
    private @Nullable LevelAirWeather skyWeather;
    private int temperatureScale = -1;
    private final AirWeather.Sample sample = new AirWeather.Sample();

    public static RegionalAir get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public AirGrid grid()
    {
        return grid;
    }

    /** @return the annual mean of sulfur dioxide in ug/m3 over this block, as of the calendar now; 0 where nothing was ever in the air */
    public double annualSo2(int x, int z)
    {
        final @Nullable AirGrid.Cell cell = grid.cellAt(x >> 7, z >> 7);
        return cell == null ? 0.0 : cell.annualSo2(AirCadence.hourOf(Calendars.SERVER.getCalendarTicks()));
    }

    /** @return the present concentration of sulfur dioxide in ug/m3 over this block */
    public double so2Now(int x, int z)
    {
        final @Nullable AirGrid.Cell cell = grid.cellAt(x >> 7, z >> 7);
        return cell == null ? 0.0 : cell.microgramsPerCubicMetre(AirSpecies.SULFUR_DIOXIDE);
    }

    /** @return moles of carbon dioxide, and of monoxide that turns to it within months, that have gone into the open air of this level */
    public double carbonMoles()
    {
        return carbonMoles;
    }

    /** @return how many hourly steps were replaced by putting the smoke into the ground in place, because the calendar had jumped by more than a week */
    public long forcedHours()
    {
        return forcedHours;
    }

    /** Forgets everything, for tests that need a world with nothing in its air. */
    public void reset()
    {
        grid.clear();
        carbonMoles = 0.0;
        lastHour = Long.MIN_VALUE;
        forcedHours = 0L;
        weather = null;
        setDirty();
    }

    /** Forgets the cell over a block, for tests that need a place with nothing in its air or on its ground. */
    public void clearCellAt(int x, int z)
    {
        grid.remove(x >> 7, z >> 7);
        setDirty();
    }

    /** Replaces the sky, for tests that need a wind and rain that they choose. {@code null} gives TFC's back. */
    public void setWeather(@Nullable AirWeather weather)
    {
        this.weather = weather;
    }

    public AirWeather weather(ServerLevel level)
    {
        if (weather != null)
        {
            return weather;
        }
        if (skyWeather == null)
        {
            skyWeather = new LevelAirWeather(level);
        }
        return skyWeather;
    }

    /** @return metres of the real world to a block of the map in this level */
    public double metresPerBlock(ServerLevel level)
    {
        if (temperatureScale < 0)
        {
            temperatureScale = LevelAirWeather.temperatureScale(level);
        }
        return AirScale.metresPerBlock(temperatureScale);
    }

    // --- Inputs ----------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Gas that has come out of an outlet under open sky, at the rim of its flue if it has one. What falls out of the air goes to the regional cell, carbon dioxide and monoxide go to the counter, and the rest is gone. Hydrogen sulfide goes up as sulfur dioxide and chlorine as hydrogen chloride. A tall stack puts its smoke into the next cell downwind.
     *
     * @param foot the height where the gas came out of the device, which is where the flue starts if there is one
     * @param rim the height of the top of the flue, equal to {@code foot} if there is none
     */
    public void emit(ServerLevel level, int x, int foot, int rim, int z, float[] gases)
    {
        boolean carries = false;
        for (int i = 0; i < gases.length; i++)
        {
            final float moles = gases[i];
            if (!(moles > 0f))
            {
                continue;
            }
            final Gas gas = Gas.VALUES[i];
            if (gas == Gas.CARBON_DIOXIDE || gas == Gas.CARBON_MONOXIDE)
            {
                carbonMoles += moles;
                setDirty();
            }
            else if (AirSpecies.emittedAs(gas) != null)
            {
                carries = true;
            }
        }
        if (!carries)
        {
            return;
        }
        int cx = x >> 7, cz = z >> 7;
        final AirGrid.Cell source = grid.cell(cx, cz);
        final int ground = groundBesideFlue(level, x, foot, rim, z);
        source.setAnchor(x, ground, z); // Kept even if the stack's smoke goes elsewhere: the wind of the cell is found there
        if (AirScale.isTallStack(rim, ground))
        {
            weather(level).sample(source, Calendars.SERVER.getCalendarTicks(), sample);
            final int[] offset = AirScale.downwind(sample.windX, sample.windZ);
            cx += offset[0];
            cz += offset[1];
        }
        for (int i = 0; i < gases.length; i++)
        {
            final @Nullable AirSpecies species = AirSpecies.emittedAs(Gas.VALUES[i]);
            if (species != null && gases[i] > 0f)
            {
                grid.emit(cx, cz, species, gases[i] * AirSpecies.yieldOf(Gas.VALUES[i]));
            }
        }
        setDirty();
    }

    /**
     * @return the height of the ground that a flue stands on: for each of the four sides, the heightmap in the first column outside its walls (found by walking out while the blocks are solid at the height just under the rim), and the highest of those. The heightmap in the flue's own column holds the stack itself, and a ring down a slope would measure a forge on a ledge against the valley, so the ground is read right outside the wall and the highest side is taken. Only chunks that are loaded are asked.
     */
    private static int groundBesideFlue(ServerLevel level, int x, int foot, int rim, int z)
    {
        int highest = Integer.MIN_VALUE;
        final int wallY = rim > foot ? rim - 1 : rim;
        for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
        {
            for (int d = 1; d <= WALL_REACH; d++)
            {
                final int bx = x + side[0] * d, bz = z + side[1] * d;
                final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
                if (chunk == null)
                {
                    break;
                }
                final BlockState state = chunk.getBlockState(new BlockPos(bx, wallY, bz));
                if (d < WALL_REACH && state.isCollisionShapeFullBlock(level, new BlockPos(bx, wallY, bz)))
                {
                    continue; // Still in the wall
                }
                highest = Math.max(highest, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx & 15, bz & 15));
                break;
            }
        }
        if (highest != Integer.MIN_VALUE)
        {
            return highest;
        }
        final @Nullable LevelChunk own = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return own == null ? rim : Math.max(own.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15), foot);
    }

    /** @return the open air over the chunk that this block is in, for a room that exchanges with it. A new object each time: it holds the cell it found, so that a step of a room looks the cell up once. */
    public Atmosphere atmosphere(int x, int z)
    {
        return new Handle(x >> 7, z >> 7);
    }

    /** @return the cell over this block, or {@code null} if nothing is in it or has fallen from it */
    @Nullable
    public AirGrid.Cell cellAt(int x, int z)
    {
        return grid.cellAt(x >> 7, z >> 7);
    }

    private final class Handle implements Atmosphere
    {
        private final int cx, cz;
        private @Nullable AirGrid.Cell cell;

        Handle(int cx, int cz)
        {
            this.cx = cx;
            this.cz = cz;
            this.cell = grid.cellAt(cx, cz);
        }

        @Override
        public float molesPerCubicMetre(Gas gas)
        {
            final @Nullable AirSpecies species = AirSpecies.of(gas);
            return species == null || cell == null ? 0f : (float) cell.molesPerCubicMetre(species);
        }

        @Override
        public void receive(Gas gas, float moles)
        {
            if (gas == Gas.CARBON_DIOXIDE || gas == Gas.CARBON_MONOXIDE)
            {
                carbonMoles += moles;
                setDirty();
                return;
            }
            final @Nullable AirSpecies species = AirSpecies.emittedAs(gas);
            if (species == null)
            {
                return;
            }
            if (species.gas() == gas)
            {
                grid.emitFromRoom(cx, cz, species, moles);
            }
            else
            {
                grid.emit(cx, cz, species, moles * AirSpecies.yieldOf(gas));
            }
            cell = grid.cellAt(cx, cz);
            setDirty();
        }

        @Override
        public float give(Gas gas, float wanted)
        {
            final @Nullable AirSpecies species = AirSpecies.of(gas);
            if (species == null || cell == null)
            {
                return 0f;
            }
            final double got = grid.take(cell, species, wanted);
            if (got > 0.0)
            {
                setDirty();
            }
            return (float) got;
        }
    }

    // --- Time ------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Called every tick of the level: does nothing until an hour of the calendar has passed. */
    public void tick(ServerLevel level)
    {
        SoilConstants.setDaysPerYear(Calendars.SERVER.getCalendarDaysInMonth() * (double) net.dries007.tfc.util.calendar.ICalendar.MONTHS_IN_YEAR);
        final long hourNow = AirCadence.hourOf(Calendars.SERVER.getCalendarTicks()), dayNow = Math.floorDiv(hourNow, 24L);
        if (dayNow != lastSweepDay)
        {
            if (lastSweepDay != Long.MIN_VALUE && grid.sweep(hourNow) > 0)
            {
                setDirty();
            }
            lastSweepDay = dayNow;
        }
        stepTo(level, AirCadence.hourOf(Calendars.SERVER.getCalendarTicks()));
    }

    /**
     * Brings the air up to a calendar hour. A jump of more than a week is followed for a week and what is then in the air is put into the ground in place.
     *
     * @return how many hourly steps were taken
     */
    public long stepTo(ServerLevel level, long nowHour)
    {
        if (lastHour == Long.MIN_VALUE || grid.activeCount() == 0)
        {
            if (lastHour != nowHour)
            {
                lastHour = nowHour;
                setDirty();
            }
            return 0L;
        }
        final AirCadence.Plan plan = AirCadence.plan(lastHour, nowHour);
        if (plan.steps() == 0L && plan.newLastHour() == lastHour)
        {
            return 0L;
        }
        final long start = System.nanoTime();
        final double scale = metresPerBlock(level);
        final AirWeather sky = weather(level);
        if (sky instanceof LevelAirWeather real)
        {
            real.collect(grid); // The rainfall that was worked out off the main thread comes in here
        }
        for (long step = 0; step < plan.steps(); step++)
        {
            grid.step(sky, AirCadence.middleOfHourBefore(plan.firstHour() + step), scale);
        }
        if (plan.depositRest())
        {
            forcedHours += nowHour - lastHour - plan.steps();
            LOGGER.info("The calendar jumped by {} hours: the regional air was followed for {} and what was left in it put into the ground where it stood", nowHour - lastHour, plan.steps());
            grid.depositInPlace();
        }
        lastHour = plan.newLastHour();
        setDirty();
        TickProfiler.PROFILE.record(TickProfiler.AIR, System.nanoTime() - start);
        return plan.steps();
    }

    // --- Saving ----------------------------------------------------------------------------------------------------------------------------------------------------------

    private static long[] bits(double[] values, int[] indices)
    {
        final long[] out = new long[indices.length];
        for (int i = 0; i < indices.length; i++)
        {
            out[i] = Double.doubleToRawLongBits(values[indices[i]]);
        }
        return out;
    }

    private static void unbits(long[] stored, int[] indices, double[] into)
    {
        for (int i = 0; i < indices.length && i < stored.length; i++)
        {
            into[indices[i]] = Double.longBitsToDouble(stored[i]);
        }
    }

    private static int[] speciesIndices()
    {
        final int[] indices = new int[AirSpecies.COUNT];
        for (int i = 0; i < indices.length; i++)
        {
            indices[i] = i;
        }
        return indices;
    }

    private static final int[] SPECIES = speciesIndices();
    private static final int[] FALL = new int[FALLING.length];

    static
    {
        for (int i = 0; i < FALLING.length; i++)
        {
            FALL[i] = FALLING[i].ordinal();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        tag.putDouble("carbon", carbonMoles);
        tag.putLong("lastHour", lastHour);
        tag.putLong("forcedHours", forcedHours);
        tag.putLongArray("emitted", bits(grid.emitted, SPECIES));
        tag.putLongArray("taken", bits(grid.taken, SPECIES));
        tag.putLongArray("returned", bits(grid.returned, SPECIES));
        tag.putLongArray("deposited", bits(grid.deposited, SPECIES));
        tag.putLongArray("depositedWet", bits(grid.depositedWet, SPECIES));
        tag.putLongArray("background", bits(grid.background, SPECIES));
        tag.putLongArray("scattered", bits(grid.scattered, FALL));
        final ListTag list = new ListTag();
        for (AirGrid.Cell cell : grid.cells())
        {
            final CompoundTag entry = new CompoundTag();
            entry.putInt("x", cell.x);
            entry.putInt("z", cell.z);
            if (cell.hasAir)
            {
                entry.putLongArray("air", bits(cell.air, SPECIES));
            }
            entry.putLongArray("fall", bits(cell.cumulative, FALL));
            if (cell.annualHour != Long.MIN_VALUE)
            {
                entry.putDouble("annualSo2", cell.annualSo2);
                entry.putLong("annualHour", cell.annualHour);
                entry.putLong("annualSince", cell.annualSince);
                entry.putDouble("excessConifer", cell.excess[0]);
                entry.putDouble("excessBroadleaf", cell.excess[1]);
            }
            if (cell.hasWind())
            {
                entry.putDouble("windX", cell.windX);
                entry.putDouble("windZ", cell.windZ);
                entry.putLong("windDay", cell.windDay);
            }
            if (cell.hasAnchor)
            {
                entry.putIntArray("anchor", new int[] {cell.anchorX, cell.anchorY, cell.anchorZ});
            }
            if (cell.hasRain)
            {
                entry.putFloat("rainAverage", cell.rainAverage);
                entry.putFloat("rainVariance", cell.rainVariance);
            }
            list.add(entry);
        }
        tag.put("cells", list);
        return tag;
    }

    public static RegionalAir load(CompoundTag tag, HolderLookup.Provider registries)
    {
        final RegionalAir air = new RegionalAir();
        air.carbonMoles = tag.getDouble("carbon");
        air.lastHour = tag.contains("lastHour") ? tag.getLong("lastHour") : Long.MIN_VALUE;
        air.forcedHours = tag.getLong("forcedHours");
        unbits(tag.getLongArray("emitted"), SPECIES, air.grid.emitted);
        unbits(tag.getLongArray("taken"), SPECIES, air.grid.taken);
        unbits(tag.getLongArray("returned"), SPECIES, air.grid.returned);
        unbits(tag.getLongArray("deposited"), SPECIES, air.grid.deposited);
        unbits(tag.getLongArray("depositedWet"), SPECIES, air.grid.depositedWet);
        unbits(tag.getLongArray("background"), SPECIES, air.grid.background);
        unbits(tag.getLongArray("scattered"), FALL, air.grid.scattered);
        for (Tag raw : tag.getList("cells", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) raw;
            final AirGrid.Cell cell = new AirGrid.Cell(entry.getInt("x"), entry.getInt("z"));
            if (entry.contains("air"))
            {
                unbits(entry.getLongArray("air"), SPECIES, cell.air);
                for (double moles : cell.air)
                {
                    cell.hasAir |= moles > 0.0;
                }
            }
            unbits(entry.getLongArray("fall"), FALL, cell.cumulative);
            if (entry.contains("annualHour"))
            {
                cell.restoreAnnual(entry.getDouble("annualSo2"), entry.getLong("annualHour"), entry.getLong("annualSince"), entry.getDouble("excessConifer"), entry.getDouble("excessBroadleaf"));
            }
            if (entry.contains("windDay"))
            {
                cell.setWind(entry.getDouble("windX"), entry.getDouble("windZ"), entry.getLong("windDay"));
            }
            if (entry.contains("anchor"))
            {
                final int[] anchor = entry.getIntArray("anchor");
                cell.setAnchor(anchor[0], anchor[1], anchor[2]);
            }
            if (entry.contains("rainAverage"))
            {
                cell.setRain(entry.getFloat("rainAverage"), entry.getFloat("rainVariance"));
            }
            air.grid.restore(cell);
        }
        return air;
    }
}
