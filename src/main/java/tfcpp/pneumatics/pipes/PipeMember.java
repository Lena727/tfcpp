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
}
