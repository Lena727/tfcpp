package tfcpp.pneumatics.pipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import tfcpp.core.TFCpp;

/**
 * The steam that a burst boiler throws out: hot water held under pressure flashes to steam the moment the wall gives way, and the cloud of it scalds whoever stands in reach, as it did the stokers of boilers that burst. Like a splash of acid, it does not stay where it came out.
 */
public final class SteamBurst
{
    /** Its own damage, so that the death says what killed: a scald. Armour gives some protection, as against a splash. */
    public static final ResourceKey<DamageType> SCALD = ResourceKey.create(Registries.DAMAGE_TYPE, TFCpp.id("scald"));

    /** The cloud reaches this far from the hole, in blocks: a little more than a splash of acid, as the volume of steam is far greater. A game figure. */
    public static final double RADIUS = 4.0;

    private static final int PARTICLES = 60;

    private SteamBurst()
    {
    }

    /**
     * Scalds every creature within reach of this place and throws up a cloud of steam.
     *
     * @param level the level where the boiler burst
     * @param pos the block it burst at
     * @param flashKilojoules the flash energy of its water, which sets how much of their health it takes from them
     */
    public static void at(ServerLevel level, BlockPos pos, float flashKilojoules)
    {
        final Vec3 centre = Vec3.atCenterOf(pos);
        final float share = PipePhysics.scaldShare(flashKilojoules);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(RADIUS)))
        {
            if (entity.distanceToSqr(centre) <= RADIUS * RADIUS)
            {
                entity.hurt(entity.damageSources().source(SCALD), entity.getMaxHealth() * share);
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y, centre.z, PARTICLES, 1.0, 0.6, 1.0, 0.08);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.4f, 0.6f);
    }
}
