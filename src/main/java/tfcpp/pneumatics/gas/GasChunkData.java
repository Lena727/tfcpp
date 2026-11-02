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

    /** Moles of each gas that have condensed onto surfaces in this chunk. They are not gone: this is the dust a later process can collect. */
    private final float[] deposits = new float[Gas.COUNT];

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
    }

    public void setMobileContent(boolean mobile)
    {
        mobileContent = mobile;
    }

    /** @return {@code true} if any cell holds enough of something that can move to be worth stepping */
    public boolean holdsMobileContent()
    {
        for (float[] cell : cells.values())
        {
            if (GasMath.mobileContent(cell) >= GasSimulator.REMOVE_CELL_MOLES)
            {
                return true;
            }
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
        return (!cells.isEmpty() && mobileContent) || (facesAwake && hasBleedingFace());
    }

    public void addDeposit(Gas gas, float moles)
    {
        deposits[gas.ordinal()] += moles;
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
        }
        mobileContent = true; // Whoever asks for a cell this way is about to put something in it
        return cell;
    }

    public void remove(int key)
    {
        cells.remove(key);
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

        final int[] keys = new int[cells.size()];
        final int[] amounts = new int[cells.size() * Gas.COUNT];
        int index = 0;
        for (Int2ObjectMap.Entry<float[]> entry : cells.int2ObjectEntrySet())
        {
            keys[index] = entry.getIntKey();
            for (int i = 0; i < Gas.COUNT; i++)
            {
                amounts[index * Gas.COUNT + i] = Float.floatToRawIntBits(entry.getValue()[i]);
            }
            index++;
        }

        final CompoundTag tag = new CompoundTag();
        tag.put("species", species);
        tag.putIntArray("keys", keys);
        tag.putIntArray("amounts", amounts);

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

        final ListTag species = tag.getList("species", Tag.TAG_STRING);
        final int[] keys = tag.getIntArray("keys");
        final int[] amounts = tag.getIntArray("amounts");
        final int width = species.size();
        if (width == 0 || amounts.length != keys.length * width)
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
}
