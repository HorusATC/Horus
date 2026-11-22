package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure;

/**
 * Published VA climb: hold true course until the chart AT_OR_ABOVE altitude.
 * Release only when altitude meets that published value (float slack ≤ 1 ft).
 */
public record ProcedureVaConstraint(double courseTrueDeg, int atOrAboveAltitudeFt) {

    /**
     * Maximum early-release slack (ft) allowed by review. Comparisons use the
     * published altitude directly so VA does not clear a full foot early.
     */
    public static final double ALTITUDE_EPS_FT = 1.0;

    public ProcedureVaConstraint {
        if (atOrAboveAltitudeFt <= 0) {
            throw new IllegalArgumentException("VA at-or-above altitude must be positive");
        }
    }

    public boolean isSatisfiedByAltitudeFt(double altitudeFt) {
        // altitude >= published (float noise only; never a multi-foot early window)
        return altitudeFt + Math.min(ALTITUDE_EPS_FT, 1e-6) >= atOrAboveAltitudeFt;
    }

    public static ProcedureVaConstraint fromVaLeg(ProcedureLeg leg) {
        if (leg.pathDescriptor() != ProcedurePathDescriptor.VA) {
            throw new IllegalArgumentException("Not a VA leg");
        }
        if (leg.courseTrueDeg() == null) {
            throw new IllegalArgumentException("VA leg missing courseTrueDeg");
        }
        if (leg.altitude() == null) {
            throw new IllegalArgumentException("VA leg missing altitude");
        }
        Integer feet = leg.altitude().resolveFeetForFlightPlan();
        if (feet == null) {
            throw new IllegalArgumentException("VA leg altitude has no feet/FL");
        }
        return new ProcedureVaConstraint(leg.courseTrueDeg(), feet);
    }
}
