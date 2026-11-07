package tfcpp.compat.guide;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything TFC++ puts into TFC's field guide. To add an entry: write it in one of the lists below (or a new class of the same shape and add it here) with {@link Entry#of} and the page methods of {@link Entry}, run {@code runData}, and {@code GuideDataTest} checks it.
 * <p>
 * Part of it replaces TFC's own entries (same id, category and sort number as TFC has them), and part is new, in the TFC++ category.
 */
public final class GuideBook
{
    /** The languages the guide is written in. TFC's other translations keep TFC's text, which for the entries replaced here is out of date. */
    public static final List<String> LANGUAGES = List.of(L.ENGLISH, L.RUSSIAN);

    /** TFC's categories, which TFC's entries and ours name. */
    public static final List<String> TFC_CATEGORIES = List.of("core_mechanics", "crafting", "food", "getting_started", "mechanics", "metalworking", "the_world");

    public static List<Category> categories()
    {
        return TfcppGuide.categories();
    }

    public static List<Entry> entries()
    {
        final List<Entry> entries = new ArrayList<>();
        entries.addAll(GettingStartedGuide.entries());
        entries.addAll(MetalworkingGuide.entries());
        entries.addAll(CraftingGuide.entries());
        entries.addAll(MechanicsGuide.entries());
        entries.addAll(WorldGuide.entries());
        entries.addAll(TfcppGuide.entries());
        return entries;
    }

    private GuideBook() {}
}
