package tfcpp.pneumatics.pipes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.climate.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import tfcpp.core.LocalTime;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * A connected run of pipe, treated as one volume at one pressure. Sound crosses a real pipe within a fraction of a second, and this runs once per second, so a pressure that differs along a pipe would not be seen anyway. What a pipe's length does change is how fast anything can get through it, which is worked out at each port.
 * <p>
 * Costs nothing per block of pipe from one second to the next: only its ports are visited, and the walls once a minute.
 */
public final class PipeNetwork
{
    private static final int CORROSION_INTERVAL_SECONDS = 60;
    private static final float WALL_HEAT_CAPACITY_PER_SEGMENT = 4f;
    /** A wall takes heat from a fire about like this: 30 kW across about 1000 K. */
    private static final float HEATING_KILOWATTS_PER_KELVIN = 0.03f;
    private static final float CHANGE_TO_SAVE = 1e-3f;
    /** Temperature drifts with the weather all day, which is not worth saving a chunk for. */
    private static final float TEMPERATURE_TO_SAVE = 1f;
    private static final float WALL_RESCAN_KELVIN = 5f;
    private static final float FULL_TO_OVERFLOW = 0.99f;
    private static final float LITRES_PER_BLOCK = 1000f;
    private static final float WATER_FREEZES = 268f;
    private static final float HOT_ENOUGH_TO_WEAKEN = 400f;
    private static final float FULL_ENOUGH_TO_BURST_WHEN_FROZEN = 0.9f;
    private static final float LITRES_FLOW_LIMIT_PER_STEP = 500f;

    private final PipeContents contents;
    private final List<PipeSegment> segments;
    private final List<BlockPos> ports = new ArrayList<>();
    private final LongOpenHashSet chunks = new LongOpenHashSet();
    private final float volume;

    private float heatLoss;
    private float lowestY = Float.MAX_VALUE, highestY = -Float.MAX_VALUE;
    private int secondsToCorrosion = CORROSION_INTERVAL_SECONDS;
    private long corrodedUntil;
    /** The first to give way, found again only when the walls have changed. Looking through every wall each second would make a long pressurised main expensive. */
    @Nullable private PipeSegment weakest;
    private float weakestRating = -1f;
    private float weakestScannedAt;
    private float lastMarkedAmount, lastMarkedTemperature;
    /** Liquid that has run out of a vent, and not yet come to a whole source block. */
    private float overflowLitres;

    PipeNetwork(ServerLevel level, List<PipeSegment> segments)
    {
        this.segments = segments;
        this.contents = new PipeContents();

        float total = 0;
        long oldest = Calendars.SERVER.getTicks();
        for (PipeSegment segment : segments)
        {
            total += segment.volume;
            if (segment.corrodedUntil >= 0L)
            {
                oldest = Math.min(oldest, segment.corrodedUntil);
            }
            contents.add(segment.stored);
            segment.stored = null;
            segment.network = this;
            chunks.add(ChunkPos.asLong(segment.pos));

            final BlockState state = level.getBlockState(segment.pos);
            if (state.getBlock() instanceof PipeMember member)
            {
                heatLoss += member.heatLoss(state);
            }
            if (state.getBlock() instanceof PipePort || state.getBlock() instanceof ManometerBlock || state.getBlock() instanceof PressureVesselBlock)
            {
                ports.add(segment.pos);
            }
            lowestY = Math.min(lowestY, segment.pos.getY());
            highestY = Math.max(highestY, segment.pos.getY());
        }
        this.volume = total;
        this.corrodedUntil = oldest;
        markUnsaved(level); // What each chunk holds has just changed: it now shares in the whole
    }

    /** A chunk that is not marked does not get saved, and would come back with its old share. */
    private void markUnsaved(ServerLevel level)
    {
        lastMarkedAmount = contents.gasMoles() + contents.litres;
        lastMarkedTemperature = contents.temperature;
        for (long chunk : chunks)
        {
            final @Nullable LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
            if (loaded != null)
            {
                loaded.setUnsaved(true);
            }
        }
    }

    public PipeContents contents()
    {
        return contents;
    }

    public float volume()
    {
        return volume;
    }

    public List<PipeSegment> segments()
    {
        return segments;
    }

    public float pressure()
    {
        return contents.pressure(volume);
    }

    /** @return the distance things travel through this on average, in metres */
    public float pathLength()
    {
        return segments.size() / 2f + 1f;
    }

