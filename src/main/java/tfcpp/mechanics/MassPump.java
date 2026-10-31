package tfcpp.mechanics;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import net.dries007.tfc.common.blockentities.rotation.CrankshaftBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.PumpBlockEntity;
import net.dries007.tfc.common.blocks.DirectionPropertyBlock;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.AqueductBlock;
import net.dries007.tfc.common.blocks.rotation.FluidPumpBlock;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.util.rotation.Node;
import net.dries007.tfc.util.rotation.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * TFC's pump with the conservation of mass put back. It used to make water from nothing, leaving its source untouched. Here it takes a block of water from the far end of its pipe to its outlet, at the rate its crank can lift it, and it loads the shaft by the height it lifts.
 * <p>
 * A river does not run out for a pump, so taking river water leaves the river as it was.
 */
public final class MassPump
{
    private static final int MAX_PIPE_LENGTH = 16;
    private static final int MAX_RESERVOIR = 64;
    /** How far a pump finds water in the pool around its pipe end. */
    private static final int MAX_INTAKE_POOL = 256;
    /** The atmosphere can only hold up a column of water of about 10.3 m, so a pump at the top of a pipe cannot draw from further below. Deeper mines need pumps in stages, as they always did. */
    private static final int MAX_SUCTION_METRES = 10;

    private record Intake(BlockPos pos, Fluid fluid, boolean renewable) {}

    /** Part of a cubic metre already drawn up. Lost on restart, which at worst loses a little work and no water. */
    private static final Map<PumpBlockEntity, Float> PROGRESS = new WeakHashMap<>();

    @Nullable
    public static Node crankNode(ServerLevel level, PumpBlockEntity pump)
    {
        final Direction face = pump.getBlockState().getValue(FluidPumpBlock.FACING);
        final @Nullable CrankshaftBlockEntity crank = CrankshaftBlockEntity.getCrankShaftAt(level, pump.getBlockPos(), face.getOpposite());
        return crank == null ? null : crank.getRotationNode();
    }

    /** @return the torque this takes from the crank */
    public static float tick(ServerLevel level, PumpBlockEntity pump, Node crank, float seconds)
    {
        final @Nullable Rotation rotation = crank.rotation();
        final BlockPos pos = pump.getBlockPos();
        final @Nullable Intake intake = rotation == null ? null : findIntake(level, pos);
        if (intake == null)
        {
            return 0;
        }

        final BlockPos outlet = pos.relative(pump.getBlockState().getValue(FluidPumpBlock.FACING));
        final @Nullable BlockPos target = findOutlet(level, outlet, intake.fluid());
        if (target == null)
        {
            return 0; // Nowhere for the water to go, so the piston just holds it
        }

        final float speed = rotation.speed() * ShaftPhysics.TICKS_PER_SECOND;
        final float progress = PROGRESS.getOrDefault(pump, 0f) + ShaftPhysics.pumpedVolume(speed, seconds);
        if (progress >= 1f)
        {
            move(level, intake, target);
            PROGRESS.put(pump, progress - 1f);
        }
        else
        {
            PROGRESS.put(pump, progress);
        }
        return ShaftPhysics.pumpTorque(target.getY() - intake.pos().getY());
    }

    /** Moves one block of water, as long as there is one to move and somewhere to put it. */
    public static boolean moveOneBlock(ServerLevel level, BlockPos pump, BlockState state)
    {
        final @Nullable Intake intake = findIntake(level, pump);
        final @Nullable BlockPos target = intake == null ? null : findOutlet(level, pump.relative(state.getValue(FluidPumpBlock.FACING)), intake.fluid());
        if (target == null)
        {
            return false;
        }
        move(level, intake, target);
        return true;
    }

    private static void move(ServerLevel level, Intake intake, BlockPos target)
    {
        final @Nullable BlockState filled = FluidHelpers.fillWithFluid(level.getBlockState(target), intake.fluid());
        if (filled == null)
        {
            return;
        }
        if (!intake.renewable())
        {
            level.setBlockAndUpdate(intake.pos(), FluidHelpers.emptyFluidFrom(level.getBlockState(intake.pos())));
        }
        level.setBlockAndUpdate(target, filled);
        level.scheduleTick(target, intake.fluid(), intake.fluid().getTickDelay(level));
    }

