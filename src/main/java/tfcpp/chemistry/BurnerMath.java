package tfcpp.chemistry;

import tfcpp.pneumatics.gas.Combustion;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasMath;

/**
 * What a gas burner makes of the gas in its pipe, free of Minecraft types. It burns hydrogen, methane and carbon monoxide, the three combustible parts of coke oven gas, with the oxygen of the room around it.
 */
public final class BurnerMath
{
    /** The most heat it can give, which is what the nozzle passes. */
    public static final float MAX_KILOWATTS = 400f;
    /** Flame temperature of coke oven gas burning in air with a little excess, in Celsius. */
    public static final float FULL_TEMPERATURE = 1100f;
    /** A mixture this rich in heat gives the full temperature. A leaner one is cooled by its own inert gas and by air, in proportion. */
    public static final float FULL_HEAT_KILOJOULES_PER_MOLE = 150f;
    /** A flame this strong holds what it heats at the full flame temperature. A weaker one cannot: see {@link #heatedTemperature}. */
    public static final float FULL_HEAT_KILOWATTS = 200f;
    /** A burner needs a push to get its gas through the nozzle. Town gas was supplied at about 500 Pa. */
    public static final float MINIMUM_GAUGE_ATM = 0.005f;
    /** Peak pressure of a confined explosion of any of these gases in air is close to 8 bar. */
    public static final float MAXIMUM_EXPLOSION_KILOPASCALS = 800f;

    private static final float AMBIENT = 20f;
    /** A well-adjusted burner leaves hardly any carbon monoxide: it rises only when the room runs short of oxygen. */
    private static final float CLEAN_MONOXIDE_SHARE = 0.01f;
    private static final float GAMMA = 1.4f;

    /** Lower heating values in kJ per mole, with the water leaving as steam. */
    private static final float HYDROGEN_HEAT = 242f, METHANE_HEAT = 802f, MONOXIDE_HEAT = 283f;
    private static final float SULFIDE_HEAT = 518f, AMMONIA_HEAT = 317f;

    private static final int HYDROGEN = Gas.HYDROGEN.ordinal(), METHANE = Gas.METHANE.ordinal(), MONOXIDE = Gas.CARBON_MONOXIDE.ordinal(), DIOXIDE = Gas.CARBON_DIOXIDE.ordinal();
    private static final int SULFIDE = Gas.HYDROGEN_SULFIDE.ordinal(), AMMONIA = Gas.AMMONIA.ordinal(), SULFUR_DIOXIDE = Gas.SULFUR_DIOXIDE.ordinal(), NITROGEN = Gas.FLUE_NITROGEN.ordinal();

    /**
     * What a burner leaves in the room.
     *
     * @param gases     moles of each gas to put in the room: whatever did not burn, and what the fuel became
     * @param oxygen    moles of oxygen that the room has to give
     * @param kilojoules heat released
     */
    public record Products(float[] gases, float oxygen, float kilojoules) {}

    /**
     * What a burst pipe took from a flame running back into it.
     *
     * @param kilojoules energy released
     * @param airUsed    moles of the pipe's air whose oxygen went
     */
    public record Explosion(float kilojoules, float airUsed) {}

    /** @return kJ that the fuel in this gas gives when burned, of the three that a burner burns */
    public static float fuelKilojoules(float[] gases)
    {
        return gases[HYDROGEN] * HYDROGEN_HEAT + gases[METHANE] * METHANE_HEAT + gases[MONOXIDE] * MONOXIDE_HEAT;
    }

    public static float totalMoles(float[] gases, float air)
    {
        return GasMath.total(gases) + air;
    }

    /** @return kJ per mole of the whole mixture, air and inert gases included */
    public static float heatPerMole(float[] gases, float air)
    {
        final float moles = totalMoles(gases, air);
        return moles > 0f ? fuelKilojoules(gases) / moles : 0f;
    }

    /** @return the temperature of the flame in Celsius, which falls in proportion once the mixture gives less than {@link #FULL_HEAT_KILOJOULES_PER_MOLE} */
    public static float temperature(float heatPerMole)
    {
        return AMBIENT + (FULL_TEMPERATURE - AMBIENT) * Math.min(heatPerMole / FULL_HEAT_KILOJOULES_PER_MOLE, 1f);
    }

    /**
     * A wall heated by a flame settles where it radiates away what it receives, and radiation grows with the fourth power of the temperature above its surroundings. So the rise in temperature goes with the fourth root of the power.
     *
     * @param flameTemperature the temperature of the flame itself, in Celsius, from {@link #temperature}
     * @param kilowatts        heat that the flame actually gives
     * @return the temperature in Celsius that what stands on the burner is heated toward
     */
    public static float heatedTemperature(float flameTemperature, float kilowatts)
    {
        if (kilowatts <= 0f)
        {
            return AMBIENT;
        }
        final float fraction = Math.min(1f, (float) Math.pow(kilowatts / FULL_HEAT_KILOWATTS, 0.25));
        return AMBIENT + (flameTemperature - AMBIENT) * fraction;
    }

    /**
     * @param evenMoles moles that have to leave the pipe to bring its pressure down to that of the room
     * @return the share of everything in the pipe that the burner takes in this time, from 0 to 1: as much as its power allows, and no more than the pressure drives
     */
    public static float share(float[] gases, float air, float seconds, float evenMoles)
    {
        final float fuel = fuelKilojoules(gases);
        final float moles = totalMoles(gases, air);
        if (fuel <= 0f || moles <= 0f || seconds <= 0f || evenMoles <= 0f)
        {
            return 0f;
        }
        return Math.clamp(Math.min(MAX_KILOWATTS * seconds / fuel, evenMoles / moles), 0f, 1f);
    }

