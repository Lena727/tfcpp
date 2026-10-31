package tfcpp.pneumatics.gas;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.common.blocks.devices.PitKilnBlock;
import net.dries007.tfc.common.recipes.CollapseRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import tfcpp.core.ServerConfig;
import tfcpp.core.TFCpp;
import tfcpp.pneumatics.instruments.DavyLampBlock;

/** Where a flame comes from, and what a {@link Deflagration} does to the world around it. */
public final class GasIgnition
{
    public static final TagKey<Block> OPEN_FLAME_BLOCKS = TagKey.create(Registries.BLOCK, TFCpp.id("open_flames"));
    public static final TagKey<Item> OPEN_FLAME_ITEMS = TagKey.create(Registries.ITEM, TFCpp.id("open_flames"));
    public static final ResourceKey<DamageType> BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, TFCpp.id("blast"));

    /** Eardrums rupture from about 35 kPa. Lung damage is usually fatal from about 400 kPa. */
    private static final float INJURY_KPA = 35f, FATAL_KPA = 400f;
    /** Rock mass is not broken by a gas explosion, but a blast does bring down a roof that is already unsupported. */
    private static final float ROOF_FALL_KPA = 100f;
    private static final int MAX_ROOF_FALLS = 8;
    private static final float FLAME_BURN = 0.25f;
    private static final int FLAME_BURN_SECONDS = 5;
    /** Each of these is a packet to every player nearby, so a large flame shows only part of its cells. */
    private static final int MAX_PARTICLE_CELLS = 64;

    public static boolean isOpenFlame(BlockState state)
    {
        if (!state.is(OPEN_FLAME_BLOCKS) || state.getBlock() instanceof DavyLampBlock)
        {
            return false;
        }
        if (state.hasProperty(PitKilnBlock.STAGE))
        {
            return state.getValue(PitKilnBlock.STAGE) == PitKilnBlock.LIT;
        }
        if (state.hasProperty(BlockStateProperties.LIT))
        {
            return state.getValue(BlockStateProperties.LIT);
        }
        return !state.hasProperty(CharcoalForgeBlock.HEAT) || state.getValue(CharcoalForgeBlock.HEAT) > 0;
    }

    /** @return the pressure in kPa that breaks a block of this blast resistance: glass about 9, wood about 40, and stone or brick about 77, close to the damage tables for blast loads. */
    public static float breakingPressure(float blastResistance)
    {
        return 5f + 12f * blastResistance;
    }

    /** @return share of a body's health taken by a blast of this pressure */
    public static float blastInjury(float kilopascals)
    {
        return Math.clamp((kilopascals - INJURY_KPA) / (FATAL_KPA - INJURY_KPA), 0f, 1f);
    }

    public static void ignite(ServerLevel level, int x, int y, int z)
    {
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final Deflagration.Result result = Deflagration.run(volume, x, y, z, ServerConfig.GAS_FLAME_CELLS.getAsInt(), ServerConfig.GAS_BLAST_CELLS.getAsInt());
        if (result.burnedCells() == 0)
        {
            return;
        }

        final float loudness = Math.min(4f + result.kilojoules() / 2000f, 32f);
        level.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, loudness, 0.7f + level.random.nextFloat() * 0.2f);

        final Long2ObjectOpenHashMap<Deflagration.Shock> byPosition = new Long2ObjectOpenHashMap<>();
        AABB area = new AABB(new BlockPos(x, y, z));
        int particlesLeft = MAX_PARTICLE_CELLS;
        for (Deflagration.Shock shock : result.shocks())
        {
            final BlockPos pos = new BlockPos(shock.x(), shock.y(), shock.z());
            byPosition.put(pos.asLong(), shock);
            area = area.minmax(new AABB(pos));
            if (shock.burned() && particlesLeft-- > 0)
            {
                level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.01);
            }
        }

        final Vec3 center = new Vec3(x + 0.5, y + 0.5, z + 0.5);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area))
        {
            final Deflagration.Shock shock = byPosition.get(BlockPos.containing(entity.getEyePosition()).asLong());
            if (shock != null)
            {
                hurt(level, entity, shock, center);
            }
        }

        breakBlocks(level, result);
    }

    private static void hurt(ServerLevel level, LivingEntity entity, Deflagration.Shock shock, Vec3 center)
    {
        final float injury = blastInjury(shock.kilopascals()) + (shock.burned() ? FLAME_BURN : 0);
        if (injury > 0)
        {
            entity.hurt(entity.damageSources().source(BLAST), entity.getMaxHealth() * injury);
        }
        if (shock.burned())
        {
            entity.igniteForSeconds(FLAME_BURN_SECONDS);
        }

        final Vec3 away = entity.position().subtract(center);
        if (away.lengthSqr() > 1e-4)
        {
            entity.push(away.normalize().scale(Math.min(shock.kilopascals() / 100f, 1.5f)));
            entity.hurtMarked = true;
        }
    }

    private static void breakBlocks(ServerLevel level, Deflagration.Result result)
    {
        final LongOpenHashSet checked = new LongOpenHashSet();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int blocksLeft = ServerConfig.GAS_BLAST_BLOCKS.getAsInt();
        int roofFallsLeft = MAX_ROOF_FALLS;

        for (Deflagration.Shock shock : result.shocks())
        {
            final BlockPos pos = new BlockPos(shock.x(), shock.y(), shock.z());
            if (blocksLeft > 0 && breakIfWeak(level, pos, shock.kilopascals(), checked))
            {
                blocksLeft--;
            }
            for (Direction side : Direction.values())
            {
                cursor.setWithOffset(pos, side);
                if (blocksLeft > 0 && breakIfWeak(level, cursor, shock.kilopascals(), checked))
                {
                    blocksLeft--;
                }
            }
            if (shock.kilopascals() >= ROOF_FALL_KPA && roofFallsLeft > 0 && level.random.nextInt(8) == 0)
            {
                roofFallsLeft--;
                CollapseRecipe.tryTriggerCollapse(level, pos);
            }
        }
    }

    private static boolean breakIfWeak(ServerLevel level, BlockPos pos, float kilopascals, LongOpenHashSet checked)
    {
        if (!checked.add(pos.asLong()) || !level.isLoaded(pos))
        {
            return false;
        }
        final BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0 || state.is(TFCTags.Blocks.CAN_COLLAPSE))
        {
            return false;
        }
        return kilopascals >= breakingPressure(state.getBlock().getExplosionResistance()) && level.destroyBlock(pos, true);
    }

    private GasIgnition() {}
}
