package tfcpp.pneumatics.instruments;

import java.util.Locale;
import net.dries007.tfc.common.capabilities.ItemCapabilities;
import net.dries007.tfc.util.data.LampFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.BreathingState;
import tfcpp.pneumatics.gas.Flammability;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;

/**
 * Humphry Davy's safety lamp of 1815. A copper gauze takes the heat out of a flame passing through it faster than the flame can reignite the gas outside, so burning can happen inside the lamp without lighting the mine.
 * <p>
 * The flame is also an instrument. Firedamp entering the lamp burns as a pale blue cap above the flame, which grows with the methane present: a trained eye can read from about 1%. From the lower limit the flame fills the whole gauze. A flame needs more oxygen than a person, so blackdamp puts it out first, at about 16%.
 * <p>
 * It does not show CO, as CO only begins to cap a flame far above the lethal level. It also cannot protect forever: a gauze filled with burning gas heats up, and glowing metal does light the gas outside.
 */
public final class DavyLamp
{
    public enum Reading
    {
        STEADY,
        DIM,
        PALE_CAP,
        BLUE_CAP,
        TALL_CAP,
        FILLED,
        OUT;

        public String translationKey()
        {
            return "tfcpp.davy_lamp." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static final float OUT_OXYGEN = 0.16f, DIM_OXYGEN = 0.18f;
    private static final float GAUZE_FAILS_SECONDS = 120f;
    private static final int READING_INTERVAL_SECONDS = 2;

    /** @return what the flame looks like in air of this composition, or in clean air if {@code null} */
    public static Reading read(@Nullable float[] air)
    {
        if (air == null)
        {
            return Reading.STEADY;
        }
        final float oxygen = GasMath.oxygenFraction(air);
        final float methaneEquivalent = Flammability.relativeToLowerLimit(air) * 0.05f;
        if (oxygen < OUT_OXYGEN || methaneEquivalent > 0.15f)
        {
            return Reading.OUT; // Too little oxygen, or far too much gas for a flame to burn in
        }
        if (methaneEquivalent >= 0.05f) return Reading.FILLED;
        if (methaneEquivalent >= 0.03f) return Reading.TALL_CAP;
        if (methaneEquivalent >= 0.02f) return Reading.BLUE_CAP;
        if (methaneEquivalent >= 0.01f) return Reading.PALE_CAP;
        return oxygen < DIM_OXYGEN ? Reading.DIM : Reading.STEADY;
    }

    /** @return whether a flame can burn in air that reads like this. One that cannot goes out, and stays out until it is lit again. */
    public static boolean burns(Reading reading)
    {
        return reading != Reading.OUT;
    }

    /** @return whether a flame struck where this player stands would burn, so that lighting the lamp there is worth a try */
    public static boolean canLightIn(Player player)
    {
        if (!(player.level() instanceof ServerLevel level))
        {
            return true;
        }
        final BlockPos eye = BlockPos.containing(player.getEyePosition());
        return burns(read(GasTracker.get(level).volume(level).cell(eye.getX(), eye.getY(), eye.getZ())));
    }

    /** @return the gauze temperature after this long in this reading. It heats while gas burns inside it, and cools at the same rate otherwise. */
    public static float stepGauze(float seconds, Reading reading, float step)
    {
        return reading == Reading.FILLED ? seconds + step : Math.max(0, seconds - step);
    }

    public static boolean gauzeFailed(float seconds)
    {
        return seconds >= GAUZE_FAILS_SECONDS;
    }

    /**
     * A lamp in the hand burns its oil while it is lit, and shows the air around the one carrying it. It is lit only if it has been lit: a lamp whose oil has run out, or whose flame the air has put out, is out, and stays so until it is lit again. Called once per second.
     */
    public static void tickHeld(ServerPlayer player)
    {
        final InteractionHand hand = heldIn(player);
        final ItemStack lamp = hand == null ? ItemStack.EMPTY : player.getItemInHand(hand);
        final @Nullable LampFuel definition = lamp.isEmpty() ? null : DavyLampItem.fuelOf(lamp);
        if (definition == null || !DavyLampItem.isLit(lamp))
        {
            if (!lamp.isEmpty() && DavyLampItem.isLit(lamp))
            {
                DavyLampItem.setLit(lamp, false); // No oil, no flame
                player.displayClientMessage(Component.translatable("tfcpp.davy_lamp.no_oil_out"), true);
            }
            if (player.hasData(GasAttachments.BREATHING))
            {
                final BreathingState state = player.getData(GasAttachments.BREATHING);
                state.gauzeSeconds = stepGauze(state.gauzeSeconds, Reading.STEADY, 1f); // Put away or out, it cools
            }
            return;
        }

        final ServerLevel level = player.serverLevel();
        final BlockPos eye = BlockPos.containing(player.getEyePosition());
        final @Nullable float[] air = GasTracker.get(level).volume(level).cell(eye.getX(), eye.getY(), eye.getZ());
        final Reading reading = read(air);

        final BreathingState state = player.getData(GasAttachments.BREATHING);
        if (!burns(reading))
        {
            DavyLampItem.setLit(lamp, false);
            state.gauzeSeconds = stepGauze(state.gauzeSeconds, Reading.STEADY, 1f);
            level.playSound(null, eye, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1f);
            player.displayClientMessage(Component.translatable("tfcpp.davy_lamp.went_out"), true);
            return;
        }
        burnFuel(lamp, definition, state, reading);

        state.gauzeSeconds = stepGauze(state.gauzeSeconds, reading, 1f);
        if (gauzeFailed(state.gauzeSeconds) && air != null && Flammability.isFlammable(air))
        {
            state.gauzeSeconds = 0;
            GasTracker.get(level).queueIgnition(eye);
        }

        if (player.tickCount % (READING_INTERVAL_SECONDS * 20) == 0)
        {
            player.displayClientMessage(Component.translatable(reading.translationKey()), true);
        }
    }

    private static void burnFuel(ItemStack lamp, LampFuel definition, BreathingState state, Reading reading)
    {
        if (definition.burnRate() <= 0)
        {
            return;
        }
        state.lampBurnTicks += 20;
        if (state.lampBurnTicks >= definition.burnRate())
        {
            state.lampBurnTicks = 0;
            final @Nullable IFluidHandlerItem handler = lamp.getCapability(ItemCapabilities.FLUID);
            if (handler != null)
            {
                handler.drain(1, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    @Nullable
    private static InteractionHand heldIn(ServerPlayer player)
    {
        if (player.getMainHandItem().is(Instruments.DAVY_LAMP_ITEM.get())) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(Instruments.DAVY_LAMP_ITEM.get())) return InteractionHand.OFF_HAND;
        return null;
    }

    private DavyLamp() {}
}
