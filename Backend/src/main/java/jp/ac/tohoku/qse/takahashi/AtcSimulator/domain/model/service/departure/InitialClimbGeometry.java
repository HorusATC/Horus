package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.departure;

/**
 * Pure helpers for catalog-runway initial climb (no taxi / SID).
 * Screen height and DER+400 ft turn gate follow FAA JO 7110.65 5-8-3 NOTE 1 framing.
 */
public final class InitialClimbGeometry {

    public static final double SCREEN_HEIGHT_ABOVE_THRESHOLD_FT = 35.0;
    public static final double TURN_RELEASE_ABOVE_DER_FT = 400.0;
    public static final double MIN_CLIMB_ABOVE_START_FT = 500.0;
    public static final double DEFAULT_DEPARTURE_GROUND_SPEED_KT = 180.0;
    public static final double MSAW_DEPARTURE_CORRIDOR_LENGTH_NM = 5.0;
    public static final double MSAW_DEPARTURE_CORRIDOR_HALF_WIDTH_NM = 1.0;
    /** Headings within this band are treated as runway heading (no deferred turn). */
    public static final double HEADING_EQUALITY_EPSILON_DEG = 0.5;

    private InitialClimbGeometry() {}

    public static double startAltitudeFt(double thresholdElevationFt) {
        return thresholdElevationFt + SCREEN_HEIGHT_ABOVE_THRESHOLD_FT;
    }

    public static double turnReleaseAltitudeFt(double derElevationFt) {
        return derElevationFt + TURN_RELEASE_ABOVE_DER_FT;
    }

    public static boolean climbAltitudeValid(double climbAltitudeFt, double startAltitudeFt) {
        return climbAltitudeFt >= startAltitudeFt + MIN_CLIMB_ABOVE_START_FT;
    }

    public static boolean headingsEqual(double leftDeg, double rightDeg) {
        return smallestHeadingDelta(leftDeg, rightDeg) <= HEADING_EQUALITY_EPSILON_DEG;
    }

    public static double smallestHeadingDelta(double leftDeg, double rightDeg) {
        double delta = Math.abs(normalizeHeading(leftDeg) - normalizeHeading(rightDeg));
        return Math.min(delta, 360.0 - delta);
    }

    public static double normalizeHeading(double headingDeg) {
        double normalized = headingDeg % 360.0;
        if (normalized < 0.0) {
            normalized += 360.0;
        }
        return normalized;
    }

    /**
     * True when the aircraft is along the runway heading from the physical start,
     * within {@link #MSAW_DEPARTURE_CORRIDOR_LENGTH_NM} and half-width
     * {@link #MSAW_DEPARTURE_CORRIDOR_HALF_WIDTH_NM}.
     */
    public static boolean isWithinDepartureCorridor(
            double startLatitude,
            double startLongitude,
            double runwayHeadingDeg,
            double aircraftLatitude,
            double aircraftLongitude,
            double distanceFromStartNm,
            double bearingFromStartDeg) {
        if (distanceFromStartNm < 0.0 || distanceFromStartNm > MSAW_DEPARTURE_CORRIDOR_LENGTH_NM) {
            return false;
        }
        double bearingDiff = smallestHeadingDelta(bearingFromStartDeg, runwayHeadingDeg);
        if (bearingDiff > 90.0) {
            return false;
        }
        double projectedNm = distanceFromStartNm * Math.cos(Math.toRadians(bearingDiff));
        if (projectedNm < 0.0 || projectedNm > MSAW_DEPARTURE_CORRIDOR_LENGTH_NM) {
            return false;
        }
        double lateralNm = distanceFromStartNm * Math.sin(Math.toRadians(bearingDiff));
        return Math.abs(lateralNm) <= MSAW_DEPARTURE_CORRIDOR_HALF_WIDTH_NM;
    }

    /** Geodetic bearing from (lat1, lon1) to (lat2, lon2), degrees true. */
    public static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLonRad = Math.toRadians(lon2 - lon1);
        double y = Math.sin(deltaLonRad) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad)
                - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLonRad);
        return normalizeHeading(Math.toDegrees(Math.atan2(y, x)));
    }
}
