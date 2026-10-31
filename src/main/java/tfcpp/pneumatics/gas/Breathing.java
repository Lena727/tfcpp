package tfcpp.pneumatics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.Nullable;

import tfcpp.core.TFCpp;
import tfcpp.pneumatics.gas.Physiology.BodyBurden;

/**
 * Applies the air at a player's mouth to their body, once per second.
 */
public final class Breathing
{
    public static final int INTERVAL_TICKS = 20;

    public static final ResourceKey<DamageType> ASPHYXIATION = ResourceKey.create(Registries.DAMAGE_TYPE, TFCpp.id("asphyxiation"));
    public static final ResourceKey<DamageType> CARBON_MONOXIDE = ResourceKey.create(Registries.DAMAGE_TYPE, TFCpp.id("carbon_monoxide"));
    public static final ResourceKey<DamageType> TOXIC_GAS = ResourceKey.create(Registries.DAMAGE_TYPE, TFCpp.id("toxic_gas"));

    private static final float SECONDS = INTERVAL_TICKS / 20f;
    /** Antimony compounds cause the same kind of harm as arsenic, at roughly a third of the toxicity. */
    private static final float ANTIMONY_RELATIVE_TOXICITY = 0.3f;
    private static final long SMELL_MESSAGE_INTERVAL_TICKS = 200;
    private static final int EFFECT_TICKS = 60;
    /** Fraction of health per second. About three minutes to escape from a dangerous level, about ten seconds at a fatal one. */
    private static final float SERIOUS_DAMAGE = 0.005f, FATAL_DAMAGE = 0.1f;

    public static void tick(ServerPlayer player)
    {
        final ServerLevel level = player.serverLevel();
        final BlockPos mouth = BlockPos.containing(player.getEyePosition());
        final @Nullable float[] air = GasTracker.get(level).volume(level).cell(mouth.getX(), mouth.getY(), mouth.getZ());

        if (air == null && !player.hasData(GasAttachments.BREATHING))
        {
            return; // Clean air and clean blood is the usual case, and costs nothing
        }

        final BreathingState state = player.getData(GasAttachments.BREATHING);
        final float oxygen = air == null ? GasMath.OXYGEN_IN_AIR : GasMath.oxygenFraction(air);
        final float carbonMonoxide = air == null ? 0 : GasMath.ppm(air[Gas.CARBON_MONOXIDE.ordinal()]);

        state.carboxyhemoglobin = Physiology.stepCarboxyhemoglobin(state.carboxyhemoglobin, carbonMonoxide, oxygen, SECONDS);
        state.arsenicMg = BodyBurden.ARSENIC.step(state.arsenicMg, moles(air, Gas.ARSENIC_TRIOXIDE) + ANTIMONY_RELATIVE_TOXICITY * moles(air, Gas.ANTIMONY_TRIOXIDE), Gas.ARSENIC_TRIOXIDE.molarMass(), SECONDS);
        state.mercuryMg = BodyBurden.MERCURY.step(state.mercuryMg, moles(air, Gas.MERCURY_VAPOR), Gas.MERCURY_VAPOR.molarMass(), SECONDS);
        state.zincOxideMg = BodyBurden.ZINC_OXIDE.step(state.zincOxideMg, moles(air, Gas.ZINC_OXIDE_FUME), Gas.ZINC_OXIDE_FUME.molarMass(), SECONDS);

        if (player.isCreative() || player.isSpectator())
        {
            return;
        }

        applyCarbonMonoxide(player, state.carboxyhemoglobin);
        applyBodyBurdens(player, state);
        applyHypoxia(player, Physiology.hypoxiaSeverity(oxygen));
        if (air != null)
        {
            applyAcuteGases(player, state, air, level.getGameTime());
        }
    }

    private static float moles(@Nullable float[] air, Gas gas)
    {
        return air == null ? 0 : air[gas.ordinal()];
    }

