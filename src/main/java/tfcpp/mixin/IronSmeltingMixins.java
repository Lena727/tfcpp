package tfcpp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity;
import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tfcpp.metallurgy.IronSmelting;

/** TFC's iron furnaces turn ore into metal with nothing left over. These add the slag and the gas that the reduction really gives. */
final class IronSmeltingMixins
{
    @Mixin(BloomeryBlockEntity.class)
    abstract static class Bloomery
    {
        @Inject(method = "completeRecipe", at = @At("HEAD"))
        private void tfcpp$leaveSlag(CallbackInfo ci)
        {
            final BloomeryBlockEntity self = (BloomeryBlockEntity) (Object) this;
            IronSmelting.bloomeryFinished(self, self.getInputStacks(), self.getCachedRecipe());
        }
    }

    @Mixin(BlastFurnaceBlockEntity.class)
    abstract static class BlastFurnace
    {
        @ModifyExpressionValue(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/common/recipes/HeatingRecipe;assembleFluid(Lnet/minecraft/world/item/ItemStack;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
        private static FluidStack tfcpp$leaveSlag(FluidStack iron, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos)
        {
            IronSmelting.blastFurnaceReduced(level, pos, iron);
            return iron;
        }
    }

    private IronSmeltingMixins() {}
}
