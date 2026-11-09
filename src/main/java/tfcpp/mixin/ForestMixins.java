package tfcpp.mixin;

import net.dries007.tfc.common.blocks.wood.TFCLeavesBlock;
import net.dries007.tfc.common.blocks.wood.TFCSaplingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import org.jetbrains.annotations.Nullable;

import tfcpp.ecology.ForestHealth;
import tfcpp.ecology.ForestTrees;
import tfcpp.ecology.Pollution;
import tfcpp.ecology.SoilCell;

/** The health of a chunk's forest acts through the random ticks that TFC already gives leaves and saplings: no ticking of its own. */
final class ForestMixins
{
    private ForestMixins() {}

    /** A leaf of a stand in bad health falls as TFC's own leaves fall when they lose their wood (the same removal, particles and fallen leaves); a leaf placed by a player stays. */
    @Mixin(TFCLeavesBlock.class)
    abstract static class Leaves
    {
        @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
        private void tfcpp$fall(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci)
        {
            if (state.getValue(TFCLeavesBlock.PERSISTENT))
            {
                return;
            }
            final @Nullable SoilCell soil = Pollution.forestCell(level, pos);
            if (soil == null)
            {
                return; // Nothing has touched the chunk and no smoke has been over its region: a healthy forest, and nothing was worked out
            }
            final TFCLeavesBlock self = (TFCLeavesBlock) (Object) this;
            final double health = soil.forestHealth(ForestTrees.isConifer(self));
            if (ForestHealth.leafMayFall(health, ForestHealth.positionHash(pos.asLong())) && random.nextDouble() < ForestHealth.LEAF_FALL_PER_TICK)
            {
                level.removeBlock(pos, false);
                if (random.nextFloat() < 0.01f)
                {
                    self.createDestructionEffects(state, level, pos, random, false);
                }
                TFCLeavesBlock.doParticles(level, pos.getX() + random.nextFloat(), pos.getY() + random.nextFloat(), pos.getZ() + random.nextFloat(), 1);
                ci.cancel();
            }
        }
    }

    /** A sapling in a dying stand does not grow. */
    @Mixin(TFCSaplingBlock.class)
    abstract static class Sapling
    {
        @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
        private void tfcpp$stunted(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci)
        {
            final @Nullable SoilCell soil = Pollution.forestCell(level, pos);
            if (soil != null && !ForestHealth.saplingsGrow(soil.forestHealth(ForestTrees.isConifer((TFCSaplingBlock) (Object) this))))
            {
                TickCounterBlockEntity.reset(level, pos); // A stunted sapling does not store up the time it was held back
                ci.cancel();
            }
        }
    }
}
