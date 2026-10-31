package tfcpp.mixin;

import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.blockentities.FireboxBlockEntity;
import net.dries007.tfc.common.blockentities.PitKilnBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tfcpp.metallurgy.MeltContext;

/**
 * Each device that can heat ore announces where it is for the length of its tick. See {@link MeltContext}.
 */
final class MeltContextMixins
{
    @Mixin(CrucibleBlockEntity.class)
    abstract static class Crucible
    {
        @Inject(method = "serverTick", at = @At("HEAD"))
        private static void tfcpp$enter(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity entity, CallbackInfo ci)
        {
            MeltContext.enter(level, pos);
        }

        @Inject(method = "serverTick", at = @At("RETURN"))
        private static void tfcpp$exit(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity entity, CallbackInfo ci)
        {
            MeltContext.exit();
        }
    }

    @Mixin(CharcoalForgeBlockEntity.class)
    abstract static class Forge
    {
        @Inject(method = "serverTick", at = @At("HEAD"))
        private static void tfcpp$enter(Level level, BlockPos pos, BlockState state, CharcoalForgeBlockEntity entity, CallbackInfo ci)
        {
            MeltContext.enter(level, pos);
        }

        @Inject(method = "serverTick", at = @At("RETURN"))
        private static void tfcpp$exit(Level level, BlockPos pos, BlockState state, CharcoalForgeBlockEntity entity, CallbackInfo ci)
        {
            MeltContext.exit();
        }
    }

    @Mixin(AbstractFirepitBlockEntity.class)
    abstract static class Firepit
    {
        @Inject(method = "serverTick", at = @At("HEAD"))
        private static void tfcpp$enter(Level level, BlockPos pos, BlockState state, AbstractFirepitBlockEntity<?> entity, CallbackInfo ci)
        {
            MeltContext.enter(level, pos);
        }

        @Inject(method = "serverTick", at = @At("RETURN"))
        private static void tfcpp$exit(Level level, BlockPos pos, BlockState state, AbstractFirepitBlockEntity<?> entity, CallbackInfo ci)
        {
            MeltContext.exit();
        }
    }

    @Mixin(PitKilnBlockEntity.class)
    abstract static class PitKiln
    {
        @Inject(method = "serverTick", at = @At("HEAD"))
        private static void tfcpp$enter(Level level, BlockPos pos, BlockState state, PitKilnBlockEntity entity, CallbackInfo ci)
        {
            MeltContext.enter(level, pos);
        }

        @Inject(method = "serverTick", at = @At("RETURN"))
        private static void tfcpp$exit(Level level, BlockPos pos, BlockState state, PitKilnBlockEntity entity, CallbackInfo ci)
        {
            MeltContext.exit();
        }
    }

    @Mixin(FireboxBlockEntity.class)
    abstract static class Firebox
    {
        @Inject(method = "serverTick", at = @At("HEAD"))
        private static void tfcpp$enter(Level level, BlockPos pos, BlockState state, FireboxBlockEntity entity, CallbackInfo ci)
        {
            MeltContext.enter(level, pos);
        }

        @Inject(method = "serverTick", at = @At("RETURN"))
        private static void tfcpp$exit(Level level, BlockPos pos, BlockState state, FireboxBlockEntity entity, CallbackInfo ci)
        {
            MeltContext.exit();
        }
    }

    private MeltContextMixins() {}
}
