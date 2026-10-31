package tfcpp.pneumatics.instruments;

import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.devices.LampBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Flammability;
import tfcpp.pneumatics.gas.GasTracker;

/** A TFC lamp behind a gauze: it takes the same fuels and is lit the same way, but is no source of ignition, and its flame can be read. */
public final class DavyLampBlock extends LampBlock
{
    public DavyLampBlock(ExtendedProperties properties)
    {
        super(properties);
    }

    /** A gauze left with gas burning inside it gets hot enough to light the gas outside. A random tick comes about once a minute, so this takes a couple of minutes on average, as it does in the hand. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        super.randomTick(state, level, pos, random);
        if (state.getValue(LIT) && random.nextFloat() < 0.5f)
        {
            final GasTracker tracker = GasTracker.get(level);
            final @Nullable float[] air = tracker.volume(level).cell(pos.getX(), pos.getY(), pos.getZ());
            if (air != null && Flammability.isFlammable(air))
            {
                tracker.queueIgnition(pos);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (stack.isEmpty() && !player.isShiftKeyDown() && state.getValue(LIT))
        {
            if (level instanceof ServerLevel serverLevel)
            {
                final @Nullable float[] air = GasTracker.get(serverLevel).volume(serverLevel).cell(pos.getX(), pos.getY(), pos.getZ());
                player.displayClientMessage(Component.translatable(DavyLamp.read(air).translationKey()), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
