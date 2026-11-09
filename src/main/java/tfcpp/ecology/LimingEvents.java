package tfcpp.ecology;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.soil.ISoilBlock;
import net.dries007.tfc.common.items.Powder;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lime, flux and wood ash scattered on the ground by a player who sneaks and right clicks (without sneaking TFC does what it always did, and wood ash that fertilises a field also limes it, see the mixin on {@code CropHelpers.useFertilizer}): the whole held stack is spread over the soil of the chunk and neutralises its acid ({@link Liming}, {@link SoilCell#lime}). It takes kilograms to tonnes to bring an acidified chunk back, and a later version brings spreaders; metals stay.
 */
public final class LimingEvents
{
    private LimingEvents() {}

    /** @return the equivalents of acid that one piece of this item neutralises, or 0 if it is not a liming material */
    public static double pieceEquivalents(ItemStack stack)
    {
        return equivalentsOf(stack);
    }

    private static double equivalentsOf(ItemStack stack)
    {
        if (stack.is(TFCItems.POWDERS.get(Powder.LIME).get()))
        {
            return Liming.LIME_EQ_PER_PIECE;
        }
        if (stack.is(TFCItems.POWDERS.get(Powder.FLUX).get()))
        {
            return Liming.FLUX_EQ_PER_PIECE;
        }
        if (stack.is(TFCItems.POWDERS.get(Powder.WOOD_ASH).get()))
        {
            return Liming.WOOD_ASH_EQ_PER_PIECE;
        }
        return 0.0;
    }

    private static boolean isGround(BlockState state)
    {
        return state.getBlock() instanceof ISoilBlock || state.is(TFCTags.Blocks.DIRT) || state.is(TFCTags.Blocks.GRASS) || state.is(TFCTags.Blocks.FARMLANDS) || state.is(TFCTags.Blocks.COARSE_DIRT);
    }

    public static void onUse(PlayerInteractEvent.RightClickBlock event)
    {
        final ItemStack stack = event.getItemStack();
        final double eq = equivalentsOf(stack);
        if (!event.getEntity().isShiftKeyDown() || !(eq > 0.0) || !isGround(event.getLevel().getBlockState(event.getPos())))
        {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (event.getLevel() instanceof ServerLevel level)
        {
            Pollution.lime(level, event.getPos(), Liming.equivalents(eq, stack.getCount()));
            level.sendParticles(ParticleTypes.CLOUD, event.getPos().getX() + 0.5, event.getPos().getY() + 1.0, event.getPos().getZ() + 0.5, 12, 0.5, 0.1, 0.5, 0.02);
            level.playSound(null, event.getPos(), SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
            if (!event.getEntity().isCreative())
            {
                stack.setCount(0);
            }
        }
    }
}
