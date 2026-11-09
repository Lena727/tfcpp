package tfcpp.ecology;

import org.jetbrains.annotations.Nullable;

/**
 * What one chunk's soil remembers: the state of its acid buffer and the totals of everything put into it. Free of Minecraft types.
 * <p>
 * Acid meets three buffers in order. Free carbonate, where the soil has any, holds the pH at its plateau and neutralises the acid for good. Then the cation exchanger: acid takes base cations off its sites, which lowers base saturation, and the pH follows it, pH = 4.2 + 2.8 BS. At base saturation 0 the aluminium range begins, where acid is held as dissolved aluminium. Silicate weathering is a flux that gives base cations back to the exchanger, up to the saturation the soil started with and not above it.
 * <p>
 * Time goes in by {@link #advance}, and every step is closed form, so that one step of many days is the same as as many of one day.
 */
public final class SoilCell
{
    /** The range a soil is buffering in now. */
    public enum Range
    {
        CARBONATE, EXCHANGER, ALUMINIUM
    }

    // Equivalents per square metre
    double carbonate, aluminium;
    /** Base saturation of the exchanger, 0 to 1, and the value it started with, which weathering brings it back to and no further. */
    double baseSaturation, startSaturation;
    /** Exchange capacity, equivalents per square metre. */
    double cec;
    /** Cumulative equivalents per square metre neutralised by carbonate, and by weathering. */
    double carbonateSpent, weathered;
    /** Totals in the units of {@link Pollutant}. The acidity slot is not used: acid is kept in the exchanger and the aluminium. */
    final double[] input = new double[Pollutant.COUNT];
    final double[] present = new double[Pollutant.COUNT];
    final double[] decayed = new double[Pollutant.COUNT];
    /** For each pollutant, the cumulative fall per square metre of the regional air cell over this chunk that the soil has already taken in. */
    final double[] claimed = new double[Pollutant.COUNT];
    /** Everything of each pollutant that the soil has given up to the water by runoff, for the whole cell, in the unit of the pollutant. For acidity it is the acid that left from the aluminium range. */
    final double[] washed = new double[Pollutant.COUNT];
    /** What has run off and not yet been handed to the water: {@link #takeOutbox} hands it on. Held, and saved, while it is not known where the chunk drains. */
    final double[] outbox = new double[Pollutant.COUNT];
    boolean hasOutbox;
    /** Where the runoff of the chunk goes. A fact of the place, found once and saved. */
    Drainage drainage = Drainage.UNKNOWN;
    /** The share of the chunk's surface columns that are not under water, found once when the cell is made and saved: the air's fall on the part that is water is taken by the water, so the soil claims only its land. */
    double landFraction = 1.0;
    /** The calendar tick that this state is as of. It never goes back. */
    long lastTick;
    /** Cumulative equivalents per square metre of acid that liming neutralised. */
    double limedNeutralised;
    /** Health of a stand of broadleaf trees and of conifers in this chunk, 0 to 1, and the calendar tick and annual mean of SO2 (ug/m3) they are as of. See {@link ForestHealth}. */
    double forestBroadleaf = 1.0, forestConifer = 1.0;
    /** The integrals of the excess of the annual mean of SO2 over the critical levels (ug/m3 hours) that this soil last saw in its regional air cell, for conifers and for broadleaves. The difference of two readings is the exact mean excess over the gap between them. */
    double seenExcessConifer, seenExcessBroadleaf;
    /** Lime: equivalents per square metre ever put on, what has leached back out of the base saturation and carbonate that lime raised above where the soil started, and the part of the free carbonate that came from lime. */
    double limeApplied, limeLeached, limeCarbonate;
    long forestTick = Long.MIN_VALUE;
    /** What plants have taken out of the soil, in the unit of each pollutant, for the whole cell. Not saved apart from the soil: it is part of the books. */
    final double[] harvested = new double[Pollutant.COUNT];
    /** The calendar hour at which the forest was last brought up to date by a look from a random tick. Not saved. */
    long forestCheckedHour = Long.MIN_VALUE;

    SoilCell()
    {
    }

