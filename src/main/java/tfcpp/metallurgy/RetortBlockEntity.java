package tfcpp.metallurgy;

import java.util.Optional;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.component.heat.IHeat;
import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
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
import tfcpp.pneumatics.gas.GasEmission;

/**
 * A closed fire-clay vessel set over a forge or firepit, which takes their heat from below.
 * <p>
 * Zinc is the reason it exists: carbon only reduces ZnO above about 1000 C, and zinc boils at 907 C, so it leaves as vapour. In an open crucible it burns to white smoke. Here the vapour goes into a condenser and runs off as a liquid. The same goes for mercury, boiling at 357 C.
 */
public final class RetortBlockEntity extends BlockEntity implements IHeatConsumer
{
    private static final int SLOTS = 8;
    private static final int REACTION_INTERVAL_TICKS = 20;
    private static final int HEAT_STABILITY_TICKS = 5;
    private static final int TANK_MILLIBUCKETS = 1000;
    /** Moles of carbon in a 3 kg piece of charcoal, on the same scale as the rest of the mod. */
    private static final float CARBON_PER_CHARCOAL = 200f;
    private static final int MERCURY_BULB_MILLIBUCKETS = 5;
    private static final int MILLIBUCKETS_PER_INGOT = 100;

    public static void serverTick(Level level, BlockPos pos, BlockState state, RetortBlockEntity retort)
    {
        if (retort.heatStabilityTicks > 0)
        {
            retort.heatStabilityTicks--;
        }
        else if (retort.targetTemperature > 0)
        {
            retort.targetTemperature = HeatCapability.adjustTempTowards(retort.targetTemperature, 0);
        }
        if (retort.temperature != retort.targetTemperature)
        {
            retort.temperature = HeatCapability.adjustTempTowards(retort.temperature, retort.targetTemperature);
            retort.setChanged();
        }

        if (level.getGameTime() % REACTION_INTERVAL_TICKS == 0 && level instanceof ServerLevel server)
        {
            retort.react(server);
        }
    }

    private final NonNullList<ItemStack> charge = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final FluidTank tank = new FluidTank(TANK_MILLIBUCKETS)
    {
        @Override
        protected void onContentsChanged()
        {
            setChanged();
        }
    };

    private float temperature;
    private float targetTemperature;
    private int heatStabilityTicks;
    private float carbon;

    public RetortBlockEntity(BlockPos pos, BlockState state)
    {
        super(Metallurgy.RETORT_ENTITY.get(), pos, state);
    }

