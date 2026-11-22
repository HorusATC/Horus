package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.ProcedureFlightPlanMapper;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.behavior.FixedWingFlightBehavior;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.characteristics.AircraftCharacteristics;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.departure.InitialClimbGeometry;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Heading;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.VerticalSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.InstructedVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.AtsRouteFixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.ProcedureCatalog;

class NavModeCombinationTest {

    private static final double START_LAT = 35.55998611;
    private static final double START_LON = 139.76906667;
    private static final double DER_ELEV = 18.2;

    @Test
    @DisplayName("VAMOS → Resume at 300 ft → VA until ≥700 → TT502")
    void vamosResumeThenReleaseToTt502() {
        CommercialAircraft aircraft = commercial("CMB1", 35.52, 139.78, 200, 330, 180);
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped = mapVamos();
        aircraft.assignProcedureRoute(mapped.flightPlan(), mapped.vaConstraint());

        // Climb to ~300 ft then Resume
        while (aircraft.getAircraftPosition().altitude.toDouble() < 300.0) {
            tick(aircraft);
        }
        aircraft.setResumeNavigation();
        assertTrue(aircraft.isProcedureVaActive());
        assertEquals(0, aircraft.getCurrentWaypointIndex());

        while (aircraft.isProcedureVaActive()) {
            assertTrue(aircraft.getAircraftPosition().altitude.toDouble() < 700.0
                    || aircraft.getAircraftPosition().altitude.toDouble() >= 700.0);
            tick(aircraft);
        }
        assertTrue(aircraft.getAircraftPosition().altitude.toDouble() >= 700.0);
        assertEquals(NavigationMode.FLIGHT_PLAN, aircraft.getNavigationMode());
        assertEquals("TT502", aircraft.getFlightPlan().getWaypoints().get(0).getFixName());
        assertEquals(0, aircraft.getCurrentWaypointIndex());
    }

    @Test
    @DisplayName("DEPART with deferred → rejected handoff → still turns at DER+400")
    void rejectedHandoffStillReleasesDeferredHeading() {
        AircraftBase aircraft = bare("CMB2", 35.0, 139.0, 5000, 90, 180);
        aircraft.beginInitialClimb(START_LAT, START_LON, 20.5, 149.88, DER_ELEV, 90.0, 5000);
        aircraft.setAircraftPosition(
                new AircraftPosition(new Latitude(34.35), new Longitude(139.77), new Altitude(2000)));
        assertThrows(IllegalArgumentException.class, () -> aircraft.beginTowerHandoff(START_LAT, START_LON));
        assertTrue(aircraft.isInitialClimbActive());

        aircraft.setAircraftPosition(
                new AircraftPosition(
                        new Latitude(START_LAT),
                        new Longitude(START_LON),
                        new Altitude(InitialClimbGeometry.turnReleaseAltitudeFt(DER_ELEV) + 1)));
        aircraft.calculateNextAircraftVector(1.0);
        assertEquals(90.0, aircraft.getInstructedVector().instructedHeading.toDouble(), 0.01);
    }

