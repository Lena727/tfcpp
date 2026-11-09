package tfcpp.ecology;

/**
 * Metals that a crop carries from the soil, and what a person takes up from eating it. Free of Minecraft types.
 * <p>
 * Grams per item = transfer factor x soil concentration (mg/kg dry) x dry mass of the item. Transfer factors are plant to soil ratios by dry weight (IAEA TRS-472, 2010, for the stable-element analogues; Kabata-Pendias 2011; Alloway 2013): Pb 0.01, As 0.02, Hg 0.02, Zn 0.3, Cu 0.1, Ni 0.05, Sb 0.01. The item is 0.2 kg fresh at 20 % dry matter, 0.04 kg (TFC gives no mass of its own).
 * <p>
 * Stacking: the contamination of a stack is stored per item, in micrograms, quantised on a logarithmic scale in steps of a factor of 2^(1/2) (about 41 %), so that the items of one field, which differ by a few per cent, form one stack; food with no metals carries no component and stacks with other clean food as before. Stacks of different codes do not stack. Recipes that combine items add up the metal and share it among the outputs ({@link #share}), so a loaf holds what its grain held.
 */
public final class FoodMetals
{
    public static final double DRY_MASS_KG = 0.04;
    /** The metals that go into food, in the order of the component's array. */
    public static final Pollutant[] METALS = {Pollutant.LEAD, Pollutant.ARSENIC, Pollutant.MERCURY, Pollutant.ZINC, Pollutant.COPPER, Pollutant.NICKEL, Pollutant.ANTIMONY};
    /** Micrograms: contamination under this is taken as none. */
    public static final double FLOOR_MICROGRAMS = 1.0;
    private static final double STEPS_PER_OCTAVE = 2.0;

    /**
     * Gut absorption of the inorganic forms. Lead 10 % of an adult's dietary dose (WHO/JECFA 1993: 3-10 %, children 40-50 %); inorganic arsenic 90 % (ATSDR 2007); inorganic mercury 7 % (ATSDR 1999: 7-15 %); antimony 5 % (ATSDR 2019). Zinc and copper are essential metals that matter to a human only above about 50 mg a day (the upper intake level of zinc is 40 mg a day, IOM 2001) and an item brings well under that at any soil of the game, so they are ignored in v1.5; nickel has no body burden in the game.
     */
    public static double absorption(Pollutant metal)
    {
        return switch (metal)
        {
            case LEAD -> 0.10;
            case ARSENIC -> 0.90;
            case MERCURY -> 0.07;
            case ANTIMONY -> 0.05;
            default -> 0.0;
        };
    }

    public static double transferFactor(Pollutant metal)
    {
        return switch (metal)
        {
            case LEAD -> 0.01;
            case ARSENIC, MERCURY -> 0.02;
            case ZINC -> 0.3;
            case COPPER -> 0.1;
            case NICKEL -> 0.05;
            case ANTIMONY -> 0.01;
            default -> 0.0;
        };
    }

    private FoodMetals() {}

    public static int indexOf(Pollutant metal)
    {
        for (int i = 0; i < METALS.length; i++)
        {
            if (METALS[i] == metal)
            {
                return i;
            }
        }
        return -1;
    }

    /** @return grams of the metal in one item grown in soil of this concentration */
    public static double gramsPerItem(Pollutant metal, double soilMgPerKg)
    {
        return transferFactor(metal) * soilMgPerKg * DRY_MASS_KG / 1000.0;
    }

    /** @return the code of an amount in micrograms, 0 for none */
    public static int quantise(double micrograms)
    {
        if (!(micrograms >= FLOOR_MICROGRAMS))
        {
            return 0;
        }
        return 1 + (int) Math.round(STEPS_PER_OCTAVE * Math.log(micrograms / FLOOR_MICROGRAMS) / Math.log(2.0));
    }

    /** @return the amount in micrograms that a code stands for */
    public static double dequantise(int code)
    {
        return code <= 0 ? 0.0 : FLOOR_MICROGRAMS * Math.pow(2.0, (code - 1) / STEPS_PER_OCTAVE);
    }

    /** @return micrograms of each metal in the total of several stacks of items (per item amounts and counts), divided among {@code outputs} items */
    public static double[] share(double[][] perItemMicrograms, int[] counts, int outputs)
    {
        final double[] total = new double[METALS.length];
        for (int i = 0; i < perItemMicrograms.length; i++)
        {
            for (int m = 0; m < total.length; m++)
            {
                total[m] += perItemMicrograms[i][m] * counts[i];
            }
        }
        for (int m = 0; m < total.length; m++)
        {
            total[m] /= Math.max(1, outputs);
        }
        return total;
    }

    /** @return milligrams that the body takes up from this many micrograms of the metal */
    public static double absorbedMilligrams(Pollutant metal, double micrograms)
    {
        return micrograms * 1e-3 * absorption(metal);
    }
}
