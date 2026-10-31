package tfcpp.pneumatics.instruments;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * Baffle plates in a dust chamber. Fine dust settles slowly because it only has to fall a short way before it meets a surface, so closely spaced plates catch far more of it than bare walls do. This is how the arsenic works of Freiberg and Cornwall collected their white arsenic, in long chambers of brick that were scraped by hand.
 * <p>
 * Scraping them stirs some of the dust back into the air, which is why that job was so deadly.
 */
public final class SettlingBaffleBlockEntity extends BlockEntity
{
    /** One item of powder, about 50 g of arsenic trioxide. Roasting a normal piece of tetrahedrite gives slightly more than this. */
    public static final float MOLES_PER_POWDER = 0.25f;
    private static final float CAPTURE_PER_SECOND = 0.05f;
    public static final float STIRRED_UP = 0.05f;

    private record Dust(Gas gas, Supplier<Item> powder) {}

    private static final Dust[] DUSTS = {
        new Dust(Gas.ARSENIC_TRIOXIDE, Instruments.ARSENIC_TRIOXIDE),
        new Dust(Gas.ANTIMONY_TRIOXIDE, Instruments.ANTIMONY_TRIOXIDE),
        new Dust(Gas.ZINC_OXIDE_FUME, Instruments.ZINC_OXIDE)
    };

    private final float[] collected = new float[DUSTS.length];

    public SettlingBaffleBlockEntity(BlockPos pos, BlockState state)
    {
        super(Instruments.SETTLING_BAFFLE_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SettlingBaffleBlockEntity baffle)
    {
        if ((level.getGameTime() + pos.asLong()) % 20 == 0)
        {
            baffle.capture((ServerLevel) level);
        }
    }

    private void capture(ServerLevel level)
    {
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        for (Direction side : Direction.values())
        {
            final BlockPos near = worldPosition.relative(side);
            final @Nullable float[] cell = volume.cell(near.getX(), near.getY(), near.getZ());
            if (cell != null)
            {
                for (int i = 0; i < DUSTS.length; i++)
                {
                    final int gas = DUSTS[i].gas.ordinal();
                    if (cell[gas] > 0)
                    {
                        final float caught = cell[gas] * CAPTURE_PER_SECOND;
                        cell[gas] -= caught;
                        collected[i] += caught;
                        volume.markChanged(near.getX(), near.getY(), near.getZ());
                        setChanged();
                    }
                }
            }
        }
    }

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

    /** Gives whole items of powder to a player, and stirs part of what was scraped back into the air around them. */
    public void scrape(Player player)
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
                stirUp(DUSTS[i].gas, scraped * STIRRED_UP, player);
                final ItemStack powder = new ItemStack(DUSTS[i].powder.get(), items);
                if (!player.addItem(powder))
                {
                    player.drop(powder, false);
                }
            }
        }
        if (total > 0)
        {
            setChanged();
        }
        else
        {
            player.displayClientMessage(Component.translatable("tfcpp.settling_baffle.nothing"), true);
        }
    }

    private void stirUp(Gas gas, float moles, Player player)
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

    /** Whole items drop with the block, and what is too little to scrape off blows away as dust. */
    public void dropContents()
    {
        if (level == null)
        {
            return;
        }
        final BlockPos above = worldPosition.above();
        final @Nullable float[] cell = level instanceof ServerLevel serverLevel ? GasTracker.get(serverLevel).volume(serverLevel).cellForEmission(above.getX(), above.getY(), above.getZ()) : null;
        for (int i = 0; i < DUSTS.length; i++)
        {
            final int items = (int) (collected[i] / MOLES_PER_POWDER);
            if (items > 0)
            {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), new ItemStack(DUSTS[i].powder.get(), items));
            }
            if (cell != null)
            {
                cell[DUSTS[i].gas.ordinal()] += collected[i] - items * MOLES_PER_POWDER;
            }
            collected[i] = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        for (int i = 0; i < DUSTS.length; i++)
        {
            tag.putFloat(DUSTS[i].gas.id(), collected[i]);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        for (int i = 0; i < DUSTS.length; i++)
        {
            collected[i] = tag.getFloat(DUSTS[i].gas.id());
        }
    }
}
