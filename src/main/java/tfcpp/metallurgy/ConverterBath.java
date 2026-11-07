package tfcpp.metallurgy;

import org.jetbrains.annotations.Nullable;

/**
 * The molten charge of a Bessemer converter. Air blown up through the iron burns what is dissolved in it, in the order that real blows showed: silicon first, then carbon, and iron itself once the carbon runs short.
 * <p>
 * No fuel is burned. Those reactions are the only source of heat, against the heat that the blast carries off and what the vessel loses. Bessemer's discovery was that this comes out ahead, which is what let steel stay liquid without a furnace able to reach 1500 C, but it only does so with a strong enough blast.
 * <p>
 * Phosphorus, which Bessemer's acid lining could not remove, burns only in a basic one (Thomas and Gilchrist, 1878): the lining and the lime thrown in hold it as 4 CaO.P2O5, and it burns after the carbon, in the "afterblow".
 */
public final class ConverterBath
{
    /**
     * What iron is called according to how much carbon it holds, with the point in weight percent where each kind ends.
     * An ingot does not remember how much it holds, so each kind counts as the least of each element that any tap of it can hold: carbon, silicon and phosphorus. Pouring iron back can then lose them, as it does in reality to the air and the slag, but never gain any.
     * The melting point is what TFC's fluid of that iron melts at. It must not be below the liquidus of the composition the kind counts as, or iron poured at its own melting point would set in the vessel at once.
     */
    public enum Grade
    {
        WROUGHT_IRON("tfc:metal/wrought_iron", 0.2f, 0.03f, 0f, 0f, 1535f),
        STEEL("tfc:metal/steel", 1.0f, 0.2f, 0f, 0f, 1520f),
        HIGH_CARBON_STEEL("tfc:metal/high_carbon_steel", 2.1f, 1.0f, 0f, 0f, 1450f),
        /** Melts where its lightest iron does, at 2.1% carbon. A heavier tap of it melts lower, which pouring never needs. */
        CAST_IRON("tfc:metal/cast_iron", 4.0f, 2.1f, 0f, 0f, 1350f),
        /** Counts as the 1% silicon of a blast furnace tap, so only a bath that still holds that much is tapped as it: see {@link ConverterBath#tapGrade()}. */
        PIG_IRON("tfc:metal/pig_iron", Float.MAX_VALUE, 4.0f, 1.0f, 0f, 1200f),
        /** Pig iron from a brown ore, which carries phosphorus. It is not named by carbon: only {@link #ofFluid} finds it, and a bath is tapped as it only if it holds all that it counts as. */
        PHOSPHORIC_PIG_IRON("tfcpp:metal/phosphoric_pig_iron", Float.MAX_VALUE, 4.0f, 1.0f, 1.5f, 1200f),
        /**
         * What phosphorus makes of iron whatever its carbon. It counts as the least of everything that a tap of it can hold: the lowest carbon, no silicon, and exactly the 0.1% from which iron is cold short.
         * A tap that held more of any of them loses the difference on the way, as real iron lost it to the slag and the air.
         */
        COLD_SHORT_IRON("tfcpp:metal/cold_short_iron", 0.2f, 0.03f, 0f, PHOSPHORUS_LIMIT_PERCENT, 1535f);

        private static final Grade[] VALUES = values();

        private final String fluid;
        private final float upTo, carbon, silicon, phosphorus, melt;

        Grade(String fluid, float upTo, float carbon, float silicon, float phosphorus, float melt)
        {
            this.fluid = fluid;
            this.upTo = upTo;
            this.carbon = carbon;
            this.silicon = silicon;
            this.phosphorus = phosphorus;
            this.melt = melt;
        }

        /** @return the temperature in C at which TFC's fluid of this iron melts, which is what a mould or crucible pours it at */
        public float melt()
        {
            return melt;
        }

        public String fluid()
        {
            return fluid;
        }

        public float carbon()
        {
            return carbon;
        }

        public float silicon()
        {
            return silicon;
        }

        public float phosphorus()
        {
            return phosphorus;
        }

