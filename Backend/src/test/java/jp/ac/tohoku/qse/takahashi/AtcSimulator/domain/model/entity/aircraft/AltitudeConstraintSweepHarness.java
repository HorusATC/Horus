package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.ProcedureFlightPlanMapper;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.airport.RunwayDefinition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.InstrumentProcedure;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedurePathDescriptor;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.departure.InitialClimbGeometry;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Heading;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.VerticalSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.AtsRouteFixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.ProcedureCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.RunwayCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.PositionUtils;

/**
 * Domain-only SID/STAR altitude-constraint sweep used by acceptance tests.
 * Start conditions mirror {@code aip/altitude-constraint-sweep.json} / the altitude-constraints spec.
 */
public final class AltitudeConstraintSweepHarness {

    public static final int MAX_SECONDS = 2400;
    public static final double SID_START_ALT_FT = 300.0;
    public static final double SID_START_SPEED_KT = 200.0;
    public static final double STAR_START_SPEED_KT = 280.0;
    public static final double STAR_DEFAULT_START_ALT_FT = 16000.0;

    /** Acceptance tolerances from the altitude-constraints spec (stricter than domain pass). */
    public static final double ACCEPT_AT_OR_ABOVE_BELOW_FT = 50.0;
    public static final double ACCEPT_AT_WINDOW_FT = 100.0;

    private final ProcedureCatalog catalog;
    private final ProcedureFlightPlanMapper mapper;
    private final RunwayCatalog runwayCatalog;

    public AltitudeConstraintSweepHarness() {
        this.catalog = new ProcedureCatalog();
        AtsRouteFixPositionRepository fixes = new AtsRouteFixPositionRepository();
        this.mapper = new ProcedureFlightPlanMapper(fixes);
        this.runwayCatalog = new RunwayCatalog();
    }

    public record CaseSpec(String procedureId, String transitionId) {}

    public record FixPass(
            String fix,
            int t,
            double altFt,
            double distNm,
            AltitudeConstraint constraint,
            Double publishedFt) {}

    public record ConstraintMiss(
            String procedureId,
            String transitionId,
            String fix,
            AltitudeConstraint constraint,
            double publishedFt,
            double actualFt,
            double deltaFt,
            double passDistNm) {}

    public record FlightResult(
            String procedureId,
            String transitionId,
            ProcedureType type,
            boolean completed,
            int endSecond,
            List<FixPass> passes,
            List<ConstraintMiss> acceptanceMisses,
            List<String> unreachableConstraintFixes) {}

    public List<CaseSpec> enumerateCases() {
        List<CaseSpec> cases = new ArrayList<>();
        for (InstrumentProcedure primary : catalog.all()) {
            if (!primary.isAssignablePrimary()) {
                continue;
            }
            boolean include =
                    primary.type() == ProcedureType.STAR
                            || (primary.type() == ProcedureType.SID && primary.rnav());
            if (!include) {
                continue;
            }
            cases.add(new CaseSpec(primary.id(), null));
            for (String transitionId : primary.enrouteTransitions()) {
                cases.add(new CaseSpec(primary.id(), transitionId));
            }
        }
        return cases;
    }

    public FlightResult fly(CaseSpec spec) {
        InstrumentProcedure primary = catalog.findById(spec.procedureId()).orElseThrow();
        Optional<InstrumentProcedure> transition = Optional.empty();
        if (spec.transitionId() != null) {
            transition = Optional.of(catalog.findById(spec.transitionId()).orElseThrow());
        }
        ProcedureFlightPlanMapper.MappedProcedureRoute mapped =
                mapper.map(new Callsign("SWP"), primary, transition, null, null);

        CommercialAircraft aircraft =
                primary.type() == ProcedureType.SID
                        ? spawnSid(primary, mapped)
                        : spawnStar(primary, mapped);
        aircraft.assignProcedureRoute(mapped.flightPlan(), mapped.vaConstraint());

        List<FlightPlanWaypoint> waypoints = mapped.flightPlan().getWaypoints();
        List<FixPass> passes = new ArrayList<>();
        int observedIndex = 0;
        boolean completed = false;
        int endSecond = MAX_SECONDS;

        for (int t = 1; t <= MAX_SECONDS; t++) {
            aircraft.calculateNextAircraftVector(1.0);
            aircraft.calculateNextAircraftPosition(1.0);

            int idx = aircraft.getCurrentWaypointIndex();
            while (observedIndex < idx && observedIndex < waypoints.size()) {
                FlightPlanWaypoint wp = waypoints.get(observedIndex);
                double dist = GeodeticUtils.distanceToFix(aircraft.getAircraftPosition(), wp.getPosition());
                passes.add(new FixPass(
                        wp.getFixName(),
                        t,
                        aircraft.getAircraftPosition().altitude.toDouble(),
                        dist,
                        wp.getAltitudeConstraint(),
                        wp.getTargetAltitude() != null ? wp.getTargetAltitude().toDouble() : null));
                observedIndex++;
            }

            if (observedIndex >= waypoints.size()) {
                completed = true;
                endSecond = t;
                break;
            }
            if (aircraft.getNavigationMode() == NavigationMode.HEADING
                    && observedIndex >= waypoints.size()) {
                completed = true;
                endSecond = t;
                break;
            }
        }

        Set<String> unreachable = new LinkedHashSet<>(aircraft.getUnreachableAltitudeConstraintFixes());
        List<ConstraintMiss> acceptanceMisses = new ArrayList<>();
        for (FixPass pass : passes) {
            if (pass.publishedFt() == null
                    || pass.constraint() == null
                    || pass.constraint() == AltitudeConstraint.NONE) {
                continue;
            }
            if (unreachable.contains(pass.fix())) {
                continue;
            }
            double delta = strictDelta(pass.constraint(), pass.publishedFt(), pass.altFt());
            if (!withinAcceptance(pass.constraint(), delta)) {
                acceptanceMisses.add(new ConstraintMiss(
                        spec.procedureId(),
                        spec.transitionId(),
                        pass.fix(),
                        pass.constraint(),
                        pass.publishedFt(),
                        pass.altFt(),
                        Math.abs(delta),
                        pass.distNm()));
            }
        }

        return new FlightResult(
                spec.procedureId(),
                spec.transitionId(),
                primary.type(),
                completed,
                endSecond,
                passes,
                acceptanceMisses,
                List.copyOf(unreachable));
    }

