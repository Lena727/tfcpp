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

import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.Pipes;

/**
 * Generates tfcpp's recipes and data, and its overrides of TFC's own. Where a whole family of TFC's recipes changes in the same way, this reads them from the TFC jar and patches them, so nothing is listed by hand and nothing gets missed.
 */
public final class MetallurgyData implements DataProvider
{
    private static final int[] ORE_MILLIBUCKETS = {10, 15, 25, 35};
    private static final String[] ORE_GRADES = {"small", "poor", "normal", "rich"};
    private static final float CINNABAR_MERCURY_MOLES = Sulfide.CINNABAR.metalMoles(25f);

    /** What the converter counts each grade as, and not below its liquidus: see {@link ConverterBath.Grade}. */
    private static final float CAST_IRON_MELT = ConverterBath.Grade.CAST_IRON.melt(), PIG_IRON_MELT = ConverterBath.Grade.PIG_IRON.melt();
    private static final float ORIGINAL_CAST_IRON_MELT = 1535f;
    /** Hematite melts at about 1565 C, and goes on being an oxide. */
    private static final float IRON_OXIDE_MELT = 1565f;
    private static final float STEEL_MELT = ConverterBath.Grade.STEEL.melt(), HIGH_CARBON_STEEL_MELT = ConverterBath.Grade.HIGH_CARBON_STEEL.melt(), ORIGINAL_STEEL_MELT = 1540f;
    /** A bloomery loses a third of its iron to slag. */
    private static final int BLOOMERY_CHARGE_PER_BLOOM = 150;
    private static final float GALENA_SMELTS = 800f;
    /** 2 PbO + C -> 2 Pb + CO2. A powder of litharge holds 0.25 mol of PbO, so it takes this much carbon and gives this much CO2. */
    private static final float LITHARGE_CARBON = 0.125f;
    /** Lead in one powder of litharge is 4.57 mL, and a tank holds whole millibuckets, so 4 mB come out at once and the retort keeps the 0.57 mL as liquid until a whole millibucket has gathered. */
    private static final int LITHARGE_LEAD_MILLIBUCKETS = 4;
    private static final float LITHARGE_LEAD_EXACT_MILLIBUCKETS = 0.25f / CupellationBath.MOLES_LEAD_PER_MILLIBUCKET;
    /** One piece of bloomery slag, in millibuckets of the iron oxide that it melts to. */
    private static final int OXIDE_PER_SLAG = IronSmelting.MILLIBUCKETS_PER_SLAG;
    /** Litharge is reduced by carbon above the melting point of lead, and well below the 1000 C that zinc needs. */
    private static final float LITHARGE_REDUCTION_TEMPERATURE = 800f;
    /** Bone meal burns to bone ash, the calcium phosphate of the bone, once its organic matter is gone. */
    private static final float BONE_ASH_TEMPERATURE = 700f;
    private static final float BURNT_DOLOMITE_TEMPERATURE = 1500f;
    /** Basic slag holds 15-20% P2O5, which is a good fertiliser and a quick one, unlike raw rock phosphate. */
    private static final float THOMAS_MEAL_PHOSPHORUS = 0.15f;
    /** Half of what bone meal gives, as most of it stays locked up in the soil. */
    private static final float APATITE_PHOSPHORUS = 0.05f;
    private static final float ORIGINAL_CERAMIC_TEMPERATURE = 1399f, POTTERY_TEMPERATURE = 850f, FIRE_CLAY_TEMPERATURE = 1300f;
    private static final Set<String> FIRE_CLAY = Set.of("ceramic/fire_brick.json", "ceramic/fire_ingot_mold.json", "crucible.json", "channel.json", "mold_table.json");
    private static final float STERLING_SILVER_MELT = 890f, ORIGINAL_STERLING_SILVER_MELT = 950f;
    private static final float WATER_QUENCH = 15f, OIL_QUENCH = 5f;
    private static final int TALLOW_MILLIBUCKETS = 400;
    /** What an ingot mould holds. */
    private static final int MILLIBUCKETS_PER_MOLD = 100;

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
            dirtierMixes();
            castOxide();
            lead();
            boneAsh();
            phosphoricIron();
            thomasProcess();
            apatite();
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

                }
            }
        }
        forgeArsenicalCopper();

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
     * Arsenical bronze tools were the first metal tools, hammered cold and hot, so arsenical copper can be forged and welded. It makes a double ingot, which is what a copper anvil is built from: without it a start on tetrahedrite alone would never leave the stone anvil.
     * <p>
     * A tool or sheet made of arsenical copper is the same item as one made of pure copper, so melting any of them down has to give the impure metal. Otherwise forging and remelting would remove arsenic for nothing, which takes electrolysis in reality. Only the ingots and double ingots, which are separate items, keep their own metal. Scrap copper being of unknown purity is also how it really behaves.
     */
    private void forgeArsenicalCopper() throws IOException
    {
        final String ingot = "tfcpp:metal/ingot/arsenical_copper", doubleIngot = "tfcpp:metal/double_ingot/arsenical_copper";

        final JsonObject doubleHeat = new JsonObject();
        doubleHeat.add("ingredient", item(doubleIngot));
        doubleHeat.addProperty("heat_capacity", MetalHeat.COPPER.ingotCapacity() * 2f);
        doubleHeat.addProperty("forging_temperature", 630f);
        doubleHeat.addProperty("welding_temperature", 840f);
        put("data/tfcpp/tfc/item_heat/arsenical_copper_double_ingot.json", doubleHeat);
        put("data/tfcpp/recipe/heating/arsenical_copper_double_ingot.json", heatingToFluid(item(doubleIngot), "tfcpp:metal/arsenical_copper", 200, 1050f));

        // Two ingots welded on the anvil, as TFC does for any metal
        final JsonObject weld = new JsonObject();
        weld.addProperty("type", "tfc:welding");
        weld.add("first_input", item(ingot));
        weld.add("second_input", item(ingot));
        weld.add("result", stack(doubleIngot, 1));
        put("data/tfcpp/recipe/welding/arsenical_copper_double_ingot.json", weld);

        // The copper anvil takes the double ingot of either. It is not put in the tag of copper's, as that would also hand it copper's own heat data and melting recipe, and the pure metal back.
        final JsonObject anvil = read(tfcFile("data/tfc/recipe/crafting/metal/anvil/copper.json"));
        anvil.getAsJsonObject("key").add("#", eitherOf(tagIngredient("c:double_ingots/copper"), item(doubleIngot)));
        put("data/tfc/recipe/crafting/metal/anvil/copper.json", anvil);

        // Whatever copper is hammered into at the anvil, arsenical copper is as well. The result is the copper item, and remelts as arsenical copper.
        final Path anvils = tfcFile("data/tfc/recipe/anvil");
        try (Stream<Path> paths = Files.walk(anvils))
        {
            for (Path path : (Iterable<Path>) paths::iterator)
            {
                final String relative = anvils.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".json"))
                {
                    final JsonObject recipe = read(path);
                    if (replaceCopperIngot(recipe, ingot, doubleIngot))
                    {
                        put("data/tfcpp/recipe/anvil/arsenical/" + relative, recipe);
                    }
                }
            }
        }

        final Path heating = tfcFile("data/tfc/recipe/heating/metal");
        try (Stream<Path> paths = Files.walk(heating))
        {
            for (Path path : (Iterable<Path>) paths::iterator)
            {
                final String relative = heating.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith("/copper.json") && !relative.startsWith("ingot/") && !relative.startsWith("double_ingot/"))
                {
                    final JsonObject recipe = read(path);
                    recipe.getAsJsonObject("result_fluid").addProperty("id", "tfcpp:metal/arsenical_copper");
                    put("data/tfc/recipe/heating/metal/" + relative, recipe);
                }
            }
        }

        addToTag("data/c/tags/item/double_ingots/arsenical_copper.json", doubleIngot);
        addToTag("data/c/tags/item/double_ingots.json", "#c:double_ingots/arsenical_copper"); // TFC sizes double ingots by this parent tag
    }

    private static JsonObject tagIngredient(String tag)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("tag", tag);
        return json;
    }

    private static JsonObject eitherOf(JsonObject... ingredients)
    {
        final JsonArray list = new JsonArray();
        for (JsonObject ingredient : ingredients)
        {
            list.add(ingredient);
        }
        final JsonObject json = new JsonObject();
        json.addProperty("type", "neoforge:compound");
        json.add("ingredients", list);
        return json;
    }

    /** Swaps the ingredients that are ingots or double ingots of copper for those of arsenical copper. @return {@code true} if anything was swapped */
    private static boolean replaceCopperIngot(JsonElement json, String ingot, String doubleIngot)
    {
        boolean replaced = false;
        if (json.isJsonArray())
        {
            for (JsonElement element : json.getAsJsonArray())
            {
                replaced |= replaceCopperIngot(element, ingot, doubleIngot);
            }
        }
        else if (json.isJsonObject())
        {
            final JsonObject object = json.getAsJsonObject();
            final String tag = object.has("tag") ? object.get("tag").getAsString() : "";
            if (tag.equals("c:ingots/copper") || tag.equals("c:double_ingots/copper"))
            {
                object.remove("tag");
                object.addProperty("item", tag.equals("c:ingots/copper") ? ingot : doubleIngot);
                return true;
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
            {
                replaced |= replaceCopperIngot(entry.getValue(), ingot, doubleIngot);
            }
        }
        return replaced;
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

        // 2 PbO + C -> 2 Pb + CO2. Lead does not boil at this heat, so it runs into the tank with no condenser and leaves no vapour.
        final JsonObject lead = new JsonObject();
        lead.addProperty("type", "tfcpp:retort");
        lead.add("input", item("tfcpp:powder/lead_oxide"));
        lead.addProperty("carbon", LITHARGE_CARBON);
        lead.addProperty("temperature", LITHARGE_REDUCTION_TEMPERATURE);
        lead.add("result", fluidStack("tfcpp:metal/lead", LITHARGE_LEAD_MILLIBUCKETS));
        lead.addProperty("result_millibuckets", LITHARGE_LEAD_EXACT_MILLIBUCKETS);
        lead.add("vapor", new JsonObject());
        lead.add("byproducts", gases("carbon_dioxide", LITHARGE_CARBON));
        lead.addProperty("needs_condenser", false);
        put("data/tfcpp/recipe/retort/lead.json", lead);

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

    /**
     * With no redstone in the world, the mechanisms that TFC built from it are built from brass clockwork instead, and so are those of the base game: the piston, the repeater, the comparator, the clock and the rest.
     * What would make redstone is gone too: its block would turn clockwork into dust, and no ore is left to smelt.
     */
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

        // The recipes of the base game, and TFC's own versions of some of them, which stand in for the base game's
        final Path vanilla = ModList.get().getModFileById("minecraft").getFile().findResource("data/minecraft/recipe");
        final Path overridden = tfcFile("data/minecraft/recipe");
        for (Path folder : List.of(vanilla, overridden))
        {
            try (Stream<Path> paths = Files.walk(folder))
            {
                for (Path path : (Iterable<Path>) paths::iterator)
                {
                    final String relative = folder.relativize(path).toString().replace('\\', '/');
                    if (relative.endsWith(".json") && (folder == overridden || !Files.exists(overridden.resolve(relative))))
                    {
                        final JsonObject recipe = read(path);
                        if (makesRedstone(recipe))
                        {
                            put("data/minecraft/recipe/" + relative, neverLoaded());
                        }
                        else if (replaceRedstone(recipe))
                        {
                            put("data/minecraft/recipe/" + relative, recipe);
                        }
                    }
                }
            }
        }
    }

    /** @return {@code true} if this recipe gives redstone or a block of it */
    private static boolean makesRedstone(JsonObject recipe)
    {
        if (!recipe.has("result") || !recipe.get("result").isJsonObject() || !recipe.getAsJsonObject("result").has("id"))
        {
            return false;
        }
        final String id = recipe.getAsJsonObject("result").get("id").getAsString();
        return id.equals("minecraft:redstone") || id.equals("minecraft:redstone_block");
    }

    /** A recipe that is never loaded, which is how a datapack removes one. */
    private static JsonObject neverLoaded()
    {
        final JsonObject disabled = new JsonObject();
        final JsonArray conditions = new JsonArray();
        final JsonObject never = new JsonObject();
        never.addProperty("type", "neoforge:false");
        conditions.add(never);
        disabled.add("neoforge:conditions", conditions);
        return disabled;
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

    /** @return where this iron melts: carbon lowers the melting point of iron by close to 400 C, which is why cast iron could flow from a furnace when pure iron could not */
    private static float meltOf(String name)
    {
        return name.contains("pig_iron") ? PIG_IRON_MELT : CAST_IRON_MELT;
    }

    /** Pig iron melts at the 4% carbon of a blast furnace tap, and cast iron at its lightest 2.1%: each at the liquidus of what it counts as. */
    private void ironTemperatures() throws IOException
    {
        for (String metal : List.of("cast_iron", "pig_iron"))
        {
            final JsonObject heat = read(tfcFile("data/tfc/tfc/fluid_heat/" + metal + ".json"));
            heat.addProperty("melt_temperature", meltOf(metal));
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
                            definition.addProperty(field, definition.get(field).getAsFloat() * meltOf(relative) / ORIGINAL_CAST_IRON_MELT);
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
                    recipe.addProperty("temperature", meltOf(relative));
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
        final JsonObject phosphoricOxide = oxide.deepCopy();
        phosphoricOxide.addProperty("fluid", "tfcpp:metal/phosphoric_iron_oxide");
        put("data/tfcpp/tfc/fluid_heat/phosphoric_iron_oxide.json", phosphoricOxide);

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
        // A bloomery's slag takes the phosphorus out of the iron, so a charge of either ore, or both, gives the same ordinary bloom
        final JsonObject bloomeryCharge = new JsonObject();
        bloomeryCharge.addProperty("type", "neoforge:compound");
        final JsonArray oxides = new JsonArray();
        oxides.add(fluidIngredient("tfcpp:metal/iron_oxide"));
        oxides.add(fluidIngredient("tfcpp:metal/phosphoric_iron_oxide"));
        bloomeryCharge.add("ingredients", oxides);
        bloomeryCharge.addProperty("amount", BLOOMERY_CHARGE_PER_BLOOM);
        bloomery.add("fluid", bloomeryCharge);
        put("data/tfc/recipe/bloomery/raw_iron_bloom.json", bloomery);

        // A blast furnace hearth is liquid iron, and iron dissolves the phosphorus of any ore in it. Which recipe applies is up to the oxide that melted, and a mixed charge is merged to the phosphoric one: see IronBlend.
        final JsonObject blastFurnace = read(tfcFile("data/tfc/recipe/blast_furnace/pig_iron.json"));
        blastFurnace.add("fluid", fluid("tfcpp:metal/iron_oxide", 1));
        put("data/tfc/recipe/blast_furnace/pig_iron.json", blastFurnace);
        final JsonObject phosphoricBlastFurnace = blastFurnace.deepCopy();
        phosphoricBlastFurnace.add("fluid", fluid("tfcpp:metal/phosphoric_iron_oxide", 1));
        phosphoricBlastFurnace.add("result", fluidStack("tfcpp:metal/phosphoric_pig_iron", 1));
        put("data/tfcpp/recipe/blast_furnace/phosphoric_pig_iron.json", phosphoricBlastFurnace);

        // Slag from a bloomery still holds its iron, and goes back in a furnace
        put("data/tfcpp/recipe/heating/iron_slag.json", heatingToFluid(item("tfcpp:slag/iron"), "tfcpp:metal/iron_oxide", IronSmelting.MILLIBUCKETS_PER_SLAG, IRON_OXIDE_MELT));
        final JsonObject slagHeat = new JsonObject();
        slagHeat.add("ingredient", item("tfcpp:slag/iron"));
        slagHeat.addProperty("heat_capacity", 1.1428572f);
        put("data/tfcpp/tfc/item_heat/iron_slag.json", slagHeat);

        // And phosphoric slag goes back as the phosphoric oxide, so that it cannot be used to clean the phosphorus out of anything
        put("data/tfcpp/recipe/heating/phosphoric_iron_slag.json", heatingToFluid(item("tfcpp:slag/phosphoric_iron"), "tfcpp:metal/phosphoric_iron_oxide", IronSmelting.MILLIBUCKETS_PER_SLAG, IRON_OXIDE_MELT));
        final JsonObject phosphoricSlagHeat = new JsonObject();
        phosphoricSlagHeat.add("ingredient", item("tfcpp:slag/phosphoric_iron"));
        phosphoricSlagHeat.addProperty("heat_capacity", 1.1428572f);
        put("data/tfcpp/tfc/item_heat/phosphoric_iron_slag.json", phosphoricSlagHeat);

        // A hammer cannot take carbon out of iron. That takes oxygen.
        final JsonObject disabled = new JsonObject();
        final JsonArray conditions = new JsonArray();
        final JsonObject never = new JsonObject();
        never.addProperty("type", "neoforge:false");
        conditions.add(never);
        disabled.add("neoforge:conditions", conditions);
        put("data/tfc/recipe/anvil/metal/ingot/high_carbon_steel.json", disabled);
        put("data/tfc/recipe/anvil/metal/ingot/steel.json", disabled);

        // Cast iron is brittle: a hammer breaks it, and a weld needs a metal that yields. It is cast, as phosphoric iron is. The rods and sheets that TFC forged from it for its stove, door and shelf are forged from wrought iron instead.
        put("data/tfc/recipe/anvil/metal/rod/cast_iron.json", disabled);
        put("data/tfc/recipe/anvil/metal/sheet/cast_iron.json", disabled);
        put("data/tfc/recipe/welding/metal/double_ingot/cast_iron.json", disabled);
        put("data/tfc/recipe/welding/metal/double_sheet/cast_iron.json", disabled);
        for (String recipe : List.of("stove", "fireproof_door", "fire_brick_shelf"))
        {
            final JsonObject patched = read(tfcFile("data/tfc/recipe/crafting/" + recipe + ".json"));
            for (Map.Entry<String, JsonElement> entry : patched.getAsJsonObject("key").entrySet())
            {
                final JsonObject ingredient = entry.getValue().getAsJsonObject();
                if (ingredient.has("tag") && ingredient.get("tag").getAsString().endsWith("/cast_iron"))
                {
                    ingredient.addProperty("tag", ingredient.get("tag").getAsString().replace("/cast_iron", "/wrought_iron"));
                }
            }
            put("data/tfc/recipe/crafting/" + recipe + ".json", patched);
        }

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

        final JsonObject hearth = shaped("tfcpp:cupel_hearth", "F F", "FFF");
        hearth.getAsJsonObject("key").add("F", item("tfc:ceramic/fire_brick"));
        put("data/tfcpp/recipe/crafting/cupel_hearth.json", hearth);

        // Fire-brick walls for the gas to cool against, and lead sheet on the joints between them: lead is what the fume cannot eat
        final JsonObject tower = shaped("tfcpp:condensation_tower", "BLB", "L L", "BLB");
        tower.getAsJsonObject("key").add("B", item("tfc:ceramic/fire_brick"));
        tower.getAsJsonObject("key").add("L", item("tfcpp:metal/sheet/lead"));
        put("data/tfcpp/recipe/crafting/condensation_tower.json", tower);
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
                    // Brown iron ore carries phosphorus, 0.5-2%. Hematite and magnetite are poor in it.
                    result.addProperty("id", relative.contains("limonite") ? "tfcpp:metal/phosphoric_iron_oxide" : "tfcpp:metal/iron_oxide");
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

    /**
     * Iron oxide that is melted with nothing to reduce it can be cast into slag, which is what it is: the pieces take 25 mB each and melt back to the same oxide.
     * Otherwise it could not be got out of a crucible or a vessel, and was lost in a forge.
     */
    private void castOxide() throws IOException
    {
        for (String kind : List.of("iron", "phosphoric_iron"))
        {
            final String oxide = "tfcpp:metal/" + kind + "_oxide", slag = "tfcpp:slag/" + kind;
            final JsonObject casting = new JsonObject();
            casting.addProperty("type", "tfc:casting");
            casting.addProperty("break_chance", 0.1f);
            casting.add("fluid", fluid(oxide, MILLIBUCKETS_PER_MOLD));
            casting.add("mold", item("tfc:ceramic/ingot_mold"));
            casting.add("result", stack(slag, MILLIBUCKETS_PER_MOLD / OXIDE_PER_SLAG));
            put("data/tfcpp/recipe/casting/" + kind + "_oxide_slag.json", casting);

            final JsonObject fire = read(tfcFile("data/tfc/recipe/casting/copper_fire_ingot.json"));
            final int amount = fire.getAsJsonObject("fluid").get("amount").getAsInt();
            if (amount % OXIDE_PER_SLAG != 0)
            {
                throw new IOException("A fire ingot mould holds " + amount + " mB, which is not whole pieces of slag");
            }
            fire.add("fluid", fluid(oxide, amount));
            fire.add("result", stack(slag, amount / OXIDE_PER_SLAG));
            put("data/tfcpp/recipe/casting/" + kind + "_oxide_slag_fire.json", fire);
        }
    }

    /**
     * Mixes that TFC's crucible takes for an unknown metal, though they only ever give one thing. A crucible knows only what is in it, not how much of an impurity, so the mix is what its dirtier part makes it, in any share:
     * arsenic cannot be diluted out of copper nor silver out of work lead, and anything that holds phosphorus is cold short. Where the iron grades meet, the result is the one that the least carbon of each makes, as the converter counts them, so no mix gives more carbon than it was given.
     * <p>
     * TFC drops the result of an alloy from the contents before it matches the rest, so a mix whose result is one of its parts is written with the other part alone, for any share.
     */
    private void dirtierMixes()
    {
        final String copper = "tfc:metal/copper", arsenical = "tfcpp:metal/arsenical_copper", lead = "tfcpp:metal/lead", workLead = "tfcpp:metal/work_lead";
        final String wrought = "tfc:metal/wrought_iron", steel = "tfc:metal/steel", highCarbon = "tfc:metal/high_carbon_steel", cast = "tfc:metal/cast_iron", pig = "tfc:metal/pig_iron";
        final String phosphoricPig = "tfcpp:metal/phosphoric_pig_iron", coldShort = "tfcpp:metal/cold_short_iron";

        // Arsenical copper and work lead stay what they are, whatever is added to them
        anyShare("copper_with_arsenical_copper", arsenical, copper);
        anyShare("lead_with_work_lead", workLead, lead);

        // Iron by its carbon, counted at the least each holds: 4.0 pig iron, 2.1 cast iron, 1.0 high-carbon steel, 0.2 steel, 0.03 wrought iron
        anyShare("pig_iron_with_cast_iron", cast, pig);
        anyShare("cast_iron_with_high_carbon_steel", highCarbon, cast);
        anyShare("high_carbon_steel_with_steel", steel, highCarbon);
        anyShare("steel_with_wrought_iron", wrought, steel);

        // Pig iron that carries phosphorus stays it when pig iron is added. Below 4% carbon, phosphorus makes iron cold short, and cold short iron stays so.
        anyShare("pig_iron_with_phosphoric_pig_iron", phosphoricPig, pig);
        for (String clean : List.of(cast, highCarbon, steel, wrought))
        {
            twoShares(clean.substring(clean.lastIndexOf('/') + 1) + "_with_phosphoric_pig_iron", coldShort, clean, phosphoricPig);
        }
        for (String other : List.of(pig, cast, highCarbon, steel, wrought, phosphoricPig))
        {
            anyShare(other.substring(other.lastIndexOf('/') + 1) + "_with_cold_short_iron", coldShort, other);
        }
    }

    /** A mix of the result and this one other metal, in any share, for which TFC matches the other metal alone. */
    private void anyShare(String name, String result, String other)
    {
        final JsonArray contents = new JsonArray();
        contents.add(range(other, 0f, 1f));
        alloy(name, result, contents);
    }

    /** A mix of two metals, neither of them the result, from a hundredth of either to ninety-nine. */
    private void twoShares(String name, String result, String first, String second)
    {
        final JsonArray contents = new JsonArray();
        contents.add(range(first, 0.01f, 0.99f));
        contents.add(range(second, 0.01f, 0.99f));
        alloy(name, result, contents);
    }

    private static JsonObject range(String fluid, float min, float max)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("fluid", fluid);
        json.addProperty("max", max);
        json.addProperty("min", min);
        return json;
    }

    private void alloy(String name, String result, JsonArray contents)
    {
        final JsonObject alloy = new JsonObject();
        alloy.addProperty("type", "tfc:alloy");
        alloy.add("contents", contents);
        alloy.addProperty("result", result);
        put("data/tfcpp/recipe/alloy/" + name + ".json", alloy);
    }

    /**
     * Galena gives up lead readily: it needs only a fire well short of copper's, and no carbon, as part of the sulfide roasting to oxide then reacts with the rest. What it gives is work lead, which holds silver. Plain lead comes only from reducing litharge in a retort, and cupellation takes the silver out of work lead.
     */
    private void lead() throws IOException
    {
        final JsonObject fluidHeat = new JsonObject();
        fluidHeat.addProperty("fluid", "tfcpp:metal/lead");
        fluidHeat.addProperty("melt_temperature", MetalHeat.LEAD.melt());
        fluidHeat.addProperty("specific_heat_capacity", MetalHeat.LEAD.liquidCapacity());
        put("data/tfcpp/tfc/fluid_heat/lead.json", fluidHeat);

        final JsonObject ingotHeat = new JsonObject();
        ingotHeat.add("ingredient", item("tfcpp:metal/ingot/lead"));
        ingotHeat.addProperty("heat_capacity", MetalHeat.LEAD.ingotCapacity());
        put("data/tfcpp/tfc/item_heat/lead_ingot.json", ingotHeat);

        put("data/tfcpp/recipe/heating/lead_ingot.json", heatingToFluid(item("tfcpp:metal/ingot/lead"), "tfcpp:metal/lead", 100, MetalHeat.LEAD.melt()));
        // A sheet is the metal of one ingot, so it melts back to the same 100 mB
        put("data/tfcpp/recipe/heating/lead_sheet.json", heatingToFluid(item("tfcpp:metal/sheet/lead"), "tfcpp:metal/lead", 100, MetalHeat.LEAD.melt()));
        // Work lead beaten flat is work lead still, with its silver, and runs back to it. It is no way to clean it, and no lead pipe is made of it.
        put("data/tfcpp/recipe/heating/work_lead_sheet.json", heatingToFluid(item("tfcpp:metal/sheet/work_lead"), "tfcpp:metal/work_lead", 100, MetalHeat.LEAD.melt()));

        final JsonObject casting = new JsonObject();
        casting.addProperty("type", "tfc:casting");
        casting.addProperty("break_chance", 0.1f);
        casting.add("fluid", fluid("tfcpp:metal/lead", 100));
        casting.add("mold", item("tfc:ceramic/ingot_mold"));
        casting.add("result", stack("tfcpp:metal/ingot/lead", 1));
        put("data/tfcpp/recipe/casting/lead_ingot.json", casting);

        // The fire ingot mold takes the same amount as the ceramic one, and TFC's own recipe for copper says how much to break
        final JsonObject fireIngot = read(tfcFile("data/tfc/recipe/casting/copper_fire_ingot.json"));
        fireIngot.add("fluid", fluid("tfcpp:metal/lead", fireIngot.getAsJsonObject("fluid").get("amount").getAsInt()));
        fireIngot.add("result", stack("tfcpp:metal/ingot/lead", 1));
        put("data/tfcpp/recipe/casting/lead_fire_ingot.json", fireIngot);

        // Galena gives work lead, which goes through the same ingot path as lead, and is cast and melted in the same way
        castableIngot("work_lead", MetalHeat.LEAD);
        final String[] pieces = {"tfcpp:ore/small_galena", "tfcpp:ore/poor_galena", "tfcpp:ore/normal_galena", "tfcpp:ore/rich_galena"};
        for (int i = 0; i < pieces.length; i++)
        {
            put("data/tfcpp/recipe/heating/ore/" + ORE_GRADES[i] + "_galena.json", heatingToFluid(item(pieces[i]), "tfcpp:metal/work_lead", ORE_MILLIBUCKETS[i], GALENA_SMELTS));
        }

        // Lead in the cold: lead recrystallises at room temperature, so it has no forging temperature and any heat will do to beat it.
        // A sheet is the metal of one ingot here, and not TFC's double ingot, so that the recipe does not make metal out of nothing.
        final JsonObject sheetHeat = new JsonObject();
        sheetHeat.add("ingredient", item("tfcpp:metal/sheet/lead"));
        sheetHeat.addProperty("heat_capacity", MetalHeat.LEAD.ingotCapacity());
        sheetHeat.addProperty("forging_temperature", 0f);
        put("data/tfcpp/tfc/item_heat/lead_sheet.json", sheetHeat);
        final JsonObject workSheetHeat = sheetHeat.deepCopy();
        workSheetHeat.add("ingredient", item("tfcpp:metal/sheet/work_lead"));
        put("data/tfcpp/tfc/item_heat/work_lead_sheet.json", workSheetHeat);

        for (String ingot : List.of("lead", "work_lead"))
        {
            final JsonObject sheet = new JsonObject();
            sheet.addProperty("type", "tfc:anvil");
            sheet.add("ingredient", item("tfcpp:metal/ingot/" + ingot));
            sheet.add("result", stack("tfcpp:metal/sheet/" + ingot, 1));
            final JsonArray rules = new JsonArray();
            rules.add("hit_last");
            rules.add("hit_second_last");
            rules.add("hit_third_last");
            sheet.add("rules", rules);
            sheet.addProperty("tier", 0); // Any anvil will do
            put("data/tfcpp/recipe/anvil/" + ingot + "_sheet.json", sheet);
        }

        addToTag("data/c/tags/item/ingots/lead.json", "tfcpp:metal/ingot/lead");
        addToTag("data/c/tags/fluid/lead.json", "tfcpp:metal/lead");
        addToTag("data/c/tags/item/dusts/litharge.json", "tfcpp:powder/lead_oxide");
    }

    /** Bone meal burned to bone ash, which is what a cupellation hearth is floored with. Bone meal has no heat of its own in TFC, so it gets one as the powders do. */
    private void boneAsh()
    {
        final JsonObject heat = new JsonObject();
        heat.add("ingredient", item("minecraft:bone_meal"));
        heat.addProperty("heat_capacity", 0.7f); // As TFC's own ground limestone
        put("data/tfcpp/tfc/item_heat/bone_meal.json", heat);

        final JsonObject burn = new JsonObject();
        burn.addProperty("type", "tfc:heating");
        burn.add("ingredient", item("minecraft:bone_meal"));
        burn.add("result_item", stack("tfcpp:powder/bone_ash", 1));
        burn.addProperty("temperature", BONE_ASH_TEMPERATURE);
        put("data/tfcpp/recipe/heating/bone_ash.json", burn);
    }

    /**
     * Phosphoric pig iron and cold short iron. They can be melted, poured and cast, and nothing more: no anvil recipe is written for either, and the alloy recipes of wrought iron and steel name only the fluids they take, so these cannot be mixed into them.
     */
    private void phosphoricIron() throws IOException
    {
        castableIngot("phosphoric_pig_iron", MetalHeat.PHOSPHORIC_PIG_IRON);
        castableIngot("cold_short_iron", MetalHeat.COLD_SHORT_IRON);
    }

    /** A metal that is only ever cast: a fluid, its heat, an ingot, and the way between the two. */
    private void castableIngot(String name, MetalHeat.Metal heat) throws IOException
    {
        final String fluidId = "tfcpp:metal/" + name, ingotId = "tfcpp:metal/ingot/" + name;

        final JsonObject fluidHeat = new JsonObject();
        fluidHeat.addProperty("fluid", fluidId);
        fluidHeat.addProperty("melt_temperature", heat.melt());
        fluidHeat.addProperty("specific_heat_capacity", heat.liquidCapacity());
        put("data/tfcpp/tfc/fluid_heat/" + name + ".json", fluidHeat);

        final JsonObject ingotHeat = new JsonObject();
        ingotHeat.add("ingredient", item(ingotId));
        ingotHeat.addProperty("heat_capacity", heat.ingotCapacity());
        put("data/tfcpp/tfc/item_heat/" + name + "_ingot.json", ingotHeat);

        put("data/tfcpp/recipe/heating/" + name + "_ingot.json", heatingToFluid(item(ingotId), fluidId, 100, heat.melt()));

        final JsonObject casting = new JsonObject();
        casting.addProperty("type", "tfc:casting");
        casting.addProperty("break_chance", 0.1f);
        casting.add("fluid", fluid(fluidId, 100));
        casting.add("mold", item("tfc:ceramic/ingot_mold"));
        casting.add("result", stack(ingotId, 1));
        put("data/tfcpp/recipe/casting/" + name + "_ingot.json", casting);

        final JsonObject fireIngot = read(tfcFile("data/tfc/recipe/casting/copper_fire_ingot.json"));
        fireIngot.add("fluid", fluid(fluidId, fireIngot.getAsJsonObject("fluid").get("amount").getAsInt()));
        fireIngot.add("result", stack(ingotId, 1));
        put("data/tfcpp/recipe/casting/" + name + "_fire_ingot.json", fireIngot);

        addToTag("data/c/tags/item/ingots/" + name + ".json", ingotId);
        addToTag("data/c/tags/fluid/" + name + ".json", fluidId);
    }

    /**
     * Thomas and Gilchrist's basic lining: dolomite ground, burned hard until it has lost its CO2 and no longer slakes, then pressed with tar into bricks. The slag it makes, with its lime, is full of phosphate and is ground for fields.
     */
    private void thomasProcess()
    {
        final JsonObject grindDolomite = new JsonObject();
        grindDolomite.addProperty("type", "tfc:quern");
        grindDolomite.add("ingredient", item("tfc:rock/cobble/dolomite"));
        grindDolomite.add("result", stack("tfcpp:powder/dolomite", 2));
        put("data/tfcpp/recipe/quern/dolomite.json", grindDolomite);

        for (String powder : List.of("dolomite", "burnt_dolomite"))
        {
            final JsonObject heat = new JsonObject();
            heat.add("ingredient", item("tfcpp:powder/" + powder));
            heat.addProperty("heat_capacity", 0.7f); // As TFC's own ground limestone
            put("data/tfcpp/tfc/item_heat/" + powder + ".json", heat);
        }

        // CaMg(CO3)2 -> CaO.MgO + 2 CO2: dead-burned dolomite is made at some 1500 C, and a pit or a bloomery cannot reach it
        final JsonObject burn = new JsonObject();
        burn.addProperty("type", "tfc:heating");
        burn.add("ingredient", item("tfcpp:powder/dolomite"));
        burn.add("result_item", stack("tfcpp:powder/burnt_dolomite", 1));
        burn.addProperty("temperature", BURNT_DOLOMITE_TEMPERATURE);
        put("data/tfcpp/recipe/heating/burnt_dolomite.json", burn);

        final JsonObject brick = new JsonObject();
        brick.addProperty("type", "tfc:barrel_instant");
        brick.add("input_item", sized("tfcpp:powder/burnt_dolomite", 4));
        brick.add("input_fluid", fluid("tfcpp:coal_tar", 1));
        brick.add("output_item", stack("tfcpp:basic_brick", 1));
        put("data/tfcpp/recipe/barrel/basic_brick.json", brick);

        final JsonObject grindSlag = new JsonObject();
        grindSlag.addProperty("type", "tfc:quern");
        grindSlag.add("ingredient", item("tfcpp:slag/thomas"));
        grindSlag.add("result", stack("tfcpp:powder/thomas_meal", 4));
        put("data/tfcpp/recipe/quern/thomas_meal.json", grindSlag);

        final JsonObject fertilizer = new JsonObject();
        fertilizer.add("ingredient", item("tfcpp:powder/thomas_meal"));
        fertilizer.addProperty("phosphorus", THOMAS_MEAL_PHOSPHORUS);
        put("data/tfcpp/tfc/fertilizer/thomas_meal.json", fertilizer);

        addToTag("data/c/tags/item/dusts/dolomite.json", "tfcpp:powder/dolomite");
    }

    /** Rock phosphate is a real fertiliser, though a slow one. Superphosphate waits for sulfuric acid. */
    private void apatite()
    {
        final JsonObject quern = new JsonObject();
        quern.addProperty("type", "tfc:quern");
        quern.add("ingredient", item("tfcpp:ore/apatite"));
        quern.add("result", stack("tfcpp:powder/apatite", 4));
        put("data/tfcpp/recipe/quern/apatite.json", quern);

        final JsonObject fertilizer = new JsonObject();
        fertilizer.add("ingredient", item("tfcpp:powder/apatite"));
        fertilizer.addProperty("phosphorus", APATITE_PHOSPHORUS);
        put("data/tfcpp/tfc/fertilizer/apatite.json", fertilizer);
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
        fuel("charcoal", 1100f, null);
        fuel("coal", 1200f, null);
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
        addToTag("data/tfc/tags/fluid/molten_metals.json", "tfcpp:metal/arsenical_copper", "tfcpp:metal/lead", "tfcpp:metal/work_lead", "tfcpp:metal/phosphoric_pig_iron", "tfcpp:metal/cold_short_iron");
        // Iron oxide is not a metal, and goes in no other tag of metals, but an ingot mould takes it: it sets into pieces of slag that go back in a furnace. See castOxide.
        addToTag("data/tfc/tags/fluid/usable_in_ingot_mold.json", "tfcpp:metal/arsenical_copper", "tfcpp:metal/iron_oxide", "tfcpp:metal/phosphoric_iron_oxide");
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
        addToTag("data/c/tags/item/ingots.json", "#c:ingots/lead", "#c:ingots/work_lead", "#c:ingots/arsenical_copper", "#c:ingots/phosphoric_pig_iron", "#c:ingots/cold_short_iron"); // TFC sizes ingots by this parent tag, and without it they would count as tiny items that stack to 64
    }

    private void lootTables()
    {
        for (String block : List.of("retort", "retort_condenser", "converter", "cupel_hearth", "condensation_tower", "coke_oven", "gas_burner", "lead_chamber", "sulfur_burner", "glass_retort", "sand_bath", "gold_leaf_indicator"))
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
            "tfc:torch", "tfc:wall_torch", "tfc:firepit", "tfc:grill", "tfc:pot", "tfc:stove", "tfc:stove_pot", "tfc:charcoal_forge", "tfc:burning_log_pile", "tfc:bloomery", "tfc:blast_furnace", "tfc:candle", "tfc:jack_o_lantern", "#tfc:lamps", "tfcpp:gas_burner");
        addToTag("data/tfcpp/tags/item/open_flames.json", "minecraft:torch", "minecraft:soul_torch", "tfc:torch");
        addToTag("data/tfcpp/tags/entity_type/sentinel_animals.json", "tfc:rat", "tfc:quail");
        addToTag("data/tfc/tags/block/lamps.json", "tfcpp:davy_lamp");
        addToTag("data/minecraft/tags/block/mineable/pickaxe.json", "tfcpp:retort", "tfcpp:retort_condenser", "tfcpp:davy_lamp", "tfcpp:settling_baffle", "tfcpp:mine_fan", "tfcpp:converter", "tfcpp:cupel_hearth", "tfcpp:condensation_tower", "tfcpp:coke_oven", "tfcpp:gas_burner", "tfcpp:lead_chamber", "tfcpp:sulfur_burner", "tfcpp:glass_retort", "tfcpp:sand_bath", "#tfcpp:pipe_pickaxe", "#tfcpp:ore_pickaxe");
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

        // A sheet of gold beaten out into leaves, eight to the sheet, as gold beaters did for the leaves of an electroscope
        final JsonObject leaf = shaped("tfcpp:gold_leaf_indicator", "S");
        leaf.getAsJsonObject("key").add("S", item("tfc:metal/sheet/gold"));
        leaf.add("result", stack("tfcpp:gold_leaf_indicator", 8));
        put("data/tfcpp/recipe/crafting/gold_leaf_indicator.json", leaf);

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

        // Lead pipe is the same shape as copper's, from the lead sheet this mod makes
        final JsonObject leadPipe = shaped("tfcpp:pipe/lead", "S", "S");
        leadPipe.getAsJsonObject("result").addProperty("count", 2);
        leadPipe.getAsJsonObject("key").add("S", item("tfcpp:metal/sheet/lead"));
        put("data/tfcpp/recipe/crafting/pipe/lead.json", leadPipe);

        fitting("pipe_valve", "tfc:brass_mechanisms", "tfc:metal/sheet/wrought_iron");
        fitting("pipe_vent", "tfc:metal/rod/wrought_iron", "tfc:metal/sheet/wrought_iron");
        fitting("pipe_tap", "tfc:metal/rod/copper", "tfc:metal/sheet/wrought_iron");
        fitting("safety_valve", "tfc:brass_mechanisms", "tfc:metal/ingot/wrought_iron");
        // Lead and stoneware bodies. What the acid touches is of the body's own material, and so is the plug of a vent and a tap. The mechanism of a valve works outside, in the dry, and stays brass.
        fitting("pipe_valve_lead", "tfc:brass_mechanisms", "tfcpp:metal/sheet/lead");
        fitting("pipe_vent_lead", "tfcpp:metal/ingot/lead", "tfcpp:metal/sheet/lead");
        fitting("pipe_tap_lead", "tfcpp:metal/ingot/lead", "tfcpp:metal/sheet/lead");
        fitting("safety_valve_lead", "tfc:brass_mechanisms", "tfcpp:metal/ingot/lead");
        fitting("pipe_valve_ceramic", "tfc:brass_mechanisms", "tfc:ceramic/fire_brick");
        fitting("pipe_vent_ceramic", "tfc:ceramic/fire_brick", "tfc:ceramic/fire_brick");
        fitting("pipe_tap_ceramic", "tfc:ceramic/fire_brick", "tfc:ceramic/fire_brick");
        fitting("safety_valve_ceramic", "tfc:brass_mechanisms", "tfc:ceramic/fire_brick");
        fitting("mercury_manometer", "tfcpp:mercury_bulb", "minecraft:glass_pane");
        fitting("piston_pump", "tfc:brass_mechanisms", "tfc:metal/double_sheet/wrought_iron");

        final JsonObject vessel = shaped("tfcpp:pressure_vessel", "SSS", "S S", "SSS");
        vessel.getAsJsonObject("key").add("S", item("tfc:metal/double_sheet/wrought_iron"));
        put("data/tfcpp/recipe/crafting/pressure_vessel.json", vessel);

        final List<String> blocks = new ArrayList<>(List.of("mercury_manometer", "pressure_vessel", "piston_pump"));
        for (String fitting : List.of("pipe_valve", "pipe_vent", "pipe_tap", "safety_valve"))
        {
            for (PipeMaterial material : Pipes.FITTING_MATERIALS)
            {
                blocks.add(Pipes.fittingName(fitting, material));
            }
        }
        final List<String> pickaxe = new ArrayList<>();
        blocks.forEach(block -> pickaxe.add("tfcpp:" + block));
        for (String material : List.of("wood", "ceramic", "copper", "lead", "wrought_iron", "steel"))
        {
            blocks.add("pipe/" + material);
            if (!material.equals("wood"))
            {
                pickaxe.add("tfcpp:pipe/" + material);
            }
        }
        addToTag("data/tfcpp/tags/block/pipe_pickaxe.json", pickaxe.toArray(String[]::new));
        addToTag("data/minecraft/tags/block/mineable/axe.json", "tfcpp:pipe/wood");
        addToTag("data/tfc/tags/block/charcoal_forge_invisible.json", "tfcpp:retort", "tfcpp:pressure_vessel", "tfcpp:converter", "tfcpp:cupel_hearth", "tfcpp:coke_oven", "tfcpp:sand_bath", "tfcpp:glass_retort");

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

    /** A fluid ingredient with no amount, for use among others in a compound one. */
    private static JsonObject fluidIngredient(String id)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("fluid", id);
        return json;
    }

    /** An item ingredient that wants several of itself. */
    private static JsonObject sized(String id, int count)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("count", count);
        json.addProperty("item", id);
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
