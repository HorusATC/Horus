package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.guidance;

/** How the kinematic step turns toward the guidance heading. */
public enum TurnConstraint {
    /** Shortest turn to the target. */
    SHORTEST,
    /** Clockwise only; remaining clockwise delta &gt; 180° means already reached. */
    FORCE_RIGHT,
    /** Counter-clockwise only; remaining CCW delta &gt; 180° means already reached. */
    FORCE_LEFT,
    /** Continuous clockwise rate turn (parallel re-intercept past 180°). */
    CONTINUOUS_RIGHT,
    /** Continuous counter-clockwise rate turn. */
    CONTINUOUS_LEFT
}
