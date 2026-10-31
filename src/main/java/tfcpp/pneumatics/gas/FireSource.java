package tfcpp.pneumatics.gas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity;
import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.blockentities.BurningLogPileBlockEntity;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blockentities.FireboxBlockEntity;
import net.dries007.tfc.common.blockentities.PitKilnBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * A TFC device that burns fuel, polled once per second. Each kind has a characteristic thermal power and share of carbon leaving as CO:
 * <ul>
 *     <li>Open fires get plenty of air and burn nearly clean.</li>
 *     <li>A charcoal pit is a smouldering, air-starved burn, and also drives methane out of the wood.</li>
 *     <li>A bloomery and blast furnace work by making CO on purpose, since CO is what reduces the ore. Real blast furnace top gas is roughly a quarter CO.</li>
 * </ul>
 */
public final class FireSource
{
    private enum Kind
    {
        FIREPIT(15f, 0.05f, 0f),
        CHARCOAL_FORGE(30f, 0.10f, 0f),
        FIREBOX(25f, 0.08f, 0f),
        PIT_KILN(15f, 0.15f, 0f),
        CHARCOAL_PIT(2f, 0.30f, 0.05f),
        BLOOMERY(60f, 0.35f, 0f),
        BLAST_FURNACE(200f, 0.40f, 0f);

        final float kilowatts, carbonMonoxideShare, methanePerCarbon;

        Kind(float kilowatts, float carbonMonoxideShare, float methanePerCarbon)
        {
            this.kilowatts = kilowatts;
            this.carbonMonoxideShare = carbonMonoxideShare;
            this.methanePerCarbon = methanePerCarbon;
        }
    }

    /** Hot flue gas rises as a plume until it hits a ceiling, then cools and spreads from there. */
    private static final int MAX_PLUME_RISE = 48;
    private static final int MAX_CHARGE_HEIGHT = 16;
    /** Bellows roughly double the rate of combustion, which is also how TFC treats fuel burn rate. */
    private static final float BELLOWS_POWER_FACTOR = 2f;
    private static final long MAX_CATCH_UP_TICKS = 20 * 60 * 10;

    @Nullable
    public static FireSource of(BlockEntity entity)
    {
        final Kind kind;
        if (entity instanceof AbstractFirepitBlockEntity<?>) kind = Kind.FIREPIT;
        else if (entity instanceof CharcoalForgeBlockEntity) kind = Kind.CHARCOAL_FORGE;
        else if (entity instanceof FireboxBlockEntity) kind = Kind.FIREBOX;
        else if (entity instanceof PitKilnBlockEntity) kind = Kind.PIT_KILN;
        else if (entity instanceof BurningLogPileBlockEntity) kind = Kind.CHARCOAL_PIT;
        else if (entity instanceof BloomeryBlockEntity) kind = Kind.BLOOMERY;
        else if (entity instanceof BlastFurnaceBlockEntity) kind = Kind.BLAST_FURNACE;
        else return null;
        return new FireSource(entity, kind);
    }

    public static void pollAll(ServerLevel level, LevelGasVolume volume, Collection<FireSource> fires)
    {
        // Putting out a fire changes the block, which can remove sources from the collection being walked
        final List<FireSource> snapshot = new ArrayList<>(fires);
        for (FireSource fire : snapshot)
        {
            fire.poll(level, volume);
        }
    }

    private final BlockEntity entity;
    private final Kind kind;
    private long lastCalendarTick = -1;

    private FireSource(BlockEntity entity, Kind kind)
    {
        this.entity = entity;
        this.kind = kind;
    }

    public BlockEntity entity()
    {
        return entity;
    }

