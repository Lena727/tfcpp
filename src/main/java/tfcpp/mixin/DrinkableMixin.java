package tfcpp.mixin;

import net.dries007.tfc.common.player.IPlayerInfo;
import net.dries007.tfc.util.data.Drinkable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tfcpp.health.Drinking;
import tfcpp.pneumatics.gas.Breathing;

/** What a drink does in TFC is only ever food and potion effects. Mercury has to reach the same dose in the body as breathing it does, and a drink from a block of water takes what the water of that place carries (pathogens, metals). */
@Mixin(Drinkable.class)
abstract class DrinkableMixin
{
    @Inject(method = "onDrink", at = @At("HEAD"))
    private void tfcpp$swallow(Player player, int mB, CallbackInfo ci)
    {
        Breathing.onDrink(player, (Drinkable) (Object) this, mB);
    }

    /** A drink with the hand from a block of water: the one place where TFC knows which block it is. */
    @Inject(method = "doDrink", at = @At("HEAD"))
    private static void tfcpp$drinkFromBlock(Level level, Player player, BlockState state, BlockPos pos, IPlayerInfo info, Drinkable drinkable, CallbackInfo ci)
    {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel server)
        {
            Drinking.fromWorld(serverPlayer, server, pos);
        }
    }
}
