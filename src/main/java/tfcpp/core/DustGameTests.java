package tfcpp.core;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.QuernBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.items.Powder;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Metal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.pneumatics.gas.DustSources;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasChunkData;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/** Coal dust and stone dust inside a running server: the sources, the flame, and what a chunk does with dust lying in it. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DustGameTests
{
    private static final String TEMPLATE = "empty9";
    /** Dust that is only put in and watched. */
    private static final String QUIET = "tfcpp_dust";
    /** The flame and the blast, apart from everything else. */
    private static final String BLAST = "tfcpp_dust_blast";
    /** These work on chunks far from the test, and must not share them with any gas. */
    private static final String CHUNKS = "tfcpp_dust_chunks";

    private static void walls(GameTestHelper helper, int sizeX, int sizeY, int sizeZ, Block wall)
    {
        for (int x = 0; x <= sizeX + 1; x++)
        {
            for (int y = 0; y <= sizeY + 1; y++)
            {
                for (int z = 0; z <= sizeZ + 1; z++)
                {
                    final boolean inside = x > 0 && x <= sizeX && y > 0 && y <= sizeY && z > 0 && z <= sizeZ;
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

    private static void add(GameTestHelper helper, int x, int y, int z, Gas gas, float amount)
    {
        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        final float[] cell = volume(helper).cellForEmission(pos.getX(), pos.getY(), pos.getZ());
        helper.assertTrue(cell != null, "Could not create a gas cell");
        cell[gas.ordinal()] += amount;
    }

    private static float amount(GameTestHelper helper, int x, int y, int z, Gas gas)
    {
        final float[] cell = cell(helper, x, y, z);
        return cell == null ? 0f : cell[gas.ordinal()];
    }

    private static float totalInBox(GameTestHelper helper, int sizeX, int sizeY, int sizeZ, Gas gas)
    {
        float total = 0;
        for (int x = 1; x <= sizeX; x++)
        {
            for (int y = 1; y <= sizeY; y++)
            {
                for (int z = 1; z <= sizeZ; z++)
                {
                    total += amount(helper, x, y, z, gas);
                }
            }
        }
        return total;
    }

    // Sources

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = QUIET)
    public static void breakingCoalRaisesDustAndLeavesSomeLying(GameTestHelper helper)
    {
        walls(helper, 3, 3, 3, Blocks.OBSIDIAN);
        final BlockPos coal = new BlockPos(2, 1, 2);
        helper.setBlock(coal, TFCBlocks.BITUMINOUS_COAL.get());

        final TestPlayer player = playerAt(helper, 1, 1, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TFCItems.METAL_ITEMS.get(Metal.BRONZE).get(Metal.ItemType.PICKAXE).get()));
        helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(coal)), "The block should break");

        helper.assertTrue(Math.abs(totalInBox(helper, 3, 3, 3, Gas.COAL_DUST) - DustSources.MINED_AIRBORNE) < 1e-6f, "5 g in the air: " + totalInBox(helper, 3, 3, 3, Gas.COAL_DUST));
        helper.assertTrue(Math.abs(totalInBox(helper, 3, 3, 3, Gas.SETTLED_COAL_DUST) - DustSources.MINED_SETTLED) < 1e-6f, "50 g lying around the broken block: " + totalInBox(helper, 3, 3, 3, Gas.SETTLED_COAL_DUST));
        helper.assertTrue(amount(helper, 2, 1, 2, Gas.SETTLED_COAL_DUST) > 0f && amount(helper, 2, 2, 2, Gas.SETTLED_COAL_DUST) > 0f, "The dust is shared between the cell and the open ones beside it");
        helper.assertTrue(Math.abs(amount(helper, 2, 1, 2, Gas.SETTLED_COAL_DUST) - amount(helper, 2, 2, 2, Gas.SETTLED_COAL_DUST)) < 1e-6f, "equally");

        helper.runAtTickTime(200, () -> {
            final float all = totalInBox(helper, 3, 3, 3, Gas.COAL_DUST) + totalInBox(helper, 3, 3, 3, Gas.SETTLED_COAL_DUST);
            helper.assertTrue(Math.abs(all - (DustSources.MINED_AIRBORNE + DustSources.MINED_SETTLED)) < 1e-5f, "Nothing is lost in a sealed room: " + all);
            helper.assertTrue(totalInBox(helper, 3, 3, 3, Gas.SETTLED_COAL_DUST) > DustSources.MINED_SETTLED, "Some of what hung in the air has settled");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = QUIET)
    public static void breakingOtherBlocksRaisesNoDust(GameTestHelper helper)
    {
        walls(helper, 3, 3, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 2, Blocks.COBBLESTONE);
        final TestPlayer player = playerAt(helper, 1, 1, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TFCItems.METAL_ITEMS.get(Metal.BRONZE).get(Metal.ItemType.PICKAXE).get()));
        helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(2, 1, 2))), "The block should break");
        helper.assertTrue(totalInBox(helper, 3, 3, 3, Gas.COAL_DUST) == 0f && totalInBox(helper, 3, 3, 3, Gas.SETTLED_COAL_DUST) == 0f, "Stone is not coal");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = QUIET)
    public static void grindingCoalAtAQuernRaisesDust(GameTestHelper helper)
    {
        walls(helper, 3, 3, 3, Blocks.OBSIDIAN);
        final BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, TFCBlocks.QUERN.get());
        final QuernBlockEntity quern = (QuernBlockEntity) helper.getBlockEntity(at);
        quern.setHandstoneFromOutsideWorld();
        quern.getInventory().setStackInSlot(QuernBlockEntity.SLOT_INPUT, new ItemStack(Items.CHARCOAL, 2));

        grind(helper, quern, at);
        helper.assertTrue(Math.abs(totalInBox(helper, 3, 3, 3, Gas.COAL_DUST) - DustSources.GROUND_AIRBORNE) < 1e-6f, "2 g for one grind: " + totalInBox(helper, 3, 3, 3, Gas.COAL_DUST));
        helper.assertTrue(quern.getInventory().getStackInSlot(QuernBlockEntity.SLOT_OUTPUT).is(TFCItems.POWDERS.get(Powder.CHARCOAL).asItem()), "The quern still gives its powder");

        grind(helper, quern, at);
        helper.assertTrue(Math.abs(totalInBox(helper, 3, 3, 3, Gas.COAL_DUST) - 2 * DustSources.GROUND_AIRBORNE) < 1e-6f, "and 2 g more for the second");

        quern.getInventory().setStackInSlot(QuernBlockEntity.SLOT_INPUT, new ItemStack(TFCItems.POWDERS.get(Powder.CHARCOAL).asItem(), 1));
        helper.assertFalse(DustSources.isCoal(new ItemStack(Items.BONE_MEAL)), "Other things raise nothing");
        helper.assertTrue(DustSources.isCoal(new ItemStack(Items.COAL)) && DustSources.isCoal(new ItemStack(Items.CHARCOAL)) && DustSources.isCoal(new ItemStack(tfcpp.chemistry.Chemistry.COKE.get())), "Coal, charcoal and coke are all coal");
        helper.succeed();
    }

    private static void grind(GameTestHelper helper, QuernBlockEntity quern, BlockPos at)
    {
        helper.assertTrue(quern.startGrinding(), "The quern should start");
        final BlockPos absolute = helper.absolutePos(at);
        for (int tick = 0; tick < 200 && quern.isGrinding(); tick++)
        {
            QuernBlockEntity.serverTick(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), quern);
        }
        helper.assertFalse(quern.isGrinding(), "The grind should finish");
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500, batch = QUIET)
    public static void airborneDustSettlesAtOnePercentASecond(GameTestHelper helper)
    {
        walls(helper, 3, 3, 3, Blocks.OBSIDIAN);
        add(helper, 2, 2, 2, Gas.COAL_DUST, 1f);

        helper.runAtTickTime(400, () -> {
            final float airborne = totalInBox(helper, 3, 3, 3, Gas.COAL_DUST);
            final float settled = totalInBox(helper, 3, 3, 3, Gas.SETTLED_COAL_DUST);
            helper.assertTrue(Math.abs(airborne + settled - 1f) < 1e-4f, "Nothing is lost: " + airborne + " + " + settled);
            // 20 s at 1% a second is 82% still in the air. The first step counts up to 5 s from before the dust was put in, and the last may fall short by as much
            helper.assertTrue(airborne > 0.72f && airborne < 0.92f, "About 80% should still hang after 20 s: " + airborne);
            helper.succeed();
        });
    }

    // Stone dusting

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = QUIET)
    public static void stoneDustClickUsesOnePowderAndReportsTheSample(GameTestHelper helper)
    {
        walls(helper, 3, 3, 3, Blocks.OBSIDIAN);
        add(helper, 2, 1, 2, Gas.SETTLED_COAL_DUST, 0.05f);

        final TestPlayer player = playerAt(helper, 1, 1, 1);
        final ItemStack powder = new ItemStack(TFCItems.POWDERS.get(Powder.FLUX).asItem(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, powder);

        final PlayerInteractEvent.RightClickBlock click = click(helper, player, new BlockPos(2, 0, 2));
        helper.assertTrue(click.isCanceled(), "The click is the stone dust's");
        helper.assertTrue(powder.getCount() == 2, "One powder is used up, got " + powder.getCount());
        helper.assertTrue(Math.abs(totalInBox(helper, 3, 3, 3, Gas.SETTLED_ROCK_DUST) - DustSources.KILOGRAMS_PER_STONE_DUST_POWDER) < 1e-4f, "0.75 kg goes into the room: " + totalInBox(helper, 3, 3, 3, Gas.SETTLED_ROCK_DUST));
        helper.assertTrue(Math.abs(amount(helper, 1, 1, 1, Gas.SETTLED_ROCK_DUST) - amount(helper, 3, 3, 3, Gas.SETTLED_ROCK_DUST)) < 1e-6f, "shared equally between the cells");

        final float rock = amount(helper, 2, 1, 2, Gas.SETTLED_ROCK_DUST);
        final int expected = Math.round(100f * rock / (rock + 0.05f));
        helper.assertTrue(player.lastMessage != null && player.lastMessage.getContents() instanceof TranslatableContents, "The player should get a sample result");
        final TranslatableContents message = (TranslatableContents) player.lastMessage.getContents();
        helper.assertTrue(message.getKey().equals("tfcpp.stone_dust.sample") && message.getArgs()[0].equals(expected) && message.getArgs()[1].equals(65), "Message should show " + expected + "%, got " + message.getKey() + " " + java.util.Arrays.toString(message.getArgs()));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, skyAccess = true, batch = QUIET)
    public static void stoneDustDoesNothingUnderTheOpenSky(GameTestHelper helper)
    {
        helper.setBlock(4, 1, 4, Blocks.STONE);
        final TestPlayer player = playerAt(helper, 2, 1, 2);
        final ItemStack powder = new ItemStack(TFCItems.POWDERS.get(Powder.FLUX).asItem(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, powder);

        final PlayerInteractEvent.RightClickBlock click = click(helper, player, new BlockPos(4, 1, 4));
        helper.assertFalse(click.isCanceled(), "Nothing was done");
        helper.assertTrue(powder.getCount() == 3, "and nothing was used up");
        helper.assertTrue(cell(helper, 4, 2, 4) == null, "No dust was put anywhere");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = QUIET)
    public static void stoneDustLeavesAloneBlocksThatTakeTheItem(GameTestHelper helper)
    {
        walls(helper, 3, 3, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 2, TFCBlocks.QUERN.get());
        final TestPlayer player = playerAt(helper, 1, 1, 1);
        final ItemStack powder = new ItemStack(TFCItems.POWDERS.get(Powder.FLUX).asItem(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, powder);

        final PlayerInteractEvent.RightClickBlock click = click(helper, player, new BlockPos(2, 1, 2), Direction.UP);
        helper.assertFalse(click.isCanceled(), "A quern takes the powder itself");
        helper.assertTrue(powder.getCount() == 3 && totalInBox(helper, 3, 3, 3, Gas.SETTLED_ROCK_DUST) == 0f, "and the room is not dusted");
        helper.succeed();
    }

    private static PlayerInteractEvent.RightClickBlock click(GameTestHelper helper, TestPlayer player, BlockPos block)
    {
        return click(helper, player, block, Direction.UP);
    }

    private static PlayerInteractEvent.RightClickBlock click(GameTestHelper helper, TestPlayer player, BlockPos block, Direction face)
    {
        final BlockPos pos = helper.absolutePos(block);
        final PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    // Flame

    /** A gallery of this many cells along x, one cell wide, in obsidian. */
    private static void gallery(GameTestHelper helper, int length)
    {
        walls(helper, length, 1, 1, Blocks.OBSIDIAN);
    }

    private static void lightFiredampAtTheMouth(GameTestHelper helper)
    {
        helper.setBlock(1, 1, 1, Blocks.TORCH);
        add(helper, 1, 1, 1, Gas.METHANE, 0.095f * 41.6f);
        add(helper, 2, 1, 1, Gas.METHANE, 0.095f * 41.6f);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = BLAST)
    public static void torchInAThickCloudOfDustExplodesAndLeavesCarbonMonoxide(GameTestHelper helper)
    {
        walls(helper, 3, 3, 3, Blocks.OBSIDIAN);
        helper.setBlock(2, 1, 2, Blocks.TORCH);
        for (int x = 1; x <= 3; x++)
        {
            for (int y = 1; y <= 3; y++)
            {
                for (int z = 1; z <= 3; z++)
                {
                    add(helper, x, y, z, Gas.COAL_DUST, 0.3f); // 300 g/m3, six times the lower limit
                }
            }
        }

        helper.succeedWhen(() -> {
            helper.assertTrue(totalInBox(helper, 3, 3, 3, Gas.CARBON_MONOXIDE) > 20f, "The afterdamp of a dust explosion holds CO: " + totalInBox(helper, 3, 3, 3, Gas.CARBON_MONOXIDE));
            helper.assertTrue(totalInBox(helper, 3, 3, 3, Gas.CARBON_DIOXIDE) > 20f, "and CO2");
            helper.assertTrue(totalInBox(helper, 3, 3, 3, Gas.COAL_DUST) < 27 * 0.3f * 0.9f, "Some of the dust has burned");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BLAST)
    public static void firedampInADustyGalleryIsFollowedByASecondaryExplosion(GameTestHelper helper)
    {
        gallery(helper, 7);
        lightFiredampAtTheMouth(helper);
        for (int x = 3; x <= 7; x++)
        {
            add(helper, x, 1, 1, Gas.SETTLED_COAL_DUST, 0.3f);
        }

        helper.succeedWhen(() -> {
            for (int x = 3; x <= 7; x++)
            {
                helper.assertTrue(amount(helper, x, 1, 1, Gas.SETTLED_COAL_DUST) == 0f, "The dust at " + x + " should have been raised and burned");
            }
            helper.assertTrue(totalInBox(helper, 7, 1, 1, Gas.CARBON_MONOXIDE) > 15f, "The whole gallery is full of afterdamp: " + totalInBox(helper, 7, 1, 1, Gas.CARBON_MONOXIDE));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = BLAST)
    public static void aStretchOfStoneDustStopsTheFlame(GameTestHelper helper)
    {
        gallery(helper, 7);
        lightFiredampAtTheMouth(helper);
        for (int x = 3; x <= 4; x++)
        {
            add(helper, x, 1, 1, Gas.SETTLED_COAL_DUST, 0.3f);
        }
        for (int x = 5; x <= 6; x++)
        {
            add(helper, x, 1, 1, Gas.SETTLED_COAL_DUST, 0.105f); // 35% coal, 65% limestone
            add(helper, x, 1, 1, Gas.SETTLED_ROCK_DUST, 0.195f);
        }
        add(helper, 7, 1, 1, Gas.SETTLED_COAL_DUST, 0.3f);

        helper.succeedWhen(() -> {
            helper.assertTrue(amount(helper, 3, 1, 1, Gas.SETTLED_COAL_DUST) == 0f && amount(helper, 4, 1, 1, Gas.SETTLED_COAL_DUST) == 0f, "The explosion should have gone through the bare dust");
            for (int x = 5; x <= 6; x++)
            {
                helper.assertTrue(Math.abs(amount(helper, x, 1, 1, Gas.SETTLED_COAL_DUST) - 0.105f) < 1e-3f, "The stone dust at " + x + " should keep its coal down");
                helper.assertTrue(Math.abs(amount(helper, x, 1, 1, Gas.SETTLED_ROCK_DUST) - 0.195f) < 1e-3f, "and stay");
            }
            helper.assertTrue(Math.abs(amount(helper, 7, 1, 1, Gas.SETTLED_COAL_DUST) - 0.3f) < 1e-3f, "Nothing beyond the stone dust happened");
        });
    }

    // Chunks

    /** A chunk well away from the test, kept loaded, where no other test puts any gas. */
    private static LevelChunk farChunk(GameTestHelper helper, int offset)
    {
        final ServerLevel level = helper.getLevel();
        final BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        final int chunkX = (origin.getX() >> 4) + offset, chunkZ = origin.getZ() >> 4;
        level.setChunkForced(chunkX, chunkZ, true);
        return level.getChunk(chunkX, chunkZ);
    }

    private static BlockPos insideChunk(ServerLevel level, LevelChunk chunk)
    {
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 4, z);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = CHUNKS)
    public static void aChunkHoldingOnlySettledDustSleepsAndAnEmissionWakesIt(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 40);
        final BlockPos at = insideChunk(level, chunk);
        final LevelGasVolume volume = GasTracker.get(level).volume(level);
        final GasChunkData data = chunk.getData(GasAttachments.CHUNK);

        volume.cellForEmission(at.getX(), at.getY(), at.getZ())[Gas.SETTLED_COAL_DUST.ordinal()] += 0.5f;
        helper.assertTrue(data.isActive(), "A chunk that has just been written to is looked at");

        helper.runAtTickTime(100, () -> {
            helper.assertFalse(data.isActive(), "Nothing in it can move, so it sleeps");
            final float[] dust = volume.cell(at.getX(), at.getY(), at.getZ());
            helper.assertTrue(dust != null && dust[Gas.SETTLED_COAL_DUST.ordinal()] == 0.5f, "but the dust is still lying there");

            volume.cellForEmission(at.getX(), at.getY(), at.getZ())[Gas.CARBON_DIOXIDE.ordinal()] += 20f;
            helper.assertTrue(data.isActive(), "A new emission wakes it");
        });

        helper.runAtTickTime(220, () -> {
            final float[] here = volume.cell(at.getX(), at.getY(), at.getZ());
            final float[] beside = volume.cell(at.getX() + 1, at.getY(), at.getZ());
            final float spread = (here == null ? 0f : here[Gas.CARBON_DIOXIDE.ordinal()]) + (beside == null ? 0f : beside[Gas.CARBON_DIOXIDE.ordinal()]);
            helper.assertTrue(beside != null && beside[Gas.CARBON_DIOXIDE.ordinal()] > 0f, "The gas has moved into the cell beside it");
            helper.assertTrue(here != null && here[Gas.SETTLED_COAL_DUST.ordinal()] == 0.5f && here[Gas.SETTLED_ROCK_DUST.ordinal()] == 0f, "and the dust did not move with it, spread " + spread);

            data.clear();
            level.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = CHUNKS)
    public static void aChunkOfSettledDustSurvivesSavingAndLoading(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = farChunk(helper, 44);
        final BlockPos at = insideChunk(level, chunk);
        final GasChunkData data = chunk.getData(GasAttachments.CHUNK);
        final int key = GasChunkData.key(at.getX(), at.getY(), at.getZ());
        final float[] cell = data.getOrCreate(key);
        cell[Gas.SETTLED_COAL_DUST.ordinal()] = 0.12f;
        cell[Gas.SETTLED_ROCK_DUST.ordinal()] = 0.34f;

        final net.minecraft.nbt.CompoundTag saved = GasChunkData.SERIALIZER.write(data, level.registryAccess());
        helper.assertTrue(saved != null, "A chunk with only settled dust is written");
        final GasChunkData loaded = GasChunkData.SERIALIZER.read(chunk, saved, level.registryAccess());
        final float[] back = loaded.get(key);
        helper.assertTrue(back != null && back[Gas.SETTLED_COAL_DUST.ordinal()] == 0.12f && back[Gas.SETTLED_ROCK_DUST.ordinal()] == 0.34f, "and read back whole");

        data.clear();
        level.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
        helper.succeed();
    }

    private static TestPlayer playerAt(GameTestHelper helper, int x, int y, int z)
    {
        final TestPlayer player = new TestPlayer(helper);
        final BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    /** Not a mock player, as joining one would make TFC sync data over a connection that does not exist. */
    private static final class TestPlayer extends FakePlayer
    {
        @Nullable Component lastMessage;

        TestPlayer(GameTestHelper helper)
        {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tfcpp_dust_test"));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar)
        {
            lastMessage = message;
        }
    }

    private DustGameTests() {}
}
