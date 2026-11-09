package tfcpp.ecology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * What the fresh waters of a level hold of each pollutant: the water column and the bed of every reach that holds anything, and where the rest went. Free of Minecraft types, so that the transport is tested on a river graph that is made up.
 * <p>
 * Each reach is a well mixed volume. An hour (or, when the clock is behind, a stretch of hours) is one step: first what happens inside each reach (breakdown, settling to the bed, scour from it in heavy rain, neutralisation of acid by the alkalinity that the catchment brings), then what runs on to the next reach downstream, worked out from the content at the start of the transport so that the order of the reaches does not matter. If a reach would pass on more than half of itself in one substep the step is cut into substeps, as the air grid does.
 * <p>
 * Every amount that goes in is counted, and {@link #imbalance} is what is missing: inputs = in water + on the bed + broken down + neutralised + run to the sea + dropped as dust + waiting for its reach to be found.
 */
public final class WaterLedger
{
    /** What the weather is over a reach for the step: the intensity of rain there, TFC's scale of 0 to 1. */
    public interface RainSource
    {
        double intensity(ReachInfo info);
    }

    /** One reach that holds something. */
    public static final class Reach
    {
        private final ReachInfo info;
        final double[] water = new double[Pollutant.COUNT];
        final double[] sediment = new double[Pollutant.COUNT];
        private final double[] out = new double[Pollutant.COUNT];
        /** Equivalents of alkalinity that still water has in store: acid takes from it, the catchment gives it back slowly. Not used by a river. */
        double reserve;
        private boolean heavy;

        Reach(ReachInfo info)
        {
            this.info = info;
            this.reserve = info.still() ? info.alkalinityStock() : 0.0;
        }

        public ReachInfo info()
        {
            return info;
        }

        /** @return the amount in the water column, in the unit of the pollutant */
        public double water(Pollutant pollutant)
        {
            return water[pollutant.ordinal()];
        }

        /** @return the amount on the bed */
        public double sediment(Pollutant pollutant)
        {
            return sediment[pollutant.ordinal()];
        }

        /** @return the amount in a litre of the water, in the unit of the pollutant: grams of a metal, equivalents of acid, kilograms of tar, litres of sewage */
        public double perLitre(Pollutant pollutant)
        {
            return water[pollutant.ordinal()] / info.litres();
        }

        /** @return equivalents of alkalinity in store, for still water */
        public double reserve()
        {
            return reserve;
        }

        boolean isEmpty()
        {
            for (int i = 0; i < Pollutant.COUNT; i++)
            {
                if (water[i] > 0.0 || sediment[i] > 0.0)
                {
                    return false;
                }
            }
            return !info.still() || reserve >= info.alkalinityStock() * (1.0 - 1e-12);
        }
    }

    /** Where inputs wait until the reach that they belong to is known: a different list for each way of coming in. */
    public static final int CHANNELS = 8;

    private final Map<Long, ReachInfo> infos = new HashMap<>();
    private final Map<Long, Reach> active = new HashMap<>();
    @SuppressWarnings("unchecked")
    private final Map<Long, double[]>[] pending = new Map[CHANNELS];
    final double[] input = new double[Pollutant.COUNT];
    /** What the air let fall on the water, counted apart from the inputs so that the air ledger's fallen total and this one are not added up twice. */
    final double[] fromAir = new double[Pollutant.COUNT];
    /** For each reach, the cumulative fall per square metre of its regional air cell that it has already taken in: the same device as the soil's. */
    final Map<Long, double[]> claimedAir = new HashMap<>();
    final double[] decayed = new double[Pollutant.COUNT];
    final double[] neutralised = new double[Pollutant.COUNT];
    final double[] toSea = new double[Pollutant.COUNT];
    final double[] dropped = new double[Pollutant.COUNT];
    private Reach[] snapshot = new Reach[0];

    public WaterLedger()
    {
        for (int i = 0; i < CHANNELS; i++)
        {
            pending[i] = new HashMap<>();
        }
    }

    // --- Geometry ---------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Makes a reach known. Nothing is put into it. */
    public void register(ReachInfo info)
    {
        infos.putIfAbsent(info.id(), info);
    }

    public boolean knows(long id)
    {
        return infos.containsKey(id);
    }

    @Nullable
    public ReachInfo info(long id)
    {
        return infos.get(id);
    }

    public Set<Long> knownIds()
    {
        return infos.keySet();
    }

    public Collection<ReachInfo> infos()
    {
        return infos.values();
    }

    /** @return the reach if it holds something now */
    @Nullable
    public Reach reach(long id)
    {
        return active.get(id);
    }

    public Collection<Reach> reaches()
    {
        return active.values();
    }

    public int activeCount()
    {
        return active.size();
    }

    /** Forgets everything. */
    public void clear()
    {
        infos.clear();
        active.clear();
        for (Map<Long, double[]> map : pending)
        {
            map.clear();
        }
        claimedAir.clear();
        for (double[] totals : new double[][] {input, fromAir, decayed, neutralised, toSea, dropped})
        {
            java.util.Arrays.fill(totals, 0.0);
        }
    }

    private Reach reachFor(long id)
    {
        Reach reach = active.get(id);
        if (reach == null)
        {
            final @Nullable ReachInfo info = infos.get(id);
            if (info == null)
            {
                throw new IllegalArgumentException("Reach " + id + " is not known");
            }
            reach = new Reach(info);
            active.put(id, reach);
        }
        return reach;
    }

    /** Makes a reach that was read from storage active again, to be filled in with its contents. Its geometry must have been registered. */
    Reach restoreReach(long id)
    {
        return reachFor(id);
    }

    // --- Inputs ----------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Puts an amount into the water of a known reach. */
    public void add(long id, Pollutant pollutant, double amount)
    {
        if (!(amount > 0.0))
        {
            return;
        }
        reachFor(id).water[pollutant.ordinal()] += amount;
        input[pollutant.ordinal()] += amount;
    }

    /** Puts an amount straight on the bed of a known reach: molten lead and zinc that freeze to shot and mercury that sinks never go into the water column. */
    public void addToBed(long id, Pollutant pollutant, double amount)
    {
        if (!(amount > 0.0))
        {
            return;
        }
        reachFor(id).sediment[pollutant.ordinal()] += amount;
        input[pollutant.ordinal()] += amount;
    }

    /**
     * Takes in what the air has let fall on a reach since it last looked: the cumulative fall per square metre of its regional cell, less what it has taken before, over the area of water that the sky sees. Counted as a separate input ("from air"). The first look takes the whole history, as a soil does.
     */
    public void claimFall(long id, Pollutant pollutant, double cumulativePerM2)
    {
        claimFall(id, pollutant, cumulativePerM2, -1.0);
    }

    /** @param area the square metres of water to take the fall over, or a negative number for the whole surface of the reach */
    public void claimFall(long id, Pollutant pollutant, double cumulativePerM2, double area)
    {
        if (!(cumulativePerM2 > 0.0))
        {
            return;
        }
        final @Nullable ReachInfo info = infos.get(id);
        if (info == null)
        {
            return;
        }
        final double[] claimed = claimedAir.computeIfAbsent(id, k -> new double[Pollutant.COUNT]);
        final int i = pollutant.ordinal();
        if (cumulativePerM2 > claimed[i])
        {
            final double amount = (cumulativePerM2 - claimed[i]) * (area >= 0.0 ? area : info.surfaceArea());
            claimed[i] = cumulativePerM2;
            reachFor(id).water[i] += amount;
            fromAir[i] += amount;
        }
    }

    public double fromAir(Pollutant pollutant)
    {
        return fromAir[pollutant.ordinal()];
    }

    /** @return the cumulative fall per square metre that this reach has taken in */
    public double claimedFall(long id, Pollutant pollutant)
    {
        final @Nullable double[] claimed = claimedAir.get(id);
        return claimed == null ? 0.0 : claimed[pollutant.ordinal()];
    }

    /** Puts an amount straight into the sea, which is the case of a discharge into salt water. */
    public void addToSea(Pollutant pollutant, double amount)
    {
        if (amount > 0.0)
        {
            input[pollutant.ordinal()] += amount;
            toSea[pollutant.ordinal()] += amount;
        }
    }

    /** Puts an amount aside, counted as put in, until the reach it belongs to is found. {@code key} is a place, and {@code channel} the way it came in. */
    public void addPending(int channel, long key, Pollutant pollutant, double amount)
    {
        if (amount > 0.0)
        {
            pending[channel].computeIfAbsent(key, k -> new double[Pollutant.COUNT])[pollutant.ordinal()] += amount;
            input[pollutant.ordinal()] += amount;
        }
    }

    public boolean hasPending(int channel, long key)
    {
        return pending[channel].containsKey(key);
    }

    public Collection<Long> pendingKeys(int channel)
    {
        return new ArrayList<>(pending[channel].keySet());
    }

    /** @return the amounts waiting at this place for this way in, or {@code null} */
    @Nullable
    public double[] pending(int channel, long key)
    {
        return pending[channel].get(key);
    }

    /** Puts what waited at a place into a reach that is now known, or into the sea if {@code target} is {@link ReachInfo#SEA}. It was counted as put in when it was put aside. */
    public void movePending(int channel, long key, long target)
    {
        final @Nullable double[] amounts = pending[channel].remove(key);
        if (amounts == null)
        {
            return;
        }
        final @Nullable Reach reach = target == ReachInfo.SEA ? null : reachFor(target);
        for (int i = 0; i < Pollutant.COUNT; i++)
        {
            if (amounts[i] > 0.0)
            {
                if (reach == null)
                {
                    toSea[i] += amounts[i];
                }
                else
                {
                    if (channel >= WaterRoute.VALUES.length)
                    {
                        reach.sediment[i] += amounts[i]; // Molten metal and mercury sank
                    }
                    else
                    {
                        reach.water[i] += amounts[i];
                    }
                }
            }
        }
    }

    /** Takes what waited at a place out of the books altogether, because it is not water after all and goes to the soil: it is no longer counted as put in here. */
    @Nullable
    public double[] takePending(int channel, long key)
    {
        final @Nullable double[] amounts = pending[channel].remove(key);
        if (amounts != null)
        {
            for (int i = 0; i < Pollutant.COUNT; i++)
            {
                input[i] -= amounts[i];
            }
        }
        return amounts;
    }

    /** Puts amounts that were saved as waiting back, without counting them as put in again: they were counted when the ledger was saved. */
    void restorePending(int channel, long key, double[] amounts)
    {
        pending[channel].put(key, amounts);
    }

    // --- Time ------------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Lets time pass.
     *
     * @param dtHours how many hours of the calendar this step stands for: 1 when the clock is not behind
     * @param rain the weather over a reach, or {@code null} for none
     */
    public void step(double dtHours, @Nullable RainSource rain)
    {
        if (active.isEmpty() || !(dtHours > 0.0))
        {
            return;
        }
        snapshot = active.values().toArray(snapshot);
        final int count = active.size();
        // Substeps: no reach passes on more than half of itself at once
        final double totalSeconds = dtHours * WaterConstants.SECONDS_PER_HOUR;
        double largest = 0.0;
        for (int i = 0; i < count; i++)
        {
            final Reach reach = snapshot[i];
            largest = Math.max(largest, totalSeconds / reach.info.residenceSeconds());
            reach.heavy = rain != null && hasBed(reach) && rain.intensity(reach.info) >= WaterConstants.HEAVY_RAIN;
        }
        final int substeps = (int) Math.min(WaterConstants.MAX_SUBSTEPS, Math.max(1.0, Math.ceil(largest / WaterConstants.MAX_OUTFLOW_PER_SUBSTEP)));
        final double seconds = totalSeconds / substeps, days = seconds / WaterConstants.SECONDS_PER_DAY;

        for (int sub = 0; sub < substeps; sub++)
        {
            if (sub > 0)
            {
                snapshot = active.values().toArray(snapshot);
            }
            final int n = active.size();
            for (int i = 0; i < n; i++)
            {
                process(snapshot[i], seconds, days);
            }
            transport(n, seconds);
        }
        prune();
    }

    private static boolean hasBed(Reach reach)
    {
        for (double mass : reach.sediment)
        {
            if (mass > 0.0)
            {
                return true;
            }
        }
        return false;
    }

    /** What happens inside one reach in one substep. */
    private void process(Reach reach, double seconds, double days)
    {
        final boolean still = reach.info.still();
        final double[] water = reach.water, bed = reach.sediment;
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            final int i = pollutant.ordinal();
            final double rate = WaterConstants.decayPerDay(pollutant, still);
            if (rate > 0.0 && water[i] > 0.0)
            {
                final double lost = water[i] * -Math.expm1(-rate * days);
                water[i] -= lost;
                decayed[i] += lost;
            }
            else if (WaterConstants.isMetal(pollutant) && (water[i] > 0.0 || bed[i] > 0.0))
            {
                // Both from the content at the start of the substep: the bound share settles, and in heavy rain the bed gives some back
                final double settled = water[i] * -Math.expm1(-WaterConstants.PARTICLE_BOUND * WaterConstants.settlingPerDay(still) * days);
                final double scoured = reach.heavy ? bed[i] * -Math.expm1(-WaterConstants.RESUSPENSION_PER_DAY * days) : 0.0;
                water[i] += scoured - settled;
                bed[i] += settled - scoured;
            }
        }
        // Acid is used up by the alkalinity that the catchment brings, before the excess is left to flow on
        final int acid = Pollutant.ACIDITY.ordinal();
        double capacity = reach.info.alkalinityFluxPerSecond() * seconds;
        if (still)
        {
            reach.reserve = Math.min(reach.info.alkalinityStock(), reach.reserve + capacity);
            capacity = reach.reserve;
        }
        if (water[acid] > 0.0 && capacity > 0.0)
        {
            final double used = Math.min(water[acid], capacity);
            water[acid] -= used;
            neutralised[acid] += used;
            if (still)
            {
                reach.reserve -= used;
            }
        }
    }

    /** What flows on, all of it worked out from the contents as they are before any of it moves. */
    private void transport(int count, double seconds)
    {
        for (int r = 0; r < count; r++)
        {
            final Reach reach = snapshot[r];
            if (reach.info.still())
            {
                continue;
            }
            final double share = Math.min(1.0, seconds / reach.info.residenceSeconds());
            for (int i = 0; i < Pollutant.COUNT; i++)
            {
                reach.out[i] = reach.water[i] * share;
            }
        }
        for (int r = 0; r < count; r++)
        {
            final Reach reach = snapshot[r];
            if (reach.info.still())
            {
                continue;
            }
            final long drain = reach.info.drain();
            final @Nullable Reach next = drain != ReachInfo.SEA && infos.containsKey(drain) ? reachFor(drain) : null;
            for (int i = 0; i < Pollutant.COUNT; i++)
            {
                final double moved = reach.out[i];
                reach.water[i] -= moved;
                if (next == null)
                {
                    toSea[i] += moved; // The mouth. A drain that is not known counts as the sea too, which it is not
                }
                else
                {
                    next.water[i] += moved;
                }
            }
        }
    }

    /** Drops dust and forgets reaches that hold nothing. */
    private void prune()
    {
        final List<Long> gone = new ArrayList<>();
        for (Reach reach : active.values())
        {
            for (Pollutant pollutant : Pollutant.VALUES)
            {
                final int i = pollutant.ordinal();
                final double floor = WaterConstants.floorPerLitre(pollutant) * reach.info.litres();
                if (reach.water[i] > 0.0 && reach.water[i] < floor)
                {
                    dropped[i] += reach.water[i];
                    reach.water[i] = 0.0;
                }
                if (reach.sediment[i] > 0.0 && reach.sediment[i] < floor)
                {
                    dropped[i] += reach.sediment[i];
                    reach.sediment[i] = 0.0;
                }
            }
            if (reach.isEmpty())
            {
                gone.add(reach.info.id());
            }
        }
        for (long id : gone)
        {
            active.remove(id);
        }
    }

    // --- Totals -----------------------------------------------------------------------------------------------------------------------------------------------------------

    public double input(Pollutant pollutant)
    {
        return input[pollutant.ordinal()];
    }

    public double decayed(Pollutant pollutant)
    {
        return decayed[pollutant.ordinal()];
    }

    /** @return acid used up by the alkalinity of the water, in equivalents; 0 for everything else */
    public double neutralised(Pollutant pollutant)
    {
        return neutralised[pollutant.ordinal()];
    }

    public double toSea(Pollutant pollutant)
    {
        return toSea[pollutant.ordinal()];
    }

    /** @return what was too little to keep, the background floor */
    public double dropped(Pollutant pollutant)
    {
        return dropped[pollutant.ordinal()];
    }

    public double inWater(Pollutant pollutant)
    {
        double sum = 0.0;
        for (Reach reach : active.values())
        {
            sum += reach.water[pollutant.ordinal()];
        }
        return sum;
    }

    public double onBed(Pollutant pollutant)
    {
        double sum = 0.0;
        for (Reach reach : active.values())
        {
            sum += reach.sediment[pollutant.ordinal()];
        }
        return sum;
    }

    /** @return how many places have inputs waiting */
    public int pendingCount()
    {
        int count = 0;
        for (Map<Long, double[]> map : pending)
        {
            count += map.size();
        }
        return count;
    }

    public double pendingTotal(Pollutant pollutant)
    {
        double sum = 0.0;
        for (Map<Long, double[]> map : pending)
        {
            for (double[] amounts : map.values())
            {
                sum += amounts[pollutant.ordinal()];
            }
        }
        return sum;
    }

    /** @return what is missing from the books: put in, less everything that it can be found as. Zero up to rounding. */
    public double imbalance(Pollutant pollutant)
    {
        return input(pollutant) + fromAir(pollutant) - inWater(pollutant) - onBed(pollutant) - decayed(pollutant) - neutralised(pollutant) - toSea(pollutant) - dropped(pollutant) - pendingTotal(pollutant);
    }
}
