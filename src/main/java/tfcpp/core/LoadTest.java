package tfcpp.core;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.dries007.tfc.common.blockentities.FirepitBlockEntity;
import net.dries007.tfc.common.blockentities.rotation.CreativeRotationBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.devices.FirepitBlock;
import net.dries007.tfc.common.blocks.wood.Wood;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.rotation.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import tfcpp.metallurgy.ConverterBath;
import tfcpp.metallurgy.ConverterBlockEntity;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.metallurgy.RetortBlockEntity;
import tfcpp.pneumatics.gas.Breathing;
import tfcpp.pneumatics.gas.FireSource;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasChunkData;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.pipes.PipeFittingBlock;
import tfcpp.pneumatics.pipes.PipeMaterial;
import tfcpp.pneumatics.pipes.PipeMember;
import tfcpp.pneumatics.pipes.PipeSegment;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipePhysics;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.Pipes;
import tfcpp.pneumatics.pipes.PistonPumpBlockEntity;

/**
 * Measures what the mod costs per tick on a large server base: sixteen sealed rooms of gas, forty fires, a hundred pipe networks and a two kilometre main, pumps, converters, retorts and forty people breathing.
 * <p>
 * It is not a pass-or-fail test, and does nothing unless the server is started with {@code -Pload=1}, so ordinary runs lose nothing. It reads {@link TickProfiler}, which the mod keeps in any case, and logs every line with the prefix {@code TFCPP_LOAD}.
 * <p>
 * Nothing in the base is faked: every part is made of the real blocks and block entities, set up the way the other game tests set them up. A group that could not be made that way is left out, and the log says so.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LoadTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TEMPLATE = "empty9";
    private static final String PROPERTY = "tfcpp.load";
    private static final String PREFIX = "TFCPP_LOAD ";

    private static final int SETTLE_TICKS = 200, MEASURE_TICKS = 1200, REPORT_TICKS = 600, STATS_TICKS = 100;
    /** The valve is opened this far into the measurement, and shut again after {@link #VALVE_HOLD_TICKS}. */
    private static final int VALVE_OPENS_AT = 300, VALVE_HOLD_TICKS = 40;
    /** Building must not be the thing that stalls the server, or the build would be what the watchdog stops. */
    private static final long BUILD_BUDGET_NANOS = 30_000_000L;
    private static final int FORMING_TICKS = 10;

    private static final int BASE_ROOMS = 16, ROOM_SIZE = 16, ROOM_HEIGHT = 8, ROOM_PITCH = 32, ROOMS_PER_ROW = 4;
    private static final int FIRES = 40, METHANE_ROOM = BASE_ROOMS - 1;
    private static final int NETWORKS = 100, NETWORK_PIPES = 50, NETWORK_PITCH_X = 64, NETWORK_PITCH_Z = 2, NETWORKS_PER_ROW = 5;
    private static final int MAIN_PIPES = 2000, MAIN_BLOCKS_PER_STEP = 250;
    private static final int PUMPS = 30, PUMP_PITCH = 5, CONVERTERS = 10, CONVERTER_PITCH = 3, RETORTS = 20, RETORT_PITCH = 3, PLAYERS = 40;

    private static final int GAS_PULSE_TICKS = 20, GAS_CELLS_PER_PULSE = 3, REFILL_TICKS = 100, PRESSURISE_TICKS = 200;
    private static final float CARBON_DIOXIDE_PER_PULSE = 0.5f, CARBON_MONOXIDE_PER_PULSE = 0.1f;
    /** Well under the 5 % at which methane burns. */
    private static final float METHANE_FRACTION = 0.03f;
    private static final float RECEIVER_ATMOSPHERES = 3f, MAIN_ATMOSPHERES = 2f, RETORT_CELSIUS = 1100f;
    private static final int FUEL_SLOTS = 4;
    /** Methane burns completely with oxygen in about 9.5 % of air by volume: CH4 + 2 O2 gives CO2 and water, and oxygen is a fifth of air. */
    private static final float STOICHIOMETRIC_METHANE = 0.095f;
    /** A cell with this much methane still counts as holding some: it started with about 4 mol. */
    private static final float MOLES_THAT_COUNT = 0.5f;
    private static final int BLAST_WAIT_TICKS = 400, BLAST_AFTER_TICKS = 3;

    @GameTest(template = TEMPLATE, required = false, batch = "tfcpp_load", timeoutTicks = 3000)
    public static void aLargeServerBase(GameTestHelper helper)
    {
        if (System.getProperty(PROPERTY) == null)
        {
            helper.succeed();
            return;
        }
        new Base(helper).start();
    }

    /** {@code -Pload=<n>} is a multiplier for the number of gas rooms, so that the same base can be made bigger than the gas budget. Anything that is not a whole number of at least 1 is 1. */
    private static int scale()
    {
        try
        {
            return Math.max(1, Integer.parseInt(System.getProperty(PROPERTY, "1").trim()));
        }
        catch (NumberFormatException e)
        {
            return 1;
        }
    }

    private static void log(String line)
    {
        LOGGER.info(PREFIX + line);
    }

    /** Not a mock player, as joining one would make TFC sync data over a connection that does not exist. Nothing it breathes may hurt it, as this measures time and not survival. */
    private static final class LoadPlayer extends FakePlayer
    {
        LoadPlayer(ServerLevel level, int number)
        {
            super(level, new GameProfile(UUID.randomUUID(), "tfcpp_load_" + number));
        }

        @Override
        public boolean hurt(DamageSource source, float amount)
        {
            return true;
        }
    }

    private static final class Step
    {
        final String group;
        final Runnable work;

        Step(String group, Runnable work)
        {
            this.group = group;
            this.work = work;
        }
    }

    private static final class Base
    {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos origin;
        private final int baseX, baseZ, floorY;
        private final int scale, rooms, zoneShift;
        private final int fieldZ, mainZ, yardZ;

        private final Deque<Step> steps = new ArrayDeque<>();
        private final Map<String, Long> buildNanos = new LinkedHashMap<>();
        private final LongOpenHashSet forced = new LongOpenHashSet();
        private final Random random = new Random(20261101L);
        private final TickProfile wall = new TickProfile("wall");

        private final List<BlockPos> parts = new ArrayList<>();
        private final List<BlockPos> fires = new ArrayList<>();
        private final List<BlockPos> receivers = new ArrayList<>();
        private final List<BlockPos> pumps = new ArrayList<>();
        private final List<BlockPos> motors = new ArrayList<>();
        private final List<BlockPos> converters = new ArrayList<>();
        private final List<BlockPos> converterReceivers = new ArrayList<>();
        private final List<BlockPos> retorts = new ArrayList<>();
        private final List<ServerPlayer> players = new ArrayList<>();
        private final LongArrayList watched = new LongArrayList();
        private BlockPos valve;
        private BlockPos mainWest, mainEast;

        private final long startedAt = System.nanoTime();
        private long measureStartedAt;
        private long lastCallbackAt;
        private int tick;
        private int armedAt = -1;
        private int formingTicks;
        private int measuring = -1;
        private boolean failed;

        /** 0 before the blast, 1 waiting for the flame to catch, 2 after the blast and watching the ticks that follow, 3 over. */
        private int blastStage;
        private int blastHit = -1, blastCellsBefore;
        private float blastMethaneBefore;
        /** Gas section, all sections and whole tick, in ms, for each tick since the torch was set. */
        private final List<double[]> blastTicks = new ArrayList<>();

        private int starvedTicks;
        private long worstLag, overdueChunkTicks;
        /** What the gas grid stepped in each measured tick, seen from outside by the stamp it leaves on a chunk. */
        private final IntArrayList cellsPerTick = new IntArrayList(), chunksPerTick = new IntArrayList();
        private int droppedGas;
        private int valveOpenedNetworks, valveClosedNetworks;
        private int networksBefore;
        private double openSpikePipe, openSpikeTotal, closeSpikePipe, closeSpikeTotal;
        private double openCallMillis, closeCallMillis;

        Base(GameTestHelper helper)
        {
            this.helper = helper;
            this.level = helper.getLevel();
            this.origin = helper.absolutePos(BlockPos.ZERO);
            // Far from the other tests, which sit in a grid near the origin, and aligned to chunks so that each room has its own
            this.baseX = ((origin.getX() >> 4) + 256) << 4;
            this.baseZ = ((origin.getZ() >> 4) + 1) << 4;
            this.floorY = origin.getY();
            this.scale = scale();
            this.rooms = BASE_ROOMS * scale;
            // The rooms stand in rows of four, and the rest of the base starts after the last of them
            this.zoneShift = (rooms / ROOMS_PER_ROW - BASE_ROOMS / ROOMS_PER_ROW) * ROOM_PITCH;
            this.fieldZ = baseZ + 144 + zoneShift;
            this.mainZ = baseZ + 200 + zoneShift;
            this.yardZ = baseZ + 240 + zoneShift;
        }

        void start()
        {
            if (!helper.absolutePos(rel(baseX + 5, floorY + 3, baseZ + 7)).equals(new BlockPos(baseX + 5, floorY + 3, baseZ + 7)))
            {
                helper.fail("The test is rotated, which this layout does not allow for");
                return;
            }
            Calendars.SERVER.setPlayersLoggedOn(true); // The TFC calendar stands still on an empty server, and fires burn by the calendar

            log("origin " + origin.toShortString() + ", base at " + baseX + " " + floorY + " " + baseZ + "; fires already tracked before building: " + GasTracker.get(level).fireCount() + ", pipe networks " + PipeTracker.get(level).networkCount() + ", active gas chunks " + GasTracker.get(level).activeChunkCount());
            log("gas budget per tick: " + ServerConfig.GAS_CHUNKS_PER_TICK.getAsInt() + " chunks, " + ServerConfig.GAS_CELLS_PER_TICK.getAsInt() + " cells, " + ServerConfig.GAS_CELLS_PER_CHUNK.getAsInt() + " cells a chunk; fans " + ServerConfig.GAS_FAN_CELLS_PER_TICK.getAsInt() + " cells; pipe rebuild " + ServerConfig.PIPE_REBUILD_SEGMENTS_PER_TICK.getAsInt() + " segments");

            log("scale " + scale + ": " + rooms + " gas rooms, " + rooms * ROOM_SIZE * ROOM_SIZE * ROOM_HEIGHT + " cells in them");
            traceStaleState();

            queueRooms();
            queueFires();
            queuePipeNetworks();
            queueMain();
            queueMachines();
            helper.onEachTick(this::onTick);
        }

        /**
         * What the trackers hold before this test builds anything, and whether the blocks they hold are still standing. The trackers keep these in private fields, which are read here by reflection because this is the only place that needs to see them: it is a diagnostic, and changes nothing.
         */
        @SuppressWarnings("unchecked")
        private void traceStaleState()
        {
            try
            {
                final GasTracker gas = GasTracker.get(level);
                final Field firesField = GasTracker.class.getDeclaredField("fires");
                firesField.setAccessible(true);
                for (FireSource fire : ((Long2ObjectMap<FireSource>) firesField.get(gas)).values())
                {
                    final BlockEntity entity = fire.entity();
                    final BlockPos pos = entity.getBlockPos();
                    log("stale fire " + describe(pos) + ": block now " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()) + ", entity removed " + entity.isRemoved() + ", still the entity in the world " + (level.getBlockEntity(pos) == entity) + ", chunk loaded " + level.hasChunkAt(pos));
                }

                final Field activeField = GasTracker.class.getDeclaredField("activeChunks");
                activeField.setAccessible(true);
                for (long key : (LongLinkedOpenHashSet) activeField.get(gas))
                {
                    final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                    final String cells = chunk != null && chunk.hasData(GasAttachments.CHUNK) ? chunk.getData(GasAttachments.CHUNK).size() + " cells" : "no gas data";
                    log("stale gas chunk at " + (ChunkPos.getX(key) * 16 - origin.getX()) + " " + (ChunkPos.getZ(key) * 16 - origin.getZ()) + " from the origin: loaded " + (chunk != null) + ", " + cells);
                }

                final PipeTracker pipes = PipeTracker.get(level);
                final Field segmentsField = PipeTracker.class.getDeclaredField("segments");
                segmentsField.setAccessible(true);
                final Map<PipeNetwork, int[]> byNetwork = new LinkedHashMap<>();
                final Map<PipeNetwork, PipeSegment> first = new LinkedHashMap<>();
                int unformed = 0;
                for (PipeSegment segment : ((Long2ObjectMap<PipeSegment>) segmentsField.get(pipes)).values())
                {
                    if (segment.network == null)
                    {
                        unformed++;
                        continue;
                    }
                    final int[] counts = byNetwork.computeIfAbsent(segment.network, network -> new int[2]);
                    first.putIfAbsent(segment.network, segment);
                    counts[0]++;
                    counts[1] += level.getBlockState(segment.pos).getBlock() instanceof PipeMember ? 1 : 0;
                }
                byNetwork.forEach((network, counts) -> {
                    final BlockPos pos = first.get(network).pos;
                    log("stale pipe network " + describe(pos) + ": " + counts[0] + " segments, " + counts[1] + " with a pipe block still standing, first one is " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()) + ", chunk loaded " + level.hasChunkAt(pos));
                });
                log("segments not in any network: " + unformed);
            }
            catch (ReflectiveOperationException error)
            {
                log("could not look inside the trackers: " + error);
            }
        }

        private String describe(BlockPos pos)
        {
            return "at " + (pos.getX() - origin.getX()) + " " + (pos.getY() - origin.getY()) + " " + (pos.getZ() - origin.getZ()) + " from the origin";
        }

        // ---- Coordinates ----

        private BlockPos rel(int x, int y, int z)
        {
            return new BlockPos(x - origin.getX(), y - origin.getY(), z - origin.getZ());
        }

        private void set(int x, int y, int z, BlockState state)
        {
            helper.setBlock(rel(x, y, z), state);
        }

        /** A block that is not part of a room's shell, and so is taken away again at the end. */
        private void part(int x, int y, int z, BlockState state)
        {
            parts.add(new BlockPos(x, y, z));
            set(x, y, z, state);
        }

        private int roomX(int room)
        {
            return baseX + (room % ROOMS_PER_ROW) * ROOM_PITCH;
        }

        private int roomZ(int room)
        {
            return baseZ + (room / ROOMS_PER_ROW) * ROOM_PITCH;
        }

        private void force(int x0, int z0, int x1, int z1)
        {
            for (int chunkX = x0 >> 4; chunkX <= x1 >> 4; chunkX++)
            {
                for (int chunkZ = z0 >> 4; chunkZ <= z1 >> 4; chunkZ++)
                {
                    if (forced.add(ChunkPos.asLong(chunkX, chunkZ)))
                    {
                        level.setChunkForced(chunkX, chunkZ, true);
                    }
                }
            }
        }

        private void queue(String group, Runnable work)
        {
            steps.add(new Step(group, work));
        }

        // ---- Building ----

        private void queueRooms()
        {
            for (int room = 0; room < rooms; room++)
            {
                final int number = room;
                queue("rooms", () -> buildRoom(number));
            }
            // For the blast at the end, 80 blocks from the nearest room and out of the way of everything else. It holds no gas until then, and is not watched. Brick and not stone: TFC lists stone as rock that can collapse, which a gas blast never breaks, so a stone shell would show no block breaking at all.
            queue("blast room", () -> buildShell(blastX(), blastZ(), Blocks.BRICKS));
        }

        private int blastX()
        {
            return baseX + 160;
        }

        private int blastZ()
        {
            return baseZ - 96;
        }

        /** A hollow stone box whose interior is exactly one chunk, 16 by 8 by 16. */
        private void buildRoom(int room)
        {
            final int x0 = roomX(room), z0 = roomZ(room);
            buildShell(x0, z0, Blocks.STONE);
            watched.add(ChunkPos.asLong(x0 >> 4, z0 >> 4));
        }

        private void buildShell(int x0, int z0, Block wall)
        {
            force(x0 - 1, z0 - 1, x0 + ROOM_SIZE, z0 + ROOM_SIZE);
            for (int x = x0 - 1; x <= x0 + ROOM_SIZE; x++)
            {
                for (int y = floorY; y <= floorY + ROOM_HEIGHT + 1; y++)
                {
                    for (int z = z0 - 1; z <= z0 + ROOM_SIZE; z++)
                    {
                        final boolean inside = x >= x0 && x < x0 + ROOM_SIZE && z >= z0 && z < z0 + ROOM_SIZE && y > floorY && y <= floorY + ROOM_HEIGHT;
                        set(x, y, z, inside ? Blocks.AIR.defaultBlockState() : wall.defaultBlockState());
                    }
                }
            }
        }

        /** Forty firepits spread over the rooms that are not the methane room, as a flame in firedamp would be a different test. */
        private void queueFires()
        {
            for (int fire = 0; fire < FIRES; fire++)
            {
                final int room = fire % (BASE_ROOMS - 1);
                final int slot = fire / (BASE_ROOMS - 1);
                final int x = roomX(room) + 3 + slot * 4, z = roomZ(room) + 3 + slot * 4;
                queue("fires", () -> {
                    part(x, floorY + 1, z, TFCBlocks.FIREPIT.get().defaultBlockState());
                    fires.add(new BlockPos(x, floorY + 1, z));
                });
            }
        }

        private void queuePipeNetworks()
        {
            for (int network = 0; network < NETWORKS; network++)
            {
                final int x0 = baseX + (network % NETWORKS_PER_ROW) * NETWORK_PITCH_X;
                final int z = fieldZ + (network / NETWORKS_PER_ROW) * NETWORK_PITCH_Z;
                queue("networks", () -> {
                    force(x0, z, x0 + NETWORK_PIPES + 2, z);
                    part(x0, floorY + 1, z, Pipes.PRESSURE_VESSEL.get().defaultBlockState());
                    for (int i = 1; i <= NETWORK_PIPES; i++)
                    {
                        part(x0 + i, floorY + 1, z, pipe());
                    }
                    part(x0 + NETWORK_PIPES + 1, floorY + 1, z, fitting(Pipes.VENT.get(), Direction.EAST, true));
                    receivers.add(new BlockPos(x0, floorY + 1, z));
                });
            }
        }

        /** A vessel at each end, 2000 pipes between them and a closed valve in the middle: two runs of a kilometre, in 126 chunks. */
        private void queueMain()
        {
            final int length = MAIN_PIPES + 3;
            final int valveIndex = 1 + MAIN_PIPES / 2;
            valve = new BlockPos(baseX + valveIndex, floorY + 1, mainZ);
            mainWest = new BlockPos(baseX, floorY + 1, mainZ);
            mainEast = new BlockPos(baseX + length - 1, floorY + 1, mainZ);
            for (int from = 0; from < length; from += MAIN_BLOCKS_PER_STEP)
            {
                final int start = from;
                queue("main", () -> {
                    force(baseX + start, mainZ, baseX + Math.min(start + MAIN_BLOCKS_PER_STEP, length), mainZ);
                    for (int i = start; i < Math.min(start + MAIN_BLOCKS_PER_STEP, length); i++)
                    {
                        final BlockState state;
                        if (i == 0 || i == length - 1)
                        {
                            state = Pipes.PRESSURE_VESSEL.get().defaultBlockState();
                        }
                        else if (i == valveIndex)
                        {
                            state = fitting(Pipes.VALVE.get(), Direction.EAST, false);
                        }
                        else
                        {
                            state = pipe();
                        }
                        part(baseX + i, floorY + 1, mainZ, state);
                    }
                });
            }
        }

        /**
         * A pump is a block on the end of a short shaft, with a creative motor behind it, because a shaft only joins its ends. Its output goes into a vessel with an open vent, and its intake is plain air.
         * <p>
         * Converters sit beside a vessel of compressed air, with hot pig iron in them. Retorts sit in the open, half with a condenser and half without.
         */
        private void queueMachines()
        {
            final Block axle = TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.AXLE).get();
            for (int pump = 0; pump < PUMPS; pump++)
            {
                final int z = yardZ + pump * PUMP_PITCH;
                final int x = baseX + 4;
                queue("pumps", () -> {
                    force(x - 1, z - 3, x + 3, z + 1);
                    part(x, floorY + 1, z, Pipes.PISTON_PUMP.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    part(x, floorY + 1, z - 1, Pipes.PRESSURE_VESSEL.get().defaultBlockState());
                    part(x, floorY + 1, z - 2, fitting(Pipes.VENT.get(), Direction.NORTH, true));
                    part(x + 1, floorY + 1, z, axle.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                    part(x + 2, floorY + 1, z, TFCBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                    pumps.add(new BlockPos(x, floorY + 1, z));
                    motors.add(new BlockPos(x + 2, floorY + 1, z));
                });
            }
            for (int converter = 0; converter < CONVERTERS; converter++)
            {
                final int z = yardZ + converter * CONVERTER_PITCH;
                final int x = baseX + 20;
                queue("converters", () -> {
                    force(x - 1, z, x + 1, z);
                    part(x, floorY + 1, z, Metallurgy.CONVERTER.get().defaultBlockState());
                    part(x - 1, floorY + 1, z, Pipes.PRESSURE_VESSEL.get().defaultBlockState());
                    converters.add(new BlockPos(x, floorY + 1, z));
                    converterReceivers.add(new BlockPos(x - 1, floorY + 1, z));
                });
            }
            for (int retort = 0; retort < RETORTS; retort++)
            {
                final int z = yardZ + retort * RETORT_PITCH;
                final int x = baseX + 40;
                final boolean condenser = retort % 2 == 0;
                queue("retorts", () -> {
                    force(x, z, x + 1, z);
                    part(x, floorY + 1, z, Metallurgy.RETORT.get().defaultBlockState());
                    if (condenser)
                    {
                        part(x + 1, floorY + 1, z, Metallurgy.RETORT_CONDENSER.get().defaultBlockState());
                    }
                    retorts.add(new BlockPos(x, floorY + 1, z));
                });
            }
            queue("yard", () -> {
                // The chunks that gas from the yard lands in, so that they are watched for budget starvation as the rooms are
                for (int chunkX = baseX >> 4; chunkX <= (baseX + 47) >> 4; chunkX++)
                {
                    for (int chunkZ = yardZ >> 4; chunkZ <= (yardZ + PUMPS * PUMP_PITCH) >> 4; chunkZ++)
                    {
                        watched.add(ChunkPos.asLong(chunkX, chunkZ));
                    }
                }
            });
        }

        private static BlockState pipe()
        {
            return Pipes.PIPES.get(PipeMaterial.WROUGHT_IRON).get().defaultBlockState();
        }

        private static BlockState fitting(Block block, Direction facing, boolean open)
        {
            return block.defaultBlockState().setValue(PipeFittingBlock.FACING, facing).setValue(PipeFittingBlock.OPEN, open);
        }

        // ---- Arming: the things that start working only once everything stands ----

        private void arm()
        {
            final long begun = System.nanoTime();
            log("terrain surface (floor of the base is " + floorY + "): rooms " + level.getHeight(Heightmap.Types.WORLD_SURFACE, roomX(0) + 5, roomZ(0) + 5) + ", pipe field " + level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + 30, fieldZ + 10)
                + ", main " + level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + 30, mainZ) + " and " + level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + 1500, mainZ) + ", yard " + level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + 5, yardZ + 20));
            fillMethaneRoom();
            lightFires();
            fillReceivers();
            startPumps();
            fillConverters();
            loadRetorts();
            seatPlayers();
            armedAt = tick;
            log("armed at tick " + tick + " in " + millis(System.nanoTime() - begun) + " ms; fires tracked " + GasTracker.get(level).fireCount() + ", pipe networks " + PipeTracker.get(level).networkCount());
        }

        /** An even mix of firedamp throughout one room, a little different in each cell as no real mix is perfect. */
        private void fillMethaneRoom()
        {
            final int x0 = roomX(METHANE_ROOM), z0 = roomZ(METHANE_ROOM);
            for (int x = x0; x < x0 + ROOM_SIZE; x++)
            {
                for (int y = floorY + 1; y <= floorY + ROOM_HEIGHT; y++)
                {
                    for (int z = z0; z < z0 + ROOM_SIZE; z++)
                    {
                        addGas(x, y, z, Gas.METHANE, METHANE_FRACTION * GasMath.CELL_MOLES * (0.8f + 0.4f * random.nextFloat()));
                    }
                }
            }
        }

        private void lightFires()
        {
            int lit = 0;
            for (BlockPos pos : fires)
            {
                if (level.getBlockEntity(pos) instanceof FirepitBlockEntity firepit)
                {
                    for (int slot = 0; slot < FUEL_SLOTS; slot++)
                    {
                        firepit.getInventory().setStackInSlot(slot, new ItemStack(Items.CHARCOAL));
                    }
                    if (firepit.light(level.getBlockState(pos)))
                    {
                        lit++;
                    }
                }
            }
            log("fires lit " + lit + " of " + fires.size());
        }

        private void fillReceivers()
        {
            for (BlockPos receiver : receivers)
            {
                pressurise(receiver, RECEIVER_ATMOSPHERES);
            }
            pressurise(mainWest, MAIN_ATMOSPHERES);
        }

        private void pressurise(BlockPos vessel, float atmospheres)
        {
            final @Nullable PipeNetwork network = PipeTracker.get(level).networkAt(vessel);
            if (network != null)
            {
                network.contents().air = PipePhysics.moles(atmospheres, 293f, network.volume());
            }
        }

        private void startPumps()
        {
            for (BlockPos motor : motors)
            {
                if (level.getBlockEntity(motor) instanceof CreativeRotationBlockEntity creative)
                {
                    for (int step = 0; step < CreativeRotationBlockEntity.MAX_STEPS; step++)
                    {
                        creative.incrementSpeed();
                    }
                }
            }
        }

        private void fillConverters()
        {
            final Fluid pigIron = TFCFluids.METALS.get(Metal.PIG_IRON).getSource();
            for (int i = 0; i < converters.size(); i++)
            {
                if (level.getBlockEntity(converters.get(i)) instanceof ConverterBlockEntity converter)
                {
                    converter.tank().fill(new FluidStack(pigIron, 1000), IFluidHandler.FluidAction.EXECUTE);
                    converter.setTemperature(0f);
                    converter.bath().temperature = 1400f; // As it arrives from a blast furnace
                    pressurise(converterReceivers.get(i), RECEIVER_ATMOSPHERES);
                }
            }
        }

        /** A retort heats at about 1 C per tick and the zinc one needs 1000, so they start hot, as they would be in a base that has run for a while. */
        private void loadRetorts()
        {
            for (BlockPos pos : retorts)
            {
                if (level.getBlockEntity(pos) instanceof RetortBlockEntity retort)
                {
                    retort.load(new ItemStack(Metallurgy.ZINC_CALCINES.get("normal").get(), 64), 10_000f);
                    final HolderLookup.Provider registries = level.registryAccess();
                    final CompoundTag saved = retort.saveWithoutMetadata(registries);
                    saved.putFloat("temperature", RETORT_CELSIUS);
                    saved.putFloat("targetTemperature", RETORT_CELSIUS);
                    retort.loadWithComponents(saved, registries);
                }
            }
        }

        private void seatPlayers()
        {
            for (int number = 0; number < PLAYERS; number++)
            {
                final int room = number % BASE_ROOMS, along = number / BASE_ROOMS;
                final LoadPlayer player = new LoadPlayer(level, number);
                player.moveTo(roomX(room) + 2 + along * 3 + 0.5, floorY + 1, roomZ(room) + 13 + 0.5);
                players.add(player);
            }
        }

        // ---- Running ----

        private void onTick()
        {
            if (failed)
            {
                return;
            }
            try
            {
                final long now = System.nanoTime();
                if (lastCallbackAt != 0L && measuring >= 0)
                {
                    wall.record(0, now - lastCallbackAt);
                    wall.endTick();
                }
                lastCallbackAt = now;

                step();
                tick++;
            }
            catch (Throwable error)
            {
                failed = true;
                LOGGER.error(PREFIX + "failed at tick " + tick, error);
                helper.fail("The load test threw " + error);
            }
        }

        private void step()
        {
            if (!steps.isEmpty())
            {
                build();
                return;
            }
            if (armedAt < 0)
            {
                if (formingTicks++ >= FORMING_TICKS)
                {
                    log("built in " + tick + " ticks, " + millis(System.nanoTime() - startedAt) + " ms; " + groupTimes());
                    arm();
                }
                return;
            }

            final int sinceArmed = tick - armedAt;
            drive(sinceArmed);
            if (blastStage > 0)
            {
                blastTick(); // The base carries on as before, so that the blast lands in an ordinary busy tick
                return;
            }
            if (sinceArmed < SETTLE_TICKS)
            {
                return;
            }
            if (measuring < 0)
            {
                beginMeasuring();
            }
            measure();
        }

        private void build()
        {
            final long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
            while (!steps.isEmpty() && System.nanoTime() < deadline)
            {
                final Step step = steps.poll();
                final long begun = System.nanoTime();
                step.work.run();
                buildNanos.merge(step.group, System.nanoTime() - begun, Long::sum);
            }
        }

        private String groupTimes()
        {
            final StringBuilder text = new StringBuilder("build ms: ");
            buildNanos.forEach((group, nanos) -> text.append(group).append(' ').append(millis(nanos)).append(", "));
            return text.toString();
        }

        /** What the people and machines of the base do, tick by tick. */
        private void drive(int sinceArmed)
        {
            watchBudget();

            if (sinceArmed % GAS_PULSE_TICKS == 0)
            {
                pulseGas();
            }
            if (sinceArmed % REFILL_TICKS == 0)
            {
                refillConverters();
                loadRetorts();
            }
            if (sinceArmed % PRESSURISE_TICKS == 0)
            {
                fillReceivers();
            }
            for (BlockPos pos : retorts)
            {
                if (level.getBlockEntity(pos) instanceof RetortBlockEntity retort)
                {
                    retort.setTemperature(RETORT_CELSIUS);
                }
            }
            breathe(sinceArmed);
        }

        /** The same calls the other game tests use to put gas in a room. */
        private void addGas(int x, int y, int z, Gas gas, float moles)
        {
            final LevelGasVolume volume = GasTracker.get(level).volume(level);
            final @Nullable float[] existing = volume.cell(x, y, z);
            final @Nullable float[] cell = existing != null ? existing : volume.createCell(x, y, z);
            if (cell == null)
            {
                droppedGas++;
                return;
            }
            cell[gas.ordinal()] += moles;
        }

        /** A little CO2 and CO into a few cells of each room, so that gas keeps moving and no room settles into rest. */
        private void pulseGas()
        {
            for (int room = 0; room < rooms; room++)
            {
                if (room == METHANE_ROOM)
                {
                    continue;
                }
                for (int cell = 0; cell < GAS_CELLS_PER_PULSE; cell++)
                {
                    final int x = roomX(room) + random.nextInt(ROOM_SIZE), z = roomZ(room) + random.nextInt(ROOM_SIZE), y = floorY + 1 + random.nextInt(ROOM_HEIGHT);
                    addGas(x, y, z, Gas.CARBON_DIOXIDE, CARBON_DIOXIDE_PER_PULSE);
                    addGas(x, y, z, Gas.CARBON_MONOXIDE, CARBON_MONOXIDE_PER_PULSE);
                }
            }
        }

        /** A converter that has blown down is charged again, as a steelworks would between heats. */
        private void refillConverters()
        {
            final Fluid pigIron = TFCFluids.METALS.get(Metal.PIG_IRON).getSource();
            for (int i = 0; i < converters.size(); i++)
            {
                if (level.getBlockEntity(converters.get(i)) instanceof ConverterBlockEntity converter && converter.bath().metal < 500f)
                {
                    converter.tank().fill(new FluidStack(pigIron, 1000), IFluidHandler.FluidAction.EXECUTE);
                    converter.setTemperature(0f);
                    converter.bath().temperature = 1400f;
                }
            }
        }

        /** Each person breathes once a second, but not all in the same tick, as players join at different moments. */
        private void breathe(int sinceArmed)
        {
            for (int number = 0; number < players.size(); number++)
            {
                if ((sinceArmed + number) % Breathing.INTERVAL_TICKS == 0)
                {
                    final long begun = System.nanoTime();
                    Breathing.tick(players.get(number));
                    TickProfiler.PROFILE.record(TickProfiler.BREATHING, System.nanoTime() - begun);
                }
            }
        }

        /**
         * The tracker does not say when it ran out of budget, so this looks from outside: after a tick, a chunk that is due and was not stepped is one the budget did not reach. Taken before anything is added in this callback, as new gas makes a chunk due.
         */
        private void watchBudget()
        {
            final long now = level.getGameTime();
            int overdue = 0, steppedCells = 0, steppedChunks = 0;
            for (long key : watched)
            {
                final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk != null && chunk.hasData(GasAttachments.CHUNK))
                {
                    final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
                    final long lag = now - data.lastStepTick - data.stepIntervalTicks;
                    if (data.isActive() && lag > 0L)
                    {
                        overdue++;
                        worstLag = Math.max(worstLag, lag);
                    }
                    if (data.lastStepTick == now) // Stepped in the tick that has just ended, and the tracker stamps a chunk with the tick it stepped it in
                    {
                        steppedCells += data.size();
                        steppedChunks++;
                    }
                }
            }
            if (measuring >= 0)
            {
                starvedTicks += overdue > 0 ? 1 : 0;
                overdueChunkTicks += overdue;
                cellsPerTick.add(steppedCells);
                chunksPerTick.add(steppedChunks);
            }
        }

        private void beginMeasuring()
        {
            measuring = 0;
            measureStartedAt = System.nanoTime();
            starvedTicks = 0;
            worstLag = 0L;
            overdueChunkTicks = 0L;
            cellsPerTick.clear();
            chunksPerTick.clear();
            TickProfiler.PROFILE.clear();
            TickProfiler.PROFILE.resetPeaks();
            wall.clear();
            lastCallbackAt = 0L;

            int turning = 0;
            for (BlockPos pos : pumps)
            {
                if (level.getBlockEntity(pos) instanceof PistonPumpBlockEntity pump)
                {
                    final @Nullable Rotation rotation = pump.getRotationNode().rotation();
                    if (rotation != null && rotation.speed() != 0f)
                    {
                        turning++;
                    }
                }
            }
            log("measuring begins at tick " + tick + ", " + SETTLE_TICKS + " ticks after arming; pumps turning " + turning + " of " + pumps.size());
            logStats();
        }

        private void measure()
        {
            if (measuring == VALVE_OPENS_AT)
            {
                networksBefore = PipeTracker.get(level).networkCount();
                TickProfiler.PROFILE.resetPeaks();
                final long begun = System.nanoTime();
                setValve(true);
                openCallMillis = (System.nanoTime() - begun) / 1e6;
            }
            else if (measuring == VALVE_OPENS_AT + VALVE_HOLD_TICKS)
            {
                openSpikePipe = TickProfiler.PROFILE.peakMillis(TickProfiler.PIPE);
                openSpikeTotal = TickProfiler.PROFILE.peakMillis(TickProfile.TOTAL);
                valveOpenedNetworks = PipeTracker.get(level).networkCount();
                TickProfiler.PROFILE.resetPeaks();
                final long begun = System.nanoTime();
                setValve(false);
                closeCallMillis = (System.nanoTime() - begun) / 1e6;
            }
            else if (measuring == VALVE_OPENS_AT + 2 * VALVE_HOLD_TICKS)
            {
                closeSpikePipe = TickProfiler.PROFILE.peakMillis(TickProfiler.PIPE);
                closeSpikeTotal = TickProfiler.PROFILE.peakMillis(TickProfile.TOTAL);
                valveClosedNetworks = PipeTracker.get(level).networkCount();
                log("rebuild spike, valve opened (two runs join; networks " + networksBefore + " -> " + valveOpenedNetworks + "): pipe " + format(openSpikePipe) + " ms, whole tick " + format(openSpikeTotal) + " ms, the toggle itself " + format(openCallMillis) + " ms");
                log("rebuild spike, valve closed (the run splits again; networks -> " + valveClosedNetworks + "): pipe " + format(closeSpikePipe) + " ms, whole tick " + format(closeSpikeTotal) + " ms, the toggle itself " + format(closeCallMillis) + " ms");
            }

            measuring++;
            if (measuring % STATS_TICKS == 0 && measuring < MEASURE_TICKS)
            {
                logStats();
            }
            if (measuring == REPORT_TICKS)
            {
                report("after " + measuring + " measured ticks");
            }
            if (measuring >= MEASURE_TICKS)
            {
                finish();
            }
        }

        // ---- The worst single tick ----

        /** An explosion in a sealed room, once the window has closed so that it cannot disturb any figure in it. */
        private void beginBlast()
        {
            final int x0 = blastX(), z0 = blastZ();
            final AABB area = blastArea();
            final int entities = level.getEntitiesOfClass(Entity.class, area).size();
            for (int x = x0; x < x0 + ROOM_SIZE; x++)
            {
                for (int y = floorY + 1; y <= floorY + ROOM_HEIGHT; y++)
                {
                    for (int z = z0; z < z0 + ROOM_SIZE; z++)
                    {
                        addGas(x, y, z, Gas.METHANE, STOICHIOMETRIC_METHANE * GasMath.CELL_MOLES);
                    }
                }
            }
            blastMethaneBefore = blastMoles(Gas.METHANE);
            blastCellsBefore = blastCellsWith(Gas.METHANE, MOLES_THAT_COUNT);
            // The same flame the other game tests use: gas is lit when its chunk is next stepped, and the blast runs at the end of that tick of the tracker
            part(x0 + 8, floorY + 1, z0 + 8, Blocks.TORCH.defaultBlockState());
            TickProfiler.PROFILE.resetPeaks();
            blastStage = 1;
            log(String.format(Locale.ROOT, "---- blast: one room filled with %.1f%% methane (%.1f mol in %d cells) and a torch set in it; %d entities within reach of it ----", 100 * STOICHIOMETRIC_METHANE, blastMethaneBefore, blastCellsBefore, entities));
        }

        private AABB blastArea()
        {
            return new AABB(blastX() - 12, floorY - 4, blastZ() - 12, blastX() + ROOM_SIZE + 12, floorY + ROOM_HEIGHT + 12, blastZ() + ROOM_SIZE + 12);
        }

        private float blastMoles(Gas gas)
        {
            float moles = 0f;
            final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(blastX() >> 4, blastZ() >> 4);
            if (chunk != null && chunk.hasData(GasAttachments.CHUNK))
            {
                final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
                for (int key : data.keys())
                {
                    moles += data.get(key)[gas.ordinal()];
                }
            }
            return moles;
        }

        private int blastCellsWith(Gas gas, float atLeast)
        {
            int cells = 0;
            final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(blastX() >> 4, blastZ() >> 4);
            if (chunk != null && chunk.hasData(GasAttachments.CHUNK))
            {
                final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
                for (int key : data.keys())
                {
                    cells += data.get(key)[gas.ordinal()] >= atLeast ? 1 : 0;
                }
            }
            return cells;
        }

        /**
         * Reads the tick that has just closed. A drop in the methane between two callbacks means the blast ran in that tick, so the figures of the tick are the blast's.
         */
        private void blastTick()
        {
            if (blastStage == 3)
            {
                return;
            }
            blastTicks.add(new double[] {TickProfiler.PROFILE.lastMillis(TickProfiler.GAS), TickProfiler.PROFILE.lastMillis(TickProfile.TOTAL), wall.lastMillis(0)});
            final float methane = blastMoles(Gas.METHANE);
            if (blastStage == 1)
            {
                if (methane < blastMethaneBefore * 0.5f)
                {
                    blastStage = 2;
                    blastHit = blastTicks.size() - 1;
                }
                else if (blastTicks.size() > BLAST_WAIT_TICKS)
                {
                    log("the methane did not light within " + BLAST_WAIT_TICKS + " ticks");
                    endBlast();
                }
            }
            else if (blastTicks.size() > blastHit + BLAST_AFTER_TICKS)
            {
                endBlast();
            }
        }

        private void endBlast()
        {
            if (blastHit >= 0)
            {
                final double[] before = blastTicks.get(Math.max(blastHit - 1, 0)), at = blastTicks.get(blastHit), after = blastTicks.get(Math.min(blastHit + 1, blastTicks.size() - 1));
                log(String.format(Locale.ROOT, "blast tick (%d ticks after the torch was set): gas section %.3f ms, all sections %.3f ms, whole tick %.3f ms; the tick before: gas %.3f ms, whole %.3f ms; the tick after: gas %.3f ms, whole %.3f ms", blastHit + 1, at[0], at[1], at[2], before[0], before[2], after[0], after[2]));
                log(String.format(Locale.ROOT, "slowest gas section from the torch to the end: %.3f ms (an ordinary tick of this base had a mean of %.3f ms and a p99 of %.3f ms in the last window)", TickProfiler.PROFILE.peakMillis(TickProfiler.GAS), TickProfiler.PROFILE.meanMillis(TickProfiler.GAS), TickProfiler.PROFILE.percentileMillis(TickProfiler.GAS, 0.99)));
            }

            final int cellsLeft = blastCellsWith(Gas.METHANE, MOLES_THAT_COUNT);
            int broken = 0;
            final int x0 = blastX(), z0 = blastZ();
            for (int x = x0 - 1; x <= x0 + ROOM_SIZE; x++)
            {
                for (int y = floorY; y <= floorY + ROOM_HEIGHT + 1; y++)
                {
                    for (int z = z0 - 1; z <= z0 + ROOM_SIZE; z++)
                    {
                        final boolean inside = x >= x0 && x < x0 + ROOM_SIZE && z >= z0 && z < z0 + ROOM_SIZE && y > floorY && y <= floorY + ROOM_HEIGHT;
                        if (!inside && !level.getBlockState(new BlockPos(x, y, z)).is(Blocks.BRICKS))
                        {
                            broken++;
                        }
                    }
                }
            }
            log(String.format(Locale.ROOT, "blast result: methane %.1f -> %.1f mol, CO2 made %.1f mol; cells that held methane %d -> %d, so about %d cells burned; %d blocks of the shell broke (the blast may break at most %d)",
                blastMethaneBefore, blastMoles(Gas.METHANE), blastMoles(Gas.CARBON_DIOXIDE), blastCellsBefore, cellsLeft, blastCellsBefore - cellsLeft, broken, ServerConfig.GAS_BLAST_BLOCKS.getAsInt()));

            // What the blast threw out is items on the ground, which are removed. No living thing was within reach of it.
            int items = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, blastArea()))
            {
                item.discard();
                items++;
            }
            log("dropped items removed: " + items);

            log("measuring took " + millis(System.nanoTime() - measureStartedAt) + " ms; the whole test took " + millis(System.nanoTime() - startedAt) + " ms (" + tick + " ticks)");
            tidyUp();
            helper.succeed();
            blastStage = 3;
        }

        private void setValve(boolean open)
        {
            level.setBlockAndUpdate(valve, fitting(Pipes.VALVE.get(), Direction.EAST, open));
            PipeTracker.get(level).onConnectionChanged(level, valve);
        }

        private void report(String when)
        {
            log("---- profile " + when + " ----");
            TickProfiler.perf().forEach(line -> log(line));
            log(String.format(Locale.ROOT, "%-9s mean %8.4f ms  p99 %8.4f ms  max %8.4f ms  (the whole interval between ticks, from the test's own callback)", "wall", wall.meanMillis(0), wall.percentileMillis(0, 0.99), wall.maxMillis(0)));
        }

        private void logStats()
        {
            final GasTracker tracker = GasTracker.get(level);
            int cells = 0, activeRoomChunks = 0, yardCells = 0, activeYardChunks = 0;
            float dueCellsPerTick = 0f;
            for (int index = 0; index < watched.size(); index++)
            {
                final long key = watched.getLong(index);
                final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk != null && chunk.hasData(GasAttachments.CHUNK))
                {
                    final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
                    if (data.isActive())
                    {
                        dueCellsPerTick += data.size() / (float) data.stepIntervalTicks;
                    }
                    if (index < rooms) // The rooms are the first to be watched
                    {
                        cells += data.size();
                        activeRoomChunks += data.isActive() ? 1 : 0;
                    }
                    else
                    {
                        yardCells += data.size();
                        activeYardChunks += data.isActive() ? 1 : 0;
                    }
                }
            }
            int lit = 0;
            for (BlockPos pos : fires)
            {
                final BlockState state = level.getBlockState(pos);
                if (state.hasProperty(FirepitBlock.LIT) && state.getValue(FirepitBlock.LIT))
                {
                    lit++;
                }
            }
            log(String.format(Locale.ROOT, "stats at measured tick %d: gas cells %d in %d active room chunks and %d in %d active yard chunks, tracker has %d active chunks, cells due per tick about %.0f of %d, budget-starved ticks %d (worst lag %d ticks), fires tracked %d and lit %d of %d, pipe networks %d, gas dropped for want of a cell %d",
                Math.max(measuring, 0), cells, activeRoomChunks, yardCells, activeYardChunks, tracker.activeChunkCount(), dueCellsPerTick, ServerConfig.GAS_CELLS_PER_TICK.getAsInt(), starvedTicks, worstLag, tracker.fireCount(), lit, fires.size(), PipeTracker.get(level).networkCount(), droppedGas));
        }

        /** What the machines did, so that a number can be trusted to come from machines that worked: a base that sat idle would be cheap for the wrong reason. */
        private void logWork()
        {
            final float[] moles = new float[Gas.COUNT];
            for (long key : watched)
            {
                final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk != null && chunk.hasData(GasAttachments.CHUNK))
                {
                    final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
                    for (int cellKey : data.keys())
                    {
                        final float[] cell = data.get(cellKey);
                        for (int i = 0; i < Gas.COUNT; i++)
                        {
                            moles[i] += cell[i];
                        }
                    }
                }
            }
            final StringBuilder gases = new StringBuilder();
            for (Gas gas : Gas.VALUES)
            {
                if (moles[gas.ordinal()] > 0.01f)
                {
                    gases.append(String.format(Locale.ROOT, "%s %.1f, ", gas.id(), moles[gas.ordinal()]));
                }
            }
            log("gas in the watched chunks, moles: " + gases);

            int blown = 0;
            for (BlockPos pos : converters)
            {
                if (level.getBlockEntity(pos) instanceof ConverterBlockEntity converter && converter.bath().carbon < ConverterBath.Grade.PIG_IRON.carbon())
                {
                    blown++;
                }
            }
            int zinc = 0, retortsLoaded = 0;
            for (BlockPos pos : retorts)
            {
                if (level.getBlockEntity(pos) instanceof RetortBlockEntity retort)
                {
                    zinc += retort.tank().getFluidAmount();
                    retortsLoaded += retort.getTemperature() >= RETORT_CELSIUS ? 1 : 0;
                }
            }
            log("converters whose iron has lost carbon to the blast " + blown + " of " + converters.size() + "; retorts at working heat " + retortsLoaded + " of " + retorts.size() + ", zinc condensed " + zinc + " mB");
        }

        private void finish()
        {
            report("final, over " + measuring + " measured ticks");
            logStats();
            logWork();
            log(String.format(Locale.ROOT, "gas budget %s: %d of %d measured ticks had a gas chunk that was due and was not stepped, worst lag %d ticks", starvedTicks > 0 ? "WAS REACHED" : "was not reached", starvedTicks, measuring, worstLag));
            logBudget();
            log("measuring took " + millis(System.nanoTime() - measureStartedAt) + " ms (" + MEASURE_TICKS + " ticks)");
            beginBlast();
        }

        /**
         * What the gas grid really did against its budget, taken from the stamp each stepped chunk carries. A chunk is stepped whole, so a tick can pass the budget by up to the size of the last chunk it started.
         */
        private void logBudget()
        {
            final int budget = ServerConfig.GAS_CELLS_PER_TICK.getAsInt(), chunkLimit = ServerConfig.GAS_CHUNKS_PER_TICK.getAsInt();
            final int[] cells = cellsPerTick.toIntArray();
            final int[] chunks = chunksPerTick.toIntArray();
            final int[] sorted = cells.clone();
            Arrays.sort(sorted);
            long sum = 0L, chunkSum = 0L;
            int over = 0, reached = 0, most = 0, mostChunks = 0, biggestChunk = 0;
            for (int i = 0; i < cells.length; i++)
            {
                sum += cells[i];
                chunkSum += chunks[i];
                over += cells[i] > budget ? 1 : 0;
                reached += cells[i] >= budget ? 1 : 0;
                most = Math.max(most, cells[i]);
                mostChunks = Math.max(mostChunks, chunks[i]);
            }
            int totalCells = 0, active = 0;
            for (int index = 0; index < Math.min(rooms, watched.size()); index++)
            {
                final long key = watched.getLong(index);
                final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk != null && chunk.hasData(GasAttachments.CHUNK))
                {
                    totalCells += chunk.getData(GasAttachments.CHUNK).size();
                    biggestChunk = Math.max(biggestChunk, chunk.getData(GasAttachments.CHUNK).size());
                    active += chunk.getData(GasAttachments.CHUNK).isActive() ? 1 : 0;
                }
            }
            final double meanCells = cells.length == 0 ? 0.0 : sum / (double) cells.length;
            final int p99 = cells.length == 0 ? 0 : sorted[Math.clamp((int) Math.ceil(0.99 * cells.length - 1e-9) - 1, 0, cells.length - 1)];
            log(String.format(Locale.ROOT, "gas stepped per tick, seen from outside: cells mean %.0f p99 %d max %d against a budget of %d; ticks that stepped at least the budget %d and past it %d of %d; worst overshoot %d cells (biggest room chunk holds %d); chunks per tick mean %.1f max %d against a limit of %d",
                meanCells, p99, most, budget, reached, over, cells.length, Math.max(most - budget, 0), biggestChunk, cells.length == 0 ? 0.0 : chunkSum / (double) cells.length, mostChunks, chunkLimit));
            log(String.format(Locale.ROOT, "gas lateness: overdue chunks per tick on average %.2f of %d room chunks (%d active), worst chunk %d ticks late; %d gas cells in the rooms are swept in about %.1f s at %.0f cells a second (a step simulates at most 5 s however late it is)",
                (double) overdueChunkTicks / Math.max(cells.length, 1), rooms, active, worstLag, totalCells, meanCells > 0 ? totalCells / (meanCells * 20.0) : 0.0, meanCells * 20.0));
        }

        /**
         * The game test world is saved when the server stops, and every run builds at a different place in it, so whatever stands here stays in the world for good. Everything but the stone shells is taken down, with nothing under pressure, hot or loaded when it goes, as it would otherwise burst, burn and drop items into the saved chunks.
         */
        private void tidyUp()
        {
            final long begun = System.nanoTime();
            for (BlockPos receiver : receivers)
            {
                pressurise(receiver, 1f);
            }
            for (BlockPos receiver : converterReceivers)
            {
                pressurise(receiver, 1f);
            }
            pressurise(mainWest, 1f);
            pressurise(mainEast, 1f);
            for (BlockPos pos : fires)
            {
                if (level.getBlockEntity(pos) instanceof FirepitBlockEntity firepit)
                {
                    for (int slot = 0; slot < firepit.getInventory().getSlots(); slot++)
                    {
                        firepit.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                    }
                }
            }
            for (BlockPos pos : converters)
            {
                if (level.getBlockEntity(pos) instanceof ConverterBlockEntity converter)
                {
                    converter.bath().metal = 0f;
                    converter.bath().slag = 0f;
                }
            }
            for (BlockPos pos : retorts)
            {
                if (level.getBlockEntity(pos) instanceof RetortBlockEntity retort)
                {
                    retort.load(ItemStack.EMPTY, 0f);
                }
            }

            final BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos pos : parts)
            {
                set(pos.getX(), pos.getY(), pos.getZ(), air);
            }
            for (long key : forced)
            {
                final @Nullable LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk != null && chunk.hasData(GasAttachments.CHUNK))
                {
                    chunk.removeData(GasAttachments.CHUNK);
                }
                GasTracker.get(level).deactivate(key);
                level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
            }
            log("tidied up " + parts.size() + " blocks and " + forced.size() + " forced chunks in " + millis(System.nanoTime() - begun) + " ms");
        }

        private static long millis(long nanos)
        {
            return nanos / 1_000_000L;
        }

        private static String format(double millis)
        {
            return String.format(Locale.ROOT, "%.3f", millis);
        }
    }

    private LoadTest() {}
}
