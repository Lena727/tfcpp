package tfcpp.core;

import java.lang.reflect.Field;
import net.dries007.tfc.common.blockentities.rotation.WaterWheelBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rotation.WaterWheelBlock;
import net.dries007.tfc.common.blocks.wood.Wood;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.mechanics.ShaftSource;
import tfcpp.metallurgy.ConverterBlockEntity;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.metallurgy.RetortBlockEntity;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasEmission;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.instruments.GoldLeafIndicatorBlockEntity;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.pneumatics.instruments.SettlingBaffleBlockEntity;
import tfcpp.pneumatics.pipes.PipeFittingBlock;
import tfcpp.pneumatics.pipes.PipeNetwork;
import tfcpp.pneumatics.pipes.PipeTracker;
import tfcpp.pneumatics.pipes.Pipes;

/**
 * What runs at one place counts the ticks of TFC's calendar, so with the calendar held still on an empty server nothing advances, however long the chunks stay loaded. Every device and tracker that simulates a process has a place in this test.
 * <p>
 * It has a batch of its own because it stops the calendar for everyone, and the control at the end starts it again and checks that every one of them does move, so that a fixture which could never have moved cannot pass.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CalendarGameTests
{
    private static final String TEMPLATE = "empty9";
    private static final String STILL_BATCH = "tfcpp_calendar_still";
    private static final int TIMEOUT = 300;

    private static final BlockPos GAS = new BlockPos(1, 1, 1), VESSEL = new BlockPos(2, 1, 1), VENT = new BlockPos(2, 1, 2);
    private static final BlockPos LEAF = new BlockPos(1, 1, 3), BAFFLE = new BlockPos(3, 1, 3), DUST = new BlockPos(3, 2, 3);
    private static final BlockPos RETORT = new BlockPos(6, 1, 2), CONVERTER = new BlockPos(6, 1, 5), WHEEL = new BlockPos(7, 3, 7);

    private static void box(GameTestHelper helper)
    {
        for (int x = 0; x <= 4; x++)
        {
            for (int y = 0; y <= 3; y++)
            {
                for (int z = 0; z <= 4; z++)
                {
                    final boolean inside = x > 0 && x < 4 && y > 0 && y < 3 && z > 0 && z < 4;
                    helper.setBlock(x, y, z, inside ? Blocks.AIR : Blocks.OBSIDIAN);
                }
            }
        }
    }

    private static LevelGasVolume volume(GameTestHelper helper)
    {
        return GasTracker.get(helper.getLevel()).volume(helper.getLevel());
    }

    private static float[] cellAt(GameTestHelper helper, BlockPos pos)
    {
        final BlockPos absolute = helper.absolutePos(pos);
        final @Nullable float[] existing = volume(helper).cell(absolute.getX(), absolute.getY(), absolute.getZ());
        final @Nullable float[] cell = existing != null ? existing : volume(helper).createCell(absolute.getX(), absolute.getY(), absolute.getZ());
        helper.assertTrue(cell != null, "Could not make a gas cell");
        return cell;
    }

    private static BlockState fitting(net.minecraft.world.level.block.Block block, Direction facing, boolean open)
    {
        return block.defaultBlockState().setValue(PipeFittingBlock.FACING, facing).setValue(PipeFittingBlock.OPEN, open);
    }

    private static void setField(Object target, String name, float value) throws ReflectiveOperationException
    {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(target, value);
    }

    /** What the devices showed when the calendar was stopped. */
    private static final class Snapshot
    {
        long calendar;
        float gas, vessel, factor, retort, converter, leaf, baffle;
        long gasStepped;
    }

    private static Snapshot take(GameTestHelper helper, PipeNetwork network, WaterWheelBlockEntity wheel, RetortBlockEntity retort, ConverterBlockEntity converter, GoldLeafIndicatorBlockEntity leaf, SettlingBaffleBlockEntity baffle)
    {
        final Snapshot snapshot = new Snapshot();
        snapshot.calendar = Calendars.SERVER.getTicks();
        snapshot.gas = cellAt(helper, GAS)[Gas.CARBON_DIOXIDE.ordinal()];
        snapshot.gasStepped = helper.getLevel().getChunkAt(helper.absolutePos(GAS)).getData(GasAttachments.CHUNK).lastStepTick;
        snapshot.vessel = network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()];
        snapshot.factor = ((ShaftSource) wheel).tfcpp$speedFactor();
        snapshot.retort = retort.tank().getFluidAmount();
        snapshot.converter = converter.bath().temperature;
        snapshot.leaf = leaf.dose();
        snapshot.baffle = baffle.collected(Gas.ARSENIC_TRIOXIDE);
        return snapshot;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT, batch = STILL_BATCH)
    public static void withTheCalendarHeldStillNothingAtAPlaceAdvances(GameTestHelper helper)
    {
        // Never leave the calendar stopped, whatever happens to the test
        helper.runAtTickTime(TIMEOUT - 2, () -> Calendars.SERVER.setPlayersLoggedOn(true));

        box(helper);
        helper.setBlock(VESSEL, Pipes.PRESSURE_VESSEL.get());
        helper.setBlock(VENT, fitting(Pipes.VENT.get(), Direction.SOUTH, true));
        helper.setBlock(LEAF, Instruments.GOLD_LEAF_INDICATOR.get());
        helper.setBlock(BAFFLE, Instruments.SETTLING_BAFFLE.get());
        helper.setBlock(RETORT, Metallurgy.RETORT.get());
        helper.setBlock(RETORT.east(), Metallurgy.RETORT_CONDENSER.get());
        helper.setBlock(CONVERTER, Metallurgy.CONVERTER.get());
        helper.setBlock(WHEEL, TFCBlocks.WOODS.get(Wood.OAK).get(Wood.BlockType.WATER_WHEEL).get().defaultBlockState().setValue(WaterWheelBlock.AXIS, Direction.Axis.X));

        final RetortBlockEntity retort = helper.getBlockEntity(RETORT);
        final ConverterBlockEntity converter = helper.getBlockEntity(CONVERTER);
        final GoldLeafIndicatorBlockEntity leaf = helper.getBlockEntity(LEAF);
        final SettlingBaffleBlockEntity baffle = helper.getBlockEntity(BAFFLE);
        final WaterWheelBlockEntity wheel = helper.getBlockEntity(WHEEL);
        helper.assertTrue(wheel instanceof ShaftSource, "The water wheel mixin should be applied");

        helper.runAtTickTime(5, () -> {
            final PipeNetwork network = pipes(helper, VESSEL);
            helper.assertTrue(network != null, "The vessel and vent should form a network");
            network.contents().gases[Gas.SULFUR_DIOXIDE.ordinal()] = 40f;

            cellAt(helper, GAS)[Gas.CARBON_DIOXIDE.ordinal()] = 10f;
            cellAt(helper, LEAF)[Gas.MERCURY_VAPOR.ordinal()] = 5e-5f;
            cellAt(helper, DUST)[Gas.ARSENIC_TRIOXIDE.ordinal()] = 1f;
            ((ShaftSource) wheel).tfcpp$setSpeedFactor(0.5f);

            retort.load(new ItemStack(Metallurgy.ZINC_CALCINES.get("normal").get()), 10f);
            converter.bath().metal = 500f;
            converter.bath().temperature = 1500f;
            try
            {
                setField(retort, "temperature", 1100f);
            }
            catch (ReflectiveOperationException e)
            {
                helper.fail("Could not heat the retort: " + e);
            }
            retort.setTemperature(1100f);
            Calendars.SERVER.setPlayersLoggedOn(false);
        });
        helper.onEachTick(() -> retort.setTemperature(1100f));

        final Snapshot[] before = new Snapshot[1];
        helper.runAtTickTime(8, () -> before[0] = take(helper, pipes(helper, VESSEL), wheel, retort, converter, leaf, baffle));
        helper.runAtTickTime(80, () -> {
            final Snapshot now = take(helper, pipes(helper, VESSEL), wheel, retort, converter, leaf, baffle);
            final Snapshot then = before[0];
            Calendars.SERVER.setPlayersLoggedOn(true); // Before anything can fail: the rest of this test runs on a calendar that moves
            helper.assertTrue(now.calendar == then.calendar, "The calendar should have stood still, or this proves nothing: " + then.calendar + " to " + now.calendar);
            helper.assertTrue(now.gas == then.gas && now.gasStepped == then.gasStepped, "Gas must not move: " + then.gas + " to " + now.gas + ", stepped at " + then.gasStepped + " and " + now.gasStepped);
            helper.assertTrue(now.vessel == then.vessel, "A pipe network must not step: " + then.vessel + " to " + now.vessel + " mol of SO2");
            helper.assertTrue(now.factor == then.factor, "The shafts must not be worked out: " + then.factor + " to " + now.factor);
            helper.assertTrue(now.retort == then.retort, "A retort must not react: " + then.retort + " to " + now.retort + " mB");
            helper.assertTrue(now.converter == then.converter, "A bath must not cool: " + then.converter + " to " + now.converter);
            helper.assertTrue(now.leaf == then.leaf, "A gold leaf must not breathe: " + then.leaf + " to " + now.leaf);
            helper.assertTrue(now.baffle == then.baffle, "A baffle must not catch dust: " + then.baffle + " to " + now.baffle);
        });

        // The control: the same fixtures on a calendar that moves
        helper.runAtTickTime(230, () -> {
            final Snapshot now = take(helper, pipes(helper, VESSEL), wheel, retort, converter, leaf, baffle);
            final Snapshot then = before[0];
            helper.assertTrue(now.calendar > then.calendar, "The calendar should run again");
            helper.assertTrue(now.gas != then.gas, "The gas should move once the calendar runs");
            helper.assertTrue(now.vessel < then.vessel, "The network should blow down once the calendar runs");
            helper.assertTrue(now.factor != then.factor, "The shafts should be worked out once the calendar runs");
            helper.assertTrue(now.retort > then.retort, "The retort should react once the calendar runs");
            helper.assertTrue(now.converter < then.converter, "The bath should cool once the calendar runs");
            helper.assertTrue(now.leaf > then.leaf, "The leaf should breathe once the calendar runs");
            helper.assertTrue(now.baffle > then.baffle, "The baffle should catch dust once the calendar runs");
            helper.succeed();
        });
    }

    @Nullable
    private static PipeNetwork pipes(GameTestHelper helper, BlockPos pos)
    {
        return PipeTracker.get(helper.getLevel()).networkAt(helper.absolutePos(pos));
    }

    /** A device left in a chunk that is not ready must not bring the chunk back up just by being asked where its smoke goes. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aDeviceInAChunkThatIsNotThereDoesNotLoadIt(GameTestHelper helper)
    {
        final BlockPos far = new BlockPos(100_000, 64, 100_000);
        final int chunkX = far.getX() >> 4, chunkZ = far.getZ() >> 4;
        helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(chunkX, chunkZ) == null, "The chunk should not be loaded to begin with");

        final int outlet = GasEmission.outletHeight(helper.getLevel(), volume(helper), far);

        helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(chunkX, chunkZ) == null, "Asking for the outlet must not have loaded " + new ChunkPos(far));
        helper.assertTrue(outlet == GasEmission.SMOTHERED, "A chunk that is not there lets nothing out");
        helper.succeed();
    }
}
