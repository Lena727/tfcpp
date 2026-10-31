package tfcpp.pneumatics.pipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final float VOLUME = 0.001f;
    /** The one bulb it is made with, of 5 ml. */
    private static final float MERCURY_MOLES = 0.34f;

    public ManometerBlock(Properties properties)
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
        if (network.pressure() - 1f > BLOWN_AT_GAUGE_ATM)
        {
            final @Nullable float[] cell = GasTracker.get(level).volume(level).cellForEmission(pos.getX(), pos.getY(), pos.getZ());
            if (cell != null)
            {
                cell[Gas.MERCURY_VAPOR.ordinal()] += MERCURY_MOLES;
            }
            PipeTracker.get(level).breakLater(pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level instanceof ServerLevel server)
        {
            final @Nullable PipeNetwork network = PipeTracker.get(server).networkAt(pos);
            final float pressure = network == null ? 1f : network.pressure();
            player.displayClientMessage(Component.translatable("tfcpp.manometer.reading", Math.round(pressure * 760f)), true);
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
