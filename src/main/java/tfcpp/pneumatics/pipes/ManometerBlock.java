package tfcpp.pneumatics.pipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasTracker;

/**
 * A glass U-tube of mercury, the way pressure was read from Torricelli until Bourdon's gauge in 1849. Three metres of tube read to about 4 atm. Past that the mercury is blown out of the top, to be breathed by whoever is in the room.
 */
public final class ManometerBlock extends Block implements PipeMember, PipePort
{
    public static final float BLOWN_AT_GAUGE_ATM = 3f;
    /** Set once the mercury has been thrown out: what is left is a broken tube, open at the top. */
    public static final BooleanProperty BLOWN = BooleanProperty.create("blown");
    /** The share of the bore that the broken tube leaves clear: a thin glass tube against a pipe of 100 mm, about a hundredth. */
    public static final float LEAK_AREA_SHARE = 0.01f;
    private static final float VOLUME = 0.001f;
    /** The one bulb it is made with, of 5 ml. */
    private static final float MERCURY_MOLES = 0.34f;

    public ManometerBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(BLOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BLOWN);
    }

    /** A blown tube has no mercury left to give back. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params)
    {
        return state.getValue(BLOWN) ? List.of() : super.getDrops(state, params);
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

    @Override
    public boolean connects(BlockState state, Direction side)
    {
        return true;
    }

    @Override
    public void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds)
    {
        if (state.getValue(BLOWN))
        {
            // The line leaks through the broken tube, a hundredth of its bore, and the rest of it stands as it was
            network.flowThroughOpening(level, pos.above(), seconds, LEAK_AREA_SHARE, 0f, true);
        }
        else if (network.pressure() - 1f > BLOWN_AT_GAUGE_ATM)
        {
            final @Nullable float[] cell = GasTracker.get(level).volume(level).cellForEmission(pos.getX(), pos.getY(), pos.getZ());
            if (cell != null)
            {
                cell[Gas.MERCURY_VAPOR.ordinal()] += MERCURY_MOLES; // Once: there is no more in it
            }
            level.setBlock(pos, state.setValue(BLOWN, true), Block.UPDATE_ALL);
            PipeNetwork.hiss(level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level instanceof ServerLevel server)
        {
            final @Nullable PipeNetwork network = PipeTracker.get(server).networkAt(pos);
            if (state.getValue(BLOWN))
            {
                player.displayClientMessage(Component.translatable("tfcpp.manometer.blown"), true);
            }
            else
            {
                final float pressure = network == null ? 1f : network.pressure();
                final MutableComponent reading = Component.translatable("tfcpp.manometer.reading", Math.round(pressure * 760f));
                player.displayClientMessage(network != null && network.truncated() ? reading.append(". ").append(Component.translatable("tfcpp.manometer.truncated")) : reading, true);
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
        PipeTracker.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
