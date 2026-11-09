package tfcpp.metallurgy;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
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

import tfcpp.chemistry.Remainder;
import tfcpp.ecology.Pollution;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.instruments.DustStore;
import tfcpp.pneumatics.pipes.PipeContents;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * A fire-clay chamber of a pipe network, a cubic metre of cool brick wall for the gas to give its fume to. The oxides of arsenic, antimony, zinc and lead settle on the walls as dust while the gas is below 250 C, and mercury runs down them as a liquid below 100 C. The dust is scraped off by hand as from a settling baffle, which is the same {@link DustStore}; the mercury is tapped.
 * <p>
 * The rates are in {@link TowerCondensation}. N towers in a network are N times the wall, so each catches its share of what the one before it left.
 */
public final class CondensationTowerBlockEntity extends BlockEntity
{
    /** Moles of mercury in a millibucket of the liquid. */
    static final float MOLES_PER_MILLIBUCKET = Sulfide.CINNABAR.metalMoles(1f);
    /** A change in the dust smaller than this is not worth saving the chunk for. */
    private static final float CHANGE_TO_SAVE = 1e-3f;

    private final DustStore dust = new DustStore();
    private final FluidTank tank = new FluidTank(TowerCondensation.MERCURY_TANK_MILLIBUCKETS, stack -> stack.getFluid() == Metallurgy.MERCURY.get())
    {
        @Override
        protected void onContentsChanged()
        {
            setChanged();
        }
    };
    /** Mercury taken out of the gas and not yet counted as a whole millibucket. */
    private Remainder carry = new Remainder();
    private final IFluidHandler outlet = new Outlet();

    public CondensationTowerBlockEntity(BlockPos pos, BlockState state)
    {
        super(Metallurgy.CONDENSATION_TOWER_ENTITY.get(), pos, state);
    }

    public DustStore dust()
    {
        return dust;
    }

    public FluidTank tank()
    {
        return tank;
    }

    /** @return the handler of the mercury tank, which can only be drained */
    public IFluidHandler outlet()
    {
        return outlet;
    }

    /** @return mercury taken out of the gas and not yet counted as a whole millibucket */
    public float pendingMillibuckets()
    {
        return (float) carry.pending();
    }

