package tfcpp.compat.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Builders of pages, one for each kind the guide uses. The page types prefixed {@code tfc:} are TFC's own and draw a recipe above a paragraph; their templates are in TFC's book.
 * <p>
 * Text uses Patchouli's formatting, as TFC's pages do: {@code $(thing)item or term$()}, {@code $(l:category/entry#anchor)link$()}, {@code $(br)}, {@code $(br2)}, {@code $(li)} for a bullet, {@code $(bold)}, {@code $(item)$(k:key.use)$()} for a key. A page holds only as much as TFC's longest (about 500 letters); {@code GuideDataTest} counts them.
 */
public final class Pages
{
    public static Page text(L text)
    {
        return new Page("patchouli:text").say("text", text);
    }

    /** A paragraph under a title. */
    public static Page titled(L title, L text)
    {
        return new Page("patchouli:text").say("title", title).say("text", text);
    }

    /** An item shown large, with a paragraph. Its recipes are not listed. */
    public static Page spotlight(String item, L title, L text)
    {
        return new Page("patchouli:spotlight").with("item", item).with("link_recipes", false).say("title", title).say("text", text);
    }

    /** The same without a title, for an item whose name says it all. */
    public static Page spotlight(String item, L text)
    {
        return new Page("patchouli:spotlight").with("item", item).with("link_recipes", false).say("text", text);
    }

    public static Page crafting(String recipe, L text)
    {
        return new Page("patchouli:crafting").with("recipe", recipe).say("text", text);
    }

    public static Page crafting(String recipe, String second, L text)
    {
        return new Page("patchouli:crafting").with("recipe", recipe).with("recipe2", second).say("text", text);
    }

    /** One or more of TFC's own pictures (a path such as {@code tfc:textures/gui/book/gui/anvil_empty.png}), framed or not. */
    public static Page image(L caption, boolean border, String... images)
    {
        final JsonArray list = new JsonArray();
        for (String image : images)
        {
            list.add(image);
        }
        return new Page("patchouli:image").with("images", list).with("border", border).say("text", caption);
    }

    /** A structure TFC's book already defines, by id. */
    public static Page multiblockOf(String id, L name, L text)
    {
        return new Page("patchouli:multiblock").with("multiblock_id", id).with("enable_visualize", true).say("name", name).say("text", text);
    }

    /** A structure drawn here: see {@link Structure}. */
    public static Page multiblock(JsonObject structure, L name, L text)
    {
        return new Page("patchouli:multiblock").with("multiblock", structure).with("enable_visualize", false).say("name", name).say("text", text);
    }

    /** A page that cycles through several structures (each an object from {@link Structure}, or the id of one TFC's book defines). */
    public static Page cycle(L text, JsonElement... structures)
    {
        final JsonArray list = new JsonArray();
        for (JsonElement structure : structures)
        {
            list.add(structure);
        }
        return new Page("tfc:multimultiblock").with("multiblocks", list).say("text", text);
    }

    public static Page heating(String recipe, L text)
    {
        return new Page("tfc:heat_recipe").with("recipe", recipe).say("text", text);
    }

    public static Page anvil(String recipe, L text)
    {
        return new Page("tfc:anvil_recipe").with("recipe", recipe).say("text", text);
    }

    public static Page welding(String recipe, L text)
    {
        return new Page("tfc:welding_recipe").with("recipe", recipe).say("text", text);
    }

    public static Page knapping(String recipe, L text)
    {
        return new Page("tfc:knapping_recipe").with("recipe", recipe).say("text", text);
    }

    public static Page quern(String recipe, L text)
    {
        return new Page("tfc:quern_recipe").with("recipe", recipe).say("text", text);
    }

    public static Page glassworking(String recipe, L text)
    {
        return new Page("tfc:glassworking_recipe").with("recipe", recipe).say("text", text);
    }

    public static Page instantBarrel(String recipe, L text)
    {
        return new Page("tfc:instant_barrel_recipe").with("recipe", recipe).say("text", text);
    }

    public static Page sealedBarrel(String recipe, L text)
    {
        return new Page("tfc:sealed_barrel_recipe").with("recipe", recipe).say("text", text);
    }

    /** Patchouli structures: layers listed from the top down, rows from north to south, {@code 0} marks the block the player looks at, a space is nothing. */
    public static final class Structure
    {
        /** One block on a base: how every ore and mineral of the book is drawn. */
        public static JsonObject block(String state)
        {
            return layers(new String[][] {{"X"}, {"0"}}, "X", state);
        }

        /** @param mapping pairs of a letter and a block state, such as {@code "G", "tfc:rock/bricks/granite"} */
        public static JsonObject layers(String[][] layers, String... mapping)
        {
            final JsonArray pattern = new JsonArray();
            for (String[] layer : layers)
            {
                final JsonArray rows = new JsonArray();
                for (String row : layer)
                {
                    rows.add(new JsonPrimitive(row));
                }
                pattern.add(rows);
            }
            final JsonObject map = new JsonObject();
            for (int i = 0; i < mapping.length; i += 2)
            {
                map.addProperty(mapping[i], mapping[i + 1]);
            }
            final JsonObject structure = new JsonObject();
            structure.add("pattern", pattern);
            structure.add("mapping", map);
            return structure;
        }

        private Structure() {}
    }

    private Pages() {}
}
