package tfcpp.compat.guide;

import com.google.gson.JsonObject;

/**
 * A category of the field guide: a tab on its first page. Its id is the file name, and entries name it as {@code tfc:<id>}.
 */
public record Category(String id, L name, L description, String icon, int sortnum)
{
    public JsonObject render(String language)
    {
        final JsonObject category = new JsonObject();
        category.addProperty("name", name.in(language));
        category.addProperty("description", description.in(language));
        category.addProperty("icon", icon);
        category.addProperty("sortnum", sortnum);
        return category;
    }
}
