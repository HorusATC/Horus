package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.airport.RunwayDefinition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atis.AtisTextComposer;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.AtsRouteFixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.RunwayCatalog;

public class AtisService {

    private static final char DEFAULT_IDENT = 'A';
    private static final String DEFAULT_RUNWAY = "16R";
    /** Session-start revision; bumps when that airport's GET representation actually changes. */
    private static final int INITIAL_REVISION = 0;

    private final RunwayCatalog runwayCatalog;
    private final AtsRouteFixPositionRepository airportRepository;
    private final ConcurrentHashMap<String, AirportAtisState> states = new ConcurrentHashMap<>();
    private UniformWind sessionWind = UniformWind.missing();

    public AtisService(RunwayCatalog runwayCatalog, AtsRouteFixPositionRepository airportRepository) {
        this.runwayCatalog = runwayCatalog;
        this.airportRepository = airportRepository;
        for (String icao : catalogAirportIcaos()) {
            ensureDefault(icao);
        }
    }

    public UniformWind getSessionWind() {
        return sessionWind;
    }

    public void resetToDefaults() {
        states.clear();
        sessionWind = UniformWind.missing();
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

    public Map<String, Object> getAtis(String airportIcao) {
        String icao = normalizeIcao(airportIcao);
        if (icao.isEmpty() || runwayCatalog.findByAirportIcao(icao).isEmpty()) {
            throw new IllegalArgumentException("Unknown airport: " + airportIcao);
        }
        return toApiMap(icao, ensureDefault(icao));
    }

    public int currentRevision(String airportIcao) {
        return ensureDefault(normalizeIcao(airportIcao)).revision();
    }

    public Map<String, Object> updateAtis(String airportIcao, String runwayInUse, Character informationIdent) {
        return updateAtis(airportIcao, runwayInUse, informationIdent, WindUpdate.absent(), QnhUpdate.absent());
    }

    public Map<String, Object> updateAtis(
            String airportIcao,
            String runwayInUse,
            Character informationIdent,
            WindUpdate windUpdate) {
        return updateAtis(airportIcao, runwayInUse, informationIdent, windUpdate, QnhUpdate.absent());
    }

    public Map<String, Object> updateAtis(
            String airportIcao,
            String runwayInUse,
            Character informationIdent,
            WindUpdate windUpdate,
            QnhUpdate qnhUpdate) {
        String icao = normalizeIcao(airportIcao);
        if (icao.isEmpty() || runwayCatalog.findByAirportIcao(icao).isEmpty()) {
            throw new IllegalArgumentException("Unknown airport: " + airportIcao);
        }
        String runway = runwayInUse == null ? "" : runwayInUse.toUpperCase(Locale.ROOT).trim();
        if (runwayCatalog.findRunway(icao, runway).isEmpty()) {
            throw new IllegalArgumentException("Unknown runway: " + runway + " at " + icao);
        }
        WindUpdate wind = windUpdate != null ? windUpdate : WindUpdate.absent();
        QnhUpdate qnh = qnhUpdate != null ? qnhUpdate : QnhUpdate.absent();
        boolean windChanged = applyWindUpdate(wind);

        if (windChanged) {
            for (String airport : catalogAirportIcaos()) {
                AirportAtisState current = ensureDefault(airport);
                char nextIdent = resolveIdentAfterWindChange(airport, icao, informationIdent, current);
                String runwayForState = airport.equals(icao) ? runway : current.runwayInUse();
                Integer qnhForState = airport.equals(icao)
                        ? resolveNextQnh(current.qnhHpa(), qnh)
                        : current.qnhHpa();
                // Shared wind changes every airport's GET wind fields and composed text.
                states.put(
                        airport,
                        new AirportAtisState(
                                runwayForState, nextIdent, qnhForState, current.revision() + 1));
            }
        } else {
            AirportAtisState current = ensureDefault(icao);
            Integer nextQnh = resolveNextQnh(current.qnhHpa(), qnh);
            boolean qnhChanged = !Objects.equals(current.qnhHpa(), nextQnh);
            char nextIdent = resolveIdentForAirportOnly(
                    runway, informationIdent, current, qnhChanged);
            boolean representationChanged = !current.runwayInUse().equals(runway)
                    || current.informationIdent() != nextIdent
                    || !Objects.equals(current.qnhHpa(), nextQnh);
            int nextRevision = representationChanged ? current.revision() + 1 : current.revision();
            states.put(icao, new AirportAtisState(runway, nextIdent, nextQnh, nextRevision));
        }
        return toApiMap(icao, ensureDefault(icao));
    }

    private boolean applyWindUpdate(WindUpdate windUpdate) {
        return switch (windUpdate.kind()) {
            case ABSENT -> false;
            case CLEAR -> {
                UniformWind cleared = UniformWind.missing();
                if (sessionWind.sameObservation(cleared)) {
                    yield false;
                }
                sessionWind = cleared;
                yield true;
            }
            case SET -> {
                UniformWind next = windUpdate.wind();
                if (sessionWind.sameObservation(next)) {
                    yield false;
                }
                sessionWind = next;
                yield true;
            }
        };
    }

    private static Integer resolveNextQnh(Integer currentQnh, QnhUpdate qnhUpdate) {
        return switch (qnhUpdate.kind()) {
            case ABSENT -> currentQnh;
            case CLEAR -> null;
            case SET -> qnhUpdate.qnhHpa();
        };
    }

    private char resolveIdentAfterWindChange(
            String airport, String targetIcao, Character informationIdent, AirportAtisState current) {
        if (airport.equals(targetIcao) && informationIdent != null) {
            char next = Character.toUpperCase(informationIdent);
            if (next < 'A' || next > 'Z') {
                throw new IllegalArgumentException("informationIdent must be A-Z");
            }
            return next;
        }
        return AtisTextComposer.nextIdent(current.informationIdent());
    }

    private char resolveIdentForAirportOnly(
            String runway,
            Character informationIdent,
            AirportAtisState current,
            boolean qnhChanged) {
        if (informationIdent != null) {
            char next = Character.toUpperCase(informationIdent);
            if (next < 'A' || next > 'Z') {
                throw new IllegalArgumentException("informationIdent must be A-Z");
            }
            return next;
        }
        boolean runwayChanged = !current.runwayInUse().equals(runway);
        if (runwayChanged || qnhChanged) {
            return AtisTextComposer.nextIdent(current.informationIdent());
        }
        return current.informationIdent();
    }

    private AirportAtisState ensureDefault(String icao) {
        return states.computeIfAbsent(icao, key -> {
            String runway = runwayCatalog.findRunway(key, DEFAULT_RUNWAY)
                    .map(RunwayDefinition::designator)
                    .orElseGet(() -> runwayCatalog.findByAirportIcao(key).stream()
                            .findFirst()
                            .map(RunwayDefinition::designator)
                            .orElse(DEFAULT_RUNWAY));
            return new AirportAtisState(runway, DEFAULT_IDENT, null, INITIAL_REVISION);
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
                AtisTextComposer.compose(
                        name,
                        icao,
                        state.informationIdent(),
                        state.runwayInUse(),
                        sessionWind,
                        state.qnhHpa()));
        boolean windAvailable = !sessionWind.isMissing();
        map.put("windAvailable", windAvailable);
        if (windAvailable) {
            sessionWind.directionDeg().ifPresent(deg -> map.put("windDirectionDeg", deg));
            sessionWind.speedKt().ifPresent(kt -> map.put("windSpeedKt", kt));
        }
        boolean qnhAvailable = state.qnhHpa() != null;
        map.put("qnhAvailable", qnhAvailable);
        if (qnhAvailable) {
            map.put("qnhHpa", state.qnhHpa());
        }
        List<String> available = runwayCatalog.findByAirportIcao(icao).stream()
                .map(RunwayDefinition::designator)
                .toList();
        map.put("availableRunways", available);
        map.put("revision", state.revision());
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

    private record AirportAtisState(
            String runwayInUse, char informationIdent, Integer qnhHpa, int revision) {
    }
}
