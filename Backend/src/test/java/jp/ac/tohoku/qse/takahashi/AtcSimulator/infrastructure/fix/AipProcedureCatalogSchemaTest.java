package jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.InstrumentProcedure;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedurePathDescriptor;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;

/**
 * Schema / fix-resolution checks for AIP SID/STAR JSON. Slice-2 inventory adds no
 * new coded legs; assignable procedures must resolve every non-VA fix.
 */
class AipProcedureCatalogSchemaTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Official missing SID/STAR plate filenames from spec/20261123-sid-star-slice2. */
    private static final List<String> EXPECTED_MISSING_SID_STAR_PLATES = List.of(
            "JP-AD-2.24.19-RJTT-en-JP.pdf",
            "JP-AD-2.24.21-RJTT-en-JP.pdf",
            "JP-AD-2.24.24-RJTT-en-JP.pdf",
            "JP-AD-2.24.13-RJTT-en-JP.pdf",
            "JP-AD-2.24.14-RJTT-en-JP.pdf",
            "JP-AD-2.24.15-RJTT-en-JP.pdf",
            "JP-AD-2.24.16-RJTT-en-JP.pdf",
            "JP-AD-2.24.17-RJTT-en-JP.pdf",
            "JP-AD-2.24.18-RJTT-en-JP.pdf",
            "JP-AD-2.24.87-RJTT-en-JP.pdf",
            "JP-AD-2.24.89-RJTT-en-JP.pdf",
            "JP-AD-2.24.28-RJTT-en-JP.pdf",
            "JP-AD-2.24.29-RJTT-en-JP.pdf",
            "JP-AD-2.24.61-RJTT-en-JP.pdf",
            "JP-AD-2.24.30-RJTT-en-JP.pdf",
            "JP-AD-2.24.31-RJTT-en-JP.pdf",
            "JP-AD-2.24.72-RJTT-en-JP.pdf",
            "JP-AD-2.24.22-RJTT-en-JP.pdf",
            "JP-AD-2.24.25-RJTT-en-JP.pdf",
            "JP-AD-2.24.73-RJTT-en-JP.pdf",
            "JP-AD-2.24.74-RJTT-en-JP.pdf",
            "JP-AD-2.24.78-RJTT-en-JP.pdf",
            "JP-AD-2.24.79-RJTT-en-JP.pdf",
            "JP-AD-2.24.80-RJTT-en-JP.pdf",
            "JP-AD-2.24.81-RJTT-en-JP.pdf",
            "JP-AD-2.24.82-RJTT-en-JP.pdf",
            "JP-AD-2.24.75-RJTT-en-JP.pdf",
            "JP-AD-2.24.76-RJTT-en-JP.pdf",
            "JP-AD-2.24.17-RJAA-en-JP.pdf",
            "JP-AD-2.24.20-RJAA-en-JP.pdf",
            "JP-AD-2.24.22-RJAA-en-JP.pdf",
            "JP-AD-2.24.10-RJAA-en-JP.pdf",
            "JP-AD-2.24.11-RJAA-en-JP.pdf",
            "JP-AD-2.24.12-RJAA-en-JP.pdf",
            "JP-AD-2.24.13-RJAA-en-JP.pdf",
            "JP-AD-2.24.14-RJAA-en-JP.pdf",
            "JP-AD-2.24.16-RJAA-en-JP.pdf",
            "JP-AD-2.24.18-RJAA-en-JP.pdf",
            "JP-AD-2.24.27-RJAA-en-JP.pdf",
            "JP-AD-2.24.28-RJAA-en-JP.pdf",
            "JP-AD-2.24.25-RJAA-en-JP.pdf",
            "JP-AD-2.24.26-RJAA-en-JP.pdf",
            "JP-AD-2.24.30-RJAA-en-JP.pdf",
            "JP-AD-2.24.45-RJAA-en-JP.pdf",
            "JP-AD-2.24.46-RJAA-en-JP.pdf",
            "JP-AD-2.24.47-RJAA-en-JP.pdf",
            "JP-AD-2.24.48-RJAA-en-JP.pdf",
            "JP-AD-2.24.49-RJAA-en-JP.pdf");

    private static final Set<String> REQUIRED_TOP_LEVEL =
            Set.of("sourceNotice", "package", "uncertain", "waypoints", "procedures");

    @Test
    void slice2InventoryMatchesTopLevelContractAndHasNoCodedLegs() throws Exception {
        JsonNode root = readAipJson("slice2-20261101.json");
        assertTopLevelContract(root);
        assertThat(root.get("sourceNotice").asText())
                .contains("Derived from AIP Japan (AIRAC 1 Nov 2026)");
        assertThat(root.get("procedures").isArray()).isTrue();
        assertThat(root.get("procedures")).isEmpty();
        assertThat(root.get("waypoints").isArray()).isTrue();
        assertThat(root.get("waypoints")).isEmpty();

        JsonNode missing = root.path("missingPlates").path("sidStarOnly");
        assertThat(missing.isArray()).isTrue();
        List<String> actual = new ArrayList<>();
        for (JsonNode node : missing) {
            actual.add(node.asText());
        }
        assertThat(actual).containsExactlyElementsOf(EXPECTED_MISSING_SID_STAR_PLATES);
    }

    @Test
    void existingCatalogMatchesTopLevelContract() throws Exception {
        JsonNode root = readAipJson("rjtt-sid-star-20261101.json");
        assertTopLevelContract(root);
        assertThat(root.get("sourceNotice").asText())
                .contains("Derived from AIP Japan (AIRAC 1 Nov 2026)");
        assertThat(root.get("procedures").isArray()).isTrue();
        assertThat(root.get("procedures")).isNotEmpty();
    }

    @Test
    void assignableCatalogProceduresResolveEveryNonVaFix() {
        ProcedureCatalog catalog = new ProcedureCatalog();
        AtsRouteFixPositionRepository fixes = new AtsRouteFixPositionRepository();
        List<String> unresolved = new ArrayList<>();

        for (InstrumentProcedure procedure : catalog.all()) {
            // Assignable primaries plus SID transitions (attached to SIDs; not assignable alone).
            boolean isTransition = procedure.type()
                    == jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure
                            .ProcedureType.SID_TRANSITION;
            if (!procedure.isAssignablePrimary() && !isTransition) {
                continue;
            }
            for (ProcedureLeg leg : procedure.legs()) {
                if (leg.pathDescriptor() == ProcedurePathDescriptor.VA) {
                    continue;
                }
                if (leg.pathDescriptor() == ProcedurePathDescriptor.HOLD) {
                    continue;
                }
                String fix = leg.waypointIdentifier();
                if (fix == null || fix.isBlank()) {
                    unresolved.add(procedure.id() + " serial=" + leg.serial() + " (blank fix)");
                    continue;
                }
                java.util.Optional<FixPosition> pos = fixes.findFixPositionByName(fix.trim());
                if (pos.isEmpty()) {
                    unresolved.add(procedure.id() + " -> " + fix.trim());
                }
            }
        }

        assertThat(unresolved)
                .as("every non-VA fix on assignable / transition procedures must resolve")
                .isEmpty();
        assertThat(catalog.findById("VISIP1")).isPresent();
        assertThat(catalog.findById("VISIP1").orElseThrow().isAssignablePrimary()).isFalse();
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

    private static JsonNode readAipJson(String fileName) throws Exception {
        Path path = Path.of("../aip").resolve(fileName).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            path = Path.of("aip").resolve(fileName).toAbsolutePath().normalize();
        }
        assertThat(path).as("AIP JSON must exist at %s", path).isRegularFile();
        return OBJECT_MAPPER.readTree(Files.readString(path));
    }
}
