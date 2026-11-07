package tfcpp.pneumatics.pipes;

import java.util.function.BiFunction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A fitting that points one way, with a hand wheel that opens and closes it. Its body is of one material, as a pipe's is, and that decides what it holds and what eats it: a valve in iron dies in a line of dilute acid, one in lead or stoneware does not.
 */
public abstract class PipeFittingBlock extends DirectionalBlock implements PipeMember
{
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final float VOLUME = 0.005f;

    private final PipeMaterial material;

    protected PipeFittingBlock(PipeMaterial material, Properties properties)
    {
        super(properties);
        this.material = material;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    /** @return the codec of a fitting made of a material and given its properties, which is how a fitting of each material is told from the others */
    protected static <B extends PipeFittingBlock> MapCodec<B> fittingCodec(BiFunction<PipeMaterial, Properties, B> factory)
    {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(PipeMaterial::byId, PipeMaterial::id).fieldOf("material").forGetter(block -> ((PipeFittingBlock) block).material),
            propertiesCodec()).apply(instance, factory::apply));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    public float volume(BlockState state)
    {
        return VOLUME;
    }

    @Override
    public PipeMaterial material(BlockState state)
    {
        return material;
    }

    /** Most fittings have only their back toward the pipe. A fitting pointing into another is not joined to it. */
    @Override
    public boolean connects(BlockState state, Direction side)
    {
        return side == state.getValue(FACING).getOpposite();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!player.getMainHandItem().isEmpty())
        {
            return InteractionResult.PASS; // What is in the hand is not for this block: it may be placed beside it
        }
        if (level instanceof ServerLevel server)
        {
            if (turnedTooRecently(server, pos))
            {
                player.displayClientMessage(Component.translatable("tfcpp.pipe.valve_wait"), true);
                return InteractionResult.sidedSuccess(false);
            }
            final BlockState next = state.cycle(OPEN);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 0.8f);
            onToggled(server, pos);
            player.displayClientMessage(Component.translatable(next.getValue(OPEN) ? "tfcpp.pipe.opened" : "tfcpp.pipe.closed"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected void onToggled(ServerLevel level, BlockPos pos) {}

    /** @return {@code true} if the hand wheel was turned less than a second ago and is not to be turned again; only a valve, which rebuilds its network at every turn, is kept to this */
    protected boolean turnedTooRecently(ServerLevel level, BlockPos pos)
    {
        return false;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving)
    {
        super.onPlace(state, level, pos, oldState, isMoving);
        PipeTracker.placed(level, pos, state, oldState);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving)
    {
        PipeTracker.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** Closed, it is two separate runs of pipe: the network ends at it. */
    public static final class Valve extends PipeFittingBlock
    {
        private static final MapCodec<Valve> CODEC = fittingCodec(Valve::new);

        public Valve(PipeMaterial material, Properties properties)
        {
            super(material, properties);
        }

        @Override
        protected MapCodec<? extends DirectionalBlock> codec()
        {
            return CODEC;
        }

        @Override
        public boolean connects(BlockState state, Direction side)
        {
            return state.getValue(OPEN) && side.getAxis() == state.getValue(FACING).getAxis();
        }

        @Override
        protected void onToggled(ServerLevel level, BlockPos pos)
        {
            PipeTracker.get(level).onConnectionChanged(level, pos);
        }

        @Override
        protected boolean turnedTooRecently(ServerLevel level, BlockPos pos)
        {
            return !PipeTracker.get(level).mayTurnValve(level, pos);
        }
    }

    /** An open pipe end into the air in front of it, which lets gas through either way according to the pressure on each side. */
    public static final class Vent extends PipeFittingBlock implements PipePort
    {
        private static final MapCodec<Vent> CODEC = fittingCodec(Vent::new);

        public Vent(PipeMaterial material, Properties properties)
        {
            super(material, properties);
        }

        @Override
        protected MapCodec<? extends DirectionalBlock> codec()
        {
            return CODEC;
        }

        @Override
        public void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds)
        {
            if (state.getValue(OPEN))
            {
                network.flowThroughVent(level, pos.relative(state.getValue(FACING)), seconds);
            }
        }
    }

    /** Lets liquid out into a barrel or anything else in front of it that holds liquid. */
    public static final class Tap extends PipeFittingBlock implements PipePort
    {
        private static final MapCodec<Tap> CODEC = fittingCodec(Tap::new);

        public Tap(PipeMaterial material, Properties properties)
        {
            super(material, properties);
        }

        @Override
        protected MapCodec<? extends DirectionalBlock> codec()
        {
            return CODEC;
        }

        @Override
        public void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds)
        {
            if (state.getValue(OPEN))
            {
                network.flowThroughTap(level, pos, state.getValue(FACING), seconds);
            }
        }
    }

    /**
     * A weight on a lever holds this shut until the pressure lifts it, as Papin fitted to his digester in 1679. Nothing can hold it shut from outside. The weight is set so that it lifts below what the body can hold, as a valve that lifted above that would never lift: 8 atm for iron, and four fifths of what a lead or stoneware body holds.
     */
    public static final class SafetyValve extends PipeFittingBlock implements PipePort
    {
        public static final float LIFTS_AT_GAUGE_ATM = 8f;
        /** The valve seats again this much below the pressure it lifted at. */
        private static final float RESEAT_DROP_ATM = 1f;
        /** A seat of 25 mm on a pipe of 100 mm. */
        private static final float SEAT_AREA_SHARE = 1f / 16f;
        private static final MapCodec<SafetyValve> CODEC = fittingCodec(SafetyValve::new);

        public SafetyValve(PipeMaterial material, Properties properties)
        {
            super(material, properties);
        }

        @Override
        protected MapCodec<? extends DirectionalBlock> codec()
        {
            return CODEC;
        }

        /** @return the gauge pressure in atm at which a valve with a body of this material lifts */
        public static float liftsAt(PipeMaterial material)
        {
            return material.safetyValveSetting(LIFTS_AT_GAUGE_ATM);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
        {
            return InteractionResult.PASS;
        }

        @Override
        public void stepPort(PipeNetwork network, ServerLevel level, BlockPos pos, BlockState state, float seconds)
        {
            final float gauge = network.pressure() - 1f;
            final float lifts = liftsAt(material(state)), seats = lifts - RESEAT_DROP_ATM;
            final boolean open = state.getValue(OPEN) ? gauge > seats : gauge > lifts;
            if (open != state.getValue(OPEN))
            {
                level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
            }
            if (open)
            {
                network.flowThroughOpening(level, pos.relative(state.getValue(FACING)), seconds, SEAT_AREA_SHARE, seats, true);
                PipeNetwork.hiss(level, pos);
            }
        }
    }
}
