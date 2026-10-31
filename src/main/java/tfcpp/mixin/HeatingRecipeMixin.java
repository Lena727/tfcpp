package tfcpp.mixin;

import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import tfcpp.metallurgy.MeltContext;

/**
 * Every device converts its items through one of these two methods, so this is the exact moment an ore gives off its fumes.
 */
@Mixin(HeatingRecipe.class)
abstract class HeatingRecipeMixin
{
    @Inject(method = "assembleItem", at = @At("HEAD"))
    private void tfcpp$assembleItem(ItemStack input, CallbackInfoReturnable<ItemStack> cir)
    {
        MeltContext.onConverted(input);
    }

    @Inject(method = "assembleStacked", at = @At("HEAD"))
    private void tfcpp$assembleStacked(ItemStack input, int stackSizeCap, boolean isDisplay, CallbackInfoReturnable<ItemStack> cir)
    {
        if (!isDisplay)
        {
            MeltContext.onConverted(input);
        }
    }
}
