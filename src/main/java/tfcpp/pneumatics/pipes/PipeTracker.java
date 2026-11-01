package tfcpp.pneumatics.pipes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import tfcpp.core.ServerConfig;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasIgnition;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * The pipe networks of one level. A network only ever covers loaded chunks: when part of one unloads it goes back to being separate segments, each holding its share, and what is still loaded forms a network of its own until the rest comes back.
 */
public final class PipeTracker
{
    private static final int INTERVAL_TICKS = 20;
    private static final float SECONDS = INTERVAL_TICKS / 20f;
    private static final float BLOWDOWN_GAUGE_ATM = 1f;
    /** A wall this far gone is scrap, so that breaking and replacing a pipe does not mend it. */
    private static final float WORN_OUT = 0.5f;
    private static final int MAX_SPILLED_BLOCKS = 64;

    private final Long2ObjectOpenHashMap<PipeSegment> segments = new Long2ObjectOpenHashMap<>();
    private final ReferenceOpenHashSet<PipeNetwork> networks = new ReferenceOpenHashSet<>();
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private final List<BlockPos> toBreak = new ArrayList<>();
    private final List<Runnable> afterWalk = new ArrayList<>();
    /** A player's block is removed before its drops are worked out, so this remembers it until the end of the tick. */
    private final LongOpenHashSet wornOutJustNow = new LongOpenHashSet();

    public static PipeTracker get(ServerLevel level)
    {
        return level.getData(GasAttachments.PIPES);
    }

    /** Breaks this block once no networks are being walked. */
    public void breakLater(BlockPos pos)
    {
        toBreak.add(pos);
    }

    /** Runs this once no networks are being walked. Anything that breaks a block has to wait for that, as it can end any number of networks. */
    public void later(Runnable action)
    {
        afterWalk.add(action);
    }

    public int networkCount()
    {
        return networks.size();
    }

    @Nullable
    public PipeSegment segmentAt(BlockPos pos)
    {
        return segments.get(pos.asLong());
    }

    /** @return the network at this position, as long as it has been formed */
    @Nullable
    public PipeNetwork networkAt(BlockPos pos)
    {
        final @Nullable PipeSegment segment = segments.get(pos.asLong());
        return segment == null ? null : segment.network;
    }

    public void onChunkLoad(LevelChunk chunk)
    {
        if (chunk.hasData(GasAttachments.PIPE_CHUNK))
        {
            for (PipeSegment segment : chunk.getData(GasAttachments.PIPE_CHUNK).segments())
            {
                segments.put(segment.pos.asLong(), segment);
                dirty.add(segment.pos.asLong());
            }
        }
    }

    public void onChunkUnload(ServerLevel level, LevelChunk chunk)
    {
        if (chunk.hasData(GasAttachments.PIPE_CHUNK))
        {
            for (PipeSegment segment : chunk.getData(GasAttachments.PIPE_CHUNK).segments())
            {
                dissolve(level, segment.network);
                segments.remove(segment.pos.asLong());
                dirty.remove(segment.pos.asLong());
            }
        }
    }

    public void onPlaced(ServerLevel level, BlockPos pos, BlockState state, PipeMember member)
    {
        final LevelChunk chunk = level.getChunkAt(pos);
        final PipeChunkData data = chunk.getData(GasAttachments.PIPE_CHUNK);
        final float volume = member.volume(state);
        final PipeSegment segment = new PipeSegment(pos, volume, PipeContents.atmospheric(volume));
        data.put(segment);
        chunk.setUnsaved(true);
        segments.put(pos.asLong(), segment);
        dirty.add(pos.asLong());
        for (Direction side : Direction.values())
        {
            final @Nullable PipeSegment near = segments.get(pos.relative(side).asLong());
            if (near != null)
            {
                dissolve(level, near.network);
            }
        }
    }

