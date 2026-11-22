package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.ProcedureFlightPlanMapper;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.fix.FixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.AtsRouteFixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.ProcedureCatalog;

class ProcedureCatalogGoldenTest {

    private ProcedureCatalog catalog;
    private ProcedureFlightPlanMapper mapper;

    @BeforeEach
    void setUp() {
        catalog = new ProcedureCatalog();
        FixPositionRepository fixes = new AtsRouteFixPositionRepository();
        mapper = new ProcedureFlightPlanMapper(fixes);
    }

    @Test
    void catalogContainsStar15VamosRunwaysAndTransitions() {
        assertThat(catalog.getSourceNotice())
                .contains("Derived from AIP Japan (AIRAC 1 Nov 2026)");
        long stars = catalog.all().stream().filter(p -> p.type() == ProcedureType.STAR).count();
        long vamosRunways = catalog.all().stream()
                .filter(p -> p.type() == ProcedureType.SID && p.id().startsWith("VAMOS4.RWY"))
                .count();
        long transitions = catalog.all().stream()
                .filter(p -> p.type() == ProcedureType.SID_TRANSITION)
                .count();
        // Slice 1 STAR 15 + slice 3 STAR 16; VAMOS runway SIDs unchanged; transitions 2 + 7.
        assertThat(stars).isEqualTo(31);
        assertThat(vamosRunways).isEqualTo(6);
        assertThat(transitions).isEqualTo(9);
        assertThat(catalog.findById("VISIP1")).isPresent();
        assertThat(catalog.findById("VISIP1").orElseThrow().isAssignablePrimary()).isFalse();
        assertThat(catalog.listByAirport("RJTT")).hasSize(90);
        assertThat(catalog.listByAirport("RJAA")).hasSize(48);
    }

    @Test
    void oshima1aGoldenWaypointOrderAndConstraintsWithoutKiasSpeed() {
        InstrumentProcedure oshima1a = catalog.findById("OSHIMA1A").orElseThrow();
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped = mapper.map(
                new Callsign("JAL001"),
                oshima1a,
                Optional.empty(),
                13000,
                250);

        assertThat(mapped.waypointNames())
                .containsExactly(
                        "XAC", "ANZAC", "TT450", "TT451", "TT452", "TT453", "WANDA", "WEDGE", "ARLON");
        assertThat(mapped.vaConstraint()).isNull();

        List<FlightPlanWaypoint> wps = mapped.flightPlan().getWaypoints();
        FlightPlanWaypoint anzac = wps.get(1);
        assertThat(anzac.getFixName()).isEqualTo("ANZAC");
        assertThat(anzac.getAltitudeConstraint()).isEqualTo(AltitudeConstraint.AT);
        assertThat(anzac.getTargetAltitude().toDouble()).isEqualTo(13000.0);
        assertThat(anzac.getTargetSpeed()).isNull();

        FlightPlanWaypoint wanda = wps.get(6);
        assertThat(wanda.getFixName()).isEqualTo("WANDA");
        assertThat(wanda.getAltitudeConstraint()).isEqualTo(AltitudeConstraint.AT);
        assertThat(wanda.getTargetAltitude().toDouble()).isEqualTo(13000.0);
        assertThat(wanda.getTargetSpeed()).isNull();

        FlightPlanWaypoint wedge = wps.get(7);
        assertThat(wedge.getFixName()).isEqualTo("WEDGE");
        assertThat(wedge.getAltitudeConstraint()).isEqualTo(AltitudeConstraint.AT);
        assertThat(wedge.getTargetAltitude().toDouble()).isEqualTo(8000.0);

        assertThat(wps).allSatisfy(wp -> assertThat(wp.getTargetSpeed()).isNull());
    }

    @Test
    void polixFl150MapsTo15000FtAtAsSimulatorFieldMapping() {
        InstrumentProcedure polix = catalog.findById("POLIX2A").orElseThrow();
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped = mapper.map(
                new Callsign("ANA002"),
                polix,
                Optional.empty(),
                15000,
                250);
        FlightPlanWaypoint polixWp = mapped.flightPlan().getWaypoints().get(0);
        assertThat(polixWp.getFixName()).isEqualTo("POLIX");
        // Simulator field mapping: FL150 → 15000 ft AT (not an AIP conversion table).
        assertThat(polixWp.getAltitudeConstraint()).isEqualTo(AltitudeConstraint.AT);
        assertThat(polixWp.getTargetAltitude().toDouble()).isEqualTo(15000.0);
    }

