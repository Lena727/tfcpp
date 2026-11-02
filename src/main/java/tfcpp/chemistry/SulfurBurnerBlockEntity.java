package tfcpp.chemistry;

import java.util.Locale;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Helpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.pipes.PipeContents;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipeTracker;

/**
 * The sulfur held in a sulfur burner, and the burning of it in the air of its network, which {@link SulfurBurning} works out. The burner is lit by the hot flue: a burner holding sulfur in air that has oxygen in it burns, and no one needs to light it.
 */
public final class SulfurBurnerBlockEntity extends BlockEntity
{
    /** Powders of sulfur that the burner holds, at most. */
    public static final int CAPACITY_POWDERS = 64;

    /** A fraction smaller than this, in moles, is float noise and not sulfur. */
    private static final float EPSILON = 1e-3f;

    private static final float CAPACITY_MOLES = CAPACITY_POWDERS * SulfurBurning.MOLES_PER_POWDER;

    /** Sulfur waiting to burn, in moles. */
    private float sulfurMoles;

    public SulfurBurnerBlockEntity(BlockPos pos, BlockState state)
    {
        super(Chemistry.SULFUR_BURNER_ENTITY.get(), pos, state);
    }

    /** @return sulfur waiting to burn, in moles */
    public float sulfurMoles()
    {
        return sulfurMoles;
    }

    /**
     * Burns sulfur for one step of the network it is in. The oxygen of the air is used up as the sulfur takes it, and the sulfur dioxide and the nitrogen of the air go into the gas.
     *
     * @param network the network that this burner is part of
     * @param seconds the length of the step
     */
    public void step(PipeNetwork network, float seconds)
    {
        if (sulfurMoles <= 0f)
        {
            return;
        }
        final PipeContents contents = network.contents();
        final SulfurBurning.Result result = SulfurBurning.burn(sulfurMoles, contents.air, contents.gasMoles(), seconds);
        if (result.sulfurBurned() <= 0f)
        {
            return;
        }
        sulfurMoles = Math.max(0f, sulfurMoles - result.sulfurBurned());
        contents.air = Math.max(0f, contents.air - result.air());
        contents.gases[Gas.SULFUR_DIOXIDE.ordinal()] += result.sulfurDioxide();
        contents.gases[Gas.FLUE_NITROGEN.ordinal()] += result.nitrogenLeftBehind();
        network.addHeat(result.heatKilojoules());
        setChanged();
    }

    /** @return {@code true} if the powder was taken into the burner, which uses one of the stack */
    public boolean addPowder(ItemStack stack, Player player)
    {
        if (sulfurMoles + SulfurBurning.MOLES_PER_POWDER > CAPACITY_MOLES + EPSILON)
        {
            player.displayClientMessage(Component.translatable("tfcpp.sulfur_burner.full"), true);
            return false;
        }
        sulfurMoles += SulfurBurning.MOLES_PER_POWDER;
        stack.shrink(1);
        setChanged();
        return true;
    }

    /** Reports the sulfur in the burner, and whether the air of its network can burn it, to a person with an empty hand. */
    public void showStatus(Player player)
    {
        player.displayClientMessage(Component.translatable("tfcpp.sulfur_burner.status", wholePowders(), CAPACITY_POWDERS), false);
        final @Nullable PipeNetwork network = networkOf();
        if (network == null)
        {
            player.displayClientMessage(Component.translatable("tfcpp.sulfur_burner.no_network"), false);
            return;
        }
        final PipeContents contents = network.contents();
        final float moles = contents.gasMoles();
        if (sulfurMoles > 0f && (moles <= 0f || contents.air * ChamberProcess.OXYGEN_IN_AIR / moles < SulfurBurning.LEAST_OXYGEN_SHARE))
        {
            player.displayClientMessage(Component.translatable("tfcpp.sulfur_burner.no_air", String.format(Locale.ROOT, "%.0f", SulfurBurning.LEAST_OXYGEN_SHARE * 100f)), false);
        }
    }

    /** Breaking the burner drops its whole powders as sulfur. What is less than a powder is lost with it. */
    public void dropContents()
    {
        final int powders = wholePowders();
        if (powders > 0 && level != null)
        {
            Helpers.spawnItem(level, worldPosition, new ItemStack(sulfurItem(), powders));
        }
        sulfurMoles = 0f;
    }

    private int wholePowders()
    {
        return (int) Math.floor(sulfurMoles / SulfurBurning.MOLES_PER_POWDER + EPSILON / SulfurBurning.MOLES_PER_POWDER);
    }

    private @Nullable PipeNetwork networkOf()
    {
        return level instanceof ServerLevel server ? PipeTracker.get(server).networkAt(worldPosition) : null;
    }

    /** @return the sulfur powder, which is the same item the ore gives when it is ground */
    public static Item sulfurItem()
    {
        return TFCItems.ORE_POWDERS.get(Ore.SULFUR).asItem();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.saveAdditional(tag, provider);
        tag.putFloat("sulfur", sulfurMoles);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {
        super.loadAdditional(tag, provider);
        sulfurMoles = tag.getFloat("sulfur");
    }
}
