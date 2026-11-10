package tfcpp.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.dries007.tfc.common.blockentities.PotBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.entities.TFCEntities;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.util.calendar.CalendarTransaction;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.common.player.IPlayerInfo;
import net.dries007.tfc.util.data.Fertilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import tfcpp.ecology.EcologyAttachments;
import tfcpp.ecology.FoodMetals;
import tfcpp.ecology.LevelWaters;
import tfcpp.ecology.Pollutant;
import tfcpp.ecology.Pollution;
import tfcpp.ecology.RegionalAir;
import tfcpp.ecology.SoilOrder;
import tfcpp.ecology.WaterBody;
import tfcpp.ecology.Waters;
import tfcpp.health.CesspitBlockEntity;
import tfcpp.health.CesspitMath;
import tfcpp.health.Disease;
import tfcpp.health.DiseaseState;
import tfcpp.health.Drinking;
import tfcpp.health.FluidQuality;
import tfcpp.health.Hygiene;
import tfcpp.health.HygieneEvents;
import tfcpp.health.NightSoil;
import tfcpp.health.Organisms;
import tfcpp.health.SandFilterBlockEntity;
import tfcpp.health.Sewage;
import tfcpp.health.WaterQuality;
import tfcpp.health.WaterSource;
import tfcpp.pneumatics.gas.BreathingState;
import tfcpp.pneumatics.gas.GasAttachments;

