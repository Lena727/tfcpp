package tfcpp.pneumatics.pipes;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** One block of pipe. Its share of the contents is held here while no network is using it, which is what makes it safe to unload a chunk with half a network in it. */
public final class PipeSegment
{
    public final BlockPos pos;
    public final float volume;
    /** Share of the wall left, which corrosion eats away. */
    public float integrity = 1f;
    public PipeContents stored;
    @Nullable public PipeNetwork network;

    public PipeSegment(BlockPos pos, float volume, PipeContents stored)
    {
        this.pos = pos.immutable();
        this.volume = volume;
        this.stored = stored;
    }

    /** @return what this holds at this moment, whether or not it is in a network */
    public PipeContents contents()
    {
        return network == null ? stored : network.contents().share(volume / network.volume());
    }
}