    public void onRemoved(ServerLevel level, BlockPos pos)
    {
        final @Nullable PipeSegment segment = segments.remove(pos.asLong());
        if (segment == null)
        {
            return;
        }
        dirty.remove(pos.asLong());
        if (segment.integrity < WORN_OUT)
        {
            wornOutJustNow.add(pos.asLong());
        }
        final LevelChunk chunk = level.getChunkAt(pos);
        chunk.getData(GasAttachments.PIPE_CHUNK).remove(pos);
        chunk.setUnsaved(true);

        final LevelGasVolume world = GasTracker.get(level).volume(level);
        if (segment.network != null)
        {
            final PipeNetwork network = segment.network;
            blowDown(level, world, network, pos); // A hole in a pipe lets out everything in the run
            dissolve(level, network);
        }
        else
        {
            PipeNetwork.release(world, pos, segment.stored);
        }
        for (Direction side : Direction.values())
        {
            if (segments.containsKey(pos.relative(side).asLong()))
            {
                dirty.add(pos.relative(side).asLong());
            }
        }
    }

    /** Closing or opening a valve changes what is joined to what. */
    public void onConnectionChanged(ServerLevel level, BlockPos pos)
    {
        final @Nullable PipeSegment segment = segments.get(pos.asLong());
        if (segment != null)
        {
            dissolve(level, segment.network);
            dirty.add(pos.asLong());
        }
        for (Direction side : Direction.values())
        {
            final @Nullable PipeSegment near = segments.get(pos.relative(side).asLong());
            if (near != null)
            {
                dissolve(level, near.network);
            }
        }
    }

    private void dissolve(ServerLevel level, @Nullable PipeNetwork network)
    {
        if (network != null && networks.remove(network))
        {
            network.dissolve(level);
            for (PipeSegment segment : network.segments())
            {
                if (segments.get(segment.pos.asLong()) == segment)
                {
                    dirty.add(segment.pos.asLong());
                }
            }
        }
    }

    public void tick(ServerLevel level)
    {
        wornOutJustNow.clear();
        if (!dirty.isEmpty())
        {
            rebuild(level);
        }
        if (networks.isEmpty())
        {
            return;
        }

        final long tick = level.getGameTime();
        for (PipeNetwork network : networks)
        {
            if (Math.floorMod(tick + network.segments().getFirst().pos.hashCode(), INTERVAL_TICKS) == 0)
            {
                final @Nullable PipeSegment burst = network.step(level, SECONDS);
                if (burst != null)
                {
                    toBreak.add(burst.pos);
                }
            }
        }
        if (!toBreak.isEmpty())
        {
            final List<BlockPos> breaking = List.copyOf(toBreak);
            toBreak.clear();
            for (BlockPos pos : breaking)
            {
                level.destroyBlock(pos, false); // A burst block leaves nothing worth picking up
            }
        }
        if (!afterWalk.isEmpty())
        {
            final List<Runnable> actions = List.copyOf(afterWalk);
            afterWalk.clear();
            actions.forEach(Runnable::run);
        }
    }

    private void rebuild(ServerLevel level)
    {
        int segmentsLeft = ServerConfig.PIPE_REBUILD_SEGMENTS_PER_TICK.getAsInt();
        final long[] starts = dirty.toLongArray();
        for (long key : starts)
        {
            if (segmentsLeft <= 0)
            {
                return; // The rest wait for the next tick, and hold their contents meanwhile
            }
            if (!dirty.contains(key))
            {
                continue;
            }
            dirty.remove(key);

            final @Nullable PipeSegment start = segments.get(key);
            if (start != null && start.network == null)
            {
                final List<PipeSegment> found = connected(level, start);
                segmentsLeft -= found.size();
                for (PipeSegment segment : found)
                {
                    dirty.remove(segment.pos.asLong());
                }
                if (!found.isEmpty())
                {
                    networks.add(new PipeNetwork(level, found));
                }
            }
        }
    }

    /** @return every segment joined to this one, or nothing if its block has gone missing */
    private List<PipeSegment> connected(ServerLevel level, PipeSegment start)
    {
        final List<PipeSegment> found = new ArrayList<>();
        final LongOpenHashSet seen = new LongOpenHashSet();
        final Queue<PipeSegment> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.pos.asLong());