    /** Hands each segment its share back, and leaves this empty. */
    void dissolve(ServerLevel level)
    {
        markUnsaved(level);
        for (PipeSegment segment : segments)
        {
            segment.stored = contents.share(segment.volume / volume);
            segment.network = null;
        }
    }

    /** Runs one step. @return the segment that burst, if any */
    @Nullable
    PipeSegment step(ServerLevel level, float seconds)
    {
        stepTemperature(level, seconds);
        for (BlockPos port : ports)
        {
            final BlockState state = level.getBlockState(port);
            if (state.getBlock() instanceof PipePort fitting)
            {
                fitting.stepPort(this, level, port, state, seconds);
            }
        }

        if (--secondsToCorrosion <= 0)
        {
            secondsToCorrosion = CORROSION_INTERVAL_SECONDS;
            final long now = Calendars.SERVER.getTicks();
            corrode(level, (float) (now - corrodedUntil) / LocalTime.TICKS_PER_DAY);
            corrodedUntil = now;
            for (PipeSegment segment : segments)
            {
                segment.corrodedUntil = now;
            }
            weakestRating = -1f;
            markUnsaved(level);
        }

        if (Math.abs(contents.gasMoles() + contents.litres - lastMarkedAmount) > CHANGE_TO_SAVE || Math.abs(contents.temperature - lastMarkedTemperature) > TEMPERATURE_TO_SAVE)
        {
            markUnsaved(level);
        }
        return findBurst(level);
    }

    private void stepTemperature(ServerLevel level, float seconds)
    {
        final BlockPos first = segments.getFirst().pos;
        final float ambient = Climate.get(level).getInstantTemperature(level, first) + 273.15f;

        // TFC's fuel burns out in a fixed number of ticks: a log that would last an hour lasts about two minutes. The same energy reaches the pipe in that time, so heat moves at that same scale.
        float conductance = heatLoss * LocalTime.TIME_SCALE;
        float power = conductance * (ambient - contents.temperature);
        for (BlockPos port : ports)
        {
            if (level.getBlockEntity(port) instanceof PressureVesselBlockEntity vessel && vessel.getTemperature() > 0f)
            {
                power += HEATING_KILOWATTS_PER_KELVIN * LocalTime.TIME_SCALE * (vessel.getTemperature() + 273.15f - contents.temperature);
                conductance += HEATING_KILOWATTS_PER_KELVIN * LocalTime.TIME_SCALE;
            }
        }
        if (conductance > 0f)
        {
            final float capacity = contents.heatCapacity() + WALL_HEAT_CAPACITY_PER_SEGMENT * segments.size();
            final float limit = Math.abs(power) / conductance;
            contents.temperature += Math.clamp(power * seconds / capacity, -limit, limit); // A step can only go as far as the temperature it is heading to
        }
    }

    private void corrode(ServerLevel level, float days)
    {
        final float moles = contents.gasMoles();
        final float fill = contents.liquidVolume() / volume;
        for (PipeSegment segment : segments)
        {
            final BlockState state = level.getBlockState(segment.pos);
            if (state.getBlock() instanceof PipeMember member)
            {
                final PipeMaterial material = member.material(state);
                float rate = contents.litres > 0f ? material.corrosion(contents.liquid) * fill : 0f;
                if (moles > 0f)
                {
                    for (Gas gas : Gas.VALUES)
                    {
                        final float amount = contents.gases[gas.ordinal()];
                        if (amount > 0f)
                        {
                            rate += material.corrosion(gas) * amount / moles;
                        }
                    }
                }
                segment.integrity = Math.max(0f, segment.integrity - rate * days);
            }
        }
    }

    @Nullable
    private PipeSegment findBurst(ServerLevel level)
    {
        if (contents.isWater() && contents.temperature < WATER_FREEZES && contents.liquidVolume() / volume > FULL_ENOUGH_TO_BURST_WHEN_FROZEN)
        {
            return segments.getFirst(); // Ice takes 9% more room than water, and no pipe holds that back
        }

        if (weakestRating < 0f || (contents.temperature > HOT_ENOUGH_TO_WEAKEN && Math.abs(contents.temperature - weakestScannedAt) > WALL_RESCAN_KELVIN))
        {
            weakestRating = Float.MAX_VALUE;
            weakestScannedAt = contents.temperature;
            for (PipeSegment segment : segments)
            {
                final BlockState state = level.getBlockState(segment.pos);
                if (state.getBlock() instanceof PipeMember member)
                {
                    final float rating = member.material(state).rating(segment.integrity, contents.temperature);
                    if (rating < weakestRating)
                    {
                        weakestRating = rating;
                        weakest = segment;
                    }
                }
            }
        }

        final float gauge = pressure() - 1f;
        return gauge > weakestRating || weakestRating <= 0f ? weakest : null;
    }

