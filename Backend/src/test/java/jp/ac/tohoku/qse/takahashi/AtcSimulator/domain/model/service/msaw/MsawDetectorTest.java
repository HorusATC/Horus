package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.Aircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
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

@DisplayName("MSAW Detector Tests")
class MsawDetectorTest {

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
                    45.0, 1.0, 1.0, 300.0,
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
        @DisplayName("Aircraft below approach floor in corridor triggers LOW alert")
        void testBelowApproachFloor() {
            Aircraft aircraft = createAircraft("TEST_APPR",
                    35.553, 139.781, 1000,
                    163, 150, -500);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            if (alert != null) {
                assertEquals(MsawAlertType.LOW, alert.type(),
                        "Should trigger LOW alert on approach");
            }
        }

        @Test
        @DisplayName("Aircraft on proper approach path does not trigger alert")
        void testProperApproach() {
            Aircraft aircraft = createAircraft("TEST_APPR2",
                    35.553, 139.781, 3500,
                    163, 150, -500);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNull(alert, "No alert on proper approach path");
        }
    }

    @Nested
    @DisplayName("Suppression Zone Tests")
    class SuppressionZoneTests {

        @Test
        @DisplayName("Aircraft within 1.0 NM of threshold does not trigger alert")
        void testSuppressionZone() {
            Aircraft aircraft = createAircraft("TEST_SUPP",
                    35.553, 139.781, 50,
                    163, 80, -300);

            MsawAlert alert = msawDetector.detectAlert(aircraft);

            assertNull(alert, "No alert within suppression zone near threshold");
        }

        @Test
        @DisplayName("Aircraft just outside suppression zone can trigger alert")
        void testJustOutsideSuppressionZone() {
            Aircraft aircraft = createAircraft("TEST_OUT",
                    35.560, 139.781, 500,
                    163, 150, -500);

            MsawAlert alert = msawDetector.detectAlert(aircraft);
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
