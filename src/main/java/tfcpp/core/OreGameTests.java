package tfcpp.core;

import java.util.List;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import tfcpp.geology.NewOre;
import tfcpp.geology.Ores;
import tfcpp.metallurgy.MeltContext;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasTracker;
import tfcpp.pneumatics.gas.LevelGasVolume;

/** Checks that each new ore works as TFC's own do. What worldgen really makes shows only in a real world: see {@link tfcpp.geology.OreSurvey}. */
@GameTestHolder(TFCpp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreGameTests
{
    private static final String TEMPLATE = "empty9";

    private static Block block(NewOre ore, String kind, Rock rock)
    {
        return Ores.BLOCKS.get(NewOre.blockId(kind, rock)).get();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void everyOreBlockBreaksToItsPieceAndCanBeProspected(GameTestHelper helper)
    {
        final BlockPos pos = new BlockPos(4, 1, 4);
        final BlockPos absolute = helper.absolutePos(pos);
        for (NewOre ore : NewOre.values())
        {
            for (String kind : ore.kinds())
            {
                for (Rock rock : ore.hosts())
                {
                    final BlockState state = block(ore, kind, rock).defaultBlockState();
                    helper.assertTrue(state.is(TFCTags.Blocks.PROSPECTABLE), kind + " in " + rock + " should show on a prospector's pick");
                    helper.assertTrue(state.is(TFCTags.Blocks.CAN_COLLAPSE), kind + " in " + rock + " should be able to collapse");
                    helper.assertTrue(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), kind + " in " + rock + " should need a pickaxe");

                    final List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), absolute, null);
                    helper.assertTrue(drops.size() == 1 && drops.getFirst().is(Ores.PIECES.get(kind).get()), kind + " in " + rock + " should drop its piece, got " + drops);
                }
            }
            if (ore.graded())
            {
                final List<ItemStack> drops = Block.getDrops(Ores.SMALL_BLOCKS.get(ore).get().defaultBlockState(), helper.getLevel(), absolute, null);
                helper.assertTrue(drops.size() == 1 && drops.getFirst().is(Ores.SMALL_ITEMS.get(ore).get()), "A small piece should pick up as itself, got " + drops);
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyVeinLoadsAndOnlyUsesRealRocks(GameTestHelper helper)
    {
        final var configured = helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        final var placed = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (String vein : List.of("galena_carbonate", "galena_vein", "bauxite", "chromite", "ilmenite", "pyrolusite", "apatite", "barite"))
        {
            final ResourceLocation id = TFCpp.id("vein/" + vein);
            helper.assertTrue(configured.containsKey(id), vein + " should load as a feature");
            helper.assertTrue(placed.containsKey(id), vein + " should load as a placed feature");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void collapseAndHeatDataLoad(GameTestHelper helper)
    {
        final var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.byKey(TFCpp.id("collapse/ore/rich_galena/limestone")).isPresent(), "Rich galena should collapse to normal");
        helper.assertTrue(recipes.byKey(TFCpp.id("collapse/cobble_limestone")).isPresent(), "Poor ore should collapse to rubble");
        helper.assertTrue(recipes.byKey(TFCpp.id("quern/apatite")).isPresent(), "Apatite should grind");
        for (var piece : Ores.PIECES.values())
        {
            helper.assertTrue(net.dries007.tfc.common.component.heat.HeatCapability.get(new ItemStack(piece.get())) != null, piece.get() + " should hold heat");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void galenaSmeltsToLeadAndGivesOffSulfurAndLead(GameTestHelper helper)
    {
        final ItemStack ore = new ItemStack(Ores.PIECES.get("normal_galena").get());
        final @Nullable HeatingRecipe recipe = HeatingRecipe.getRecipe(ore);
        helper.assertTrue(recipe != null, "Galena should smelt");
        helper.assertTrue(recipe.getTemperature() == 800f, "Galena smelts at 800 C, got " + recipe.getTemperature());
        final FluidStack melted = recipe.assembleFluid(ore);
        helper.assertTrue(melted.getFluid() == Metallurgy.LEAD.get() && melted.getAmount() == 25, "Normal galena should give 25 mB of lead, got " + melted);

        final FluidHeat lead = FluidHeat.get(Metallurgy.LEAD.get());
        helper.assertTrue(lead != null && lead.meltTemperature() == 327f, "Lead should melt at 327 C");
        helper.assertTrue(HeatingRecipe.getRecipe(new ItemStack(Metallurgy.LEAD_INGOT.get())) != null, "A lead ingot should melt");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(TFCpp.id("casting/lead_ingot")).isPresent(), "Lead should cast");

        final BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        MeltContext.enter(helper.getLevel(), pos);
        recipe.assembleItem(ore);
        MeltContext.exit();

        final LevelGasVolume volume = GasTracker.get(helper.getLevel()).volume(helper.getLevel());
        float sulfurDioxide = 0f, fume = 0f;
        for (int y = 0; y < 48; y++)
        {
            final @Nullable float[] cell = volume.cell(pos.getX(), pos.getY() + y, pos.getZ());
            if (cell != null)
            {
                sulfurDioxide += cell[Gas.SULFUR_DIOXIDE.ordinal()];
                fume += cell[Gas.LEAD_OXIDE_FUME.ordinal()];
            }
        }
        final float expected = 25f * 11.34f / 207.2f; // Moles of lead, and one of sulfur with each
        helper.assertTrue(Math.abs(sulfurDioxide - expected) < 0.01f, "Galena should give " + expected + " mol of SO2, got " + sulfurDioxide);
        helper.assertTrue(fume > 0f, "Smelting lead in the open should fill the air with its fume");
        helper.succeed();
    }

    private OreGameTests() {}
}
