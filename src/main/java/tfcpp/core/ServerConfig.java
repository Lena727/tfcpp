package tfcpp.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Technical performance limits only. Physics and difficulty are deliberately not configurable.
 */
public final class ServerConfig
{
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue GAS_CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue GAS_CELLS_PER_TICK;
    public static final ModConfigSpec.IntValue GAS_CELLS_PER_CHUNK;

    static
    {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        GAS_CHUNKS_PER_TICK = builder
            .comment("Most chunks of gas to simulate in one tick. If the server cannot keep up gas moves more slowly, but none is lost.")
            .defineInRange("gasChunksPerTick", 64, 1, 4096);
        GAS_CELLS_PER_TICK = builder
            .comment("Most gas cells to simulate in one tick.")
            .defineInRange("gasCellsPerTick", 6000, 16, 1_000_000);
        GAS_CELLS_PER_CHUNK = builder
            .comment("Most gas cells a chunk may store. Gas stops spreading into new cells of a full chunk.")
            .defineInRange("gasCellsPerChunk", 8192, 64, 98304);

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
