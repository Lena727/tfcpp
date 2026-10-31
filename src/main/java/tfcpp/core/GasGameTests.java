package tfcpp.core;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.Breathing;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasMath;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;
import tfcpp.pneumatics.gas.Physiology;

/** Checks the parts that only exist inside a running server: mixins, TFC block entities, chunk storage and blocks stopping gas. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasGameTests
{
    private static final String TEMPLATE = "empty9";

    /** Builds a hollow stone box, with an air interior from (1,1,1) to (size,height,size) relative to the test. */
    private static void sealedBox(GameTestHelper helper, int size, int height, Block wall)
    {
        for (int x = 0; x <= size + 1; x++)
        {
            for (int y = 0; y <= height + 1; y++)
            {
                for (int z = 0; z <= size + 1; z++)
                {
                    final boolean inside = x > 0 && x <= size && y > 0 && y <= height && z > 0 && z <= size;
                    helper.setBlock(x, y, z, inside ? Blocks.AIR : wall);
                }
            }
        }
    }

    private static LevelGasVolume volume(GameTestHelper helper)
    {
        return GasTracker.get(helper.getLevel()).volume(helper.getLevel());
    }

    @Nullable
    private static float[] cell(GameTestHelper helper, int x, int y, int z)
    {
        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        return volume(helper).cell(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void addGas(GameTestHelper helper, int x, int y, int z, Gas gas, float moles)
    {
        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        final float[] existing = volume(helper).cell(pos.getX(), pos.getY(), pos.getZ());
        final float[] cell = existing != null ? existing : volume(helper).createCell(pos.getX(), pos.getY(), pos.getZ());
        helper.assertTrue(cell != null, "Could not create a gas cell");
        cell[gas.ordinal()] += moles;
    }

    private static float totalInBox(GameTestHelper helper, int size, int height, Gas gas)
    {
        float total = 0;
        for (int x = 1; x <= size; x++)
        {
            for (int y = 1; y <= height; y++)
            {
                for (int z = 1; z <= size; z++)
                {
                    final float[] cell = cell(helper, x, y, z);
                    if (cell != null)
                    {
                        total += cell[gas.ordinal()];
                    }
                }
            }
        }
        return total;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void gasStaysInASealedRoomAndSinks(GameTestHelper helper)
    {
        sealedBox(helper, 3, 4, Blocks.STONE);
        addGas(helper, 2, 4, 2, Gas.SULFUR_DIOXIDE, 20f);

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(Math.abs(totalInBox(helper, 3, 4, Gas.SULFUR_DIOXIDE) - 20f) < 0.1f, "SO2 must be conserved in a sealed room");

            final float[] floor = cell(helper, 2, 1, 2), roof = cell(helper, 2, 4, 2);
            helper.assertTrue(floor != null && roof != null && floor[Gas.SULFUR_DIOXIDE.ordinal()] > roof[Gas.SULFUR_DIOXIDE.ordinal()], "SO2 must collect at the floor");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void closedDoorHoldsGasBackAndOpenDoorLetsItThrough(GameTestHelper helper)
    {
        sealedBox(helper, 3, 2, Blocks.STONE);

        // A wall across x = 2, with a door in it
        for (int z = 1; z <= 3; z++)
        {
            helper.setBlock(2, 1, z, Blocks.STONE);
            helper.setBlock(2, 2, z, Blocks.STONE);
        }
        final BlockPos door = new BlockPos(2, 1, 2);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState());
        helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

        addGas(helper, 1, 1, 2, Gas.CARBON_DIOXIDE, 10f);

        helper.runAfterDelay(80, () -> {
            helper.assertTrue(cell(helper, 3, 1, 2) == null, "Gas must not pass a closed door");
            helper.setBlock(door, helper.getBlockState(door).setValue(DoorBlock.OPEN, true));
            helper.setBlock(door.above(), helper.getBlockState(door.above()).setValue(DoorBlock.OPEN, true));
        });
        helper.runAfterDelay(240, () -> {
            helper.assertTrue(cell(helper, 3, 1, 2) != null, "Gas must pass an open door");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, skyAccess = true)
    public static void gasUnderOpenSkyDisperses(GameTestHelper helper)
    {
        final BlockPos above = new BlockPos(4, 6, 4);
        addGas(helper, above.getX(), above.getY(), above.getZ(), Gas.CARBON_DIOXIDE, 0.05f);

        helper.runAfterDelay(400, () -> {
            final float[] cell = cell(helper, above.getX(), above.getY(), above.getZ());
            helper.assertTrue(cell == null || cell[Gas.CARBON_DIOXIDE.ordinal()] < 0.001f, "The atmosphere must carry gas away");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void forgeNoLongerNeedsToSeeTheSky(GameTestHelper helper)
    {
        final Block insulation = TFCBlocks.ROCK_BLOCKS.get(Rock.GRANITE).get(Rock.BlockType.BRICKS).get();

        sealedBox(helper, 3, 3, insulation);
        final BlockPos forge = new BlockPos(2, 1, 2);
        for (BlockPos side : new BlockPos[] {forge.north(), forge.south(), forge.east(), forge.west()})
        {
            helper.setBlock(side, insulation);
        }

        final BlockPos absolute = helper.absolutePos(forge);
        helper.assertFalse(volume(helper).isOpenToSky(absolute.getX(), absolute.getY() + 1, absolute.getZ()), "The test room should be roofed");
        helper.assertTrue(CharcoalForgeBlock.isValid(helper.getLevel(), absolute), "A roofed, insulated forge must be valid");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void charcoalPitFillsARoomWithFlueGas(GameTestHelper helper)
    {
        Calendars.SERVER.setPlayersLoggedOn(true); // The TFC calendar stands still on an empty server, and fires burn by the calendar
        sealedBox(helper, 3, 3, Blocks.STONE);
        helper.setBlock(2, 1, 2, TFCBlocks.BURNING_LOG_PILE.get());

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(GasTracker.get(helper.getLevel()).fireCount() > 0, "The pit must be tracked as a fire");
            final float carbonMonoxide = totalInBox(helper, 3, 3, Gas.CARBON_MONOXIDE);
            helper.assertTrue(carbonMonoxide > 0f, "A burning pit must release CO, got " + carbonMonoxide);
            final float[] roof = cell(helper, 2, 3, 2);
            helper.assertTrue(roof != null && GasMath.oxygenFraction(roof) < GasMath.OXYGEN_IN_AIR, "Burning must use up oxygen");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void breathingCarbonMonoxideBindsToBlood(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.STONE);
        final TestPlayer player = survivalPlayerAt(helper, 2, 1, 2);
        fillMouth(helper, player, Gas.CARBON_MONOXIDE, 0.6f); // About 14400 ppm

        for (int second = 0; second < 30; second++)
        {
            Breathing.tick(player);
        }

        final float blood = player.getData(GasAttachments.BREATHING).carboxyhemoglobin;
        helper.assertTrue(blood > 10f && blood < Physiology.COHB_FATAL, "30 s at about 14400 ppm should give roughly 19% COHb, got " + blood);
        helper.assertTrue(player.lastDamage == null, "CO must not hurt until it is too late");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void airWithoutOxygenSuffocates(GameTestHelper helper)
    {
        sealedBox(helper, 3, 3, Blocks.STONE);
        final TestPlayer player = survivalPlayerAt(helper, 2, 1, 2);
        fillMouth(helper, player, Gas.FLUE_NITROGEN, GasMath.CELL_MOLES);

        Breathing.tick(player);

        helper.assertTrue(player.lastDamage != null && player.lastDamage.is(Breathing.ASPHYXIATION), "Breathing pure nitrogen must suffocate");
        helper.assertTrue(player.lastDamage.is(DamageTypeTags.BYPASSES_ARMOR), "Armour must not protect against a lack of oxygen");
        helper.succeed();
    }

    private static void fillMouth(GameTestHelper helper, ServerPlayer player, Gas gas, float moles)
    {
        final BlockPos mouth = BlockPos.containing(player.getEyePosition());
        final float[] cell = volume(helper).createCell(mouth.getX(), mouth.getY(), mouth.getZ());
        helper.assertTrue(cell != null, "Could not create a gas cell");
        cell[gas.ordinal()] += moles;
    }

    private static TestPlayer survivalPlayerAt(GameTestHelper helper, int x, int y, int z)
    {
        final TestPlayer player = new TestPlayer(helper);
        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    /** Not a mock player, as joining one would make TFC sync data over a connection that does not exist. */
    private static final class TestPlayer extends FakePlayer
    {
        @Nullable DamageSource lastDamage;

        TestPlayer(GameTestHelper helper)
        {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_test"));
        }

        @Override
        public boolean hurt(DamageSource source, float amount)
        {
            lastDamage = source;
            return true;
        }
    }

    private GasGameTests() {}
}
