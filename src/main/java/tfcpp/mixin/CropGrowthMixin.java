package tfcpp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dries007.tfc.common.blockentities.CropBlockEntity;
import net.dries007.tfc.common.blocks.crop.CropHelpers;
import net.dries007.tfc.common.blocks.crop.IPickableCrop;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;

import tfcpp.ecology.CropSoil;
import tfcpp.ecology.FoodContamination;
import tfcpp.ecology.LimingEvents;
import tfcpp.ecology.Pollution;

/** Soil and air act on the growth of a crop, and what a crop carries out of the soil goes into its fruit. */
final class CropGrowthMixin
{
    private CropGrowthMixin() {}

    /** The step of growth: its head reads the ground and the air and irrigates, the growth of the step is multiplied by what they allow, and a step that the ground or the air makes deadly is unhealthy, which is TFC's own way for a crop to die. */
    @Mixin(CropHelpers.class)
    abstract static class Step
    {
        @Inject(method = "growthTickStep", at = @At("HEAD"))
        private static void tfcpp$read(Level level, BlockPos pos, BlockState state, RandomSource random, long fromTick, long toTick, CropBlockEntity crop, CallbackInfoReturnable<Boolean> cir)
        {
            CropSoil.prepare(level, pos, fromTick, toTick);
        }

        /** The second store of {@code growth} is the growth that the step adds (the first is its start). Only that branch is scaled: what is left over goes to expiry as before, so that a ripe crop expires at TFC's rate. */
        @ModifyVariable(method = "growthTickStep", at = @At(value = "STORE", ordinal = 1), name = "growth")
        private static float tfcpp$scaleGrowth(float growth, @Local(argsOnly = true) CropBlockEntity crop)
        {
            final float initial = crop.getGrowth(); // TFC sets it only at the end of the step
            return (float) (initial + (growth - initial) * CropSoil.growth());
        }

        @ModifyVariable(method = "growthTickStep", at = @At("STORE"), name = "healthy")
        private static boolean tfcpp$poison(boolean healthy)
        {
            return healthy && !CropSoil.unhealthy();
        }
    }

    /** Wood ash on a field fertilises through TFC, and it limes it as well: for each piece that TFC takes the soil is credited the neutralising value of the ash (real ash does both). */
    @Mixin(CropHelpers.class)
    abstract static class Fertilize
    {
        @WrapOperation(method = "useFertilizer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
        private static void tfcpp$lime(ItemStack stack, int count, Operation<Void> original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos farmlandPos)
        {
            final double eq = LimingEvents.pieceEquivalents(stack);
            if (eq > 0.0 && level instanceof ServerLevel server)
            {
                Pollution.lime(server, farmlandPos, eq * count);
            }
            original.call(stack, count);
        }
    }

    /** The fruit that a hand picks from a crop that keeps growing: marked with what the soil under it holds. */
    @Mixin(IPickableCrop.class)
    interface Picked
    {
        @WrapOperation(method = "getItemInteractionResult", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/items/ItemHandlerHelper;giveItemToPlayer(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V"))
        private static void tfcpp$mark(Player player, ItemStack stack, Operation<Void> original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos)
        {
            if (level instanceof ServerLevel server)
            {
                FoodContamination.harvest(server, pos, stack);
            }
            original.call(player, stack);
        }
    }
}
