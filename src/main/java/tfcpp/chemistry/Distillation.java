package tfcpp.chemistry;

import tfcpp.pneumatics.gas.Gas;

/**
 * One batch in a glass retort, free of Minecraft types. A charge is boiled over and condensed in proportion to the time it has spent in the working range of temperature: too cold and nothing comes over, too hot and the wrong things do, and in both the charge keeps its place.
 * <p>
 * The inputs are taken at the start of a batch and never again, and what comes out arrives in proportion to progress, so nothing leaves beyond what the recipe gives however the time is divided into steps.
 */
public final class Distillation
{
    /** Above this borosilicate glass in a thin vessel cracks under the gradient: 550 C, taken as the strain point of a laboratory glass with a margin. */
    public static final float CRACK_TEMPERATURE = 550f;

    /** Float sums land a hair below the whole they stand for, and a stock of 0.23 L must not be refused 0.23 L. */
    private static final double EPSILON = 1e-4;

    /**
     * What one batch is.
     *
     * @param items          pieces of powder the batch takes
     * @param inputLitres    litres of the input liquid it takes
     * @param reagentLitres  litres of a second liquid it takes, 0 if it takes none
     * @param minTemperature the lowest temperature at which it runs, in Celsius
     * @param maxTemperature the highest temperature at which it runs, in Celsius
     * @param seconds        time of the whole batch while it is in range
     * @param outputLitres   litres that arrive in the receiver over the batch
     * @param residueLitres  litres of a second liquid that stay behind in the flask, 0 if none
     * @param receiverWater  litres of water that the receiver takes up in the batch, 0 if it holds none
     * @param gas            moles of each gas that the batch gives off, indexed by {@link Gas#ordinal()}
     * @param byproducts     pieces of powder left in the flask when the batch ends
     */
    public record Recipe(int items, float inputLitres, float reagentLitres, float minTemperature, float maxTemperature, float seconds, float outputLitres, float residueLitres, float receiverWater, float[] gas, int byproducts) {}

    /** What the retort holds ready to be taken: the liquids in float litres, as they are used in fractions of a millibucket, and a count of pieces. */
    public record Stock(float litres, float reagent, int items, float water) {}

    /** How much more each product can be given before it is full. */
    public record Room(double output, double residue, int items) {}

    /** Why a step did not run, for a status to tell the operator. */
    public enum Reason
    {
        NONE, COLD, HOT, MISSING_LIQUID, MISSING_REAGENT, MISSING_ITEMS, MISSING_WATER, NO_ROOM
    }

    /**
     * What one stretch of time did. The inputs are those taken at the start of the batch, and are zero on every later step.
     *
     * @param ran        {@code true} if the batch advanced
     * @param finished   {@code true} once the batch has run its whole time
     * @param reason     {@link Reason#NONE} if it ran, and what stopped it if not
     * @param litres     input liquid taken
     * @param reagent    second liquid taken
     * @param items      pieces taken
     * @param water      receiver water taken
     * @param output     litres made in this step
     * @param residue    litres of residue made in this step
     * @param gas        moles of each gas given off in this step, indexed by {@link Gas#ordinal()}
     * @param byproducts pieces left, given only in the step that finishes the batch
     */
    public record Step(boolean ran, boolean finished, Reason reason, double litres, double reagent, int items, double water, double output, double residue, double[] gas, int byproducts)
    {
        static Step blocked(Reason reason)
        {
            return new Step(false, false, reason, 0.0, 0.0, 0, 0.0, 0.0, 0.0, new double[Gas.COUNT], 0);
        }
    }

    private final Recipe recipe;
    private double progress;
    private boolean active;

    /**
     * @param progress seconds of the batch that have run
     * @param active   {@code true} if the inputs of the batch have already been taken
     */
    public Distillation(Recipe recipe, double progress, boolean active)
    {
        this.recipe = recipe;
        this.progress = Math.clamp(progress, 0.0, recipe.seconds());
        this.active = active;
    }

    public Recipe recipe()
    {
        return recipe;
    }

    /** @return seconds of the batch that have run */
    public double progress()
    {
        return progress;
    }

    /** @return share of the batch that has run, from 0 to 1 */
    public double fraction()
    {
        return progress / recipe.seconds();
    }

    /** @return {@code true} once the inputs have been taken, after which the batch is committed */
    public boolean active()
    {
        return active;
    }

    /** @return {@code true} if glass at this temperature cracks */
    public static boolean cracks(float temperature)
    {
        return temperature > CRACK_TEMPERATURE;
    }

    /** @return {@code true} if the temperature lies in the working range, both ends included */
    public boolean inRange(float temperature)
    {
        return temperature >= recipe.minTemperature() && temperature <= recipe.maxTemperature();
    }

    /**
     * Lets time pass. Nothing happens outside the working range. A batch that has not started takes its inputs only if they are all there and its first stretch has room to go. A batch that has started needs only the range and the room.
     *
     * @param seconds     length of the step
     * @param temperature the temperature of the flask, in Celsius
     * @param stock       what the retort holds, which is looked at only for a batch that has not started
     * @param room        what the receiver and the flask can still take
     */
    public Step advance(double seconds, float temperature, Stock stock, Room room)
    {
        if (seconds <= 0.0)
        {
            return Step.blocked(Reason.NONE);
        }
        if (temperature < recipe.minTemperature())
        {
            return Step.blocked(Reason.COLD);
        }
        if (temperature > recipe.maxTemperature())
        {
            return Step.blocked(Reason.HOT);
        }
        if (!active)
        {
            if (stock.litres() + EPSILON < recipe.inputLitres())
            {
                return Step.blocked(Reason.MISSING_LIQUID);
            }
            if (stock.reagent() + EPSILON < recipe.reagentLitres())
            {
                return Step.blocked(Reason.MISSING_REAGENT);
            }
            if (stock.items() < recipe.items())
            {
                return Step.blocked(Reason.MISSING_ITEMS);
            }
            if (stock.water() + EPSILON < recipe.receiverWater())
            {
                return Step.blocked(Reason.MISSING_WATER);
            }
        }

        final double step = Math.min(seconds, recipe.seconds() - progress);
        final double before = progress / recipe.seconds();
        final double after = (progress + step) / recipe.seconds();
        final boolean finishes = progress + step >= recipe.seconds();
        final double share = (finishes ? 1.0 : after) - before;
        final double output = recipe.outputLitres() * share;
        final double residue = recipe.residueLitres() * share;
        final int byproducts = finishes ? recipe.byproducts() : 0;
        if (output > room.output() + EPSILON || residue > room.residue() + EPSILON || byproducts > room.items())
        {
            return Step.blocked(Reason.NO_ROOM);
        }

        final boolean starting = !active;
        active = true;
        progress = finishes ? recipe.seconds() : progress + step;
        final double[] gas = new double[Gas.COUNT];
        for (int i = 0; i < Gas.COUNT; i++)
        {
            gas[i] = recipe.gas()[i] * share;
        }
        if (finishes)
        {
            active = false;
            progress = 0.0;
        }
        return new Step(true, finishes, Reason.NONE, starting ? recipe.inputLitres() : 0.0, starting ? recipe.reagentLitres() : 0.0, starting ? recipe.items() : 0, starting ? recipe.receiverWater() : 0.0, output, residue, gas, byproducts);
    }
}