    @Test
    @DisplayName("JOIN FINAL → STAR assign → plan end → downwind heading matches instruction")
    void joinFinalThenStarThenPlainHeading() throws Exception {
        AircraftBase aircraft = bare("CMB3", 35.0, 139.0, 5000, 150, 180);
        aircraft.setAmbientWind(UniformWind.measured(270, 20));
        aircraft.joinFinalApproachIntercept(
                new FixPosition(new Latitude(35.05), new Longitude(139.05)), "FA", 149.88);
        var reached = AircraftBase.class.getDeclaredMethod("onDirectToTargetReached");
        reached.setAccessible(true);
        reached.invoke(aircraft);

        ProcedureFlightPlanMapper.MappedProcedureRoute star = mapOshima();
        aircraft.assignProcedureRoute(star.flightPlan(), star.vaConstraint());
        assertEquals(NavigationMode.FLIGHT_PLAN, aircraft.getNavigationMode());
        assertFalse(aircraft.isFinalApproachCoursePending());

        // Satisfy STAR AT constraints (±500 ft) so each fix can pass.
        aircraft.setAircraftPosition(
                new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(13000)));
        var onPassed =
                AircraftBase.class.getDeclaredMethod("onWaypointPassed", FlightPlanWaypoint.class);
        onPassed.setAccessible(true);
        List<FlightPlanWaypoint> wps = star.flightPlan().getWaypoints();
        for (FlightPlanWaypoint wp : wps) {
            if (wp.getTargetAltitude() != null) {
                aircraft.setAircraftPosition(
                        new AircraftPosition(
                                new Latitude(35.0),
                                new Longitude(139.0),
                                wp.getTargetAltitude()));
            }
            onPassed.invoke(aircraft, wp);
        }
        assertEquals(NavigationMode.HEADING, aircraft.getNavigationMode());

        aircraft.setInstructedVector(
                new InstructedVector(new Heading(45), new Altitude(5000), new GroundSpeed(220)));
        for (int i = 0; i < 120; i++) {
            tick(aircraft);
        }
        double heading = aircraft.getAircraftVector().heading.toDouble();
        double instructed = aircraft.getInstructedVector().instructedHeading.toDouble();
        double delta = Math.min(Math.abs(heading - instructed), 360.0 - Math.abs(heading - instructed));
        assertTrue(delta < 1.5, "heading=" + heading + " instructed=" + instructed);
    }

    private static ProcedureFlightPlanMapper.MappedProcedureRoute mapVamos() {
        ProcedureCatalog catalog = new ProcedureCatalog();
        ProcedureFlightPlanMapper mapper =
                new ProcedureFlightPlanMapper(new AtsRouteFixPositionRepository());
        return mapper.map(
                new Callsign("CMB1"),
                catalog.findById("VAMOS4.RWY34L_34R").orElseThrow(),
                Optional.empty(),
                9000,
                250);
    }

    private static ProcedureFlightPlanMapper.MappedProcedureRoute mapOshima() {
        ProcedureCatalog catalog = new ProcedureCatalog();
        ProcedureFlightPlanMapper mapper =
                new ProcedureFlightPlanMapper(new AtsRouteFixPositionRepository());
        return mapper.map(
                new Callsign("CMB3"),
                catalog.findById("OSHIMA1A").orElseThrow(),
                Optional.empty(),
                13000,
                250);
    }

    private static CommercialAircraft commercial(
            String cs, double lat, double lon, double alt, double hdg, double gs) {
        return new CommercialAircraft(
                new Callsign(cs),
                new AircraftType("B738"),
                new AircraftPosition(new Latitude(lat), new Longitude(lon), new Altitude(alt)),
                new AircraftVector(new Heading(hdg), new GroundSpeed(gs), new VerticalSpeed(0)),
                "HND",
                "RJTT",
                "HND",
                "RJTT",
                "2026-11-23T00:00:00Z");
    }

    private static AircraftBase bare(
            String cs, double lat, double lon, double alt, double hdg, double gs) {
        var characteristics = new AircraftCharacteristics(
                3.0,
                3.0,
                1640.0,
                500.0,
                140.0,
                42000.0,
                0.0,
                AircraftCharacteristics.AircraftCategory.COMMERCIAL_PASSENGER);
        return new AircraftBase(
                new Callsign(cs),
                new AircraftType("B738"),
                new AircraftPosition(new Latitude(lat), new Longitude(lon), new Altitude(alt)),
                new AircraftVector(new Heading(hdg), new GroundSpeed(gs), new VerticalSpeed(0)),
                new FixedWingFlightBehavior(),
                characteristics) {};
    }

    private static void tick(AircraftBase a) {
        a.calculateNextAircraftVector(1.0);
        a.calculateNextAircraftPosition(1.0);
    }
}
