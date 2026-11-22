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
        assertEquals(false, rjtt.get("qnhAvailable"));
        assertTrue(!rjtt.containsKey("qnhHpa"));
        assertTrue(((String) rjtt.get("text")).contains("RUNWAY 16R"));
        assertTrue(((String) rjtt.get("text")).contains("WIND AND WEATHER NOT AVAILABLE"));
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

    @Test
    void qnhSetAdvancesOnlyTargetAirportIdent() {
        Map<String, Object> rjtt = atisService.updateAtis(
                "RJTT", "16R", null, WindUpdate.absent(), QnhUpdate.set(1013));
        assertEquals(true, rjtt.get("qnhAvailable"));
        assertEquals(1013, rjtt.get("qnhHpa"));
        assertEquals("B", rjtt.get("informationIdent"));
        assertTrue(((String) rjtt.get("text")).contains("QNH 1013"));
        assertTrue(((String) rjtt.get("text")).contains("WIND NOT AVAILABLE"));

        Map<String, Object> rjaa = atisService.getAllAtis().stream()
                .filter(m -> "RJAA".equals(m.get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertEquals("A", rjaa.get("informationIdent"));
        assertEquals(false, rjaa.get("qnhAvailable"));
        assertTrue(((String) rjaa.get("text")).contains("WIND AND WEATHER NOT AVAILABLE"));
    }

    @Test
    void omitQnhPreservesObservation() {
        atisService.updateAtis("RJTT", "16R", null, WindUpdate.absent(), QnhUpdate.set(1013));
        Map<String, Object> again =
                atisService.updateAtis("RJTT", "16R", null, WindUpdate.absent(), QnhUpdate.absent());
        assertEquals(1013, again.get("qnhHpa"));
        assertEquals("B", again.get("informationIdent"));
    }

    @Test
    void nullQnhClearsToMissing() {
        atisService.updateAtis("RJTT", "16R", null, WindUpdate.absent(), QnhUpdate.set(1013));
        Map<String, Object> cleared =
                atisService.updateAtis("RJTT", "16R", null, WindUpdate.absent(), QnhUpdate.clear());
        assertEquals(false, cleared.get("qnhAvailable"));
        assertTrue(!cleared.containsKey("qnhHpa"));
        assertTrue(((String) cleared.get("text")).contains("WIND AND WEATHER NOT AVAILABLE"));
        assertEquals("C", cleared.get("informationIdent"));
    }

    @Test
    void sameQnhResendDoesNotAdvanceIdent() {
        atisService.updateAtis("RJTT", "16R", null, WindUpdate.absent(), QnhUpdate.set(1013));
        Map<String, Object> again =
                atisService.updateAtis("RJTT", "16R", null, WindUpdate.absent(), QnhUpdate.set(1013));
        assertEquals("B", again.get("informationIdent"));
    }

    @Test
    void runwayAndQnhChangeAdvancesTargetOnce() {
        Map<String, Object> updated = atisService.updateAtis(
                "RJTT", "34L", null, WindUpdate.absent(), QnhUpdate.set(995));
        assertEquals("B", updated.get("informationIdent"));
        assertEquals("34L", updated.get("runwayInUse"));
        assertEquals(995, updated.get("qnhHpa"));
    }

    @Test
    void windAndQnhTogetherAdvancesBothAirportsOnceForWind() {
        Map<String, Object> rjtt = atisService.updateAtis(
                "RJTT",
                "16R",
                null,
                WindUpdate.set(UniformWind.measured(160, 12)),
                QnhUpdate.set(1013));
        assertEquals("B", rjtt.get("informationIdent"));
        assertEquals(1013, rjtt.get("qnhHpa"));
        assertTrue(((String) rjtt.get("text")).contains("WIND 160 DEGREES 12 KNOTS"));
        assertTrue(((String) rjtt.get("text")).contains("QNH 1013"));
        assertTrue(!((String) rjtt.get("text")).contains("WEATHER NOT AVAILABLE"));

        Map<String, Object> rjaa = atisService.getAllAtis().stream()
                .filter(m -> "RJAA".equals(m.get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertEquals("B", rjaa.get("informationIdent"));
        assertEquals(false, rjaa.get("qnhAvailable"));
        assertTrue(((String) rjaa.get("text")).contains("WEATHER NOT AVAILABLE"));
    }

    @Test
    void resetToDefaultsClearsQnh() {
        atisService.updateAtis("RJTT", "16R", null, WindUpdate.absent(), QnhUpdate.set(1013));
        atisService.resetToDefaults();
        Map<String, Object> rjtt = atisService.getAllAtis().stream()
                .filter(m -> "RJTT".equals(m.get("airportIcao")))
                .findFirst()
                .orElseThrow();
        assertEquals(false, rjtt.get("qnhAvailable"));
        assertEquals("A", rjtt.get("informationIdent"));
    }

    @Test
    void invalidQnhThrows() {
        assertThrows(IllegalArgumentException.class, () -> QnhUpdate.set(849));
        assertThrows(IllegalArgumentException.class, () -> QnhUpdate.set(1101));
    }

    @Test
    void qnhDoesNotChangeSessionWind() {
        atisService.updateAtis(
                "RJTT", "16R", null, WindUpdate.set(UniformWind.measured(90, 8)), QnhUpdate.absent());
        atisService.updateAtis("RJTT", "16R", null, WindUpdate.absent(), QnhUpdate.set(1013));
        assertEquals(90, atisService.getSessionWind().directionDeg().orElseThrow());
        assertEquals(8, atisService.getSessionWind().speedKt().orElseThrow());
    }
}
