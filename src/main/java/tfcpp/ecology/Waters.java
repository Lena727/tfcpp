package tfcpp.ecology;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Where something that is let out meets water, and the way to ask what the water at a place holds: the entry for pipes and vessels, and the query for the stages that use it (drinking, irrigation, colour).
 */
public final class Waters
{
    /** The kinds of water, as the fluid says: river water is TFC's river fluid, salt water is the sea, a spring is TFC's spring water (a small body of its own, a still cell like a lake), and any other fresh water is still water, which is a lake only in a TFC lake biome. */
    public enum Kind
    {
        RIVER, STILL, SPRING, SEA
    }

    /** Water that a liquid meets: where it is and what kind. */
    public record Meeting(BlockPos pos, Kind kind)
    {
    }

    /** How far a liquid that is let out falls through open air before it is taken to have landed on the ground. */
    private static final int FALL_REACH = 32;

    private Waters() {}

    /** @return the kind of water that this fluid is, or {@code null} if it is not water */
    @Nullable
    public static Kind kindOf(FluidState fluid)
    {
        if (fluid.isEmpty())
        {
            return null;
        }
        if (fluid.is(TFCTags.Fluids.SALT_WATER))
        {
            return Kind.SEA;
        }
        if (fluid.getType() == TFCFluids.RIVER_WATER.get()) // Not isSame: TFC's river water answers true for vanilla water too
        {
            return Kind.RIVER;
        }
        if (fluid.getType() == TFCFluids.SPRING_WATER.getSource() || fluid.getType() == TFCFluids.SPRING_WATER.getFlowing())
        {
            return Kind.SPRING;
        }
        return fluid.is(FluidTags.WATER) ? Kind.STILL : null;
    }

    /**
     * Whether a liquid let out at this position meets water: the block it comes out in is water, or the liquid falls through the open air under it onto water, or it would spill sideways into water. A chemical that leaves a pipe into a river goes into the river and not into the ground under it. Only blocks of loaded chunks are looked at.
     *
     * @return the first water it meets, or {@code null} if it reaches the ground
     */
    @Nullable
    public static Meeting meeting(ServerLevel level, BlockPos pos)
    {
        final @Nullable Meeting here = water(level, pos);
        if (here != null)
        {
            return here;
        }
        final BlockPos.MutableBlockPos fall = pos.mutable();
        for (int depth = 0; depth < FALL_REACH; depth++)
        {
            fall.move(Direction.DOWN);
            if (!level.isLoaded(fall))
            {
                break;
            }
            final BlockState state = level.getBlockState(fall);
            if (!state.getFluidState().isEmpty())
            {
                final @Nullable Meeting landed = water(level, fall);
                if (landed != null)
                {
                    return landed;
                }
                break; // Some other liquid lies under it
            }
            if (!state.getCollisionShape(level, fall).isEmpty())
            {
                break; // The ground
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            final @Nullable Meeting beside = water(level, pos.relative(side));
            if (beside != null)
            {
                return beside;
            }
        }
        return null;
    }

    @Nullable
    private static Meeting water(ServerLevel level, BlockPos pos)
    {
        if (!level.isLoaded(pos))
        {
            return null;
        }
        final @Nullable Kind kind = kindOf(level.getFluidState(pos));
        if (kind == null)
        {
            return null;
        }
        if (kind == Kind.STILL && !LevelWaters.get(level).isLake(level, level.getChunkAt(pos)))
        {
            return null; // A puddle, a bucket of water, a pocket in a cave: not a body of water here, and the spill goes on to the soil under it
        }
        return new Meeting(pos.immutable(), kind);
    }

    /**
     * The water body at a position, with what it holds now, for drinking, irrigation and colour. Cheap to call again and again: the mapping from a chunk to its body is kept in memory, and the figures are read from the ledger.
     *
     * @return {@code null} if there is no water body at the position: no water, a puddle or other still water outside a lake biome, or river water that is not near any river of the graph. A river or a lake whose place the river graph has not answered for yet is {@link WaterBody#resolved()} false, with zero figures
     */
    @Nullable
    public static WaterBody at(ServerLevel level, BlockPos pos)
    {
        final @Nullable Meeting meeting = level.isLoaded(pos) ? water(level, pos) : null;
        if (meeting == null)
        {
            return null;
        }
        final Kind kind = meeting.kind();
        if (kind == Kind.SEA)
        {
            return WaterBody.sea();
        }
        return LevelWaters.get(level).bodyFor(level, kind == Kind.RIVER ? WaterRoute.DISCHARGE_RIVER : WaterRoute.DISCHARGE_STILL, pos.getX() >> 4, pos.getZ() >> 4);
    }
}