        /** Names phosphorus-free iron by its carbon. The grades that carry phosphorus are never chosen here: see {@link ConverterBath#tapGrade()}. */
        public static Grade ofCarbon(float carbon)
        {
            for (Grade grade : VALUES)
            {
                if (grade.phosphorus > 0f)
                {
                    continue;
                }
                if (carbon + ROUNDING < grade.upTo) // Iron of 4% poured in by the drop should not come to just under it
                {
                    return grade;
                }
            }
            return PIG_IRON;
        }

        /** Names phosphorus-free iron by what it holds. Iron with the carbon of pig iron and less silicon than a pig iron counts as, which a blow or a blend leaves, is cast iron: it is never taken to hold more than that. */
        static Grade ofComposition(float carbon, float silicon)
        {
            final Grade byCarbon = ofCarbon(carbon);
            return byCarbon == PIG_IRON && silicon + ROUNDING < PIG_IRON.silicon ? CAST_IRON : byCarbon;
        }

        @Nullable
        public static Grade ofFluid(String fluid)
        {
            for (Grade grade : VALUES)
            {
                if (grade.fluid.equals(fluid))
                {
                    return grade;
                }
            }
            return null;
        }
    }

    /** What a blower can read from the flame at the mouth, which is how a blow was judged before anything could measure the metal. */
    public enum Flame
    {
        /** Silicon burns to slag, leaving no gas: sparks, and hardly a flame. */
        SPARKS,
        /** Carbon boils out as CO, which burns above the mouth in a long white flame. */
        BOILING,
        /** Carbon is running short. */
        SHORTENING,
        /** The carbon is gone. The flame falls away and brown smoke of burning iron takes its place. */
        DROPPED,
        /** Phosphorus burns on a basic lining with the carbon flame gone: thick white smoke of P2O5. */
        AFTERBLOW
    }

    /** The heat and the gas that came out of one step of a blow. */
    public record Blow(float carbonBurned, float oxygenUsed, float ironBurned, float phosphorusBurned)
    {
        public static final Blow NONE = new Blow(0f, 0f, 0f, 0f);
    }

    private static final float ROUNDING = 1e-3f;
    public static final float MILLIBUCKETS = 2000f;
    public static final float KG_PER_MILLIBUCKET = 0.0079f;
    public static final float AMBIENT = 20f;

