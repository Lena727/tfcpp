package tfcpp.metallurgy;

import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A person blowing air into a hearth through an empty blowpipe, clay or iron: the first forced draught, before bellows, which need lumber and so a saw.
 * Held in use while looking at a charcoal forge or a firepit, or at a vessel standing on one (see {@link #hearthAt}), it gives {@link Draught#BLOWER_AIR} for the next {@link DraughtState#BLOW_TICKS} ticks, and it is as tiring as running.
 */
public final class Blowing
{
    /** Exhaustion for each tick of blowing: the same as sprinting. */
    public static final float EXHAUSTION_PER_TICK = 0.02f;

    /** @return {@code true} for an empty blowpipe. One with glass on it is at its own work, and is no use for blowing on a fire. */
    public static boolean isBlowpipe(ItemStack stack)
    {
        return stack.is(TFCItems.BLOWPIPE.get()) || stack.is(TFCItems.CERAMIC_BLOWPIPE.get());
    }

    /** Vessels that can stand on a vessel that stands on a hearth: a retort on a sand bath on a firepit. */
    private static final int MOST_STACKED = 6;

    /**
     * @return the hearth that a person looking at this block blows into: the block itself, or the hearth under whatever is heated from below, as a crucible, and any of our vessels that take heat through {@code IHeatConsumer}: a zinc retort, a coke oven, a glass retort, a sand bath, a cupel hearth, a converter. A sand bath passes the heat on, so a retort on a bath on a firepit is blown on through both.
     */
    @Nullable
    public static Hearth hearthAt(Level level, BlockPos pos)
    {
        final BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int step = 0; step < MOST_STACKED; step++)
        {
            final @Nullable BlockEntity entity = level.getBlockEntity(cursor);
            if (entity instanceof Hearth hearth)
            {
                return hearth;
            }
            if (!(entity instanceof CrucibleBlockEntity || entity instanceof IHeatConsumer))
            {
                return null;
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isBlowpipe(event.getItem()))
        {
            return;
        }
        final HitResult hit = player.pick(player.blockInteractionRange(), 1f, false);
        if (hit.getType() != HitResult.Type.BLOCK)
        {
            return;
        }
        final ServerLevel level = player.serverLevel();
        final BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        final @Nullable Hearth hearth = hearthAt(level, pos);
        if (hearth == null)
        {
            return;
        }
        hearth.tfcpp$draught().blow(player.getUUID(), level.getGameTime());
        player.causeFoodExhaustion(EXHAUSTION_PER_TICK);
        if (level.getGameTime() % 7 == 0)
        {
            level.sendParticles(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.15, 0.05, 0.15, 0.01);
        }
    }

    private Blowing() {}
}
