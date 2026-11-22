package jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.InstrumentProcedure;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureAltitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedurePathDescriptor;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureType;

/**
 * Loads RJTT/RJAA SID/STAR extracts from classpath.
 * Committed sources of truth are {@code aip/rjtt-sid-star-20261101.json} (slice 1)
 * and {@code aip/slice3-20261101.json} (slice 3). Gradle {@code processResources}
 * copies both to these classpath paths. Slice 1 is never overwritten by slice 3.
 */
public final class ProcedureCatalog {

    public static final String CLASS_PATH_RESOURCE = "fix/rjtt-sid-star-20261101.json";
    public static final String SLICE3_CLASS_PATH_RESOURCE = "fix/slice3-20261101.json";

    /** Slice-1 then slice-3; load order is stable for merge and conflict detection. */
    public static final List<String> CLASS_PATH_RESOURCES =
            List.of(CLASS_PATH_RESOURCE, SLICE3_CLASS_PATH_RESOURCE);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String sourceNotice;
    private final Map<String, InstrumentProcedure> proceduresById;

    public ProcedureCatalog() {
        try {
            Loaded loaded = loadAll(CLASS_PATH_RESOURCES);
            this.sourceNotice = loaded.sourceNotice();
            this.proceduresById = Collections.unmodifiableMap(loaded.proceduresById());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load procedure catalog from " + CLASS_PATH_RESOURCES, e);
        }
    }

    /** Test constructor. */
    ProcedureCatalog(String sourceNotice, Map<String, InstrumentProcedure> proceduresById) {
        this.sourceNotice = sourceNotice;
        this.proceduresById = Collections.unmodifiableMap(new LinkedHashMap<>(proceduresById));
    }

    public String getSourceNotice() {
        return sourceNotice;
    }

