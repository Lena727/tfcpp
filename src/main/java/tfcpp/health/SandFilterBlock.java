package tfcpp.health;

import net.dries007.tfc.common.fluids.FluidHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * A slow sand filter of a cubic metre: a jug or bucket pours water in at the top and draws it from a side, and pipes and pumps do the same through the capability of the faces. See {@link SandFilterBlockEntity}.
 */
public final class SandFilterBlock extends Block implements EntityBlock
{
    public SandFilterBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new SandFilterBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving)
    {
        if (level instanceof net.minecraft.server.level.ServerLevel server && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SandFilterBlockEntity filter)
        {
            filter.spill(server);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (level.getBlockEntity(pos) instanceof SandFilterBlockEntity filter && stack.getCapability(Capabilities.FluidHandler.ITEM) != null)
        {
            if (level.isClientSide)
            {
                return ItemInteractionResult.sidedSuccess(true); // The server decides whether anything moved; the client only plays along
            }
            if (FluidHelpers.transferBetweenBlockHandlerAndItem(stack, filter.handler(hit.getDirection()), level, pos, FluidHelpers.with(player, hand)))
            {
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
