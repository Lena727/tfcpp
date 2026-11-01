package tfcpp.metallurgy;

import java.util.IdentityHashMap;
import java.util.Map;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.Ore;
import net.dries007.tfc.common.items.Powder;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import tfcpp.geology.NewOre;
import tfcpp.geology.Ores;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasEmission;

/**
 * Connects TFC's heating recipes to the world. A recipe converting an item knows nothing of where that is happening, so each device announces itself for the length of its tick.
 * Conversions outside any device's tick are tooltips and displays, so they release nothing.
 */
public final class MeltContext
{
    private record Device(ServerLevel level, BlockPos pos) {}

    private record OreFumes(Sulfide mineral, float milliBuckets) {}

    private static final ThreadLocal<Device> CURRENT = new ThreadLocal<>();

    private static @Nullable Map<Item, OreFumes> sulfides;

    public static void enter(Level level, BlockPos pos)
    {
        if (level instanceof ServerLevel server)
        {
            CURRENT.set(new Device(server, pos));
        }
    }

    public static void exit()
    {
        CURRENT.remove();
    }

    public static void onConverted(ItemStack input)
    {
        final @Nullable Device device = CURRENT.get();
        if (device == null)
        {
            return;
        }
        if (input.getItem() == TFCItems.POWDERS.get(Powder.FLUX).asItem())
        {
            final float[] gases = new float[Gas.COUNT];
            gases[Gas.CARBON_DIOXIDE.ordinal()] = IronSmelting.CARBON_DIOXIDE_PER_FLUX * input.getCount(); // CaCO3 -> CaO + CO2, which is 44% of its weight gone
            GasEmission.release(device.level, device.pos, gases);
            return;
        }
        final @Nullable OreFumes ore = sulfides().get(input.getItem());
        if (ore != null)
        {
            GasEmission.release(device.level, device.pos, ore.mineral.fumes(ore.milliBuckets * input.getCount()));
        }
    }

    private static Map<Item, OreFumes> sulfides()
    {
        if (sulfides == null)
        {
            final Map<Item, OreFumes> map = new IdentityHashMap<>();
            graded(map, Ore.TETRAHEDRITE, Sulfide.TETRAHEDRITE);
            graded(map, Ore.SPHALERITE, Sulfide.SPHALERITE);
            graded(map, Ore.BISMUTHINITE, Sulfide.BISMUTHINITE);
            map.put(TFCItems.ORES.get(Ore.CINNABAR).get(), new OreFumes(Sulfide.CINNABAR, 25f));
            map.put(Ores.PIECES.get("poor_galena").get(), new OreFumes(Sulfide.GALENA, 15f));
            map.put(Ores.PIECES.get("normal_galena").get(), new OreFumes(Sulfide.GALENA, 25f));
            map.put(Ores.PIECES.get("rich_galena").get(), new OreFumes(Sulfide.GALENA, 35f));
            map.put(Ores.SMALL_ITEMS.get(NewOre.GALENA).get(), new OreFumes(Sulfide.GALENA, 10f));
            sulfides = map;
        }
        return sulfides;
    }

    /** The volumes of metal are the ones TFC's own recipes give for each grade. */
    private static void graded(Map<Item, OreFumes> map, Ore ore, Sulfide mineral)
    {
        final var grades = TFCItems.GRADED_ORES.get(ore);
        map.put(grades.get(Ore.Grade.POOR).get(), new OreFumes(mineral, 15f));
        map.put(grades.get(Ore.Grade.NORMAL).get(), new OreFumes(mineral, 25f));
        map.put(grades.get(Ore.Grade.RICH).get(), new OreFumes(mineral, 35f));
        map.put(TFCBlocks.SMALL_ORES.get(ore).get().asItem(), new OreFumes(mineral, 10f));
    }

    private MeltContext() {}
}
