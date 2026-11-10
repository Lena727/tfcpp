package tfcpp.health;

import java.nio.file.Path;
import java.util.ArrayList;
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

/**
 * The data of stage E of v1.5: the recipes of the cesspit and the sand filter, night soil as a fertiliser, and the drops of the two blocks.
 */
public final class HygieneData implements DataProvider
{
    /** Night soil on the scale of TFC's fertilisers (compost is 0.2 nitrogen and 0.2 potassium): a piece is 20 litres of wet waste, and the nitrogen of night soil is about 1 % of its wet mass (compost, drier, is 1 to 2 % of a smaller mass); 0.1 nitrogen, half that of phosphorus and potassium (a person's waste has 4 to 5 g of nitrogen, 1 g each of phosphorus and potassium a day: Jonsson et al., 2004, EcoSanRes). */
    static final float NIGHT_SOIL_NITROGEN = 0.1f, NIGHT_SOIL_PHOSPHORUS = 0.05f, NIGHT_SOIL_POTASSIUM = 0.05f;

    private final Path root;
    private final Map<String, JsonElement> files = new LinkedHashMap<>();

    public HygieneData(PackOutput output)
    {
        this.root = output.getOutputFolder();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache)
    {
        recipes();
        fertilizer();
        drops();

        final List<CompletableFuture<?>> saves = new ArrayList<>();
        files.forEach((path, json) -> saves.add(DataProvider.saveStable(cache, json, root.resolve(path))));
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    private void recipes()
    {
        // A lid of lumber over a hole lined with stone: all of it available before there is any metal
        final JsonObject pit = shaped("tfcpp:cesspit", "LLL", "S S", "SSS");
        pit.getAsJsonObject("key").add("L", tag("tfc:lumber"));
        pit.getAsJsonObject("key").add("S", tag("c:stones/loose"));
        put("data/tfcpp/recipe/crafting/cesspit.json", pit);

        // A box of lumber with sand over gravel
        final JsonObject filter = shaped("tfcpp:sand_filter", "LSL", "LGL", "LLL");
        filter.getAsJsonObject("key").add("L", tag("tfc:lumber"));
        filter.getAsJsonObject("key").add("S", tag("c:sands"));
        filter.getAsJsonObject("key").add("G", tag("c:gravels"));
        put("data/tfcpp/recipe/crafting/sand_filter.json", filter);
    }

    private void fertilizer()
    {
        final JsonObject fertilizer = new JsonObject();
        fertilizer.add("ingredient", item("tfcpp:night_soil"));
        fertilizer.addProperty("nitrogen", NIGHT_SOIL_NITROGEN);
        fertilizer.addProperty("phosphorus", NIGHT_SOIL_PHOSPHORUS);
        fertilizer.addProperty("potassium", NIGHT_SOIL_POTASSIUM);
        put("data/tfcpp/tfc/fertilizer/night_soil.json", fertilizer);
    }

    /** The two blocks drop themselves; what a cesspit holds is let into the ground when it is broken. */
    private void drops()
    {
        for (String block : List.of("cesspit", "sand_filter"))
        {
            final JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", "tfcpp:" + block);
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
    }

    private void put(String path, JsonElement json)
    {
        files.put(path, json);
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
        final JsonObject stack = new JsonObject();
        stack.addProperty("count", 1);
        stack.addProperty("id", result);
        recipe.add("result", stack);
        return recipe;
    }

    private static JsonObject item(String id)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }

    private static JsonObject tag(String id)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("tag", id);
        return json;
    }

    @Override
    public String getName()
    {
        return "tfcpp hygiene data";
    }
}
