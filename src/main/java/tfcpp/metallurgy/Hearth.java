package tfcpp.metallurgy;

/** A TFC device whose temperature follows {@link Draught}: a charcoal forge or a firepit. Implemented onto them by {@code DraughtMixins}. */
public interface Hearth
{
    DraughtState tfcpp$draught();

    /** @return the burn ticks left on the piece of fuel now burning */
    int tfcpp$burnTicks();
}
