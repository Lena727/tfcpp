package tfcpp.chemistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.pipes.PipeContents;
import tfcpp.pneumatics.pipes.PipeMember;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * A closed brick chamber heated from below, in which a charge of up to 64 pieces is distilled without air. The charge is locked once it has begun to change: coal that is already coking cannot be added to.
 * <p>
 * Volatile matter leaves in proportion to the time spent hot enough. Its gas goes into the pipe above the oven, or into the room if there is none, its tar and liquor condense in the oven's tanks if a condenser stands beside it, and the solid that remains is taken out when the batch ends.
 */
public final class CokeOvenBlockEntity extends BlockEntity implements IHeatConsumer
{
    public static final int MAX_CHARGE = 64;
    public static final int TAR_TANK_MILLIBUCKETS = 50, LIQUOR_TANK_MILLIBUCKETS = 200;
    private static final int STEP_TICKS = 20;
    private static final float MAX_STEP_SECONDS = 5f;
    private static final int HEAT_STABILITY_TICKS = 5;
    /** kJ per kelvin of a mole of gas, as everywhere in the pipes. */
    private static final float GAS_HEAT_CAPACITY = 0.029f;
    private static final float KELVIN = 273.15f;

    public static void serverTick(Level level, BlockPos pos, BlockState state, CokeOvenBlockEntity oven)
    {
        if (oven.heatStabilityTicks > 0)
        {
            oven.heatStabilityTicks--;
        }
        else if (oven.targetTemperature > 0)
        {
            oven.targetTemperature = HeatCapability.adjustTempTowards(oven.targetTemperature, 0);
        }
        if (oven.temperature != oven.targetTemperature)
        {
            oven.temperature = HeatCapability.adjustTempTowards(oven.temperature, oven.targetTemperature);
            oven.setChanged();
        }

        // Counted in the ticks of TFC's calendar, which stop on an empty server as the fuel of a fire does. Game time would go on cooking over a fire that burns nothing.
        final long now = Calendars.SERVER.getTicks();
        if (oven.lastStepTick < 0 || now < oven.lastStepTick)
        {
            oven.lastStepTick = now; // The first tick since loading, or a calendar that was set back
        }
        else if (now - oven.lastStepTick >= STEP_TICKS && level instanceof ServerLevel server)
        {
            final float seconds = Math.min(now - oven.lastStepTick, MAX_STEP_SECONDS * 20f) / 20f;
            oven.lastStepTick = now;
            oven.step(server, seconds);
        }
    }

    private ItemStack charge = ItemStack.EMPTY;
    private ItemStack product = ItemStack.EMPTY;
    /** Seconds of the batch that have run. */
    private double progress;
    /** Solid made by the batch so far, which stays inside until it ends. */
    private double batchSolid;
    /** Fractions of a unit that each product has left over, for the next time: of the solids, and of every liquid. */
    private final Map<String, Remainder> carry = new HashMap<>();
    private final FluidTank tarTank = new FluidTank(TAR_TANK_MILLIBUCKETS, stack -> isOf(stack, true))
    {
        @Override
        protected void onContentsChanged()
        {
            setChanged();
        }
    };
    private final FluidTank liquorTank = new FluidTank(LIQUOR_TANK_MILLIBUCKETS, stack -> isOf(stack, false))
    {
        @Override
        protected void onContentsChanged()
        {
            setChanged();
        }
    };
    private final CokeOvenTanks tanks = new CokeOvenTanks(tarTank, liquorTank);

    private float temperature;
    private float targetTemperature;
    private int heatStabilityTicks;
    private long lastStepTick = -1L;
    private boolean outputBlocked;

    public CokeOvenBlockEntity(BlockPos pos, BlockState state)
    {
        super(Chemistry.COKE_OVEN_ENTITY.get(), pos, state);
    }

    private static boolean isOf(FluidStack stack, boolean tar)
    {
        final @Nullable Liquid liquid = Chemistry.liquidOf(stack.getFluid());
        return liquid != null && liquid.isTar() == tar;
    }

    /** @return the handler of both tanks, which can only be drained */
    public CokeOvenTanks tanks()
    {
        return tanks;
    }

    public FluidTank tarTank()
    {
        return tarTank;
    }

    public FluidTank liquorTank()
    {
        return liquorTank;
    }

    public ItemStack charge()
    {
        return charge;
    }

    public ItemStack product()
    {
        return product;
    }

    public double progress()
    {
        return progress;
    }

    /** @return {@code true} once the batch has begun to change, after which nothing can be added to it */
    public boolean isLocked()
    {
        return progress > 0.0;
    }

