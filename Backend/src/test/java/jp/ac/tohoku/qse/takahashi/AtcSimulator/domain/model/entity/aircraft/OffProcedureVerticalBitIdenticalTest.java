package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlan;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.WaypointAction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.WaypointAltitudeTargetResolver;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Heading;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.VerticalSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.InstructedVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;

/**
 * Off-procedure aircraft must keep the pre-slice vertical path (no look-ahead).
 */
class OffProcedureVerticalBitIdenticalTest {

    @Test
    @DisplayName("setFlightPlan (not assign-procedure) keeps single-WP altitude targeting")
    void plainFlightPlanDoesNotEnableProcedureLookahead() {
        FlightPlanWaypoint wp0 = new FlightPlanWaypoint(
                "A",
                new FixPosition(new Latitude(35.0), new Longitude(139.0)),
                null,
                null,
                AltitudeConstraint.NONE,
                WaypointAction.CONTINUE);
        FlightPlanWaypoint wp1 = new FlightPlanWaypoint(
                "B",
                new FixPosition(new Latitude(35.2), new Longitude(139.2)),
                new Altitude(13000),
                null,
                AltitudeConstraint.AT_OR_ABOVE,
                WaypointAction.CONTINUE);
        FlightPlan plan = new FlightPlan(
                new Callsign("OFF1"),
                "RJTT",
                "RJAA",
                List.of(wp0, wp1),
                new Altitude(10000),
                new GroundSpeed(250));

        CommercialAircraft aircraft = new CommercialAircraft(
                new Callsign("OFF1"),
                new AircraftType("B738"),
                new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(5000)),
                new AircraftVector(new Heading(45), new GroundSpeed(250), new VerticalSpeed(0)),
                "HND",
                "RJTT",
                "NRT",
                "RJAA",
                "2026-11-23T00:00:00Z");
        aircraft.setFlightPlan(plan);
        assertFalse(aircraft.isProcedureVerticalGuidance());

        List<Double> instructedAlts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            aircraft.calculateNextAircraftVector(1.0);
            aircraft.calculateNextAircraftPosition(1.0);
            instructedAlts.add(aircraft.getInstructedVector().instructedAltitude.toDouble());
        }

        // Next WP has no constraint → cruise 10000 (legacy single-WP fallback), not 13000 look-ahead.
        assertEquals(10000.0, instructedAlts.get(0), 0.01);
        assertEquals(
                10000.0,
                WaypointAltitudeTargetResolver.resolveTarget(wp0, 5000) == null
                        ? plan.getCruiseAltitude().toDouble()
                        : WaypointAltitudeTargetResolver.resolveTarget(wp0, 5000).toDouble(),
                0.01);
    }

    @Test
    @DisplayName("heading-only aircraft altitude path is unchanged by procedure guidance code")
    void plainHeadingAltitudeTrace() {
        CommercialAircraft aircraft = new CommercialAircraft(
                new Callsign("HDG1"),
                new AircraftType("B738"),
                new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(5000)),
                new AircraftVector(new Heading(90), new GroundSpeed(250), new VerticalSpeed(0)),
                "HND",
                "RJTT",
                "NRT",
                "RJAA",
                "2026-11-23T00:00:00Z");
        aircraft.setInstructedVector(
                new InstructedVector(new Heading(90), new Altitude(8000), new GroundSpeed(250)));

        double[] alts = new double[60];
        for (int i = 0; i < 60; i++) {
            aircraft.calculateNextAircraftVector(1.0);
            aircraft.calculateNextAircraftPosition(1.0);
            alts[i] = aircraft.getAircraftPosition().altitude.toDouble();
        }

        // 1640 fpm for 60 s from 5000 → 6640, then capture toward 8000.
        assertEquals(5000.0 + 1640.0, alts[59], 30.0);
        assertFalse(aircraft.isProcedureVerticalGuidance());
        assertTrue(aircraft.getUnreachableAltitudeConstraintFixes().isEmpty());
    }
}
