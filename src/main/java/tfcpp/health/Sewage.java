package tfcpp.health;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import tfcpp.ecology.LevelWaters;
import tfcpp.ecology.Pollutant;
import tfcpp.ecology.Pollution;
import tfcpp.ecology.Waters;

/**
 * Where the waste of people and animals goes: into a cesspit if a person has one in reach, into the water if the one who made it stands in water, else into the soil where it was made. Waste goes where the people and the animals spend their time, and no one has to do anything for it to happen.
 */
public final class Sewage
{
    /** Ticks between deposits: a minute. */
    public static final int INTERVAL_TICKS = 1200;

    private Sewage() {}

    /**
     * Puts waste at a place.
     *
     * @param person {@code true} for a person, who uses a cesspit that is within reach; livestock do not
     * @param dysentery litres of it that came from a person shedding Shigella, and {@code typhoid} litres from one shedding S. Typhi
     */
    public static void deposit(ServerLevel level, BlockPos pos, double litres, double dysentery, double typhoid, boolean person, boolean inWater)
    {
        if (!(litres > 0.0) || !level.isLoaded(pos))
        {
            return;
        }
        if (person && !inWater)
        {
            final @Nullable CesspitBlockEntity pit = Cesspits.nearestForWaste(level, pos);
            if (pit != null)
            {
                final double taken = pit.accept(level, litres, dysentery, typhoid);
                final double left = litres - taken;
                if (!(left > 1e-9))
                {
                    return;
                }
                final double share = left / litres; // The pit is full: what does not fit runs over onto the ground around it
                litres = left;
                dysentery *= share;
                typhoid *= share;
                pos = pit.getBlockPos(); // It runs over at the pit, wherever the person stood (which may be another chunk)
            }
        }
        if (inWater)
        {
            final @Nullable Waters.Meeting meeting = Waters.meeting(level, pos);
            if (meeting != null)
            {
                final double[] load = new double[Pollutant.COUNT];
                load[Pollutant.SEWAGE.ordinal()] = litres;
                load[Pollutant.SHED_DYSENTERY.ordinal()] = dysentery;
                load[Pollutant.SHED_TYPHOID.ordinal()] = typhoid;
                LevelWaters.get(level).discharge(level, meeting.pos(), meeting.kind(), load, false);
                return;
            }
        }
        Pollution.deposit(level, pos, Pollutant.SEWAGE, litres);
        Pollution.deposit(level, pos, Pollutant.SHED_DYSENTERY, dysentery);
        Pollution.deposit(level, pos, Pollutant.SHED_TYPHOID, typhoid);
    }

    /** What the livestock of one place make: the animals counted since the last flush, as litres a day, and the place of the first of them. */
    private static final class Herd
    {
        final BlockPos pos;
        final boolean inWater;
        double litresPerDay;

        Herd(BlockPos pos, boolean inWater)
        {
            this.pos = pos;
            this.inWater = inWater;
        }
    }

    /** The herds of each level, by chunk and by whether the animal stood in water, and the calendar tick of the last flush. */
    private static final class Pasture
    {
        final java.util.Map<Long, Herd> herds = new java.util.HashMap<>();
        long lastFlush;
    }

    private static final java.util.Map<ServerLevel, Pasture> PASTURES = new java.util.WeakHashMap<>();

    /**
     * Counts an animal for the minute: its rate is added to the herd of its chunk, which puts down the waste of the whole herd at once ({@link #flushLivestock}), so that a chunk full of animals is one deposit a minute and not one for each.
     */
    public static void livestock(ServerLevel level, LivingEntity animal)
    {
        final double perDay = SewageRates.animalLitresPerDay(BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath());
        if (perDay > 0.0)
        {
            final boolean inWater = animal.isInWater();
            final long key = animal.chunkPosition().toLong() * 2L + (inWater ? 1L : 0L);
            PASTURES.computeIfAbsent(level, l -> new Pasture()).herds.computeIfAbsent(key, k -> new Herd(animal.blockPosition(), inWater)).litresPerDay += perDay;
        }
    }

    /**
     * Puts down the waste of the animals counted since the last flush: their rates over the calendar ticks that really passed (none while the calendar stands, and no more than {@link SewageRates#MAX_CATCH_UP_TICKS} after a long jump), one deposit for each chunk. A calendar that goes back changes nothing and the stamp stays at the later time.
     */
    public static void flushLivestock(ServerLevel level, long calendarNow)
    {
        final @Nullable Pasture pasture = PASTURES.get(level);
        if (pasture == null)
        {
            return;
        }
        final long last = pasture.lastFlush;
        pasture.lastFlush = Math.max(last, calendarNow);
        final long passed = last == 0L ? 0L : Math.min(Math.max(0L, calendarNow - last), SewageRates.MAX_CATCH_UP_TICKS);
        if (passed > 0L)
        {
            for (Herd herd : pasture.herds.values())
            {
                deposit(level, herd.pos, SewageRates.litres(herd.litresPerDay, passed), 0.0, 0.0, false, herd.inWater);
            }
        }
        pasture.herds.clear();
    }

    /** @return whether this entity is an animal that is counted */
    public static boolean isLivestock(Entity entity)
    {
        return !(entity instanceof Player) && entity instanceof net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
    }
}
