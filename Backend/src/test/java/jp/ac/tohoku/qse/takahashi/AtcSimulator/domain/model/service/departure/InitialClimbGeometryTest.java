package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.departure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InitialClimbGeometryTest {

    @Test
    void startAltitudeIsThresholdPlusScreenHeight() {
        assertEquals(55.5, InitialClimbGeometry.startAltitudeFt(20.5), 0.01);
    }

    @Test
    void turnReleaseIsDerPlus400() {
        assertEquals(418.2, InitialClimbGeometry.turnReleaseAltitudeFt(18.2), 0.01);
    }

    @Test
    void climbMustExceedStartBy500() {
        assertTrue(InitialClimbGeometry.climbAltitudeValid(3000, 55.5));
        assertFalse(InitialClimbGeometry.climbAltitudeValid(500, 55.5));
    }

    @Test
    void headingsEqualWithinEpsilon() {
        assertTrue(InitialClimbGeometry.headingsEqual(149.88, 149.88));
        assertTrue(InitialClimbGeometry.headingsEqual(149.88, 150.2));
        assertFalse(InitialClimbGeometry.headingsEqual(149.88, 090.0));
    }

    @Test
    void corridorContainsOnAxisPointWithinFiveNm() {
        double startLat = 35.55998611;
        double startLon = 139.76906667;
        double heading = 149.88;
        // ~2 NM along runway heading
        double lat = 35.531;
        double lon = 139.790;
        double bearing = InitialClimbGeometry.bearingDeg(startLat, startLon, lat, lon);
        // Use synthetic: projected distance along heading
        assertTrue(InitialClimbGeometry.isWithinDepartureCorridor(
                startLat, startLon, heading, lat, lon, 2.0, heading));
        assertFalse(InitialClimbGeometry.isWithinDepartureCorridor(
                startLat, startLon, heading, lat, lon, 6.0, heading));
        assertFalse(InitialClimbGeometry.isWithinDepartureCorridor(
                startLat, startLon, heading, lat, lon, 2.0, heading + 180.0));
        assertFalse(InitialClimbGeometry.isWithinDepartureCorridor(
                startLat, startLon, heading, lat, lon, 2.0, heading + 90.0));
        voidUnused(bearing);
    }

    private static void voidUnused(double value) {
        // bearing computed for documentation of helper; corridor test uses known headings
        assertTrue(value >= 0.0);
    }
}
