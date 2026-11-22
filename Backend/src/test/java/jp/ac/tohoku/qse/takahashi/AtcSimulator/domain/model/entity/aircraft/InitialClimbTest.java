package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.behavior.FixedWingFlightBehavior;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.characteristics.AircraftCharacteristics;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.departure.InitialClimbGeometry;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Heading;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.VerticalSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.InstructedVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;

class InitialClimbTest {

    private static final double START_LAT = 35.55998611;
    private static final double START_LON = 139.76906667;
    private static final double START_ELEV = 20.5;
    private static final double RUNWAY_HDG = 149.88;
    private static final double DER_ELEV = 18.2;

    @Test
    void departPlacesAtPhysicalStartWithScreenHeightAndDefaultGs() {
        AircraftBase aircraft = createTestAircraft();
        aircraft.beginInitialClimb(START_LAT, START_LON, START_ELEV, RUNWAY_HDG, DER_ELEV, null, 3000);

        assertEquals(NavigationMode.HEADING, aircraft.getNavigationMode());
        assertTrue(aircraft.isInitialClimbActive());
        assertFalse(aircraft.isGoAroundActive());
        assertEquals(START_LAT, aircraft.getAircraftPosition().latitude.toDouble(), 1e-7);
        assertEquals(START_LON, aircraft.getAircraftPosition().longitude.toDouble(), 1e-7);
        assertEquals(
                InitialClimbGeometry.startAltitudeFt(START_ELEV),
                aircraft.getAircraftPosition().altitude.toDouble(),
                0.01);
        assertEquals(RUNWAY_HDG, aircraft.getInstructedVector().instructedHeading.toDouble(), 0.01);
        assertEquals(3000, aircraft.getInstructedVector().instructedAltitude.toDouble(), 0.01);
        assertEquals(
                InitialClimbGeometry.DEFAULT_DEPARTURE_GROUND_SPEED_KT,
                aircraft.getInstructedVector().instructedGroundSpeed.toDouble(),
                0.01);
        assertEquals(RUNWAY_HDG, aircraft.getAircraftVector().heading.toDouble(), 0.01);
        assertNull(aircraft.getInstructedIndicatedAirspeedKnots());
    }

    @Test
    void climbBelowStartPlus500IsRejected() {
        AircraftBase aircraft = createTestAircraft();
        assertThrows(
                IllegalArgumentException.class,
                () -> aircraft.beginInitialClimb(
                        START_LAT, START_LON, START_ELEV, RUNWAY_HDG, DER_ELEV, null, 400));
        assertFalse(aircraft.isInitialClimbActive());
    }

    @Test
    void deferredHeadingReleasedAfterDerPlus400() {
        AircraftBase aircraft = createTestAircraft();
        aircraft.beginInitialClimb(START_LAT, START_LON, START_ELEV, RUNWAY_HDG, DER_ELEV, 090.0, 5000);
        assertEquals(RUNWAY_HDG, aircraft.getInstructedVector().instructedHeading.toDouble(), 0.01);

        // Still below DER+400
        aircraft.setAircraftPosition(new AircraftPosition(
                new Latitude(START_LAT),
                new Longitude(START_LON),
                new Altitude(InitialClimbGeometry.turnReleaseAltitudeFt(DER_ELEV) - 10)));
        aircraft.calculateNextAircraftVector(1.0);
        assertEquals(RUNWAY_HDG, aircraft.getInstructedVector().instructedHeading.toDouble(), 0.01);

        aircraft.setAircraftPosition(new AircraftPosition(
                new Latitude(START_LAT),
                new Longitude(START_LON),
                new Altitude(InitialClimbGeometry.turnReleaseAltitudeFt(DER_ELEV) + 1)));
        aircraft.calculateNextAircraftVector(1.0);
        assertEquals(90.0, aircraft.getInstructedVector().instructedHeading.toDouble(), 0.01);
        assertTrue(aircraft.isInitialClimbActive());
    }

    @Test
    void headingInstructionClearsInitialClimb() {
        AircraftBase aircraft = createTestAircraft();
        aircraft.beginInitialClimb(START_LAT, START_LON, START_ELEV, RUNWAY_HDG, DER_ELEV, null, 3000);
        aircraft.setNavigationMode(NavigationMode.HEADING);
        aircraft.setInstructedVector(
                new InstructedVector(new Heading(90), new Altitude(7000), new GroundSpeed(200)));
        assertFalse(aircraft.isInitialClimbActive());
        assertNull(aircraft.getInitialClimbStartLatitude());
    }

    @Test
    void keepsIndicatedAirspeedWhenPresent() {
        AircraftBase aircraft = createTestAircraft();
        aircraft.setInstructedVector(
                new InstructedVector(new Heading(90), new Altitude(5000), new GroundSpeed(180)),
                160);
        aircraft.beginInitialClimb(START_LAT, START_LON, START_ELEV, RUNWAY_HDG, DER_ELEV, null, 3000);
        assertEquals(160, aircraft.getInstructedIndicatedAirspeedKnots());
        assertEquals(180, aircraft.getInstructedVector().instructedGroundSpeed.toDouble(), 0.01);
    }

    private static AircraftBase createTestAircraft() {
        var characteristics = new AircraftCharacteristics(3.0, 3.0, 1640.0, 500.0, 140.0, 42000.0, 0.0,
                AircraftCharacteristics.AircraftCategory.COMMERCIAL_PASSENGER);
        return new AircraftBase(
                new Callsign("DEP01"),
                new AircraftType("B738"),
                new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(5000)),
                new AircraftVector(new Heading(0), new GroundSpeed(250), new VerticalSpeed(0)),
                new FixedWingFlightBehavior(),
                characteristics) {
        };
    }
}
