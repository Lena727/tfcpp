package tfcpp.pneumatics.gas;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * The gas cells of one chunk, stored sparsely: a position with nothing but air takes no memory.
 */
public final class GasChunkData
{
    private static final int Y_OFFSET = 2048;

    public static final IAttachmentSerializer<CompoundTag, GasChunkData> SERIALIZER = new IAttachmentSerializer<>()
    {
        @Override
        public GasChunkData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider)
        {
            final GasChunkData data = new GasChunkData();
            data.load(tag);
            return data;
        }

        @Override
        @Nullable
        public CompoundTag write(GasChunkData data, HolderLookup.Provider provider)
        {
            return data.isEmpty() && !data.hasDeposits() && data.seamFaces.isEmpty() && data.openedPockets.isEmpty() ? null : data.save();
        }
    };

    public static int key(int x, int y, int z)
    {
        return (y + Y_OFFSET) << 8 | (z & 15) << 4 | (x & 15);
    }

    public static int keyX(int key)
    {
        return key & 15;
    }

    public static int keyY(int key)
    {
        return (key >>> 8) - Y_OFFSET;
    }

    public static int keyZ(int key)
    {
        return (key >> 4) & 15;
    }

    private final Int2ObjectOpenHashMap<float[]> cells = new Int2ObjectOpenHashMap<>();

    public long lastStepTick;
    public int stepIntervalTicks = 20;

    /** Moles of each gas that have condensed onto surfaces in this chunk. They are not gone: a film of mercury gives its vapour off again, see {@link #filmSites}, and the dust of an oxide stays where it fell. */
    private final float[] deposits = new float[Gas.COUNT];

    /** The gases whose deposit is a film that evaporates again. */
    private static final Gas[] FILM_GASES = java.util.Arrays.stream(Gas.VALUES).filter(Gas::evaporatesFromDeposit).toArray(Gas[]::new);

    /**
     * The positions where one of the {@link #FILM_GASES} condensed. The amount is kept for the chunk as a whole, as for any other deposit, and is given back to the air of these positions only: a chunk with a mercury spill in one room does not fill the air of every other room in it. A film lying where a cell was cleared by ventilation is why that cell has to be kept alive and stepped.
     */
    private final IntOpenHashSet filmSites = new IntOpenHashSet();

    /** Counted up whenever a cell is made or taken away, so that a pass can tell whether that happened in it. */
    private int structureVersion;
    /** What has changed since this chunk was last marked for saving, in the units of {@link GasSimulator#takeLargestChange()}. Not saved. */
    private float unsavedChange;
    /** Set while the chunk sleeps although it holds gas, so that the tracker looks at it again from time to time. Not saved. */
    private boolean resting;

    /** Coal faces opened by mining, and the moles of gas each still has to bleed. Faces that have run out stay here, so that their gas is not counted again. */
    private final Int2FloatOpenHashMap seamFaces = new Int2FloatOpenHashMap();
    /** Regions of rock whose gas pocket was already opened. */
    private final IntOpenHashSet openedPockets = new IntOpenHashSet();

    /** Cleared while every face with gas left is sealed behind other blocks, so that those do not keep a chunk active forever. */
    private boolean facesAwake = true;

    /**
     * Cleared when a full pass finds nothing that can move: a chunk holding only settled dust is saved and kept, but is not stepped any more. Set again whenever anything is added to it. Not saved, so a loaded chunk is looked at once.
     */
    private boolean mobileContent = true;

    /** Something that can move may have entered this chunk. */
    public void wake()
    {
        mobileContent = true;
        resting = false;
    }

    public void setMobileContent(boolean mobile)
    {
        mobileContent = mobile;
        resting = false;
    }

    /**
     * Puts the chunk to sleep although it holds gas, because nothing in it is changing. It is woken as a chunk holding only settled dust is, and the tracker also looks again after a while, as a door may have been opened or a wall broken without anything telling the gas.
     */
    public void rest()
    {
        mobileContent = false;
        resting = true;
    }

    /** @return {@code true} if anything has changed since the chunk was last marked for saving, however little */
    public boolean flushPendingChange()
    {
        final boolean pending = unsavedChange > 0;
        unsavedChange = 0;
        return pending;
    }

    /** @return {@code true} if the chunk sleeps although it holds gas that could move */
    public boolean isResting()
    {
        return resting && !mobileContent;
    }

    /** @return {@code true} if any cell holds enough of something that can move to be worth stepping, or a film is still giving off vapour */
    public boolean holdsMobileContent()
    {
        for (float[] cell : cells.values())
        {
            if (GasMath.holdsMobileContent(cell))
            {
                return true;
            }
        }
        return hasFilm();
    }

    public int structureVersion()
    {
        return structureVersion;
    }

    /**
     * @return {@code true} if what has changed in the chunk, added up over the steps since it was last marked for saving, has reached {@code threshold}. Many steps too small to matter on their own do not leave the saved copy behind for good.
     */
    public boolean accumulateChange(float change, float threshold)
    {
        unsavedChange += change;
        if (unsavedChange >= threshold)
        {
            unsavedChange = 0;
            return true;
        }
        return false;
    }

    public void addSeamFace(int key, float moles)
    {
        seamFaces.putIfAbsent(key, moles);
        facesAwake = true;
    }

    public void setFacesAwake(boolean awake)
    {
        facesAwake = awake;
    }

    public boolean hasSeamFace(int key)
    {
        return seamFaces.containsKey(key);
    }

    public float removeSeamFace(int key)
    {
        return seamFaces.remove(key);
    }

    public void setSeamFace(int key, float moles)
    {
        seamFaces.put(key, moles);
    }

    public float seamFace(int key)
    {
        return seamFaces.get(key);
    }

    public int[] seamFaceKeys()
    {
        return seamFaces.keySet().toIntArray();
    }

    /** @return {@code true} if a face still has gas to give, which keeps the chunk being visited */
    public boolean hasBleedingFace()
    {
        for (float moles : seamFaces.values())
        {
            if (moles > 0)
            {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} if this region had not been opened before */
    public boolean openPocket(int regionKey)
    {
        return openedPockets.add(regionKey);
    }

    /** @return {@code true} if this chunk has anything to simulate */
    public boolean isActive()
    {
        return ((!cells.isEmpty() || hasFilm()) && mobileContent) || (facesAwake && hasBleedingFace());
    }

    public void addDeposit(Gas gas, float moles)
    {
        deposits[gas.ordinal()] += moles;
    }

    /** A deposit that lay down at this cell. If it is a film it goes on giving off vapour there. */
    public void addDeposit(Gas gas, float moles, int cellKey)
    {
        deposits[gas.ordinal()] += moles;
        if (gas.evaporatesFromDeposit() && moles > 0)
        {
            filmSites.add(cellKey);
            mobileContent = true; // The air there is about to be given something
        }
    }

    /**
     * Takes vapour from a film. Whatever is taken is gone from the deposit, so that nothing is made and the whole stays in one place or the other.
     *
     * @return what was taken, no more than {@code wanted}, and nothing if no film lies at this cell
     */
    public float takeFilm(Gas gas, float wanted, int cellKey)
    {
        if (wanted <= 0 || !filmSites.contains(cellKey))
        {
            return 0;
        }
        final float taken = Math.min(wanted, deposits[gas.ordinal()]);
        deposits[gas.ordinal()] -= taken;
        if (deposits[gas.ordinal()] <= 0)
        {
            deposits[gas.ordinal()] = 0;
            if (!hasFilm())
            {
                filmSites.clear();
            }
        }
        return taken;
    }

    /** @return {@code true} if a film lies at this cell and has anything left to give */
    public boolean holdsFilm(int cellKey)
    {
        return filmSites.contains(cellKey) && hasFilm();
    }

    /** @return {@code true} if any film in the chunk has anything left to give */
    public boolean hasFilm()
    {
        if (filmSites.isEmpty())
        {
            return false;
        }
        for (Gas gas : FILM_GASES)
        {
            if (deposits[gas.ordinal()] > 0)
            {
                return true;
            }
        }
        return false;
    }

    public int[] filmSiteKeys()
    {
        return filmSites.toIntArray();
    }

    /** The position can no longer hold a film (a block was put there). What lay on it stays in the deposit of the chunk. */
    public void dropFilmSite(int cellKey)
    {
        filmSites.remove(cellKey);
    }

    public float deposit(Gas gas)
    {
        return deposits[gas.ordinal()];
    }

    private boolean hasDeposits()
    {
        for (float deposit : deposits)
        {
            if (deposit > 0)
            {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty()
    {
        return cells.isEmpty();
    }

    public int size()
    {
        return cells.size();
    }

    @Nullable
    public float[] get(int key)
    {
        return cells.get(key);
    }

    public float[] getOrCreate(int key)
    {
        float[] cell = cells.get(key);
        if (cell == null)
        {
            cell = new float[Gas.COUNT];
            cells.put(key, cell);
            structureVersion++;
        }
        mobileContent = true; // Whoever asks for a cell this way is about to put something in it
        resting = false;
        return cell;
    }

    public void remove(int key)
    {
        if (cells.remove(key) != null)
        {
            structureVersion++;
        }
    }

    public void clear()
    {
        cells.clear();
    }

    public int[] keys()
    {
        return cells.keySet().toIntArray();
    }

    public CompoundTag save()
    {
        final ListTag species = new ListTag();
        for (Gas gas : Gas.VALUES)
        {
            species.add(StringTag.valueOf(gas.id()));
        }

        // Only what a cell holds is written: most cells hold two or three of the species, and a dense copy of all of them was most of a chunk's save
        final int[] keys = new int[cells.size()];
        final int[] counts = new int[cells.size()];
        final IntArrayList slots = new IntArrayList();
        final IntArrayList values = new IntArrayList();
        int index = 0;
        for (Int2ObjectMap.Entry<float[]> entry : cells.int2ObjectEntrySet())
        {
            keys[index] = entry.getIntKey();
            final float[] cell = entry.getValue();
            int count = 0;
            for (int i = 0; i < Gas.COUNT; i++)
            {
                if (cell[i] > 0f) // Nothing, a rounding error below nothing, and a number that is not one all load as nothing
                {
                    slots.add(i);
                    values.add(Float.floatToRawIntBits(cell[i]));
                    count++;
                }
            }
            counts[index] = count;
            index++;
        }

        final CompoundTag tag = new CompoundTag();
        tag.put("species", species);
        tag.putIntArray("keys", keys);
        tag.putIntArray("counts", counts);
        tag.putIntArray("slots", slots.toIntArray());
        tag.putIntArray("values", values.toIntArray());
        tag.putIntArray("filmSites", filmSites.toIntArray());

        final int[] depositBits = new int[Gas.COUNT];
        for (int i = 0; i < Gas.COUNT; i++)
        {
            depositBits[i] = Float.floatToRawIntBits(deposits[i]);
        }
        tag.putIntArray("deposits", depositBits);

        final int[] faceKeys = seamFaceKeys();
        final int[] faceBits = new int[faceKeys.length];
        for (int i = 0; i < faceKeys.length; i++)
        {
            faceBits[i] = Float.floatToRawIntBits(seamFaces.get(faceKeys[i]));
        }
        tag.putIntArray("seamFaces", faceKeys);
        tag.putIntArray("seamFaceMoles", faceBits);
        tag.putIntArray("pockets", openedPockets.toIntArray());
        return tag;
    }

    /** A negative or not-a-number amount of gas can only be an error that an older version saved: it is stored as nothing, so it cannot go on breaking every step. */
    private static float sanitized(float amount)
    {
        return amount > 0f ? amount : 0f;
    }

    /** Species are stored by name, so that adding a gas in a later version does not corrupt saved chunks. */
    public void load(CompoundTag tag)
    {
        cells.clear();

        final int[] faceKeys = tag.getIntArray("seamFaces");
        final int[] faceBits = tag.getIntArray("seamFaceMoles");
        for (int i = 0; i < faceKeys.length && i < faceBits.length; i++)
        {
            seamFaces.put(faceKeys[i], Float.intBitsToFloat(faceBits[i]));
        }
        openedPockets.addAll(new IntArrayList(tag.getIntArray("pockets")));
        filmSites.addAll(new IntArrayList(tag.getIntArray("filmSites")));

        final ListTag species = tag.getList("species", Tag.TAG_STRING);
        final int[] keys = tag.getIntArray("keys");
        final int width = species.size();
        if (width == 0)
        {
            return;
        }

        final Gas[] order = new Gas[width];
        for (int i = 0; i < width; i++)
        {
            order[i] = Gas.byId(species.getString(i));
        }

        final int[] depositBits = tag.getIntArray("deposits");
        for (int i = 0; i < width && i < depositBits.length; i++)
        {
            if (order[i] != null)
            {
                deposits[order[i].ordinal()] = sanitized(Float.intBitsToFloat(depositBits[i]));
            }
        }

        if (tag.contains("counts"))
        {
            loadSparse(tag, keys, order);
        }
        else
        {
            loadDense(tag.getIntArray("amounts"), keys, order);
        }
    }

    /** The form written before cells were saved sparsely: every species of every cell, in the order of the list of species. */
    private void loadDense(int[] amounts, int[] keys, Gas[] order)
    {
        final int width = order.length;
        if (amounts.length != keys.length * width)
        {
            return;
        }
        for (int index = 0; index < keys.length; index++)
        {
            final float[] cell = new float[Gas.COUNT];
            for (int i = 0; i < width; i++)
            {
                if (order[i] != null)
                {
                    cell[order[i].ordinal()] = sanitized(Float.intBitsToFloat(amounts[index * width + i]));
                }
            }
            cells.put(keys[index], cell);
        }
    }

    /** For every cell, how many species it holds, and then for every one of them which species it is (an index in the list of species) and how much. */
    private void loadSparse(CompoundTag tag, int[] keys, Gas[] order)
    {
        final int[] counts = tag.getIntArray("counts");
        final int[] slots = tag.getIntArray("slots");
        final int[] values = tag.getIntArray("values");
        long total = 0;
        for (int count : counts)
        {
            total += count;
        }
        if (counts.length != keys.length || total != slots.length || slots.length != values.length)
        {
            return;
        }

        int at = 0;
        for (int index = 0; index < keys.length; index++)
        {
            final float[] cell = new float[Gas.COUNT];
            for (int n = 0; n < counts[index]; n++, at++)
            {
                final int slot = slots[at];
                if (slot >= 0 && slot < order.length && order[slot] != null)
                {
                    cell[order[slot].ordinal()] = sanitized(Float.intBitsToFloat(values[at]));
                }
            }
            cells.put(keys[index], cell);
        }
    }
}
