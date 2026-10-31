package tfcpp.mixin;

import net.dries007.tfc.common.blockentities.TFCBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tfcpp.pneumatics.gas.GasTracker;

/**
 * Lets the gas grid know exactly which fires exist, with no scanning of block entities.
 */
@Mixin(TFCBlockEntity.class)
abstract class TFCBlockEntityMixin
{
    @Inject(method = "onLoad", at = @At("TAIL"))
    private void tfcpp$onLoad(CallbackInfo ci)
    {
        final BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() instanceof ServerLevel level)
        {
            GasTracker.get(level).onBlockEntityLoaded(self);
        }
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void tfcpp$setRemoved(CallbackInfo ci)
    {
        tfcpp$unload();
    }

    @Inject(method = "onChunkUnloaded", at = @At("TAIL"))
    private void tfcpp$onChunkUnloaded(CallbackInfo ci)
    {
        tfcpp$unload();
    }

    private void tfcpp$unload()
    {
        final BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() instanceof ServerLevel level)
        {
            GasTracker.get(level).onBlockEntityUnloaded(self);
        }
    }
}
