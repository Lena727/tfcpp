package tfcpp.pneumatics.gas;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkRotationTest
{
    private static final long A = 1L, B = 2L, C = 3L;

    /** Visits everything that is present once and keeps it. */
    private static List<Long> tickAll(ChunkRotation rotation, LongOpenHashSet present, boolean recheck)
    {
        final List<Long> visited = new ArrayList<>();
        rotation.tick(recheck, 100, present::contains, () -> true, chunk -> {
            visited.add(chunk);
            return ChunkRotation.Outcome.KEEP;
        });
        return visited;
    }

    @Test
    void aChunkThatIsAbsentIsParkedAndNotForgotten()
    {
        final ChunkRotation rotation = new ChunkRotation();
        rotation.activate(A);
        rotation.activate(B);
        final LongOpenHashSet present = new LongOpenHashSet(new long[] {B});

        assertEquals(List.of(B), tickAll(rotation, present, true));

        assertTrue(rotation.isParked(A), "A demoted chunk keeps its place, waiting");
        assertFalse(rotation.isQueued(A));
        assertTrue(rotation.isQueued(B));
    }

    @Test
    void aChunkThatComesBackWithoutAnyLoadEventIsSteppedAgain()
    {
        final ChunkRotation rotation = new ChunkRotation();
        rotation.activate(A);
        final LongOpenHashSet present = new LongOpenHashSet();

        assertEquals(List.of(), tickAll(rotation, present, true));
        assertTrue(rotation.isParked(A));

        present.add(A); // Promoted back to a full chunk: no ChunkEvent.Load is posted for that
        assertEquals(List.of(), tickAll(rotation, present, false), "It is looked for only when the parked are rechecked, about once a second");
        assertEquals(List.of(A), tickAll(rotation, present, true));
        assertFalse(rotation.isParked(A));
        assertTrue(rotation.isQueued(A));
    }

    @Test
    void anUnloadTakesAChunkOffForGoodWhereverItIs()
    {
        final ChunkRotation rotation = new ChunkRotation();
        rotation.activate(A);
        rotation.activate(B);
        tickAll(rotation, new LongOpenHashSet(new long[] {B}), true);

        rotation.deactivate(A);
        rotation.deactivate(B);

        assertEquals(0, rotation.activeCount());
        assertEquals(0, rotation.parkedCount());
    }

    @Test
    void chunksThatAreAbsentDoNotUseUpTheVisitsOfThePresent()
    {
        final ChunkRotation rotation = new ChunkRotation();
        for (long absent = 100; absent < 200; absent++)
        {
            rotation.activate(absent);
        }
        rotation.activate(A);

        final List<Long> visited = new ArrayList<>();
        rotation.tick(false, 1, chunk -> chunk == A, () -> true, chunk -> {
            visited.add(chunk);
            return ChunkRotation.Outcome.KEEP;
        });
        assertEquals(List.of(A), visited, "The one chunk with gas in it gets its turn at once, not after a hundred that are not there");
    }

    @Test
    void dropRemovesAChunkAndKeepRotatesIt()
    {
        final ChunkRotation rotation = new ChunkRotation();
        rotation.activate(A);
        rotation.activate(B);
        rotation.activate(C);
        final LongOpenHashSet present = new LongOpenHashSet(new long[] {A, B, C});

        final List<Long> order = new ArrayList<>();
        rotation.tick(false, 2, present::contains, () -> true, chunk -> {
            order.add(chunk);
            return chunk == A ? ChunkRotation.Outcome.DROP : ChunkRotation.Outcome.KEEP;
        });

        assertEquals(List.of(A, B), order);
        assertFalse(rotation.isQueued(A), "Dropped: nothing in it moves");
        assertEquals(2, rotation.activeCount());
        assertEquals(List.of(C, B), tickAll(rotation, present, false), "B went to the back, behind C");
    }

    @Test
    void theBudgetStopsTheWalk()
    {
        final ChunkRotation rotation = new ChunkRotation();
        rotation.activate(A);
        rotation.activate(B);
        final int[] left = {1};
        final List<Long> visited = new ArrayList<>();
        rotation.tick(false, 10, chunk -> true, () -> left[0] > 0, chunk -> {
            visited.add(chunk);
            left[0]--;
            return ChunkRotation.Outcome.KEEP;
        });
        assertEquals(List.of(A), visited);
    }
}
