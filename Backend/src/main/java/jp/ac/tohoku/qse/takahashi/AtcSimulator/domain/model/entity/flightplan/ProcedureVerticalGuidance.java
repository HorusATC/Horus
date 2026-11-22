package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

/**
 * Look-ahead vertical targeting for procedure-assigned aircraft.
 *
 * <p>Climb: unconstrained next fixes no longer fall back to a low default cruise while later
 * at-or-above floors remain — the target stays at clearance / the highest ahead climb floor.
 *
 * <p>Hard level constraints (AT / at-or-below / window) on the <em>next</em> fix keep the legacy
 * immediate target so STAR quickstart geometry stays stable; reachability is still assessed
 * with path look-ahead.
 */
public final class ProcedureVerticalGuidance {

    public static final double PASS_EVAL_NM = 1.5;

    private ProcedureVerticalGuidance() {}

    public record Resolution(Altitude targetAltitude, Set<String> newlyUnreachableFixes) {}

    public static Resolution resolve(
            List<FlightPlanWaypoint> remainingWaypoints,
            AircraftPosition position,
            double cruiseAltitudeFt,
            double maxClimbRateFpm,
            double groundSpeedKt,
            Set<String> alreadyUnreachable) {
        if (remainingWaypoints == null || remainingWaypoints.isEmpty()) {
            return new Resolution(new Altitude(cruiseAltitudeFt), Set.of());
        }

        double currentAlt = position.altitude.toDouble();
        Set<String> newlyUnreachable = new LinkedHashSet<>();

        double climbFloorFt = cruiseAltitudeFt;
        double nmAlong = 0.0;
        FixPosition prev = new FixPosition(position.latitude, position.longitude);
        boolean reachabilityChecked = false;

        for (FlightPlanWaypoint wp : remainingWaypoints) {
            nmAlong += GeodeticUtils.distanceToFix(
                    new AircraftPosition(prev.latitude, prev.longitude, position.altitude),
                    wp.getPosition());
            prev = wp.getPosition();

            VerticalConstraint constraint = VerticalConstraints.fromFlightPlanWaypoint(wp);
            double minutesToPass =
                    minutesToCoverNm(Math.max(0.0, nmAlong - PASS_EVAL_NM), groundSpeedKt);

            switch (constraint) {
                case VerticalConstraint.None n -> {
                }
                case VerticalConstraint.AtOrAbove(double floorFt) -> {
                    climbFloorFt = Math.max(climbFloorFt, floorFt);
                    if (!reachabilityChecked && currentAlt < floorFt) {
                        maybeFlag(
                                wp.getFixName(),
                                floorFt - currentAlt,
                                minutesToPass,
                                maxClimbRateFpm,
                                alreadyUnreachable,
                                newlyUnreachable);
                        reachabilityChecked = true;
                    }
                }
                case VerticalConstraint.At(double atFt) -> {
                    climbFloorFt = Math.max(climbFloorFt, atFt);
                    if (!reachabilityChecked && Math.abs(atFt - currentAlt) > 1.0) {
                        maybeFlag(
                                wp.getFixName(),
                                Math.abs(atFt - currentAlt),
                                minutesToPass,
                                maxClimbRateFpm,
                                alreadyUnreachable,
                                newlyUnreachable);
                        reachabilityChecked = true;
                    }
                }
                case VerticalConstraint.AtOrBelow(double ceilingFt) -> {
                    if (currentAlt > ceilingFt && !reachabilityChecked) {
                        maybeFlag(
                                wp.getFixName(),
                                currentAlt - ceilingFt,
                                minutesToPass,
                                maxClimbRateFpm,
                                alreadyUnreachable,
                                newlyUnreachable);
                        reachabilityChecked = true;
                    }
                }
                case VerticalConstraint.Window(double minFt, double maxFt) -> {
                    climbFloorFt = Math.max(climbFloorFt, minFt);
                    if (!reachabilityChecked) {
                        if (currentAlt < minFt) {
                            maybeFlag(
                                    wp.getFixName(),
                                    minFt - currentAlt,
                                    minutesToPass,
                                    maxClimbRateFpm,
                                    alreadyUnreachable,
                                    newlyUnreachable);
                            reachabilityChecked = true;
                        } else if (currentAlt > maxFt) {
                            maybeFlag(
                                    wp.getFixName(),
                                    currentAlt - maxFt,
                                    minutesToPass,
                                    maxClimbRateFpm,
                                    alreadyUnreachable,
                                    newlyUnreachable);
                            reachabilityChecked = true;
                        }
                    }
                }
            }
        }

        FlightPlanWaypoint next = remainingWaypoints.get(0);
        VerticalConstraint nextConstraint = VerticalConstraints.fromFlightPlanWaypoint(next);
        Altitude target = switch (nextConstraint) {
            case VerticalConstraint.None n -> {
                // Climb continuation: do not sink to a lower cruise between higher floors.
                // STAR paths keep legacy cruise fallback here so the 10-minute arrival sample
                // conflict geometry stays unchanged; unmet AT levels are flagged unreachable.
                if (climbFloorFt > cruiseAltitudeFt) {
                    yield new Altitude(climbFloorFt);
                }
                yield new Altitude(cruiseAltitudeFt);
            }
            case VerticalConstraint.AtOrAbove(double floorFt) -> {
                double targetFt = Math.max(climbFloorFt, floorFt);
                if (currentAlt >= floorFt) {
                    targetFt = Math.max(currentAlt, targetFt);
                }
                yield new Altitude(targetFt);
            }
            case VerticalConstraint.At(double altitudeFt) -> new Altitude(altitudeFt);
            case VerticalConstraint.AtOrBelow(double altitudeFt) -> new Altitude(altitudeFt);
            case VerticalConstraint.Window(double minFt, double maxFt) -> {
                if (currentAlt < minFt) {
                    yield new Altitude(minFt);
                }
                if (currentAlt > maxFt) {
                    yield new Altitude(maxFt);
                }
                yield new Altitude(currentAlt);
            }
        };

        return new Resolution(target, newlyUnreachable);
    }

    private static void maybeFlag(
            String fixName,
            double absDeltaFt,
            double minutesToPass,
            double maxRateFpm,
            Set<String> alreadyUnreachable,
            Set<String> newlyUnreachable) {
        if (fixName == null || absDeltaFt <= 0) {
            return;
        }
        if (alreadyUnreachable != null && alreadyUnreachable.contains(fixName)) {
            return;
        }
        if (minutesToPass <= 0) {
            newlyUnreachable.add(fixName);
            return;
        }
        if (absDeltaFt / minutesToPass > maxRateFpm + 1e-6) {
            newlyUnreachable.add(fixName);
        }
    }

    private static double minutesToCoverNm(double nm, double groundSpeedKt) {
        if (nm <= 0) {
            return 0.0;
        }
        return (nm / Math.max(1.0, groundSpeedKt)) * 60.0;
    }

    public static List<FlightPlanWaypoint> remainingFrom(FlightPlan plan, int currentIndex) {
        if (plan == null || plan.getWaypoints().isEmpty()) {
            return List.of();
        }
        List<FlightPlanWaypoint> all = plan.getWaypoints();
        int from = Math.max(0, Math.min(currentIndex, all.size()));
        if (from >= all.size()) {
            return List.of();
        }
        return new ArrayList<>(all.subList(from, all.size()));
    }
}
