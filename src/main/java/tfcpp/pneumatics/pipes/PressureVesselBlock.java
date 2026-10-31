package tfcpp.pneumatics.pipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A riveted iron drum of half a cubic metre, which joins whatever pipes touch it. A fire beneath it heats everything in the network. */
public final class PressureVesselBlock extends Block implements PipeMember, EntityBlock
{
    private static final float VOLUME = 0.5f;

    public PressureVesselBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public float volume(BlockState state)
    {
        return VOLUME;
    }

    @Override
    public PipeMaterial material(BlockState state)
    {
        return PipeMaterial.WROUGHT_IRON;
    }

    @Override
    public boolean connects(BlockState state, Direction side)
    {
        return true;
    }

    @Override
    public float heatLoss(BlockState state)
    {
        return 0.03f;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new PressureVesselBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving)
    {
        super.onPlace(state, level, pos, oldState, isMoving);
        PipeTracker.placed(level, pos, state, oldState);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving)
    {
        PipeTracker.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
