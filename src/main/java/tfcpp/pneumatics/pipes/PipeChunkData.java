package tfcpp.pneumatics.pipes;

import java.util.Collection;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

/** The pipe segments of one chunk. What each holds is worked out when saving, as its share of its network at that moment. */
public final class PipeChunkData
{
    public static final IAttachmentSerializer<CompoundTag, PipeChunkData> SERIALIZER = new IAttachmentSerializer<>()
    {
        @Override
        public PipeChunkData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider)
        {
            final PipeChunkData data = new PipeChunkData();
            data.load(tag);
            return data;
        }

        @Override
        @Nullable
        public CompoundTag write(PipeChunkData data, HolderLookup.Provider provider)
        {
            return data.segments.isEmpty() ? null : data.save();
        }
    };

    private final Long2ObjectOpenHashMap<PipeSegment> segments = new Long2ObjectOpenHashMap<>();

    public Collection<PipeSegment> segments()
    {
        return segments.values();
    }

    public void put(PipeSegment segment)
    {
        segments.put(segment.pos.asLong(), segment);
    }

    public void remove(BlockPos pos)
    {
        segments.remove(pos.asLong());
    }

    @Nullable
    public PipeSegment get(BlockPos pos)
    {
        return segments.get(pos.asLong());
    }

    public CompoundTag save()
    {
        final ListTag list = new ListTag();
        for (PipeSegment segment : segments.values())
        {
            final CompoundTag tag = new CompoundTag();
            tag.putLong("pos", segment.pos.asLong());
            tag.putFloat("volume", segment.volume);
            tag.putFloat("integrity", segment.integrity);
            tag.put("contents", segment.contents().save());
            list.add(tag);
        }
        final CompoundTag root = new CompoundTag();
        root.put("segments", list);
        return root;
    }

    public void load(CompoundTag root)
    {
        segments.clear();
        final ListTag list = root.getList("segments", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag tag = list.getCompound(i);
            final PipeSegment segment = new PipeSegment(BlockPos.of(tag.getLong("pos")), tag.getFloat("volume"), PipeContents.load(tag.getCompound("contents")));
            segment.integrity = tag.getFloat("integrity");
            put(segment);
        }
    }
}
