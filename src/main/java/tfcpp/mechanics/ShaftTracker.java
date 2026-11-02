package tfcpp.mechanics;

import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.dries007.tfc.common.blockentities.BellowsBlockEntity;
import net.dries007.tfc.common.blockentities.LoomBlockEntity;
import net.dries007.tfc.common.blockentities.QuernBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.BladedAxleBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.CrankshaftBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.PowerLoomBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.PumpBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.RotatingBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.TripHammerBlockEntity;
import net.dries007.tfc.common.recipes.LoomRecipe;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.rotation.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import tfcpp.core.ServerConfig;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.pipes.PistonPumpBlockEntity;

/**
 * Adds up the load on each rotation network of a level once per second, and tells each source how far that slows it down. Costs nothing in a level without any shafts.
 * <p>
 * Pumps and fans do real work, so each takes its turn at a tick of its own in the second, with a budget for the space fans walk.
 */
public final class ShaftTracker
{
    private static final int INTERVAL_TICKS = 20;
    private static final float SECONDS = INTERVAL_TICKS / ShaftPhysics.TICKS_PER_SECOND;

    private final ReferenceOpenHashSet<BlockEntity> consumers = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<BlockEntity> sources = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<BlockEntity> workers = new ReferenceOpenHashSet<>();
    private final Reference2FloatOpenHashMap<BlockEntity> workerTorque = new Reference2FloatOpenHashMap<>();
    private final Long2FloatOpenHashMap torque = new Long2FloatOpenHashMap();
    private final WalkTurns<BlockEntity> turns = new WalkTurns<>();
    private long lastCalendarTick = Long.MIN_VALUE;

    public static ShaftTracker get(ServerLevel level)
    {
        return level.getData(GasAttachments.SHAFTS);
    }

    public void onBlockEntityLoaded(BlockEntity entity)
    {
        if (entity instanceof ShaftSource)
        {
            sources.add(entity);
        }
        else if (entity instanceof RotatingBlockEntity || entity instanceof TripHammerBlockEntity || entity instanceof BellowsBlockEntity || entity instanceof PumpBlockEntity)
        {
            consumers.add(entity);
            if (entity instanceof PumpBlockEntity || entity instanceof MineFanBlockEntity || entity instanceof PistonPumpBlockEntity)
            {
                workers.add(entity);
            }
        }
    }

    public void onBlockEntityUnloaded(BlockEntity entity)
    {
        if (entity instanceof ShaftSource)
        {
            sources.remove(entity);
        }
        else
        {
            consumers.remove(entity);
            workers.remove(entity);
            workerTorque.removeFloat(entity);
            turns.forget(entity);
        }
    }

    /** @return the load on this network at the last update, in kN m */
    public float networkTorque(long network)
    {
        return torque.get(network);
    }

    /**
     * Runs on the ticks of TFC's calendar, which stop on an empty server: shafts do not go on turning pumps and fans in chunks that a server keeps loaded, while the fire and the water that drive them stand still. A second is 20 ticks of the calendar, and each worker keeps its own place in it.
     */
    public void tick(ServerLevel level)
    {
        final long now = Calendars.SERVER.getTicks();
        if (now == lastCalendarTick)
        {
            return;
        }
        lastCalendarTick = now;

        if (!workers.isEmpty())
        {
            tickWorkers(level, now);
        }
        if (Math.floorMod(now, INTERVAL_TICKS) != 0 || (consumers.isEmpty() && sources.isEmpty()))
        {
            return;
        }

        torque.clear();
        for (BlockEntity consumer : consumers)
        {
            addLoad(level, consumer);
        }

        for (BlockEntity entity : sources)
        {
            final long network = ((RotatingBlockEntity) entity).getRotationNode().network();
            final ShaftSource source = (ShaftSource) entity;
            source.tfcpp$setSpeedFactor(ShaftPhysics.speedFactor(torque.get(network), source.tfcpp$stallTorque()));
        }
    }