    /**
     * Lets gas out through an opening under a head of liquid, into something that takes all of it, as a tuyere does under molten iron.
     *
     * @param backPressure pressure in atm on the far side of the opening
     * @param areaShare share of the bore that the opening leaves clear
     * @return what came through, or {@code null} if the pressure in the pipe cannot lift the liquid
     */
    @Nullable
    public PipeContents blowInto(float backPressure, float areaShare, float seconds)
    {
        final float drive = contents.pressure(volume) - backPressure;
        final float moles = contents.gasMoles();
        if (drive <= 0f || moles <= 0f)
        {
            return null;
        }
        final float density = contents.gasDensity(volume);
        final float speed = PipePhysics.flowSpeed(drive * PipePhysics.PASCALS_PER_ATM, density, pathLength());
        final float flow = speed * PipePhysics.BORE_AREA * areaShare * density * 1000f / contents.molarMass() * seconds;
        final float toEven = PipePhysics.moles(drive, contents.temperature, contents.freeVolume(volume));
        return contents.takeGas(Math.min(Math.min(flow, toEven) / moles, 1f));
    }

    /** Lets gas through a vent, either way, according to the pressure on each side. */
    public void flowThroughVent(ServerLevel level, BlockPos outside, float seconds)
    {
        flowThroughOpening(level, outside, seconds, 1f, 0f, false);
    }

    /**
     * @param areaShare share of the bore that the opening leaves clear
     * @param holdsGauge pressure above outside that the opening shuts at
     */
    public void flowThroughOpening(ServerLevel level, BlockPos outside, float seconds, float areaShare, float holdsGauge, boolean outOnly)
    {
        final LevelGasVolume world = GasTracker.get(level).volume(level);
        if (!world.isPassable(outside.getX(), outside.getY(), outside.getZ()))
        {
            return;
        }

        final @Nullable float[] cell = world.cell(outside.getX(), outside.getY(), outside.getZ());
        final float outsidePressure = cell == null ? 1f : GasMath.pressure(cell);
        final float inside = contents.pressure(volume);
        if (inside > outsidePressure)
        {
            final float density = contents.gasDensity(volume);
            final float speed = PipePhysics.flowSpeed((inside - outsidePressure) * PipePhysics.PASCALS_PER_ATM, density, pathLength());
            final float flow = speed * PipePhysics.BORE_AREA * areaShare * density * 1000f / contents.molarMass() * seconds;
            final float toEven = PipePhysics.moles(Math.max(inside - outsidePressure - holdsGauge, 0f), contents.temperature, contents.freeVolume(volume));
            final float moles = contents.gasMoles();
            if (moles > 0f)
            {
                release(world, outside, contents.takeGas(Math.min(Math.min(flow, toEven) / moles, 1f)));
            }
            boilOff(inside, outsidePressure, holdsGauge, areaShare, seconds);
            overflow(level, outside);
        }
        else if (!outOnly && outsidePressure > inside)
        {
            final float density = PipePhysics.AIR_MOLAR_MASS / 1000f * GasMath.CELL_MOLES; // About air, 1.2 kg/m3
            final float speed = PipePhysics.flowSpeed((outsidePressure - inside) * PipePhysics.PASCALS_PER_ATM, density, pathLength());
            final float toEven = PipePhysics.moles(outsidePressure - inside, contents.temperature, contents.freeVolume(volume));
            drawFromCell(cell, outside, world, Math.min(speed * PipePhysics.BORE_AREA * GasMath.CELL_MOLES * seconds, toEven));
        }
    }

    /**
     * Pressure that comes from water's own vapour can only be let out as steam, which takes the heat of boiling with it. This is what keeps a boiler with an open valve at the boiling point for its pressure. The steam itself condenses in the air and is not tracked.
     */
    private void boilOff(float inside, float outside, float holdsGauge, float areaShare, float seconds)
    {
        if (!contents.isWater() || contents.litres <= 0f)
        {
            return;
        }
        final float vapour = PipePhysics.waterVapourPressure(contents.temperature);
        final float drive = Math.min(vapour, inside) - outside - holdsGauge;
        if (drive > 0f)
        {
            final float density = PipePhysics.steamDensity(vapour, contents.temperature);
            final float speed = PipePhysics.flowSpeed(drive * PipePhysics.PASCALS_PER_ATM, density, pathLength());
            final float litres = Math.min(speed * PipePhysics.BORE_AREA * areaShare * density * seconds, contents.litres);
            final float capacity = contents.heatCapacity() + WALL_HEAT_CAPACITY_PER_SEGMENT * segments.size();
            contents.temperature -= litres * PipePhysics.WATER_LATENT_HEAT / capacity;
            contents.litres -= litres;
            if (contents.litres <= 0f)
            {
                contents.liquid = "";
            }
        }
    }

