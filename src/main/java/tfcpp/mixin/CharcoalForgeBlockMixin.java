package tfcpp.mixin;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.util.Helpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TFC refuses to run a forge that cannot see the sky. A chimney is not what makes a forge burn: it is what carries its fumes away.
 * Here a forge only needs its insulation, and one without a chimney fills the room with flue gas.
 */
@Mixin(CharcoalForgeBlock.class)
abstract class CharcoalForgeBlockMixin
{
    private static final Direction[] TFCPP$INSULATED_SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN};

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private static void tfcpp$chimneyIsNotStructural(LevelAccessor level, BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        final BlockState above = level.getBlockState(pos.above());
        if (!above.isAir() && !Helpers.isBlock(above, TFCTags.Blocks.CHARCOAL_FORGE_INVISIBLE))
        {
            cir.setReturnValue(false);
            return;
        }
        for (Direction side : TFCPP$INSULATED_SIDES)
        {
            if (!CharcoalForgeBlock.isForgeInsulationBlock(level.getBlockState(pos.relative(side))))
            {
                cir.setReturnValue(false);
                return;
            }
        }
        cir.setReturnValue(true);
    }
}
