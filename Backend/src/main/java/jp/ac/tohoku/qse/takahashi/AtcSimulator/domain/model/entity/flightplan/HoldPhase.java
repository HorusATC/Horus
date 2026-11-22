package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

/**
 * Holding phases after the instruction is accepted.
 * ENTRY flies to the fix; ENTRY_* cover teardrop/parallel joining; then the racetrack.
 */
public enum HoldPhase {
    ENTRY,
    ENTRY_TEARDROP_TURN,
    ENTRY_TEARDROP_LEG,
    ENTRY_TEARDROP_INBOUND_TURN,
    ENTRY_PARALLEL_OUTBOUND_TURN,
    ENTRY_PARALLEL_LEG,
    ENTRY_PARALLEL_INBOUND_TURN,
    OUTBOUND_TURN,
    OUTBOUND_LEG,
    INBOUND_TURN,
    INBOUND_LEG
}