    public Optional<InstrumentProcedure> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(proceduresById.get(id.trim()));
    }

    public List<InstrumentProcedure> listByAirport(String airportIcao) {
        if (airportIcao == null || airportIcao.isBlank()) {
            return List.of();
        }
        String key = airportIcao.trim().toUpperCase(Locale.ROOT);
        List<InstrumentProcedure> result = new ArrayList<>();
        for (InstrumentProcedure procedure : proceduresById.values()) {
            if (key.equalsIgnoreCase(procedure.airportIcao())) {
                result.add(procedure);
            }
        }
        return result;
    }

    public List<InstrumentProcedure> all() {
        return List.copyOf(proceduresById.values());
    }

    private static Loaded loadAll(List<String> resourcePaths) throws IOException {
        String notice = "";
        Map<String, InstrumentProcedure> byId = new LinkedHashMap<>();
        for (String resourcePath : resourcePaths) {
            Loaded part = load(resourcePath);
            if (notice.isBlank() && !part.sourceNotice().isBlank()) {
                notice = part.sourceNotice();
            }
            for (Map.Entry<String, InstrumentProcedure> entry : part.proceduresById().entrySet()) {
                InstrumentProcedure previous = byId.put(entry.getKey(), entry.getValue());
                if (previous != null) {
                    throw new IllegalStateException(
                            "Duplicate procedure id across catalog resources: " + entry.getKey());
                }
            }
        }
        return new Loaded(notice, attachSidTransitions(byId));
    }

    private static Loaded load(String resourcePath) throws IOException {
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            JsonNode root = OBJECT_MAPPER.readTree(is);
            String notice = textOrEmpty(root.get("sourceNotice"));
            Map<String, InstrumentProcedure> byId = new LinkedHashMap<>();
            JsonNode procedures = root.get("procedures");
            if (procedures != null && procedures.isArray()) {
                for (JsonNode node : procedures) {
                    InstrumentProcedure procedure = parseProcedure(node);
                    byId.put(procedure.id(), procedure);
                }
            }
            return new Loaded(notice, byId);
        }
    }

    /**
     * Extract SIDs list empty {@code enrouteTransitions}; attach same-family
     * {@link ProcedureType#SID_TRANSITION} rows whose {@code connectsAt} equals the SID end fix.
     * Uses existing transition IDs only — invents nothing.
     */
    private static Map<String, InstrumentProcedure> attachSidTransitions(
            Map<String, InstrumentProcedure> byId) {
        List<InstrumentProcedure> transitions = byId.values().stream()
                .filter(p -> p.type() == ProcedureType.SID_TRANSITION)
                .toList();
        Map<String, InstrumentProcedure> updated = new LinkedHashMap<>(byId);
        for (InstrumentProcedure procedure : byId.values()) {
            if (procedure.type() != ProcedureType.SID) {
                continue;
            }
            String endFix = procedure.endFixIdentifier();
            if (endFix == null || endFix.isBlank()) {
                continue;
            }
            List<String> attached = new ArrayList<>(procedure.enrouteTransitions());
            for (InstrumentProcedure transition : transitions) {
                if (!familyMatches(procedure.family(), transition.family())) {
                    continue;
                }
                if (transition.connectsAt() == null
                        || !endFix.equalsIgnoreCase(transition.connectsAt())) {
                    continue;
                }
                if (!attached.contains(transition.id())) {
                    attached.add(transition.id());
                }
            }
            if (!attached.equals(procedure.enrouteTransitions())) {
                updated.put(procedure.id(), procedure.withEnrouteTransitions(List.copyOf(attached)));
            }
        }
        return updated;
    }

    private static boolean familyMatches(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }

    private static InstrumentProcedure parseProcedure(JsonNode node) {
        String id = requiredText(node, "id");
        ProcedureType type = ProcedureType.valueOf(requiredText(node, "type"));
        String name = textOrEmpty(node.get("name"));
        String family = textOrEmpty(node.get("family"));
        String variant = textOrEmpty(node.get("variant"));
        String airportIcao = requiredText(node, "airportIcao");
        boolean rnav = node.path("rnav").asBoolean(false);
        List<String> runways = readStringList(node.get("applicableRunways"));
        List<ProcedureLeg> legs = new ArrayList<>();
        JsonNode legsNode = node.get("legs");
        if (legsNode != null && legsNode.isArray()) {
            for (JsonNode legNode : legsNode) {
                legs.add(parseLeg(legNode));
            }
        }
        List<String> transitions = readStringList(node.get("enrouteTransitions"));
        String connectsAt = null;
        JsonNode connects = node.get("connectsAt");
        if (connects != null && !connects.isNull() && !connects.asText().isBlank()) {
            connectsAt = connects.asText().trim();
        }
        return new InstrumentProcedure(
                id,
                type,
                name,
                family,
                variant,
                airportIcao,
                rnav,
                runways,
                List.copyOf(legs),
                transitions,
                connectsAt);
    }

    private static ProcedureLeg parseLeg(JsonNode node) {
        int serial = node.path("serial").asInt(0);
        ProcedurePathDescriptor descriptor =
                ProcedurePathDescriptor.fromJson(textOrEmpty(node.get("pathDescriptor")));
        String waypoint = null;
        JsonNode wp = node.get("waypointIdentifier");
        if (wp != null && !wp.isNull() && !wp.asText().isBlank()) {
            waypoint = wp.asText().trim();
        }
        boolean flyOver = node.path("flyOver").asBoolean(false);
        Double courseMagnetic = readNullableDouble(node.get("courseMagneticDeg"));
        Double courseTrue = readNullableDouble(node.get("courseTrueDeg"));
        Double distanceNm = readNullableDouble(node.get("distanceNm"));
        String turnDirection = null;
        JsonNode turn = node.get("turnDirection");
        if (turn != null && !turn.isNull() && !turn.asText().isBlank()) {
            turnDirection = turn.asText().trim();
        }
        ProcedureAltitude altitude = parseAltitude(node.get("altitude"));
        Integer speedKias = null;
        JsonNode speed = node.get("speed");
        if (speed != null && !speed.isNull() && speed.hasNonNull("kias")) {
            speedKias = speed.get("kias").asInt();
        }
        return new ProcedureLeg(
                serial,
                descriptor,
                waypoint,
                flyOver,
                courseMagnetic,
                courseTrue,
                distanceNm,
                turnDirection,
                altitude,
                speedKias);
    }

    private static ProcedureAltitude parseAltitude(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        AltitudeConstraint constraint = AltitudeConstraint.NONE;
        JsonNode constraintNode = node.get("constraint");
        if (constraintNode != null && !constraintNode.isNull() && !constraintNode.asText().isBlank()) {
            try {
                constraint = AltitudeConstraint.valueOf(constraintNode.asText().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                constraint = AltitudeConstraint.NONE;
            }
        }
        Integer feet = node.hasNonNull("ft") ? node.get("ft").asInt() : null;
        Integer fl = node.hasNonNull("fl") ? node.get("fl").asInt() : null;
        return new ProcedureAltitude(constraint, feet, fl);
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode child : node) {
            if (child != null && !child.isNull() && !child.asText().isBlank()) {
                values.add(child.asText().trim());
            }
        }
        return List.copyOf(values);
    }

    private static Double readNullableDouble(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asDouble();
    }

    private static String requiredText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new IllegalStateException("Procedure catalog missing required field: " + field);
        }
        return node.asText().trim();
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        String text = node.asText();
        return text != null ? text.trim() : "";
    }

    private record Loaded(String sourceNotice, Map<String, InstrumentProcedure> proceduresById) {
    }
}
