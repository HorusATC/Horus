package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.AtsRouteFixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.RunwayCatalog;

class AtisServiceTest {

    private AtisService atisService;

    @BeforeEach
    void setUp() {
        atisService = new AtisService(new RunwayCatalog(), new AtsRouteFixPositionRepository());
    }

    @Test
    void defaultsAreRjttAndRjaa16RAlpha() {
        List<Map<String, Object>> all = atisService.getAllAtis();
        assertEquals(2, all.size());
        Map<String, Object> rjtt = all.stream()
                .filter(m -> "RJTT".equals(m.get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertEquals("16R", rjtt.get("runwayInUse"));
        assertEquals("A", rjtt.get("informationIdent"));
        assertTrue(((String) rjtt.get("text")).contains("RUNWAY 16R"));
        List<?> available = (List<?>) rjtt.get("availableRunways");
        assertTrue(available.contains("34L"));
        assertTrue(available.contains("22"));
        assertTrue(available.contains("05"));
        assertEquals(8, available.size());
    }

    @Test
    void runwayChangeToCrossRunwayAdvancesIdent() {
        Map<String, Object> updated = atisService.updateAtis("RJTT", "22", null);
        assertEquals("22", updated.get("runwayInUse"));
        assertEquals("B", updated.get("informationIdent"));
        assertTrue(((String) updated.get("text")).contains("RUNWAY 22"));
    }

    @Test
    void runwayChangeAdvancesIdentAndText() {
        Map<String, Object> updated = atisService.updateAtis("RJTT", "34L", null);
        assertEquals("34L", updated.get("runwayInUse"));
        assertEquals("B", updated.get("informationIdent"));
        assertTrue(((String) updated.get("text")).contains("RUNWAY 34L"));
        assertTrue(((String) updated.get("text")).contains("INFORMATION BRAVO"));
    }

    @Test
    void sameRunwayPutDoesNotAdvanceIdent() {
        atisService.updateAtis("RJTT", "34L", null);
        Map<String, Object> again = atisService.updateAtis("RJTT", "34L", null);
        assertEquals("B", again.get("informationIdent"));
    }

    @Test
    void identWrapsFromZuluToAlphaOnRunwayChange() {
        atisService.updateAtis("RJTT", "16R", 'Z');
        Map<String, Object> updated = atisService.updateAtis("RJTT", "34L", null);
        assertEquals("A", updated.get("informationIdent"));
        assertTrue(((String) updated.get("text")).contains("INFORMATION ALPHA"));
    }

    @Test
    void unknownRunwayRejected() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> atisService.updateAtis("RJTT", "99X", null));
        assertTrue(ex.getMessage().contains("Unknown runway"));
    }

    @Test
    void unknownAirportRejected() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> atisService.updateAtis("XXXX", "16R", null));
        assertTrue(ex.getMessage().contains("Unknown airport"));
    }
}
