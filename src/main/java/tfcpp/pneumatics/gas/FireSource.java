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
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import tfcpp.metallurgy.Hearth;

/**
 * A TFC device that burns fuel, polled once per second. Each kind has a characteristic thermal power and share of carbon leaving as CO:
 * <ul>
 *     <li>Open fires get plenty of air and burn nearly clean.</li>
 *     <li>A charcoal pit is a smouldering, air-starved burn, and also drives methane out of the wood.</li>
 *     <li>A bloomery and blast furnace work by making CO on purpose, since CO is what reduces the ore. Real blast furnace top gas is 20 to 28% CO.</li>
 * </ul>
 * A fire in air with less than {@link Combustion#EXTINCTION_OXYGEN} of oxygen puts nothing into it, whatever kind it is: there is nothing burning, and the air a fire that cannot be starved sits in is not made worse by it.
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
        BLAST_FURNACE(200f, Combustion.BLAST_FURNACE_CARBON_MONOXIDE_SHARE, 0f);

        final float kilowatts, carbonMonoxideShare, methanePerCarbon;

        Kind(float kilowatts, float carbonMonoxideShare, float methanePerCarbon)
        {
            this.kilowatts = kilowatts;
            this.carbonMonoxideShare = carbonMonoxideShare;
            this.methanePerCarbon = methanePerCarbon;
        }
    }

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
        if (entity.isRemoved() || previous < 0)
        {
            return;
        }
        // A border chunk, or one that has been demoted since its player left, keeps its block entities but runs none of them: the fuel is not burning, and looking at the blocks there could bring the chunk back up to full status
        final BlockPos pos = entity.getBlockPos();
        if (!level.shouldTickBlocksAt(pos) || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null || !isBurning(entity.getBlockState()))
        {
            return;
        }

        final float seconds = Math.min(now - previous, MAX_CATCH_UP_TICKS) / 20f;
        if (seconds <= 0)
        {
            return;
        }

        final BlockPos device = entity instanceof BloomeryBlockEntity bloomery ? bloomery.getInternalBlockPos() : pos;
        int intakeY = GasEmission.outletHeight(level, volume, device);
        float share = 1f;
        if (intakeY == GasEmission.SMOTHERED)
        {
            // Buried too deep to vent, as a charcoal pit under two layers of earth: a tenth of it still gets through the cover, if there is any open place above it for that to come out into
            intakeY = GasEmission.leakHeight(level, volume, device);
            share = GasEmission.LEAK_SHARE;
        }
        if (intakeY == GasEmission.SMOTHERED)
        {
            return;
        }

        final @Nullable float[] intake = volume.cell(pos.getX(), intakeY, pos.getZ());
        final float oxygen = intake == null ? GasMath.OXYGEN_IN_AIR : GasMath.oxygenFraction(intake);
        if (!Combustion.sustainsFlame(oxygen))
        {
            extinguish(); // A device that can be starved goes out. One that cannot (a bloomery, a firebox, a pit) burns on, but produces nothing in air like this
            return;
        }

        final float carbonMonoxideShare = Combustion.carbonMonoxideShare(kind.carbonMonoxideShare, oxygen);
        final float carbon = Combustion.carbonRate(kind.kilowatts * bellowsFactor(), carbonMonoxideShare) * seconds;

        final float[] flue = new float[Gas.COUNT];
        Combustion.addFlueGas(flue, carbon * share, carbonMonoxideShare);
        flue[Gas.METHANE.ordinal()] += carbon * share * kind.methanePerCarbon;
        GasEmission.addToPlume(volume, pos.getX(), intakeY, pos.getZ(), flue);
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

    /** A forge or a firepit burns faster by the share of full bellows it has, from a pipe or from wind as well; full bellows give the factor of 2. A blast furnace has only its bellows. */
    private float bellowsFactor()
    {
        if (entity instanceof Hearth hearth)
        {
            return 1f + hearth.tfcpp$draught().air();
        }
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
