package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;

public final class WaypointAltitudeTargetResolver {

    private WaypointAltitudeTargetResolver() {}

    /**
     * Single-waypoint target (non-procedure / legacy path). Exhaustive on {@link VerticalConstraint}.
     */
    public static Altitude resolveTarget(FlightPlanWaypoint waypoint, double currentAltitudeFeet) {
        VerticalConstraint constraint = VerticalConstraints.fromFlightPlanWaypoint(waypoint);
        return resolveTarget(constraint, currentAltitudeFeet);
    }

    public static Altitude resolveTarget(VerticalConstraint constraint, double currentAltitudeFeet) {
        return switch (constraint) {
            case VerticalConstraint.None n -> null;
            case VerticalConstraint.At(double altitudeFt) -> new Altitude(altitudeFt);
            case VerticalConstraint.AtOrAbove(double altitudeFt) -> new Altitude(
                    currentAltitudeFeet >= altitudeFt ? currentAltitudeFeet : altitudeFt);
            case VerticalConstraint.AtOrBelow(double altitudeFt) -> new Altitude(altitudeFt);
            case VerticalConstraint.Window(double minFt, double maxFt) -> {
                if (currentAltitudeFeet < minFt) {
                    yield new Altitude(minFt);
                }
                if (currentAltitudeFeet > maxFt) {
                    yield new Altitude(maxFt);
                }
                yield new Altitude(currentAltitudeFeet);
            }
        };
    }

    public static boolean satisfiesConstraintAtPass(FlightPlanWaypoint waypoint, double altitudeFeet) {
        VerticalConstraint constraint = VerticalConstraints.fromFlightPlanWaypoint(waypoint);
        return satisfiesConstraintAtPass(constraint, altitudeFeet);
    }

    public static boolean satisfiesConstraintAtPass(VerticalConstraint constraint, double altitudeFeet) {
        return switch (constraint) {
            case VerticalConstraint.None n -> true;
            case VerticalConstraint.At(double altitudeFt) -> Math.abs(altitudeFeet - altitudeFt) <= 500;
            case VerticalConstraint.AtOrAbove(double altitudeFt) -> altitudeFeet + 100 >= altitudeFt;
            case VerticalConstraint.AtOrBelow(double altitudeFt) -> altitudeFeet - 100 <= altitudeFt;
            case VerticalConstraint.Window(double minFt, double maxFt) ->
                    altitudeFeet + 500 >= minFt && altitudeFeet - 500 <= maxFt;
        };
    }

    /**
     * Tight published-constraint check used for unreachable-flag maintenance.
     * Matches the altitude-constraints acceptance band (not the wider domain pass band).
     */
    public static boolean meetsPublishedConstraint(FlightPlanWaypoint waypoint, double altitudeFeet) {
        return meetsPublishedConstraint(VerticalConstraints.fromFlightPlanWaypoint(waypoint), altitudeFeet);
    }

    public static boolean meetsPublishedConstraint(VerticalConstraint constraint, double altitudeFeet) {
        return switch (constraint) {
            case VerticalConstraint.None n -> true;
            case VerticalConstraint.At(double altitudeFt) -> Math.abs(altitudeFeet - altitudeFt) <= 100;
            case VerticalConstraint.AtOrAbove(double altitudeFt) -> altitudeFeet + 50 >= altitudeFt;
            case VerticalConstraint.AtOrBelow(double altitudeFt) -> altitudeFeet - 50 <= altitudeFt;
            case VerticalConstraint.Window(double minFt, double maxFt) ->
                    altitudeFeet + 100 >= minFt && altitudeFeet - 100 <= maxFt;
        };
    }
}
