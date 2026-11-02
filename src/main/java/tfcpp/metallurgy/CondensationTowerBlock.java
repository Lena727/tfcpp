package tfcpp.metallurgy;

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
 * A fire-brick tower in a run of pipe, joined to pipe on every side. It does nothing by itself but let the gas of its network cool against its walls: what settles there is worked out in {@link CondensationTowerBlockEntity}.
 */
public final class CondensationTowerBlock extends Block implements EntityBlock, PipeMember, PipePort
{
    /** A chamber a metre across, and open: the whole of the block is gas space. */
    private static final float VOLUME = 1f;
    /** kW per kelvin. Six faces of a metre of brick, at about 2 W per square metre and kelvin: the pipes' 0.005 is for a thin run of pipe, and this is a whole block of wall. */
    private static final float HEAT_LOSS = 0.012f;

    public CondensationTowerBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public float volume(BlockState state)
    {
        return VOLUME;
    }

    /** Fire brick holds hot fume and every acid, but it is brittle, and its joints give way at a few atmospheres. */
    @Override
    public PipeMaterial material(BlockState state)
    {
        return PipeMaterial.CERAMIC;
    }

    @Override
    public boolean connects(BlockState state, Direction side)
    {
        return true;
    }

    @Override
    public float heatLoss(BlockState state)
    {
        return HEAT_LOSS;
    }

    @Override
    public void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds)
    {
        if (level.getBlockEntity(pos) instanceof CondensationTowerBlockEntity tower)
        {
            tower.catchFrom(network, seconds);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CondensationTowerBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof CondensationTowerBlockEntity tower) || !tower.canUse(stack))
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide)
        {
            tower.interact(stack, player, hand);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand reads the tower, and an empty hand while crouching scrapes the dust off its walls. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.getBlockEntity(pos) instanceof CondensationTowerBlockEntity tower && !level.isClientSide)
        {
            if (player.isShiftKeyDown())
            {
                tower.scrape(player);
            }
            else
            {
                tower.showStatus(player);
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
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CondensationTowerBlockEntity tower)
        {
            tower.dropContents();
        }
        PipeTracker.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
