package tfcpp.chemistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import tfcpp.core.TFCpp;

/** The models and block states of coal chemistry. */
public final class ChemistryAssets
{
    public static final class Models extends ItemModelProvider
    {
        public Models(PackOutput output, ExistingFileHelper helper)
        {
            super(output, TFCpp.MOD_ID, helper);
        }

        @Override
        protected void registerModels()
        {
            flatItem(Chemistry.COKE.get());
            flatItem(Chemistry.LIGNITE_CHAR.get());
            flatItem(Chemistry.BOROSILICATE_GLASS_BATCH.get());
            flatItem(Chemistry.GLASS_CARBOY.get());
            flatItem(Chemistry.POTASSIUM_BISULFATE.get());
            flatItem(Chemistry.SODIUM_BISULFATE.get());
            flatItem(Chemistry.SUPERPHOSPHATE.get());
            flatItem(Chemistry.AMMONIUM_SULFATE.get());
            flatItem(Chemistry.MARSH_APPARATUS.get());
        }

        /** The name has to differ from that of the other providers of models, or the data run refuses both. */
        @Override
        public String getName()
        {
            return "Chemistry item models";
        }

        private void flatItem(Item item)
        {
            final String path = BuiltInRegistries.ITEM.getKey(item).getPath();
            getBuilder("item/" + path).parent(new ModelFile.UncheckedModelFile("item/generated")).texture("layer0", modLoc("item/" + path));
        }
    }

    public static final class BlockStates extends BlockStateProvider
    {
        public BlockStates(PackOutput output, ExistingFileHelper helper)
        {
            super(output, TFCpp.MOD_ID, helper);
        }

        @Override
        public String getName()
        {
            return "Chemistry block states";
        }

        @Override
        protected void registerStatesAndModels()
        {
            simpleBlockWithItem(Chemistry.COKE_OVEN.get(), models().cubeColumn("coke_oven", modLoc("block/coke_oven_side"), modLoc("block/coke_oven_top")));

            final ModelFile unlit = models().cubeBottomTop("gas_burner", modLoc("block/gas_burner_side"), modLoc("block/gas_burner_bottom"), modLoc("block/gas_burner_top"));
            final ModelFile lit = models().cubeBottomTop("gas_burner_lit", modLoc("block/gas_burner_side"), modLoc("block/gas_burner_bottom"), modLoc("block/gas_burner_top_lit"));
            getVariantBuilder(Chemistry.GAS_BURNER.get()).forAllStates(state -> ConfiguredModel.builder().modelFile(state.getValue(GasBurnerBlock.LIT) ? lit : unlit).build());
            simpleBlockItem(Chemistry.GAS_BURNER.get(), unlit);

            simpleBlockWithItem(Chemistry.LEAD_CHAMBER.get(), models().cubeAll("lead_chamber", modLoc("block/lead_chamber")));
            simpleBlockWithItem(Chemistry.SULFUR_BURNER.get(), models().cubeAll("sulfur_burner", modLoc("block/sulfur_burner")));
            simpleBlockWithItem(Chemistry.GLASS_RETORT.get(), models().cubeColumn("glass_retort", modLoc("block/glass_retort_side"), modLoc("block/glass_retort_top")));
            // One model for every depth; each state is named in the block state file, as a block state with a property needs
            final ModelFile bath = models().cubeBottomTop("sand_bath", modLoc("block/sand_bath_side"), modLoc("block/sand_bath_side"), modLoc("block/sand_bath_top"));
            getVariantBuilder(Chemistry.SAND_BATH.get()).forAllStates(state -> ConfiguredModel.builder().modelFile(bath).build());
            simpleBlockItem(Chemistry.SAND_BATH.get(), bath);
        }
    }

    private ChemistryAssets() {}
}
