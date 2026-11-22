package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure;

public record ProcedureLeg(
        int serial,
        ProcedurePathDescriptor pathDescriptor,
        String waypointIdentifier,
        boolean flyOver,
        Double courseMagneticDeg,
        Double courseTrueDeg,
        Double distanceNm,
        String turnDirection,
        ProcedureAltitude altitude,
        Integer speedKias
) {
}
