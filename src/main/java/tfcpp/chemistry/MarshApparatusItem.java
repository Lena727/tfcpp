package tfcpp.chemistry;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import tfcpp.chemistry.PoisonTests.Mirror;
import tfcpp.core.TFCpp;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.instruments.Instruments;

/**
 * Marsh's apparatus: a flask of glass with zinc, in which sulfuric acid makes arsine from a sample. The arsine is broken by heat into a mirror of arsenic, which shows as a dark film on the glass.
 * <p>
 * The acid is the reagent that is used up: one millibucket of sulfuric or chamber acid from a carboy in the other hand gives eight tests. A test is a right-click in the air, which tests the air at the eye, or a right-click with a sample in the other hand, which tests the sample.
 */
public final class MarshApparatusItem extends Item
{
    /** Tests that one millibucket of acid makes. */
    public static final int TESTS_PER_CHARGE = 8;
    /** Acid that one charge takes from the carboy, in millibuckets, which are litres here. */
    public static final int ACID_PER_CHARGE = 1;

    public MarshApparatusItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    /** @return tests left in this apparatus; a new one starts with none */
    public static int testsLeft(ItemStack stack)
    {
        return stack.getOrDefault(Chemistry.MARSH_TESTS.get(), 0);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tfcpp.marsh.tests", testsLeft(stack)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        final ItemStack apparatus = player.getItemInHand(hand);
        if (level.isClientSide || !(level instanceof ServerLevel server))
        {
            return InteractionResultHolder.sidedSuccess(apparatus, level.isClientSide);
        }

        final ItemStack other = player.getItemInHand(otherHand(hand));
        if (testsLeft(apparatus) <= 0)
        {
            if (charge(other))
            {
                apparatus.set(Chemistry.MARSH_TESTS.get(), TESTS_PER_CHARGE);
                player.displayClientMessage(Component.translatable("tfcpp.marsh.charged", TESTS_PER_CHARGE), true);
            }
            else
            {
                player.displayClientMessage(Component.translatable("tfcpp.marsh.no_acid"), true);
            }
            return InteractionResultHolder.success(apparatus);
        }

        apparatus.set(Chemistry.MARSH_TESTS.get(), testsLeft(apparatus) - 1);
        final Mirror mirror = sample(server, player, other);
        player.displayClientMessage(Component.translatable(messageKey(mirror)), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1f, 1f);
        return InteractionResultHolder.success(apparatus);
    }

    /**
     * @param other the item in the other hand: a sample if it is one, or the air at the eye when it is empty or a carboy
     * @return the mirror that the test gives
     */
    public static Mirror sample(ServerLevel level, Player player, ItemStack other)
    {
        if (!other.isEmpty() && !(other.getItem() instanceof GlassCarboyItem))
        {
            return sampleItem(other);
        }
        final BlockPos eye = BlockPos.containing(player.getEyePosition());
        final @Nullable float[] cell = GasTracker.get(level).volume(level).cell(eye.getX(), eye.getY(), eye.getZ());
        if (cell == null)
        {
            return Mirror.NONE;
        }
        return PoisonTests.marsh(cell[Gas.ARSENIC_TRIOXIDE.ordinal()], cell[Gas.ANTIMONY_TRIOXIDE.ordinal()]);
    }

    /** The oxides and the ore of the poisons give a dense mirror, and the arsenical copper of a furnace gives a faint one. Anything else gives none. */
    private static Mirror sampleItem(ItemStack sample)
    {
        if (sample.is(Instruments.ARSENIC_TRIOXIDE.get()) || sample.is(Instruments.ANTIMONY_TRIOXIDE.get()) || BuiltInRegistries.ITEM.getKey(sample.getItem()).getPath().contains("tetrahedrite"))
        {
            return Mirror.STRONG;
        }
        return BuiltInRegistries.ITEM.getKey(sample.getItem()).equals(TFCpp.id("metal/ingot/arsenical_copper")) ? Mirror.FAINT : Mirror.NONE;
    }

    /** Draws one millibucket of sulfuric or chamber acid from a carboy.
     *
     * @return {@code true} if the acid was drawn, which is the only way to charge the apparatus */
    private static boolean charge(ItemStack other)
    {
        if (!(other.getItem() instanceof GlassCarboyItem))
        {
            return false;
        }
        final @Nullable IFluidHandlerItem handler = other.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null)
        {
            return false;
        }
        final FluidStack held = handler.getFluidInTank(0);
        final @Nullable Liquid liquid = Chemistry.liquidOf(held.getFluid());
        if (held.getAmount() < ACID_PER_CHARGE || (liquid != Liquid.SULFURIC_ACID && liquid != Liquid.CHAMBER_ACID))
        {
            return false;
        }
        handler.drain(ACID_PER_CHARGE, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private static String messageKey(Mirror mirror)
    {
        return switch (mirror)
        {
            case NONE -> "tfcpp.marsh.none";
            case FAINT -> "tfcpp.marsh.faint";
            case STRONG -> "tfcpp.marsh.strong";
        };
    }

    private static InteractionHand otherHand(InteractionHand hand)
    {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
