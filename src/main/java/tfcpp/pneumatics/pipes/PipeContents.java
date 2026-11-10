package tfcpp.pneumatics.pipes;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import tfcpp.health.Filtration;
import tfcpp.health.WaterQuality;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasMath;

/**
 * What is inside some length of pipe. Unlike a gas cell, this has real air in it, as a pipe can hold more or less of it than the room outside.
 */
public final class PipeContents
{
    public final float[] gases = new float[Gas.COUNT];
    public float air;
    /** The id of the one liquid held, or empty. Two liquids in one pipe are not modelled: networks of different liquids do not join, and {@link #add} returns what a clash would leave over. */
    public String liquid = "";
    public float litres;
    /** What the water in here carries (sewage, metals, acid), or {@code null} for water that carries nothing and for every other liquid. It mixes by volume with the water that comes in, and is boiled clean at the boiling point (stage E of v1.5). */
    @Nullable
    public WaterQuality quality;
    /** Seconds the water has been held at the boiling point since it last took in water that could carry pathogens. */
    public float boilSeconds;
    /** Less than this much liquid cannot be drained through a tap, and counts as the wet of the walls, not as a liquid that keeps another out. */
    public static final float FILM_LITRES = PipePhysics.WHOLE_MILLILITRE;
    /** Liquid that has run over at an opening and waits there for a whole block to be placed. It takes no room in the pipe and no part in its pressure, and it is kept through every rebuild and save, so that nothing is lost on the way. */
    public float runoff;
    public String runoffLiquid = "";
    public float temperature = PipePhysics.ROOM_TEMPERATURE;

    /** @return the contents of a new, empty length of pipe, which is to say full of air */
    public static PipeContents atmospheric(float volume)
    {
        final PipeContents contents = new PipeContents();
        contents.air = PipePhysics.moles(1f, contents.temperature, volume);
        return contents;
    }

    public float gasMoles()
    {
        return air + GasMath.total(gases);
    }

    /** @return mean molar mass of the gas in g/mol */
    public float molarMass()
    {
        final float moles = gasMoles();
        if (moles <= 0f)
        {
            return PipePhysics.AIR_MOLAR_MASS;
        }
        float mass = air * PipePhysics.AIR_MOLAR_MASS;
        for (int i = 0; i < Gas.COUNT; i++)
        {
            mass += gases[i] * Gas.VALUES[i].molarMass();
        }
        return mass / moles;
    }

    public float liquidVolume()
    {
        return PipePhysics.liquidVolume(litres, temperature);
    }

    public float freeVolume(float volume)
    {
        return Math.max(volume - liquidVolume(), volume * 0.001f);
    }

    /** @return pressure in atm, absolute */
    public float pressure(float volume)
    {
        final float vapour = isWater() ? PipePhysics.waterVapourPressure(temperature) : 0f;
        return PipePhysics.pressure(gasMoles(), temperature, volume, liquidVolume()) + vapour;
    }

    /** @return density of the gas in kg/m3 */
    public float gasDensity(float volume)
    {
        return gasMoles() * molarMass() / 1000f / freeVolume(volume);
    }

    /** @return {@code true} for liquids that boil as water does, which for their vapour pressure includes brine: salt lowers it, and that is left out */
    public boolean isWater()
    {
        return liquid.equals("minecraft:water") || liquid.equals("tfc:salt_water") || liquid.equals("tfc:spring_water") || liquid.equals("tfc:brine");
    }

    /** @return the temperature in kelvin below which this liquid freezes solid: seawater just under 0 C, saturated brine at its eutectic of -21 C */
    public float freezingPoint()
    {
        return liquid.equals("tfc:brine") ? 252f : 268f;
    }

    /** @return {@code true} if this holds no liquid that a different one could not be put on: nothing, or a film */
    public boolean takesLiquid(String id)
    {
        return litres < FILM_LITRES || liquid.isEmpty() || liquid.equals(id);
    }

    /** Sets the liquid down to nothing if there is none left, leaving the runoff its own name. */
    public void tidy()
    {
        if (litres <= 0f)
        {
            litres = 0f;
            liquid = "";
            quality = null;
            boilSeconds = 0f;
        }
    }

    /** Mixes in water of this quality, {@code amount} litres of it, to the {@code litres} that are already here (call before they are added). */
    private void mixQuality(float held, @Nullable WaterQuality incoming, float amount)
    {
        if (quality != null || incoming != null)
        {
            quality = WaterQuality.mix(quality, held, incoming, amount);
            if (quality.isClean())
            {
                quality = null;
            }
            else if (incoming != null && incoming.hasPathogens())
            {
                boilSeconds = 0f; // Water that can carry pathogens has to be boiled for a minute from the moment it comes in
            }
        }
    }

