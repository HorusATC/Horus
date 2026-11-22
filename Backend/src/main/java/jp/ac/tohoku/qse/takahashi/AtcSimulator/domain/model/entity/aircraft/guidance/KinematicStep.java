package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.guidance;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldingPatternGeometry;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atmosphere.WindTriangle;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Heading;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.VerticalSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.IndicatedAirspeedConverter;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.MathUtils;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.PositionUtils;

/**
 * Shared horizontal/vertical motion used by the live tick and tower-handoff prediction.
 * Order: resolve guidance heading → turn → TAS toward target → wind triangle → move on track.
 */
public final class KinematicStep {

    /** Degrees of intercept per NM of cross-track (capped by {@link #MAX_INTERCEPT_DEG}). */
    public static final double XTK_GAIN_DEG_PER_NM = 40.0;
    public static final double MAX_INTERCEPT_DEG = 30.0;
    public static final double HEADING_CAPTURE_DEG = 2.0;

    private KinematicStep() {}

    public record State(
            double latitude,
            double longitude,
            double altitudeFt,
            double headingDeg,
            double trueAirspeedKt,
            double groundSpeedKt,
            double trackDeg,
            double verticalSpeedFpm) {

        public static State from(AircraftPosition position, AircraftVector vector) {
            return new State(
                    position.latitude.toDouble(),
                    position.longitude.toDouble(),
                    position.altitude.toDouble(),
                    vector.heading.toDouble(),
                    vector.trueAirspeedKnots,
                    vector.groundSpeed.toDouble(),
                    vector.trackDeg,
                    vector.verticalSpeed.toDouble());
        }

        public AircraftPosition toPosition() {
            return new AircraftPosition(
                    new Latitude(latitude), new Longitude(longitude), new Altitude(altitudeFt));
        }

        public AircraftVector toVector() {
            return new AircraftVector(
                    new Heading(headingDeg),
                    new GroundSpeed(groundSpeedKt),
                    new VerticalSpeed(verticalSpeedFpm),
                    trackDeg,
                    trueAirspeedKt);
        }
    }

    public record Targets(
            double targetAltitudeFt, double instructedGroundSpeedKt, Integer indicatedAirspeedKnots) {}

    public record Limits(double maxTurnRateDegPerSec, double maxAccelerationKtPerSec, double maxClimbRateFpm) {}

    /**
     * Heading that guidance asks the aircraft to fly this tick (includes WCA for TrackCourse).
     */
    public static double resolveGuidanceHeading(
            Guidance guidance,
            double latitude,
            double longitude,
            double altitudeFt,
            double currentHeadingDeg,
            double trueAirspeedKt,
            UniformWind wind) {
        return switch (guidance) {
            case Guidance.FlyHeading fly -> MathUtils.normalizeAngle(fly.headingDeg());
            case Guidance.SteerTo steer -> PositionUtils.calculateBearingToTarget(
                    new AircraftPosition(new Latitude(latitude), new Longitude(longitude), new Altitude(altitudeFt)),
                    steer.fix().latitude.toDouble(),
                    steer.fix().longitude.toDouble());
            case Guidance.TrackCourse track -> {
                FixPosition here = new FixPosition(new Latitude(latitude), new Longitude(longitude));
                double xtk = HoldingPatternGeometry.crossTrackNm(track.anchorFix(), here, track.courseDeg());
                double intercept = Math.max(-MAX_INTERCEPT_DEG, Math.min(MAX_INTERCEPT_DEG, XTK_GAIN_DEG_PER_NM * xtk));
                // Positive xtk = right of course → steer left (subtract).
                double courseToFly = MathUtils.normalizeAngle(track.courseDeg() - intercept);
                yield WindTriangle.headingToMaintainCourseDeg(courseToFly, trueAirspeedKt, wind);
            }
        };
    }

    /** Desired ground track for a TrackCourse before WCA (course ± intercept). */
    public static double resolveDesiredTrackDeg(
            Guidance.TrackCourse track, double latitude, double longitude) {
        FixPosition here = new FixPosition(new Latitude(latitude), new Longitude(longitude));
        double xtk = HoldingPatternGeometry.crossTrackNm(track.anchorFix(), here, track.courseDeg());
        double intercept = Math.max(-MAX_INTERCEPT_DEG, Math.min(MAX_INTERCEPT_DEG, XTK_GAIN_DEG_PER_NM * xtk));
        return MathUtils.normalizeAngle(track.courseDeg() - intercept);
    }