    private void react(ServerLevel level)
    {
        for (int slot = 0; slot < SLOTS; slot++)
        {
            final ItemStack stack = charge.get(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            final @Nullable RetortRecipe recipe = recipeFor(stack);
            if (recipe != null && temperature >= recipe.temperature() && carbon >= recipe.carbon())
            {
                final boolean condensing = hasCondenser();
                if (condensing && tank.fill(recipe.result(), IFluidHandler.FluidAction.SIMULATE) < recipe.result().getAmount())
                {
                    continue; // Full, or holding a different liquid. The charge just waits, and nothing is wasted.
                }

                final float[] gases = new float[Gas.COUNT];
                RetortRecipe.addTo(gases, recipe.byproducts());
                if (condensing)
                {
                    tank.fill(recipe.result(), IFluidHandler.FluidAction.EXECUTE);
                }
                else
                {
                    RetortRecipe.addTo(gases, recipe.vapor());
                }
                GasEmission.release(level, worldPosition.above(), gases);

                carbon -= recipe.carbon();
                stack.shrink(1);
                setChanged();
                return; // One piece of charge per reaction
            }
        }
    }

    @Nullable
    private RetortRecipe recipeFor(ItemStack stack)
    {
        assert level != null;
        final Optional<RecipeHolder<RetortRecipe>> holder = level.getRecipeManager().getRecipeFor(Metallurgy.RETORT_RECIPE.get(), new SingleRecipeInput(stack), level);
        return holder.map(RecipeHolder::value).orElse(null);
    }

    public boolean hasCondenser()
    {
        assert level != null;
        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            if (level.getBlockState(worldPosition.relative(direction)).is(Metallurgy.RETORT_CONDENSER.get()))
            {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} if holding this item means to use it on the retort, and not to place it against the block */
    public boolean canUse(ItemStack stack)
    {
        return stack.is(Items.CHARCOAL)
            || stack.is(Metallurgy.GLASS_BULB.get())
            || stack.getCapability(Capabilities.FluidHandler.ITEM) != null
            || recipeFor(stack) != null;
    }

    /** @return {@code true} if a held item did something */
    public boolean interact(ItemStack stack, Player player, InteractionHand hand)
    {
        if (stack.is(Metallurgy.GLASS_BULB.get()))
        {
            return fillBulb(stack, player);
        }
        if (tapInto(stack, player, hand))
        {
            return true;
        }
        if (stack.is(Items.CHARCOAL))
        {
            carbon += CARBON_PER_CHARCOAL;
            stack.shrink(1);
            setChanged();
            return true;
        }
        if (recipeFor(stack) != null)
        {
            for (int slot = 0; slot < SLOTS; slot++)
            {
                if (charge.get(slot).isEmpty())
                {
                    charge.set(slot, stack.split(1));
                    setChanged();
                    if (!hasCondenser())
                    {
                        player.displayClientMessage(Component.translatable("tfcpp.retort.no_condenser"), true);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean fillBulb(ItemStack stack, Player player)
    {
        if (tank.getFluid().getFluid() != Metallurgy.MERCURY.get() || tank.getFluidAmount() < MERCURY_BULB_MILLIBUCKETS)
        {
            return false;
        }
        tank.drain(MERCURY_BULB_MILLIBUCKETS, IFluidHandler.FluidAction.EXECUTE);
        stack.shrink(1);
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Metallurgy.MERCURY_BULB.get()));
        return true;
    }

    /** Runs condensed liquid into one held mould or container, along with its heat. A stack is only ever filled one at a time, as its capability would fill all of them for the price of one. */
    private boolean tapInto(ItemStack stack, Player player, InteractionHand hand)
    {
        if (tank.isEmpty() || stack.getCapability(Capabilities.FluidHandler.ITEM) == null)
        {
            return false;
        }

        final ItemStack single = stack.copyWithCount(1);
        final IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        final FluidStack available = tank.getFluid().copy();
        final int filled = handler == null ? 0 : handler.fill(available, IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0)
        {
            return false;
        }
        tank.drain(filled, IFluidHandler.FluidAction.EXECUTE);

        // Heating a vessel can melt what is inside it, which counts as happening here
        assert level != null;
        MeltContext.enter(level, worldPosition);
        final @Nullable IHeat heat = HeatCapability.get(single);
        if (heat != null)
        {
            heat.addTemperatureFromSourceWithHeatCapacity(temperature, FluidHeat.getOrUnknown(available).heatCapacity(filled));
        }
        MeltContext.exit();

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

    public void showStatus(Player player)
    {
        int count = 0;
        for (ItemStack stack : charge)
        {
            count += stack.getCount();
        }
        final Component contents = tank.isEmpty() ? Component.translatable("tfcpp.retort.status.empty_tank") : tank.getFluid().getHoverName();
        player.displayClientMessage(Component.translatable("tfcpp.retort.status", Math.round(temperature), count, Math.round(carbon), tank.getFluidAmount(), contents), false);
        if (!hasCondenser())
        {
            player.displayClientMessage(Component.translatable("tfcpp.retort.no_condenser"), false);
        }
    }

    /** Breaking a retort open spills it. Unused charcoal survives, mercury evaporates into the room, and zinc sets into ingots, with the last of it burning off as smoke. */
    public void dropContents()
    {
        assert level != null;
        final double x = worldPosition.getX(), y = worldPosition.getY(), z = worldPosition.getZ();
        for (ItemStack stack : charge)
        {
            Containers.dropItemStack(level, x, y, z, stack);
        }
        Containers.dropItemStack(level, x, y, z, new ItemStack(Items.CHARCOAL, (int) (carbon / CARBON_PER_CHARCOAL)));

        if (!tank.isEmpty() && level instanceof ServerLevel server)
        {
            final float[] gases = new float[Gas.COUNT];
            int millibuckets = tank.getFluidAmount();
            if (tank.getFluid().getFluid() == Metallurgy.MERCURY.get())
            {
                gases[Gas.MERCURY_VAPOR.ordinal()] = Sulfide.CINNABAR.metalMoles(millibuckets);
            }
            else
            {
                final int ingots = millibuckets / MILLIBUCKETS_PER_INGOT;
                Containers.dropItemStack(level, x, y, z, new ItemStack(TFCItems.METAL_ITEMS.get(Metal.ZINC).get(Metal.ItemType.INGOT).get(), ingots));
                gases[Gas.ZINC_OXIDE_FUME.ordinal()] = Sulfide.SPHALERITE.metalMoles(millibuckets - ingots * MILLIBUCKETS_PER_INGOT);
            }
            GasEmission.release(server, worldPosition, gases);
        }
    }

    public FluidTank tank()
    {
        return tank;
    }

    /** Loads a retort directly, without a player. */
    public void load(ItemStack stack, float carbonMoles)
    {
        charge.set(0, stack);
        carbon = carbonMoles;
    }

    @Override
    public float getTemperature()
    {
        return temperature;
    }

    @Override
    public void setTemperature(float temperature)
    {
        // Devices report their temperature every tick, even when cold. Only something hotter than the retort can heat it.
        if (temperature >= this.temperature)
        {
            targetTemperature = temperature;
            heatStabilityTicks = HEAT_STABILITY_TICKS;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        ContainerHelper.saveAllItems(tag, charge, provider);
        tag.put("tank", tank.writeToNBT(provider, new CompoundTag()));
        tag.putFloat("temperature", temperature);
        tag.putFloat("targetTemperature", targetTemperature);
        tag.putFloat("carbon", carbon);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        ContainerHelper.loadAllItems(tag, charge, provider);
        tank.readFromNBT(provider, tag.getCompound("tank"));
        temperature = tag.getFloat("temperature");
        targetTemperature = tag.getFloat("targetTemperature");
        carbon = tag.getFloat("carbon");
    }
}
