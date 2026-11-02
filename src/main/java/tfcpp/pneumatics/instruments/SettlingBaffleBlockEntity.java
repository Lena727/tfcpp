package tfcpp.pneumatics.instruments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
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
 * Scraping them stirs some of the dust back into the air, which is why that job was so deadly. What is caught, and how it is scraped off, is kept in a {@link DustStore}.
 */
public final class SettlingBaffleBlockEntity extends BlockEntity
{
    private static final float CAPTURE_PER_SECOND = 0.05f;

    private final DustStore dust = new DustStore();

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
                for (Gas gas : DustStore.GASES)
                {
                    final int index = gas.ordinal();
                    if (cell[index] > 0)
                    {
                        final float caught = cell[index] * CAPTURE_PER_SECOND;
                        cell[index] -= caught;
                        dust.add(gas, caught);
                        volume.markChanged(near.getX(), near.getY(), near.getZ());
                        setChanged();
                    }
                }
            }
        }
    }

    public float collected(Gas gas)
    {
        return dust.collected(gas);
    }

    /** Gives whole items of powder to a player, and stirs part of what was scraped back into the air around them. */
    public void scrape(Player player)
    {
        if (dust.scrape(level, player) > 0)
        {
            setChanged();
        }
        else
        {
            player.displayClientMessage(Component.translatable("tfcpp.settling_baffle.nothing"), true);
        }
    }

    /** Whole items drop with the block, and what is too little to scrape off blows away as dust. */
    public void dropContents()
    {
        if (level != null)
        {
            dust.dropContents(level, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        dust.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        dust.load(tag);
    }
}
