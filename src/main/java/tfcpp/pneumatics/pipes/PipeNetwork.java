package tfcpp.pneumatics.pipes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
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
import net.minecraft.world.level.material.FluidState;
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
 * A connected run of pipe, treated as one volume at one pressure. Sound crosses a real pipe within a fraction of a second, and this runs once per second, so a pressure that differs along a pipe would not be seen anyway. What a pipe's length does change is how fast anything can get through it, which is worked out at each port. What does differ along a pipe is the weight of the liquid in it: a column of it presses on the bottom, and that is worked out from the layers of the pipe, one for each level.
 * <p>
 * Costs nothing per block of pipe from one second to the next: only its ports are visited, the layers of the walls, and the walls once a minute.
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
    private static final float HOT_ENOUGH_TO_WEAKEN = 400f;
    private static final float FULL_ENOUGH_TO_BURST_WHEN_FROZEN = 0.9f;
    private static final float LITRES_FLOW_LIMIT_PER_STEP = 500f;
    /** Gas let out under liquid rises this far through it at most, and is lost beyond. */
    private static final int MAX_BUBBLE_RISE = 16;
    /** Height of the liquid over an opening that looks into a block of it: the block's own, for now. */
    private static final float OWN_HEAD_METRES = 1f;

    private final PipeContents contents;
    private final List<PipeSegment> segments;
    private final List<BlockPos> ports = new ArrayList<>();
    private final LongOpenHashSet chunks = new LongOpenHashSet();
    /** Chunks that a joint of this leads into and that were not loaded when it was formed: the run goes on past loaded land. */
    private final LongOpenHashSet missing = new LongOpenHashSet();
    private float volume;

    private float heatLoss;
    private int lowestY = Integer.MAX_VALUE, highestY = Integer.MIN_VALUE;
    /** Cubic metres of room in each level, the lowest first: where the liquid stands and how deep it is under each wall comes from these. */
    private float[] layers = new float[0];
    private int secondsToCorrosion = CORROSION_INTERVAL_SECONDS;
    /** The first to give way in each level, found again only when the walls have changed. Looking through every wall each second would make a long pressurised main expensive. */
    private PipeSegment[] weakestByLayer = new PipeSegment[0];
    private float[] ratingByLayer = new float[0];
    @Nullable private PipeSegment weakest;
    private float weakestRating = -1f;
    private float weakestScannedAt;
    private float lastMarkedAmount, lastMarkedTemperature;
    /** Part of a millilitre that a tap owes, from a flow too slow to give one in a second. */
    private float tapCredit;

    PipeNetwork(ServerLevel level, List<PipeSegment> segments, LongOpenHashSet missing)
    {
        this.segments = segments;
        this.contents = new PipeContents();
        this.missing.addAll(missing);

        final long now = Calendars.SERVER.getTicks();
        for (PipeSegment segment : segments)
        {
            addMember(level, segment, now);
        }
        computeLayers();
        markUnsaved(level); // What each chunk holds has just changed: it now shares in the whole
    }

    private void addMember(ServerLevel level, PipeSegment segment, long now)
    {
        volume += segment.volume;
        if (segment.corrodedUntil < 0L)
        {
            segment.corrodedUntil = now; // Never counted yet, so it starts now
        }
        final @Nullable PipeContents rejected = contents.add(segment.stored);
        if (rejected != null)
        {
            PipeTracker.spillRejected(level, segment.pos, rejected); // Networks of different liquids are kept apart, so this is not meant to happen
        }
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

    private void computeLayers()
    {
        layers = new float[highestY - lowestY + 1];
        for (PipeSegment segment : segments)
        {
            layers[segment.pos.getY() - lowestY] += segment.volume;
        }
    }

    /**
     * Takes one more pipe into this without taking the network apart. Its contents mix in as they would in a rebuild, and nothing else about the network is looked at again except the walls, which are found anew at the next step.
     *
     * @param segment a pipe that is joined to this and to nothing else, with no network
     * @param missing the chunks that it leads into and that are not loaded
     */
    void attach(ServerLevel level, PipeSegment segment, LongOpenHashSet missing)
    {
        segments.add(segment);
        addMember(level, segment, Calendars.SERVER.getTicks());
        this.missing.addAll(missing);
        computeLayers();
        weakestRating = -1f;
        markUnsaved(level);
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

    /** @return {@code true} if this run goes on past land that is loaded: the far side may be anything, and holds its share until it is loaded again */
    public boolean truncated()
    {
        return !missing.isEmpty();
    }

    /** @return the chunks, as {@link ChunkPos#asLong} keys, that this goes on into and that were not loaded */
    public LongOpenHashSet missingChunks()
    {
        return missing;
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

    /** @return the height in blocks at which the top of the liquid stands, with the liquid lying at the bottom as it does; the bottom of the lowest pipe if there is none */
    public float liquidSurface()
    {
        return PipePhysics.surfaceHeight(layers, lowestY, contents.liquidVolume());
    }

    /** @return the density of the liquid in kg/m3, or that of water if it is not known */
    public float liquidDensity()
    {
        final @Nullable Fluid fluid = fluid();
        return fluid == null ? 1000f : Math.max(fluid.getFluidType().getDensity(), 0f);
    }

    /**
     * Heat that a reaction in the network gives out. The gas, the liquid and the walls all take their share of it, so the temperature rises by the energy over the whole heat capacity.
     *
     * @param kilojoules heat given to the network
     */
    public void addHeat(float kilojoules)
    {
        final float capacity = contents.heatCapacity() + WALL_HEAT_CAPACITY_PER_SEGMENT * segments.size();
        if (capacity > 0f)
        {
            contents.temperature += kilojoules / capacity;
        }
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
            corrode(level);
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

    /**
     * Counts the corrosion of every wall from the tick of the clock that it was last counted up to, which each segment keeps for itself: a network is rebuilt at any moment, one part of it can be unloaded for days, and a pipe just put in has none to count. The chunks are marked to be saved only if a wall has really changed.
     */
    public void corrode(ServerLevel level)
    {
        final long now = Calendars.SERVER.getTicks();
        final float moles = contents.gasMoles();
        final float fill = contents.liquidVolume() / volume;
        boolean changed = false;
        for (PipeSegment segment : segments)
        {
            final long since = segment.corrodedUntil < 0L ? now : segment.corrodedUntil; // Never counted: it starts now
            final float days = Math.max(0f, (float) (now - since) / LocalTime.TICKS_PER_DAY);
            segment.corrodedUntil = now;
            if (days <= 0f)
            {
                continue;
            }
            final BlockState state = level.getBlockState(segment.pos);
            if (state.getBlock() instanceof PipeMember member)
            {
                final PipeMaterial material = member.material(state);
                float rate = contents.litres > 0f ? material.corrosion(contents.liquid, contents.temperature) * fill : 0f;
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
                rate += member.tankCorrosion(level, segment.pos, state, contents.temperature);
                final float before = segment.integrity;
                segment.integrity = Math.max(0f, before - rate * days);
                changed |= segment.integrity != before;
            }
        }
        if (changed)
        {
            weakestRating = -1f;
            markUnsaved(level);
        }
    }

    /** Finds the weakest wall of each level. */
    private void scanWalls(ServerLevel level)
    {
        weakestRating = Float.MAX_VALUE;
        weakestScannedAt = contents.temperature;
        weakest = null;
        ratingByLayer = new float[layers.length];
        weakestByLayer = new PipeSegment[layers.length];
        Arrays.fill(ratingByLayer, Float.MAX_VALUE);
        for (PipeSegment segment : segments)
        {
            final BlockState state = level.getBlockState(segment.pos);
            if (state.getBlock() instanceof PipeMember member)
            {
                final float rating = member.material(state).rating(segment.integrity, contents.temperature);
                final int layer = segment.pos.getY() - lowestY;
                if (rating < ratingByLayer[layer])
                {
                    ratingByLayer[layer] = rating;
                    weakestByLayer[layer] = segment;
                }
                if (rating < weakestRating)
                {
                    weakestRating = rating;
                    weakest = segment;
                }
            }
        }
    }

    @Nullable
    private PipeSegment findBurst(ServerLevel level)
    {
        if (contents.isWater() && contents.temperature < contents.freezingPoint() && contents.liquidVolume() / volume > FULL_ENOUGH_TO_BURST_WHEN_FROZEN)
        {
            return segments.getFirst(); // Ice takes 9% more room than water, and no pipe holds that back
        }

        if (weakestRating < 0f || (contents.temperature > HOT_ENOUGH_TO_WEAKEN && Math.abs(contents.temperature - weakestScannedAt) > WALL_RESCAN_KELVIN))
        {
            scanWalls(level);
        }

        final float gauge = pressure() - 1f;
        if (weakestRating <= 0f)
        {
            return weakest;
        }
        if (contents.litres <= 0f)
        {
            return gauge > weakestRating ? weakest : null;
        }

        // A column of liquid presses on what is under it: the lowest wall carries the gauge pressure and the whole weight of the liquid above it
        final float surface = liquidSurface(), density = liquidDensity();
        for (int layer = 0; layer < layers.length; layer++)
        {
            final float depth = Math.max(surface - (lowestY + layer), 0f);
            if (weakestByLayer[layer] != null && gauge + PipePhysics.head(density, depth) / PipePhysics.PASCALS_PER_ATM > ratingByLayer[layer])
            {
                return weakestByLayer[layer];
            }
        }
        return null;
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
        if (!level.isLoaded(outside))
        {
            return;
        }
        final LevelGasVolume world = GasTracker.get(level).volume(level);
        final FluidState liquidOutside = level.getFluidState(outside);
        if (!liquidOutside.isEmpty())
        {
            dischargeUnderLiquid(level, world, outside, liquidOutside, seconds, areaShare, holdsGauge);
            return;
        }
        if (!world.isPassable(outside.getX(), outside.getY(), outside.getZ()))
        {
            return;
        }

        final @Nullable float[] cell = world.cell(outside.getX(), outside.getY(), outside.getZ());
        final float outsidePressure = cell == null ? 1f : GasMath.pressure(cell);
        final float inside = contents.pressure(volume);
        if (inside > outsidePressure)
        {
            letGasOut(inside, outsidePressure, holdsGauge, areaShare, seconds, released -> release(world, outside, released));
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
     * An opening that looks into liquid: gas gets out only if it is pushed harder than the air over the liquid and the weight of the liquid over the opening together, and it bubbles up through the liquid to the first air over it. Nothing comes in. The liquid over the opening is, for now, only the block it looks into.
     */
    private void dischargeUnderLiquid(ServerLevel level, LevelGasVolume world, BlockPos outside, FluidState liquidOutside, float seconds, float areaShare, float holdsGauge)
    {
        final float density = Math.max(liquidOutside.getType().getFluidType().getDensity(), 0f);
        final float outsidePressure = 1f + PipePhysics.head(density, OWN_HEAD_METRES) / PipePhysics.PASCALS_PER_ATM;
        final float inside = contents.pressure(volume);
        if (inside > outsidePressure)
        {
            letGasOut(inside, outsidePressure, holdsGauge, areaShare, seconds, released -> bubbleUp(level, world, outside, released));
        }
    }

    private void letGasOut(float inside, float outsidePressure, float holdsGauge, float areaShare, float seconds, Consumer<PipeContents> receiver)
    {
        final float density = contents.gasDensity(volume);
        final float speed = PipePhysics.flowSpeed((inside - outsidePressure) * PipePhysics.PASCALS_PER_ATM, density, pathLength());
        final float flow = speed * PipePhysics.BORE_AREA * areaShare * density * 1000f / contents.molarMass() * seconds;
        final float toEven = PipePhysics.moles(Math.max(inside - outsidePressure - holdsGauge, 0f), contents.temperature, contents.freeVolume(volume));
        final float moles = contents.gasMoles();
        if (moles > 0f)
        {
            receiver.accept(contents.takeGas(Math.min(Math.min(flow, toEven) / moles, 1f)));
        }
        boilOff(inside, outsidePressure, holdsGauge, areaShare, seconds);
    }

    /** Gas that comes out under liquid rises through it, and goes into the first air above. Where there is none within reach, or a ceiling stops it, it is lost. */
    private static void bubbleUp(ServerLevel level, LevelGasVolume world, BlockPos from, PipeContents released)
    {
        final BlockPos.MutableBlockPos pos = from.mutable();
        for (int rise = 0; rise < MAX_BUBBLE_RISE; rise++)
        {
            pos.move(Direction.UP);
            if (!level.isLoaded(pos))
            {
                return;
            }
            if (world.isPassable(pos.getX(), pos.getY(), pos.getZ()))
            {
                release(world, pos, released);
                return;
            }
            if (level.getFluidState(pos).isEmpty())
            {
                return;
            }
        }
    }

    /**
     * Pressure that comes from water's own vapour can only be let out as steam, which takes the heat of boiling with it. This is what keeps a boiler with an open valve at the boiling point for its pressure, and no lower: the water cannot be cooled below the boiling point at the pressure it is let out against. The steam itself condenses in the air and is not tracked.
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
            final float capacity = contents.heatCapacity() + WALL_HEAT_CAPACITY_PER_SEGMENT * segments.size();
            final float toTheBoilingPoint = Math.max(contents.temperature - PipePhysics.saturationTemperature(outside + holdsGauge), 0f) * capacity / PipePhysics.WATER_LATENT_HEAT;
            final float litres = Math.min(Math.min(speed * PipePhysics.BORE_AREA * areaShare * density * seconds, contents.litres), toTheBoilingPoint);
            contents.temperature -= litres * PipePhysics.WATER_LATENT_HEAT / capacity;
            contents.litres -= litres;
            contents.tidy();
        }
    }

    /**
     * A pipe filled to the top runs over at any opening, and does not burst. What runs over waits as runoff in the contents, which are kept through every rebuild and save, until there is a whole block of it and somewhere to put it: the nearest block beyond the opening that can take liquid. If there is none, or the runoff is already a whole block, the rest stays in the pipe and the pressure rises.
     */
    private void overflow(ServerLevel level, BlockPos outside)
    {
        final @Nullable Fluid fluid = fluid();
        final float surplus = (contents.liquidVolume() - volume * FULL_TO_OVERFLOW) * LITRES_PER_BLOCK;
        if (surplus > 0f && fluid != null && (contents.runoff <= 0f || contents.runoffLiquid.equals(contents.liquid)))
        {
            final float litres = Math.min(Math.min(surplus, contents.litres), LITRES_PER_BLOCK - contents.runoff);
            if (litres > 0f)
            {
                contents.litres -= litres;
                contents.runoff += litres;
                contents.runoffLiquid = contents.liquid;
                contents.tidy();
            }
        }
        if (contents.runoff >= LITRES_PER_BLOCK)
        {
            final @Nullable Fluid runoffFluid = fluidOf(contents.runoffLiquid);
            if (runoffFluid != null && PipeTracker.placeBlock(level, outside, runoffFluid))
            {
                contents.runoff -= LITRES_PER_BLOCK;
                if (contents.runoff <= 0f)
                {
                    contents.runoff = 0f;
                    contents.runoffLiquid = "";
                }
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
                if (Gas.VALUES[i].isParticulate())
                {
                    continue; // The intake of a pipe is not a dust collector: dust, lying or flying, stays in the room
                }
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

    /**
     * Lets liquid out of a tap into whatever holds liquid beyond it, driven by the pressure in the pipe and the weight of the liquid over the tap. The weight counts for or against: a tap above the top of the liquid has nothing to push it out but the pressure.
     */
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

        final float density = fluid.getFluidType().getDensity();
        final float head = PipePhysics.head(density, liquidSurface() - (tap.getY() + 0.5f)); // Signed: negative above the surface
        final float drive = (pressure() - 1f) * PipePhysics.PASCALS_PER_ATM + head;
        final float flow = Math.min(PipePhysics.flowSpeed(drive, density, pathLength()) * PipePhysics.BORE_AREA * 1000f * seconds, LITRES_FLOW_LIMIT_PER_STEP);

        final PipePhysics.Drip owed = PipePhysics.drip(tapCredit, flow, contents.litres);
        int filled = 0;
        if (owed.whole() > 0)
        {
            filled = handler.fill(new FluidStack(fluid, owed.whole()), IFluidHandler.FluidAction.EXECUTE);
            contents.litres -= filled;
            contents.tidy();
        }
        tapCredit = PipePhysics.creditAfter(owed, filled);
    }

    @Nullable
    public Fluid fluid()
    {
        return fluidOf(contents.liquid);
    }

    @Nullable
    public static Fluid fluidOf(String id)
    {
        final @Nullable ResourceLocation location = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return location == null ? null : BuiltInRegistries.FLUID.get(location);
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
