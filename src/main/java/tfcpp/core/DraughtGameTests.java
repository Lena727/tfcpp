package tfcpp.core;

import java.lang.reflect.Field;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.BellowsBlockEntity;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.climate.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.chemistry.Chemistry;
import tfcpp.metallurgy.Draught;
import tfcpp.metallurgy.DraughtFuel;
import tfcpp.metallurgy.DraughtState;
import tfcpp.metallurgy.Hearth;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/**
 * The temperature of a charcoal forge is set by the air it is given and by its fuel: see {@link Draught}. Every forge here is in a roofed room of bricks, so no wind reaches it, and the weather is pinned clear.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DraughtGameTests
{
    private static final String TEMPLATE = "empty9";
    /** Apart from the other tests: the room is filled with gas in one of them, and a batch shares the budget of the gas grid. */
    private static final String BATCH = "tfcpp_draught";
    /** These change the calendar for everything on the server, so they run in a batch of their own. */
    private static final String CALENDAR_BATCH = "tfcpp_draught_calendar";
    private static final float DEFAULT_RATE = 20f / 24f;
    private static final int SIZE = 7, HEIGHT = 4;
    private static final BlockPos FORGE = new BlockPos(4, 1, 4);

    @AfterBatch(batch = CALENDAR_BATCH)
    public static void afterCalendar(ServerLevel level)
    {
        Calendars.SERVER.setCalendarTickRate(DEFAULT_RATE);
    }

    private static Item copperIngot()
    {
        return TFCItems.METAL_ITEMS.get(Metal.COPPER).get(Metal.ItemType.INGOT).asItem();
    }

    /** A roofed room of the bricks a forge needs around it, with the forge at {@link #FORGE}, lit with one piece of this fuel. */
    private static CharcoalForgeBlockEntity litForge(GameTestHelper helper, Item fuel, float startTemperature)
    {
        final ServerLevel level = helper.getLevel();
        level.setWeatherParameters(1_000_000, 0, false, false);
        final Block bricks = TFCBlocks.ROCK_BLOCKS.get(Rock.GRANITE).get(Rock.BlockType.BRICKS).get();
        for (int x = 0; x <= SIZE + 1; x++)
        {
            for (int y = 0; y <= HEIGHT + 1; y++)
            {
                for (int z = 0; z <= SIZE + 1; z++)
                {
                    final boolean inside = x > 0 && x <= SIZE && y > 0 && y <= HEIGHT && z > 0 && z <= SIZE;
                    helper.setBlock(x, y, z, inside ? Blocks.AIR : bricks);
                }
            }
        }
        for (BlockPos side : new BlockPos[] {FORGE.north(), FORGE.south(), FORGE.east(), FORGE.west()})
        {
            helper.setBlock(side, bricks);
        }
        helper.setBlock(FORGE, TFCBlocks.CHARCOAL_FORGE.get());
        final CharcoalForgeBlockEntity forge = helper.getBlockEntity(FORGE);
        forge.getInventory().setStackInSlot(CharcoalForgeBlockEntity.SLOT_FUEL_MIN, new ItemStack(fuel));
        helper.assertTrue(forge.light(level.getBlockState(helper.absolutePos(FORGE))), "The forge takes the fuel and lights");
        setTemperature(helper, forge, startTemperature);
        return forge;
    }

    private static void setTemperature(GameTestHelper helper, CharcoalForgeBlockEntity forge, float temperature)
    {
        try
        {
            final Field field = CharcoalForgeBlockEntity.class.getDeclaredField("temperature");
            field.setAccessible(true);
            field.setFloat(forge, temperature);
        }
        catch (ReflectiveOperationException e)
        {
            helper.fail("Could not set the temperature of the forge: " + e);
        }
    }

    private static DraughtState draught(CharcoalForgeBlockEntity forge)
    {
        return ((Hearth) forge).tfcpp$draught();
    }

    /** @return the wind that the model would add, if there is any where the forge is: none, in a roofed room */
    private static float windAir(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(FORGE);
        return level.canSeeSky(pos.above()) ? Draught.windAir(Climate.get(level).getWind(level, pos).length()) : 0f;
    }

    private static void putIngot(CharcoalForgeBlockEntity forge, float temperature)
    {
        final ItemStack ingot = new ItemStack(copperIngot());
        HeatCapability.setTemperature(ingot, temperature);
        forge.getInventory().setStackInSlot(CharcoalForgeBlockEntity.SLOT_INPUT_MIN, ingot);
        forge.setAndUpdateSlots(CharcoalForgeBlockEntity.SLOT_INPUT_MIN);
    }

    /** People blowing into the forge at every tick, without a body: this goes straight to the hearth. */
    private static void blowEveryTick(GameTestHelper helper, CharcoalForgeBlockEntity forge, int people)
    {
        final UUID[] ids = new UUID[people];
        for (int i = 0; i < people; i++)
        {
            ids[i] = UUID.randomUUID();
        }
        helper.onEachTick(() -> {
            for (UUID id : ids)
            {
                draught(forge).blow(id, helper.getLevel().getGameTime());
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = BATCH)
    public static void anUnblownCharcoalForgeSettlesNearAThousandAndDoesNotMeltCopper(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Items.CHARCOAL, 990f);
        helper.onEachTick(() -> {});
        putIngot(forge, 1070f);
        helper.runAtTickTime(5, () -> draught(forge).invalidate());
        helper.runAtTickTime(250, () -> {
            helper.assertTrue(!helper.getLevel().canSeeSky(helper.absolutePos(FORGE).above()), "The roofed forge sees no sky, so no wind");
            final float expected = Draught.temperature(1000f, 1600f, 0f, draught(forge).oxygenFactor());
            helper.assertTrue(Math.abs(forge.getTemperature() - expected) < 3f && expected <= 1000.5f, "Charcoal on the natural draught of fresh air is at 1000 C, got " + forge.getTemperature() + ", expected " + expected);
            final ItemStack ingot = forge.getInventory().getStackInSlot(CharcoalForgeBlockEntity.SLOT_INPUT_MIN);
            helper.assertTrue(ingot.is(copperIngot()), "The copper has not melted in a fire of 1000 C, got " + ingot);
            helper.assertTrue(HeatCapability.getTemperature(ingot) < 1080f, "and is below its melting point, at " + HeatCapability.getTemperature(ingot));
            helper.assertTrue(draught(forge).air() == 0f, "with no air beyond the chimney's");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void aPersonBlowingThroughAPipeTakesTheForgeToAboutTwelveHundredAndMeltsCopper(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Items.CHARCOAL, 1000f);
        blowEveryTick(helper, forge, 1);
        putIngot(forge, 1070f);
        helper.runAtTickTime(5, () -> draught(forge).invalidate());
        helper.runAtTickTime(250, () -> {
            final float expected = Draught.temperature(1000f, 1600f, Math.max(Draught.pipeAir(1), windAir(helper)), draught(forge).oxygenFactor());
            helper.assertTrue(Math.abs(forge.getTemperature() - expected) < 3f, "A pipe takes charcoal to " + expected + " C, got " + forge.getTemperature());
            helper.assertTrue(forge.getTemperature() > 1190f && forge.getTemperature() < 1250f, "and that is about 1204 C, got " + forge.getTemperature());
            helper.assertTrue(draught(forge).blowers() == 1 && Math.abs(draught(forge).air() - 0.22f) < 1e-4f, "The air is a fifth of a bellows, got " + draught(forge).air());
            final ItemStack ingot = forge.getInventory().getStackInSlot(CharcoalForgeBlockEntity.SLOT_INPUT_MIN);
            helper.assertTrue(!ingot.is(copperIngot()), "The copper has melted, got " + ingot);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void twoPeopleBlowingGiveMoreThanOne(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Items.CHARCOAL, 1300f);
        blowEveryTick(helper, forge, 2);
        helper.runAtTickTime(5, () -> draught(forge).invalidate());
        helper.runAtTickTime(250, () -> {
            final float expected = Draught.temperature(1000f, 1600f, Draught.pipeAir(2), draught(forge).oxygenFactor());
            helper.assertTrue(draught(forge).blowers() == 2 && Math.abs(draught(forge).air() - 0.44f) < 1e-4f, "Two people give 0.44 of a bellows, got " + draught(forge).air() + " from " + draught(forge).blowers());
            helper.assertTrue(Math.abs(forge.getTemperature() - expected) < 3f && forge.getTemperature() > 1300f, "Two blowers take charcoal to " + expected + " C, above 1300 C of fire clay, got " + forge.getTemperature());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = BATCH)
    public static void blowingStopsTenTicksAfterThePersonStops(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Items.CHARCOAL, 1000f);
        final DraughtState draught = draught(forge);
        helper.onEachTick(() -> {});
        helper.runAtTickTime(5, () -> {
            draught.invalidate();
            draught.blow(UUID.randomUUID(), helper.getLevel().getGameTime());
        });
        helper.runAtTickTime(10, () -> helper.assertTrue(draught.air() > 0.2f, "Someone just blew, so there is air, got " + draught.air()));
        helper.runAtTickTime(25, () -> helper.assertTrue(draught.air() == 0f && !draught.isBlowing(), "Ten ticks after the last breath there is none, got " + draught.air()));
        helper.runAtTickTime(26, helper::succeed);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = BATCH)
    public static void fullBellowsTakeCharcoalToSixteenHundred(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Items.CHARCOAL, 1400f);
        helper.onEachTick(() -> forge.intakeAir(BellowsBlockEntity.MAX_DEVICE_AIR_TICKS));
        helper.runAtTickTime(5, () -> draught(forge).invalidate());
        helper.runAtTickTime(200, () -> {
            final float expected = Draught.temperature(1000f, 1600f, 1f, draught(forge).oxygenFactor());
            helper.assertTrue(Math.abs(forge.getTemperature() - expected) < 3f && expected > 1590f, "Charcoal on a full bellows is at 1600 C, got " + forge.getTemperature() + ", expected " + expected);
            helper.assertTrue(Math.abs(draught(forge).air() - 1f) < 1e-4f, "with all the air there is, got " + draught(forge).air());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = BATCH)
    public static void cokeOnTheNaturalDraughtBarelyKeepsAlight(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Chemistry.COKE.get(), 860f);
        helper.onEachTick(() -> {});
        helper.runAtTickTime(5, () -> draught(forge).invalidate());
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(draught(forge).knownFuel(1250f) != null, "The forge has noted that it took coke");
            final float expected = Draught.temperature(850f, 1750f, 0f, draught(forge).oxygenFactor());
            helper.assertTrue(Math.abs(forge.getTemperature() - expected) < 3f && expected <= 850.5f, "Coke without a blast is at 850 C, where its fuel temperature says 1250 C, got " + forge.getTemperature() + ", expected " + expected);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BATCH)
    public static void aForgeInAirThatIsShortOfOxygenRunsCooler(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Items.CHARCOAL, 810f);
        final ServerLevel level = helper.getLevel();
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        // Air in which exhaust gas has taken a fifth of the room: 16.5% oxygen, where the fire still burns
        final float share = 0.2125f;
        final float nitrogen = share * GasMath.CELL_MOLES;
        for (int x = 1; x <= SIZE; x++)
        {
            for (int y = 1; y <= HEIGHT; y++)
            {
                for (int z = 1; z <= SIZE; z++)
                {
                    final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    final @Nullable float[] cell = volume.cellForEmission(pos.getX(), pos.getY(), pos.getZ());
                    if (cell != null)
                    {
                        cell[Gas.FLUE_NITROGEN.ordinal()] = nitrogen;
                    }
                }
            }
        }
        helper.onEachTick(() -> {});
        helper.runAtTickTime(5, () -> draught(forge).invalidate());
        helper.runAtTickTime(150, () -> {
            final float factor = draught(forge).oxygenFactor();
            final float wanted = Draught.oxygenFactor(GasMath.OXYGEN_IN_AIR * (1f - share));
            helper.assertTrue(Math.abs(factor - wanted) < 0.03f, "The oxygen of the room gives a factor of " + wanted + ", got " + factor);
            final float expected = Draught.temperature(1000f, 1600f, 0f, factor);
            helper.assertTrue(Math.abs(forge.getTemperature() - expected) < 4f, "The forge runs at " + expected + " C, got " + forge.getTemperature());
            helper.assertTrue(forge.getTemperature() < 850f, "and that is well below 1000 C, got " + forge.getTemperature());
            helper.succeed();
        });
    }

    /** Rain cools the target of a hearth by 300 C as TFC has it: the model passes it through. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void rainCoolsTheTargetByThreeHundredDegrees(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Items.CHARCOAL, 1000f);
        final DraughtState draught = draught(forge);
        final ServerLevel level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(FORGE);
        draught.invalidate();
        float temperature = 1000f;
        for (int tick = 0; tick < 400; tick++)
        {
            temperature = draught.adjust(level, pos, temperature, 1100f, 0, true);
        }
        final float expected = Draught.temperature(1000f, 1600f, 0f, draught.oxygenFactor()) - 300f;
        helper.assertTrue(Math.abs(temperature - expected) < 1f && temperature < 710f, "In the rain the forge settles 300 C lower, at " + expected + ", got " + temperature);
        float dry = 700f;
        for (int tick = 0; tick < 400; tick++)
        {
            dry = draught.adjust(level, pos, dry, 1100f, 0, false);
        }
        helper.assertTrue(Math.abs(dry - (expected + 300f)) < 1f, "and as the rain stops it returns, got " + dry);
        helper.assertTrue(draught.takeBurnTicks(true) == 2 && draught.takeBurnTicks(true) == 2, "The fuel burns twice as fast in the rain, as in TFC");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20, batch = BATCH)
    public static void theFuelADeviceTookIsKeptWhenItIsSavedAndLoaded(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Chemistry.COKE.get(), 20f);
        final HolderLookup.Provider registries = helper.getLevel().registryAccess();
        final CompoundTag tag = forge.saveWithoutMetadata(registries);
        helper.assertTrue(tag.getString("tfcppFuel").equals("tfcpp:coke"), "The coke is in the saved data, got " + tag.getString("tfcppFuel"));
        final CharcoalForgeBlockEntity copy = new CharcoalForgeBlockEntity(helper.absolutePos(FORGE), forge.getBlockState());
        copy.loadWithComponents(tag, registries);
        final DraughtState loaded = ((Hearth) copy).tfcpp$draught();
        helper.assertTrue("tfcpp:coke".equals(loaded.knownFuel(1250f)), "The copy knows the fuel while it still burns at its temperature");
        helper.assertTrue(loaded.knownFuel(1100f) == null, "and not once the forge has gone on to a piece of another temperature that nothing recorded");
        helper.assertTrue(DraughtFuel.natural(loaded.knownFuel(1100f), 1100f) == 950f, "which then counts as the lowest fuel that burns at 1100: lignite char");
        helper.succeed();
    }

    /** What a person does: the pipe in the hand, a click on the forge or on a crucible standing on it, then holding the use. Through the real handler. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = BATCH)
    public static void playersWithPipesBlowThroughTheRealHandlerAndTheClickDoesNotOpenTheScreen(GameTestHelper helper)
    {
        final CharcoalForgeBlockEntity forge = litForge(helper, Items.CHARCOAL, 1000f);
        helper.setBlock(FORGE.above(), TFCBlocks.CRUCIBLE.get());
        final ServerLevel level = helper.getLevel();
        final BlockPos forgePos = helper.absolutePos(FORGE);
        final ServerPlayer first = pipePlayer(helper, forgePos), second = pipePlayer(helper, forgePos);

        // A right click on the forge, and on the crucible over it, falls through to the use of the pipe
        final ItemStack pipe = first.getItemInHand(InteractionHand.MAIN_HAND);
        final BlockHitResult onForge = new BlockHitResult(Vec3.atCenterOf(forgePos), Direction.UP, forgePos, false);
        final BlockHitResult onCrucible = new BlockHitResult(Vec3.atCenterOf(forgePos.above()), Direction.UP, forgePos.above(), false);
        helper.assertTrue(level.getBlockState(forgePos).useItemOn(pipe, level, first, InteractionHand.MAIN_HAND, onForge) == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "A click on the forge with a pipe does not open its screen");
        helper.assertTrue(level.getBlockState(forgePos.above()).useItemOn(pipe, level, first, InteractionHand.MAIN_HAND, onCrucible) == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "Nor does one on the crucible standing on it");
        final ItemStack withGlass = new ItemStack(TFCItems.CERAMIC_BLOWPIPE_WITH_GLASS.get());
        helper.assertTrue(level.getBlockState(forgePos).useItemOn(withGlass, level, first, InteractionHand.MAIN_HAND, onForge) != ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "A pipe with glass on it is at its own work, and the forge keeps its screen");

        first.startUsingItem(InteractionHand.MAIN_HAND);
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(first.isUsingItem(), "The first player is holding the use");
            helper.assertTrue(draught(forge).blowers() == 1, "One person is blowing, got " + draught(forge).blowers());
            helper.assertTrue(first.getFoodData().getExhaustionLevel() > 0f, "and it is tiring");
            second.startUsingItem(InteractionHand.MAIN_HAND);
        });
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(draught(forge).blowers() == 2, "Two people blowing are counted as two, got " + draught(forge).blowers());
            helper.assertTrue(draught(forge).air() > Draught.pipeAir(1) + 0.1f, "and give more air than one, got " + draught(forge).air());
            first.stopUsingItem();
            second.stopUsingItem();
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(draught(forge).blowers() == 0, "When they stop the air stops, got " + draught(forge).blowers());
            helper.succeed();
        });
    }

    /** A player above the forge, looking straight down at it, with an empty ceramic pipe in the hand. A fake player does not tick, so this one carries on the use of its item at the tick that the test gives it, which is what a real player does through the same event. */
    private static ServerPlayer pipePlayer(GameTestHelper helper, BlockPos forgePos)
    {
        final FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_blower"))
        {
            @Override
            public void tick()
            {
                if (isUsingItem())
                {
                    updateUsingItem(getUseItem());
                }
            }
        };
        helper.onEachTick(player::tick);
        player.setNoGravity(true);
        player.moveTo(forgePos.getX() + 0.5, forgePos.getY() + 1.5, forgePos.getZ() + 0.5, 0f, 90f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TFCItems.CERAMIC_BLOWPIPE.get()));
        return player;
    }

    /** With the calendar running a tick for a tick, the fuel burns as fast as the air drives it: once on its own, twice on a full bellows. */
    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = CALENDAR_BATCH)
    public static void fuelBurnsAsFastAsTheAirDrivesIt(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true);
        Calendars.SERVER.setCalendarTickRate(1f);
        final CharcoalForgeBlockEntity forge = litForge(helper, Items.CHARCOAL, 1000f);
        final int[] start = new int[1], middle = new int[1];
        helper.runAtTickTime(10, () -> start[0] = ((Hearth) forge).tfcpp$burnTicks());
        helper.runAtTickTime(110, () -> {
            middle[0] = ((Hearth) forge).tfcpp$burnTicks();
            helper.assertTrue(Math.abs((start[0] - middle[0]) - 100) <= 6, "Unblown, 100 ticks cost about 100 burn ticks, got " + (start[0] - middle[0]));
        });
        helper.onEachTick(() -> {
            if (helper.getTick() >= 110)
            {
                forge.intakeAir(BellowsBlockEntity.MAX_DEVICE_AIR_TICKS);
            }
        });
        helper.runAtTickTime(250, () -> {
            final int now = ((Hearth) forge).tfcpp$burnTicks();
            helper.assertTrue(Math.abs((middle[0] - now) - 280) <= 14, "On a full bellows, 140 ticks cost about 280 burn ticks, got " + (middle[0] - now));
            helper.succeed();
        });
    }

    private DraughtGameTests() {}
}