    @Test
    void vamos34l34rMapsVaThenDfTfFixes() {
        InstrumentProcedure vamos = catalog.findById("VAMOS4.RWY34L_34R").orElseThrow();
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped = mapper.map(
                new Callsign("JAL900"),
                vamos,
                Optional.empty(),
                9000,
                250);

        assertThat(mapped.vaConstraint()).isNotNull();
        assertThat(mapped.vaConstraint().courseTrueDeg()).isEqualTo(330.0);
        assertThat(mapped.vaConstraint().atOrAboveAltitudeFt()).isEqualTo(700);
        assertThat(mapped.waypointNames()).containsExactly("TT502", "LOCUP", "VAMOS");
        assertThat(vamos.enrouteTransitions())
                .containsExactly("VAMOS4.TATEYAMA", "VAMOS4.DRAKY");

        FlightPlanWaypoint locup = mapped.flightPlan().getWaypoints().get(1);
        assertThat(locup.getAltitudeConstraint()).isEqualTo(AltitudeConstraint.AT_OR_ABOVE);
        assertThat(locup.getTargetAltitude().toDouble()).isEqualTo(5000.0);

        FlightPlanWaypoint vamosFix = mapped.flightPlan().getWaypoints().get(2);
        assertThat(vamosFix.getAltitudeConstraint()).isEqualTo(AltitudeConstraint.AT_OR_ABOVE);
        assertThat(vamosFix.getTargetAltitude().toDouble()).isEqualTo(9000.0);

        Optional<FixPosition> tt502 = new AtsRouteFixPositionRepository().findFixPositionByName("TT502");
        assertThat(tt502).isPresent();
    }

    @Test
    void vamos34WithTateyamaMapsThroughUtibo() {
        InstrumentProcedure vamos = catalog.findById("VAMOS4.RWY34L_34R").orElseThrow();
        InstrumentProcedure tateyama = catalog.findById("VAMOS4.TATEYAMA").orElseThrow();
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped = mapper.map(
                new Callsign("JAL901"),
                vamos,
                Optional.of(tateyama),
                9000,
                250);
        assertThat(mapped.waypointNames()).containsExactly("TT502", "LOCUP", "VAMOS", "UTIBO");
    }

    @Test
    void vamos34WithDrakyMapsThroughDrakyXac() {
        InstrumentProcedure vamos = catalog.findById("VAMOS4.RWY34L_34R").orElseThrow();
        InstrumentProcedure draky = catalog.findById("VAMOS4.DRAKY").orElseThrow();
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped = mapper.map(
                new Callsign("JAL902"),
                vamos,
                Optional.of(draky),
                9000,
                250);
        assertThat(mapped.waypointNames()).containsExactly("TT502", "LOCUP", "VAMOS", "DRAKY", "XAC");
    }

    @Test
    void missingProcedureFixesAreInWaypointCatalog() {
        AtsRouteFixPositionRepository fixes = new AtsRouteFixPositionRepository();
        for (String name : List.of(
                "XAC",
                "T6R11",
                "T6L21",
                "TT450",
                "TT468",
                "TT501",
                "TT502",
                "A6R11",
                "AA657",
                "GUGVA",
                "TT503")) {
            assertThat(fixes.findFixPositionByName(name))
                    .as(name)
                    .isPresent();
        }
        // Existing fix coordinates must not be rewritten by the AIP append.
        FixPosition anzac = fixes.findFixPositionByName("ANZAC").orElseThrow();
        assertThat(anzac.latitude.toDouble()).isCloseTo(34.841339, within(1e-9));
        assertThat(anzac.longitude.toDouble()).isCloseTo(139.696308, within(1e-9));
    }

    @Test
    void laxas16rMapsVaThenDfTfFixes() {
        InstrumentProcedure laxas = catalog.findById("LAXAS4.RWY16R").orElseThrow();
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped = mapper.map(
                new Callsign("JAL910"),
                laxas,
                Optional.empty(),
                17000,
                250);
        assertThat(mapped.vaConstraint()).isNotNull();
        assertThat(mapped.vaConstraint().courseTrueDeg()).isEqualTo(150.0);
        assertThat(mapped.vaConstraint().atOrAboveAltitudeFt()).isEqualTo(500);
        assertThat(mapped.waypointNames()).containsExactly("T6R11", "TAURA", "IMOLA", "LAXAS");
    }

    @Test
    void tetra16rWithAgrisMapsThroughAgris() {
        InstrumentProcedure tetra = catalog.findById("TETRA8.RWY16R").orElseThrow();
        InstrumentProcedure agris = catalog.findById("TETRA8.AGRIS").orElseThrow();
        assertThat(tetra.enrouteTransitions()).contains("TETRA8.AGRIS");
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped = mapper.map(
                new Callsign("ANA910"),
                tetra,
                Optional.of(agris),
                20000,
                250);
        assertThat(mapped.vaConstraint()).isNotNull();
        assertThat(mapped.vaConstraint().courseTrueDeg()).isEqualTo(149.6);
        assertThat(mapped.waypointNames())
                .containsExactly("ASPEN", "A6R11", "A6R13", "A6R14", "TETRA", "RADIX", "WHARF", "AGRIS");
    }
}
