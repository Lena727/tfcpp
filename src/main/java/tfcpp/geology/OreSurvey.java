package tfcpp.geology;

import java.util.Map;
import java.util.TreeMap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Counts what lies in a square of the world, for judging how common ores are: this is the only way to see what worldgen really made. An operator can run it with {@code /tfcpp survey}, or a build can start with {@code -Dtfcpp.survey=<chunks>} to log one at a place no player has been.
 * <p>
 * It generates every chunk in the square on the server thread, so it stops the server for as long as that takes.
 */
public final class OreSurvey
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FAR_FROM_SPAWN = 16384;
    private static final int SITES = 8;

    private static final class Tally
    {
        long blocks;
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
    }

    public static void onServerStarted(ServerStartedEvent event)
    {
        final String radius = System.getProperty("tfcpp.survey");
        if (radius != null)
        {
            // Rock types cover whole regions, so one square says little about how common an ore is
            final ServerLevel level = event.getServer().overworld();
            for (int site = 0; site < SITES; site++)
            {
                final double angle = 2 * Math.PI * site / SITES;
                final BlockPos center = level.getSharedSpawnPos().offset((int) (FAR_FROM_SPAWN * Math.cos(angle)), 0, (int) (FAR_FROM_SPAWN * Math.sin(angle)));
                for (String line : run(level, center, Integer.parseInt(radius)))
                {
                    LOGGER.info("TFCPP_SURVEY {}", line);
                }
            }
        }
    }

    /** @return one line for each kind of ore found, and how much of it, in the square of chunks around this position */
    public static java.util.List<String> run(ServerLevel level, BlockPos center, int radius)
    {
        final Map<String, Tally> found = new TreeMap<>();
        final ChunkPos middle = new ChunkPos(center);
        long stone = 0;
        for (int x = middle.x - radius; x <= middle.x + radius; x++)
        {
            for (int z = middle.z - radius; z <= middle.z + radius; z++)
            {
                final ChunkAccess chunk = level.getChunk(x, z);
                final LevelChunkSection[] sections = chunk.getSections();
                for (int s = 0; s < sections.length; s++)
                {
                    final LevelChunkSection section = sections[s];
                    if (section.hasOnlyAir())
                    {
                        continue;
                    }
                    final int baseY = chunk.getSectionYFromSectionIndex(s) << 4;
                    for (int by = 0; by < 16; by++)
                    {
                        for (int bx = 0; bx < 16; bx++)
                        {
                            for (int bz = 0; bz < 16; bz++)
                            {
                                final BlockState state = section.getBlockState(bx, by, bz);
                                if (state.isAir())
                                {
                                    continue;
                                }
                                final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                                if (id.getPath().startsWith("rock/raw/"))
                                {
                                    stone++;
                                }
                                else if (id.getPath().startsWith("ore/"))
                                {
                                    final Tally tally = found.computeIfAbsent(oreName(id), key -> new Tally());
                                    tally.blocks++;
                                    tally.lowest = Math.min(tally.lowest, baseY + by);
                                    tally.highest = Math.max(tally.highest, baseY + by);
                                }
                            }
                        }
                    }
                }
            }
        }

        final java.util.List<String> lines = new java.util.ArrayList<>();
        final int chunks = (2 * radius + 1) * (2 * radius + 1);
        lines.add(chunks + " chunks around " + center.toShortString() + ", " + stone + " blocks of raw rock");
        found.forEach((name, tally) -> lines.add("%s: %d blocks, %.1f per chunk, y %d to %d".formatted(name, tally.blocks, (float) tally.blocks / chunks, tally.lowest, tally.highest)));
        return lines;
    }

    /** @return the ore without its grade or rock, such as {@code tfcpp:galena}, with the loose pieces on the surface apart */
    private static String oreName(ResourceLocation id)
    {
        final String[] parts = id.getPath().split("/");
        final String kind = parts[1];
        final String mineral = kind.startsWith("poor_") || kind.startsWith("normal_") || kind.startsWith("rich_") ? kind.substring(kind.indexOf('_') + 1) : kind;
        return id.getNamespace() + ":" + mineral;
    }

    private OreSurvey() {}
}
