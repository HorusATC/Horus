package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.api;

import static jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.constants.AtcSimulatorConstants.DEFAULT_SIMULATION_ETA;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.AircraftFactory;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.AtisService;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.FlightPlanFromDtoConverter;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.ProcedureSpawnSupport;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.ScenarioService;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.globals.GlobalVariables;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlan;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.AtsRouteFixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.RunwayCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.CreateAircraftDto;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.FlightPlanDto;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.InitialPositionDto;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.ScenarioAtisSeedDto;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.ScenarioLoadDto;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.SpawnWithFlightPlanDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/scenario")
public class ScenarioController {

    private static final Logger logger = LoggerFactory.getLogger(ScenarioController.class);

    private final ScenarioService scenarioService;
    private final AircraftRepository aircraftRepository;
    private final FlightPlanFromDtoConverter flightPlanConverter;
    private final AtsRouteFixPositionRepository airportRepository;
    private final AtisService atisService;
    private final ProcedureSpawnSupport procedureSpawnSupport;
    private final RunwayCatalog runwayCatalog;

    public ScenarioController(
            ScenarioService scenarioService,
            AircraftRepository aircraftRepository,
            FlightPlanFromDtoConverter flightPlanConverter,
            AtsRouteFixPositionRepository airportRepository,
            AtisService atisService,
            ProcedureSpawnSupport procedureSpawnSupport,
            RunwayCatalog runwayCatalog) {
        this.scenarioService = scenarioService;
        this.aircraftRepository = aircraftRepository;
        this.flightPlanConverter = flightPlanConverter;
        this.airportRepository = airportRepository;
        this.atisService = atisService;
        this.procedureSpawnSupport = procedureSpawnSupport;
        this.runwayCatalog = runwayCatalog;
    }

    @PostMapping(path = "/load", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> loadScenario(
            @Valid @RequestBody ScenarioLoadDto dto) {
        if (dto.aircraft() == null || dto.aircraft().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "aircraft array is required and must not be empty"));
        }

        Set<String> seenCallsigns = new HashSet<>();
        for (SpawnWithFlightPlanDto ac : dto.aircraft()) {
            String cs = ac.flightPlan().callsign();
            if (!seenCallsigns.add(cs)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Duplicate callsign: " + cs));
            }
        }

        // Validate FlightPlan of aircraft that will be spawned; skip those without initialPosition
        for (SpawnWithFlightPlanDto ac : dto.aircraft()) {
            if (ac.initialPosition() == null) continue;
            if (ac.procedureId() == null || ac.procedureId().isBlank()) {
                try {
                    flightPlanConverter.toDomain(ac.flightPlan());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", e.getMessage()));
                }
            }
        }

        ProcedureSpawnSupport.ResolveResult[] resolved = new ProcedureSpawnSupport.ResolveResult[dto.aircraft().size()];
        for (int i = 0; i < dto.aircraft().size(); i++) {
            SpawnWithFlightPlanDto ac = dto.aircraft().get(i);
            if (ac.initialPosition() == null) {
                resolved[i] = ProcedureSpawnSupport.ResolveResult.none();
                continue;
            }
            ProcedureSpawnSupport.ResolveResult result = procedureSpawnSupport.resolve(ac);
            if (result.kind() == ProcedureSpawnSupport.ResolveKind.NOT_FOUND) {
                return ResponseEntity.notFound().build();
            }
            if (result.kind() == ProcedureSpawnSupport.ResolveKind.BAD_REQUEST) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", result.message()));
            }
            resolved[i] = result;
        }

        ResponseEntity<Map<String, Object>> atisError = validateAtisSeeds(dto.atis());
        if (atisError != null) {
            return atisError;
        }

        GlobalVariables.isSimulationRunning = false;
        aircraftRepository.clear();
        atisService.resetToDefaults();
        applyAtisSeeds(dto.atis());

        int count = 0;
        for (int i = 0; i < dto.aircraft().size(); i++) {
            SpawnWithFlightPlanDto ac = dto.aircraft().get(i);
            if (ac.initialPosition() == null) {
                logger.warn("Skipping aircraft {}: missing initialPosition", ac.flightPlan().callsign());
                continue;
            }
            CommercialAircraft aircraft = createAircraftFromSpawn(ac.flightPlan(), ac.initialPosition());
            ProcedureSpawnSupport.ResolveResult result = resolved[i];
            if (result.kind() == ProcedureSpawnSupport.ResolveKind.OK) {
                procedureSpawnSupport.apply((AircraftBase) aircraft, result.mapped());
            } else {
                FlightPlan fp = flightPlanConverter.toDomain(ac.flightPlan());
                aircraft.setFlightPlan(fp);
            }
            scenarioService.spawnAircraft(aircraft);
            count++;
        }

        logger.info("Scenario loaded: {} aircraft, scenarioName={}", count, dto.scenarioName());
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("scenarioName", dto.scenarioName());
        body.put("aircraftCount", count);
        body.put("message", "Scenario loaded successfully");
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> validateAtisSeeds(List<ScenarioAtisSeedDto> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return null;
        }
        for (ScenarioAtisSeedDto seed : seeds) {
            String icao = seed.icao() == null ? "" : seed.icao().trim().toUpperCase(Locale.ROOT);
            String runway = seed.runwayInUse() == null
                    ? ""
                    : seed.runwayInUse().trim().toUpperCase(Locale.ROOT);
            if (icao.isEmpty() || runwayCatalog.findByAirportIcao(icao).isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Unknown airport: " + seed.icao()));
            }
            if (runwayCatalog.findRunway(icao, runway).isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success",
                                false,
                                "message",
                                "Unknown runway: " + runway + " at " + icao));
            }
        }
        return null;
    }

    private void applyAtisSeeds(List<ScenarioAtisSeedDto> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return;
        }
        for (ScenarioAtisSeedDto seed : seeds) {
            atisService.updateAtis(seed.icao(), seed.runwayInUse(), null);
        }
    }

    private CommercialAircraft createAircraftFromSpawn(FlightPlanDto fpDto, InitialPositionDto pos) {
        String dep = fpDto.departureAirport() != null ? fpDto.departureAirport() : "RJTT";
        String arr = fpDto.arrivalAirport() != null ? fpDto.arrivalAirport() : "RJAA";
        String originIata = airportRepository.findIataByIcao(dep);
        String destIata = airportRepository.findIataByIcao(arr);
        return AircraftFactory.createCommercialAircraft(
                new CreateAircraftDto(
                        fpDto.callsign(),
                        pos.latitude(),
                        pos.longitude(),
                        pos.altitude(),
                        pos.groundSpeed(),
                        pos.verticalSpeed(),
                        pos.heading(),
                        fpDto.aircraftType() != null ? fpDto.aircraftType() : "B738",
                        originIata,
                        dep,
                        destIata,
                        arr,
                        DEFAULT_SIMULATION_ETA.toString()));
    }
}
