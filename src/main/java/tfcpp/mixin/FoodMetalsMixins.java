package tfcpp.mixin;

import java.util.ArrayList;
import java.util.List;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.dries007.tfc.common.recipes.AdvancedShapedRecipe;
import net.dries007.tfc.common.recipes.AdvancedShapelessRecipe;
import net.dries007.tfc.common.recipes.outputs.ItemStackProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import tfcpp.ecology.FoodContamination;

/** What a food carries from the soil is not cooked out of it: it goes through every recipe whose output is made from food. */
final class FoodMetalsMixins
{
    private FoodMetalsMixins() {}

    /** TFC makes the output of heating, grinding, barrel and pot recipes from one input, through this provider: the metal in the input stack goes to the output. */
    @Mixin(ItemStackProvider.class)
    abstract static class Provider
    {
        @ModifyReturnValue(method = "getStack(Lnet/minecraft/world/item/ItemStack;Lnet/dries007/tfc/common/recipes/outputs/ItemStackModifier$Context;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
        private ItemStack tfcpp$carry(ItemStack output, ItemStack input)
        {
            if (!input.isEmpty() && !output.isEmpty())
            {
                FoodContamination.carry(List.of(input), output);
            }
            return output;
        }
    }

    /** A crafting recipe may take many foods: the metal of all of them is added up. */
    @Mixin(AdvancedShapelessRecipe.class)
    abstract static class Shapeless
    {
        @ModifyReturnValue(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
        private ItemStack tfcpp$carry(ItemStack output, CraftingInput input, HolderLookup.Provider registries)
        {
            FoodContamination.carryOnePerSlot(input.items(), output);
            return output;
        }
    }

    @Mixin(AdvancedShapedRecipe.class)
    abstract static class Shaped
    {
        @ModifyReturnValue(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
        private ItemStack tfcpp$carry(ItemStack output, CraftingInput input, HolderLookup.Provider registries)
        {
            FoodContamination.carryOnePerSlot(input.items(), output);
            return output;
        }
    }
}
