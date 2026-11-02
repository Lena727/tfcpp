package tfcpp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity;
import net.dries007.tfc.common.recipes.BlastFurnaceRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import tfcpp.metallurgy.IronBlend;

/**
 * Ore with phosphorus and ore without go into one furnace, and the whole hearth comes out phosphoric. TFC's furnace turns away a piece that does not suit the recipe of the first one, replaces one oxide with the next and loses the first, and drops iron of a second kind into a tank of the first.
 */
final class IronBlendMixins
{
    @Mixin(BlastFurnaceRecipe.class)
    abstract static class Recipe
    {
        /** Either recipe of iron takes a piece of either oxide. What comes out is decided later, by what actually melted. */
        @Inject(method = "matchesInput(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
        private void tfcpp$takeMixedCharge(ItemStack stack, CallbackInfoReturnable<Boolean> cir)
        {
            if (!cir.getReturnValueZ() && IronBlend.takesAsMixedCharge((BlastFurnaceRecipe) (Object) this, stack))
            {
                cir.setReturnValue(true);
            }
        }
    }

    @Mixin(BlastFurnaceBlockEntity.class)
    abstract static class BlastFurnace
    {
        /** Two different oxides melting in one tick are one bath of phosphoric iron, and not one thrown away for the other. */
        @Inject(method = "insertOrReplaceFluid", at = @At("HEAD"), cancellable = true)
        private void tfcpp$mixOxides(FluidStack newFluid, FluidStack currentFluid, CallbackInfoReturnable<FluidStack> cir)
        {
            final @Nullable FluidStack merged = IronBlend.merged(currentFluid, newFluid);
            if (merged != null)
            {
                cir.setReturnValue(merged);
            }
        }

        @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/capability/templates/FluidTank;fill(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)I"))
        private static int tfcpp$mixPigIron(FluidTank tank, FluidStack incoming, IFluidHandler.FluidAction action, Operation<Integer> original)
        {
            return IronBlend.fill(tank, incoming, action, original);
        }
    }

    private IronBlendMixins() {}
}
