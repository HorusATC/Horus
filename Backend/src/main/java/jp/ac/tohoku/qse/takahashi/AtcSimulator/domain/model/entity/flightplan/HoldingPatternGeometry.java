package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

import java.util.Objects;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

/**
 * Single definition source for holding racetrack outbound geometry.
 * Timed-leg distance uses groundSpeedKt × (minutes / 60); outbound bearing is inbound + 180°.
 */
public final class HoldingPatternGeometry {

    public static final double MIN_GROUND_SPEED_KT_FOR_TIMED_LEG = 1.0;

    private HoldingPatternGeometry() {
        throw new AssertionError("utility");
    }

    /**
     * Instructed inbound course when present; otherwise the historical fix-name hash (0–359).
     */
    public static double resolveInboundCourseDeg(Double instructedCourseDeg, String fixName, FixPosition fix) {
        if (instructedCourseDeg != null) {
            return instructedCourseDeg;
        }
        return deterministicInboundBearing(fix, fixName);
    }

    public static double outboundBearingDeg(double inboundCourseDeg) {
        return GeodeticUtils.normalizeAngle(inboundCourseDeg + 180.0);
    }

    /**
     * Outbound leg distance in NM.
     *
     * @throws IllegalArgumentException when a timed leg is used with ground speed below 1 kt
     */
    public static double outboundDistanceNm(HoldLeg leg, double groundSpeedKt) {
        return switch (leg) {
            case HoldLeg.DistanceNm(var nm) -> nm;
            case HoldLeg.TimedMinutes(var minutes) -> {
                if (groundSpeedKt < MIN_GROUND_SPEED_KT_FOR_TIMED_LEG) {
                    throw new IllegalArgumentException(
                            "legMinutes requires ground speed ≥ "
                                    + MIN_GROUND_SPEED_KT_FOR_TIMED_LEG
                                    + " kt");
                }
                yield groundSpeedKt * (minutes / 60.0);
            }
        };
    }

    public static FixPosition outboundTarget(
            FixPosition fix, double inboundCourseDeg, HoldLeg leg, double groundSpeedKt) {
        double distanceNm = outboundDistanceNm(leg, groundSpeedKt);
        double outboundBearing = outboundBearingDeg(inboundCourseDeg);
        return offsetFixPlanar(fix, outboundBearing, distanceNm);
    }

    /**
     * Same planar NM offset historically used by the hold implementation
     * (1° lat ≈ 60 NM; lon scaled by cos(lat)).
     */
    public static FixPosition offsetFixPlanar(FixPosition origin, double bearingDeg, double distanceNm) {
        double bearingRad = Math.toRadians(bearingDeg);
        double latDeg = origin.latitude.toDouble();
        double lonDeg = origin.longitude.toDouble();
        double deltaLatDeg = (distanceNm * Math.cos(bearingRad)) / 60.0;
        double cosLat = Math.cos(Math.toRadians(latDeg));
        double deltaLonDeg = cosLat == 0.0 ? 0.0 : (distanceNm * Math.sin(bearingRad)) / (60.0 * cosLat);
        return new FixPosition(
                new Latitude(latDeg + deltaLatDeg),
                new Longitude(lonDeg + deltaLonDeg));
    }

    public static double deterministicInboundBearing(FixPosition fix, String fixName) {
        if (fixName != null && !fixName.isBlank()) {
            return Math.floorMod(fixName.trim().toUpperCase().hashCode(), 360);
        }
        int latMilli = (int) Math.round(fix.latitude.toDouble() * 1000.0);
        int lonMilli = (int) Math.round(fix.longitude.toDouble() * 1000.0);
        return Math.floorMod(Objects.hash(latMilli, lonMilli), 360);
    }
}
