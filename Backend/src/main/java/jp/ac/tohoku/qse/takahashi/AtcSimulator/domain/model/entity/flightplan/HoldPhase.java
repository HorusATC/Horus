package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

/**
 * Racetrack holding phases after the instruction is accepted.
 * ENTRY flies to the fix; subsequent phases follow the wind-free standard-rate pattern.
 */
public enum HoldPhase {
    ENTRY,
    OUTBOUND_TURN,
    OUTBOUND_LEG,
    INBOUND_TURN,
    INBOUND_LEG
}
