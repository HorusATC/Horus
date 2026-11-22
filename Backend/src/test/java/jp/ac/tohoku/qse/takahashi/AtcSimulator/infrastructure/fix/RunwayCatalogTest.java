package jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.airport.RunwayDefinition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

class RunwayCatalogTest {

    private static final double RJTT_16R_HEADING = 149.88;
    private static final double RJTT_16R_LAT = 35.55624167;
    private static final double RJTT_16R_LON = 139.77171944;

    @Test
    void catalogContainsTwelveSurveyedEndsWithoutDerivedDuplicates() {
        RunwayCatalog catalog = new RunwayCatalog();
        assertEquals(12, catalog.getAllRunways().size());
        assertTrue(catalog.getAllRunways().stream()
                .allMatch(r -> RunwayDefinition.SOURCE_AIP_AD_2_12.equals(r.source())));
        assertTrue(catalog.findRunway("RJTT", "22").isPresent());
        assertTrue(catalog.findRunway("RJAA", "16L").isPresent());
        assertFalse(catalog.findRunway("RJTT", "05").orElseThrow().usableForLanding());
    }

    @Test
    void surveyed34LIsNotOld16RDerivation() {
        RunwayCatalog catalog = new RunwayCatalog();
        var r34l = catalog.findRunway("RJTT", "34L").orElseThrow();
        // Old catalog: 16R at 35.553/139.781 heading 163 length 3360 → derived 34L elsewhere.
        assertEquals(35.5366, r34l.thresholdLatitude(), 1e-7);
        assertEquals(139.78566944, r34l.thresholdLongitude(), 1e-7);
        assertEquals(329.88, r34l.heading(), 0.01);
        assertEquals(RunwayDefinition.SOURCE_AIP_AD_2_12, r34l.source());
    }

    @Test
    void inboundApproachCourseUsesSurveyTrueBearing() {
        RunwayCatalog catalog = new RunwayCatalog();
        var rwy = catalog.findRunway("RJTT", "16R").orElseThrow();
        var geom = catalog.buildFinalApproachGeometry(rwy).orElseThrow();
        assertEquals(RJTT_16R_HEADING, geom.approachCourseHeading(), 0.01);
        assertEquals(RJTT_16R_LAT, rwy.thresholdLatitude(), 1e-7);
        assertEquals(RJTT_16R_LON, rwy.thresholdLongitude(), 1e-7);
        assertEquals(3000, rwy.lengthM());
        assertEquals(2520, rwy.ldaM());
    }

    @Test
    void departureEndUsesOppositeThresholdNotPavementFromDisplacedLanding() {
        RunwayCatalog catalog = new RunwayCatalog();
        var rwy = catalog.findRunway("RJTT", "16R").orElseThrow();
        var opp = catalog.findRunway("RJTT", "34L").orElseThrow();
        var geom = catalog.buildFinalApproachGeometry(rwy).orElseThrow();
        assertEquals(opp.thresholdLatitude(), geom.departureEndLatitude(), 1e-7);
        assertEquals(opp.thresholdLongitude(), geom.departureEndLongitude(), 1e-7);
        AircraftPosition threshold = new AircraftPosition(
                new Latitude(rwy.thresholdLatitude()),
                new Longitude(rwy.thresholdLongitude()),
                new Altitude(0));
        AircraftPosition departureEnd = new AircraftPosition(
                new Latitude(geom.departureEndLatitude()),
                new Longitude(geom.departureEndLongitude()),
                new Altitude(0));
        double nm = GeodeticUtils.calculateHorizontalDistance(threshold, departureEnd);
        double expectedNm = rwy.ldaM() / 1852.0;
        assertTrue(Math.abs(nm - expectedNm) < 0.05, "LDA-ish length " + nm + " vs " + expectedNm);
    }

    @Test
    void interceptLiesOnOutboundExtensionFromLandingThreshold() {
        RunwayCatalog catalog = new RunwayCatalog();
        var rwy = catalog.findRunway("RJTT", "16R").orElseThrow();
        var geom = catalog.buildFinalApproachGeometry(rwy).orElseThrow();
        AircraftPosition threshold = new AircraftPosition(
                new Latitude(rwy.thresholdLatitude()),
                new Longitude(rwy.thresholdLongitude()),
                new Altitude(0));
        AircraftPosition intercept = new AircraftPosition(
                new Latitude(geom.interceptLatitude()),
                new Longitude(geom.interceptLongitude()),
                new Altitude(0));
        // Geodetic offset (not planar atan2) places the intercept on the outbound radial.
        assertEquals(
                RunwayCatalog.normalizeHeading(RJTT_16R_HEADING + 180),
                RunwayCatalog.normalizeHeading(geom.approachCourseHeading() + 180),
                0.01);
        double interceptNm = GeodeticUtils.calculateHorizontalDistance(threshold, intercept);
        assertEquals(8.0, interceptNm, 0.1);
    }

    @Test
    void reciprocalDerivationFallbackRoundTrip() {
        // Synthetic single-end definition (survey preference path is covered by the 12-end catalog).
        RunwayDefinition original = new RunwayDefinition(
                "TEST", "09", 90.0, 35.0, 139.0, 2000);
        var once = RunwayCatalog.deriveReciprocal(original);
        assertEquals("27", once.designator());
        assertEquals(RunwayDefinition.SOURCE_DERIVED_RECIPROCAL, once.source());
        var twice = RunwayCatalog.deriveReciprocal(once);
        assertEquals("09", twice.designator());
        assertEquals(original.heading(), twice.heading(), 1);
        AircraftPosition a = new AircraftPosition(
                new Latitude(original.thresholdLatitude()),
                new Longitude(original.thresholdLongitude()),
                new Altitude(0));
        AircraftPosition b = new AircraftPosition(
                new Latitude(twice.thresholdLatitude()),
                new Longitude(twice.thresholdLongitude()),
                new Altitude(0));
        assertTrue(
                GeodeticUtils.calculateHorizontalDistance(a, b) < 0.05,
                "round-trip threshold drift");
    }

    @Test
    void apiMapsExposeUsableForLandingAndFractionalHeading() {
        RunwayCatalog catalog = new RunwayCatalog();
        var maps = catalog.toApiMaps();
        assertEquals(12, maps.size());
        var rjtt16r = maps.stream()
                .filter(m -> "RJTT".equals(m.get("airportIcao")) && "16R".equals(m.get("designator")))
                .findFirst()
                .orElseThrow();
        assertEquals(149.88, ((Number) rjtt16r.get("heading")).doubleValue(), 0.01);
        assertEquals(true, rjtt16r.get("usableForLanding"));
        var rjtt05 = maps.stream()
                .filter(m -> "RJTT".equals(m.get("airportIcao")) && "05".equals(m.get("designator")))
                .findFirst()
                .orElseThrow();
        assertEquals(false, rjtt05.get("usableForLanding"));
    }
}
