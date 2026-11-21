package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.api;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public class SquawkAssignmentIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
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
    void testAssignEmergencySquawk7700() {
        Map<String, Object> scenarioBody = Map.of(
                "aircraft", List.of(Map.of(
                        "flightPlan", Map.of(
                                "callsign", "JAL123",
                                "aircraftType", "B773",
                                "departureAirport", "RJTT",
                                "arrivalAirport", "RJOO",
                                "cruiseAltitude", 10000,
                                "cruiseSpeed", 300,
                                "route", List.of()
                        ),
                        "initialPosition", Map.of(
                                "latitude", 35.5,
                                "longitude", 139.5,
                                "altitude", 10000,
                                "heading", 90,
                                "groundSpeed", 300,
                                "verticalSpeed", 0
                        )
                ))
        );

        ResponseEntity<Map> scenarioResponse = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenarioBody,
                Map.class
        );
        assertThat(scenarioResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, String> squawkBody = Map.of("squawk", "7700");
        ResponseEntity<String> squawkResponse = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/JAL123/squawk",
                squawkBody,
                String.class
        );
        assertThat(squawkResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> locationResponse = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/location/all",
                null,
                List.class
        );
        assertThat(locationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(locationResponse.getBody()).isNotNull();
        assertThat(locationResponse.getBody()).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> aircraft = (Map<String, Object>) locationResponse.getBody().get(0);
        assertThat(aircraft.get("callsign")).isEqualTo("JAL123");
        assertThat(aircraft.get("squawk")).isEqualTo("7700");
    }

    @Test
    void testAssignSquawk7500Hijack() {
        Map<String, Object> scenarioBody = Map.of(
                "aircraft", List.of(Map.of(
                        "flightPlan", Map.of(
                                "callsign", "ANA456",
                                "aircraftType", "B773",
                                "departureAirport", "RJTT",
                                "arrivalAirport", "RJOO",
                                "cruiseAltitude", 10000,
                                "cruiseSpeed", 300,
                                "route", List.of()
                        ),
                        "initialPosition", Map.of(
                                "latitude", 35.5,
                                "longitude", 139.5,
                                "altitude", 10000,
                                "heading", 90,
                                "groundSpeed", 300,
                                "verticalSpeed", 0
                        )
                ))
        );

        restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenarioBody,
                Map.class
        );

        Map<String, String> squawkBody = Map.of("squawk", "7500");
        restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/ANA456/squawk",
                squawkBody,
                String.class
        );

        ResponseEntity<List> locationResponse = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/location/all",
                null,
                List.class
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> aircraft = (Map<String, Object>) locationResponse.getBody().get(0);
        assertThat(aircraft.get("squawk")).isEqualTo("7500");
    }

    @Test
    void testAssignSquawk7600RadioFailure() {
        Map<String, Object> scenarioBody = Map.of(
                "aircraft", List.of(Map.of(
                        "flightPlan", Map.of(
                                "callsign", "SKY789",
                                "aircraftType", "B773",
                                "departureAirport", "RJTT",
                                "arrivalAirport", "RJOO",
                                "cruiseAltitude", 10000,
                                "cruiseSpeed", 300,
                                "route", List.of()
                        ),
                        "initialPosition", Map.of(
                                "latitude", 35.5,
                                "longitude", 139.5,
                                "altitude", 10000,
                                "heading", 90,
                                "groundSpeed", 300,
                                "verticalSpeed", 0
                        )
                ))
        );

        restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenarioBody,
                Map.class
        );

        Map<String, String> squawkBody = Map.of("squawk", "7600");
        restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/SKY789/squawk",
                squawkBody,
                String.class
        );

        ResponseEntity<List> locationResponse = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/location/all",
                null,
                List.class
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> aircraft = (Map<String, Object>) locationResponse.getBody().get(0);
        assertThat(aircraft.get("squawk")).isEqualTo("7600");
    }

    @Test
    void testAssignNormalSquawk() {
        Map<String, Object> scenarioBody = Map.of(
                "aircraft", List.of(Map.of(
                        "flightPlan", Map.of(
                                "callsign", "JJP100",
                                "aircraftType", "B773",
                                "departureAirport", "RJTT",
                                "arrivalAirport", "RJOO",
                                "cruiseAltitude", 10000,
                                "cruiseSpeed", 300,
                                "route", List.of()
                        ),
                        "initialPosition", Map.of(
                                "latitude", 35.5,
                                "longitude", 139.5,
                                "altitude", 10000,
                                "heading", 90,
                                "groundSpeed", 300,
                                "verticalSpeed", 0
                        )
                ))
        );

        restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenarioBody,
                Map.class
        );

        Map<String, String> squawkBody = Map.of("squawk", "1200");
        restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/JJP100/squawk",
                squawkBody,
                String.class
        );

        ResponseEntity<List> locationResponse = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/location/all",
                null,
                List.class
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> aircraft = (Map<String, Object>) locationResponse.getBody().get(0);
        assertThat(aircraft.get("squawk")).isEqualTo("1200");
    }

    @Test
    void testInvalidSquawkRejected() {
        Map<String, Object> scenarioBody = Map.of(
                "aircraft", List.of(Map.of(
                        "flightPlan", Map.of(
                                "callsign", "TEST001",
                                "aircraftType", "B773",
                                "departureAirport", "RJTT",
                                "arrivalAirport", "RJOO",
                                "cruiseAltitude", 10000,
                                "cruiseSpeed", 300,
                                "route", List.of()
                        ),
                        "initialPosition", Map.of(
                                "latitude", 35.5,
                                "longitude", 139.5,
                                "altitude", 10000,
                                "heading", 90,
                                "groundSpeed", 300,
                                "verticalSpeed", 0
                        )
                ))
        );

        restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load",
                scenarioBody,
                Map.class
        );

        Map<String, String> invalidSquawkBody = Map.of("squawk", "9999");
        ResponseEntity<String> squawkResponse = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/TEST001/squawk",
                invalidSquawkBody,
                String.class
        );

        assertThat(squawkResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
