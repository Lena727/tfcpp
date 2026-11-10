package tfcpp.health;

import java.util.random.RandomGenerator;
import org.jetbrains.annotations.Nullable;

/**
 * The diseases of one person and the course of each over the calendar. Free of Minecraft types.
 * <p>
 * An infection is a timeline fixed at the moment it is caught: {@code infectedAt}, then the incubation, then {@code symptomsAt}, then the illness until {@code illnessEnds}, then (typhoid) a time of recovery in which the person still sheds until {@code sheddingEnds}. All in ticks of the calendar of TFC; nothing ticks, the state at any time is a function of the time, and a person who was away is brought up to date at once.
 * <ul>
 * <li>Dysentery (shigellosis): incubation 1 to 3 days (the usual 1 to 2, up to 7: Kotloff et al., 2018, Lancet 391), illness 5 to 7 days when untreated (Kotloff et al.), sheds while ill, no immunity worth the name (immunity to Shigella is serotype specific and short).</li>
 * <li>Typhoid: incubation 7 to 14 days (6 to 30; Parry et al., 2002, N. Engl. J. Med. 347), illness 21 to 28 days (3 to 4 weeks untreated), sheds while ill and for two weeks after (about 10 % of untreated patients shed for three months; the game takes two weeks), 10 to 20 % fatal untreated before antibiotics (Parry et al.: 10 to 20 %), immune for two years of TFC after recovery (immunity after natural typhoid is partial and wanes in years, Levine et al.).</li>
 * </ul>
 */
public final class DiseaseState
{
    private static final long DAY = 24_000L;

    /** The phase of one infection. */
    public enum Phase
    {
        NONE, INCUBATING, ILL, RECOVERING
    }

    /** How an infection ended, for the step that saw it end. */
    public enum Outcome
    {
        NONE, RECOVERED, DIED
    }

    /** One infection: its timeline, and whether its end has been decided. */
    public static final class Course
    {
        public final Disease disease;
        public final long infectedAt, symptomsAt, illnessEnds, sheddingEnds;
        public boolean resolved;
        /** Whether the end of the illness has been drawn, and if so whether it is a death. The draw is made once and kept: a death that has not yet landed (the blow was turned aside for a moment) is tried again and not drawn again. */
        public boolean decided, doomed;

        public Course(Disease disease, long infectedAt, long symptomsAt, long illnessEnds, long sheddingEnds, boolean resolved, boolean decided, boolean doomed)
        {
            this(disease, infectedAt, symptomsAt, illnessEnds, sheddingEnds, resolved);
            this.decided = decided;
            this.doomed = doomed;
        }

        public Course(Disease disease, long infectedAt, long symptomsAt, long illnessEnds, long sheddingEnds, boolean resolved)
        {
            this.disease = disease;
            this.infectedAt = infectedAt;
            this.symptomsAt = symptomsAt;
            this.illnessEnds = illnessEnds;
            this.sheddingEnds = sheddingEnds;
            this.resolved = resolved;
        }

        public Phase phase(long now)
        {
            if (now < symptomsAt)
            {
                return Phase.INCUBATING;
            }
            if (now < illnessEnds)
            {
                return Phase.ILL;
            }
            return Phase.RECOVERING;
        }

        /** @return whether the person sheds the pathogen into sewage now: while ill, and typhoid for two weeks after */
        public boolean sheds(long now)
        {
            return now >= symptomsAt && now < sheddingEnds;
        }
    }

    private final Course[] courses = new Course[Disease.VALUES.length];
    private final long[] immuneUntil = new long[Disease.VALUES.length];
    /** The calendar tick at which the waste of this person was last counted, 0 for never. */
    public long lastExcretion;
    /** The calendar tick of the last time the symptoms were applied, for the thirst that diarrhoea takes by the time. */
    public long lastSymptoms;

    @Nullable
    public Course course(Disease disease)
    {
        return courses[disease.ordinal()];
    }

    public long immuneUntil(Disease disease)
    {
        return immuneUntil[disease.ordinal()];
    }

    public void setCourse(Disease disease, @Nullable Course course)
    {
        courses[disease.ordinal()] = course;
    }

    public void setImmuneUntil(Disease disease, long tick)
    {
        immuneUntil[disease.ordinal()] = tick;
    }

    public boolean isEmpty()
    {
        for (Course course : courses)
        {
            if (course != null)
            {
                return false;
            }
        }
        return true;
    }

