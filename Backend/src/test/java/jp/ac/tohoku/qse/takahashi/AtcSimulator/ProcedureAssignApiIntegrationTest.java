package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProcedureAssignApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AircraftRepository aircraftRepository;

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
    void listProceduresReturnsRjttCatalog() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/procedures?airport=RJTT", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("sourceNotice").toString())
                .contains("Derived from AIP Japan (AIRAC 1 Nov 2026)");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> procedures =
                (List<Map<String, Object>>) response.getBody().get("procedures");
        assertThat(procedures).isNotEmpty();
        assertThat(procedures.stream().anyMatch(p -> "OSHIMA1A".equals(p.get("id")))).isTrue();
    }

    @Test
    void unknownProcedureDetailIs404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/procedures/DOES-NOT-EXIST", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignOshima1aSetsFlightPlanRoute() {
        spawn("STAR01", 34.75, 139.45, 14000);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/STAR01/assign-procedure",
                Map.of("procedureId", "OSHIMA1A", "cruiseAltitude", 13000, "cruiseSpeed", 250),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("procedureId", "OSHIMA1A");
        @SuppressWarnings("unchecked")
        List<String> waypoints = (List<String>) response.getBody().get("waypoints");
        assertThat(waypoints)
                .containsExactly(
                        "XAC", "ANZAC", "TT450", "TT451", "TT452", "TT453", "WANDA", "WEDGE", "ARLON");

        AircraftBase aircraft = (AircraftBase) aircraftRepository.findByCallsign(new Callsign("STAR01"));
        assertThat(aircraft.getNavigationMode()).isEqualTo(NavigationMode.FLIGHT_PLAN);
        assertThat(aircraft.getFlightPlan().getWaypoints()).hasSize(9);
        assertThat(aircraft.isProcedureVaActive()).isFalse();

        ResponseEntity<Map> plan = restTemplate.getForEntity(
                baseUrl() + "/api/aircraft/STAR01/flightplan", Map.class);
        assertThat(plan.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> remaining = (List<String>) plan.getBody().get("remainingWaypoints");
        assertThat(remaining.get(0)).isEqualTo("XAC");
    }

    @Test
    void assignUnknownProcedureIs404() {
        spawn("STAR02", 34.75, 139.45, 14000);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/STAR02/assign-procedure",
                Map.of("procedureId", "NOPE"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignUnknownCallsignIs404() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/GHOST/assign-procedure",
                Map.of("procedureId", "OSHIMA1A"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignVamos34ActivatesVaTrueCourse330ThenReleasesToFlightPlan() {
        spawn("SID34", 35.52, 139.78, 200);
        ResponseEntity<Map> assign = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/SID34/assign-procedure",
                Map.of(
                        "procedureId", "VAMOS4.RWY34L_34R",
                        "cruiseAltitude", 9000,
                        "cruiseSpeed", 250),
                Map.class);
        assertThat(assign.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assign.getBody()).containsEntry("procedureVaActive", true);
        assertThat(((Number) assign.getBody().get("procedureVaCourseTrueDeg")).doubleValue())
                .isCloseTo(330.0, within(0.01));
        assertThat(assign.getBody()).containsEntry("procedureVaAltitudeFt", 700);
        @SuppressWarnings("unchecked")
        List<String> waypoints = (List<String>) assign.getBody().get("waypoints");
        assertThat(waypoints).containsExactly("TT502", "LOCUP", "VAMOS");

        AircraftBase aircraft = (AircraftBase) aircraftRepository.findByCallsign(new Callsign("SID34"));
        assertThat(aircraft.getNavigationMode()).isEqualTo(NavigationMode.HEADING);
        assertThat(aircraft.isProcedureVaActive()).isTrue();
        assertThat(aircraft.getInstructedVector().instructedHeading.toDouble())
                .isCloseTo(330.0, within(0.01));

        // Climb past VA altitude while keeping VA active until the tick releases it.
        GlobalVariables.isSimulationRunning = true;
        for (int i = 0; i < 400 && aircraft.isProcedureVaActive(); i++) {
            aircraft.calculateNextAircraftVector(1.0);
            aircraft.calculateNextAircraftPosition(1.0);
        }
        assertThat(aircraft.getAircraftPosition().altitude.toDouble()).isGreaterThanOrEqualTo(600.0);
        if (aircraft.getAircraftPosition().altitude.toDouble() >= 600.0) {
            // Force altitude above VA threshold and tick once more.
            aircraft.getAircraftPosition();
        }
        // Drive altitude above 700 by repeated vectors with high climb target already set.
        while (aircraft.getAircraftPosition().altitude.toDouble() < 700.0) {
            aircraft.calculateNextAircraftVector(1.0);
            aircraft.calculateNextAircraftPosition(1.0);
        }
        aircraft.calculateNextAircraftVector(1.0);
        assertThat(aircraft.isProcedureVaActive()).isFalse();
        assertThat(aircraft.getNavigationMode()).isEqualTo(NavigationMode.FLIGHT_PLAN);
        assertThat(aircraft.getFlightPlan().getWaypoints().get(0).getFixName()).isEqualTo("TT502");
        assertThat(aircraft.getFlightPlan().getWaypoints().get(1).getFixName()).isEqualTo("LOCUP");
        assertThat(aircraft.getFlightPlan().getWaypoints().get(1).getTargetAltitude().toDouble())
                .isEqualTo(5000.0);
        assertThat(aircraft.getFlightPlan().getWaypoints().get(2).getFixName()).isEqualTo("VAMOS");
        assertThat(aircraft.getFlightPlan().getWaypoints().get(2).getTargetAltitude().toDouble())
                .isEqualTo(9000.0);
    }

    @Test
    void visipIsNotAssignable() {
        spawn("SIDV", 35.52, 139.78, 1000);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/SIDV/assign-procedure",
                Map.of("procedureId", "VISIP1"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void spawn(String callsign, double lat, double lon, int alt) {
        Map<String, Object> body = Map.of(
                "flightPlan",
                Map.of(
                        "callsign", callsign,
                        "aircraftType", "B738",
                        "departureAirport", "RJTT",
                        "arrivalAirport", "RJTT",
                        "cruiseAltitude", alt,
                        "cruiseSpeed", 250,
                        "route", List.of()),
                "initialPosition",
                Map.of(
                        "latitude", lat,
                        "longitude", lon,
                        "altitude", alt,
                        "heading", 330,
                        "groundSpeed", 220,
                        "verticalSpeed", 0));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/spawn-with-flightplan", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
