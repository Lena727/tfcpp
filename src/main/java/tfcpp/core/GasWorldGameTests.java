package tfcpp.core;

import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasChunkData;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * What the gas of a chunk does over time, in chunks far from the test and from any other gas: the open air at the mouth of a shaft, a chunk whose gas has come to rest, and a film of mercury on the floor of a room that has been cleared. They are built in the air above the ground, so that nothing else is near them.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasWorldGameTests
{
    private static final String TEMPLATE = "empty9";
    /** Apart from every other test: these watch whole chunks. */
    private static final String CHUNKS = "tfcpp_gas_world";

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** A chunk well away from the test, kept loaded, where no other test puts any gas. */
    private static LevelChunk farChunk(GameTestHelper helper, int offset)
    {
        final ServerLevel level = helper.getLevel();
        final BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        final int chunkX = (origin.getX() >> 4) + offset, chunkZ = origin.getZ() >> 4;
        level.setChunkForced(chunkX, chunkZ, true);
        return level.getChunk(chunkX, chunkZ);
    }

    private static void fill(ServerLevel level, BlockPos min, BlockPos max, BlockState state)
    {
        for (BlockPos pos : BlockPos.betweenClosed(min, max))
        {
            level.setBlock(pos, state, 2);
        }
    }

    /** A sealed room of 3 x 3 x 3 cells in the air above a chunk. */
    private static BlockPos sealedRoom(ServerLevel level, LevelChunk chunk)
    {
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        final BlockPos center = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 8, z);
        fill(level, center.offset(-2, -2, -2), center.offset(2, 2, 2), STONE);
        fill(level, center.offset(-1, -1, -1), center.offset(1, 1, 1), AIR);
        return center;
    }

    private static void takeDown(ServerLevel level, LevelChunk chunk, BlockPos center)
    {
        fill(level, center.offset(-2, -2, -2), center.offset(2, 2, 2), AIR);
        chunk.getData(GasAttachments.CHUNK).clear();
        level.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
    }

    private static float inRoom(LevelGasVolume volume, BlockPos center, Gas gas)
    {
        float total = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1)))
        {
            final @Nullable float[] cell = volume.cell(pos.getX(), pos.getY(), pos.getZ());
            total += cell == null ? 0f : cell[gas.ordinal()];
        }
        return total;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = CHUNKS)
    public static void theAirInAShaftIsNearlyStillAndAtTheMouthItIsNot(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 60);
        final LevelGasVolume volume = GasTracker.get(level).volume(level);

        // A pillar of 3 x 3 columns, 12 high, with a shaft 1 x 1 through the middle of it
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        final int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        final BlockPos bottom = new BlockPos(x, ground + 6, z); // Above the ground, so that nothing else is in the column
        fill(level, bottom.offset(-1, 0, -1), bottom.offset(1, 11, 1), STONE);
        fill(level, bottom.offset(-1, -1, -1), bottom.offset(1, -1, 1), STONE); // A floor under the shaft
        fill(level, bottom, bottom.offset(0, 11, 0), AIR);
        final int top = bottom.getY() + 11;

        try
        {
            for (int below : new int[] {0, 3, 6, 11})
            {
                final float rate = volume.atmosphereExchangeRate(x, top - below, z);
                final float expected = GasMath.openAirExchange(below);
                helper.assertTrue(Math.abs(rate - expected) < 1e-4f, below + " m down the shaft the exchange should be " + expected + ", got " + rate);
            }
            helper.assertTrue(volume.atmosphereExchangeRate(x, top + 1, z) > 0.29f, "Above the mouth of the shaft it is the wind of the surface");
            helper.assertTrue(volume.atmosphereExchangeRate(x, bottom.getY(), z) < 0.01f, "At the bottom, twelve metres down, the air is nearly still");

            level.setBlock(new BlockPos(x, top, z), STONE, 2); // A lid on the shaft
            helper.assertTrue(volume.atmosphereExchangeRate(x, top - 1, z) == 0f, "Under a lid there is no exchange at all");
            helper.succeed();
        }
        finally
        {
            fill(level, bottom.offset(-1, -1, -1), bottom.offset(1, 11, 1), AIR);
            level.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 520, batch = CHUNKS)
    public static void gasAtRestSleepsIsNotSavedAgainAndIsLookedAtAgain(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true); // Gas is stepped by the calendar of TFC, which stands still on an empty server
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 64);
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
        final BlockPos center = sealedRoom(level, chunk);

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1)))
        {
            volume.cellForEmission(pos.getX(), pos.getY(), pos.getZ())[Gas.CARBON_DIOXIDE.ordinal()] += 6f; // The same in every cell: nothing can move
        }
        helper.assertTrue(data.isActive(), "A chunk that has just been written to is looked at");

        helper.runAtTickTime(100, () -> {
            helper.assertFalse(data.isActive(), "Nothing in it is changing, so it sleeps");
            helper.assertTrue(data.isResting(), "though it holds gas");
            helper.assertTrue(inRoom(volume, center, Gas.CARBON_DIOXIDE) > 161f, "and the gas is all there");
            chunk.setUnsaved(false);
        });
        helper.runAtTickTime(200, () -> {
            helper.assertFalse(chunk.isUnsaved(), "A chunk at rest is not saved again and again");
            fill(level, center.offset(2, -1, -1), center.offset(2, 1, 1), AIR); // A wall knocked down: nothing tells the gas
        });
        helper.runAtTickTime(500, () -> {
            helper.assertTrue(inRoom(volume, center, Gas.CARBON_DIOXIDE) < 150f, "Looked at again after a while, the gas finds its way out, " + inRoom(volume, center, Gas.CARBON_DIOXIDE) + " of 162 mol is left");
            takeDown(level, chunk, center);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = CHUNKS)
    public static void newGasWakesAChunkAtRest(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 68);
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
        final BlockPos center = sealedRoom(level, chunk);

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1)))
        {
            volume.cellForEmission(pos.getX(), pos.getY(), pos.getZ())[Gas.FLUE_NITROGEN.ordinal()] += 6f;
        }
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(data.isResting(), "It is at rest");
            volume.cellForEmission(center.getX(), center.getY(), center.getZ())[Gas.FLUE_NITROGEN.ordinal()] += 6f;
            helper.assertTrue(data.isActive() && !data.isResting(), "New gas wakes it");
        });
        helper.runAtTickTime(180, () -> {
            final float[] middle = volume.cell(center.getX(), center.getY(), center.getZ());
            final float[] beside = volume.cell(center.getX() + 1, center.getY(), center.getZ());
            helper.assertTrue(middle != null && beside != null && middle[Gas.FLUE_NITROGEN.ordinal()] < 11f && beside[Gas.FLUE_NITROGEN.ordinal()] > 6f, "and it spreads through the room, middle " + (middle == null ? null : middle[Gas.FLUE_NITROGEN.ordinal()]) + ", beside " + (beside == null ? null : beside[Gas.FLUE_NITROGEN.ordinal()]) + ", active " + data.isActive() + ", resting " + data.isResting());
            takeDown(level, chunk, center);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 700, batch = CHUNKS)
    public static void aFilmOfMercuryFillsTheAirOfAClearedRoom(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 72);
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
        final BlockPos center = sealedRoom(level, chunk);
        final float film = 2e-3f;

        // What is left on the floor of a room that has been aired. There is no gas in it, and no cell
        volume.deposit(center.getX(), center.getY() - 1, center.getZ(), Gas.MERCURY_VAPOR, film);
        helper.assertTrue(data.isEmpty() && data.isActive(), "A film alone keeps the chunk looked at");
        helper.assertTrue(inRoom(volume, center, Gas.MERCURY_VAPOR) == 0f, "and the air of the room is clean");

        helper.runAtTickTime(600, () -> {
            final float vapour = inRoom(volume, center, Gas.MERCURY_VAPOR);
            final float left = data.deposit(Gas.MERCURY_VAPOR);
            helper.assertTrue(vapour > 2e-5f, "The air has taken up the vapour, got " + vapour);
            helper.assertTrue(vapour <= 27f * Gas.MERCURY_VAPOR.saturationMoles() * 1.01f, "and not more than it can hold");
            helper.assertTrue(left < film, "It came from the film, " + left + " of " + film + " is left");
            helper.assertTrue(Math.abs(vapour + left - film) < film * 0.05f, "and nothing was made or lost on the way: " + vapour + " in the air and " + left + " in the film");
            takeDown(level, chunk, center);
            helper.succeed();
        });
    }

    private GasWorldGameTests() {}
}
