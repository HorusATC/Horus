package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.AtisService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AtisApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AtisService atisService;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void reset() {
        atisService.resetToDefaults();
    }

    @Test
    void getAtisReturnsDefaults() {
        ResponseEntity<List> response = restTemplate.getForEntity(baseUrl() + "/api/atis", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void putRunwayChangeAdvancesIdent() {
        // HttpMethod.PUT must appear before baseUrl() for ApiContractTest extraction.
        HttpMethod put = HttpMethod.PUT;
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "34L")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("runwayInUse", "34L");
        assertThat(response.getBody()).containsEntry("informationIdent", "B");
    }

    @Test
    void putUnknownRunwayReturns400() {
        HttpMethod put = HttpMethod.PUT;
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "99X")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void putUnknownAirportReturns400() {
        HttpMethod put = HttpMethod.PUT;
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/atis/XXXX",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "16R")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getAtisIncludesWindAvailableFalseByDefault() {
        ResponseEntity<List> response = restTemplate.getForEntity(baseUrl() + "/api/atis", List.class);
        assertThat(response.getBody()).isNotNull();
        Map<?, ?> rjtt = (Map<?, ?>) response.getBody().stream()
                .filter(m -> "RJTT".equals(((Map<?, ?>) m).get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertThat(rjtt.get("windAvailable")).isEqualTo(false);
    }

    @Test
    void putWithWindUpdatesTextAndAdvancesBothIdents() {
        HttpMethod put = HttpMethod.PUT;
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of(
                        "runwayInUse", "16R",
                        "wind", Map.of("directionDeg", 160, "speedKt", 12))),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("windAvailable", true);
        assertThat(response.getBody()).containsEntry("windDirectionDeg", 160);
        assertThat(response.getBody()).containsEntry("windSpeedKt", 12);
        assertThat(response.getBody()).containsEntry("informationIdent", "B");
        assertThat((String) response.getBody().get("text")).contains("WIND 160 DEGREES 12 KNOTS");

        ResponseEntity<List> all = restTemplate.getForEntity(baseUrl() + "/api/atis", List.class);
        Map<?, ?> rjaa = (Map<?, ?>) all.getBody().stream()
                .filter(m -> "RJAA".equals(((Map<?, ?>) m).get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertThat(rjaa.get("informationIdent")).isEqualTo("B");
    }

    @Test
    void putWindNullClearsObservation() {
        HttpMethod put = HttpMethod.PUT;
        restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of(
                        "runwayInUse", "16R",
                        "wind", Map.of("speedKt", 0))),
                Map.class);
        Map<String, Object> clearBody = new HashMap<>();
        clearBody.put("runwayInUse", "16R");
        clearBody.put("wind", null);
        ResponseEntity<Map> cleared = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(clearBody),
                Map.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody()).containsEntry("windAvailable", false);
    }

    @Test
    void putBadWindReturns400() {
        HttpMethod put = HttpMethod.PUT;
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of(
                        "runwayInUse", "16R",
                        "wind", Map.of("directionDeg", 160, "speedKt", 100))),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getAtisIncludesQnhAvailableFalseByDefault() {
        ResponseEntity<List> response = restTemplate.getForEntity(baseUrl() + "/api/atis", List.class);
        assertThat(response.getBody()).isNotNull();
        Map<?, ?> rjtt = (Map<?, ?>) response.getBody().stream()
                .filter(m -> "RJTT".equals(((Map<?, ?>) m).get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertThat(rjtt.get("qnhAvailable")).isEqualTo(false);
        assertThat(rjtt.containsKey("qnhHpa")).isFalse();
    }

    @Test
    void putWithQnhUpdatesOnlyTargetAirport() {
        HttpMethod put = HttpMethod.PUT;
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "16R", "qnhHpa", 1013)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("qnhAvailable", true);
        assertThat(response.getBody()).containsEntry("qnhHpa", 1013);
        assertThat(response.getBody()).containsEntry("informationIdent", "B");
        assertThat((String) response.getBody().get("text")).contains("QNH 1013");

        ResponseEntity<List> all = restTemplate.getForEntity(baseUrl() + "/api/atis", List.class);
        Map<?, ?> rjaa = (Map<?, ?>) all.getBody().stream()
                .filter(m -> "RJAA".equals(((Map<?, ?>) m).get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertThat(rjaa.get("informationIdent")).isEqualTo("A");
        assertThat(rjaa.get("qnhAvailable")).isEqualTo(false);
    }

    @Test
    void putQnhNullClearsObservation() {
        HttpMethod put = HttpMethod.PUT;
        restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "16R", "qnhHpa", 1013)),
                Map.class);
        Map<String, Object> clearBody = new HashMap<>();
        clearBody.put("runwayInUse", "16R");
        clearBody.put("qnhHpa", null);
        ResponseEntity<Map> cleared = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(clearBody),
                Map.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody()).containsEntry("qnhAvailable", false);
        assertThat(cleared.getBody().containsKey("qnhHpa")).isFalse();
    }

    @Test
    void putBadQnhReturns400() {
        HttpMethod put = HttpMethod.PUT;
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "16R", "qnhHpa", 849)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void putWithoutQnhKeyLeavesQnhUnchanged() {
        HttpMethod put = HttpMethod.PUT;
        restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "16R", "qnhHpa", 1013)),
                Map.class);
        ResponseEntity<Map> again = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "16R")),
                Map.class);
        assertThat(again.getBody()).containsEntry("qnhHpa", 1013);
        assertThat(again.getBody()).containsEntry("informationIdent", "B");
    }
}
