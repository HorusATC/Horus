package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.globals.GlobalVariables;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.departure.InitialClimbGeometry;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.RunwayCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class InitialClimbApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private RunwayCatalog runwayCatalog;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void setUp() {
        GlobalVariables.isSimulationRunning = false;
        aircraftRepository.clear();
    }

    @AfterEach
    void tearDown() {
        GlobalVariables.isSimulationRunning = false;
        aircraftRepository.clear();
    }

    @Test
    void departRjtt16rPlacesNearPhysicalStartAndSetsInitialClimb() {
        spawn("DEP16");
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/DEP16/depart",
                Map.of(
                        "airportIcao", "RJTT",
                        "runwayDesignator", "16R",
                        "climbAltitudeFt", 3000),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("initialClimb", true);

        ResponseEntity<List> locations = restTemplate.getForEntity(
                baseUrl() + "/aircraft/location/all", List.class);
        assertThat(locations.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> dep16 = ((List<Map<String, Object>>) (List<?>) locations.getBody()).stream()
                .filter(m -> "DEP16".equals(m.get("callsign")))
                .findFirst()
                .orElseThrow();
        assertThat(dep16.get("initialClimb")).isEqualTo(true);
        assertThat(dep16.get("goAround")).isEqualTo(false);

        var endpoints = runwayCatalog.resolveDepartureEndpoints(
                runwayCatalog.findRunway("RJTT", "16R").orElseThrow()).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> position = (Map<String, Object>) dep16.get("position");
        AircraftPosition placed = new AircraftPosition(
                new Latitude(((Number) position.get("latitude")).doubleValue()),
                new Longitude(((Number) position.get("longitude")).doubleValue()),
                new Altitude(0));
        AircraftPosition start = new AircraftPosition(
                new Latitude(endpoints.startLatitude()),
                new Longitude(endpoints.startLongitude()),
                new Altitude(0));
        assertThat(GeodeticUtils.calculateHorizontalDistance(placed, start)).isLessThan(0.1);
        assertThat(((Number) position.get("altitude")).doubleValue())
                .isCloseTo(InitialClimbGeometry.startAltitudeFt(endpoints.startElevationFt()),
                        org.assertj.core.data.Offset.offset(1.0));
        @SuppressWarnings("unchecked")
        Map<String, Object> instructed = (Map<String, Object>) dep16.get("instructedVector");
        assertThat(((Number) instructed.get("heading")).doubleValue()).isEqualTo(149.88);
    }

    @Test
    void departRjtt05SucceedsWhileJoinFinalRemainsRejected() {
        spawn("DEP05");
        ResponseEntity<Map> depart = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/DEP05/depart",
                Map.of(
                        "airportIcao", "RJTT",
                        "runwayDesignator", "05",
                        "climbAltitudeFt", 3000),
                Map.class);
        assertThat(depart.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(depart.getBody()).containsEntry("initialClimb", true);

        ResponseEntity<Map> joinFinal = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/DEP05/final-approach",
                Map.of("airportIcao", "RJTT", "runwayDesignator", "05"),
                Map.class);
        assertThat(joinFinal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(joinFinal.getBody().get("message"))).contains("not usable for landing");
    }

    @Test
    void departRejectsUnknownRunwayAndLowClimb() {
        spawn("DEPBAD");
        ResponseEntity<Map> unknown = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/DEPBAD/depart",
                Map.of(
                        "airportIcao", "RJTT",
                        "runwayDesignator", "99",
                        "climbAltitudeFt", 3000),
                Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> low = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/DEPBAD/depart",
                Map.of(
                        "airportIcao", "RJTT",
                        "runwayDesignator", "16R",
                        "climbAltitudeFt", 1000),
                Map.class);
        // 1000 may still be >= start+500 for RJTT 16R (~55 ft start) — use absolute floor via DTO min.
        // Use a value that fails domain rule: start ~55.5 → need < 555.5, but DTO min is 1000.
        // Domain reject for climb below start+500 is covered in unit test; here check unknown callsign path
        // with a known-low relative case by departing then re-departing with climb barely above start after teleport.
        assertThat(low.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> tooLowAfterPlace = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/DEPBAD/depart",
                Map.of(
                        "airportIcao", "RJTT",
                        "runwayDesignator", "16R",
                        "climbAltitudeFt", 400),
                Map.class);
        // @Min(1000) on DTO → 400 is validation 400
        assertThat(tooLowAfterPlace.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void spawn(String callsign) {
        restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/spawn-with-flightplan",
                Map.of(
                        "flightPlan", Map.of(
                                "callsign", callsign,
                                "cruiseAltitude", 10000,
                                "cruiseSpeed", 250,
                                "route", List.of(Map.of("fix", "ABENO", "action", "CONTINUE"))),
                        "initialPosition", Map.of(
                                "latitude", 35.6,
                                "longitude", 139.8,
                                "altitude", 5000,
                                "heading", 150,
                                "groundSpeed", 200,
                                "verticalSpeed", 0)),
                Map.class);
    }
}
