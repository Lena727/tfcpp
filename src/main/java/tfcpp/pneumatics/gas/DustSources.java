package tfcpp.pneumatics.gas;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import tfcpp.chemistry.Chemistry;

/**
 * Where coal dust and stone dust come from. Every source writes through {@link LevelGasVolume#cellForEmission}, which wakes a chunk that was asleep holding only settled dust, and none writes anything under the open sky, where the wind takes dust away as it does any gas.
 */
public final class DustSources
{
    /** Breaking a block of coal throws 5 g into the air, and leaves 50 g on the surfaces around it. Units are kilograms. */
    public static final float MINED_AIRBORNE = 0.005f, MINED_SETTLED = 0.05f;
    /** A grind at a quern raises 2 g. */
    public static final float GROUND_AIRBORNE = 0.002f;
    /** A powder of ground limestone or dolomite weighs 0.75 kg. */
    public static final float KILOGRAMS_PER_STONE_DUST_POWDER = 0.75f;
    public static final int STONE_DUST_RADIUS = 2;

    private static final int[][] SIDES = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    /** @return {@code true} for what a quern turns to coal powder: charcoal, coke and every coal */
    public static boolean isCoal(ItemStack stack)
    {
        return stack.is(Items.CHARCOAL) || stack.is(Chemistry.COKE.get()) || stack.is(ItemTags.COALS);
    }

    /**
     * A block of coal broken at this position. The position itself is still solid when this runs, and is the cell the dust comes out into.
     */
    public static void onCoalMined(ServerLevel level, BlockPos pos)
    {
        final LevelGasVolume volume = GasTracker.get(level).volume(level);

        final List<int[]> cells = new ArrayList<>();
        cells.add(new int[] {pos.getX(), pos.getY(), pos.getZ()});
        for (int[] side : SIDES)
        {
            final int nx = pos.getX() + side[0], ny = pos.getY() + side[1], nz = pos.getZ() + side[2];
            if (volume.isPassable(nx, ny, nz))
            {
                cells.add(new int[] {nx, ny, nz});
            }
        }

        // The share of a cell open to the sky is carried off, so it is part of the division and not of the result
        final float share = MINED_SETTLED / cells.size();
        for (int[] at : cells)
        {
            if (!volume.isOpenToSky(at[0], at[1], at[2]))
            {
                add(volume, at[0], at[1], at[2], Gas.SETTLED_COAL_DUST, share);
            }
        }

        if (!volume.isOpenToSky(pos.getX(), pos.getY(), pos.getZ()))
        {
            add(volume, pos.getX(), pos.getY(), pos.getZ(), Gas.COAL_DUST, MINED_AIRBORNE);
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** One grind of coal, charcoal or coke at a quern. */
    public static void onGround(ServerLevel level, BlockPos quern)
    {
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        // A quern is not a full block, so the dust comes out into the cell it stands in
        final BlockPos at = volume.isPassable(quern.getX(), quern.getY(), quern.getZ()) ? quern : quern.above();
        if (!volume.isOpenToSky(at.getX(), at.getY(), at.getZ()))
        {
            add(volume, at.getX(), at.getY(), at.getZ(), Gas.COAL_DUST, GROUND_AIRBORNE);
        }
    }

    /**
     * Spreads one powder of stone dust over the open cells within {@link #STONE_DUST_RADIUS} of a position, reached from it through open cells, equally.
     *
     * @return the share of the dust lying in the first cell that is incombustible, from 0 to 1, or -1 if nothing could be done: the position is a block, or is open to the sky
     */
    public static float spreadStoneDust(ServerLevel level, BlockPos origin)
    {
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        if (!volume.isPassable(origin.getX(), origin.getY(), origin.getZ()) || volume.isOpenToSky(origin.getX(), origin.getY(), origin.getZ()))
        {
            return -1;
        }

        final LongOpenHashSet seen = new LongOpenHashSet();
        final List<BlockPos> reached = new ArrayList<>();
        reached.add(origin);
        seen.add(origin.asLong());
        for (int i = 0; i < reached.size(); i++)
        {
            final BlockPos from = reached.get(i);
            for (Direction side : Direction.values())
            {
                final BlockPos next = from.relative(side);
                if (Math.abs(next.getX() - origin.getX()) <= STONE_DUST_RADIUS
                    && Math.abs(next.getY() - origin.getY()) <= STONE_DUST_RADIUS
                    && Math.abs(next.getZ() - origin.getZ()) <= STONE_DUST_RADIUS
                    && volume.isPassable(next.getX(), next.getY(), next.getZ())
                    && seen.add(next.asLong()))
                {
                    reached.add(next);
                }
            }
        }

        final List<BlockPos> receiving = new ArrayList<>();
        for (BlockPos at : reached)
        {
            if (!volume.isOpenToSky(at.getX(), at.getY(), at.getZ()))
            {
                receiving.add(at);
            }
        }

        final float share = KILOGRAMS_PER_STONE_DUST_POWDER / receiving.size();
        for (BlockPos at : receiving)
        {
            add(volume, at.getX(), at.getY(), at.getZ(), Gas.SETTLED_ROCK_DUST, share);
        }

        final @Nullable float[] spot = volume.cell(origin.getX(), origin.getY(), origin.getZ());
        return spot == null ? 1f : incombustibleShare(spot);
    }

    /** @return the part of the settled dust in a cell that cannot burn: what an inspector's sample from the floor of a gallery measures. 1 where there is no dust at all. */
    public static float incombustibleShare(float[] cell)
    {
        final float rock = cell[Gas.SETTLED_ROCK_DUST.ordinal()], coal = cell[Gas.SETTLED_COAL_DUST.ordinal()];
        return rock + coal <= 0 ? 1f : rock / (rock + coal);
    }

    private static void add(LevelGasVolume volume, int x, int y, int z, Gas gas, float kilograms)
    {
        final @Nullable float[] cell = volume.cellForEmission(x, y, z);
        if (cell != null)
        {
            cell[gas.ordinal()] += kilograms;
        }
    }

    private DustSources() {}
}
