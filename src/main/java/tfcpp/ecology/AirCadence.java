package tfcpp.ecology;

/**
 * How many hourly steps the regional air takes to get to the present, after the calendar has jumped. Free of Minecraft types.
 */
public final class AirCadence
{
    /** A week of steps: after a longer jump the smoke has had time to come down wherever it was, and what is left is put into the ground in place. */
    public static final long MAX_CATCH_UP_STEPS = 7L * 24L;

    /**
     * @param firstHour the calendar hour of the first step to take, whose weather is that of the hour before it
     * @param steps how many steps to take
     * @param depositRest whether what is left in the air after them is put into the ground where it stands
     * @param newLastHour the hour that the air is as of afterwards
     */
    public record Plan(long firstHour, long steps, boolean depositRest, long newLastHour)
    {
    }

    private AirCadence() {}

    public static long hourOf(long calendarTick)
    {
        return Math.floorDiv(calendarTick, AirScale.TICKS_PER_HOUR);
    }

    /** @return the tick in the middle of the hour that ends at the start of this hour index, which is where the weather of a step is read */
    public static long middleOfHourBefore(long hour)
    {
        return (hour - 1L) * AirScale.TICKS_PER_HOUR + AirScale.TICKS_PER_HOUR / 2L;
    }

    /**
     * @param lastHour the hour the air is as of
     * @param nowHour the hour of the calendar now
     * @return what to do: nothing if no hour has passed, and if the calendar was set back the clock goes back with it and nothing is done
     */
    public static Plan plan(long lastHour, long nowHour)
    {
        if (nowHour <= lastHour)
        {
            return new Plan(lastHour + 1L, 0L, false, nowHour);
        }
        final long hours = nowHour - lastHour;
        return new Plan(lastHour + 1L, Math.min(hours, MAX_CATCH_UP_STEPS), hours > MAX_CATCH_UP_STEPS, nowHour);
    }
}
