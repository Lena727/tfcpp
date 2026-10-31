package tfcpp.pneumatics.pipes;

import java.util.List;
import java.util.Map;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** A straight length of pipe, joining whatever pipe fittings stand next to it. */
public final class PipeBlock extends net.minecraft.world.level.block.PipeBlock implements PipeMember
{
    private static final float VOLUME = 0.01f;
    private static final Map<Direction, BooleanProperty> SIDES = PROPERTY_BY_DIRECTION;

    private final PipeMaterial material;

    public PipeBlock(PipeMaterial material, Properties properties)
    {
        super(0.25f, properties);
        this.material = material;
        BlockState state = getStateDefinition().any();
        for (BooleanProperty side : SIDES.values())
        {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.block.PipeBlock> codec()
    {
        return simpleCodec(properties -> new PipeBlock(material, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        SIDES.values().forEach(builder::add);
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

    @Override
    public boolean connects(BlockState state, Direction side)
    {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values())
        {
            state = state.setValue(SIDES.get(side), joins(side, context.getLevel().getBlockState(context.getClickedPos().relative(side))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos)
    {
        return state.setValue(SIDES.get(side), joins(side, neighbour));
    }

    private static boolean joins(Direction side, BlockState neighbour)
    {
        return neighbour.getBlock() instanceof PipeMember member && member.connects(neighbour, side.getOpposite());
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params)
    {
        final Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        return origin != null && PipeTracker.get(params.getLevel()).isWornOut(BlockPos.containing(origin)) ? List.of() : super.getDrops(state, params);
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
}
