package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.guidance;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;

/**
 * Exactly one lateral guidance kind per simulation tick.
 * Wind correction angle applies only for {@link TrackCourse}.
 */
public sealed interface Guidance permits Guidance.FlyHeading, Guidance.TrackCourse, Guidance.SteerTo {

    /** Fly a magnetic/true heading (vectoring, hold turns). No WCA. */
    record FlyHeading(double headingDeg) implements Guidance {}

    /**
     * Hold a ground track along {@code courseDeg}, closing cross-track error toward the
     * course line through {@code anchorFix}. WCA is applied after the intercept angle.
     */
    record TrackCourse(double courseDeg, FixPosition anchorFix) implements Guidance {}

    /** Point the nose at {@code fix} (Direct To / pre-entry). No WCA. */
    record SteerTo(FixPosition fix) implements Guidance {}
}
