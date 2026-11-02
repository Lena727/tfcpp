package tfcpp.chemistry;

import java.util.Locale;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.pipes.PipeContents;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * The tank of a lead chamber: the chamber acid that the gas of its network makes, kept until it is pumped or tapped out. The reaction itself is {@link ChamberProcess}, run once for each step of the network the chamber is in.
 */
public final class LeadChamberBlockEntity extends BlockEntity
{
    /** Millibuckets of acid that the tank holds, which is 100 L at 1 mB = 1 L: what one chamber keeps before it has to be emptied. */
    public static final int TANK_MILLIBUCKETS = 100;

    /** Moles of nitrogen oxides that one powder of saltpeter brings into the gas: 50 g of KNO3 is about half a mole, and it is burnt with sulfur in the nitre pot. */
    public static final float SALTPETER_MOLES_PER_POWDER = 0.5f;

    private static final float PERCENT = 100f;

    private final FluidTank tank = new FluidTank(TANK_MILLIBUCKETS, stack -> stack.getFluid() == Chemistry.fluid(Liquid.CHAMBER_ACID))
    {
        @Override
        protected void onContentsChanged()
        {
            setChanged();
        }
    };
    /** Acid made and not yet counted as a whole millibucket. */
    private Remainder carry = new Remainder();
    private final IFluidHandler outlet = new Outlet();

    public LeadChamberBlockEntity(BlockPos pos, BlockState state)
    {
        super(Chemistry.LEAD_CHAMBER_ENTITY.get(), pos, state);
    }

    public FluidTank tank()
    {
        return tank;
    }

    /** @return the handler of the tank, which can only be drained: acid is made here and not poured in */
    public IFluidHandler outlet()
    {
        return outlet;
    }

    /** @return acid made and not yet counted as a whole millibucket */
    public float pendingMillibuckets()
    {
        return (float) carry.pending();
    }

