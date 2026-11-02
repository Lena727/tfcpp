package tfcpp.pneumatics.gas;

import net.dries007.tfc.common.items.Powder;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Stone dusting. Mines have been made safe from dust explosions since the 1920s by spreading ground limestone through the galleries until at least 65% of the dust on the floor cannot burn. Inspectors took samples to check it, and so does a player here.
 */
public final class StoneDusting
{
    /** What a sample must show for the dust to stop a flame. */
    public static final float REQUIRED_INCOMBUSTIBLE = 0.65f;

    public static boolean isStoneDust(ItemStack stack)
    {
        return stack.is(TFCItems.POWDERS.get(Powder.FLUX).asItem());
    }

    /**
     * @return {@code true} if the powder was used on the face of this block. Nothing is used if the block takes the item itself, if the air in front of the face is a block or open to the sky.
     */
    public static boolean use(ServerLevel level, Player player, InteractionHand hand, BlockPos clicked, @Nullable Direction face)
    {
        final ItemStack stack = player.getItemInHand(hand);
        if (face == null || !isStoneDust(stack) || handlesItemItself(level, player, clicked))
        {
            return false;
        }

        final float incombustible = DustSources.spreadStoneDust(level, clicked.relative(face));
        if (incombustible < 0)
        {
            return false;
        }

        if (!player.getAbilities().instabuild)
        {
            stack.shrink(1);
        }
        level.playSound(null, clicked, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.8f, 1.3f);
        player.displayClientMessage(Component.translatable("tfcpp.stone_dust.sample", Math.round(incombustible * 100f), Math.round(REQUIRED_INCOMBUSTIBLE * 100f)), true);
        return true;
    }

    /** Devices and doors use the item or the click themselves. A player who is crouching clicks past all of them, as with any item. */
    private static boolean handlesItemItself(ServerLevel level, Player player, BlockPos pos)
    {
        if (player.isSecondaryUseActive())
        {
            return false;
        }
        final BlockState state = level.getBlockState(pos);
        return level.getBlockEntity(pos) != null
            || state.getMenuProvider(level, pos) != null
            || state.getBlock() instanceof LeverBlock
            || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.BUTTONS)
            || state.is(BlockTags.BEDS) || state.is(BlockTags.ANVIL);
    }

    private StoneDusting() {}
}
