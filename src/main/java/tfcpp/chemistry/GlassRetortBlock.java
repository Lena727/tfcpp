package tfcpp.chemistry;

import net.minecraft.core.BlockPos;
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

/**
 * A retort and receiver of borosilicate glass. It takes its heat from the block below it, which should be a sand bath, and a condenser must stand beside it for what boils off to be caught.
 * <p>
 * It is glass: the block breaks at once in the hand, and whatever is inside it is let out when it does.
 */
public final class GlassRetortBlock extends Block implements EntityBlock
{
    public GlassRetortBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new GlassRetortBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide || type != Chemistry.GLASS_RETORT_ENTITY.get() ? null : (BlockEntityTicker<T>) (BlockEntityTicker<GlassRetortBlockEntity>) GlassRetortBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof GlassRetortBlockEntity retort) || !retort.canUse(stack))
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide)
        {
            retort.interact(stack, player, hand);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand reads the retort, and an empty hand while crouching takes out the powder it left. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!player.getMainHandItem().isEmpty())
        {
            return InteractionResult.PASS; // What is in the hand is not for the retort: it may be placed beside it
        }
        if (level.getBlockEntity(pos) instanceof GlassRetortBlockEntity retort && !level.isClientSide)
        {
            if (player.isShiftKeyDown())
            {
                retort.takeProduct(player);
            }
            else
            {
                retort.showStatus(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving)
    {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof GlassRetortBlockEntity retort)
        {
            retort.spill(server);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
