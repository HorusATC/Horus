package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.ConflictAlertService;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.SimulationTiming;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.globals.GlobalVariables;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.Aircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw.MsawDetector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.ProcedureCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.ConflictAlertDto;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class QuickstartDepartureScenarioIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int CONFLICT_FREE_SIM_SECONDS = 600;
    private static final List<String> EXPECTED_PROCEDURE_IDS = List.of("VAMOS4.RWY34L_34R");
    private static final List<String> EXPECTED_TRANSITION_IDS = List.of("VAMOS4.TATEYAMA");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private ProcedureCatalog procedureCatalog;

    @Autowired
    private ConflictAlertService conflictAlertService;

    @Autowired
    private MsawDetector msawDetector;

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
    void sampleJsonProcedureIdsExistInCatalogAndExcludeVisip() throws Exception {
        JsonNode root = readDepartureSample();
        List<String> procedureIds = new java.util.ArrayList<>();
        List<String> transitionIds = new java.util.ArrayList<>();
        for (JsonNode aircraft : root.get("aircraft")) {
            String procedureId = aircraft.get("procedureId").asText();
            assertThat(procedureId).isNotEqualTo("VISIP1");
            procedureIds.add(procedureId);
            assertThat(procedureCatalog.findById(procedureId))
                    .as("catalog must contain %s", procedureId)
                    .isPresent();
            assertThat(procedureCatalog.findById(procedureId).orElseThrow().isAssignablePrimary())
                    .as("%s must be assignable", procedureId)
                    .isTrue();
            String transitionId = aircraft.get("transitionId").asText();
            transitionIds.add(transitionId);
            assertThat(procedureCatalog.findById(transitionId)).isPresent();
        }
        assertThat(procedureIds).containsExactlyElementsOf(EXPECTED_PROCEDURE_IDS);
        assertThat(transitionIds).containsExactlyElementsOf(EXPECTED_TRANSITION_IDS);
    }

    @Test
    void loadDepartureSampleMapsVamosTateyamaRouteAndSeedsAtis34L() throws Exception {
        JsonNode root = readDepartureSample();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = OBJECT_MAPPER.convertValue(root, Map.class);

        ResponseEntity<Map> load = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load", body, Map.class);
        assertThat(load.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(load.getBody()).containsEntry("aircraftCount", 1);

        AircraftBase jal900 = (AircraftBase) aircraftRepository.findByCallsign(new Callsign("JAL900"));
        List<FlightPlanWaypoint> wps = jal900.getFlightPlan().getWaypoints();
        assertThat(wps.stream().map(FlightPlanWaypoint::getFixName).toList())
                .containsExactly("TT502", "LOCUP", "VAMOS", "UTIBO");

        ResponseEntity<List> atis = restTemplate.getForEntity(baseUrl() + "/api/atis", List.class);
        assertThat(atis.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = atis.getBody();
        assertThat(rows).isNotNull();
        Map<String, Object> rjtt = rows.stream()
                .filter(r -> "RJTT".equals(r.get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertThat(rjtt.get("runwayInUse")).isEqualTo("34L");
    }

    @Test
    void departureSampleStaysConflictAndMsawFreeForTenSimulatedMinutes() throws Exception {
        JsonNode root = readDepartureSample();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = OBJECT_MAPPER.convertValue(root, Map.class);

        ResponseEntity<Map> load = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load", body, Map.class);
        assertThat(load.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aircraftRepository.findAll()).hasSize(1);

        GlobalVariables.isSimulationRunning = true;
        double dt = SimulationTiming.SIM_DELTA_SECONDS;
        for (int second = 0; second <= CONFLICT_FREE_SIM_SECONDS; second++) {
            assertConflictAndMsawClean(second);
            if (second == CONFLICT_FREE_SIM_SECONDS) {
                break;
            }
            for (Aircraft aircraft : aircraftRepository.findAll()) {
                AircraftBase base = (AircraftBase) aircraft;
                base.calculateNextAircraftVector(dt);
                base.calculateNextAircraftPosition(dt);
            }
        }
    }

    private void assertConflictAndMsawClean(int simSecond) {
        List<ConflictAlertDto> violations = conflictAlertService.getSeparationViolationAlerts();
        assertThat(violations)
                .as("conflict/violations must stay empty at t=%ds", simSecond)
                .isEmpty();
        for (Aircraft aircraft : aircraftRepository.findAll()) {
            assertThat(msawDetector.detectAlert(aircraft))
                    .as("MSAW must stay clean for %s at t=%ds", aircraft.getCallsign(), simSecond)
                    .isNull();
        }
    }

    private JsonNode readDepartureSample() throws Exception {
        Path docsCopy = Path.of("docs/test-data/quickstart-departure-sample.json");
        Path frontendCopy = Path.of("../Frontend/data/quickstart-departure-sample.json")
                .toAbsolutePath()
                .normalize();
        Path path = Files.isRegularFile(docsCopy) ? docsCopy : frontendCopy;
        assertThat(path).as("departure sample JSON must exist at %s", path).isRegularFile();
        return OBJECT_MAPPER.readTree(Files.readString(path));
    }
}
