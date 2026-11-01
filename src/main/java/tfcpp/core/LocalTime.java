package tfcpp.core;

/**
 * The rule for time in this mod. What happens at one place, such as a fire, a pipe rusting or a poison leaving a body, runs on ticks, so that it takes as long on every server. TFC's calendar, with a length of day and of month that each server sets, only decides what happens to the whole world: the date, the seasons and the climate.
 * <p>
 * A process that really takes hours or days is scaled by these, which are TFC's default calendar. To count ticks that pass in the world, use {@code Calendars.SERVER.getTicks()}: it stops on an empty server and jumps forward when players sleep, whatever the length of a day.
 */
public final class LocalTime
{
    /** A day of slow processes, in real seconds. */
    public static final float SECONDS_PER_DAY = 24f * 60f;
    public static final long TICKS_PER_DAY = 24L * 60L * 20L;
    /** How much faster than reality slow processes run: 60 seconds of them pass in each real second. */
    public static final float TIME_SCALE = 60f;

    private LocalTime() {}
}