    private static final float GRAMS_PER_MOL_CARBON = 12.011f, GRAMS_PER_MOL_SILICON = 28.086f, GRAMS_PER_MOL_IRON = 55.845f, GRAMS_PER_MOL_PHOSPHORUS = 30.974f;
    /** Heats of Si + O2 -> SiO2, C + 1/2 O2 -> CO and Fe + 1/2 O2 -> FeO, in kJ per mole of the element. */
    private static final float SILICON_HEAT = 911f, CARBON_HEAT = 110.5f, IRON_HEAT = 272f;
    /**
     * Heat of 2 P + 5/2 O2 -> P2O5 in kJ per mole of phosphorus: 2984 kJ for P4 + 5 O2 -> P4O10, over four.
     * It leaves out the heat that lime gives as it takes up the P2O5 in 4 CaO.P2O5, some 350 kJ more for each mole of phosphorus, so the afterblow of a real Thomas blow is hotter than this one.
     */
    private static final float PHOSPHORUS_HEAT = 745f;
    /** Moles of O2 for a mole of P: 2 P + 5/2 O2 -> P2O5. */
    private static final float OXYGEN_PER_PHOSPHORUS = 1.25f;
    /** Moles of CaO that slag needs to hold a mole of silicon (as 2 CaO.SiO2) and a mole of phosphorus (as 4 CaO.P2O5). */
    private static final float LIME_PER_SILICON = 2f, LIME_PER_PHOSPHORUS = 2f;
    /** Moles of CaO in one powder of lime. */
    public static final float LIME_MOLES_PER_POWDER = 7.5f;
    /** Molar heat of CaO, in kJ/mol/K. Lime goes in cold, and heating it to the temperature of the bath is a real cost of the Thomas process. */
    private static final float LIME_HEAT_CAPACITY = 0.048f;
    /** From this much phosphorus, in weight percent, iron is cold short. Iron within the rounding of it counts: see {@link #isPhosphoric()}. */
    public static final float PHOSPHORUS_LIMIT_PERCENT = 0.1f;
    /** Phosphorus burns only once the carbon no longer competes for the air. At twice the floor the carbon still takes a tenth of the oxygen, and less as it falls. */
    private static final float AFTERBLOW_CARBON_PERCENT = 0.06f;
    /** Below this much phosphorus the white smoke of P2O5 is too thin to see. */
    private static final float SMOKE_PERCENT = 0.02f;
    /** Below this much carbon, a bubble of air meets iron more often than it meets carbon. */
    private static final float CARBON_RACE_PERCENT = 0.3f;
    /** The least carbon air can take out of iron, where it stands in balance with the oxygen dissolved in the iron. */
    private static final float CARBON_FLOOR_PERCENT = 0.03f;
    /** Iron blown out of the mouth in sparks and fume, for each mole of carbon burned. Together with what burns, this gives the usual 5-10% lost in a blow. */
    private static final float IRON_THROWN_PER_CARBON = 0.3f;
    /** Molar heat of the nitrogen and CO that leave, in kJ/mol/K. */
    private static final float GAS_HEAT_CAPACITY = 0.030f;
    private static final float IRON_HEAT_CAPACITY = 0.82f;
    /**
     * A small vessel with its mouth open, far larger against its charge than a five tonne one: radiation from a mouth of 0.03 m2 at 1750 K alone is some 16 kW, and the shell loses more, so 0.01 kW/K gives 15 kW at the temperature of steel.
     * A blast has to bring in more heat than this to keep the iron liquid, which a pump on a water wheel does and a bellows in light wind does not.
     */
    private static final float LOSS_KILOWATTS_PER_KELVIN = 0.01f;
    private static final float LINING_HEAT_CAPACITY = 5f;
    private static final float LIQUIDUS_PURE_IRON = 1538f;
    /** The iron-carbon liquidus, from 1538 C for pure iron to the eutectic of 1147 C at 4.3% carbon, which is nearly straight. */
    private static final float LIQUIDUS_PER_CARBON_PERCENT = 91f;
    private static final float EUTECTIC = 1147f;

    /** The liquid iron in millibuckets. */
    public float metal;
    /** Carbon, silicon and phosphorus in weight percent. */
    public float carbon, silicon, phosphorus;
    public float temperature;
    /** What has gone from the metal to slag, in millibuckets of iron. */
    public float slag;
    /** Whether the lining is basic (dolomite) and not acid (silica). It decides whether phosphorus can leave the metal. */
    public boolean basic;
    /** Moles of CaO in the bath, waiting to take silica and phosphorus into slag. It only comes in through {@link #addLime}, which makes it cost heat. */
    private float lime;
    /** Moles of silica that formed on a basic lining with no lime to take it, and so attacked the lining instead. For the vessel to wear its lining by and reset. */
    public float silicaWithoutLime;
    /** Moles of phosphorus that have gone to slag as 4 CaO.P2O5, which is Thomas slag. For the vessel to turn into items and reset. */
    public float thomasSlagPhosphorus;

    /** @return the temperature in whole degrees C, as TFC states its melting points, at which iron of this much carbon starts to set */
    public static float liquidus(float carbonPercent)
    {
        return (float) Math.floor(Math.max(LIQUIDUS_PURE_IRON - LIQUIDUS_PER_CARBON_PERCENT * carbonPercent, EUTECTIC));
    }

    public boolean isMolten()
    {
        return metal > 0f && temperature >= liquidus(carbon);
    }

    /** @return the grade by carbon and silicon, which ignores phosphorus: use {@link #tapGrade()} for what a tap gives */
    public Grade grade()
    {
        return Grade.ofComposition(carbon, silicon);
    }

    /** @return {@code true} if the iron holds enough phosphorus to be cold short, counting what is within the rounding of the limit */
    public boolean isPhosphoric()
    {
        return phosphorus + ROUNDING >= PHOSPHORUS_LIMIT_PERCENT;
    }

