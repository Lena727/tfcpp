package tfcpp.chemistry;

import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The heat of a sand bath: it takes the heat of a fire or a burner below it as any vessel does, and hands up to whatever stands on it only what {@link SandBath} lets through, every tick, as a gas burner hands up its flame.
 */
public final class SandBathBlockEntity extends BlockEntity implements IHeatConsumer
{
    private static final int HEAT_STABILITY_TICKS = 5;

    public static void serverTick(Level level, BlockPos pos, BlockState state, SandBathBlockEntity bath)
    {
        if (bath.heatStabilityTicks > 0)
        {
            bath.heatStabilityTicks--;
        }
        else if (bath.targetTemperature > 0)
        {
            bath.targetTemperature = HeatCapability.adjustTempTowards(bath.targetTemperature, 0);
        }
        if (bath.temperature != bath.targetTemperature)
        {
            bath.temperature = HeatCapability.adjustTempTowards(bath.temperature, bath.targetTemperature);
            bath.setChanged();
        }

        if (bath.temperature > SandBath.AMBIENT)
        {
            HeatCapability.provideHeatTo(level, pos.above(), Direction.DOWN, SandBath.passedUp(bath.temperature, state.getValue(SandBathBlock.DEPTH)));
        }
    }

    private float temperature;
    private float targetTemperature;
    private int heatStabilityTicks;

    public SandBathBlockEntity(BlockPos pos, BlockState state)
    {
        super(Chemistry.SAND_BATH_ENTITY.get(), pos, state);
    }

    @Override
    public float getTemperature()
    {
        return temperature;
    }

    @Override
    public void setTemperature(float temperature)
    {
        // Devices report their temperature every tick, even when cold. Only something hotter than the sand can heat it.
        if (temperature >= this.temperature)
        {
            targetTemperature = temperature;
            heatStabilityTicks = HEAT_STABILITY_TICKS;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        tag.putFloat("temperature", temperature);
        tag.putFloat("targetTemperature", targetTemperature);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        temperature = tag.getFloat("temperature");
        targetTemperature = tag.getFloat("targetTemperature");
    }

    /** Sets the temperature of the sand at once, for a test that does not want to wait for a fire to bring it there. */
    public void warmTo(float celsius)
    {
        temperature = celsius;
        targetTemperature = celsius;
        heatStabilityTicks = HEAT_STABILITY_TICKS;
    }
}
