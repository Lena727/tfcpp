package tfcpp.metallurgy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import tfcpp.core.TFCpp;
import tfcpp.pneumatics.instruments.DavyLampBlock;
import tfcpp.pneumatics.instruments.Instruments;

public final class MetallurgyAssets
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
            flatItem(Metallurgy.ARSENICAL_COPPER_INGOT.get());
            Metallurgy.ZINC_CALCINES.values().forEach(calcine -> flatItem(calcine.get()));
            basicItem(Metallurgy.GLASS_BULB.get());
            basicItem(Metallurgy.MERCURY_BULB.get());
            basicItem(Instruments.DAVY_LAMP_ITEM.get());
            basicItem(Instruments.WIRE_GAUZE.get());
            basicItem(Instruments.ANIMAL_CAGE.get());
            basicItem(Instruments.SENTINEL_CAGE.get());
            flatItem(Instruments.ARSENIC_TRIOXIDE.get());
            flatItem(Instruments.ANTIMONY_TRIOXIDE.get());
            flatItem(Instruments.ZINC_OXIDE.get());
        }

        /** {@link #basicItem} leaves out the item folder for a name that has a path in it, and the game then cannot find the model. */
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
        protected void registerStatesAndModels()
        {
            simpleBlockWithItem(Metallurgy.RETORT.get(), models().cubeColumn("retort", modLoc("block/retort_side"), modLoc("block/retort_top")));
            simpleBlockWithItem(Metallurgy.RETORT_CONDENSER.get(), models().cubeAll("retort_condenser", modLoc("block/retort_condenser")));
            simpleBlockWithItem(Instruments.SETTLING_BAFFLE.get(), models().cubeColumn("settling_baffle", modLoc("block/settling_baffle"), modLoc("block/retort_top")));

            horizontalBlock(Instruments.MINE_FAN.get(), models().orientable("mine_fan", modLoc("block/mine_fan_side"), modLoc("block/mine_fan_front"), modLoc("block/mine_fan_side")));
            simpleBlockItem(Instruments.MINE_FAN.get(), models().getExistingFile(modLoc("block/mine_fan")));

            // The shape of a TFC copper lamp, whose models are referenced and not copied
            getVariantBuilder(Instruments.DAVY_LAMP.get()).forAllStates(state -> {
                final String hanging = state.getValue(DavyLampBlock.HANGING) ? "_hanging" : "";
                final String lit = state.getValue(DavyLampBlock.LIT) ? "_on" : "_off";
                return ConfiguredModel.builder().modelFile(new ModelFile.UncheckedModelFile("tfc:block/metal/lamp/copper" + hanging + lit)).build();
            });
        }
    }

    private MetallurgyAssets() {}
}
