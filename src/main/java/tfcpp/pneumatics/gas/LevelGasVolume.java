package tfcpp.pneumatics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import tfcpp.core.ServerConfig;
import tfcpp.ecology.RegionalAir;
import tfcpp.geology.SeamGas;

/**
 * Connects the simulation to a level. Gas never crosses into an unloaded chunk, so nothing is lost or duplicated when chunks unload: the gas simply waits.
 */
public final class LevelGasVolume implements GasVolume
{
    private static final int MAX_FLUE = 256;
    private static final TagKey<Block> GLASS_PANES = Tags.Blocks.GLASS_PANES;

    private final ServerLevel level;
    private final GasTracker tracker;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelGasVolume(ServerLevel level, GasTracker tracker)
    {
        this.level = level;
        this.tracker = tracker;
    }

    @Nullable
    private LevelChunk chunk(int x, int z)
    {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4);
    }

    @Override
    @Nullable
    public float[] cell(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk == null || !chunk.hasData(GasAttachments.CHUNK))
        {
            return null;
        }
        return chunk.getData(GasAttachments.CHUNK).get(GasChunkData.key(x, y, z));
    }

    @Override
    @Nullable
    public float[] createCell(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk == null)
        {
            return null;
        }
        if (chunk.getData(GasAttachments.CHUNK).size() >= ServerConfig.GAS_CELLS_PER_CHUNK.getAsInt())
        {
            return null;
        }
        return createCell(chunk, x, y, z);
    }

    /**
     * @return the cell at this position, created if needed. For new gas entering the world: the cell budget limits how far gas spreads, and must not make it vanish.
     */
    @Nullable
    public float[] cellForEmission(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        return chunk == null ? null : createCell(chunk, x, y, z);
    }

    private float[] createCell(LevelChunk chunk, int x, int y, int z)
    {
        tracker.activate(chunk.getPos().toLong());
        chunk.setUnsaved(true);
        return chunk.getData(GasAttachments.CHUNK).getOrCreate(GasChunkData.key(x, y, z));
    }

    @Override
    public void markChanged(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk != null)
        {
            chunk.setUnsaved(true);
            if (chunk.hasData(GasAttachments.CHUNK))
            {
                // Gas may have come into a chunk that was asleep because it held only settled dust
                chunk.getData(GasAttachments.CHUNK).wake();
                tracker.activate(chunk.getPos().toLong());
            }
        }
    }

    @Override
    public void removeCell(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk != null && chunk.hasData(GasAttachments.CHUNK))
        {
            chunk.getData(GasAttachments.CHUNK).remove(GasChunkData.key(x, y, z));
            chunk.setUnsaved(true);
        }
    }

    @Override
    public void deposit(int x, int y, int z, Gas gas, float moles)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk != null)
        {
            chunk.getData(GasAttachments.CHUNK).addDeposit(gas, moles, GasChunkData.key(x, y, z));
            chunk.setUnsaved(true);
            if (gas.evaporatesFromDeposit())
            {
                tracker.activate(chunk.getPos().toLong()); // A film gives its vapour off for as long as it lies there, and nothing else keeps the chunk looked at
            }
        }
    }

    @Override
    public float evaporate(int x, int y, int z, Gas gas, float wanted)
    {
        final LevelChunk chunk = chunk(x, z);
        return chunk == null ? 0 : chunk.getData(GasAttachments.CHUNK).takeFilm(gas, wanted, GasChunkData.key(x, y, z));
    }

    @Override
    public boolean holdsFilm(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        return chunk != null && chunk.getData(GasAttachments.CHUNK).holdsFilm(GasChunkData.key(x, y, z));
    }

    @Override
    public boolean isPassable(int x, int y, int z)
    {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight())
        {
            return false;
        }
        final LevelChunk chunk = chunk(x, z);
        if (chunk == null)
        {
            return false;
        }
        cursor.set(x, y, z);
        return isPassable(chunk.getBlockState(cursor), cursor);
    }

    private boolean isPassable(BlockState state, BlockPos pos)
    {
        if (state.isAir())
        {
            return true;
        }
        if (!state.getFluidState().isEmpty())
        {
            return false; // Gas does not displace liquid, this is also what makes water a seal
        }
        final Block block = state.getBlock();
        if (block instanceof DoorBlock)
        {
            return state.getValue(DoorBlock.OPEN);
        }
        if (block instanceof TrapDoorBlock)
        {
            return state.getValue(TrapDoorBlock.OPEN);
        }
        if (block instanceof SlabBlock || block instanceof StairBlock)
        {
            return false; // Not worth a cell of their own: a roof of slabs or stairs is a roof
        }
        return !state.is(GLASS_PANES) && !state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Zero in a position with a roof over it. In an open column it is {@link GasMath#openAirExchange(float)} of how far the position lies below the surface around it, so the air in a well or a bell pit is nearly still, and a heavy gas collects at the bottom of it.
     */
    @Override
    public float atmosphereExchangeRate(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        if (chunk == null)
        {
            return 0;
        }
        final int own = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        if (y < own)
        {
            return 0;
        }
        // The heightmap already tells whether the column has a roof. The same lookup in the four columns next to it finds the ground that a shaft was cut through, and the surface of the shaft's mouth is where the wind blows
        int surface = own;
        surface = Math.max(surface, surfaceAt(chunk, x + 1, z));
        surface = Math.max(surface, surfaceAt(chunk, x - 1, z));
        surface = Math.max(surface, surfaceAt(chunk, x, z + 1));
        surface = Math.max(surface, surfaceAt(chunk, x, z - 1));
        return GasMath.openAirExchange(surface - y);
    }

    /** @return the height of the ground in this column as the heightmap has it (the first position with nothing solid above it), or the lowest value if the chunk is not loaded. Asks only the chunk that is loaded: the level would load one. */
    public int surfaceHeight(int x, int z)
    {
        final @Nullable LevelChunk chunk = chunk(x, z);
        return chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    private int surfaceAt(LevelChunk home, int x, int z)
    {
        final @Nullable LevelChunk chunk = x >> 4 == home.getPos().x && z >> 4 == home.getPos().z ? home : chunk(x, z);
        return chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    /**
     * @return the air that a living thing or an instrument at this position is in: the cell of the grid, if there is one, and where the position exchanges with the open air, the larger of the cell's amount and the regional air's of each gas that the regional air carries (a sleeping cell of settled dust outdoors holds none of the smog that is around it). {@code null} if there is nothing but clean air. Allocates only when the regional air adds something.
     */
    @Nullable
    public float[] breathableAir(int x, int y, int z)
    {
        final @Nullable float[] own = cell(x, y, z);
        final @Nullable tfcpp.ecology.AirGrid.Cell region = RegionalAir.get(level).cellAt(x, z);
        if (region == null || !region.hasAir() || atmosphereExchangeRate(x, y, z) <= 0f)
        {
            return own;
        }
        @Nullable float[] air = null;
        for (tfcpp.ecology.AirSpecies species : tfcpp.ecology.AirSpecies.VALUES)
        {
            final int i = species.gas().ordinal();
            final float outside = (float) region.molesPerCubicMetre(species);
            if (outside > (own == null ? 0f : own[i]))
            {
                if (air == null)
                {
                    air = own == null ? new float[Gas.COUNT] : own.clone();
                }
                air[i] = outside;
            }
        }
        return air != null ? air : own;
    }

    /** @return the height of the top of the flue that stands over this height of an open column: it climbs while the four sides of the column are solid, and stops where one of them is not or the column is closed above. A column with no walls has its rim where the gas came out. */
    public int flueRim(int x, int y, int z)
    {
        int rim = y;
        for (int climbed = 0; climbed < MAX_FLUE && isPassable(x, rim + 1, z); climbed++)
        {
            if (isPassable(x + 1, rim, z) || isPassable(x - 1, rim, z) || isPassable(x, rim, z + 1) || isPassable(x, rim, z - 1))
            {
                break;
            }
            rim++;
        }
        return rim;
    }

    /** Gas that has come out of an outlet under open sky, for the regional air. */
    public void releaseToSky(int x, int foot, int rim, int z, float[] gases)
    {
        RegionalAir.get(level).emit(level, x, foot, rim, z, gases);
    }

    @Override
    public Atmosphere atmosphere(int x, int z)
    {
        return RegionalAir.get(level).atmosphere(x, z);
    }

    /** Uses the heightmap and not sky light, which lags behind block changes. Leaves do not hold gas in. */
    public boolean isOpenToSky(int x, int y, int z)
    {
        final LevelChunk chunk = chunk(x, z);
        return chunk != null && y >= chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    /** @return how many faces were looked at, which counts against the budget like a cell */
    private int bleedSeamFaces(LevelChunk chunk, GasChunkData data, int originX, int originZ, float seconds)
    {
        final int[] faces = data.seamFaceKeys();
        boolean anyBled = false;
        for (int key : faces)
        {
            final float moles = data.seamFace(key);
            final float left = SeamGas.bleedFace(this, level, originX + GasChunkData.keyX(key), GasChunkData.keyY(key), originZ + GasChunkData.keyZ(key), moles, seconds);
            if (left < 0)
            {
                data.removeSeamFace(key); // The coal is gone
                chunk.setUnsaved(true);
            }
            else if (left != moles)
            {
                data.setSeamFace(key, left);
                chunk.setUnsaved(true); // The gas may have gone into another chunk, and this one must remember it is gone
                anyBled = true;
            }
        }
        data.setFacesAwake(anyBled);
        return faces.length;
    }

    private boolean touchesFlame(LevelChunk chunk, int x, int y, int z)
    {
        cursor.set(x, y, z);
        if (GasIgnition.isOpenFlame(chunk.getBlockState(cursor)))
        {
            return true;
        }
        for (Direction side : Direction.values())
        {
            final int nx = x + side.getStepX(), ny = y + side.getStepY(), nz = z + side.getStepZ();
            final @Nullable LevelChunk near = chunk(nx, nz);
            cursor.set(nx, ny, nz);
            if (near != null && GasIgnition.isOpenFlame(near.getBlockState(cursor)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * A film of mercury lies where it condensed, and the air there has to be given a cell to take its vapour from it, even if ventilation has cleared the cell and everything else in it.
     */
    private void keepFilmCellsAlive(GasChunkData data, int originX, int originZ)
    {
        for (int key : data.filmSiteKeys())
        {
            if (!isPassable(originX + GasChunkData.keyX(key), GasChunkData.keyY(key), originZ + GasChunkData.keyZ(key)))
            {
                data.dropFilmSite(key); // Built over: what lay there stays in the deposit of the chunk
            }
            else if (data.get(key) == null)
            {
                data.getOrCreate(key);
            }
        }
    }

    public int stepChunk(GasSimulator simulator, LevelChunk chunk, GasChunkData data, float seconds)
    {
        final int originX = chunk.getPos().getMinBlockX(), originZ = chunk.getPos().getMinBlockZ();
        final int faces = bleedSeamFaces(chunk, data, originX, originZ, seconds);

        final int structure = data.structureVersion();
        if (data.hasFilm())
        {
            keepFilmCellsAlive(data, originX, originZ);
        }

        boolean flammable = false;
        final int[] keys = data.keys();
        for (int key : keys)
        {
            final int x = originX + GasChunkData.keyX(key), y = GasChunkData.keyY(key), z = originZ + GasChunkData.keyZ(key);
            simulator.stepCell(this, x, y, z, seconds);

            final @Nullable float[] cell = data.get(key);
            if (cell != null && Flammability.isFlammable(cell))
            {
                flammable = true;
                if (touchesFlame(chunk, x, y, z))
                {
                    tracker.queueIgnition(new BlockPos(x, y, z));
                }
            }
        }

        // Marked for saving only if something really moved: a chunk in equilibrium used to be saved again every second, up to half a megabyte of tag each time
        final float change = simulator.takeLargestChange();
        final boolean structural = data.structureVersion() != structure; // Cells made or taken away mark the chunk for saving on their own
        if (data.accumulateChange(change, GasSimulator.CHANGE_THRESHOLD_MOLES))
        {
            chunk.setUnsaved(true);
        }

        // Over the cells as they are now: a step creates cells as well as removing them
        final boolean holds = data.holdsMobileContent();
        if (holds && change < GasSimulator.CHANGE_THRESHOLD_MOLES && !structural && !flammable)
        {
            // Gas that is all at rest. It sleeps like a chunk of settled dust, and what it has drifted by since it was last saved is saved once now. Firedamp does not sleep: a flame put beside it has to be noticed
            if (data.flushPendingChange())
            {
                chunk.setUnsaved(true);
            }
            data.rest();
        }
        else
        {
            data.setMobileContent(holds);
        }
        return keys.length + faces;
    }
}