    /**
     * Runs the reaction in the gas of the network for one step, and takes the acid it makes into the tank. The gas, the water and the oxygen it uses are taken from the network, and its heat goes into the network too.
     *
     * @param network the network that this chamber is part of
     * @param seconds the length of the step
     */
    public void step(PipeNetwork network, float seconds)
    {
        final PipeContents contents = network.contents();
        final float water = contents.isWater() ? contents.litres : 0f;
        final float room = Math.max(0f, TANK_MILLIBUCKETS - tank.getFluidAmount() - (float) carry.pending()); // Litres of room, less what is waiting to be counted into the tank
        final ChamberProcess.Result result = ChamberProcess.react(
            contents.gases[Gas.SULFUR_DIOXIDE.ordinal()], contents.gases[Gas.NITROGEN_OXIDES.ordinal()], contents.air, contents.gasMoles(), water, room, seconds);
        if (result.acidMoles() <= 0f)
        {
            return;
        }

        contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] = Math.max(0f, contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] - result.sulfurDioxide());
        contents.gases[Gas.NITROGEN_OXIDES.ordinal()] = Math.max(0f, contents.gases[Gas.NITROGEN_OXIDES.ordinal()] - result.oxidesLost());
        contents.air = Math.max(0f, contents.air - result.air());
        contents.gases[Gas.FLUE_NITROGEN.ordinal()] += result.nitrogenLeftBehind();
        contents.litres = Math.max(0f, contents.litres - result.water());
        if (contents.litres <= 0f)
        {
            contents.liquid = "";
        }
        network.addHeat(result.heatKilojoules());

        carry.add(result.acidLitres());
        setChanged(); // Acid went in, even if it is still a fraction: a saved chunk must keep that fraction, or it is lost on reload
        final int whole = carry.whole();
        if (whole > 0)
        {
            carry.take(tank.fill(new FluidStack(Chemistry.fluid(Liquid.CHAMBER_ACID), whole), IFluidHandler.FluidAction.EXECUTE)); // Marks the chunk itself
        }
    }

    /** @return {@code true} if holding this item means to use it on the chamber, and not to place it against the block */
    public boolean canUse(ItemStack stack)
    {
        return stack.is(saltpeter()) || stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    /** @return {@code true} if a held item did something */
    public boolean interact(ItemStack stack, Player player, InteractionHand hand)
    {
        if (stack.is(saltpeter()))
        {
            return addSaltpeter(stack, player);
        }
        return tapInto(stack, player, hand);
    }

    /** Saltpeter burns to nitrous fumes in the gas of the network. One powder is used, and its oxides start the reaction. */
    private boolean addSaltpeter(ItemStack stack, Player player)
    {
        final @Nullable PipeNetwork network = networkOf();
        if (network == null)
        {
            player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.saltpeter_no_network"), true);
            return false;
        }
        network.contents().gases[Gas.NITROGEN_OXIDES.ordinal()] += SALTPETER_MOLES_PER_POWDER;
        stack.shrink(1);
        player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.saltpeter"), true);
        return true;
    }

    /** Runs the acid into one held container. A stack is only ever filled one at a time, as its capability would fill all of them for the price of one. A container that refuses chamber acid keeps what it had. */
    private boolean tapInto(ItemStack stack, Player player, InteractionHand hand)
    {
        if (tank.isEmpty())
        {
            player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.empty"), true);
            return false;
        }
        final ItemStack single = stack.copyWithCount(1);
        final @Nullable IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        final int filled = handler == null ? 0 : handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (handler == null || filled <= 0)
        {
            player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.refused"), true);
            return false;
        }
        tank.drain(filled, IFluidHandler.FluidAction.EXECUTE);

        final ItemStack result = handler.getContainer();
        if (stack.getCount() == 1)
        {
            player.setItemInHand(hand, result);
        }
        else
        {
            stack.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, result);
        }
        return true;
    }

    /** Reports what the chamber is doing to a person with an empty hand. */
    public void showStatus(Player player)
    {
        final @Nullable PipeNetwork network = networkOf();
        if (network == null)
        {
            player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.no_network"), false);
            player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.acid", tank.getFluidAmount()), false);
            return;
        }
        final PipeContents contents = network.contents();
        final float moles = contents.gasMoles();
        final float sulfurDioxide = share(contents.gases[Gas.SULFUR_DIOXIDE.ordinal()], moles);
        final float oxygen = share(contents.air * ChamberProcess.OXYGEN_IN_AIR, moles);
        final float oxides = share(contents.gases[Gas.NITROGEN_OXIDES.ordinal()], moles);
        player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.status",
            String.format(Locale.ROOT, "%.0f", contents.temperature - 273.15f),
            tank.getFluidAmount(),
            String.format(Locale.ROOT, "%.1f", sulfurDioxide * PERCENT),
            String.format(Locale.ROOT, "%.1f", oxygen * PERCENT),
            String.format(Locale.ROOT, "%.2f", oxides * PERCENT),
            String.format(Locale.ROOT, "%.1f", contents.isWater() ? contents.litres : 0f)), false);
        if (!contents.isWater())
        {
            player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.no_water"), false);
        }
        if (oxides < ChamberProcess.NITROGEN_OXIDES_FULL)
        {
            player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.oxides_low", String.format(Locale.ROOT, "%.1f", ChamberProcess.NITROGEN_OXIDES_FULL * PERCENT)), false);
        }
        if (oxygen < ChamberProcess.OXYGEN_FULL)
        {
            player.displayClientMessage(Component.translatable("tfcpp.lead_chamber.oxygen_low", String.format(Locale.ROOT, "%.1f", ChamberProcess.OXYGEN_FULL * PERCENT)), false);
        }
    }

    private static float share(float amount, float moles)
    {
        return moles > 0f ? amount / moles : 0f;
    }

    /**
     * Breaking the chamber lets the acid in its tank out. It splashes whoever is by it. The fraction of a millibucket still waiting to be counted is less than a drop, and goes with it.
     */
    public void spill(ServerLevel level)
    {
        if (!tank.isEmpty())
        {
            CorrosiveSpill.at(level, worldPosition);
        }
        tank.setFluid(FluidStack.EMPTY);
        carry = new Remainder();
    }

    private @Nullable PipeNetwork networkOf()
    {
        return level instanceof ServerLevel server ? PipeTracker.get(server).networkAt(worldPosition) : null;
    }

    private static Item saltpeter()
    {
        return TFCItems.ORE_POWDERS.get(Ore.SALTPETER).asItem();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        tag.put("acid", tank.writeToNBT(provider, new CompoundTag()));
        tag.putDouble("acidCarry", carry.pending());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        tank.readFromNBT(provider, tag.getCompound("acid"));
        carry = new Remainder(tag.getDouble("acidCarry"));
    }

    /** The tank seen from outside. Acid runs out of the chamber and is not poured back in. */
    private final class Outlet implements IFluidHandler
    {
        @Override
        public int getTanks()
        {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int index)
        {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int index)
        {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack)
        {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action)
        {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action)
        {
            return tank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action)
        {
            return tank.drain(maxDrain, action);
        }
    }
}