    /**
     * Burns the fuel in gas that has been taken from the pipe. Hydrogen and methane make water, which is not tracked, and carbon makes carbon dioxide, with some carbon monoxide once the room is short of oxygen. Hydrogen sulfide in the gas burns to sulfur dioxide. Ammonia and everything else passes through.
     *
     * @param drawn          moles of each gas taken from the pipe
     * @param oxygenFraction oxygen in the air at the burner's intake
     */
    public static Products burn(float[] drawn, float oxygenFraction)
    {
        final float hydrogen = drawn[HYDROGEN], methane = drawn[METHANE], monoxide = drawn[MONOXIDE], sulfide = drawn[SULFIDE];
        final float carbon = methane + monoxide;
        final float monoxideShare = Combustion.carbonMonoxideShare(CLEAN_MONOXIDE_SHARE, oxygenFraction);

        final float[] out = drawn.clone();
        out[HYDROGEN] = 0f;
        out[METHANE] = 0f;
        out[SULFIDE] = 0f;
        out[MONOXIDE] = carbon * monoxideShare;
        out[DIOXIDE] += carbon * (1f - monoxideShare);
        out[SULFUR_DIOXIDE] += sulfide;

        // Oxygen atoms in the products (water, carbon dioxide and monoxide, sulfur dioxide and the water with it), less those the carbon monoxide fuel already holds
        final float atoms = hydrogen + 2f * methane + (2f - monoxideShare) * carbon - monoxide + 3f * sulfide;
        final float oxygen = atoms / 2f;
        // The room's air is displaced by what is added to it, and has to lose exactly the oxygen that burned. See Combustion.
        out[NITROGEN] += Math.max(0f, oxygen / GasMath.OXYGEN_IN_AIR - carbon - sulfide);

        final float kilojoules = hydrogen * HYDROGEN_HEAT + methane * METHANE_HEAT + monoxide * MONOXIDE_HEAT - carbon * monoxideShare * MONOXIDE_HEAT;
        return new Products(out, oxygen, kilojoules);
    }

    /** @return the gas and air of a pipe as a cell of the gas grid, with the same shares of each, so that {@link tfcpp.pneumatics.gas.Flammability} can say whether it burns */
    public static float[] asCell(float[] gases, float air)
    {
        final float moles = totalMoles(gases, air);
        final float[] cell = new float[Gas.COUNT];
        if (moles > 0f)
        {
            for (int i = 0; i < Gas.COUNT; i++)
            {
                cell[i] = gases[i] / moles * GasMath.CELL_MOLES;
            }
        }
        return cell;
    }

    /**
     * Burns everything in a pipe that can burn, with the oxygen of the air in it, as a flame running back into a mixture does. The gases are changed in place.
     */
    public static Explosion deflagrate(float[] gases, float air)
    {
        final int[] fuels = {HYDROGEN, METHANE, MONOXIDE, SULFIDE, AMMONIA};
        final float[] heats = {HYDROGEN_HEAT, METHANE_HEAT, MONOXIDE_HEAT, SULFIDE_HEAT, AMMONIA_HEAT};
        final float[] oxygenNeeded = {0.5f, 2f, 0.5f, 1.5f, 0.75f};

        float needed = 0f;
        for (int i = 0; i < fuels.length; i++)
        {
            needed += gases[fuels[i]] * oxygenNeeded[i];
        }
        if (needed <= 0f)
        {
            return new Explosion(0f, 0f);
        }
        // Rich mixtures leave fuel behind: the flame stops where the oxygen does
        final float done = Math.min(1f, air * GasMath.OXYGEN_IN_AIR / needed);

        float kilojoules = 0f;
        for (int i = 0; i < fuels.length; i++)
        {
            final float burned = gases[fuels[i]] * done;
            gases[fuels[i]] -= burned;
            kilojoules += burned * heats[i];
            if (fuels[i] == METHANE || fuels[i] == MONOXIDE)
            {
                gases[DIOXIDE] += burned;
            }
            else if (fuels[i] == SULFIDE)
            {
                gases[SULFUR_DIOXIDE] += burned;
            }
            else if (fuels[i] == AMMONIA)
            {
                gases[NITROGEN] += burned * 0.5f;
            }
        }
        final float oxygen = needed * done;
        gases[NITROGEN] += oxygen * Combustion.NITROGEN_PER_OXYGEN; // The nitrogen that came with the oxygen is left behind
        return new Explosion(kilojoules, oxygen / GasMath.OXYGEN_IN_AIR);
    }

    /**
     * @param kilojoules energy released in the pipe
     * @param volume     cubic metres it burned in
     * @return the peak pressure in kPa above the pipe's own, from heating a closed volume of gas at constant volume: dp = (gamma - 1) E / V. Real peaks stop at about 8 bar.
     */
    public static float peakKilopascals(float kilojoules, float volume)
    {
        return volume <= 0f ? 0f : Math.min((GAMMA - 1f) * kilojoules / volume, MAXIMUM_EXPLOSION_KILOPASCALS);
    }

    private BurnerMath() {}
}
