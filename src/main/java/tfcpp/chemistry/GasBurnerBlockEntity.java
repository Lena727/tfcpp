package tfcpp.chemistry;

import net.dries007.tfc.common.component.heat.HeatCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Combustion;
import tfcpp.pneumatics.gas.Flammability;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.gas.GasIgnition;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.pipes.PipeContents;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePhysics;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * The flame of a gas burner. Its network visits it once a second, and it burns what that second's gas allows. Between visits it keeps handing the temperature of its flame to whatever sits on top of it, every tick, as a forge does.
 */
public final class GasBurnerBlockEntity extends BlockEntity
{
    /** A network visits a burner once in 20 ticks, so the heat has to last a little longer than that to be unbroken. */
    private static final int HEAT_HOLD_TICKS = 25;
    /** The nozzle, 25 mm across on a pipe of 100 mm, as the seat of a safety valve is. */
    private static final float NOZZLE_AREA_SHARE = 1f / 16f;

    public static void serverTick(Level level, BlockPos pos, BlockState state, GasBurnerBlockEntity burner)
    {
        if (burner.heatTicks > 0)
        {
            burner.heatTicks--;
            HeatCapability.provideHeatTo(level, pos.above(), Direction.DOWN, burner.temperature);
        }
    }

    private float temperature;
    private int heatTicks;

    public GasBurnerBlockEntity(BlockPos pos, BlockState state)
    {
        super(Chemistry.GAS_BURNER_ENTITY.get(), pos, state);
    }

    /** @return the temperature of the flame in Celsius, or zero if it is not burning gas just now */
    public float flameTemperature()
    {
        return heatTicks > 0 ? temperature : 0f;
    }

    private void cool()
    {
        temperature = 0f;
        heatTicks = 0;
    }

    private void goOut(ServerLevel level, BlockPos pos, BlockState state)
    {
        setLit(level, pos, state, false);
        cool();
    }

    private void setLit(ServerLevel level, BlockPos pos, BlockState state, boolean lit)
    {
        if (state.getValue(GasBurnerBlock.LIT) != lit)
        {
            level.setBlock(pos, state.setValue(GasBurnerBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** Burns this second's gas. Does nothing at all while closed, and leaks while open and unlit. */
    void burn(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds)
    {
        if (!state.getValue(GasBurnerBlock.OPEN))
        {
            setLit(level, pos, state, false);
            cool();
            return;
        }
        if (!state.getValue(GasBurnerBlock.LIT))
        {
            // The flame is out and the gas is not: it comes through the nozzle into the room, which is how town gas killed people
            final @Nullable PipeContents leaked = network.blowInto(1f, NOZZLE_AREA_SHARE, seconds);
            if (leaked != null)
            {
                GasEmission.release(level, pos, leaked.gases.clone());
            }
            cool();
            return;
        }

        final PipeContents contents = network.contents();
        // A flame that finds a mixture it can burn in the pipe behind it runs back along the pipe at once
        if (Flammability.isFlammable(BurnerMath.asCell(contents.gases, contents.air)))
        {
            flashBack(network, level, pos);
            return;
        }

        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final int intakeY = GasEmission.outletHeight(level, volume, pos);
        if (intakeY == GasEmission.SMOTHERED)
        {
            goOut(level, pos, state);
            return;
        }
        final @Nullable float[] intake = volume.cell(pos.getX(), intakeY, pos.getZ());
        final float oxygen = intake == null ? GasMath.OXYGEN_IN_AIR : GasMath.oxygenFraction(intake);
        if (oxygen < Combustion.EXTINCTION_OXYGEN)
        {
            goOut(level, pos, state); // The room has nothing left to burn it in
            return;
        }

        final float gauge = network.pressure() - 1f;
        if (gauge < BurnerMath.MINIMUM_GAUGE_ATM)
        {
            goOut(level, pos, state); // A flame lives on the gas that feeds it, and there is none pushing through the nozzle
            return;
        }
        final float evenMoles = PipePhysics.moles(gauge, contents.temperature, contents.freeVolume(network.volume()));
        float share = BurnerMath.share(contents.gases, contents.air, seconds, evenMoles);
        if (share <= 0f)
        {
            goOut(level, pos, state); // Nothing in the pipe that burns
            return;
        }

        // Only as much as the oxygen of the room, down to where a flame goes out, can burn
        final float heatPerMole = BurnerMath.heatPerMole(contents.gases, contents.air);
        float[] wanted = scaled(contents.gases, share);
        BurnerMath.Products products = BurnerMath.burn(wanted, oxygen);
        final float available = (oxygen - Combustion.EXTINCTION_OXYGEN) * GasMath.CELL_MOLES;
        if (products.oxygen() > available)
        {
            share *= Math.max(0f, available) / products.oxygen();
            wanted = scaled(contents.gases, share);
            products = BurnerMath.burn(wanted, oxygen);
        }
        if (share <= 0f)
        {
            goOut(level, pos, state);
            return;
        }

        contents.takeGas(share); // The air that comes with the gas leaves with it, and joins the room's own
        GasEmission.release(level, pos, products.gases());
        temperature = BurnerMath.heatedTemperature(BurnerMath.temperature(heatPerMole), products.kilojoules() / seconds);
        heatTicks = HEAT_HOLD_TICKS;
    }

    private static float[] scaled(float[] gases, float share)
    {
        final float[] scaled = new float[Gas.COUNT];
        for (int i = 0; i < Gas.COUNT; i++)
        {
            scaled[i] = gases[i] * share;
        }
        return scaled;
    }

    /**
     * Everything combustible in the pipe burns at once, in a volume it cannot expand out of. The burner is where the flame meets the pipe, so that is where the pipe gives way. This has to wait until no network is being walked.
     */
    private void flashBack(PipeNetwork network, ServerLevel level, BlockPos pos)
    {
        final PipeContents contents = network.contents();
        final BurnerMath.Explosion explosion = BurnerMath.deflagrate(contents.gases, contents.air);
        contents.air = Math.max(0f, contents.air - explosion.airUsed());
        final float kilojoules = explosion.kilojoules();
        final float kilopascals = BurnerMath.peakKilopascals(kilojoules, network.volume());
        cool();
        PipeTracker.get(level).later(() -> {
            level.destroyBlock(pos, false);
            GasIgnition.rupture(level, pos, kilojoules, kilopascals);
        });
    }
}
