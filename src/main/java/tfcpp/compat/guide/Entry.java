package tfcpp.compat.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * An entry of the field guide, with its pages in both languages. Its id is the path under {@code entries/}, which is also how other entries link to it ({@code $(l:metalworking/bloomery)}).
 * <p>
 * An entry with the id of one of TFC's replaces it whole, in every language this guide is written in. Keep TFC's category and sort number in that case, so that the structure of the book stays.
 */
public final class Entry
{
    /** For an entry that TFC itself gives no sort number: it is then listed by name. */
    public static final int NO_SORT = -1;

    private final String id;
    private final String category;
    private final String icon;
    private final int sortnum;
    private final L name;
    private final List<Page> pages = new ArrayList<>();
    private final Map<String, Integer> pageOfItem = new LinkedHashMap<>();

    private Entry(String id, String category, String icon, int sortnum, L name)
    {
        this.id = id;
        this.category = category;
        this.icon = icon;
        this.sortnum = sortnum;
        this.name = name;
    }

    /**
     * @param id       path under {@code entries/}, such as {@code metalworking/bloomery}
     * @param category the category it sits in, such as {@code tfc:metalworking}
     * @param icon     an item id, or an item id with components, as TFC writes them
     */
    public static Entry of(String id, String category, String icon, int sortnum, L name)
    {
        return new Entry(id, category, icon, sortnum, name);
    }

    public String id()
    {
        return id;
    }

    public String category()
    {
        return category;
    }

    public int sortnum()
    {
        return sortnum;
    }

    public L name()
    {
        return name;
    }

    public List<Page> pages()
    {
        return pages;
    }

    public Entry add(Page page)
    {
        pages.add(page);
        return this;
    }

    /** Gives the last page an anchor for links. */
    public Entry anchor(String anchor)
    {
        last().anchor(anchor);
        return this;
    }

    /** Makes a recipe lookup for any of these items open the last page of this entry, as TFC's {@code extra_recipe_mappings} do. */
    public Entry showsOnLastPage(String... items)
    {
        for (String item : items)
        {
            pageOfItem.put(item, pages.size() - 1);
        }
        return this;
    }

    private Page last()
    {
        if (pages.isEmpty())
        {
            throw new IllegalStateException(id + " has no page yet");
        }
        return pages.get(pages.size() - 1);
    }

    public Entry text(L text)
    {
        return add(Pages.text(text));
    }

    public Entry titled(L title, L text)
    {
        return add(Pages.titled(title, text));
    }

    public Entry spotlight(String item, L title, L text)
    {
        return add(Pages.spotlight(item, title, text));
    }

    public Entry spotlight(String item, L text)
    {
        return add(Pages.spotlight(item, text));
    }

    public Entry crafting(String recipe, L text)
    {
        return add(Pages.crafting(recipe, text));
    }

    public Entry crafting(String recipe, String second, L text)
    {
        return add(Pages.crafting(recipe, second, text));
    }

    public Entry image(L caption, boolean border, String... images)
    {
        return add(Pages.image(caption, border, images));
    }

    public Entry multiblockOf(String id, L name, L text)
    {
        return add(Pages.multiblockOf(id, name, text));
    }

    public Entry multiblock(JsonObject structure, L name, L text)
    {
        return add(Pages.multiblock(structure, name, text));
    }

    public Entry cycle(L text, JsonElement... structures)
    {
        return add(Pages.cycle(text, structures));
    }

    public Entry heating(String recipe, L text)
    {
        return add(Pages.heating(recipe, text));
    }

    public Entry anvil(String recipe, L text)
    {
        return add(Pages.anvil(recipe, text));
    }

    public Entry welding(String recipe, L text)
    {
        return add(Pages.welding(recipe, text));
    }

    public Entry knapping(String recipe, L text)
    {
        return add(Pages.knapping(recipe, text));
    }

    public Entry quern(String recipe, L text)
    {
        return add(Pages.quern(recipe, text));
    }

    public Entry glassworking(String recipe, L text)
    {
        return add(Pages.glassworking(recipe, text));
    }

    public Entry instantBarrel(String recipe, L text)
    {
        return add(Pages.instantBarrel(recipe, text));
    }

    public Entry sealedBarrel(String recipe, L text)
    {
        return add(Pages.sealedBarrel(recipe, text));
    }

    public Map<String, Integer> pageOfItem()
    {
        return pageOfItem;
    }

    public JsonObject render(String language)
    {
        if (pages.isEmpty())
        {
            throw new IllegalStateException(id + " has no pages");
        }
        final JsonObject entry = new JsonObject();
        entry.addProperty("name", name.in(language));
        entry.addProperty("category", category);
        entry.addProperty("icon", icon);
        final JsonArray list = new JsonArray();
        for (Page page : pages)
        {
            list.add(page.render(language));
        }
        entry.add("pages", list);
        entry.addProperty("read_by_default", true);
        if (sortnum != NO_SORT)
        {
            entry.addProperty("sortnum", sortnum);
        }
        if (!pageOfItem.isEmpty())
        {
            final JsonObject mappings = new JsonObject();
            pageOfItem.forEach(mappings::addProperty);
            entry.add("extra_recipe_mappings", mappings);
        }
        return entry;
    }
}
