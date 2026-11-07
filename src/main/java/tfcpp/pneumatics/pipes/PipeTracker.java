package tfcpp.pneumatics.pipes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import tfcpp.chemistry.Chemistry;
import tfcpp.chemistry.CorrosiveSpill;
import tfcpp.core.ServerConfig;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasIgnition;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * The pipe networks of one level. A network only ever covers loaded chunks: when part of one unloads it goes back to being separate segments, each holding its share, and what is still loaded forms a network of its own, marked as running on past loaded land, until the rest comes back.
 */
public final class PipeTracker
{
    private static final int INTERVAL_TICKS = 20;
    private static final float SECONDS = INTERVAL_TICKS / 20f;
    private static final float BLOWDOWN_GAUGE_ATM = 1f;
    /** A wall that has lost more than this is scrap, so that breaking and replacing a pipe does not mend it: a block gives itself back only while it is above 95% whole. */
    public static final float KEEPS_ABOVE = 0.95f;
    private static final int MAX_SPILLED_BLOCKS = 64;
    /** A hand wheel is not turned twice in a second: the network is rebuilt at every turn. */
    private static final long VALVE_COOLDOWN_TICKS = 20L;
    private static final int VALVE_CLICKS_KEPT = 256;

    private final Long2ObjectOpenHashMap<PipeSegment> segments = new Long2ObjectOpenHashMap<>();
    private final ReferenceOpenHashSet<PipeNetwork> networks = new ReferenceOpenHashSet<>();
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private final List<BlockPos> toBreak = new ArrayList<>();
    private final List<Runnable> afterWalk = new ArrayList<>();
    /** A player's block is removed before its drops are worked out, so this remembers it until the end of the tick. */
    private final LongOpenHashSet damagedJustNow = new LongOpenHashSet();
    /** Chunks that were unloaded with pipes in them, until they are loaded again: a pipe that ends at their edge runs on past loaded land, even where the world still answers for the chunk. */
    private final LongOpenHashSet unloadedChunks = new LongOpenHashSet();
    private final Long2LongOpenHashMap valveClicks = new Long2LongOpenHashMap();
    private long lastCalendarTick = Long.MIN_VALUE;

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

