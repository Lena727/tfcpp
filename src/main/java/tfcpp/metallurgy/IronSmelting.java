package tfcpp.metallurgy;

import java.util.List;
import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.recipes.BloomeryRecipe;
import net.dries007.tfc.util.Helpers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;

/**
 * What TFC's iron furnaces leave behind, which TFC itself does not track: the slag, and the gas that takes the oxygen away from the ore.
 * <p>
 * A bloomery never gets hot enough to melt iron, so its slag has to carry the sand of the ore away by itself, and it can only do that as an iron silicate, 2FeO.SiO2. That is why a third or more of the iron in the ore ends up in the slag. Blast furnace slag is a calcium silicate instead, as limestone takes the iron's place, and so it loses almost no iron.
 */
public final class IronSmelting
{
    /** Iron carried by a piece of bloomery slag. It is rich enough to smelt again, which is exactly what early blast furnaces did with old bloomery heaps. */
    public static final int MILLIBUCKETS_PER_SLAG = 25;
    /** What a bloom holds, as the rest of TFC expects when it is forged or melted. */
    private static final int BLOOM_MILLIBUCKETS = 100;
    private static final float MOLES_OF_IRON_PER_MILLIBUCKET = 7.87f / 55.845f;
    /** Fe2O3 + 3 CO -> 2 Fe + 3 CO2: one and a half moles of CO2 for every mole of iron. */
    private static final float CARBON_DIOXIDE_PER_IRON = 1.5f;
    /** A flux powder of 0.75 kg of limestone: CaCO3 -> CaO + CO2. */
    public static final float CARBON_DIOXIDE_PER_FLUX = 7.5f;
    /** A dolomite powder of 0.75 kg: CaMg(CO3)2 -> CaO + MgO + 2 CO2, 184 g/mol. */
    public static final float CARBON_DIOXIDE_PER_DOLOMITE = 8.1f;

    /** @return pieces of slag left by a bloomery charge holding this much iron, for what did not go into blooms */
    public static int bloomerySlag(int chargeMillibuckets, int bloomMillibuckets)
    {
        return Math.max(chargeMillibuckets - bloomMillibuckets, 0) / MILLIBUCKETS_PER_SLAG;
    }

    /** @return moles of CO2 for taking the oxygen off this much iron */
    public static float reductionGas(float millibuckets)
    {
        return millibuckets * MOLES_OF_IRON_PER_MILLIBUCKET * CARBON_DIOXIDE_PER_IRON;
    }

    /**
     * The slag of a bloomery that smelted any brown ore. The phosphorus of the ore does not go into a bloom: a real tap cinder is very phosphoric, so the slag must not wash it out for free.
     */
    public static Item slagOf(boolean phosphoric)
    {
        return phosphoric ? Metallurgy.PHOSPHORIC_IRON_SLAG.get() : Metallurgy.IRON_SLAG.get();
    }

    /** A bloomery has burned out. Called before it consumes what was in it. */
    public static void bloomeryFinished(BloomeryBlockEntity bloomery, List<ItemStack> charge, @Nullable BloomeryRecipe recipe)
    {
        if (recipe == null || !(bloomery.getLevel() instanceof ServerLevel level))
        {
            return;
        }

        int iron = 0, catalyst = 0;
        boolean phosphoric = false;
        for (ItemStack stack : charge)
        {
            final @Nullable FluidStack fluid = recipe.consumeInput(stack);
            if (fluid != null)
            {
                iron += fluid.getAmount();
                phosphoric |= fluid.getFluid() == Metallurgy.PHOSPHORIC_IRON_OXIDE.get();
            }
            else
            {
                catalyst++;
            }
        }

        final int perBloom = recipe.getInputFluid().amount();
        final int blooms = Math.min(iron / perBloom, catalyst / recipe.getCatalyst().count());
        if (blooms > 0)
        {
            // Only what reached a bloom has lost all its oxygen. The iron in the slag still holds its share.
            final float[] gases = new float[Gas.COUNT];
            gases[Gas.CARBON_DIOXIDE.ordinal()] = reductionGas(blooms * BLOOM_MILLIBUCKETS);
            GasEmission.release(level, bloomery.getInternalBlockPos(), gases);
        }

        // All the iron that did not reach a bloom
        final int slag = bloomerySlag(iron, blooms * BLOOM_MILLIBUCKETS);
        if (slag > 0)
        {
            Helpers.spawnItem(level, bloomery.getExternalBlock(), new ItemStack(slagOf(phosphoric), slag));
        }
    }

    /** A blast furnace has reduced one piece of its charge, along with a piece of flux. */
    public static void blastFurnaceReduced(Level level, BlockPos pos, FluidStack iron)
    {
        if (level instanceof ServerLevel server)
        {
            final float[] gases = new float[Gas.COUNT];
            gases[Gas.CARBON_DIOXIDE.ordinal()] = reductionGas(iron.getAmount()) + CARBON_DIOXIDE_PER_FLUX;
            GasEmission.release(server, pos, gases);
            Helpers.spawnItem(level, pos, new ItemStack(Metallurgy.FURNACE_SLAG.get()));
        }
    }

    private IronSmelting() {}
}
