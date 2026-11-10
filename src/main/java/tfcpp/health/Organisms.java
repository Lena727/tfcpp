package tfcpp.health;

/**
 * How many pathogens are in a litre of water that holds some sewage, and how likely a swallowed dose is to infect. Free of Minecraft types.
 * <p>
 * Sewage is a mixture of the stools of everybody who contributed. Endemic sewage, from people who are not ill, carries the organisms at the carriage rate of a population; sewage from a person who is shedding carries them at the concentration of an ill person's stool. The ledgers keep the shedders' share of the sewage apart for each disease, so that the organisms in a litre are
 * {@code endemic * (sewage - shed) + shedding * shed} for each pathogen.
 * <p>
 * The dose-response is the approximate beta-Poisson, P = 1 - (1 + d/beta)^(-alpha), beta = N50 / (2^(1/alpha) - 1) (Haas, Rose and Gerba, Quantitative Microbial Risk Assessment, 1999; with a median dose of the order of 1e3 organisms for Shigella (volunteer studies find infection from tens to hundreds of organisms in some, so the figure is on the cautious side) and, for S. Typhi, the beta-Poisson fit to the volunteer data of Hornick et al., 1966, N. Engl. J. Med. 273, that the QMRA Wiki lists for Salmonella Typhi: alpha 0.1086, N50 3.6e6. The fit gives beta 6097 and 26.7 % infected by 1e5 organisms, the attack rate of the order of a quarter that Hornick's volunteers had at that dose (they swallowed the organisms in milk, after bicarbonate, and a larger dose was needed than for any other enteric pathogen), and 50 % at 3.6e6; the curve is flat in its tail (0.87 at 1e12), so typhoid needs a very foul water or a long exposure, as it did. The parameters are of the right order of magnitude and are not claimed to be more.
 */
public final class Organisms
{
    /** Organisms in a litre of endemic raw sewage. Shigella is carried by about 1 in 1000 of a population in settings with poor sanitation at the stool concentrations of the order of 1e5 per gram, and a litre of sewage is the stool of 1/150 of a person-day: 100 per litre is the order of the figure for sewage in endemic areas (Feachem et al., Sanitation and Disease, 1983); S. Typhi, carried by fewer, 10 per litre. A GAME FIGURE inside those ranges. */
    public static final double SHIGELLA_ENDEMIC_PER_LITRE = 100.0, TYPHI_ENDEMIC_PER_LITRE = 10.0;
    /** Organisms in a litre of the sewage of a person who is shedding. The stool of a person with shigellosis holds 1e5 to 1e9 organisms per gram (Feachem et al.); 1e6 per litre of sewage is the figure after the dilution by the urine and the water of the toilet. Typhoid patients shed 1e6 to 1e9 per gram in the second and third weeks; 1e5 per litre. GAME FIGURES. */
    public static final double SHIGELLA_SHEDDING_PER_LITRE = 1e6, TYPHI_SHEDDING_PER_LITRE = 1e5;
    /** Beta-Poisson parameters: alpha and the median infective dose N50, in organisms. */
    public static final double SHIGELLA_ALPHA = 0.21, SHIGELLA_N50 = 1120.0, TYPHI_ALPHA = 0.1086, TYPHI_N50 = 3.6e6;

    private Organisms() {}

    public static double alpha(Disease disease)
    {
        return disease == Disease.DYSENTERY ? SHIGELLA_ALPHA : TYPHI_ALPHA;
    }

    public static double n50(Disease disease)
    {
        return disease == Disease.DYSENTERY ? SHIGELLA_N50 : TYPHI_N50;
    }

    /** @return the beta of the approximate beta-Poisson model that has this median dose */
    public static double beta(Disease disease)
    {
        return n50(disease) / (Math.pow(2.0, 1.0 / alpha(disease)) - 1.0);
    }

    /** @return the chance that a person who swallows this many organisms at once is infected */
    public static double infectionProbability(Disease disease, double organisms)
    {
        if (!(organisms > 0.0))
        {
            return 0.0;
        }
        return 1.0 - Math.pow(1.0 + organisms / beta(disease), -alpha(disease));
    }

    /**
     * @param sewage litres of sewage in a litre of the water
     * @param shed the part of it that came from people shedding this disease
     * @return organisms of the pathogen of this disease in a litre of the water
     */
    public static double perLitre(Disease disease, double sewage, double shed)
    {
        final double shedding = Math.min(Math.max(shed, 0.0), Math.max(sewage, 0.0));
        final double endemic = Math.max(sewage, 0.0) - shedding;
        return disease == Disease.DYSENTERY
            ? SHIGELLA_ENDEMIC_PER_LITRE * endemic + SHIGELLA_SHEDDING_PER_LITRE * shedding
            : TYPHI_ENDEMIC_PER_LITRE * endemic + TYPHI_SHEDDING_PER_LITRE * shedding;
    }

    /** @return the chance of being infected by one drink of this many litres of water of this quality */
    public static double infectionProbability(Disease disease, WaterQuality quality, double litres)
    {
        final double shed = disease == Disease.DYSENTERY ? quality.shedDysentery : quality.shedTyphoid;
        return infectionProbability(disease, perLitre(disease, quality.faecal, shed) * litres);
    }
}
