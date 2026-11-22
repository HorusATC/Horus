package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.navigation;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;

/**
 * Single discriminated lateral navigation state for an aircraft.
 *
 * <p>Illegal combinations that previously arose from independent flags
 * ({@code FLIGHT_PLAN} + VA, plain heading + leftover JOIN FINAL course,
 * {@code InitialClimb} + {@code TowerHandoff}, etc.) are unrepresentable.
 *
 * <p>Location API strings ({@link NavigationMode} plus {@code goAround} /
 * {@code initialClimb} / {@code towerHandoff}) are projections of this type.
 */
public sealed interface LateralNavigation
        permits LateralNavigation.PlainHeading,
                LateralNavigation.FinalApproach,
                LateralNavigation.FlightPlanNav,
                LateralNavigation.ProcedureVa,
                LateralNavigation.DirectToNav,
                LateralNavigation.HoldingNav,
                LateralNavigation.InitialClimb,
                LateralNavigation.GoAround,
                LateralNavigation.TowerHandoff {

    /** Vectoring without a JOIN FINAL approach course. */
    record PlainHeading() implements LateralNavigation {}

    /**
     * Established (or establishing) on a JOIN FINAL approach course under HEADING projection.
     *
     * @param courseHeadingDeg published approach course
     * @param pending true while still Direct-To the intercept; false once established
     */
    record FinalApproach(double courseHeadingDeg, boolean pending) implements LateralNavigation {
        public FinalApproach {
            if (pending) {
                throw new IllegalArgumentException(
                        "FinalApproach pending intercept must use DirectToNav.joinFinal");
            }
        }
    }

    /** Following assigned flight-plan fixes (no VA, no approach course). */
    record FlightPlanNav() implements LateralNavigation {}

    /**
     * SID VA leg: hold published true course until AT_OR_ABOVE altitude.
     * Projects as {@link NavigationMode#HEADING} with {@code procedureVaActive}.
     */
    record ProcedureVa(double courseTrueDeg, int atOrAboveAltitudeFt) implements LateralNavigation {}

    /**
     * Direct To a fix. When {@code approachCourseHeadingDeg} is non-null this is JOIN FINAL
     * intercept ({@code pending} until the fix); otherwise a plain Direct To.
     */
    record DirectToNav(Double approachCourseHeadingDeg, boolean approachCoursePending)
            implements LateralNavigation {
        public DirectToNav {
            if (approachCoursePending && approachCourseHeadingDeg == null) {
                throw new IllegalArgumentException("pending JOIN FINAL requires an approach course");
            }
            if (!approachCoursePending && approachCourseHeadingDeg != null) {
                throw new IllegalArgumentException(
                        "non-pending approach course belongs on FinalApproach, not DirectToNav");
            }
        }

        public static DirectToNav plain() {
            return new DirectToNav(null, false);
        }

        public static DirectToNav joinFinal(double approachCourseHeadingDeg) {
            return new DirectToNav(approachCourseHeadingDeg, true);
        }
    }

    record HoldingNav() implements LateralNavigation {}

    record InitialClimb(
            double startLatitude,
            double startLongitude,
            double runwayHeadingDeg,
            Double deferredInitialHeadingDeg,
            double derElevationFt)
            implements LateralNavigation {}

    record GoAround() implements LateralNavigation {}

    /**
     * Approach seat released to the tower. Projects as HEADING; may retain an established
     * approach course for guidance until removal.
     */
    record TowerHandoff(FixPosition threshold, Double approachCourseHeadingDeg)
            implements LateralNavigation {
        public TowerHandoff {
            if (threshold == null) {
                throw new IllegalArgumentException("threshold is required");
            }
        }
    }

    default NavigationMode projectedNavigationMode() {
        return switch (this) {
            case PlainHeading h -> NavigationMode.HEADING;
            case FinalApproach f -> NavigationMode.HEADING;
            case FlightPlanNav f -> NavigationMode.FLIGHT_PLAN;
            case ProcedureVa v -> NavigationMode.HEADING;
            case DirectToNav d -> NavigationMode.DIRECT_TO;
            case HoldingNav h -> NavigationMode.HOLDING;
            case InitialClimb i -> NavigationMode.HEADING;
            case GoAround g -> NavigationMode.HEADING;
            case TowerHandoff t -> NavigationMode.HEADING;
        };
    }

    default boolean projectedGoAround() {
        return this instanceof GoAround;
    }

    default boolean projectedInitialClimb() {
        return this instanceof InitialClimb;
    }

    default boolean projectedTowerHandoff() {
        return this instanceof TowerHandoff;
    }

    default boolean projectedProcedureVa() {
        return this instanceof ProcedureVa;
    }

    default Double approachCourseHeadingOrNull() {
        return switch (this) {
            case FinalApproach f -> f.courseHeadingDeg();
            case DirectToNav d -> d.approachCourseHeadingDeg();
            case TowerHandoff t -> t.approachCourseHeadingDeg();
            default -> null;
        };
    }

    default boolean approachCoursePending() {
        return this instanceof DirectToNav d && d.approachCoursePending();
    }

    /** IAS instruction applies on the kinematic step (legacy HEADING && !VA). */
    default boolean usesInstructedIndicatedAirspeed() {
        return switch (this) {
            case PlainHeading h -> true;
            case FinalApproach f -> true;
            case InitialClimb i -> true;
            case GoAround g -> true;
            case TowerHandoff t -> true;
            default -> false;
        };
    }
}
