package jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.InstrumentProcedure;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedurePathDescriptor;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;

/**
 * Schema, fix-resolution, and merge checks for AIP slice-3 RNAV SID/STAR JSON.
 * Does not invent or rewrite published coordinates / courses.
 */
class AipSlice3ProcedureCatalogSchemaTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> REQUIRED_TOP_LEVEL =
            Set.of("sourceNotice", "package", "uncertain", "waypoints", "procedures");

    private static final Set<String> ENGINE_PATH_DESCRIPTORS =
            Set.of("IF", "TF", "VA", "DF", "HOLD");

    private static final List<String> EXPECTED_NEW_WAYPOINTS = List.of(
            "A4L11",
            "A4L12",
            "A4L13",
            "A4L14",
            "A4R21",
            "A4R22",
            "A4R23",
            "A6L21",
            "A6R11",
            "A6R12",
            "A6R13",
            "A6R14",
            "AA433",
            "AA451",
            "AA452",
            "AA453",
            "AA455",
            "AA456",
            "AA457",
            "AA458",
            "AA459",
            "AA460",
            "AA461",
            "AA631",
            "AA632",
            "AA651",
            "AA652",
            "AA653",
            "AA654",
            "AA655",
            "AA656",
            "AA657",
            "AA658",
            "AA659",
            "GUGVA",
            "T6L22",
            "T6L23",
            "T6L31",
            "T6L32",
            "T6L43",
            "T6R12",
            "T6R13",
            "TT503",
            "TT631");

    @Test
    void slice3MatchesTopLevelContractAndProcedureShape() throws Exception {
        JsonNode root = readAipJson("slice3-20261101.json");
        assertTopLevelContract(root);
        assertThat(root.get("sourceNotice").asText())
                .isEqualTo(
                        "Derived from AIP Japan (AIRAC 1 Nov 2026), may not be current, edited by the Horus project and not government-made.");
        assertThat(root.path("package").path("airports").isArray()).isTrue();
        assertThat(root.path("package").path("airports")).hasSize(2);
        assertThat(root.path("excluded").isArray()).isTrue();
        assertThat(root.path("excluded")).isEmpty();
        assertThat(root.path("crossCheck").path("coordinateConflicts")).isEmpty();
        assertThat(root.path("crossCheck").path("flaggedPairs")).hasSize(16);

        JsonNode waypoints = root.get("waypoints");
        assertThat(waypoints.isArray()).isTrue();
        assertThat(waypoints).hasSize(166);
        Set<String> waypointIds = new HashSet<>();
        for (JsonNode wp : waypoints) {
            String id = requiredText(wp, "identifier");
            assertThat(wp.hasNonNull("latitudeDeg")).isTrue();
            assertThat(wp.hasNonNull("longitudeDeg")).isTrue();
            assertThat(waypointIds.add(id)).as("duplicate waypoint %s", id).isTrue();
        }
        assertThat(waypointIds).containsAll(EXPECTED_NEW_WAYPOINTS);

        JsonNode procedures = root.get("procedures");
        assertThat(procedures.isArray()).isTrue();
        assertThat(procedures).hasSize(114);

        int rjtt = 0;
        int rjaa = 0;
        int sid = 0;
        int star = 0;
        int transition = 0;
        Set<String> procedureIds = new HashSet<>();
        List<String> unknownDescriptors = new ArrayList<>();

        for (JsonNode procedure : procedures) {
            String id = requiredText(procedure, "id");
            assertThat(procedureIds.add(id)).as("duplicate procedure %s", id).isTrue();
            ProcedureType type = ProcedureType.valueOf(requiredText(procedure, "type"));
            String airport = requiredText(procedure, "airportIcao");
            assertThat(airport).isIn("RJTT", "RJAA");
            if ("RJTT".equals(airport)) {
                rjtt++;
            } else {
                rjaa++;
            }
            switch (type) {
                case SID -> sid++;
                case STAR -> star++;
                case SID_TRANSITION -> transition++;
            }
            assertThat(procedure.path("rnav").asBoolean()).isTrue();
            assertThat(procedure.path("legs").isArray()).isTrue();
            assertThat(procedure.path("legs")).isNotEmpty();

            int expectedSerial = 1;
            for (JsonNode leg : procedure.get("legs")) {
                assertThat(leg.path("serial").asInt()).isEqualTo(expectedSerial++);
                String descriptorRaw = requiredText(leg, "pathDescriptor");
                String descriptor = descriptorRaw.toUpperCase(Locale.ROOT);
                if (!ENGINE_PATH_DESCRIPTORS.contains(descriptor)) {
                    unknownDescriptors.add(id + ":" + descriptorRaw);
                }
                ProcedurePathDescriptor parsed = ProcedurePathDescriptor.fromJson(descriptorRaw);
                assertThat(parsed).isNotEqualTo(ProcedurePathDescriptor.UNKNOWN);
                assertExhaustiveDescriptor(parsed);

                if (parsed == ProcedurePathDescriptor.VA) {
                    assertThat(leg.path("courseTrueDeg").isNumber()).isTrue();
                    assertThat(leg.path("altitude").isObject()).isTrue();
                } else {
                    String fix = requiredText(leg, "waypointIdentifier");
                    assertThat(waypointIds).contains(fix);
                }

                // TF with a published true course must also carry magnetic course and distance.
                if (parsed == ProcedurePathDescriptor.TF && leg.hasNonNull("courseTrueDeg")) {
                    assertThat(leg.hasNonNull("courseMagneticDeg"))
                            .as("%s serial=%s TF has courseTrueDeg but missing courseMagneticDeg", id, leg.path("serial"))
                            .isTrue();
                    assertThat(leg.hasNonNull("distanceNm"))
                            .as("%s serial=%s TF has courseTrueDeg but missing distanceNm", id, leg.path("serial"))
                            .isTrue();
                }

                JsonNode altitude = leg.get("altitude");
                if (altitude != null && !altitude.isNull()) {
                    assertThat(altitude.hasNonNull("constraint")).isTrue();
                    boolean hasFt = altitude.hasNonNull("ft");
                    boolean hasFl = altitude.hasNonNull("fl");
                    assertThat(hasFt || hasFl)
                            .as("%s altitude must have ft or fl", id)
                            .isTrue();
                    assertThat(hasFt && hasFl)
                            .as("%s altitude must not have both ft and fl", id)
                            .isFalse();
                }

                JsonNode speed = leg.get("speed");
                if (speed != null && !speed.isNull()) {
                    assertThat(speed.hasNonNull("kias")).isTrue();
                    if (speed.path("uncertain").asBoolean(false)) {
                        JsonNode restriction = speed.get("restriction");
                        assertThat(restriction == null || restriction.isNull())
                                .as("%s unsigned speed must not invent restriction", id)
                                .isTrue();
                    }
                }
            }
        }

        assertThat(unknownDescriptors).isEmpty();
        assertThat(rjtt).isEqualTo(66);
        assertThat(rjaa).isEqualTo(48);
        assertThat(sid).isEqualTo(91);
        assertThat(transition).isEqualTo(7);
        assertThat(star).isEqualTo(16);

        JsonNode notIn = root.path("crossCheck").path("horusWaypointsJson").path("notInWaypointsJson");
        List<String> listedNew = new ArrayList<>();
        for (JsonNode node : notIn) {
            listedNew.add(node.asText());
        }
        assertThat(listedNew).containsExactlyElementsOf(EXPECTED_NEW_WAYPOINTS);
    }

    @Test
    void mergedCatalogLoadsSlice1AndSlice3WithoutIdCollision() {
        ProcedureCatalog catalog = new ProcedureCatalog();
        assertThat(catalog.getSourceNotice())
                .contains("Derived from AIP Japan (AIRAC 1 Nov 2026)");

        assertThat(catalog.findById("OSHIMA1A")).isPresent();
        assertThat(catalog.findById("VAMOS4.RWY34L_34R")).isPresent();
        assertThat(catalog.findById("VISIP1")).isPresent();
        assertThat(catalog.findById("LAXAS4.RWY16R")).isPresent();
        assertThat(catalog.findById("TETRA8.RWY16R")).isPresent();
        assertThat(catalog.findById("RUTASE")).isPresent();

        assertThat(catalog.listByAirport("RJTT")).hasSize(24 + 66);
        assertThat(catalog.listByAirport("RJAA")).hasSize(48);

        long stars = catalog.all().stream().filter(p -> p.type() == ProcedureType.STAR).count();
        long transitions =
                catalog.all().stream().filter(p -> p.type() == ProcedureType.SID_TRANSITION).count();
        assertThat(stars).isEqualTo(15 + 16);
        assertThat(transitions).isEqualTo(2 + 7);

        InstrumentProcedure tetra = catalog.findById("TETRA8.RWY16R").orElseThrow();
        assertThat(tetra.enrouteTransitions())
                .contains("TETRA8.AGRIS", "TETRA8.KIMIN", "TETRA8.ENPAR");
        InstrumentProcedure rover = catalog.findById("ROVER4C.RWY16R").orElseThrow();
        assertThat(rover.enrouteTransitions())
                .contains("ROVER4C.AKAGI", "ROVER4C.BRUCE", "ROVER4C.INUBO");
    }

    @Test
    void everySlice3AssignableOrTransitionFixResolvesInWaypointsJson() {
        ProcedureCatalog catalog = new ProcedureCatalog();
        AtsRouteFixPositionRepository fixes = new AtsRouteFixPositionRepository();
        List<String> unresolved = new ArrayList<>();

        for (InstrumentProcedure procedure : catalog.all()) {
            if (!"RJTT".equals(procedure.airportIcao()) && !"RJAA".equals(procedure.airportIcao())) {
                continue;
            }
            boolean isTransition = procedure.type() == ProcedureType.SID_TRANSITION;
            if (!procedure.isAssignablePrimary() && !isTransition) {
                continue;
            }
            // Skip slice-1 VISIP (not assignable) already covered elsewhere.
            for (ProcedureLeg leg : procedure.legs()) {
                if (leg.pathDescriptor() == ProcedurePathDescriptor.VA
                        || leg.pathDescriptor() == ProcedurePathDescriptor.HOLD) {
                    continue;
                }
                assertExhaustiveDescriptor(leg.pathDescriptor());
                String fix = leg.waypointIdentifier();
                if (fix == null || fix.isBlank()) {
                    unresolved.add(procedure.id() + " serial=" + leg.serial() + " (blank fix)");
                    continue;
                }
                if (fixes.findFixPositionByName(fix.trim()).isEmpty()) {
                    unresolved.add(procedure.id() + " -> " + fix.trim());
                }
            }
        }

        assertThat(unresolved).isEmpty();

        AtsRouteFixPositionRepository repo = new AtsRouteFixPositionRepository();
        for (String name : EXPECTED_NEW_WAYPOINTS) {
            assertThat(repo.findFixPositionByName(name)).as(name).isPresent();
        }
        // Existing coordinates must not be rewritten.
        FixPosition anzac = repo.findFixPositionByName("ANZAC").orElseThrow();
        assertThat(anzac.latitude.toDouble()).isCloseTo(34.841339, within(1e-9));
        assertThat(anzac.longitude.toDouble()).isCloseTo(139.696308, within(1e-9));
    }

    private static void assertExhaustiveDescriptor(ProcedurePathDescriptor descriptor) {
        // Fails compilation if ProcedurePathDescriptor gains a value without updating callers.
        switch (descriptor) {
            case IF, TF, VA, DF, HOLD -> {
                // supported
            }
            case UNKNOWN -> throw new AssertionError("UNKNOWN path descriptor is not allowed in slice 3");
        }
    }

    private static void assertTopLevelContract(JsonNode root) {
        Iterator<String> fieldNames = root.fieldNames();
        List<String> names = new ArrayList<>();
        fieldNames.forEachRemaining(names::add);
        assertThat(names).containsAll(REQUIRED_TOP_LEVEL);
        assertThat(root.get("sourceNotice").isTextual()).isTrue();
        assertThat(root.get("package").isObject()).isTrue();
        assertThat(root.get("uncertain").isArray()).isTrue();
        assertThat(root.get("waypoints").isArray()).isTrue();
        assertThat(root.get("procedures").isArray()).isTrue();
    }

    private static String requiredText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        assertThat(node).as("missing %s", field).isNotNull();
        assertThat(node.isNull() || node.asText().isBlank()).as("blank %s", field).isFalse();
        return node.asText().trim();
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
