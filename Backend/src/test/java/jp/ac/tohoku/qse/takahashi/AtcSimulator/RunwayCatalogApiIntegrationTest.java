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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RunwayCatalogApiIntegrationTest {

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
    void getAtsRunwaysReturnsTwelveEndsWithSurveyHeadings() {
        ResponseEntity<List> response = restTemplate.getForEntity(baseUrl() + "/ats/runways", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(12);
        @SuppressWarnings("unchecked")
        Map<String, Object> rjtt16r = ((List<Map<String, Object>>) (List<?>) response.getBody()).stream()
                .filter(m -> "RJTT".equals(m.get("airportIcao")) && "16R".equals(m.get("designator")))
                .findFirst()
                .orElseThrow();
        assertThat(((Number) rjtt16r.get("heading")).doubleValue()).isEqualTo(149.88);
        assertThat(rjtt16r.get("usableForLanding")).isEqualTo(true);
    }

    @Test
    void joinFinalToNewRunwaySucceedsAndRjtt05IsRejected() {
        restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/spawn-with-flightplan",
                Map.of(
                        "flightPlan", Map.of(
                                "callsign", "RWY01",
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

        ResponseEntity<Map> ok22 = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/RWY01/final-approach",
                Map.of("airportIcao", "RJTT", "runwayDesignator", "22"),
                Map.class);
        assertThat(ok22.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok22.getBody()).containsEntry("success", true);
        assertThat(((Number) ok22.getBody().get("approachCourseHeading")).doubleValue()).isEqualTo(215.01);

        ResponseEntity<Map> okRjaa = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/RWY01/final-approach",
                Map.of("airportIcao", "RJAA", "runwayDesignator", "16L"),
                Map.class);
        assertThat(okRjaa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) okRjaa.getBody().get("approachCourseHeading")).doubleValue()).isEqualTo(149.5);

        ResponseEntity<Map> bad05 = restTemplate.postForEntity(
                baseUrl() + "/api/aircraft/RWY01/final-approach",
                Map.of("airportIcao", "RJTT", "runwayDesignator", "05"),
                Map.class);
        assertThat(bad05.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(bad05.getBody().get("message"))).contains("not usable for landing");
    }
}
