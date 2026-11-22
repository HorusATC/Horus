package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure;

public enum ProcedurePathDescriptor {
    IF,
    TF,
    VA,
    DF,
    HOLD,
    UNKNOWN;

    public static ProcedurePathDescriptor fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return ProcedurePathDescriptor.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
