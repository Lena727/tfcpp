package tfcpp.chemistry;

/**
 * The lead chamber process, free of Minecraft types. Sulfur dioxide is oxidised and taken up by water to make sulfuric acid: SO2 + 1/2 O2 + H2O -> H2SO4. Nitrogen oxides carry the oxygen across: SO2 reduces NO2 to NO, and the air oxidises the NO back to NO2 in the chamber, so the oxides go round again and are not used up. Some are carried out with the exhaust, and that is the only way they are spent here.
 * <p>
 * The acid comes out at about 65%, not at 98%, because water must be there in excess. Each mole of acid takes 71 g of water: 18 g go into its molecules and 53 g dilute it, so that 98 g of H2SO4 ends up as 151 g of 65% acid.
 */
public final class ChamberProcess
{
    /**
     * Moles of acid per second per cubic metre of chamber for each unit of SO2 mole fraction. Not derived from kinetics: it is fitted to the output of real plants, so that 7% SO2 gives about 2.5 kg of acid per cubic metre in a day of 1440 s.
     */
    public static final float RATE_CONSTANT = 0.26f;

    /**
     * Oxides share at which the nitrogen oxides stop being the limit. They are a carrier, not a reagent, so a few tenths of a percent of the gas is enough to keep the reaction going.
     */
    public static final float NITROGEN_OXIDES_FULL = 0.002f;

    /**
     * Oxygen share at which the oxygen stops being the limit. Below it the chamber slows, because the oxygen must be carried to the SO2 by the oxides and there is less of it for each carrier to take.
     */
    public static final float OXYGEN_FULL = 0.06f;

    /** Share of oxygen in dry air, by volume and so by mole. */
    public static final float OXYGEN_IN_AIR = 0.2095f;

    /** Moles of oxygen that one mole of acid takes: 2 SO2 + O2 -> 2 SO3, so half a mole of oxygen for each mole of SO2. */
    public static final float OXYGEN_PER_MOLE = 0.5f;

    /**
     * Litres of water taken out of the chamber for each mole of acid made: 18 g of water goes into the H2SO4 and 53 g dilutes it to 65%, so 71 g, which is 0.071 L at 1 g/mL.
     */
    public static final float WATER_LITRES_PER_MOLE = 0.071f;

    /**
     * Litres of acid for each mole of H2SO4: 98 g of H2SO4 at 65% weighs 151 g, and at 1.55 kg/L that is 0.0975 L.
     */
    public static final float ACID_LITRES_PER_MOLE = 0.0975f;

    /**
     * Moles of nitrogen oxides lost to the exhaust for each mole of acid. A plant without a Gay-Lussac tower to catch them lost about 3 kg of saltpeter per 100 kg of sulfur, which is about 0.01 mol of oxides per mole of S.
     */
    public static final float OXIDES_LOST_PER_MOLE = 0.01f;

    /**
     * Heat released per mole of acid. SO2 to SO3 gives 99 kJ, and SO3 taken up by water to make H2SO4 gives about 132 kJ more, so 230 kJ.
     */
    public static final float HEAT_KILOJOULES_PER_MOLE = 230f;

    /** What one step of the chamber gave. Gas is in moles, water in litres of the network, and acid in both moles and litres. */
    public record Result(float acidMoles, float sulfurDioxide, float air, float nitrogenLeftBehind, float oxidesLost, float water, float acidLitres, float heatKilojoules)
    {
        public static final Result NONE = new Result(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
    }

    private ChamberProcess()
    {
    }

    /**
     * How fast the chamber makes acid for the shares of its gas at this moment.
     *
     * @param sulfurDioxideShare mole fraction of SO2
     * @param oxidesShare mole fraction of nitrogen oxides
     * @param oxygenShare mole fraction of oxygen
     * @return moles of acid per second per cubic metre, or 0 if any of the three is missing
     */
    public static float rate(float sulfurDioxideShare, float oxidesShare, float oxygenShare)
    {
        if (sulfurDioxideShare <= 0f || oxidesShare <= 0f || oxygenShare <= 0f)
        {
            return 0f;
        }
        final float oxides = Math.min(1f, oxidesShare / NITROGEN_OXIDES_FULL);
        final float oxygen = Math.min(1f, oxygenShare / OXYGEN_FULL);
        return RATE_CONSTANT * sulfurDioxideShare * oxides * oxygen;
    }

    /**
     * Runs the chamber for a stretch of time. The acid made is the rate times the time, but no more than the SO2, the oxygen, the water and the room in the tank allow.
     *
     * @param sulfurDioxide moles of SO2 in one cubic metre of chamber
     * @param nitrogenOxides moles of nitrogen oxides in it
     * @param air moles of air in it
     * @param gasMoles moles of all the gas in it
     * @param waterLitres litres of water in the network
     * @param tankRoomLitres litres of room left in the tank that takes the acid
     * @param seconds time of the step
     * @return what that gave
     */
    public static Result react(float sulfurDioxide, float nitrogenOxides, float air, float gasMoles, float waterLitres, float tankRoomLitres, float seconds)
    {
        if (gasMoles <= 0f || seconds <= 0f)
        {
            return Result.NONE;
        }

        final float oxygen = air * OXYGEN_IN_AIR;
        final float wanted = rate(sulfurDioxide / gasMoles, nitrogenOxides / gasMoles, oxygen / gasMoles) * seconds;
        final float acid = Math.min(
            Math.min(wanted, sulfurDioxide),
            Math.min(oxygen / OXYGEN_PER_MOLE, Math.min(waterLitres / WATER_LITRES_PER_MOLE, tankRoomLitres / ACID_LITRES_PER_MOLE)));
        if (acid <= 0f)
        {
            return Result.NONE;
        }

        final float airUsed = acid * OXYGEN_PER_MOLE / OXYGEN_IN_AIR;
        final float nitrogen = airUsed - acid * OXYGEN_PER_MOLE; // What is left of the air once its oxygen is taken: it does not react
        final float oxidesLost = Math.min(nitrogenOxides, acid * OXIDES_LOST_PER_MOLE);
        return new Result(acid, acid, airUsed, nitrogen, oxidesLost, acid * WATER_LITRES_PER_MOLE, acid * ACID_LITRES_PER_MOLE, acid * HEAT_KILOJOULES_PER_MOLE);
    }
}
