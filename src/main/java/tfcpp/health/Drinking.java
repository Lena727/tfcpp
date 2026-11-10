package tfcpp.health;

import java.util.random.RandomGenerator;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.fluids.FluidStack;

import tfcpp.ecology.FoodMetals;
import tfcpp.ecology.Pollution;
import tfcpp.pneumatics.gas.Breathing;

/**
 * What a drink of water does to a person: the pathogens in it are rolled for each disease once, by the dose-response of {@link Organisms}, and the metals in it go into the body as those of food do ({@link Breathing#onEat}, with the gut absorption of {@link FoodMetals}). No message says anything of it: a person sees the symptoms and nothing else.
 * <p>
 * Acid in a drink does nothing here: stomach acid is far stronger than any natural water, which is why lemon juice at pH 2 is drunk, and v1.5 has no chronic effect of acid to feed.
 */
public final class Drinking
{
    /**
     * Litres in a millibucket of a drink. TFC's drink from a jug is 100 mB, and a drink of water is half a litre, so 1 mB of a drink is 5 mL. (The chemical liquids and the tanks of the mod count a millibucket as a litre; a person's drink is not counted that way, as a jug of 100 litres is no drink.)
     */
    public static final double LITRES_PER_DRUNK_MILLIBUCKET = 0.005;
    /** The amount that TFC takes for a drink from a block of water: {@code Drinkable.JUG_DRINK_MB}. */
    public static final int WORLD_DRINK_MILLIBUCKETS = 100;

    private Drinking() {}

    /** A drink from a block of water in the world, at the position of the block. */
    public static void fromWorld(ServerPlayer player, ServerLevel level, BlockPos water)
    {
        final double litres = WORLD_DRINK_MILLIBUCKETS * LITRES_PER_DRUNK_MILLIBUCKET;
        swallow(player, WaterSource.take(level, water, litres), litres);
    }

    /** A drink from a jug, a glass bottle or any container whose fluid stack is the one drunk. */
    public static void fromStack(ServerPlayer player, FluidStack drunk)
    {
        if (!drunk.isEmpty() && FluidQuality.carries(drunk.getFluid()))
        {
            swallow(player, FluidQuality.get(drunk), drunk.getAmount() * LITRES_PER_DRUNK_MILLIBUCKET);
        }
    }

    /** Swallows this many litres of water of this quality. */
    public static void swallow(ServerPlayer player, WaterQuality quality, double litres)
    {
        if (player.isCreative() || player.isSpectator() || quality.isClean())
        {
            return;
        }
        final RandomGenerator random = new SplittableRandom(player.getRandom().nextLong());
        final DiseaseState state = player.getData(Hygiene.DISEASES);
        final long now = Pollution.now();
        for (Disease disease : Disease.VALUES)
        {
            final double chance = Organisms.infectionProbability(disease, quality, litres);
            if (chance > 0.0 && state.canCatch(disease, now) && random.nextDouble() < chance)
            {
                state.infect(disease, now, random);
            }
        }
        final double[] micrograms = new double[FoodMetals.METALS.length];
        boolean any = false;
        for (int i = 0; i < micrograms.length; i++)
        {
            micrograms[i] = quality.metal(i) * litres;
            any |= micrograms[i] > 0.0;
        }
        if (any)
        {
            Breathing.onEat(player, micrograms);
        }
    }
}
