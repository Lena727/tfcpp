package tfcpp.chemistry;

/**
 * A fraction that a device keeps between whole units. Devices count litres, moles and pieces in floating point and hand out only whole millibuckets and items, so what is left over has to wait here for the next time: it is neither rounded away nor made up.
 */
public final class Remainder
{
    /** Float sums such as ten times 0.1 land a hair below the whole number they stand for, and must not be lost to that. */
    private static final double EPSILON = 1e-4;

    private double pending;

    public Remainder() {}

    public Remainder(double pending)
    {
        this.pending = pending;
    }

    public void add(double amount)
    {
        pending += amount;
    }

    public double pending()
    {
        return pending;
    }

    /** @return how many whole units could be taken now, which takes none */
    public int wholeFor(double extra)
    {
        return (int) Math.floor(pending + extra + EPSILON);
    }

    public int whole()
    {
        return wholeFor(0.0);
    }

    /** Takes these away, which must have been counted by {@link #whole}. What remains never goes below zero. */
    public void take(int units)
    {
        pending = Math.max(0.0, pending - units);
    }
}
