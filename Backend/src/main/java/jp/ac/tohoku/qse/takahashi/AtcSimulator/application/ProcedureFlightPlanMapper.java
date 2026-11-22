package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.fix.FixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlan;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.VerticalConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.VerticalConstraints;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.WaypointAction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.InstrumentProcedure;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureAltitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedurePathDescriptor;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureVaConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;

/**
 * Maps assignable SID/STAR legs onto existing {@link FlightPlanWaypoint} rows.
 * Path-table KIAS is never copied into ground-speed {@code targetSpeed}.
 * FL150 becomes 15000 ft AT as a simulator field mapping (not an AIP conversion table).
 */
@Component
public class ProcedureFlightPlanMapper {

    public static final int DEFAULT_CRUISE_ALTITUDE_FT = 10000;
    public static final int DEFAULT_CRUISE_SPEED_KT = 250;

    private final FixPositionRepository fixPositionRepository;

    public ProcedureFlightPlanMapper(FixPositionRepository fixPositionRepository) {
        this.fixPositionRepository = fixPositionRepository;
    }

    public record MappedProcedureRoute(
            FlightPlan flightPlan,
            ProcedureVaConstraint vaConstraint,
            List<String> waypointNames
    ) {
    }

    public MappedProcedureRoute map(
            Callsign callsign,
            InstrumentProcedure primary,
            Optional<InstrumentProcedure> transition,
            Integer cruiseAltitudeFt,
            Integer cruiseSpeedKt) {
        if (!primary.isAssignablePrimary()) {
            throw new IllegalArgumentException("Procedure is not assignable: " + primary.id());
        }
        if (transition.isPresent()) {
            InstrumentProcedure t = transition.get();
            if (t.type() != ProcedureType.SID_TRANSITION) {
                throw new IllegalArgumentException("Transition must be SID_TRANSITION: " + t.id());
            }
            if (!primary.enrouteTransitions().contains(t.id())) {
                throw new IllegalArgumentException(
                        "Transition " + t.id() + " is not listed on " + primary.id());
            }
        }

        List<FlightPlanWaypoint> waypoints = new ArrayList<>();
        ProcedureVaConstraint vaConstraint = null;

        for (ProcedureLeg leg : primary.legs()) {
            switch (mapAction(leg.pathDescriptor())) {
                case SKIP -> {
                    // HOLD / UNKNOWN stay catalog-only.
                }
                case VA -> vaConstraint = ProcedureVaConstraint.fromVaLeg(leg);
                case FIX -> appendFixLeg(waypoints, leg);
            }
        }

        if (transition.isPresent()) {
            for (ProcedureLeg leg : transition.get().legs()) {
                switch (mapAction(leg.pathDescriptor())) {
                    case SKIP, VA -> {
                        // Transitions do not re-arm VA; HOLD / UNKNOWN stay catalog-only.
                    }
                    case FIX -> {
                        if (leg.pathDescriptor() == ProcedurePathDescriptor.IF
                                && !waypoints.isEmpty()
                                && leg.waypointIdentifier() != null
                                && leg.waypointIdentifier()
                                        .equalsIgnoreCase(
                                                waypoints.get(waypoints.size() - 1).getFixName())) {
                            // Connect transition at shared fix already present (e.g. VAMOS).
                            break;
                        }
                        appendFixLeg(waypoints, leg);
                    }
                }
            }
        }

        if (waypoints.isEmpty()) {
            throw new IllegalArgumentException("Procedure has no flyable fix legs: " + primary.id());
        }

        // When assign omits cruise on a SID, raise clearance to the highest climb constraint
        // on the path so Climb-Via does not descend back toward the 10000 ft default between
        // published at-or-above floors. Explicit cruise (including quickstart samples) wins.
        int cruiseAlt = cruiseAltitudeFt != null
                ? cruiseAltitudeFt
                : defaultCruiseAltitudeFt(primary.type(), waypoints);
        int cruiseSpd = cruiseSpeedKt != null ? cruiseSpeedKt : DEFAULT_CRUISE_SPEED_KT;
        String departure = primary.type() == ProcedureType.SID ? primary.airportIcao() : "";
        String arrival = primary.type() == ProcedureType.STAR ? primary.airportIcao() : "";

        FlightPlan plan = new FlightPlan(
                callsign,
                departure,
                arrival,
                waypoints,
                new Altitude(cruiseAlt),
                new GroundSpeed(cruiseSpd));

        List<String> names = waypoints.stream().map(FlightPlanWaypoint::getFixName).toList();
        return new MappedProcedureRoute(plan, vaConstraint, names);
    }

    /**
     * Exhaustive path-descriptor dispatch. New enum values fail compilation here
     * instead of silently becoming TF-like fixes.
     */
    private static LegMapAction mapAction(ProcedurePathDescriptor descriptor) {
        return switch (descriptor) {
            case HOLD, UNKNOWN -> LegMapAction.SKIP;
            case VA -> LegMapAction.VA;
            case IF, TF, DF -> LegMapAction.FIX;
        };
    }

    private enum LegMapAction {
        SKIP,
        VA,
        FIX
    }

    static int defaultCruiseAltitudeFt(ProcedureType type, List<FlightPlanWaypoint> waypoints) {
        if (type != ProcedureType.SID) {
            return DEFAULT_CRUISE_ALTITUDE_FT;
        }
        int highest = DEFAULT_CRUISE_ALTITUDE_FT;
        for (FlightPlanWaypoint wp : waypoints) {
            VerticalConstraint constraint = VerticalConstraints.fromFlightPlanWaypoint(wp);
            highest = Math.max(highest, climbFloorFt(constraint));
        }
        return highest;
    }

    private static int climbFloorFt(VerticalConstraint constraint) {
        return switch (constraint) {
            case VerticalConstraint.None n -> 0;
            case VerticalConstraint.At(double altitudeFt) -> (int) Math.round(altitudeFt);
            case VerticalConstraint.AtOrAbove(double altitudeFt) -> (int) Math.round(altitudeFt);
            case VerticalConstraint.AtOrBelow(double altitudeFt) -> 0;
            case VerticalConstraint.Window(double minFt, double maxFt) -> (int) Math.round(minFt);
        };
    }

    private void appendFixLeg(List<FlightPlanWaypoint> waypoints, ProcedureLeg leg) {
        if (leg.waypointIdentifier() == null || leg.waypointIdentifier().isBlank()) {
            throw new IllegalArgumentException(
                    "Fix leg missing waypointIdentifier (serial=" + leg.serial() + ")");
        }
        String fixName = leg.waypointIdentifier().trim();
        FixPosition position = fixPositionRepository.findFixPositionByName(fixName)
                .orElseThrow(() -> new IllegalArgumentException("Fix not found: " + fixName));

        ProcedureAltitude published = leg.altitude();
        AltitudeConstraint constraint = AltitudeConstraint.NONE;
        Altitude targetAltitude = null;
        if (published != null) {
            constraint = published.constraint() != null ? published.constraint() : AltitudeConstraint.NONE;
            Integer feet = published.resolveFeetForFlightPlan();
            if (feet != null) {
                targetAltitude = new Altitude(feet);
            }
            if (constraint == AltitudeConstraint.NONE && feet != null) {
                constraint = AltitudeConstraint.AT;
            }
        }

        // KIAS from the path table must not become FlightPlanWaypoint targetSpeed (ground speed).
        waypoints.add(new FlightPlanWaypoint(
                fixName, position, targetAltitude, null, constraint, WaypointAction.CONTINUE));
    }
}
