package tfcpp.health;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

/** Saves the diseases of a person. */
final class DiseaseSerializer implements IAttachmentSerializer<CompoundTag, DiseaseState>
{
    static final DiseaseSerializer INSTANCE = new DiseaseSerializer();

    @Override
    public DiseaseState read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider)
    {
        final DiseaseState state = new DiseaseState();
        state.lastExcretion = tag.getLong("lastExcretion");
        state.lastSymptoms = tag.getLong("lastSymptoms");
        for (Disease disease : Disease.VALUES)
        {
            state.setImmuneUntil(disease, tag.getLong("immune_" + disease.id()));
            if (tag.contains(disease.id()))
            {
                final CompoundTag course = tag.getCompound(disease.id());
                state.setCourse(disease, new DiseaseState.Course(disease, course.getLong("infectedAt"), course.getLong("symptomsAt"), course.getLong("illnessEnds"), course.getLong("sheddingEnds"), course.getBoolean("resolved"), course.getBoolean("decided"), course.getBoolean("doomed")));
            }
        }
        return state;
    }

    @Override
    @Nullable
    public CompoundTag write(DiseaseState state, HolderLookup.Provider provider)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("lastExcretion", state.lastExcretion);
        tag.putLong("lastSymptoms", state.lastSymptoms);
        for (Disease disease : Disease.VALUES)
        {
            tag.putLong("immune_" + disease.id(), state.immuneUntil(disease));
            final DiseaseState.Course course = state.course(disease);
            if (course != null)
            {
                final CompoundTag out = new CompoundTag();
                out.putLong("infectedAt", course.infectedAt);
                out.putLong("symptomsAt", course.symptomsAt);
                out.putLong("illnessEnds", course.illnessEnds);
                out.putLong("sheddingEnds", course.sheddingEnds);
                out.putBoolean("resolved", course.resolved);
                out.putBoolean("decided", course.decided);
                out.putBoolean("doomed", course.doomed);
                tag.put(disease.id(), out);
            }
        }
        return tag;
    }
}
