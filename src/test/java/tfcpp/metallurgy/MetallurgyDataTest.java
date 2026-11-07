package tfcpp.metallurgy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetallurgyDataTest
{
    private static JsonObject json(String text)
    {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void aResultStackIsMarkedWithoutChangingTheOriginal()
    {
        final JsonObject original = json("{\"count\":2,\"id\":\"tfc:metal/rod/copper\"}");
        final JsonObject marked = MetallurgyData.marked(original);
        assertTrue(marked.getAsJsonObject("components").has("tfcpp:arsenical"));
        assertEquals("tfc:metal/rod/copper", marked.get("id").getAsString());
        assertEquals(2, marked.get("count").getAsInt());
        assertFalse(original.has("components"), "The recipe of pure copper must be left as it was");
    }

    @Test
    void aProviderResultIsMarkedInItsStack()
    {
        final JsonObject marked = MetallurgyData.marked(json("{\"stack\":{\"id\":\"tfc:metal/rod/copper\",\"count\":1},\"modifiers\":[]}"));
        assertTrue(marked.getAsJsonObject("stack").getAsJsonObject("components").has("tfcpp:arsenical"));
        assertFalse(marked.has("components"));
    }

    @Test
    void theMarkedAndTheUnmarkedIngredientsPartitionTheSameItems()
    {
        final JsonObject tag = json("{\"tag\":\"c:sheets/copper\"}");
        final JsonObject marked = MetallurgyData.markedIngredient(tag);
        assertEquals("neoforge:components", marked.get("type").getAsString());
        assertEquals("tfc:metal/sheet/copper", marked.get("items").getAsString(), "Named, as a tag is empty when the recipe is read");
        assertTrue(marked.getAsJsonObject("components").has("tfcpp:arsenical"));

        final JsonObject unmarked = MetallurgyData.unmarkedIngredient(tag);
        assertEquals("neoforge:difference", unmarked.get("type").getAsString());
        assertEquals(tag, unmarked.getAsJsonObject("base"));
        assertEquals(marked, unmarked.getAsJsonObject("subtracted"), "What is taken away is exactly what the other recipe takes, so no stack matches both");

        assertEquals("tfc:metal/rod/copper", MetallurgyData.markedIngredient(json("{\"item\":\"tfc:metal/rod/copper\"}")).get("items").getAsString());
        assertThrows(IllegalArgumentException.class, () -> MetallurgyData.markedIngredient(json("{\"type\":\"neoforge:compound\"}")));
    }

    @Test
    void ingotsAreNotCopperItemsThatCarryTheMark()
    {
        assertTrue(MetallurgyData.isCopperItems(json("{\"tag\":\"c:sheets/copper\"}")));
        assertTrue(MetallurgyData.isCopperItems(json("{\"item\":\"tfc:metal/unfinished_lamp/copper\"}")));
        assertFalse(MetallurgyData.isCopperItems(json("{\"tag\":\"c:ingots/copper\"}")), "Arsenical ingots are items of their own");
        assertFalse(MetallurgyData.isCopperItems(json("{\"tag\":\"c:sheets/steel\"}")));
    }
}
