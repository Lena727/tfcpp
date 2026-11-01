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
            state.arsenicMg = tag.getFloat("arsenic");
            state.mercuryMg = tag.getFloat("mercury");
            state.swallowedMercuryMg = tag.getFloat("swallowedMercury");
            state.zincOxideMg = tag.getFloat("zincOxide");
            state.gauzeSeconds = tag.getFloat("gauze");
            state.lampBurnTicks = tag.getInt("lampBurn");
            return state;
        }

        @Override
        @Nullable
        public CompoundTag write(BreathingState state, HolderLookup.Provider provider)
        {
            final CompoundTag tag = new CompoundTag();
            tag.putFloat("coHb", state.carboxyhemoglobin);
            tag.putFloat("arsenic", state.arsenicMg);
            tag.putFloat("mercury", state.mercuryMg);
            tag.putFloat("swallowedMercury", state.swallowedMercuryMg);
            tag.putFloat("zincOxide", state.zincOxideMg);
            tag.putFloat("gauze", state.gauzeSeconds);
            tag.putInt("lampBurn", state.lampBurnTicks);
            return tag;
        }
    };

    /**
     * @return what carries over to a respawned player. Dying must not be a cure for slow poisoning, so those burdens stay, but only up to a level that cannot kill again and trap a player in a loop. Blood is fresh.
     */
    BreathingState afterDeath()
    {
        final BreathingState state = new BreathingState();
        state.arsenicMg = Physiology.BodyBurden.ARSENIC.survivable(arsenicMg);
        state.mercuryMg = Physiology.BodyBurden.MERCURY.survivable(mercuryMg);
        return state;
    }

    /** Share of haemoglobin bound by CO, in percent. */
    public float carboxyhemoglobin;
    /** Body burdens in milligrams. These leave the body slowly, so small exposures add up over days. */
    public float arsenicMg, mercuryMg, zincOxideMg;
    /** The dose of swallowed mercury still in the gut, on its way to {@link #mercuryMg}. A new body does not bring this back, or it would kill again. */
    public float swallowedMercuryMg;
    /** Game tick before which no smell message is shown again. */
    public long nextSmellMessageTick;
    /** Seconds the gauze of a carried lamp has spent with gas burning inside it. */
    public float gauzeSeconds;
    /** Ticks a carried lamp has burned since it last used up a millibucket of oil. */
    public int lampBurnTicks;
}
