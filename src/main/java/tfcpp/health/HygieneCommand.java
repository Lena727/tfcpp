package tfcpp.health;

import java.util.Locale;
import java.util.SplittableRandom;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import tfcpp.ecology.FoodMetals;
import tfcpp.ecology.Pollution;

/**
 * The operator's view of the diseases and of the water: players are shown none of this (truth inside, instruments outside), so this is for tests and for admins.
 * <ul>
 * <li>{@code /tfcpp disease <player>}: the state of a player's diseases.</li>
 * <li>{@code /tfcpp disease infect <player> <dysentery|typhoid>}: starts an infection.</li>
 * </ul>
 * The quality of the water at a place and the cesspits near it are part of {@code /tfcpp pollution}, through {@link #report}.
 */
public final class HygieneCommand
{
    private HygieneCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("tfcpp").requires(source -> source.hasPermission(2))
            .then(Commands.literal("disease").requires(source -> source.hasPermission(2))
                .then(Commands.literal("infect")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("disease", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[] {"dysentery", "typhoid"}, builder))
                            .executes(context -> infect(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "disease"))))))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> show(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
    }

    private static int infect(CommandSourceStack source, ServerPlayer player, String name)
    {
        final @Nullable Disease disease = byName(name);
        if (disease == null)
        {
            source.sendFailure(Component.literal("Unknown disease " + name + ": dysentery or typhoid"));
            return 0;
        }
        final boolean infected = player.getData(Hygiene.DISEASES).infect(disease, Pollution.now(), new SplittableRandom(player.getRandom().nextLong()));
        send(source, infected ? player.getGameProfile().getName() + " is infected with " + disease.id() : player.getGameProfile().getName() + " cannot be infected with " + disease.id() + " now (already ill with it, or immune)");
        return infected ? 1 : 0;
    }

    @Nullable
    private static Disease byName(String name)
    {
        for (Disease disease : Disease.VALUES)
        {
            if (disease.id().equals(name.toLowerCase(Locale.ROOT)))
            {
                return disease;
            }
        }
        return null;
    }

    private static int show(CommandSourceStack source, ServerPlayer player)
    {
        final DiseaseState state = player.getData(Hygiene.DISEASES);
        final long now = Pollution.now();
        send(source, "Diseases of " + player.getGameProfile().getName() + " (calendar tick " + now + ")");
        for (Disease disease : Disease.VALUES)
        {
            final DiseaseState.Phase phase = state.phase(disease, now);
            final var c = state.course(disease);
            if (c == null)
            {
                send(source, "  %s: none%s".formatted(disease.id(), state.immuneUntil(disease) > now ? ", immune for another %s days".formatted(day(state.immuneUntil(disease) - now)) : ""));
            }
            else
            {
                send(source, "  %s: %s; symptoms in %s days, illness ends in %s days, sheds %s (until %s days from now)".formatted(disease.id(), phase.name().toLowerCase(Locale.ROOT), day(c.symptomsAt - now), day(c.illnessEnds - now), c.sheds(now), day(c.sheddingEnds - now)));
            }
        }
        return 1;
    }

    private static String day(long ticks)
    {
        return String.format(Locale.ROOT, "%.2f", ticks / (double) DiseaseState.dayTicks());
    }

    /** The water quality at the position (if there is fresh water) and the cesspits near it, for {@code /tfcpp pollution}. */
    public static void report(CommandSourceStack source, ServerLevel level, BlockPos pos)
    {
        final WaterQuality quality = WaterSource.at(level, pos);
        if (level.getFluidState(pos).isEmpty())
        {
            send(source, "Drinking water quality: no water at this position");
        }
        else
        {
            send(source, "Drinking water quality: sewage %s mL/L (shigella shedders %s mL/L, typhoid shedders %s mL/L), acid %s meq/L".formatted(f(quality.faecal * 1e3), f(quality.shedDysentery * 1e3), f(quality.shedTyphoid * 1e3), f(quality.acidity)));
            final StringBuilder metals = new StringBuilder();
            for (int i = 0; i < FoodMetals.METALS.length; i++)
            {
                metals.append(FoodMetals.METALS[i].id()).append(' ').append(f(quality.metal(i))).append(' ');
            }
            send(source, "  metals ug/L: " + metals.toString().trim());
            send(source, "  one drink (0.5 L): chance of dysentery %s, of typhoid %s".formatted(f(Organisms.infectionProbability(Disease.DYSENTERY, quality, 0.5)), f(Organisms.infectionProbability(Disease.TYPHOID, quality, 0.5))));
        }
        final int[] count = {0};
        Cesspits.around(level, pos, CesspitMath.GROUNDWATER_REACH, (at, pit) ->
        {
            count[0]++;
            final CesspitMath.State now = pit.catchUp(level);
            send(source, "  cesspit at %s: %s L (infective %s L, shedders %s and %s L), overflowed %s L".formatted(at.toShortString(), f(now.volume()), f(now.active()), f(now.shedDysentery()), f(now.shedTyphoid()), f(pit.overflowed())));
        });
        if (count[0] == 0)
        {
            send(source, "  no cesspits within " + CesspitMath.GROUNDWATER_REACH + " blocks");
        }
    }

    private static void send(CommandSourceStack source, String text)
    {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static String f(double value)
    {
        return String.format(Locale.ROOT, "%.4g", value);
    }
}
