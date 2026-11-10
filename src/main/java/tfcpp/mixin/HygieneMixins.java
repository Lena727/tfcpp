package tfcpp.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.dries007.tfc.common.blockentities.PotBlockEntity;
import net.dries007.tfc.common.component.fluid.FluidComponent;
import net.dries007.tfc.common.component.fluid.FluidContainerInfo;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.common.items.GlassBottleItem;
import net.dries007.tfc.common.items.JugItem;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.data.Drinkable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import tfcpp.health.Drinking;
import tfcpp.health.Filtration;
import tfcpp.health.Hygiene;
import tfcpp.health.FluidQuality;
import tfcpp.health.WaterQuality;
import tfcpp.health.WaterSource;

/**
 * The quality of water travels with the water (stage E of v1.5): it is put on the water when it is taken from the world, mixed where two waters meet in a tank or a container, swallowed with a drink from a jug or a bottle, and boiled away in a pot.
 */
final class HygieneMixins
{
    private HygieneMixins() {}

    /** Water picked up from a block in the world, by a bucket, a jug or any container, takes the quality of the place it was taken from. */
    @Mixin(FluidHelpers.class)
    abstract static class Pickup
    {
        @ModifyReturnValue(method = "pickupFluid(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;Ljava/util/function/Consumer;)Lnet/neoforged/neoforge/fluids/FluidStack;", at = @At("RETURN"))
        private static @Nullable FluidStack tfcpp$quality(@Nullable FluidStack picked, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) IFluidHandler.FluidAction action)
        {
            if (picked != null && !picked.isEmpty() && level instanceof ServerLevel server && FluidQuality.carries(picked.getFluid()))
            {
                // A simulated pickup only looks: nothing is taken from the soil, the pits or the river until the water is really taken
                final double litres = picked.getAmount() * Drinking.LITRES_PER_DRUNK_MILLIBUCKET;
                return FluidQuality.with(picked, action.execute() ? WaterSource.take(server, pos, litres) : WaterSource.at(server, pos));
            }
            return picked;
        }
    }

    /** Water that is poured into a tank that holds water of another quality mixes with it, by volume, where a tank would otherwise refuse it as a different fluid. Every tank of NeoForge's template does this: barrels, pots, molds and the tanks of this mod. */
    @Mixin(FluidTank.class)
    abstract static class Tank
    {
        @Shadow
        protected FluidStack fluid;
        @Shadow
        protected int capacity;

        @Shadow
        public abstract boolean isFluidValid(FluidStack stack);

        @Shadow
        protected abstract void onContentsChanged();

        @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
        private void tfcpp$mix(FluidStack resource, IFluidHandler.FluidAction action, CallbackInfoReturnable<Integer> cir)
        {
            if (!fluid.isEmpty() && !resource.isEmpty() && FluidQuality.mergeable(fluid, resource) && isFluidValid(resource))
            {
                final int filled = Math.max(0, Math.min(capacity - fluid.getAmount(), resource.getAmount()));
                if (filled > 0 && action.execute())
                {
                    final WaterQuality mixed = FluidQuality.mixed(fluid, fluid.getAmount(), resource, filled);
                    fluid.grow(filled);
                    FluidQuality.set(fluid, mixed);
                    onContentsChanged();
                }
                cir.setReturnValue(filled);
            }
        }
    }

    /** The same for the fluid containers of TFC (jugs, bottles, buckets, vessels), which keep their fluid in a component and do not use a tank. */
    @Mixin(FluidComponent.class)
    abstract static class Container
    {
        @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
        private static void tfcpp$mix(FluidStack content, FluidStack input, FluidContainerInfo info, CallbackInfoReturnable<FluidComponent.FillInfo> cir)
        {
            if (!content.isEmpty() && !input.isEmpty() && info.canContainFluid(input) && FluidQuality.mergeable(content, input))
            {
                final int filled = Math.max(0, Math.min(content.getAmount() + input.getAmount(), info.fluidCapacity()) - content.getAmount());
                if (filled <= 0)
                {
                    cir.setReturnValue(new FluidComponent.FillInfo(content, 0));
                    return;
                }
                final FluidStack merged = content.copyWithAmount(content.getAmount() + filled);
                FluidQuality.set(merged, FluidQuality.mixed(content, content.getAmount(), input, filled));
                cir.setReturnValue(new FluidComponent.FillInfo(merged, filled));
            }
        }
    }

    /** What a person drinks from a ceramic jug is what the jug holds. */
    @Mixin(JugItem.class)
    abstract static class Jug
    {
        @WrapOperation(method = "finishUsingItem", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/capability/IFluidHandler;drain(ILnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
        private FluidStack tfcpp$swallow(IFluidHandler handler, int amount, IFluidHandler.FluidAction action, Operation<FluidStack> original, @Local(argsOnly = true) LivingEntity entity)
        {
            final FluidStack drained = original.call(handler, amount, action);
            if (entity instanceof ServerPlayer player && !drained.isEmpty() && Drinkable.get(drained.getFluid()) != null)
            {
                Drinking.fromStack(player, drained);
            }
            return drained;
        }
    }

    /** And from a glass bottle. */
    @Mixin(GlassBottleItem.class)
    abstract static class Bottle
    {
        @WrapOperation(method = "finishUsingItem", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/capability/IFluidHandler;drain(ILnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
        private FluidStack tfcpp$swallow(IFluidHandler handler, int amount, IFluidHandler.FluidAction action, Operation<FluidStack> original, @Local(argsOnly = true) LivingEntity entity)
        {
            final FluidStack drained = original.call(handler, amount, action);
            if (entity instanceof ServerPlayer player && !drained.isEmpty() && Drinkable.get(drained.getFluid()) != null)
            {
                Drinking.fromStack(player, drained);
            }
            return drained;
        }
    }

    /** Water in a pot that is held at the boiling point for a minute has its pathogens killed (WHO: bring to a rolling boil, a minute is enough). Metals and acid stay. Fresh water poured in afterwards starts the minute again. The minute is counted in the calendar: a fire that stands still on an empty server does not boil. */
    @Mixin(PotBlockEntity.class)
    abstract static class Pot
    {
        /** Calendar ticks of boiling so far, the calendar tick of the last look, and the volume then. */
        @Unique
        private long tfcpp$boilTicks;
        @Unique
        private long tfcpp$lastCalendar;
        @Unique
        private int tfcpp$lastAmount;
        /** The water in the pot has been boiled, and is not looked at again until more is poured in. */
        @Unique
        private boolean tfcpp$boiled;

        @Inject(method = "handleCooking", at = @At("TAIL"))
        private void tfcpp$boil(CallbackInfo ci)
        {
            final PotBlockEntity pot = (PotBlockEntity) (Object) this;
            final @Nullable Level level = pot.getLevel();
            if (level == null || level.isClientSide)
            {
                return;
            }
            final long calendar = Calendars.SERVER.getTicks();
            final long passed = tfcpp$lastCalendar == 0L ? 0L : Math.min(Math.max(0L, calendar - tfcpp$lastCalendar), MAX_STEP_TICKS); // The first look counts nothing
            tfcpp$lastCalendar = calendar;
            if (pot.getTemperature() < Filtration.BOILING_CELSIUS)
            {
                tfcpp$boilTicks = 0;
                return;
            }
            final FluidStack water = pot.getInventory().getFluidHandler().getFluidInTank(0);
            if (water.isEmpty() || !FluidQuality.carries(water.getFluid()))
            {
                tfcpp$boilTicks = 0;
                tfcpp$lastAmount = 0;
                tfcpp$boiled = false;
                return;
            }
            if (water.getAmount() > tfcpp$lastAmount)
            {
                tfcpp$boilTicks = 0; // Water that was just poured in has to come to the boil too
                tfcpp$boiled = false;
            }
            tfcpp$lastAmount = water.getAmount();
            if (tfcpp$boiled)
            {
                return;
            }
            if (tfcpp$boilTicks < Filtration.BOIL_SECONDS * 20)
            {
                tfcpp$boilTicks += passed;
                return;
            }
            tfcpp$boiled = true;
            if (water.has(Hygiene.WATER_QUALITY.get())) // Water with no component is clean: nothing to decode
            {
                final WaterQuality quality = FluidQuality.get(water);
                if (quality.hasPathogens())
                {
                    FluidQuality.set(water, quality.boiled());
                    pot.setChanged();
                }
            }
        }

        /** The longest stretch of calendar that one look counts: a calendar that jumps is not a boil. */
        @Unique
        private static final long MAX_STEP_TICKS = 40L;
    }
}