        while (!queue.isEmpty())
        {
            final PipeSegment segment = queue.poll();
            final BlockState state = level.getBlockState(segment.pos);
            if (!(state.getBlock() instanceof PipeMember member))
            {
                forget(level, segment);
                continue;
            }
            found.add(segment);
            for (Direction side : Direction.values())
            {
                final BlockPos near = segment.pos.relative(side);
                final @Nullable PipeSegment next = segments.get(near.asLong());
                if (next != null && member.connects(state, side) && !seen.contains(near.asLong()))
                {
                    final BlockState nearState = level.getBlockState(near);
                    if (nearState.getBlock() instanceof PipeMember nearMember && nearMember.connects(nearState, side.getOpposite()))
                    {
                        dissolve(level, next.network); // A network already standing next to this, from a chunk that loaded earlier, joins it
                        seen.add(near.asLong());
                        queue.add(next);
                    }
                }
            }
        }
        return found;
    }

    /** The block is not there any more, for example after a chunk was edited from outside. */
    private void forget(ServerLevel level, PipeSegment segment)
    {
        segments.remove(segment.pos.asLong());
        level.getChunkAt(segment.pos).getData(GasAttachments.PIPE_CHUNK).remove(segment.pos);
    }

    /** All that is in a network comes out at one hole: its gas into the air there, under its full energy if it was under pressure, and its liquid over the ground. */
    private void blowDown(ServerLevel level, LevelGasVolume world, PipeNetwork network, BlockPos hole)
    {
        final PipeContents contents = network.contents();
        final float pressure = network.pressure();
        final float kilojoules = PipePhysics.burstEnergy(pressure, contents.freeVolume(network.volume()));

        final @Nullable Fluid fluid = network.fluid();
        final int blocksOfLiquid = fluid == null ? 0 : (int) (contents.litres / 1000f);
        final PipeContents released = contents.take(1f);
        PipeNetwork.release(world, hole, released);
        contents.temperature = released.temperature;
        contents.air = PipePhysics.moles(1f, Math.max(contents.temperature, 1f), network.volume());

        if (blocksOfLiquid > 0)
        {
            spill(level, hole, fluid, Math.min(blocksOfLiquid, MAX_SPILLED_BLOCKS));
        }
        if (pressure - 1f >= BLOWDOWN_GAUGE_ATM)
        {
            PipeNetwork.hiss(level, hole);
            GasIgnition.rupture(level, hole, kilojoules, (pressure - 1f) * PipePhysics.PASCALS_PER_ATM / 1000f);
        }
    }

    /** Puts what ran out on the ground around a hole, the lowest and nearest places first. Less than a whole block soaks away. */
    private static void spill(ServerLevel level, BlockPos hole, Fluid fluid, int blocks)
    {
        final Queue<BlockPos> queue = new ArrayDeque<>();
        final LongOpenHashSet seen = new LongOpenHashSet();
        queue.add(hole);
        seen.add(hole.asLong());

        int left = blocks;
        while (!queue.isEmpty() && left > 0)
        {
            final BlockPos pos = queue.poll();
            final BlockState state = level.getBlockState(pos);
            if (pos.equals(hole) || (state.canBeReplaced() && state.getFluidState().isEmpty()))
            {
                if (!pos.equals(hole))
                {
                    level.setBlockAndUpdate(pos, fluid.defaultFluidState().createLegacyBlock());
                    left--;
                }
                for (Direction side : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
                {
                    final BlockPos next = pos.relative(side);
                    if (level.isLoaded(next) && seen.add(next.asLong()))
                    {
                        queue.add(next);
                    }
                }
            }
        }
    }

    /** @return {@code true} if the wall here has corroded too far to be worth anything, whether or not it has just been broken */
    public boolean isWornOut(BlockPos pos)
    {
        final @Nullable PipeSegment segment = segments.get(pos.asLong());
        return segment != null ? segment.integrity < WORN_OUT : wornOutJustNow.contains(pos.asLong());
    }

    /** Called by the blocks of a network. */
    public static void placed(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, BlockState oldState)
    {
        if (level instanceof ServerLevel server && oldState.getBlock() != state.getBlock() && state.getBlock() instanceof PipeMember member)
        {
            get(server).onPlaced(server, pos, state, member);
        }
    }

    /** Called by the blocks of a network. */
    public static void removed(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, BlockState newState)
    {
        if (level instanceof ServerLevel server && newState.getBlock() != state.getBlock())
        {
            get(server).onRemoved(server, pos);
        }
    }
}
