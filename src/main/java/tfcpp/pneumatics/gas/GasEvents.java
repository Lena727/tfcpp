package tfcpp.pneumatics.gas;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class GasEvents
{
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event)
    {
        if (event.getLevel() instanceof ServerLevel level)
        {
            GasTracker.get(level).tick(level);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk
            && chunk.hasData(GasAttachments.CHUNK) && !chunk.getData(GasAttachments.CHUNK).isEmpty())
        {
            GasTracker.get(level).activate(chunk.getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event)
    {
        if (event.getLevel() instanceof ServerLevel level)
        {
            GasTracker.get(level).deactivate(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        final Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer && player.tickCount % Breathing.INTERVAL_TICKS == 0)
        {
            Breathing.tick(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        GasCommand.register(event.getDispatcher());
    }

    private GasEvents() {}
}
