package tfcpp.chemistry;

import tfcpp.pneumatics.gas.Gas;

/**
 * The balance of one charge in a coke oven, free of Minecraft types. Dry distillation runs in proportion to the time the charge has spent hot enough: the volatile matter leaves as it is driven off, steadily, and what is left is the solid.
 * <p>
 * Nothing leaves beyond what the table gives for each piece, however the time is divided into steps.
 */
public final class CarbonizationBatch
{
    /**
     * What one piece of charge becomes.
     *
     * @param seconds     time the whole charge takes while hot enough, in seconds
     * @param temperature the temperature it needs, in Celsius
     * @param solid       pieces of solid residue per piece of charge, not necessarily a whole number
     * @param tar         litres of tar per piece
     * @param liquor      litres of watery liquor per piece
     * @param gas         moles of each gas per piece, indexed by {@link Gas#ordinal()}
     */
    public record Yield(float seconds, float temperature, double solid, double tar, double liquor, float[] gas) {}

    /** What one stretch of time gave. Gas is in moles, indexed by {@link Gas#ordinal()}. */
    public record Step(double solid, double tar, double liquor, double[] gas)
    {
        public boolean isEmpty()
        {
            return solid == 0.0 && tar == 0.0 && liquor == 0.0 && java.util.Arrays.stream(gas).allMatch(moles -> moles == 0.0);
        }
    }

    private final Yield yield;
    private final int pieces;
    private double progress;

    /**
     * @param progress seconds of the batch that have already run
     */
    public CarbonizationBatch(Yield yield, int pieces, double progress)
    {
        this.yield = yield;
        this.pieces = pieces;
        this.progress = Math.clamp(progress, 0.0, yield.seconds());
    }

    public int pieces()
    {
        return pieces;
    }

    /** @return seconds of the batch that have run */
    public double progress()
    {
        return progress;
    }

    /** @return share of the batch that has run, from 0 to 1 */
    public double fraction()
    {
        return progress / yield.seconds();
    }

    /** @return {@code true} once the charge has begun to change, and can no longer be added to */
    public boolean started()
    {
        return progress > 0.0;
    }

    public boolean isComplete()
    {
        return progress >= yield.seconds();
    }

    /** @return what the next stretch of time would give, with nothing changed */
    public Step peek(double seconds, boolean hot)
    {
        final double[] gas = new double[Gas.COUNT];
        if (!hot || isComplete() || seconds <= 0.0)
        {
            return new Step(0.0, 0.0, 0.0, gas);
        }
        final double share = Math.min(seconds, yield.seconds() - progress) / yield.seconds();
        for (int i = 0; i < Gas.COUNT; i++)
        {
            gas[i] = pieces * yield.gas()[i] * share;
        }
        return new Step(pieces * yield.solid() * share, pieces * yield.tar() * share, pieces * yield.liquor() * share, gas);
    }

    /**
     * Lets time pass. The charge only changes while hot enough, and a charge that is not hot does not lose its place.
     *
     * @return what that gave
     */
    public Step advance(double seconds, boolean hot)
    {
        final Step step = peek(seconds, hot);
        if (hot && seconds > 0.0)
        {
            final double remaining = yield.seconds() - progress;
            progress = seconds >= remaining ? yield.seconds() : progress + seconds;
        }
        return step;
    }
}
