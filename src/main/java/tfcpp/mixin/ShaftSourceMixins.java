package tfcpp.mixin;

import net.dries007.tfc.common.blockentities.BellowsBlockEntity;
import net.dries007.tfc.common.blockentities.TFCBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.PumpBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.WaterWheelBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.WindmillBlockEntity;
import net.dries007.tfc.common.blocks.rock.AqueductBlock;
import net.dries007.tfc.common.blocks.rotation.WindmillBlock;
import net.dries007.tfc.util.rotation.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import tfcpp.mechanics.ShaftPhysics;
import tfcpp.mechanics.ShaftSource;

/**
 * Makes TFC's sources slow down under load. These speeds run on the client as well, so the factor is saved with the block entity and synced with it, and shafts visibly slow.
 */
final class ShaftSourceMixins
{
    private static final float RESYNC_CHANGE = 0.02f;
    /** In radians per tick, about 0.2 rpm. */
    private static final float STOPPED_SPEED = 1e-3f;

    @Mixin(WaterWheelBlockEntity.class)
    abstract static class WaterWheel implements ShaftSource
    {
        @Unique private float tfcpp$factor = 1f;
        @Unique private float tfcpp$stall;

        @ModifyVariable(method = "clientTick", at = @At("STORE"), ordinal = 1)
        private static float tfcpp$loadedSpeed(float freeSpeed, Level level, BlockPos pos, BlockState state, WaterWheelBlockEntity wheel)
        {
            final WaterWheel self = (WaterWheel) (Object) wheel;
            self.tfcpp$stall = ShaftPhysics.waterWheelStallTorque(freeSpeed);
            return freeSpeed * self.tfcpp$factor;
        }

        @Override
        public float tfcpp$stallTorque()
        {
            return tfcpp$stall;
        }

        @Override
        public float tfcpp$speedFactor()
        {
            return tfcpp$factor;
        }

        @Override
        public void tfcpp$setSpeedFactor(float factor)
        {
            if (Math.abs(factor - tfcpp$factor) > RESYNC_CHANGE || (factor != tfcpp$factor && (factor == 0f || factor == 1f)))
            {
                tfcpp$factor = factor;
                ((TFCBlockEntity) (Object) this).markForSync();
            }
        }

        @Inject(method = "saveAdditional", at = @At("TAIL"))
        private void tfcpp$save(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci)
        {
            tag.putFloat("tfcppSpeedFactor", tfcpp$factor);
        }

        @Inject(method = "loadAdditional", at = @At("TAIL"))
        private void tfcpp$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci)
        {
            tfcpp$factor = tag.contains("tfcppSpeedFactor") ? tag.getFloat("tfcppSpeedFactor") : 1f;
        }
    }

    @Mixin(WindmillBlockEntity.class)
    abstract static class Windmill implements ShaftSource
    {
        @Unique private float tfcpp$factor = 1f;
        @Unique private float tfcpp$stall;

        @ModifyVariable(method = "clientTick", at = @At("STORE"), ordinal = 3)
        private static float tfcpp$loadedSpeed(float freeSpeed, Level level, BlockPos pos, BlockState state, WindmillBlockEntity windmill)
        {
            final Windmill self = (Windmill) (Object) windmill;
            final int blades = state.getValue(WindmillBlock.COUNT);
            final float calm = Mth.map(blades, 1, WindmillBlockEntity.SLOTS, 0, WindmillBlockEntity.MAX_SPEED) + (blades > 1 ? WindmillBlockEntity.MIN_SPEED : 0);
            self.tfcpp$stall = ShaftPhysics.windmillStallTorque(freeSpeed, calm, blades);
            return freeSpeed * self.tfcpp$factor;
        }

        @Override
        public float tfcpp$stallTorque()
        {
            return tfcpp$stall;
        }

        @Override
        public float tfcpp$speedFactor()
        {
            return tfcpp$factor;
        }

        @Override
        public void tfcpp$setSpeedFactor(float factor)
        {
            if (Math.abs(factor - tfcpp$factor) > RESYNC_CHANGE || (factor != tfcpp$factor && (factor == 0f || factor == 1f)))
            {
                tfcpp$factor = factor;
                ((TFCBlockEntity) (Object) this).markForSync();
            }
        }

        @Inject(method = "saveAdditional", at = @At("TAIL"))
        private void tfcpp$save(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci)
        {
            tag.putFloat("tfcppSpeedFactor", tfcpp$factor);
        }

        @Inject(method = "loadAdditional", at = @At("TAIL"))
        private void tfcpp$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci)
        {
            tfcpp$factor = tag.contains("tfcppSpeedFactor") ? tag.getFloat("tfcppSpeedFactor") : 1f;
        }
    }

    /** The pump is run by {@link tfcpp.mechanics.MassPump} instead, which moves water and does not make it. */
    @Mixin(PumpBlockEntity.class)
    abstract static class Pump
    {
        @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
        private static void tfcpp$noWaterFromNothing(Level level, BlockPos pos, BlockState state, PumpBlockEntity pump, CallbackInfo ci)
        {
            ci.cancel();
        }

        /** What a pump has lifted stays where it was put when the pump is removed. */
        @Inject(method = "onRemoved", at = @At("HEAD"), cancellable = true)
        private void tfcpp$keepTheWater(CallbackInfo ci)
        {
            ci.cancel();
        }
    }

    /** A shaft can now stand still with a bellows on it. TFC works out the time to its next push by dividing by the speed, which at zero would never let it push again. */
    @Mixin(BellowsBlockEntity.class)
    abstract static class Bellows
    {
        @ModifyVariable(method = "tickBoth", at = @At("STORE"), ordinal = 0)
        private static Rotation tfcpp$stoppedShaftDoesNotPush(Rotation rotation)
        {
            return rotation != null && Math.abs(rotation.speed()) < STOPPED_SPEED ? null : rotation;
        }
    }

    /** One source next to an aqueduct would fill it again after every bucket, and each of those buckets could be poured out as a new source. TFC already refuses this for lava. */
    @Mixin(AqueductBlock.class)
    abstract static class Aqueduct
    {
        @Inject(method = "pickupBlock", at = @At("HEAD"), cancellable = true)
        private void tfcpp$noBucketsFromThePassingFlow(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state, CallbackInfoReturnable<ItemStack> cir)
        {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    private ShaftSourceMixins() {}
}
