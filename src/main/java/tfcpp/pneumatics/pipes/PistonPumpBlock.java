package tfcpp.pneumatics.pipes;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** Delivers out of the side it faces and takes in from the side opposite. The shaft comes in from the right, seen from behind. */
public final class PistonPumpBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    private static final MapCodec<PistonPumpBlock> CODEC = simpleCodec(PistonPumpBlock::new);

    public PistonPumpBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec()
    {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /** Says whether the pump will deliver. Only an empty hand does this, so that a block can still be put down against it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!player.getMainHandItem().isEmpty())
        {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof PistonPumpBlockEntity pump)
        {
            player.displayClientMessage(Component.translatable(pump.statusKey(server)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving)
    {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof PistonPumpBlockEntity pump)
        {
            pump.soakHeld(server);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new PistonPumpBlockEntity(pos, state);
    }
}
