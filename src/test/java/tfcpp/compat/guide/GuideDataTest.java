package tfcpp.compat.guide;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the field guide without a client: that every file the guide should have is written and equal to its source, that both languages say the same, that links, anchors and recipes lead somewhere, that each page fits, that nothing of TFC's wording is carried over, and that the replaced entries keep TFC's structure.
 * <p>
 * What it cannot check is how a page looks on the screen.
 */
class GuideDataTest
{
    private static final String BOOK = GuideData.BOOK;
    private static final String TFC_ENTRIES = BOOK + "en_us/entries/";
    private static final Pattern MACRO = Pattern.compile("\\$\\([^)]*\\)");
    private static final Pattern KNOWN_MACRO = Pattern.compile("\\$\\((thing|item|li|br|br2|bold|italic|l:[^)]+|k:[^)]+|cfg:[^)]+|[0-9a-f]|#[0-9A-Fa-f]{6}|t:[^)]*|)\\)");
    private static final Pattern LINK = Pattern.compile("\\$\\(l:([^)]*)\\)");
    private static final Set<String> PAGE_TYPES = Set.of("patchouli:text", "patchouli:spotlight", "patchouli:crafting", "patchouli:image", "patchouli:multiblock", "tfc:multimultiblock",
        "tfc:heat_recipe", "tfc:anvil_recipe", "tfc:welding_recipe", "tfc:knapping_recipe", "tfc:quern_recipe", "tfc:glassworking_recipe", "tfc:instant_barrel_recipe", "tfc:sealed_barrel_recipe");

    /** Every TFC entry in English by its id (path under {@code entries/} without {@code .json}), as TFC ships it. */
    private static final Map<String, JsonObject> TFC = new LinkedHashMap<>();
    /** The same in Russian, for the entries TFC translated. */
    private static final Map<String, JsonObject> TFC_RU = new LinkedHashMap<>();
    private static final Map<String, Entry> OURS = new LinkedHashMap<>();

