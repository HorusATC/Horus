package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.airport;

/**
 * Catalog runway end. {@code threshold*} is the landing threshold (displaced when applicable).
 * {@code heading} is true bearing (AD 2.12 TRUE BRG), stored as double.
 * {@code physicalThreshold*} is the pavement start (TORA begin) when the landing threshold is displaced.
 */
public record RunwayDefinition(
        String airportIcao,
        String designator,
        double heading,
        double thresholdLatitude,
        double thresholdLongitude,
        int lengthM,
        Integer widthM,
        Double thresholdElevationFt,
        Integer ldaM,
        Integer toraM,
        boolean displacedLandingThreshold,
        Double physicalThresholdLatitude,
        Double physicalThresholdLongitude,
        Double physicalThresholdElevationFt,
        String source,
        boolean usableForLanding) {

    public static final String SOURCE_AIP_AD_2_12 = "AIP_AD_2_12";
    public static final String SOURCE_DERIVED_RECIPROCAL = "DERIVED_RECIPROCAL";

    /** Minimal constructor used by reciprocal derivation fallback. */
    public RunwayDefinition(
            String airportIcao,
            String designator,
            double heading,
            double thresholdLatitude,
            double thresholdLongitude,
            int lengthM) {
        this(
                airportIcao,
                designator,
                heading,
                thresholdLatitude,
                thresholdLongitude,
                lengthM,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                SOURCE_DERIVED_RECIPROCAL,
                true);
    }

    /** Physical pavement start when surveyed; otherwise the landing threshold. */
    public double departureStartLatitude() {
        return physicalThresholdLatitude() != null ? physicalThresholdLatitude() : thresholdLatitude();
    }

    public double departureStartLongitude() {
        return physicalThresholdLongitude() != null ? physicalThresholdLongitude() : thresholdLongitude();
    }

    public double departureStartElevationFt() {
        if (physicalThresholdLatitude() != null && physicalThresholdLongitude() != null
                && physicalThresholdElevationFt() != null) {
            return physicalThresholdElevationFt();
        }
        return thresholdElevationFt() != null ? thresholdElevationFt() : 0.0;
    }
}
