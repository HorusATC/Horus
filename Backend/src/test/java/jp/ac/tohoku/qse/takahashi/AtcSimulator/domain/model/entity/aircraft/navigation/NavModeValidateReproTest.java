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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlan;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.WaypointAction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureVaConstraint;
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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

/**
 * Inverted external-review claims ⑤ / ⑥ / P-3 for PR-C.
 */
class NavModeValidateReproTest {

    private static final double START_LAT = 35.55998611;
    private static final double START_LON = 139.76906667;
    private static final double START_ELEV = 20.5;
    private static final double RUNWAY_HDG = 149.88;
    private static final double DER_ELEV = 18.2;

    @Test
    @DisplayName("⑤ VAMOS: Resume during VA keeps VA (not FLIGHT_PLAN+VA); release ≥700 ft")
    void claim5_resumeDuringVaLeavesVaActiveInFlightPlanMode_realVamos() {
        CommercialAircraft aircraft = commercial("VA5A", 35.52, 139.78, 200, 330, 180);
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped = mapVamos();
        aircraft.assignProcedureRoute(mapped.flightPlan(), mapped.vaConstraint());
        assertTrue(aircraft.isProcedureVaActive());
        assertEquals(NavigationMode.HEADING, aircraft.getNavigationMode());

        aircraft.setResumeNavigation();

        assertTrue(aircraft.isProcedureVaActive());
        assertEquals(NavigationMode.HEADING, aircraft.getNavigationMode());
        assertEquals(330.0, aircraft.getInstructedVector().instructedHeading.toDouble(), 0.01);
        assertEquals(0, aircraft.getCurrentWaypointIndex());

        Double lastVaAlt = null;
        Double firstReleasedAlt = null;
        for (int i = 0; i < 3000 && firstReleasedAlt == null; i++) {
            double alt = aircraft.getAircraftPosition().altitude.toDouble();
            if (aircraft.isProcedureVaActive()) {
                lastVaAlt = alt;
            } else if (lastVaAlt != null) {
                firstReleasedAlt = alt;
                break;
            }
            tick(aircraft);
        }
        assertTrue(lastVaAlt != null && lastVaAlt >= 700.0, "last VA alt=" + lastVaAlt);
        assertTrue(
                firstReleasedAlt != null && firstReleasedAlt >= 700.0, "release alt=" + firstReleasedAlt);
        assertEquals(NavigationMode.FLIGHT_PLAN, aircraft.getNavigationMode());
        assertFalse(aircraft.isProcedureVaActive());
    }

    @Test
    @DisplayName("⑤ contrived: Resume does not advance next fix below VA altitude")
    void claim5_resumeDuringVaAdvancesWaypointBelowVaAltitude_contrived() {
        double course = 330.0;
        double lat = 35.50;
        double lon = 139.80;
        double[] next = GeodeticUtils.offsetPointNm(lat, lon, course, 0.5);
        AircraftBase withResume = bare("VA5B", lat, lon, 300, course, 180);
        AircraftBase withoutResume = bare("VA5C", lat, lon, 300, course, 180);
        FlightPlan plan = singleFixPlan("NEAR", next[0], next[1]);
        ProcedureVaConstraint va = new ProcedureVaConstraint(course, 700);
        withResume.assignProcedureRoute(plan, va);
        withoutResume.assignProcedureRoute(plan, va);

        withResume.setResumeNavigation();

        for (int i = 0; i < 120; i++) {
            tick(withResume);
            tick(withoutResume);
            if (withResume.getAircraftPosition().altitude.toDouble() >= 700.0) {
                break;
            }
            assertEquals(0, withResume.getCurrentWaypointIndex(), "withResume advanced below 700");
            assertEquals(0, withoutResume.getCurrentWaypointIndex(), "withoutResume advanced below 700");
            assertTrue(withResume.isProcedureVaActive());
            assertTrue(withoutResume.isProcedureVaActive());
        }
    }

    @Test
    @DisplayName("⑥ rejected tower handoff keeps DEPART / deferred heading")
    void claim6_rejectedTowerHandoffClearsInitialClimb() {
        AircraftBase aircraft = bare("CL6", 35.0, 139.0, 5000, 90, 180);
        aircraft.beginInitialClimb(START_LAT, START_LON, START_ELEV, RUNWAY_HDG, DER_ELEV, 90.0, 5000);
        assertTrue(aircraft.isInitialClimbActive());
        assertEquals(90.0, aircraft.getDeferredInitialHeadingDeg(), 0.01);

        aircraft.setAircraftPosition(
                new AircraftPosition(new Latitude(34.35), new Longitude(139.77), new Altitude(2000)));
        aircraft.setAircraftVector(
                new AircraftVector(
                        new Heading(RUNWAY_HDG), new GroundSpeed(180), new VerticalSpeed(1000)));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> aircraft.beginTowerHandoff(START_LAT, START_LON));
        assertTrue(ex.getMessage().contains("too far") || ex.getMessage().contains("does not reach"));
        assertFalse(aircraft.isTowerHandoffActive());
        assertTrue(aircraft.isInitialClimbActive());
        assertEquals(RUNWAY_HDG, aircraft.getInitialClimbRunwayHeadingDeg(), 0.01);
        assertEquals(90.0, aircraft.getDeferredInitialHeadingDeg(), 0.01);

