package tfcpp.pneumatics.pipes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** A block of a network that does something each second. */
public interface PipePort
{
    void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds);
}
