package tfcpp.pneumatics.gas;

import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import tfcpp.core.TFCpp;

public final class GasAttachments
{
    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TFCpp.MOD_ID);

    public static final Supplier<AttachmentType<GasChunkData>> CHUNK = TYPES.register("gas_chunk", () -> AttachmentType.builder(GasChunkData::new)
        .serialize(GasChunkData.SERIALIZER)
        .build());

    /** Per-level runtime state. Rebuilt on load, so it is not saved. */
    public static final Supplier<AttachmentType<GasTracker>> TRACKER = TYPES.register("gas_tracker", () -> AttachmentType.builder(GasTracker::new).build());

    public static final Supplier<AttachmentType<BreathingState>> BREATHING = TYPES.register("breathing", () -> AttachmentType.builder(BreathingState::new)
        .serialize(BreathingState.SERIALIZER)
        .copyOnDeath()
        .copyHandler((state, holder, provider) -> state.afterDeath())
        .build());

    private GasAttachments() {}
}