    /** Called every second of the pipe's step: water at the boiling point for a minute is boiled (WHO: a rolling boil, a minute is enough). Above 100 C counts: water in a boiler under pressure is hotter than that. */
    public void watchBoiling(float seconds)
    {
        if (quality == null || litres <= 0f || !isWater() || temperature < 273.15f + (float) Filtration.BOILING_CELSIUS)
        {
            boilSeconds = 0f;
            return;
        }
        boilSeconds += seconds;
        if (boilSeconds >= Filtration.BOIL_SECONDS && quality.hasPathogens())
        {
            quality = quality.boiled();
        }
    }

    /**
     * Takes in another amount, which comes out empty. Heat mixes by the amounts.
     * <p>
     * Two different liquids are not mixed: the lesser is not taken in, and is returned. Networks of different liquids are kept apart, so this is only a fallback for what should not happen, and the caller spills what comes back.
     *
     * @return the liquid that was left over by a clash of liquids, or {@code null}
     */
    @Nullable
    public PipeContents add(PipeContents other)
    {
        PipeContents rejected = null;
        if (litres > 0f && other.litres > 0f && !liquid.equals(other.liquid))
        {
            rejected = new PipeContents();
            if (other.litres > litres)
            {
                rejected.liquid = liquid;
                rejected.litres = litres;
                rejected.temperature = temperature;
                rejected.quality = quality;
                liquid = "";
                litres = 0f;
                quality = null;
            }
            else
            {
                rejected.liquid = other.liquid;
                rejected.litres = other.litres;
                rejected.temperature = other.temperature;
                rejected.quality = other.quality;
                other.liquid = "";
                other.litres = 0f;
                other.quality = null;
            }
        }

        final float mine = heatCapacity(), theirs = other.heatCapacity();
        if (mine + theirs > 0f)
        {
            temperature = (temperature * mine + other.temperature * theirs) / (mine + theirs);
        }
        for (int i = 0; i < Gas.COUNT; i++)
        {
            gases[i] += other.gases[i];
            other.gases[i] = 0f;
        }
        air += other.air;
        other.air = 0f;

        if (other.litres > 0f)
        {
            mixQuality(litres, other.quality, other.litres);
            liquid = other.liquid;
            litres += other.litres;
        }
        other.litres = 0f;
        other.liquid = "";
        other.quality = null;

        if (other.runoff > 0f)
        {
            if (runoff > 0f && !runoffLiquid.equals(other.runoffLiquid))
            {
                if (rejected == null)
                {
                    rejected = new PipeContents();
                    rejected.temperature = other.temperature;
                }
                rejected.runoff = other.runoff; // The lesser stands aside as well
                rejected.runoffLiquid = other.runoffLiquid;
            }
            else
            {
                runoffLiquid = other.runoffLiquid;
                runoff += other.runoff;
            }
            other.runoff = 0f;
            other.runoffLiquid = "";
        }
        return rejected;
    }

    /** @return this share of everything in here, which is removed from it */
    public PipeContents take(float share)
    {
        final PipeContents taken = new PipeContents();
        taken.temperature = temperature;
        for (int i = 0; i < Gas.COUNT; i++)
        {
            taken.gases[i] = gases[i] * share;
            gases[i] -= taken.gases[i];
        }
        taken.air = air * share;
        air -= taken.air;
        taken.litres = litres * share;
        litres -= taken.litres;
        taken.liquid = taken.litres > 0f ? liquid : "";
        taken.quality = taken.litres > 0f ? quality : null;
        tidy();
        taken.runoff = runoff * share;
        runoff -= taken.runoff;
        taken.runoffLiquid = taken.runoff > 0f ? runoffLiquid : "";
        if (runoff <= 0f)
        {
            runoff = 0f;
            runoffLiquid = "";
        }
        return taken;
    }

    /** @return this share of the gas alone, which is removed from it. Liquid stays behind, as it does at a vent in the top of a vessel. */
    public PipeContents takeGas(float share)
    {
        final PipeContents taken = new PipeContents();
        taken.temperature = temperature;
        for (int i = 0; i < Gas.COUNT; i++)
        {
            taken.gases[i] = gases[i] * share;
            gases[i] -= taken.gases[i];
        }
        taken.air = air * share;
        air -= taken.air;
        return taken;
    }