    /**
     * @param order the soil at the surface
     * @param carbonateBedrock whether the rock under it is limestone, dolomite, chalk or marble. Its carbonate is taken as the full range regardless of the soil above it: the water that rises through such a rock keeps the root zone supplied, and the exchanger is full of calcium
     * @param nowTick the calendar tick now
     */
    public static SoilCell fresh(SoilOrder order, boolean carbonateBedrock, long nowTick)
    {
        final SoilCell cell = new SoilCell();
        cell.lastTick = nowTick;
        cell.forestTick = nowTick;
        cell.cec = order.cecPerM2();
        final boolean carbonate = carbonateBedrock || order.hasFreeCarbonate();
        if (carbonate)
        {
            cell.carbonate = SoilConstants.CARBONATE_FULL;
        }
        // With carbonate the exchanger is full of calcium. Without it the pH says how full
        cell.startSaturation = carbonate ? 1.0 : Math.min(1.0, (order.startPh() - SoilConstants.PH_EXCHANGER_BOTTOM) / (SoilConstants.PH_EXCHANGER_TOP - SoilConstants.PH_EXCHANGER_BOTTOM));
        cell.baseSaturation = cell.startSaturation;
        return cell;
    }

    public SoilCell copy()
    {
        final SoilCell copy = new SoilCell();
        copy.carbonate = carbonate;
        copy.aluminium = aluminium;
        copy.baseSaturation = baseSaturation;
        copy.startSaturation = startSaturation;
        copy.cec = cec;
        copy.carbonateSpent = carbonateSpent;
        copy.weathered = weathered;
        System.arraycopy(input, 0, copy.input, 0, Pollutant.COUNT);
        System.arraycopy(present, 0, copy.present, 0, Pollutant.COUNT);
        System.arraycopy(decayed, 0, copy.decayed, 0, Pollutant.COUNT);
        System.arraycopy(claimed, 0, copy.claimed, 0, Pollutant.COUNT);
        System.arraycopy(washed, 0, copy.washed, 0, Pollutant.COUNT);
        System.arraycopy(outbox, 0, copy.outbox, 0, Pollutant.COUNT);
        copy.hasOutbox = hasOutbox;
        copy.drainage = drainage;
        copy.landFraction = landFraction;
        copy.lastTick = lastTick;
        copy.limedNeutralised = limedNeutralised;
        copy.forestBroadleaf = forestBroadleaf;
        copy.forestConifer = forestConifer;
        copy.seenExcessConifer = seenExcessConifer;
        copy.seenExcessBroadleaf = seenExcessBroadleaf;
        copy.limeApplied = limeApplied;
        copy.limeLeached = limeLeached;
        copy.limeCarbonate = limeCarbonate;
        copy.forestTick = forestTick;
        System.arraycopy(harvested, 0, copy.harvested, 0, Pollutant.COUNT);
        return copy;
    }

    // --- Reading ---------------------------------------------------------------------------------------------------------------------------------------------------------

    /** @return the pH of the root zone */
    public double pH()
    {
        if (carbonate > 0.0)
        {
            return SoilConstants.PH_CARBONATE_PLATEAU;
        }
        if (aluminium > 0.0)
        {
            return SoilConstants.PH_EXCHANGER_BOTTOM - (SoilConstants.PH_EXCHANGER_BOTTOM - SoilConstants.PH_ALUMINIUM_BOTTOM) * Math.min(1.0, aluminium / SoilConstants.ALUMINIUM_RANGE);
        }
        return SoilConstants.PH_EXCHANGER_BOTTOM + (SoilConstants.PH_EXCHANGER_TOP - SoilConstants.PH_EXCHANGER_BOTTOM) * baseSaturation;
    }

    /** @return the range that the soil is buffering in */
    public Range range()
    {
        if (carbonate > 0.0)
        {
            return Range.CARBONATE;
        }
        return aluminium > 0.0 ? Range.ALUMINIUM : Range.EXCHANGER;
    }

    /** @return equivalents per square metre of carbonate that is left to neutralise acid */
    public double carbonatePerM2()
    {
        return carbonate;
    }

    /** @return base saturation of the exchanger, 0 to 1 */
    public double baseSaturation()
    {
        return baseSaturation;
    }

    /** @return equivalents per square metre of exchange capacity */
    public double cecPerM2()
    {
        return cec;
    }

    /** @return equivalents per square metre of acid held as aluminium, below base saturation 0 */
    public double aluminiumPerM2()
    {
        return aluminium;
    }