    private void tickWorkers(ServerLevel level, long now)
    {
        turns.startTick(ServerConfig.GAS_FAN_CELLS_PER_TICK.getAsInt());

        // Fans that were refused a walk by an earlier tick go first, so that others whose second has just come cannot keep taking their place
        final List<BlockEntity> refused = turns.takeWaiting();
        final ReferenceOpenHashSet<BlockEntity> served = new ReferenceOpenHashSet<>(refused);
        for (BlockEntity worker : refused)
        {
            work(level, worker);
        }

        for (BlockEntity worker : List.copyOf(workers)) // Pumps change blocks, which can unload what is being walked
        {
            if (Math.floorMod(now + worker.getBlockPos().hashCode(), INTERVAL_TICKS) == 0 && !served.contains(worker))
            {
                work(level, worker);
            }
        }
    }

    private void work(ServerLevel level, BlockEntity worker)
    {
        final BlockPos pos = worker.getBlockPos();
        if (worker.isRemoved() || !workers.contains(worker) || !canRun(level, pos))
        {
            return;
        }
        if (worker instanceof MineFanBlockEntity fan)
        {
            // Cells of the budget are spent only if the fan really walks: it does not while the air is clean, nor while it stands still
            workerTorque.put(worker, fan.tickFan(level, SECONDS, () -> turns.request(fan, MineFanBlockEntity.REACH_CELLS)));
        }
        else if (worker instanceof PistonPumpBlockEntity piston)
        {
            workerTorque.put(worker, piston.tickPump(level, SECONDS));
        }
        else if (worker instanceof PumpBlockEntity pump)
        {
            final @Nullable Node crank = MassPump.crankNode(level, pump);
            workerTorque.put(worker, crank == null ? 0 : MassPump.tick(level, pump, crank, SECONDS));
        }
    }

    /** Border chunks are loaded but do not tick, and looking across into a chunk that is not ready would load it right there. */
    private static boolean canRun(ServerLevel level, BlockPos pos)
    {
        return level.shouldTickBlocksAt(pos) && level.hasChunksAt(pos.offset(-2, 0, -2), pos.offset(2, 0, 2));
    }

    private void addLoad(ServerLevel level, BlockEntity entity)
    {
        if (entity.isRemoved() || !canRun(level, entity.getBlockPos()))
        {
            return;
        }
        if (entity instanceof PumpBlockEntity pump)
        {
            final @Nullable Node crank = MassPump.crankNode(level, pump);
            if (crank != null)
            {
                add(crank, workerTorque.getFloat(entity));
            }
        }
        else if (entity instanceof BellowsBlockEntity bellows)
        {
            final @Nullable CrankshaftBlockEntity crank = bellows.getCrankBlockEntity();
            if (crank != null)
            {
                add(crank.getRotationNode(), ShaftPhysics.BELLOWS_TORQUE);
            }
        }
        else if (entity instanceof TripHammerBlockEntity hammer)
        {
            if (level.getBlockEntity(entity.getBlockPos().above()) instanceof BladedAxleBlockEntity axle && !hammer.getInventory().getStackInSlot(0).isEmpty())
            {
                add(axle.getRotationNode(), ShaftPhysics.TRIP_HAMMER_TORQUE);
            }
        }
        else if (entity instanceof RotatingBlockEntity rotating)
        {
            add(rotating.getRotationNode(), ShaftPhysics.BEARING_TORQUE + workingTorque(entity));
        }
    }

    private float workingTorque(BlockEntity entity)
    {
        if (entity instanceof MineFanBlockEntity || entity instanceof PistonPumpBlockEntity)
        {
            return workerTorque.getFloat(entity);
        }
        if (entity instanceof QuernBlockEntity quern)
        {
            return quern.isGrinding() ? ShaftPhysics.QUERN_TORQUE : 0;
        }
        if (entity instanceof PowerLoomBlockEntity loom)
        {
            return isWeaving(loom) ? ShaftPhysics.POWER_LOOM_TORQUE : 0;
        }
        return 0;
    }

    private static boolean isWeaving(LoomBlockEntity loom)
    {
        final @Nullable LoomRecipe recipe = loom.getRecipe();
        return recipe != null && loom.getProgress() < recipe.getStepCount();
    }

    private void add(Node node, float amount)
    {
        if (node.network() != Node.NO_NETWORK)
        {
            torque.addTo(node.network(), amount);
        }
    }
}
