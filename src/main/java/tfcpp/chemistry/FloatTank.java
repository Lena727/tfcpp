package tfcpp.chemistry;

import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A tank that keeps its liquid in float litres, as a retort uses its inputs in fractions of a millibucket. Pipes and containers see whole millibuckets of it: they can pour in only what whole room there is, and take out only whole millibuckets, and the fraction stays in the tank for the next batch to use.
 */
public final class FloatTank implements IFluidHandler
{
    /** Float sums land a hair short of the whole number they stand for. */
    private static final double EPSILON = 1e-4;

    private final int capacity;
    private final Predicate<Fluid> valid;
    private final Runnable changed;
    private Fluid fluid = Fluids.EMPTY;
    private double litres;

    /**
     * @param capacity millibuckets (litres) the tank holds
     * @param valid    which liquids may be poured in
     * @param changed  called when the contents have changed
     */
    public FloatTank(int capacity, Predicate<Fluid> valid, Runnable changed)
    {
        this.capacity = capacity;
        this.valid = valid;
        this.changed = changed;
    }

    /** @return the liquid in the tank, or {@link Fluids#EMPTY} */
    public Fluid fluid()
    {
        return litres <= 0.0 ? Fluids.EMPTY : fluid;
    }

    public double litres()
    {
        return litres;
    }

    public boolean isEmpty()
    {
        return litres <= 0.0;
    }

    /** @return whole millibuckets that can be taken out now */
    public int wholeMillibuckets()
    {
        return (int) Math.floor(litres + EPSILON);
    }

    /** @return whole millibuckets of room left */
    public int room()
    {
        return Math.max(0, (int) Math.floor(capacity - litres + EPSILON));
    }

    /** Uses up some of the liquid, as a batch does. What is asked for beyond the contents is not taken. */
    public void take(double amount)
    {
        litres = Math.max(0.0, litres - amount);
        if (litres < EPSILON)
        {
            clear();
        }
        changed.run();
    }

    /** Empties the tank without a word, for a device that has already dealt with what was in it. */
    public void clear()
    {
        litres = 0.0;
        fluid = Fluids.EMPTY;
    }

    /** Puts liquid in directly, whatever the predicate says, for a test or a load. */
    public void set(Fluid fluid, double litres)
    {
        this.fluid = litres <= 0.0 ? Fluids.EMPTY : fluid;
        this.litres = litres <= 0.0 ? 0.0 : litres;
        changed.run();
    }

    public boolean accepts(Fluid candidate)
    {
        return valid.test(candidate) && (isEmpty() || fluid == candidate);
    }

    public CompoundTag save()
    {
        final CompoundTag tag = new CompoundTag();
        if (!isEmpty())
        {
            tag.putString("fluid", BuiltInRegistries.FLUID.getKey(fluid).toString());
            tag.putDouble("litres", litres);
        }
        return tag;
    }

    public void load(CompoundTag tag)
    {
        fluid = Fluids.EMPTY;
        litres = 0.0;
        if (tag.contains("fluid"))
        {
            final ResourceLocation key = ResourceLocation.tryParse(tag.getString("fluid"));
            final Fluid loaded = key == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(key);
            if (loaded != Fluids.EMPTY && tag.getDouble("litres") > 0.0)
            {
                fluid = loaded;
                litres = tag.getDouble("litres");
            }
        }
    }

    @Override
    public int getTanks()
    {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank)
    {
        return isEmpty() ? FluidStack.EMPTY : new FluidStack(fluid, wholeMillibuckets());
    }

    @Override
    public int getTankCapacity(int tank)
    {
        return capacity;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack)
    {
        return accepts(stack.getFluid());
    }

    @Override
    public int fill(FluidStack resource, FluidAction action)
    {
        if (resource.isEmpty() || !accepts(resource.getFluid()))
        {
            return 0;
        }
        final int accepted = Math.min(resource.getAmount(), room());
        if (accepted > 0 && action.execute())
        {
            fluid = resource.getFluid();
            litres += accepted;
            changed.run();
        }
        return accepted;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action)
    {
        if (resource.isEmpty() || isEmpty() || resource.getFluid() != fluid)
        {
            return FluidStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action)
    {
        final int taken = Math.min(maxDrain, wholeMillibuckets());
        if (taken <= 0 || isEmpty())
        {
            return FluidStack.EMPTY;
        }
        final FluidStack out = new FluidStack(fluid, taken);
        if (action.execute())
        {
            litres -= taken;
            if (litres < EPSILON)
            {
                clear();
            }
            changed.run();
        }
        return out;
    }
}
