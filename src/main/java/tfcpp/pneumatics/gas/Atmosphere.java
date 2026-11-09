package tfcpp.pneumatics.gas;

/**
 * The open air outside the gas grid, as a room sees it through its openings. Free of Minecraft types. A cell that exchanges with it relaxes towards its concentrations instead of towards nothing: what leaves a cell is {@link #receive received} and what enters is {@link #give given}, so that gas is neither made nor lost at the border.
 */
public interface Atmosphere
{
    /** @return moles of this gas in a cubic metre of the outside air, which is one cell of the grid */
    float molesPerCubicMetre(Gas gas);

    /** Gas that has left a cell for the open air. */
    void receive(Gas gas, float moles);

    /** @return gas that the open air gives up to a cell, no more than {@code wanted} and no more than it holds */
    float give(Gas gas, float wanted);
}
