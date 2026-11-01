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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipeMember;
import tfcpp.pneumatics.pipes.PipePort;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * A fire-brick coke oven. It takes heat from a forge, a fire or a gas burner below, and its gas goes up into a pipe above it, which is the only side it joins.
 */
public final class CokeOvenBlock extends Block implements EntityBlock, PipeMember, PipePort
{
    /** The free space above a loaded charge in a chamber a metre across. */
    private static final float VOLUME = 0.5f;

    public CokeOvenBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public float volume(BlockState state)
    {
        return VOLUME;
    }

    /** Fire brick holds hot gas and every acid, but it is brittle, and its doors and joints give way at a few atmospheres. */
    @Override
    public PipeMaterial material(BlockState state)
    {
        return PipeMaterial.CERAMIC;
    }

    @Override
    public boolean connects(BlockState state, Direction side)
    {
        return side == Direction.UP;
    }

    /** The oven only joins a network. Its gas is put in as it is made, by the oven itself, whether or not there is a pipe. */
    @Override
    public void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds) {}

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CokeOvenBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide || type != Chemistry.COKE_OVEN_ENTITY.get() ? null : (BlockEntityTicker<T>) (BlockEntityTicker<CokeOvenBlockEntity>) CokeOvenBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof CokeOvenBlockEntity oven) || !oven.canUse(stack))
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide)
        {
            oven.interact(stack, player, hand);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand reads the oven, and an empty hand while crouching takes out what it made. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.getBlockEntity(pos) instanceof CokeOvenBlockEntity oven && !level.isClientSide)
        {
            if (player.isShiftKeyDown())
            {
                oven.takeProduct(player);
            }
            else
            {
                oven.showStatus(player);
            }
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
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CokeOvenBlockEntity oven)
        {
            oven.dropContents();
        }
        PipeTracker.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