    /**
     * Takes from the gas of the network what it gives up at the temperature it has now. Runs once in each step of the network.
     *
     * @param seconds the length of the step
     */
    public void catchFrom(PipeNetwork network, float seconds)
    {
        final PipeContents contents = network.contents();
        final float kelvin = contents.temperature;
        boolean marked = false;

        if (TowerCondensation.catchesDust(kelvin))
        {
            for (Gas gas : DustStore.GASES)
            {
                final int index = gas.ordinal();
                final float caught = TowerCondensation.dust(contents.gases[index], seconds);
                if (caught > 0f)
                {
                    contents.gases[index] -= caught;
                    dust.add(gas, caught);
                    marked |= caught > CHANGE_TO_SAVE;
                }
            }
        }

        if (TowerCondensation.catchesMercury(kelvin))
        {
            final int index = Gas.MERCURY_VAPOR.ordinal();
            final float moles = TowerCondensation.mercury(contents.gases[index], seconds, (float) carry.pending(), tank.getCapacity() - tank.getFluidAmount(), MOLES_PER_MILLIBUCKET);
            if (moles > 0f)
            {
                contents.gases[index] -= moles;
                carry.add(moles / MOLES_PER_MILLIBUCKET);
                final int whole = carry.whole();
                if (whole > 0)
                {
                    carry.take(tank.fill(new FluidStack(Metallurgy.MERCURY.get(), whole), IFluidHandler.FluidAction.EXECUTE)); // Marks the chunk itself
                }
                marked |= moles > CHANGE_TO_SAVE;
            }
        }

        if (marked)
        {
            setChanged();
        }
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
            player.displayClientMessage(Component.translatable("tfcpp.condensation_tower.nothing"), true);
        }
    }

    /** @return {@code true} if holding this item means to use it on the tower, and not to place it against the block */
    public boolean canUse(ItemStack stack)
    {
        return stack.is(Metallurgy.GLASS_BULB.get()) || stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    /** @return {@code true} if a held item did something */
    public boolean interact(ItemStack stack, Player player, InteractionHand hand)
    {
        if (stack.is(Metallurgy.GLASS_BULB.get()))
        {
            return fillBulb(stack, player);
        }
        return tapInto(stack, player, hand);
    }

    private boolean fillBulb(ItemStack stack, Player player)
    {
        if (tank.getFluidAmount() < RetortBlockEntity.MERCURY_BULB_MILLIBUCKETS)
        {
            return false;
        }
        tank.drain(RetortBlockEntity.MERCURY_BULB_MILLIBUCKETS, IFluidHandler.FluidAction.EXECUTE);
        stack.shrink(1);
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Metallurgy.MERCURY_BULB.get()));
        return true;
    }

    /** Runs the mercury into one held container. A stack is only ever filled one at a time, as its capability would fill all of them for the price of one. The liquid is cold, so unlike a retort this brings no heat to the container. */
    private boolean tapInto(ItemStack stack, Player player, InteractionHand hand)
    {
        if (tank.isEmpty())
        {
            return false;
        }
        final ItemStack single = stack.copyWithCount(1);
        final @Nullable IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        final int filled = handler == null ? 0 : handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0)
        {
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

    /** Reports what is in the tower to a person with an empty hand. */
    public void showStatus(Player player)
    {
        final @Nullable PipeNetwork network = level instanceof ServerLevel server ? PipeTracker.get(server).networkAt(worldPosition) : null;
        final long celsius = network == null ? 0L : Math.round(network.contents().temperature - 273.15f);
        player.displayClientMessage(Component.translatable("tfcpp.condensation_tower.status", celsius,
            powders(Gas.ARSENIC_TRIOXIDE), powders(Gas.ANTIMONY_TRIOXIDE), powders(Gas.ZINC_OXIDE_FUME), powders(Gas.LEAD_OXIDE_FUME), tank.getFluidAmount()), false);
        if (network == null)
        {
            player.displayClientMessage(Component.translatable("tfcpp.condensation_tower.no_network"), false);
        }
        else if (!TowerCondensation.catchesDust(network.contents().temperature))
        {
            player.displayClientMessage(Component.translatable("tfcpp.condensation_tower.too_hot", celsius, Math.round(TowerCondensation.DUST_CEILING_KELVIN - 273.15f), Math.round(TowerCondensation.MERCURY_CEILING_KELVIN - 273.15f)), false);
        }
        if (tank.getFluidAmount() >= tank.getCapacity())
        {
            player.displayClientMessage(Component.translatable("tfcpp.condensation_tower.tank_full"), false);
        }
    }

    /** @return powders of this dust that could be scraped off now, to one place of decimals */
    private String powders(Gas gas)
    {
        return String.format(Locale.ROOT, "%.1f", dust.collected(gas) / DustStore.MOLES_PER_POWDER);
    }

    /**
     * Breaking the tower open drops the whole items of powder and lets the rest blow away as dust, as for a baffle. The mercury in the tank, and the fraction of a millibucket not yet in it, runs into the ground if the tower is cold, and evaporates into the room if it is hot.
     */
    public void dropContents()
    {
        if (level == null)
        {
            return;
        }
        dust.dropContents(level, worldPosition);
        final float millibuckets = tank.getFluidAmount() + (float) carry.pending();
        if (millibuckets > 0f && level instanceof ServerLevel server)
        {
            final @Nullable PipeNetwork network = PipeTracker.get(server).networkAt(worldPosition);
            if (network != null && !TowerCondensation.catchesMercury(network.contents().temperature))
            {
                // A hot charge flashes to vapour in the room
                final float[] gases = new float[Gas.COUNT];
                gases[Gas.MERCURY_VAPOR.ordinal()] = millibuckets * MOLES_PER_MILLIBUCKET;
                GasEmission.release(server, worldPosition, gases);
            }
            else
            {
                // Cold liquid mercury, below 100 C, does not evaporate to speak of: it runs into the ground
                Pollution.soak(server, worldPosition, Metallurgy.MERCURY.get(), millibuckets);
            }
        }
        tank.setFluid(FluidStack.EMPTY);
        carry = new Remainder();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        dust.save(tag);
        tag.put("mercury", tank.writeToNBT(provider, new CompoundTag()));
        tag.putDouble("mercuryCarry", carry.pending());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        dust.load(tag);
        tank.readFromNBT(provider, tag.getCompound("mercury"));
        carry = new Remainder(tag.getDouble("mercuryCarry"));
    }

    /** The mercury tank seen from outside. Mercury runs out of the tower and is not poured back in: the tower is no vessel to fill. */
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