        aircraft.setAircraftPosition(
                new AircraftPosition(
                        new Latitude(START_LAT),
                        new Longitude(START_LON),
                        new Altitude(InitialClimbGeometry.turnReleaseAltitudeFt(DER_ELEV) + 1)));
        aircraft.calculateNextAircraftVector(1.0);
        assertEquals(90.0, aircraft.getInstructedVector().instructedHeading.toDouble(), 0.01);
        assertTrue(aircraft.isInitialClimbActive());
    }

    @Test
    @DisplayName("P-3 JOIN FINAL then setFlightPlan clears approach course; downwind heading matches instruction")
    void preexisting3_finalApproachStateSurvivesRouteAssignment() throws Exception {
        AircraftBase aircraft = bare("P3", 35.0, 139.0, 3000, 150, 180);
        aircraft.setAmbientWind(UniformWind.measured(250, 25));
        aircraft.joinFinalApproachIntercept(
                new FixPosition(new Latitude(35.1), new Longitude(139.1)), "FA", 149.88);
        var reached = AircraftBase.class.getDeclaredMethod("onDirectToTargetReached");
        reached.setAccessible(true);
        reached.invoke(aircraft);
        assertEquals(149.88, aircraft.getInstructedVector().instructedHeading.toDouble(), 0.01);

        double[] end = GeodeticUtils.offsetPointNm(35.0, 139.0, 90.0, 1.5);
        FlightPlan shortPlan = new FlightPlan(
                new Callsign("P3"),
                "RJTT",
                "RJTT",
                List.of(
                        new FlightPlanWaypoint(
                                "END",
                                new FixPosition(new Latitude(end[0]), new Longitude(end[1])),
                                null,
                                null,
                                AltitudeConstraint.NONE,
                                WaypointAction.CONTINUE)),
                new Altitude(3000),
                new GroundSpeed(180));
        aircraft.setFlightPlan(shortPlan);
        assertEquals(NavigationMode.FLIGHT_PLAN, aircraft.getNavigationMode());
        assertFalse(aircraft.isFinalApproachCoursePending());
        var lateralField = AircraftBase.class.getDeclaredField("lateral");
        lateralField.setAccessible(true);
        assertTrue(lateralField.get(aircraft) instanceof LateralNavigation.FlightPlanNav);

        // Exhaust the single-fix plan the same way production does at the last pass.
        var onPassed = AircraftBase.class.getDeclaredMethod("onWaypointPassed", FlightPlanWaypoint.class);
        onPassed.setAccessible(true);
        onPassed.invoke(aircraft, shortPlan.getWaypoints().get(0));
        assertEquals(NavigationMode.HEADING, aircraft.getNavigationMode());
        assertTrue(lateralField.get(aircraft) instanceof LateralNavigation.PlainHeading);

        aircraft.setInstructedVector(
                new InstructedVector(new Heading(90), new Altitude(3000), new GroundSpeed(180)));
        for (int i = 0; i < 120; i++) {
            tick(aircraft);
        }
        double heading = aircraft.getAircraftVector().heading.toDouble();
        double instructed = aircraft.getInstructedVector().instructedHeading.toDouble();
        double delta = Math.min(Math.abs(heading - instructed), 360.0 - Math.abs(heading - instructed));
        assertTrue(
                delta < 1.5,
                "residual approach WCA should be gone; heading=" + heading + " instructed=" + instructed);
    }

    private static ProcedureFlightPlanMapper.MappedProcedureRoute mapVamos() {
        ProcedureCatalog catalog = new ProcedureCatalog();
        ProcedureFlightPlanMapper mapper =
                new ProcedureFlightPlanMapper(new AtsRouteFixPositionRepository());
        return mapper.map(
                new Callsign("VA5A"),
                catalog.findById("VAMOS4.RWY34L_34R").orElseThrow(),
                Optional.empty(),
                9000,
                250);
    }

    private static FlightPlan singleFixPlan(String name, double lat, double lon) {
        return new FlightPlan(
                new Callsign("VA5"),
                "RJTT",
                "RJTT",
                List.of(
                        new FlightPlanWaypoint(
                                name,
                                new FixPosition(new Latitude(lat), new Longitude(lon)),
                                null,
                                null,
                                AltitudeConstraint.NONE,
                                WaypointAction.CONTINUE)),
                new Altitude(9000),
                new GroundSpeed(250));
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
