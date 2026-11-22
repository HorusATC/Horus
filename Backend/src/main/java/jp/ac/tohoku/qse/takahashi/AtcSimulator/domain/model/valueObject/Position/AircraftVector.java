package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Heading;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.VerticalSpeed;

public class AircraftVector {
    public final Heading heading;
    public final GroundSpeed groundSpeed;
    public final VerticalSpeed verticalSpeed;
    /** Ground track in degrees. Equals heading when wind is absent/calm. */
    public final double trackDeg;
    /** True airspeed along heading (kt). Equals groundSpeed when wind is absent/calm. */
    public final double trueAirspeedKnots;

    /**
     * No-wind constructor: track equals heading and TAS equals ground speed.
     * Existing call sites keep identical kinematics until wind is applied.
     */
    public AircraftVector(Heading heading, GroundSpeed groundSpeed, VerticalSpeed verticalSpeed) {
        this(heading, groundSpeed, verticalSpeed, heading.toDouble(), groundSpeed.toDouble());
    }

    public AircraftVector(
            Heading heading,
            GroundSpeed groundSpeed,
            VerticalSpeed verticalSpeed,
            double trackDeg,
            double trueAirspeedKnots) {
        this.heading = heading;
        this.groundSpeed = groundSpeed;
        this.verticalSpeed = verticalSpeed;
        this.trackDeg = trackDeg;
        this.trueAirspeedKnots = trueAirspeedKnots;
    }

    /** Direction used for ground motion / conflict prediction. */
    public double motionDirectionDeg() {
        return trackDeg;
    }
}
