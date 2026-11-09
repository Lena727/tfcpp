package tfcpp.ecology;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * The soil memory of one chunk: its one {@link SoilCell}, which is made when something is first put into the chunk and not before. A chunk that nothing has touched saves nothing.
 */
public final class PollutionChunkData
{
    public static final IAttachmentSerializer<CompoundTag, PollutionChunkData> SERIALIZER = new IAttachmentSerializer<>()
    {
        @Override
        public PollutionChunkData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider)
        {
            final PollutionChunkData data = new PollutionChunkData();
            data.cell = load(tag);
            return data;
        }

        @Override
        @Nullable
        public CompoundTag write(PollutionChunkData data, HolderLookup.Provider provider)
        {
            return data.cell == null ? null : save(data.cell);
        }
    };

    private @Nullable SoilCell cell;

    @Nullable
    public SoilCell cell()
    {
        return cell;
    }

    public void set(SoilCell cell)
    {
        this.cell = cell;
    }

    static CompoundTag save(SoilCell cell)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putDouble("carbonate", cell.carbonate);
        tag.putDouble("baseSaturation", cell.baseSaturation);
        tag.putDouble("startSaturation", cell.startSaturation);
        tag.putDouble("aluminium", cell.aluminium);
        tag.putDouble("cec", cell.cec);
        tag.putDouble("carbonateSpent", cell.carbonateSpent);
        tag.putDouble("weathered", cell.weathered);
        tag.putLong("lastTick", cell.lastTick);
        // By name, so that a kind added to the enum later does not shift the others
        final CompoundTag input = new CompoundTag(), present = new CompoundTag(), decayed = new CompoundTag(), claimed = new CompoundTag();
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            input.putDouble(pollutant.id(), cell.input[pollutant.ordinal()]);
            present.putDouble(pollutant.id(), cell.present[pollutant.ordinal()]);
            decayed.putDouble(pollutant.id(), cell.decayed[pollutant.ordinal()]);
            claimed.putDouble(pollutant.id(), cell.claimed[pollutant.ordinal()]);
        }
        tag.put("input", input);
        tag.put("present", present);
        tag.put("decayed", decayed);
        tag.put("claimed", claimed);
        return tag;
    }

    static SoilCell load(CompoundTag tag)
    {
        final SoilCell cell = new SoilCell();
        cell.carbonate = tag.getDouble("carbonate");
        cell.baseSaturation = tag.getDouble("baseSaturation");
        cell.startSaturation = tag.getDouble("startSaturation");
        cell.aluminium = tag.getDouble("aluminium");
        cell.cec = tag.getDouble("cec");
        cell.carbonateSpent = tag.getDouble("carbonateSpent");
        cell.weathered = tag.getDouble("weathered");
        cell.lastTick = tag.getLong("lastTick");
        final CompoundTag input = tag.getCompound("input"), present = tag.getCompound("present"), decayed = tag.getCompound("decayed"), claimed = tag.getCompound("claimed");
        for (Pollutant pollutant : Pollutant.VALUES)
        {
            cell.input[pollutant.ordinal()] = input.getDouble(pollutant.id());
            cell.present[pollutant.ordinal()] = present.getDouble(pollutant.id());
            cell.decayed[pollutant.ordinal()] = decayed.getDouble(pollutant.id());
            cell.claimed[pollutant.ordinal()] = claimed.getDouble(pollutant.id());
        }
        return cell;
    }
}
