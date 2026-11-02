package tfcpp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dries007.tfc.common.blockentities.QuernBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import tfcpp.pneumatics.gas.DustSources;

/**
 * A quern gives no event when it finishes. The input is used up after the recipe has matched and its product has been made, which is the last moment the item is still known and the first that a grind is certain to have happened.
 */
@Mixin(QuernBlockEntity.class)
abstract class QuernMixin
{
    @WrapOperation(method = "finishGrinding", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void tfcpp$dust(ItemStack input, int amount, Operation<Void> original)
    {
        final QuernBlockEntity self = (QuernBlockEntity) (Object) this;
        if (self.getLevel() instanceof ServerLevel level && DustSources.isCoal(input))
        {
            DustSources.onGround(level, self.getBlockPos());
        }
        original.call(input, amount);
    }
}
