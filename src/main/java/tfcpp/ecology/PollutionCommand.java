package tfcpp.ecology;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/**
 * An operator's view of the soil memory of a chunk, for testing. Players are given no display of it: they will read the soil with instruments, later.
 */
public final class PollutionCommand
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("tfcpp").requires(source -> source.hasPermission(2))
            .then(Commands.literal("pollution").requires(source -> source.hasPermission(2))
                .executes(context -> show(context.getSource(), BlockPos.containing(context.getSource().getPosition())))
                .then(Commands.literal("world").executes(context -> world(context.getSource())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> show(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"))))));
    }

    private static int show(CommandSourceStack source, BlockPos pos)
    {
        final ServerLevel level = source.getLevel();
        if (!level.isLoaded(pos))
        {
            source.sendFailure(Component.literal("This chunk is not loaded"));
            return 0;
        }
        final boolean touched = Pollution.isTouched(level, pos);
        final SoilCell cell = Pollution.view(level, pos);
        send(source, "Chunk %d, %d: %s".formatted(pos.getX() >> 4, pos.getZ() >> 4, touched ? "soil memory kept" : "untouched, soil as it started"));
        send(source, "pH %s, buffering in the %s range".formatted(f(cell.pH()), cell.range().name().toLowerCase(Locale.ROOT)));
        send(source, "Buffer eq/m2: carbonate left %s, base saturation %s of %s (%s per pH unit), aluminium acid %s".formatted(
            f(cell.carbonatePerM2()), f(cell.baseSaturation()), f(cell.cecPerM2()), f(cell.acidPerPhUnit()), f(cell.aluminiumPerM2())));
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            final double neutralised = cell.totalNeutralised(pollutant), decayed = cell.totalDecayed(pollutant);
            send(source, "%s: %s %s (%s %s/m2), in %s, %s".formatted(pollutant.id(), f(cell.total(pollutant)), pollutant.unit(), f(cell.perM2(pollutant)), pollutant.unit(), f(cell.totalInput(pollutant)),
                pollutant == Pollutant.ACIDITY ? "neutralised " + f(neutralised) : "decayed " + f(decayed)));
        }
        air(source, level, pos);
        return 1;
    }

    private static void air(CommandSourceStack source, ServerLevel level, BlockPos pos)
    {
        final RegionalAir air = RegionalAir.get(level);
        final AirGrid.Cell region = air.cellAt(pos.getX(), pos.getZ());
        send(source, "Regional air cell %d, %d (128 blocks, %s m to a block of the map)".formatted(pos.getX() >> 7, pos.getZ() >> 7, f(air.metresPerBlock(level))));
        if (region == null)
        {
            send(source, "Nothing in it, and nothing has fallen from it");
            return;
        }
        send(source, "Wind %s, %s m/s (towards +x, +z), as of calendar day %s".formatted(f(region.windX()), f(region.windZ()), region.hasWind() ? String.valueOf(region.windDay()) : "never found"));
        for (AirSpecies species : AirSpecies.VALUES)
        {
            send(source, "%s: %s ug/m3 (%s mol)".formatted(species.gas().id(), f(region.microgramsPerCubicMetre(species)), f(region.moles(species))));
        }
        for (Pollutant pollutant : RegionalAir.FALLING)
        {
            send(source, "fallen %s: %s %s/m2 in all".formatted(pollutant.id(), f(region.cumulative(pollutant)), pollutant.unit()));
        }
    }

    private static int world(CommandSourceStack source)
    {
        final RegionalAir air = RegionalAir.get(source.getLevel());
        send(source, "Carbon dioxide and monoxide let into the open air: %s mol (%s t of CO2)".formatted(f(air.carbonMoles()), f(air.carbonMoles() * 44.01e-6)));
        send(source, "Regional air: %d cells hold something, %d cells in all; %d hours were put into the ground in place after calendar jumps".formatted(air.grid().activeCount(), air.grid().cellCount(), air.forcedHours()));
        for (AirSpecies species : AirSpecies.VALUES)
        {
            final AirGrid grid = air.grid();
            send(source, "%s mol: emitted %s (+ returned below) = in air %s + fallen %s (wet %s) + background %s + taken by rooms %s, returned by rooms %s (emitted is real production)".formatted(species.gas().id(), f(grid.emitted(species)), f(grid.inAir(species)), f(grid.deposited(species)),
                f(grid.depositedWet(species)), f(grid.background(species)), f(grid.taken(species)), f(grid.returned(species))));
        }
        return 1;
    }

    private static void send(CommandSourceStack source, String text)
    {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static String f(double value)
    {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private PollutionCommand() {}
}
