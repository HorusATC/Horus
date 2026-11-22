package jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.airport.RunwayDefinition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

public final class RunwayCatalog {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double FINAL_INTERCEPT_NM = 8.0;
    private static final double FINAL_EXTENSION_NM = 10.0;
    private static final double NM_PER_METER = 1.0 / 1852.0;

    private final List<RunwayDefinition> runways;

    public RunwayCatalog() {
        this.runways = loadRunways();
    }

    public List<RunwayDefinition> getAllRunways() {
        return Collections.unmodifiableList(runways);
    }

    public List<RunwayDefinition> findByAirportIcao(String icao) {
        if (icao == null || icao.isBlank()) {
            return List.of();
        }
        String key = icao.toUpperCase(Locale.ROOT).trim();
        return runways.stream().filter(r -> r.airportIcao().equals(key)).toList();
    }

    public Optional<RunwayDefinition> findRunway(String airportIcao, String designator) {
        if (airportIcao == null || designator == null) {
            return Optional.empty();
        }
        String icao = airportIcao.toUpperCase(Locale.ROOT).trim();
        String rwy = designator.toUpperCase(Locale.ROOT).trim();
        return runways.stream()
                .filter(r -> r.airportIcao().equals(icao) && r.designator().equals(rwy))
                .findFirst();
    }

    public Optional<FinalApproachGeometry> buildFinalApproachGeometry(RunwayDefinition runway) {
        double inboundCourseHeading = normalizeHeading(runway.heading());
        double outboundExtensionHeading = normalizeHeading(runway.heading() + 180);
        double[] intercept = GeodeticUtils.offsetPointNm(
                runway.thresholdLatitude(), runway.thresholdLongitude(), outboundExtensionHeading, FINAL_INTERCEPT_NM);
        double[] departureEnd = resolveDepartureEnd(runway);
        double[] extensionEnd = GeodeticUtils.offsetPointNm(
                runway.thresholdLatitude(), runway.thresholdLongitude(), outboundExtensionHeading, FINAL_EXTENSION_NM);
        return Optional.of(new FinalApproachGeometry(
                runway,
                inboundCourseHeading,
                intercept[0],
                intercept[1],
                departureEnd[0],
                departureEnd[1],
                extensionEnd[0],
                extensionEnd[1]));
    }

    /**
     * Far end for drawing / LDA geometry: opposite physical threshold when surveyed,
     * else landing threshold offset by LDA (displaced) or pavement length.
     */
    private double[] resolveDepartureEnd(RunwayDefinition runway) {
        Optional<RunwayDefinition> opposite = findRunway(runway.airportIcao(), reciprocalDesignator(runway.designator()));
        if (opposite.isPresent()) {
            RunwayDefinition opp = opposite.get();
            if (opp.physicalThresholdLatitude() != null && opp.physicalThresholdLongitude() != null) {
                return new double[] {opp.physicalThresholdLatitude(), opp.physicalThresholdLongitude()};
            }
            return new double[] {opp.thresholdLatitude(), opp.thresholdLongitude()};
        }
        int alongM = runway.ldaM() != null ? runway.ldaM() : runway.lengthM();
        return GeodeticUtils.offsetPointNm(
                runway.thresholdLatitude(),
                runway.thresholdLongitude(),
                runway.heading(),
                alongM * NM_PER_METER);
    }

