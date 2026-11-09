package tfcpp.ecology;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * Which river edge is nearest to the middle of a chunk, and how far, once the river graph has been asked. It is a pure function of the seed, so it is kept with the chunk for good: a chunk resolved once is never unresolved again, and nothing has to be kept in memory for the chunks of the world. A chunk that was never asked saves nothing.
 */
public final class WaterChunkData
{
    public static final IAttachmentSerializer<CompoundTag, WaterChunkData> SERIALIZER = new IAttachmentSerializer<>()
    {
        @Override
        public WaterChunkData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider)
        {
            final WaterChunkData data = new WaterChunkData();
            data.resolved = tag.getBoolean("resolved");
            data.edge = tag.getLong("edge");
            data.distance = tag.contains("distance") ? tag.getDouble("distance") : Double.POSITIVE_INFINITY;
            return data;
        }

        @Override
        @Nullable
        public CompoundTag write(WaterChunkData data, HolderLookup.Provider provider)
        {
            if (!data.resolved)
            {
                return null;
            }
            final CompoundTag tag = new CompoundTag();
            tag.putBoolean("resolved", true);
            tag.putLong("edge", data.edge);
            if (Double.isFinite(data.distance))
            {
                tag.putDouble("distance", data.distance);
            }
            return tag;
        }
    };

    private boolean resolved;
    private long edge;
    private double distance = Double.POSITIVE_INFINITY;

    public boolean resolved()
    {
        return resolved;
    }

    /** @return the id of the nearest edge, {@link ReachInfo#SEA} if the graph has none */
    public long edge()
    {
        return edge;
    }

    /** @return blocks to it, infinite if none */
    public double distance()
    {
        return distance;
    }

    public void set(long edge, double distance)
    {
        this.resolved = true;
        this.edge = edge;
        this.distance = distance;
    }

    public void clear()
    {
        resolved = false;
    }
}
