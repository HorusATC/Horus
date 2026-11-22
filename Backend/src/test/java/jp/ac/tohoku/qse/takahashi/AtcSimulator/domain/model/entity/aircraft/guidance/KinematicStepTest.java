package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.guidance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Heading;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.VerticalSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.MathUtils;

class KinematicStepTest {

    @Test
    void forceRightTreatsRetreatingTargetAsCaptured() {
        // Current 170.60°, target retreated to 170.54° → clockwise delta ≈ 359.94° without the fix.
        double next = KinematicStep.nextHeading(170.60, 170.54, 3.0, 1.0, TurnConstraint.FORCE_RIGHT);
        assertEquals(170.54, next, 1e-6);
    }

    @Test
    void forceRightKeepsLongTurnPastOneHundredEighty() {
        // Teardrop inbound: 240° → 90° right is 210° clockwise and must not snap.
        double next = KinematicStep.nextHeading(240.0, 90.0, 3.0, 1.0, TurnConstraint.FORCE_RIGHT);
        assertEquals(243.0, next, 1e-6);
    }

    @Test
    void trackCourseAppliesWcaUnderCrosswind() {
        FixPosition fix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        Guidance.TrackCourse track = new Guidance.TrackCourse(180.0, fix);
        double heading = KinematicStep.resolveGuidanceHeading(
                track, 35.0, 139.0, 5000, 180.0, 180.0, UniformWind.measured(90, 30));
        // On-course: heading ≈ 180 − asin(30/180) ≈ 170.5°
        assertEquals(170.5, heading, 1.0);
    }

    @Test
    void missingWindStepKeepsTrackEqualHeading() {
        KinematicStep.State state = KinematicStep.State.from(
                new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(5000)),
                new AircraftVector(new Heading(90), new GroundSpeed(180), new VerticalSpeed(0)));
        KinematicStep.State next = KinematicStep.next(
                state,
                new Guidance.FlyHeading(90),
                UniformWind.missing(),
                new KinematicStep.Targets(5000, 180, null),
                new KinematicStep.Limits(3.0, 3.0, 2000),
                TurnConstraint.SHORTEST,
                1.0);
        assertEquals(next.headingDeg(), next.trackDeg(), 1e-6);
        assertEquals(next.trueAirspeedKt(), next.groundSpeedKt(), 1e-6);
        assertTrue(MathUtils.normalizeAngle(next.headingDeg() - 90) < 1e-6
                || Math.abs(next.headingDeg() - 90) < 1e-6);
    }
}
