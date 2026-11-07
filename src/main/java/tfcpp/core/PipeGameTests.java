package tfcpp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.wood.Wood;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.pipes.ManometerBlock;
import tfcpp.pneumatics.pipes.PipeFittingBlock;
import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePhysics;
import tfcpp.pneumatics.pipes.PipeSegment;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.Pipes;
import tfcpp.pneumatics.pipes.PistonPumpBlockEntity;
import tfcpp.pneumatics.pipes.SteamBurst;

/**
 * The pipes of batch 3 of the v1.0 review: a line that runs on past loaded land, the corrosion that a reload must not even out, liquids that do not mix, the weight of a column of liquid, an opening under water, a boiler that scalds, and what the walls give back.
 * <p>
 * The tests that drive chunks work in chunks of their own, far from the others and forced to stay loaded, and call the tracker's unload and load by hand: the world cannot be made to unload a chunk in a test, and the chunk stays loaded for everything else. That is why the tracker keeps its own account of which chunks it has let go of.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeGameTests
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TEMPLATE = "empty9";
    private static final String BATCH = "tfcpp_pipes";
    /** These unload and load chunks by hand, so they run apart from everything else. */
    private static final String CHUNK_BATCH = "tfcpp_pipes_chunks";
    /** These break boilers and hurt players. */
    private static final String BURST_BATCH = "tfcpp_pipes_burst";
    private static final String WATER = "minecraft:water";
    private static final String ACID = "tfcpp:chamber_acid";

    // ---- Helpers ----

    private static ServerLevel level(GameTestHelper helper)
    {
        return helper.getLevel();
    }

    private static PipeTracker tracker(GameTestHelper helper)
    {
        return PipeTracker.get(helper.getLevel());
    }

    @Nullable
    private static PipeNetwork networkAt(GameTestHelper helper, int x, int y, int z)
    {
        return tracker(helper).networkAt(helper.absolutePos(new BlockPos(x, y, z)));
    }

    private static PipeNetwork requireNetwork(GameTestHelper helper, int x, int y, int z)
    {
        final @Nullable PipeNetwork network = networkAt(helper, x, y, z);
        helper.assertTrue(network != null, "There should be a network at " + x + " " + y + " " + z);
        return network;
    }

    private static Block pipe(PipeMaterial material)
    {
        return Pipes.PIPES.get(material).get();
    }

    private static Block ironPipe()
    {
        return pipe(PipeMaterial.WROUGHT_IRON);
    }

    private static BlockState fitting(Supplier<Block> block, Direction facing, boolean open)
    {
        return block.get().defaultBlockState().setValue(PipeFittingBlock.FACING, facing).setValue(PipeFittingBlock.OPEN, open);
    }

    private static BlockState pump(Direction facing)
    {
        return Pipes.PISTON_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    private static PistonPumpBlockEntity pumpAt(GameTestHelper helper, int x, int y, int z)
    {
        return (PistonPumpBlockEntity) helper.getBlockEntity(new BlockPos(x, y, z));
    }

    /** Fills the room above the liquid with air at this pressure, as a closed pipe would have it. */
    private static void setAirPressure(PipeNetwork network, float atmospheres)
    {
        final var contents = network.contents();
        contents.temperature = PipePhysics.ROOM_TEMPERATURE;
        contents.air = Math.max(0f, PipePhysics.moles(atmospheres, contents.temperature, contents.freeVolume(network.volume())) - tfcpp.pneumatics.gas.GasMath.total(contents.gases));
    }

    private static void sealedBox(GameTestHelper helper, int size, int height)
    {
        for (int x = 0; x <= size + 1; x++)
        {
            for (int y = 0; y <= height + 1; y++)
            {
                for (int z = 0; z <= size + 1; z++)
                {
                    final boolean inside = x > 0 && x <= size && y > 0 && y <= height && z > 0 && z <= size;
                    helper.setBlock(x, y, z, inside ? Blocks.AIR : Blocks.OBSIDIAN);
                }
            }
        }
    }

    private static float gasInBox(GameTestHelper helper, int size, int height, Gas gas)
    {
        final LevelGasVolume volume = GasTracker.get(level(helper)).volume(level(helper));
        float total = 0f;
        for (int x = 1; x <= size; x++)
        {
            for (int y = 1; y <= height; y++)
            {
                for (int z = 1; z <= size; z++)
                {
                    final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    final @Nullable float[] cell = volume.cell(pos.getX(), pos.getY(), pos.getZ());
                    total += cell == null ? 0f : cell[gas.ordinal()];
                }
            }
        }
        return total;
    }

    /** A player that is in the level, so that a burst finds it, and keeps the hits it is dealt instead of taking them. */
    private static final class TestPlayer extends FakePlayer
    {
        final List<DamageSource> hits = new ArrayList<>();
        float mostDamage;

        TestPlayer(GameTestHelper helper)
        {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_pipe_test"));
        }

        @Override
        public boolean hurt(DamageSource source, float amount)
        {
            hits.add(source);
            if (source.is(SteamBurst.SCALD))
            {
                mostDamage = Math.max(mostDamage, amount);
            }
            return true;
        }

        boolean scalded()
        {
            return hits.stream().anyMatch(source -> source.is(SteamBurst.SCALD));
        }
    }

    private static TestPlayer playerAt(GameTestHelper helper, BlockPos pos)
    {
        final TestPlayer player = new TestPlayer(helper);
        final BlockPos absolute = helper.absolutePos(pos);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    /** Two chunks side by side, far from the test and forced to stay loaded. */
    private record Pair(ServerLevel level, ChunkPos west, ChunkPos east, int y, int z)
    {
        BlockPos at(int fromBorder, int up, int along)
        {
            return new BlockPos(east.getMinBlockX() + fromBorder, y + up, z + along);
        }

        LevelChunk westChunk()
        {
            return level.getChunk(west.x, west.z);
        }

        LevelChunk eastChunk()
        {
            return level.getChunk(east.x, east.z);
        }

        void release()
        {
            level.setChunkForced(west.x, west.z, false);
            level.setChunkForced(east.x, east.z, false);
        }
    }

    private static Pair farPair(GameTestHelper helper, int index)
    {
        final BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        final ChunkPos base = new ChunkPos(origin);
        final ChunkPos west = new ChunkPos(base.x + 6, base.z + 6 + 3 * index), east = new ChunkPos(base.x + 7, base.z + 6 + 3 * index);
        final ServerLevel level = helper.getLevel();
        level.setChunkForced(west.x, west.z, true);
        level.setChunkForced(east.x, east.z, true);
        level.getChunk(west.x, west.z);
        level.getChunk(east.x, east.z);
        return new Pair(level, west, east, origin.getY() + 1, west.getMinBlockZ() + 4);
    }

    private static float totalOf(PipeNetwork network, Gas gas)
    {
        return network.contents().gases[gas.ordinal()];
    }

    // ---- P1: a line that runs on past loaded land ----

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = CHUNK_BATCH)
    public static void aLineThatRunsOnPastLoadedLandIsNotDeliveredIntoAndLosesNothing(GameTestHelper helper)
    {
        final Pair pair = farPair(helper, 0);
        final ServerLevel level = pair.level();
        final BlockPos pumpPos = pair.at(-3, 0, 0);
        level.setBlockAndUpdate(pumpPos, pump(Direction.EAST));
        final BlockPos[] line = {pair.at(-2, 0, 0), pair.at(-1, 0, 0), pair.at(0, 0, 0), pair.at(1, 0, 0)};
        for (BlockPos pos : line)
        {
            level.setBlockAndUpdate(pos, ironPipe().defaultBlockState());
        }
        final PistonPumpBlockEntity pump = (PistonPumpBlockEntity) level.getBlockEntity(pumpPos);

        helper.runAtTickTime(5, () -> {
            final @Nullable PipeNetwork whole = PipeTracker.get(level).networkAt(line[0]);
            helper.assertTrue(whole != null && whole.segments().size() == 4 && !whole.truncated(), "The four pipes should be one network that ends in itself");
            whole.contents().gases[Gas.METHANE.ordinal()] = 8f;
        });
        helper.runAtTickTime(6, () -> PipeTracker.get(level).onChunkUnload(level, pair.eastChunk()));
        helper.runAtTickTime(12, () -> {
            final PipeTracker tracker = PipeTracker.get(level);
            helper.assertTrue(tracker.segmentAt(line[2]) == null, "The tracker should have let go of the chunk that was unloaded");
            final @Nullable PipeNetwork west = tracker.networkAt(line[0]);
            helper.assertTrue(west != null && west.segments().size() == 2, "The loaded half should form a network of its own");
            helper.assertTrue(west.truncated(), "and know that it runs on past loaded land");
            helper.assertTrue(Math.abs(totalOf(west, Gas.METHANE) - 4f) < 0.01f, "with its own share of the gas, got " + totalOf(west, Gas.METHANE));
            helper.assertTrue("tfcpp.pump.status.truncated".equals(pump.statusKey(level)), "The pump should say why it will not deliver");

            final float airBefore = west.contents().air;
            pump.pump(level, 0.01f);
            helper.assertTrue(west.contents().air == airBefore, "A pump does not deliver into a line that may be anything at its far end");

            float held = 0f;
            for (PipeSegment segment : pair.eastChunk().getData(tfcpp.pneumatics.gas.GasAttachments.PIPE_CHUNK).segments())
            {
                helper.assertTrue(segment.network == null, "The far half is separate segments again");
                held += segment.stored.gases[Gas.METHANE.ordinal()];
            }
            helper.assertTrue(Math.abs(held - 4f) < 0.01f, "and holds the other half of the gas, got " + held);
        });
        helper.runAtTickTime(13, () -> PipeTracker.get(level).onChunkLoad(pair.eastChunk()));
        helper.runAtTickTime(20, () -> {
            final PipeTracker tracker = PipeTracker.get(level);
            final @Nullable PipeNetwork whole = tracker.networkAt(line[0]);
            helper.assertTrue(whole != null && whole == tracker.networkAt(line[3]), "When the far chunk loads, the line rejoins");
            helper.assertTrue(whole.segments().size() == 4 && !whole.truncated(), "whole, and no longer truncated");
            helper.assertTrue(Math.abs(totalOf(whole, Gas.METHANE) - 8f) < 0.01f, "with all the gas, got " + totalOf(whole, Gas.METHANE));
            final float airBefore = whole.contents().air;
            pump.pump(level, 0.01f);
            helper.assertTrue(whole.contents().air > airBefore, "and the pump delivers into it again");
            for (BlockPos pos : line)
            {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(pumpPos, Blocks.AIR.defaultBlockState());
            pair.release();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = CHUNK_BATCH)
    public static void aManometerSaysWhenTheLineRunsOnAndAnUnloadMarksTheChunkToSave(GameTestHelper helper)
    {
        final Pair pair = farPair(helper, 1);
        final ServerLevel level = pair.level();
        final BlockPos[] line = {pair.at(-1, 0, 0), pair.at(0, 0, 0)};
        for (BlockPos pos : line)
        {
            level.setBlockAndUpdate(pos, ironPipe().defaultBlockState());
        }

        helper.runAtTickTime(5, () -> {
            final LevelChunk chunk = pair.eastChunk();
            chunk.setUnsaved(false);
            PipeTracker.get(level).onChunkUnload(level, chunk);
            helper.assertTrue(chunk.isUnsaved(), "A chunk that is let go of with pipes in it must be saved with the shares they were handed");
        });
        helper.runAtTickTime(10, () -> {
            final @Nullable PipeNetwork west = PipeTracker.get(level).networkAt(line[0]);
            helper.assertTrue(west != null && west.truncated(), "The one pipe that is left runs on past loaded land");
            PipeTracker.get(level).onChunkLoad(pair.eastChunk());
        });
        helper.runAtTickTime(16, () -> {
            final @Nullable PipeNetwork network = PipeTracker.get(level).networkAt(line[0]);
            helper.assertTrue(network != null && !network.truncated() && network == PipeTracker.get(level).networkAt(line[1]), "and it joins the rest when the chunk is back");
            for (BlockPos pos : line)
            {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            pair.release();
            helper.succeed();
        });
    }

    // ---- P2: corrosion counted for each wall ----

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = CHUNK_BATCH)
    public static void everyWallPaysForItsOwnTimeAndAReloadDoesNotEvenThemOut(GameTestHelper helper)
    {
        final Pair pair = farPair(helper, 2);
        final ServerLevel level = pair.level();
        final BlockPos[] line = {pair.at(-4, 0, 0), pair.at(-3, 0, 0), pair.at(-2, 0, 0)};
        for (BlockPos pos : line)
        {
            level.setBlockAndUpdate(pos, ironPipe().defaultBlockState());
        }
        final float[] rate = new float[1];

        helper.runAtTickTime(5, () -> {
            final PipeTracker tracker = PipeTracker.get(level);
            final @Nullable PipeNetwork network = tracker.networkAt(line[0]);
            helper.assertTrue(network != null && network.segments().size() == 3, "The three pipes should be one network");
            network.contents().addLiquid(ACID, 15f, PipePhysics.ROOM_TEMPERATURE); // About half full
            rate[0] = PipeMaterial.WROUGHT_IRON.corrosion(ACID, PipePhysics.ROOM_TEMPERATURE) * network.contents().liquidVolume() / network.volume();
            helper.assertTrue(rate[0] > 0.05f, "Chamber acid must eat iron, got " + rate[0]);

            final long now = Calendars.SERVER.getTicks();
            tracker.segmentAt(line[0]).corrodedUntil = now - 2L * tfcpp.core.LocalTime.TICKS_PER_DAY;
            tracker.segmentAt(line[1]).corrodedUntil = now - tfcpp.core.LocalTime.TICKS_PER_DAY;
            tracker.segmentAt(line[2]).corrodedUntil = -1L; // Never counted
            // The chunk is let go of and comes back: the walls keep what they were counted up to
            tracker.onChunkUnload(level, pair.westChunk());
        });
        helper.runAtTickTime(8, () -> PipeTracker.get(level).onChunkLoad(pair.westChunk()));
        helper.runAtTickTime(15, () -> {
            final PipeTracker tracker = PipeTracker.get(level);
            final PipeNetwork network = tracker.networkAt(line[0]);
            helper.assertTrue(network != null && network.segments().size() == 3, "The pipes should be a network again");
            network.corrode(level);
            final float oldest = 1f - tracker.segmentAt(line[0]).integrity, middle = 1f - tracker.segmentAt(line[1]).integrity, newest = 1f - tracker.segmentAt(line[2]).integrity;
            helper.assertTrue(Math.abs(oldest - 2f * rate[0]) < 0.1f * rate[0], "The wall counted up to two days ago pays two days: " + oldest + " of " + 2f * rate[0]);
            helper.assertTrue(Math.abs(middle - rate[0]) < 0.1f * rate[0], "the wall of a day pays a day: " + middle + " of " + rate[0]);
            helper.assertTrue(newest < 0.01f * rate[0] + 1e-4f, "and the wall never counted starts now and pays nothing yet: " + newest);
            for (BlockPos pos : line)
            {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            pair.release();
            helper.succeed();
        });
    }

    // ---- P25: the chunks are marked only for what changed ----

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aMinuteOfCorrosionMarksTheChunkOnlyIfAWallChanged(GameTestHelper helper)
    {
        helper.setBlock(1, 1, 1, pipe(PipeMaterial.LEAD));
        helper.setBlock(5, 1, 1, ironPipe());
        helper.runAtTickTime(5, () -> {
            final PipeNetwork lead = requireNetwork(helper, 1, 1, 1), iron = requireNetwork(helper, 5, 1, 1);
            lead.contents().addLiquid(WATER, 5f, PipePhysics.ROOM_TEMPERATURE); // Lead takes nothing from water
            iron.contents().addLiquid(ACID, 5f, PipePhysics.ROOM_TEMPERATURE);
            final long now = Calendars.SERVER.getTicks();
            tracker(helper).segmentAt(helper.absolutePos(new BlockPos(1, 1, 1))).corrodedUntil = now - tfcpp.core.LocalTime.TICKS_PER_DAY;
            tracker(helper).segmentAt(helper.absolutePos(new BlockPos(5, 1, 1))).corrodedUntil = now - tfcpp.core.LocalTime.TICKS_PER_DAY;

            final LevelChunk leadChunk = level(helper).getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
            leadChunk.setUnsaved(false);
            lead.corrode(level(helper));
            final boolean leadMarked = leadChunk.isUnsaved();
            final LevelChunk ironChunk = level(helper).getChunkAt(helper.absolutePos(new BlockPos(5, 1, 1)));
            ironChunk.setUnsaved(false);
            iron.corrode(level(helper));
            helper.assertFalse(leadMarked, "Nothing wore away in the lead pipe: its chunk need not be saved again");
            helper.assertTrue(ironChunk.isUnsaved(), "The iron wore away: its chunk must be saved");
            helper.succeed();
        });
    }

    // ---- P3: networks of different liquids do not join ----

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aPipePutBetweenTwoLiquidsJoinsOneOfThemAndLosesNothing(GameTestHelper helper)
    {
        helper.setBlock(1, 1, 3, ironPipe());
        helper.setBlock(3, 1, 3, ironPipe());
        tracker(helper).segmentAt(helper.absolutePos(new BlockPos(1, 1, 3))).stored.addLiquid(WATER, 4f, PipePhysics.ROOM_TEMPERATURE);
        tracker(helper).segmentAt(helper.absolutePos(new BlockPos(3, 1, 3))).stored.addLiquid(ACID, 4f, PipePhysics.ROOM_TEMPERATURE);

        helper.runAtTickTime(3, () -> {
            final PipeNetwork water = requireNetwork(helper, 1, 1, 3), acid = requireNetwork(helper, 3, 1, 3);
            helper.assertTrue(water != acid && water.contents().liquid.equals(WATER) && acid.contents().liquid.equals(ACID), "Two networks, one of each liquid");
            helper.setBlock(2, 1, 3, ironPipe());
        });
        helper.runAtTickTime(10, () -> {
            final PipeNetwork water = requireNetwork(helper, 1, 1, 3), acid = requireNetwork(helper, 3, 1, 3), between = requireNetwork(helper, 2, 1, 3);
            helper.assertTrue(water == between, "The empty pipe goes with the water, which comes first in the alphabet");
            helper.assertTrue(acid != water, "and the acid is left on its own side of a closed joint");
            helper.assertTrue(Math.abs(water.contents().litres - 4f) < 1e-3f && Math.abs(acid.contents().litres - 4f) < 1e-3f, "Neither liquid is lost: " + water.contents().litres + " and " + acid.contents().litres);
            helper.assertTrue(water.contents().liquid.equals(WATER) && acid.contents().liquid.equals(ACID), "and neither is mistaken for the other");
            helper.succeed();
        });
    }

    // ---- P4: the weight of liquid ----

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void liftingWaterUpATallColumnLoadsThePumpByItsWeight(GameTestHelper helper)
    {
        helper.setBlock(4, 1, 5, pump(Direction.NORTH));
        helper.setBlock(4, 1, 6, Blocks.WATER);
        helper.setBlock(3, 1, 6, Blocks.OBSIDIAN);
        helper.setBlock(5, 1, 6, Blocks.OBSIDIAN);
        helper.setBlock(4, 1, 7, Blocks.OBSIDIAN);
        for (int y = 1; y <= 24; y++)
        {
            helper.setBlock(4, y, 4, ironPipe());
        }
        helper.setBlock(4, 25, 4, fitting(Pipes.VENT, Direction.UP, true));

        helper.runAtTickTime(5, () -> {
            final PipeNetwork column = requireNetwork(helper, 4, 1, 4);
            column.contents().addLiquid(WATER, 130f, PipePhysics.ROOM_TEMPERATURE);
            setAirPressure(column, 1f);
        });
        helper.runAtTickTime(6, () -> {
            final PipeNetwork column = requireNetwork(helper, 4, 1, 4);
            final float surface = column.liquidSurface() - helper.absolutePos(new BlockPos(4, 1, 4)).getY();
            helper.assertTrue(Math.abs(surface - 13f) < 0.1f, "130 litres stand 13 metres up a pipe of 10, got " + surface);
            final float torque = pumpAt(helper, 4, 1, 5).pump(level(helper), 0.0005f);
            final float expected = 0.02f + PipePhysics.pistonTorque(0.02f, column.pressure() - 1f + PipePhysics.head(1000f, 13f) / PipePhysics.PASCALS_PER_ATM);
            helper.assertTrue(torque > 0.35f, "13 metres of water is more than 1 atm: the pump should be loaded by about 0.43, got " + torque);
            helper.assertTrue(Math.abs(torque - expected) < 0.03f, "Expected " + expected + ", got " + torque);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH)
    public static void aTapAboveTheLiquidGivesNothingUntilThePressureLiftsIt(GameTestHelper helper)
    {
        final StringBuilder trace = new StringBuilder();
        final PipeNetwork[] held = new PipeNetwork[1];
        helper.onEachTick(() -> {
            final @Nullable PipeNetwork network = held[0];
            if (network != null)
            {
                network.contents().temperature = PipePhysics.ROOM_TEMPERATURE;
                final IFluidHandler b = level(helper).getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(new BlockPos(3, 4, 2)), Direction.UP);
                final PipeNetwork now = networkAt(helper, 2, 1, 2);
                trace.append(helper.getTick()).append(':').append(now == null ? -1 : (int) now.contents().litres).append('/').append(b.getFluidInTank(0).getAmount()).append(now == network ? "" : "*").append(' ');
            }
        });
        for (int y = 1; y <= 6; y++)
        {
            helper.setBlock(2, y, 2, ironPipe());
        }
        helper.setBlock(3, 6, 2, ironPipe());
        helper.setBlock(3, 5, 2, fitting(Pipes.TAP, Direction.DOWN, true));
        helper.setBlock(3, 4, 2, TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.BARREL).get());

        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = requireNetwork(helper, 2, 1, 2);
            network.contents().addLiquid(WATER, 20f, PipePhysics.ROOM_TEMPERATURE);
            setAirPressure(network, 1.1f); // 10 kPa over the air outside, against the 25 kPa that the tap stands over the top of the water
            held[0] = network;
        });
        helper.runAtTickTime(60, () -> {
            final IFluidHandler barrel = level(helper).getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(new BlockPos(3, 4, 2)), Direction.UP);
            helper.assertTrue(barrel != null, "The barrel should hold liquid");
            helper.assertTrue(barrel.getFluidInTank(0).isEmpty(), "A tap 2.5 m over the top of the water, with 0.1 atm behind it, gives nothing, but it gave " + barrel.getFluidInTank(0).getAmount() + "; trace " + trace + "; the network holds " + requireNetwork(helper, 2, 1, 2).contents().litres + " l at " + requireNetwork(helper, 2, 1, 2).pressure() + " atm, surface " + (requireNetwork(helper, 2, 1, 2).liquidSurface() - helper.absolutePos(BlockPos.ZERO).getY()));
            setAirPressure(requireNetwork(helper, 2, 1, 2), 1.5f);
        });
        helper.runAtTickTime(110, () -> {
            final IFluidHandler barrel = level(helper).getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(new BlockPos(3, 4, 2)), Direction.UP);
            helper.assertTrue(barrel.getFluidInTank(0).getAmount() > 0, "With 0.5 atm behind it, enough to lift it, the tap gives");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aPumpDrawsNothingFromMoreThanTenMetresBelow(GameTestHelper helper)
    {
        helper.setBlock(2, 16, 2, pump(Direction.NORTH));
        helper.setBlock(2, 16, 1, ironPipe());
        for (int y = 16; y >= 1; y--)
        {
            helper.setBlock(2, y, 3, ironPipe());
        }
        helper.setBlock(6, 16, 2, pump(Direction.NORTH));
        helper.setBlock(6, 16, 1, ironPipe());
        for (int y = 16; y >= 9; y--)
        {
            helper.setBlock(6, y, 3, ironPipe());
        }

        helper.runAtTickTime(5, () -> {
            final PipeNetwork deep = requireNetwork(helper, 2, 1, 3), shallow = requireNetwork(helper, 6, 9, 3);
            deep.contents().addLiquid(WATER, 30f, PipePhysics.ROOM_TEMPERATURE); // Three levels of the sixteen: 12 m below the pump
            shallow.contents().addLiquid(WATER, 30f, PipePhysics.ROOM_TEMPERATURE); // Three levels of the eight: 5 m below it
            setAirPressure(deep, 1f);
            setAirPressure(shallow, 1f);
        });
        helper.runAtTickTime(6, () -> {
            final PipeNetwork deep = requireNetwork(helper, 2, 1, 3), shallow = requireNetwork(helper, 6, 9, 3);
            final PipeNetwork deepOut = requireNetwork(helper, 2, 16, 1), shallowOut = requireNetwork(helper, 6, 16, 1);
            pumpAt(helper, 2, 16, 2).pump(level(helper), 0.002f);
            pumpAt(helper, 6, 16, 2).pump(level(helper), 0.002f);
            helper.assertTrue(deepOut.contents().litres == 0f && Math.abs(deep.contents().litres - 30f) < 1e-3f, "A pump 12 m above the water draws nothing: the air does not lift it so far");
            helper.assertTrue(shallowOut.contents().litres > 1.9f && shallow.contents().litres < 29f, "and one 5 m above it does, got " + shallowOut.contents().litres);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = BATCH)
    public static void aColumnOfWaterBurstsItsFootAndNotItsTop(GameTestHelper helper)
    {
        for (int y = 1; y <= 45; y++)
        {
            helper.setBlock(6, y, 6, pipe(PipeMaterial.CERAMIC));
            helper.setBlock(2, y, 6, pipe(PipeMaterial.CERAMIC));
        }

        helper.runAtTickTime(5, () -> {
            final PipeNetwork tall = requireNetwork(helper, 6, 1, 6), low = requireNetwork(helper, 2, 1, 6);
            tall.contents().addLiquid(WATER, 405f, PipePhysics.ROOM_TEMPERATURE); // 40 m of water: 3.9 atm at the foot, where fired clay holds 3.5
            low.contents().addLiquid(WATER, 135f, PipePhysics.ROOM_TEMPERATURE); // 13 m: 1.3 atm
            setAirPressure(tall, 1f);
            setAirPressure(low, 1f);
        });
        helper.runAtTickTime(50, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, 1, 6));
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 6)).is(pipe(PipeMaterial.CERAMIC)), "A column that is only a third full has no such weight on its foot");
            helper.succeed();
        });
    }

    // ---- P5, P14: an opening under water, and the runoff of a full pipe ----

    private static void boxWithWater(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2);
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(2, 1, 2, fitting(Pipes.VENT, Direction.SOUTH, true));
        helper.setBlock(1, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(3, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 3, Blocks.WATER);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void aVentUnderWaterBubblesOutWhenPushedHarderThanTheWaterHoldsIt(GameTestHelper helper)
    {
        boxWithWater(helper);
        helper.runAtTickTime(5, () -> requireNetwork(helper, 2, 1, 1).contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] = 40f);
        helper.runAtTickTime(70, () -> {
            final PipeNetwork network = requireNetwork(helper, 2, 1, 1);
            final float pressure = network.pressure();
            helper.assertTrue(pressure < 1.2f && pressure > 1.04f, "The vessel should have let down to the weight of the water over the vent, about 1.1 atm, got " + pressure);
            final float total = gasInBox(helper, 3, 2, Gas.SULFUR_DIOXIDE) + totalOf(network, Gas.SULFUR_DIOXIDE);
            helper.assertTrue(gasInBox(helper, 3, 2, Gas.SULFUR_DIOXIDE) > 5f, "and the gas should have come up through the water into the room");
            helper.assertTrue(Math.abs(total - 40f) < 4f, "none of it lost on the way, got " + total);
            helper.assertBlockPresent(Blocks.WATER, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void aVentUnderWaterHoldsWhatTheWaterHolds(GameTestHelper helper)
    {
        boxWithWater(helper);
        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = requireNetwork(helper, 2, 1, 1);
            network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] = 0.5f; // About 1.03 atm
        });
        helper.runAtTickTime(70, () -> {
            final PipeNetwork network = requireNetwork(helper, 2, 1, 1);
            helper.assertTrue(Math.abs(totalOf(network, Gas.SULFUR_DIOXIDE) - 0.5f) < 1e-4f, "Under the weight of a block of water, 0.03 atm lets nothing out");
            helper.assertTrue(gasInBox(helper, 3, 2, Gas.SULFUR_DIOXIDE) < 1e-3f, "none got into the room");
            helper.succeed();
        });
    }

    private static void fullVesselWithVent(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(2, 1, 2, fitting(Pipes.VENT, Direction.SOUTH, true));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void whatRunsOverStaysInThePipeThroughARebuildAndIsPlacedBeyondTheOutlet(GameTestHelper helper)
    {
        fullVesselWithVent(helper);
        helper.runAtTickTime(3, () -> {
            final PipeNetwork network = requireNetwork(helper, 2, 1, 1);
            network.contents().addLiquid(WATER, 502f, PipePhysics.ROOM_TEMPERATURE); // Over 99% of the half cubic metre
            setAirPressure(network, 2f);
        });
        helper.runAtTickTime(50, () -> {
            final PipeNetwork network = requireNetwork(helper, 2, 1, 1);
            helper.assertTrue(network.contents().runoff > 0f, "The water over the top of the pipe has run out of the vent, and waits there for a whole block");
            final float before = network.contents().litres + network.contents().runoff;
            helper.assertTrue(before > 495f && before < 505f, "Nothing was lost on the way out, got " + before);
            tracker(helper).onConnectionChanged(level(helper), helper.absolutePos(new BlockPos(2, 1, 2)));
        });
        helper.runAtTickTime(56, () -> {
            final PipeNetwork network = requireNetwork(helper, 2, 1, 1);
            helper.assertTrue(network.contents().runoff > 0f, "A rebuild of the network keeps the runoff");
            final float after = network.contents().litres + network.contents().runoff;
            helper.assertTrue(after > 495f && after < 505f, "and the water: it is not disposed of by rebuilding, got " + after);
            network.contents().runoff = 1000f;
            network.contents().runoffLiquid = WATER;
            setAirPressure(network, 2f); // What ran over has let the pressure down: it is pushed up again, so that the vent runs
        });
        helper.runAtTickTime(90, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 3));
            int blocks = 0;
            for (int x = 0; x <= 4; x++)
            {
                for (int y = 0; y <= 2; y++)
                {
                    for (int z = 2; z <= 6; z++)
                    {
                        blocks += helper.getBlockState(new BlockPos(x, y, z)).getFluidState().isSource() ? 1 : 0;
                    }
                }
            }
            helper.assertTrue(blocks == 1, "One whole block of water should have been put down beyond the outlet, found " + blocks);
            helper.assertTrue(requireNetwork(helper, 2, 1, 1).contents().runoff < 1f, "taking it from the runoff");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void aWholeBlockOfRunoffWithNowhereToGoStaysInThePipe(GameTestHelper helper)
    {
        fullVesselWithVent(helper);
        helper.setBlock(1, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(3, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 4, Blocks.OBSIDIAN);
        helper.setBlock(2, 0, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 2, 3, Blocks.OBSIDIAN);

        helper.runAtTickTime(3, () -> {
            final PipeNetwork network = requireNetwork(helper, 2, 1, 1);
            setAirPressure(network, 2f);
            network.contents().litres = 10f;
            network.contents().liquid = WATER;
            network.contents().runoff = 1000f;
            network.contents().runoffLiquid = WATER;
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(requireNetwork(helper, 2, 1, 1).contents().runoff >= 1000f, "With no place to put it, the water stays in the pipe");
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    // ---- P6: a boiler that bursts scalds ----

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BURST_BATCH)
    public static void aBurstBoilerScaldsThoseNearAndNotThoseFar(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 2, Pipes.PRESSURE_VESSEL.get());
        final TestPlayer near = playerAt(helper, new BlockPos(2, 1, 4));
        final TestPlayer far = playerAt(helper, new BlockPos(7, 1, 7));

        helper.runAtTickTime(4, () -> {
            final PipeNetwork vessel = requireNetwork(helper, 2, 1, 2);
            vessel.contents().liquid = WATER;
            vessel.contents().litres = 435f; // 0.5 m3 about 90% full
            vessel.contents().temperature = PipePhysics.saturationTemperature(11f);
            vessel.contents().air = 0f;
            helper.assertTrue(PipePhysics.flashEnergy(vessel.contents().litres, vessel.contents().temperature) > 20000f, "It holds about 27 MJ of flash");
            level(helper).destroyBlock(helper.absolutePos(new BlockPos(2, 1, 2)), false);
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(near.scalded(), "The steam scalds the player by the boiler");
            helper.assertTrue(near.mostDamage > 0.5f * near.getMaxHealth(), "and takes more than half of the health from him, got " + near.mostDamage);
            helper.assertFalse(far.scalded(), "The player further off than four blocks is not touched");
            near.discard();
            far.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BURST_BATCH)
    public static void aPipeOfColdWaterThatBurstsScaldsNobody(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 2, ironPipe());
        final TestPlayer near = playerAt(helper, new BlockPos(2, 1, 4));
        helper.runAtTickTime(4, () -> {
            requireNetwork(helper, 2, 1, 2).contents().addLiquid(WATER, 5f, PipePhysics.ROOM_TEMPERATURE);
            level(helper).destroyBlock(helper.absolutePos(new BlockPos(2, 1, 2)), false);
        });
        helper.runAtTickTime(8, () -> {
            helper.assertFalse(near.scalded(), "Water below its boiling point does not flash");
            near.discard();
            helper.succeed();
        });
    }

    // ---- P8: a pump that stands still is still loaded ----

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aPumpThatStandsStillIsLoadedByThePressureItStandsAgainst(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 2, pump(Direction.NORTH));
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.runAtTickTime(5, () -> {
            final PipeNetwork vessel = requireNetwork(helper, 2, 1, 1);
            vessel.contents().air = PipePhysics.moles(3f, 293f, vessel.volume());
            final float torque = pumpAt(helper, 2, 1, 2).tickPump(level(helper), 1f);
            helper.assertTrue(torque > 0.5f, "A pump with no shaft turning still has 2 atm to push against: it loads the shaft by 0.67, got " + torque);
            helper.succeed();
        });
    }

    // ---- P10: a wall that has gone gives nothing back ----

    private static int itemsAround(GameTestHelper helper, BlockPos pos)
    {
        final List<ItemEntity> items = level(helper).getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(0.9));
        final int count = items.size();
        items.forEach(ItemEntity::discard);
        return count;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void blocksOfANetworkGiveThemselvesBackOnlyWhileTheirWallsAreWhole(GameTestHelper helper)
    {
        final BlockPos[] fresh = {new BlockPos(1, 1, 1), new BlockPos(1, 1, 3), new BlockPos(1, 1, 5), new BlockPos(1, 1, 7)};
        final BlockPos[] worn = {new BlockPos(5, 1, 1), new BlockPos(5, 1, 3), new BlockPos(5, 1, 5), new BlockPos(5, 1, 7)};
        final List<BlockState> states = List.of(ironPipe().defaultBlockState(), fitting(Pipes.VALVE, Direction.EAST, true), Pipes.PRESSURE_VESSEL.get().defaultBlockState(), Pipes.MANOMETER.get().defaultBlockState());
        for (int i = 0; i < states.size(); i++)
        {
            helper.setBlock(fresh[i], states.get(i));
            helper.setBlock(worn[i], states.get(i));
        }
        helper.runAtTickTime(4, () -> {
            for (BlockPos pos : worn)
            {
                tracker(helper).segmentAt(helper.absolutePos(pos)).integrity = 0.9f;
            }
            for (BlockPos pos : fresh)
            {
                level(helper).destroyBlock(helper.absolutePos(pos), true);
            }
        });
        helper.runAtTickTime(8, () -> {
            for (BlockPos pos : fresh)
            {
                final int count = itemsAround(helper, pos);
                helper.assertTrue(count >= 1, "A whole block gives itself back: " + pos + " " + helper.getBlockState(pos) + " items " + count);
            }
            for (BlockPos pos : worn)
            {
                level(helper).destroyBlock(helper.absolutePos(pos), true);
            }
        });
        helper.runAtTickTime(12, () -> {
            for (BlockPos pos : worn)
            {
                helper.assertTrue(itemsAround(helper, pos) == 0, "A wall that has lost a tenth is scrap: " + pos);
            }
            helper.succeed();
        });
    }

    // ---- P12: valves and attaching ----

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = BATCH)
    public static void aValveIsNotTurnedTwiceInASecond(GameTestHelper helper)
    {
        helper.setBlock(1, 1, 2, ironPipe());
        helper.setBlock(2, 1, 2, fitting(Pipes.VALVE, Direction.EAST, false));
        helper.setBlock(3, 1, 2, ironPipe());
        final BlockPos valve = new BlockPos(2, 1, 2);
        final FakePlayer player = new FakePlayer(level(helper), new GameProfile(UUID.randomUUID(), "tfcpp_pipe_test"));
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(valve)), Direction.UP, helper.absolutePos(valve), false);

        helper.runAtTickTime(5, () -> {
            helper.getBlockState(valve).useWithoutItem(level(helper), player, hit);
            helper.assertTrue(helper.getBlockState(valve).getValue(PipeFittingBlock.OPEN), "The first turn opens it");
            helper.getBlockState(valve).useWithoutItem(level(helper), player, hit);
            helper.assertTrue(helper.getBlockState(valve).getValue(PipeFittingBlock.OPEN), "A second turn in the same second is refused, with a message");
        });
        helper.runAtTickTime(40, () -> {
            helper.getBlockState(valve).useWithoutItem(level(helper), player, hit);
            helper.assertFalse(helper.getBlockState(valve).getValue(PipeFittingBlock.OPEN), "A second later it turns");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = BATCH)
    public static void aPipeBesideOneNetworkJoinsItWithoutTakingItApart(GameTestHelper helper)
    {
        helper.setBlock(1, 1, 1, ironPipe());
        helper.setBlock(2, 1, 1, ironPipe());
        helper.setBlock(3, 1, 1, ironPipe());
        helper.runAtTickTime(4, () -> {
            final PipeNetwork network = requireNetwork(helper, 1, 1, 1);
            network.contents().gases[Gas.METHANE.ordinal()] = 6f;
            network.contents().addLiquid(WATER, 3f, PipePhysics.ROOM_TEMPERATURE);
            helper.setBlock(4, 1, 1, ironPipe());
        });
        helper.runAtTickTime(10, () -> {
            final PipeNetwork network = requireNetwork(helper, 1, 1, 1);
            helper.assertTrue(network.segments().size() == 4 && network == networkAt(helper, 4, 1, 1), "The new pipe should be in the same network, with the object kept");
            helper.assertTrue(Math.abs(totalOf(network, Gas.METHANE) - 6f) < 1e-4f && Math.abs(network.contents().litres - 3f) < 1e-4f, "and the contents kept as they were");
            helper.assertTrue(Math.abs(network.volume() - 0.04f) < 1e-5f, "and the volume grown by the one pipe, got " + network.volume());
            helper.setBlock(2, 2, 1, ironPipe());
            helper.setBlock(3, 2, 1, ironPipe());
        });
        helper.runAtTickTime(16, () -> {
            final PipeNetwork network = requireNetwork(helper, 1, 1, 1);
            helper.assertTrue(network.segments().size() == 6 && network == networkAt(helper, 3, 2, 1), "Two pipes put in together are flooded in one pass and join all the same");
            helper.succeed();
        });
    }

    // ---- P12: the cost of a rebuild ----

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = CHUNK_BATCH)
    public static void aMainOfTwoThousandSegmentsIsRebuiltInReasonableTime(GameTestHelper helper)
    {
        final Pair pair = farPair(helper, 3);
        final ServerLevel level = pair.level();
        final PipeTracker tracker = PipeTracker.get(level);
        // The cube joins itself on all sides, which is a network of 2016 segments with six neighbours each
        final List<BlockPos> cube = new ArrayList<>();
        for (int x = 0; x < 12; x++)
        {
            for (int y = 0; y < 14; y++)
            {
                for (int z = 0; z < 12; z++)
                {
                    cube.add(pair.at(-6 + x, y, z));
                }
            }
        }
        final BlockState iron = ironPipe().defaultBlockState();
        final long placeStart = System.nanoTime();
        for (BlockPos pos : cube)
        {
            level.setBlock(pos, iron, Block.UPDATE_CLIENTS);
        }
        final double placing = (System.nanoTime() - placeStart) / 1e6;

        final long formStart = System.nanoTime();
        tracker.tick(level);
        final double forming = (System.nanoTime() - formStart) / 1e6;
        final @Nullable PipeNetwork main = tracker.networkAt(cube.getFirst());
        helper.assertTrue(main != null && main.segments().size() == cube.size(), "The cube should be one network of " + cube.size() + " segments");

        final long rebuildStart = System.nanoTime();
        tracker.onConnectionChanged(level, cube.get(cube.size() / 2));
        tracker.tick(level);
        final double rebuilding = (System.nanoTime() - rebuildStart) / 1e6;
        final @Nullable PipeNetwork rebuilt = tracker.networkAt(cube.getFirst());
        helper.assertTrue(rebuilt != null && rebuilt != main && rebuilt.segments().size() == cube.size(), "A valve turned in it rebuilds the whole");

        final BlockPos extra = pair.at(-7, 0, 0);
        final long attachStart = System.nanoTime();
        level.setBlock(extra, iron, Block.UPDATE_CLIENTS);
        tracker.tick(level);
        final double attaching = (System.nanoTime() - attachStart) / 1e6;
        helper.assertTrue(tracker.networkAt(extra) == rebuilt && rebuilt.segments().size() == cube.size() + 1, "One pipe put beside it is attached to it, and not by a rebuild");

        LOGGER.info("[pipes-timing] main of {} segments: placing the blocks {} ms ({} ms each), first forming {} ms, rebuild after a valve turn {} ms, attaching one pipe {} ms", cube.size(), String.format("%.1f", placing), String.format("%.3f", placing / cube.size()), String.format("%.1f", forming), String.format("%.1f", rebuilding), String.format("%.2f", attaching));
        helper.assertTrue(rebuilding < 1500.0, "A rebuild of 2016 segments took " + rebuilding + " ms");
        helper.assertTrue(attaching * 5.0 < rebuilding, "Attaching should cost a small part of a rebuild: " + attaching + " against " + rebuilding);

        final BlockState air = Blocks.AIR.defaultBlockState();
        level.setBlock(extra, air, Block.UPDATE_CLIENTS);
        for (BlockPos pos : cube)
        {
            level.setBlock(pos, air, Block.UPDATE_CLIENTS);
        }
        pair.release();
        helper.succeed();
    }

    // ---- P13, P18, P20, P24: the pump ----

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aPumpDoesNotDrawLavaIntoAPipe(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 2, pump(Direction.NORTH));
        helper.setBlock(2, 1, 1, ironPipe());
        helper.setBlock(2, 1, 3, Blocks.LAVA);
        helper.setBlock(1, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(3, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 4, Blocks.OBSIDIAN);
        helper.runAtTickTime(5, () -> {
            pumpAt(helper, 2, 1, 2).pump(level(helper), 0.002f);
            helper.assertTrue(requireNetwork(helper, 2, 1, 1).contents().litres == 0f, "Lava must not be put in a pipe at room temperature");
            helper.assertBlockPresent(Blocks.LAVA, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aPumpDeliversOnlyThroughAnOpeningThatFacesIt(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 2, pump(Direction.NORTH));
        helper.setBlock(2, 1, 1, fitting(Pipes.VENT, Direction.SOUTH, true)); // Its mouth is toward the pump
        helper.setBlock(6, 1, 2, pump(Direction.NORTH));
        helper.setBlock(6, 1, 1, fitting(Pipes.VENT, Direction.NORTH, true)); // Its back is toward the pump
        helper.runAtTickTime(5, () -> {
            final PipeNetwork shut = requireNetwork(helper, 2, 1, 1), open = requireNetwork(helper, 6, 1, 1);
            final float shutBefore = shut.contents().air, openBefore = open.contents().air;
            pumpAt(helper, 2, 1, 2).pump(level(helper), 0.002f);
            pumpAt(helper, 6, 1, 2).pump(level(helper), 0.002f);
            helper.assertTrue(shut.contents().air == shutBefore, "A vent that looks into the pump takes nothing from it");
            helper.assertTrue(open.contents().air > openBefore, "and one that is open toward it does");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aFilmOfAnotherLiquidDoesNotKeepWaterOutOfAPipe(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 2, pump(Direction.NORTH));
        helper.setBlock(2, 1, 1, ironPipe());
        helper.setBlock(2, 1, 3, Blocks.WATER);
        helper.setBlock(1, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(3, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 4, Blocks.OBSIDIAN);
        helper.setBlock(6, 1, 2, pump(Direction.NORTH));
        helper.setBlock(6, 1, 1, ironPipe());
        helper.setBlock(6, 1, 3, Blocks.WATER);
        helper.setBlock(5, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(7, 1, 3, Blocks.OBSIDIAN);
        helper.setBlock(6, 1, 4, Blocks.OBSIDIAN);
        helper.runAtTickTime(5, () -> {
            final PipeNetwork filmed = requireNetwork(helper, 2, 1, 1), soaked = requireNetwork(helper, 6, 1, 1);
            filmed.contents().liquid = ACID;
            filmed.contents().litres = 0.4f; // Less than a millilitre: the wet of the walls
            soaked.contents().liquid = ACID;
            soaked.contents().litres = 3f;
            pumpAt(helper, 2, 1, 2).pump(level(helper), 0.002f);
            pumpAt(helper, 6, 1, 2).pump(level(helper), 0.002f);
            helper.assertTrue(filmed.contents().liquid.equals(WATER) && filmed.contents().litres > 1.9f, "The film is washed out ahead of the water, which is not kept out by it");
            helper.assertTrue(soaked.contents().liquid.equals(ACID) && soaked.contents().litres == 3f, "but three litres of acid keep the water out");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aPumpGivesWhatItSweepsAndNotAMillilitreMoreEveryStroke(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 2, pump(Direction.NORTH));
        helper.setBlock(2, 1, 1, ironPipe());
        helper.setBlock(2, 1, 3, TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.BARREL).get());
        helper.runAtTickTime(3, () -> {
            final IFluidHandler barrel = level(helper).getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(new BlockPos(2, 1, 3)), Direction.UP);
            helper.assertTrue(barrel != null && barrel.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE) == 100, "The barrel should take its 100 millilitres");
        });
        helper.runAtTickTime(5, () -> {
            final PistonPumpBlockEntity pump = pumpAt(helper, 2, 1, 2);
            for (int stroke = 0; stroke < 10; stroke++)
            {
                pump.pump(level(helper), 0.0003f); // 0.3 litre a stroke
            }
            final IFluidHandler barrel = level(helper).getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(new BlockPos(2, 1, 3)), Direction.UP);
            final float delivered = requireNetwork(helper, 2, 1, 1).contents().litres;
            final int drained = 100 - barrel.getFluidInTank(0).getAmount();
            helper.assertTrue(Math.abs(delivered - 3f) < 0.05f, "Ten strokes of 0.3 litre give 3 litres, not a litre each: " + delivered);
            helper.assertTrue(drained >= 3 && drained <= 4, "and the barrel loses what is delivered and the part of a millilitre that is held for the next stroke, got " + drained);
            helper.succeed();
        });
    }

    // ---- P17: the tube of a manometer ----

    @GameTest(template = TEMPLATE, timeoutTicks = 160, batch = BATCH)
    public static void aBlownManometerLeaksAHundredthOfTheBoreAndThrowsMercuryOnlyOnce(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3);
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(2, 1, 2, Pipes.MANOMETER.get());
        helper.runAtTickTime(5, () -> requireNetwork(helper, 2, 1, 1).contents().air = PipePhysics.moles(5f, 293f, 0.5f));
        final float[] mercury = new float[1];
        helper.runAtTickTime(45, () -> {
            final BlockState state = helper.getBlockState(new BlockPos(2, 1, 2));
            helper.assertTrue(state.is(Pipes.MANOMETER.get()) && state.getValue(ManometerBlock.BLOWN), "The tube is blown, and what is left stays");
            final PipeNetwork network = requireNetwork(helper, 2, 1, 1);
            helper.assertTrue(network == networkAt(helper, 2, 1, 2), "The vessel is still joined to it: the line was not torn open");
            helper.assertTrue(network.pressure() > 3f, "and holds most of its pressure, got " + network.pressure());
            mercury[0] = gasInBox(helper, 3, 3, Gas.MERCURY_VAPOR);
            helper.assertTrue(mercury[0] > 0.1f, "The mercury is in the room, got " + mercury[0]);
        });
        helper.runAtTickTime(140, () -> {
            final float mercuryNow = gasInBox(helper, 3, 3, Gas.MERCURY_VAPOR);
            helper.assertTrue(mercuryNow < mercury[0] + 0.01f, "and no more of it comes: " + mercuryNow + " against " + mercury[0]);
            helper.assertTrue(requireNetwork(helper, 2, 1, 1).pressure() < 4.99f, "The broken tube leaks, slowly");
            helper.succeed();
        });
    }

    // ---- P21: a pipe broken between a dissolving and a rebuild ----

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = BATCH)
    public static void aSegmentBrokenBetweenNetworksSpillsItsLiquid(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 2, ironPipe());
        final PipeSegment segment = tracker(helper).segmentAt(helper.absolutePos(new BlockPos(2, 1, 2)));
        helper.assertTrue(segment != null && segment.network == null, "No network has formed yet");
        segment.stored.addLiquid(WATER, 1500f, PipePhysics.ROOM_TEMPERATURE);
        level(helper).destroyBlock(helper.absolutePos(new BlockPos(2, 1, 2)), false);
        int water = 0;
        for (int x = 0; x <= 4; x++)
        {
            for (int y = 0; y <= 2; y++)
            {
                for (int z = 0; z <= 4; z++)
                {
                    water += helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER) ? 1 : 0;
                }
            }
        }
        helper.assertTrue(water >= 1, "A block of the water that was in the pipe should be on the ground around it, found " + water);
        helper.succeed();
    }

    // ---- P23: a boiler cannot be cooled below its boiling point by letting steam out ----

    @GameTest(template = TEMPLATE, timeoutTicks = 140, batch = BATCH)
    public static void steamLetOutOfABoilerCoolsItToTheBoilingPointAndNoLower(GameTestHelper helper)
    {
        helper.setBlock(2, 1, 1, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(2, 1, 2, fitting(Pipes.VENT, Direction.SOUTH, true));
        final float[] lowest = {Float.MAX_VALUE};
        final boolean[] started = {false};
        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = requireNetwork(helper, 2, 1, 1);
            network.contents().liquid = WATER;
            network.contents().litres = 5f;
            network.contents().air = 0f;
            network.contents().temperature = 400f;
            started[0] = true;
        });
        helper.onEachTick(() -> {
            final @Nullable PipeNetwork network = networkAt(helper, 2, 1, 1);
            if (started[0] && network != null && network.contents().litres > 0f)
            {
                lowest[0] = Math.min(lowest[0], network.contents().temperature);
                network.contents().temperature = Math.max(network.contents().temperature, 400f); // Held hot, so that every step boils
            }
        });
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(lowest[0] > 372f, "Boiling off steam against the air cannot take the water below 100 C, but it went to " + lowest[0] + " K");
            helper.assertTrue(requireNetwork(helper, 2, 1, 1).contents().litres < 5f, "and some of it did boil off");
            helper.succeed();
        });
    }

    // ---- P9, P10 etc. recipes and data are checked elsewhere ----
}