    /** A pipe filled to the top runs over at any opening, and does not burst. */
    private void overflow(ServerLevel level, BlockPos outside)
    {
        final float surplus = (contents.liquidVolume() - volume * FULL_TO_OVERFLOW) * LITRES_PER_BLOCK;
        final @Nullable Fluid fluid = fluid();
        if (surplus > 0f && fluid != null)
        {
            final float litres = Math.min(surplus, contents.litres);
            contents.litres -= litres;
            overflowLitres += litres;
            if (overflowLitres >= LITRES_PER_BLOCK && level.getBlockState(outside).canBeReplaced())
            {
                overflowLitres -= LITRES_PER_BLOCK;
                level.setBlockAndUpdate(outside, fluid.defaultFluidState().createLegacyBlock());
            }
        }
    }

    /** Takes in this many moles of whatever the air at this position is made of. Plain air costs the world nothing, as it has plenty. */
    public void drawFromCell(@Nullable float[] cell, BlockPos pos, LevelGasVolume world, float moles)
    {
        float taken = 0f;
        if (cell != null)
        {
            final float total = Math.max(GasMath.total(cell), GasMath.CELL_MOLES);
            for (int i = 0; i < Gas.COUNT; i++)
            {
                final float amount = Math.min(cell[i], moles * cell[i] / total);
                cell[i] -= amount;
                contents.gases[i] += amount;
                taken += amount;
            }
            world.markChanged(pos.getX(), pos.getY(), pos.getZ());
        }
        contents.mixHeat(moles * 0.029f, PipePhysics.ROOM_TEMPERATURE);
        contents.air += Math.max(0f, moles - taken);
    }

    /** Lets liquid out of a tap into whatever holds liquid beyond it, driven by the pressure in the pipe and the height of liquid above the tap. */
    public void flowThroughTap(ServerLevel level, BlockPos tap, Direction facing, float seconds)
    {
        if (contents.litres <= 0f)
        {
            return;
        }
        if (!level.isLoaded(tap.relative(facing)))
        {
            return;
        }
        final @Nullable IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, tap.relative(facing), facing.getOpposite());
        final @Nullable Fluid fluid = fluid();
        if (handler == null || fluid == null)
        {
            return;
        }

        final float fill = Math.min(contents.liquidVolume() / volume, 1f);
        final float surface = lowestY + fill * (highestY - lowestY + 1f);
        final float density = fluid.getFluidType().getDensity();
        final float head = density * 9.81f * Math.max(0f, surface - tap.getY() - 0.5f);
        final float drive = (pressure() - 1f) * PipePhysics.PASCALS_PER_ATM + head;
        final float litres = Math.min(Math.min(PipePhysics.flowSpeed(drive, density, pathLength()) * PipePhysics.BORE_AREA * 1000f * seconds, LITRES_FLOW_LIMIT_PER_STEP), contents.litres);

        final int offered = (int) litres;
        if (offered > 0)
        {
            final int filled = handler.fill(new FluidStack(fluid, offered), IFluidHandler.FluidAction.EXECUTE);
            contents.litres -= filled;
            if (contents.litres <= 0f)
            {
                contents.liquid = "";
            }
        }
    }

    @Nullable
    public Fluid fluid()
    {
        final @Nullable ResourceLocation id = contents.liquid.isEmpty() ? null : ResourceLocation.tryParse(contents.liquid);
        return id == null ? null : BuiltInRegistries.FLUID.get(id);
    }

    public static void hiss(ServerLevel level, BlockPos pos)
    {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.4f + level.random.nextFloat() * 0.3f);
    }

    /** Puts the gas in these contents into the world. Air needs no storing, as a cell counts whatever else is there as air. */
    public static void release(LevelGasVolume world, BlockPos pos, PipeContents released)
    {
        if (GasMath.total(released.gases) <= 0f)
        {
            return;
        }
        final @Nullable float[] cell = world.cellForEmission(pos.getX(), pos.getY(), pos.getZ());
        if (cell != null)
        {
            for (int i = 0; i < Gas.COUNT; i++)
            {
                cell[i] += released.gases[i];
            }
        }
    }
}