    /** @return {@code false} if the valve here was turned less than a second ago, and so is not to be turned again; otherwise notes the turn */
    public boolean mayTurnValve(ServerLevel level, BlockPos pos)
    {
        final long now = level.getGameTime();
        final long last = valveClicks.getOrDefault(pos.asLong(), Long.MIN_VALUE / 2);
        if (now >= last && now - last < VALVE_COOLDOWN_TICKS)
        {
            return false;
        }
        valveClicks.put(pos.asLong(), now);
        if (valveClicks.size() > VALVE_CLICKS_KEPT)
        {
            valveClicks.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() >= VALVE_COOLDOWN_TICKS);
        }
        return true;
    }

    public void onChunkLoad(LevelChunk chunk)
    {
        unloadedChunks.remove(chunk.getPos().toLong());
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
        if (chunk.hasData(GasAttachments.PIPE_CHUNK) && !chunk.getData(GasAttachments.PIPE_CHUNK).segments().isEmpty())
        {
            unloadedChunks.add(chunk.getPos().toLong());
            chunk.setUnsaved(true); // The shares that the dissolving hands out are what the chunk must save, whatever it was marked as before
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

        // Whatever it joins is found by the rebuild; here only a pipe put between two different liquids is noticed, which joins neither of them for good
        String first = "";
        for (Direction side : Direction.values())
        {
            final @Nullable PipeSegment near = joinedAcross(level, segment, state, member, side);
            final String liquid = near == null ? "" : liquidOf(near);
            if (!liquid.isEmpty() && !first.equals(liquid))
            {
                if (first.isEmpty())
                {
                    first = liquid;
                }
                else
                {
                    PipeNetwork.hiss(level, pos);
                    break;
                }
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
        if (segment.integrity <= KEEPS_ABOVE)
        {
            damagedJustNow.add(pos.asLong());
        }
        final LevelChunk chunk = level.getChunkAt(pos);
        chunk.getData(GasAttachments.PIPE_CHUNK).remove(pos);
        chunk.setUnsaved(true);

        final LevelGasVolume world = GasTracker.get(level).volume(level);
        if (segment.network != null)
        {
            final PipeNetwork network = segment.network;
            blowDown(level, world, network.contents(), network.volume(), pos); // A hole in a pipe lets out everything in the run
            dissolve(level, network);
        }
        else
        {
            blowDown(level, world, segment.stored, segment.volume, pos); // Between a rebuild and the next it is the segment that holds its share
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
        damagedJustNow.clear();
        // On the ticks of TFC's calendar, which stop on an empty server: boilers do not go on heating, nor chambers on working, in chunks that a server keeps loaded while the fires under them stand still. A jump of the calendar, as in sleep, is still one step of a second.
        final long tick = Calendars.SERVER.getTicks();
        final boolean calendarMoved = tick != lastCalendarTick;
        lastCalendarTick = tick;
        if (!dirty.isEmpty())
        {
            rebuild(level);
        }
        if (networks.isEmpty())
        {
            return;
        }

        for (PipeNetwork network : calendarMoved ? networks : List.<PipeNetwork>of())
        {
            if (Math.floorMod(tick + network.segments().getFirst().pos.hashCode(), INTERVAL_TICKS) == 0)
            {
                if (network.truncated() && missingChunkIsBack(level, network))
                {
                    later(() -> dissolve(level, network)); // Formed again at the next rebuild, now with what lies beyond
                }
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

    private boolean chunkAvailable(ServerLevel level, long chunk)
    {
        return !unloadedChunks.contains(chunk) && level.getChunkSource().hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
    }

    private boolean missingChunkIsBack(ServerLevel level, PipeNetwork network)
    {
        for (long chunk : network.missingChunks())
        {
            if (chunkAvailable(level, chunk))
            {
                return true;
            }
        }
        return false;
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
                if (tryAttach(level, start))
                {
                    segmentsLeft--;
                    continue;
                }
                final List<PipeSegment> found = connected(level, start);
                segmentsLeft -= found.size();
                for (PipeSegment segment : found)
                {
                    dirty.remove(segment.pos.asLong());
                }
                if (!found.isEmpty())
                {
                    networks.add(new PipeNetwork(level, found, missingChunks(level, found)));
                }
            }
        }
    }

    /**
     * A pipe put in beside a network, and joined to no other, is taken into it without taking it apart: with a long main this saves going through every block of it for the sake of one more. This is done only where the result is the same as a rebuild would give: every pipe it joins must be in the same formed network, and it must not bring a different liquid to the liquid in that network.
     * <p>
     * Left to the rebuild: a pipe that joins pipes not yet formed (a row put in at once is flooded in one pass), one that joins two or more networks (their contents would have to be merged and the liquids sorted out), and one that brings a different liquid into a network that holds one.
     */
    private boolean tryAttach(ServerLevel level, PipeSegment start)
    {
        final BlockState state = level.getBlockState(start.pos);
        if (!(state.getBlock() instanceof PipeMember member))
        {
            return false;
        }
        @Nullable PipeNetwork only = null;
        for (Direction side : Direction.values())
        {
            final @Nullable PipeSegment near = joinedAcross(level, start, state, member, side);
            if (near != null)
            {
                if (near.network == null || (only != null && only != near.network))
                {
                    return false;
                }
                only = near.network;
            }
        }
        if (only == null || only.truncated())
        {
            return false; // A line that ran on past loaded land is worked out afresh: what it joins may be what it was missing
        }
        final String liquid = liquidOf(start), held = only.contents().litres >= PipeContents.FILM_LITRES ? only.contents().liquid : "";
        if (!liquid.isEmpty() && !held.isEmpty() && !liquid.equals(held))
        {
            return false;
        }
        final LongOpenHashSet missing = new LongOpenHashSet();
        addMissing(level, start, missing);
        only.attach(level, start, missing);
        return true;
    }

    /** The liquid in a pipe: a network's, or the share a pipe holds while it is between networks. */
    static String liquidOf(PipeSegment segment)
    {
        final PipeContents contents = segment.network != null ? segment.network.contents() : segment.stored;
        return contents.litres >= PipeContents.FILM_LITRES ? contents.liquid : ""; // A film is the wet of the walls, and keeps no other liquid out
    }

    /** @return the pipe beside this one on that side if they are open to each other, and both are blocks of a network */
    @Nullable
    private PipeSegment joinedAcross(ServerLevel level, PipeSegment segment, BlockState state, PipeMember member, Direction side)
    {
        final BlockPos near = segment.pos.relative(side);
        final @Nullable PipeSegment next = segments.get(near.asLong());
        if (next == null || !member.connects(state, side))
        {
            return null;
        }
        final BlockState nearState = level.getBlockState(near);
        return nearState.getBlock() instanceof PipeMember nearMember && nearMember.connects(nearState, side.getOpposite()) ? next : null;
    }

    private List<PipeSegment> joinedTo(ServerLevel level, PipeSegment segment)
    {
        final BlockState state = level.getBlockState(segment.pos);
        if (!(state.getBlock() instanceof PipeMember member))
        {
            return List.of();
        }
        final List<PipeSegment> joined = new ArrayList<>(6);
        for (Direction side : Direction.values())
        {
            final @Nullable PipeSegment next = joinedAcross(level, segment, state, member, side);
            if (next != null)
            {
                joined.add(next);
            }
        }
        return joined;
    }

    /** @return every segment joined to this one that holds the same liquid, or nothing if its block has gone missing; the networks among them are dissolved */
    private List<PipeSegment> connected(ServerLevel level, PipeSegment start)
    {
        if (!(level.getBlockState(start.pos).getBlock() instanceof PipeMember))
        {
            forget(level, start);
            return List.of();
        }
        final List<PipeSegment> found = LiquidRuns.network(start, segment -> joinedTo(level, segment), PipeTracker::liquidOf);
        for (PipeSegment segment : found)
        {
            dissolve(level, segment.network); // A network already standing next to this, from a chunk that loaded earlier, joins it
        }
        return found;
    }

    /** @return the chunks that pipes of these segments lead into, that are not loaded */
    private LongOpenHashSet missingChunks(ServerLevel level, List<PipeSegment> found)
    {
        final LongOpenHashSet missing = new LongOpenHashSet();
        for (PipeSegment segment : found)
        {
            addMissing(level, segment, missing);
        }
        return missing;
    }

    private void addMissing(ServerLevel level, PipeSegment segment, LongOpenHashSet missing)
    {
        final int x = segment.pos.getX() & 15, z = segment.pos.getZ() & 15;
        if (x != 0 && x != 15 && z != 0 && z != 15)
        {
            return; // Not by a border: its sides all look into its own chunk
        }
        final BlockState state = level.getBlockState(segment.pos);
        if (!(state.getBlock() instanceof PipeMember member))
        {
            return;
        }
        final long own = ChunkPos.asLong(segment.pos);
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            final BlockPos near = segment.pos.relative(side);
            final long chunk = ChunkPos.asLong(near);
            if (chunk != own && member.reaches(state, side) && !segments.containsKey(near.asLong()) && !chunkAvailable(level, chunk))
            {
                missing.add(chunk);
            }
        }
    }

    /** The block is not there any more, for example after a chunk was edited from outside. */
    private void forget(ServerLevel level, PipeSegment segment)
    {
        segments.remove(segment.pos.asLong());
        level.getChunkAt(segment.pos).getData(GasAttachments.PIPE_CHUNK).remove(segment.pos);
    }

    /**
     * All that is in a network comes out at one hole: its gas into the air there, under its full energy if it was under pressure, and its liquid over the ground. A boiler, whose hot water flashes to steam at once, adds that energy to the blast and scalds whoever is near.
     */
    private void blowDown(ServerLevel level, LevelGasVolume world, PipeContents contents, float volume, BlockPos hole)
    {
        final float pressure = contents.pressure(volume);
        final float gasKilojoules = PipePhysics.burstEnergy(pressure, contents.freeVolume(volume));
        final float flashKilojoules = contents.isWater() ? PipePhysics.flashEnergy(contents.litres, contents.temperature) : 0f;

        final @Nullable Fluid fluid = PipeNetwork.fluidOf(contents.liquid), runoffFluid = PipeNetwork.fluidOf(contents.runoffLiquid);
        final float litres = fluid == null ? 0f : contents.litres, runoff = runoffFluid == null ? 0f : contents.runoff; // Read before the take below empties them
        final PipeContents released = contents.take(1f);
        PipeNetwork.release(world, hole, released);
        contents.temperature = released.temperature;
        contents.air = PipePhysics.moles(1f, Math.max(contents.temperature, 1f), volume);

        final boolean corrosive = (litres > 0f && fluid.is(Chemistry.CORROSIVE)) || (runoff > 0f && runoffFluid.is(Chemistry.CORROSIVE));
        if (litres >= 1000f)
        {
            spill(level, hole, fluid, Math.min((int) (litres / 1000f), MAX_SPILLED_BLOCKS));
        }
        if (runoff >= 1000f)
        {
            spill(level, hole, runoffFluid, Math.min((int) (runoff / 1000f), MAX_SPILLED_BLOCKS));
        }
        if (corrosive)
        {
            CorrosiveSpill.at(level, hole); // Any of it, even less than a block, splashes the people by the hole
        }
        if (flashKilojoules >= PipePhysics.SCALD_MINIMUM_KILOJOULES)
        {
            SteamBurst.at(level, hole, flashKilojoules);
        }
        if (pressure - 1f >= BLOWDOWN_GAUGE_ATM)
        {
            PipeNetwork.hiss(level, hole);
            // The water of a boiler adds its flash to the blast, and the blast can be no stronger than the pressure the boiler burst at
            GasIgnition.rupture(level, hole, gasKilojoules + flashKilojoules, (pressure - 1f) * PipePhysics.PASCALS_PER_ATM / 1000f);
        }
    }

    /** Liquid that a joint of two networks would have lost: it runs out where the pipe is, as it would out of a broken one. */
    static void spillRejected(ServerLevel level, BlockPos pos, PipeContents rejected)
    {
        for (int part = 0; part < 2; part++)
        {
            final @Nullable Fluid fluid = PipeNetwork.fluidOf(part == 0 ? rejected.liquid : rejected.runoffLiquid);
            final float litres = part == 0 ? rejected.litres : rejected.runoff;
            if (fluid != null && litres > 0f)
            {
                if (litres >= 1000f)
                {
                    spill(level, pos, fluid, Math.min((int) (litres / 1000f), MAX_SPILLED_BLOCKS));
                }
                if (fluid.is(Chemistry.CORROSIVE))
                {
                    CorrosiveSpill.at(level, pos);
                }
            }
        }
    }

    /**
     * Puts one whole block of liquid down at the nearest place beyond this one that can take it, which is never this place itself.
     *
     * @return {@code false} if there is no such place
     */
    static boolean placeBlock(ServerLevel level, BlockPos outlet, Fluid fluid)
    {
        if (spill(level, outlet, fluid, 1) < 1)
        {
            return false;
        }
        if (fluid.is(Chemistry.CORROSIVE)) // A whole block of it on the ground splashes whoever is by it
        {
            CorrosiveSpill.at(level, outlet);
        }
        return true;
    }

    /**
     * Puts what ran out on the ground around a hole, the lowest and nearest places first. Less than a whole block soaks away.
     *
     * @return the number of blocks put down
     */
    private static int spill(ServerLevel level, BlockPos hole, Fluid fluid, int blocks)
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
        return blocks - left;
    }

    /** @return {@code true} if the wall here has corroded too far to give anything back when it is broken, whether or not it has just been broken */
    public boolean dropsNothing(BlockPos pos)
    {
        final @Nullable PipeSegment segment = segments.get(pos.asLong());
        return segment != null ? segment.integrity <= KEEPS_ABOVE : damagedJustNow.contains(pos.asLong());
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
