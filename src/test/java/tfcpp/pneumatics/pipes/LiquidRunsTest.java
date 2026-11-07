package tfcpp.pneumatics.pipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiquidRunsTest
{
    private static final String WATER = "minecraft:water", ACID = "tfcpp:chamber_acid";

    /** Pipes in a row, each joined to the next, with these liquids (an empty string for none). */
    private static Set<Integer> network(int start, String... liquids)
    {
        final Map<Integer, List<Integer>> edges = new HashMap<>();
        for (int i = 0; i < liquids.length; i++)
        {
            final List<Integer> next = new ArrayList<>();
            if (i > 0)
            {
                next.add(i - 1);
            }
            if (i < liquids.length - 1)
            {
                next.add(i + 1);
            }
            edges.put(i, next);
        }
        return new HashSet<>(LiquidRuns.network(start, edges::get, node -> liquids[node]));
    }

    @Test
    void pipesWithNoLiquidAreOneNetwork()
    {
        assertEquals(Set.of(0, 1, 2, 3), network(2, "", "", "", ""));
    }

    @Test
    void sameLiquidJoinsAndDifferentLiquidsDoNot()
    {
        assertEquals(Set.of(0, 1), network(0, WATER, WATER, ACID, ACID));
        assertEquals(Set.of(2, 3), network(3, WATER, WATER, ACID, ACID));
    }

    @Test
    void anEmptyPipeBetweenTwoLiquidsGoesWithTheFirstInTheAlphabetWhereverTheRebuildBegins()
    {
        // minecraft:water comes before tfcpp:chamber_acid in the alphabet, on whichever side it stands
        final String[] line = {ACID, "", WATER};
        assertEquals(Set.of(0), network(0, line), "The acid is on its own");
        assertEquals(Set.of(1, 2), network(1, line), "The pipe between them is with the water, begun from it");
        assertEquals(Set.of(1, 2), network(2, line), "and begun from the water");

        final String[] turned = {WATER, "", ACID};
        assertEquals(Set.of(0, 1), network(0, turned));
        assertEquals(Set.of(0, 1), network(1, turned));
        assertEquals(Set.of(2), network(2, turned));
    }

    @Test
    void aRunOfEmptyPipesGoesWithOneLiquidAsAWhole()
    {
        final String[] line = {ACID, "", "", WATER};
        assertEquals(Set.of(1, 2, 3), network(1, line));
        assertEquals(Set.of(1, 2, 3), network(2, line));
        assertEquals(Set.of(1, 2, 3), network(3, line));
        assertEquals(Set.of(0), network(0, line));
    }

    @Test
    void emptyPipesJoinTwoRunsOfTheSameLiquidIntoOne()
    {
        assertEquals(Set.of(0, 1, 2), network(0, WATER, "", WATER));
        assertEquals(Set.of(0, 1, 2), network(2, WATER, "", WATER));
    }
}
