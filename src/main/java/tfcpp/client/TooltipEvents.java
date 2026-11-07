package tfcpp.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import tfcpp.core.TFCpp;
import tfcpp.metallurgy.Metallurgy;

/** What a player is told about an item that its look does not show. */
@EventBusSubscriber(modid = TFCpp.MOD_ID, value = Dist.CLIENT)
public final class TooltipEvents
{
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        if (event.getItemStack().has(Metallurgy.ARSENICAL.get()))
        {
            event.getToolTip().add(Component.translatable("tfcpp.tooltip.arsenical").withStyle(ChatFormatting.GOLD));
        }
    }

    private TooltipEvents() {}
}
