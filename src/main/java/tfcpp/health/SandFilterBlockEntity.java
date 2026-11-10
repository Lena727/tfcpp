package tfcpp.health;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import tfcpp.ecology.Pollution;

/**
 * A slow sand filter: water poured in at the top seeps through at {@link Filtration#LITRES_PER_HOUR} and is drawn from the side. No ticker: the water has gone through by the calendar when somebody fills, draws or looks, by the closed form of {@link Filtration#throughput}. The tanks are ordinary tanks, which mix the quality of the water poured into them (the tank mixin), and the water that comes out has been through {@link Filtration#filter}.
 */
public final class SandFilterBlockEntity extends BlockEntity
{
    public static final int INPUT_CAPACITY = 2000, OUTPUT_CAPACITY = 1000;

    private final FluidTank input = new FluidTank(INPUT_CAPACITY, stack -> FluidQuality.carries(stack.getFluid()));
    private final FluidTank output = new FluidTank(OUTPUT_CAPACITY, stack -> FluidQuality.carries(stack.getFluid()));
    /** The calendar tick up to which the water has been passed. */
    private long lastTick;
    /** Days of running, which ripen the filter. */
    private double ripeDays;
    /** Litres that have been due but are not yet a whole millibucket. */
    private double carry;
    /** Micrograms of each of the metals that the sand has held back. Pathogens need no store: they die in the living layer of the sand, which is what happens to them. Metals do not die. */
    private final double[] retained = new double[tfcpp.ecology.FoodMetals.METALS.length];
    private final Face top = new Face(Role.TOP), side = new Face(Role.SIDE), any = new Face(Role.ANY);

    public SandFilterBlockEntity(BlockPos pos, BlockState state)
    {
        super(Hygiene.SAND_FILTER_ENTITY.get(), pos, state);
    }

    /** @return the handler of a face: the top only takes water in, the sides only give the filtered water out, and a query with no side fills the input and drains the output */
    public IFluidHandler handler(@Nullable Direction face)
    {
        return face == null ? any : face == Direction.UP ? top : side;
    }

    /** @return micrograms of one metal that the sand holds */
    public double retained(int metal)
    {
        return retained[metal];
    }

    /** The block is broken: the water in both tanks and the metals the sand held run into the soil. */
    public void spill(ServerLevel level)
    {
        catchUp();
        for (FluidTank tank : new FluidTank[] {input, output})
        {
            final FluidStack water = tank.getFluid();
            if (!water.isEmpty() && FluidQuality.carries(water.getFluid()))
            {
                FluidQuality.soak(level, worldPosition, FluidQuality.get(water), water.getAmount());
            }
            tank.setFluid(FluidStack.EMPTY);
        }
        for (int i = 0; i < retained.length; i++)
        {
            tfcpp.ecology.Pollution.deposit(level, worldPosition, tfcpp.ecology.FoodMetals.METALS[i], retained[i] / 1e6);
            retained[i] = 0.0;
        }
    }

    public boolean isRipe()
    {
        return ripeDays >= Filtration.RIPENING_DAYS;
    }

    public double ripeDays()
    {
        return ripeDays;
    }

    public FluidStack inputFluid()
    {
        return input.getFluid();
    }

    public FluidStack outputFluid()
    {
        return output.getFluid();
    }

    /** Sets how many days the filter has run, for a test. */
    public void setRipeDays(double days)
    {
        ripeDays = days;
        setChanged();
    }

    /** Puts this in the output tank, for a test. */
    public void setOutput(FluidStack water)
    {
        output.setFluid(water);
        setChanged();
    }

    /** Makes the filter think that it was last looked at this many calendar ticks ago, for a test that does not want to wait. */
    public void backdate(long calendarTicks)
    {
        lastTick -= calendarTicks;
    }

    /** Puts water in the input tank, as a pipe or a pump does. */
    public int pour(FluidStack water)
    {
        return top.fill(water, IFluidHandler.FluidAction.EXECUTE);
    }

