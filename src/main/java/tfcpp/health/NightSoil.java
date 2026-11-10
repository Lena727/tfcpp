package tfcpp.health;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import tfcpp.ecology.Pollutant;
import tfcpp.ecology.Pollution;

/**
 * The contents of a cesspit taken out in pieces: a fertiliser (the nitrogen of night soil is of the order of 1 % of its wet mass, Feachem et al., Sanitation and Disease, 1983; human excreta carry 4 to 5 g of nitrogen, 1 g of phosphorus and 1 g of potassium a person-day in 1.5 kg: Jönsson et al., 2004, Guidelines on the Use of Urine and Faeces in Crop Production, EcoSanRes), and the way that a household's pathogens come to a field and to the water under it. Each piece carries, hidden, the infective sewage that was in its 20 litres, in the same quantised form as the water, so that pieces from one pit stack.
 */
public final class NightSoil
{
    /** Quantised infective litres in one piece, in order: fresh, shedding Shigella, shedding S. Typhi; then the calendar day (whole days, so that pieces of one take stack) on which it was taken from the pit. */
    public record Load(List<Integer> codes)
    {
    }

    /** Calendar ticks in a day. */
    private static final double TICKS_PER_DAY = 24_000.0;

    private static final double FLOOR_LITRES = 1e-3;

    private NightSoil() {}

    /** @return the piece with this much infective sewage in it, which is the share of the 20 litres that the pit's contents are */
    public static ItemStack piece(int count, double fresh, double shedDysentery, double shedTyphoid, long calendarTick)
    {
        final ItemStack stack = new ItemStack(Hygiene.NIGHT_SOIL.get(), count);
        final int[] codes = {WaterQuality.quantise(fresh, FLOOR_LITRES), WaterQuality.quantise(shedDysentery, FLOOR_LITRES), WaterQuality.quantise(shedTyphoid, FLOOR_LITRES)};
        if (codes[0] > 0 || codes[1] > 0 || codes[2] > 0)
        {
            stack.set(Hygiene.NIGHT_SOIL_LOAD.get(), new Load(List.of(codes[0], codes[1], codes[2], (int) Math.floorDiv(calendarTick, 24_000L))));
        }
        return stack;
    }

    /** @return the infective litres in one piece: fresh, shedding Shigella, shedding S. Typhi */
    public static double[] load(ItemStack stack)
    {
        final @Nullable Load load = stack.get(Hygiene.NIGHT_SOIL_LOAD.get());
        final double[] out = new double[3];
        if (load != null && load.codes().size() >= 3)
        {
            for (int i = 0; i < 3; i++)
            {
                out[i] = WaterQuality.dequantise(load.codes().get(i), FLOOR_LITRES);
            }
        }
        return out;
    }

    /** @return the calendar day on which the piece was taken from its pit, or -1 if it does not say */
    public static int day(ItemStack stack)
    {
        final @Nullable Load load = stack.get(Hygiene.NIGHT_SOIL_LOAD.get());
        return load != null && load.codes().size() >= 4 ? load.codes().get(3) : -1;
    }

    /**
     * Night soil becomes safe by being kept: the pathogens in it die with the T90 of the pit ({@link CesspitMath#PATHOGEN_T90_DAYS}), which is what composting does. A load, after this many days.
     *
     * @return the litres of infective sewage left in a piece that was {@code days} old
     */
    public static double decayed(double litres, double days)
    {
        return days > 0.0 ? litres * Math.exp(-CesspitMath.decayRate() * days) : litres;
    }

    /** @return the age of the piece in days at this calendar tick; none if it does not say, and never negative (the calendar may be turned back) */
    public static double ageDays(ItemStack stack, long calendarTick)
    {
        final int day = day(stack);
        return day < 0 ? 0.0 : Math.max(0.0, (calendarTick - day * (long) TICKS_PER_DAY) / TICKS_PER_DAY);
    }

    /** A fertiliser has been spread: what it carries goes into the soil where it was used, where the pathogens die out as they do there. */
    public static void spread(ServerLevel level, BlockPos pos, ItemStack stack, int count)
    {
        if (!stack.is(Hygiene.NIGHT_SOIL.get()) || count <= 0)
        {
            return;
        }
        final double[] load = load(stack);
        final double days = ageDays(stack, Pollution.now());
        Pollution.deposit(level, pos, Pollutant.SEWAGE, decayed(load[0], days) * count);
        Pollution.deposit(level, pos, Pollutant.SHED_DYSENTERY, decayed(load[1], days) * count);
        Pollution.deposit(level, pos, Pollutant.SHED_TYPHOID, decayed(load[2], days) * count);
    }
}
