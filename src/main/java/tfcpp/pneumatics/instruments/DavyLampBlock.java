package tfcpp.pneumatics.instruments;

import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.devices.LampBlock;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.util.data.LampFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Flammability;
import tfcpp.pneumatics.gas.GasTracker;

/** A TFC lamp behind a gauze: it takes the same fuels and is lit the same way, but is no source of ignition, and its flame can be read. */
public final class DavyLampBlock extends LampBlock
{
    /** How often a lit lamp looks at the air around it, in ticks. A flame that the air has put out should not go on lighting the gallery for the average minute that a random tick takes. */
    private static final int CHECK_TICKS = 40;

    public DavyLampBlock(ExtendedProperties properties)
    {
        super(properties);
    }

    /** A lamp put down lit stays lit, if it has oil: the flame is carried over from the stack it was in. One taken up again comes back as an unlit lamp. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        final @Nullable BlockState state = super.getStateForPlacement(context);
        if (state == null)
        {
            return null;
        }
        final ItemStack stack = context.getItemInHand();
        final boolean oiled = !FluidHelpers.getContainedFluid(stack).isEmpty() && LampFuel.get(FluidHelpers.getContainedFluid(stack).getFluid(), state) != null;
        return state.setValue(LIT, DavyLampItem.isLit(stack) && oiled);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving)
    {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (state.getValue(LIT) && !level.isClientSide)
        {
            level.scheduleTick(pos, this, CHECK_TICKS);
        }
    }

    /** A lit lamp looks at the air, and goes out in air that cannot keep a flame going. It does not light itself again when the air clears: it has to be lit, as any TFC lamp. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!state.getValue(LIT))
        {
            return;
        }
        final @Nullable float[] air = GasTracker.get(level).volume(level).cell(pos.getX(), pos.getY(), pos.getZ());
        if (DavyLamp.burns(DavyLamp.read(air)))
        {
            level.scheduleTick(pos, this, CHECK_TICKS);
            return;
        }
        level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        level.getBlockEntity(pos, TFCBlockEntities.LAMP.get()).ifPresent(TickCounterBlockEntity::resetCounter);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1f);
    }

    /** A gauze left with gas burning inside it gets hot enough to light the gas outside. A random tick comes about once a minute, so this takes a couple of minutes on average, as it does in the hand. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        super.randomTick(state, level, pos, random);
        if (state.getValue(LIT))
        {
            level.scheduleTick(pos, this, CHECK_TICKS); // A lamp lit before this was looked at has no tick waiting; one that has is not scheduled twice
        }
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
        if (stack.isEmpty() && !player.isShiftKeyDown())
        {
            if (state.getValue(LIT))
            {
                if (level instanceof ServerLevel serverLevel)
                {
                    final @Nullable float[] air = GasTracker.get(serverLevel).volume(serverLevel).cell(pos.getX(), pos.getY(), pos.getZ());
                    player.displayClientMessage(Component.translatable(DavyLamp.read(air).translationKey()), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide)
            {
                player.displayClientMessage(Component.translatable("tfcpp.davy_lamp.block_unlit"), true);
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
