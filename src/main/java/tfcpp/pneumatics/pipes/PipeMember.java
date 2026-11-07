package tfcpp.pneumatics.pipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** A block that is part of a pipe network. */
public interface PipeMember
{
    /** @return cubic metres inside this */
    float volume(BlockState state);

    /** @return what the walls are made of */
    PipeMaterial material(BlockState state);

    /** @return {@code true} if this opens toward that side, which it needs to do as well as the block on that side, to join it */
    boolean connects(BlockState state, Direction side);

    /**
     * Whether this has a joint on that side, as far as it can tell without looking at what is there. A pipe knows from the arms it has grown; the other blocks open wherever {@link #connects} says. It tells the network whether a pipe may run on into a chunk that is not loaded.
     */
    default boolean reaches(BlockState state, Direction side)
    {
        return connects(state, side);
    }

    /** @return kW per kelvin lost to the air around this */
    default float heatLoss(BlockState state)
    {
        return 0.005f;
    }

    /**
     * A block that joins its network on every side still keeps some liquid in a tank of its own, which is not in the network at all. A piston pump on a side that opens to this takes from that tank first, when the tank holds anything, and from the network otherwise. Off by default, so that the other blocks keep what the pump has always drawn from them.
     *
     * @return {@code true} if the tank of this block is what a pump on its open side draws from
     */
    default boolean tankOpenToPump(BlockState state)
    {
        return false;
    }

    /**
     * A block that keeps liquid in a tank of its own wears under that liquid as a pipe does under what it carries, and the network cannot see it. The network adds what this gives to the wear of the wall of the block, on top of its own, over the same calendar days.
     *
     * @param kelvin the temperature of the network, which is the temperature of the acid on the walls
     * @return share of the wall of this block lost per calendar day to the liquid in its tank, in the units of {@link PipeMaterial#corrosion(String, float)}: that rate in a full tank, in proportion to how full the tank is
     */
    default float tankCorrosion(ServerLevel level, BlockPos pos, BlockState state, float kelvin)
    {
        return 0f;
    }
}
