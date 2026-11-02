package tfcpp.chemistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import tfcpp.core.TFCpp;

/**
 * A splash of corrosive liquid that has run out of a broken chamber, a burst pipe or an overflow. It is the acid on the skin and the fume that comes off it, and it does not stay where it fell: anyone within a few blocks is burned.
 */
public final class CorrosiveSpill
{
    /** Its own damage, so that the death says what killed: acid burns, and is not a blow. Armour gives some protection, as it does against a splash. */
    public static final ResourceKey<DamageType> CORROSIVE = ResourceKey.create(Registries.DAMAGE_TYPE, TFCpp.id("corrosive"));

    /** A splash reaches this far from the place it came out, in blocks. */
    public static final double RADIUS = 3.0;

    /** Share of full health that a splash takes off anyone within reach. A game figure, not measured: a few hits of a splash are a bad burn. */
    public static final float SHARE_OF_MAX_HEALTH = 0.3f;

    private CorrosiveSpill()
    {
    }

    /**
     * Burns every player within reach of this place and throws up a cloud of it.
     *
     * @param level the level where the liquid came out
     * @param pos the block it came out of
     */
    public static void at(ServerLevel level, BlockPos pos)
    {
        final Vec3 centre = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(RADIUS)))
        {
            if (player.distanceToSqr(centre) <= RADIUS * RADIUS)
            {
                player.hurt(player.damageSources().source(CORROSIVE), player.getMaxHealth() * SHARE_OF_MAX_HEALTH);
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y, centre.z, 20, 0.5, 0.3, 0.5, 0.05);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 1.2f);
    }
}
