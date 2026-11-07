package tfcpp.chemistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipeMember;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePort;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * A nozzle with a hand wheel that burns the gas of the pipe it is joined to under whatever stands on it. A flame is lit with flint and steel or a fire starter, and a lit burner is an open flame to any gas in the room.
 */
public final class GasBurnerBlock extends Block implements EntityBlock, PipeMember, PipePort
{
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int LIGHT_LEVEL = 12;
    private static final float VOLUME = 0.005f;

    public GasBurnerBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(OPEN, false).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(OPEN, LIT);
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

    /** Pipe comes in from any side but the top, which is where the flame is. */
    @Override
    public boolean connects(BlockState state, Direction side)
    {
        return side != Direction.UP;
    }

    @Override
    public void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds)
    {
        if (level.getBlockEntity(pos) instanceof GasBurnerBlockEntity burner)
        {
            burner.burn(network, level, pos, state, seconds);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new GasBurnerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide || type != Chemistry.GAS_BURNER_ENTITY.get() ? null : (BlockEntityTicker<T>) (BlockEntityTicker<GasBurnerBlockEntity>) GasBurnerBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!stack.is(Chemistry.LIGHTS_BURNER))
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide)
        {
            if (!state.getValue(OPEN))
            {
                player.displayClientMessage(Component.translatable("tfcpp.gas_burner.shut"), true);
            }
            else if (!state.getValue(LIT))
            {
                level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f, level.random.nextFloat() * 0.4f + 0.8f);
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The hand wheel. Shutting the gas puts the flame out. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!player.getMainHandItem().isEmpty())
        {
            return InteractionResult.PASS; // What is in the hand is not for this block: it may be placed beside it
        }
        if (!level.isClientSide)
        {
            final boolean open = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, open).setValue(LIT, open && state.getValue(LIT)), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 0.8f);
            player.displayClientMessage(Component.translatable(open ? "tfcpp.pipe.opened" : "tfcpp.pipe.closed"), true);
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
        PipeTracker.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