    @Nullable
    private CarbonizationRecipe recipeFor(ItemStack stack)
    {
        assert level != null;
        final Optional<RecipeHolder<CarbonizationRecipe>> holder = level.getRecipeManager().getRecipeFor(Chemistry.CARBONIZATION_RECIPE.get(), new SingleRecipeInput(stack), level);
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

    private boolean pipeAbove()
    {
        assert level != null;
        final BlockState above = level.getBlockState(worldPosition.above());
        return above.getBlock() instanceof PipeMember member && member.connects(above, Direction.DOWN);
    }

    private Remainder remainder(String key)
    {
        return carry.computeIfAbsent(key, k -> new Remainder());
    }

    private static String key(Item item)
    {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static String key(Fluid fluid)
    {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    /**
     * Lets one stretch of time pass. This is what the oven does every second, and a game test calls it with longer stretches to get through a batch of hours.
     */
    public void step(ServerLevel level, float seconds)
    {
        outputBlocked = false;
        if (charge.isEmpty())
        {
            return;
        }
        final @Nullable CarbonizationRecipe recipe = recipeFor(charge);
        if (recipe == null || temperature < recipe.temperature())
        {
            return; // A cold charge keeps its place: nothing changes
        }

        final boolean pipe = pipeAbove();
        final @Nullable PipeNetwork network = pipe ? PipeTracker.get(level).networkAt(worldPosition) : null;
        if (pipe && network == null)
        {
            return; // The pipe above has not formed yet. Gas has to have somewhere to go.
        }

        final CarbonizationBatch batch = new CarbonizationBatch(recipe.yield(), charge.getCount(), progress);
        final boolean condensing = hasCondenser();
        final CarbonizationBatch.Step preview = batch.peek(seconds, true);
        if (condensing && (!fits(recipe.tar(), preview.tar(), tarTank) || !fits(recipe.liquor(), preview.liquor(), liquorTank)))
        {
            outputBlocked = true; // Full, or holding another liquid. The batch waits, and nothing is lost.
            return;
        }

        final CarbonizationBatch.Step step = batch.advance(seconds, true);
        progress = batch.progress();
        batchSolid += step.solid();
        if (condensing)
        {
            collect(recipe.tar(), step.tar(), tarTank);
            collect(recipe.liquor(), step.liquor(), liquorTank);
        }
        // Without a condenser the vapours of tar and liquor simply escape and are lost, and the gas is no less for it

        final float[] gases = new float[Gas.COUNT];
        for (int i = 0; i < Gas.COUNT; i++)
        {
            gases[i] = (float) step.gas()[i];
        }
        deliver(level, network, gases);

        if (batch.isComplete())
        {
            finish(recipe);
        }
        setChanged();
    }

    private boolean fits(CarbonizationRecipe.Condensate condensate, double litres, FluidTank tank)
    {
        final int whole = remainder(key(condensate.fluid())).wholeFor(litres);
        return whole <= 0 || tank.fill(new FluidStack(condensate.fluid(), whole), IFluidHandler.FluidAction.SIMULATE) == whole;
    }

    private void collect(CarbonizationRecipe.Condensate condensate, double litres, FluidTank tank)
    {
        final Remainder remainder = remainder(key(condensate.fluid()));
        remainder.add(litres);
        final int whole = remainder.whole();
        if (whole > 0)
        {
            remainder.take(tank.fill(new FluidStack(condensate.fluid(), whole), IFluidHandler.FluidAction.EXECUTE));
        }
    }

    /** Hot gas goes into the pipe above with its heat, or into the room above the oven if no pipe stands there. */
    private void deliver(ServerLevel level, @Nullable PipeNetwork network, float[] gases)
    {
        if (network == null)
        {
            GasEmission.release(level, worldPosition.above(), gases);
            return;
        }
        final PipeContents contents = network.contents();
        float moles = 0f;
        for (int i = 0; i < Gas.COUNT; i++)
        {
            contents.gases[i] += gases[i];
            moles += gases[i];
        }
        contents.mixHeat(moles * GAS_HEAT_CAPACITY, temperature + KELVIN);
    }

    private void finish(CarbonizationRecipe recipe)
    {
        final Remainder remainder = remainder(key(recipe.solid().item()));
        remainder.add(batchSolid);
        final int whole = remainder.whole();
        remainder.take(whole);
        product = new ItemStack(recipe.solid().item(), whole);
        charge = ItemStack.EMPTY;
        progress = 0.0;
        batchSolid = 0.0;
    }

    /** @return {@code true} if holding this item means to use it on the oven, and not to place it against the block */
    public boolean canUse(ItemStack stack)
    {
        return recipeFor(stack) != null || stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    /** @return {@code true} if a held item did something */
    public boolean interact(ItemStack stack, Player player, InteractionHand hand)
    {
        if (tapInto(stack, player, hand))
        {
            return true;
        }
        return recipeFor(stack) != null && load(stack, player);
    }

    private boolean load(ItemStack stack, Player player)
    {
        if (isLocked())
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.locked"), true);
            return true;
        }
        if (!product.isEmpty())
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.take_first"), true);
            return true;
        }
        if (!charge.isEmpty() && !ItemStack.isSameItem(charge, stack))
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.one_kind"), true);
            return true;
        }
        final int room = MAX_CHARGE - charge.getCount();
        if (room <= 0)
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.full"), true);
            return true;
        }
        final int moved = Math.min(room, stack.getCount());
        if (charge.isEmpty())
        {
            charge = stack.copyWithCount(moved);
        }
        else
        {
            charge.grow(moved);
        }
        stack.shrink(moved);
        setChanged();
        player.displayClientMessage(Component.translatable("tfcpp.coke_oven.loaded", charge.getCount(), charge.getHoverName()), true);
        if (!hasCondenser())
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.no_condenser"), true);
        }
        return true;
    }

    /** Loads a charge directly, without a player. */
    public void load(ItemStack stack)
    {
        charge = stack;
        progress = 0.0;
        batchSolid = 0.0;
        setChanged();
    }

    /** Runs condensed liquid into one held container, a single container at a time, as a stack's capability would fill all of them for the price of one. */
    private boolean tapInto(ItemStack stack, Player player, InteractionHand hand)
    {
        if (stack.getCapability(Capabilities.FluidHandler.ITEM) == null)
        {
            return false;
        }
        for (FluidTank tank : new FluidTank[] {tarTank, liquorTank})
        {
            if (tank.isEmpty())
            {
                continue;
            }
            final ItemStack single = stack.copyWithCount(1);
            final @Nullable IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
            final int filled = handler == null ? 0 : handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
            if (filled <= 0)
            {
                continue;
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
        return false;
    }

    /** Takes out what the last batch left. */
    public void takeProduct(Player player)
    {
        if (product.isEmpty())
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.nothing"), true);
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(player, product);
        product = ItemStack.EMPTY;
        setChanged();
    }

    public void showStatus(Player player)
    {
        final Component contents = charge.isEmpty()
            ? Component.translatable("tfcpp.coke_oven.status.empty")
            : Component.empty().append(charge.getCount() + " x ").append(charge.getHoverName());
        final @Nullable CarbonizationRecipe recipe = charge.isEmpty() ? null : recipeFor(charge);
        final long percent = recipe == null ? 0L : Math.round(100.0 * progress / recipe.seconds());
        player.displayClientMessage(Component.translatable("tfcpp.coke_oven.status", Math.round(temperature), contents, percent, tarTank.getFluidAmount(), liquorTank.getFluidAmount()), false);
        if (recipe != null && temperature < recipe.temperature())
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.too_cold", Math.round(recipe.temperature())), false);
        }
        if (outputBlocked)
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.tanks_full"), false);
        }
        if (!product.isEmpty())
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.ready", product.getCount(), product.getHoverName()), false);
        }
        if (!hasCondenser())
        {
            player.displayClientMessage(Component.translatable("tfcpp.coke_oven.no_condenser"), false);
        }
    }

    /**
     * Breaking an oven open gives back the part of the charge that has not yet reacted, and what has coked so far. Tar and liquor run away. The gas that is in the oven's pipe comes out when the oven goes out of its network, as for any pipe.
     */
    public void dropContents()
    {
        assert level != null;
        final double x = worldPosition.getX(), y = worldPosition.getY(), z = worldPosition.getZ();
        if (!charge.isEmpty())
        {
            final @Nullable CarbonizationRecipe recipe = recipeFor(charge);
            if (recipe == null)
            {
                Containers.dropItemStack(level, x, y, z, charge);
            }
            else
            {
                final int unreacted = (int) Math.floor(charge.getCount() * (1.0 - progress / recipe.seconds()) + 1e-4);
                Containers.dropItemStack(level, x, y, z, charge.copyWithCount(unreacted));
                final Remainder remainder = carry.get(key(recipe.solid().item()));
                final double coked = batchSolid + (remainder == null ? 0.0 : remainder.pending());
                Containers.dropItemStack(level, x, y, z, new ItemStack(recipe.solid().item(), (int) Math.floor(coked + 1e-4)));
            }
        }
        Containers.dropItemStack(level, x, y, z, product);
    }

    @Override
    public float getTemperature()
    {
        return temperature;
    }

    @Override
    public void setTemperature(float temperature)
    {
        // Devices report their temperature every tick, even when cold. Only something hotter than the oven can heat it.
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
        tag.put("charge", charge.saveOptional(provider));
        tag.put("product", product.saveOptional(provider));
        tag.putDouble("progress", progress);
        tag.putDouble("batchSolid", batchSolid);
        tag.put("tar", tarTank.writeToNBT(provider, new CompoundTag()));
        tag.put("liquor", liquorTank.writeToNBT(provider, new CompoundTag()));
        final CompoundTag carried = new CompoundTag();
        carry.forEach((key, remainder) -> carried.putDouble(key, remainder.pending()));
        tag.put("carry", carried);
        tag.putFloat("temperature", temperature);
        tag.putFloat("targetTemperature", targetTemperature);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        charge = ItemStack.parseOptional(provider, tag.getCompound("charge"));
        product = ItemStack.parseOptional(provider, tag.getCompound("product"));
        progress = tag.getDouble("progress");
        batchSolid = tag.getDouble("batchSolid");
        tarTank.readFromNBT(provider, tag.getCompound("tar"));
        liquorTank.readFromNBT(provider, tag.getCompound("liquor"));
        carry.clear();
        final CompoundTag carried = tag.getCompound("carry");
        for (String key : carried.getAllKeys())
        {
            carry.put(key, new Remainder(carried.getDouble(key)));
        }
        temperature = tag.getFloat("temperature");
        targetTemperature = tag.getFloat("targetTemperature");
    }
}
