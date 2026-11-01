package tfcpp.chemistry;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import tfcpp.metallurgy.MetalHeat;
import tfcpp.pneumatics.gas.Gas;

/**
 * Generates the data of coal chemistry: the carbonization recipes from {@link CarbonizationTable}, the fuels it makes, and the tags that tell TFC what its liquids and fuels are. The tags that other providers of this mod also write, such as the pickaxe tag, are written there.
 */
public final class ChemistryData implements DataProvider
{
    private static final List<String> WOODS = List.of("acacia", "ash", "aspen", "birch", "blackwood", "chestnut", "douglas_fir", "hickory", "kapok", "mangrove", "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce", "sycamore", "white_cedar", "willow");
    /** Coke and char are porous carbon: the specific heat of coke over its working range is close to 1.3 J/g/K, which is more than graphite has at room temperature. */
    private static final float COKE_SPECIFIC_HEAT = 1.3f, CHAR_SPECIFIC_HEAT = 1.2f;
    private static final float PIECE_GRAMS = 3000f;

    private final Path root;
    private final Map<String, JsonElement> files = new LinkedHashMap<>();

    public ChemistryData(PackOutput output)
    {
        this.root = output.getOutputFolder();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache)
    {
        carbonization();
        fuels();
        tags();
        crafting();

        final List<CompletableFuture<?>> saves = new java.util.ArrayList<>();
        files.forEach((path, json) -> saves.add(DataProvider.saveStable(cache, json, root.resolve(path))));
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    private void carbonization()
    {
        for (CarbonizationTable.Row row : CarbonizationTable.ROWS)
        {
            final JsonObject solid = new JsonObject();
            solid.addProperty("item", row.solidId());
            solid.addProperty("per_piece", (float) row.solidPerPiece());

            final JsonObject gas = new JsonObject();
            for (Gas species : Gas.VALUES)
            {
                final Float moles = row.gas().get(species);
                if (moles != null)
                {
                    gas.addProperty(species.id(), moles);
                }
            }

            final JsonObject recipe = new JsonObject();
            recipe.addProperty("type", "tfcpp:carbonization");
            recipe.add("input", input(row.name()));
            recipe.addProperty("temperature", row.temperature());
            recipe.addProperty("seconds", row.seconds());
            recipe.add("solid", solid);
            recipe.add("tar", condensate(row.tar(), row.tarPerPiece()));
            recipe.add("liquor", condensate(row.liquor(), row.liquorPerPiece()));
            recipe.add("gas", gas);
            put("data/tfcpp/recipe/carbonization/" + row.name() + ".json", recipe);
        }
    }

    private static JsonElement input(String name)
    {
        final JsonArray list = new JsonArray();
        switch (name)
        {
            case "coal" -> {
                list.add(item("minecraft:coal"));
                list.add(item("tfc:ore/bituminous_coal"));
            }
            case "lignite" -> list.add(item("tfc:ore/lignite"));
            default -> WOODS.forEach(wood -> {
                final JsonObject logs = new JsonObject();
                logs.addProperty("tag", "tfc:" + wood + "_logs");
                list.add(logs);
            });
        }
        return list;
    }

    private static JsonObject condensate(Liquid liquid, float litres)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("fluid", "tfcpp:" + liquid.id());
        json.addProperty("litres", litres);
        return json;
    }

    private void fuels()
    {
        // Burns hotter than the coal it came from because the volatile matter that cools a flame is gone, and for as long as charcoal does
        fuel("coke", 1250f, 2200, COKE_SPECIFIC_HEAT);
        // Lignite chars without caking and leaves a crumbling, ashy fuel that burns out quickly and cannot hold a blast furnace charge
        fuel("lignite_char", 900f, 800, CHAR_SPECIFIC_HEAT);

        final JsonObject quern = new JsonObject();
        quern.addProperty("type", "tfc:quern");
        quern.add("ingredient", item("tfcpp:coke"));
        quern.add("result", stack("tfc:powder/coke", 4));
        put("data/tfcpp/recipe/quern/coke.json", quern);
    }

    private void fuel(String name, float temperature, int duration, float specificHeat)
    {
        final JsonObject fuel = new JsonObject();
        fuel.addProperty("duration", duration);
        fuel.add("ingredient", item("tfcpp:" + name));
        fuel.addProperty("temperature", temperature);
        put("data/tfcpp/tfc/fuel/" + name + ".json", fuel);

        final JsonObject heat = new JsonObject();
        heat.add("ingredient", item("tfcpp:" + name));
        heat.addProperty("heat_capacity", PIECE_GRAMS * specificHeat / MetalHeat.JOULES_PER_UNIT);
        put("data/tfcpp/tfc/item_heat/" + name + ".json", heat);
    }

    private void tags()
    {
        addToTag("data/tfc/tags/item/blast_furnace_fuel.json", "tfcpp:coke"); // Coal and lignite are not: their sulfur would go into the iron and their lumps would cake shut
        addToTag("data/minecraft/tags/item/coals.json", "tfcpp:coke");
        addToTag("data/c/tags/item/coal_coke.json", "tfcpp:coke");
        addToTag("data/tfcpp/tags/item/lights_gas_burner.json", "minecraft:flint_and_steel", "tfc:firestarter", "tfc:flint_and_pyrite");

        // Tar and the liquors are kept in barrels and wooden buckets in reality. They are not ingredients of any TFC recipe, so they stay out of tfc:ingredients.
        final String[] liquids = java.util.Arrays.stream(Liquid.values()).map(liquid -> "tfcpp:" + liquid.id()).toArray(String[]::new);
        addToTag("data/tfc/tags/fluid/usable_in_barrel.json", liquids);
        addToTag("data/tfc/tags/fluid/usable_in_wooden_bucket.json", liquids);
        for (Liquid liquid : Liquid.values())
        {
            addToTag("data/c/tags/fluid/" + liquid.id() + ".json", "tfcpp:" + liquid.id());
        }
    }

    private void crafting()
    {
        final JsonObject oven = shaped("tfcpp:coke_oven", "BBB", "BSB", "BBB");
        oven.getAsJsonObject("key").add("B", item("tfc:ceramic/fire_brick"));
        oven.getAsJsonObject("key").add("S", item("tfc:metal/sheet/wrought_iron"));
        put("data/tfcpp/recipe/crafting/coke_oven.json", oven);

        final JsonObject burner = shaped("tfcpp:gas_burner", " C ", "BBB");
        burner.getAsJsonObject("key").add("C", item("tfc:brass_mechanisms"));
        burner.getAsJsonObject("key").add("B", item("tfc:metal/sheet/wrought_iron"));
        put("data/tfcpp/recipe/crafting/gas_burner.json", burner);
    }

    private void put(String path, JsonElement json)
    {
        files.put(path, json);
    }

    private void addToTag(String path, String... values)
    {
        final JsonArray array = new JsonArray();
        for (String value : values)
        {
            array.add(value);
        }
        final JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", array);
        put(path, tag);
    }

    private static JsonObject shaped(String result, String... pattern)
    {
        final JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");
        recipe.addProperty("category", "misc");
        recipe.add("key", new JsonObject());
        final JsonArray rows = new JsonArray();
        for (String row : pattern)
        {
            rows.add(row);
        }
        recipe.add("pattern", rows);
        recipe.add("result", stack(result, 1));
        return recipe;
    }

    private static JsonObject item(String id)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }

    private static JsonObject stack(String id, int count)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("count", count);
        json.addProperty("id", id);
        return json;
    }

    @Override
    public String getName()
    {
        return "tfcpp chemistry data";
    }
}