    /** Passes the water that the time since the last look allows through the sand. */
    public void catchUp()
    {
        final long now = Pollution.now();
        if (lastTick == 0L)
        {
            lastTick = now;
        }
        final double hours = (now - lastTick) / 1000.0;
        lastTick = now;
        if (!(hours > 0.0) || input.getFluidAmount() <= 0)
        {
            carry = 0.0;
            return;
        }
        final Filtration.Throughput through = Filtration.throughput(ripeDays, hours, input.getFluidAmount(), OUTPUT_CAPACITY - output.getFluidAmount());
        final double due = through.total() + carry;
        final int whole = (int) Math.min(Math.floor(due), Math.min(input.getFluidAmount(), OUTPUT_CAPACITY - output.getFluidAmount()));
        if (whole <= 0)
        {
            carry = Math.min(due, 1.0);
            return;
        }
        final WaterQuality in = FluidQuality.get(input.getFluid());
        // The output takes only what it can: it may hold another kind of water, and then nothing moves (and the sand does not ripen on water that did not pass)
        final int accepted = output.fill(input.getFluid().copyWithAmount(whole), IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0)
        {
            carry = Math.min(due, 1.0);
            return;
        }
        carry = due - Math.min(whole, accepted);
        final int moved = Math.min(whole, accepted);
        final int unripe = (int) Math.min(moved, Math.round(through.unripeLitres()));
        final FluidStack taken = input.drain(moved, IFluidHandler.FluidAction.EXECUTE);
        if (taken.isEmpty())
        {
            return;
        }
        if (unripe > 0)
        {
            pass(taken.copyWithAmount(unripe), in, Filtration.UNRIPE_REMOVAL);
        }
        if (moved - unripe > 0)
        {
            pass(taken.copyWithAmount(moved - unripe), in, Filtration.RIPE_REMOVAL);
        }
        ripeDays = Filtration.daysRun(ripeDays, moved);
        setChanged();
    }

    /** Puts water that has been through the sand into the output, and keeps the metals that the sand took out of it. */
    private void pass(FluidStack water, WaterQuality in, double removal)
    {
        final WaterQuality out = Filtration.filter(in, removal);
        for (int i = 0; i < retained.length; i++)
        {
            retained[i] += (in.metal(i) - out.metal(i)) * water.getAmount();
        }
        output.fill(FluidQuality.with(water, out), IFluidHandler.FluidAction.EXECUTE);
    }

    private void catchUpOnServer()
    {
        if (level instanceof ServerLevel)
        {
            catchUp();
        }
    }

    private enum Role
    {
        /** Takes water in and gives none. */
        TOP,
        /** Gives the filtered water out and takes none. */
        SIDE,
        /** A query with no side: fills the input, drains the output (tank 0 is the input, tank 1 the output). */
        ANY
    }

    /** One face of the filter. */
    private final class Face implements IFluidHandler
    {
        private final Role role;

        Face(Role role)
        {
            this.role = role;
        }

        private FluidTank tank(int index)
        {
            return role == Role.TOP || role == Role.ANY && index == 0 ? input : output;
        }

        @Override
        public int getTanks()
        {
            return role == Role.ANY ? 2 : 1;
        }

        @Override
        public FluidStack getFluidInTank(int index)
        {
            catchUpOnServer();
            return tank(index).getFluid();
        }

        @Override
        public int getTankCapacity(int index)
        {
            return tank(index).getCapacity();
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack)
        {
            return role != Role.SIDE && input.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action)
        {
            if (role == Role.SIDE)
            {
                return 0;
            }
            catchUpOnServer();
            final int filled = input.fill(resource, action);
            if (filled > 0 && action.execute())
            {
                setChanged();
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action)
        {
            if (role == Role.TOP)
            {
                return FluidStack.EMPTY;
            }
            catchUpOnServer();
            final FluidStack drained = output.drain(resource, action);
            if (!drained.isEmpty() && action.execute())
            {
                setChanged();
            }
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action)
        {
            if (role == Role.TOP)
            {
                return FluidStack.EMPTY;
            }
            catchUpOnServer();
            final FluidStack drained = output.drain(maxDrain, action);
            if (!drained.isEmpty() && action.execute())
            {
                setChanged();
            }
            return drained;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        tag.put("input", input.writeToNBT(provider, new CompoundTag()));
        tag.put("output", output.writeToNBT(provider, new CompoundTag()));
        tag.putLong("lastTick", lastTick);
        tag.putDouble("ripeDays", ripeDays);
        tag.putDouble("carry", carry);
        final net.minecraft.nbt.ListTag held = new net.minecraft.nbt.ListTag();
        for (double micrograms : retained)
        {
            held.add(net.minecraft.nbt.DoubleTag.valueOf(micrograms));
        }
        tag.put("retained", held);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        input.readFromNBT(provider, tag.getCompound("input"));
        output.readFromNBT(provider, tag.getCompound("output"));
        lastTick = tag.getLong("lastTick");
        ripeDays = tag.getDouble("ripeDays");
        carry = tag.getDouble("carry");
        final net.minecraft.nbt.ListTag held = tag.getList("retained", net.minecraft.nbt.Tag.TAG_DOUBLE);
        for (int i = 0; i < retained.length; i++)
        {
            retained[i] = i < held.size() ? held.getDouble(i) : 0.0;
        }
    }
}
