package tfcpp.ecology;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tfcpp.chemistry.Chemistry;
import tfcpp.chemistry.Liquid;
import tfcpp.metallurgy.Metallurgy;

/**
 * The one way in for everything that goes into the ground: each source of waste calls {@link #deposit} or {@link #soak} at the place where it reaches the soil, as gas sources call {@code GasEmission}.
 * <p>
 * Time is the calendar of TFC, as for anything that is about the whole world and not about one place. A cell is made when something is first put into the chunk, from the soil and rock there, and is brought up to date whenever it is touched or viewed. Nothing catches up on chunk load: spawn chunks load before TFC has read the saved calendar, and the clock of a cell never goes back.
 */
public final class Pollution
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Pollution.class);
    /** Liquids already reported as leaving no record, so that a pipe that leaks water all day says so once. */
    private static final Set<String> REPORTED = new HashSet<>();

    private Pollution() {}

    /** @return the calendar tick that every cell is dated by */
    public static long now()
    {
        return Calendars.SERVER.getCalendarTicks();
    }

    /**
     * Puts this much of a pollutant into the soil of the chunk at this position.
     *
     * @param amount in the unit of the pollutant: equivalents, grams, kilograms or litres
     */
    public static void deposit(ServerLevel level, BlockPos pos, Pollutant pollutant, double amount)
    {
        if (!(amount > 0.0))
        {
            return;
        }
        final LevelChunk chunk = level.getChunkAt(pos);
        touch(level, chunk).deposit(pollutant, amount);
        chunk.setUnsaved(true);
    }

    /** Liquid that has run into the ground at this position. */
    public static void soak(ServerLevel level, BlockPos pos, FluidStack stack)
    {
        if (!stack.isEmpty())
        {
            soak(level, pos, stack.getFluid(), stack.getAmount());
        }
    }

    /**
     * Liquid that has run into the ground at this position, recorded as what it leaves in the soil if it leaves anything. A liquid that has no entry in {@link Soakable} soaks away without a record, and is logged once at debug level.
     *
     * @param millibuckets how much, on the scale of the mod: for the chemical liquids a millibucket is a litre, for mercury and molten metals a millilitre
     */
    public static void soak(ServerLevel level, BlockPos pos, Fluid fluid, double millibuckets)
    {
        if (!(millibuckets > 0.0))
        {
            return;
        }
        final @Nullable Soakable soakable = soakableOf(fluid);
        if (soakable == null)
        {
            final String key = String.valueOf(BuiltInRegistries.FLUID.getKey(fluid));
            if (REPORTED.add(key))
            {
                LOGGER.debug("{} soaked into the ground at {} and leaves no record: it has no entry in Soakable", key, pos.toShortString());
            }
            return;
        }
        final double[] load = soakable.load(millibuckets);
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            deposit(level, pos, pollutant, load[pollutant.ordinal()]);
        }
    }

    private static @Nullable Map<Metal, Soakable> metalSoakables;

    private static Map<Metal, Soakable> metalSoakables()
    {
        if (metalSoakables == null)
        {
            final Map<Metal, Soakable> map = new EnumMap<>(Metal.class);
            map.put(Metal.COPPER, Soakable.COPPER);
            map.put(Metal.BRONZE, Soakable.BRONZE);
            map.put(Metal.BISMUTH_BRONZE, Soakable.BISMUTH_BRONZE);
            map.put(Metal.BLACK_BRONZE, Soakable.BLACK_BRONZE);
            map.put(Metal.BRASS, Soakable.BRASS);
            map.put(Metal.ROSE_GOLD, Soakable.ROSE_GOLD);
            map.put(Metal.STERLING_SILVER, Soakable.STERLING_SILVER);
            map.put(Metal.NICKEL, Soakable.NICKEL);
            metalSoakables = map;
        }
        return metalSoakables;
    }

    /** @return the record that a fluid leaves in soil, or {@code null} if it leaves none */
    @Nullable
    public static Soakable soakableOf(Fluid fluid)
    {
        final @Nullable Liquid liquid = Chemistry.liquidOf(fluid);
        if (liquid != null)
        {
            return Soakable.of(liquid);
        }
        final FluidType type = fluid.getFluidType();
        if (type == Metallurgy.MERCURY_TYPE.get())
        {
            return Soakable.MERCURY;
        }
        if (type == Metallurgy.LEAD_TYPE.get() || type == Metallurgy.WORK_LEAD_TYPE.get())
        {
            return Soakable.LEAD;
        }
        if (type == Metallurgy.ARSENICAL_COPPER_TYPE.get())
        {
            return Soakable.ARSENICAL_COPPER;
        }
        if (type == TFCFluids.METALS.get(Metal.ZINC).getType())
        {
            return Soakable.ZINC;
        }
        for (Map.Entry<Metal, Soakable> entry : metalSoakables().entrySet())
        {
            if (type == TFCFluids.METALS.get(entry.getKey()).getType())
            {
                return entry.getValue();
            }
        }
        return null;
    }

    /** @return the soil memory of the chunk at this position as it is now, without making a cell in a chunk that has none: a chunk nothing was put into gives its soil as it was before anything was */
    public static SoilCell view(ServerLevel level, BlockPos pos)
    {
        final LevelChunk chunk = level.getChunkAt(pos);
        final long now = now();
        if (chunk.hasData(EcologyAttachments.CHUNK))
        {
            final @Nullable SoilCell cell = chunk.getData(EcologyAttachments.CHUNK).cell();
            if (cell != null)
            {
                final SoilCell copy = cell.copy();
                claim(level, chunk, copy);
                copy.catchUp(now);
                return copy;
            }
        }
        final SoilCell pristine = SoilReader.pristine(level, chunk, now);
        claim(level, chunk, pristine);
        return pristine;
    }

    /** @return {@code true} if something has been put into the chunk at this position, so that it keeps a cell */
    public static boolean isTouched(ServerLevel level, BlockPos pos)
    {
        final LevelChunk chunk = level.getChunkAt(pos);
        return chunk.hasData(EcologyAttachments.CHUNK) && chunk.getData(EcologyAttachments.CHUNK).cell() != null;
    }

    /** Gives a chunk a new soil of a given kind, for tests that need a known start. */
    public static SoilCell seed(ServerLevel level, BlockPos pos, SoilOrder order, boolean carbonateBedrock)
    {
        final LevelChunk chunk = level.getChunkAt(pos);
        final SoilCell cell = SoilCell.fresh(order, carbonateBedrock, now());
        final @Nullable AirGrid.Cell region = RegionalAir.get(level).grid().cellAt(chunk.getPos().x >> 3, chunk.getPos().z >> 3);
        if (region != null)
        {
            for (Pollutant pollutant : RegionalAir.FALLING)
            {
                cell.claimed[pollutant.ordinal()] = region.cumulative(pollutant); // A known start: what has fallen already is not this soil's
            }
        }
        chunk.getData(EcologyAttachments.CHUNK).set(cell);
        chunk.setUnsaved(true);
        return cell;
    }

    private static SoilCell touch(ServerLevel level, LevelChunk chunk)
    {
        final PollutionChunkData data = chunk.getData(EcologyAttachments.CHUNK);
        final long now = now();
        @Nullable SoilCell cell = data.cell();
        if (cell == null)
        {
            cell = SoilReader.pristine(level, chunk, now);
            data.set(cell);
        }
        claim(level, chunk, cell); // First what fell from the air, then the passage of time: see SoilCell#absorb
        cell.catchUp(now);
        return cell;
    }

    /** Takes in what the regional air has let fall on the chunk since this soil last looked. */
    private static void claim(ServerLevel level, LevelChunk chunk, SoilCell cell)
    {
        final @Nullable AirGrid.Cell region = RegionalAir.get(level).grid().cellAt(chunk.getPos().x >> 3, chunk.getPos().z >> 3);
        if (region != null)
        {
            for (Pollutant pollutant : RegionalAir.FALLING)
            {
                cell.absorb(pollutant, region.cumulative(pollutant));
            }
        }
    }
}
