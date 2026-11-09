package tfcpp.ecology;

import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import tfcpp.core.TFCpp;

public final class EcologyAttachments
{
    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TFCpp.MOD_ID);

    /** The soil memory of a chunk. Saved with it, and not sent to clients: players are given no display of it. */
    public static final Supplier<AttachmentType<PollutionChunkData>> CHUNK = TYPES.register("pollution_chunk", () -> AttachmentType.builder(PollutionChunkData::new)
        .serialize(PollutionChunkData.SERIALIZER)
        .build());

    private EcologyAttachments() {}
}
