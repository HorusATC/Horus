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
        List<String> enrouteTransitions,
        /** For {@link ProcedureType#SID_TRANSITION}: fix where the transition joins the SID. */
        String connectsAt
) {
    public InstrumentProcedure {
        enrouteTransitions = enrouteTransitions == null ? List.of() : List.copyOf(enrouteTransitions);
        applicableRunways = applicableRunways == null ? List.of() : List.copyOf(applicableRunways);
        legs = legs == null ? List.of() : List.copyOf(legs);
    }

    public boolean isAssignablePrimary() {
        if (type == ProcedureType.SID_TRANSITION) {
            return false;
        }
        if (type == ProcedureType.SID && !rnav) {
            return false;
        }
        return !legs.isEmpty();
    }

    public InstrumentProcedure withEnrouteTransitions(List<String> transitions) {
        return new InstrumentProcedure(
                id,
                type,
                name,
                family,
                variant,
                airportIcao,
                rnav,
                applicableRunways,
                legs,
                transitions,
                connectsAt);
    }

    /** Last named fix on the procedure path (SID end fix for transition matching). */
    public String endFixIdentifier() {
        String last = null;
        for (ProcedureLeg leg : legs) {
            if (leg.waypointIdentifier() != null && !leg.waypointIdentifier().isBlank()) {
                last = leg.waypointIdentifier().trim();
            }
        }
        return last;
    }
}