    /** @return whether a person can catch the disease now: not ill with it and not immune */
    public boolean canCatch(Disease disease, long now)
    {
        return course(disease) == null && now >= immuneUntil(disease);
    }

    /** @return whether the person sheds this disease into their waste now */
    public boolean sheds(Disease disease, long now)
    {
        final @Nullable Course course = course(disease);
        return course != null && course.sheds(now);
    }

    /** @return the phase of the person's infection with this disease */
    public Phase phase(Disease disease, long now)
    {
        final @Nullable Course course = course(disease);
        return course == null ? Phase.NONE : course.phase(now);
    }

    // --- Starting an infection ---------------------------------------------------------------------------------------------------------------------------------------

    /** @return the incubation, the illness and the shedding after it, in days, drawn at random from the ranges of the disease */
    public static double[] durationsInDays(Disease disease, RandomGenerator random)
    {
        return disease == Disease.DYSENTERY
            ? new double[] {1.0 + 2.0 * random.nextDouble(), 5.0 + 2.0 * random.nextDouble(), 0.0}
            : new double[] {7.0 + 7.0 * random.nextDouble(), 21.0 + 7.0 * random.nextDouble(), 14.0};
    }

    /** Infects the person at this moment, if they can be infected. @return {@code true} if they were */
    public boolean infect(Disease disease, long now, RandomGenerator random)
    {
        if (!canCatch(disease, now))
        {
            return false;
        }
        final double[] days = durationsInDays(disease, random);
        final long symptoms = now + Math.round(days[0] * DAY), ends = symptoms + Math.round(days[1] * DAY), sheds = ends + Math.round(days[2] * DAY);
        setCourse(disease, new Course(disease, now, symptoms, ends, sheds, false));
        return true;
    }

    // --- The course over time ----------------------------------------------------------------------------------------------------------------------------------------------

    /** A person who was to die of the disease has lived through the blow (a totem): it is a recovery, with the immunity and the shedding that go with it. */
    public void recover(Disease disease, long yearTicks)
    {
        final @Nullable Course course = course(disease);
        if (course != null && !course.resolved)
        {
            course.resolved = true;
            course.doomed = false;
            if (disease == Disease.TYPHOID)
            {
                setImmuneUntil(disease, course.illnessEnds + 2L * yearTicks);
            }
        }
    }

    /** @return the chance that a person with typhoid dies at the end of the illness: 15 % of (1.5 minus the average nutrition, 0 to 1), clamped to 5 to 25 %. Untreated typhoid killed 10 to 20 % before antibiotics, the worse nourished more (Parry et al., 2002); the better fed survive */
    public static double typhoidFatality(double averageNutrition)
    {
        return Math.min(0.25, Math.max(0.05, 0.15 * (1.5 - averageNutrition)));
    }

    /** Ticks of the calendar in a day. */
    public static long dayTicks()
    {
        return DAY;
    }

    /**
     * Brings the person up to this moment: an illness that has ended is decided (typhoid kills with the chance of {@link #typhoidFatality}, dysentery only by dehydration, which TFC's thirst does), and an infection that has run its course, shedding included, is forgotten and, for typhoid, leaves immunity.
     *
     * @param nutrition the person's average nutrition, 0 to 1
     * @param yearTicks ticks in a year of TFC, for the length of immunity
     * @return how the last illness to end did
     */
    public Outcome step(long now, double nutrition, RandomGenerator random, long yearTicks)
    {
        Outcome result = Outcome.NONE;
        for (Disease disease : Disease.VALUES)
        {
            final @Nullable Course course = course(disease);
            if (course == null)
            {
                continue;
            }
            if (!course.resolved && now >= course.illnessEnds)
            {
                if (!course.decided)
                {
                    course.decided = true;
                    course.doomed = disease == Disease.TYPHOID && random.nextDouble() < typhoidFatality(nutrition);
                }
                if (course.doomed)
                {
                    result = Outcome.DIED; // Not resolved until the blow has landed: the caller ends it with the death, or with recover() if the person lived through it
                    continue;
                }
                course.resolved = true;
                result = result == Outcome.DIED ? result : Outcome.RECOVERED;
                if (disease == Disease.TYPHOID)
                {
                    setImmuneUntil(disease, course.illnessEnds + 2L * yearTicks);
                }
            }
            if (course.resolved && now >= course.sheddingEnds)
            {
                setCourse(disease, null);
            }
        }
        return result;
    }
}
