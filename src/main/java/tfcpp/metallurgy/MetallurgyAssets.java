package tfcpp.metallurgy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import tfcpp.core.TFCpp;
import tfcpp.pneumatics.instruments.DavyLampBlock;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.pneumatics.pipes.Pipes;

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
            Pipes.PIPE_ITEMS.values().forEach(pipe -> flatItem(pipe.get()));
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

            Pipes.PIPES.forEach((material, block) -> pipe(material.id(), block.get()));
            fitting("pipe_valve", Pipes.VALVE.get(), "block/pipe_fitting", "block/pipe_valve");
            fitting("pipe_vent", Pipes.VENT.get(), "block/pipe_fitting", "block/pipe_vent");
            fitting("pipe_tap", Pipes.TAP.get(), "block/pipe_fitting", "block/pipe_tap");
            fitting("safety_valve", Pipes.SAFETY_VALVE.get(), "block/pipe_fitting", "block/safety_valve");
            simpleBlockWithItem(Pipes.MANOMETER.get(), models().cubeAll("mercury_manometer", modLoc("block/mercury_manometer")));
            simpleBlockWithItem(Pipes.PRESSURE_VESSEL.get(), models().cubeAll("pressure_vessel", modLoc("block/pressure_vessel")));
            horizontalBlock(Pipes.PISTON_PUMP.get(), models().orientable("piston_pump", modLoc("block/pipe_fitting"), modLoc("block/piston_pump"), modLoc("block/pipe_fitting")));
            simpleBlockItem(Pipes.PISTON_PUMP.get(), models().getExistingFile(modLoc("block/piston_pump")));

            // The shape of a TFC copper lamp, whose models are referenced and not copied
            getVariantBuilder(Instruments.DAVY_LAMP.get()).forAllStates(state -> {
                final String hanging = state.getValue(DavyLampBlock.HANGING) ? "_hanging" : "";
                final String lit = state.getValue(DavyLampBlock.LIT) ? "_on" : "_off";
                return ConfiguredModel.builder().modelFile(new ModelFile.UncheckedModelFile("tfc:block/metal/lamp/copper" + hanging + lit)).build();
            });
        }

        private void fitting(String name, Block block, String side, String front)
        {
            final ModelFile model = models().orientable(name, modLoc(side), modLoc(front), modLoc(side));
            directionalBlock(block, model);
            simpleBlockItem(block, model);
        }

        /** A centre with an arm toward each side it joins, the one arm being turned into each of the six. */
        private void pipe(String material, Block block)
        {
            final ModelFile center = models().getBuilder("block/pipe/" + material + "_center")
                .texture("all", modLoc("block/pipe/" + material)).texture("particle", modLoc("block/pipe/" + material))
                .element().from(4, 4, 4).to(12, 12, 12).allFaces((face, builder) -> builder.texture("#all")).end();
            final ModelFile arm = models().getBuilder("block/pipe/" + material + "_arm")
                .texture("all", modLoc("block/pipe/" + material)).texture("particle", modLoc("block/pipe/" + material))
                .element().from(4, 4, 0).to(12, 12, 4).allFaces((face, builder) -> builder.texture("#all")).end();

            final MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
            builder.part().modelFile(center).addModel().end();
            builder.part().modelFile(arm).addModel().condition(PipeChain.NORTH, true).end();
            builder.part().modelFile(arm).rotationY(90).addModel().condition(PipeChain.EAST, true).end();
            builder.part().modelFile(arm).rotationY(180).addModel().condition(PipeChain.SOUTH, true).end();
            builder.part().modelFile(arm).rotationY(270).addModel().condition(PipeChain.WEST, true).end();
            builder.part().modelFile(arm).rotationX(270).addModel().condition(PipeChain.UP, true).end();
            builder.part().modelFile(arm).rotationX(90).addModel().condition(PipeChain.DOWN, true).end();
        }
    }

    /** The properties of the vanilla base class, which are protected on it. */
    private abstract static class PipeChain extends net.minecraft.world.level.block.PipeBlock
    {
        static final BooleanProperty NORTH = net.minecraft.world.level.block.PipeBlock.NORTH, EAST = net.minecraft.world.level.block.PipeBlock.EAST, SOUTH = net.minecraft.world.level.block.PipeBlock.SOUTH, WEST = net.minecraft.world.level.block.PipeBlock.WEST, UP = net.minecraft.world.level.block.PipeBlock.UP, DOWN = net.minecraft.world.level.block.PipeBlock.DOWN;

        private PipeChain(float apothem, Properties properties)
        {
            super(apothem, properties);
        }
    }

    private MetallurgyAssets() {}
}