    /**
     * @return what a tap gives. Every grade counts as the least of carbon, silicon and phosphorus that a tap of it holds, so that pouring it back can never give a bath more of them than it had.
     * With phosphorus it is cold short iron, unless the bath still holds everything that phosphoric pig iron counts as (4% carbon, 1% silicon, 1.5% phosphorus), which only a blast furnace gives and a blow or a blend takes away.
     * Without phosphorus the grade goes by carbon and silicon.
     */
    public Grade tapGrade()
    {
        if (isPhosphoric())
        {
            final Grade pig = Grade.PHOSPHORIC_PIG_IRON;
            final boolean holdsAll = carbon + ROUNDING >= pig.carbon && silicon + ROUNDING >= pig.silicon && phosphorus + ROUNDING >= pig.phosphorus;
            return holdsAll ? pig : Grade.COLD_SHORT_IRON;
        }
        return grade();
    }

    /** @return moles of CaO in the bath */
    public float lime()
    {
        return lime;
    }

    /**
     * Throws lime into the vessel, cold. It takes heat from the bath to reach its temperature; into an empty vessel it is held until the first iron is poured, which then pays for it.
     */
    public void addLime(float moles)
    {
        if (moles <= 0f)
        {
            return;
        }
        lime += moles;
        if (metal > 0f)
        {
            mixColdLime(moles);
        }
    }

    /** For loading a saved vessel only. Lime that was in a saved bath has already paid its heat. */
    public void restoreLime(float moles)
    {
        lime = Math.max(moles, 0f);
    }

    private void mixColdLime(float moles)
    {
        final float capacity = heatCapacity();
        final float limeCapacity = moles * LIME_HEAT_CAPACITY;
        temperature = (capacity * temperature + limeCapacity * AMBIENT) / (capacity + limeCapacity);
    }

    public Flame flame()
    {
        if (silicon > 0.02f) return Flame.SPARKS;
        if (carbon > 1f) return Flame.BOILING;
        if (carbon > Grade.WROUGHT_IRON.upTo) return Flame.SHORTENING;
        if (basic && lime > 0f && carbon <= AFTERBLOW_CARBON_PERCENT && phosphorus > SMOKE_PERCENT) return Flame.AFTERBLOW;
        return Flame.DROPPED;
    }

    /** @return the kilojoules needed to heat the charge and its lining by one kelvin */
    private float heatCapacity()
    {
        return metal * KG_PER_MILLIBUCKET * IRON_HEAT_CAPACITY + LINING_HEAT_CAPACITY;
    }

    /** Pours in more iron. Carbon, silicon, phosphorus and heat all mix by weight. */
    public void add(float amount, Grade grade, float sourceTemperature)
    {
        final float total = metal + amount;
        if (total > 0f)
        {
            final boolean emptyWithLime = metal <= 0f && lime > 0f;
            carbon = (carbon * metal + grade.carbon() * amount) / total;
            silicon = (silicon * metal + grade.silicon() * amount) / total;
            phosphorus = (phosphorus * metal + grade.phosphorus() * amount) / total;
            temperature = (temperature * metal + sourceTemperature * amount) / total;
            metal = total;
            if (emptyWithLime)
            {
                mixColdLime(lime); // Lime put in before the iron was poured, and cold
            }
        }
    }

