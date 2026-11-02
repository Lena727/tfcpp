package tfcpp.pneumatics.pipes;

import org.junit.jupiter.api.Test;

import tfcpp.pneumatics.gas.Gas;
import tfcpp.pneumatics.gas.GasMath;

import static org.junit.jupiter.api.Assertions.*;

class PipePhysicsTest
{
    @Test
    void idealGasLawHolds()
    {
        final float moles = PipePhysics.moles(1f, 293f, 1f);
        assertEquals(GasMath.CELL_MOLES, moles, 0.1f, "A cubic metre at 1 atm and 20 C");
        assertEquals(1f, PipePhysics.pressure(moles, 293f, 1f, 0f), 1e-3f);
        assertEquals(2f, PipePhysics.pressure(2f * moles, 293f, 1f, 0f), 1e-3f, "Twice the gas, twice the pressure");
        assertEquals(2f, PipePhysics.pressure(moles, 293f, 0.5f, 0f), 1e-3f, "Half the volume, twice the pressure");
        assertEquals(2f, PipePhysics.pressure(moles, 586f, 1f, 0f), 1e-3f, "Twice the absolute temperature, twice the pressure");
    }

    @Test
    void liquidIsMostOfTheSpaceAGasLoses()
    {
        final float moles = PipePhysics.moles(1f, 293f, 1f);
        assertEquals(2f, PipePhysics.pressure(moles, 293f, 1f, 0.5f), 1e-3f, "Half filling a vessel with water compresses the gas into half the space");
    }

    @Test
    void trappedLiquidBurstsWhenHeated()
    {
        final float cold = PipePhysics.liquidVolume(1000f, 293f);
        assertEquals(1f, cold, 1e-4f);
        final float warm = PipePhysics.liquidVolume(1000f, 313f);
        final float pressure = PipePhysics.pressure(0f, 313f, 1f, warm);
        assertTrue(pressure > 50f, "20 K of heating with no space to expand gives a pressure no pipe holds, got " + pressure);
    }

    @Test
    void waterBoilsWhereItShould()
    {
        assertEquals(1f, PipePhysics.waterVapourPressure(373.15f), 0.01f, "1 atm at 100 C");
        assertEquals(4.7f, PipePhysics.waterVapourPressure(423.15f), 0.1f, "A boiler at 150 C");
        assertEquals(0.023f, PipePhysics.waterVapourPressure(293.15f), 0.002f, "20 C");
        assertEquals(0f, PipePhysics.waterVapourPressure(250f));
    }

    @Test
    void flowFollowsDarcyWeisbach()
    {
        final float near = PipePhysics.flowSpeed(1000f, 1000f, 1f);
        final float far = PipePhysics.flowSpeed(1000f, 1000f, 100f);
        assertTrue(far < near / 3f, "A long pipe passes much less");
        assertEquals(2f * PipePhysics.flowSpeed(1000f, 1000f, 10f), PipePhysics.flowSpeed(4000f, 1000f, 10f), 1e-3f, "Speed goes with the root of pressure");
        assertEquals(340f, PipePhysics.flowSpeed(1e8f, 1.2f, 1f), "Gas chokes at the speed of sound");
        assertEquals(0f, PipePhysics.flowSpeed(-1f, 1000f, 1f));
    }

    @Test
    void burstEnergyMatchesIsentropicExpansion()
    {
        assertEquals(0f, PipePhysics.burstEnergy(1f, 1f));
        // 1 m3 at 10 atm: p V / (gamma - 1) * (1 - (1/10)^(0.2857)) = 2533 kJ * 0.482
        assertEquals(1221f, PipePhysics.burstEnergy(10f, 1f), 10f);
        assertEquals(2f * PipePhysics.burstEnergy(5f, 1f), PipePhysics.burstEnergy(5f, 2f), 1f);
    }

    @Test
    void wallsFollowBarlowUntilTheJointsGiveWay()
    {
        assertEquals(28.4f, PipePhysics.barlow(36f, 4f), 0.1f, "2 S t / D");
        assertEquals(PipeMaterial.JOINT_LIMIT_ATM, PipeMaterial.STEEL.rating(1f, 293f), "Packing gives way before steel does");
        assertTrue(PipeMaterial.CERAMIC.rating(1f, 293f) < PipeMaterial.COPPER.rating(1f, 293f));
        assertEquals(0f, PipeMaterial.WOOD.rating(1f, 500f), "Wood chars when hot");
        assertTrue(PipeMaterial.COPPER.rating(0.1f, 293f) < PipeMaterial.COPPER.rating(1f, 293f), "A corroded wall holds less");
    }

