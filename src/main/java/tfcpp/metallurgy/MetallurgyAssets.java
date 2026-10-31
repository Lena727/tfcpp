package tfcpp.metallurgy;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import tfcpp.core.TFCpp;

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
            basicItem(Metallurgy.ARSENICAL_COPPER_INGOT.get());
            Metallurgy.ZINC_CALCINES.values().forEach(calcine -> basicItem(calcine.get()));
            basicItem(Metallurgy.GLASS_BULB.get());
            basicItem(Metallurgy.MERCURY_BULB.get());
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
        }
    }

    private MetallurgyAssets() {}
}
