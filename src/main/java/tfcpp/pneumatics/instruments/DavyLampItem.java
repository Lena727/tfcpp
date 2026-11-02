package tfcpp.pneumatics.instruments;

import java.util.List;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.common.items.LampBlockItem;
import net.dries007.tfc.util.data.LampFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import tfcpp.chemistry.Chemistry;

/**
 * A Davy lamp in the hand. Whether its flame is alight is kept on the stack, as it is on the block once it is put down: a flame that has gone out in bad air stays out, and has to be lit again. A lamp is lit with the lighters that light a gas burner, held in the other hand.
 */
public final class DavyLampItem extends LampBlockItem
{
    public DavyLampItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    public static boolean isLit(ItemStack stack)
    {
        return stack.getOrDefault(Instruments.LAMP_LIT.get(), false);
    }

    public static void setLit(ItemStack stack, boolean lit)
    {
        if (lit)
        {
            stack.set(Instruments.LAMP_LIT.get(), true);
        }
        else
        {
            stack.remove(Instruments.LAMP_LIT.get()); // An unlit lamp is the plain one, and stacks with the others
        }
    }

    /** @return the fuel that burns in this lamp, if it holds any */
    @Nullable
    public static LampFuel fuelOf(ItemStack stack)
    {
        final FluidStack fluid = FluidHelpers.getContainedFluid(stack);
        return fluid.isEmpty() ? null : LampFuel.get(fluid.getFluid(), Instruments.DAVY_LAMP.get().defaultBlockState());
    }

    /**
     * Lights the lamp in this hand with a lighter in the other one, if that is what is being asked: the lamp is out, has oil in it, and the other hand holds something from {@code tfcpp:lights_gas_burner}.
     *
     * @return {@code null} if the player is not trying to light it, and the result of the attempt otherwise
     */
    @Nullable
    private static InteractionResult tryLight(Level level, Player player, InteractionHand hand)
    {
        final ItemStack lamp = player.getItemInHand(hand);
        final InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        final ItemStack lighter = player.getItemInHand(other);
        if (isLit(lamp) || !lighter.is(Chemistry.LIGHTS_BURNER))
        {
            return null;
        }
        if (!level.isClientSide)
        {
            if (fuelOf(lamp) == null)
            {
                player.displayClientMessage(Component.translatable("tfcpp.davy_lamp.no_oil"), true);
            }
            else if (!DavyLamp.canLightIn(player))
            {
                player.displayClientMessage(Component.translatable("tfcpp.davy_lamp.wont_light"), true);
            }
            else
            {
                setLit(lamp, true);
                level.playSound(null, player.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1f, level.random.nextFloat() * 0.4f + 0.8f);
                lighter.hurtAndBreak(1, player, LivingEntity.getSlotForHand(other));
                player.displayClientMessage(Component.translatable("tfcpp.davy_lamp.lit"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A lamp that is put down alight must not be taken for one that has been burning since the start of the world: the block counts the time it has burned from the moment of the last reset, and a fresh one counts from nothing. Filling it with its oil would otherwise burn the whole of the oil at once, and put it out.
     */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state)
    {
        level.getBlockEntity(pos, TFCBlockEntities.LAMP.get()).ifPresent(TickCounterBlockEntity::resetCounter);
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        final @Nullable Player player = context.getPlayer();
        final @Nullable InteractionResult lighting = player == null ? null : tryLight(context.getLevel(), player, context.getHand());
        return lighting != null ? lighting : super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        final @Nullable InteractionResult lighting = tryLight(level, player, hand);
        return lighting != null ? new InteractionResultHolder<>(lighting, player.getItemInHand(hand)) : super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(isLit(stack) ? "tfcpp.davy_lamp.item_lit" : "tfcpp.davy_lamp.item_unlit"));
    }
}
