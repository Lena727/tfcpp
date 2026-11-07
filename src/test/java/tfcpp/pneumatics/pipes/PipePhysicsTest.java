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
    void acidsEatTheMetalsTheyEatAndLeadHoldsColdChamberAcid()
    {
        final String chamber = "tfcpp:chamber_acid", sulfuric = "tfcpp:sulfuric_acid";
        final String[] acids = {chamber, sulfuric, "tfcpp:nitric_acid", "tfcpp:hydrochloric_acid"};
        assertTrue(PipeMaterial.WROUGHT_IRON.corrosion(chamber, PipePhysics.ROOM_TEMPERATURE) > 10f * PipeMaterial.WROUGHT_IRON.corrosion(sulfuric, PipePhysics.ROOM_TEMPERATURE), "Chamber acid eats iron, and strong sulfuric acid hardly does");
        assertTrue(PipeMaterial.STEEL.corrosion(chamber, PipePhysics.ROOM_TEMPERATURE) > 0.1f, "Steel is eaten by chamber acid");
        for (String acid : acids)
        {
            assertEquals(0f, PipeMaterial.CERAMIC.corrosion(acid, 360f), "Ceramic takes nothing from " + acid);
        }
        final float cold = PipeMaterial.LEAD.corrosion(chamber, 300f);
        assertTrue(cold < 0.01f, "Lead holds chamber acid while it is cool, got " + cold);
        assertEquals(20f * cold, PipeMaterial.LEAD.corrosion(chamber, 360f), 1e-6f, "Above 80 C the film is gone and the lead is 20 times worse");
        assertEquals(20f * PipeMaterial.LEAD.corrosion(sulfuric, 300f), PipeMaterial.LEAD.corrosion(sulfuric, 360f), 1e-6f, "The same for sulfuric acid");
        assertEquals(PipeMaterial.LEAD.corrosion(chamber, 300f), PipeMaterial.LEAD.corrosion("tfcpp:chamber_acid", 293f), 0f, "Cool enough is the same at any temperature below 80 C");
        assertEquals(PipeMaterial.COPPER.corrosion("tfc:vinegar"), PipeMaterial.COPPER.corrosion("tfc:vinegar", PipePhysics.ROOM_TEMPERATURE), 0f, "The old method is the new one at room temperature");
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

    @Test
    void aColumnOfLiquidPressesOnItsFoot()
    {
        assertEquals(0.968f, PipePhysics.head(1000f, 10f) / PipePhysics.PASCALS_PER_ATM, 0.005f, "10 m of water is about 1 atm");
        assertEquals(-PipePhysics.head(1000f, 3f), PipePhysics.head(1000f, -3f), 1e-3f, "A negative height is a column that has to be lifted");
        assertEquals(1.8f * PipePhysics.head(1000f, 7f), PipePhysics.head(1800f, 7f), 1e-2f, "and it goes with the density of the liquid");
        final float torque = PipePhysics.pistonTorque(0.02f, PipePhysics.head(1000f, 100f) / PipePhysics.PASCALS_PER_ATM);
        assertEquals(3.1f, torque, 0.1f, "Lifting water 100 m costs 3.1 kN m a turn, where the difference in pressure alone said 0.02");
    }

    @Test
    void theAtmosphereLiftsWaterTenMetresAndNoMore()
    {
        assertEquals(10.33f, PipePhysics.suctionLift(1f, 1000f), 0.01f);
        assertEquals(PipePhysics.MAX_SUCTION_METRES_OF_WATER, PipePhysics.suctionLift(1f, 1000f), 1e-4f);
        assertTrue(PipePhysics.suctionLift(1f, 1800f) < 6f, "Acid is heavier, and goes up less");
        assertEquals(0f, PipePhysics.suctionLift(0f, 1000f), "A vacuum over the liquid lifts nothing");
        assertEquals(2f * PipePhysics.suctionLift(1f, 1000f), PipePhysics.suctionLift(2f, 1000f), 1e-3f, "Twice the pressure over it, twice the lift");
    }

    @Test
    void theTopOfTheLiquidIsFoundFromTheBottomUp()
    {
        final float[] layers = {0.015f, 0.02f};
        assertEquals(1f, PipePhysics.surfaceHeight(layers, 1, 0f), 1e-6f, "No liquid: the bottom of the lowest level");
        assertEquals(1f + 0.002f / 0.015f, PipePhysics.surfaceHeight(layers, 1, 0.002f), 1e-4f, "2 litres lie in the foot of the lowest level");
        assertEquals(2.5f, PipePhysics.surfaceHeight(layers, 1, 0.025f), 1e-4f, "and a fuller pipe fills the lowest level, then half of the next");
        assertEquals(3f, PipePhysics.surfaceHeight(layers, 1, 1f), 1e-6f, "More than there is room for stands at the top");
    }

    @Test
    void boilingPointFollowsPressureBothWays()
    {
        assertEquals(373.15f, PipePhysics.saturationTemperature(1f), 0.6f);
        assertEquals(423.15f, PipePhysics.saturationTemperature(4.7f), 1f, "150 C");
        assertEquals(354.4f, PipePhysics.saturationTemperature(0.5f), 1f, "Water boils at 81 C under half an atmosphere");
        for (float celsius : new float[] {60f, 99f, 100f, 130f, 170f, 220f, 300f})
        {
            assertEquals(celsius + 273.15f, PipePhysics.saturationTemperature(PipePhysics.waterVapourPressure(celsius + 273.15f)), 0.8f, "Round trip at " + celsius);
        }
    }

    @Test
    void aBoilerThatBurstsFlashesToSteam()
    {
        final float temperature = PipePhysics.saturationTemperature(11f);
        final float megajoules = PipePhysics.flashEnergy(450f, temperature) / 1000f;
        assertEquals(27f, megajoules, 1.5f, "0.5 m3 of water, 90% full, at 11 atm: about 27 MJ, where the steam above it holds under 2");
        assertEquals(0f, PipePhysics.flashEnergy(450f, 373f), "Water that is not above its boiling point has no flash");
        assertEquals(0f, PipePhysics.flashEnergy(0f, 450f));
        assertEquals(2f * PipePhysics.flashEnergy(100f, 450f), PipePhysics.flashEnergy(200f, 450f), 1e-2f, "It goes with the mass");
        float last = 0f;
        for (float celsius = 105f; celsius <= 370f; celsius += 15f)
        {
            final float energy = PipePhysics.flashEnergy(1f, celsius + 273.15f);
            assertTrue(energy > last, "Hotter water flashes harder: " + celsius);
            last = energy;
        }
        assertTrue(PipePhysics.flashEnergy(450f, temperature) > 10f * PipePhysics.burstEnergy(11f, 0.05f), "The water is most of the energy of a boiler");
    }

    @Test
    void aScaldTakesMoreFromAGreaterFlash()
    {
        assertEquals(0.05f, PipePhysics.scaldShare(60f), "The least is a little burn");
        assertEquals(0.6f, PipePhysics.scaldShare(27000f), "and the most of a boiler is a little over half of one's health");
        assertTrue(PipePhysics.scaldShare(6000f) > PipePhysics.scaldShare(2000f));
    }

    @Test
    void aSlowTapStillGivesAMillilitreInTheEnd()
    {
        float credit = 0f;
        int given = 0;
        for (int second = 0; second < 10; second++)
        {
            final PipePhysics.Drip owed = PipePhysics.drip(credit, 0.35f, 100f);
            given += owed.whole();
            credit = PipePhysics.creditAfter(owed, owed.whole());
        }
        assertEquals(3, given, "3.5 litres in ten seconds, a litre at a time as they add up");
        assertEquals(0.5f, credit, 1e-4f);

        final PipePhysics.Drip owed = PipePhysics.drip(0.6f, 0.6f, 100f);
        assertEquals(1, owed.whole());
        assertEquals(0.2f, owed.credit(), 1e-4f);
        assertEquals(0.999f, PipePhysics.creditAfter(owed, 0), 1e-6f, "A receiver that took nothing leaves it all owed, but never a whole millilitre");
        assertEquals(0, PipePhysics.drip(0f, 50f, 0.4f).whole(), "Less than a millilitre left in the pipe is not given: it is a film");
    }
}
