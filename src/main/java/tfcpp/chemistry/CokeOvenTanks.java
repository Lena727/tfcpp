package tfcpp.chemistry;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The two tanks of a coke oven, seen from outside as one handler that can only be drained. Tar is heavier than the watery liquor, and settles out of it into a tank of its own; nothing is pumped back in.
 */
public final class CokeOvenTanks implements IFluidHandler
{
    private final FluidTank tar, liquor;

    CokeOvenTanks(FluidTank tar, FluidTank liquor)
    {
        this.tar = tar;
        this.liquor = liquor;
    }

    private FluidTank tank(int index)
    {
        return index == 0 ? tar : liquor;
    }

    @Override
    public int getTanks()
    {
        return 2;
    }

    @Override
    public FluidStack getFluidInTank(int tank)
    {
        return tank(tank).getFluid();
    }

    @Override
    public int getTankCapacity(int tank)
    {
        return tank(tank).getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack)
    {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action)
    {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action)
    {
        for (int index = 0; index < 2; index++)
        {
            if (!tank(index).isEmpty() && tank(index).getFluid().getFluid() == resource.getFluid())
            {
                return tank(index).drain(resource, action);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action)
    {
        for (int index = 0; index < 2; index++)
        {
            if (!tank(index).isEmpty())
            {
                return tank(index).drain(maxDrain, action);
            }
        }
        return FluidStack.EMPTY;
    }
}
