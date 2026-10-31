package tfcpp.mechanics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShaftPhysicsTest
{
    private static final float FULL_WHEEL_SPEED_PER_TICK = 2f * (float) Math.PI / 80f;
    private static final float FULL_WHEEL_SPEED = FULL_WHEEL_SPEED_PER_TICK * ShaftPhysics.TICKS_PER_SECOND;

    private static float power(float torque, float stall, float freeSpeed)
    {
        return torque * freeSpeed * ShaftPhysics.speedFactor(torque, stall);
    }

    @Test
    void wheelGivesItsBestPowerAtHalfOfFreeSpeed()
    {
        final float stall = ShaftPhysics.waterWheelStallTorque(FULL_WHEEL_SPEED_PER_TICK);

        float bestTorque = 0, bestPower = 0;
        for (float torque = 0; torque < stall; torque += stall / 200f)
        {
            final float power = power(torque, stall, FULL_WHEEL_SPEED);
            if (power > bestPower)
            {
                bestPower = power;
                bestTorque = torque;
            }
        }

        assertEquals(3f, bestPower, 0.01f, "A full undershot wheel gives about 3 kW");
        assertEquals(0.5f, ShaftPhysics.speedFactor(bestTorque, stall), 0.01f, "Smeaton's result for an undershot wheel");
    }

    @Test
    void noLoadRunsFreeAndTooMuchStalls()
    {
        final float stall = ShaftPhysics.waterWheelStallTorque(FULL_WHEEL_SPEED_PER_TICK);
        assertEquals(1f, ShaftPhysics.speedFactor(0f, stall));
        assertEquals(0f, ShaftPhysics.speedFactor(stall * 1.5f, stall));
    }

    @Test
    void wheelLosesPowerWithTheCubeOfFlow()
    {
        final float full = ShaftPhysics.waterWheelStallTorque(FULL_WHEEL_SPEED_PER_TICK);
        final float half = ShaftPhysics.waterWheelStallTorque(FULL_WHEEL_SPEED_PER_TICK / 2f);
        assertEquals(full / 4f, half, 0.01f, "Force goes with the square of the flow");
        assertEquals(0f, ShaftPhysics.waterWheelStallTorque(0f));
    }

    @Test
    void reversedFlowWorksTheSame()
    {
        assertEquals(ShaftPhysics.waterWheelStallTorque(FULL_WHEEL_SPEED_PER_TICK), ShaftPhysics.waterWheelStallTorque(-FULL_WHEEL_SPEED_PER_TICK));
    }

    @Test
    void windmillPowerFollowsBladesAndTheCubeOfWind()
    {
        final float calm = 2f * (float) Math.PI / 160f;
        final float calmSpeed = calm * ShaftPhysics.TICKS_PER_SECOND;

        final float five = ShaftPhysics.windmillStallTorque(calm, calm, 5);
        assertEquals(5f, five * calmSpeed / 4f, 0.01f, "Five blades give about 5 kW at the reference wind");

        final float windy = ShaftPhysics.windmillStallTorque(2f * calm, calm, 5);
        assertEquals(40f, windy * 2f * calmSpeed / 4f, 0.1f, "Twice the wind gives eight times the power");
        assertEquals(0f, ShaftPhysics.windmillStallTorque(0f, 0f, 1));
    }

    @Test
    void pumpWorkMatchesLiftingWater()
    {
        final float head = 16f;
        final float torque = ShaftPhysics.pumpTorque(head);
        final float volume = ShaftPhysics.pumpedVolume(FULL_WHEEL_SPEED, 1f);
        final float power = torque * FULL_WHEEL_SPEED;
        assertEquals(1000f * 9.81f * head * volume / 1000f, power, 0.01f, "Shaft power must equal rho g H Q");
    }

    @Test
    void aWheelCanOnlyLiftSoFar()
    {
        final float stall = ShaftPhysics.waterWheelStallTorque(FULL_WHEEL_SPEED_PER_TICK);
        assertTrue(ShaftPhysics.speedFactor(ShaftPhysics.pumpTorque(10f), stall) > 0.5f, "A full wheel lifts water 10 m comfortably");
        assertEquals(0f, ShaftPhysics.speedFactor(ShaftPhysics.pumpTorque(60f), stall), "and cannot lift it 60 m in one stage");
        assertEquals(ShaftPhysics.pumpTorque(1f), ShaftPhysics.pumpTorque(0f), "A pump always works against some friction");
    }

    @Test
    void fanLoadGrowsWithTheSquareOfSpeed()
    {
        assertEquals(0.64f, ShaftPhysics.fanTorque(FULL_WHEEL_SPEED), 0.01f);
        assertEquals(4f * ShaftPhysics.fanTorque(0.5f), ShaftPhysics.fanTorque(1f), 1e-4f);
        assertEquals(2f * ShaftPhysics.fanFlow(0.5f), ShaftPhysics.fanFlow(1f), 1e-4f);
        assertEquals(ShaftPhysics.fanFlow(1f), ShaftPhysics.fanFlow(-1f));
    }
}
