package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.AircraftFactory;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.ScenarioService;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.globals.GlobalVariables;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.CreateAircraftDto;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("MSAW Alert Integration Test")
class MsawAlertIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private AircraftRepository aircraftRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    @DisplayName("location/all includes msawAlert when aircraft is below MSA")
    void locationApi_includesMsawAlertWhenBelowMsa() throws Exception {
        CreateAircraftDto dto = new CreateAircraftDto(
                "MSAW001", 35.5, 139.5, 2000, 250, 0, 90,
                "B738", "HND", "RJTT", "NRT", "RJAA", "2024-12-13T14:30:00Z");
        scenarioService.spawnAircraft(AircraftFactory.createCommercialAircraft(dto));
        assertThat(aircraftRepository.isAircraftExist(new Callsign("MSAW001"))).isTrue();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/aircraft/location/all", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode locationArray = objectMapper.readTree(response.getBody());
        JsonNode aircraft = findByCallsign(locationArray, "MSAW001");
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.has("msawAlert")).isTrue();
        assertThat(aircraft.get("msawAlert").isNull()).isFalse();

        JsonNode msawAlert = aircraft.get("msawAlert");
        assertThat(msawAlert.get("type").asText()).isEqualTo("MSA");
        assertThat(msawAlert.get("msaFt").asDouble()).isEqualTo(2500.0);
        assertThat(msawAlert.get("trigger").asText()).isEqualTo("CURRENT");
    }

    @Test
    @DisplayName("location/all includes msawAlert:null when aircraft is above MSA")
    void locationApi_includesNullMsawAlertWhenAboveMsa() throws Exception {
        CreateAircraftDto dto = new CreateAircraftDto(
                "MSAW002", 35.5, 139.5, 10000, 250, 0, 90,
                "B738", "HND", "RJTT", "NRT", "RJAA", "2024-12-13T14:30:00Z");
        scenarioService.spawnAircraft(AircraftFactory.createCommercialAircraft(dto));

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/aircraft/location/all", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode locationArray = objectMapper.readTree(response.getBody());
        JsonNode aircraft = findByCallsign(locationArray, "MSAW002");
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.has("msawAlert")).as("msawAlert field must always be present").isTrue();
        assertThat(aircraft.get("msawAlert").isNull()).as("msawAlert should be null above MSA").isTrue();
    }

    private static JsonNode findByCallsign(JsonNode locationArray, String callsign) {
        for (JsonNode aircraft : locationArray) {
            if (callsign.equals(aircraft.get("callsign").asText())) {
                return aircraft;
            }
        }
        return null;
    }
}
