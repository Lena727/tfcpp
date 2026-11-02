package tfcpp.metallurgy;

import java.util.Locale;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.component.heat.IHeat;
import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Combustion;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.instruments.Instruments;

/**
 * A fire-brick hearth over a fire, floored with bone ash. Work lead is poured in, an air blast oxidises it to litharge, and what is left at the end is the silver.
 * <p>
 * The physics is in {@link CupellationBath}. This holds the bath, takes its heat from the fire below, counts its time in the calendar, and deals with what the blast does to the room: the lead fume, the litharge raked off the floor, and the blick at the end.
 */
public final class CupelHearthBlockEntity extends BlockEntity implements IHeatConsumer
{
    private static final int HEAT_STABILITY_TICKS = 5;
    private static final int STEP_TICKS = 20;
    /** The longest stretch of blast in one step. A calendar set forward more than this drops the rest, as the coke oven does. */
    private static final float MAX_STEP_SECONDS = 5f;
    private static final int MILLIBUCKETS_PER_INGOT = 100;
    private static final int BLICK_RANGE = 16;
    private static final int BLICK_PARTICLES = 12;

    public static void serverTick(Level level, BlockPos pos, BlockState state, CupelHearthBlockEntity hearth)
    {
        final int degreesBefore = Math.round(hearth.temperature), targetBefore = Math.round(hearth.targetTemperature);
        if (hearth.heatStabilityTicks > 0)
        {
            hearth.heatStabilityTicks--;
        }
        else if (hearth.targetTemperature > 0)
        {
            hearth.targetTemperature = HeatCapability.adjustTempTowards(hearth.targetTemperature, 0);
        }
        if (hearth.temperature != hearth.targetTemperature)
        {
            hearth.temperature = HeatCapability.adjustTempTowards(hearth.temperature, hearth.targetTemperature);
        }
        // The heat moves every tick while a fire is under it, so the chunk is marked only when a whole degree has changed
        if (Math.round(hearth.temperature) != degreesBefore || Math.round(hearth.targetTemperature) != targetBefore)
        {
            hearth.setChanged();
        }

        // Counted in the ticks of TFC's calendar, which stop on an empty server as the fuel of a fire does. Game time would blow over a fire that burns nothing.
        if (level instanceof ServerLevel server)
        {
            hearth.tickClock(server, Calendars.SERVER.getTicks());
        }
    }

    private final CupellationBath bath = new CupellationBath();
    private final IFluidHandler tank = new Tank();

    private float temperature;
    private float targetTemperature;
    private int heatStabilityTicks;
    /** Calendar tick of the last step. Not saved, as the coke oven's is not: the first tick after a load starts the count again. */
    private long lastStepTick = -1L;
    /** The blick has been seen for the lead now in the hearth. It is not seen again until new lead goes in. */
    private boolean blicked;

    public CupelHearthBlockEntity(BlockPos pos, BlockState state)
    {
        super(Metallurgy.CUPEL_HEARTH_ENTITY.get(), pos, state);
    }

    public CupellationBath bath()
    {
        return bath;
    }

    public IFluidHandler tank()
    {
        return tank;
    }

    /** @return {@code true} once the blick has been seen for the lead in the hearth */
    public boolean blicked()
    {
        return blicked;
    }

    /**
     * Counts the calendar, and blows one step for each 20 ticks that have passed. A first tick, or a calendar that has been set back, only starts the count again.
     *
     * @param now the calendar tick now
     */
    public void tickClock(ServerLevel level, long now)
    {
        if (lastStepTick < 0 || now < lastStepTick)
        {
            lastStepTick = now;
        }
        else if (now - lastStepTick >= STEP_TICKS)
        {
            final float seconds = (float) Math.min(now - lastStepTick, MAX_STEP_SECONDS * STEP_TICKS) / STEP_TICKS;
            lastStepTick = now;
            step(level, seconds);
        }
    }

