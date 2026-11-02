package tfcpp.mechanics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.dries007.tfc.common.blockentities.TFCBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.RotationSinkBlockEntity;
import net.dries007.tfc.util.rotation.NetworkAction;
import net.dries007.tfc.util.rotation.Node;
import net.dries007.tfc.util.rotation.Rotation;
import net.dries007.tfc.util.rotation.SinkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.instruments.Instruments;

/**
 * A centrifugal fan on a shaft, of the kind that replaced the ventilation furnace in the deep mines of the 1850s. It draws air along the spaces leading to it, so what collects in a working is carried out to its outlet.
 * <p>
 * The speed of the air through any part of a working is the flow of the fan over the cross section there: quick along a narrow drift, and hardly noticeable across a large chamber.
 */
public final class MineFanBlockEntity extends TFCBlockEntity implements RotationSinkBlockEntity
{
    /** How far a fan draws, in cells of open space walked each second. */
    public static final int REACH_CELLS = 512;
    private static final float MAX_MOVED = 0.9f;
    private static final int IDLE_INTERVAL_SECONDS = 5;
    private static final int UNSEEN = -2, WALL = -1;

    /** The spaces a walk has found, which may turn out to be one space reached in two ways, and which of them reach open air. */
    private static final class Spaces
    {
        private final IntArrayList parent = new IntArrayList();
        private final BooleanArrayList air = new BooleanArrayList();

        int count()
        {
            return parent.size();
        }

        int open()
        {
            parent.add(parent.size());
            air.add(false);
            return parent.size() - 1;
        }

        private int root(int space)
        {
            int at = space;
            while (parent.getInt(at) != at)
            {
                parent.set(at, parent.getInt(parent.getInt(at)));
                at = parent.getInt(at);
            }
            return at;
        }

        void join(int a, int b)
        {
            final int rootA = root(a), rootB = root(b);
            if (rootA != rootB)
            {
                parent.set(rootB, rootA);
                air.set(rootA, air.getBoolean(rootA) || air.getBoolean(rootB));
            }
        }

        void reachesAir(int space)
        {
            air.set(root(space), true);
        }

        boolean hasAir(int space)
        {
            return air.getBoolean(root(space));
        }
    }

    /** What the last walk of the fan found. Not saved: the fan walks again within seconds of its shaft turning. */
    public enum Status
    {
        UNKNOWN,
        /** There is air for the fan to draw: a way from the space it draws on to open air. */
        DRAWING,
        /** The space it draws on has no way to open air other than the way the fan blows into, so there is nothing to bring the air: the fan only stirs what is in it. */
        NO_INTAKE,
        /** Nowhere to blow to. */
        BLOCKED;

