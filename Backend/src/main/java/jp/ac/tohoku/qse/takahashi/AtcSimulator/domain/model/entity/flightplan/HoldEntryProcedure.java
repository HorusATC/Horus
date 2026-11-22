package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

/**
 * AIM 5-3-8 / FAA AIP ENR 1.5 holding entry procedure selected from the arrival heading
 * at the fix (FIG 5-3-4 sectors). Boundaries are deterministic (no ±5° pilot choice).
 */
public enum HoldEntryProcedure {
    DIRECT,
    TEARDROP,
    PARALLEL
}
