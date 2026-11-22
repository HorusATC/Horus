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

/**
 * Inverted stale-runway repro (PR-B / ⑩b): with expectedRevision, an older client's wind PUT
 * must not overwrite another client's runway change. Without expectedRevision, legacy behavior
 * remains (wind PUT still applies the supplied runwayInUse).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AtisStaleRunwayReproTest {

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
    void staleExpectedRevisionWindPutReturns409AndKeepsRunway34L() {
        HttpMethod put = HttpMethod.PUT;
        int revisionBeforeB = atisService.currentRevision("RJTT");

        ResponseEntity<Map> clientB = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "34L")),
                Map.class);
        assertThat(clientB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clientB.getBody()).containsEntry("runwayInUse", "34L");
        assertThat(clientB.getBody()).containsEntry("informationIdent", "B");
        int revisionAfterB = ((Number) clientB.getBody().get("revision")).intValue();
        assertThat(revisionAfterB).isGreaterThan(revisionBeforeB);

        Map<String, Object> staleWindPut = new HashMap<>();
        staleWindPut.put("runwayInUse", "16R");
        staleWindPut.put("expectedRevision", revisionBeforeB);
        staleWindPut.put("wind", Map.of("directionDeg", 160, "speedKt", 12));

        ResponseEntity<Map> clientA = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(staleWindPut),
                Map.class);

        assertThat(clientA.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(clientA.getBody()).containsEntry("runwayInUse", "34L");
        assertThat(clientA.getBody()).containsEntry("informationIdent", "B");
        assertThat(clientA.getBody()).containsEntry("revision", revisionAfterB);
        assertThat(clientA.getBody()).containsEntry("message", "ATIS revision mismatch");
        assertThat(clientA.getBody()).containsEntry("windAvailable", false);

        ResponseEntity<List> all = restTemplate.getForEntity(baseUrl() + "/api/atis", List.class);
        Map<?, ?> rjtt = (Map<?, ?>) all.getBody().stream()
                .filter(m -> "RJTT".equals(((Map<?, ?>) m).get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertThat(rjtt.get("runwayInUse")).isEqualTo("34L");
        assertThat(rjtt.get("informationIdent")).isEqualTo("B");
        assertThat(rjtt.get("revision")).isEqualTo(revisionAfterB);
        assertThat(rjtt.get("windAvailable")).isEqualTo(false);
    }

    @Test
    void windPutWithoutExpectedRevisionStillAppliesStaleRunway() {
        HttpMethod put = HttpMethod.PUT;

        ResponseEntity<Map> clientB = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(Map.of("runwayInUse", "34L")),
                Map.class);
        assertThat(clientB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clientB.getBody()).containsEntry("runwayInUse", "34L");
        assertThat(clientB.getBody()).containsEntry("informationIdent", "B");

        Map<String, Object> legacyWindPut = new HashMap<>();
        legacyWindPut.put("runwayInUse", "16R");
        legacyWindPut.put("wind", Map.of("directionDeg", 160, "speedKt", 12));

        ResponseEntity<Map> clientA = restTemplate.exchange(
                baseUrl() + "/api/atis/RJTT",
                put,
                new HttpEntity<>(legacyWindPut),
                Map.class);

        assertThat(clientA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clientA.getBody()).containsEntry("runwayInUse", "16R");
        assertThat(clientA.getBody()).containsEntry("windAvailable", true);
        assertThat(clientA.getBody()).containsEntry("windDirectionDeg", 160);
        assertThat(clientA.getBody()).containsEntry("windSpeedKt", 12);
        assertThat(clientA.getBody()).containsEntry("informationIdent", "C");
    }
}
