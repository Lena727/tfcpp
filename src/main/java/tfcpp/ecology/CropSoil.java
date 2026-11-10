package tfcpp.ecology;

import net.dries007.tfc.common.TFCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What the ground and the air do to one growth step of a TFC crop: read by the mixin into {@code CropHelpers.growthTickStep} at the head of the step, applied to its growth and its health.
 * <p>
 * The state is static because TFC's step is one method on the server thread that does not call itself: the head of the step sets it, the two injections inside read it.
 */
public final class CropSoil
{
    /** The multiplier and the verdict of the step that is running. */
    private static double growth = 1.0;
    private static boolean unhealthy;

    private CropSoil() {}

    public static double growth()
    {
        return growth;
    }

    public static boolean unhealthy()
    {
        return unhealthy;
    }

    /** Called at the head of a step: irrigates the field from the water that hydrates it, then reads the soil and the air. */
    public static void prepare(Level level, BlockPos pos, long fromTick, long toTick)
    {
        growth = 1.0;
        unhealthy = false;
        if (!(level instanceof ServerLevel server) || toTick <= fromTick)
        {
            return;
        }
        final double years = (toTick - fromTick) / (SoilConstants.CALENDAR_TICKS_PER_DAY * SoilConstants.daysPerYear());
        irrigate(server, pos, years);
        final CropEffects.Result result = at(server, pos);
        growth = result.growth();
        unhealthy = result.unhealthy();
    }

    /** @return what soil and air do to a crop at this position now */
    public static CropEffects.Result at(ServerLevel level, BlockPos pos)
    {
        return of(level, pos, Pollution.soilForCrop(level, pos));
    }

    /** @return the same from a soil that the caller already has (or {@code null} for none: then the acidity is exactly neutral), without touching or making any cell */
    public static CropEffects.Result of(ServerLevel level, BlockPos pos, @Nullable SoilCell soil)
    {
        final RegionalAir air = RegionalAir.get(level);
        final double annual = air.annualSo2(pos.getX(), pos.getZ()), hourly = air.so2Now(pos.getX(), pos.getZ());
        if (soil == null)
        {
            return CropEffects.combine(7.0, 7.0, new double[Pollutant.COUNT], annual, hourly);
        }
        final double[] metals = new double[Pollutant.COUNT];
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            if (pollutant != Pollutant.ACIDITY && pollutant != Pollutant.TAR && !pollutant.isSewage())
            {
                metals[pollutant.ordinal()] = soil.mgPerKg(pollutant);
            }
        }
        return CropEffects.combine(soil.pH(), soil.startPh(), metals, annual, hourly);
    }

    private static void irrigate(ServerLevel level, BlockPos cropPos, double years)
    {
        final LevelWaters waters = LevelWaters.get(level);
        if (waters.ledger().activeCount() == 0)
        {
            return; // No water holds anything: nothing to carry
        }
        final @Nullable BlockPos water = hydratingWater(level, cropPos.below());
        if (water == null)
        {
            return;
        }
        final @Nullable WaterBody body = Waters.at(level, water);
        if (body != null)
        {
            waters.irrigate(level, body, cropPos, years);
        }
    }

    /**
     * The water that hydrates a field, found as TFC finds it ({@code FarmlandBlock.findMinCostWater}): water of the hydrating tag within 4 blocks sideways and one block down, the cheapest by distance; or water on the crop itself.
     */
    @Nullable
    public static BlockPos hydratingWater(Level level, BlockPos farmland)
    {
        if (level.getFluidState(farmland.above()).is(TFCTags.Fluids.HYDRATING))
        {
            return farmland.above();
        }
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int best = 5;
        @Nullable BlockPos found = null;
        for (int dx = -4; dx <= 4; dx++)
        {
            for (int dz = -4; dz <= 4; dz++)
            {
                for (int dy = -1; dy <= 0; dy++)
                {
                    final int cost = Math.max(Math.abs(dx), Math.abs(dz)) + (-2 * dy);
                    if (cost < best && level.getFluidState(cursor.setWithOffset(farmland, dx, dy, dz)).is(TFCTags.Fluids.HYDRATING))
                    {
                        best = cost;
                        found = cursor.immutable();
                    }
                }
            }
        }
        return found;
    }
}
