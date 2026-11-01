package tfcpp.mixin;

import net.dries007.tfc.util.data.Drinkable;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tfcpp.pneumatics.gas.Breathing;

/** What a drink does in TFC is only ever food and potion effects. Mercury has to reach the same dose in the body as breathing it does. */
@Mixin(Drinkable.class)
abstract class DrinkableMixin
{
    @Inject(method = "onDrink", at = @At("HEAD"))
    private void tfcpp$swallow(Player player, int mB, CallbackInfo ci)
    {
        Breathing.onDrink(player, (Drinkable) (Object) this, mB);
    }
}