    /**
     * Takes in liquid of this kind, with the heat it brings. A film of another liquid is washed out ahead of it (less than one mB, which no tap could have given back); more than a film keeps it out.
     *
     * @return {@code false} if another liquid is in the way, and nothing was taken in
     */
    public boolean addLiquid(String id, float amount, float sourceTemperature)
    {
        return addLiquid(id, amount, sourceTemperature, null);
    }

    /** As {@link #addLiquid(String, float, float)}, for water that carries this quality. */
    public boolean addLiquid(String id, float amount, float sourceTemperature, @Nullable WaterQuality incoming)
    {
        if (!takesLiquid(id))
        {
            return false;
        }
        if (!liquid.equals(id))
        {
            litres = 0f;
            quality = null;
        }
        mixHeat(amount * 4.186f, sourceTemperature);
        mixQuality(litres, incoming, amount);
        liquid = id;
        litres += amount;
        return true;
    }

    /** Takes in gas that has already been counted, at this temperature. */
    public void mixHeat(float capacity, float sourceTemperature)
    {
        final float mine = heatCapacity();
        if (mine + capacity > 0f)
        {
            temperature = (temperature * mine + sourceTemperature * capacity) / (mine + capacity);
        }
    }

    /** @return a copy of this share, which leaves this unchanged */
    public PipeContents share(float share)
    {
        final PipeContents copy = new PipeContents();
        copy.temperature = temperature;
        for (int i = 0; i < Gas.COUNT; i++)
        {
            copy.gases[i] = gases[i] * share;
        }
        copy.air = air * share;
        copy.litres = litres * share;
        copy.liquid = copy.litres > 0f ? liquid : "";
        copy.quality = copy.litres > 0f ? quality : null;
        copy.runoff = runoff * share;
        copy.runoffLiquid = copy.runoff > 0f ? runoffLiquid : "";
        return copy;
    }

    /** @return kJ per kelvin of the contents: about 29 J/mol K for gas and 4.2 kJ/L K for liquid */
    public float heatCapacity()
    {
        return gasMoles() * 0.029f + litres * 4.186f;
    }

    public CompoundTag save()
    {
        final CompoundTag tag = new CompoundTag();
        final ListTag species = new ListTag();
        final int[] bits = new int[Gas.COUNT];
        int stored = 0;
        for (int i = 0; i < Gas.COUNT; i++)
        {
            if (gases[i] > 0f) // Most pipes hold only a few gases, and there can be thousands of pipes to save
            {
                species.add(StringTag.valueOf(Gas.VALUES[i].id()));
                bits[stored++] = Float.floatToRawIntBits(gases[i]);
            }
        }
        tag.put("species", species);
        tag.putIntArray("gases", Arrays.copyOf(bits, stored));
        tag.putFloat("air", air);
        tag.putString("liquid", liquid);
        tag.putFloat("litres", litres);
        if (runoff > 0f)
        {
            tag.putFloat("runoff", runoff);
            tag.putString("runoffLiquid", runoffLiquid);
        }
        tag.putFloat("temperature", temperature);
        final ListTag savedQuality = tfcpp.health.FluidQuality.toTag(quality);
        if (savedQuality != null)
        {
            tag.put("quality", savedQuality);
        }
        return tag;
    }

    public static PipeContents load(CompoundTag tag)
    {
        final PipeContents contents = new PipeContents();
        final ListTag species = tag.getList("species", Tag.TAG_STRING);
        final int[] bits = tag.getIntArray("gases");
        for (int i = 0; i < species.size() && i < bits.length; i++)
        {
            final Gas gas = Gas.byId(species.getString(i));
            if (gas != null)
            {
                contents.gases[gas.ordinal()] = Float.intBitsToFloat(bits[i]);
            }
        }
        contents.air = tag.getFloat("air");
        contents.liquid = tag.getString("liquid");
        contents.litres = tag.getFloat("litres");
        contents.runoff = tag.getFloat("runoff");
        contents.runoffLiquid = contents.runoff > 0f ? tag.getString("runoffLiquid") : "";
        contents.temperature = tag.contains("temperature") ? tag.getFloat("temperature") : PipePhysics.ROOM_TEMPERATURE;
        if (tag.contains("quality", Tag.TAG_LIST) && contents.litres > 0f)
        {
            contents.quality = tfcpp.health.FluidQuality.fromTag(tag.getList("quality", tfcpp.health.FluidQuality.TAG_TYPE));
        }
        return contents;
    }
}