    @BeforeAll
    static void load() throws IOException
    {
        final URL anyTfcEntry = tfcResource(BOOK + "en_us/categories/metalworking.json");
        assertNotNull(anyTfcEntry, "TFC's field guide must be on the class path");
        final JarFile jar = ((JarURLConnection) anyTfcEntry.openConnection()).getJarFile();
        final Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements())
        {
            final String name = entries.nextElement().getName();
            if (name.startsWith(TFC_ENTRIES) && name.endsWith(".json"))
            {
                TFC.put(name.substring(TFC_ENTRIES.length(), name.length() - 5), read(jar.getInputStream(jar.getEntry(name)).readAllBytes()));
            }
            final String russian = BOOK + "ru_ru/entries/";
            if (name.startsWith(russian) && name.endsWith(".json"))
            {
                TFC_RU.put(name.substring(russian.length(), name.length() - 5), read(jar.getInputStream(jar.getEntry(name)).readAllBytes()));
            }
        }
        assertTrue(TFC.size() > 70, "Found only " + TFC.size() + " entries of TFC's guide");
        for (Entry entry : GuideBook.entries())
        {
            assertNull(OURS.put(entry.id(), entry), "Two entries with the id " + entry.id());
        }
    }

    private static JsonObject read(byte[] bytes)
    {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** The resource of TFC's jar with this path, as against the one the mod itself generates. */
    private static URL tfcResource(String path) throws IOException
    {
        for (URL url : Collections.list(GuideDataTest.class.getClassLoader().getResources(path)))
        {
            if (url.getProtocol().equals("jar"))
            {
                return url;
            }
        }
        return null;
    }

    private static List<URL> allResources(String path) throws IOException
    {
        return Collections.list(GuideDataTest.class.getClassLoader().getResources(path));
    }

    // ---------------------------------------------------------------- files

    @Test
    void everyFileIsWrittenAndEqualToItsSource() throws IOException
    {
        final Map<String, JsonElement> files = GuideData.files();
        assertTrue(files.size() >= 2 * (OURS.size() + GuideBook.categories().size()));
        for (Map.Entry<String, JsonElement> file : files.entrySet())
        {
            final Path path = Path.of("src/generated/resources").resolve(file.getKey());
            assertTrue(Files.exists(path), path + " is missing: run runData");
            assertEquals(file.getValue(), JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)), path + " differs from the source of the guide: run runData");
        }
    }

    @Test
    void theModLoadsAfterTfcSoItsFilesReplaceTfcs() throws IOException
    {
        final String toml = Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
        final int block = toml.indexOf("modId = \"tfc\"");
        assertTrue(block > 0, "tfcpp must depend on tfc");
        final int next = toml.indexOf("[[dependencies", block);
        final String dependency = toml.substring(block, next < 0 ? toml.length() : next);
        assertTrue(dependency.contains("ordering = \"AFTER\""), "A mod's resources hide those of mods it is ordered after: tfcpp must be AFTER tfc, or the replaced entries would not show");
    }

    @Test
    void everyReplacedEntryHidesTfcsFileAtTheSamePath() throws IOException
    {
        int replaced = 0;
        for (Entry entry : OURS.values())
        {
            if (!TFC.containsKey(entry.id()))
            {
                continue;
            }
            replaced++;
            for (String language : GuideBook.LANGUAGES)
            {
                final String path = GuideData.entryPath(language, entry.id());
                final List<URL> urls = allResources(path);
                if (language.equals(L.ENGLISH) || TFC_RU.containsKey(entry.id()))
                {
                    assertTrue(urls.stream().anyMatch(url -> url.getProtocol().equals("jar")), "TFC has no " + path);
                    assertTrue(urls.stream().anyMatch(url -> url.getProtocol().equals("file")), "The mod does not provide " + path);
                }
                else
                {
                    assertFalse(urls.isEmpty());
                }
            }
        }
        assertTrue(replaced >= 21, "Only " + replaced + " of TFC's entries are replaced");
    }

    // ---------------------------------------------------------------- structure

    @Test
    void entriesAreSane()
    {
        for (Entry entry : OURS.values())
        {
            assertFalse(entry.pages().isEmpty(), entry.id());
            assertFalse(entry.name().en().isBlank(), entry.id());
            assertFalse(entry.name().ru().isBlank(), entry.id());
            final String category = entry.category();
            assertTrue(category.startsWith("tfc:"), entry.id());
            final String name = category.substring(4);
            assertTrue(GuideBook.TFC_CATEGORIES.contains(name) || GuideBook.categories().stream().anyMatch(c -> c.id().equals(name)), entry.id() + " is in the unknown category " + category);
            if (name.equals("tfcpp"))
            {
                assertTrue(entry.id().startsWith("tfcpp/"), entry.id() + " belongs in entries/tfcpp/");
                assertFalse(TFC.containsKey(entry.id()), entry.id() + " is one of TFC's");
            }
            else
            {
                assertTrue(TFC.containsKey(entry.id()), entry.id() + " is not an entry of TFC: a new entry belongs in the TFC++ category");
            }
            final Set<String> anchors = new HashSet<>();
            for (Page page : entry.pages())
            {
                assertTrue(PAGE_TYPES.contains(page.type()), entry.id() + ": page type " + page.type());
                requireFields(entry, page);
                if (page.fixed().has("anchor"))
                {
                    assertTrue(anchors.add(page.fixed().get("anchor").getAsString()), entry.id() + ": anchor used twice");
                }
            }
            entry.pageOfItem().forEach((item, page) -> assertTrue(page >= 0 && page < entry.pages().size(), entry.id() + ": " + item + " maps to page " + page));
        }
        for (Category category : GuideBook.categories())
        {
            assertTrue(category.sortnum() > 6, "The TFC++ category goes after TFC's seven");
            assertFalse(category.name().en().isBlank());
        }
    }

    private static void requireFields(Entry entry, Page page)
    {
        final String where = entry.id() + " (" + page.type() + ")";
        switch (page.type())
        {
            case "patchouli:text" -> assertTrue(page.words().containsKey("text"), where);
            case "patchouli:spotlight" -> assertTrue(page.fixed().has("item"), where);
            case "patchouli:crafting" -> assertTrue(page.fixed().has("recipe"), where);
            case "patchouli:image" -> assertTrue(page.fixed().get("images").getAsJsonArray().size() > 0, where);
            case "patchouli:multiblock" -> assertTrue(page.fixed().has("multiblock") || page.fixed().has("multiblock_id"), where);
            case "tfc:multimultiblock" -> assertTrue(page.fixed().get("multiblocks").getAsJsonArray().size() > 0, where);
            default -> assertTrue(page.fixed().has("recipe"), where);
        }
    }

    @Test
    void bothLanguagesSayTheSameThing()
    {
        for (Entry entry : OURS.values())
        {
            final JsonObject en = entry.render(L.ENGLISH), ru = entry.render(L.RUSSIAN);
            assertEquals(strip(en), strip(ru), entry.id() + ": the languages differ in more than words");
            for (Page page : entry.pages())
            {
                page.words().forEach((key, text) ->
                {
                    final String where = entry.id() + " / " + key;
                    assertEquals(links(text.en()), links(text.ru()), where + ": the links differ between the languages");
                    assertEquals(configValues(text.en()), configValues(text.ru()), where + ": the config values differ");
                    final String visibleEn = visible(text.en()), visibleRu = visible(text.ru());
                    if (visibleEn.chars().anyMatch(Character::isLetter))
                    {
                        assertTrue(visibleRu.chars().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC), where + ": the Russian has no Russian in it");
                        assertNotEquals(text.en(), text.ru(), where + ": not translated");
                    }
                });
            }
        }
    }

    /** What must be the same in both languages: the entry without its words. */
    private static JsonObject strip(JsonObject entry)
    {
        final JsonObject copy = entry.deepCopy();
        copy.remove("name");
        final JsonArray pages = copy.getAsJsonArray("pages");
        for (JsonElement element : pages)
        {
            final JsonObject page = element.getAsJsonObject();
            page.remove("text");
            page.remove("title");
            page.remove("name");
        }
        return copy;
    }

    private static List<String> links(String text)
    {
        final List<String> found = new ArrayList<>();
        final Matcher matcher = LINK.matcher(text);
        while (matcher.find())
        {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static List<String> configValues(String text)
    {
        return MACRO.matcher(text).results().map(java.util.regex.MatchResult::group).filter(macro -> macro.startsWith("$(cfg:")).toList();
    }

    private static String visible(String text)
    {
        return MACRO.matcher(text).replaceAll("");
    }

    /** The length of a page of text as it fills the screen: the letters, and a share of a line for each break and each bullet. */
    private static int fill(String text)
    {
        int breaks = 0, doubles = 0, bullets = 0;
        final Matcher matcher = MACRO.matcher(text);
        while (matcher.find())
        {
            switch (matcher.group())
            {
                case "$(br)" -> breaks++;
                case "$(br2)" -> doubles++;
                case "$(li)" -> bullets++;
                default -> {}
            }
        }
        return visible(text).length() + 15 * breaks + 45 * doubles + 15 * bullets;
    }

    // ---------------------------------------------------------------- text

    @Test
    void textUsesOnlyFormattingPatchouliHas()
    {
        for (Entry entry : OURS.values())
        {
            for (Page page : entry.pages())
            {
                for (Map.Entry<String, L> word : page.words().entrySet())
                {
                    for (String language : GuideBook.LANGUAGES)
                    {
                        final String text = word.getValue().in(language);
                        final String where = entry.id() + " / " + language + " / " + word.getKey() + ": " + abbreviate(text);
                        final long opened = Pattern.compile("\\$\\(").matcher(text).results().count();
                        final long whole = MACRO.matcher(text).results().count();
                        assertEquals(opened, whole, where + ": a macro is not closed");
                        MACRO.matcher(text).results().forEach(m -> assertTrue(KNOWN_MACRO.matcher(m.group()).matches(), where + ": unknown macro " + m.group()));
                        assertFalse(text.contains("  "), where + ": a double space");
                        assertFalse(text.contains("TODO"), where);
                        assertFalse(text.contains("%%") && !text.contains("%"), where);
                    }
                }
            }
        }
    }

    private static String abbreviate(String text)
    {
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }

    /**
     * A page holds about as much as the fullest page of TFC's own book of the same kind, measured in the same way. TFC's pages are laid out by hand against the screen, so what they hold is known to fit.
     */
    @Test
    void everyPageFitsAsTfcsFullestDo()
    {
        final Map<String, Integer> room = new HashMap<>();
        for (Map<String, JsonObject> book : List.of(TFC, TFC_RU))
        {
            for (JsonObject entry : book.values())
            {
                for (JsonElement element : entry.getAsJsonArray("pages"))
                {
                    final JsonObject page = element.getAsJsonObject();
                    for (String key : List.of("text"))
                    {
                        if (page.has(key) && page.get(key).isJsonPrimitive())
                        {
                            room.merge(shape(page.get("type").getAsString(), page.has("title") || page.has("name") && !page.get("name").getAsString().isEmpty()), fill(page.get(key).getAsString()), Math::max);
                        }
                    }
                }
            }
        }
        final List<String> tooLong = new ArrayList<>();
        for (Entry entry : OURS.values())
        {
            int index = 0;
            for (Page page : entry.pages())
            {
                for (String language : GuideBook.LANGUAGES)
                {
                    final L text = page.words().get("text");
                    if (text != null)
                    {
                        final String key = shape(page.type(), page.words().containsKey("title") || page.words().containsKey("name") && !page.words().get("name").in(language).isEmpty());
                        final int limit = room.getOrDefault(key, 400);
                        final int used = fill(text.in(language));
                        if (used > limit)
                        {
                            tooLong.add(entry.id() + " page " + index + " (" + language + ", " + page.type() + "): " + used + " > " + limit);
                        }
                    }
                }
                index++;
            }
        }
        assertTrue(tooLong.isEmpty(), "Pages longer than TFC's fullest of the kind:\n" + String.join("\n", tooLong));
    }

    private static String shape(String type, boolean titled)
    {
        return type + (titled ? "+title" : "");
    }

    // ---------------------------------------------------------------- links

    @Test
    void linksAndAnchorsLeadToEntriesThatExist()
    {
        final List<String> broken = new ArrayList<>();
        for (Entry entry : OURS.values())
        {
            for (Page page : entry.pages())
            {
                for (Map.Entry<String, L> word : page.words().entrySet())
                {
                    for (String language : GuideBook.LANGUAGES)
                    {
                        for (String link : links(word.getValue().in(language)))
                        {
                            final String problem = problemWith(link);
                            if (problem != null)
                            {
                                broken.add(entry.id() + " (" + language + "): $(l:" + link + ") " + problem);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(broken.isEmpty(), String.join("\n", broken));
    }

    /** TFC's own links to entries that this mod replaces must find their anchors in the new text. */
    @Test
    void tfcsLinksToReplacedEntriesStillFindTheirAnchors()
    {
        final List<String> broken = new ArrayList<>();
        for (Map.Entry<String, JsonObject> tfc : TFC.entrySet())
        {
            for (JsonElement element : tfc.getValue().getAsJsonArray("pages"))
            {
                for (String key : List.of("text"))
                {
                    if (element.getAsJsonObject().has(key))
                    {
                        for (String link : links(element.getAsJsonObject().get(key).getAsString()))
                        {
                            final String target = link.contains("#") ? link.substring(0, link.indexOf('#')) : link;
                            if (OURS.containsKey(target))
                            {
                                final String problem = problemWith(link);
                                if (problem != null)
                                {
                                    broken.add(tfc.getKey() + ": $(l:" + link + ") " + problem);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(broken.isEmpty(), String.join("\n", broken));
    }

    /** @return what is wrong with this link, or {@code null} */
    private static String problemWith(String link)
    {
        if (link.startsWith("http"))
        {
            return null;
        }
        final String entry = link.contains("#") ? link.substring(0, link.indexOf('#')) : link;
        final String anchor = link.contains("#") ? link.substring(link.indexOf('#') + 1) : null;
        final Set<String> anchors = new HashSet<>();
        if (OURS.containsKey(entry))
        {
            OURS.get(entry).pages().forEach(page -> { if (page.fixed().has("anchor")) anchors.add(page.fixed().get("anchor").getAsString()); });
        }
        else if (TFC.containsKey(entry))
        {
            TFC.get(entry).getAsJsonArray("pages").forEach(page -> { if (page.getAsJsonObject().has("anchor")) anchors.add(page.getAsJsonObject().get("anchor").getAsString()); });
        }
        else
        {
            return "leads to no entry";
        }
        return anchor == null || anchors.contains(anchor) ? null : "has no anchor " + anchor + " in " + entry;
    }

    // ---------------------------------------------------------------- recipes

    @Test
    void everyRecipeOfAPageExistsAndIsNotSwitchedOff() throws IOException
    {
        final List<String> missing = new ArrayList<>();
        for (Entry entry : OURS.values())
        {
            for (Page page : entry.pages())
            {
                final List<String> ids = new ArrayList<>();
                for (String key : List.of("recipe", "recipe2"))
                {
                    if (page.fixed().has(key))
                    {
                        ids.add(page.fixed().get(key).getAsString());
                    }
                }
                for (String id : ids)
                {
                    if (!recipeExists(id))
                    {
                        missing.add(entry.id() + ": " + id);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Recipes shown by the guide that do not exist (or that the mod switches off):\n" + String.join("\n", missing));
    }

    private static boolean recipeExists(String id) throws IOException
    {
        final String namespace = id.substring(0, id.indexOf(':')), path = id.substring(id.indexOf(':') + 1);
        final String resource = "data/" + namespace + "/recipe/" + path + ".json";
        for (String root : List.of("src/generated/resources/", "src/main/resources/"))
        {
            final Path file = Path.of(root + resource);
            if (Files.exists(file))
            {
                final JsonObject recipe = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                return !(recipe.has("neoforge:conditions") && !recipe.has("type"));
            }
        }
        return !allResources(resource).isEmpty();
    }

    // ---------------------------------------------------------------- TFC's structure and wording

    @Test
    void aReplacedEntryKeepsTfcsStructure()
    {
        for (Entry entry : OURS.values())
        {
            final JsonObject tfc = TFC.get(entry.id());
            if (tfc == null)
            {
                continue;
            }
            final JsonObject ours = entry.render(L.ENGLISH);
            assertEquals(tfc.get("category").getAsString(), ours.get("category").getAsString(), entry.id() + ": category");
            assertEquals(tfc.has("sortnum") ? tfc.get("sortnum").getAsInt() : null, ours.has("sortnum") ? ours.get("sortnum").getAsInt() : null, entry.id() + ": sort number");
            assertEquals(tfc.get("icon").getAsString(), ours.get("icon").getAsString(), entry.id() + ": icon");

            final Set<String> mapped = tfc.has("extra_recipe_mappings") ? tfc.getAsJsonObject("extra_recipe_mappings").keySet() : Set.of();
            final Set<String> ourMapped = ours.has("extra_recipe_mappings") ? ours.getAsJsonObject("extra_recipe_mappings").keySet() : Set.of();
            assertTrue(ourMapped.containsAll(mapped), entry.id() + ": no longer shows " + difference(mapped, ourMapped));

            assertTrue(anchorsOf(entry).containsAll(anchorsOf(tfc)), entry.id() + ": lost the anchor " + difference(anchorsOf(tfc), anchorsOf(entry)));
        }
    }

    private static Set<String> difference(Set<String> all, Set<String> some)
    {
        final Set<String> rest = new HashSet<>(all);
        rest.removeAll(some);
        return rest;
    }

    private static Set<String> anchorsOf(Entry entry)
    {
        final Set<String> anchors = new HashSet<>();
        entry.pages().forEach(page -> { if (page.fixed().has("anchor")) anchors.add(page.fixed().get("anchor").getAsString()); });
        return anchors;
    }

    private static Set<String> anchorsOf(JsonObject entry)
    {
        final Set<String> anchors = new HashSet<>();
        entry.getAsJsonArray("pages").forEach(page -> { if (page.getAsJsonObject().has("anchor")) anchors.add(page.getAsJsonObject().get("anchor").getAsString()); });
        return anchors;
    }

    /** TFC is under the EUPL, and an entry that replaces one of TFC's has to be our own writing: no run of eight words may be the same. */
    @Test
    void noReplacedEntryRepeatsRunsOfTfcsWords()
    {
        final List<String> copied = new ArrayList<>();
        for (Entry entry : OURS.values())
        {
            for (Map.Entry<String, Map<String, JsonObject>> book : Map.of(L.ENGLISH, TFC, L.RUSSIAN, TFC_RU).entrySet())
            {
                final JsonObject tfc = book.getValue().get(entry.id());
                if (tfc == null)
                {
                    continue;
                }
                final Set<String> theirs = new HashSet<>();
                for (JsonElement page : tfc.getAsJsonObject().getAsJsonArray("pages"))
                {
                    for (String key : List.of("text", "title", "name"))
                    {
                        if (page.getAsJsonObject().has(key) && page.getAsJsonObject().get(key).isJsonPrimitive())
                        {
                            theirs.addAll(runs(page.getAsJsonObject().get(key).getAsString(), 8));
                        }
                    }
                }
                for (Page page : entry.pages())
                {
                    for (L text : page.words().values())
                    {
                        for (String run : runs(text.in(book.getKey()), 8))
                        {
                            if (theirs.contains(run))
                            {
                                copied.add(entry.id() + " (" + book.getKey() + "): \"" + run + "\"");
                            }
                        }
                    }
                }
            }
        }
        if (!copied.isEmpty())
        {
            try
            {
                Files.writeString(Path.of("build/guide-shared-wording.txt"), String.join("\n", copied), StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }
        }
        assertTrue(copied.isEmpty(), "Wording shared with TFC's text of the same entry (all of it in build/guide-shared-wording.txt):\n" + String.join("\n", copied.subList(0, Math.min(copied.size(), 20))));
    }

    private static Set<String> runs(String text, int length)
    {
        final List<String> words = new ArrayList<>();
        final Matcher matcher = Pattern.compile("[\\p{L}\\p{N}]+").matcher(MACRO.matcher(text).replaceAll(" ").toLowerCase());
        while (matcher.find())
        {
            words.add(matcher.group());
        }
        final Set<String> runs = new HashSet<>();
        for (int i = 0; i + length <= words.size(); i++)
        {
            runs.add(String.join(" ", words.subList(i, i + length)));
        }
        return runs;
    }

    // ---------------------------------------------------------------- numbers

    /** The figures the guide gives for fuels and alloys are those of the data the mod generates, not a memory of them. */
    @Test
    void figuresOfFuelsAndAlloysAreThoseOfTheData() throws IOException
    {
        final String forge = allText("metalworking/charcoal_forge");
        for (String fuel : List.of("data/tfc/tfc/fuel/charcoal.json", "data/tfc/tfc/fuel/coal.json", "data/tfc/tfc/fuel/ore/lignite.json", "data/tfcpp/tfc/fuel/coke.json", "data/tfcpp/tfc/fuel/lignite_char.json"))
        {
            final int temperature = (int) generated(fuel).get("temperature").getAsFloat();
            assertTrue(forge.contains(temperature + " °C"), "The forge page does not give " + temperature + " °C of " + fuel);
        }
        final JsonObject brass = generated("data/tfc/recipe/alloy/brass.json");
        final JsonObject copper = brass.getAsJsonArray("contents").get(0).getAsJsonObject();
        final String alloys = allText("getting_started/primitive_alloys");
        assertTrue(alloys.contains(percent(copper.get("min").getAsFloat()) + " to " + percent(copper.get("max").getAsFloat()) + " %"), "Brass copper share");
        final JsonObject sterling = generated("data/tfc/recipe/alloy/sterling_silver.json").getAsJsonArray("contents").get(1).getAsJsonObject();
        assertTrue(alloys.contains(percent(sterling.get("min").getAsFloat()) + " to " + percent(sterling.get("max").getAsFloat()) + " %"), "Sterling silver share");
        final int oxideMelt = (int) generated("data/tfcpp/tfc/fluid_heat/iron_oxide.json").get("melt_temperature").getAsFloat();
        assertTrue(allText("the_world/ores_and_minerals").contains(oxideMelt + " °C"), "Melting point of iron oxide");
        assertTrue(allText("metalworking/blast_furnace").contains(oxideMelt + " °C"), "Melting point of iron oxide at the furnace");
        assertEquals(1050, (int) generated("data/tfcpp/tfc/fluid_heat/arsenical_copper.json").get("melt_temperature").getAsFloat());
        assertTrue(allText("the_world/ores_and_minerals").contains("1050 °C"), "Melting point of arsenical copper");
        final int pitKiln = 900;
        assertTrue(allText("getting_started/pit_kiln").contains(pitKiln + " °C"));
        final int fireClay = (int) generated("data/tfc/recipe/heating/crucible.json").get("temperature").getAsFloat();
        assertTrue(allText("metalworking/fire_clay").contains(fireClay + " °C") && allText("metalworking/crucible").contains(fireClay + " °C"), "Firing of fire clay ware");
        final int clay = (int) generated("data/tfc/recipe/heating/brick.json").get("temperature").getAsFloat();
        assertTrue(allText("getting_started/pottery").contains(clay + " °C"), "Firing of clay ware");
    }

    private static String percent(float share)
    {
        return Integer.toString(Math.round(share * 100f));
    }

    private static JsonObject generated(String path) throws IOException
    {
        return JsonParser.parseString(Files.readString(Path.of("src/generated/resources/" + path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String allText(String entry)
    {
        final StringBuilder text = new StringBuilder();
        OURS.get(entry).pages().forEach(page -> page.words().values().forEach(words -> text.append(words.en()).append(' ')));
        return text.toString();
    }

    @Test
    void theTfcppCategoryHasItsIntroduction()
    {
        assertTrue(OURS.containsKey("tfcpp/introduction"));
        assertEquals("tfc:tfcpp", OURS.get("tfcpp/introduction").category());
        assertEquals(1, GuideBook.categories().size());
        assertEquals("tfcpp", GuideBook.categories().get(0).id());
    }
}
