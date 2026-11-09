package tfcpp.ecology;

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
    /** The calendar tick that this state is as of. It never goes back. */
    long lastTick;

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
        copy.lastTick = lastTick;
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
            return ((startSaturation - baseSaturation) * cec + aluminium) * SoilConstants.AREA;
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
        return pollutant == Pollutant.ACIDITY ? (carbonateSpent + weathered) * SoilConstants.AREA : 0.0;
    }

    public long lastTick()
    {
        return lastTick;
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
        acid -= neutralised;

        final double fromExchanger = Math.min(acid, baseSaturation * cec);
        if (fromExchanger > 0.0)
        {
            baseSaturation = Math.max(0.0, baseSaturation - fromExchanger / cec);
            acid -= fromExchanger;
        }
        aluminium += acid; // Past base saturation 0 the acid goes on collecting, though the pH stays at its floor
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

    /** Lets this many days of the calendar pass: tar and sewage break down, and weathering gives base cations back. */
    public void advance(double days)
    {
        if (!(days > 0.0))
        {
            return;
        }
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            final double rate = SoilConstants.decayPerDay(pollutant);
            final int i = pollutant.ordinal();
            if (rate > 0.0 && present[i] > 0.0)
            {
                final double lost = present[i] * -Math.expm1(-rate * days);
                present[i] -= lost;
                decayed[i] += lost;
            }
        }
        // Weathering gives base cations back to the exchanger, after the acid held as aluminium is used up, and only to the saturation it started at
        double supply = SoilConstants.WEATHERING_PER_YEAR * days / SoilConstants.DAYS_PER_YEAR;
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
