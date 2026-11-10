package tfcpp.health;

import tfcpp.ecology.FoodMetals;
import tfcpp.ecology.Pollutant;

/**
 * The slow sand filter, and boiling. Free of Minecraft types.
 * <p>
 * A slow sand filter passes water at 0.1 to 0.4 m/h through a metre or so of sand (Huisman and Wood, Slow Sand Filtration, WHO 1974); 0.2 m/h through the 1 m2 of the block is 200 litres an hour. A film of living organisms on the sand, the schmutzdecke, does most of the work, and it takes days to grow: a new filter removes little of the bacteria for the first days and then, ripe, 99 % and more (a 2-log removal is the least that Huisman and Wood count on; the first filters worked at Chelsea from 1829 and the sand filters of Altona, which took the water of the Elbe that Hamburg drank raw, kept the city's neighbour almost free of cholera in 1892).
 */
public final class Filtration
{
    /** Litres an hour through the filter. */
    public static final double LITRES_PER_HOUR = 200.0;
    /** Days of running before the filter is ripe. A GAME FIGURE for the two to three weeks of a real filter, which at the pace of the game is too long to be a lesson and not a chore: the schmutzdecke of the real thing needs days to weeks (Huisman and Wood: ripening takes from a few days to a few weeks). */
    public static final double RIPENING_DAYS = 3.0;
    /** The share of the sewage, and so of the pathogens, that a ripe filter takes out, and an unripe one. */
    public static final double RIPE_REMOVAL = 0.99, UNRIPE_REMOVAL = 0.5;
    /** The share of a metal that the sand takes out: the particulate part is held, the dissolved is not. Lead, copper, zinc and mercury travel mostly on particles in surface water (Allison and Allison 2005, EPA/600/R-05/074) and half goes; arsenic is mostly dissolved and 20 % goes. Nickel and antimony, mostly dissolved, 20 %. Free acid is not touched. */
    public static double metalRemoval(Pollutant metal)
    {
        return switch (metal)
        {
            case LEAD, COPPER, ZINC, MERCURY -> 0.5;
            default -> 0.2;
        };
    }

    private Filtration() {}

    /** The water that comes through a filter in a stretch of time, in the two parts of it that differ: what went through before the filter was ripe and what went through after. */
    public record Throughput(double unripeLitres, double ripeLitres)
    {
        public double total()
        {
            return unripeLitres + ripeLitres;
        }
    }

    /**
     * @param ripeDays the days the filter has run
     * @param hours the hours of the calendar that have passed
     * @param available litres in the input tank
     * @param room litres of room in the output tank
     * @return how much goes through, and how much of it before the filter is ripe
     */
    public static Throughput throughput(double ripeDays, double hours, double available, double room)
    {
        final double litres = Math.max(0.0, Math.min(Math.min(LITRES_PER_HOUR * hours, available), room));
        final double unripeCapacity = Math.max(0.0, (RIPENING_DAYS - ripeDays) * 24.0 * LITRES_PER_HOUR);
        final double unripe = Math.min(litres, unripeCapacity);
        return new Throughput(unripe, litres - unripe);
    }

    /** @return the days the filter has run after passing this many litres */
    public static double daysRun(double ripeDays, double litres)
    {
        return ripeDays + litres / (LITRES_PER_HOUR * 24.0);
    }

    /** @return water after the sand: the sewage and with it the pathogens reduced by the removal, particulate metals partly held, acid as it was */
    public static WaterQuality filter(WaterQuality in, double removal)
    {
        final double keep = 1.0 - removal;
        final double[] metals = in.metals();
        for (int i = 0; i < metals.length; i++)
        {
            metals[i] *= 1.0 - metalRemoval(FoodMetals.METALS[i]) * (removal >= RIPE_REMOVAL ? 1.0 : 0.5);
        }
        return new WaterQuality(in.faecal * keep, in.shedDysentery * keep, in.shedTyphoid * keep, metals, in.acidity);
    }

    // --- Boiling ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Seconds of a rolling boil that kill the pathogens of water: the WHO advice is to bring it to a rolling boil, and a minute is enough (WHO, Boil Water, 2015; 3 minutes above 2000 m). Pathogens die at the temperature of boiling in seconds; the minute is the safety margin. */
    public static final double BOIL_SECONDS = 60.0;
    /** The temperature in degrees Celsius of a rolling boil of water at sea level. */
    public static final double BOILING_CELSIUS = 100.0;
}