    public static double nextHeading(
            double currentHeadingDeg,
            double targetHeadingDeg,
            double maxTurnRateDegPerSec,
            double dtSeconds,
            TurnConstraint constraint) {
        double rate = Math.max(0.0, maxTurnRateDegPerSec) * Math.max(0.0, dtSeconds);
        return switch (constraint) {
            case SHORTEST -> PositionUtils.calculateNextHeading(currentHeadingDeg, targetHeadingDeg, rate)
                    .toDouble();
            case FORCE_RIGHT -> {
                double clockwise = GeodeticUtils.normalizeAngle(targetHeadingDeg - currentHeadingDeg);
                // Only treat as captured when the target retreated a few degrees past the nose
                // (clockwise wraps to ~360). A genuine forced turn past 180° must keep going.
                if (clockwise > 180.0) {
                    double pastBy = 360.0 - clockwise;
                    if (pastBy <= HEADING_CAPTURE_DEG || pastBy <= rate) {
                        yield MathUtils.normalizeAngle(targetHeadingDeg);
                    }
                }
                yield MathUtils.normalizeAngle(currentHeadingDeg + Math.min(rate, clockwise));
            }
            case FORCE_LEFT -> {
                double counterClockwise = GeodeticUtils.normalizeAngle(currentHeadingDeg - targetHeadingDeg);
                if (counterClockwise > 180.0) {
                    double pastBy = 360.0 - counterClockwise;
                    if (pastBy <= HEADING_CAPTURE_DEG || pastBy <= rate) {
                        yield MathUtils.normalizeAngle(targetHeadingDeg);
                    }
                }
                yield MathUtils.normalizeAngle(currentHeadingDeg - Math.min(rate, counterClockwise));
            }
            case CONTINUOUS_RIGHT -> MathUtils.normalizeAngle(currentHeadingDeg + rate);
            case CONTINUOUS_LEFT -> MathUtils.normalizeAngle(currentHeadingDeg - rate);
        };
    }

    public static boolean headingCaptured(double currentHeadingDeg, double targetHeadingDeg) {
        double delta = Math.abs(
                MathUtils.normalizeAngle(currentHeadingDeg) - MathUtils.normalizeAngle(targetHeadingDeg));
        delta = Math.min(delta, 360.0 - delta);
        return delta <= HEADING_CAPTURE_DEG;
    }

    public static boolean trackCaptured(double trackDeg, double courseDeg) {
        return headingCaptured(trackDeg, courseDeg);
    }

    public static State next(
            State state,
            Guidance guidance,
            UniformWind wind,
            Targets targets,
            Limits limits,
            TurnConstraint turnConstraint,
            double dtSeconds) {
        double guidanceHeading = resolveGuidanceHeading(
                guidance,
                state.latitude(),
                state.longitude(),
                state.altitudeFt(),
                state.headingDeg(),
                state.trueAirspeedKt(),
                wind);
        // Limits are per-second; scale by dt so 1 s ticks match the legacy call sites.
        double turnBudget = limits.maxTurnRateDegPerSec() * Math.max(0.0, dtSeconds);
        double nextHeading = nextHeading(
                state.headingDeg(), guidanceHeading, turnBudget, 1.0, turnConstraint);

        double targetTas = resolveTargetTrueAirspeed(
                nextHeading,
                state.altitudeFt(),
                targets.instructedGroundSpeedKt(),
                targets.indicatedAirspeedKnots(),
                wind);
        double accelBudget = limits.maxAccelerationKtPerSec() * Math.max(0.0, dtSeconds);
        double nextTas = PositionUtils.calculateNextGroundSpeed(
                        state.trueAirspeedKt(), targetTas, accelBudget)
                .toDouble();

        double nextVs = PositionUtils.calculateNextVerticalSpeed(
                        state.altitudeFt(),
                        targets.targetAltitudeFt(),
                        limits.maxClimbRateFpm(),
                        dtSeconds)
                .toDouble();
        if (Math.abs(state.altitudeFt() - targets.targetAltitudeFt()) <= 5.0 && Math.abs(nextVs) <= 50.0) {
            nextVs = 0.0;
        }

        WindTriangle.GroundVelocity gv = WindTriangle.groundVelocity(nextHeading, nextTas, wind);
        // PositionUtils applies verticalSpeed once; pass the pre-step altitude.
        AircraftPosition stepped = PositionUtils.calculateNextPosition(
                new AircraftPosition(
                        new Latitude(state.latitude()),
                        new Longitude(state.longitude()),
                        new Altitude(
                                nextVs == 0.0 && Math.abs(state.altitudeFt() - targets.targetAltitudeFt()) <= 5.0
                                        ? targets.targetAltitudeFt()
                                        : state.altitudeFt())),
                Math.max(0.0, gv.groundSpeedKt()),
                gv.trackDeg(),
                nextVs,
                dtSeconds);

        return new State(
                stepped.latitude.toDouble(),
                stepped.longitude.toDouble(),
                stepped.altitude.toDouble(),
                nextHeading,
                nextTas,
                gv.groundSpeedKt(),
                gv.trackDeg(),
                nextVs);
    }

    public static double resolveTargetTrueAirspeed(
            double headingDeg,
            double altitudeFt,
            double instructedGroundSpeedKt,
            Integer indicatedAirspeedKnots,
            UniformWind wind) {
        if (indicatedAirspeedKnots != null) {
            return IndicatedAirspeedConverter.indicatedToTrueAirspeedKnots(
                    indicatedAirspeedKnots, altitudeFt);
        }
        if (wind == null || !wind.affectsMotion()) {
            return instructedGroundSpeedKt;
        }
        return WindTriangle.trueAirspeedForGroundSpeed(headingDeg, instructedGroundSpeedKt, wind);
    }
}
