package tfcpp.metallurgy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.DyeColor;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Generates tfcpp's recipes and data, and its overrides of TFC's own. Where a whole family of TFC's recipes changes in the same way, this reads them from the TFC jar and patches them, so nothing is listed by hand and nothing gets missed.
 */
public final class MetallurgyData implements DataProvider
{
    private static final int[] ORE_MILLIBUCKETS = {10, 15, 25, 35};
    private static final String[] ORE_GRADES = {"small", "poor", "normal", "rich"};
    private static final float CINNABAR_MERCURY_MOLES = Sulfide.CINNABAR.metalMoles(25f);

    private static final float CAST_IRON_MELT = 1200f;
    private static final float ORIGINAL_CAST_IRON_MELT = 1535f;
    /** Hematite melts at about 1565 C, and goes on being an oxide. */
    private static final float IRON_OXIDE_MELT = 1565f;
    private static final float STEEL_MELT = 1500f, HIGH_CARBON_STEEL_MELT = 1450f, ORIGINAL_STEEL_MELT = 1540f;
    /** A bloomery loses a third of its iron to slag. */
    private static final int BLOOMERY_CHARGE_PER_BLOOM = 150;
    private static final float ORIGINAL_CERAMIC_TEMPERATURE = 1399f, POTTERY_TEMPERATURE = 850f, FIRE_CLAY_TEMPERATURE = 1300f;
    private static final Set<String> FIRE_CLAY = Set.of("ceramic/fire_brick.json", "ceramic/fire_ingot_mold.json", "crucible.json", "channel.json", "mold_table.json");
    private static final float STERLING_SILVER_MELT = 890f, ORIGINAL_STERLING_SILVER_MELT = 950f;
    private static final float WATER_QUENCH = 15f, OIL_QUENCH = 5f;
    private static final int TALLOW_MILLIBUCKETS = 400;

    private final Path root;
    private final Map<String, JsonElement> files = new LinkedHashMap<>();