    @Test
    void materialsSufferFromTheRightThings()
    {
        assertTrue(PipeMaterial.COPPER.corrosion(Gas.AMMONIA) > PipeMaterial.WROUGHT_IRON.corrosion(Gas.AMMONIA), "Ammonia attacks copper and not iron");
        assertTrue(PipeMaterial.COPPER.corrosion("tfcpp:mercury") > 0f, "Mercury dissolves copper");
        assertEquals(0f, PipeMaterial.WROUGHT_IRON.corrosion("tfcpp:mercury"), "and is carried in iron");
        assertTrue(PipeMaterial.WROUGHT_IRON.corrosion("tfc:brine") > PipeMaterial.WOOD.corrosion("tfc:brine"), "Brine rusts iron, and wood is why brine mains were bored logs");
        assertEquals(0f, PipeMaterial.CERAMIC.corrosion(Gas.CHLORINE));
    }

    @Test
    void leadResistsSulfurButNotChlorineOrVinegar()
    {
        assertEquals(0f, PipeMaterial.LEAD.corrosion(Gas.SULFUR_DIOXIDE), "Sulfur dioxide makes a film of lead sulfate that protects the metal");
        assertTrue(PipeMaterial.LEAD.corrosion(Gas.CHLORINE) > 0f, "Chlorine still attacks lead");
        assertTrue(PipeMaterial.LEAD.corrosion(Gas.HYDROGEN_SULFIDE) > 0f, "Hydrogen sulfide blackens lead");
        assertEquals(0f, PipeMaterial.LEAD.corrosion(Gas.AMMONIA), "Nothing else in the gases touches lead");
        assertEquals(0.1f, PipeMaterial.LEAD.corrosion("tfc:vinegar"), 1e-6f, "Vinegar dissolves lead, as it did in Roman wine");
        assertTrue(PipeMaterial.LEAD.corrosion("tfc:vinegar") > PipeMaterial.COPPER.corrosion("tfc:vinegar"), "Lead suffers more from vinegar than copper does");
        assertEquals(0f, PipeMaterial.LEAD.corrosion("tfc:brine"), "Brine leaves lead alone");
        assertEquals(0f, PipeMaterial.LEAD.corrosion("tfcpp:mercury"), "Mercury does not dissolve lead");
        assertTrue(PipeMaterial.LEAD.rating(1f, 293f) > 0f, "A lead pipe holds pressure at room temperature");
        assertEquals(0f, PipeMaterial.LEAD.rating(1f, 430f), "Lead gives way above 150 C, which is 423 K");
    }

    @Test
    void contentsSplitAndJoinWithoutLoss()
    {
        final PipeContents contents = new PipeContents();
        contents.gases[Gas.METHANE.ordinal()] = 10f;
        contents.air = 30f;
        contents.liquid = "minecraft:water";
        contents.litres = 100f;

        final PipeContents taken = contents.take(0.25f);
        assertEquals(2.5f, taken.gases[Gas.METHANE.ordinal()], 1e-4f);
        assertEquals(7.5f, contents.gases[Gas.METHANE.ordinal()], 1e-4f);
        assertEquals(25f, taken.litres, 1e-4f);

        contents.add(taken);
        assertEquals(10f, contents.gases[Gas.METHANE.ordinal()], 1e-4f);
        assertEquals(30f, contents.air, 1e-4f);
        assertEquals(100f, contents.litres, 1e-4f);
        assertEquals(0f, taken.gasMoles(), "What was added is gone from where it came");
    }

    @Test
    void joiningMixesHeatByAmount()
    {
        final PipeContents hot = new PipeContents();
        hot.liquid = "minecraft:water";
        hot.litres = 100f;
        hot.temperature = 373f;
        final PipeContents cold = new PipeContents();
        cold.liquid = "minecraft:water";
        cold.litres = 300f;
        cold.temperature = 293f;

        cold.add(hot);
        assertEquals(313f, cold.temperature, 0.1f);
    }

    @Test
    void newPipeIsFullOfAir()
    {
        assertEquals(1f, PipeContents.atmospheric(0.01f).pressure(0.01f), 1e-3f);
    }

    @Test
    void savingKeepsEverything()
    {
        final PipeContents contents = new PipeContents();
        contents.gases[Gas.CHLORINE.ordinal()] = 3f;
        contents.air = 2f;
        contents.liquid = "tfc:brine";
        contents.litres = 40f;
        contents.temperature = 350f;

        final PipeContents loaded = PipeContents.load(contents.save());
        assertEquals(3f, loaded.gases[Gas.CHLORINE.ordinal()]);
        assertEquals(2f, loaded.air);
        assertEquals("tfc:brine", loaded.liquid);
        assertEquals(40f, loaded.litres);
        assertEquals(350f, loaded.temperature);
    }

    @Test
    void pistonWorkMatchesPressureDifference()
    {
        final float torque = PipePhysics.pistonTorque(0.02f, 1f);
        assertEquals(0.32f, torque, 0.01f, "dp V / 2 pi: 1 atm over a sweep of 20 litres");
        assertEquals(0f, PipePhysics.pistonTorque(0.02f, -1f), "Flow helped along by pressure gives no work back through a piston without a motor");
    }
}
