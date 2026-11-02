package tfcpp.chemistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * An iron tray of sand between a fire and a glass vessel. The depth of the sand is what sets how much of the fire reaches the glass: see {@link SandBath}. An empty hand adds a layer, and a crouching empty hand scoops one off.
 */
public final class SandBathBlock extends Block implements EntityBlock
{
    public static final IntegerProperty DEPTH = IntegerProperty.create("depth", SandBath.MIN_DEPTH, SandBath.MAX_DEPTH);
    /** The depth of a new bath: half way, which passes about a fifth of the fire's excess heat. */
    public static final int DEFAULT_DEPTH = 4;

    public SandBathBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DEPTH, DEFAULT_DEPTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(DEPTH);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new SandBathBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide || type != Chemistry.SAND_BATH_ENTITY.get() ? null : (BlockEntityTicker<T>) (BlockEntityTicker<SandBathBlockEntity>) SandBathBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!player.getMainHandItem().isEmpty())
        {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide)
        {
            final int depth = state.getValue(DEPTH);
            final int changed = Math.clamp(depth + (player.isShiftKeyDown() ? -1 : 1), SandBath.MIN_DEPTH, SandBath.MAX_DEPTH);
            if (changed != depth)
            {
                level.setBlock(pos, state.setValue(DEPTH, changed), Block.UPDATE_ALL);
            }
            final float temperature = level.getBlockEntity(pos) instanceof SandBathBlockEntity bath ? bath.getTemperature() : 0f;
            player.displayClientMessage(Component.translatable("tfcpp.sand_bath.status", changed, SandBath.MAX_DEPTH, Math.round(temperature), Math.round(SandBath.passedUp(temperature, changed))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
