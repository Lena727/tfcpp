package tfcpp.ecology;

import net.dries007.tfc.common.blocks.crop.CropBlock;
import net.dries007.tfc.common.blocks.crop.WildCropBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Breathing;

/** The two ends of the metals in food: what a broken crop drops takes them from the soil, and what is eaten gives them to the body. */
public final class FoodEvents
{
    private FoodEvents() {}

    /** A crop that is broken (by hand, a tool, or water that washes it away) drops food grown in this soil. Picking from a crop that stays is the mixin on {@code IPickableCrop}. */
    public static void onDrops(BlockDropsEvent event)
    {
        final Block block = event.getState().getBlock();
        if (!(event.getLevel() instanceof ServerLevel level) || !(block instanceof CropBlock || block instanceof WildCropBlock))
        {
            return;
        }
        for (ItemEntity entity : event.getDrops())
        {
            FoodContamination.harvest(level, event.getPos(), entity.getItem());
        }
    }

    public static void onEaten(LivingEntityUseItemEvent.Finish event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final ItemStack eaten = event.getItem();
        final @Nullable double[] metals = FoodContamination.get(eaten);
        if (metals != null)
        {
            Breathing.onEat(player, metals);
        }
    }
}
