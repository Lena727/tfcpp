package tfcpp.pneumatics.pipes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** What happens to the blocks of a network when they are broken, in one place for all of them. */
public final class PipeEvents
{
    /**
     * A pipe, fitting, vessel or device of a network whose wall has lost more than a twentieth gives nothing back, whatever its loot table says, so that breaking and replacing it does not mend it. Run last, after anything that adds to the drops.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event)
    {
        if (event.getLevel() instanceof ServerLevel level && event.getState().getBlock() instanceof PipeMember && PipeTracker.get(level).dropsNothing(event.getPos()))
        {
            event.getDrops().clear();
            if (event.getBreaker() instanceof ServerPlayer player)
            {
                player.displayClientMessage(Component.translatable("tfcpp.pipe.too_worn"), true);
            }
        }
    }

    private PipeEvents() {}
}
