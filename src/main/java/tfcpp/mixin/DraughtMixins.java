package tfcpp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Shadow;
import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.common.blocks.devices.CrucibleBlock;
import net.dries007.tfc.common.blocks.devices.FirepitBlock;
import net.dries007.tfc.util.data.Fuel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import tfcpp.metallurgy.Blowing;
import tfcpp.metallurgy.DraughtState;
import tfcpp.metallurgy.Hearth;

/**
 * The temperature of a charcoal forge or a firepit follows {@code Draught} and not only the fuel: TFC's own call is replaced by it, and each device remembers which fuel it took and who is blowing into it.
 * A person blowing through a pipe must also get past what a right click on these blocks does, which is to open their screen.
 */
final class DraughtMixins
{
    @Mixin(CharcoalForgeBlockEntity.class)
    abstract static class Forge implements Hearth
    {
        @Shadow private int burnTicks;
        @Unique private final DraughtState tfcpp$draught = new DraughtState();

        @Override
        public DraughtState tfcpp$draught()
        {
            return tfcpp$draught;
        }

        @Override
        public int tfcpp$burnTicks()
        {
            return burnTicks;
        }

        /** TFC takes 1 burn tick a tick, or 2 with any bellows or in the rain. Here the fuel burns as fast as the air: 1 + air, in fractions carried over. */
        @WrapOperation(method = "serverTick", at = @At(value = "FIELD", target = "Lnet/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity;burnTicks:I", opcode = Opcodes.PUTFIELD, ordinal = 0))
        private static void tfcpp$burnWithAir(CharcoalForgeBlockEntity forge, int tfcValue, Operation<Void> original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos)
        {
            final Hearth hearth = (Hearth) forge;
            final boolean raining = level.isRainingAt(pos.above()) || level.isRainingAt(pos.above(2));
            original.call(forge, hearth.tfcpp$burnTicks() - hearth.tfcpp$draught().takeBurnTicks(raining));
        }

        @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/common/component/heat/HeatCapability;adjustDeviceTemp(FFIZ)F"))
        private static float tfcpp$governTemperature(float temperature, float burnTemperature, int airTicks, boolean raining, Operation<Float> original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) CharcoalForgeBlockEntity forge)
        {
            final DraughtState draught = ((Hearth) forge).tfcpp$draught();
            if (burnTemperature <= 0f)
            {
                draught.cold();
                return original.call(temperature, burnTemperature, airTicks, raining);
            }
            return draught.adjust(level, pos, temperature, burnTemperature, airTicks, raining);
        }

        @ModifyExpressionValue(method = "consumeFuel", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/util/data/Fuel;get(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/util/data/Fuel;"))
        private Fuel tfcpp$rememberFuel(Fuel fuel, @Local ItemStack stack)
        {
            if (fuel != null)
            {
                tfcpp$draught.fuelTaken(stack, fuel.temperature());
            }
            return fuel;
        }

        @Inject(method = "saveAdditional", at = @At("TAIL"))
        private void tfcpp$save(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci)
        {
            tfcpp$draught.save(tag);
        }

        @Inject(method = "loadAdditional", at = @At("TAIL"))
        private void tfcpp$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci)
        {
            tfcpp$draught.load(tag);
        }
    }

    @Mixin(AbstractFirepitBlockEntity.class)
    abstract static class Firepit implements Hearth
    {
        @Shadow private int burnTicks;
        @Unique private final DraughtState tfcpp$draught = new DraughtState();

        @Override
        public DraughtState tfcpp$draught()
        {
            return tfcpp$draught;
        }

        @Override
        public int tfcpp$burnTicks()
        {
            return burnTicks;
        }

        @WrapOperation(method = "serverTick", at = @At(value = "FIELD", target = "Lnet/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity;burnTicks:I", opcode = Opcodes.PUTFIELD, ordinal = 0))
        private static void tfcpp$burnWithAir(AbstractFirepitBlockEntity<?> firepit, int tfcValue, Operation<Void> original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos)
        {
            final Hearth hearth = (Hearth) firepit;
            original.call(firepit, hearth.tfcpp$burnTicks() - hearth.tfcpp$draught().takeBurnTicks(level.isRainingAt(pos)));
        }

        /** A firepit made into a pot, a grill or a stove keeps burning the same fuel. */
        @Inject(method = "copyFrom", at = @At("TAIL"))
        private void tfcpp$copyDraught(AbstractFirepitBlockEntity<?> other, CallbackInfo ci)
        {
            tfcpp$draught.copyFrom(((Hearth) other).tfcpp$draught());
        }

        @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/common/component/heat/HeatCapability;adjustDeviceTemp(FFIZ)F"))
        private static float tfcpp$governTemperature(float temperature, float burnTemperature, int airTicks, boolean raining, Operation<Float> original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) AbstractFirepitBlockEntity<?> firepit)
        {
            final DraughtState draught = ((Hearth) firepit).tfcpp$draught();
            if (burnTemperature <= 0f)
            {
                draught.cold();
                return original.call(temperature, burnTemperature, airTicks, raining);
            }
            return draught.adjust(level, pos, temperature, burnTemperature, airTicks, raining);
        }

        @ModifyExpressionValue(method = "consumeFuel", at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/util/data/Fuel;get(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/util/data/Fuel;"))
        private Fuel tfcpp$rememberFuel(Fuel fuel, @Local ItemStack stack)
        {
            if (fuel != null)
            {
                tfcpp$draught.fuelTaken(stack, fuel.temperature());
            }
            return fuel;
        }

        @Inject(method = "saveAdditional", at = @At("TAIL"))
        private void tfcpp$save(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci)
        {
            tfcpp$draught.save(tag);
        }

        @Inject(method = "loadAdditional", at = @At("TAIL"))
        private void tfcpp$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci)
        {
            tfcpp$draught.load(tag);
        }
    }

    @Mixin(CharcoalForgeBlock.class)
    abstract static class ForgeBlock
    {
        @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
        private void tfcpp$blowInstead(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir)
        {
            if (Blowing.isBlowpipe(stack))
            {
                cir.setReturnValue(ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
            }
        }
    }

    @Mixin(FirepitBlock.class)
    abstract static class FirepitBlockUse
    {
        @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
        private void tfcpp$blowInstead(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir)
        {
            if (Blowing.isBlowpipe(stack))
            {
                cir.setReturnValue(ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
            }
        }
    }

    /** Only a crucible with a hearth under it, directly or through vessels: one anywhere else opens its screen as ever. */
    @Mixin(CrucibleBlock.class)
    abstract static class CrucibleBlockUse
    {
        @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
        private void tfcpp$blowInstead(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir)
        {
            if (Blowing.isBlowpipe(stack) && Blowing.hearthAt(level, pos) != null)
            {
                cir.setReturnValue(ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
            }
        }
    }

    private DraughtMixins() {}
}
