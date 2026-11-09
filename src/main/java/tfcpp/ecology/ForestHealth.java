package tfcpp.ecology;

/**
 * The health of a stand of trees in one chunk, 0 to 1, as a calendar-time equation. Free of Minecraft types.
 * <p>
 * dh/dt = -k s h + r (1 - h), with s the stress (dimensionless, zero when nothing harms the stand), t in calendar years. For constant s it is solved in closed form: h(t) = h* + (h0 - h*) exp(-(k s + r) t), h* = r / (k s + r). A stand is damaged in proportion to what it has left and heals toward 1.
 * <p>
 * Stress is the sum of relative excesses over critical values: s = max(0, A - Ac)/Ac for the annual mean SO2 A (UNECE critical level for forests 20 ug/m3; conifers are about twice as sensitive and take 10; UNECE Mapping Manual 2017 ch. 3, Nilsson and Grennfelt 1988); (4.2 - pH)/0.3 for soil acidity under the aluminium range (a GAME FIGURE: pH 3.0 is as bad as a broadleaf stand at 100 ug/m3); and (c - t)/t for copper and zinc above the phytotoxic thresholds 100 and 300 mg/kg.
 * <p>
 * Calibration (GAME FIGURES with real anchors): k = 0.05 per year makes a conifer stand at a constant annual mean of 100 ug/m3 fall below h = 0.2 in about 5 years. The Ore Mountains spruce dieback of the 1970s and 80s took 10-15 years at 50-150 ug/m3 as emissions rose slowly over those years; a stand under a constant 100 dies faster. Recovery r = ln(10)/30 per year gives h = 0.9 thirty years after a dead stand is left in clean air; the real recovery after the cleanup of the 1990s took decades (Schulze 1989, Science 244; Ulrich 1992). A stand does not heal fully while stressed: h* is its equilibrium.
 */
public final class ForestHealth
{
    public static final double K = 0.05;
    public static final double R = Math.log(10.0) / 30.0;
    public static final double SO2_CRITICAL_BROADLEAF = 20.0, SO2_CRITICAL_CONIFER = 10.0;
    public static final double PH_ALUMINIUM = 4.2, PH_STRESS_WIDTH = 0.3;
    public static final double COPPER_THRESHOLD = 100.0, ZINC_THRESHOLD = 300.0;
    /** Leaves begin to fall below this health, saplings stop growing below the other. */
    public static final double LEAVES_FALL_BELOW = 0.7, SAPLINGS_STOP_BELOW = 0.5;
    /** Chance per random tick that a leaf that may fall does fall: a GAME FIGURE; at the default random tick rate such a leaf lives about an hour. */
    public static final double LEAF_FALL_PER_TICK = 0.02;

    private ForestHealth() {}

    /** @return the critical level of annual SO2 of a kind of stand, ug/m3 */
    public static double critical(boolean conifer)
    {
        return conifer ? SO2_CRITICAL_CONIFER : SO2_CRITICAL_BROADLEAF;
    }

    /** @return the stress of one kind of stand under a constant annual mean */
    public static double stress(boolean conifer, double annualSo2, double ph, double copperMgPerKg, double zincMgPerKg)
    {
        return so2Stress(conifer, Math.max(0.0, annualSo2 - critical(conifer))) + soilStress(ph, copperMgPerKg, zincMgPerKg);
    }

    /** @return the stress from SO2 given the mean over a stretch of time of the excess of the annual mean over the critical level: s is linear in the excess, so the mean stress is the stress of the mean excess */
    public static double so2Stress(boolean conifer, double meanExcess)
    {
        return Math.max(0.0, meanExcess) / critical(conifer);
    }

    /** @return the stress from the soil: acidity under the aluminium range, copper and zinc over their thresholds */
    public static double soilStress(double ph, double copperMgPerKg, double zincMgPerKg)
    {
        double s = 0.0;
        if (ph < PH_ALUMINIUM)
        {
            s += (PH_ALUMINIUM - ph) / PH_STRESS_WIDTH;
        }
        s += Math.max(0.0, copperMgPerKg - COPPER_THRESHOLD) / COPPER_THRESHOLD;
        s += Math.max(0.0, zincMgPerKg - ZINC_THRESHOLD) / ZINC_THRESHOLD;
        return s;
    }

    /** @return the health after this many years under constant stress */
    public static double advance(double health, double years, double stress)
    {
        if (!(years > 0.0))
        {
            return health;
        }
        final double lambda = K * stress + R;
        final double equilibrium = R / lambda;
        return equilibrium + (health - equilibrium) * Math.exp(-lambda * years);
    }

    /** @return the equilibrium health under constant stress */
    public static double equilibrium(double stress)
    {
        return R / (K * stress + R);
    }

    /**
     * @return a number in [0, 1) that belongs to a block position and is spread evenly (splitmix64 of the packed position). A leaf is among those that a stand in health h loses only if this exceeds h, so that a share 1 - h of the canopy goes and the rest stays, and the same leaves go every time. When the health recovers the leaves that fell are to grow back: that is stage F (the hook is {@link #leafMayFall}, which says only which leaves are the doomed ones).
     */
    public static double positionHash(long packedPosition)
    {
        long z = packedPosition + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    /** @return whether the leaf at a position is one of those that a stand of this health loses: the health is under {@link #LEAVES_FALL_BELOW} and the hash of the position is over it */
    public static boolean leafMayFall(double health, double hash)
    {
        return health < LEAVES_FALL_BELOW && hash > health;
    }

    public static boolean saplingsGrow(double health)
    {
        return health >= SAPLINGS_STOP_BELOW;
    }
}
