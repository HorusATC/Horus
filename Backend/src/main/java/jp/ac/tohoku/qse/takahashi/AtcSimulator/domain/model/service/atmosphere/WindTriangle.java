package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atmosphere;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.MathUtils;

/**
 * Wind triangle (PHAK): GS vector = TAS vector (along heading) + wind vector (blows toward from+180).
 * First-order WCA: sin(WCA) = (Vw / TAS) · sin(windFrom − course).
 */
public final class WindTriangle {

    public record GroundVelocity(double groundSpeedKt, double trackDeg) {}

    private WindTriangle() {}

    public static GroundVelocity groundVelocity(double headingDeg, double trueAirspeedKt, UniformWind wind) {
        double tas = Math.max(0.0, trueAirspeedKt);
        if (wind == null || !wind.affectsMotion() || tas == 0.0 && wind.speedKnotsOrZero() == 0) {
            return new GroundVelocity(tas, MathUtils.normalizeAngle(headingDeg));
        }
        double headingRad = Math.toRadians(headingDeg);
        double tasEast = tas * Math.sin(headingRad);
        double tasNorth = tas * Math.cos(headingRad);

        double fromRad = Math.toRadians(wind.directionDegOrZero());
        double windSpeed = wind.speedKnotsOrZero();
        // Wind FROM θ blows TO θ+180 → components are −sin/cos of from-bearing.
        double windEast = -windSpeed * Math.sin(fromRad);
        double windNorth = -windSpeed * Math.cos(fromRad);

        double gsEast = tasEast + windEast;
        double gsNorth = tasNorth + windNorth;
        double gs = Math.hypot(gsEast, gsNorth);
        if (gs < 1e-9) {
            return new GroundVelocity(0.0, MathUtils.normalizeAngle(headingDeg));
        }
        double trackDeg = MathUtils.normalizeAngle(Math.toDegrees(Math.atan2(gsEast, gsNorth)));
        return new GroundVelocity(gs, trackDeg);
    }

    /**
     * Signed wind correction angle in degrees so that flying {@code courseDeg + WCA} yields track ≈ course.
     * Clamped when |Vw/TAS · sinδ| &gt; 1 (cannot hold course).
     */
    public static double windCorrectionAngleDeg(double courseDeg, double trueAirspeedKt, UniformWind wind) {
        if (wind == null || !wind.affectsMotion()) {
            return 0.0;
        }
        double tas = Math.max(trueAirspeedKt, 1e-6);
        double windSpeed = wind.speedKnotsOrZero();
        double deltaRad = Math.toRadians(wind.directionDegOrZero() - courseDeg);
        double arg = (windSpeed / tas) * Math.sin(deltaRad);
        arg = Math.max(-1.0, Math.min(1.0, arg));
        return Math.toDegrees(Math.asin(arg));
    }

    /** Heading that maintains the given course as track (course + WCA). */
    public static double headingToMaintainCourseDeg(
            double courseDeg, double trueAirspeedKt, UniformWind wind) {
        return MathUtils.normalizeAngle(courseDeg + windCorrectionAngleDeg(courseDeg, trueAirspeedKt, wind));
    }

    /**
     * TAS along {@code headingDeg} such that |TAS⃗ + wind| equals {@code targetGroundSpeedKt}.
     * When wind is absent, returns the target GS. Clamps to a non-negative result that does not diverge
     * under excessive headwind (best achievable GS may be below the target).
     */
    public static double trueAirspeedForGroundSpeed(
            double headingDeg, double targetGroundSpeedKt, UniformWind wind) {
        double targetGs = Math.max(0.0, targetGroundSpeedKt);
        if (wind == null || !wind.affectsMotion()) {
            return targetGs;
        }
        double headingRad = Math.toRadians(headingDeg);
        double hx = Math.sin(headingRad);
        double hy = Math.cos(headingRad);
        double fromRad = Math.toRadians(wind.directionDegOrZero());
        double wx = -wind.speedKnotsOrZero() * Math.sin(fromRad);
        double wy = -wind.speedKnotsOrZero() * Math.cos(fromRad);

        // |TAS·û + w|² = GS² → TAS² + 2·TAS·(û·w) + |w|² − GS² = 0
        double b = 2.0 * (hx * wx + hy * wy);
        double c = wx * wx + wy * wy - targetGs * targetGs;
        double disc = b * b - 4.0 * c;
        if (disc < 0.0) {
            // Cannot reach target GS on this heading; take TAS that maximises GS along heading
            // (TAS → ∞ grows GS; minimum GS under pure headwind is |Vw − TAS|).
            // Use the TAS that yields the closest achievable GS: project −w onto heading when headwind-dominant.
            double alongWind = hx * wx + hy * wy;
            if (alongWind >= 0.0) {
                return targetGs;
            }
            // Headwind component magnitude; fly at least enough to have non-negative GS along heading.
            return Math.max(0.0, -alongWind);
        }
        double sqrtDisc = Math.sqrt(disc);
        double tasPos = (-b + sqrtDisc) / 2.0;
        double tasNeg = (-b - sqrtDisc) / 2.0;
        if (tasPos >= 0.0) {
            return tasPos;
        }
        if (tasNeg >= 0.0) {
            return tasNeg;
        }
        return 0.0;
    }
}
