package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

/**
 * Outbound leg length for a holding instruction.
 * Either a fixed distance in NM or a timed leg converted via ground speed.
 */
public sealed interface HoldLeg permits HoldLeg.DistanceNm, HoldLeg.TimedMinutes {

    double MAX_LEG_NM = 100.0;
    double MAX_LEG_MINUTES = 30.0;
    double DEFAULT_OUTBOUND_LEG_NM = 4.0;

    /**
     * Resolves request fields into a leg. Omitting both yields the historical 4.0 NM default.
     *
     * @throws IllegalArgumentException when both are set, either is non-positive, or exceeds bounds
     */
    static HoldLeg fromRequest(Double legMinutes, Double legNm) {
        boolean hasMinutes = legMinutes != null;
        boolean hasNm = legNm != null;
        if (hasMinutes && hasNm) {
            throw new IllegalArgumentException("Specify only one of legMinutes or legNm");
        }
        if (hasMinutes) {
            return new TimedMinutes(legMinutes);
        }
        if (hasNm) {
            return new DistanceNm(legNm);
        }
        return new DistanceNm(DEFAULT_OUTBOUND_LEG_NM);
    }

    record DistanceNm(double nauticalMiles) implements HoldLeg {
        public DistanceNm {
            if (!(nauticalMiles > 0.0) || nauticalMiles > MAX_LEG_NM) {
                throw new IllegalArgumentException(
                        "legNm must be > 0 and ≤ " + MAX_LEG_NM);
            }
        }
    }

    record TimedMinutes(double minutes) implements HoldLeg {
        public TimedMinutes {
            if (!(minutes > 0.0) || minutes > MAX_LEG_MINUTES) {
                throw new IllegalArgumentException(
                        "legMinutes must be > 0 and ≤ " + MAX_LEG_MINUTES);
            }
        }
    }
}
