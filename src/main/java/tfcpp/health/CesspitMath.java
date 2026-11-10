package tfcpp.health;

/**
 * What lies in a cesspit and what comes out of it. Free of Minecraft types.
 * <p>
 * A pit holds a volume of waste, which does not rot away (it settles, and the liquid goes into the ground; the solids stay) and, apart from it, the amount of that waste that is still infective, in litres of fresh sewage, with the part of it that came from each kind of shedder. The infective amount dies out in the pit by T90 of 60 days (pathogens live longer in the cool, wet, anaerobic waste of a pit than in soil: the T90 of Salmonella and Shigella in faecal sludge is weeks to months, Feachem et al., Sanitation and Disease, 1983; Strauss 1985), and a share of the contents seeps into the ground every day: 1 % a day is a GAME FIGURE for a pit that is not sealed, which none of the pits of the age of the game was (the old pits were lined with brick and clay, which leaks).
 */
public final class CesspitMath
{
    /** Litres: a cubic metre. */
    public static final double CAPACITY_LITRES = 1000.0;
    /** Per day, the share of the contents that goes into the ground. */
    public static final double LEAK_PER_DAY = 0.01;
    /** Days for the pathogens in a pit to fall to a tenth. */
    public static final double PATHOGEN_T90_DAYS = 60.0;
    /** Litres of waste in one piece of night soil. */
    public static final double LITRES_PER_NIGHT_SOIL = 20.0;
    /** Blocks within which a person's waste goes into a pit and not onto the ground: a latrine that has to be walked to is used when it is near (the real rule is a few tens of metres at most). */
    public static final int REACH_HORIZONTAL = 16, REACH_VERTICAL = 4;
    /** Blocks around a pit in which water takes up what seeps from it: the rule for a well is at least 15 to 30 m from a latrine (WHO, Guidelines for Drinking-water Quality; Lewis et al., 1982, Water Res.). */
    public static final int GROUNDWATER_REACH = 30;
    /** Litres of sewage per litre of water at the pit itself, per litre of infective waste in it, and the distance in blocks over which it falls to a half of that (the factor is 1/(1 + distance/5)). A GAME FIGURE: 1000 litres of fresh waste give a part in a thousand of sewage in a well at the edge of the pit, a quarter of that at 15 m. */
    public static final double GROUNDWATER_PER_LITRE = 1e-6, GROUNDWATER_DISTANCE_SCALE = 5.0;

    private CesspitMath() {}

    /** The contents of a pit. */
    public record State(double volume, double active, double shedDysentery, double shedTyphoid)
    {
        public static final State EMPTY = new State(0.0, 0.0, 0.0, 0.0);
    }

    /** What happens to a pit over a stretch of time: its new contents, and what went into the ground (infective litres of each kind). */
    public record Step(State state, double leakedActive, double leakedDysentery, double leakedTyphoid, double leakedVolume)
    {
    }

    /** @return the rate of the leak, per day, as a first-order rate: {@code -ln(1 - LEAK_PER_DAY)}, so that a day takes exactly 1 % */
    public static double leakRate()
    {
        return -Math.log(1.0 - LEAK_PER_DAY);
    }

    public static double decayRate()
    {
        return Math.log(10.0) / PATHOGEN_T90_DAYS;
    }

    /** @return the pit after this many days, with what has seeped into the ground in them; the infective part of the contents is the sum of what is left, what has died and what has seeped away, which is checked */
    public static Step advance(State state, double days)
    {
        if (!(days > 0.0))
        {
            return new Step(state, 0.0, 0.0, 0.0, 0.0);
        }
        final double r = leakRate(), lambda = decayRate();
        final double volume = state.volume * Math.exp(-r * days);
        final double survive = Math.exp(-(r + lambda) * days);
        final double leakShare = r / (r + lambda) * (1.0 - survive);
        return new Step(new State(volume, state.active * survive, state.shedDysentery * survive, state.shedTyphoid * survive),
            state.active * leakShare, state.shedDysentery * leakShare, state.shedTyphoid * leakShare, state.volume - volume);
    }

    /** @return litres of a deposit that the pit has room for */
    public static double room(State state)
    {
        return Math.max(0.0, CAPACITY_LITRES - state.volume);
    }

    /** @return the pit with this much waste added (the part of it that fits; check {@link #room}); the waste is fresh, so all of it is infective, and {@code dysentery} and {@code typhoid} litres of it came from shedders */
    public static State add(State state, double litres, double dysentery, double typhoid)
    {
        return new State(state.volume + litres, state.active + litres, state.shedDysentery + dysentery, state.shedTyphoid + typhoid);
    }

    /** @return the pit after this many litres of its contents have been taken out: the infective part goes in proportion */
    public static State take(State state, double litres)
    {
        if (!(state.volume > 0.0) || !(litres > 0.0))
        {
            return state;
        }
        final double keep = Math.max(0.0, 1.0 - litres / state.volume);
        return new State(state.volume * keep, state.active * keep, state.shedDysentery * keep, state.shedTyphoid * keep);
    }

    /** @return the factor by which a pit's infective contents show in water at this distance in blocks: litres of sewage per litre of water, for each litre of infective waste in the pit */
    public static double groundwaterFactor(double distanceBlocks)
    {
        return GROUNDWATER_PER_LITRE / (1.0 + Math.max(distanceBlocks, 0.0) / GROUNDWATER_DISTANCE_SCALE);
    }

    /** What water takes from a pit: the pit after, and the infective litres of each kind that went with the water. */
    public record Draw(State state, double active, double shedDysentery, double shedTyphoid)
    {
    }

    /**
     * Water that is drawn near a pit carries off {@code litres * factor} litres of its infective contents (the concentration of {@link #groundwaterFactor}), and no more than the pit holds: what the water takes is gone from the pit, so that buckets cannot carry away more than there was. The volume stays (the water takes the pathogens, not the waste).
     */
    public static Draw draw(State state, double factor, double litres)
    {
        return draw(state, factor, litres, 1.0, 1.0, 1.0);
    }

    /** As {@link #draw(State, double, double)}, with the amount of each kind (all sewage, from shedders of Shigella, from shedders of S. Typhi) scaled: the water may carry a rounded figure, and what the pit loses is what the water carries. */
    public static Draw draw(State state, double factor, double litres, double scaleActive, double scaleDysentery, double scaleTyphoid)
    {
        if (!(litres > 0.0) || !(factor > 0.0))
        {
            return new Draw(state, 0.0, 0.0, 0.0);
        }
        final double active = Math.min(state.active * factor * litres * scaleActive, state.active);
        final double dysentery = Math.min(state.shedDysentery * factor * litres * scaleDysentery, state.shedDysentery);
        final double typhoid = Math.min(state.shedTyphoid * factor * litres * scaleTyphoid, state.shedTyphoid);
        return new Draw(new State(state.volume, state.active - active, state.shedDysentery - dysentery, state.shedTyphoid - typhoid), active, dysentery, typhoid);
    }
}
