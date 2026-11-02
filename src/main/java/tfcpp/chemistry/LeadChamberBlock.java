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
 * A lead chamber in a run of pipe. Sulfur dioxide, the oxides of nitrogen, air and water in the chamber make sulfuric acid on its walls, and the acid runs into the tank of the block. Lead holds the acid because its surface sulfate stops the attack while the acid is cool.
 */
public final class LeadChamberBlock extends Block implements EntityBlock, PipeMember, PipePort
{
    /** A chamber a metre across, and open: the whole of the block is gas space. */
    private static final float VOLUME = 1f;
    /**
     * kW per kelvin. Real chambers were built big and thin, in order to lose their heat: a thin lead sheet on air gives its heat away far faster than brick does, and the gas of the reaction would otherwise warm the acid until the lead softens. A game figure, not measured.
     */
    private static final float HEAT_LOSS = 0.02f;

    public LeadChamberBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public float volume(BlockState state)
    {
        return VOLUME;
    }

    @Override
    public PipeMaterial material(BlockState state)
    {
        return PipeMaterial.LEAD;
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

    /** Its tank is the only liquid a pump takes from this block, as the acid is not in the network. */
    @Override
    public boolean tankOpenToPump(BlockState state)
    {
        return true;
    }

    @Override
    public void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds)
    {
        if (level.getBlockEntity(pos) instanceof LeadChamberBlockEntity chamber)
        {
            chamber.step(network, seconds);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new LeadChamberBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof LeadChamberBlockEntity chamber) || !chamber.canUse(stack))
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide)
        {
            chamber.interact(stack, player, hand);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand reads the chamber. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.getBlockEntity(pos) instanceof LeadChamberBlockEntity chamber && !level.isClientSide)
        {
            chamber.showStatus(player);
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
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof LeadChamberBlockEntity chamber)
        {
            chamber.spill(server);
        }
        PipeTracker.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
