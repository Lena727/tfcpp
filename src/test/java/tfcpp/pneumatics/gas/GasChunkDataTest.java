package tfcpp.pneumatics.gas;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GasChunkDataTest
{
    @Test
    void aNegativeAmountThatWasSavedIsCleanedWhenTheChunkLoads()
    {
        final GasChunkData stored = new GasChunkData();
        final int key = GasChunkData.key(3, 5, 7);
        final float[] cell = stored.getOrCreate(key);
        cell[Gas.FLUE_NITROGEN.ordinal()] = -4e-8f; // What the burning of a sulfide and ammonia used to leave behind
        cell[Gas.CARBON_DIOXIDE.ordinal()] = 2.5f;
        cell[Gas.AMMONIA.ordinal()] = Float.NaN;
        stored.addDeposit(Gas.ARSENIC_TRIOXIDE, -1f);

        final CompoundTag tag = stored.save();
        final GasChunkData loaded = new GasChunkData();
        loaded.load(tag);

        final float[] after = loaded.get(key);
        assertNotNull(after);
        assertEquals(0f, after[Gas.FLUE_NITROGEN.ordinal()], "A negative amount is nothing");
        assertEquals(0f, after[Gas.AMMONIA.ordinal()], "So is a number that is not one");
        assertEquals(2.5f, after[Gas.CARBON_DIOXIDE.ordinal()], 1e-6f, "Everything else stays as it was");
        assertEquals(0f, loaded.deposit(Gas.ARSENIC_TRIOXIDE), "A deposit cannot be below nothing either");
    }

    /** The way a chunk was saved before cells were written sparsely: every species of every cell, in the order of the list of species. */
    private static CompoundTag denseTag(int[] keys, float[][] cells)
    {
        final ListTag species = new ListTag();
        for (Gas gas : Gas.VALUES)
        {
            species.add(StringTag.valueOf(gas.id()));
        }
        final int[] amounts = new int[keys.length * Gas.COUNT];
        for (int c = 0; c < keys.length; c++)
        {
            for (int i = 0; i < Gas.COUNT; i++)
            {
                amounts[c * Gas.COUNT + i] = Float.floatToRawIntBits(cells[c][i]);
            }
        }
        final CompoundTag tag = new CompoundTag();
        tag.put("species", species);
        tag.putIntArray("keys", keys);
        tag.putIntArray("amounts", amounts);
        return tag;
    }

    @Test
    void aChunkSavedWithEverySpeciesOfEveryCellStillLoads()
    {
        final int a = GasChunkData.key(1, 2, 3), b = GasChunkData.key(9, -5, 14);
        final float[] first = new float[Gas.COUNT], second = new float[Gas.COUNT];
        first[Gas.METHANE.ordinal()] = 3.5f;
        first[Gas.SETTLED_COAL_DUST.ordinal()] = 0.25f;
        second[Gas.CARBON_DIOXIDE.ordinal()] = 8f;
        second[Gas.MERCURY_VAPOR.ordinal()] = 5e-5f;

        final GasChunkData loaded = new GasChunkData();
        loaded.load(denseTag(new int[] {a, b}, new float[][] {first, second}));

        assertEquals(2, loaded.size());
        assertArrayEquals(first, loaded.get(a));
        assertArrayEquals(second, loaded.get(b));
    }

    @Test
    void aChunkIsSavedWithOnlyWhatItsCellsHold()
    {
        final GasChunkData data = new GasChunkData();
        final int a = GasChunkData.key(1, 2, 3), b = GasChunkData.key(4, 70, 5), empty = GasChunkData.key(7, 7, 7);
        data.getOrCreate(a)[Gas.METHANE.ordinal()] = 3.5f;
        data.getOrCreate(a)[Gas.CARBON_DIOXIDE.ordinal()] = 1.5f;
        data.getOrCreate(b)[Gas.HYDROGEN_SULFIDE.ordinal()] = 0.002f;
        data.getOrCreate(empty);

        final CompoundTag tag = data.save();

        assertFalse(tag.contains("amounts"), "The dense copy is not written any more");
        assertEquals(3, tag.getIntArray("keys").length);
        assertEquals(3, tag.getIntArray("slots").length, "Three species are held in all, and nothing else is written");
        assertEquals(3, tag.getIntArray("values").length);
        assertTrue(tag.getIntArray("slots").length < tag.getIntArray("keys").length * Gas.COUNT);

        final GasChunkData loaded = new GasChunkData();
        loaded.load(tag);
        assertEquals(3, loaded.size());
        assertArrayEquals(data.get(a), loaded.get(a));
        assertArrayEquals(data.get(b), loaded.get(b));
        assertArrayEquals(new float[Gas.COUNT], loaded.get(empty), "A cell with nothing in it is kept, and is empty");
    }

    @Test
    void aChunkLoadedFromTheOldFormatIsSavedInTheNewOne()
    {
        final int key = GasChunkData.key(3, 3, 3);
        final float[] cell = new float[Gas.COUNT];
        cell[Gas.AMMONIA.ordinal()] = 0.75f;
        final GasChunkData old = new GasChunkData();
        old.load(denseTag(new int[] {key}, new float[][] {cell}));

        final CompoundTag tag = old.save();
        assertTrue(tag.contains("counts") && !tag.contains("amounts"));
        final GasChunkData again = new GasChunkData();
        again.load(tag);
        assertArrayEquals(cell, again.get(key));
    }

    @Test
    void aBrokenSparseChunkLoadsAsEmptyAndKeepsItsDeposits()
    {
        final GasChunkData data = new GasChunkData();
        data.getOrCreate(GasChunkData.key(1, 1, 1))[Gas.METHANE.ordinal()] = 2f;
        data.addDeposit(Gas.LEAD_OXIDE_FUME, 4f);
        final CompoundTag tag = data.save();
        tag.putIntArray("values", new int[0]); // Cut short

        final GasChunkData loaded = new GasChunkData();
        assertDoesNotThrow(() -> loaded.load(tag));
        assertTrue(loaded.isEmpty());
        assertEquals(4f, loaded.deposit(Gas.LEAD_OXIDE_FUME));
    }

    // The film of mercury

    @Test
    void aFilmIsKeptWhereItLayAndGivenBackOnlyThere()
    {
        final GasChunkData data = new GasChunkData();
        final int here = GasChunkData.key(1, 1, 1), elsewhere = GasChunkData.key(9, 1, 9);
        data.addDeposit(Gas.MERCURY_VAPOR, 1e-3f, here);
        data.addDeposit(Gas.ARSENIC_TRIOXIDE, 2f, here);

        assertTrue(data.hasFilm() && data.holdsFilm(here) && !data.holdsFilm(elsewhere));
        assertEquals(0f, data.takeFilm(Gas.MERCURY_VAPOR, 1f, elsewhere), "Another room has none of it");
        assertEquals(4e-4f, data.takeFilm(Gas.MERCURY_VAPOR, 4e-4f, here), 1e-9f);
        assertEquals(6e-4f, data.deposit(Gas.MERCURY_VAPOR), 1e-9f, "What is taken is gone from the deposit and nowhere else");
        assertEquals(6e-4f, data.takeFilm(Gas.MERCURY_VAPOR, 1f, here), 1e-9f, "No more than there is");
        assertFalse(data.hasFilm(), "and an empty film is no film");
        assertFalse(data.holdsFilm(here));
        assertEquals(2f, data.deposit(Gas.ARSENIC_TRIOXIDE), "The dust is not touched");
    }

    @Test
    void aFilmKeepsAChunkActiveEvenWithNoCellsInIt()
    {
        final GasChunkData data = new GasChunkData();
        data.addDeposit(Gas.MERCURY_VAPOR, 1e-3f, GasChunkData.key(1, 1, 1));
        assertTrue(data.isEmpty());
        assertTrue(data.isActive(), "The air that was cleared is to be given its vapour again");
        assertTrue(data.holdsMobileContent());

        final GasChunkData dust = new GasChunkData();
        dust.addDeposit(Gas.ARSENIC_TRIOXIDE, 1f, GasChunkData.key(1, 1, 1));
        assertFalse(dust.isActive(), "but a heap of dust does nothing");
    }

    @Test
    void aFilmSurvivesSavingAndLoading()
    {
        final GasChunkData data = new GasChunkData();
        final int here = GasChunkData.key(5, 6, 7);
        data.addDeposit(Gas.MERCURY_VAPOR, 2e-3f, here);

        final CompoundTag tag = data.save();
        final GasChunkData loaded = new GasChunkData();
        loaded.load(tag);

        assertEquals(2e-3f, loaded.deposit(Gas.MERCURY_VAPOR), 1e-9f);
        assertTrue(loaded.holdsFilm(here));
        assertArrayEquals(new int[] {here}, loaded.filmSiteKeys());
    }

    // What counts as a change worth saving

    @Test
    void smallChangesAddUpUntilTheyAreWorthSaving()
    {
        final GasChunkData data = new GasChunkData();
        assertFalse(data.accumulateChange(4e-7f, 1e-6f));
        assertFalse(data.accumulateChange(4e-7f, 1e-6f));
        assertTrue(data.accumulateChange(4e-7f, 1e-6f), "Steps too small to save one by one are not allowed to leave the saved chunk behind for good");
        assertFalse(data.accumulateChange(0f, 1e-6f));
        assertTrue(data.accumulateChange(5e-6f, 1e-6f), "and a large one is saved at once");
    }

    @Test
    void aChunkThatRestsHoldsGasAndIsNotActive()
    {
        final GasChunkData data = new GasChunkData();
        data.getOrCreate(GasChunkData.key(1, 1, 1))[Gas.CARBON_DIOXIDE.ordinal()] = 2f;
        assertTrue(data.isActive());
        assertFalse(data.isResting());

        data.rest();
        assertFalse(data.isActive());
        assertTrue(data.isResting());
        assertFalse(data.isEmpty());

        data.wake();
        assertTrue(data.isActive());
        assertFalse(data.isResting());

        data.rest();
        data.getOrCreate(GasChunkData.key(2, 1, 1));
        assertTrue(data.isActive(), "A new cell wakes it like any other");
    }

    @Test
    void makingAndTakingCellsIsCounted()
    {
        final GasChunkData data = new GasChunkData();
        final int before = data.structureVersion();
        final int key = GasChunkData.key(1, 1, 1);
        data.getOrCreate(key);
        assertNotEquals(before, data.structureVersion());
        final int made = data.structureVersion();
        data.getOrCreate(key);
        assertEquals(made, data.structureVersion(), "Asking again for the same cell makes nothing");
        data.remove(key);
        assertNotEquals(made, data.structureVersion());
    }
}
