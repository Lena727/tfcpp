package tfcpp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blockentities.FireboxBlockEntity;
import net.dries007.tfc.util.data.Fuel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import tfcpp.chemistry.FuelFlue;

/**
 * The point where a firepit, a forge or a blast furnace takes a piece of fuel to burn it. Sulfur in the fuel goes up the flue as sulfur dioxide at that moment, and only if what was taken is a fuel at all.
 */
final class FuelSulfurMixins
{
    @Mixin(AbstractFirepitBlockEntity.class)
    abstract static class Firepit
    {
        @ModifyExpressionValue(method = "consumeFuel", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/util/data/Fuel;get(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/util/data/Fuel;"))
        private Fuel tfcpp$sulfur(Fuel fuel, @Local ItemStack stack)
        {
            if (fuel != null)
            {
                FuelFlue.burned((BlockEntity) (Object) this, stack, FuelFlue.OPEN_FIRE_SHARE);
            }
            return fuel;
        }
    }

    /** A firebox has a consumeFuel of its own, and does not go through the firepit's. */
    @Mixin(FireboxBlockEntity.class)
    abstract static class Firebox
    {
        @ModifyExpressionValue(method = "consumeFuel", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/util/data/Fuel;get(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/util/data/Fuel;"))
        private Fuel tfcpp$sulfur(Fuel fuel, @Local ItemStack stack)
        {
            if (fuel != null)
            {
                FuelFlue.burned((BlockEntity) (Object) this, stack, FuelFlue.OPEN_FIRE_SHARE);
            }
            return fuel;
        }
    }

    @Mixin(CharcoalForgeBlockEntity.class)
    abstract static class Forge
    {
        @ModifyExpressionValue(method = "consumeFuel", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/util/data/Fuel;get(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/util/data/Fuel;"))
        private Fuel tfcpp$sulfur(Fuel fuel, @Local ItemStack stack)
        {
            if (fuel != null)
            {
                FuelFlue.burned((BlockEntity) (Object) this, stack, FuelFlue.OPEN_FIRE_SHARE);
            }
            return fuel;
        }
    }

    @Mixin(BlastFurnaceBlockEntity.class)
    abstract static class BlastFurnace
    {
        @ModifyExpressionValue(method = "consumeFuel", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/util/data/Fuel;get(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/util/data/Fuel;"))
        private Fuel tfcpp$sulfur(Fuel fuel, @Local ItemStack stack)
        {
            if (fuel != null)
            {
                FuelFlue.burned((BlockEntity) (Object) this, stack, FuelFlue.BLAST_FURNACE_SHARE);
            }
            return fuel;
        }
    }

    private FuelSulfurMixins() {}
}
