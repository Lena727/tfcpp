package tfcpp.health;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
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

/**
 * A covered pit: the block is the wooden lid over a lined hole of a cubic metre. People who spend their time within 16 blocks of it leave their waste in it. An empty hand or a shovel on the lid takes the contents out as night soil.
 */
public final class CesspitBlock extends Block implements EntityBlock
{
    /** The most pieces a single use takes: 16, which is 320 litres. */
    private static final int MOST_PER_USE = 16;

    public CesspitBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CesspitBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving)
    {
        if (level instanceof ServerLevel server && !oldState.is(this))
        {
            Cesspits.register(server, pos);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving)
    {
        if (level instanceof ServerLevel server && !state.is(newState.getBlock()))
        {
            if (level.getBlockEntity(pos) instanceof CesspitBlockEntity pit)
            {
                pit.spill(server, piece -> Block.popResource(level, pos, piece));
            }
            Cesspits.unregister(server, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!stack.is(ItemTags.SHOVELS))
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        empty(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!player.getMainHandItem().isEmpty())
        {
            return InteractionResult.PASS; // A block placed on the lid, or anything else in the hand, is not a request to empty the pit
        }
        empty(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void empty(Level level, BlockPos pos, Player player)
    {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof CesspitBlockEntity pit)
        {
            final int pieces = pit.takePieces(server, MOST_PER_USE, piece -> player.getInventory().placeItemBackInInventory(piece));
            if (pieces > 0)
            {
                level.playSound(null, pos, SoundEvents.MUD_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
            }
        }
    }
}
