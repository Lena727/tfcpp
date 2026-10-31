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
    public static final ModConfigSpec.IntValue GAS_FLAME_CELLS;
    public static final ModConfigSpec.IntValue GAS_BLAST_CELLS;
    public static final ModConfigSpec.IntValue GAS_BLAST_BLOCKS;
    public static final ModConfigSpec.IntValue GAS_FAN_CELLS_PER_TICK;
    public static final ModConfigSpec.IntValue PIPE_REBUILD_SEGMENTS_PER_TICK;

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

        GAS_FLAME_CELLS = builder
            .comment("Most gas cells one flame can burn in a single tick. Any gas beyond this stays unburned, and can be lit again.")
            .defineInRange("gasFlameCells", 4096, 64, 65536);
        GAS_BLAST_CELLS = builder
            .comment("Most cells one blast can reach in a single tick, including those burned.")
            .defineInRange("gasBlastCells", 16384, 64, 262144);
        GAS_BLAST_BLOCKS = builder
            .comment("Most blocks one blast can break.")
            .defineInRange("gasBlastBlocks", 512, 0, 16384);

        GAS_FAN_CELLS_PER_TICK = builder
            .comment("Most cells of open space that mine fans may walk in one tick. A fan over budget waits for its next turn, and air moves more slowly.")
            .defineInRange("gasFanCellsPerTick", 2048, 512, 262144);

        PIPE_REBUILD_SEGMENTS_PER_TICK = builder
            .comment("Most pipe segments to join into networks in one tick after pipes change or chunks load. The rest wait, holding what they hold.")
            .defineInRange("pipeRebuildSegmentsPerTick", 4096, 64, 1_000_000);

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
