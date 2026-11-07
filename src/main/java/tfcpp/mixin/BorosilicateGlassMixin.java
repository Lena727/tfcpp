package tfcpp.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.dries007.tfc.common.component.glass.GlassOperation;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import tfcpp.chemistry.BorosilicateGlass;

/**
 * TFC has one temperature for a step of glassworking, 480 C, whatever the glass: it is compared with the pipe in the player's hand and written into the operation in code, so no data can say that a glass is worked hotter. Borosilicate is worked from about 1250 C, and only on a ceramic pipe.
 */
@Mixin(GlassOperation.class)
abstract class BorosilicateGlassMixin
{
    @Shadow
    private float minTemperature;

    @ModifyReturnValue(method = "hasRequiredTemperature", at = @At("RETURN"))
    private boolean tfcpp$workingPoint(boolean original, ItemStack stack)
    {
        return BorosilicateGlass.workable(original, minTemperature > 0f, BorosilicateGlass.carriesOurs(stack), BorosilicateGlass.isBrass(stack), HeatCapability.getTemperature(stack));
    }
}
