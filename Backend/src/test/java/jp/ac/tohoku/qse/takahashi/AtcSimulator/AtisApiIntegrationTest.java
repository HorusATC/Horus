package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;

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
}
