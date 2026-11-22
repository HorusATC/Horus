package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure;

import java.util.List;

public record InstrumentProcedure(
        String id,
        ProcedureType type,
        String name,
        String family,
        String variant,
        String airportIcao,
        boolean rnav,
        List<String> applicableRunways,
        List<ProcedureLeg> legs,
        List<String> enrouteTransitions
) {
    public boolean isAssignablePrimary() {
        if (type == ProcedureType.SID_TRANSITION) {
            return false;
        }
        if (type == ProcedureType.SID && !rnav) {
            return false;
        }
        return !legs.isEmpty();
    }
}
