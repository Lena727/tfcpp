package tfcpp.compat.guide;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * One page of an entry of the field guide. The fixed part (recipe, item, multiblock) is the same in every language; the words are kept in both, under the keys Patchouli reads them from.
 */
public final class Page
{
    private final String type;
    private final JsonObject fixed = new JsonObject();
    private final Map<String, L> words = new LinkedHashMap<>();

    Page(String type)
    {
        this.type = type;
    }

    public String type()
    {
        return type;
    }

    /** What does not change with the language: recipe ids, items, anchors, structures. */
    public JsonObject fixed()
    {
        return fixed;
    }

    /** The text of the page by key ({@code text}, {@code title}, {@code name}). */
    public Map<String, L> words()
    {
        return words;
    }

    Page with(String key, JsonElement value)
    {
        fixed.add(key, value);
        return this;
    }

    Page with(String key, String value)
    {
        fixed.addProperty(key, value);
        return this;
    }

    Page with(String key, boolean value)
    {
        fixed.addProperty(key, value);
        return this;
    }

    Page say(String key, L text)
    {
        words.put(key, text);
        return this;
    }

    /** Lets other entries link to this page as {@code $(l:category/entry#anchor)}. */
    public Page anchor(String anchor)
    {
        return with("anchor", anchor);
    }

    public JsonObject render(String language)
    {
        final JsonObject page = new JsonObject();
        page.addProperty("type", type);
        fixed.entrySet().forEach(entry -> page.add(entry.getKey(), entry.getValue()));
        words.forEach((key, text) -> page.addProperty(key, text.in(language)));
        return page;
    }
}
