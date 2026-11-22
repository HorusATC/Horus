package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureAltitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;

class VerticalConstraintTest {

    private static FlightPlanWaypoint wp(AltitudeConstraint constraint, Integer altFt) {
        return new FlightPlanWaypoint(
                "FIX",
                new FixPosition(new Latitude(35.0), new Longitude(139.0)),
                altFt != null ? new Altitude(altFt) : null,
                null,
                constraint,
                WaypointAction.CONTINUE);
    }

    @Test
    void mapsFlightPlanConstraintsExhaustively() {
        assertInstanceOf(
                VerticalConstraint.None.class, VerticalConstraints.fromFlightPlanWaypoint(wp(AltitudeConstraint.NONE, null)));
        assertEquals(
                new VerticalConstraint.At(9000),
                VerticalConstraints.fromFlightPlanWaypoint(wp(AltitudeConstraint.AT, 9000)));
        assertEquals(
                new VerticalConstraint.AtOrAbove(13000),
                VerticalConstraints.fromFlightPlanWaypoint(wp(AltitudeConstraint.AT_OR_ABOVE, 13000)));
        assertEquals(
                new VerticalConstraint.AtOrBelow(11000),
                VerticalConstraints.fromFlightPlanWaypoint(wp(AltitudeConstraint.AT_OR_BELOW, 11000)));
        assertEquals(
                new VerticalConstraint.Window(8000, 8000),
                VerticalConstraints.fromFlightPlanWaypoint(wp(AltitudeConstraint.BETWEEN, 8000)));
    }

    @Test
    void mapsProcedureAltitudeFeetAndFlightLevel() {
        assertEquals(
                new VerticalConstraint.AtOrAbove(9000),
                VerticalConstraints.fromProcedureAltitude(
                        new ProcedureAltitude(AltitudeConstraint.AT_OR_ABOVE, 9000, null)));
        assertEquals(
                new VerticalConstraint.At(15000),
                VerticalConstraints.fromProcedureAltitude(
                        new ProcedureAltitude(AltitudeConstraint.AT, null, 150)));
        assertEquals(
                new VerticalConstraint.At(7000),
                VerticalConstraints.fromProcedureAltitude(
                        new ProcedureAltitude(AltitudeConstraint.NONE, 7000, null)));
        assertInstanceOf(
                VerticalConstraint.None.class, VerticalConstraints.fromProcedureAltitude(null));
    }

    @Test
    void resolveTargetSwitchIsExhaustive() {
        assertNull(WaypointAltitudeTargetResolver.resolveTarget(new VerticalConstraint.None(), 5000));
        assertEquals(
                9000,
                WaypointAltitudeTargetResolver.resolveTarget(new VerticalConstraint.At(9000), 5000).toDouble());
        assertEquals(
                12000,
                WaypointAltitudeTargetResolver.resolveTarget(new VerticalConstraint.AtOrAbove(10000), 12000)
                        .toDouble());
        assertEquals(
                10000,
                WaypointAltitudeTargetResolver.resolveTarget(new VerticalConstraint.AtOrAbove(10000), 8000)
                        .toDouble());
        assertEquals(
                7000,
                WaypointAltitudeTargetResolver.resolveTarget(new VerticalConstraint.AtOrBelow(7000), 9000)
                        .toDouble());
        assertEquals(
                8500,
                WaypointAltitudeTargetResolver.resolveTarget(new VerticalConstraint.Window(8000, 9000), 8500)
                        .toDouble());
        assertTrue(WaypointAltitudeTargetResolver.satisfiesConstraintAtPass(
                new VerticalConstraint.AtOrAbove(9000), 8950));
    }
}