        public String translationKey()
        {
            return "tfcpp.mine_fan." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final SinkNode node;
    private int idleSeconds;
    private Status status = Status.UNKNOWN;

    public MineFanBlockEntity(BlockPos pos, BlockState state)
    {
        super(Instruments.MINE_FAN_ENTITY.get(), pos, state);
        this.node = new SinkNode(pos, state.getValue(HorizontalDirectionalBlock.FACING).getOpposite())
        {
            @Override
            public String toString()
            {
                return "MineFan[pos=%s]".formatted(pos());
            }
        };
    }

    @Override
    public Node getRotationNode()
    {
        return node;
    }

    public Status status()
    {
        return status;
    }

    @Override
    protected void onLoadAdditional()
    {
        performNetworkAction(NetworkAction.ADD);
    }

    @Override
    protected void onUnloadAdditional()
    {
        performNetworkAction(NetworkAction.REMOVE);
    }

    /**
     * @param requestWalk asked only when this fan means to walk, and says {@code false} if the budget of the tick is spent, in which case the fan is served at an early tick and the air waits
     * @return the torque this takes from the shaft
     */
    public float tickFan(ServerLevel level, float seconds, BooleanSupplier requestWalk)
    {
        final @Nullable Rotation rotation = node.rotation();
        final float speed = rotation == null ? 0 : rotation.speed() * ShaftPhysics.TICKS_PER_SECOND;
        if (speed != 0)
        {
            if (idleSeconds > 0)
            {
                idleSeconds -= (int) seconds;
            }
            else if (requestWalk.getAsBoolean())
            {
                idleSeconds = moveAir(level, speed, seconds) ? 0 : IDLE_INTERVAL_SECONDS; // Nothing is walked while the air is clean
            }
        }
        return ShaftPhysics.fanTorque(speed);
    }

    /**
     * Walks the open space outward from the intake, one shell of cells at a time, and moves gas one shell closer. The nearest shell goes first, so that gas moves one shell per step and is not carried all the way at once. That limits it to about 1 m/s, which is as fast as air moved in any working these fans served.
     *
     * Air only moves where there is air to replace it. Every space the fan draws on is judged by itself: the walk has to find in it, other than through the outlet, a cell that is open to the sky, or find it too large for the reach of the fan to walk all of (which is taken to reach one). The open air above a fan in the roof of a room is not an intake of the room. A room with no other opening, or a dead end of a heading, is not ventilated by a fan: it was a second shaft that made a mine ventilable, a rule that was written into the law after the Hartley Colliery disaster of 1862, and a fan on a single shaft only stirs the air. Then nothing is moved and the status says so.
     *
     * @return {@code true} if there was any gas to move
     */
    public boolean moveAir(ServerLevel level, float speed, float seconds)
    {
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final BlockPos outlet = worldPosition.relative(facing);
        if (!volume.isPassable(outlet.getX(), outlet.getY(), outlet.getZ()))
        {
            status = Status.BLOCKED;
            return false; // Nowhere to blow to
        }

        record Cell(BlockPos pos, @Nullable BlockPos next, int space) {}
        final Spaces spaces = new Spaces();
        final List<List<Cell>> shells = new ArrayList<>();
        final Long2IntOpenHashMap seen = new Long2IntOpenHashMap();
        seen.defaultReturnValue(UNSEEN);
        seen.put(worldPosition.asLong(), WALL);
        seen.put(outlet.asLong(), WALL);
        seen.put(worldPosition.relative(facing.getOpposite()).asLong(), WALL); // The shaft

        // Each side of the fan draws on a space of its own, until the walk finds that two of them are one. The one above the fan in a roof is the sky, and the one below it is the room, and the sky does not bring air to the room
        List<Cell> shell = new ArrayList<>();
        for (Direction side : Direction.values())
        {
            final BlockPos pos = worldPosition.relative(side);
            if (seen.get(pos.asLong()) != UNSEEN)
            {
                continue;
            }
            if (!volume.isPassable(pos.getX(), pos.getY(), pos.getZ()))
            {
                seen.put(pos.asLong(), WALL);
                continue;
            }
            if (volume.isOpenToSky(pos.getX(), pos.getY(), pos.getZ()))
            {
                seen.put(pos.asLong(), WALL); // The open air beside the fan is not a space to ventilate, and brings no air to any other
                continue;
            }
            final int space = spaces.open();
            seen.put(pos.asLong(), space);
            shell.add(new Cell(pos, null, space));
        }

        int walked = shell.size();
        while (!shell.isEmpty())
        {
            shells.add(shell);
            final List<Cell> next = new ArrayList<>();
            for (Cell cell : shell)
            {
                for (Direction side : Direction.values())
                {
                    final BlockPos pos = cell.pos.relative(side);
                    final int known = seen.get(pos.asLong());
                    if (known == WALL)
                    {
                        continue;
                    }
                    if (known >= 0)
                    {
                        spaces.join(cell.space, known); // Two ways in from the fan lead into the same space
                        continue;
                    }
                    if (!volume.isPassable(pos.getX(), pos.getY(), pos.getZ()))
                    {
                        seen.put(pos.asLong(), WALL);
                    }
                    else if (volume.isOpenToSky(pos.getX(), pos.getY(), pos.getZ()))
                    {
                        spaces.reachesAir(cell.space); // Open air is not walked into: its gas is the atmosphere's
                        seen.put(pos.asLong(), WALL);
                    }
                    else if (walked < REACH_CELLS)
                    {
                        seen.put(pos.asLong(), cell.space);
                        next.add(new Cell(pos, cell.pos, cell.space));
                        walked++;
                    }
                    else
                    {
                        spaces.reachesAir(cell.space); // More space than the fan can walk: one volume it cannot see the end of
                    }
                }
            }
            shell = next;
        }

        // Only the spaces that have air to come from are moved
        final List<List<Cell>> served = new ArrayList<>();
        for (List<Cell> walkedShell : shells)
        {
            final List<Cell> kept = new ArrayList<>();
            for (Cell cell : walkedShell)
            {
                if (spaces.hasAir(cell.space))
                {
                    kept.add(cell);
                }
            }
            if (!kept.isEmpty())
            {
                served.add(kept);
            }
        }
        if (served.isEmpty() && spaces.count() > 0)
        {
            status = Status.NO_INTAKE;
            return false; // The fan turns in a closed space, and only churns it
        }
        status = Status.DRAWING;

        boolean any = false;
        final float flow = ShaftPhysics.fanFlow(speed) * seconds;
        final float[] moved = new float[Gas.COUNT];
        for (int i = 0; i < served.size(); i++)
        {
            final float share = Math.min(flow / served.get(i).size(), MAX_MOVED);
            for (Cell cell : served.get(i))
            {
                final @Nullable float[] from = volume.cell(cell.pos.getX(), cell.pos.getY(), cell.pos.getZ());
                if (from == null || GasMath.mobileContent(from) <= 0)
                {
                    continue;
                }
                any = true;
                if (cell.next == null)
                {
                    for (int gas = 0; gas < Gas.COUNT; gas++)
                    {
                        moved[gas] = Gas.VALUES[gas].mobile() ? from[gas] * share : 0f; // A fan does not lift settled dust
                        from[gas] -= moved[gas];
                    }
                    GasEmission.addToPlume(volume, outlet.getX(), outlet.getY(), outlet.getZ(), moved);
                }
                else
                {
                    final @Nullable float[] existing = volume.cell(cell.next.getX(), cell.next.getY(), cell.next.getZ());
                    final @Nullable float[] to = existing != null ? existing : volume.createCell(cell.next.getX(), cell.next.getY(), cell.next.getZ());
                    if (to != null)
                    {
                        for (int gas = 0; gas < Gas.COUNT; gas++)
                        {
                            final float amount = Gas.VALUES[gas].mobile() ? from[gas] * share : 0f;
                            from[gas] -= amount;
                            to[gas] += amount;
                        }
                        volume.markChanged(cell.next.getX(), cell.next.getY(), cell.next.getZ());
                    }
                }
                volume.markChanged(cell.pos.getX(), cell.pos.getY(), cell.pos.getZ());
            }
        }
        return any;
    }
}
