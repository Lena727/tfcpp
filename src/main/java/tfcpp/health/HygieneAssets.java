package tfcpp.health;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import tfcpp.core.TFCpp;

/** The models and block states of stage E of v1.5. */
public final class HygieneAssets
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
            getBuilder("item/night_soil").parent(new ModelFile.UncheckedModelFile("item/generated")).texture("layer0", modLoc("item/night_soil"));
        }

        /** The name has to differ from that of the other providers of models, or the data run refuses both. */
        @Override
        public String getName()
        {
            return "Hygiene item models";
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
            return "Hygiene block states";
        }

        @Override
        protected void registerStatesAndModels()
        {
            simpleBlockWithItem(Hygiene.CESSPIT.get(), models().cubeBottomTop("cesspit", modLoc("block/cesspit_side"), modLoc("block/cesspit_side"), modLoc("block/cesspit_top")));
            simpleBlockWithItem(Hygiene.SAND_FILTER.get(), models().cubeBottomTop("sand_filter", modLoc("block/sand_filter_side"), modLoc("block/sand_filter_side"), modLoc("block/sand_filter_top")));
        }
    }

    private HygieneAssets() {}
}
