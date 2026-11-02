package tfcpp.chemistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipeMember;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePort;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * A fire-clay trough in a run of pipe that burns sulfur in the air of the pipe. It is open on top, where the sulfur is put in, and joins pipe on every other side.
 */
public final class SulfurBurnerBlock extends Block implements EntityBlock, PipeMember, PipePort
{
    /** About a tenth of a cubic metre: a trough, not a room. */
    private static final float VOLUME = 0.1f;

    public SulfurBurnerBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public float volume(BlockState state)
    {
        return VOLUME;
    }

    /** Fire-clay takes the hot flue of sulfur, and no acid of it, which is why a burner is clay and not iron. */
    @Override
    public PipeMaterial material(BlockState state)
    {
        return PipeMaterial.CERAMIC;
    }

    /** Open on the top, where the sulfur is put in: a pipe above it does not join. */
    @Override
    public boolean connects(BlockState state, Direction side)
    {
        return side != Direction.UP;
    }

    @Override
    public void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds)
    {
        if (level.getBlockEntity(pos) instanceof SulfurBurnerBlockEntity burner)
        {
            burner.step(network, seconds);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new SulfurBurnerBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!stack.is(SulfurBurnerBlockEntity.sulfurItem()) || !(level.getBlockEntity(pos) instanceof SulfurBurnerBlockEntity burner))
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide)
        {
            burner.addPowder(stack, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand reads the burner. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.getBlockEntity(pos) instanceof SulfurBurnerBlockEntity burner && !level.isClientSide)
        {
            burner.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SulfurBurnerBlockEntity burner)
        {
            burner.dropContents();
        }
        PipeTracker.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
