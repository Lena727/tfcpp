package tfcpp.pneumatics.instruments;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasTracker;

/**
 * The white dust that settles out of furnace fumes in a chamber, kept in moles for each kind until it is scraped off by hand. Shared by everything that catches it: the plates of a settling baffle and the walls of a condensation tower.
 * <p>
 * Scraping stirs some of it back into the air at the one who scrapes, which is why that job was so deadly.
 */
public final class DustStore
{
    /** One item of powder, about 50 g of arsenic trioxide. Roasting a normal piece of tetrahedrite gives slightly more than this. */
    public static final float MOLES_PER_POWDER = 0.25f;
    /** Share of what is scraped off that goes up into the air as well, on top of what the player takes away. */
    public static final float STIRRED_UP = 0.05f;

    private record Dust(Gas gas, Supplier<Item> powder) {}

    private static final Dust[] DUSTS = {
        new Dust(Gas.ARSENIC_TRIOXIDE, Instruments.ARSENIC_TRIOXIDE),
        new Dust(Gas.ANTIMONY_TRIOXIDE, Instruments.ANTIMONY_TRIOXIDE),
        new Dust(Gas.ZINC_OXIDE_FUME, Instruments.ZINC_OXIDE),
        new Dust(Gas.LEAD_OXIDE_FUME, Instruments.LEAD_OXIDE)
    };

    /** The gases that settle as these dusts. */
    public static final List<Gas> GASES = List.of(DUSTS[0].gas, DUSTS[1].gas, DUSTS[2].gas, DUSTS[3].gas);

    private final float[] collected = new float[DUSTS.length];

    private static int indexOf(Gas gas)
    {
        for (int i = 0; i < DUSTS.length; i++)
        {
            if (DUSTS[i].gas == gas)
            {
                return i;
            }
        }
        throw new IllegalArgumentException(gas + " does not settle as dust");
    }

    /** Adds dust that has settled. @param gas one of {@link #GASES} */
    public void add(Gas gas, float moles)
    {
        collected[indexOf(gas)] += moles;
    }

    /** @return moles of this dust held, or 0 for a gas that does not settle as dust */
    public float collected(Gas gas)
    {
        for (int i = 0; i < DUSTS.length; i++)
        {
            if (DUSTS[i].gas == gas)
            {
                return collected[i];
            }
        }
        return 0;
    }

    /**
     * Gives whole items of powder to a player, and stirs part of what was scraped back into the air around them.
     *
     * @return how many items were given, which is 0 if there was too little of any dust to make one
     */
    public int scrape(@Nullable Level level, Player player)
    {
        int total = 0;
        for (int i = 0; i < DUSTS.length; i++)
        {
            final int items = (int) (collected[i] / (MOLES_PER_POWDER * (1 + STIRRED_UP)));
            if (items > 0)
            {
                total += items;
                final float scraped = items * MOLES_PER_POWDER;
                collected[i] -= scraped * (1 + STIRRED_UP);
                stirUp(level, DUSTS[i].gas, scraped * STIRRED_UP, player);
                final ItemStack powder = new ItemStack(DUSTS[i].powder.get(), items);
                if (!player.addItem(powder))
                {
                    player.drop(powder, false);
                }
            }
        }
        return total;
    }

    private static void stirUp(@Nullable Level level, Gas gas, float moles, Player player)
    {
        if (level instanceof ServerLevel serverLevel)
        {
            final BlockPos eye = BlockPos.containing(player.getEyePosition());
            final @Nullable float[] cell = GasTracker.get(serverLevel).volume(serverLevel).cellForEmission(eye.getX(), eye.getY(), eye.getZ());
            if (cell != null)
            {
                cell[gas.ordinal()] += moles;
            }
        }
    }

    /** Whole items drop where the chamber stood, and what is too little to scrape off blows away as dust over it. */
    public void dropContents(Level level, BlockPos pos)
    {
        final BlockPos above = pos.above();
        final @Nullable float[] cell = level instanceof ServerLevel serverLevel ? GasTracker.get(serverLevel).volume(serverLevel).cellForEmission(above.getX(), above.getY(), above.getZ()) : null;
        for (int i = 0; i < DUSTS.length; i++)
        {
            final int items = (int) (collected[i] / MOLES_PER_POWDER);
            if (items > 0)
            {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(DUSTS[i].powder.get(), items));
            }
            if (cell != null)
            {
                cell[DUSTS[i].gas.ordinal()] += collected[i] - items * MOLES_PER_POWDER;
            }
            collected[i] = 0;
        }
    }

    public void save(CompoundTag tag)
    {
        for (int i = 0; i < DUSTS.length; i++)
        {
            tag.putFloat(DUSTS[i].gas.id(), collected[i]);
        }
    }

    public void load(CompoundTag tag)
    {
        for (int i = 0; i < DUSTS.length; i++)
        {
            collected[i] = tag.getFloat(DUSTS[i].gas.id());
        }
    }
}
