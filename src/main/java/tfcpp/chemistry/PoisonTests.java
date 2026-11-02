package tfcpp.chemistry;

import tfcpp.core.LocalTime;

/**
 * The poisons that a chemist finds in the air by eye, and the dose of mercury that a leaf of gold takes up over time. Pure arithmetic with no Minecraft types, so that it runs without a game.
 * <p>
 * Marsh's test of 1836 reduces arsenic in a sample to arsine, which the heat of a glass tube breaks up into a mirror of metal. A sample of 20 litres that holds 0.02 mg of arsenic shows a faint mirror, which is 1 mg/m3. Antimony gives the same mirror, as it does in real life: the mirror says that arsenic or antimony is there, and not which one.
 * <p>
 * Mercury vapour takes up gold into an amalgam that whitens it, and it does not wash off, so a leaf keeps the dose of its whole life. The dose is in (mg/m3)&middot;h, as the poisoning of a body is in dose over time. A local hour is {@link LocalTime#SECONDS_PER_DAY} / 24 real seconds, as the rule of the mod is for slow processes.
 * <p>
 * A cell of gas is 1 m3, so moles per cell and moles per cubic metre are the same number.
 */
public final class PoisonTests
{
    /** Molar masses in g/mol, as the {@link tfcpp.pneumatics.gas.Gas} enum has them. */
    public static final float AS2O3_MOLAR_MASS = 197.84f;
    public static final float SB2O3_MOLAR_MASS = 291.52f;
    public static final float HG_MOLAR_MASS = 200.59f;

    /** The mirror shows from this concentration of arsenic or antimony oxide: 0.02 mg in 20 L of air. */
    public static final float FAINT_MG_PER_M3 = 1f;
    /** A dense mirror from ten times that: a sample from a furnace hall that a man should leave. */
    public static final float STRONG_MG_PER_M3 = 20f;

    /** A local hour in real seconds: 60 s, from {@link LocalTime#SECONDS_PER_DAY} split into 24. */
    public static final float HOUR_SECONDS = LocalTime.SECONDS_PER_DAY / 24f;

    /** The dose at which a leaf of gold starts to whiten, and the next two stages. In (mg/m3)&middot;h. */
    public static final float STAGE_ONE_DOSE = 1f, STAGE_TWO_DOSE = 10f, STAGE_THREE_DOSE = 100f;

    /** How dense the mirror of one test is: none, faint, or strong. */
    public enum Mirror
    {
        NONE,
        FAINT,
        STRONG
    }

    /**
     * @param molesPerCubicMetreArsenic  moles of arsenic trioxide per m3
     * @param molesPerCubicMetreAntimony moles of antimony trioxide per m3
     * @return the mirror of the test. Arsenic and antimony are summed, and both give the same mirror, so that a sample holding either one or both reads the same.
     */
    public static Mirror marsh(float molesPerCubicMetreArsenic, float molesPerCubicMetreAntimony)
    {
        final float milligrams = molesPerCubicMetreArsenic * AS2O3_MOLAR_MASS * 1000f + molesPerCubicMetreAntimony * SB2O3_MOLAR_MASS * 1000f;
        if (milligrams >= STRONG_MG_PER_M3)
        {
            return Mirror.STRONG;
        }
        return milligrams >= FAINT_MG_PER_M3 ? Mirror.FAINT : Mirror.NONE;
    }

    /** @return mg/m3 of mercury vapour that this many moles per m3 make */
    public static float mercuryMilligrams(float moles)
    {
        return moles * HG_MOLAR_MASS * 1000f;
    }

    /**
     * @param dose                  the dose so far, in (mg/m3)&middot;h
     * @param milligramsPerCubicMetre the concentration that the leaf breathes now
     * @param seconds               how long it breathes it
     * @return the dose after that time
     */
    public static float addDose(float dose, float milligramsPerCubicMetre, float seconds)
    {
        return dose + milligramsPerCubicMetre * seconds / HOUR_SECONDS;
    }

    /** @return the whitening stage of a leaf of gold: 0 clean, 1 traces, 2 exposed, 3 heavily poisoned */
    public static int leafStage(float dose)
    {
        if (dose >= STAGE_THREE_DOSE)
        {
            return 3;
        }
        if (dose >= STAGE_TWO_DOSE)
        {
            return 2;
        }
        return dose >= STAGE_ONE_DOSE ? 1 : 0;
    }

    private PoisonTests() {}
}