    /**
     * One stretch of blast. Nothing happens if the air over the hearth has too little oxygen to oxidise the lead, as the blast needs the air.
     *
     * @param seconds time of the blast
     */
    public void step(ServerLevel level, float seconds)
    {
        if (poorAir(level))
        {
            return;
        }
        final CupellationBath.Step step = bath.step(seconds, temperature);
        if (step.leadOxidised() <= 0f)
        {
            return;
        }

        // The lead fume is what kills people here, so it goes into the room. The nitrogen of the blast stays behind as flue gas.
        final float[] gases = new float[Gas.COUNT];
        gases[Gas.LEAD_OXIDE_FUME.ordinal()] = step.fumeMoles();
        gases[Gas.FLUE_NITROGEN.ordinal()] = step.oxygenMoles() * Combustion.NITROGEN_PER_OXYGEN;
        GasEmission.release(level, worldPosition.above(), gases);

        if (step.litharge() > 0)
        {
            Helpers.spawnItem(level, worldPosition.above(), new ItemStack(Instruments.LEAD_OXIDE.get(), step.litharge()));
        }
        // Only a step that oxidised lead can finish the bath, so this is the moment the last film clears
        if (!blicked && bath.isFinished())
        {
            blick(level);
        }
        setChanged();
    }

    /** The last litharge has cleared and the silver flashes. Seen once for each charge of lead. */
    private void blick(ServerLevel level)
    {
        blicked = true;
        final double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.1, z = worldPosition.getZ() + 0.5;
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1f);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, BLICK_PARTICLES, 0.2, 0.1, 0.2, 0.05);
        final Component message = Component.translatable("tfcpp.cupel_hearth.blick");
        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(x, y, z) <= BLICK_RANGE * BLICK_RANGE)
            {
                player.displayClientMessage(message, false);
            }
        }
    }

    /** @return {@code true} when the air in the cell over the hearth is too poor in oxygen to burn anything. Air not yet in the grid is plain air. */
    private boolean poorAir(ServerLevel level)
    {
        final BlockPos mouth = worldPosition.above();
        final @Nullable float[] air = GasTracker.get(level).volume(level).cell(mouth.getX(), mouth.getY(), mouth.getZ());
        return air != null && GasMath.oxygenFraction(air) < Combustion.EXTINCTION_OXYGEN;
    }

    /** @return {@code true} if holding this item means to use it on the hearth, and not to place it against the block */
    public static boolean canUse(ItemStack stack)
    {
        return stack.is(Metallurgy.BONE_ASH.get()) || stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    /** Lays ash on the floor, or pours from or into a held container. */
    public void interact(ItemStack stack, Player player, InteractionHand hand)
    {
        if (stack.is(Metallurgy.BONE_ASH.get()))
        {
            bath.addAsh();
            stack.shrink(1);
            setChanged();
            return;
        }
        final ItemStack single = stack.copyWithCount(1);
        final @Nullable IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null)
        {
            return;
        }
        // A run gives only about 3 mB, so a mould that already holds silver has to be able to take more
        if (handler.getFluidInTank(0).isEmpty() || handler.getFluidInTank(0).getFluid() == silver())
        {
            tapInto(stack, single, handler, player, hand);
        }
        else
        {
            pourIn(stack, handler, player, hand);
        }
    }

    /** Pours work lead from a held container, in whole millibuckets. Plain lead has no silver left to take out, so it is refused: otherwise it would make free silver. */
    private void pourIn(ItemStack stack, IFluidHandlerItem handler, Player player, InteractionHand hand)
    {
        final FluidStack held = handler.getFluidInTank(0);
        if (held.getFluid() == Metallurgy.LEAD.get())
        {
            player.displayClientMessage(Component.translatable("tfcpp.cupel_hearth.plain_lead"), true);
            return;
        }
        if (held.getFluid() != Metallurgy.WORK_LEAD.get())
        {
            player.displayClientMessage(Component.translatable("tfcpp.cupel_hearth.wrong_fluid"), true);
            return;
        }
        final int room = (int) Math.max(CupellationBath.CAPACITY_MILLIBUCKETS - bath.lead, 0f);
        if (room <= 0)
        {
            player.displayClientMessage(Component.translatable("tfcpp.cupel_hearth.full"), true);
            return;
        }
        // The room is the one the bath takes, so everything drained goes in
        final FluidStack poured = handler.drain(room, IFluidHandler.FluidAction.EXECUTE);
        if (poured.isEmpty())
        {
            return;
        }
        bath.add(poured.getAmount());
        blicked = false; // The blick belongs to the lead in the hearth, so new lead makes a new one
        setChanged();
        handBack(stack, player, hand, handler.getContainer());
    }

    /**
     * Fills one held empty mould with silver, in whole millibuckets, once the lead is all oxidised. The mould is heated to the hearth's temperature.
     */
    private void tapInto(ItemStack stack, ItemStack single, IFluidHandlerItem handler, Player player, InteractionHand hand)
    {
        if (bath.lead > 0f)
        {
            player.displayClientMessage(Component.translatable("tfcpp.cupel_hearth.lead_left"), true);
            return;
        }
        if (bath.silver < 1f)
        {
            player.displayClientMessage(Component.translatable("tfcpp.cupel_hearth.no_silver"), true);
            return;
        }
        final Fluid silver = silver();
        final int filled = handler.fill(new FluidStack(silver, (int) bath.silver), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0)
        {
            return;
        }
        final float at = temperature;
        bath.silver -= filled; // Only the whole millibuckets that went in leave the hearth, and the fraction stays
        setChanged();

        assert level != null;
        MeltContext.enter(level, worldPosition);
        final @Nullable IHeat heat = HeatCapability.get(single);
        if (heat != null)
        {
            heat.addTemperatureFromSourceWithHeatCapacity(at, FluidHeat.getOrUnknown(new FluidStack(silver, filled)).heatCapacity(filled));
        }
        MeltContext.exit();

        handBack(stack, player, hand, handler.getContainer());
    }

    private static Fluid silver()
    {
        return TFCFluids.METALS.get(Metal.SILVER).getSource();
    }

    private static void handBack(ItemStack stack, Player player, InteractionHand hand, ItemStack result)
    {
        if (stack.getCount() == 1)
        {
            player.setItemInHand(hand, result);
        }
        else
        {
            stack.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, result);
        }
    }

    /** Reports the hearth to a person with an empty hand. */
    public void showStatus(Player player)
    {
        player.displayClientMessage(Component.translatable("tfcpp.cupel_hearth.status", Math.round(temperature), fixed(bath.lead), fixed(bath.silver), fixed(Math.max(bath.ashCapacity, 0f))), false);
        if (bath.needsAsh())
        {
            player.displayClientMessage(Component.translatable("tfcpp.cupel_hearth.needs_ash"), false);
        }
        if (bath.lead > 0f && temperature < CupellationBath.LEAST_TEMPERATURE)
        {
            player.displayClientMessage(Component.translatable("tfcpp.cupel_hearth.too_cold", Math.round(CupellationBath.LEAST_TEMPERATURE)), false);
        }
        if (bath.lead > 0f && level instanceof ServerLevel server && poorAir(server))
        {
            player.displayClientMessage(Component.translatable("tfcpp.cupel_hearth.poor_air"), false);
        }
    }

    private static String fixed(float value)
    {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Breaking the hearth spills it. Work lead sets into ingots, and the part short of an ingot burns off as litharge smoke. Silver sets into ingots, and what is short of an ingot stays spattered on the ruin and is lost. Ash is lost.
     */
    public void dropContents()
    {
        assert level != null;
        final double x = worldPosition.getX(), y = worldPosition.getY(), z = worldPosition.getZ();

        final int leadIngots = (int) (bath.lead / MILLIBUCKETS_PER_INGOT);
        if (leadIngots > 0)
        {
            Containers.dropItemStack(level, x, y, z, new ItemStack(Metallurgy.WORK_LEAD_INGOT.get(), leadIngots));
        }
        final float[] gases = new float[Gas.COUNT];
        gases[Gas.LEAD_OXIDE_FUME.ordinal()] = (bath.lead - leadIngots * MILLIBUCKETS_PER_INGOT) * CupellationBath.MOLES_LEAD_PER_MILLIBUCKET;

        // Silver stays in whole ingots only. The part short of one is lost with the ruin, as its spatter cannot be gathered.
        final int silverIngots = (int) (bath.silver / MILLIBUCKETS_PER_INGOT);
        if (silverIngots > 0)
        {
            Containers.dropItemStack(level, x, y, z, new ItemStack(TFCItems.METAL_ITEMS.get(Metal.SILVER).get(Metal.ItemType.INGOT).get(), silverIngots));
        }

        if (gases[Gas.LEAD_OXIDE_FUME.ordinal()] > 0f && level instanceof ServerLevel server)
        {
            GasEmission.release(server, worldPosition, gases);
        }
    }

    @Override
    public float getTemperature()
    {
        return temperature;
    }

    /** Devices report their temperature every tick, even when cold. Only something hotter than the hearth can heat it. */
    @Override
    public void setTemperature(float temperature)
    {
        if (temperature >= this.temperature)
        {
            if (Math.round(temperature) != Math.round(targetTemperature))
            {
                setChanged();
            }
            targetTemperature = temperature;
            heatStabilityTicks = HEAT_STABILITY_TICKS;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        tag.putFloat("lead", bath.lead);
        tag.putFloat("silver", bath.silver);
        tag.putFloat("ashCapacity", bath.ashCapacity);
        tag.putFloat("oxidisedForLitharge", bath.oxidisedForLitharge);
        tag.putFloat("temperature", temperature);
        tag.putFloat("targetTemperature", targetTemperature);
        tag.putBoolean("blicked", blicked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        bath.lead = tag.getFloat("lead");
        bath.silver = tag.getFloat("silver");
        bath.ashCapacity = tag.getFloat("ashCapacity");
        bath.oxidisedForLitharge = tag.getFloat("oxidisedForLitharge");
        temperature = tag.getFloat("temperature");
        targetTemperature = tag.getFloat("targetTemperature");
        blicked = tag.getBoolean("blicked");
    }

    /**
     * Work lead comes in from above, and silver goes out of the hearth. Plain lead is refused here as it is by hand, and a tank that holds lead gives nothing out.
     */
    private final class Tank implements IFluidHandler
    {
        @Override
        public int getTanks()
        {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank)
        {
            if ((int) bath.lead > 0)
            {
                return new FluidStack(Metallurgy.WORK_LEAD.get(), (int) bath.lead);
            }
            return (int) bath.silver > 0 ? new FluidStack(silver(), (int) bath.silver) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank)
        {
            return (int) CupellationBath.CAPACITY_MILLIBUCKETS;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack)
        {
            return stack.getFluid() == Metallurgy.WORK_LEAD.get();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action)
        {
            if (resource.getFluid() != Metallurgy.WORK_LEAD.get())
            {
                return 0;
            }
            final int room = (int) Math.max(CupellationBath.CAPACITY_MILLIBUCKETS - bath.lead, 0f);
            final int amount = Math.min(resource.getAmount(), room);
            if (amount > 0 && action.execute())
            {
                bath.add(amount);
                blicked = false;
                setChanged();
            }
            return amount;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action)
        {
            return resource.getFluid() == silver() ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action)
        {
            // Silver can come out only once no lead is left to oxidise
            if (bath.lead > 0f)
            {
                return FluidStack.EMPTY;
            }
            final int amount = Math.min(maxDrain, (int) bath.silver);
            if (amount <= 0)
            {
                return FluidStack.EMPTY;
            }
            if (action.execute())
            {
                bath.silver -= amount;
                setChanged();
            }
            return new FluidStack(silver(), amount);
        }
    }
}