/**
 * Stage E of v1.5: sewage, drinking water and disease. Every test works in a chunk of its own, far from the others, and clears what it builds. Tests that touch the waters of the level (which are one for the whole level) have a batch of their own.
 */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HygieneGameTests
{
    private static final String TEMPLATE = "empty9";
    private static final int FIRST_CHUNK_X = 5200, CHUNK_Z = 5200, SLOT_CHUNKS = 16;
    private static final long DAY = 24_000L;
    private static final int LEAD = FoodMetals.indexOf(Pollutant.LEAD);

    private static LevelChunk chunk(ServerLevel level, int slot)
    {
        final int chunkX = FIRST_CHUNK_X + slot * SLOT_CHUNKS;
        level.setChunkForced(chunkX, CHUNK_Z, true);
        final LevelChunk chunk = level.getChunk(chunkX, CHUNK_Z);
        chunk.removeData(EcologyAttachments.CHUNK);
        chunk.removeData(EcologyAttachments.WATER_CHUNK);
        chunk.removeData(Hygiene.CESSPITS);
        RegionalAir.get(level).clearCellAt(chunkX << 4, CHUNK_Z << 4);
        final BlockPos at = spot(level, chunk);
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-7, -1, -7), at.offset(7, 9, 7))) // What an earlier run of a kept world left
        {
            if (!level.getBlockState(pos).isAir())
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        return chunk;
    }

    private static BlockPos spot(ServerLevel level, LevelChunk chunk)
    {
        final int x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 12, z);
    }

    private static void release(ServerLevel level, LevelChunk chunk, BlockPos at)
    {
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-7, -1, -7), at.offset(7, 9, 7)))
        {
            if (!level.getBlockState(pos).isAir())
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        chunk.removeData(Hygiene.CESSPITS);
        chunk.removeData(EcologyAttachments.CHUNK);
        level.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
    }

    private static FakePlayer player(ServerLevel level, BlockPos at)
    {
        final FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "tfcpp_hygiene"));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    /** A fake player that can be hurt: the fake player of NeoForge is invulnerable to everything. */
    private static final class Mortal extends FakePlayer
    {
        Mortal(ServerLevel level)
        {
            super(level, new GameProfile(UUID.randomUUID(), "tfcpp_mortal"));
        }

        @Override
        public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source)
        {
            return isInvulnerable() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        }
    }

    private static double sewageInSoil(ServerLevel level, BlockPos at, Pollutant kind)
    {
        return Pollution.view(level, at).total(kind);
    }

    private static CesspitBlockEntity pit(ServerLevel level, BlockPos pos)
    {
        level.setBlock(pos, Hygiene.CESSPIT.get().defaultBlockState(), 3);
        return (CesspitBlockEntity) level.getBlockEntity(pos);
    }

    private static WaterQuality dirty(double faecal, double lead)
    {
        final double[] metals = new double[FoodMetals.METALS.length];
        metals[LEAD] = lead;
        return new WaterQuality(faecal, 0.0, 0.0, metals, 0.0);
    }

    // --- Waste ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void aPersonLeavesWasteWhereTheyStandAndInTheNearbyCesspitAndAFullPitSendsTheRestToTheGround(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 0);
        final BlockPos at = spot(level, chunk);
        try
        {
            final FakePlayer player = player(level, at);
            Pollution.seed(level, at, SoilOrder.PODZOL, false);
            final DiseaseState state = player.getData(Hygiene.DISEASES);
            final long now = Pollution.now();

            state.lastExcretion = now - DAY; // A day standing here
            HygieneEvents.excrete(player);
            helper.assertTrue(Math.abs(sewageInSoil(level, at, Pollutant.SEWAGE) - 1.5) < 0.01, "A day leaves 1.5 L of sewage in the soil, got " + sewageInSoil(level, at, Pollutant.SEWAGE));

            final CesspitBlockEntity pit = pit(level, at.east(5));
            state.lastExcretion = now - DAY;
            HygieneEvents.excrete(player);
            helper.assertTrue(Math.abs(pit.state().volume() - 1.5) < 1e-6, "With a cesspit within 16 blocks the day goes into the pit, got " + pit.state().volume());
            helper.assertTrue(Math.abs(sewageInSoil(level, at, Pollutant.SEWAGE) - 1.5) < 0.01, "and the soil has no more");

            // A person shedding typhoid: the pit's shedder share rises
            state.infect(Disease.TYPHOID, now - 15 * DAY, new SplittableRandom(1));
            helper.assertTrue(state.sheds(Disease.TYPHOID, now), "The person is ill and sheds");
            state.lastExcretion = now - DAY;
            HygieneEvents.excrete(player);
            helper.assertTrue(Math.abs(pit.state().shedTyphoid() - 1.5) < 1e-6, "and their waste is marked in the pit, got " + pit.state().shedTyphoid());

            // A full pit: what does not fit runs onto the ground
            pit.set(new CesspitMath.State(999.0, 0.0, 0.0, 0.0), now);
            state.lastExcretion = now - DAY;
            HygieneEvents.excrete(player);
            helper.assertTrue(Math.abs(pit.state().volume() - 1000.0) < 1e-6, "The pit takes what it can, got " + pit.state().volume());
            helper.assertTrue(Math.abs(sewageInSoil(level, at, Pollutant.SEWAGE) - 2.0) < 0.01, "and the rest, half a litre, runs over onto the soil, got " + sewageInSoil(level, at, Pollutant.SEWAGE));

            // A pit out of the vertical reach is not used
            level.setBlock(at.east(5), Blocks.AIR.defaultBlockState(), 3);
            final CesspitBlockEntity high = pit(level, at.east(2).above(6));
            final double before = sewageInSoil(level, at, Pollutant.SEWAGE);
            state.lastExcretion = now - DAY;
            HygieneEvents.excrete(player);
            helper.assertTrue(high.state().volume() == 0.0 && sewageInSoil(level, at, Pollutant.SEWAGE) > before + 1.0, "A pit 6 blocks up is out of reach");
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void livestockFoulTheSoilUnderThemAtTheirOwnRatesByTheCalendarThatPassedAndOneDepositToAChunk(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 1);
        final BlockPos at = spot(level, chunk);
        try
        {
            Pollution.seed(level, at, SoilOrder.PODZOL, false);
            final Entity cow = TFCEntities.COW.get().create(level), cow2 = TFCEntities.COW.get().create(level), duck = TFCEntities.DUCK.get().create(level);
            for (Entity animal : List.of(cow, cow2, duck))
            {
                animal.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            }
            final long base = Pollution.now();
            Sewage.livestock(level, (LivingEntity) cow);
            Sewage.flushLivestock(level, base); // The first window only sets the stamp
            helper.assertTrue(sewageInSoil(level, at, Pollutant.SEWAGE) == 0.0, "Nothing is laid for a calendar that has not moved");

            Sewage.livestock(level, (LivingEntity) cow);
            Sewage.livestock(level, (LivingEntity) cow2);
            Sewage.livestock(level, (LivingEntity) duck);
            Sewage.flushLivestock(level, base); // The calendar stands: the same tick again
            helper.assertTrue(sewageInSoil(level, at, Pollutant.SEWAGE) == 0.0, "and none while it stands");

            Sewage.livestock(level, (LivingEntity) cow);
            Sewage.livestock(level, (LivingEntity) cow2);
            Sewage.livestock(level, (LivingEntity) duck);
            Sewage.flushLivestock(level, base + 1200L);
            final double laid = sewageInSoil(level, at, Pollutant.SEWAGE);
            helper.assertTrue(Math.abs(laid - 5.005) < 0.01, "Two cows (50 L a day each) and a duck (0.1) over 1200 calendar ticks: 5.005 L, got " + laid);

            Sewage.livestock(level, (LivingEntity) cow);
            Sewage.flushLivestock(level, base + 1200L + 12_000L);
            final double half = sewageInSoil(level, at, Pollutant.SEWAGE) - laid;
            helper.assertTrue(Math.abs(half - 25.0) < 0.05, "Over half a day the amount follows the calendar that really passed: 25 L, got " + half);
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void aCalendarTurnedBackCountsNoWasteTwiceADeadPlayerLeavesNoneAndLogoutKeepsTheLastMinute(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 9);
        final BlockPos at = spot(level, chunk);
        try
        {
            Pollution.seed(level, at, SoilOrder.PODZOL, false);
            final FakePlayer player = player(level, at);
            final DiseaseState state = player.getData(Hygiene.DISEASES);
            final long now = Pollution.now();

            state.lastExcretion = now + DAY; // The calendar was turned back by a day
            HygieneEvents.excrete(player);
            helper.assertTrue(state.lastExcretion == now + DAY, "The stamp keeps the later time");
            helper.assertTrue(sewageInSoil(level, at, Pollutant.SEWAGE) == 0.0, "and the stretch that was already counted is not counted again");

            state.lastExcretion = now - DAY;
            player.setHealth(0f);
            HygieneEvents.excrete(player);
            helper.assertTrue(sewageInSoil(level, at, Pollutant.SEWAGE) == 0.0, "A player on the death screen leaves nothing");
            player.setHealth(20f);

            state.lastExcretion = now - DAY / 2;
            HygieneEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertTrue(Math.abs(sewageInSoil(level, at, Pollutant.SEWAGE) - 0.75) < 0.01, "Logging out puts down the waste since the last mark: 0.75 L, got " + sewageInSoil(level, at, Pollutant.SEWAGE));
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    // --- Water ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void waterNearAFullCesspitCarriesItsSewageAndADrinkOfItInfects(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 2);
        final BlockPos at = spot(level, chunk);
        try
        {
            final CesspitBlockEntity pit = pit(level, at);
            pit.set(new CesspitMath.State(1000.0, 1000.0, 1000.0, 0.0), Pollution.now()); // Full, and all of it from people shedding Shigella
            final BlockPos near = at.east(3), high = at.east(3).above(3);
            level.setBlock(near, Blocks.WATER.defaultBlockState(), 2);
            level.setBlock(high, Blocks.WATER.defaultBlockState(), 2);

            final WaterQuality quality = WaterSource.at(level, near);
            final double expected = 1000.0 * 1e-6 / (1.0 + 3.0 / 5.0);
            helper.assertTrue(Math.abs(quality.faecal - expected) < expected * 0.012, "A well 3 blocks away (rounded to the step of the source) has " + expected + " L of sewage in a litre, got " + quality.faecal);
            helper.assertTrue(Math.abs(quality.shedDysentery - expected) < expected * 0.012, "all of it from shedders");
            helper.assertTrue(WaterSource.at(level, high).faecal == 0.0, "Water more than 2 blocks above the base of the pit is not reached by it");

            final FakePlayer player = player(level, at.above());
            final DiseaseState state = player.getData(Hygiene.DISEASES);
            int drinks = 0;
            while (state.course(Disease.DYSENTERY) == null && drinks < 80)
            {
                Drinking.fromWorld(player, level, near);
                drinks++;
            }
            helper.assertTrue(state.course(Disease.DYSENTERY) != null, "Drinking there infects, in " + drinks + " drinks (about one in three each)");
            for (int i = 0; i < 200; i++)
            {
                Drinking.fromWorld(player, level, high);
            }
            helper.assertTrue(state.course(Disease.TYPHOID) == null, "and the clean water caused no typhoid");
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "tfcpp_hygiene_boil")
    public static void aPotHeldAtTheBoilForAMinuteOfCalendarClearsThePathogensAndKeepsTheLead(GameTestHelper helper) throws Exception
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 3);
        final BlockPos at = spot(level, chunk);
        try
        {
            level.setBlock(at, TFCBlocks.POT.get().defaultBlockState(), 3);
            final PotBlockEntity pot = (PotBlockEntity) level.getBlockEntity(at);
            final Field temperature = AbstractFirepitBlockEntity.class.getDeclaredField("temperature");
            temperature.setAccessible(true);
            temperature.setFloat(pot, 110f);
            final Method cook = PotBlockEntity.class.getDeclaredMethod("handleCooking");
            cook.setAccessible(true);
            final IFluidHandler tank = pot.getInventory().getFluidHandler();
            final FluidStack water = FluidQuality.with(new FluidStack(Fluids.WATER, 500), dirty(1e-2, 30.0));
            helper.assertTrue(tank.fill(water, IFluidHandler.FluidAction.EXECUTE) == 500, "The pot takes the water");
            for (int tick = 0; tick < 5000; tick++)
            {
                cook.invoke(pot);
            }
            helper.assertTrue(FluidQuality.get(tank.getFluidInTank(0)).faecal > 0.0, "A fire that stands still on an empty server does not boil the water: the calendar has not moved");
            try (CalendarTransaction calendar = Calendars.SERVER.transaction())
            {
                for (int tick = 0; tick < 1190; tick++)
                {
                    calendar.add(1L);
                    cook.invoke(pot);
                }
                helper.assertTrue(FluidQuality.get(tank.getFluidInTank(0)).faecal > 0.0, "Not yet boiled after 59 seconds");
                for (int tick = 0; tick < 20; tick++)
                {
                    calendar.add(1L);
                    cook.invoke(pot);
                }
                final WaterQuality boiled = FluidQuality.get(tank.getFluidInTank(0));
                helper.assertTrue(boiled.faecal == 0.0 && !boiled.hasPathogens(), "A minute at the boil kills what lives in the sewage");
                helper.assertTrue(boiled.metal(LEAD) > 20.0, "and leaves the lead, got " + boiled.metal(LEAD));

                tank.fill(FluidQuality.with(new FluidStack(Fluids.WATER, 100), dirty(1e-2, 0.0)), IFluidHandler.FluidAction.EXECUTE);
                calendar.add(1L);
                cook.invoke(pot);
                helper.assertTrue(FluidQuality.get(tank.getFluidInTank(0)).hasPathogens(), "Water poured in afterwards has to boil for its own minute");
                temperature.setFloat(pot, 90f);
                for (int tick = 0; tick < 2000; tick++)
                {
                    calendar.add(1L);
                    cook.invoke(pot);
                }
                helper.assertTrue(FluidQuality.get(tank.getFluidInTank(0)).hasPathogens(), "and below the boil nothing is killed");
            }
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void aSandFilterPassesTwoHundredLitresAnHourAndClearsNinetyNinePercentOnceRipe(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 4);
        final BlockPos at = spot(level, chunk);
        try
        {
            level.setBlock(at, Hygiene.SAND_FILTER.get().defaultBlockState(), 3);
            final SandFilterBlockEntity filter = (SandFilterBlockEntity) level.getBlockEntity(at);
            final IFluidHandler top = filter.handler(Direction.UP), side = filter.handler(Direction.NORTH);
            final FluidStack foul = FluidQuality.with(new FluidStack(Fluids.WATER, 1000), dirty(1e-2, 100.0));
            final double in = FluidQuality.get(foul).faecal;
            helper.assertTrue(top.fill(foul, IFluidHandler.FluidAction.EXECUTE) == 1000, "The top takes 1000 L");
            helper.assertTrue(side.fill(foul, IFluidHandler.FluidAction.EXECUTE) == 0, "and a side takes none");
            helper.assertTrue(side.getFluidInTank(0).isEmpty(), "Nothing has come through yet");

            filter.backdate(1000L); // One hour of the calendar
            final FluidStack first = side.getFluidInTank(0);
            helper.assertTrue(Math.abs(first.getAmount() - 200) <= 1, "An hour passes 200 L, got " + first.getAmount());
            final double young = FluidQuality.get(first).faecal / in;
            helper.assertTrue(young > 0.5 / 1.2 && young < 0.5 * 1.2, "A new filter takes out half, got " + young);
            helper.assertTrue(top.getFluidInTank(0).getAmount() <= 801, "and the input has lost as much");
            helper.assertTrue(FluidQuality.get(first).metal(LEAD) > 60.0, "A new filter holds back some of the lead");

            // A ripe one
            level.setBlock(at.east(2), Hygiene.SAND_FILTER.get().defaultBlockState(), 3);
            final SandFilterBlockEntity ripe = (SandFilterBlockEntity) level.getBlockEntity(at.east(2));
            ripe.setRipeDays(3.0);
            ripe.handler(Direction.UP).fill(foul, IFluidHandler.FluidAction.EXECUTE);
            ripe.backdate(1000L);
            final FluidStack clean = ripe.handler(Direction.EAST).getFluidInTank(0);
            final double kept = FluidQuality.get(clean).faecal / in;
            helper.assertTrue(Math.abs(clean.getAmount() - 200) <= 1, "A ripe filter passes the same 200 L");
            helper.assertTrue(kept > 0.01 / 1.2 && kept < 0.01 * 1.2, "and takes out 99 %, got " + (1.0 - kept));
            final FluidStack drawn = ripe.handler(Direction.EAST).drain(100, IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(drawn.getAmount() == 100 && FluidQuality.get(drawn).faecal > 0.0, "Water is drawn from a side");
            helper.assertTrue(ripe.handler(Direction.EAST).getFluidInTank(0).getAmount() <= 101, "and the side has less");
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void tanksAndJugsMixTheQualityOfTheWaterPouredIntoThem(GameTestHelper helper)
    {
        final FluidTank tank = new FluidTank(1000);
        final FluidStack clean = FluidQuality.with(new FluidStack(Fluids.WATER, 500), WaterQuality.CLEAN);
        final FluidStack foul = FluidQuality.with(new FluidStack(Fluids.WATER, 500), dirty(1e-2, 0.0));
        final double fouled = FluidQuality.get(foul).faecal;
        helper.assertTrue(tank.fill(clean, IFluidHandler.FluidAction.EXECUTE) == 500, "Clean water goes into a tank");
        helper.assertTrue(tank.fill(foul, IFluidHandler.FluidAction.SIMULATE) == 500, "and dirty water is not refused as another fluid");
        helper.assertTrue(tank.fill(foul, IFluidHandler.FluidAction.EXECUTE) == 500, "it goes in");
        final double mixed = FluidQuality.get(tank.getFluid()).faecal;
        helper.assertTrue(tank.getFluidAmount() == 1000 && Math.abs(mixed - fouled * 0.5) < fouled * 1e-9, "and the tank holds half the sewage, exactly, " + mixed + " of " + fouled);
        // Many small pours of clean water do not round the sewage away: 20 pours of 10 mB into a tank of 800 mB of foul water
        final FluidTank diluted = new FluidTank(2000);
        diluted.fill(foul.copyWithAmount(500), IFluidHandler.FluidAction.EXECUTE);
        for (int pour = 0; pour < 100; pour++)
        {
            diluted.fill(clean.copyWithAmount(10), IFluidHandler.FluidAction.EXECUTE);
        }
        helper.assertTrue(diluted.getFluidAmount() == 1500 && Math.abs(FluidQuality.get(diluted.getFluid()).faecal * 1500.0 - fouled * 500.0) < fouled * 500.0 * 1e-9, "and a hundred small pours into foul water conserve what the water carries");

        final ItemStack jug = new ItemStack(TFCItems.JUG.asItem());
        final IFluidHandlerItem handler = jug.getCapability(Capabilities.FluidHandler.ITEM);
        helper.assertTrue(handler.fill(clean.copyWithAmount(40), IFluidHandler.FluidAction.EXECUTE) == 40, "A jug takes clean water");
        helper.assertTrue(handler.fill(foul.copyWithAmount(40), IFluidHandler.FluidAction.EXECUTE) == 40, "and dirty water on top of it");
        final double inJug = FluidQuality.get(handler.getFluidInTank(0)).faecal;
        helper.assertTrue(Math.abs(inJug - fouled * 0.5) < fouled * 1e-9, "mixed by volume, got " + inJug);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "tfcpp_hygiene_lake")
    public static void waterTakenFromAPollutedLakeCarriesItsQualityIntoAJugAndDrinkingAppliesTheLead(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 5);
        final BlockPos at = spot(level, chunk);
        final LevelWaters waters = LevelWaters.get(level);
        waters.reset();
        waters.setLakeDetector((l, c) -> true);
        try
        {
            final BlockPos water = at;
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
            final double[] load = new double[Pollutant.COUNT];
            load[Pollutant.LEAD.ordinal()] = 1e5;
            load[Pollutant.SEWAGE.ordinal()] = 200.0;
            waters.discharge(level, water, Waters.Kind.STILL, load, false);
            final WaterBody body = Waters.at(level, water);
            helper.assertTrue(body != null && body.microgramsPerLitre(Pollutant.LEAD) > 1000.0, "The lake holds lead, " + (body == null ? "no body" : body.microgramsPerLitre(Pollutant.LEAD)));

            final FluidStack picked = FluidHelpers.pickupFluid(level, water, level.getBlockState(water), IFluidHandler.FluidAction.SIMULATE);
            helper.assertTrue(picked != null && picked.getFluid() == Fluids.WATER, "A container picks water from the lake");
            final WaterQuality quality = FluidQuality.get(picked);
            helper.assertTrue(quality.metal(LEAD) > 800.0 && quality.metal(LEAD) < 1700.0, "and the water carries the lead of the lake, got " + quality.metal(LEAD));
            helper.assertTrue(quality.faecal > 1e-7, "and its sewage, got " + quality.faecal);

            final ItemStack jug = new ItemStack(TFCItems.JUG.asItem());
            final IFluidHandlerItem handler = jug.getCapability(Capabilities.FluidHandler.ITEM);
            helper.assertTrue(handler.fill(picked.copyWithAmount(100), IFluidHandler.FluidAction.EXECUTE) == 100, "A jug is filled");
            helper.assertTrue(FluidQuality.get(handler.getFluidInTank(0)).metal(LEAD) > 800.0, "and keeps the quality");

            final FakePlayer player = player(level, at.above());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, jug);
            final BreathingState body2 = player.getData(GasAttachments.BREATHING);
            final float before = body2.leadMg;
            jug.getItem().finishUsingItem(jug, level, player);
            helper.assertTrue(body2.leadMg > before + 0.02f, "Drinking the jug puts lead in the body: " + (body2.leadMg - before) + " mg");

            // Water that is taken from the lake is taken out of it: the lead and the sewage a pump carries away are no longer in the river
            final WaterBody lake = Waters.at(level, water);
            final double leadIn = lake.inWater()[Pollutant.LEAD.ordinal()], sewageIn = lake.inWater()[Pollutant.SEWAGE.ordinal()];
            final WaterQuality drawn = WaterSource.take(level, water, 1000.0);
            final WaterBody lakeAfter = Waters.at(level, water);
            final double leadOut = lakeAfter.inWater()[Pollutant.LEAD.ordinal()], sewageOut = lakeAfter.inWater()[Pollutant.SEWAGE.ordinal()];
            helper.assertTrue(drawn.metal(LEAD) > 0.0 && Math.abs((leadIn - leadOut) - drawn.metal(LEAD) * 1000.0 / 1e6) < 1e-9 * leadIn, "A thousand litres take their lead out of the lake: " + (leadIn - leadOut) + " g against " + drawn.metal(LEAD) * 1e-3);
            helper.assertTrue(drawn.faecal > 0.0 && Math.abs((sewageIn - sewageOut) - drawn.faecal * 1000.0) < 1e-9 * sewageIn, "and their sewage: " + (sewageIn - sewageOut) + " L against " + drawn.faecal * 1000.0);
            helper.assertTrue(WaterSource.at(level, water).metal(LEAD) == WaterSource.at(level, water).metal(LEAD) && Waters.at(level, water).inWater()[Pollutant.LEAD.ordinal()] == leadOut, "Looking does not take");
            final FluidStack looked = FluidHelpers.pickupFluid(level, water, level.getBlockState(water), IFluidHandler.FluidAction.SIMULATE);
            helper.assertTrue(looked != null && Math.abs(Waters.at(level, water).inWater()[Pollutant.LEAD.ordinal()] - leadOut) == 0.0, "and a simulated pickup takes nothing");
            helper.succeed();
        }
        finally
        {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            waters.setLakeDetector(null);
            waters.reset();
            release(level, chunk, at);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "tfcpp_hygiene_lake2")
    public static void aSheddingPersonsWasteInTheWaterMakesItInfectious(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 6);
        final BlockPos at = spot(level, chunk);
        final LevelWaters waters = LevelWaters.get(level);
        waters.reset();
        waters.setLakeDetector((l, c) -> true);
        try
        {
            level.setBlock(at, Blocks.WATER.defaultBlockState(), 2);
            final FakePlayer player = player(level, at.above());
            final DiseaseState state = player.getData(Hygiene.DISEASES);
            state.infect(Disease.TYPHOID, Pollution.now() - 15 * DAY, new SplittableRandom(3));
            Pollution.seed(level, at.east(4), SoilOrder.PODZOL, false);
            state.lastExcretion = Pollution.now() - DAY;
            HygieneEvents.excrete(player);
            helper.assertTrue(sewageInSoil(level, at, Pollutant.SHED_TYPHOID) > 1.4, "On the ground a shedder's waste is marked in the soil, got " + sewageInSoil(level, at, Pollutant.SHED_TYPHOID));

            Sewage.deposit(level, at, 1.5, 0.0, 1.5, true, true); // The same person in the water
            final WaterQuality quality = WaterSource.at(level, at);
            helper.assertTrue(quality.shedTyphoid > 0.0 && quality.faecal > 0.0, "The lake carries the shedder's waste, " + quality);
            final double chance = Organisms.infectionProbability(Disease.TYPHOID, quality, 0.5);
            helper.assertTrue(chance > 0.0, "and a drink of it has a chance of typhoid, " + chance);
            helper.succeed();
        }
        finally
        {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            waters.setLakeDetector(null);
            waters.reset();
            release(level, chunk, at);
        }
    }

    // --- Disease -------------------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void anInfectionShowsSymptomsOnlyAfterTheIncubationAndDysenteryDrainsThirst(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 7);
        final BlockPos at = spot(level, chunk);
        try
        {
            final FakePlayer player = player(level, at);
            final DiseaseState state = player.getData(Hygiene.DISEASES);
            final long now = Pollution.now();
            state.setCourse(Disease.DYSENTERY, new DiseaseState.Course(Disease.DYSENTERY, now - DAY, now + DAY, now + 7 * DAY, now + 7 * DAY, false));
            HygieneEvents.symptoms(player);
            helper.assertTrue(!player.hasEffect(Hygiene.DIARRHOEA_EFFECT), "Nothing is felt while the infection incubates");

            state.setCourse(Disease.DYSENTERY, new DiseaseState.Course(Disease.DYSENTERY, now - 3 * DAY, now - 10, now + 5 * DAY, now + 5 * DAY, false));
            IPlayerInfo.get(player).setThirst(100f);
            state.lastSymptoms = now - 2400L;
            HygieneEvents.symptoms(player);
            helper.assertTrue(player.hasEffect(Hygiene.DIARRHOEA_EFFECT) && player.hasEffect(Hygiene.ILLNESS_WEAKNESS_EFFECT), "Then diarrhoea and weakness");
            helper.assertTrue(Math.abs(IPlayerInfo.get(player).getThirst() - 80f) < 0.5f, "which drain 200 points of thirst a day: 20 in 2400 ticks, got " + IPlayerInfo.get(player).getThirst());
            final MobEffectInstance held = player.getEffect(Hygiene.DIARRHOEA_EFFECT);
            state.lastSymptoms = now;
            HygieneEvents.symptoms(player);
            helper.assertTrue(player.getEffect(Hygiene.DIARRHOEA_EFFECT) == held, "An effect that has time left is not given again every second");
            state.lastSymptoms = 0L;
            IPlayerInfo.get(player).setThirst(100f);
            HygieneEvents.symptoms(player);
            helper.assertTrue(IPlayerInfo.get(player).getThirst() > 99.9f, "Symptoms never applied before take no thirst for the age of the world, got " + IPlayerInfo.get(player).getThirst());

            state.setCourse(Disease.DYSENTERY, null);
            state.setCourse(Disease.TYPHOID, new DiseaseState.Course(Disease.TYPHOID, now - 15 * DAY, now - DAY, now + 20 * DAY, now + 34 * DAY, false));
            HygieneEvents.symptoms(player);
            helper.assertTrue(player.hasEffect(Hygiene.FEVER_EFFECT), "Typhoid gives a fever");
            helper.assertTrue(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(Hygiene.FEVER).isPresent(), "and a death of a fever exists");
            for (var effect : Hygiene.symptoms())
            {
                helper.assertTrue(!effect.getKey().location().getPath().contains("typhoid") && !effect.getKey().location().getPath().contains("dysentery"), "No effect names the disease");
            }
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    // --- Night soil ----------------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void nightSoilCarriesItsLoadIntoTheFieldAndIsAFertiliser(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 8);
        final BlockPos at = spot(level, chunk);
        try
        {
            Pollution.seed(level, at, SoilOrder.PODZOL, false);
            final CesspitBlockEntity pit = pit(level, at);
            pit.set(new CesspitMath.State(1000.0, 800.0, 100.0, 0.0), Pollution.now());
            final List<ItemStack> pieces = new ArrayList<>();
            helper.assertTrue(pit.takePieces(level, 16, pieces::add) == 16, "A use of the lid takes 16 pieces");
            helper.assertTrue(Math.abs(pit.state().volume() - 680.0) < 1e-6, "320 L less in the pit, got " + pit.state().volume());
            helper.assertTrue(Math.abs(pit.state().active() - 544.0) < 1e-6, "and the infective part in proportion");
            final ItemStack stack = pieces.get(0);
            final double[] load = NightSoil.load(stack);
            helper.assertTrue(load[0] > 13.0 && load[0] < 19.5, "A piece carries the 16 L of infective waste of its 20, got " + load[0]);
            helper.assertTrue(Fertilizer.get(stack) != null && Fertilizer.get(stack).getNutrient(net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType.NITROGEN) > 0.09f, "Night soil is a fertiliser of TFC");

            NightSoil.spread(level, at, stack, 16);
            helper.assertTrue(Math.abs(sewageInSoil(level, at, Pollutant.SEWAGE) - load[0] * 16) < 0.04 * load[0] * 16, "Spread on a field it puts its sewage into the soil, less what died in the day it was kept (T90 of 60 days), got " + sewageInSoil(level, at, Pollutant.SEWAGE));
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    // --- The end of typhoid ----------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void aFeverThatEndsADoomedCourseKillsThroughResistanceAndWaitsOutAnInvulnerablePlayer(GameTestHelper helper) throws Exception
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 10);
        final BlockPos at = spot(level, chunk);
        try
        {
            final FakePlayer player = new Mortal(level);
            player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            level.addFreshEntity(player);
            final DiseaseState state = player.getData(Hygiene.DISEASES);
            final long now = Pollution.now();
            final long yearTicks = Math.round(tfcpp.ecology.SoilConstants.daysPerYear() * DAY);
            state.setCourse(Disease.TYPHOID, new DiseaseState.Course(Disease.TYPHOID, now - 40 * DAY, now - 20 * DAY, now - 10L, now + 14 * DAY, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1000, 4));
            // A player who has just come in is invulnerable for 60 ticks (a fake player is not ticked, so it stays so): the blow is turned aside
            final Field spawnProtection = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawnProtection.setAccessible(true);
            helper.assertTrue(spawnProtection.getInt(player) > 0, "The new player is protected");
            HygieneEvents.symptoms(player);
            helper.assertTrue(player.isAlive() && state.course(Disease.TYPHOID) != null && !state.course(Disease.TYPHOID).resolved, "The blow that was turned aside leaves the course open");
            helper.assertTrue(state.immuneUntil(Disease.TYPHOID) == 0L, "with no immunity");
            spawnProtection.setInt(player, 0);
            HygieneEvents.symptoms(player);
            helper.assertTrue(player.isDeadOrDying(), "At the next step the fever kills, though Resistance V is on: health " + player.getHealth() + " alive " + player.isAlive() + " course " + state.course(Disease.TYPHOID) + " resolved " + (state.course(Disease.TYPHOID) == null ? "-" : state.course(Disease.TYPHOID).resolved) + " effect " + player.hasEffect(MobEffects.DAMAGE_RESISTANCE));
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    // --- Devices ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "tfcpp_hygiene")
    public static void aBlockPlacedOnTheLidDoesNotEmptyThePitButAnEmptyHandDoesAndBreakingItGivesTheSamePiecesAndTheRestToTheSoil(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 11);
        final BlockPos at = spot(level, chunk);
        try
        {
            Pollution.seed(level, at, SoilOrder.PODZOL, false);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS).set(true, level.getServer());
            final CesspitBlockEntity pit = pit(level, at);
            pit.set(new CesspitMath.State(990.0, 900.0, 0.0, 0.0), Pollution.now());
            final FakePlayer player = player(level, at.above(3));
            final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at).add(0.0, 0.5, 0.0), Direction.UP, at, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE));
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(Math.abs(pit.state().volume() - 990.0) < 1e-6, "A block placed on the lid takes nothing from the pit, got " + pit.state().volume());
            helper.assertTrue(player.getInventory().countItem(Hygiene.NIGHT_SOIL.get()) == 0, "and no night soil comes");
            level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 3);

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(player.getInventory().countItem(Hygiene.NIGHT_SOIL.get()) == 16, "An empty hand takes 16 pieces, got " + player.getInventory().countItem(Hygiene.NIGHT_SOIL.get()));

            // Break it: whole pieces drop, each with its share, and the infective part of what is under one piece goes into the soil
            final double activeBefore = pit.state().active(), volumeBefore = pit.state().volume();
            final int pieces = (int) Math.floor(volumeBefore / 20.0);
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            // A forced chunk shows its entities a tick or two after it was forced
            helper.runAfterDelay(10, () -> {
                try
                {
                    final List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3.0));
                    int dropped = 0;
                    double carried = 0.0;
                    for (ItemEntity drop : drops)
                    {
                        if (drop.getItem().is(Hygiene.NIGHT_SOIL.get()))
                        {
                            if (drop.getAge() < 100) // Those of an earlier run of a kept world are older, and are cleared away
                            {
                                dropped += drop.getItem().getCount();
                                carried += NightSoil.load(drop.getItem())[0] * drop.getItem().getCount();
                            }
                            drop.discard();
                        }
                    }
                    helper.assertTrue(dropped == pieces, "Breaking the pit drops " + pieces + " whole pieces, got " + dropped);
                    final double remainder = activeBefore * (volumeBefore - pieces * 20.0) / volumeBefore;
                    helper.assertTrue(Math.abs(sewageInSoil(level, at, Pollutant.SEWAGE) - remainder) < 0.05 * Math.max(remainder, 1.0), "and the infective part of the remainder goes into the soil: " + remainder + ", got " + sewageInSoil(level, at, Pollutant.SEWAGE));
                    helper.assertTrue(carried > 0.9 * activeBefore * pieces * 20.0 / volumeBefore, "Each piece carries its share, got " + carried);
                    helper.succeed();
                }
                finally
                {
                    release(level, chunk, at);
                }
            });
        }
        catch (RuntimeException failure)
        {
            release(level, chunk, at);
            throw failure;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void waterTakenFromAPondOrNearAPitIsTakenFromTheSoilAndThePitAndNeverMoreThanTheyHold(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 12);
        final BlockPos at = spot(level, chunk);
        try
        {
            Pollution.seed(level, at, SoilOrder.PODZOL, false);
            Pollution.deposit(level, at, Pollutant.SEWAGE, 1000.0);
            level.setBlock(at.east(4), Blocks.WATER.defaultBlockState(), 2);
            final double before = sewageInSoil(level, at, Pollutant.SEWAGE);
            final WaterQuality first = WaterSource.take(level, at.east(4), 1000.0);
            final double after = sewageInSoil(level, at, Pollutant.SEWAGE);
            helper.assertTrue(first.faecal > 0.0 && after < before, "Water drawn from a pond takes its sewage out of the soil, " + before + " to " + after);
            helper.assertTrue(Math.abs((before - after) - first.faecal * 1000.0) < 1e-9 * before, "exactly what it carries, " + (before - after) + " against " + first.faecal * 1000.0);
            double carried = first.faecal * 1000.0;
            for (int draw = 0; draw < 5; draw++)
            {
                carried += WaterSource.take(level, at.east(4), 1e9).faecal * 1e9;
            }
            helper.assertTrue(carried < 1000.0 * (1.0 + 1e-9), "Taking again and again cannot carry away more than the soil held: " + carried);
            helper.assertTrue(sewageInSoil(level, at, Pollutant.SEWAGE) < 1e-6, "The soil is left with none");
            helper.assertTrue(WaterSource.at(level, at.east(4)).faecal == 0.0, "and so the water is clean");

            final CesspitBlockEntity pit = pit(level, at.west(3));
            pit.set(new CesspitMath.State(1000.0, 1000.0, 0.0, 0.0), Pollution.now());
            level.setBlock(at.west(6), Blocks.WATER.defaultBlockState(), 2);
            final double seen = WaterSource.at(level, at.west(6)).faecal;
            helper.assertTrue(seen > 0.0 && Math.abs(pit.state().active() - 1000.0) < 1e-9, "Looking at the water takes nothing from the pit");
            final double litresDrawn = 1000.0;
            final WaterQuality pitWater = WaterSource.take(level, at.west(6), litresDrawn);
            helper.assertTrue(Math.abs((1000.0 - pit.state().active()) - pitWater.faecal * litresDrawn) < 1e-9, "What the pit loses is exactly what the water carries, " + (1000.0 - pit.state().active()) + " against " + pitWater.faecal * litresDrawn);
            WaterSource.take(level, at.west(6), 1e9);
            helper.assertTrue(pit.state().active() < 1e-9, "Water drawn near the pit takes its infective contents, and no more than there were, " + pit.state().active());
            helper.assertTrue(pit.state().volume() > 999.0, "the waste stays");
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void theSandFilterTakesWaterOnlyAtTheTopGivesItOnlyAtTheSidesKeepsWhatItDoesNotMoveAndKeepsTheMetalsUntilBroken(GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final LevelChunk chunk = chunk(level, 13);
        final BlockPos at = spot(level, chunk);
        try
        {
            Pollution.seed(level, at, SoilOrder.PODZOL, false);
            level.setBlock(at, Hygiene.SAND_FILTER.get().defaultBlockState(), 3);
            final SandFilterBlockEntity filter = (SandFilterBlockEntity) level.getBlockEntity(at);
            final IFluidHandler top = filter.handler(Direction.UP), side = filter.handler(Direction.NORTH), any = filter.handler(null);
            final FluidStack foul = FluidQuality.with(new FluidStack(Fluids.WATER, 1000), dirty(1e-2, 100.0));
            helper.assertTrue(any.fill(foul, IFluidHandler.FluidAction.EXECUTE) == 1000, "A query with no side fills the input");
            helper.assertTrue(top.drain(500, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "The top gives nothing: a jug cannot drain the unfiltered water");
            helper.assertTrue(any.drain(500, IFluidHandler.FluidAction.SIMULATE).isEmpty() && filter.inputFluid().getAmount() == 1000, "and a query with no side drains the output, which is empty, and not the input");

            // The output holds another kind of fresh water: nothing moves, and the sand does not ripen on water that did not pass
            filter.setOutput(new FluidStack(TFCFluids.RIVER_WATER.get(), 500));
            filter.backdate(1000L);
            filter.catchUp();
            helper.assertTrue(filter.inputFluid().getAmount() == 1000, "Water that the output does not accept stays in the input, got " + filter.inputFluid().getAmount());
            helper.assertTrue(filter.ripeDays() == 0.0, "and the filter has not ripened");

            // Metals are kept in the sand
            filter.setOutput(FluidStack.EMPTY);
            filter.backdate(1000L);
            filter.catchUp();
            final double kept = filter.retained(LEAD);
            helper.assertTrue(kept > 0.0 && Math.abs(kept - (100.0 - FluidQuality.get(side.getFluidInTank(0)).metal(LEAD)) * side.getFluidInTank(0).getAmount()) < kept * 0.02, "The lead that the sand took is kept, " + kept + " ug");

            final double soilLeadBefore = sewageInSoil(level, at, Pollutant.LEAD);
            final int inInput = filter.inputFluid().getAmount();
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(sewageInSoil(level, at, Pollutant.LEAD) > soilLeadBefore + kept / 1e6 * 0.99, "Breaking the filter puts the lead it held, and that of the water in it, into the soil");
            helper.assertTrue(inInput > 0 && sewageInSoil(level, at, Pollutant.SEWAGE) > 0.0, "and the sewage of the water in the tanks");
            helper.succeed();
        }
        finally
        {
            release(level, chunk, at);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "tfcpp_hygiene")
    public static void theMiningTagsListTheNewBlocks(GameTestHelper helper)
    {
        helper.assertTrue(Hygiene.CESSPIT.get().defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE), "The cesspit is mined with an axe");
        helper.assertTrue(Hygiene.SAND_FILTER.get().defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), "and the sand filter with a pickaxe");
        helper.succeed();
    }
}
