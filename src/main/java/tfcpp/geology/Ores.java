package tfcpp.geology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.dries007.tfc.common.blocks.GroundcoverBlock;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import tfcpp.metallurgy.Metallurgy;

/** Registers the blocks and items of each {@link NewOre}, in the same form as TFC's own: a block for each rock it lies in, the pieces it breaks into, and a small loose piece on the surface above a graded one. */
public final class Ores
{
    /** Blocks by their id, such as {@code ore/normal_galena/limestone}. */
    public static final Map<String, Supplier<Block>> BLOCKS = new LinkedHashMap<>();
    /** The pieces a block breaks into, by the kind of ore. */
    public static final Map<String, Supplier<Item>> PIECES = new LinkedHashMap<>();
    /** The loose pieces lying on the surface, by the ore. */
    public static final Map<NewOre, Supplier<Block>> SMALL_BLOCKS = new LinkedHashMap<>();
    public static final Map<NewOre, Supplier<Item>> SMALL_ITEMS = new LinkedHashMap<>();
    private static final List<Supplier<Item>> BLOCK_ITEMS = new ArrayList<>();

    static
    {
        for (NewOre ore : NewOre.values())
        {
            for (String kind : ore.kinds())
            {
                PIECES.put(kind, Metallurgy.ITEMS.register("ore/" + kind, () -> new Item(new Item.Properties())));
                for (Rock rock : ore.hosts())
                {
                    final String id = NewOre.blockId(kind, rock);
                    final Supplier<Block> block = Metallurgy.BLOCKS.register(id, () -> new Block(oreProperties(rock)));
                    BLOCKS.put(id, block);
                    BLOCK_ITEMS.add(Metallurgy.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
                }
            }
            if (ore.graded())
            {
                final String id = "ore/small_" + ore.id();
                final Supplier<Block> small = Metallurgy.BLOCKS.register(id, () -> GroundcoverBlock.looseOre(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.05f, 0f).sound(SoundType.NETHER_ORE).noCollission().pushReaction(PushReaction.DESTROY)));
                SMALL_BLOCKS.put(ore, small);
                SMALL_ITEMS.put(ore, Metallurgy.ITEMS.register(id, () -> new BlockItem(small.get(), new Item.Properties())));
            }
        }
    }

    /** As hard to break as the rock around it, the same as TFC's ores. */
    private static BlockBehaviour.Properties oreProperties(Rock rock)
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE).strength(rock.category().hardness(6.5f), 10f).requiresCorrectToolForDrops();
    }

    public static void addToTab(CreativeModeTab.Output output)
    {
        PIECES.values().forEach(piece -> output.accept(piece.get()));
        SMALL_ITEMS.values().forEach(small -> output.accept(small.get()));
        BLOCK_ITEMS.forEach(block -> output.accept(block.get()));
    }

    /** Called so that all of this registers before the registers close. */
    public static void load() {}

    private Ores() {}
}
