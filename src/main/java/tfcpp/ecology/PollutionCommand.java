package tfcpp.ecology;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

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
                (pollutant == Pollutant.ACIDITY ? "neutralised " + f(neutralised) : "decayed " + f(decayed)) + ", ran off " + f(cell.totalWashed(pollutant))));
        }
        air(source, level, pos);
        forestAndCrops(source, level, pos, cell);
        water(source, level, pos);
        tfcpp.health.HygieneCommand.report(source, level, pos);
        return 1;
    }

    private static void forestAndCrops(CommandSourceStack source, ServerLevel level, BlockPos pos, SoilCell cell)
    {
        final RegionalAir air = RegionalAir.get(level);
        send(source, "Forest health: broadleaf %s, conifer %s; annual mean SO2 %s ug/m3 (present %s ug/m3); harvested from the soil: lead %s g, arsenic %s g".formatted(
            f(cell.forestHealth(false)), f(cell.forestHealth(true)), f(air.annualSo2(pos.getX(), pos.getZ())), f(air.so2Now(pos.getX(), pos.getZ())), f(cell.totalHarvested(Pollutant.LEAD)), f(cell.totalHarvested(Pollutant.ARSENIC))));
        final CropEffects.Result crop = CropSoil.of(level, pos, cell);
        send(source, "Crop growth multiplier here: %s%s".formatted(f(crop.growth()), crop.unhealthy() ? " (a step here is unhealthy: the crop dies)" : ""));
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

    /** The water at the position if there is water there, else the water that the chunk drains to, if it drains to any. */
    private static void water(CommandSourceStack source, ServerLevel level, BlockPos pos)
    {
        final LevelWaters waters = LevelWaters.get(level);
        @Nullable WaterBody body = Waters.at(level, pos);
        String of = "the water at this position";
        if (body == null)
        {
            final Drainage drainage = waters.classify(level, level.getChunkAt(pos));
            of = "the chunk drains " + drainage.name().toLowerCase(Locale.ROOT);
            body = drainage.drains() && drainage != Drainage.SEA ? waters.bodyFor(level, drainage == Drainage.LAKE ? WaterRoute.RUNOFF_LAKE : WaterRoute.RUNOFF_RIVER, pos.getX() >> 4, pos.getZ() >> 4) : null;
        }
        if (body == null)
        {
            send(source, "Water: " + of + ", nothing to report");
            return;
        }
        if (!body.resolved())
        {
            send(source, "Water: %s, %s: the river graph has not answered for this place yet".formatted(of, body.type().name().toLowerCase(Locale.ROOT)));
            return;
        }
        if (body.type() == WaterBody.Type.SEA)
        {
            send(source, "Water: " + of + " is the sea, which takes everything and is background");
            return;
        }
        final @Nullable ReachInfo info = body.info();
        if (info == null)
        {
            send(source, "Water: %s, %s %s holds nothing (its geometry is kept only while it holds something)".formatted(of, body.type().name().toLowerCase(Locale.ROOT), Long.toHexString(body.id())));
            return;
        }
        send(source, "Water: %s, %s %s, Q %s m3/s (game volume over the compressed residence), residence %s days, volume %s m3, surface %s m2, alkalinity %s meq/L".formatted(of, body.type().name().toLowerCase(Locale.ROOT), Long.toHexString(body.id()),
            f(info.discharge()), info.still() ? "infinite (no outflow)" : f(info.residenceSeconds() / 86_400.0), f(info.volume()), f(info.surfaceArea()), f(info.alkalinity())));
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            send(source, "  %s: %s in the water, %s on the bed (%s %s)".formatted(pollutant.id(), concentration(pollutant, body.perLitre(pollutant)), f(body.onBed()[pollutant.ordinal()]), f(body.inWater()[pollutant.ordinal()]), pollutant.unit()));
        }
        send(source, "  pH of the free acid: %s".formatted(f(body.pH())));
    }

    /** @return a concentration in the unit that fits its size: micrograms per litre for a metal, microequivalents for acid, milligrams for tar, millilitres of sewage per litre */
    static String concentration(Pollutant pollutant, double perLitre)
    {
        return switch (pollutant)
        {
            case ACIDITY -> f(perLitre * 1e6) + " ueq/L";
            case TAR -> f(perLitre * 1e6) + " mg/L";
            case SEWAGE, SHED_DYSENTERY, SHED_TYPHOID -> f(perLitre * 1e3) + " mL/L";
            default -> f(perLitre * 1e6) + " ug/L";
        };
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
        final LevelWaters waters = LevelWaters.get(source.getLevel());
        waters.collect(source.getLevel());
        final WaterLedger ledger = waters.ledger();
        send(source, "Waters: %d reaches hold something, %d are known, %d watched for runoff, %d places have inputs waiting for the river graph, %d hours folded into coarse steps".formatted(
            ledger.activeCount(), ledger.infos().size(), waters.watchedCount(), ledger.pendingCount(), waters.foldedHours()));
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            send(source, "%s: put in %s + from the air %s (counted apart: the air ledger has it as fallen) = in water %s + on beds %s + broken down %s + neutralised %s + to the sea %s + dropped %s + waiting %s (%s)".formatted(pollutant.id(), f(ledger.input(pollutant)), f(ledger.fromAir(pollutant)), f(ledger.inWater(pollutant)),
                f(ledger.onBed(pollutant)), f(ledger.decayed(pollutant)), f(ledger.neutralised(pollutant)), f(ledger.toSea(pollutant)), f(ledger.dropped(pollutant)), f(ledger.pendingTotal(pollutant)), pollutant.unit()));
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
