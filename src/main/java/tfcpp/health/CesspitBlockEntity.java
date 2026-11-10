package tfcpp.health;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import tfcpp.ecology.Pollutant;
import tfcpp.ecology.Pollution;

/**
 * The contents of a cesspit. No ticker: the contents are brought up to the calendar when somebody touches the pit, deposits into it, empties it or draws water near it ({@link #catchUp}), and the closed form of {@link CesspitMath#advance} does the rest. What seeps into the ground in the time goes into the soil of the chunk at that moment.
 */
public final class CesspitBlockEntity extends BlockEntity
{
    private CesspitMath.State state = CesspitMath.State.EMPTY;
    /** The calendar tick the contents were last brought up to. */
    private long lastTick;
    /** Litres that ran over and went into the soil, for the tests and the command. */
    private double overflowed;
    /** What has seeped out and is not yet in the soil: infective litres, in all and from each kind of shedder. It goes in when it comes to {@link #FLUSH_LITRES}, so that a pit that is looked at every second does not mark its chunk every second. */
    private double pendingActive, pendingDysentery, pendingTyphoid;
    /** The volume at the last time the block was marked as changed. */
    private double markedVolume;

    /** Infective litres of seepage that are put into the soil at once; and litres of volume that mark the block changed. */
    private static final double FLUSH_LITRES = 0.01, MARK_LITRES = 0.5;

    public CesspitBlockEntity(BlockPos pos, BlockState blockState)
    {
        super(Hygiene.CESSPIT_ENTITY.get(), pos, blockState);
    }

    /** @return the contents as they are now, after the passage of the calendar since the last touch */
    public CesspitMath.State catchUp(ServerLevel level)
    {
        final long now = Pollution.now();
        if (lastTick == 0L || state.volume() <= 0.0 && state.active() <= 0.0)
        {
            lastTick = now; // Nothing in the pit to age: the clock starts when something is put in
            return state;
        }
        if (now > lastTick)
        {
            final CesspitMath.Step step = CesspitMath.advance(state, (now - lastTick) / 24_000.0);
            state = step.state();
            lastTick = now;
            pendingActive += step.leakedActive();
            pendingDysentery += step.leakedDysentery();
            pendingTyphoid += step.leakedTyphoid();
            boolean mark = Math.abs(markedVolume - state.volume()) >= MARK_LITRES;
            if (pendingActive >= FLUSH_LITRES)
            {
                flushSeepage(level);
                mark = true;
            }
            if (mark)
            {
                markedVolume = state.volume();
                setChanged();
            }
        }
        return state;
    }

    private void flushSeepage(ServerLevel level)
    {
        Pollution.deposit(level, worldPosition, Pollutant.SEWAGE, pendingActive);
        Pollution.deposit(level, worldPosition, Pollutant.SHED_DYSENTERY, pendingDysentery);
        Pollution.deposit(level, worldPosition, Pollutant.SHED_TYPHOID, pendingTyphoid);
        pendingActive = pendingDysentery = pendingTyphoid = 0.0;
    }

    /** @return litres of the deposit that the pit took: the rest runs over and the caller puts it on the ground */
    public double accept(ServerLevel level, double litres, double dysentery, double typhoid)
    {
        catchUp(level);
        final double taken = Math.min(litres, CesspitMath.room(state));
        if (taken > 0.0)
        {
            final double share = taken / litres;
            state = CesspitMath.add(state, taken, dysentery * share, typhoid * share);
            setChanged();
        }
        overflowed += litres - taken;
        return taken;
    }

    /**
     * Takes up to this many litres out, as night soil: the contents come out in proportion, and each piece of 20 litres carries its share of the infective part.
     *
     * @return the number of pieces
     */
    public int takePieces(ServerLevel level, int most, java.util.function.Consumer<net.minecraft.world.item.ItemStack> to)
    {
        final CesspitMath.State now = catchUp(level);
        final int pieces = Math.min(most, (int) Math.floor(now.volume() / CesspitMath.LITRES_PER_NIGHT_SOIL));
        if (pieces <= 0)
        {
            return 0;
        }
        final double litres = pieces * CesspitMath.LITRES_PER_NIGHT_SOIL, share = litres / now.volume();
        to.accept(NightSoil.piece(pieces, now.active() * share / pieces, now.shedDysentery() * share / pieces, now.shedTyphoid() * share / pieces, Pollution.now()));
        state = CesspitMath.take(now, litres);
        setChanged();
        return pieces;
    }

    /**
     * The pit is broken: the contents come out as night soil, whole pieces, each with its infective share exactly as cleaning gives them (so that nothing is gained by breaking a pit and putting it back), and the infective part of what is left under one piece goes into the soil.
     */
    public void spill(ServerLevel level, java.util.function.Consumer<net.minecraft.world.item.ItemStack> to)
    {
        takePieces(level, Integer.MAX_VALUE, to);
        final CesspitMath.State rest = state;
        Pollution.deposit(level, worldPosition, Pollutant.SEWAGE, rest.active() + pendingActive);
        Pollution.deposit(level, worldPosition, Pollutant.SHED_DYSENTERY, rest.shedDysentery() + pendingDysentery);
        Pollution.deposit(level, worldPosition, Pollutant.SHED_TYPHOID, rest.shedTyphoid() + pendingTyphoid);
        pendingActive = pendingDysentery = pendingTyphoid = 0.0;
        state = CesspitMath.State.EMPTY;
    }

    /** Water drawn near this pit carries off what it takes of the infective contents: they are gone from the pit. @return what went, with the pit after it */
    public CesspitMath.Draw drawInfective(ServerLevel level, double factor, double litres, double[] scale)
    {
        final CesspitMath.Draw draw = CesspitMath.draw(catchUp(level), factor, litres, scale[0], scale[1], scale[2]);
        if (draw.active() > 0.0)
        {
            state = draw.state();
            setChanged();
        }
        return draw;
    }

    /** Sets the contents, for a test. */
    public void set(CesspitMath.State contents, long calendarTick)
    {
        state = contents;
        lastTick = calendarTick;
        setChanged();
    }

    public CesspitMath.State state()
    {
        return state;
    }

    public double overflowed()
    {
        return overflowed;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        tag.putDouble("volume", state.volume());
        tag.putDouble("active", state.active());
        tag.putDouble("shedDysentery", state.shedDysentery());
        tag.putDouble("shedTyphoid", state.shedTyphoid());
        tag.putLong("lastTick", lastTick);
        tag.putDouble("overflowed", overflowed);
        tag.putDouble("pendingActive", pendingActive);
        tag.putDouble("pendingDysentery", pendingDysentery);
        tag.putDouble("pendingTyphoid", pendingTyphoid);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        state = new CesspitMath.State(tag.getDouble("volume"), tag.getDouble("active"), tag.getDouble("shedDysentery"), tag.getDouble("shedTyphoid"));
        lastTick = tag.getLong("lastTick");
        overflowed = tag.getDouble("overflowed");
        pendingActive = tag.getDouble("pendingActive");
        pendingDysentery = tag.getDouble("pendingDysentery");
        pendingTyphoid = tag.getDouble("pendingTyphoid");
        markedVolume = state.volume();
    }
}
