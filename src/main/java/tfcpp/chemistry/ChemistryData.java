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
    /** Fertiliser values on the scale of TFC's: apatite 0.05 and Thomas meal 0.15 are the raw and the slag. */
    static final float SUPERPHOSPHATE_PHOSPHORUS = 0.12f, RAW_APATITE_PHOSPHORUS = 0.05f;
    private static final float AMMONIUM_SULFATE_NITROGEN = 0.1f;

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
        distillation();
        glass();

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

    /** The recipes of the glass retort, from {@link DistillationTable}. */
    private void distillation()
    {
        for (DistillationTable.Row row : DistillationTable.ROWS)
        {
            final JsonObject recipe = new JsonObject();
            recipe.addProperty("type", "tfcpp:glass_distillation");
            if (row.item() != null)
            {
                recipe.add("item", item(row.item()));
                recipe.addProperty("items", row.items());
            }
            recipe.add("fluid", condensate(row.fluid(), row.litres()));
            if (row.reagent() != null)
            {
                recipe.add("reagent", condensate(row.reagent(), row.reagentLitres()));
            }
            recipe.addProperty("min_temperature", row.minTemperature());
            recipe.addProperty("max_temperature", row.maxTemperature());
            recipe.addProperty("seconds", row.seconds());
            if (row.output() != null)
            {
                recipe.add("output", condensate(row.output(), row.outputLitres()));
            }
            if (row.residue() != null)
            {
                recipe.add("residue", condensate(row.residue(), row.residueLitres()));
            }
            if (row.receiverWater() > 0f)
            {
                recipe.addProperty("receiver_water", row.receiverWater());
            }
            final JsonObject gas = new JsonObject();
            for (Gas species : Gas.VALUES)
            {
                final Float moles = row.gas().get(species);
                if (moles != null)
                {
                    gas.addProperty(species.id(), moles);
                }
            }
            recipe.add("gas", gas);
            if (row.byproduct() != null)
            {
                final JsonObject byproduct = new JsonObject();
                byproduct.addProperty("item", row.byproduct());
                byproduct.addProperty("count", row.byproducts());
                recipe.add("byproduct", byproduct);
            }
            put("data/tfcpp/recipe/glass_distillation/" + row.name() + ".json", recipe);
        }

        // Superphosphate is rock phosphate made soluble, so it feeds a field at four times the rate of raw apatite; ammonium sulfate is ammonia in a form that does not blow away
        fertilizer("superphosphate", "tfcpp:powder/superphosphate", "phosphorus", SUPERPHOSPHATE_PHOSPHORUS);
        fertilizer("ammonium_sulfate", "tfcpp:powder/ammonium_sulfate", "nitrogen", AMMONIUM_SULFATE_NITROGEN);
    }

    private void fertilizer(String name, String ingredient, String nutrient, float amount)
    {
        final JsonObject fertilizer = new JsonObject();
        fertilizer.add("ingredient", item(ingredient));
        fertilizer.addProperty(nutrient, amount);
        put("data/tfcpp/tfc/fertilizer/" + name + ".json", fertilizer);
    }

    /** The batch of borosilicate glass, and what is blown from it. */
    private void glass()
    {
        // Borosilicate is silica and boron oxide with a little soda, about 81% SiO2, 13% B2O3 and 4% Na2O, and no lime: borax is the flux and brings the boron and the soda, so the batch is two sands and one borax, four batches from one lot
        final JsonObject batch = new JsonObject();
        batch.addProperty("type", "minecraft:crafting_shapeless");
        batch.addProperty("category", "misc");
        final JsonArray ingredients = new JsonArray();
        ingredients.add(item("tfcpp:powder/borax"));
        final JsonObject sand = new JsonObject();
        sand.addProperty("tag", "c:sands/silica");
        ingredients.add(sand);
        ingredients.add(sand.deepCopy());
        batch.add("ingredients", ingredients);
        batch.add("result", stack("tfcpp:borosilicate_glass_batch", 4));
        put("data/tfcpp/recipe/crafting/borosilicate_glass_batch.json", batch);

        glassworking("glass_carboy", "tfcpp:glass_carboy", "tfc:blow", "tfc:blow", "tfc:roll", "tfc:saw");
        glassworking("glass_retort", "tfcpp:glass_retort", "tfc:blow", "tfc:stretch", "tfc:pinch", "tfc:saw");

        // The batch is a glass of the highest tier, like volcanic glass: it goes on a blowpipe, and into the recipes that take any batch or any but the first tier
        addToTag("data/tfc/tags/item/glass_batches.json", "tfcpp:borosilicate_glass_batch");
        addToTag("data/tfc/tags/item/glass_batches_not_tier_1.json", "tfcpp:borosilicate_glass_batch");

        // A brass blowpipe melts at 930 C and borosilicate is worked at 1250 C and up, so the batch does not go on a brass pipe: TFC's recipe of the brass pipe with glass takes any batch but ours. The ceramic pipe's recipe takes it, as it takes any batch.
        final JsonObject notOurs = new JsonObject();
        notOurs.addProperty("type", "neoforge:difference");
        final JsonObject anyBatch = new JsonObject();
        anyBatch.addProperty("tag", "tfc:glass_batches");
        notOurs.add("base", anyBatch);
        notOurs.add("subtracted", item("tfcpp:borosilicate_glass_batch"));
        final JsonObject brassPipe = new JsonObject();
        brassPipe.addProperty("type", "tfc:advanced_shapeless_crafting");
        final JsonArray pipeIngredients = new JsonArray();
        pipeIngredients.add(notOurs);
        pipeIngredients.add(item("tfc:blowpipe"));
        brassPipe.add("ingredients", pipeIngredients);
        brassPipe.add("primary_ingredient", notOurs.deepCopy());
        final JsonObject pipeResult = new JsonObject();
        final JsonArray pipeModifiers = new JsonArray();
        final JsonObject addGlass = new JsonObject();
        addGlass.addProperty("type", "tfc:add_glass");
        pipeModifiers.add(addGlass);
        pipeResult.add("modifiers", pipeModifiers);
        pipeResult.add("stack", stack("tfc:blowpipe_with_glass", 1));
        brassPipe.add("result", pipeResult);
        put("data/tfc/recipe/crafting/blowpipe_with_glass.json", brassPipe);

        final JsonObject bath = shaped("tfcpp:sand_bath", "ISI", "III");
        bath.getAsJsonObject("key").add("I", item("tfc:metal/sheet/wrought_iron"));
        final JsonObject sandKey = new JsonObject();
        sandKey.addProperty("tag", "c:sands");
        bath.getAsJsonObject("key").add("S", sandKey);
        put("data/tfcpp/recipe/crafting/sand_bath.json", bath);

        for (String powder : List.of("potassium_bisulfate", "sodium_bisulfate", "superphosphate", "ammonium_sulfate"))
        {
            addToTag("data/c/tags/item/dusts/" + powder + ".json", "tfcpp:powder/" + powder);
        }
    }

    private void glassworking(String name, String result, String... operations)
    {
        final JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "tfc:glassworking");
        recipe.add("batch", item("tfcpp:borosilicate_glass_batch"));
        final JsonArray steps = new JsonArray();
        for (String operation : operations)
        {
            steps.add(operation);
        }
        recipe.add("operations", steps);
        recipe.add("result", stack(result, 1));
        put("data/tfcpp/recipe/glassworking/" + name + ".json", recipe);
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
        return condensate("tfcpp:" + liquid.id(), litres);
    }

    private static JsonObject condensate(String fluid, float litres)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("fluid", fluid);
        json.addProperty("litres", litres);
        return json;
    }

    private void fuels()
    {
        // Burns hotter than the coal it came from because the volatile matter that cools a flame is gone, and for as long as charcoal does
        fuel("coke", 1250f, 2200, COKE_SPECIFIC_HEAT);
        // Lignite chars without caking and leaves a crumbling, ashy fuel that cannot hold a blast furnace charge. Its heat comes from its carbon, about 25 MJ/kg against 30 for charcoal and coal, so it burns a sixth cooler and a sixth shorter than charcoal (1350 C, 1800 ticks): 1100 C, 1500 ticks. The ledger has the working.
        fuel("lignite_char", 1100f, 1500, CHAR_SPECIFIC_HEAT);

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
        // Lignite char is a fuel for a forge and a firepit like the coals, but not for a blast furnace
        addToTag("data/tfc/tags/item/forge_fuel.json", "tfcpp:lignite_char");
        addToTag("data/tfc/tags/item/firepit_fuel.json", "tfcpp:lignite_char");
        addToTag("data/tfcpp/tags/item/lights_gas_burner.json", "minecraft:flint_and_steel", "tfc:firestarter", "tfc:flint_and_pyrite");

        // Tar and the liquors are kept in barrels and wooden buckets in reality. Corrosive liquids are not: they eat the wood. None of these are ingredients of any TFC recipe, so they stay out of tfc:ingredients.
        final String[] liquids = java.util.Arrays.stream(Liquid.values()).filter(liquid -> !liquid.isCorrosive()).map(liquid -> "tfcpp:" + liquid.id()).toArray(String[]::new);
        addToTag("data/tfc/tags/fluid/usable_in_barrel.json", liquids);
        addToTag("data/tfc/tags/fluid/usable_in_wooden_bucket.json", liquids);
        addToTag("data/tfcpp/tags/fluid/corrosive.json", java.util.Arrays.stream(Liquid.values()).filter(Liquid::isCorrosive).map(liquid -> "tfcpp:" + liquid.id()).toArray(String[]::new));
        for (Liquid liquid : Liquid.values())
        {
            addToTag("data/c/tags/fluid/" + liquid.id() + ".json", "tfcpp:" + liquid.id());
        }
        // A carboy of glass holds every chemical liquid of the mod, vinegar for the acetic acid of the pyroligneous distillation, and fresh water for the receiver
        final java.util.List<String> carboy = new java.util.ArrayList<>(java.util.Arrays.stream(Liquid.values()).map(liquid -> "tfcpp:" + liquid.id()).toList());
        carboy.add("tfc:vinegar");
        carboy.add("#tfc:any_fresh_water");
        addToTag("data/tfcpp/tags/fluid/usable_in_carboy.json", carboy.toArray(String[]::new));
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

        // Lead sheet walls: lead is what the acid cannot eat while it is cool
        final JsonObject chamber = shaped("tfcpp:lead_chamber", "SSS", "S S", "SSS");
        chamber.getAsJsonObject("key").add("S", item("tfcpp:metal/sheet/lead"));
        put("data/tfcpp/recipe/crafting/lead_chamber.json", chamber);

        final JsonObject sulfurBurner = shaped("tfcpp:sulfur_burner", "BBB", "B B", "BBB");
        sulfurBurner.getAsJsonObject("key").add("B", item("tfc:ceramic/fire_brick"));
        put("data/tfcpp/recipe/crafting/sulfur_burner.json", sulfurBurner);

        // A flask of glass with zinc in it: the zinc and the acid of the test make the arsine that shows the poison
        final JsonObject marsh = new JsonObject();
        marsh.addProperty("type", "minecraft:crafting_shapeless");
        marsh.addProperty("category", "misc");
        final JsonArray marshIngredients = new JsonArray();
        marshIngredients.add(item("tfc:silica_glass_bottle"));
        marshIngredients.add(item("tfc:metal/ingot/zinc"));
        marsh.add("ingredients", marshIngredients);
        marsh.add("result", stack("tfcpp:marsh_apparatus", 1));
        put("data/tfcpp/recipe/crafting/marsh_apparatus.json", marsh);
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
