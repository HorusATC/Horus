package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;

/**
 * Published altitude constraint from the AIP extract.
 * Flight-level values are mapped to feet only when building a simulator flight plan
 * (FL150 → 15000 ft AT); that mapping is not an AIP conversion table.
 */
public record ProcedureAltitude(
        AltitudeConstraint constraint,
        Integer feet,
        Integer flightLevel
) {
    public Integer resolveFeetForFlightPlan() {
        if (feet != null) {
            return feet;
        }
        if (flightLevel != null) {
            return flightLevel * 100;
        }
        return null;
    }
}
