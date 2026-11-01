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
 * A piston on a crank. Each turn it sweeps the same volume, whatever it holds: liquid from a pipe, a barrel or standing water behind it, or gas from a pipe or the air, and it pushes that into the pipe in front. The shaft pays for the difference in pressure, so what stops it is the strength of its source, and otherwise the pipe gives way first.
 */
public final class PistonPumpBlockEntity extends TFCBlockEntity implements RotationSinkBlockEntity
{
    /** About 25 cm by 40 cm. At the speed of a full water wheel that is 5 l/s of liquid, or 5 l/s of gas at the pressure it comes in at. */
    public static final float SWEPT_VOLUME = 0.02f;
    private static final float FRICTION_TORQUE = 0.02f;
    private static final int LITRES_PER_BLOCK = 1000;

    private final SinkNode node;
    /** Water taken out of a pool a block at a time, and not yet pushed on. */
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

    /** @return the torque this takes from the shaft */
    public float tickPump(ServerLevel level, float seconds)
    {
        final @Nullable Rotation rotation = node.rotation();
        final float speed = rotation == null ? 0f : Math.abs(rotation.speed()) * ShaftPhysics.TICKS_PER_SECOND;
        return speed <= 0f ? 0f : pump(level, SWEPT_VOLUME * speed * seconds / (2f * (float) Math.PI));
    }

    /** Sweeps this volume once. @return the torque it takes */
    public float pump(ServerLevel level, float swept)
    {
        final Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final PipeTracker pipes = PipeTracker.get(level);
        final @Nullable PipeNetwork output = pipes.networkAt(worldPosition.relative(facing));
        if (output == null)
        {
            return FRICTION_TORQUE;
        }

        final BlockPos inletPos = worldPosition.relative(facing.getOpposite());
        // A device such as an oven is a block of a network too, but its tanks are the only thing open toward the pump
        final @Nullable PipeNetwork input = opensToward(level, inletPos, facing) ? pipes.networkAt(inletPos) : null;
        final float inletPressure = input == null ? 1f : input.pressure();
        final float torque = FRICTION_TORQUE + PipePhysics.pistonTorque(SWEPT_VOLUME, output.pressure() - inletPressure);

        final float litres = swept * LITRES_PER_BLOCK;
        if (input != null)
        {
            fromNetwork(input, output, swept, litres);
        }
        else if (!fromLiquid(level, inletPos, facing, output, litres))
        {
            fromAir(level, inletPos, output, swept);
        }
        return torque;
    }

    /** @return {@code true} if the block at this position is a part of a pipe network that opens on this side, which is the side that faces the pump */
    private static boolean opensToward(ServerLevel level, BlockPos pos, Direction side)
    {
        final BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PipeMember member && member.connects(state, side);
    }

    private static void fromNetwork(PipeNetwork input, PipeNetwork output, float swept, float litres)
    {
        final PipeContents from = input.contents();
        if (from.litres > 0f)
        {
            final PipeContents to = output.contents();
            if (to.liquid.isEmpty() || to.liquid.equals(from.liquid))
            {
                final float moved = Math.min(litres, from.litres);
                to.addLiquid(from.liquid, moved, from.temperature);
                from.litres -= moved;
                if (from.litres <= 0f)
                {
                    from.liquid = "";
                }
            }
            return;
        }
        final float share = Math.min(swept / from.freeVolume(input.volume()), 1f);
        output.contents().add(from.take(share));
    }

    /** @return {@code true} if there was liquid behind the pump, whether or not any could be moved */
    private boolean fromLiquid(ServerLevel level, BlockPos inlet, Direction facing, PipeNetwork output, float litres)
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
            if (!contained.isEmpty() && !contained.getFluid().is(TFCTags.Fluids.MOLTEN_METALS) && accepts(to, contained.getFluid()))
            {
                final FluidStack drained = handler.drain(contained.copyWithAmount((int) Math.ceil(litres)), IFluidHandler.FluidAction.EXECUTE);
                if (!drained.isEmpty()) // A sealed barrel gives nothing
                {
                    to.addLiquid(idOf(drained.getFluid()), drained.getAmount(), PipePhysics.ROOM_TEMPERATURE);
                }
            }
            return true;
        }

        final FluidState state = level.getFluidState(inlet);
        if (state.isEmpty() && heldLitres <= 0f)
        {
            return false;
        }

        if (heldLitres < litres && level.getBlockState(inlet).getBlock() == TFCBlocks.RIVER_WATER.get())
        {
            heldLiquid = idOf(Fluids.WATER); // A river does not run out for one pump
            heldLitres += LITRES_PER_BLOCK;
            setChanged();
        }
        else if (heldLitres < litres && state.isSource() && FluidHelpers.isAirOrEmptyFluid(level.getBlockState(inlet)))
        {
            heldLiquid = idOf(state.getType());
            heldLitres += LITRES_PER_BLOCK;
            level.setBlockAndUpdate(inlet, FluidHelpers.emptyFluidFrom(level.getBlockState(inlet)));
            setChanged();
        }

        if (heldLitres > 0f && (to.liquid.isEmpty() || to.liquid.equals(heldLiquid)))
        {
            final float moved = Math.min(litres, heldLitres);
            to.addLiquid(heldLiquid, moved, PipePhysics.ROOM_TEMPERATURE);
            heldLitres -= moved;
            setChanged();
        }
        return true;
    }

    private static boolean accepts(PipeContents to, Fluid fluid)
    {
        return to.liquid.isEmpty() || to.liquid.equals(idOf(fluid));
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
