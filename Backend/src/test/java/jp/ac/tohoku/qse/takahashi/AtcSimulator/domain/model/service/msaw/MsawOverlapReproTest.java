package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.airport.RunwayDefinition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.MsaCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.RunwayCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

/**
 * Inverted MSAW overlap repro (PR-D): each parallel centerline selects itself
 * under {@link ClosestCenterlineRunway#selectClosestCenterlineRunway}, matching
 * the Frontend golden table.
 */
class MsawOverlapReproTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RunwayCatalog runwayCatalog;
    private MsaCatalog msaCatalog;
    private List<RunwayDefinition> parallelEnds;

    @BeforeEach
    void setUp() {
        runwayCatalog = new RunwayCatalog();
        msaCatalog = new MsaCatalog();
        parallelEnds = new ArrayList<>();
        for (String designator : List.of("16L", "16R", "34L", "34R")) {
            parallelEnds.add(runwayCatalog.findRunway("RJTT", designator).orElseThrow());
        }
    }

    @Test
    @DisplayName("inverted: each centerline at 5 NM selects itself (not neighbor)")
    void eachCenterlineAtFiveNmSelectsSelf() {
        for (RunwayDefinition rwy : parallelEnds) {
            ClosestCenterlineRunway.Selection selected = selectOnCenterline(rwy, 5.0);
            assertThat(selected).isNotNull();
            assertThat(selected.designator()).isEqualTo(rwy.designator());
            assertThat(Math.abs(selected.crossTrackNm())).isLessThan(0.05);
            assertThat(selected.alongTrackNm()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.05));
        }
    }

    @Test
    @DisplayName("shared golden table: 16L/16R/34L/34R × 1/5/9 NM select self")
    void sharedGoldenTableSelectsOwnCenterline() throws Exception {
        JsonNode root;
        try (InputStream in = getClass()
                .getClassLoader()
                .getResourceAsStream("fixtures/closest-centerline-golden.json")) {
            assertThat(in).as("golden fixture").isNotNull();
            root = MAPPER.readTree(in);
        }
        JsonNode cases = root.get("cases");
        assertThat(cases).isNotNull();
        assertThat(cases.size()).isEqualTo(12);

        for (Iterator<JsonNode> it = cases.elements(); it.hasNext(); ) {
            JsonNode c = it.next();
            String designator = c.get("centerlineDesignator").asText();
            double along = c.get("alongTrackNm").asDouble();
            String expected = c.get("expectedDesignator").asText();
            RunwayDefinition rwy = runwayCatalog.findRunway("RJTT", designator).orElseThrow();
            ClosestCenterlineRunway.Selection selected = selectOnCenterline(rwy, along);
            assertThat(selected).as("%s @ %s NM", designator, along).isNotNull();
            assertThat(selected.designator()).isEqualTo(expected);
            assertThat(Math.abs(selected.crossTrackNm())).isLessThan(0.05);
        }
    }

    private ClosestCenterlineRunway.Selection selectOnCenterline(
            RunwayDefinition rwy, double alongTrackNm) {
        double outbound = (rwy.heading() + 180.0) % 360.0;
        double[] point = GeodeticUtils.offsetPointNm(
                rwy.thresholdLatitude(), rwy.thresholdLongitude(), outbound, alongTrackNm);
        AircraftPosition position = new AircraftPosition(
                new Latitude(point[0]),
                new Longitude(point[1]),
                new Altitude(3000));
        return ClosestCenterlineRunway.selectClosestCenterlineRunway(
                position,
                parallelEnds,
                msaCatalog.getApproachCorridorWidthNm(),
                msaCatalog.getApproachCorridorExtensionNm());
    }
}
