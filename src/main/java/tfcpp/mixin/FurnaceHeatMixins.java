package tfcpp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.dries007.tfc.common.blockentities.FireboxBlockEntity;
import net.dries007.tfc.common.blockentities.PitKilnBlockEntity;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** What a fire can really do. */
final class FurnaceHeatMixins
{
    /** A fire in a pit of straw and logs, with no draught, gets to about 900 C, where TFC has it at 1400 C. */
    private static final float PIT_KILN_TEMPERATURE = 900f;
    /** What a blast adds to burning charcoal. A real forge reaches 1400-1600 C, and TFC's 600 C on top would make melting steel easy. */
    private static final float MOST_FROM_A_BLAST = 450f;

    @Mixin(PitKilnBlockEntity.class)
    abstract static class PitKiln
    {
        @Shadow @Final private HeatingRecipe[] cachedRecipes;

        /** The kiln runs its cached recipe again every tick, on what the recipe has already made. That goes unnoticed until a recipe has a chance of giving nothing, as kaolin and seaweed do, and then it rolls until nothing is left. */
        @ModifyExpressionValue(method = "cookContents", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/common/recipes/HeatingRecipe;assembleItem(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
        private ItemStack tfcpp$onlyOnce(ItemStack output, @Local int slot)
        {
            cachedRecipes[slot] = null;
            return output;
        }

        @ModifyExpressionValue(method = "cookContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
        private float tfcpp$limitHeat(float temperature)
        {
            return Math.min(temperature, PIT_KILN_TEMPERATURE);
        }

        /** Metal that melts in a kiln runs down into the ashes and sets again. TFC deletes it, and here it stays in the kiln as it was. */
        @ModifyExpressionValue(method = "cookContents", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/common/recipes/HeatingRecipe;isValidTemperature(F)Z"))
        private boolean tfcpp$keepWhatMelts(boolean valid, @Local HeatingRecipe recipe, @Local ItemStack stack)
        {
            return valid && recipe.assembleFluid(stack).isEmpty();
        }
    }

    @Mixin(FireboxBlockEntity.class)
    abstract static class Firebox
    {
        /** The same goes for metal on a shelf above a firebox. */
        @ModifyExpressionValue(method = "tryApplyRecipe", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/common/recipes/HeatingRecipe;isValidTemperature(F)Z"))
        private static boolean tfcpp$keepWhatMelts(boolean valid, @Local HeatingRecipe recipe, @Local(argsOnly = true) ItemStack item)
        {
            return valid && recipe.assembleFluid(item).isEmpty();
        }
    }

    @Mixin(HeatCapability.class)
    abstract static class Blast
    {
        @ModifyConstant(method = "targetDeviceTemp", constant = @Constant(floatValue = 600f))
        private static float tfcpp$limitBlast(float limit)
        {
            return MOST_FROM_A_BLAST;
        }
    }

    private FurnaceHeatMixins() {}
}