    public MetallurgyData(PackOutput output)
    {
        this.root = output.getOutputFolder();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache)
    {
        try
        {
            realAlloys();
            sulfideOres();
            arsenicalCopper();
            retort();
            noMoreRedstone();
            ironTemperatures();
            ironAndSteel();
            ceramics();
            fuels();
            heatCapacities();
            otherRecipes();
            impossibleRecipes();
            tags();
            lootTables();
            mining();
            pipes();
        }
        catch (IOException e)
        {
            return CompletableFuture.failedFuture(e);
        }

        final List<CompletableFuture<?>> saves = new ArrayList<>();
        files.forEach((path, json) -> saves.add(DataProvider.saveStable(cache, json, root.resolve(path))));
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    private void sulfideOres()
    {
        for (int i = 0; i < ORE_GRADES.length; i++)
        {
            final String grade = ORE_GRADES[i];

            // Tetrahedrite smelts to copper still carrying its arsenic and antimony
            put("data/tfc/recipe/heating/ore/" + grade + "_tetrahedrite.json", heatingToFluid(item("tfc:ore/" + grade + "_tetrahedrite"), "tfcpp:metal/arsenical_copper", ORE_MILLIBUCKETS[i], 1080f));

            // Bismuthinite has to be roasted before it gives up its bismuth, well above the 271 C that bismuth melts at
            put("data/tfc/recipe/heating/ore/" + grade + "_bismuthinite.json", heatingToFluid(item("tfc:ore/" + grade + "_bismuthinite"), "tfc:metal/bismuth", ORE_MILLIBUCKETS[i], 600f));

            // Sphalerite does not melt to zinc at all. Roasting gives an oxide, and only a retort can reduce that.
            final JsonObject sphalerite = new JsonObject();
            sphalerite.addProperty("type", "tfc:heating");
            sphalerite.add("ingredient", item("tfc:ore/" + grade + "_sphalerite"));
            sphalerite.add("result_item", stack("tfcpp:calcine/" + grade + "_zinc", 1));
            sphalerite.addProperty("temperature", 900f);
            put("data/tfc/recipe/heating/ore/" + grade + "_sphalerite.json", sphalerite);
        }

        // Roasting cinnabar in an open furnace leaves nothing: all the mercury goes up as vapour
        final JsonObject cinnabar = new JsonObject();
        cinnabar.addProperty("type", "tfc:heating");
        cinnabar.add("ingredient", item("tfc:ore/cinnabar"));
        cinnabar.addProperty("temperature", 600f);
        put("data/tfcpp/recipe/heating/cinnabar.json", cinnabar);

        final JsonObject cinnabarHeat = new JsonObject();
        cinnabarHeat.add("ingredient", item("tfc:ore/cinnabar"));
        cinnabarHeat.addProperty("heat_capacity", 1.1428572f);
        put("data/tfcpp/tfc/item_heat/cinnabar.json", cinnabarHeat);
    }

    private void arsenicalCopper() throws IOException
    {
        final JsonObject fluidHeat = new JsonObject();
        fluidHeat.addProperty("fluid", "tfcpp:metal/arsenical_copper");
        fluidHeat.addProperty("melt_temperature", 1050f); // Arsenic lowers the melting point of copper
        fluidHeat.addProperty("specific_heat_capacity", MetalHeat.COPPER.liquidCapacity());
        put("data/tfcpp/tfc/fluid_heat/arsenical_copper.json", fluidHeat);

        final JsonObject ingotHeat = new JsonObject();
        ingotHeat.add("ingredient", item("tfcpp:metal/ingot/arsenical_copper"));
        ingotHeat.addProperty("heat_capacity", MetalHeat.COPPER.ingotCapacity());
        ingotHeat.addProperty("forging_temperature", 630f);
        ingotHeat.addProperty("welding_temperature", 840f);
        put("data/tfcpp/tfc/item_heat/arsenical_copper_ingot.json", ingotHeat);

        put("data/tfcpp/recipe/heating/arsenical_copper_ingot.json", heatingToFluid(item("tfcpp:metal/ingot/arsenical_copper"), "tfcpp:metal/arsenical_copper", 100, 1050f));

        final JsonObject casting = new JsonObject();
        casting.addProperty("type", "tfc:casting");
        casting.addProperty("break_chance", 0.1f);
        casting.add("fluid", fluid("tfcpp:metal/arsenical_copper", 100));
        casting.add("mold", item("tfc:ceramic/ingot_mold"));
        casting.add("result", stack("tfcpp:metal/ingot/arsenical_copper", 1));
        put("data/tfcpp/recipe/casting/arsenical_copper_ingot.json", casting);

        // It casts into every copper tool: being harder than pure copper, it was what early bronze age tools were actually made of
        final Path castingDir = tfcFile("data/tfc/recipe/casting");
        try (Stream<Path> recipes = Files.list(castingDir))
        {
            for (Path path : (Iterable<Path>) recipes::iterator)
            {
                final String name = path.getFileName().toString();
                if (name.startsWith("copper_") && !name.contains("ingot"))
                {
                    final JsonObject recipe = read(path);
                    final JsonObject mold = recipe.getAsJsonObject("fluid");
                    final JsonObject tagged = new JsonObject();
                    tagged.addProperty("amount", mold.get("amount").getAsInt());
                    tagged.addProperty("tag", "tfcpp:castable_copper");
                    recipe.add("fluid", tagged);
                    put("data/tfc/recipe/casting/" + name, recipe);

                    final String part = recipe.getAsJsonObject("result").get("id").getAsString().split("/")[1];
                    remeltsImpure(part);
                    remeltsImpure(part.replace("_head", "").replace("_blade", ""));
                }
            }
        }

        final JsonObject fireIngot = read(tfcFile("data/tfc/recipe/casting/copper_fire_ingot.json"));
        fireIngot.add("fluid", fluid("tfcpp:metal/arsenical_copper", fireIngot.getAsJsonObject("fluid").get("amount").getAsInt()));
        fireIngot.add("result", stack("tfcpp:metal/ingot/arsenical_copper", 1));
        put("data/tfcpp/recipe/casting/arsenical_copper_fire_ingot.json", fireIngot);

        // Bronze hides arsenic well, so it works in place of copper. Silver and gold alloys are left out, being valued for their purity.
        for (String alloy : List.of("bronze", "bismuth_bronze", "black_bronze", "brass"))
        {
            final JsonObject recipe = current("data/tfc/recipe/alloy/" + alloy + ".json");
            for (JsonElement content : recipe.getAsJsonArray("contents"))
            {
                final JsonObject range = content.getAsJsonObject();
                if (range.get("fluid").getAsString().equals("tfc:metal/copper"))
                {
                    range.addProperty("fluid", "tfcpp:metal/arsenical_copper");
                }
            }
            put("data/tfcpp/recipe/alloy/arsenical_" + alloy + ".json", recipe);
        }
    }

    /**
     * A tool cast from arsenical copper is the same item as one cast from pure copper, so melting it down has to give the impure metal. Otherwise casting and remelting would remove arsenic for nothing, which takes electrolysis in reality.
     * Scrap tools being of unknown purity is also how scrap copper really behaves.
     */
    private void remeltsImpure(String part) throws IOException
    {
        final Path path = tfcFile("data/tfc/recipe/heating/metal/" + part + "/copper.json");
        if (Files.exists(path))
        {
            final JsonObject recipe = read(path);
            recipe.getAsJsonObject("result_fluid").addProperty("id", "tfcpp:metal/arsenical_copper");
            put("data/tfc/recipe/heating/metal/" + part + "/copper.json", recipe);
        }
    }

    private void retort()
    {
        // ZnO + C -> Zn + CO, with the CO venting off
        for (int i = 0; i < ORE_GRADES.length; i++)
        {
            final float moles = Sulfide.SPHALERITE.metalMoles(ORE_MILLIBUCKETS[i]);
            final JsonObject zinc = new JsonObject();
            zinc.addProperty("type", "tfcpp:retort");
            zinc.add("input", item("tfcpp:calcine/" + ORE_GRADES[i] + "_zinc"));
            zinc.addProperty("carbon", moles);
            zinc.addProperty("temperature", 1000f);
            zinc.add("result", fluidStack("tfc:metal/zinc", ORE_MILLIBUCKETS[i]));
            zinc.add("vapor", gases("zinc_oxide_fume", moles));
            zinc.add("byproducts", gases("carbon_monoxide", moles));
            put("data/tfcpp/recipe/retort/zinc_" + ORE_GRADES[i] + ".json", zinc);
        }

        // HgS + O2 -> Hg + SO2
        final JsonObject mercury = new JsonObject();
        mercury.addProperty("type", "tfcpp:retort");
        mercury.add("input", item("tfc:ore/cinnabar"));
        mercury.addProperty("temperature", 600f);
        mercury.add("result", fluidStack("tfcpp:mercury", 25));
        mercury.add("vapor", gases("mercury_vapor", CINNABAR_MERCURY_MOLES));
        mercury.add("byproducts", gases("sulfur_dioxide", CINNABAR_MERCURY_MOLES));
        put("data/tfcpp/recipe/retort/mercury.json", mercury);

        final JsonObject retort = shaped("tfcpp:retort", "BBB", "B B", "BBB");
        retort.getAsJsonObject("key").add("B", item("tfc:ceramic/fire_brick"));
        put("data/tfcpp/recipe/crafting/retort.json", retort);

        final JsonObject condenser = shaped("tfcpp:retort_condenser", "BSB", "B B", "BSB");
        condenser.getAsJsonObject("key").add("B", item("tfc:ceramic/fire_brick"));
        condenser.getAsJsonObject("key").add("S", item("tfc:metal/sheet/copper"));
        put("data/tfcpp/recipe/crafting/retort_condenser.json", condenser);

        final JsonObject bulb = shaped("tfcpp:glass_bulb", "G");
        bulb.getAsJsonObject("key").add("G", item("minecraft:glass_pane"));
        put("data/tfcpp/recipe/crafting/glass_bulb.json", bulb);

        // A real thermometer is a bulb of mercury in glass. This replaces TFC's use of redstone, now that cinnabar no longer gives any.
        final JsonObject thermometer = shaped("tfc:thermometer", "G", "B", "S");
        final JsonObject key = thermometer.getAsJsonObject("key");
        key.add("G", item("minecraft:glass_pane"));
        key.add("S", item("tfc:metal/sheet/brass"));
        key.add("B", item("tfcpp:mercury_bulb"));
        put("data/tfc/recipe/crafting/thermometer.json", thermometer);
    }

    /** With no redstone in the world, the mechanisms that TFC built from it are built from brass clockwork instead. */
    private void noMoreRedstone() throws IOException
    {
        final Path recipes = tfcFile("data/tfc/recipe");
        try (Stream<Path> paths = Files.walk(recipes))
        {
            for (Path path : (Iterable<Path>) paths::iterator)
            {
                final String relative = recipes.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".json") && !relative.equals("quern/redstone.json") && !relative.equals("crafting/thermometer.json"))
                {
                    final JsonObject recipe = read(path);
                    if (replaceRedstone(recipe))
                    {
                        put("data/tfc/recipe/" + relative, recipe);
                    }
                }
            }
        }
    }

    private static boolean replaceRedstone(JsonElement json)
    {
        boolean replaced = false;
        if (json.isJsonArray())
        {
            for (JsonElement element : json.getAsJsonArray())
            {
                replaced |= replaceRedstone(element);
            }
        }
        else if (json.isJsonObject())
        {
            final JsonObject object = json.getAsJsonObject();
            final boolean isItem = object.has("item") && object.get("item").getAsString().equals("minecraft:redstone");
            final boolean isTag = object.has("tag") && object.get("tag").getAsString().equals("c:dusts/redstone");
            if (isItem || isTag)
            {
                object.remove("tag");
                object.addProperty("item", "tfc:brass_mechanisms");
                return true;
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
            {
                replaced |= replaceRedstone(entry.getValue());
            }
        }
        return replaced;
    }

    /** Carbon lowers the melting point of iron by close to 400 C, which is why cast iron could flow from a furnace when pure iron could not. */
    private void ironTemperatures() throws IOException
    {
        for (String metal : List.of("cast_iron", "pig_iron"))
        {
            final JsonObject heat = read(tfcFile("data/tfc/tfc/fluid_heat/" + metal + ".json"));
            heat.addProperty("melt_temperature", CAST_IRON_MELT);
            put("data/tfc/tfc/fluid_heat/" + metal + ".json", heat);
        }

        // TFC derives working temperatures from the melting point, and welding has to stay below it
        final Path itemHeat = tfcFile("data/tfc/tfc/item_heat");
        try (Stream<Path> definitions = Files.walk(itemHeat))
        {
            for (Path path : (Iterable<Path>) definitions::iterator)
            {
                final String relative = itemHeat.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".json") && (relative.contains("cast_iron") || relative.contains("pig_iron")))
                {
                    final JsonObject definition = read(path);
                    for (String field : List.of("forging_temperature", "welding_temperature"))
                    {
                        if (definition.has(field))
                        {
                            definition.addProperty(field, definition.get(field).getAsFloat() * CAST_IRON_MELT / ORIGINAL_CAST_IRON_MELT);
                        }
                    }
                    put("data/tfc/tfc/item_heat/" + relative, definition);
                }
            }
        }

        final Path heating = tfcFile("data/tfc/recipe/heating");
        try (Stream<Path> recipes = Files.walk(heating))
        {
            for (Path path : (Iterable<Path>) recipes::iterator)
            {
                final String relative = heating.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".json") && (relative.contains("cast_iron") || relative.contains("pig_iron")))
                {
                    final JsonObject recipe = read(path);
                    recipe.addProperty("temperature", CAST_IRON_MELT);
                    put("data/tfc/recipe/heating/" + relative, recipe);
                }
            }
        }
    }

    /**
     * The whole family of iron, put in the order of its carbon. TFC calls every kind of molten iron cast iron, with carbon coming from nowhere when it is remelted and going nowhere under a hammer.
     * Here ore melts to nothing but an oxide without a furnace to reduce it, each metal melts back to itself, and carbon only moves by mixing or by burning.
     */
    private void ironAndSteel() throws IOException
    {
        final JsonObject oxide = new JsonObject();
        oxide.addProperty("fluid", "tfcpp:metal/iron_oxide");
        oxide.addProperty("melt_temperature", IRON_OXIDE_MELT);
        oxide.addProperty("specific_heat_capacity", 0.008571429f);
        put("data/tfcpp/tfc/fluid_heat/iron_oxide.json", oxide);

        final JsonObject steelHeat = read(tfcFile("data/tfc/tfc/fluid_heat/steel.json"));
        steelHeat.addProperty("melt_temperature", STEEL_MELT);
        put("data/tfc/tfc/fluid_heat/steel.json", steelHeat);
        final JsonObject highCarbonHeat = read(tfcFile("data/tfc/tfc/fluid_heat/high_carbon_steel.json"));
        highCarbonHeat.addProperty("melt_temperature", HIGH_CARBON_STEEL_MELT);
        put("data/tfc/tfc/fluid_heat/high_carbon_steel.json", highCarbonHeat);

        final Path heating = tfcFile("data/tfc/recipe/heating");
        try (Stream<Path> recipes = Files.walk(heating))
        {
            for (Path path : (Iterable<Path>) recipes::iterator)
            {
                final String relative = heating.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".json"))
                {
                    remeltIron(relative, read(path));
                }
            }
        }

        // Working temperatures follow the melting point, as TFC itself derives them
        final Path itemHeat = tfcFile("data/tfc/tfc/item_heat");
        try (Stream<Path> definitions = Files.walk(itemHeat))
        {
            for (Path path : (Iterable<Path>) definitions::iterator)
            {
                final String relative = itemHeat.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".json") && (relative.startsWith("steel") || relative.startsWith("high_carbon_steel")))
                {
                    final JsonObject definition = read(path);
                    final float melt = relative.startsWith("steel") ? STEEL_MELT : HIGH_CARBON_STEEL_MELT;
                    for (String field : List.of("forging_temperature", "welding_temperature"))
                    {
                        if (definition.has(field))
                        {
                            definition.addProperty(field, definition.get(field).getAsFloat() * melt / ORIGINAL_STEEL_MELT);
                        }
                    }
                    put("data/tfc/tfc/item_heat/" + relative, definition);
                }
            }
        }

        final JsonObject bloomery = read(tfcFile("data/tfc/recipe/bloomery/raw_iron_bloom.json"));
        bloomery.add("fluid", fluid("tfcpp:metal/iron_oxide", BLOOMERY_CHARGE_PER_BLOOM));
        put("data/tfc/recipe/bloomery/raw_iron_bloom.json", bloomery);

        final JsonObject blastFurnace = read(tfcFile("data/tfc/recipe/blast_furnace/pig_iron.json"));
        blastFurnace.add("fluid", fluid("tfcpp:metal/iron_oxide", 1));
        put("data/tfc/recipe/blast_furnace/pig_iron.json", blastFurnace);

        // Slag from a bloomery still holds its iron, and goes back in a furnace
        put("data/tfcpp/recipe/heating/iron_slag.json", heatingToFluid(item("tfcpp:slag/iron"), "tfcpp:metal/iron_oxide", IronSmelting.MILLIBUCKETS_PER_SLAG, IRON_OXIDE_MELT));
        final JsonObject slagHeat = new JsonObject();
        slagHeat.add("ingredient", item("tfcpp:slag/iron"));
        slagHeat.addProperty("heat_capacity", 1.1428572f);
        put("data/tfcpp/tfc/item_heat/iron_slag.json", slagHeat);

        // A hammer cannot take carbon out of iron. That takes oxygen.
        final JsonObject disabled = new JsonObject();
        final JsonArray conditions = new JsonArray();
        final JsonObject never = new JsonObject();
        never.addProperty("type", "neoforge:false");
        conditions.add(never);
        disabled.add("neoforge:conditions", conditions);
        put("data/tfc/recipe/anvil/metal/ingot/high_carbon_steel.json", disabled);
        put("data/tfc/recipe/anvil/metal/ingot/steel.json", disabled);

        // Melting wrought iron with high-carbon iron shares the carbon out by weight, as Indian crucible steel did, and as an open hearth still does. These shares are worked out from the carbon each one holds.
        for (String source : List.of("pig_iron", "cast_iron"))
        {
            final float carbon = source.equals("pig_iron") ? ConverterBath.Grade.PIG_IRON.carbon() : ConverterBath.Grade.CAST_IRON.carbon();
            final float steel = carbonShare(0.2f, carbon), highCarbon = carbonShare(1f, carbon), castIron = carbonShare(2.1f, carbon);
            alloyOfIron("steel_from_" + source, "tfc:metal/steel", source, steel, highCarbon);
            alloyOfIron("high_carbon_steel_from_" + source, "tfc:metal/high_carbon_steel", source, highCarbon, castIron);
            if (source.equals("pig_iron"))
            {
                alloyOfIron("cast_iron_from_pig_iron", "tfc:metal/cast_iron", source, castIron, 0.99f);
            }
        }

        final JsonObject converter = shaped("tfcpp:converter", "S S", "SBS", "SSS");
        converter.getAsJsonObject("key").add("S", item("tfc:metal/double_sheet/wrought_iron"));
        converter.getAsJsonObject("key").add("B", item("tfc:ceramic/fire_brick"));
        put("data/tfcpp/recipe/crafting/converter.json", converter);
    }

    /** @return the share of high-carbon iron that brings a mix with wrought iron up to this much carbon */
    private static float carbonShare(float targetPercent, float sourcePercent)
    {
        final float wrought = ConverterBath.Grade.WROUGHT_IRON.carbon();
        return (targetPercent - wrought) / (sourcePercent - wrought);
    }

    private void alloyOfIron(String name, String result, String source, float minShare, float maxShare)
    {
        final JsonObject high = new JsonObject();
        high.addProperty("fluid", "tfc:metal/" + source);
        high.addProperty("max", maxShare);
        high.addProperty("min", minShare);
        final JsonObject wrought = new JsonObject();
        wrought.addProperty("fluid", "tfc:metal/wrought_iron");
        wrought.addProperty("max", 1f - minShare);
        wrought.addProperty("min", 1f - maxShare);
        final JsonArray contents = new JsonArray();
        contents.add(high);
        contents.add(wrought);
        final JsonObject alloy = new JsonObject();
        alloy.addProperty("type", "tfc:alloy");
        alloy.add("contents", contents);
        alloy.addProperty("result", result);
        put("data/tfcpp/recipe/alloy/" + name + ".json", alloy);
    }

    /** Sends one of TFC's heating recipes to the metal that the item is really made of. */
    private void remeltIron(String relative, JsonObject recipe)
    {
        if (!recipe.has("result_fluid"))
        {
            return;
        }
        final JsonObject result = recipe.getAsJsonObject("result_fluid");
        switch (result.get("id").getAsString())
        {
            case "tfc:metal/cast_iron" -> {
                if (relative.startsWith("ore/"))
                {
                    result.addProperty("id", "tfcpp:metal/iron_oxide");
                    recipe.addProperty("temperature", IRON_OXIDE_MELT);
                    put("data/tfc/recipe/heating/" + relative, recipe);
                }
                else if (!relative.contains("cast_iron"))
                {
                    result.addProperty("id", "tfc:metal/wrought_iron"); // Blooms, and everything made from them
                    put("data/tfc/recipe/heating/" + relative, recipe);
                }
            }
            case "tfc:metal/pig_iron" -> {
                if (relative.contains("high_carbon_steel"))
                {
                    result.addProperty("id", "tfc:metal/high_carbon_steel");
                    recipe.addProperty("temperature", HIGH_CARBON_STEEL_MELT);
                    put("data/tfc/recipe/heating/" + relative, recipe);
                }
            }
            case "tfc:metal/steel" -> {
                recipe.addProperty("temperature", STEEL_MELT);
                put("data/tfc/recipe/heating/" + relative, recipe);
            }
            default -> {}
        }
    }

    /** TFC copied the proportions of bronze onto brass, and left sterling silver well short of what the name means. */
    private void realAlloys() throws IOException
    {
        alloyContents("brass", Map.of("tfc:metal/copper", new float[] {0.62f, 0.72f}, "tfc:metal/zinc", new float[] {0.28f, 0.38f}));
        alloyContents("sterling_silver", Map.of("tfc:metal/silver", new float[] {0.9f, 0.95f}, "tfc:metal/copper", new float[] {0.05f, 0.1f}));

        final float melt = STERLING_SILVER_MELT;
        final JsonObject heat = read(tfcFile("data/tfc/tfc/fluid_heat/sterling_silver.json"));
        heat.addProperty("melt_temperature", melt);
        put("data/tfc/tfc/fluid_heat/sterling_silver.json", heat);

        final Path recipes = tfcFile("data/tfc/recipe/heating");
        try (Stream<Path> paths = Files.walk(recipes))
        {
            for (Path path : (Iterable<Path>) paths::iterator)
            {
                final String relative = recipes.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".json") && relative.contains("sterling_silver"))
                {
                    final JsonObject recipe = read(path);
                    recipe.addProperty("temperature", melt);
                    put("data/tfc/recipe/heating/" + relative, recipe);
                }
            }
        }
        final Path itemHeat = tfcFile("data/tfc/tfc/item_heat/sterling_silver");
        try (Stream<Path> definitions = Files.list(itemHeat))
        {
            for (Path path : (Iterable<Path>) definitions::iterator)
            {
                final JsonObject definition = read(path);
                for (String field : List.of("forging_temperature", "welding_temperature"))
                {
                    if (definition.has(field))
                    {
                        definition.addProperty(field, definition.get(field).getAsFloat() * melt / ORIGINAL_STERLING_SILVER_MELT);
                    }
                }
                put("data/tfc/tfc/item_heat/sterling_silver/" + path.getFileName(), definition);
            }
        }
    }

    private void alloyContents(String name, Map<String, float[]> ranges) throws IOException
    {
        final JsonObject recipe = read(tfcFile("data/tfc/recipe/alloy/" + name + ".json"));
        for (JsonElement content : recipe.getAsJsonArray("contents"))
        {
            final JsonObject range = content.getAsJsonObject();
            final float[] share = ranges.get(range.get("fluid").getAsString());
            range.addProperty("min", share[0]);
            range.addProperty("max", share[1]);
        }
        put("data/tfc/recipe/alloy/" + name + ".json", recipe);
    }

    /**
     * Real earthenware is fired at 800-1100 C, and a pit reaches about 900 C. Only fire clay needs TFC's 1399 C or near it, and with that it needs a blast. This makes fire bricks, crucibles and the rest of the way to iron into a real step.
     */
    private void ceramics() throws IOException
    {
        final Path heating = tfcFile("data/tfc/recipe/heating");
        try (Stream<Path> recipes = Files.walk(heating))
        {
            for (Path path : (Iterable<Path>) recipes::iterator)
            {
                final String relative = heating.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".json"))
                {
                    final JsonObject recipe = read(path);
                    if (recipe.has("temperature") && recipe.get("temperature").getAsFloat() == ORIGINAL_CERAMIC_TEMPERATURE)
                    {
                        recipe.addProperty("temperature", FIRE_CLAY.contains(relative) ? FIRE_CLAY_TEMPERATURE : POTTERY_TEMPERATURE);
                        put("data/tfc/recipe/heating/" + relative, recipe);
                    }
                }
            }
        }
    }

    /** Without a blast, a fire of charcoal or coal stays well short of what TFC gives it. Lignite is a poor fuel with half the energy of coal. */
    private void fuels() throws IOException
    {
        fuel("charcoal", 1190f, null); // Enough to pour copper from a vessel before it sets, and still short of pig iron and fire clay
        fuel("coal", 1250f, null);
        fuel("ore/lignite", 1000f, 1100);
    }

    private void fuel(String name, float temperature, @Nullable Integer duration) throws IOException
    {
        final JsonObject fuel = read(tfcFile("data/tfc/tfc/fuel/" + name + ".json"));
        fuel.addProperty("temperature", temperature);
        if (duration != null)
        {
            fuel.addProperty("duration", duration);
        }
        put("data/tfc/tfc/fuel/" + name + ".json", fuel);
    }

    /** Every metal on one scale of energy, with its heat of melting. See {@link MetalHeat}. */
    private void heatCapacities() throws IOException
    {
        final Map<String, Float> ratios = new HashMap<>();
        for (MetalHeat.Metal metal : MetalHeat.METALS)
        {
            final String directory = "data/tfc/tfc/item_heat/" + metal.id() + "/";
            final float ratio = metal.ingotCapacity() / current(directory + "ingot.json").get("heat_capacity").getAsFloat();
            ratios.put("tfc:metal/" + metal.id(), ratio);
            try (Stream<Path> definitions = Files.list(tfcFile(directory)))
            {
                for (Path path : (Iterable<Path>) definitions::iterator)
                {
                    final String file = directory + path.getFileName();
                    final JsonObject definition = current(file);
                    definition.addProperty("heat_capacity", definition.get("heat_capacity").getAsFloat() * ratio);
                    put(file, definition);
                }
            }

            final String fluid = "data/tfc/tfc/fluid_heat/" + metal.id() + ".json";
            final JsonObject heat = current(fluid);
            heat.addProperty("specific_heat_capacity", metal.liquidCapacity());
            put(fluid, heat);
        }

        // Blooms, bells, doors and the rest that are listed on their own follow the metal they melt to
        final Map<String, String> meltsTo = new HashMap<>();
        final Path heating = tfcFile("data/tfc/recipe/heating");
        try (Stream<Path> recipes = Files.walk(heating))
        {
            for (Path path : (Iterable<Path>) recipes::iterator)
            {
                final String relative = heating.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".json"))
                {
                    final JsonObject recipe = current("data/tfc/recipe/heating/" + relative);
                    if (recipe.has("result_fluid"))
                    {
                        meltsTo.put(recipe.get("ingredient").toString(), recipe.getAsJsonObject("result_fluid").get("id").getAsString());
                    }
                }
            }
        }
        try (Stream<Path> definitions = Files.list(tfcFile("data/tfc/tfc/item_heat")))
        {
            for (Path path : (Iterable<Path>) definitions::iterator)
            {
                final String name = path.getFileName().toString();
                if (name.endsWith(".json"))
                {
                    final String file = "data/tfc/tfc/item_heat/" + name;
                    final JsonObject definition = current(file);
                    final @Nullable Float ratio = ratios.get(meltsTo.get(definition.get("ingredient").toString()));
                    if (ratio != null)
                    {
                        definition.addProperty("heat_capacity", definition.get("heat_capacity").getAsFloat() * ratio);
                        put(file, definition);
                    }
                }
            }
        }
    }

    private void otherRecipes() throws IOException
    {
        // 75/15/10 is the classic mix by weight, and 6:1:1 of equal powders is the nearest that fits. Graphite only coats the grains, so it adds its own weight and no more.
        final String saltpeter = "tfc:powder/saltpeter", sulfur = "tfc:powder/sulfur", charcoal = "tfc:powder/charcoal";
        put("data/tfc/recipe/crafting/gunpowder.json", shapeless("minecraft:gunpowder", 8, saltpeter, saltpeter, saltpeter, saltpeter, saltpeter, saltpeter, sulfur, charcoal));
        put("data/tfc/recipe/crafting/gunpowder_with_graphite.json", shapeless("minecraft:gunpowder", 9, saltpeter, saltpeter, saltpeter, saltpeter, saltpeter, saltpeter, sulfur, charcoal, "tfc:powder/graphite"));

        // Boron does not turn into calcium. Borax is a flux for welding, and limestone is what burns to lime.
        final JsonObject borax = read(tfcFile("data/tfc/recipe/quern/powder/flux_from_borax.json"));
        borax.add("result", stack("tfcpp:powder/borax", borax.getAsJsonObject("result").get("count").getAsInt()));
        put("data/tfc/recipe/quern/powder/flux_from_borax.json", borax);
        addToTag("data/tfc/tags/item/welding_flux.json", "tfcpp:powder/borax");
        addToTag("data/c/tags/item/dusts/borax.json", "tfcpp:powder/borax");

        // Chalk hardly dissolves at all. Lime water is made from burned lime.
        final JsonObject limewater = read(tfcFile("data/tfc/recipe/barrel/limewater.json"));
        final JsonObject lime = item("tfc:powder/lime");
        lime.addProperty("count", 1);
        limewater.add("input_item", lime);
        put("data/tfc/recipe/barrel/limewater.json", limewater);

        // Water takes heat away faster than oil, which is why oil makes a gentler quench. TFC has them the other way around.
        quench("water_cooling", WATER_QUENCH);
        quench("salt_water_cooling", WATER_QUENCH);
        quench("olive_oil_cooling", OIL_QUENCH);
        quench("canola_oil_cooling", OIL_QUENCH);

        // Water does not turn into fat. Blubber gives what fat it holds, and the water boils off.
        final JsonObject tallow = read(tfcFile("data/tfc/recipe/pot/tallow.json"));
        tallow.getAsJsonObject("fluid_output").addProperty("amount", TALLOW_MILLIBUCKETS);
        put("data/tfc/recipe/pot/tallow.json", tallow);
    }

    private void quench(String name, float degrees) throws IOException
    {
        final JsonObject recipe = read(tfcFile("data/tfc/recipe/barrel/" + name + ".json"));
        for (JsonElement modifier : recipe.getAsJsonObject("output_item").getAsJsonArray("modifiers"))
        {
            if (modifier.getAsJsonObject().get("type").getAsString().equals("tfc:add_heat"))
            {
                modifier.getAsJsonObject().addProperty("temperature", -degrees);
            }
        }
        put("data/tfc/recipe/barrel/" + name + ".json", recipe);
    }

    private static JsonObject shapeless(String result, int count, String... ingredients)
    {
        final JsonArray list = new JsonArray();
        for (String ingredient : ingredients)
        {
            list.add(item(ingredient));
        }
        final JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");
        recipe.addProperty("category", "misc");
        recipe.add("ingredients", list);
        recipe.add("result", stack(result, count));
        return recipe;
    }

    /** @return what this file holds so far, from this data if something here has already changed it, or else from TFC */
    private JsonObject current(String path) throws IOException
    {
        final @Nullable JsonElement changed = files.get(path);
        return changed != null ? changed.deepCopy().getAsJsonObject() : read(tfcFile(path));
    }

    /** Recipes that break the conservation of elements. */
    private void impossibleRecipes() throws IOException
    {
        final JsonObject disabled = new JsonObject();
        final JsonArray conditions = new JsonArray();
        final JsonObject never = new JsonObject();
        never.addProperty("type", "neoforge:false");
        conditions.add(never);
        disabled.add("neoforge:conditions", conditions);

        put("data/tfc/recipe/quern/redstone.json", disabled); // Cinnabar and cryolite do not contain redstone, whatever that would be
        put("data/tfc/recipe/quern/ore/gypsum.json", disabled); // Grinding limestone does not make it a sulfate

        // Seaweed ash holds only a few percent of soda, by weight of the dried plant. Three times its own weight would be creating matter.
        final Path powders = tfcFile("data/tfc/recipe/heating/powder");
        try (Stream<Path> recipes = Files.list(powders))
        {
            for (Path path : (Iterable<Path>) recipes::iterator)
            {
                final String name = path.getFileName().toString();
                if (name.startsWith("soda_ash_"))
                {
                    final JsonObject recipe = read(path);
                    final JsonObject result = new JsonObject();
                    final JsonArray modifiers = new JsonArray();
                    final JsonObject chance = new JsonObject();
                    chance.addProperty("type", "tfc:chance");
                    chance.addProperty("chance", 0.1f);
                    modifiers.add(chance);
                    result.add("modifiers", modifiers);
                    result.add("stack", stack("tfc:powder/soda_ash", 1));
                    recipe.add("result_item", result);
                    put("data/tfc/recipe/heating/powder/" + name, recipe);
                }
            }
        }
    }

    private void tags()
    {
        addToTag("data/tfc/tags/fluid/molten_metals.json", "tfcpp:metal/arsenical_copper"); // Not iron oxide: this is what lets a mould take it, and nothing can be cast from it
        addToTag("data/tfc/tags/fluid/usable_in_ingot_mold.json", "tfcpp:metal/arsenical_copper");
        addToTag("data/tfc/tags/fluid/usable_in_tool_head_mold.json", "tfcpp:metal/arsenical_copper");
        addToTag("data/tfc/tags/fluid/usable_in_jug.json", "tfcpp:mercury"); // Glazed pottery and glass both hold mercury, which attacks neither

        // Anything in a jug can be drunk, and nothing stops a person drinking this. What it does is in Breathing.
        final JsonObject mercury = new JsonObject();
        final JsonObject mercuryFluid = new JsonObject();
        mercuryFluid.addProperty("fluid", "tfcpp:mercury");
        final JsonArray mercuryIngredient = new JsonArray();
        mercuryIngredient.add(mercuryFluid);
        mercury.add("ingredient", mercuryIngredient);
        mercury.addProperty("may_drink_when_full", true);
        put("data/tfcpp/tfc/drinkable/mercury.json", mercury);
        addToTag("data/tfcpp/tags/fluid/castable_copper.json", "tfc:metal/copper", "tfcpp:metal/arsenical_copper");
        addToTag("data/c/tags/fluid/mercury.json", "tfcpp:mercury");
        addToTag("data/c/tags/item/ingots/arsenical_copper.json", "tfcpp:metal/ingot/arsenical_copper");
    }

    private void lootTables()
    {
        for (String block : List.of("retort", "retort_condenser", "converter"))
        {
            final JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", "tfcpp:" + block);
            final JsonArray entries = new JsonArray();
            entries.add(entry);

            final JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);
            pool.add("entries", entries);
            final JsonArray pools = new JsonArray();
            pools.add(pool);

            final JsonObject table = new JsonObject();
            table.addProperty("type", "minecraft:block");
            table.add("pools", pools);
            put("data/tfcpp/loot_table/blocks/" + block + ".json", table);
        }
    }

    /** What goes down a mine: the tags telling flames from safe lamps, the instruments and the dust chamber. */
    private void mining()
    {
        final List<String> flames = new ArrayList<>(List.of("tfc:firebox", "tfc:pit_kiln", "tfc:candle_cake", "#minecraft:candle_cakes"));
        for (DyeColor color : DyeColor.values())
        {
            flames.add("tfc:candle/" + color.getName());
            flames.add("tfc:candle_cake/" + color.getName());
        }
        addToTag("data/tfcpp/tags/block/open_flames_tfc.json", flames.toArray(String[]::new));
        addToTag("data/tfcpp/tags/block/open_flames.json",
            "#tfcpp:open_flames_tfc", "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch", "minecraft:fire", "minecraft:soul_fire", "minecraft:lava", "minecraft:campfire", "minecraft:soul_campfire", "#minecraft:candles",
            "tfc:torch", "tfc:wall_torch", "tfc:firepit", "tfc:grill", "tfc:pot", "tfc:stove", "tfc:stove_pot", "tfc:charcoal_forge", "tfc:burning_log_pile", "tfc:bloomery", "tfc:blast_furnace", "tfc:candle", "tfc:jack_o_lantern", "#tfc:lamps");
        addToTag("data/tfcpp/tags/item/open_flames.json", "minecraft:torch", "minecraft:soul_torch", "tfc:torch");
        addToTag("data/tfcpp/tags/entity_type/sentinel_animals.json", "tfc:rat", "tfc:quail");
        addToTag("data/tfc/tags/block/lamps.json", "tfcpp:davy_lamp");
        addToTag("data/minecraft/tags/block/mineable/pickaxe.json", "tfcpp:retort", "tfcpp:retort_condenser", "tfcpp:davy_lamp", "tfcpp:settling_baffle", "tfcpp:mine_fan", "tfcpp:converter", "#tfcpp:pipe_pickaxe");
        addToTag("data/c/tags/item/dusts/arsenic_trioxide.json", "tfcpp:powder/arsenic_trioxide");
        addToTag("data/c/tags/item/dusts/antimony_trioxide.json", "tfcpp:powder/antimony_trioxide");
        addToTag("data/c/tags/item/dusts/zinc_oxide.json", "tfcpp:powder/zinc_oxide");

        final JsonObject fan = shaped("tfcpp:mine_fan", "SMS", "S S", "SMS");
        fan.getAsJsonObject("key").add("S", item("tfc:metal/sheet/copper"));
        fan.getAsJsonObject("key").add("M", item("tfc:brass_mechanisms"));
        put("data/tfcpp/recipe/crafting/mine_fan.json", fan);

        final JsonObject gauze = shaped("tfcpp:wire_gauze", "RR", "RR");
        gauze.getAsJsonObject("key").add("R", item("tfc:metal/rod/copper"));
        put("data/tfcpp/recipe/crafting/wire_gauze.json", gauze);

        // Davy's lamp has no glass: the gauze is what surrounds the flame
        final JsonObject lamp = shaped("tfcpp:davy_lamp", "G", "L");
        lamp.getAsJsonObject("key").add("G", item("tfcpp:wire_gauze"));
        lamp.getAsJsonObject("key").add("L", item("tfc:metal/unfinished_lamp/copper"));
        put("data/tfcpp/recipe/crafting/davy_lamp.json", lamp);

        final JsonObject cage = shaped("tfcpp:animal_cage", "RRR", "R R", "RRR");
        cage.getAsJsonObject("key").add("R", item("tfc:metal/rod/copper"));
        put("data/tfcpp/recipe/crafting/animal_cage.json", cage);

        final JsonObject baffle = shaped("tfcpp:settling_baffle", "BSB", "BSB", "BSB");
        baffle.getAsJsonObject("key").add("B", item("tfc:ceramic/fire_brick"));
        baffle.getAsJsonObject("key").add("S", item("tfc:metal/sheet/copper"));
        put("data/tfcpp/recipe/crafting/settling_baffle.json", baffle);

        for (String block : List.of("settling_baffle", "mine_fan"))
        {
            final JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", "tfcpp:" + block);
            final JsonArray entries = new JsonArray();
            entries.add(entry);
            final JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);
            pool.add("entries", entries);
            final JsonArray pools = new JsonArray();
            pools.add(pool);
            final JsonObject table = new JsonObject();
            table.addProperty("type", "minecraft:block");
            table.add("pools", pools);
            put("data/tfcpp/loot_table/blocks/" + block + ".json", table);
        }

        // The same as a TFC lamp: it keeps the fuel inside when broken
        final JsonObject copy = new JsonObject();
        copy.addProperty("function", "tfc:copy_fluid");
        final JsonArray functions = new JsonArray();
        functions.add(copy);
        final JsonObject lampEntry = new JsonObject();
        lampEntry.addProperty("type", "minecraft:item");
        lampEntry.add("functions", functions);
        lampEntry.addProperty("name", "tfcpp:davy_lamp");
        final JsonArray lampEntries = new JsonArray();
        lampEntries.add(lampEntry);
        final JsonObject lampPool = new JsonObject();
        lampPool.addProperty("rolls", 1);
        lampPool.add("entries", lampEntries);
        final JsonArray lampPools = new JsonArray();
        lampPools.add(lampPool);
        final JsonObject lampTable = new JsonObject();
        lampTable.addProperty("type", "minecraft:block");
        lampTable.add("pools", lampPools);
        put("data/tfcpp/loot_table/blocks/davy_lamp.json", lampTable);
    }

    private void pipes()
    {
        final JsonObject wood = shaped("tfcpp:pipe/wood", "L", "L", "L");
        wood.getAsJsonObject("result").addProperty("count", 3);
        final JsonObject lumber = new JsonObject();
        lumber.addProperty("tag", "tfc:lumber");
        wood.getAsJsonObject("key").add("L", lumber);
        put("data/tfcpp/recipe/crafting/pipe/wood.json", wood);

        final JsonObject ceramic = shaped("tfcpp:pipe/ceramic", "B", "B", "B");
        ceramic.getAsJsonObject("result").addProperty("count", 3);
        ceramic.getAsJsonObject("key").add("B", item("tfc:ceramic/fire_brick"));
        put("data/tfcpp/recipe/crafting/pipe/ceramic.json", ceramic);

        for (String metal : List.of("copper", "wrought_iron", "steel"))
        {
            final JsonObject pipe = shaped("tfcpp:pipe/" + metal, "S", "S");
            pipe.getAsJsonObject("result").addProperty("count", 2);
            pipe.getAsJsonObject("key").add("S", item("tfc:metal/sheet/" + metal));
            put("data/tfcpp/recipe/crafting/pipe/" + metal + ".json", pipe);
        }

        fitting("pipe_valve", "tfc:brass_mechanisms", "tfc:metal/sheet/wrought_iron");
        fitting("pipe_vent", "tfc:metal/rod/wrought_iron", "tfc:metal/sheet/wrought_iron");
        fitting("pipe_tap", "tfc:metal/rod/copper", "tfc:metal/sheet/wrought_iron");
        fitting("safety_valve", "tfc:brass_mechanisms", "tfc:metal/ingot/wrought_iron");
        fitting("mercury_manometer", "tfcpp:mercury_bulb", "minecraft:glass_pane");
        fitting("piston_pump", "tfc:brass_mechanisms", "tfc:metal/double_sheet/wrought_iron");

        final JsonObject vessel = shaped("tfcpp:pressure_vessel", "SSS", "S S", "SSS");
        vessel.getAsJsonObject("key").add("S", item("tfc:metal/double_sheet/wrought_iron"));
        put("data/tfcpp/recipe/crafting/pressure_vessel.json", vessel);

        final List<String> blocks = new ArrayList<>(List.of("pipe_valve", "pipe_vent", "pipe_tap", "safety_valve", "mercury_manometer", "pressure_vessel", "piston_pump"));
        final List<String> pickaxe = new ArrayList<>();
        blocks.forEach(block -> pickaxe.add("tfcpp:" + block));
        for (String material : List.of("wood", "ceramic", "copper", "wrought_iron", "steel"))
        {
            blocks.add("pipe/" + material);
            if (!material.equals("wood"))
            {
                pickaxe.add("tfcpp:pipe/" + material);
            }
        }
        addToTag("data/tfcpp/tags/block/pipe_pickaxe.json", pickaxe.toArray(String[]::new));
        addToTag("data/minecraft/tags/block/mineable/axe.json", "tfcpp:pipe/wood");
        addToTag("data/tfc/tags/block/charcoal_forge_invisible.json", "tfcpp:retort", "tfcpp:pressure_vessel", "tfcpp:converter");

        for (String block : blocks)
        {
            final JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", "tfcpp:" + block);
            final JsonArray entries = new JsonArray();
            entries.add(entry);
            final JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);
            pool.add("entries", entries);
            final JsonArray pools = new JsonArray();
            pools.add(pool);
            final JsonObject table = new JsonObject();
            table.addProperty("type", "minecraft:block");
            table.add("pools", pools);
            put("data/tfcpp/loot_table/blocks/" + block + ".json", table);
        }
    }

    private void fitting(String name, String core, String body)
    {
        final JsonObject recipe = shaped("tfcpp:" + name, " C ", "BBB");
        recipe.getAsJsonObject("key").add("C", item(core));
        recipe.getAsJsonObject("key").add("B", item(body));
        put("data/tfcpp/recipe/crafting/" + name + ".json", recipe);
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

    private static Path tfcFile(String path)
    {
        return ModList.get().getModFileById("tfc").getFile().findResource(path);
    }

    private static JsonObject read(Path path) throws IOException
    {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static JsonObject heatingToFluid(JsonObject ingredient, String fluid, int amount, float temperature)
    {
        final JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "tfc:heating");
        recipe.add("ingredient", ingredient);
        recipe.add("result_fluid", fluidStack(fluid, amount));
        recipe.addProperty("temperature", temperature);
        return recipe;
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

    private static JsonObject fluid(String id, int amount)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("amount", amount);
        json.addProperty("fluid", id);
        return json;
    }

    private static JsonObject fluidStack(String id, int amount)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("amount", amount);
        json.addProperty("id", id);
        return json;
    }

    private static JsonObject gases(String id, float moles)
    {
        final JsonObject json = new JsonObject();
        json.addProperty(id, moles);
        return json;
    }

    @Override
    public String getName()
    {
        return "tfcpp metallurgy data";
    }
}
