package tfcpp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one profile of the server, and the lines that describe it, so that {@code /tfcpp perf} and the load test print the same thing.
 * <p>
 * Sections are timed wherever their work happens and added up here, so a cost that is spread over many calls in a tick, such as breathing for each player, still appears as the cost of that tick. The tick is closed once, by the overworld, which every server has: other dimensions add their time to the tick that is running, so the figures are what the whole server pays.
 */
public final class TickProfiler
{
    public static final int GAS = 0, SHAFT = 1, PIPE = 2, BREATHING = 3, AIR = 4;

    public static final TickProfile PROFILE = new TickProfile("gas", "shaft", "pipe", "breathing", "air");

    /** The time a tick has before the server falls behind, which is what every figure is compared with. */
    private static final double TICK_MILLIS = 50.0;
    private static final double SLOW_PERCENTILE = 0.99;

    /** One line for each section, then the total with its share of the tick. */
    public static List<String> perf()
    {
        final List<String> lines = new ArrayList<>();
        for (int section = 0; section < PROFILE.sectionCount(); section++)
        {
            lines.add(line(PROFILE.name(section), section));
        }
        lines.add(line("total", TickProfile.TOTAL) + String.format(Locale.ROOT, ", mean is %.2f%% and p99 %.2f%% of the %.0f ms tick, over %d ticks",
            100.0 * PROFILE.meanMillis(TickProfile.TOTAL) / TICK_MILLIS, 100.0 * PROFILE.percentileMillis(TickProfile.TOTAL, SLOW_PERCENTILE) / TICK_MILLIS, TICK_MILLIS, PROFILE.ticks()));
        return lines;
    }

    private static String line(String name, int section)
    {
        return String.format(Locale.ROOT, "%-9s mean %8.4f ms  p99 %8.4f ms  max %8.4f ms", name, PROFILE.meanMillis(section), PROFILE.percentileMillis(section, SLOW_PERCENTILE), PROFILE.maxMillis(section));
    }

    private TickProfiler() {}
}
