package tfcpp.pneumatics.gas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.dries007.tfc.common.blockentities.rotation.RotatingBlockEntity;
import net.dries007.tfc.util.rotation.Node;
import net.dries007.tfc.util.rotation.Rotation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import tfcpp.core.TickProfiler;
import tfcpp.geology.OreSurvey;
import tfcpp.mechanics.ShaftPhysics;
import tfcpp.mechanics.ShaftTracker;

/**
 * Operator tools for testing and inspecting the gas grid. Players are meant to read the air with instruments.
 */
public final class GasCommand
{
    static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("tfcpp").requires(source -> source.hasPermission(2))
            .then(Commands.literal("gas")
                .then(Commands.literal("read")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> read(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("add")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("gas", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Gas.VALUES).map(Gas::id), builder))
                            .then(Commands.argument("moles", FloatArgumentType.floatArg(0))
                                .executes(context -> add(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"), StringArgumentType.getString(context, "gas"), FloatArgumentType.getFloat(context, "moles")))))))
                .then(Commands.literal("stats")
                    .executes(context -> stats(context.getSource()))))
            .then(Commands.literal("perf")
                .executes(context -> perf(context.getSource())))
            .then(Commands.literal("survey")
                // Worldgen takes about 80 ms a chunk on the server thread, and the watchdog stops the server after a 60 s tick: 289 chunks is about 25 s
                .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 8))
                    .executes(context -> survey(context.getSource(), IntegerArgumentType.getInteger(context, "chunks")))))
            .then(Commands.literal("shaft")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> shaft(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"))))));
    }

    private static int perf(CommandSourceStack source)
    {
        TickProfiler.perf().forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    private static int survey(CommandSourceStack source, int chunks)
    {
        OreSurvey.run(source.getLevel(), BlockPos.containing(source.getPosition()), chunks).forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    private static int shaft(CommandSourceStack source, BlockPos pos)
    {
        if (!(source.getLevel().getBlockEntity(pos) instanceof RotatingBlockEntity rotating))
        {
            source.sendFailure(Component.literal("No shaft at " + pos.toShortString()));
            return 0;
        }
        final Node node = rotating.getRotationNode();
        final @Nullable Rotation rotation = node.rotation();
        final float speed = rotation == null ? 0 : Math.abs(rotation.speed()) * ShaftPhysics.TICKS_PER_SECOND;
        final float torque = ShaftTracker.get(source.getLevel()).networkTorque(node.network());
        source.sendSuccess(() -> Component.literal("Network %d: %.2f rad/s (%.1f rpm), load %.2f kN m, %.2f kW".formatted(node.network(), speed, speed * 60f / (2f * Math.PI), torque, torque * speed)), false);
        return 1;
    }

    private static int read(CommandSourceStack source, BlockPos pos)
    {
        final ServerLevel level = source.getLevel();
        final @Nullable float[] cell = GasTracker.get(level).volume(level).cell(pos.getX(), pos.getY(), pos.getZ());

        final float oxygen = cell == null ? GasMath.OXYGEN_IN_AIR : GasMath.oxygenFraction(cell);
        final StringBuilder text = new StringBuilder("O2 %.2f%%".formatted(oxygen * 100));
        if (cell != null)
        {
            for (Gas gas : Gas.VALUES)
            {
                if (cell[gas.ordinal()] > 0)
                {
                    text.append(", %s %.0f ppm".formatted(gas.id(), GasMath.ppm(cell[gas.ordinal()])));
                }
            }
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int add(CommandSourceStack source, BlockPos pos, String id, float moles)
    {
        final @Nullable Gas gas = Gas.byId(id);
        if (gas == null)
        {
            source.sendFailure(Component.literal("Unknown gas: " + id));
            return 0;
        }

        final ServerLevel level = source.getLevel();
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final @Nullable float[] cell = volume.cellForEmission(pos.getX(), pos.getY(), pos.getZ());
        if (cell == null)
        {
            source.sendFailure(Component.literal("This chunk is not loaded"));
            return 0;
        }
        cell[gas.ordinal()] += moles;
        return 1;
    }

    private static int stats(CommandSourceStack source)
    {
        final GasTracker tracker = GasTracker.get(source.getLevel());
        source.sendSuccess(() -> Component.literal("Chunks with gas: %d, fires tracked: %d".formatted(tracker.activeChunkCount(), tracker.fireCount())), false);
        return 1;
    }

    private GasCommand() {}
}
