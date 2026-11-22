package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureAltitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;

/**
 * Maps catalog / flight-plan altitude rows onto {@link VerticalConstraint}.
 * Flight-plan feet use FL×100 when only {@code fl} is present (unchanged contract).
 */
public final class VerticalConstraints {

    private VerticalConstraints() {}

    public static VerticalConstraint fromFlightPlanWaypoint(FlightPlanWaypoint waypoint) {
        if (waypoint == null || !waypoint.hasAltitudeConstraint() || waypoint.getTargetAltitude() == null) {
            return new VerticalConstraint.None();
        }
        return from(waypoint.getAltitudeConstraint(), waypoint.getTargetAltitude().toDouble(), null);
    }

    public static VerticalConstraint fromProcedureAltitude(ProcedureAltitude published) {
        if (published == null) {
            return new VerticalConstraint.None();
        }
        Integer feet = published.resolveFeetForFlightPlan();
        if (feet == null) {
            return new VerticalConstraint.None();
        }
        AltitudeConstraint constraint =
                published.constraint() != null ? published.constraint() : AltitudeConstraint.NONE;
        if (constraint == AltitudeConstraint.NONE) {
            // Catalog bare feet become AT when mapped onto a flight plan.
            return new VerticalConstraint.At(feet.doubleValue());
        }
        return from(constraint, feet.doubleValue(), null);
    }

    public static VerticalConstraint from(
            AltitudeConstraint constraint, double altitudeFt, Double windowMaxFt) {
        if (constraint == null || constraint == AltitudeConstraint.NONE) {
            return new VerticalConstraint.None();
        }
        return switch (constraint) {
            case NONE -> new VerticalConstraint.None();
            case AT -> new VerticalConstraint.At(altitudeFt);
            case AT_OR_ABOVE -> new VerticalConstraint.AtOrAbove(altitudeFt);
            case AT_OR_BELOW -> new VerticalConstraint.AtOrBelow(altitudeFt);
            case BETWEEN -> {
                double max = windowMaxFt != null ? windowMaxFt : altitudeFt;
                yield new VerticalConstraint.Window(altitudeFt, max);
            }
        };
    }

    public static AltitudeConstraint toAltitudeConstraint(VerticalConstraint constraint) {
        return switch (constraint) {
            case VerticalConstraint.None n -> AltitudeConstraint.NONE;
            case VerticalConstraint.At a -> AltitudeConstraint.AT;
            case VerticalConstraint.AtOrAbove a -> AltitudeConstraint.AT_OR_ABOVE;
            case VerticalConstraint.AtOrBelow a -> AltitudeConstraint.AT_OR_BELOW;
            case VerticalConstraint.Window w -> AltitudeConstraint.BETWEEN;
        };
    }

    public static Altitude primaryAltitude(VerticalConstraint constraint) {
        return switch (constraint) {
            case VerticalConstraint.None n -> null;
            case VerticalConstraint.At a -> new Altitude(a.altitudeFt());
            case VerticalConstraint.AtOrAbove a -> new Altitude(a.altitudeFt());
            case VerticalConstraint.AtOrBelow a -> new Altitude(a.altitudeFt());
            case VerticalConstraint.Window w -> new Altitude(w.minFt());
        };
    }
}
