package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.airport.RunwayDefinition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atis.AtisTextComposer;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.AtsRouteFixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.RunwayCatalog;

public class AtisService {

    private static final char DEFAULT_IDENT = 'A';
    private static final String DEFAULT_RUNWAY = "16R";

    private final RunwayCatalog runwayCatalog;
    private final AtsRouteFixPositionRepository airportRepository;
    private final ConcurrentHashMap<String, AirportAtisState> states = new ConcurrentHashMap<>();

    public AtisService(RunwayCatalog runwayCatalog, AtsRouteFixPositionRepository airportRepository) {
        this.runwayCatalog = runwayCatalog;
        this.airportRepository = airportRepository;
        for (String icao : catalogAirportIcaos()) {
            ensureDefault(icao);
        }
    }

    public void resetToDefaults() {
        states.clear();
        for (String icao : catalogAirportIcaos()) {
            ensureDefault(icao);
        }
    }

    public List<Map<String, Object>> getAllAtis() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String icao : catalogAirportIcaos()) {
            result.add(toApiMap(icao, ensureDefault(icao)));
        }
        return result;
    }

    public Map<String, Object> updateAtis(String airportIcao, String runwayInUse, Character informationIdent) {
        String icao = normalizeIcao(airportIcao);
        if (icao.isEmpty() || runwayCatalog.findByAirportIcao(icao).isEmpty()) {
            throw new IllegalArgumentException("Unknown airport: " + airportIcao);
        }
        String runway = runwayInUse == null ? "" : runwayInUse.toUpperCase(Locale.ROOT).trim();
        if (runwayCatalog.findRunway(icao, runway).isEmpty()) {
            throw new IllegalArgumentException("Unknown runway: " + runway + " at " + icao);
        }
        AirportAtisState current = ensureDefault(icao);
        char nextIdent;
        if (informationIdent != null) {
            nextIdent = Character.toUpperCase(informationIdent);
            if (nextIdent < 'A' || nextIdent > 'Z') {
                throw new IllegalArgumentException("informationIdent must be A-Z");
            }
        } else if (!current.runwayInUse().equals(runway)) {
            nextIdent = AtisTextComposer.nextIdent(current.informationIdent());
        } else {
            nextIdent = current.informationIdent();
        }
        AirportAtisState updated = new AirportAtisState(runway, nextIdent);
        states.put(icao, updated);
        return toApiMap(icao, updated);
    }

    private AirportAtisState ensureDefault(String icao) {
        return states.computeIfAbsent(icao, key -> {
            String runway = runwayCatalog.findRunway(key, DEFAULT_RUNWAY)
                    .map(RunwayDefinition::designator)
                    .orElseGet(() -> runwayCatalog.findByAirportIcao(key).stream()
                            .findFirst()
                            .map(RunwayDefinition::designator)
                            .orElse(DEFAULT_RUNWAY));
            return new AirportAtisState(runway, DEFAULT_IDENT);
        });
    }

    private Map<String, Object> toApiMap(String icao, AirportAtisState state) {
        String name = airportRepository.findAirportNameByIcao(icao);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("airportIcao", icao);
        map.put("airportName", name);
        map.put("runwayInUse", state.runwayInUse());
        map.put("informationIdent", String.valueOf(state.informationIdent()));
        map.put(
                "text",
                AtisTextComposer.compose(name, icao, state.informationIdent(), state.runwayInUse()));
        List<String> available = runwayCatalog.findByAirportIcao(icao).stream()
                .map(RunwayDefinition::designator)
                .toList();
        map.put("availableRunways", available);
        return map;
    }

    private List<String> catalogAirportIcaos() {
        return runwayCatalog.getAllRunways().stream()
                .map(RunwayDefinition::airportIcao)
                .distinct()
                .sorted()
                .toList();
    }

    private static String normalizeIcao(String airportIcao) {
        if (airportIcao == null) {
            return "";
        }
        return airportIcao.toUpperCase(Locale.ROOT).trim();
    }

    private record AirportAtisState(String runwayInUse, char informationIdent) {
    }
}