    public List<Map<String, Object>> toApiMaps() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RunwayDefinition rwy : runways) {
            buildFinalApproachGeometry(rwy).ifPresent(geom -> result.add(geom.toApiMap()));
        }
        return result;
    }

    /**
     * Opposite runway end derived from one catalog definition (heading + length offset).
     * Used only when the reciprocal designator is missing from surveyed JSON.
     * Coordinates are geometric derivatives of the source entry, not AIP survey values.
     */
    public static RunwayDefinition deriveReciprocal(RunwayDefinition source) {
        double lengthNm = source.lengthM() * NM_PER_METER;
        double[] oppositeThreshold = GeodeticUtils.offsetPointNm(
                source.thresholdLatitude(),
                source.thresholdLongitude(),
                source.heading(),
                lengthNm);
        return new RunwayDefinition(
                source.airportIcao(),
                reciprocalDesignator(source.designator()),
                normalizeHeading(source.heading() + 180),
                oppositeThreshold[0],
                oppositeThreshold[1],
                source.lengthM(),
                source.widthM(),
                null,
                source.ldaM(),
                source.toraM(),
                false,
                null,
                null,
                RunwayDefinition.SOURCE_DERIVED_RECIPROCAL,
                true);
    }

    static String reciprocalDesignator(String designator) {
        String trimmed = designator.trim().toUpperCase(Locale.ROOT);
        int i = 0;
        while (i < trimmed.length() && Character.isDigit(trimmed.charAt(i))) {
            i++;
        }
        if (i == 0) {
            throw new IllegalArgumentException("Invalid runway designator: " + designator);
        }
        int number = Integer.parseInt(trimmed.substring(0, i));
        String suffix = trimmed.substring(i);
        int reciprocalNumber = ((number - 1 + 18) % 36) + 1;
        String reciprocalSuffix = switch (suffix) {
            case "L" -> "R";
            case "R" -> "L";
            default -> suffix;
        };
        return String.format(Locale.ROOT, "%02d%s", reciprocalNumber, reciprocalSuffix);
    }

    static double normalizeHeading(double heading) {
        double h = heading % 360;
        return h < 0 ? h + 360 : h;
    }

    private static List<RunwayDefinition> loadRunways() {
        try (InputStream is = new ClassPathResource("fix/runways.json").getInputStream()) {
            JsonNode root = OBJECT_MAPPER.readTree(is);
            List<RunwayDefinition> surveyed = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (JsonNode node : root.get("runways")) {
                RunwayDefinition def = parseRunway(node);
                surveyed.add(def);
                keys.add(catalogKey(def.airportIcao(), def.designator()));
            }
            List<RunwayDefinition> list = new ArrayList<>(surveyed);
            for (RunwayDefinition base : surveyed) {
                String reciprocal = reciprocalDesignator(base.designator());
                if (!keys.contains(catalogKey(base.airportIcao(), reciprocal))) {
                    list.add(deriveReciprocal(base));
                }
            }
            return list;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load runways.json", e);
        }
    }

    private static String catalogKey(String airportIcao, String designator) {
        return airportIcao.toUpperCase(Locale.ROOT) + "/" + designator.toUpperCase(Locale.ROOT);
    }

    private static RunwayDefinition parseRunway(JsonNode node) {
        boolean usable = !node.has("usableForLanding") || node.get("usableForLanding").asBoolean(true);
        boolean displaced = node.has("displacedLandingThreshold")
                && node.get("displacedLandingThreshold").asBoolean(false);
        String source = node.has("source")
                ? node.get("source").asText()
                : RunwayDefinition.SOURCE_AIP_AD_2_12;
        return new RunwayDefinition(
                node.get("airportIcao").asText(),
                node.get("designator").asText(),
                node.get("heading").asDouble(),
                node.get("thresholdLatitude").asDouble(),
                node.get("thresholdLongitude").asDouble(),
                node.get("lengthM").asInt(),
                optionalInt(node, "widthM"),
                optionalDouble(node, "thresholdElevationFt"),
                optionalInt(node, "ldaM"),
                optionalInt(node, "toraM"),
                displaced,
                optionalDouble(node, "physicalThresholdLatitude"),
                optionalDouble(node, "physicalThresholdLongitude"),
                source,
                usable);
    }

    private static Integer optionalInt(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }

    private static Double optionalDouble(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asDouble() : null;
    }

    public record FinalApproachGeometry(
            RunwayDefinition runway,
            double approachCourseHeading,
            double interceptLatitude,
            double interceptLongitude,
            double departureEndLatitude,
            double departureEndLongitude,
            double extensionEndLatitude,
            double extensionEndLongitude) {

        public Map<String, Object> toApiMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("airportIcao", runway.airportIcao());
            map.put("designator", runway.designator());
            map.put("heading", runway.heading());
            map.put("thresholdLatitude", runway.thresholdLatitude());
            map.put("thresholdLongitude", runway.thresholdLongitude());
            map.put("departureEndLatitude", departureEndLatitude);
            map.put("departureEndLongitude", departureEndLongitude);
            map.put("approachCourseHeading", approachCourseHeading);
            map.put("extensionEndLatitude", extensionEndLatitude);
            map.put("extensionEndLongitude", extensionEndLongitude);
            map.put("interceptLatitude", interceptLatitude);
            map.put("interceptLongitude", interceptLongitude);
            map.put("usableForLanding", runway.usableForLanding());
            map.put("source", runway.source());
            if (runway.lengthM() > 0) {
                map.put("lengthM", runway.lengthM());
            }
            if (runway.ldaM() != null) {
                map.put("ldaM", runway.ldaM());
            }
            return map;
        }
    }
}
