package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;
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
        assertEquals(false, rjtt.get("windAvailable"));
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

    @Test
    void windSetAdvancesBothAirportIdents() {
        Map<String, Object> rjtt = atisService.updateAtis(
                "RJTT", "16R", null, WindUpdate.set(UniformWind.measured(160, 12)));
        assertEquals("B", rjtt.get("informationIdent"));
        assertEquals(true, rjtt.get("windAvailable"));
        assertEquals(160, rjtt.get("windDirectionDeg"));
        assertEquals(12, rjtt.get("windSpeedKt"));

        Map<String, Object> rjaa = atisService.getAllAtis().stream()
                .filter(m -> "RJAA".equals(m.get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertEquals("B", rjaa.get("informationIdent"));
        assertTrue(((String) rjaa.get("text")).contains("WIND 160 DEGREES 12 KNOTS"));
    }

    @Test
    void omitWindPreservesObservation() {
        atisService.updateAtis("RJTT", "16R", null, WindUpdate.set(UniformWind.measured(90, 8)));
        Map<String, Object> again = atisService.updateAtis("RJTT", "16R", null, WindUpdate.absent());
        assertEquals(90, again.get("windDirectionDeg"));
        assertEquals("B", again.get("informationIdent"));
    }

    @Test
    void nullWindClearsToMissing() {
        atisService.updateAtis("RJTT", "16R", null, WindUpdate.set(UniformWind.calm()));
        Map<String, Object> cleared = atisService.updateAtis("RJTT", "16R", null, WindUpdate.clear());
        assertEquals(false, cleared.get("windAvailable"));
        assertTrue(((String) cleared.get("text")).contains("WIND AND WEATHER NOT AVAILABLE"));
        assertEquals("C", cleared.get("informationIdent"));
    }

    @Test
    void sameWindResendDoesNotAdvanceIdent() {
        atisService.updateAtis("RJTT", "16R", null, WindUpdate.set(UniformWind.measured(160, 12)));
        Map<String, Object> again = atisService.updateAtis(
                "RJTT", "16R", null, WindUpdate.set(UniformWind.measured(160, 12)));
        assertEquals("B", again.get("informationIdent"));
    }

    @Test
    void runwayAndWindChangeAdvancesTargetOnce() {
        Map<String, Object> updated = atisService.updateAtis(
                "RJTT", "34L", null, WindUpdate.set(UniformWind.measured(270, 20)));
        assertEquals("B", updated.get("informationIdent"));
        assertEquals("34L", updated.get("runwayInUse"));
    }

    @Test
    void resetToDefaultsClearsWind() {
        atisService.updateAtis("RJTT", "16R", null, WindUpdate.set(UniformWind.measured(10, 5)));
        atisService.resetToDefaults();
        assertTrue(atisService.getSessionWind().isMissing());
        Map<String, Object> rjtt = atisService.getAllAtis().stream()
                .filter(m -> "RJTT".equals(m.get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertEquals(false, rjtt.get("windAvailable"));
        assertEquals("A", rjtt.get("informationIdent"));
    }

    @Test
    void invalidWindThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WindUpdate.set(UniformWind.measured(400, 12)));
    }
}
