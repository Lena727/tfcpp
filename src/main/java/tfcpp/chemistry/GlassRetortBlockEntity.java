package tfcpp.chemistry;

import java.util.HashMap;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.Map;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import tfcpp.ecology.Pollution;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;

/**
 * A retort and receiver of borosilicate glass, set over a sand bath. A charge of powder and one or two liquids is boiled in the flask while it stays in the working range of its recipe, and what comes over condenses in the receiver beside it, if a condenser stands against the flask. The batch is {@link Distillation}.
 * <p>
 * Glass is what lets acid be distilled at all, and glass breaks: above 550 C the flask cracks, and whatever was in it goes into the room.
 * <p>
 * The liquids of the flask are kept in float litres, since a batch uses fractions of a millibucket, and the receiver is a tank of whole millibuckets that a fraction waits to fill.
 */
public final class GlassRetortBlockEntity extends BlockEntity implements IHeatConsumer
{
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int FLASK_MILLIBUCKETS = 25, RECEIVER_MILLIBUCKETS = 25, WATER_MILLIBUCKETS = 5, STACK_LIMIT = 64;
    /** The range of search for a person to tell that a flask has cracked, in blocks. */
    private static final double CRACK_HEARD = 12.0;
    private static final int STEP_TICKS = 20;
    private static final float MAX_STEP_SECONDS = 5f;
    private static final int HEAT_STABILITY_TICKS = 5;
    private static final double EPSILON = 1e-4;

    public static void serverTick(Level level, BlockPos pos, BlockState state, GlassRetortBlockEntity retort)
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

        if (!(level instanceof ServerLevel server))
        {
            return;
        }
        if (Distillation.cracks(retort.temperature))
        {
            retort.crack(server);
            return;
        }