    /** None of these have any smell, and they build up unnoticed until the body starts to fail. */
    private static void applyBodyBurdens(ServerPlayer player, BreathingState state)
    {
        final int arsenic = BodyBurden.ARSENIC.severity(state.arsenicMg);
        if (arsenic >= 1)
        {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, arsenic - 1, false, false, true));
        }
        if (arsenic >= 2)
        {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_TICKS * 2, 0, false, false, true));
        }
        if (arsenic >= 3)
        {
            hurt(player, TOXIC_GAS, SERIOUS_DAMAGE * 4);
        }

        final int mercury = BodyBurden.MERCURY.severity(state.mercuryMg);
        if (mercury >= 1)
        {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_TICKS, mercury - 1, false, false, true)); // Tremor
        }
        if (mercury >= 2)
        {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_TICKS * 2, 0, false, false, true));
        }
        if (mercury >= 3)
        {
            hurt(player, TOXIC_GAS, SERIOUS_DAMAGE * 2);
        }

        final int fever = BodyBurden.ZINC_OXIDE.severity(state.zincOxideMg);
        if (fever >= 1)
        {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, EFFECT_TICKS, fever - 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, 0, false, false, true));
        }
    }

    /** CO has no smell: the first sign is feeling unwell, and by then a player is already in trouble. */
    private static void applyCarbonMonoxide(ServerPlayer player, float carboxyhemoglobin)
    {
        if (carboxyhemoglobin >= Physiology.COHB_SYMPTOMS)
        {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_TICKS, 0, false, false, true));
        }
        if (carboxyhemoglobin >= Physiology.COHB_DISABLED)
        {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_TICKS * 2, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, 2, false, false, true));
        }
        if (carboxyhemoglobin >= Physiology.COHB_FATAL)
        {
            hurt(player, CARBON_MONOXIDE, 0.1f);
        }
    }

    private static void applyHypoxia(ServerPlayer player, int severity)
    {
        if (severity >= 1)
        {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, severity - 1, false, false, true));
        }
        if (severity >= 2)
        {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_TICKS * 2, 0, false, false, true));
        }
        if (severity >= 2)
        {
            hurt(player, ASPHYXIATION, severity == 2 ? SERIOUS_DAMAGE : FATAL_DAMAGE);
        }
    }

    private static void applyAcuteGases(ServerPlayer player, BreathingState state, float[] air, long tick)
    {
        @Nullable Gas smelled = null;
        int worst = 0;
        for (Gas gas : Gas.VALUES)
        {
            final float ppm = GasMath.ppm(air[gas.ordinal()]);
            worst = Math.max(worst, Physiology.acuteSeverity(gas, ppm));
            if (smelled == null && Physiology.isNoticeable(gas, ppm))
            {
                smelled = gas;
            }
        }

        if (smelled != null && tick >= state.nextSmellMessageTick)
        {
            state.nextSmellMessageTick = tick + SMELL_MESSAGE_INTERVAL_TICKS;
            player.displayClientMessage(Component.translatable("tfcpp.gas.smell." + smelled.id()), true);
        }

        if (holdsOpenFlame(player) && Flammability.isFlammable(air))
        {
            GasTracker.get(player.serverLevel()).queueIgnition(BlockPos.containing(player.getEyePosition()));
        }

        if (worst >= 1)
        {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, worst - 1, false, false, true));
        }
        if (worst >= 2)
        {
            hurt(player, TOXIC_GAS, worst == 2 ? SERIOUS_DAMAGE : FATAL_DAMAGE);
        }
    }

    private static boolean holdsOpenFlame(ServerPlayer player)
    {
        return player.getMainHandItem().is(GasIgnition.OPEN_FLAME_ITEMS) || player.getOffhandItem().is(GasIgnition.OPEN_FLAME_ITEMS);
    }

    private static void hurt(ServerPlayer player, ResourceKey<DamageType> type, float fractionOfMaxHealth)
    {
        player.hurt(player.damageSources().source(type), player.getMaxHealth() * fractionOfMaxHealth);
    }

    private Breathing() {}
}
