package tfcpp.metallurgy;

import java.util.Locale;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.component.heat.IHeat;
import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.dries007.tfc.common.items.Powder;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import tfcpp.metallurgy.ConverterBath.Blow;
import tfcpp.metallurgy.ConverterBath.Flame;
import tfcpp.metallurgy.ConverterBath.Grade;
import tfcpp.pneumatics.gas.Combustion;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.gas.GasIgnition;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.pipes.PipeContents;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * Bessemer's converter of 1856: air blown through molten pig iron burns the carbon out of it, with no fuel at all.
 * <p>
 * This holds the charge, feeds it the blast from its pipe, and deals with everything the charge does to the world around it: the flame at the mouth, the slag, wear of the lining, and what happens when water reaches liquid iron.
 */
public final class ConverterBlockEntity extends BlockEntity implements IHeatConsumer
{
    private static final int STEP_TICKS = 20;
    private static final int HEAT_STABILITY_TICKS = 5;
    /** Pressure of iron over the tuyeres, in atm above the air: a full vessel holds about 70 cm, as in the small converters of the 1860s. */
    private static final float FULL_HEAD_ATM = 0.7f;
    /** Some small holes in a clay floor. */
    private static final float TUYERE_AREA_SHARE = 1f / 16f;
    /** What the hearth of a blast furnace or a forge can pass into the iron through a runner or a lined wall, whatever its own heat. */
    private static final float MOST_HEAT_FROM_OUTSIDE = 1450f;
    /** Seconds of blowing at ordinary heat that a lining lasts: real clay bottoms lasted some twenty blows, and the walls longer. */
    private static final float LINING_LIFE = 12000f;
    private static final float LINING_SOFTENS_AT = 1650f, LINING_WEAR_PER_KELVIN = 0.02f;
    private static final float LINING_THIN = 0.75f;
    private static final float LINING_PER_BRICK = LINING_LIFE / 8f;
    /** A breath of steam in a bath of iron is the most violent thing in a steelworks: a litre of water takes up 1700 litres as steam, at once. */
    private static final float EXPLOSION_KILOJOULES_PER_LITRE = 500f, EXPLOSION_MAX_LITRES = 50f, EXPLOSION_KILOPASCALS = 150f;
    private static final float LEAST_LITRES_TO_EXPLODE = 1f;
    /** A vessel this far gone is scrap, so that breaking and replacing it does not make a new lining. */
    private static final float LINING_SCRAP = 0.25f;
    private static final float CLEAN_FLAME_CARBON_MONOXIDE = 0.05f;
    private static final int MILLIBUCKETS_PER_INGOT = 100;
    /** Basic bricks that make a new basic lining, as many as a lining takes of fire bricks: the lining is the same size. */
    private static final int BASIC_BRICKS_PER_LINING = 8;
    /** Seconds of lining life that a mole of silica eats when there is no lime to take it into slag: a basic lining is the only thing that silica can attack. */
    private static final float SILICA_WEAR_PER_MOLE = 60f;
    /** Moles of phosphorus in one piece of Thomas slag, which is one formula unit of 4CaO.P2O5. */
    private static final float PHOSPHORUS_PER_THOMAS_PIECE = 2f;
    private static final int SPILL_BURN_RADIUS = 2;
    private static final int SPILL_BURN_SECONDS = 8;

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConverterBlockEntity converter)
    {
        if (converter.awaitingHeat > 0f)
        {
            converter.mixArrived(ConverterBath.AMBIENT);
        }
        if (converter.heatStabilityTicks > 0)
        {
            converter.heatStabilityTicks--;
            if (converter.bath.temperature < converter.heatFromOutside)
            {
                converter.bath.temperature = HeatCapability.adjustTempTowards(converter.bath.temperature, converter.heatFromOutside);
                converter.setChanged();
            }
        }

        if (converter.nextStepTick < 0)
        {
            converter.nextStepTick = level.getGameTime();
        }
        if (level.getGameTime() >= converter.nextStepTick)
        {
            // Measured from the last step, so that a charge left in an unloaded chunk comes back cold, and not as it was left
            final float seconds = (level.getGameTime() - converter.nextStepTick + STEP_TICKS) / (float) STEP_TICKS;
            converter.nextStepTick = level.getGameTime() + STEP_TICKS;
            if (converter.bath.metal > 0f)
            {
                converter.bath.cool(seconds);
                converter.setChanged();
            }
            converter.ejectSlag(level);
        }
    }

    private final ConverterBath bath = new ConverterBath();
    private final IFluidHandler tank = new Tank();

    private float liningWorn;
    /** Basic bricks laid in an acid lining so far. Until the lining is complete it is still the old acid one. */
    private int basicBricks;
    private float heatFromOutside;
    private int heatStabilityTicks;
    private long nextStepTick = -1L;
    /** Iron that has arrived from a block, before the source has said how hot it is. It is taken as cold if it never says. */
    private float awaitingHeat;

    public ConverterBlockEntity(BlockPos pos, BlockState state)
    {
        super(Metallurgy.CONVERTER_ENTITY.get(), pos, state);
    }

    public ConverterBath bath()
    {
        return bath;
    }

    public IFluidHandler tank()
    {
        return tank;
    }

    public float liningWorn()
    {
        return liningWorn;
    }

    /** @return basic bricks laid toward a basic lining that is not complete yet */
    public int basicBricks()
    {
        return basicBricks;
    }

    public void wearLining(float seconds)
    {
        liningWorn += seconds;
    }

    /** One second of blast, driven by the pipe joined to this. */
    void blow(PipeNetwork network, ServerLevel level, float seconds)
    {
        if (bath.metal <= 0f)
        {
            // Nothing over the tuyeres, so they are just holes
            final @Nullable PipeContents through = network.blowInto(1f, TUYERE_AREA_SHARE, seconds);
            if (through != null)
            {
                GasEmission.release(level, worldPosition, through.gases.clone());
            }
            return;
        }
        if (!bath.isMolten())
        {
            return; // Iron that has set in the vessel has sealed the tuyeres as well
        }

        final PipeContents line = network.contents();
        if (line.litres >= LEAST_LITRES_TO_EXPLODE)
        {
            steamExplosion(level, line);
            return;
        }
        if (line.litres > 0f)
        {
            // A last wet film in the pipe flashes off in the wind box, with a bang that the shell can take
            line.litres = 0f;
            line.liquid = "";
            PipeNetwork.hiss(level, worldPosition);
        }

        final float head = 1f + FULL_HEAD_ATM * bath.metal / ConverterBath.MILLIBUCKETS;
        final @Nullable PipeContents blast = network.blowInto(head, TUYERE_AREA_SHARE, seconds);
        if (blast == null)
        {
            return;
        }

        final float oxygen = blast.air * GasMath.OXYGEN_IN_AIR;
        final Blow blow = bath.blow(oxygen, blast.gasMoles(), blast.temperature - 273.15f);
        release(level, blast, blow);
        liningWorn += bath.silicaWithoutLime * SILICA_WEAR_PER_MOLE;
        bath.silicaWithoutLime = 0f;
        wear(level, seconds);
        setChanged();
        showFlame(level);
    }

    private void steamExplosion(ServerLevel level, PipeContents line)
    {
        final float litres = Math.min(line.litres, EXPLOSION_MAX_LITRES);
        line.litres -= litres;
        if (line.litres <= 0f)
        {
            line.liquid = "";
        }
        final BlockPos pos = worldPosition;
        PipeTracker.get(level).later(() -> {
            level.destroyBlock(pos, false);
            GasIgnition.rupture(level, pos, litres * EXPLOSION_KILOJOULES_PER_LITRE, EXPLOSION_KILOPASCALS);
        });
    }

    /**
     * The nitrogen leaves along with the CO, which burns above the mouth if there is any air to burn it. Anything else in the blast comes straight through.
     */
    private void release(ServerLevel level, PipeContents blast, Blow blow)
    {
        final float[] gases = new float[Gas.COUNT];
        for (int i = 0; i < Gas.COUNT; i++)
        {
            gases[i] = blast.gases[i];
        }

        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final BlockPos mouth = worldPosition.above();
        final @Nullable float[] air = volume.isPassable(mouth.getX(), mouth.getY(), mouth.getZ()) ? volume.cell(mouth.getX(), mouth.getY(), mouth.getZ()) : null;
        final float share = Combustion.carbonMonoxideShare(CLEAN_FLAME_CARBON_MONOXIDE, air == null ? GasMath.OXYGEN_IN_AIR : GasMath.oxygenFraction(air));
        Combustion.addFlueGas(gases, blow.carbonBurned(), share);

        // Oxygen spent on the silicon and the iron leaves its nitrogen as well. What burned the carbon was counted just above.
        final float otherOxygen = Math.max(blow.oxygenUsed() - blow.carbonBurned() / 2f, 0f);
        gases[Gas.FLUE_NITROGEN.ordinal()] += otherOxygen * Combustion.NITROGEN_PER_OXYGEN;
        GasEmission.release(level, worldPosition, gases);
    }

    private void wear(ServerLevel level, float seconds)
    {
        liningWorn += seconds * (1f + Math.max(bath.temperature - LINING_SOFTENS_AT, 0f) * LINING_WEAR_PER_KELVIN);
        if (liningWorn >= LINING_LIFE)
        {
            breakOut(level);
        }
    }

    /** The lining has gone, and the iron has burned through the shell. The metal can be dug out afterward, but the vessel is lost. */
    private void breakOut(ServerLevel level)
    {
        final BlockPos pos = worldPosition;
        PipeTracker.get(level).later(() -> {
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2f, 1.4f);
            level.destroyBlock(pos, false);
        });
    }

    private void showFlame(ServerLevel level)
    {
        final Flame flame = bath.flame();
        final double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.1, z = worldPosition.getZ() + 0.5;
        final ParticleOptions particle = switch (flame)
        {
            case SPARKS -> ParticleTypes.LAVA;
            case BOILING, SHORTENING -> ParticleTypes.FLAME;
            case DROPPED -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case AFTERBLOW -> ParticleTypes.CAMPFIRE_SIGNAL_SMOKE; // The thick white smoke of P2O5
        };
        final int count = flame == Flame.BOILING ? 24 : flame == Flame.AFTERBLOW ? 36 : 6;
        final double rise = flame == Flame.BOILING ? 0.25 : 0.08;
        level.sendParticles(particle, x, y, z, count, 0.15, 0.1, 0.15, rise);
        level.playSound(null, worldPosition, flame == Flame.BOILING ? SoundEvents.BLAZE_BURN : SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, flame == Flame.BOILING ? 1.5f : 0.6f, 0.6f);
    }

    /** Skimmed off the top of the bath as it forms. */
    private void ejectSlag(Level level)
    {
        if (bath.slag >= IronSmelting.MILLIBUCKETS_PER_SLAG)
        {
            final int pieces = (int) (bath.slag / IronSmelting.MILLIBUCKETS_PER_SLAG);
            bath.slag -= pieces * IronSmelting.MILLIBUCKETS_PER_SLAG;
            Helpers.spawnItem(level, worldPosition.above(), new ItemStack(IronSmelting.slagOf(bath.phosphorus > ConverterBath.PHOSPHORUS_LIMIT_PERCENT), pieces));
            setChanged();
        }
        if (bath.thomasSlagPhosphorus >= PHOSPHORUS_PER_THOMAS_PIECE)
        {
            final int pieces = (int) (bath.thomasSlagPhosphorus / PHOSPHORUS_PER_THOMAS_PIECE);
            bath.thomasSlagPhosphorus -= pieces * PHOSPHORUS_PER_THOMAS_PIECE;
            Helpers.spawnItem(level, worldPosition.above(), new ItemStack(Metallurgy.THOMAS_SLAG.get(), pieces));
            setChanged();
        }
    }

    /** @return {@code true} if holding this item means to use it on the converter, and not to place it against the block */
    public static boolean canUse(ItemStack stack)
    {
        return stack.is(TFCItems.FIRE_BRICK.get()) || stack.is(Metallurgy.BASIC_BRICK.get()) || isLime(stack) || stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    private static boolean isLime(ItemStack stack)
    {
        return stack.is(TFCItems.POWDERS.get(Powder.LIME).asItem());
    }

    public void interact(ItemStack stack, Player player, InteractionHand hand)
    {
        if (stack.is(TFCItems.FIRE_BRICK.get()) || stack.is(Metallurgy.BASIC_BRICK.get()))
        {
            reline(stack, player);
            return;
        }
        if (isLime(stack))
        {
            throwLime(stack, player);
            return;
        }
        if (!pourIn(stack, player, hand))
        {
            tapInto(stack, player, hand);
        }
    }

    /**
     * New lining goes into an empty vessel, which is how a worn one has to be turned around.
     * A vessel is acid when new. Eight basic bricks laid in an empty one make the lining basic and new, and until the eighth it is still the acid one. A basic lining is only repaired with basic bricks, as fire clay is silica and would flux away in the slag.
     */
    private void reline(ItemStack stack, Player player)
    {
        final boolean basicBrick = stack.is(Metallurgy.BASIC_BRICK.get());
        if (bath.metal > 0f)
        {
            player.displayClientMessage(Component.translatable("tfcpp.converter.reline_full"), true);
        }
        else if (bath.basic && !basicBrick)
        {
            player.displayClientMessage(Component.translatable("tfcpp.converter.reline_basic"), true);
        }
        else if (!bath.basic && basicBrick)
        {
            basicBricks++;
            stack.shrink(1);
            if (basicBricks >= BASIC_BRICKS_PER_LINING)
            {
                basicBricks = 0;
                bath.basic = true;
                liningWorn = 0f;
                player.displayClientMessage(Component.translatable("tfcpp.converter.basic_done"), true);
            }
            else
            {
                player.displayClientMessage(Component.translatable("tfcpp.converter.basic_progress", basicBricks, BASIC_BRICKS_PER_LINING), true);
            }
            setChanged();
        }
        else if (liningWorn > 0f)
        {
            liningWorn = Math.max(liningWorn - LINING_PER_BRICK, 0f);
            stack.shrink(1);
            setChanged();
        }
    }

    /** Lime has to be thrown into the bath to take the silica and phosphorus into slag. On a silica lining it would only flux the lining away, so that is refused. */
    private void throwLime(ItemStack stack, Player player)
    {
        if (!bath.basic)
        {
            player.displayClientMessage(Component.translatable("tfcpp.converter.lime_acid"), true);
            return;
        }
        bath.addLime(ConverterBath.LIME_MOLES_PER_POWDER);
        stack.shrink(1);
        setChanged();
    }

    /** Empties a held mould or vessel of molten iron into the bath, along with its heat. @return {@code true} if anything was poured */
    private boolean pourIn(ItemStack stack, Player player, InteractionHand hand)
    {
        final ItemStack single = stack.copyWithCount(1);
        final @Nullable IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null)
        {
            return false;
        }
        final FluidStack held = handler.getFluidInTank(0);
        final @Nullable Grade grade = gradeOf(held);
        if (grade == null)
        {
            return false;
        }

        final int room = (int) (ConverterBath.MILLIBUCKETS - bath.metal);
        final FluidStack poured = room <= 0 ? FluidStack.EMPTY : handler.drain(room, IFluidHandler.FluidAction.EXECUTE);
        if (poured.isEmpty())
        {
            return false;
        }

        final @Nullable IHeat heat = HeatCapability.get(single);
        bath.add(poured.getAmount(), grade, heat == null ? bath.temperature : heat.getTemperature());
        setChanged();
        hand(stack, player, hand, handler.getContainer());
        return true;
    }

    /** Runs the bath into one held mould or container, along with its heat. A stack is only ever filled one at a time, as its capability would fill all of them for the price of one. */
    private void tapInto(ItemStack stack, Player player, InteractionHand hand)
    {
        if (bath.metal < 1f)
        {
            return;
        }
        if (!bath.isMolten())
        {
            player.displayClientMessage(Component.translatable("tfcpp.converter.set"), true);
            return;
        }

        final ItemStack single = stack.copyWithCount(1);
        final @Nullable IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        final Fluid fluid = fluidOf(bath.tapGrade());
        final int filled = handler == null ? 0 : handler.fill(new FluidStack(fluid, (int) bath.metal), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0)
        {
            return;
        }
        final float temperature = bath.temperature;
        bath.tap(filled);
        setChanged();

        final @Nullable IHeat heat = HeatCapability.get(single);
        if (heat != null)
        {
            heat.addTemperatureFromSourceWithHeatCapacity(temperature, FluidHeat.getOrUnknown(new FluidStack(fluid, filled)).heatCapacity(filled));
        }
        hand(stack, player, hand, handler.getContainer());
    }

    private static void hand(ItemStack stack, Player player, InteractionHand hand, ItemStack result)
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

    public void showStatus(Player player)
    {
        final String lime = String.format(Locale.ROOT, "%.1f", bath.lime());
        if (bath.metal < 1f)
        {
            player.displayClientMessage(Component.translatable("tfcpp.converter.status_empty", lining(), liningKind(), lime), false);
            if (!bath.basic && basicBricks > 0)
            {
                player.displayClientMessage(Component.translatable("tfcpp.converter.basic_progress", basicBricks, BASIC_BRICKS_PER_LINING), false);
            }
            return;
        }
        final Component contents = new FluidStack(fluidOf(bath.tapGrade()), 1).getHoverName();
        final Component state = bath.isMolten() ? Component.translatable("tfcpp.converter.flame." + bath.flame().name().toLowerCase(Locale.ROOT)) : Component.translatable("tfcpp.converter.set");
        player.displayClientMessage(Component.translatable("tfcpp.converter.status", Math.round(bath.temperature), (int) bath.metal, contents, state, lining(), liningKind(), lime), false);
    }

    private Component liningKind()
    {
        return Component.translatable(bath.basic ? "tfcpp.converter.lining.basic" : "tfcpp.converter.lining.acid");
    }

    public boolean isScrap()
    {
        return liningWorn / LINING_LIFE > LINING_SCRAP;
    }

    private Component lining()
    {
        final float worn = liningWorn / LINING_LIFE;
        return Component.translatable(worn > LINING_THIN ? "tfcpp.converter.lining.thin" : worn > LINING_SCRAP ? "tfcpp.converter.lining.worn" : "tfcpp.converter.lining.good");
    }

    /** Breaking the vessel lets it all out. Hot iron sets the floor alight and burns anyone standing there, and what is left sets into ingots once it is cold. */
    public void spill()
    {
        assert level != null;
        // Slag that was not yet skimmed comes out whole piece by piece, and what is less than a piece is lost with the vessel
        final int thomas = (int) (bath.thomasSlagPhosphorus / PHOSPHORUS_PER_THOMAS_PIECE);
        if (thomas > 0)
        {
            Helpers.spawnItem(level, worldPosition, new ItemStack(Metallurgy.THOMAS_SLAG.get(), thomas));
        }
        bath.thomasSlagPhosphorus = 0f;
        if (bath.metal <= 0f)
        {
            return;
        }
        final boolean molten = bath.isMolten();

        final int ingots = (int) (bath.metal / MILLIBUCKETS_PER_INGOT);
        if (ingots > 0)
        {
            final ItemStack metal = new ItemStack(ingotOf(bath.tapGrade()), ingots);
            HeatCapability.setTemperature(metal, bath.temperature);
            Helpers.spawnItem(level, worldPosition, metal);
        }
        final int slag = (int) ((bath.slag + (bath.metal - ingots * MILLIBUCKETS_PER_INGOT)) / IronSmelting.MILLIBUCKETS_PER_SLAG);
        if (slag > 0)
        {
            Helpers.spawnItem(level, worldPosition, new ItemStack(IronSmelting.slagOf(bath.phosphorus > ConverterBath.PHOSPHORUS_LIMIT_PERCENT), slag));
        }
        bath.metal = 0f;
        bath.slag = 0f;

        if (molten)
        {
            for (Direction direction : Direction.Plane.HORIZONTAL)
            {
                final BlockPos side = worldPosition.relative(direction);
                if (level.getBlockState(side).canBeReplaced() && !level.getBlockState(side).liquid() && Blocks.FIRE.defaultBlockState().canSurvive(level, side))
                {
                    level.setBlockAndUpdate(side, Blocks.FIRE.defaultBlockState());
                }
            }
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(SPILL_BURN_RADIUS)))
            {
                entity.igniteForSeconds(SPILL_BURN_SECONDS);
            }
        }
    }

    private static Item ingotOf(Grade grade)
    {
        final Metal metal = switch (grade)
        {
            case WROUGHT_IRON -> Metal.WROUGHT_IRON;
            case STEEL -> Metal.STEEL;
            case HIGH_CARBON_STEEL -> Metal.HIGH_CARBON_STEEL;
            case CAST_IRON -> Metal.CAST_IRON;
            case PIG_IRON -> Metal.PIG_IRON;
            case PHOSPHORIC_PIG_IRON -> null;
            case COLD_SHORT_IRON -> null;
        };
        if (metal == null)
        {
            return grade == Grade.PHOSPHORIC_PIG_IRON ? Metallurgy.PHOSPHORIC_PIG_IRON_INGOT.get() : Metallurgy.COLD_SHORT_IRON_INGOT.get();
        }
        return TFCItems.METAL_ITEMS.get(metal).get(Metal.ItemType.INGOT).asItem();
    }

    private static Fluid fluidOf(Grade grade)
    {
        return BuiltInRegistries.FLUID.get(ResourceLocation.parse(grade.fluid()));
    }

    @Nullable
    private static Grade gradeOf(FluidStack fluid)
    {
        return fluid.isEmpty() ? null : Grade.ofFluid(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
    }

    @Override
    public float getTemperature()
    {
        return bath.temperature;
    }

    /** Mixes in the iron that arrived from a block, now that it is known how hot it was. */
    private void mixArrived(float temperature)
    {
        if (bath.metal > 0f)
        {
            final float share = Math.min(awaitingHeat / bath.metal, 1f);
            bath.temperature += (temperature - bath.temperature) * share;
        }
        awaitingHeat = 0f;
    }

    /** Devices report their temperature every tick, even when cold. Only something hotter than the iron can heat it. */
    @Override
    public void setTemperature(float temperature)
    {
        final float limited = Math.min(temperature, MOST_HEAT_FROM_OUTSIDE);
        if (awaitingHeat > 0f)
        {
            mixArrived(limited);
        }
        if (limited > bath.temperature)
        {
            heatFromOutside = limited;
            heatStabilityTicks = HEAT_STABILITY_TICKS;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        tag.putFloat("metal", bath.metal);
        tag.putFloat("carbon", bath.carbon);
        tag.putFloat("silicon", bath.silicon);
        tag.putFloat("phosphorus", bath.phosphorus);
        tag.putFloat("lime", bath.lime());
        tag.putBoolean("basic", bath.basic);
        tag.putInt("basicBricks", basicBricks);
        tag.putFloat("silicaWithoutLime", bath.silicaWithoutLime);
        tag.putFloat("thomasSlag", bath.thomasSlagPhosphorus);
        tag.putFloat("temperature", bath.temperature);
        tag.putFloat("slag", bath.slag);
        tag.putFloat("lining", liningWorn);
        tag.putFloat("awaitingHeat", awaitingHeat);
        tag.putLong("nextStep", nextStepTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        bath.metal = tag.getFloat("metal");
        bath.carbon = tag.getFloat("carbon");
        bath.silicon = tag.getFloat("silicon");
        bath.phosphorus = tag.getFloat("phosphorus");
        bath.restoreLime(tag.getFloat("lime"));
        bath.basic = tag.getBoolean("basic");
        basicBricks = tag.getInt("basicBricks");
        bath.silicaWithoutLime = tag.getFloat("silicaWithoutLime");
        bath.thomasSlagPhosphorus = tag.getFloat("thomasSlag");
        bath.temperature = tag.getFloat("temperature");
        bath.slag = tag.getFloat("slag");
        liningWorn = tag.getFloat("lining");
        awaitingHeat = tag.getFloat("awaitingHeat");
        nextStepTick = tag.contains("nextStep") ? tag.getLong("nextStep") : -1L;
    }

    /** What pours into the vessel from above, as a blast furnace does, and out through a tap. */
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
            return bath.metal < 1f ? FluidStack.EMPTY : new FluidStack(fluidOf(bath.tapGrade()), (int) bath.metal);
        }

        @Override
        public int getTankCapacity(int tank)
        {
            return (int) ConverterBath.MILLIBUCKETS;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack)
        {
            return gradeOf(stack) != null;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action)
        {
            final @Nullable Grade grade = gradeOf(resource);
            if (grade == null)
            {
                return 0;
            }
            final int amount = Math.min(resource.getAmount(), (int) (ConverterBath.MILLIBUCKETS - bath.metal));
            if (amount > 0 && action.execute())
            {
                bath.add(amount, grade, bath.temperature);
                awaitingHeat += amount;
                setChanged();
            }
            return amount;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action)
        {
            final FluidStack held = getFluidInTank(0);
            return !held.isEmpty() && FluidStack.isSameFluidSameComponents(held, resource) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action)
        {
            if (!bath.isMolten())
            {
                return FluidStack.EMPTY;
            }
            final Fluid fluid = fluidOf(bath.tapGrade());
            final int amount = Math.min(maxDrain, (int) bath.metal);
            if (amount <= 0)
            {
                return FluidStack.EMPTY;
            }
            if (action.execute())
            {
                bath.tap(amount);
                setChanged();
            }
            return new FluidStack(fluid, amount);
        }
    }
}
