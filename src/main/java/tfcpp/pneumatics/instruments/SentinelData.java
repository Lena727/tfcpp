package tfcpp.pneumatics.instruments;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The animal in a cage.
 *
 * @param carboxyhemoglobin share of its haemoglobin bound by CO, in percent
 * @param fedUntil          calendar tick at which it starves
 */
public record SentinelData(float carboxyhemoglobin, long fedUntil)
{
    public static final Codec<SentinelData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.FLOAT.fieldOf("carboxyhemoglobin").forGetter(SentinelData::carboxyhemoglobin),
        Codec.LONG.fieldOf("fed_until").forGetter(SentinelData::fedUntil)
    ).apply(i, SentinelData::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SentinelData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, SentinelData::carboxyhemoglobin,
        ByteBufCodecs.VAR_LONG, SentinelData::fedUntil,
        SentinelData::new
    );
}
