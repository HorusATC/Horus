package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

/**
 * Single definition source for wind-free holding racetrack geometry.
 * Timed-leg distance uses groundSpeedKt × (minutes / 60); turn rate follows AIM 5-3-8 /
 * FAA AIP ENR 1.5: min(3°/s, bank-30° rate). Outbound bearing is inbound + 180°.
 */
public final class HoldingPatternGeometry {

    public static final double MIN_GROUND_SPEED_KT_FOR_TIMED_LEG = 1.0;
    public static final double STANDARD_RATE_DEG_PER_SEC = 3.0;
    public static final double MAX_BANK_DEG = 30.0;
    /** Standard gravity for bank-limited turn rate (m/s²). */
    public static final double GRAVITY_M_PER_S2 = 9.80665;
    public static final double KT_TO_M_PER_S = 1852.0 / 3600.0;
    /** Arc samples per 180° turn (exclusive of endpoints counted once in the polyline). */
    public static final int ARC_SEGMENTS = 8;

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
     * Bearing from the fix to the protected side of the pattern (inbound +90° RIGHT, −90° LEFT).
     */
    public static double offsetBearingDeg(double inboundCourseDeg, HoldTurnDirection turnDirection) {
        double delta = turnDirection == HoldTurnDirection.RIGHT ? 90.0 : -90.0;
        return GeodeticUtils.normalizeAngle(inboundCourseDeg + delta);
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

    /**
     * Hold turn rate ω = min(3°/s, bank-30° rate). Bank rate: ω_rad/s = g tan(30°) / v.
     */
    public static double holdTurnRateDegPerSec(double groundSpeedKt) {
        double speedMps = Math.max(groundSpeedKt, MIN_GROUND_SPEED_KT_FOR_TIMED_LEG) * KT_TO_M_PER_S;
        double bankRadPerSec = GRAVITY_M_PER_S2 * Math.tan(Math.toRadians(MAX_BANK_DEG)) / speedMps;
        double bankDegPerSec = Math.toDegrees(bankRadPerSec);
        return Math.min(STANDARD_RATE_DEG_PER_SEC, bankDegPerSec);
    }

    /**
     * Turn radius r [NM] = groundSpeedKt / (20 × π × ω_deg/s).
     */
    public static double turnRadiusNm(double groundSpeedKt, double turnRateDegPerSec) {
        if (!(turnRateDegPerSec > 0.0)) {
            throw new IllegalArgumentException("turnRateDegPerSec must be > 0");
        }
        double speed = Math.max(groundSpeedKt, MIN_GROUND_SPEED_KT_FOR_TIMED_LEG);
        return speed / (20.0 * Math.PI * turnRateDegPerSec);
    }

    public static double turnRadiusNm(double groundSpeedKt) {
        return turnRadiusNm(groundSpeedKt, holdTurnRateDegPerSec(groundSpeedKt));
    }

    /**
     * End of the outbound turn (point B): fix offset by diameter 2r on the protected side.
     */
    public static FixPosition outboundTurnExit(
            FixPosition fix, double inboundCourseDeg, HoldTurnDirection turnDirection, double turnRadiusNm) {
        return offsetFixPlanar(
                fix, offsetBearingDeg(inboundCourseDeg, turnDirection), 2.0 * turnRadiusNm);
    }

    /**
     * Outbound leg end (point C): parallel leg start + outboundLegNm along outbound bearing.
     */
    public static FixPosition outboundLegEnd(
            FixPosition fix,
            double inboundCourseDeg,
            HoldTurnDirection turnDirection,
            HoldLeg leg,
            double groundSpeedKt,
            double turnRadiusNm) {
        FixPosition turnExit = outboundTurnExit(fix, inboundCourseDeg, turnDirection, turnRadiusNm);
        double legNm = outboundDistanceNm(leg, groundSpeedKt);
        return offsetFixPlanar(turnExit, outboundBearingDeg(inboundCourseDeg), legNm);
    }

    /**
     * Historical axial outbound target (fix + leg along outbound). Kept for callers that still
     * need the pre-racetrack point; racetrack flight uses {@link #outboundLegEnd}.
     */
    public static FixPosition outboundTarget(
            FixPosition fix, double inboundCourseDeg, HoldLeg leg, double groundSpeedKt) {
        double distanceNm = outboundDistanceNm(leg, groundSpeedKt);
        double outboundBearing = outboundBearingDeg(inboundCourseDeg);
        return offsetFixPlanar(fix, outboundBearing, distanceNm);
    }

    /**
     * Closed racetrack polyline: fix → outbound semicircle → outbound straight → inbound semicircle
     * → inbound straight back to fix. Each semicircle has {@link #ARC_SEGMENTS} interior samples.
     */
    public static List<FixPosition> buildPattern(
            FixPosition fix,
            double inboundCourseDeg,
            HoldTurnDirection turnDirection,
            double outboundLegNm,
            double turnRadiusNm) {
        double outbound = outboundBearingDeg(inboundCourseDeg);
        double offset = offsetBearingDeg(inboundCourseDeg, turnDirection);
        FixPosition pointB = outboundTurnExit(fix, inboundCourseDeg, turnDirection, turnRadiusNm);
        FixPosition pointC = offsetFixPlanar(pointB, outbound, outboundLegNm);
        FixPosition pointD = offsetFixPlanar(fix, outbound, outboundLegNm);

        List<FixPosition> points = new ArrayList<>();
        points.add(fix);
        appendSemicircle(points, fix, pointB, turnRadiusNm, offset, turnDirection, false);
        points.add(pointB);
        points.add(pointC);
        appendSemicircle(points, pointC, pointD, turnRadiusNm, offset, turnDirection, true);
        points.add(pointD);
        points.add(fix);
        return points;
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

    public static double planarDistanceNm(FixPosition a, FixPosition b) {
        double lat1 = a.latitude.toDouble();
        double lon1 = a.longitude.toDouble();
        double lat2 = b.latitude.toDouble();
        double lon2 = b.longitude.toDouble();
        double dLatNm = (lat2 - lat1) * 60.0;
        double meanLatRad = Math.toRadians((lat1 + lat2) * 0.5);
        double dLonNm = (lon2 - lon1) * 60.0 * Math.cos(meanLatRad);
        return Math.hypot(dLatNm, dLonNm);
    }

    public static double planarBearingDeg(FixPosition from, FixPosition to) {
        double lat1 = from.latitude.toDouble();
        double lon1 = from.longitude.toDouble();
        double lat2 = to.latitude.toDouble();
        double lon2 = to.longitude.toDouble();
        double dLatNm = (lat2 - lat1) * 60.0;
        double meanLatRad = Math.toRadians((lat1 + lat2) * 0.5);
        double dLonNm = (lon2 - lon1) * 60.0 * Math.cos(meanLatRad);
        return GeodeticUtils.normalizeAngle(Math.toDegrees(Math.atan2(dLonNm, dLatNm)));
    }

    /**
     * Abeam of the fix on the outbound leg: cross-track from the inbound axis toward the
     * protected side is ≥ turn diameter − tolerance, and along-track from the fix along
     * outbound is near zero. Used as the timed-leg clock start (wind-free ≈ rollout complete).
     */
    public static boolean isAbeamOutbound(
            FixPosition aircraft,
            FixPosition fix,
            double inboundCourseDeg,
            HoldTurnDirection turnDirection,
            double turnRadiusNm,
            double distanceToleranceNm) {
        double alongOutbound = alongTrackNm(fix, aircraft, outboundBearingDeg(inboundCourseDeg));
        double cross = crossTrackNm(fix, aircraft, inboundCourseDeg);
        double expectedCross = turnDirection == HoldTurnDirection.RIGHT
                ? 2.0 * turnRadiusNm
                : -2.0 * turnRadiusNm;
        return Math.abs(alongOutbound) <= distanceToleranceNm
                && Math.abs(cross - expectedCross) <= distanceToleranceNm;
    }

    /** Along-track NM from origin along {@code courseDeg} (positive forward). */
    public static double alongTrackNm(FixPosition origin, FixPosition point, double courseDeg) {
        double lat1 = origin.latitude.toDouble();
        double lon1 = origin.longitude.toDouble();
        double lat2 = point.latitude.toDouble();
        double lon2 = point.longitude.toDouble();
        double dLatNm = (lat2 - lat1) * 60.0;
        double meanLatRad = Math.toRadians((lat1 + lat2) * 0.5);
        double dLonNm = (lon2 - lon1) * 60.0 * Math.cos(meanLatRad);
        double courseRad = Math.toRadians(courseDeg);
        return dLatNm * Math.cos(courseRad) + dLonNm * Math.sin(courseRad);
    }

    /**
     * Cross-track NM relative to the inbound course through the fix.
     * Positive is to the right of inbound (inbound + 90°).
     */
    public static double crossTrackNm(FixPosition fix, FixPosition point, double inboundCourseDeg) {
        double lat1 = fix.latitude.toDouble();
        double lon1 = fix.longitude.toDouble();
        double lat2 = point.latitude.toDouble();
        double lon2 = point.longitude.toDouble();
        double dLatNm = (lat2 - lat1) * 60.0;
        double meanLatRad = Math.toRadians((lat1 + lat2) * 0.5);
        double dLonNm = (lon2 - lon1) * 60.0 * Math.cos(meanLatRad);
        double courseRad = Math.toRadians(inboundCourseDeg);
        return -dLatNm * Math.sin(courseRad) + dLonNm * Math.cos(courseRad);
    }

    private static void appendSemicircle(
            List<FixPosition> points,
            FixPosition from,
            FixPosition to,
            double turnRadiusNm,
            double offsetBearingDeg,
            HoldTurnDirection turnDirection,
            boolean inboundTurn) {
        FixPosition center = inboundTurn
                ? offsetFixPlanar(from, GeodeticUtils.normalizeAngle(offsetBearingDeg + 180.0), turnRadiusNm)
                : offsetFixPlanar(from, offsetBearingDeg, turnRadiusNm);
        double startBearing = planarBearingDeg(center, from);
        double sweep = turnDirection == HoldTurnDirection.RIGHT ? 180.0 : -180.0;
        for (int i = 1; i < ARC_SEGMENTS; i++) {
            double frac = (double) i / ARC_SEGMENTS;
            double bearing = GeodeticUtils.normalizeAngle(startBearing + sweep * frac);
            points.add(offsetFixPlanar(center, bearing, turnRadiusNm));
        }
        // `to` is added by the caller so endpoints are not duplicated.
        Objects.requireNonNull(to);
    }
}
