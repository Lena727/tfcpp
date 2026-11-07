package tfcpp.pneumatics.pipes;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.TFCBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.RotationSinkBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.util.rotation.NetworkAction;
import net.dries007.tfc.util.rotation.Node;
import net.dries007.tfc.util.rotation.Rotation;
import net.dries007.tfc.util.rotation.SinkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import tfcpp.mechanics.ShaftPhysics;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * A piston on a crank. Each turn it sweeps the same volume, whatever it holds: liquid from a pipe, a barrel or standing water behind it, or gas from a pipe or the air, and it pushes that into the pipe in front. The shaft pays for the difference in pressure and for the lift of the liquid, so what stops it is the strength of its source, and otherwise the pipe gives way first.
 */
public final class PistonPumpBlockEntity extends TFCBlockEntity implements RotationSinkBlockEntity
{
    /** About 25 cm by 40 cm. At the speed of a full water wheel that is 5 l/s of liquid, or 5 l/s of gas at the pressure it comes in at. */
    public static final float SWEPT_VOLUME = 0.02f;
    private static final float FRICTION_TORQUE = 0.02f;
    private static final int LITRES_PER_BLOCK = 1000;
    /** Lava, molten metal and boiling water are not drawn into a pipe that is at room temperature. */
    private static final float HOT_FLUID_KELVIN = 373.15f;

    private final SinkNode node;
    /** Liquid taken out of a pool a block at a time, or out of a barrel by the millilitre, and not yet pushed on. */
    private float heldLitres;
    private String heldLiquid = "";

    public PistonPumpBlockEntity(BlockPos pos, BlockState state)
    {
        super(Pipes.PISTON_PUMP_ENTITY.get(), pos, state);
        this.node = new SinkNode(pos, state.getValue(HorizontalDirectionalBlock.FACING).getClockWise())
        {
            @Override
            public String toString()
            {
                return "PistonPump[pos=%s]".formatted(pos());
            }
        };
    }

    @Override
    public Node getRotationNode()
    {
        return node;
    }

    @Override
    protected void onLoadAdditional()
    {
        performNetworkAction(NetworkAction.ADD);
    }

