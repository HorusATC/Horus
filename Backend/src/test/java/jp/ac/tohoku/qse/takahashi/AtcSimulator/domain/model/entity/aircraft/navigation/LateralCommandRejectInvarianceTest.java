package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.behavior.FixedWingFlightBehavior;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.characteristics.AircraftCharacteristics;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlan;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldInstruction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldTurnDirection;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.WaypointAction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureVaConstraint;
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
 * Rejected lateral commands leave the aircraft snapshot-equal.
 */
class LateralCommandRejectInvarianceTest {

    private static final double START_LAT = 35.55998611;
    private static final double START_LON = 139.76906667;

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedCases")
    @DisplayName("rejected lateral command is snapshot-invariant")
    void rejectedCommandLeavesSnapshotEqual(String name, Setup setup, Action action) {
        AircraftBase aircraft = setup.prepare(bare());
        NavSnapshot before = NavSnapshot.capture(aircraft);
        assertThrows(RuntimeException.class, () -> action.run(aircraft));
        assertEquals(before, NavSnapshot.capture(aircraft), name);
    }

    static Stream<Arguments> rejectedCases() {
        return Stream.of(
                Arguments.of(
                        "towerHandoff/tooFar while InitialClimb",
                        (Setup) a -> {
                            a.beginInitialClimb(
                                    START_LAT, START_LON, 20.5, 149.88, 18.2, 90.0, 5000);
                            a.setAircraftPosition(
                                    new AircraftPosition(
                                            new Latitude(34.35),
                                            new Longitude(139.77),
                                            new Altitude(2000)));
                            return a;
                        },
                        (Action) a -> a.beginTowerHandoff(START_LAT, START_LON)),
                Arguments.of(
                        "towerHandoff/goAround",
                        (Setup) a -> {
                            a.beginGoAround(149.88, 6000);
                            return a;
                        },
                        (Action) a -> a.beginTowerHandoff(35.0, 139.08)),
                Arguments.of(
                        "towerHandoff/notHeading",
                        (Setup) a -> {
                            a.setFlightPlan(tinyPlan());
                            return a;
                        },
                        (Action) a -> a.beginTowerHandoff(35.0, 139.08)),
                Arguments.of(
                        "towerHandoff/afterHandoff",
                        (Setup) a -> {
                            a.beginTowerHandoff(35.0, 139.08);
                            return a;
                        },
                        (Action) a -> a.setInstructedVector(
                                new InstructedVector(
                                        new Heading(10), new Altitude(5000), new GroundSpeed(180)))),
                Arguments.of(
                        "goAround/altitudeTooLow",
                        (Setup) a -> a,
                        (Action) a -> a.beginGoAround(149.88, 5100)),
                Arguments.of(
                        "depart/altitudeTooLow",
                        (Setup) a -> a,
                        (Action) a -> a.beginInitialClimb(
                                START_LAT, START_LON, 20.5, 149.88, 18.2, null, 40)),
                Arguments.of(
                        "assignProcedure/nullPlan",
                        (Setup) a -> a,
                        (Action) a -> a.assignProcedureRoute(
                                null, new ProcedureVaConstraint(330, 700))),
                Arguments.of(
                        "hold then handoff after release",
                        (Setup) a -> {
                            a.setHoldAtFix(
                                    new FixPosition(new Latitude(35.1), new Longitude(139.1)),
                                    new HoldInstruction(
                                            "ABENO",
                                            HoldTurnDirection.RIGHT,
                                            360.0,
                                            new HoldLeg.DistanceNm(4.0)));
                            return a;
                        },
                        (Action) a -> a.beginTowerHandoff(35.0, 139.08)));
    }

    @FunctionalInterface
    interface Setup {
        AircraftBase prepare(AircraftBase aircraft);
    }

    @FunctionalInterface
    interface Action {
        void run(AircraftBase aircraft);
    }

    private static FlightPlan tinyPlan() {
        return new FlightPlan(
                new Callsign("RJ"),
                "RJTT",
                "RJTT",
                List.of(
                        new FlightPlanWaypoint(
                                "WP1",
                                new FixPosition(new Latitude(35.1), new Longitude(139.1)),
                                null,
                                null,
                                AltitudeConstraint.NONE,
                                WaypointAction.CONTINUE)),
                new Altitude(5000),
                new GroundSpeed(180));
    }

    private static AircraftBase bare() {
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
                new Callsign("RJ01"),
                new AircraftType("B738"),
                new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(5000)),
                new AircraftVector(new Heading(90), new GroundSpeed(180), new VerticalSpeed(0)),
                new FixedWingFlightBehavior(),
                characteristics) {};
    }

    record NavSnapshot(
            double lat,
            double lon,
            double alt,
            double hdg,
            double gs,
            double vs,
            double track,
            double tas,
            double instrHdg,
            double instrAlt,
            double instrGs,
            Integer ias,
            String mode,
            boolean va,
            Double vaCourse,
            Integer vaAltFt,
            boolean climb,
            Double climbLat,
            Double climbLon,
            Double climbRwy,
            Double deferred,
            boolean goAround,
            boolean tower,
            boolean finalPending,
            int wpIndex,
            String lateralType) {
        static NavSnapshot capture(AircraftBase a) {
            try {
                var lateralField = AircraftBase.class.getDeclaredField("lateral");
                lateralField.setAccessible(true);
                Object lateral = lateralField.get(a);
                return new NavSnapshot(
                        a.getAircraftPosition().latitude.toDouble(),
                        a.getAircraftPosition().longitude.toDouble(),
                        a.getAircraftPosition().altitude.toDouble(),
                        a.getAircraftVector().heading.toDouble(),
                        a.getAircraftVector().groundSpeed.toDouble(),
                        a.getAircraftVector().verticalSpeed.toDouble(),
                        a.getAircraftVector().trackDeg,
                        a.getAircraftVector().trueAirspeedKnots,
                        a.getInstructedVector().instructedHeading.toDouble(),
                        a.getInstructedVector().instructedAltitude.toDouble(),
                        a.getInstructedVector().instructedGroundSpeed.toDouble(),
                        a.getInstructedIndicatedAirspeedKnots(),
                        a.getNavigationMode().name(),
                        a.isProcedureVaActive(),
                        a.getProcedureVaCourseTrueDeg(),
                        a.getProcedureVaAltitudeFt(),
                        a.isInitialClimbActive(),
                        a.getInitialClimbStartLatitude(),
                        a.getInitialClimbStartLongitude(),
                        a.getInitialClimbRunwayHeadingDeg(),
                        a.getDeferredInitialHeadingDeg(),
                        a.isGoAroundActive(),
                        a.isTowerHandoffActive(),
                        a.isFinalApproachCoursePending(),
                        a.getCurrentWaypointIndex(),
                        lateral.getClass().getName());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