    /**
     * Blows air through the bath. All the oxygen is used up, as it has a long way to rise through hot iron.
     *
     * @param oxygen moles of oxygen in the blast
     * @param gasThrough moles of all the gas in the blast, which comes out at the temperature of the iron
     * @param blastTemperature in C
     */
    public Blow blow(float oxygen, float gasThrough, float blastTemperature)
    {
        if (metal <= 0f || oxygen <= 0f)
        {
            return Blow.NONE;
        }

        final float grams = metal * KG_PER_MILLIBUCKET * 1000f;
        float left = oxygen;

        final float siliconBurned = Math.min(grams * silicon / 100f / GRAMS_PER_MOL_SILICON, left);
        left -= siliconBurned;
        final float limeForSilicon = Math.min(lime, siliconBurned * LIME_PER_SILICON);
        lime -= limeForSilicon;
        if (basic)
        {
            silicaWithoutLime += siliconBurned - limeForSilicon / LIME_PER_SILICON;
        }

        final float carbonAvailable = Math.max(grams * (carbon - CARBON_FLOOR_PERCENT) / 100f / GRAMS_PER_MOL_CARBON, 0f);
        final float race = Math.clamp((carbon - CARBON_FLOOR_PERCENT) / CARBON_RACE_PERCENT, 0f, 1f);
        final float carbonBurned = Math.min(left * race * 2f, carbonAvailable);
        left -= carbonBurned / 2f;

        // On an acid lining the silica slag cannot hold phosphorus, so what forms goes straight back into the iron and nothing is lost for good.
        // On a basic one it needs lime; with none, it stays too and the blast goes on to the iron.
        float phosphorusBurned = 0f;
        if (basic && lime > 0f && carbon <= AFTERBLOW_CARBON_PERCENT)
        {
            final float phosphorusAvailable = grams * phosphorus / 100f / GRAMS_PER_MOL_PHOSPHORUS;
            phosphorusBurned = Math.max(Math.min(Math.min(phosphorusAvailable, Math.max(left, 0f) / OXYGEN_PER_PHOSPHORUS), lime / LIME_PER_PHOSPHORUS), 0f);
            left -= phosphorusBurned * OXYGEN_PER_PHOSPHORUS;
            lime -= phosphorusBurned * LIME_PER_PHOSPHORUS;
            thomasSlagPhosphorus += phosphorusBurned;
        }

        final float thrown = carbonBurned * IRON_THROWN_PER_CARBON;
        final float ironAvailable = grams / GRAMS_PER_MOL_IRON - thrown;
        final float ironBurned = Math.clamp(left * 2f, 0f, Math.max(ironAvailable, 0f));
        final float used = oxygen - left + ironBurned / 2f;

        final float heat = siliconBurned * SILICON_HEAT + carbonBurned * CARBON_HEAT + phosphorusBurned * PHOSPHORUS_HEAT + ironBurned * IRON_HEAT - gasThrough * GAS_HEAT_CAPACITY * (temperature - blastTemperature);
        temperature += heat / heatCapacity();

        final float ironLost = (ironBurned + thrown) * GRAMS_PER_MOL_IRON / (KG_PER_MILLIBUCKET * 1000f);
        final float remaining = Math.max(grams - siliconBurned * GRAMS_PER_MOL_SILICON - carbonBurned * GRAMS_PER_MOL_CARBON - phosphorusBurned * GRAMS_PER_MOL_PHOSPHORUS - (ironBurned + thrown) * GRAMS_PER_MOL_IRON, 0f);
        if (remaining > 0f)
        {
            silicon = Math.max((grams * silicon / 100f - siliconBurned * GRAMS_PER_MOL_SILICON) / remaining * 100f, 0f);
            carbon = Math.max((grams * carbon / 100f - carbonBurned * GRAMS_PER_MOL_CARBON) / remaining * 100f, 0f);
            phosphorus = Math.max((grams * phosphorus / 100f - phosphorusBurned * GRAMS_PER_MOL_PHOSPHORUS) / remaining * 100f, 0f);
        }
        slag += ironLost;
        metal = remaining / (KG_PER_MILLIBUCKET * 1000f); // The carbon, silicon and phosphorus burned have gone as well, as gas and into the slag
        return new Blow(carbonBurned, used, ironBurned, phosphorusBurned);
    }

    /** Lets the vessel lose heat to the air. */
    public void cool(float seconds)
    {
        if (temperature > AMBIENT)
        {
            temperature = AMBIENT + (temperature - AMBIENT) * (float) Math.exp(-LOSS_KILOWATTS_PER_KELVIN * seconds / heatCapacity());
        }
    }

    /** Takes up to this much metal out, and whatever is too little to pour goes with the slag. @return how much came out, in whole millibuckets */
    public int tap(int amount)
    {
        final int tapped = Math.min(amount, (int) metal);
        metal -= tapped;
        if (metal < 1f)
        {
            // Too little to pour, and it stays behind on the lining
            slag += metal;
            metal = 0f;
            carbon = 0f;
            silicon = 0f;
            phosphorus = 0f;
            lime = 0f; // What is left goes out with the slag, and must not carry into the next charge
        }
        return tapped;
    }
}
