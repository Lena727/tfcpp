package tfcpp.pneumatics.pipes;

import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Takes the heat of a fire below it, in the same way as TFC's crucible. The network then works out what that does to what is inside. */
public final class PressureVesselBlockEntity extends BlockEntity implements IHeatConsumer
{
    /** A fire reports its heat every tick. One that has not for this long has gone out, or stopped running with its chunk. */
    private static final int HEAT_STABILITY_TICKS = 5;

    private float temperature;
    private long lastHeated;

    public PressureVesselBlockEntity(BlockPos pos, BlockState state)
    {
        super(Pipes.PRESSURE_VESSEL_ENTITY.get(), pos, state);
    }

    /** @return the heat of the fire below, in Celsius as TFC counts it. The contents keep their own temperature, in their network. */
    @Override
    public float getTemperature()
    {
        return level != null && level.getGameTime() - lastHeated <= HEAT_STABILITY_TICKS ? temperature : 0f;
    }

    @Override
    public void setTemperature(float temperature)
    {
        // Devices report every tick, even when cold
        if (temperature > 0f)
        {
            this.temperature = temperature;
            lastHeated = level == null ? 0L : level.getGameTime();
        }
    }
}
