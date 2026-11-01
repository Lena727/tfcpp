package tfcpp.geology;

import java.util.List;
import java.util.Locale;
import net.dries007.tfc.common.blocks.rock.Rock;

/**
 * The minerals industry needs and TFC lacks, each in the rock where it really forms. A block exists only for those rocks, as the rest would never be generated.
 */
public enum NewOre
{
    /** PbS. In carbonates as Mississippi Valley type deposits, and in hydrothermal veins through metamorphic rock. */
    GALENA(true, "Galena", "галенит", Rock.LIMESTONE, Rock.DOLOMITE, Rock.MARBLE, Rock.QUARTZITE, Rock.SLATE, Rock.PHYLLITE, Rock.SCHIST, Rock.GNEISS),
    /** Aluminium hydroxides, left where tropical rain has washed everything else out of limestone, clay or basalt. It lies near the surface, in blankets. */
    BAUXITE(true, "Bauxite", "боксит", Rock.LIMESTONE, Rock.DOLOMITE, Rock.CLAYSTONE, Rock.SHALE, Rock.BASALT),
    /** FeCr2O4. Settles out of mafic magma in layers, as in the Bushveld. TFC has no peridotite, so gabbro is the only host. */
    CHROMITE(true, "Chromite", "хромит", Rock.GABBRO),
    /** FeTiO3. In gabbro and related rock, where it also carries vanadium. */
    ILMENITE(true, "Ilmenite", "ильменит", Rock.GABBRO, Rock.DIORITE),
    /** MnO2. Bedded in sediments laid down in shallow seas, as at Nikopol. */
    PYROLUSITE(true, "Pyrolusite", "пиролюзит", Rock.SHALE, Rock.CLAYSTONE, Rock.LIMESTONE, Rock.DOLOMITE, Rock.CHERT),
    /** Ca5(PO4)3(F,Cl,OH). As phosphorite, in beds of marine sediment. */
    APATITE(false, "Apatite", "апатит", Rock.SHALE, Rock.CLAYSTONE, Rock.LIMESTONE, Rock.DOLOMITE, Rock.CHERT),
    /** BaSO4. Filling veins through sediments, and very dense, which is why it weights drilling mud. */
    BARITE(false, "Barite", "барит", Rock.LIMESTONE, Rock.DOLOMITE, Rock.MARBLE, Rock.SHALE, Rock.CLAYSTONE, Rock.CHERT);

    /** The grades of a graded ore, poorest first. */
    public static final List<String> GRADES = List.of("poor", "normal", "rich");

    private final boolean graded;
    private final String english, russian;
    private final List<Rock> hosts;

    NewOre(boolean graded, String english, String russian, Rock... hosts)
    {
        this.graded = graded;
        this.english = english;
        this.russian = russian;
        this.hosts = List.of(hosts);
    }

    public String id()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean graded()
    {
        return graded;
    }

    public String english()
    {
        return english;
    }

    public String russian()
    {
        return russian;
    }

    public List<Rock> hosts()
    {
        return hosts;
    }

    /** @return the names of its kinds of ore: its grades, or just itself */
    public List<String> kinds()
    {
        return graded ? GRADES.stream().map(grade -> grade + "_" + id()).toList() : List.of(id());
    }

    /** @return the id of a block of this kind of ore in this rock */
    public static String blockId(String kind, Rock rock)
    {
        return "ore/" + kind + "/" + rockId(rock);
    }

    public static String rockId(Rock rock)
    {
        return rock.name().toLowerCase(Locale.ROOT);
    }
}
