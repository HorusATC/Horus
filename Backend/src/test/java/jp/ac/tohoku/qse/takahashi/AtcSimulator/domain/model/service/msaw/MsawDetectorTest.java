package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.Aircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.airport.RunwayDefinition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.*;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Msaw.MsawAlert;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Msaw.MsawAlertType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Msaw.MsawTrigger;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.MsaCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.RunwayCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

@DisplayName("MSAW Detector Tests")
class MsawDetectorTest {

    private static final double RJTT_THRESHOLD_LAT = 35.55624167;
    private static final double RJTT_THRESHOLD_LON = 139.77171944;
    private static final double RJTT_APPROACH_HEADING = 149.88;
    private static final double RJTT_ELEVATION_FT = 20.0;
    private static final double FLOOR_GRADIENT_FT_PER_NM = 300.0;

    private MsawDetector msawDetector;
    private MsaCatalog msaCatalog;
    private RunwayCatalog runwayCatalog;

    @BeforeEach
    void setUp() {
        msaCatalog = new MsaCatalog();
        runwayCatalog = new RunwayCatalog();
        msawDetector = new MsawDetector(msaCatalog, runwayCatalog);
    }

    @Nested
    @DisplayName("Area MSA Detection Tests")
    class AreaMsaTests {

        @Test
        @DisplayName("Aircraft below MSA in Kanto plain area triggers MSA alert (current altitude)")
        void testBelowMsaCurrentAltitude() {
            Aircraft aircraft = createAircraft("TEST001",
                    35.5, 139.5, 2000,
                    90, 250, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNotNull(alert, "Alert should be triggered when below MSA");
            assertEquals(MsawAlertType.MSA, alert.type());
            assertEquals(2500.0, alert.msaFt());
            assertEquals(MsawTrigger.CURRENT, alert.trigger());
        }

        @Test
        @DisplayName("Aircraft at or above MSA does not trigger alert")
        void testAtOrAboveMsa() {
            Aircraft aircraft = createAircraft("TEST002",
                    35.5, 139.5, 2500,
                    90, 250, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNull(alert, "No alert when at or above MSA");
        }

        @Test
        @DisplayName("Aircraft will descend below MSA in 45 seconds triggers PREDICTED alert")
        void testPredictedViolation() {
            Aircraft aircraft = createAircraft("TEST003",
                    35.5, 139.5, 3000,
                    90, 250, -800);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNotNull(alert, "Alert should be triggered for predicted violation");
            assertEquals(MsawAlertType.MSA, alert.type());
            assertEquals(MsawTrigger.PREDICTED, alert.trigger());
        }

        @Test
        @DisplayName("Aircraft above MSA and not descending does not trigger alert")
        void testAboveMsaNotDescending() {
            Aircraft aircraft = createAircraft("TEST004",
                    35.5, 139.5, 10000,
                    90, 250, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNull(alert, "No alert when well above MSA");
        }

        @Test
        @DisplayName("Aircraft in west mountains area uses higher MSA (10000 ft)")
        void testWestMountainsHigherMsa() {
            Aircraft aircraft = createAircraft("TEST005",
                    35.2, 138.8, 9000,
                    90, 250, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNotNull(alert, "Alert should trigger in mountain area");
            assertEquals(MsawAlertType.MSA, alert.type());
            assertEquals(10000.0, alert.msaFt());
        }

        @Test
        @DisplayName("Overlapping polygons use highest MSA")
        void testOverlappingPolygonsUseHighest() {
            List<MsaCatalog.LatLon> square = List.of(
                    new MsaCatalog.LatLon(35.0, 139.0),
                    new MsaCatalog.LatLon(36.0, 139.0),
                    new MsaCatalog.LatLon(36.0, 140.0),
                    new MsaCatalog.LatLon(35.0, 140.0));
            MsaCatalog overlappingCatalog = new MsaCatalog(
                    45.0, 1.0, 1.0, 10.0, 300.0,
                    List.of(),
                    List.of(
                            new MsaCatalog.MsaArea("low", "low", 2500.0, square),
                            new MsaCatalog.MsaArea("high", "high", 8000.0, square)));
            MsawDetector overlappingDetector = new MsawDetector(overlappingCatalog, runwayCatalog);

            Aircraft aircraft = createAircraft("TEST_OVERLAP",
                    35.5, 139.5, 5000,
                    90, 250, 0);
            MsawAlert alert = overlappingDetector.detectAlert(aircraft);

            assertNotNull(alert, "Should alert when below the higher overlapping MSA");
            assertEquals(8000.0, alert.msaFt(), "Higher overlapping MSA must win");
            assertEquals(MsawAlertType.MSA, alert.type());
        }

        @Test
        @DisplayName("Aircraft outside all areas does not trigger alert")
        void testOutsideAllAreas() {
            Aircraft aircraft = createAircraft("TEST006",
                    40.0, 145.0, 1000,
                    90, 250, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNull(alert, "No alert when outside defined areas");
        }
    }

    @Nested
    @DisplayName("Approach Corridor LOW Tests")
    class ApproachCorridorTests {

        @Test
        @DisplayName("Aircraft below approach floor at ~5 NM on final triggers LOW")
        void testBelowApproachFloorAtFiveNm() {
            double distanceNm = 5.0;
            double floorFt = RJTT_ELEVATION_FT + FLOOR_GRADIENT_FT_PER_NM * distanceNm;
            double[] point = pointOnFinalNm(distanceNm);
            Aircraft aircraft = createAircraft("TEST_APPR_LOW",
                    point[0], point[1], floorFt - 200,
                    RJTT_APPROACH_HEADING, 150, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNotNull(alert, "Must alert LOW when below approach floor outside suppression");
            assertEquals(MsawAlertType.LOW, alert.type());
            assertEquals(MsawTrigger.CURRENT, alert.trigger());
            assertEquals(floorFt, alert.msaFt(), 1.0);
        }

        @Test
        @DisplayName("Proper 3° ILS altitude at ~5 NM (~1600 ft) does not trigger alert")
        void testProperIlsAtFiveNm() {
            double distanceNm = 5.0;
            double[] point = pointOnFinalNm(distanceNm);
            // On-path ~3°: elevation + 300*5 = 1520; use ~1600 ft as a proper ILS altitude
            Aircraft aircraft = createAircraft("TEST_APPR_ILS",
                    point[0], point[1], 1600,
                    RJTT_APPROACH_HEADING, 150, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNull(alert, "Proper ILS altitude at 5 NM must not alert");
        }

        @Test
        @DisplayName("Aircraft exactly at approach floor does not trigger alert")
        void testExactlyAtApproachFloor() {
            double distanceNm = 5.0;
            double floorFt = RJTT_ELEVATION_FT + FLOOR_GRADIENT_FT_PER_NM * distanceNm;
            double[] point = pointOnFinalNm(distanceNm);
            Aircraft aircraft = createAircraft("TEST_APPR_EXACT",
                    point[0], point[1], floorFt,
                    RJTT_APPROACH_HEADING, 150, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNull(alert, "Exactly-at-floor must be treated as no alert");
        }
    }

    @Nested
    @DisplayName("Suppression Zone Tests")
    class SuppressionZoneTests {

        @Test
        @DisplayName("Aircraft at 0.9 NM from threshold below floor is suppressed")
        void testInsideSuppressionZoneAtZeroPointNineNm() {
            double distanceNm = 0.9;
            double floorFt = RJTT_ELEVATION_FT + FLOOR_GRADIENT_FT_PER_NM * distanceNm;
            double[] point = pointOnFinalNm(distanceNm);
            Aircraft aircraft = createAircraft("TEST_SUPP_IN",
                    point[0], point[1], Math.max(50, floorFt - 100),
                    RJTT_APPROACH_HEADING, 80, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNull(alert, "Within 1.0 NM of threshold must suppress MSAW");
        }

        @Test
        @DisplayName("Aircraft at 2.0 NM on 16R final below floor triggers LOW (outside all threshold suppressions)")
        void testOutsideAllSuppressionZonesOnFinal() {
            // With 12 catalog ends, 1.1 NM on 16R final is still inside RWY 22's 1 NM suppression.
            double distanceNm = 2.0;
            double floorFt = RJTT_ELEVATION_FT + FLOOR_GRADIENT_FT_PER_NM * distanceNm;
            double[] point = pointOnFinalNm(distanceNm);
            Aircraft aircraft = createAircraft("TEST_SUPP_OUT",
                    point[0], point[1], floorFt - 100,
                    RJTT_APPROACH_HEADING, 150, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNotNull(alert, "Outside every 1.0 NM threshold suppression must alert when below floor");
            assertEquals(MsawAlertType.LOW, alert.type());
            assertEquals(MsawTrigger.CURRENT, alert.trigger());
        }

        @Test
        @DisplayName("initialClimb within departure corridor suppresses MSAW; outside corridor does not")
        void testInitialClimbDepartureCorridorSuppression() {
            var rwy = runwayCatalog.findRunway("RJTT", "16R").orElseThrow();
            var endpoints = runwayCatalog.resolveDepartureEndpoints(rwy).orElseThrow();
            CommercialAircraft aircraft = (CommercialAircraft) createAircraft(
                    "TEST_DEP_CORR",
                    endpoints.startLatitude(),
                    endpoints.startLongitude(),
                    200,
                    endpoints.runwayHeadingDeg(),
                    180,
                    0);
            aircraft.beginInitialClimb(
                    endpoints.startLatitude(),
                    endpoints.startLongitude(),
                    endpoints.startElevationFt(),
                    endpoints.runwayHeadingDeg(),
                    endpoints.derElevationFt(),
                    null,
                    3000);

            double[] inside = GeodeticUtils.offsetPointNm(
                    endpoints.startLatitude(),
                    endpoints.startLongitude(),
                    endpoints.runwayHeadingDeg(),
                    3.0);
            aircraft.setAircraftPosition(new AircraftPosition(
                    new Latitude(inside[0]),
                    new Longitude(inside[1]),
                    new Altitude(200)));
            assertNull(msawDetector.detectAlert(aircraft),
                    "initialClimb inside 5 NM × ±1 NM departure corridor must suppress MSAW");

            double[] outside = GeodeticUtils.offsetPointNm(
                    endpoints.startLatitude(),
                    endpoints.startLongitude(),
                    endpoints.runwayHeadingDeg(),
                    6.0);
            aircraft.setAircraftPosition(new AircraftPosition(
                    new Latitude(outside[0]),
                    new Longitude(outside[1]),
                    new Altitude(200)));
            MsawAlert outsideAlert = msawDetector.detectAlert(aircraft);
            assertNotNull(outsideAlert,
                    "Beyond 5 NM departure corridor MSAW must apply with existing rules");
        }
    }

    @Nested
    @DisplayName("Threshold Inversion Tests - Ensuring correct logic")
    class ThresholdInversionTests {

        @Test
        @DisplayName("Inverting threshold (alert when above MSA) should fail test")
        void testInvertedThresholdFails() {
            Aircraft aboveMsa = createAircraft("TEST_ABOVE",
                    35.5, 139.5, 5000,
                    90, 250, 0);

            MsawAlert alert = msawDetector.detectAlert(aboveMsa);

            assertNull(alert, "Aircraft above MSA should NOT trigger alert - if this fails, threshold logic is inverted");
        }

        @Test
        @DisplayName("Aircraft exactly at MSA boundary does not trigger alert")
        void testExactlyAtMsaBoundary() {
            Aircraft aircraft = createAircraft("TEST_BOUNDARY",
                    35.5, 139.5, 2500,
                    90, 250, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNull(alert, "Aircraft exactly at MSA should not trigger alert");
        }

        @Test
        @DisplayName("Aircraft 1 foot below MSA triggers alert")
        void testOneFootBelowMsa() {
            Aircraft aircraft = createAircraft("TEST_ONE_BELOW",
                    35.5, 139.5, 2499,
                    90, 250, 0);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNotNull(alert, "Aircraft 1 foot below MSA should trigger alert");
            assertEquals(MsawAlertType.MSA, alert.type());
        }
    }

    @Nested
    @DisplayName("Null and Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null aircraft returns null alert")
        void testNullAircraft() {
            MsawAlert alert = msawDetector.detectAlert(null);

            assertNull(alert, "Null aircraft should return null alert");
        }

        @Test
        @DisplayName("Aircraft climbing away from low altitude clears alert")
        void testClimbingAway() {
            Aircraft aircraft = createAircraft("TEST_CLIMB",
                    35.5, 139.5, 2400,
                    90, 250, 1500);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNotNull(alert, "Should alert for current low altitude");
            assertEquals(MsawTrigger.CURRENT, alert.trigger());
        }

        @Test
        @DisplayName("isBelowMinimum treats exact floor and within epsilon as not below")
        void testAltitudeFloorEpsilon() {
            assertFalse(MsawDetector.isBelowMinimum(1520.0, 1520.0));
            assertFalse(MsawDetector.isBelowMinimum(1520.0, 1520.0 + MsawDetector.ALTITUDE_FLOOR_EPSILON_FT));
            assertTrue(MsawDetector.isBelowMinimum(1520.0 - MsawDetector.ALTITUDE_FLOOR_EPSILON_FT - 0.01, 1520.0));
        }
    }

    /**
     * Point on the RJTT 16R final, {@code distanceNm} outbound from the threshold
     * along the reciprocal of the approach course (same geometry as RunwayCatalog).
     */
    private double[] pointOnFinalNm(double distanceNm) {
        RunwayDefinition rjtt = runwayCatalog.findRunway("RJTT", "16R")
                .orElseThrow(() -> new IllegalStateException("RJTT 16R missing from RunwayCatalog"));
        var geometry = runwayCatalog.buildFinalApproachGeometry(rjtt)
                .orElseThrow(() -> new IllegalStateException("RJTT final geometry missing"));
        double outbound = (geometry.approachCourseHeading() + 180.0) % 360.0;
        return GeodeticUtils.offsetPointNm(
                RJTT_THRESHOLD_LAT, RJTT_THRESHOLD_LON, outbound, distanceNm);
    }

    private Aircraft createAircraft(String callsign, double lat, double lon, double altFt,
                                    double heading, double groundSpeed, double verticalSpeed) {
        return new CommercialAircraft(
                new Callsign(callsign),
                new AircraftType("B738"),
                new AircraftPosition(
                        new Latitude(lat),
                        new Longitude(lon),
                        new Altitude(altFt)
                ),
                new AircraftVector(
                        new Heading(heading),
                        new GroundSpeed(groundSpeed),
                        new VerticalSpeed(verticalSpeed)
                ),
                "HND", "RJTT",
                "NRT", "RJAA",
                "2024-12-13T15:00:00Z"
        );
    }
}
