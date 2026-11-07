package tfcpp.chemistry;

import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.common.items.FluidContainerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * A carboy of borosilicate glass, in a wicker basket: the vessel that acids were carried in. It holds 25 L of the chemical liquids of the mod, vinegar and water, and nothing else. TFC's own containers hold what its fluid tags allow, and no acid is among them, while the carboy takes what {@link Chemistry#USABLE_IN_CARBOY} lists and refuses every other liquid, including those of TFC's blocks.
 * <p>
 * The contents are the fluid component of TFC, so that the same code that fills a jug fills this: right-clicking a block that handles fluids pours into it or draws from it through its item fluid handler.
 */
public final class GlassCarboyItem extends FluidContainerItem
{
    /** Litres, which is millibuckets: a carboy of the size that held a vat's worth of acid for the laboratory. */
    public static final int CAPACITY_MILLIBUCKETS = 25;

    public GlassCarboyItem(Properties properties)
    {
        super(properties.stacksTo(1), () -> CAPACITY_MILLIBUCKETS, Chemistry.USABLE_IN_CARBOY, false, () -> false);
    }

    /**
     * A person who is crouching and has something in the hand does not use the block they click at all, unless the item says so. A carboy says so for a glass retort, where crouching with it is what tips the liquid out of the flask: without this that use never reached the retort in the real game.
     */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player)
    {
        return level.getBlockState(pos).getBlock() instanceof GlassRetortBlock;
    }

    /** The bar shows how full it is, in the colour of what it holds. */
    @Override
    public boolean isBarVisible(ItemStack stack)
    {
        return !FluidHelpers.getContainedFluid(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack)
    {
        final FluidStack fluid = FluidHelpers.getContainedFluid(stack);
        return fluid.isEmpty() ? 0 : (int) Mth.clamp((float) fluid.getAmount() / CAPACITY_MILLIBUCKETS * 13, 1, 13);
    }

    @Override
    public int getBarColor(ItemStack stack)
    {
        final Fluid fluid = FluidHelpers.getContainedFluid(stack).getFluid();
        final @Nullable Liquid liquid = Chemistry.liquidOf(fluid);
        return liquid == null ? 0x3F76E4 : liquid.tint() & 0xFFFFFF;
    }
}
