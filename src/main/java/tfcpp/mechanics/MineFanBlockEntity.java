package tfcpp.mechanics;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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

    private final SinkNode node;
    private int idleSeconds;

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
     * @param mayWalk {@code false} if this tick's budget is spent, in which case the air waits for the next turn
     * @return the torque this takes from the shaft
     */
    public float tickFan(ServerLevel level, float seconds, boolean mayWalk)
    {
        final @Nullable Rotation rotation = node.rotation();
        final float speed = rotation == null ? 0 : rotation.speed() * ShaftPhysics.TICKS_PER_SECOND;
        if (speed != 0)
        {
            if (idleSeconds > 0)
            {
                idleSeconds -= (int) seconds;
            }
            else if (mayWalk)
            {
                idleSeconds = moveAir(level, speed, seconds) ? 0 : IDLE_INTERVAL_SECONDS; // Nothing is walked while the air is clean
            }
        }
        return ShaftPhysics.fanTorque(speed);
    }

    /**
     * Walks the open space outward from the intake, one shell of cells at a time, and moves gas one shell closer. The nearest shell goes first, so that gas moves one shell per step and is not carried all the way at once. That limits it to about 1 m/s, which is as fast as air moved in any working these fans served.
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
            return false; // Nowhere to blow to
        }

        record Cell(BlockPos pos, @Nullable BlockPos next) {}
        final List<List<Cell>> shells = new ArrayList<>();
        final LongOpenHashSet seen = new LongOpenHashSet();
        seen.add(worldPosition.asLong());
        seen.add(outlet.asLong());
        seen.add(worldPosition.relative(facing.getOpposite()).asLong()); // The shaft

        List<Cell> shell = new ArrayList<>();
        for (Direction side : Direction.values())
        {
            final BlockPos pos = worldPosition.relative(side);
            if (seen.add(pos.asLong()) && volume.isPassable(pos.getX(), pos.getY(), pos.getZ()))
            {
                shell.add(new Cell(pos, null));
            }
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
                    if (walked < REACH_CELLS && seen.add(pos.asLong()) && !volume.isOpenToSky(pos.getX(), pos.getY(), pos.getZ()) && volume.isPassable(pos.getX(), pos.getY(), pos.getZ()))
                    {
                        next.add(new Cell(pos, cell.pos));
                        walked++;
                    }
                }
            }
            shell = next;
        }

        boolean any = false;
        final float flow = ShaftPhysics.fanFlow(speed) * seconds;
        final float[] moved = new float[Gas.COUNT];
        for (int i = 0; i < shells.size(); i++)
        {
            final float share = Math.min(flow / shells.get(i).size(), MAX_MOVED);
            for (Cell cell : shells.get(i))
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