        // Counted in the ticks of TFC's calendar, which stop on an empty server as the fuel of a fire does
        final long now = Calendars.SERVER.getTicks();
        if (retort.lastStepTick < 0 || now < retort.lastStepTick)
        {
            retort.lastStepTick = now; // The first tick since loading, or a calendar that was set back
        }
        else if (now - retort.lastStepTick >= STEP_TICKS)
        {
            final float seconds = Math.min(now - retort.lastStepTick, MAX_STEP_SECONDS * 20f) / 20f;
            retort.lastStepTick = now;
            retort.step(server, seconds);
        }
    }

    private final FloatTank input = new FloatTank(FLASK_MILLIBUCKETS, this::acceptsLiquid, this::setChanged);
    private final FloatTank reagent = new FloatTank(FLASK_MILLIBUCKETS, this::acceptsLiquid, this::setChanged);
    private final FloatTank water = new FloatTank(WATER_MILLIBUCKETS, fluid -> Helpers.isFluid(fluid, TFCTags.Fluids.ANY_FRESH_WATER), this::setChanged);
    private final FluidTank receiver = new FluidTank(RECEIVER_MILLIBUCKETS)
    {
        @Override
        protected void onContentsChanged()
        {
            setChanged();
        }
    };
    private final FluidTank residue = new FluidTank(FLASK_MILLIBUCKETS)
    {
        @Override
        protected void onContentsChanged()
        {
            setChanged();
        }
    };
    /** Fractions of a millibucket that each product has left over, for the next time. */
    private final Map<String, Remainder> carry = new HashMap<>();
    private final IFluidHandler above = new Pour();
    private final IFluidHandler beside = new Side();
    private final IFluidHandler below = new Draw(false);
    private final IFluidHandler whole = new Draw(true);

    private ItemStack charge = ItemStack.EMPTY;
    private ItemStack product = ItemStack.EMPTY;

    /** The recipe of the batch that is running, or {@code null} while none is. */
    @Nullable
    private ResourceLocation batchRecipe;
    private double progress;
    private boolean active;
    /** {@code true} if the running batch had no receiver water to take up its hydrogen chloride, or no receiver to take it to. */
    private boolean dry;

    private float temperature;
    private float targetTemperature;
    private int heatStabilityTicks;
    private long lastStepTick = -1L;
    private Distillation.Reason lastReason = Distillation.Reason.NONE;
    private boolean noRecipe;

    public GlassRetortBlockEntity(BlockPos pos, BlockState state)
    {
        super(Chemistry.GLASS_RETORT_ENTITY.get(), pos, state);
    }

    private boolean acceptsLiquid(Fluid fluid)
    {
        return level == null || DistillationRecipe.takesLiquid(level, fluid);
    }

    public FloatTank input()
    {
        return input;
    }

    public FloatTank reagent()
    {
        return reagent;
    }

    public FloatTank water()
    {
        return water;
    }

    public FluidTank receiver()
    {
        return receiver;
    }

    public FluidTank residue()
    {
        return residue;
    }

    public ItemStack charge()
    {
        return charge;
    }

    public ItemStack product()
    {
        return product;
    }

    public boolean isActive()
    {
        return active;
    }

    public double progress()
    {
        return progress;
    }

    public Distillation.Reason lastReason()
    {
        return lastReason;
    }

    /** @return the fluid handler that the capability gives on this side: it takes liquid from above, gives it from below, and on the sides it is the receiver, which takes water in and gives what has condensed */
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side)
    {
        if (side == null)
        {
            return whole;
        }
        return switch (side)
        {
            case UP -> above;
            case DOWN -> below;
            default -> beside;
        };
    }

    /** @return litres of the pending fraction that this product has not yet become a whole millibucket */
    public double pending(Fluid fluid)
    {
        final @Nullable Remainder remainder = carry.get(key(fluid));
        return remainder == null ? 0.0 : remainder.pending();
    }

    private static String key(Fluid fluid)
    {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    private Remainder remainder(Fluid fluid)
    {
        return carry.computeIfAbsent(key(fluid), k -> new Remainder());
    }

    /** @return {@code true} unless the charge is one that makes nothing to catch: concentrating acid boils off water, which nothing is meant to condense */
    private boolean needsCondenser()
    {
        if (!(level instanceof ServerLevel server))
        {
            return true;
        }
        final @Nullable RecipeHolder<DistillationRecipe> holder = currentRecipe(server);
        return holder == null || holder.value().output().isPresent();
    }

    public boolean hasCondenser()
    {
        assert level != null;
        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            if (level.getBlockState(worldPosition.relative(direction)).is(tfcpp.metallurgy.Metallurgy.RETORT_CONDENSER.get()))
            {
                return true;
            }
        }
        return false;
    }

    // --- The batch ---------------------------------------------------------------------------------------------------------------------------------------------------

    @Nullable
    private RecipeHolder<DistillationRecipe> currentRecipe(ServerLevel server)
    {
        if (active && batchRecipe != null)
        {
            final var holder = server.getRecipeManager().byKey(batchRecipe).orElse(null);
            if (holder != null && holder.value() instanceof DistillationRecipe)
            {
                @SuppressWarnings("unchecked") final RecipeHolder<DistillationRecipe> typed = (RecipeHolder<DistillationRecipe>) holder;
                return typed;
            }
            return null;
        }
        return DistillationRecipe.find(server, nullIfEmpty(input.fluid()), nullIfEmpty(reagent.fluid()), charge);
    }

    @Nullable
    private static Fluid nullIfEmpty(Fluid fluid)
    {
        return fluid == Fluids.EMPTY ? null : fluid;
    }

    private float litresOf(Fluid fluid)
    {
        return (float) (input.fluid() == fluid ? input.litres() : reagent.fluid() == fluid ? reagent.litres() : 0.0);
    }

    private FloatTank tankOf(Fluid fluid)
    {
        return input.fluid() == fluid ? input : reagent;
    }

    private double roomFor(Fluid fluid, FluidTank tank)
    {
        if (!tank.isEmpty() && tank.getFluid().getFluid() != fluid)
        {
            return 0.0; // Holding another liquid
        }
        return Math.max(0.0, tank.getCapacity() - tank.getFluidAmount() - remainder(fluid).pending());
    }

    private int productRoom(@Nullable Item item)
    {
        if (item == null || product.isEmpty())
        {
            return STACK_LIMIT;
        }
        return product.is(item) ? STACK_LIMIT - product.getCount() : 0;
    }

    /**
     * Lets one stretch of time pass. This is what the retort does every second, and a game test calls it with longer stretches to get through a batch.
     */
    public void step(ServerLevel server, float seconds)
    {
        lastReason = Distillation.Reason.NONE;
        noRecipe = false;
        final @Nullable RecipeHolder<DistillationRecipe> holder = currentRecipe(server);
        if (holder == null)
        {
            if (active)
            {
                abandon(server);
            }
            noRecipe = !input.isEmpty() || !reagent.isEmpty() || !charge.isEmpty();
            return;
        }
        final DistillationRecipe recipe = holder.value();
        final boolean condenser = hasCondenser();

        final boolean starting = !active;
        final boolean runDry = starting ? recipe.receiverWater() > 0f && (!condenser || water.litres() + EPSILON < recipe.receiverWater()) : dry;
        final Distillation.Recipe plan = runDry ? recipe.unabsorbed() : recipe.batch();
        final Distillation batch = new Distillation(plan, progress, active);

        final Fluid mainFluid = recipe.fluid().fluid();
        final float reagentLitres = recipe.reagent().map(extra -> litresOf(extra.fluid())).orElse(0f);
        final Distillation.Stock stock = new Distillation.Stock(litresOf(mainFluid), reagentLitres, recipe.item().isPresent() ? charge.getCount() : 0, (float) water.litres());

        final @Nullable Fluid outputFluid = recipe.output().map(CarbonizationRecipe.Condensate::fluid).orElse(null);
        final @Nullable Fluid residueFluid = recipe.residue().map(CarbonizationRecipe.Condensate::fluid).orElse(null);
        final @Nullable Item byproductItem = recipe.byproduct().map(DistillationRecipe.Byproduct::item).orElse(null);
        // Without a condenser nothing is caught, so a full receiver holds nothing back
        final double outputRoom = outputFluid == null ? 0.0 : (condenser ? roomFor(outputFluid, receiver) : Double.MAX_VALUE);
        final double residueRoom = residueFluid == null ? 0.0 : roomFor(residueFluid, residue);
        final Distillation.Room room = new Distillation.Room(outputRoom, residueRoom, productRoom(byproductItem));

        final Distillation.Step result = batch.advance(seconds, temperature, stock, room);
        lastReason = result.reason();
        if (!result.ran())
        {
            return;
        }

        if (starting)
        {
            batchRecipe = holder.id();
            dry = runDry;
            if (result.litres() > 0.0)
            {
                tankOf(mainFluid).take(result.litres());
            }
            if (result.reagent() > 0.0)
            {
                recipe.reagent().ifPresent(extra -> tankOf(extra.fluid()).take(result.reagent()));
            }
            if (result.items() > 0)
            {
                charge.shrink(result.items());
            }
            if (result.water() > 0.0)
            {
                water.take(result.water());
            }
        }
        active = !result.finished();
        progress = result.finished() ? 0.0 : batch.progress();
        if (result.finished())
        {
            batchRecipe = null;
            dry = false;
        }

        final float[] gases = new float[Gas.COUNT];
        for (int i = 0; i < Gas.COUNT; i++)
        {
            gases[i] = (float) result.gas()[i];
        }
        if (outputFluid != null && result.output() > 0.0)
        {
            if (condenser)
            {
                collect(outputFluid, result.output(), receiver);
            }
            else
            {
                // Nothing catches it: what would have condensed goes into the room
                final @Nullable Liquid liquid = Chemistry.liquidOf(outputFluid);
                if (liquid != null)
                {
                    final float[] vapour = AcidVapour.of(liquid, result.output());
                    for (int i = 0; i < Gas.COUNT; i++)
                    {
                        gases[i] += vapour[i];
                    }
                }
            }
        }
        if (residueFluid != null && result.residue() > 0.0)
        {
            collect(residueFluid, result.residue(), residue);
        }
        if (result.byproducts() > 0 && byproductItem != null)
        {
            if (product.isEmpty())
            {
                product = new ItemStack(byproductItem, result.byproducts());
            }
            else
            {
                product.grow(result.byproducts());
            }
        }
        release(server, worldPosition.above(), gases);
        setChanged();
    }

    private void collect(Fluid fluid, double litres, FluidTank tank)
    {
        final Remainder remainder = remainder(fluid);
        remainder.add(litres);
        final int whole = remainder.whole();
        if (whole > 0)
        {
            remainder.take(tank.fill(new FluidStack(fluid, whole), IFluidHandler.FluidAction.EXECUTE));
        }
    }

    private static void release(ServerLevel server, BlockPos pos, float[] gases)
    {
        for (float moles : gases)
        {
            if (moles > 0f)
            {
                GasEmission.release(server, pos, gases);
                return;
            }
        }
    }

    /**
     * A batch whose recipe has gone (a data pack was changed under a running retort) cannot go on, and without the recipe nobody can tell what it had taken, so that cannot be given back. It is stopped and logged. This is the one place where the retort loses matter, and only by an edit of the data.
     */
    private void abandon(ServerLevel server)
    {
        LOGGER.warn("The glass retort at {} lost the recipe {} of its running batch and has dropped the batch", worldPosition, batchRecipe);
        batchRecipe = null;
        active = false;
        progress = 0.0;
        dry = false;
        setChanged();
    }

    // --- Cracking and breaking --------------------------------------------------------------------------------------------------------------------------------------------

    /** Glass that is heated past what it stands cracks, whatever is in it, and the whole of the charge is let out. */
    private void crack(ServerLevel server)
    {
        for (ServerPlayer player : server.players())
        {
            if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= CRACK_HEARD * CRACK_HEARD)
            {
                player.displayClientMessage(Component.translatable("tfcpp.glass_retort.cracked", Math.round(temperature)), false);
            }
        }
        server.playSound(null, worldPosition, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
        server.destroyBlock(worldPosition, false); // No drop: broken glass. The block's removal lets the contents out.
    }

    /**
     * What breaking the retort lets out, whether it was cracked by heat or broken by hand: a corrosive liquid splashes whoever is by it, every liquid goes into the room as the fume it would make, and the powder is picked up from the floor.
     * The part of a batch that has not yet reacted is given back as it was: its powder to the floor and its liquid to the fume, while what has reacted has already come out.
     */
    public void spill(ServerLevel server)
    {
        final double x = worldPosition.getX(), y = worldPosition.getY(), z = worldPosition.getZ();
        final float[] gases = new float[Gas.COUNT];
        boolean corrosive = false;

        for (FloatTank tank : new FloatTank[] {input, reagent})
        {
            if (!tank.isEmpty())
            {
                corrosive |= addVapour(server, worldPosition, gases, tank.fluid(), tank.litres());
            }
        }
        for (FluidTank tank : new FluidTank[] {receiver, residue})
        {
            if (!tank.isEmpty())
            {
                final Fluid fluid = tank.getFluid().getFluid();
                corrosive |= addVapour(server, worldPosition, gases, fluid, tank.getFluidAmount() + pending(fluid));
            }
        }
        if (active && batchRecipe != null)
        {
            final var holder = server.getRecipeManager().byKey(batchRecipe).orElse(null);
            if (holder != null && holder.value() instanceof DistillationRecipe recipe)
            {
                final double left = 1.0 - progress / (dry ? recipe.unabsorbed() : recipe.batch()).seconds();
                corrosive |= addVapour(server, worldPosition, gases, recipe.fluid().fluid(), recipe.fluid().litres() * left);
                if (recipe.reagent().isPresent())
                {
                    corrosive |= addVapour(server, worldPosition, gases, recipe.reagent().get().fluid(), recipe.reagent().get().litres() * left);
                }
                Containers.dropItemStack(server, x, y, z, recipe.chargeStack((int) Math.floor(recipe.items() * left + EPSILON)));
                final double done = 1.0 - left;
                recipe.byproduct().ifPresent(byproduct -> Containers.dropItemStack(server, x, y, z, new ItemStack(byproduct.item(), (int) Math.floor(byproduct.count() * done + EPSILON))));
            }
        }
        if (corrosive)
        {
            CorrosiveSpill.at(server, worldPosition);
        }
        release(server, worldPosition, gases);

        Containers.dropItemStack(server, x, y, z, charge);
        Containers.dropItemStack(server, x, y, z, product);
        charge = ItemStack.EMPTY;
        product = ItemStack.EMPTY;
        input.clear();
        reagent.clear();
        water.clear();
        receiver.setFluid(FluidStack.EMPTY);
        residue.setFluid(FluidStack.EMPTY);
        carry.clear();
        batchRecipe = null;
        active = false;
        progress = 0.0;
    }

    /** @return {@code true} if the liquid is a corrosive one */
    private static boolean addVapour(ServerLevel server, BlockPos pos, float[] gases, Fluid fluid, double litres)
    {
        final @Nullable Liquid liquid = Chemistry.liquidOf(fluid);
        if (liquid == null)
        {
            return false;
        }
        // What does not go up as vapour runs over the floor and into the ground: all of a tar or a sulfuric acid, and the part of a fuming acid that stayed liquid
        Pollution.soak(server, pos, fluid, AcidVapour.unvapourised(liquid, litres));
        final float[] vapour = AcidVapour.of(liquid, litres);
        for (int i = 0; i < Gas.COUNT; i++)
        {
            gases[i] += vapour[i];
        }
        return liquid.isCorrosive();
    }

    // --- Use by a person -----------------------------------------------------------------------------------------------------------------------------------------------

    /** @return {@code true} if holding this item means to use it on the retort, and not to place it against the block */
    public boolean canUse(ItemStack stack)
    {
        assert level != null;
        return stack.getCapability(Capabilities.FluidHandler.ITEM) != null || DistillationRecipe.isCharge(level, stack);
    }

    /** @return {@code true} if a held item did something */
    public boolean interact(ItemStack stack, Player player, InteractionHand hand)
    {
        if (stack.getCapability(Capabilities.FluidHandler.ITEM) != null)
        {
            if (!player.isShiftKeyDown() && pourFrom(stack, player, hand))
            {
                return true;
            }
            return tapInto(stack, player, hand);
        }
        return loadCharge(stack, player);
    }

    /** Pours the contents of one held container in: water to the receiver's tank, anything else a recipe takes to the flask. A container is only ever emptied one at a time. */
    private boolean pourFrom(ItemStack stack, Player player, InteractionHand hand)
    {
        final ItemStack single = stack.copyWithCount(1);
        final @Nullable IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null)
        {
            return false;
        }
        final FluidStack held = handler.getFluidInTank(0);
        if (held.isEmpty())
        {
            return false;
        }
        final IFluidHandler target = Helpers.isFluid(held.getFluid(), TFCTags.Fluids.ANY_FRESH_WATER) ? water : above;
        final int accepted = target.fill(held.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0)
        {
            player.displayClientMessage(Component.translatable("tfcpp.glass_retort.refused"), true);
            return false;
        }
        final FluidStack drained = handler.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        giveBack(stack, handler.getContainer(), player, hand);
        return true;
    }

    /** Runs the condensed liquid into one held container: the receiver first, then what stayed in the flask, and the flask's own liquids only for a person who is crouching. */
    private boolean tapInto(ItemStack stack, Player player, InteractionHand hand)
    {
        final java.util.List<IFluidHandler> sources = new java.util.ArrayList<>(java.util.List.of(receiver, residue));
        if (player.isShiftKeyDown())
        {
            sources.add(input);
            sources.add(reagent);
        }
        for (IFluidHandler source : sources)
        {
            final FluidStack available = source.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (available.isEmpty())
            {
                continue;
            }
            final ItemStack single = stack.copyWithCount(1);
            final @Nullable IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
            final int filled = handler == null ? 0 : handler.fill(available.copy(), IFluidHandler.FluidAction.EXECUTE);
            if (handler == null || filled <= 0)
            {
                continue;
            }
            source.drain(filled, IFluidHandler.FluidAction.EXECUTE);
            giveBack(stack, handler.getContainer(), player, hand);
            return true;
        }
        if (player.isShiftKeyDown() && rinseDregs(player))
        {
            return true;
        }
        player.displayClientMessage(Component.translatable("tfcpp.glass_retort.nothing_to_tap"), true);
        return false;
    }

    /**
     * A flask tipped out into a carboy is left with a film of liquid, under a whole litre, that no container takes: and while it clings there, the flask holds a liquid and will take no other, and no recipe matches a stray one. It is rinsed out onto the floor, where it gives off what it would, and a corrosive one splashes whoever stands by.
     *
     * @return {@code true} if a flask held such dregs
     */
    private boolean rinseDregs(Player player)
    {
        if (!(level instanceof ServerLevel server))
        {
            return false;
        }
        final float[] gases = new float[Gas.COUNT];
        boolean corrosive = false, found = false;
        for (FloatTank tank : new FloatTank[] {input, reagent})
        {
            if (!tank.isEmpty() && tank.wholeMillibuckets() == 0)
            {
                found = true;
                corrosive |= addVapour(server, worldPosition, gases, tank.fluid(), tank.litres());
                tank.clear();
            }
        }
        if (!found)
        {
            return false;
        }
        setChanged();
        player.displayClientMessage(Component.translatable("tfcpp.glass_retort.rinsed"), true);
        if (corrosive)
        {
            CorrosiveSpill.at(server, worldPosition);
        }
        release(server, worldPosition, gases);
        return true;
    }

    private static void giveBack(ItemStack stack, ItemStack result, Player player, InteractionHand hand)
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

    /** Puts powder in the slot. One kind at a time, and not while a batch that has taken its powder could be spoilt: a batch that has started needs none. */
    private boolean loadCharge(ItemStack stack, Player player)
    {
        if (!charge.isEmpty() && !ItemStack.isSameItemSameComponents(charge, stack))
        {
            player.displayClientMessage(Component.translatable("tfcpp.glass_retort.one_kind"), true);
            return true;
        }
        final int room = STACK_LIMIT - charge.getCount();
        if (room <= 0)
        {
            player.displayClientMessage(Component.translatable("tfcpp.glass_retort.full"), true);
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
        player.displayClientMessage(Component.translatable("tfcpp.glass_retort.loaded", charge.getCount(), charge.getHoverName()), true);
        if (!hasCondenser() && needsCondenser())
        {
            player.displayClientMessage(Component.translatable("tfcpp.glass_retort.no_condenser"), true);
        }
        return true;
    }

    /**
     * Takes out the powder: what the last batches left, and the charge that was put in and has not been taken by a batch. Without this a powder that no batch will take, or one that is not the next recipe's, would hold the retort for ever.
     */
    public void takePowder(Player player)
    {
        if (product.isEmpty() && charge.isEmpty())
        {
            player.displayClientMessage(Component.translatable("tfcpp.glass_retort.nothing"), true);
            return;
        }
        if (!product.isEmpty())
        {
            ItemHandlerHelper.giveItemToPlayer(player, product);
            product = ItemStack.EMPTY;
        }
        if (!charge.isEmpty())
        {
            ItemHandlerHelper.giveItemToPlayer(player, charge);
            charge = ItemStack.EMPTY;
        }
        setChanged();
    }

    private static String litres(double litres)
    {
        return String.format(Locale.ROOT, "%.2f", litres);
    }

    private Component describe(FloatTank tank)
    {
        return tank.isEmpty() ? Component.translatable("tfcpp.glass_retort.empty") : Component.translatable("tfcpp.glass_retort.tank", litres(tank.litres()), Component.translatable(tank.fluid().getFluidType().getDescriptionId()));
    }

    private Component describe(FluidTank tank, Fluid fluid)
    {
        return tank.isEmpty() ? Component.translatable("tfcpp.glass_retort.empty") : Component.translatable("tfcpp.glass_retort.tank", litres(tank.getFluidAmount() + pending(fluid)), tank.getFluid().getHoverName());
    }

    /** Reports what the retort holds and why it is not working, to a person with an empty hand. */
    public void showStatus(Player player)
    {
        final Component stage = active
            ? Component.translatable("tfcpp.glass_retort.stage.running", Math.round(100.0 * progress / (batchSeconds())))
            : Component.translatable("tfcpp.glass_retort.stage.idle");
        player.displayClientMessage(Component.translatable("tfcpp.glass_retort.status", Math.round(temperature), Math.round(Distillation.CRACK_TEMPERATURE), stage), false);
        player.displayClientMessage(Component.translatable("tfcpp.glass_retort.contents", describe(input), describe(reagent),
            charge.isEmpty() ? Component.translatable("tfcpp.glass_retort.empty") : Component.empty().append(charge.getCount() + " x ").append(charge.getHoverName())), false);
        player.displayClientMessage(Component.translatable("tfcpp.glass_retort.receiver", describe(receiver, receiver.getFluid().getFluid()), describe(residue, residue.getFluid().getFluid()), describe(water)), false);
        if (!product.isEmpty())
        {
            player.displayClientMessage(Component.translatable("tfcpp.glass_retort.ready", product.getCount(), product.getHoverName()), false);
        }
        if (!hasCondenser() && needsCondenser())
        {
            player.displayClientMessage(Component.translatable("tfcpp.glass_retort.no_condenser"), false);
        }
        if (noRecipe)
        {
            player.displayClientMessage(Component.translatable("tfcpp.glass_retort.no_recipe"), false);
        }
        if (level instanceof ServerLevel server && !active)
        {
            final @Nullable RecipeHolder<DistillationRecipe> holder = currentRecipe(server);
            if (holder != null)
            {
                final DistillationRecipe recipe = holder.value();
                if (recipe.receiverWater() > 0f && (!hasCondenser() || water.litres() + EPSILON < recipe.receiverWater()))
                {
                    player.displayClientMessage(Component.translatable("tfcpp.glass_retort.dry", litres(recipe.receiverWater())), false);
                }
                final Distillation.Recipe plan = recipe.batch();
                if (charge.getCount() < plan.items())
                {
                    player.displayClientMessage(Component.translatable("tfcpp.glass_retort.need_items", plan.items()), false);
                }
                if (litresOf(recipe.fluid().fluid()) + EPSILON < plan.inputLitres())
                {
                    player.displayClientMessage(Component.translatable("tfcpp.glass_retort.need_liquid", litres(plan.inputLitres())), false);
                }
                if (recipe.reagent().isPresent() && litresOf(recipe.reagent().get().fluid()) + EPSILON < plan.reagentLitres())
                {
                    player.displayClientMessage(Component.translatable("tfcpp.glass_retort.need_reagent", litres(plan.reagentLitres())), false);
                }
                if (temperature < plan.minTemperature())
                {
                    player.displayClientMessage(Component.translatable("tfcpp.glass_retort.too_cold", Math.round(plan.minTemperature())), false);
                }
                else if (temperature > plan.maxTemperature())
                {
                    player.displayClientMessage(Component.translatable("tfcpp.glass_retort.too_hot", Math.round(plan.maxTemperature())), false);
                }
            }
        }
        if (lastReason == Distillation.Reason.NO_ROOM)
        {
            player.displayClientMessage(Component.translatable("tfcpp.glass_retort.output_full"), false);
        }
    }

    private double batchSeconds()
    {
        if (level instanceof ServerLevel server && batchRecipe != null)
        {
            final var holder = server.getRecipeManager().byKey(batchRecipe).orElse(null);
            if (holder != null && holder.value() instanceof DistillationRecipe recipe)
            {
                return Math.max(1e-6, recipe.seconds());
            }
        }
        return 1.0;
    }

    // --- Heat and saving ---------------------------------------------------------------------------------------------------------------------------------------------

    @Override
    public float getTemperature()
    {
        return temperature;
    }

    @Override
    public void setTemperature(float temperature)
    {
        // Devices report their temperature every tick, even when cold. Only something hotter than the flask can heat it.
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
        tag.put("input", input.save());
        tag.put("reagent", reagent.save());
        tag.put("water", water.save());
        tag.put("receiver", receiver.writeToNBT(provider, new CompoundTag()));
        tag.put("residue", residue.writeToNBT(provider, new CompoundTag()));
        tag.put("charge", charge.saveOptional(provider));
        tag.put("product", product.saveOptional(provider));
        final CompoundTag carried = new CompoundTag();
        carry.forEach((key, remainder) -> carried.putDouble(key, remainder.pending()));
        tag.put("carry", carried);
        if (batchRecipe != null)
        {
            tag.putString("batch", batchRecipe.toString());
        }
        tag.putDouble("progress", progress);
        tag.putBoolean("active", active);
        tag.putBoolean("dry", dry);
        tag.putFloat("temperature", temperature);
        tag.putFloat("targetTemperature", targetTemperature);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        input.load(tag.getCompound("input"));
        reagent.load(tag.getCompound("reagent"));
        water.load(tag.getCompound("water"));
        receiver.readFromNBT(provider, tag.getCompound("receiver"));
        residue.readFromNBT(provider, tag.getCompound("residue"));
        charge = ItemStack.parseOptional(provider, tag.getCompound("charge"));
        product = ItemStack.parseOptional(provider, tag.getCompound("product"));
        carry.clear();
        final CompoundTag carried = tag.getCompound("carry");
        for (String key : carried.getAllKeys())
        {
            carry.put(key, new Remainder(carried.getDouble(key)));
        }
        batchRecipe = tag.contains("batch") ? ResourceLocation.tryParse(tag.getString("batch")) : null;
        progress = tag.getDouble("progress");
        active = tag.getBoolean("active") && batchRecipe != null;
        dry = tag.getBoolean("dry");
        temperature = tag.getFloat("temperature");
        targetTemperature = tag.getFloat("targetTemperature");
    }

    /** Sets the temperature of the flask at once, for a test that does not want to wait for a fire to bring it there. */
    public void warmTo(float celsius)
    {
        temperature = celsius;
        targetTemperature = celsius;
        heatStabilityTicks = HEAT_STABILITY_TICKS;
    }

    // --- The fluid handlers ------------------------------------------------------------------------------------------------------------------------------------------

    /** Puts a liquid in the tank that holds it already, or else in an empty one. A liquid that no recipe takes, or a third one, is refused. */
    private int pour(FluidStack resource, IFluidHandler.FluidAction action)
    {
        if (resource.isEmpty())
        {
            return 0;
        }
        final Fluid fluid = resource.getFluid();
        if (input.fluid() == fluid)
        {
            return input.fill(resource, action);
        }
        if (reagent.fluid() == fluid)
        {
            return reagent.fill(resource, action);
        }
        if (input.isEmpty())
        {
            return input.fill(resource, action);
        }
        if (reagent.isEmpty())
        {
            return reagent.fill(resource, action);
        }
        return 0;
    }

    /** Takes liquid in from a pipe or a container above: the input tanks, and nothing comes out. */
    private final class Pour implements IFluidHandler
    {
        @Override
        public int getTanks()
        {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int index)
        {
            return (index == 0 ? input : reagent).getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int index)
        {
            return FLASK_MILLIBUCKETS;
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack)
        {
            return input.accepts(stack.getFluid()) || reagent.accepts(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action)
        {
            return pour(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action)
        {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action)
        {
            return FluidStack.EMPTY;
        }
    }

    /**
     * The receiver on the sides: water runs in, into the tank that the receiver takes its hydrogen chloride up with, and what has condensed runs out, as it does from below. Nothing else goes in.
     */
    private final class Side implements IFluidHandler
    {
        @Override
        public int getTanks()
        {
            return 3;
        }

        @Override
        public FluidStack getFluidInTank(int index)
        {
            return switch (index)
            {
                case 0 -> receiver.getFluid();
                case 1 -> residue.getFluid();
                default -> water.getFluidInTank(0);
            };
        }

        @Override
        public int getTankCapacity(int index)
        {
            return index == 0 ? RECEIVER_MILLIBUCKETS : index == 1 ? FLASK_MILLIBUCKETS : WATER_MILLIBUCKETS;
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack)
        {
            return index == 2 && water.accepts(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action)
        {
            return water.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action)
        {
            return below.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action)
        {
            return below.drain(maxDrain, action);
        }
    }

    /** Lets what has condensed run out of the bottom: the receiver, then what stayed in the flask. With {@code all}, it also takes the input tanks in. */
    private final class Draw implements IFluidHandler
    {
        private final boolean all;

        Draw(boolean all)
        {
            this.all = all;
        }

        @Override
        public int getTanks()
        {
            return all ? 4 : 2;
        }

        @Override
        public FluidStack getFluidInTank(int index)
        {
            return switch (index)
            {
                case 0 -> receiver.getFluid();
                case 1 -> residue.getFluid();
                case 2 -> input.getFluidInTank(0);
                default -> reagent.getFluidInTank(0);
            };
        }

        @Override
        public int getTankCapacity(int index)
        {
            return index == 0 ? RECEIVER_MILLIBUCKETS : FLASK_MILLIBUCKETS;
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack)
        {
            return all && index >= 2 && (input.accepts(stack.getFluid()) || reagent.accepts(stack.getFluid()));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action)
        {
            return all ? pour(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action)
        {
            if (resource.isEmpty())
            {
                return FluidStack.EMPTY;
            }
            final FluidStack fromReceiver = receiver.drain(resource, action);
            return fromReceiver.isEmpty() ? residue.drain(resource, action) : fromReceiver;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action)
        {
            final FluidStack fromReceiver = receiver.drain(maxDrain, action);
            return fromReceiver.isEmpty() ? residue.drain(maxDrain, action) : fromReceiver;
        }
    }
}
