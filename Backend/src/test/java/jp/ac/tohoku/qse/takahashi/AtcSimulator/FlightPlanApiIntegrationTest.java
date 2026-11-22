package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.globals.GlobalVariables;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class FlightPlanApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private Map<String, Object> defaultInitialPosition() {
        return Map.of(
                "latitude", 35.0,
                "longitude", 139.0,
                "altitude", 5000,
                "heading", 90,
                "groundSpeed", 250,
                "verticalSpeed", 0
        );
    }

    private void spawnWithFlightPlan(String callsign, List<Map<String, Object>> route) {
        var flightPlan = Map.of(
                "callsign", callsign,
                "cruiseAltitude", 35000,
                "cruiseSpeed", 450,
                "route", route
        );
        restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/spawn-with-flightplan",
                Map.of("flightPlan", flightPlan, "initialPosition", defaultInitialPosition()),
                Map.class
        );
    }

    private void spawnWithSingleWaypointFlightPlan(String callsign, String fixName) {
        spawnWithFlightPlan(
                callsign,
                List.of(Map.of("fix", fixName, "action", "CONTINUE"))
        );
    }

    private static Stream<Arguments> holdTurnCases() {
        return Stream.of(
                Arguments.of("RIGHT", HttpStatus.OK, true, "HOLDING"),
                Arguments.of("LEFT", HttpStatus.OK, true, "HOLDING")
        );
    }

    @BeforeEach
    void setUp() {
        GlobalVariables.isSimulationRunning = false;
    }

    @AfterEach
    void tearDown() {
        GlobalVariables.isSimulationRunning = false;
        aircraftRepository.clear();
    }

    @Test
    @DisplayName("POST /api/aircraft/spawn-with-flightplan spawns aircraft with flight plan")
    void spawnWithFlightPlan_createsAircraft() {
        var flightPlan = Map.of(
                "callsign", "FPAPI01",
                "aircraftType", "B738",
                "departureAirport", "RJTT",
                "arrivalAirport", "RJOO",
                "cruiseAltitude", 35000,
                "cruiseSpeed", 450,
                "route", List.of(
                        Map.of("fix", "ABENO", "action", "CONTINUE"),
                        Map.of("fix", "MAIKO", "action", "CONTINUE")
                )
        );
        var initialPosition = Map.of(
                "latitude", 35.0,
                "longitude", 139.0,
                "altitude", 5000,
                "heading", 90,
                "groundSpeed", 250,
                "verticalSpeed", 0
        );
        var body = Map.of("flightPlan", flightPlan, "initialPosition", initialPosition);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/spawn-with-flightplan",
                body,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("callsign", "FPAPI01");
        assertThat(aircraftRepository.isAircraftExist(new Callsign("FPAPI01"))).isTrue();
    }

    @Test
    @DisplayName("GET /api/aircraft/{callsign}/flightplan returns flight plan status")
    void getFlightPlan_returnsStatus() {
        spawnWithSingleWaypointFlightPlan("FPAPI01", "ABENO");

        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/aircraft/FPAPI01/flightplan",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("callsign");
        assertThat(response.getBody()).containsKey("navigationMode");
        assertThat(response.getBody()).containsKey("remainingWaypoints");
    }

    @Test
    @DisplayName("POST /api/aircraft/{callsign}/direct-to applies direct to")
    void directTo_appliesInstruction() {
        spawnWithSingleWaypointFlightPlan("FPAPI01", "ABENO");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/FPAPI01/direct-to",
                Map.of("fixName", "ABENO", "resumeFlightPlan", false),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("targetFix", "ABENO");
    }

    @Test
    @DisplayName("POST /api/scenario/load succeeds with multiple aircraft")
    void loadScenario_successWithMultipleAircraft() {
        var ac1 = Map.of(
                "flightPlan", Map.of(
                        "callsign", "SCN01",
                        "departureAirport", "RJTT",
                        "arrivalAirport", "RJAA",
                        "cruiseAltitude", 35000,
                        "cruiseSpeed", 450,
                        "route", List.<Map<String, Object>>of()
                ),
                "initialPosition", Map.of(
                        "latitude", 35.0, "longitude", 139.0, "altitude", 5000,
                        "heading", 90, "groundSpeed", 250, "verticalSpeed", 0
                )
        );
        var ac2 = Map.of(
                "flightPlan", Map.of(
                        "callsign", "SCN02",
                        "departureAirport", "RJAA",
                        "arrivalAirport", "RJTT",
                        "cruiseAltitude", 36000,
                        "cruiseSpeed", 460,
                        "route", List.<Map<String, Object>>of()
                ),
                "initialPosition", Map.of(
                        "latitude", 35.01, "longitude", 139.01, "altitude", 5100,
                        "heading", 180, "groundSpeed", 260, "verticalSpeed", 0
                )
        );
        var scenario = Map.of(
                "scenarioName", "Test Scenario",
                "aircraft", List.of(ac1, ac2)
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenario,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("aircraftCount", 2);
        assertThat(response.getBody()).containsEntry("scenarioName", "Test Scenario");
        assertThat(aircraftRepository.isAircraftExist(new Callsign("SCN01"))).isTrue();
        assertThat(aircraftRepository.isAircraftExist(new Callsign("SCN02"))).isTrue();
    }

    @Test
    @DisplayName("POST /api/scenario/load does not start simulation; isSimulationRunning remains false")
    void loadScenario_doesNotStartSimulation() {
        var ac = Map.of(
                "flightPlan", Map.of(
                        "callsign", "NOSIM01",
                        "departureAirport", "RJTT",
                        "arrivalAirport", "RJAA",
                        "cruiseAltitude", 35000,
                        "cruiseSpeed", 450,
                        "route", List.<Map<String, Object>>of()
                ),
                "initialPosition", Map.of(
                        "latitude", 35.0, "longitude", 139.0, "altitude", 5000,
                        "heading", 90, "groundSpeed", 250, "verticalSpeed", 0
                )
        );
        var scenario = Map.of(
                "scenarioName", "No Auto-Start Test",
                "aircraft", List.of(ac)
        );

        restTemplate.postForEntity(baseUrl() + "/api/scenario/load", scenario, Map.class);

        ResponseEntity<Map> statusResponse = restTemplate.getForEntity(
                baseUrl() + "/simulation/status", Map.class);

        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).containsEntry("isSimulationRunning", false);
    }

    @Test
    @DisplayName("POST /api/scenario/load returns 400 when aircraft array is empty")
    void loadScenario_returns400_whenAircraftEmpty() {
        var scenario = Map.of(
                "scenarioName", "Empty",
                "aircraft", List.of()
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenario,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    @DisplayName("POST /api/scenario/load returns 400 on duplicate callsign")
    void loadScenario_returns400_onDuplicateCallsign() {
        var ac = Map.of(
                "flightPlan", Map.of(
                        "callsign", "DUP01",
                        "departureAirport", "RJTT",
                        "arrivalAirport", "RJAA",
                        "cruiseAltitude", 35000,
                        "cruiseSpeed", 450,
                        "route", List.<Map<String, Object>>of()
                ),
                "initialPosition", Map.of(
                        "latitude", 35.0, "longitude", 139.0, "altitude", 5000,
                        "heading", 90, "groundSpeed", 250, "verticalSpeed", 0
                )
        );
        var scenario = Map.of(
                "scenarioName", "Duplicate Test",
                "aircraft", List.of(ac, ac)
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenario,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(String.valueOf(response.getBody().get("message"))).contains("Duplicate callsign");
    }

    @Test
    @DisplayName("POST /api/scenario/load skips aircraft without initialPosition")
    void loadScenario_skipsAircraftWithoutInitialPosition() {
        var acValid = Map.of(
                "flightPlan", Map.of(
                        "callsign", "SKIPVALID",
                        "departureAirport", "RJTT",
                        "arrivalAirport", "RJAA",
                        "cruiseAltitude", 35000,
                        "cruiseSpeed", 450,
                        "route", List.<Map<String, Object>>of()
                ),
                "initialPosition", Map.of(
                        "latitude", 35.0, "longitude", 139.0, "altitude", 5000,
                        "heading", 90, "groundSpeed", 250, "verticalSpeed", 0
                )
        );
        // Map.of() rejects null; HashMap is required for initialPosition: null
        var acNoPos = new HashMap<String, Object>();
        acNoPos.put("flightPlan", Map.of(
                "callsign", "SKIPNULL",
                "departureAirport", "RJTT",
                "arrivalAirport", "RJAA",
                "cruiseAltitude", 35000,
                "cruiseSpeed", 450,
                "route", List.<Map<String, Object>>of()
        ));
        acNoPos.put("initialPosition", null);
        var scenario = Map.of(
                "scenarioName", "Skip Test",
                "aircraft", List.of(acValid, acNoPos)
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenario,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("aircraftCount", 1);
        assertThat(aircraftRepository.isAircraftExist(new Callsign("SKIPVALID"))).isTrue();
        assertThat(aircraftRepository.isAircraftExist(new Callsign("SKIPNULL"))).isFalse();
    }

    @Test
    @DisplayName("POST /api/scenario/load returns 400 when fix in route does not exist")
    void loadScenario_returns400_whenFixNotFound() {
        var ac = Map.of(
                "flightPlan", Map.of(
                        "callsign", "BADFIX01",
                        "departureAirport", "RJTT",
                        "arrivalAirport", "RJAA",
                        "cruiseAltitude", 35000,
                        "cruiseSpeed", 450,
                        "route", List.of(Map.of("fix", "NONEXISTENT_WAYPOINT_XYZ", "action", "CONTINUE"))
                ),
                "initialPosition", Map.of(
                        "latitude", 35.0, "longitude", 139.0, "altitude", 5000,
                        "heading", 90, "groundSpeed", 250, "verticalSpeed", 0
                )
        );
        var scenario = Map.of(
                "scenarioName", "Bad Fix Test",
                "aircraft", List.of(ac)
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenario,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(String.valueOf(response.getBody().get("message"))).contains("Fix not found");
    }

    @Test
    @DisplayName("POST /api/aircraft/{callsign}/flightplan assigns flight plan to existing aircraft")
    void assignFlightPlan_toExistingAircraft() {
        spawnWithSingleWaypointFlightPlan("ASGN01", "ABENO");

        var assignBody = Map.of(
                "callsign", "ASGN01",
                "aircraftType", "B738",
                "departureAirport", "RJTT",
                "arrivalAirport", "RJOO",
                "cruiseAltitude", 36000,
                "cruiseSpeed", 460,
                "route", List.of(
                        Map.of("fix", "ABENO", "action", "CONTINUE"),
                        Map.of("fix", "MAIKO", "action", "CONTINUE")
                )
        );
        ResponseEntity<Map> assignResp = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/ASGN01/flightplan",
                assignBody,
                Map.class
        );

        assertThat(assignResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assignResp.getBody()).containsEntry("success", true);

        ResponseEntity<Map> getResp = restTemplate.getForEntity(
                baseUrl() + "/api/aircraft/ASGN01/flightplan", Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> remaining = (List<String>) getResp.getBody().get("remainingWaypoints");
        assertThat(remaining).as("route should include MAIKO after assign").contains("MAIKO");
    }

    @Test
    @DisplayName("POST /api/aircraft/{callsign}/resume-navigation applies resume")
    void resumeNavigation_appliesInstruction() {
        spawnWithSingleWaypointFlightPlan("FPAPI01", "ABENO");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/FPAPI01/resume-navigation",
                null,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("navigationMode", "FLIGHT_PLAN");
    }

    @ParameterizedTest(name = "turnDirection={0} -> status={1}")
    @MethodSource("holdTurnCases")
    @DisplayName("POST /api/aircraft/{callsign}/hold validates turn direction")
    void holdAtFix_validatesTurnDirection(
            String turnDirection,
            HttpStatus expectedStatus,
            boolean expectedSuccess,
            String expectedNavigationMode
    ) {
        spawnWithSingleWaypointFlightPlan("FPAPI01", "ABENO");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/FPAPI01/hold",
                Map.of("fixName", "ABENO", "turnDirection", turnDirection),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).containsEntry("success", expectedSuccess);
        if (expectedNavigationMode != null) {
            assertThat(response.getBody()).containsEntry("navigationMode", expectedNavigationMode);
            assertThat(response.getBody()).containsEntry("turnDirection", turnDirection);
        }
    }

    @Test
    @DisplayName("POST /api/aircraft/{callsign}/hold accepts LEFT with inbound course and timed leg")
    void holdAtFix_acceptsLeftInboundAndTimedLeg() {
        spawnWithSingleWaypointFlightPlan("FPAPI01", "ABENO");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/FPAPI01/hold",
                Map.of(
                        "fixName", "ABENO",
                        "turnDirection", "LEFT",
                        "inboundCourseDeg", 209,
                        "legMinutes", 1.5
                ),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("navigationMode", "HOLDING");
        assertThat(response.getBody()).containsEntry("turnDirection", "LEFT");
        assertThat(response.getBody()).containsEntry("inboundCourseDeg", 209.0);
        assertThat(response.getBody()).containsEntry("legMinutes", 1.5);
    }

    @Test
    @DisplayName("POST /api/aircraft/{callsign}/hold accepts NM leg")
    void holdAtFix_acceptsNmLeg() {
        spawnWithSingleWaypointFlightPlan("FPAPI01", "ABENO");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/FPAPI01/hold",
                Map.of(
                        "fixName", "ABENO",
                        "inboundCourseDeg", 90,
                        "legNm", 7.0,
                        "turnDirection", "RIGHT"
                ),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("legNm", 7.0);
    }

    private static Stream<Arguments> holdValidationErrorCases() {
        return Stream.of(
                Arguments.of(
                        Map.of("fixName", "ABENO", "inboundCourseDeg", 360),
                        "inboundCourseDeg"),
                Arguments.of(
                        Map.of("fixName", "ABENO", "inboundCourseDeg", -1),
                        "inboundCourseDeg"),
                Arguments.of(
                        Map.of("fixName", "ABENO", "legMinutes", 1.0, "legNm", 4.0),
                        "legMinutes"),
                Arguments.of(
                        Map.of("fixName", "ABENO", "legMinutes", 0),
                        "legMinutes"),
                Arguments.of(
                        Map.of("fixName", "ABENO", "legNm", -2.0),
                        "legNm"),
                Arguments.of(
                        Map.of("fixName", "ABENO", "legNm", 101.0),
                        "legNm")
        );
    }

    @ParameterizedTest(name = "body contains {1} → 400")
    @MethodSource("holdValidationErrorCases")
    @DisplayName("POST /api/aircraft/{callsign}/hold rejects invalid hold parameters")
    void holdAtFix_rejectsInvalidParameters(Map<String, Object> body, String messageContains) {
        spawnWithSingleWaypointFlightPlan("FPAPI01", "ABENO");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/FPAPI01/hold",
                body,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(String.valueOf(response.getBody().get("message"))).containsIgnoringCase(messageContains);
    }

    @Test
    @DisplayName("resume-navigation returns HEADING after hold at final waypoint")
    void resumeNavigation_afterHoldAtFinalWaypoint_returnsHeading() {
        spawnWithFlightPlan(
                "HOLDEND1",
                List.of(
                        Map.of("fix", "ABENO", "action", "CONTINUE"),
                        Map.of("fix", "MAIKO", "action", "REMOVE_AIRCRAFT")
                )
        );

        restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/HOLDEND1/hold",
                Map.of("fixName", "MAIKO", "turnDirection", "RIGHT"),
                Map.class
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/HOLDEND1/resume-navigation",
                null,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("navigationMode", "HEADING");
    }

    @Test
    @DisplayName("location/all includes holding.pattern while HOLDING and omits it after resume")
    void locationApi_includesHoldingPatternWhileHolding() {
        spawnWithSingleWaypointFlightPlan("HOLDLOC1", "ABENO");

        ResponseEntity<List> beforeHold = restTemplate.getForEntity(
                baseUrl() + "/aircraft/location/all", List.class);
        assertThat(beforeHold.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> beforeDto = findLocationByCallsign(beforeHold.getBody(), "HOLDLOC1");
        assertThat(beforeDto).doesNotContainKey("holding");

        ResponseEntity<Map> holdResp = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/HOLDLOC1/hold",
                Map.of(
                        "fixName", "ABENO",
                        "inboundCourseDeg", 90,
                        "legMinutes", 1.0,
                        "turnDirection", "RIGHT"),
                Map.class);
        assertThat(holdResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> duringHold = restTemplate.getForEntity(
                baseUrl() + "/aircraft/location/all", List.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> holdingDto = findLocationByCallsign(duringHold.getBody(), "HOLDLOC1");
        assertThat(holdingDto).containsKey("holding");
        @SuppressWarnings("unchecked")
        Map<String, Object> holding = (Map<String, Object>) holdingDto.get("holding");
        assertThat(holding).containsEntry("fixName", "ABENO");
        assertThat(holding).containsEntry("turnDirection", "RIGHT");
        assertThat(holding.get("inboundCourseDeg")).isEqualTo(90.0);
        assertThat(holding.get("legMinutes")).isEqualTo(1.0);
        assertThat(holding).containsKey("outboundLegNm");
        assertThat(holding).containsKey("turnRadiusNm");
        assertThat(holding).containsKey("entryProcedure");
        assertThat(holding.get("entryProcedure")).isIn("DIRECT", "TEARDROP", "PARALLEL");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pattern = (List<Map<String, Object>>) holding.get("pattern");
        assertThat(pattern).isNotNull();
        assertThat(pattern.size()).isGreaterThanOrEqualTo(4);
        assertThat(pattern.get(0).get("latitude"))
                .isEqualTo(pattern.get(pattern.size() - 1).get("latitude"));

        restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/HOLDLOC1/resume-navigation",
                null,
                Map.class);

        ResponseEntity<List> afterResume = restTemplate.getForEntity(
                baseUrl() + "/aircraft/location/all", List.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> afterDto = findLocationByCallsign(afterResume.getBody(), "HOLDLOC1");
        assertThat(afterDto).doesNotContainKey("holding");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findLocationByCallsign(List<?> body, String callsign) {
        assertThat(body).isNotNull();
        for (Object item : body) {
            Map<String, Object> dto = (Map<String, Object>) item;
            if (callsign.equals(dto.get("callsign"))) {
                return dto;
            }
        }
        throw new AssertionError("callsign not found: " + callsign);
    }
}
