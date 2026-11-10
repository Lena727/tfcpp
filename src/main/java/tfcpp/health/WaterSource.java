package tfcpp.health;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import tfcpp.ecology.FoodMetals;
import tfcpp.ecology.LevelWaters;
import tfcpp.ecology.Pollutant;
import tfcpp.ecology.Pollution;
import tfcpp.ecology.SoilCell;
import tfcpp.ecology.SoilConstants;
import tfcpp.ecology.WaterBody;
import tfcpp.ecology.Waters;

/**
 * What the water at a place carries when it is taken from the world: the body it belongs to (river, lake, spring: the contents of its reach in the ledger of the waters), the sewage that the cesspits near it put into the ground water, and, for still water that is not a body of water (a pond, a puddle, a river far from the graph), the sewage of the soil around it. Water that is really taken ({@link #take}) is taken out of what it came from: the water column of the reach in the ledger, the soil, the pits.
 */
public final class WaterSource
{
    /**
     * Share of the soil's sewage that a pond or a puddle takes up, as a fraction of the soil's sewage per litre of the 1 m deep column of water over the 256 m2 of the chunk: 0.1. A GAME FIGURE for how much of what lies on and in the ground a standing water drains: a pond in a fouled yard is fouled, and not as much as the yard. A chunk that holds 1000 litres of sewage gives such a pond 1000 / 256 000 * 0.1 = 0.4 mL of sewage per litre.
     */
    public static final double SOIL_TO_STANDING_WATER = 0.1;
    private static final double COLUMN_LITRES = SoilConstants.AREA * 1000.0;

    private WaterSource() {}

    /** @return the quality of the fresh water in the block at this position, as it would be taken (rounded to the steps of {@link WaterQuality#quantised}); clean if it is not fresh water, or nothing is known of it. Reads only: nothing is taken from the soil or the pits. */
    public static WaterQuality at(ServerLevel level, BlockPos pos)
    {
        return sample(level, pos, 0.0);
    }

    /**
     * The quality of {@code litres} of fresh water taken from the block at this position: what the soil of a pond or a puddle and the pits near a well put into it is taken out of the soil and the pits, no more than they hold, so that taking water again and again cannot carry away more sewage than there was. From a river, a lake or a spring (a body of the ledger of the waters) it is taken out of the water column of the reach, through {@link LevelWaters#draw}, never from the bed. Every source is rounded to a step first and what is removed is what the water carries; a store that holds less gives what it has.
     */
    public static WaterQuality take(ServerLevel level, BlockPos pos, double litres)
    {
        return sample(level, pos, litres);
    }

    private static WaterQuality sample(ServerLevel level, BlockPos pos, double litres)
    {
        if (!level.isLoaded(pos))
        {
            return WaterQuality.CLEAN;
        }
        final @Nullable Waters.Kind kind = Waters.kindOf(level.getFluidState(pos));
        if (kind == null || kind == Waters.Kind.SEA)
        {
            return WaterQuality.CLEAN;
        }
        // Every source is rounded to a step first, and what is taken out of it is what the water carries (or all it holds, if that is less)
        final double[] none = new double[FoodMetals.METALS.length];
        WaterQuality bodyPart = WaterQuality.CLEAN;
        double soilSewage = 0.0, soilDysentery = 0.0, soilTyphoid = 0.0;

        final @Nullable WaterBody body = Waters.at(level, pos);
        if (body != null && body.resolved() && body.type() != WaterBody.Type.SEA)
        {
            final double[] metals = new double[none.length];
            for (int i = 0; i < metals.length; i++)
            {
                metals[i] = body.microgramsPerLitre(FoodMetals.METALS[i]);
            }
            bodyPart = new WaterQuality(body.perLitre(Pollutant.SEWAGE), body.perLitre(Pollutant.SHED_DYSENTERY), body.perLitre(Pollutant.SHED_TYPHOID), metals, body.perLitre(Pollutant.ACIDITY) * 1000.0).quantised(); // eq/L to meq/L
            if (litres > 0.0)
            {
                bodyPart = drawFromBody(level, body, bodyPart, litres);
            }
        }
        else if (body == null && Pollution.isTouched(level, pos))
        {
            final SoilCell soil = Pollution.view(level, pos);
            final double factor = SOIL_TO_STANDING_WATER / COLUMN_LITRES;
            final WaterQuality soilPart = new WaterQuality(soil.total(Pollutant.SEWAGE) * factor, soil.total(Pollutant.SHED_DYSENTERY) * factor, soil.total(Pollutant.SHED_TYPHOID) * factor, none, 0.0).quantised();
            soilSewage = soilPart.faecal;
            soilDysentery = soilPart.shedDysentery;
            soilTyphoid = soilPart.shedTyphoid;
            if (litres > 0.0) // What the water takes is out of the soil
            {
                soilSewage = Pollution.take(level, pos, Pollutant.SEWAGE, soilSewage * litres) / litres;
                soilDysentery = Pollution.take(level, pos, Pollutant.SHED_DYSENTERY, soilDysentery * litres) / litres;
                soilTyphoid = Pollution.take(level, pos, Pollutant.SHED_TYPHOID, soilTyphoid * litres) / litres;
            }
        }

        // The sewage of the pits reaches wells, ponds and puddles by the ground water. A river or a lake (a body of the ledger) has what the pit leaks into the soil, which runs off to it: counting it here as well would count it twice
        double[] pits = new double[3];
        if (body == null)
        {
            final double[] read = Cesspits.groundwaterAt(level, pos);
            final WaterQuality rounded = new WaterQuality(read[0], read[1], read[2], none, 0.0).quantised();
            pits = new double[] {rounded.faecal, rounded.shedDysentery, rounded.shedTyphoid};
            if (litres > 0.0)
            {
                final double[] scale = new double[3];
                for (int i = 0; i < 3; i++)
                {
                    scale[i] = read[i] > 0.0 ? pits[i] / read[i] : 0.0;
                }
                pits = Cesspits.drawGroundwater(level, pos, litres, scale);
            }
        }
        return new WaterQuality(soilSewage + pits[0] + bodyPart.faecal, soilDysentery + pits[1] + bodyPart.shedDysentery, soilTyphoid + pits[2] + bodyPart.shedTyphoid, bodyPart.metals(), bodyPart.acidity);
    }

    /** Takes what {@code litres} of the (rounded) water of a body carry out of the water column of its reach, through the ledger; the water carries what the reach had to give. */
    private static WaterQuality drawFromBody(ServerLevel level, WaterBody body, WaterQuality wanted, double litres)
    {
        final LevelWaters waters = LevelWaters.get(level);
        final double[] metals = new double[FoodMetals.METALS.length];
        for (int i = 0; i < metals.length; i++)
        {
            metals[i] = waters.draw(body.id(), FoodMetals.METALS[i], wanted.metal(i) * litres / 1e6) * 1e6 / litres;
        }
        return new WaterQuality(waters.draw(body.id(), Pollutant.SEWAGE, wanted.faecal * litres) / litres,
            waters.draw(body.id(), Pollutant.SHED_DYSENTERY, wanted.shedDysentery * litres) / litres,
            waters.draw(body.id(), Pollutant.SHED_TYPHOID, wanted.shedTyphoid * litres) / litres,
            metals, waters.draw(body.id(), Pollutant.ACIDITY, wanted.acidity * litres / 1000.0) * 1000.0 / litres);
    }
}
