package tfcpp.geology;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import tfcpp.core.LocalTime;
import tfcpp.pneumatics.gas.DustSources;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasChunkData;
import tfcpp.pneumatics.gas.GasIgnition;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.Flammability;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * Gas held in coal seams. Coal adsorbs methane on its inner surface, and holds more of it the higher the pressure, following a Langmuir isotherm. Nothing is stored for this until a seam is opened, so an untouched seam costs nothing.
 * <ul>
 *     <li>Breaking coal lets out its fast fraction at once, about a tenth of the total. The rest takes weeks to diffuse out of the lumps.</li>
 *     <li>A newly exposed coal face bleeds the same amount slowly, so gas keeps collecting in a working nobody is in. This is what made old workings dangerous to re-enter.</li>
 *     <li>Fractures next to a seam can hold free gas under pressure, which comes out all at once when mining breaks into them. Near the surface, where air has reached the coal, this is blackdamp and not firedamp, as coal oxidising slowly turns oxygen into CO2.</li>
 * </ul>
 */
public final class SeamGas
{
    public enum Rank
    {
        /** Langmuir volume about 25 m3/t, Langmuir pressure about 2 MPa, for high volatile bituminous coal. Its gas is nearly all methane. */
        BITUMINOUS(25f, 2f, 0.95f),
        /** Low rank coal holds little gas, and that is mostly CO2. */
        LIGNITE(6f, 2f, 0.2f);

        final float langmuirVolume, langmuirPressure, methaneShare;

        Rank(float langmuirVolume, float langmuirPressure, float methaneShare)
        {
            this.langmuirVolume = langmuirVolume;
            this.langmuirPressure = langmuirPressure;
            this.methaneShare = methaneShare;
        }
    }

    private static final float COAL_TONNES_PER_BLOCK = 1.3f;
    /** Hydrostatic pressure in the seam, about 10 kPa per metre of depth. */
    private static final float MEGAPASCALS_PER_METRE = 0.01f;
    private static final float FAST_FRACTION = 0.1f;
    private static final float FACE_DONE_MOLES = 0.01f;

    /** One in this many 4 x 4 x 4 regions of rock next to a seam holds a pocket. */
    private static final int POCKET_RARITY = 64;
    private static final int POCKET_REACH = 2;
    private static final float MIN_POCKET_VOLUME = 4f, MAX_POCKET_VOLUME = 16f;
    /** Air has reached the coal down to about this depth, so what collects there is the product of oxidation and not methane. */
    private static final int BLACKDAMP_DEPTH = 15;
    /** Blackdamp as measured in old workings: air with its oxygen burned out by coal, leaving nitrogen and some CO2. */
    private static final float BLACKDAMP_CARBON_DIOXIDE = 0.13f;

    private static final float SPARK_CHANCE = 0.05f;

    /** @return cubic metres of gas per tonne of coal at this depth */
    public static float adsorbedVolume(Rank rank, float depth)
    {
        final float pressure = Math.max(depth, 0) * MEGAPASCALS_PER_METRE;
        return rank.langmuirVolume * pressure / (pressure + rank.langmuirPressure);
    }

    /** @return moles of gas that come out quickly from one block of coal, at this depth */
    public static float fastMoles(Rank rank, float depth)
    {
        return adsorbedVolume(rank, depth) * COAL_TONNES_PER_BLOCK * GasMath.CELL_MOLES * FAST_FRACTION;
    }

    /** @return moles of free gas in a pocket of this volume, compressed by the rock above it */
    public static float pocketMoles(float volume, float depth)
    {
        return volume * GasMath.CELL_MOLES * (1 + Math.max(depth, 0) / 10f);
    }

    @Nullable
    public static Rank rankOf(BlockState state)
    {
        final Block block = state.getBlock();
        if (block == TFCBlocks.BITUMINOUS_COAL.get()) return Rank.BITUMINOUS;
        if (block == TFCBlocks.LIGNITE.get()) return Rank.LIGNITE;
        return null;
    }

