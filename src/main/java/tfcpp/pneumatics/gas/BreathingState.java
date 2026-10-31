package tfcpp.pneumatics.gas;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

/** What gas exposure has done to a player so far. */
public final class BreathingState
{
    public static final IAttachmentSerializer<CompoundTag, BreathingState> SERIALIZER = new IAttachmentSerializer<>()
    {
        @Override
        public BreathingState read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider)
        {
            final BreathingState state = new BreathingState();
            state.carboxyhemoglobin = tag.getFloat("coHb");
            return state;
        }

        @Override
        @Nullable
        public CompoundTag write(BreathingState state, HolderLookup.Provider provider)
        {
            final CompoundTag tag = new CompoundTag();
            tag.putFloat("coHb", state.carboxyhemoglobin);
            return tag;
        }
    };

    /** Share of haemoglobin bound by CO, in percent. */
    public float carboxyhemoglobin;
    /** Game tick before which no smell message is shown again. */
    public long nextSmellMessageTick;
}