    private void poll(ServerLevel level, LevelGasVolume volume)
    {
        final long now = Calendars.SERVER.getTicks();
        final long previous = lastCalendarTick;
        lastCalendarTick = now;
        if (entity.isRemoved() || previous < 0 || !isBurning(entity.getBlockState()))
        {
            return;
        }

        final float seconds = Math.min(now - previous, MAX_CATCH_UP_TICKS) / 20f;
        if (seconds <= 0)
        {
            return;
        }

        final BlockPos pos = entity.getBlockPos();
        final int intakeY = firstOpenAbove(level, volume, entity instanceof BloomeryBlockEntity bloomery ? bloomery.getInternalBlockPos() : pos);
        if (intakeY == Integer.MIN_VALUE)
        {
            return;
        }

        final @Nullable float[] intake = volume.cell(pos.getX(), intakeY, pos.getZ());
        final float oxygen = intake == null ? GasMath.OXYGEN_IN_AIR : GasMath.oxygenFraction(intake);
        if (oxygen < Combustion.EXTINCTION_OXYGEN && extinguish())
        {
            return;
        }

        final float carbonMonoxideShare = Combustion.carbonMonoxideShare(kind.carbonMonoxideShare, oxygen);
        final float carbon = Combustion.carbonRate(kind.kilowatts * bellowsFactor(), carbonMonoxideShare) * seconds;

        emit(volume, pos.getX(), intakeY, pos.getZ(), carbon, carbonMonoxideShare);
    }

    private void emit(LevelGasVolume volume, int x, int fromY, int z, float carbon, float carbonMonoxideShare)
    {
        int y = fromY;
        for (int rise = 0; rise < MAX_PLUME_RISE; rise++)
        {
            if (volume.isOpenToSky(x, y, z))
            {
                return; // Goes straight into the atmosphere, without creating any cells
            }
            if (!volume.isPassable(x, y + 1, z))
            {
                break;
            }
            y++;
        }

        final @Nullable float[] cell = volume.cellForEmission(x, y, z);
        if (cell != null)
        {
            volume.markChanged(x, y, z);
            Combustion.addFlueGas(cell, carbon, carbonMonoxideShare);
            cell[Gas.METHANE.ordinal()] += carbon * kind.methanePerCarbon;
        }
    }

    /**
     * @return the height where this device's flue gas comes out: up through its own burning charge, and at most one covering block, which is how a charcoal pit vents through its earth.
     * Anything thicker smothers the fire, so a device can never leak into a separate room above it.
     */
    private static int firstOpenAbove(ServerLevel level, LevelGasVolume volume, BlockPos pos)
    {
        final BlockPos.MutableBlockPos cursor = pos.mutable();
        boolean usedCover = false;
        for (int rise = 0; rise < MAX_CHARGE_HEIGHT; rise++)
        {
            cursor.setY(pos.getY() + rise);
            if (volume.isPassable(cursor.getX(), cursor.getY(), cursor.getZ()))
            {
                return cursor.getY();
            }
            final Block block = level.getBlockState(cursor).getBlock();
            if (block != TFCBlocks.MOLTEN.get() && block != TFCBlocks.BURNING_LOG_PILE.get() && rise > 0)
            {
                if (usedCover)
                {
                    return Integer.MIN_VALUE;
                }
                usedCover = true;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean isBurning(BlockState state)
    {
        return switch (kind)
        {
            case CHARCOAL_PIT -> true;
            case CHARCOAL_FORGE -> state.hasProperty(CharcoalForgeBlock.HEAT) && state.getValue(CharcoalForgeBlock.HEAT) > 0;
            case PIT_KILN -> ((PitKilnBlockEntity) entity).isLit();
            default -> state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
        };
    }

    private float bellowsFactor()
    {
        final int air;
        if (entity instanceof AbstractFirepitBlockEntity<?> firepit) air = firepit.getAirTicks();
        else if (entity instanceof CharcoalForgeBlockEntity forge) air = forge.getAirTicks();
        else if (entity instanceof BlastFurnaceBlockEntity furnace) air = furnace.getAirTicks();
        else air = 0;
        return air > 0 ? BELLOWS_POWER_FACTOR : 1f;
    }

    /** @return {@code true} if this device can be starved of oxygen and went out */
    private boolean extinguish()
    {
        final BlockState state = entity.getBlockState();
        if (entity instanceof AbstractFirepitBlockEntity<?> firepit) firepit.extinguish(state);
        else if (entity instanceof CharcoalForgeBlockEntity forge) forge.extinguish(state);
        else if (entity instanceof BlastFurnaceBlockEntity furnace) furnace.extinguish(state);
        else return false;
        return true;
    }
}
