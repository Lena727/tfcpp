package tfcpp.pneumatics.pipes;

import net.minecraft.core.Direction;
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
}