    /**
     * @return metres of rock above a position. A shaft lowers the surface of its own column, so this takes the highest of a few columns around it.
     */
    public static int depth(ServerLevel level, BlockPos pos)
    {
        int surface = pos.getY();
        for (int dx = -8; dx <= 8; dx += 8)
        {
            for (int dz = -8; dz <= 8; dz += 8)
            {
                surface = Math.max(surface, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() + dx, pos.getZ() + dz));
            }
        }
        return surface - pos.getY();
    }

    /** The same as {@link #depth}, but from the columns that are loaded: a face is visited every second, and must not be the reason that a chunk at the edge of the loaded world is loaded. */
    private static int depthLoaded(LevelGasVolume volume, int x, int y, int z)
    {
        int surface = y;
        for (int dx = -8; dx <= 8; dx += 8)
        {
            for (int dz = -8; dz <= 8; dz += 8)
            {
                surface = Math.max(surface, volume.surfaceHeight(x + dx, z + dz));
            }
        }
        return surface - y;
    }

    public static void onBlockBroken(ServerLevel level, BlockPos pos, BlockState state, Player player)
    {
        final @Nullable Rank rank = rankOf(state);
        if (rank != null)
        {
            breakCoal(level, pos, rank);
            DustSources.onCoalMined(level, pos);
        }
        if (rank != null || state.is(TFCTags.Blocks.CAN_COLLAPSE))
        {
            openPocket(level, pos);
        }
        strikeSpark(level, pos, player);
        wakeSealedFaces(level, pos);
    }

    /** Whatever was just dug out may have uncovered a coal face that had been sealed off, and whose chunk has stopped visiting it. */
    private static void wakeSealedFaces(ServerLevel level, BlockPos pos)
    {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction side : Direction.values())
        {
            cursor.setWithOffset(pos, side);
            if (level.isLoaded(cursor))
            {
                final LevelChunk chunk = level.getChunkAt(cursor);
                if (chunk.hasData(GasAttachments.CHUNK) && chunk.getData(GasAttachments.CHUNK).hasBleedingFace())
                {
                    chunk.getData(GasAttachments.CHUNK).setFacesAwake(true);
                    GasTracker.get(level).activate(chunk.getPos().toLong());
                }
            }
        }
    }

    private static void breakCoal(ServerLevel level, BlockPos pos, Rank rank)
    {
        final LevelChunk chunk = level.getChunkAt(pos);
        final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
        final int depth = depth(level, pos);

        final int key = GasChunkData.key(pos.getX(), pos.getY(), pos.getZ());
        final float remaining = data.hasSeamFace(key) ? data.removeSeamFace(key) : fastMoles(rank, depth);
        release(level, pos, rank, remaining);

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction side : Direction.values())
        {
            cursor.setWithOffset(pos, side);
            if (level.isLoaded(cursor))
            {
                final @Nullable Rank neighbour = rankOf(level.getBlockState(cursor));
                if (neighbour != null)
                {
                    final LevelChunk other = level.getChunkAt(cursor);
                    other.getData(GasAttachments.CHUNK).addSeamFace(GasChunkData.key(cursor.getX(), cursor.getY(), cursor.getZ()), fastMoles(neighbour, depth));
                    other.setUnsaved(true);
                    GasTracker.get(level).activate(other.getPos().toLong());
                }
            }
        }
    }

    private static void release(ServerLevel level, BlockPos pos, Rank rank, float moles)
    {
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final @Nullable float[] cell = volume.cellForEmission(pos.getX(), pos.getY(), pos.getZ());
        if (cell != null)
        {
            addAdsorbed(cell, rank, depth(level, pos), moles);
        }
    }

    /** Adds blackdamp, in the shares that a pocket of it is found in. */
    static void addBlackdamp(float[] cell, float moles)
    {
        cell[Gas.CARBON_DIOXIDE.ordinal()] += moles * BLACKDAMP_CARBON_DIOXIDE;
        cell[Gas.FLUE_NITROGEN.ordinal()] += moles * (1 - BLACKDAMP_CARBON_DIOXIDE);
    }

    /**
     * Gas that came out of the coal itself. Where air has reached the seam, above {@link #BLACKDAMP_DEPTH}, the coal has been oxidising for as long as it has lain there and what it gives up is blackdamp, as in the pockets: its methane has gone out through the same cracks that the air came in by.
     */
    static void addAdsorbed(float[] cell, Rank rank, float depth, float moles)
    {
        if (depth < BLACKDAMP_DEPTH)
        {
            addBlackdamp(cell, moles);
        }
        else
        {
            cell[Gas.METHANE.ordinal()] += moles * rank.methaneShare;
            cell[Gas.CARBON_DIOXIDE.ordinal()] += moles * (1 - rank.methaneShare);
        }
    }

    /** Whether a region holds a pocket depends only on the world seed and its position, so nothing has to be generated or stored until it is opened. */
    private static void openPocket(ServerLevel level, BlockPos pos)
    {
        final long hash = regionHash(level.getSeed(), pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        if (Long.remainderUnsigned(hash, POCKET_RARITY) != 0)
        {
            return;
        }

        final @Nullable Rank seam = nearbySeam(level, pos);
        final LevelChunk chunk = level.getChunkAt(pos);
        final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
        final int regionKey = GasChunkData.key(pos.getX() & ~3, pos.getY() & ~3, pos.getZ() & ~3);
        if (seam == null || !data.openPocket(regionKey))
        {
            return;
        }

        final int depth = depth(level, pos);
        final float size = MIN_POCKET_VOLUME + (MAX_POCKET_VOLUME - MIN_POCKET_VOLUME) * ((hash >>> 8 & 255) / 255f);
        final float moles = pocketMoles(size, depth);

        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final @Nullable float[] cell = volume.cellForEmission(pos.getX(), pos.getY(), pos.getZ());
        if (cell != null)
        {
            if (depth < BLACKDAMP_DEPTH || seam == Rank.LIGNITE)
            {
                addBlackdamp(cell, moles);
            }
            else
            {
                cell[Gas.METHANE.ordinal()] += moles;
            }
        }
        chunk.setUnsaved(true);
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.2f, 0.5f); // Gas under pressure rushing out of the rock
    }

    @Nullable
    private static Rank nearbySeam(ServerLevel level, BlockPos pos)
    {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-POCKET_REACH, -POCKET_REACH, -POCKET_REACH), pos.offset(POCKET_REACH, POCKET_REACH, POCKET_REACH)))
        {
            if (level.isLoaded(near))
            {
                final @Nullable Rank rank = rankOf(level.getBlockState(near));
                if (rank != null)
                {
                    return rank;
                }
            }
        }
        return null;
    }

    private static long regionHash(long seed, int x, int y, int z)
    {
        long hash = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B3F2C5A4F1D9B7L) ^ (z * 0xD6E8FEB86659FD93L);
        hash ^= hash >>> 32;
        hash *= 0xD6E8FEB86659FD93L;
        return hash ^ (hash >>> 32);
    }

    /** Steel striking rock throws sparks hot enough to light firedamp. Bronze does not, which is why non-sparking tools are still made of copper alloys. */
    private static void strikeSpark(ServerLevel level, BlockPos pos, Player player)
    {
        if (!isFerrous(player) || level.random.nextFloat() >= SPARK_CHANCE)
        {
            return;
        }
        // The block is still solid when this runs, so the air the spark flies into is around it
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction side : Direction.values())
        {
            cursor.setWithOffset(pos, side);
            final @Nullable float[] cell = volume.cell(cursor.getX(), cursor.getY(), cursor.getZ());
            if (cell != null && Flammability.isFlammable(cell))
            {
                GasTracker.get(level).queueIgnition(cursor.immutable());
                return;
            }
        }
    }

    private static boolean isFerrous(Player player)
    {
        final String tool = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
        return tool.contains("iron") || tool.contains("steel");
    }

    /**
     * Bleeds gas from one exposed face.
     *
     * @return what is left in the face. The face stays recorded when empty, as otherwise mining next to it would count its gas a second time.
     */
    public static float bleedFace(LevelGasVolume volume, ServerLevel level, int x, int y, int z, float moles, float seconds)
    {
        if (moles <= 0)
        {
            return 0;
        }
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
        final @Nullable Rank rank = rankOf(level.getBlockState(cursor));
        if (rank == null)
        {
            return -1;
        }
        for (Direction side : Direction.values())
        {
            final int nx = x + side.getStepX(), ny = y + side.getStepY(), nz = z + side.getStepZ();
            if (volume.isPassable(nx, ny, nz))
            {
                // A face bleeds its fast fraction with a time constant of one day of slow time
                final float bled = moles <= FACE_DONE_MOLES ? moles : moles * (1 - (float) Math.exp(-seconds / LocalTime.SECONDS_PER_DAY));
                final @Nullable float[] cell = volume.cellForEmission(nx, ny, nz);
                if (cell != null)
                {
                    addAdsorbed(cell, rank, depthLoaded(volume, x, y, z), bled);
                    return moles - bled;
                }
            }
        }
        return moles; // Sealed behind other blocks again, so it keeps its gas
    }

    private SeamGas() {}
}