    public static double strictDelta(AltitudeConstraint constraint, double publishedFt, double actualFt) {
        return switch (constraint) {
            case AT_OR_ABOVE -> publishedFt - actualFt; // positive = short
            case AT_OR_BELOW -> actualFt - publishedFt; // positive = high
            case AT, BETWEEN -> actualFt - publishedFt; // signed error
            case NONE -> 0.0;
        };
    }

    public static boolean withinAcceptance(AltitudeConstraint constraint, double signedDelta) {
        return switch (constraint) {
            case AT_OR_ABOVE, AT_OR_BELOW -> signedDelta <= ACCEPT_AT_OR_ABOVE_BELOW_FT;
            case AT, BETWEEN -> Math.abs(signedDelta) <= ACCEPT_AT_WINDOW_FT;
            case NONE -> true;
        };
    }

    public static boolean isStrictMiss(AltitudeConstraint constraint, double publishedFt, double actualFt) {
        return switch (constraint) {
            case AT_OR_ABOVE -> actualFt < publishedFt;
            case AT_OR_BELOW -> actualFt > publishedFt;
            case AT, BETWEEN -> Math.abs(actualFt - publishedFt) > 100.0;
            case NONE -> false;
        };
    }

    private CommercialAircraft spawnSid(
            InstrumentProcedure primary, ProcedureFlightPlanMapper.MappedProcedureRoute mapped) {
        String runwayId = pickRunway(primary);
        RunwayDefinition runway = runwayCatalog
                .findRunway(primary.airportIcao(), runwayId)
                .orElseThrow(() -> new IllegalStateException(
                        "Runway not found: " + primary.airportIcao() + "/" + runwayId));
        double heading = mapped.vaConstraint() != null
                ? mapped.vaConstraint().courseTrueDeg()
                : runway.heading();
        return commercial(
                runway.thresholdLatitude(),
                runway.thresholdLongitude(),
                SID_START_ALT_FT,
                InitialClimbGeometry.normalizeHeading(heading),
                SID_START_SPEED_KT);
    }

    private CommercialAircraft spawnStar(
            InstrumentProcedure primary, ProcedureFlightPlanMapper.MappedProcedureRoute mapped) {
        FlightPlanWaypoint first = mapped.flightPlan().getWaypoints().get(0);
        double startAlt = STAR_DEFAULT_START_ALT_FT;
        if (first.getTargetAltitude() != null) {
            startAlt = Math.max(startAlt, first.getTargetAltitude().toDouble());
        }
        double heading = firstTfTrueHeading(primary).orElseGet(() -> bearingToSecond(mapped));
        return commercial(
                first.getPosition().latitude.toDouble(),
                first.getPosition().longitude.toDouble(),
                startAlt,
                InitialClimbGeometry.normalizeHeading(heading),
                STAR_START_SPEED_KT);
    }

    private static Optional<Double> firstTfTrueHeading(InstrumentProcedure primary) {
        for (ProcedureLeg leg : primary.legs()) {
            if (leg.pathDescriptor() == ProcedurePathDescriptor.TF && leg.courseTrueDeg() != null) {
                return Optional.of(leg.courseTrueDeg());
            }
        }
        return Optional.empty();
    }

    private double bearingToSecond(ProcedureFlightPlanMapper.MappedProcedureRoute mapped) {
        List<FlightPlanWaypoint> wps = mapped.flightPlan().getWaypoints();
        if (wps.size() < 2) {
            return 0.0;
        }
        FixPosition a = wps.get(0).getPosition();
        FixPosition b = wps.get(1).getPosition();
        AircraftPosition from = new AircraftPosition(a.latitude, a.longitude, new Altitude(0));
        return PositionUtils.calculateBearingToTarget(
                from, b.latitude.toDouble(), b.longitude.toDouble());
    }

    private static String pickRunway(InstrumentProcedure primary) {
        List<String> runways = primary.applicableRunways();
        if (runways == null || runways.isEmpty()) {
            // Fall back to parsing id suffix (e.g. TIARA3B.RWY16R / VAMOS4.RWY34L_34R).
            String id = primary.id().toUpperCase(Locale.ROOT);
            int idx = id.indexOf(".RWY");
            if (idx >= 0) {
                String rest = id.substring(idx + 4);
                if (rest.contains("_")) {
                    return rest.split("_")[0];
                }
                return rest;
            }
            throw new IllegalStateException("SID has no applicable runway: " + primary.id());
        }
        String raw = runways.get(0);
        if (raw.contains("_")) {
            return raw.split("_")[0];
        }
        return raw;
    }

    private static CommercialAircraft commercial(
            double lat, double lon, double alt, double hdg, double gs) {
        return new CommercialAircraft(
                new Callsign("SWP"),
                new AircraftType("B738"),
                new AircraftPosition(new Latitude(lat), new Longitude(lon), new Altitude(alt)),
                new AircraftVector(new Heading(hdg), new GroundSpeed(gs), new VerticalSpeed(0)),
                "HND",
                "RJTT",
                "HND",
                "RJTT",
                "2026-11-23T00:00:00Z");
    }
}
