package jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MSA Catalog Tests")
class MsaCatalogTest {

    private MsaCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new MsaCatalog();
    }

    @Test
    @DisplayName("Catalog loads successfully with expected parameters")
    void testCatalogLoads() {
        assertNotNull(catalog);
        assertEquals(45.0, catalog.getPredictionTimeSeconds());
        assertEquals(1.0, catalog.getSuppressionDistanceNm());
        assertEquals(1.0, catalog.getApproachCorridorWidthNm());
        assertEquals(300.0, catalog.getApproachFloorGradientFtPerNm());
    }

    @Test
    @DisplayName("Catalog contains expected airports")
    void testAirports() {
        var airports = catalog.getAirports();
        assertNotNull(airports);
        assertTrue(airports.size() >= 2);

        boolean hasRjtt = airports.stream().anyMatch(a -> a.icao().equals("RJTT"));
        boolean hasRjaa = airports.stream().anyMatch(a -> a.icao().equals("RJAA"));

        assertTrue(hasRjtt, "Catalog should contain RJTT");
        assertTrue(hasRjaa, "Catalog should contain RJAA");
    }

    @Test
    @DisplayName("Catalog contains expected MSA areas")
    void testAreas() {
        var areas = catalog.getAreas();
        assertNotNull(areas);
        assertTrue(areas.size() >= 3);

        boolean hasWestMountains = areas.stream().anyMatch(a -> a.id().equals("west-mountains"));
        boolean hasIzuSouth = areas.stream().anyMatch(a -> a.id().equals("izu-south"));
        boolean hasKanto = areas.stream().anyMatch(a -> a.id().equals("kanto-plain-and-bay"));

        assertTrue(hasWestMountains, "Catalog should contain west-mountains area");
        assertTrue(hasIzuSouth, "Catalog should contain izu-south area");
        assertTrue(hasKanto, "Catalog should contain kanto-plain-and-bay area");
    }

    @Test
    @DisplayName("West mountains area has correct minimum altitude")
    void testWestMountainsAltitude() {
        var area = catalog.getAreas().stream()
                .filter(a -> a.id().equals("west-mountains"))
                .findFirst()
                .orElse(null);

        assertNotNull(area);
        assertEquals(10000.0, area.minimumAltitudeFt());
    }

    @Test
    @DisplayName("Kanto plain area has correct minimum altitude")
    void testKantoPlainAltitude() {
        var area = catalog.getAreas().stream()
                .filter(a -> a.id().equals("kanto-plain-and-bay"))
                .findFirst()
                .orElse(null);

        assertNotNull(area);
        assertEquals(2500.0, area.minimumAltitudeFt());
    }

    @Test
    @DisplayName("Airport elevations are retrievable")
    void testAirportElevations() {
        double rjttElevation = catalog.getAirportElevationFt("RJTT");
        double rjaaElevation = catalog.getAirportElevationFt("RJAA");

        assertEquals(20.0, rjttElevation);
        assertEquals(140.0, rjaaElevation);
    }

    @Test
    @DisplayName("Unknown airport returns default elevation")
    void testUnknownAirport() {
        double elevation = catalog.getAirportElevationFt("UNKN");
        assertEquals(0.0, elevation);
    }

    @Test
    @DisplayName("Each area has valid polygon with at least 3 vertices")
    void testPolygonValidity() {
        for (var area : catalog.getAreas()) {
            assertNotNull(area.polygon());
            assertTrue(area.polygon().size() >= 3,
                    "Area " + area.id() + " should have at least 3 vertices");
        }
    }
}
