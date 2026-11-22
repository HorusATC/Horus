package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;

/**
 * Inverted acceptance of {@code aip/altitude-constraint-sweep.json}:
 * baseline on main documented 160 strict misses; this test requires reachable
 * constraints to pass within acceptance tolerances, with unreachable fixes flagged.
 */
class AltitudeConstraintSweepAcceptanceTest {

    @Test
    @DisplayName("SID/STAR sweep: reachable constraints within tolerance; unreachable flagged")
    void sweepAcceptsReachableConstraintsAndFlagsUnreachable() {
        AltitudeConstraintSweepHarness harness = new AltitudeConstraintSweepHarness();
        List<AltitudeConstraintSweepHarness.CaseSpec> cases = harness.enumerateCases();
        assertEquals(195, cases.size(), "baseline enumerated 195 procedure/transition cases");

        List<AltitudeConstraintSweepHarness.ConstraintMiss> misses = new ArrayList<>();
        Map<AltitudeConstraint, Integer> missesByType = new EnumMap<>(AltitudeConstraint.class);
        TreeSet<String> flaggedUnreachable = new TreeSet<>();
        int completed = 0;

        for (AltitudeConstraintSweepHarness.CaseSpec spec : cases) {
            AltitudeConstraintSweepHarness.FlightResult result = harness.fly(spec);
            if (result.completed()) {
                completed++;
            }
            flaggedUnreachable.addAll(result.unreachableConstraintFixes().stream()
                    .map(fix -> result.procedureId()
                            + (result.transitionId() != null ? "+" + result.transitionId() : "")
                            + "@"
                            + fix)
                    .toList());
            for (AltitudeConstraintSweepHarness.ConstraintMiss miss : result.acceptanceMisses()) {
                misses.add(miss);
                missesByType.merge(miss.constraint(), 1, Integer::sum);
            }
        }

        if (!misses.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Acceptance misses=").append(misses.size()).append(" byType=").append(missesByType);
            sb.append(" flagged=").append(flaggedUnreachable.size()).append('\n');
            int limit = Math.min(25, misses.size());
            for (int i = 0; i < limit; i++) {
                var m = misses.get(i);
                sb.append("  ")
                        .append(m.procedureId())
                        .append(m.transitionId() != null ? "+" + m.transitionId() : "")
                        .append(' ')
                        .append(m.fix())
                        .append(' ')
                        .append(m.constraint())
                        .append(" pub=")
                        .append(m.publishedFt())
                        .append(" act=")
                        .append(String.format("%.1f", m.actualFt()))
                        .append(" Δ=")
                        .append(String.format("%.1f", m.deltaFt()))
                        .append('\n');
            }
            fail(sb.toString());
        }

        assertTrue(completed >= 193, "completed flights=" + completed);
        // POLIX-class IF AT constraints are physically unreachable from the sweep start.
        assertTrue(
                flaggedUnreachable.stream().anyMatch(s -> s.contains("@POLIX")),
                "expected POLIX unreachable flags, got " + flaggedUnreachable);
    }
}
