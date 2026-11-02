package tfcpp.pneumatics.instruments;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A leaf of gold laid on a thin plate, which mercury vapour whitens. Mercury forms an amalgam with gold that does not come off, so the whitening only goes one way: each stage is a model of its own, from bright gold to white.
 */
public final class GoldLeafIndicatorBlock extends Block implements EntityBlock
{
    /** 0 is clean gold, 3 is white. The stage is what the leaf shows, and the dose it has taken is in its block entity. */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);

    /** A plate a sixteenth of a block thick, so that a gas cell can lie across it: gas passes a thin shape, as it does an open door. */
    private static final VoxelShape LEAF = Block.box(0, 0, 0, 16, 1, 16);

    public GoldLeafIndicatorBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(STAGE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new GoldLeafIndicatorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide || type != Instruments.GOLD_LEAF_INDICATOR_ENTITY.get() ? null : (BlockEntityTicker<T>) (BlockEntityTicker<GoldLeafIndicatorBlockEntity>) GoldLeafIndicatorBlockEntity::serverTick;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return LEAF;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return LEAF;
    }

    /** Only the stage is a fact that can be read by hand: the dose is not shown, as the leaf is judged by what the eye sees. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!player.getMainHandItem().isEmpty())
        {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide)
        {
            player.displayClientMessage(Component.translatable("tfcpp.gold_leaf.stage." + state.getValue(STAGE)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A leaf that is still clean comes up as a new one. A leaf that mercury has whitened is spent, and comes up as nothing: its dose does not go with it. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params)
    {
        return state.getValue(STAGE) > 0 ? List.of() : super.getDrops(state, params);
    }
}
