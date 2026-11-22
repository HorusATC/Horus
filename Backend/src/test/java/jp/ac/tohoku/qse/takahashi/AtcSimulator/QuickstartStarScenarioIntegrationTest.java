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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw.MsawDetector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.ProcedureCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.ConflictAlertDto;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class QuickstartStarScenarioIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int CONFLICT_FREE_SIM_SECONDS = 600;
    private static final List<String> EXPECTED_PROCEDURE_IDS =
            List.of("OSHIMA1A", "AKSEL1K", "AROSA2C", "AKSEL2C", "GODIN2A");

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
    void sampleJsonProcedureIdsExistInCatalog() throws Exception {
        JsonNode root = readQuickstartSample();
        List<String> ids = new java.util.ArrayList<>();
        for (JsonNode aircraft : root.get("aircraft")) {
            String procedureId = aircraft.get("procedureId").asText();
            ids.add(procedureId);
            assertThat(procedureCatalog.findById(procedureId))
                    .as("catalog must contain %s", procedureId)
                    .isPresent();
            assertThat(procedureCatalog.findById(procedureId).orElseThrow().isAssignablePrimary())
                    .as("%s must be assignable", procedureId)
                    .isTrue();
        }
        assertThat(ids).containsExactlyElementsOf(EXPECTED_PROCEDURE_IDS);
    }

    @Test
    void loadQuickstartSampleMapsOshimaRouteAndSeedsAtis34L() throws Exception {
        JsonNode root = readQuickstartSample();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = OBJECT_MAPPER.convertValue(root, Map.class);

        ResponseEntity<Map> load = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load", body, Map.class);
        assertThat(load.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(load.getBody()).containsEntry("aircraftCount", 5);

        AircraftBase ana101 = (AircraftBase) aircraftRepository.findByCallsign(new Callsign("ANA101"));
        List<FlightPlanWaypoint> wps = ana101.getFlightPlan().getWaypoints();
        assertThat(wps.stream().map(FlightPlanWaypoint::getFixName).toList())
                .containsExactly(
                        "XAC", "ANZAC", "TT450", "TT451", "TT452", "TT453", "WANDA", "WEDGE", "ARLON");
        assertThat(wps.get(1).getFixName()).isEqualTo("ANZAC");
        assertThat(wps.get(1).getAltitudeConstraint()).isEqualTo(AltitudeConstraint.AT);
        assertThat(wps.get(1).getTargetAltitude().toDouble()).isEqualTo(13000.0);
        assertThat(wps.get(6).getFixName()).isEqualTo("WANDA");
        assertThat(wps.get(6).getTargetAltitude().toDouble()).isEqualTo(13000.0);
        assertThat(wps.get(7).getFixName()).isEqualTo("WEDGE");
        assertThat(wps.get(7).getTargetAltitude().toDouble()).isEqualTo(8000.0);
        assertThat(wps).allSatisfy(wp -> assertThat(wp.getTargetSpeed()).isNull());

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
    void defaultSampleStaysConflictAndMsawFreeForTenSimulatedMinutes() throws Exception {
        JsonNode root = readQuickstartSample();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = OBJECT_MAPPER.convertValue(root, Map.class);

        ResponseEntity<Map> load = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load", body, Map.class);
        assertThat(load.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aircraftRepository.findAll()).hasSize(5);

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

    @Test
    void loadWithUnknownProcedureIdIs404() {
        Map<String, Object> aircraft = Map.of(
                "procedureId", "NO-SUCH-STAR",
                "flightPlan", Map.of(
                        "callsign", "BAD01",
                        "departureAirport", "RJGG",
                        "arrivalAirport", "RJTT",
                        "cruiseAltitude", 14000,
                        "cruiseSpeed", 280,
                        "route", List.of()),
                "initialPosition", Map.of(
                        "latitude", 34.71225,
                        "longitude", 139.41402778,
                        "altitude", 14000,
                        "heading", 61,
                        "groundSpeed", 280,
                        "verticalSpeed", 0));
        Map<String, Object> scenario = Map.of("scenarioName", "bad", "aircraft", List.of(aircraft));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load", scenario, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(aircraftRepository.findAll()).isEmpty();
    }

    @Test
    void loadWithoutAtisSeedKeepsDefault16R() {
        Map<String, Object> aircraft = Map.of(
                "flightPlan", Map.of(
                        "callsign", "SCN16",
                        "departureAirport", "RJTT",
                        "arrivalAirport", "RJAA",
                        "cruiseAltitude", 10000,
                        "cruiseSpeed", 250,
                        "route", List.of()),
                "initialPosition", Map.of(
                        "latitude", 35.0,
                        "longitude", 139.0,
                        "altitude", 5000,
                        "heading", 90,
                        "groundSpeed", 250,
                        "verticalSpeed", 0));
        Map<String, Object> scenario = Map.of("scenarioName", "no-atis", "aircraft", List.of(aircraft));

        ResponseEntity<Map> load = restTemplate.postForEntity(
                baseUrl() + "/api/scenario/load", scenario, Map.class);
        assertThat(load.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> atis = restTemplate.getForEntity(baseUrl() + "/api/atis", List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = atis.getBody();
        Map<String, Object> rjtt = rows.stream()
                .filter(r -> "RJTT".equals(r.get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertThat(rjtt.get("runwayInUse")).isEqualTo("16R");
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

    private JsonNode readQuickstartSample() throws Exception {
        Path docsCopy = Path.of("docs/test-data/quickstart-arrival-sample.json");
        Path frontendCopy = Path.of("../Frontend/data/quickstart-arrival-sample.json")
                .toAbsolutePath()
                .normalize();
        Path path = Files.isRegularFile(docsCopy) ? docsCopy : frontendCopy;
        assertThat(path).as("quickstart sample JSON must exist at %s", path).isRegularFile();
        return OBJECT_MAPPER.readTree(Files.readString(path));
    }
}
