package tfcpp.mechanics;

/** Added to TFC's water wheel and windmill, so that they slow down under load. */
public interface ShaftSource
{
    /** @return the torque this source can start against at this moment, in kN m */
    float tfcpp$stallTorque();

    /** Sets the share of its free speed to run at, and sends it to clients if it changed noticeably. */
    void tfcpp$setSpeedFactor(float factor);

    float tfcpp$speedFactor();
}