    /** @return equivalents per square metre of acid that lower the pH by one unit while the exchanger is buffering: its capacity over the 2.8 units from base saturation 1 to 0 */
    public double acidPerPhUnit()
    {
        return cec / (SoilConstants.PH_EXCHANGER_TOP - SoilConstants.PH_EXCHANGER_BOTTOM);
    }

    /** @return the total of this pollutant in the cell, in its unit. For acidity this is the acid put in that is still held, which the soil started without */
    public double total(Pollutant pollutant)
    {
        if (pollutant == Pollutant.ACIDITY)
        {
            return (Math.max(0.0, startSaturation - baseSaturation) * cec + aluminium) * SoilConstants.AREA;
        }
        return present[pollutant.ordinal()];
    }

    /** @return the amount per square metre, which is what a threshold is written in */
    public double perM2(Pollutant pollutant)
    {
        return total(pollutant) / SoilConstants.AREA;
    }

    /** @return everything of this kind that was ever put into the cell */
    public double totalInput(Pollutant pollutant)
    {
        return input[pollutant.ordinal()];
    }

    /** @return what has broken down: tar and sewage only */
    public double totalDecayed(Pollutant pollutant)
    {
        return decayed[pollutant.ordinal()];
    }

    /** @return acid neutralised by carbonate or weathering, in equivalents of the whole cell. Nothing else is neutralised */
    public double totalNeutralised(Pollutant pollutant)
    {
        return pollutant == Pollutant.ACIDITY ? (carbonateSpent + weathered + limedNeutralised) * SoilConstants.AREA : 0.0;
    }

    public long lastTick()
    {
        return lastTick;
    }

    /** @return everything of this kind that has run off to the water, for the whole cell */
    public double totalWashed(Pollutant pollutant)
    {
        return washed[pollutant.ordinal()];
    }

    /** @return the share of the chunk that is land, 0 to 1 */
    public double landFraction()
    {
        return landFraction;
    }

    /** Says how much of the chunk is land. Only for a cell that is being made, or a test. */
    public void setLandFraction(double fraction)
    {
        landFraction = Math.max(0.0, Math.min(1.0, fraction));
    }

    public Drainage drainage()
    {
        return drainage;
    }

    /** Says where the runoff of the chunk goes, once it is known. {@link Drainage#UNKNOWN} changes nothing: what was found stays found. */
    public void setDrainage(Drainage found)
    {
        if (found != Drainage.UNKNOWN)
        {
            drainage = found;
        }
    }

