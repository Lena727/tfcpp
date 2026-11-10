package tfcpp.health;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

/** The cesspits that stand in a chunk, as block positions, so that the lookup at the time of drinking is a scan of a few chunks and not of every block entity. Saved with the chunk. */
public final class CesspitChunkData
{
    public static final IAttachmentSerializer<CompoundTag, CesspitChunkData> SERIALIZER = new IAttachmentSerializer<>()
    {
        @Override
        public CesspitChunkData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider)
        {
            final CesspitChunkData data = new CesspitChunkData();
            for (long pos : tag.getLongArray("pits"))
            {
                data.pits.add(pos);
            }
            return data;
        }

        @Override
        @Nullable
        public CompoundTag write(CesspitChunkData data, HolderLookup.Provider provider)
        {
            if (data.pits.isEmpty())
            {
                return null;
            }
            final CompoundTag tag = new CompoundTag();
            tag.putLongArray("pits", new LongArrayList(data.pits).toLongArray());
            return tag;
        }
    };

    private final LongOpenHashSet pits = new LongOpenHashSet();

    public boolean add(long pos)
    {
        return pits.add(pos);
    }

    public boolean remove(long pos)
    {
        return pits.remove(pos);
    }

    public boolean isEmpty()
    {
        return pits.isEmpty();
    }

    /** @return the positions of the pits, as longs of {@code BlockPos#asLong} */
    public long[] positions()
    {
        return pits.toLongArray();
    }
}
