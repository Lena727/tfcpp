package tfcpp.geology;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Generates the veins of the new ores, and the data that makes their blocks behave like TFC's: loot, tags, collapsing, and so on.
 * <p>
 * Numbers of veins and their sizes are on TFC's own scale. Most of these are common enough to find, as a world needs them all to get to the end of the chain, though not all in one place.
 */
public final class OreData implements DataProvider
{
    /** One kind of block in a vein, with its share. */
    private record Part(String block, int weight) {}

    /** How common each grade is, poorest first. */
    private static final int[] COMMON_GRADES = {35, 40, 25};
    private static final int[] POOR_GRADES = {60, 30, 10};

    private final Path root;
    private final Map<String, JsonElement> files = new LinkedHashMap<>();

    public OreData(PackOutput output)
    {
        this.root = output.getOutputFolder();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache)
    {
        final List<String> placed = new ArrayList<>();
        veins(placed);
        put("data/tfc/tags/worldgen/placed_feature/in_biome/veins.json", tag(placed));
        blocks();

        final List<CompletableFuture<?>> saves = new ArrayList<>();
        files.forEach((path, json) -> saves.add(DataProvider.saveStable(cache, json, root.resolve(path))));
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    private void veins(List<String> placed)
    {
        // Lead and zinc go together in carbonate rock, with barite as the waste around them: this is what a Mississippi Valley deposit is
        placed.add(cluster("galena_carbonate", 45, 0.5f, -30, 70, 30, 20, rocks(List.of(Rock.LIMESTONE, Rock.DOLOMITE, Rock.MARBLE), rock -> {
            final List<Part> parts = new ArrayList<>(graded(NewOre.GALENA, rock, 60, COMMON_GRADES));
            parts.addAll(sphalerite(rock, 30));
            parts.add(new Part("tfcpp:" + NewOre.blockId(NewOre.BARITE.id(), rock), 10));
            return parts;
        }), NewOre.GALENA));

        placed.add(cluster("galena_vein", 60, 0.5f, -70, 40, 25, 25, rocks(List.of(Rock.QUARTZITE, Rock.SLATE, Rock.PHYLLITE, Rock.SCHIST, Rock.GNEISS), rock -> {
            final List<Part> parts = new ArrayList<>(graded(NewOre.GALENA, rock, 90, COMMON_GRADES));
            parts.addAll(sphalerite(rock, 10));
            return parts;
        }), NewOre.GALENA));

        // A blanket under the soil, and not at any depth
        placed.add(disc("bauxite", 40, 0.7f, 50, 100, 40, 4, 20, gradedVein(NewOre.BAUXITE, COMMON_GRADES), NewOre.BAUXITE));
        placed.add(cluster("chromite", 30, 0.6f, -80, 20, 25, 12, gradedVein(NewOre.CHROMITE, COMMON_GRADES), NewOre.CHROMITE));
        placed.add(cluster("ilmenite", 70, 0.5f, -70, 40, 25, 20, gradedVein(NewOre.ILMENITE, POOR_GRADES), NewOre.ILMENITE));
        placed.add(disc("pyrolusite", 80, 0.5f, 20, 90, 30, 3, 20, gradedVein(NewOre.PYROLUSITE, COMMON_GRADES), NewOre.PYROLUSITE));
        placed.add(disc("apatite", 90, 0.35f, 20, 90, 30, 3, 0, plainVein(NewOre.APATITE), null));
        placed.add(cluster("barite", 30, 0.4f, -40, 70, 20, 0, plainVein(NewOre.BARITE), null));
    }

    private interface RockParts
    {
        List<Part> of(Rock rock);
    }

    private static JsonArray rocks(List<Rock> hosts, RockParts parts)
    {
        final JsonArray blocks = new JsonArray();
        for (Rock rock : hosts)
        {
            final JsonArray replace = new JsonArray();
            replace.add("tfc:rock/raw/" + NewOre.rockId(rock));
            final JsonArray with = new JsonArray();
            for (Part part : parts.of(rock))
            {
                final JsonObject entry = new JsonObject();
                entry.addProperty("weight", part.weight);
                entry.addProperty("block", part.block);
                with.add(entry);
            }
            final JsonObject replacement = new JsonObject();
            replacement.add("replace", replace);
            replacement.add("with", with);
            blocks.add(replacement);
        }
        return blocks;
    }

    private static List<Part> graded(NewOre ore, Rock rock, int share, int[] grades)
    {
        final List<Part> parts = new ArrayList<>();
        final List<String> kinds = ore.kinds();
        for (int i = 0; i < kinds.size(); i++)
        {
            parts.add(new Part("tfcpp:" + NewOre.blockId(kinds.get(i), rock), share * grades[i]));
        }
        return parts;
    }

    /** What little zinc there is in a lead vein is mostly poor. */
    private static List<Part> sphalerite(Rock rock, int share)
    {
        final String id = NewOre.rockId(rock);
        return List.of(new Part("tfc:ore/poor_sphalerite/" + id, share * 70), new Part("tfc:ore/normal_sphalerite/" + id, share * 25), new Part("tfc:ore/rich_sphalerite/" + id, share * 5));
    }

    private static JsonArray gradedVein(NewOre ore, int[] grades)
    {
        return rocks(ore.hosts(), rock -> graded(ore, rock, 1, grades));
    }

    private static JsonArray plainVein(NewOre ore)
    {
        return rocks(ore.hosts(), rock -> List.of(new Part("tfcpp:" + NewOre.blockId(ore.id(), rock), 1)));
    }

    private String cluster(String name, int rarity, float density, int minY, int maxY, int size, int indicatorRarity, JsonArray blocks, @Nullable NewOre indicator)
    {
        return vein("tfc:cluster_vein", name, rarity, density, minY, maxY, size, null, indicatorRarity, blocks, indicator);
    }

    private String disc(String name, int rarity, float density, int minY, int maxY, int size, int height, int indicatorRarity, JsonArray blocks, @Nullable NewOre indicator)
    {
        return vein("tfc:disc_vein", name, rarity, density, minY, maxY, size, height, indicatorRarity, blocks, indicator);
    }

    /** @return the id of the placed feature */
    private String vein(String type, String name, int rarity, float density, int minY, int maxY, int size, @Nullable Integer height, int indicatorRarity, JsonArray blocks, @Nullable NewOre indicator)
    {
        final JsonObject config = new JsonObject();
        config.addProperty("rarity", rarity);
        config.addProperty("density", density);
        config.addProperty("min_y", minY);
        config.addProperty("max_y", maxY);
        config.addProperty("size", size);
        if (height != null)
        {
            config.addProperty("height", height);
        }
        config.addProperty("random_name", "tfcpp_" + name);
        config.add("blocks", blocks);
        if (indicator != null)
        {
            final JsonObject small = new JsonObject();
            small.addProperty("block", "tfcpp:ore/small_" + indicator.id());
            final JsonArray list = new JsonArray();
            list.add(small);
            final JsonObject marker = new JsonObject();
            marker.addProperty("rarity", indicatorRarity);
            marker.addProperty("depth", 35);
            marker.addProperty("underground_rarity", 1);
            marker.addProperty("underground_count", 0);
            marker.add("blocks", list);
            config.add("indicator", marker);
        }

        final JsonObject feature = new JsonObject();
        feature.addProperty("type", type);
        feature.add("config", config);
        put("data/tfcpp/worldgen/configured_feature/vein/" + name + ".json", feature);

        final JsonObject placed = new JsonObject();
        placed.addProperty("feature", "tfcpp:vein/" + name);
        placed.add("placement", new JsonArray());
        put("data/tfcpp/worldgen/placed_feature/vein/" + name + ".json", placed);
        return "tfcpp:vein/" + name;
    }

    private void blocks()
    {
        final List<String> allOres = new ArrayList<>();
        final List<String> pieces = new ArrayList<>();
        final List<String> small = new ArrayList<>();
        final List<String> allBlocks = new ArrayList<>();
        final Map<Rock, List<String>> toCobble = new LinkedHashMap<>();

        for (NewOre ore : NewOre.values())
        {
            final List<String> ofOre = new ArrayList<>();
            final List<String> kinds = ore.kinds();
            for (int i = 0; i < kinds.size(); i++)
            {
                final String kind = kinds.get(i);
                pieces.add("tfcpp:ore/" + kind);
                for (Rock rock : ore.hosts())
                {
                    final String block = "tfcpp:" + NewOre.blockId(kind, rock);
                    ofOre.add(block);
                    loot(NewOre.blockId(kind, rock), "tfcpp:ore/" + kind);

                    // A rich block collapses into a poorer one, and the poorest into rubble, as in TFC
                    if (i == 0)
                    {
                        toCobble.computeIfAbsent(rock, r -> new ArrayList<>()).add(block);
                    }
                    else
                    {
                        final JsonArray ingredient = new JsonArray();
                        ingredient.add(block);
                        final JsonObject collapse = new JsonObject();
                        collapse.addProperty("type", "tfc:collapse");
                        collapse.add("ingredient", ingredient);
                        collapse.addProperty("result", "tfcpp:" + NewOre.blockId(kinds.get(i - 1), rock));
                        put("data/tfcpp/recipe/collapse/" + NewOre.blockId(kind, rock) + ".json", collapse);
                    }
                }
            }
            allBlocks.addAll(ofOre);
            put("data/c/tags/block/ores/" + ore.id() + ".json", tag(ofOre));
            put("data/c/tags/item/ores/" + ore.id() + ".json", tag(ofOre));
            allOres.add("#c:ores/" + ore.id());

            if (ore.graded())
            {
                final String id = "ore/small_" + ore.id();
                small.add("tfcpp:" + id);
                loot(id, "tfcpp:" + id);
            }

            final JsonObject heat = new JsonObject();
            final JsonArray ingredient = new JsonArray();
            kinds.forEach(kind -> ingredient.add(item("tfcpp:ore/" + kind)));
            heat.add("ingredient", ingredient);
            heat.addProperty("heat_capacity", 1.1428572f);
            put("data/tfcpp/tfc/item_heat/ore_" + ore.id() + ".json", heat);
        }

        toCobble.forEach((rock, blocks) -> {
            final JsonArray ingredient = new JsonArray();
            blocks.forEach(ingredient::add);
            final JsonObject collapse = new JsonObject();
            collapse.addProperty("type", "tfc:collapse");
            collapse.add("ingredient", ingredient);
            collapse.addProperty("result", "tfc:rock/cobble/" + NewOre.rockId(rock));
            put("data/tfcpp/recipe/collapse/cobble_" + NewOre.rockId(rock) + ".json", collapse);
        });

        put("data/c/tags/block/ores.json", tag(allOres));
        put("data/c/tags/item/ores.json", tag(allOres));
        put("data/tfcpp/tags/block/ore_pickaxe.json", tag(allBlocks));
        put("data/tfc/tags/item/ore_pieces.json", tag(pieces));
        put("data/tfc/tags/item/small_ore_pieces.json", tag(small));
        put("data/tfc/tags/block/can_be_snow_piled.json", tag(small));

        final JsonObject heat = new JsonObject();
        final JsonArray ingredient = new JsonArray();
        small.forEach(id -> ingredient.add(item(id)));
        heat.add("ingredient", ingredient);
        heat.addProperty("heat_capacity", 0.5714286f);
        put("data/tfcpp/tfc/item_heat/small_ores.json", heat);
    }

    private void loot(String block, String drop)
    {
        final JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", drop);
        final JsonArray entries = new JsonArray();
        entries.add(entry);
        final JsonObject survives = new JsonObject();
        survives.addProperty("condition", "minecraft:survives_explosion");
        final JsonArray conditions = new JsonArray();
        conditions.add(survives);
        final JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("entries", entries);
        pool.add("conditions", conditions);
        final JsonArray pools = new JsonArray();
        pools.add(pool);
        final JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:block");
        table.add("pools", pools);
        put("data/tfcpp/loot_table/blocks/" + block + ".json", table);
    }

    private void put(String path, JsonElement json)
    {
        files.put(path, json);
    }

    private static JsonObject tag(List<String> values)
    {
        final JsonArray array = new JsonArray();
        values.forEach(array::add);
        final JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", array);
        return tag;
    }

    private static JsonObject item(String id)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }

    @Override
    public String getName()
    {
        return "tfcpp ore data";
    }
}
