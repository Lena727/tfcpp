package tfcpp.pneumatics.instruments;

import java.util.List;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.component.food.FoodCapability;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.Physiology;

/**
 * A small animal carried as a detector for CO, as John Scott Haldane proposed after studying mine disasters in the 1890s. Its breathing and circulation turn over about twenty times faster for its size than a person's, so it reaches the same level of CO in the blood twenty times sooner.
 * <p>
 * It has to be fed, and it recovers in clean air. A dead one leaves an empty cage and a warning.
 */
public final class SentinelCageItem extends Item
{
    /** Ratio of the blood turnover of a small animal to that of a person. */
    public static final float METABOLIC_RATIO = 20f;
    private static final float UNEASY = 10f;
    private static final long FED_DAYS = 3;
    private static final long TICKS_PER_DAY = 24 * 60 * 20;

    /** @return the level of CO in the animal's blood after a second of breathing this air */
    public static float stepCarboxyhemoglobin(float carboxyhemoglobin, float carbonMonoxidePpm, float oxygenFraction)
    {
        return Physiology.stepCarboxyhemoglobin(carboxyhemoglobin, carbonMonoxidePpm, oxygenFraction, METABOLIC_RATIO);
    }

    public SentinelCageItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    public static ItemStack withAnimal()
    {
        final ItemStack stack = new ItemStack(Instruments.SENTINEL_CAGE.get());
        stack.set(Instruments.SENTINEL.get(), new SentinelData(0f, Calendars.SERVER.getTicks() + FED_DAYS * TICKS_PER_DAY));
        return stack;
    }

    /** An empty cage takes a small animal that is right-clicked. */
    public static boolean tryCapture(Player player, InteractionHand hand, Entity target)
    {
        final ItemStack held = player.getItemInHand(hand);
        if (!held.is(Instruments.ANIMAL_CAGE.get()) || !target.getType().is(Instruments.SENTINEL_ANIMALS))
        {
            return false;
        }
        if (!player.level().isClientSide)
        {
            target.discard();
            held.shrink(1);
            final ItemStack caged = withAnimal();
            if (held.isEmpty())
            {
                player.setItemInHand(hand, caged);
            }
            else if (!player.addItem(caged))
            {
                player.drop(caged, false);
            }
        }
        return true;
    }

    /** Using the cage feeds the animal from the other hand. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        final ItemStack cage = player.getItemInHand(hand);
        final ItemStack food = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        final @Nullable SentinelData data = cage.get(Instruments.SENTINEL.get());
        if (data == null || !food.is(TFCTags.Items.CHICKEN_FOOD) || FoodCapability.isRotten(food))
        {
            return InteractionResultHolder.pass(cage);
        }
        if (!level.isClientSide)
        {
            food.shrink(1);
            cage.set(Instruments.SENTINEL.get(), new SentinelData(data.carboxyhemoglobin(), Calendars.SERVER.getTicks() + FED_DAYS * TICKS_PER_DAY));
        }
        return InteractionResultHolder.sidedSuccess(cage, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected)
    {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player && player.tickCount % 20 == 0)
        {
            final @Nullable SentinelData data = stack.get(Instruments.SENTINEL.get());
            if (data != null)
            {
                breathe(stack, data, serverLevel, player);
            }
        }
    }

    private static void breathe(ItemStack stack, SentinelData data, ServerLevel level, ServerPlayer player)
    {
        final BlockPos eye = BlockPos.containing(player.getEyePosition());
        final @Nullable float[] air = GasTracker.get(level).volume(level).cell(eye.getX(), eye.getY(), eye.getZ());

        final float oxygen = air == null ? GasMath.OXYGEN_IN_AIR : GasMath.oxygenFraction(air);
        final float carbonMonoxide = air == null ? 0 : GasMath.ppm(air[Gas.CARBON_MONOXIDE.ordinal()]);
        final float next = stepCarboxyhemoglobin(data.carboxyhemoglobin(), carbonMonoxide, oxygen);

        if (next >= Physiology.COHB_FATAL || Physiology.hypoxiaSeverity(oxygen) >= 2 || breathesPoison(air) || Calendars.SERVER.getTicks() > data.fedUntil())
        {
            final boolean starved = Calendars.SERVER.getTicks() > data.fedUntil();
            // The slot passed to an inventory tick is only an index within one part of the inventory, so it cannot be used to put anything back
            stack.shrink(1);
            final ItemStack empty = new ItemStack(Instruments.ANIMAL_CAGE.get());
            if (!player.addItem(empty))
            {
                player.drop(empty, false);
            }
            player.displayClientMessage(Component.translatable(starved ? "tfcpp.sentinel.starved" : "tfcpp.sentinel.dead"), true);
            player.playNotifySound(SoundEvents.CHICKEN_DEATH, SoundSource.PLAYERS, 1f, 1.4f);
            return;
        }

        if (next >= Physiology.COHB_SYMPTOMS && data.carboxyhemoglobin() < Physiology.COHB_SYMPTOMS)
        {
            player.displayClientMessage(Component.translatable("tfcpp.sentinel.collapsed"), true);
            player.playNotifySound(SoundEvents.CHICKEN_HURT, SoundSource.PLAYERS, 1f, 1.4f);
        }
        else if (next >= UNEASY && data.carboxyhemoglobin() < UNEASY)
        {
            player.displayClientMessage(Component.translatable("tfcpp.sentinel.uneasy"), true);
        }
        stack.set(Instruments.SENTINEL.get(), new SentinelData(next, data.fedUntil()));
    }

    private static boolean breathesPoison(@Nullable float[] air)
    {
        if (air != null)
        {
            for (Gas gas : Gas.VALUES)
            {
                if (Physiology.acuteSeverity(gas, GasMath.ppm(air[gas.ordinal()])) >= 2)
                {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        final @Nullable SentinelData data = stack.get(Instruments.SENTINEL.get());
        if (data != null)
        {
            final String state = data.carboxyhemoglobin() >= Physiology.COHB_SYMPTOMS ? "collapsed" : data.carboxyhemoglobin() >= UNEASY ? "uneasy" : "lively";
            tooltip.add(Component.translatable("tfcpp.sentinel.state." + state));
        }
    }
}
