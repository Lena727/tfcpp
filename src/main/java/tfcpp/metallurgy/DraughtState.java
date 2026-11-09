package tfcpp.metallurgy;

import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.util.climate.Climate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * What one charcoal forge or firepit knows for {@link Draught}: the fuel it burns, the person blowing into it, and the wind and the oxygen of its room, which are looked up once a second (on the game time: this is only how often a cache is refreshed, not a process) and not on every tick.
 */
public final class DraughtState
{
    private static final String FUEL_KEY = "tfcppFuel", FUEL_TEMPERATURE_KEY = "tfcppFuelTemperature";
    /** Blowing carries on for this many ticks after the last moment of it. */
    public static final int BLOW_TICKS = 10;
    private static final long REFRESH_TICKS = 20;

    private @Nullable String fuel;
    private float fuelTemperature;
    private long refreshedAt = Long.MIN_VALUE;
    private float windAir;
    private float oxygenFactor = 1f;
    private @Nullable Map<UUID, Long> blowers;
    private int blowerCount;
    private float air;
    private float burnRemainder;

    /** The fire takes a new piece of fuel. */
    public void fuelTaken(ItemStack stack, float burnTemperature)
    {
        fuel = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        fuelTemperature = burnTemperature;
    }

    /** This player blows into the hearth through a pipe, at this game time. The same player at every tick counts as one. */
    public void blow(UUID player, long gameTime)
    {
        if (blowers == null)
        {
            blowers = new HashMap<>(4);
        }
        blowers.put(player, gameTime);
    }

    /** @return how many players blew into the hearth at its last tick, as counted, at most {@link Draught#MOST_BLOWERS} */
    public int blowers()
    {
        return blowerCount;
    }

    public boolean isBlowing()
    {
        return blowerCount > 0;
    }

    private int countBlowers(long now)
    {
        if (blowers == null)
        {
            return 0;
        }
        for (Iterator<Long> times = blowers.values().iterator(); times.hasNext(); )
        {
            if (now - times.next() > BLOW_TICKS)
            {
                times.remove();
            }
        }
        return Math.min(blowers.size(), Draught.MOST_BLOWERS);
    }

    /** Copies what a firepit knew when it is made into a pot or a grill. */
    public void copyFrom(DraughtState other)
    {
        fuel = other.fuel;
        fuelTemperature = other.fuelTemperature;
        burnRemainder = other.burnRemainder;
    }

    /** @return the burn ticks that the fuel loses at this tick: 1 + air, or 2 in the rain as TFC has it, and the fractions are carried to the next tick. Full bellows still burn fuel twice as fast. */
    public int takeBurnTicks(boolean raining)
    {
        burnRemainder += raining ? 2f : 1f + air;
        final int whole = (int) burnRemainder;
        burnRemainder -= whole;
        return whole;
    }

    /** @return all the air the hearth has had at its last tick, as a share of full bellows. Natural draught is not counted. */
    public float air()
    {
        return air;
    }

    /** Makes the next tick look the wind and the oxygen up again. */
    public void invalidate()
    {
        refreshedAt = Long.MIN_VALUE;
    }

    public float oxygenFactor()
    {
        return oxygenFactor;
    }

    /** @return the fuel that is burning, if this is the one that was taken and nothing has replaced it unseen: a catch-up over a long time burns on to another piece */
    @Nullable
    public String knownFuel(float burnTemperature)
    {
        return fuel != null && Math.abs(burnTemperature - fuelTemperature) < 0.5f ? fuel : null;
    }

    /** @return the temperature of the hearth after this tick, in the manner of TFC's own movement: 1 degree a tick, and 2 up and a half down with forced air */
    public float adjust(Level level, BlockPos pos, float temperature, float burnTemperature, int airTicks, boolean raining)
    {
        refresh(level, pos);
        blowerCount = countBlowers(level.getGameTime());
        air = Draught.totalAir(Draught.bellowsAir(airTicks), Draught.pipeAir(blowerCount), windAir);
        final float natural = DraughtFuel.natural(knownFuel(burnTemperature), burnTemperature);
        final float full = Draught.fullBlastTemperature(burnTemperature);
        final float target = HeatCapability.targetDeviceTemp(Draught.temperature(natural, full, air, oxygenFactor), 0, raining);
        if (temperature == target)
        {
            return target;
        }
        final boolean forced = airTicks > 0 || blowerCount > 0;
        return HeatCapability.adjustTempTowards(temperature, target, forced ? 2f : 1f, forced ? 0.5f : 1f);
    }

    /** A hearth with no fuel burning has nothing to govern: it cools as TFC has it, and has no air. */
    public void cold()
    {
        air = 0f;
        blowerCount = 0;
    }

    private void refresh(Level level, BlockPos pos)
    {
        final long now = level.getGameTime();
        if (refreshedAt != Long.MIN_VALUE && Math.abs(now - refreshedAt) < REFRESH_TICKS)
        {
            return;
        }
        refreshedAt = now;
        windAir = level.canSeeSky(pos.above()) ? Draught.windAir(Climate.get(level).getWind(level, pos).length()) : 0f;
        oxygenFactor = Draught.oxygenFactor(intakeOxygen(level, pos));
    }

    /** @return the oxygen share of the gas the fire draws from, which is the cell its flue gas comes out in, as for {@code FireSource} */
    private static float intakeOxygen(Level level, BlockPos pos)
    {
        if (level instanceof ServerLevel server)
        {
            final LevelGasVolume volume = GasTracker.get(server).volume(server);
            int y = GasEmission.outletHeight(server, volume, pos);
            if (y == GasEmission.SMOTHERED)
            {
                y = GasEmission.leakHeight(server, volume, pos);
            }
            if (y != GasEmission.SMOTHERED)
            {
                final @Nullable float[] cell = volume.cell(pos.getX(), y, pos.getZ());
                if (cell != null)
                {
                    return GasMath.oxygenFraction(cell);
                }
            }
        }
        return GasMath.OXYGEN_IN_AIR;
    }

    public void save(CompoundTag tag)
    {
        if (fuel != null)
        {
            tag.putString(FUEL_KEY, fuel);
            tag.putFloat(FUEL_TEMPERATURE_KEY, fuelTemperature);
        }
    }

    public void load(CompoundTag tag)
    {
        fuel = tag.contains(FUEL_KEY) ? tag.getString(FUEL_KEY) : null;
        fuelTemperature = tag.getFloat(FUEL_TEMPERATURE_KEY);
    }
}
