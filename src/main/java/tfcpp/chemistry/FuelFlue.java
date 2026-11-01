package tfcpp.chemistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;

/** Puts what a piece of fuel gives off into the air above the fire that burns it. */
public final class FuelFlue
{
    /** An open fire sends all of its sulfur up the flue. */
    public static final float OPEN_FIRE_SHARE = 1f;
    /** The lime in a blast furnace slag takes up the sulfur of the coke, which is why coke is allowed in one at all. Only about a tenth is left to go up the stack. */
    public static final float BLAST_FURNACE_SHARE = 0.1f;

    /**
     * Called by the fires of TFC at the moment they take a piece of fuel to burn.
     *
     * @param share the share of the fuel's sulfur that reaches the air
     */
    public static void burned(BlockEntity fire, ItemStack fuel, float share)
    {
        if (fire.getLevel() instanceof ServerLevel level)
        {
            final float moles = FuelSulfur.sulfurDioxideMoles(BuiltInRegistries.ITEM.getKey(fuel.getItem()).toString()) * share;
            if (moles > 0f)
            {
                final float[] gases = new float[Gas.COUNT];
                gases[Gas.SULFUR_DIOXIDE.ordinal()] = moles;
                GasEmission.release(level, fire.getBlockPos(), gases);
            }
        }
    }

    private FuelFlue() {}
}