    /** Walks the pipes down from the pump, to the first source block of liquid that an open pipe end stands in. */
    @Nullable
    private static Intake findIntake(ServerLevel level, BlockPos pump)
    {
        final BlockPos first = pump.below();
        if (!isPipe(level.getBlockState(first)))
        {
            return null;
        }

        record Path(BlockPos pos, int length) {}
        final Queue<Path> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        queue.add(new Path(first, 1));
        seen.add(first);

        while (!queue.isEmpty())
        {
            final Path path = queue.poll();
            final BlockState pipe = level.getBlockState(path.pos);
            for (Direction direction : Direction.values())
            {
                final BlockPos next = path.pos.relative(direction);
                if (!level.isLoaded(next) || seen.contains(next))
                {
                    continue;
                }
                final BlockState state = level.getBlockState(next);
                if (isPipe(state))
                {
                    seen.add(next);
                    if (path.length < MAX_PIPE_LENGTH)
                    {
                        queue.add(new Path(next, path.length + 1));
                    }
                }
                else if (pipe.getValue(DirectionPropertyBlock.getProperty(direction)) && pump.getY() - next.getY() <= MAX_SUCTION_METRES)
                {
                    // A pipe cannot hold river water, whose flow it would not keep, so a river is taken as plain water
                    if (state.getBlock() == TFCBlocks.RIVER_WATER.get())
                    {
                        return new Intake(next, Fluids.WATER, true);
                    }
                    if (!pipe.getFluidState().isEmpty() && FluidHelpers.isAirOrEmptyFluid(state) && state.getFluidState().getType().isSame(pipe.getFluidState().getType()))
                    {
                        final @Nullable BlockPos source = nearestSource(level, next, pipe.getFluidState().getType());
                        if (source != null)
                        {
                            return new Intake(source, pipe.getFluidState().getType(), false);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** The liquid at a pipe end may be flowing, once its own source has been drawn off and the pool runs in to fill the hole. */
    @Nullable
    private static BlockPos nearestSource(ServerLevel level, BlockPos start, Fluid fluid)
    {
        final Queue<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && seen.size() < MAX_INTAKE_POOL)
        {
            final BlockPos current = queue.poll();
            final BlockState state = level.getBlockState(current);
            if (state.getFluidState().isSourceOfType(fluid) && FluidHelpers.isAirOrEmptyFluid(state))
            {
                return current;
            }
            for (Direction direction : Direction.values())
            {
                final BlockPos next = current.relative(direction);
                if (level.isLoaded(next) && seen.add(next) && level.getFluidState(next).getType().isSame(fluid))
                {
                    queue.add(next);
                }
            }
        }
        return null;
    }

    /** @return where the next block of liquid goes: the outlet, or if that is full, the nearest free space at the same height of the pool it stands in */
    @Nullable
    private static BlockPos findOutlet(ServerLevel level, BlockPos outlet, Fluid fluid)
    {
        final BlockState state = level.getBlockState(outlet);
        if (state.getBlock() instanceof AqueductBlock)
        {
            return null;
        }
        if (!state.getFluidState().isSourceOfType(fluid))
        {
            return FluidHelpers.fillWithFluid(state, fluid) != null ? outlet : null;
        }

        final Queue<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        queue.add(outlet);
        seen.add(outlet);
        while (!queue.isEmpty() && seen.size() < MAX_RESERVOIR)
        {
            final BlockPos current = queue.poll();
            for (Direction direction : Direction.Plane.HORIZONTAL)
            {
                final BlockPos next = current.relative(direction);
                if (!level.isLoaded(next) || !seen.add(next))
                {
                    continue;
                }
                final BlockState at = level.getBlockState(next);
                if (at.getFluidState().isSourceOfType(fluid))
                {
                    queue.add(next);
                }
                else if ((at.isAir() || at.getFluidState().getType().isSame(fluid)) && FluidHelpers.fillWithFluid(at, fluid) != null && at.getBlock() != TFCBlocks.RIVER_WATER.get() && hasFloor(level, next, fluid))
                {
                    return next;
                }
            }
        }
        return null;
    }

    private static boolean hasFloor(ServerLevel level, BlockPos pos, Fluid fluid)
    {
        final BlockPos below = pos.below();
        final BlockState state = level.getBlockState(below);
        return state.getFluidState().isSourceOfType(fluid) || state.isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean isPipe(BlockState state)
    {
        return state.getBlock() == TFCBlocks.STEEL_PIPE.get();
    }

    private MassPump() {}
}
