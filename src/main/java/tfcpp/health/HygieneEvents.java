package tfcpp.health;

import java.util.SplittableRandom;
import net.dries007.tfc.common.player.IPlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import tfcpp.ecology.Pollution;
import tfcpp.ecology.SoilConstants;

/**
 * The clock of the diseases and of waste: once a second for the symptoms of a player who is ill (a check of an empty state for everybody else), once a minute for the waste of players and of livestock. Nothing ticks in the world for this: it is all a function of the calendar, brought up to date at these moments.
 */
public final class HygieneEvents
{
    /** Ticks between checks of the symptoms: a second. */
    private static final int SYMPTOM_TICKS = 20;
    private static final int EFFECT_TICKS = 100;
    /** Per calendar day: the thirst that diarrhoea takes on top of what a person loses. TFC takes 5 points of thirst for each point of hunger (its thirstModifier), so the bar of 100 goes with 20 points of hunger, which is some days of ordinary work; 200 points a day empties a full bar in half a calendar day, so that diarrhoea must be answered with water as a person with it must: it takes 5 to 10 litres a day from the body (WHO, Treatment of diarrhoea, 2005) against the 2 to 3 litres of ordinary intake. A GAME FIGURE for that ratio. */
    public static final double DIARRHOEA_THIRST_PER_DAY = 200.0;
    /** The chance per second, while ill with typhoid, that a nausea spell starts: about one in a minute and a half. */
    private static final double NAUSEA_CHANCE = 1.0 / 90.0;

    private HygieneEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        if (player.tickCount % SYMPTOM_TICKS == 0)
        {
            symptoms(player);
        }
        if (player.tickCount % Sewage.INTERVAL_TICKS == 0)
        {
            excrete(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            final DiseaseState state = player.getData(Hygiene.DISEASES);
            state.lastExcretion = Pollution.now(); // The time that a player was away leaves no waste in the world
            state.lastSymptoms = state.lastExcretion;
        }
    }

    /** The waste that a player made since the last minute mark is not lost at logout. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            excrete(player);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event)
    {
        final var entity = event.getEntity();
        if ((entity.tickCount + entity.getId()) % Sewage.INTERVAL_TICKS != 0) // The cheapest test first: this listener runs for every entity of the world
        {
            return;
        }
        if (!entity.level().isClientSide && entity instanceof net.minecraft.world.entity.LivingEntity living && Sewage.isLivestock(entity))
        {
            Sewage.livestock((ServerLevel) entity.level(), living);
        }
    }

    /** Once a minute the waste of the animals counted in it goes down, by the calendar that has passed. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event)
    {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % Sewage.INTERVAL_TICKS == 0)
        {
            Sewage.flushLivestock(level, Pollution.now());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        HygieneCommand.register(event.getDispatcher());
    }

    /** The waste of a player since the last count, at the place where they are. */
    public static void excrete(ServerPlayer player)
    {
        final DiseaseState state = player.getData(Hygiene.DISEASES);
        final long now = Pollution.now();
        final long last = state.lastExcretion;
        state.lastExcretion = Math.max(last, now); // A calendar that was turned back must not count the same stretch twice
        if (last == 0L || now <= last || !player.isAlive() || player.isCreative() || player.isSpectator())
        {
            return;
        }
        final double perDay = state.phase(Disease.DYSENTERY, now) == DiseaseState.Phase.ILL ? SewageRates.DYSENTERY_LITRES_PER_DAY : SewageRates.PERSON_LITRES_PER_DAY;
        final double litres = SewageRates.litres(perDay, Math.min(now - last, SewageRates.MAX_CATCH_UP_TICKS));
        Sewage.deposit(player.serverLevel(), player.blockPosition(), litres, state.sheds(Disease.DYSENTERY, now) ? litres : 0.0, state.sheds(Disease.TYPHOID, now) ? litres : 0.0, true, player.isInWater());
    }

    /** Brings the diseases of a player up to the calendar and applies what they feel. */
    public static void symptoms(ServerPlayer player)
    {
        final DiseaseState state = player.getData(Hygiene.DISEASES);
        if (state.isEmpty() || player.isCreative() || player.isSpectator())
        {
            return;
        }
        final long now = Pollution.now();
        final SplittableRandom random = new SplittableRandom(player.getRandom().nextLong());
        final long yearTicks = Math.round(SoilConstants.daysPerYear() * DiseaseState.dayTicks());
        final DiseaseState.Outcome outcome = state.step(now, IPlayerInfo.get(player).nutrition().getAverageNutrition(), random, yearTicks);
        if (outcome == DiseaseState.Outcome.DIED)
        {
            if (die(player) && !player.isDeadOrDying())
            {
                state.recover(Disease.TYPHOID, yearTicks); // The blow landed and the person lived (a totem): a recovery, with its immunity and the shedding of the tail
            }
            else
            {
                return; // Dead, or the blow was turned aside for now (the invulnerability of a player who has just come in): the course is still open and is tried again at the next step
            }
        }

        final DiseaseState.Phase dysentery = state.phase(Disease.DYSENTERY, now), typhoid = state.phase(Disease.TYPHOID, now);
        if (dysentery == DiseaseState.Phase.ILL)
        {
            feel(player, Hygiene.DIARRHOEA_EFFECT);
            feel(player, Hygiene.ILLNESS_WEAKNESS_EFFECT);
            final long since = state.lastSymptoms == 0L ? 0L : Math.min(now - state.lastSymptoms, 2400L); // Never set: no time has passed that we know of
            IPlayerInfo.get(player).addThirst((float) -(DIARRHOEA_THIRST_PER_DAY * Math.max(0L, since) / DiseaseState.dayTicks()));
        }
        if (typhoid == DiseaseState.Phase.ILL)
        {
            feel(player, Hygiene.FEVER_EFFECT);
            feel(player, Hygiene.ILLNESS_WEAKNESS_EFFECT);
            if (random.nextDouble() < NAUSEA_CHANCE)
            {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false, true));
            }
        }
        state.lastSymptoms = now;
    }

    /** Gives the effect only when it has run short: not a new instance every second for every ill player. */
    private static void feel(ServerPlayer player, net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.effect.MobEffect, net.minecraft.world.effect.MobEffect> effect)
    {
        final @org.jetbrains.annotations.Nullable MobEffectInstance held = player.getEffect(effect);
        if (held == null || held.getDuration() <= EFFECT_TICKS / 2)
        {
            player.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, 0, false, true, true));
        }
    }

    /** The end of a fever: the player dies, and the message says of what. @return whether the blow landed */
    private static boolean die(ServerPlayer player)
    {
        final Holder<DamageType> type = player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(Hygiene.FEVER);
        return player.hurt(new DamageSource(type), Float.MAX_VALUE);
    }
}
