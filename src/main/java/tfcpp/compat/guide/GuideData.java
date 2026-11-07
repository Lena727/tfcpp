package tfcpp.compat.guide;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.google.gson.JsonElement;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * Writes the part of TFC's field guide that TFC++ owns: the entries it replaces because the mod made them wrong or incomplete, and its own category.
 * <p>
 * The book is TFC's ({@code tfc:field_guide}, with {@code use_resource_pack}, so its pages are resources and not data). A file here with the path of one of TFC's replaces it, because the mod loads after TFC: {@code ResourcePackLoader} builds the packs of all mods from one ordered map, dependencies first, and a later pack hides an earlier one at the same path. Entries are listed in {@link GuideBook}; a page is written once, in both languages, with {@link Pages}.
 */
public final class GuideData implements DataProvider
{
    public static final String BOOK = "assets/tfc/patchouli_books/field_guide/";

    private final Path root;

    public GuideData(PackOutput output)
    {
        this.root = output.getOutputFolder();
    }

    /** @return every file of the guide by its path under the output folder, for every language */
    public static Map<String, JsonElement> files()
    {
        final Map<String, JsonElement> files = new LinkedHashMap<>();
        for (String language : GuideBook.LANGUAGES)
        {
            for (Category category : GuideBook.categories())
            {
                put(files, categoryPath(language, category.id()), category.render(language));
            }
            for (Entry entry : GuideBook.entries())
            {
                put(files, entryPath(language, entry.id()), entry.render(language));
            }
        }
        return files;
    }

    public static String entryPath(String language, String id)
    {
        return BOOK + language + "/entries/" + id + ".json";
    }

    public static String categoryPath(String language, String id)
    {
        return BOOK + language + "/categories/" + id + ".json";
    }

    private static void put(Map<String, JsonElement> files, String path, JsonElement json)
    {
        if (files.put(path, json) != null)
        {
            throw new IllegalStateException("The guide writes " + path + " twice");
        }
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache)
    {
        final List<CompletableFuture<?>> saves = new ArrayList<>();
        files().forEach((path, json) -> saves.add(DataProvider.saveStable(cache, json, root.resolve(path))));
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName()
    {
        return "TFC++ field guide";
    }
}
