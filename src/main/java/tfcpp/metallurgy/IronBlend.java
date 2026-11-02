package tfcpp.metallurgy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.common.recipes.BlastFurnaceRecipe;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.Metal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * What happens when ore with phosphorus in it and ore without go into one blast furnace. Phosphorus does not stay with the ore that carried it: it goes into the iron of the whole hearth, so the whole charge counts as phosphoric.
 * <p>
 * TFC's furnace holds one fluid at a time. A second kind of oxide used to replace the first and throw it away, and iron of a second kind tapped into a tank still holding the first was refused and vanished. Both are mended here, in favour of the phosphoric kind.
 */
public final class IronBlend
{
    public static boolean isOxide(Fluid fluid)
    {
        return fluid == Metallurgy.IRON_OXIDE.get() || fluid == Metallurgy.PHOSPHORIC_IRON_OXIDE.get();
    }

    private static boolean isPig(Fluid fluid)
    {
        return fluid == TFCFluids.METALS.get(Metal.PIG_IRON).getSource() || fluid == Metallurgy.PHOSPHORIC_PIG_IRON.get();
    }

    /**
     * @return {@code true} if this furnace recipe, which melts one kind of oxide, may also take a piece of the other kind into the same charge
     */
    public static boolean takesAsMixedCharge(BlastFurnaceRecipe recipe, ItemStack stack)
    {
        final @Nullable HeatingRecipe heat = HeatingRecipe.getRecipe(stack);
        if (heat == null || !isOxide(heat.assembleFluid(stack).getFluid()))
        {
            return false;
        }
        final SizedFluidIngredient wanted = recipe.inputFluid();
        return wanted.ingredient().test(new FluidStack(Metallurgy.IRON_OXIDE.get(), 1)) || wanted.ingredient().test(new FluidStack(Metallurgy.PHOSPHORIC_IRON_OXIDE.get(), 1));
    }

    /** @return both oxides as one phosphoric oxide, or {@code null} if they are not two different oxides */
    @Nullable
    public static FluidStack merged(FluidStack current, FluidStack incoming)
    {
        if (current.isEmpty() || incoming.isEmpty() || current.getFluid() == incoming.getFluid() || !isOxide(current.getFluid()) || !isOxide(incoming.getFluid()))
        {
            return null;
        }
        return new FluidStack(Metallurgy.PHOSPHORIC_IRON_OXIDE.get(), current.getAmount() + incoming.getAmount());
    }

    /** Runs iron into the furnace's output tank. Pig iron meeting phosphoric pig iron makes phosphoric pig iron, as one bath of liquid iron would. */
    public static int fill(FluidTank tank, FluidStack incoming, IFluidHandler.FluidAction action, Operation<Integer> original)
    {
        final FluidStack current = tank.getFluid();
        FluidStack poured = incoming;
        if (!current.isEmpty() && !incoming.isEmpty() && current.getFluid() != incoming.getFluid() && isPig(current.getFluid()) && isPig(incoming.getFluid()))
        {
            if (incoming.getFluid() == Metallurgy.PHOSPHORIC_PIG_IRON.get())
            {
                if (action.simulate())
                {
                    return Math.min(tank.getCapacity() - current.getAmount(), incoming.getAmount());
                }
                tank.setFluid(new FluidStack(Metallurgy.PHOSPHORIC_PIG_IRON.get(), current.getAmount()));
            }
            else
            {
                poured = new FluidStack(Metallurgy.PHOSPHORIC_PIG_IRON.get(), incoming.getAmount());
            }
        }
        return original.call(tank, poured, action);
    }

    private IronBlend() {}
}
