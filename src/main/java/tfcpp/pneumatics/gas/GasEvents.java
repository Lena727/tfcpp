package tfcpp.pneumatics.gas;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import tfcpp.core.TickProfiler;
import tfcpp.ecology.PollutionCommand;
import tfcpp.ecology.RegionalAir;
import tfcpp.geology.SeamGas;
import tfcpp.mechanics.ShaftTracker;
import tfcpp.pneumatics.instruments.DavyLamp;
import tfcpp.pneumatics.instruments.SentinelCageItem;
import tfcpp.pneumatics.pipes.PipeTracker;

public final class GasEvents
{
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event)
    {
        if (event.getLevel() instanceof ServerLevel level)
        {
            final long start = System.nanoTime();
            GasTracker.get(level).tick(level);
            final long gasDone = System.nanoTime();
            ShaftTracker.get(level).tick(level);
            final long shaftDone = System.nanoTime();
            PipeTracker.get(level).tick(level);
            final long pipeDone = System.nanoTime();
            RegionalAir.get(level).tick(level); // Records its own time, only when it takes a step
            tfcpp.ecology.LevelWaters.get(level).tick(level); // The same, for the rivers and lakes

            TickProfiler.PROFILE.record(TickProfiler.GAS, gasDone - start);
            TickProfiler.PROFILE.record(TickProfiler.SHAFT, shaftDone - gasDone);
            TickProfiler.PROFILE.record(TickProfiler.PIPE, pipeDone - shaftDone);
            if (level.dimension() == Level.OVERWORLD)
            {
                TickProfiler.PROFILE.endTick(); // Once per server tick, and the overworld is the one level that always ticks
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
        {
            if (chunk.hasData(GasAttachments.CHUNK) && chunk.getData(GasAttachments.CHUNK).isActive())
            {
                GasTracker.get(level).activate(chunk.getPos().toLong());
            }
            PipeTracker.get(level).onChunkLoad(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event)
    {
        if (event.getLevel() instanceof ServerLevel level)
        {
            GasTracker.get(level).deactivate(event.getChunk().getPos().toLong());
            if (event.getChunk() instanceof LevelChunk chunk)
            {
                PipeTracker.get(level).onChunkUnload(level, chunk);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        final Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer && player.tickCount % Breathing.INTERVAL_TICKS == 0)
        {
            final long start = System.nanoTime();
            Breathing.tick(serverPlayer);
            TickProfiler.PROFILE.record(TickProfiler.BREATHING, System.nanoTime() - start); // Players tick before their level's Post event, so this lands in the tick that is about to be closed
            DavyLamp.tickHeld(serverPlayer);
        }
    }

    /** Last, so that a block protected by another mod does not let out any gas. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(BlockEvent.BreakEvent event)
    {
        if (event.getLevel() instanceof ServerLevel level)
        {
            SeamGas.onBlockBroken(level, event.getPos(), event.getState(), event.getPlayer());
        }
    }

    /**
     * Stone dusting: a powder of ground limestone or dolomite used on a block is spread over the gallery around it. Run after other mods, and not at all on a block that takes the item itself.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (event.getLevel() instanceof ServerLevel level && StoneDusting.use(level, event.getEntity(), event.getHand(), event.getPos(), event.getFace()))
        {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        if (SentinelCageItem.tryCapture(event.getEntity(), event.getHand(), event.getTarget()))
        {
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        GasCommand.register(event.getDispatcher());
        PollutionCommand.register(event.getDispatcher());
    }

    private GasEvents() {}
}
