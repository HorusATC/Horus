package jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Per-procedure geometry and constraint checks generated from
 * {@code aip/slice3-20261101.json}. Values exceeding 0.05 NM / 0.05° are left as
 * published (flaggedPairs); nothing is rewritten.
 */
class AipSlice3GeometryAndConstraintTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double METERS_PER_NM = 1852.0;
    private static final double FLAG_NM = 0.05;
    private static final double FLAG_DEG = 0.05;
    private static final double HARD_NM = 0.10;
    private static final double HARD_DEG = 0.10;

    private static JsonNode root;
    private static Map<String, JsonNode> proceduresById;
    private static Map<String, double[]> waypointDeg;

    @BeforeAll
    static void load() throws Exception {
        root = readAipJson("slice3-20261101.json");
        proceduresById = new HashMap<>();
        for (JsonNode procedure : root.get("procedures")) {
            proceduresById.put(procedure.get("id").asText(), procedure);
        }
        waypointDeg = new HashMap<>();
        for (JsonNode wp : root.get("waypoints")) {
            waypointDeg.put(
                    wp.get("identifier").asText(),
                    new double[] {wp.get("latitudeDeg").asDouble(), wp.get("longitudeDeg").asDouble()});
        }
    }

    @Test
    void everyTfWithPublishedGeometryMatchesSphereOrIsInFlaggedPairs() {
        Map<String, List<String>> expectedFlags = new HashMap<>();
        for (JsonNode flag : root.path("crossCheck").path("flaggedPairs")) {
            String id = flag.get("id").asText();
            expectedFlags
                    .computeIfAbsent(id, ignored -> new ArrayList<>())
                    .add(flag.get("from").asText() + "->" + flag.get("to").asText());
        }

        List<String> unexpectedFlags = new ArrayList<>();
        List<String> missingFlags = new ArrayList<>();
        List<String> hardFailures = new ArrayList<>();
        Map<String, List<String>> actualFlags = new HashMap<>();

        for (JsonNode procedure : root.get("procedures")) {
            String id = procedure.get("id").asText();
            List<JsonNode> fixLegs = new ArrayList<>();
            for (JsonNode leg : procedure.get("legs")) {
                String descriptor = leg.path("pathDescriptor").asText("").toUpperCase(Locale.ROOT);
                if ("IF".equals(descriptor) || "TF".equals(descriptor) || "DF".equals(descriptor)) {
                    fixLegs.add(leg);
                }
            }
            for (int i = 0; i < fixLegs.size(); i++) {
                JsonNode leg = fixLegs.get(i);
                String descriptor = leg.path("pathDescriptor").asText("").toUpperCase(Locale.ROOT);
                if (!"TF".equals(descriptor)) {
                    continue;
                }
                if (!leg.hasNonNull("distanceNm") || !leg.hasNonNull("courseTrueDeg")) {
                    continue;
                }
                if (i == 0) {
                    continue;
                }
                String from = fixLegs.get(i - 1).path("waypointIdentifier").asText(null);
                String to = leg.path("waypointIdentifier").asText(null);
                if (from == null || to == null) {
                    continue;
                }
                double[] a = waypointDeg.get(from);
                double[] b = waypointDeg.get(to);
                if (a == null || b == null) {
                    hardFailures.add(id + " missing coords " + from + "/" + to);
                    continue;
                }
                double publishedNm = leg.get("distanceNm").asDouble();
                double publishedTrue = leg.get("courseTrueDeg").asDouble();
                double computedNm = greatCircleNm(a[0], a[1], b[0], b[1]);
                double computedTrue = initialBearingDeg(a[0], a[1], b[0], b[1]);
                double diffNm = Math.abs(computedNm - publishedNm);
                double diffDeg = smallestAngleDiff(computedTrue, publishedTrue);
                String pair = from + "->" + to;
                if (diffNm > HARD_NM || diffDeg > HARD_DEG) {
                    hardFailures.add(String.format(
                            Locale.ROOT,
                            "%s %s diffNm=%.4f diffDeg=%.4f",
                            id,
                            pair,
                            diffNm,
                            diffDeg));
                }
                if (diffNm > FLAG_NM || diffDeg > FLAG_DEG) {
                    actualFlags.computeIfAbsent(id, ignored -> new ArrayList<>()).add(pair);
                    List<String> expected = expectedFlags.getOrDefault(id, List.of());
                    if (!expected.contains(pair)) {
                        unexpectedFlags.add(id + " " + pair);
                    }
                }
            }
        }

        for (Map.Entry<String, List<String>> entry : expectedFlags.entrySet()) {
            List<String> actual = actualFlags.getOrDefault(entry.getKey(), List.of());
            for (String pair : entry.getValue()) {
                if (!actual.contains(pair)) {
                    missingFlags.add(entry.getKey() + " " + pair);
                }
            }
        }

        assertThat(hardFailures).as("pairs beyond 0.10 NM/° must not occur").isEmpty();
        assertThat(unexpectedFlags).as("unexpected geometry flags").isEmpty();
        assertThat(missingFlags).as("flaggedPairs must be reproducible").isEmpty();
        assertThat(root.path("crossCheck").path("flaggedPairs")).hasSize(16);
    }

    @Test
    void representativeProcedureConstraintsMatchExtractTable() {
        assertVa("LAXAS4.RWY16R", 158.0, 150.0, 500);
        assertDf("LAXAS4.RWY16R", 2, "T6R11", false);
        assertTf("LAXAS4.RWY16R", 3, "TAURA", 9000, null, 9.0, "AT_OR_ABOVE");
        assertTf("LAXAS4.RWY16R", 4, "IMOLA", null, 150, 18.8, "AT_OR_ABOVE");
        assertTf("LAXAS4.RWY16R", 5, "LAXAS", null, 170, 12.8, "AT_OR_ABOVE");

        assertVa("TETRA8.RWY16R", 157.0, 149.6, 600);
        assertDf("TETRA8.RWY16R", 2, "ASPEN", true);
        assertTf("TETRA8.RWY16R", 3, "A6R11", null, null, 4.5, null);
        assertThat(proceduresById.get("TETRA8.RWY16R").get("legs")).hasSize(6);
        assertThat(lastFix("TETRA8.RWY16R")).isEqualTo("TETRA");

        JsonNode agris = proceduresById.get("TETRA8.AGRIS");
        assertThat(agris.path("type").asText()).isEqualTo("SID_TRANSITION");
        assertIf("TETRA8.AGRIS", 1, "TETRA", 12000, null, "AT_OR_ABOVE");
        assertTf("TETRA8.AGRIS", 2, "RADIX", null, 150, 13.9, "AT_OR_ABOVE");
        assertTf("TETRA8.AGRIS", 3, "WHARF", null, 180, 8.7, "AT_OR_ABOVE");
        assertTf("TETRA8.AGRIS", 4, "AGRIS", null, 200, 19.3, "AT_OR_ABOVE");

        assertDf("PEDLA1.RWY34L", 2, "ASTRA", true);

        JsonNode rutase = proceduresById.get("RUTASE");
        assertIf("RUTASE", 1, "RUTAS", null, null, null);
        assertTf("RUTASE", 4, "SIGMA", 8000, null, 12.3, "AT_OR_BELOW");
        assertThat(rutase.path("holds")).hasSize(4);
        List<String> holdFixes = new ArrayList<>();
        for (JsonNode hold : rutase.path("holds")) {
            holdFixes.add(hold.path("waypointIdentifier").asText());
        }
        assertThat(holdFixes).containsExactly("RUTAS", "AQUOS", "CORGI", "ELGAR");

        JsonNode aa657 = leg("SUPOKG", 2);
        assertThat(aa657.path("waypointIdentifier").asText()).isEqualTo("AA657");
        assertThat(aa657.path("speed").path("kias").asInt()).isEqualTo(250);
        assertThat(aa657.path("speed").path("restriction").asText()).isEqualTo("AT_OR_BELOW");
        assertThat(aa657.path("speed").path("uncertain").asBoolean()).isFalse();
        JsonNode vogue = leg("SUPOKG", 4);
        assertThat(vogue.path("waypointIdentifier").asText()).isEqualTo("VOGUE");
        assertThat(vogue.path("speed").path("kias").asInt()).isEqualTo(210);
        assertThat(vogue.path("speed").path("uncertain").asBoolean()).isTrue();

        // CHANGE-split rows were rejoined; leg counts match the coding table.
        assertThat(proceduresById.get("OLVAN2.RWY16L").path("legs")).hasSize(4);
        assertThat(proceduresById.get("ROVER3A.RWY16L").path("legs")).hasSize(7);
        assertThat(lastFix("OLVAN2.RWY16L")).isEqualTo("OLVAN");
        assertThat(lastFix("ROVER3A.RWY16L")).isEqualTo("ROVER");
    }

    @Test
    void magneticVariationStoredPerAirportAndNotAppliedAsHack() {
        assertThat(root.path("package").path("headingNote").asText())
                .contains("true")
                .containsIgnoringCase("magnetic");
        for (JsonNode procedure : root.get("procedures")) {
            String airport = procedure.path("airportIcao").asText();
            Double magVar = null;
            for (JsonNode leg : procedure.path("legs")) {
                if (leg.hasNonNull("magneticVariationDeg")) {
                    magVar = leg.get("magneticVariationDeg").asDouble();
                    break;
                }
            }
            if (magVar == null) {
                for (JsonNode hold : procedure.path("holds")) {
                    if (hold.hasNonNull("magneticVariationDeg")) {
                        magVar = hold.get("magneticVariationDeg").asDouble();
                        break;
                    }
                }
            }
            if ("RJTT".equals(airport) && magVar != null) {
                assertThat(magVar).isEqualTo(-7.9);
            }
            if ("RJAA".equals(airport) && magVar != null) {
                assertThat(magVar).isEqualTo(-7.5);
            }
        }
    }

    private static void assertVa(String id, double magnetic, double trueCourse, int ft) {
        JsonNode leg = leg(id, 1);
        assertThat(leg.path("pathDescriptor").asText()).isEqualToIgnoringCase("VA");
        assertThat(leg.path("courseMagneticDeg").asDouble()).isEqualTo(magnetic);
        assertThat(leg.path("courseTrueDeg").asDouble()).isEqualTo(trueCourse);
        assertThat(leg.path("altitude").path("constraint").asText()).isEqualTo("AT_OR_ABOVE");
        assertThat(leg.path("altitude").path("ft").asInt()).isEqualTo(ft);
    }

    private static void assertDf(String id, int serial, String fix, boolean flyOver) {
        JsonNode leg = leg(id, serial);
        assertThat(leg.path("pathDescriptor").asText()).isEqualToIgnoringCase("DF");
        assertThat(leg.path("waypointIdentifier").asText()).isEqualTo(fix);
        assertThat(leg.path("flyOver").asBoolean()).isEqualTo(flyOver);
    }

    private static void assertIf(
            String id, int serial, String fix, Integer ft, Integer fl, String constraint) {
        JsonNode leg = leg(id, serial);
        assertThat(leg.path("pathDescriptor").asText()).isEqualToIgnoringCase("IF");
        assertThat(leg.path("waypointIdentifier").asText()).isEqualTo(fix);
        if (constraint == null) {
            assertThat(leg.path("altitude").isMissingNode() || leg.path("altitude").isNull()).isTrue();
            return;
        }
        assertThat(leg.path("altitude").path("constraint").asText()).isEqualTo(constraint);
        if (ft != null) {
            assertThat(leg.path("altitude").path("ft").asInt()).isEqualTo(ft);
        }
        if (fl != null) {
            assertThat(leg.path("altitude").path("fl").asInt()).isEqualTo(fl);
        }
    }

    private static void assertTf(
            String id,
            int serial,
            String fix,
            Integer ft,
            Integer fl,
            Double distanceNm,
            String constraint) {
        JsonNode leg = leg(id, serial);
        assertThat(leg.path("pathDescriptor").asText()).isEqualToIgnoringCase("TF");
        assertThat(leg.path("waypointIdentifier").asText()).isEqualTo(fix);
        if (distanceNm != null) {
            assertThat(leg.path("distanceNm").asDouble()).isEqualTo(distanceNm);
        }
        if (constraint == null) {
            return;
        }
        assertThat(leg.path("altitude").path("constraint").asText()).isEqualTo(constraint);
        if (ft != null) {
            assertThat(leg.path("altitude").path("ft").asInt()).isEqualTo(ft);
        }
        if (fl != null) {
            assertThat(leg.path("altitude").path("fl").asInt()).isEqualTo(fl);
        }
    }

    private static JsonNode leg(String id, int serial) {
        JsonNode procedure = proceduresById.get(id);
        assertThat(procedure).as(id).isNotNull();
        for (JsonNode leg : procedure.get("legs")) {
            if (leg.path("serial").asInt() == serial) {
                return leg;
            }
        }
        throw new AssertionError("Missing serial " + serial + " on " + id);
    }

    private static String lastFix(String id) {
        JsonNode legs = proceduresById.get(id).get("legs");
        JsonNode last = legs.get(legs.size() - 1);
        return last.path("waypointIdentifier").asText();
    }

    private static double greatCircleNm(double lat1, double lon1, double lat2, double lon2) {
        double r1 = Math.toRadians(lat1);
        double r2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(r1) * Math.cos(r2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (EARTH_RADIUS_KM * 1000.0 * c) / METERS_PER_NM;
    }

    private static double initialBearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double r1 = Math.toRadians(lat1);
        double r2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(r2);
        double x = Math.cos(r1) * Math.sin(r2) - Math.sin(r1) * Math.cos(r2) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0) % 360.0;
    }

    private static double smallestAngleDiff(double a, double b) {
        double diff = Math.abs(a - b) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private static JsonNode readAipJson(String fileName) throws Exception {
        Path path = Path.of("../aip").resolve(fileName).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            path = Path.of("aip").resolve(fileName).toAbsolutePath().normalize();
        }
        assertThat(path).as("AIP JSON must exist at %s", path).isRegularFile();
        return OBJECT_MAPPER.readTree(Files.readString(path));
    }
}
