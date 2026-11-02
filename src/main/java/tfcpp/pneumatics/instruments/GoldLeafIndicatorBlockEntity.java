package tfcpp.pneumatics.instruments;

import org.jetbrains.annotations.Nullable;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import tfcpp.chemistry.PoisonTests;
import tfcpp.core.TickClock;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasTracker;

/**
 * Keeps the mercury dose of a gold leaf. Once a second it breathes the air in its own cell, as a leaf in a chamber takes up what is there, and its stage follows the dose.
 * <p>
 * A dose that has moved less than {@link #SAVE_THRESHOLD} since it was last saved does not mark the chunk as changed, so a leaf in still air costs nothing to save. At the last stage it stops reading the air, since nothing more can be taken up.
 */
public final class GoldLeafIndicatorBlockEntity extends BlockEntity
{
    /** (mg/m3)&middot;h. A change smaller than this is not worth a write to the chunk. */
    public static final float SAVE_THRESHOLD = 0.01f;

    private static final int STEP_TICKS = 20;
    private static final long MAX_STEP_TICKS = 100L;

    private final TickClock clock = new TickClock();
    private float dose;
    private float savedDose;

    public GoldLeafIndicatorBlockEntity(BlockPos pos, BlockState state)
    {
        super(Instruments.GOLD_LEAF_INDICATOR_ENTITY.get(), pos, state);
    }

    /** Runs once a second in total, each leaf in its own tick: the offset by its position spreads the work out over the second. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, GoldLeafIndicatorBlockEntity leaf)
    {
        // On the ticks of TFC's calendar, which stop on an empty server along with the air the leaf breathes. Each leaf keeps its own place in the second.
        final long elapsed = leaf.clock.due(Calendars.SERVER.getTicks(), STEP_TICKS, MAX_STEP_TICKS, (int) Math.floorMod(pos.asLong(), (long) STEP_TICKS));
        if (elapsed > 0L)
        {
            leaf.breathe((ServerLevel) level, elapsed / 20f);
        }
    }

    /** Takes one second of the air in this block's own cell. */
    public void breathe(ServerLevel level)
    {
        breathe(level, 1f);
    }

    /** Takes this many seconds of the air in this block's own cell. Air with no cell in it has no mercury, and the dose stays where it is. */
    public void breathe(ServerLevel level, float seconds)
    {
        final BlockState state = getBlockState();
        final int stage = state.getValue(GoldLeafIndicatorBlock.STAGE);
        if (stage >= 3)
        {
            return;
        }
        final BlockPos pos = getBlockPos();
        final @Nullable float[] cell = GasTracker.get(level).volume(level).cell(pos.getX(), pos.getY(), pos.getZ());
        if (cell == null)
        {
            return;
        }
        dose = PoisonTests.addDose(dose, PoisonTests.mercuryMilligrams(cell[Gas.MERCURY_VAPOR.ordinal()]), seconds);
        final int next = PoisonTests.leafStage(dose);
        if (next != stage)
        {
            level.setBlock(pos, state.setValue(GoldLeafIndicatorBlock.STAGE, next), Block.UPDATE_ALL);
        }
        if (next != stage || Math.abs(dose - savedDose) > SAVE_THRESHOLD)
        {
            savedDose = dose;
            setChanged();
        }
    }

    /** @return the mercury dose so far, in (mg/m3)&middot;h */
    public float dose()
    {
        return dose;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        tag.putFloat("dose", dose);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        dose = tag.getFloat("dose");
        savedDose = dose;
    }
}
