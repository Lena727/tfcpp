package tfcpp.chemistry;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.component.glass.GlassWorking;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;

/**
 * How hot borosilicate glass is worked, and on what. TFC has one threshold for a glassworking step, 480 C, for every batch. Borosilicate softens near 820 C and is worked from about 1250 C (its working point, where the viscosity is 10^4 poise, is about 1250 C), and is still glass at 1500 C. A brass blowpipe melts at 930 C, so brass cannot hold the glass at the heat it is worked at, and the ceramic blowpipe is the tool.
 * <p>
 * The step temperature is checked by {@code GlassOperation.hasRequiredTemperature}, which the mixin {@code BorosilicateGlassMixin} routes here. The window in which the batch can be worked is 1250 to 1500 C: TFC's own recipe turns a blowpipe with glass back into an empty one at 1500 C.
 */
public final class BorosilicateGlass
{
    /** The working point of borosilicate glass, in Celsius: the pipe must be hotter than this to work it. */
    public static final float WORKING_TEMPERATURE = 1250f;

    /** Brass melts at about 930 C, which is what TFC's own recipe for a brass blowpipe says. */
    public static final float BRASS_MELTING_POINT = 930f;

    private BorosilicateGlass() {}

    /**
     * Whether a step of glassworking can be done with the pipe as hot as it is.
     *
     * @param original          what TFC says, for the threshold of the step
     * @param stepHasMinimum    {@code true} if the step has a minimum temperature at all: sawing the glass off does not
     * @param ourBatch          {@code true} if the glass on the pipe is borosilicate
     * @param brassPipe         {@code true} if the pipe is brass
     * @param temperatureCelsius temperature of the pipe
     * @return {@code true} if the step may be done
     */
    public static boolean workable(boolean original, boolean stepHasMinimum, boolean ourBatch, boolean brassPipe, float temperatureCelsius)
    {
        if (!ourBatch || !stepHasMinimum)
        {
            return original;
        }
        // A brass pipe is melted before it gets to the working point: it cannot be worked at all, and is not "hot enough" at any temperature it survives
        return !brassPipe && temperatureCelsius > WORKING_TEMPERATURE;
    }

    /** @return {@code true} if this batch of glass is borosilicate */
    public static boolean isOurs(ItemStack batch)
    {
        return batch.is(Chemistry.BOROSILICATE_GLASS_BATCH.get());
    }

    /** @return {@code true} if the glass on this blowpipe is borosilicate */
    public static boolean carriesOurs(ItemStack pipe)
    {
        return isOurs(GlassWorking.get(pipe).batch());
    }

    /** @return {@code true} if this is the brass blowpipe, with or without glass */
    public static boolean isBrass(ItemStack pipe)
    {
        return pipe.is(TFCItems.BLOWPIPE.asItem()) || pipe.is(TFCItems.BLOWPIPE_WITH_GLASS.asItem());
    }

    /**
     * Putting a batch on a pipe by clicking it with the batch: TFC does it for any batch on any pipe, and borosilicate on brass would be a pipe that melts. It is refused before TFC sees it.
     */
    public static void onItemStacked(ItemStackedOnOtherEvent event)
    {
        final ItemStack batch = event.getCarriedItem();
        final ItemStack pipe = event.getStackedOnItem();
        if (event.getClickAction() == ClickAction.SECONDARY && isOurs(batch) && pipe.is(TFCTags.Items.TOOLS_BLOWPIPE) && isBrass(pipe))
        {
            final Player player = event.getPlayer();
            if (!player.level().isClientSide)
            {
                player.displayClientMessage(Component.translatable("tfcpp.glass.brass_pipe"), true);
            }
            event.setCanceled(true);
        }
    }
}