    /** Gives back to the soil what the outbox holds, for a chunk found to drain to nothing after all: it was held and not handed on. */
    public void returnOutbox()
    {
        if (!hasOutbox)
        {
            return;
        }
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            final int i = pollutant.ordinal();
            if (outbox[i] > 0.0)
            {
                if (pollutant == Pollutant.ACIDITY)
                {
                    aluminium += outbox[i] / SoilConstants.AREA;
                }
                else
                {
                    present[i] += outbox[i];
                }
                washed[i] -= outbox[i];
                outbox[i] = 0.0;
            }
        }
        hasOutbox = false;
    }

    /** @return {@code true} if something has run off and is held */
    public boolean holdsOutbox()
    {
        return hasOutbox;
    }

    /** @return what has run off since the last call, in the units of the pollutants and for the whole cell, or {@code null} if nothing has. The cell forgets it: the caller must put it into the water. */
    @Nullable
    public double[] takeOutbox()
    {
        if (!hasOutbox)
        {
            return null;
        }
        final double[] taken = outbox.clone();
        java.util.Arrays.fill(outbox, 0.0);
        hasOutbox = false;
        return taken;
    }

    // --- Writing -----------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Puts this much of a pollutant into the cell. Acid is shared over the cell's whole area and taken up by the buffers in order.
     *
     * @param amount in the unit of the pollutant, for the whole cell
     */
    public void deposit(Pollutant pollutant, double amount)
    {
        if (!(amount > 0.0))
        {
            return;
        }
        input[pollutant.ordinal()] += amount;
        if (pollutant != Pollutant.ACIDITY)
        {
            present[pollutant.ordinal()] += amount;
            return;
        }
        double acid = amount / SoilConstants.AREA;
        final double neutralised = Math.min(acid, carbonate);
        carbonate -= neutralised;
        carbonateSpent += neutralised;
        limeCarbonate = Math.min(limeCarbonate, carbonate);
        acid -= neutralised;

        final double fromExchanger = Math.min(acid, baseSaturation * cec);
        if (fromExchanger > 0.0)
        {
            limedNeutralised += Math.min(fromExchanger, Math.max(0.0, (baseSaturation - startSaturation) * cec)); // Base that lime put above the start neutralises first, and is counted as lime's work
            baseSaturation = Math.max(0.0, baseSaturation - fromExchanger / cec);
            acid -= fromExchanger;
        }
        aluminium += acid; // Past base saturation 0 the acid goes on collecting, though the pH stays at its floor
    }

    /**
     * Takes in what the regional air has let fall on this chunk since the last time: the difference between the cumulative fall per square metre and what was taken before, over the 256 square metres of the cell. Called before the cell is brought forward in time, so that the fall decays or is weathered for the whole interval; the error of that is at most the weathering flux over the interval, 0.01 eq/m2 a year, since the other pollutants of the air do not decay.
     */
    public void absorb(Pollutant pollutant, double cumulativePerM2)
    {
        final int i = pollutant.ordinal();
        if (cumulativePerM2 > claimed[i])
        {
            final double fall = (cumulativePerM2 - claimed[i]) * SoilConstants.AREA * landFraction;
            claimed[i] = cumulativePerM2;
            deposit(pollutant, fall);
        }
    }

    /** @return the cumulative fall per square metre that has been taken in so far */
    public double claimed(Pollutant pollutant)
    {
        return claimed[pollutant.ordinal()];
    }

    // --- Plants and lime -----------------------------------------------------------------------------------------------------------------------------------------------

    /** @return the concentration of a metal in the dry soil, mg/kg */
    public double mgPerKg(Pollutant metal)
    {
        return CropEffects.mgPerKg(present[metal.ordinal()]);
    }

    /** Takes this much of a pollutant out of the soil into a plant, no more than there is; the amount taken is returned and counted as harvested. */
    public double uptake(Pollutant pollutant, double amount)
    {
        final int i = pollutant.ordinal();
        final double taken = Math.max(0.0, Math.min(amount, present[i]));
        present[i] -= taken;
        harvested[i] += taken;
        return taken;
    }

    /** @return everything of this kind that plants have taken out of the soil, for the whole cell */
    public double totalHarvested(Pollutant pollutant)
    {
        return harvested[pollutant.ordinal()];
    }

    /**
     * Lime on the soil, in equivalents of carbonate for the whole cell. It neutralises what is acid in the order that acid is held: aluminium first, then the exchanger back to the saturation the soil started at; what is left raises the exchanger above its start (up to full saturation) and then is free carbonate that holds the pH at its plateau. Metals do not leave. What lime raised above the start does not stay: it leaches at first order with a half-life of {@link SoilConstants#LIME_HALF_LIFE_YEARS} TFC years, and weathering still returns the soil only to its original saturation, so that lime must be renewed.
     */
    public void lime(double equivalents)
    {
        if (!(equivalents > 0.0))
        {
            return;
        }
        double eq = equivalents / SoilConstants.AREA;
        limeApplied += eq;
        final double fromAluminium = Math.min(eq, aluminium);
        aluminium -= fromAluminium;
        limedNeutralised += fromAluminium;
        eq -= fromAluminium;
        if (cec > 0.0)
        {
            final double deficit = Math.max(0.0, (startSaturation - baseSaturation) * cec);
            final double toExchanger = Math.min(eq, deficit);
            baseSaturation += toExchanger / cec;
            limedNeutralised += toExchanger;
            eq -= toExchanger;
            final double room = Math.max(0.0, (1.0 - baseSaturation) * cec);
            final double surplus = Math.min(eq, room);
            baseSaturation += surplus / cec;
            eq -= surplus;
        }
        carbonate += eq;
        limeCarbonate += eq;
    }

    /** @return equivalents per square metre of what lime has put into the soil that is still there above the start: base saturation over where it started, and carbonate that came from lime */
    public double limeSurplusPerM2()
    {
        return Math.max(0.0, baseSaturation - startSaturation) * cec + limeCarbonate;
    }

    /** @return equivalents per square metre of lime ever put on */
    public double limeAppliedPerM2()
    {
        return limeApplied;
    }

    /** @return equivalents per square metre of what lime raised that has leached out again */
    public double limeLeachedPerM2()
    {
        return limeLeached;
    }

    /** @return equivalents per square metre of acid that lime has neutralised */
    public double limeNeutralisedPerM2()
    {
        return limedNeutralised;
    }

    /** @return the pH that the soil started at, from the base saturation it started with (a soil with free carbonate started full, which is the same to a crop: above 5.5) */
    public double startPh()
    {
        return SoilConstants.PH_EXCHANGER_BOTTOM + (SoilConstants.PH_EXCHANGER_TOP - SoilConstants.PH_EXCHANGER_BOTTOM) * startSaturation;
    }

    public double forestHealth(boolean conifer)
    {
        return conifer ? forestConifer : forestBroadleaf;
    }

    /** Sets the health of both kinds of stand as of this tick, and the integrals that the soil has seen, for tests that need a forest in a known state. */
    public void setForestHealth(double broadleaf, double conifer, long tick, double seenConifer, double seenBroadleaf)
    {
        forestBroadleaf = broadleaf;
        forestConifer = conifer;
        forestTick = tick;
        seenExcessConifer = seenConifer;
        seenExcessBroadleaf = seenBroadleaf;
    }

    /** Says that the soil has seen the air of its region up to these integrals, as when it is made in a region that smoke has been over before the years that matter. */
    public void markSeen(double excessConifer, double excessBroadleaf)
    {
        seenExcessConifer = excessConifer;
        seenExcessBroadleaf = excessBroadleaf;
    }

    /** Sets where the forest clock starts, for a soil that is first made after smoke has been over the region for a while: at the first hour of the region's annual mean, from which its integrals count, so that what happened in the years before the first touch is not lost. */
    public void startForestAt(long tick)
    {
        if (tick < forestTick)
        {
            forestTick = tick;
            seenExcessConifer = 0.0;
            seenExcessBroadleaf = 0.0;
        }
    }

    /**
     * Brings the health of the forest to this calendar tick. The stress from SO2 over the gap is that of the mean excess of the annual mean over the critical level, worked out exactly from the two readings of the region's integrals; the stress of the soil is that of the soil now. Under constant stress the equation is solved in closed form, so a step of any length is one evaluation. Called when the soil is touched or looked at: nothing ticks.
     *
     * @param excessConifer the integral now of the excess of the annual mean over 10 ug/m3, in ug/m3 hours
     * @param excessBroadleaf the same over 20 ug/m3
     */
    public void advanceForest(long nowTick, double excessConifer, double excessBroadleaf)
    {
        if (forestTick == Long.MIN_VALUE)
        {
            forestTick = nowTick;
            seenExcessConifer = excessConifer;
            seenExcessBroadleaf = excessBroadleaf;
            return;
        }
        if (nowTick <= forestTick)
        {
            return;
        }
        final double hours = (nowTick - forestTick) / (double) AirScale.TICKS_PER_HOUR;
        final double years = (nowTick - forestTick) / (SoilConstants.CALENDAR_TICKS_PER_DAY * SoilConstants.daysPerYear());
        // A region cell that was forgotten and made again starts its integrals from zero: then what it shows is all of the gap
        final double meanConifer = (excessConifer >= seenExcessConifer ? excessConifer - seenExcessConifer : excessConifer) / hours;
        final double meanBroadleaf = (excessBroadleaf >= seenExcessBroadleaf ? excessBroadleaf - seenExcessBroadleaf : excessBroadleaf) / hours;
        final double soil = ForestHealth.soilStress(pH(), mgPerKg(Pollutant.COPPER), mgPerKg(Pollutant.ZINC));
        forestBroadleaf = ForestHealth.advance(forestBroadleaf, years, ForestHealth.so2Stress(false, meanBroadleaf) + soil);
        forestConifer = ForestHealth.advance(forestConifer, years, ForestHealth.so2Stress(true, meanConifer) + soil);
        forestTick = nowTick;
        seenExcessConifer = excessConifer;
        seenExcessBroadleaf = excessBroadleaf;
    }

    /** Brings the cell forward to this calendar tick. The clock never goes back: a tick before the last one does nothing, as when the calendar has not yet read its saved date. */
    public void catchUp(long nowTick)
    {
        if (nowTick > lastTick)
        {
            advance((nowTick - lastTick) / (double) SoilConstants.CALENDAR_TICKS_PER_DAY);
            lastTick = nowTick;
        }
    }

    /**
     * Lets this many days of the calendar pass: tar and sewage break down, and in a chunk that drains to water the soluble load runs off to it; weathering gives base cations back. Every step is closed form, so one step of many days is the same as as many of one day.
     * <p>
     * Breakdown and runoff are two first-order losses at the same time, and share what leaves in proportion to their rates. The acid held as aluminium loses 10% a year to the water while weathering supplies base cations to it at a steady rate: da/dt = -r a - w, so a(t) = (a0 + w/r) e^(-rt) - w/r until the aluminium is gone, after which weathering goes to the exchanger.
     */
    public void advance(double days)
    {
        if (!(days > 0.0))
        {
            return;
        }
        // What lime raised above where the soil started leaches back out at first order
        final double keep = Math.exp(-Math.log(2.0) / (SoilConstants.LIME_HALF_LIFE_YEARS * SoilConstants.daysPerYear()) * days);
        if (cec > 0.0 && baseSaturation > startSaturation)
        {
            final double excess = baseSaturation - startSaturation;
            limeLeached += excess * (1.0 - keep) * cec;
            baseSaturation = startSaturation + excess * keep;
        }
        if (limeCarbonate > 0.0)
        {
            final double lost = limeCarbonate * (1.0 - keep);
            limeCarbonate -= lost;
            carbonate -= lost;
            limeLeached += lost;
        }
        final boolean drains = drainage.mayDrain(); // Until it is known where the chunk drains, the runoff is worked out and held in the outbox
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            final double breakdown = SoilConstants.decayPerDay(pollutant);
            final double runoff = drains ? SoilConstants.runoffPerDay(pollutant) : 0.0;
            final int i = pollutant.ordinal();
            final double rate = breakdown + runoff;
            if (rate > 0.0 && present[i] > 0.0)
            {
                final double lost = present[i] * -Math.expm1(-rate * days);
                present[i] -= lost;
                decayed[i] += lost * breakdown / rate;
                final double ran = lost * runoff / rate;
                if (ran > 0.0)
                {
                    washed[i] += ran;
                    outbox[i] += ran;
                    hasOutbox = true;
                }
            }
        }
        // Weathering gives base cations back to the exchanger, after the acid held as aluminium is used up, and only to the saturation it started at
        double supply = SoilConstants.WEATHERING_PER_YEAR * days / SoilConstants.daysPerYear();
        if (drains && aluminium > 0.0)
        {
            final double w = SoilConstants.WEATHERING_PER_YEAR / SoilConstants.daysPerYear(), r = SoilConstants.aluminiumAcidRunoffPerDay();
            final double gone = Math.log1p(r * aluminium / w) / r; // Days until the aluminium acid is used up
            final double t = Math.min(days, gone);
            final double left = gone <= days ? 0.0 : Math.max(0.0, (aluminium + w / r) * Math.exp(-r * t) - w / r);
            final double byWeathering = w * t;
            final double ran = Math.max(0.0, aluminium - left - byWeathering);
            aluminium = left;
            weathered += byWeathering;
            supply = w * (days - t);
            if (ran > 0.0)
            {
                washed[Pollutant.ACIDITY.ordinal()] += ran * SoilConstants.AREA;
                outbox[Pollutant.ACIDITY.ordinal()] += ran * SoilConstants.AREA;
                hasOutbox = true;
            }
        }
        final double fromAluminium = Math.min(supply, aluminium);
        aluminium -= fromAluminium;
        supply -= fromAluminium;
        final double room = Math.max(0.0, (startSaturation - baseSaturation) * cec);
        final double toExchanger = cec > 0.0 ? Math.min(supply, room) : 0.0;
        if (toExchanger > 0.0)
        {
            baseSaturation = Math.min(startSaturation, baseSaturation + toExchanger / cec);
        }
        weathered += fromAluminium + toExchanger;
    }
}