    @Override
    protected void onUnloadAdditional()
    {
        performNetworkAction(NetworkAction.REMOVE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        tag.putFloat("heldLitres", heldLitres);
        tag.putString("heldLiquid", heldLiquid);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        heldLitres = tag.getFloat("heldLitres");
        heldLiquid = tag.getString("heldLiquid");
    }

    /** @return the torque this takes from the shaft; a pump that is not turning is still loaded by the pressure it stands against, which is what holds a shaft that the load has stopped */
    public float tickPump(ServerLevel level, float seconds)
    {
        final @Nullable Rotation rotation = node.rotation();
        final float speed = rotation == null ? 0f : Math.abs(rotation.speed()) * ShaftPhysics.TICKS_PER_SECOND;
        return pump(level, speed <= 0f ? 0f : SWEPT_VOLUME * speed * seconds / (2f * (float) Math.PI));
    }

    /** What a transfer did: whether there was liquid behind the pump, and the density of the liquid that really moved, which is 0 for gas or for nothing. */
    private record Moved(boolean liquid, float density)
    {
        static final Moved NOTHING = new Moved(false, 0f);
    }

    /** @return the key of the message that says what this pump will do */
    public String statusKey(ServerLevel level)
    {
        final Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final @Nullable PipeNetwork output = outputNetwork(level, facing);
        return output == null ? "tfcpp.pump.status.no_output" : output.truncated() ? "tfcpp.pump.status.truncated" : "tfcpp.pump.status.ready";
    }

    /** @return the network in front of the pump, if the block there is open toward it */
    @Nullable
    private PipeNetwork outputNetwork(ServerLevel level, Direction facing)
    {
        final BlockPos outputPos = worldPosition.relative(facing);
        return opensToward(level, outputPos, facing.getOpposite()) ? PipeTracker.get(level).networkAt(outputPos) : null;
    }

    /**
     * Sweeps this volume once.
     *
     * @return the torque it takes: friction, the difference in pressure, and the weight of the liquid it lifts from the top of the liquid behind it to the top of the liquid in front
     */
    public float pump(ServerLevel level, float swept)
    {
        final Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final PipeTracker pipes = PipeTracker.get(level);
        final @Nullable PipeNetwork output = outputNetwork(level, facing);
        if (output == null || output.truncated())
        {
            return FRICTION_TORQUE; // The far side of a line that runs on past loaded land may be anything: nothing is delivered into it
        }

        final BlockPos inletPos = worldPosition.relative(facing.getOpposite());
        // A device such as a lead chamber is a block of a network too, but its tank is the only thing open toward the pump: the water and the gas of its network are what it works with, and are not for drawing out into an acid line. With the tank empty the pump takes nothing
        final boolean tankOnly = isTankOnly(level, inletPos, facing);
        final @Nullable PipeNetwork input = opensToward(level, inletPos, facing) && !tankOnly ? pipes.networkAt(inletPos) : null;
        final float inletPressure = input == null ? 1f : input.pressure();
        final float pressureLoad = output.pressure() - inletPressure;
        final float outletY = worldPosition.relative(facing).getY(), inletY = inletPos.getY();
        final float outputSurface = output.liquidSurface();
        final float inputSurface = input != null && input.contents().litres > 0f ? input.liquidSurface() : inletY; // A barrel or a pool is at the pump's own level

        if (swept <= 0f)
        {
            return FRICTION_TORQUE + PipePhysics.pistonTorque(SWEPT_VOLUME, pressureLoad);
        }

        final float litres = swept * LITRES_PER_BLOCK;
        final Moved moved;
        if (input != null)
        {
            moved = fromNetwork(input, output, swept, litres, inletY);
        }
        else
        {
            moved = fromLiquid(level, inletPos, facing, output, litres);
            if (!moved.liquid() && !tankOnly)
            {
                fromAir(level, inletPos, output, swept);
            }
        }
        if (moved.liquid() && moved.density() <= 0f)
        {
            return FRICTION_TORQUE; // There was liquid, and none could be drawn: the piston works against a vacuum, or a wall
        }
        final float lift = moved.density() > 0f ? PipePhysics.head(moved.density(), (outputSurface - outletY) + (inletY - inputSurface)) / PipePhysics.PASCALS_PER_ATM : 0f;
        return FRICTION_TORQUE + PipePhysics.pistonTorque(SWEPT_VOLUME, pressureLoad + lift);
    }

    /** @return {@code true} if the block at this position is a part of a pipe network that opens on this side, which is the side that faces the pump */
    private static boolean opensToward(ServerLevel level, BlockPos pos, Direction side)
    {
        final BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PipeMember member && member.connects(state, side);
    }

    /** @return {@code true} if the block here keeps liquid in a tank of its own that a pump on this side draws from, and from nothing else: not from its network, and not from the air of its cell */
    private static boolean isTankOnly(ServerLevel level, BlockPos pos, Direction side)
    {
        final BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PipeMember member && member.tankOpenToPump(state) && member.connects(state, side);
    }

    private static Moved fromNetwork(PipeNetwork input, PipeNetwork output, float swept, float litres, float inletY)
    {
        final PipeContents from = input.contents();
        if (from.litres > 0f)
        {
            final float density = input.liquidDensity();
            // The air over the liquid pushes it up the pipe, and no further than its weight allows: a pump cannot draw from deeper than that
            if (inletY - input.liquidSurface() > PipePhysics.suctionLift(input.pressure(), density))
            {
                return new Moved(true, 0f);
            }
            final float amount = Math.min(litres, from.litres);
            if (output.contents().addLiquid(from.liquid, amount, from.temperature))
            {
                from.litres -= amount;
                from.tidy();
                return new Moved(true, density);
            }
            return new Moved(true, 0f);
        }
        final float share = Math.min(swept / from.freeVolume(input.volume()), 1f);
        final @Nullable PipeContents rejected = output.contents().add(from.take(share));
        if (rejected != null)
        {
            from.add(rejected); // Gas carries no liquid, so this is not meant to happen; if it does, what did not fit goes back
        }
        return Moved.NOTHING;
    }

    /** Whether the liquid is too hot to be drawn into a pipe at room temperature: lava, molten metal and anything above the boiling point of water. */
    private static boolean tooHotToPump(Fluid fluid)
    {
        return fluid.is(FluidTags.LAVA) || fluid.is(TFCTags.Fluids.MOLTEN_METALS) || fluid.getFluidType().getTemperature() > HOT_FLUID_KELVIN;
    }

    /** Moves what is held into the pipe in front, as much as this stroke sweeps. @return the density of the liquid that went, or 0 */
    private float deliverHeld(PipeContents to, float litres)
    {
        if (heldLitres > 0f && to.takesLiquid(heldLiquid))
        {
            final float amount = Math.min(litres, heldLitres);
            to.addLiquid(heldLiquid, amount, PipePhysics.ROOM_TEMPERATURE);
            final float density = densityOf(heldLiquid);
            heldLitres -= amount;
            if (heldLitres <= 0f)
            {
                heldLitres = 0f;
                heldLiquid = "";
            }
            setChanged();
            return density;
        }
        return 0f;
    }

    private static float densityOf(String id)
    {
        final @Nullable Fluid fluid = PipeNetwork.fluidOf(id);
        return fluid == null ? 1000f : Math.max(fluid.getFluidType().getDensity(), 1f);
    }

    /** @return whether there was liquid behind the pump, and if any could be moved, the density of it */
    private Moved fromLiquid(ServerLevel level, BlockPos inlet, Direction facing, PipeNetwork output, float litres)
    {
        final PipeContents to = output.contents();

        final @Nullable IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, inlet, facing);
        if (handler != null)
        {
            FluidStack contained = FluidStack.EMPTY; // The first tank that holds anything, as some devices have more than one
            for (int tank = 0; tank < handler.getTanks() && contained.isEmpty(); tank++)
            {
                contained = handler.getFluidInTank(tank);
            }
            if (!contained.isEmpty() && !tooHotToPump(contained.getFluid()) && accepts(to, contained.getFluid()))
            {
                // A barrel gives whole millilitres and a stroke may sweep a part of one: what is drawn and not yet delivered is held, and the next stroke goes on from it
                final String id = idOf(contained.getFluid());
                if (heldLitres < litres && (heldLitres <= 0f || heldLiquid.equals(id)))
                {
                    final FluidStack drained = handler.drain(contained.copyWithAmount((int) Math.ceil(litres - heldLitres)), IFluidHandler.FluidAction.EXECUTE);
                    if (!drained.isEmpty()) // A sealed barrel gives nothing
                    {
                        heldLiquid = id;
                        heldLitres += drained.getAmount();
                        setChanged();
                    }
                }
                return new Moved(true, deliverHeld(to, litres));
            }
            return new Moved(true, 0f);
        }

        final FluidState state = level.getFluidState(inlet);
        if (state.isEmpty() && heldLitres <= 0f)
        {
            return Moved.NOTHING;
        }

        if (heldLitres < litres && !tooHotToPump(state.getType()) && level.getBlockState(inlet).getBlock() == TFCBlocks.RIVER_WATER.get())
        {
            heldLiquid = idOf(Fluids.WATER); // A river does not run out for one pump
            heldLitres += LITRES_PER_BLOCK;
            setChanged();
        }
        else if (heldLitres < litres && state.isSource() && !tooHotToPump(state.getType()) && FluidHelpers.isAirOrEmptyFluid(level.getBlockState(inlet)))
        {
            heldLiquid = idOf(state.getType());
            heldLitres += LITRES_PER_BLOCK;
            level.setBlockAndUpdate(inlet, FluidHelpers.emptyFluidFrom(level.getBlockState(inlet)));
            setChanged();
        }
        return new Moved(true, deliverHeld(to, litres));
    }

    private static boolean accepts(PipeContents to, Fluid fluid)
    {
        return to.takesLiquid(idOf(fluid));
    }

    private static String idOf(Fluid fluid)
    {
        final ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        return id.toString();
    }

    private static void fromAir(ServerLevel level, BlockPos inlet, PipeNetwork output, float swept)
    {
        final LevelGasVolume world = GasTracker.get(level).volume(level);
        if (world.isPassable(inlet.getX(), inlet.getY(), inlet.getZ()))
        {
            output.drawFromCell(world.cell(inlet.getX(), inlet.getY(), inlet.getZ()), inlet, world, PipePhysics.moles(1f, PipePhysics.ROOM_TEMPERATURE, swept));
        }
    }
}
