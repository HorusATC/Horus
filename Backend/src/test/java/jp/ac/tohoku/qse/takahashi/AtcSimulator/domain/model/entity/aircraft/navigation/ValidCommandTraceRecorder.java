package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.ProcedureFlightPlanMapper;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.AltitudeConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlan;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldInstruction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldTurnDirection;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.WaypointAction;
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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.InstructedVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.AtsRouteFixPositionRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.ProcedureCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

/**
 * Fixed-dt, no-RNG script of <em>valid</em> lateral commands for bit-identical traces.
 * Bug-fix paths (VA Resume, rejected handoff, JOIN FINAL then reassign) are excluded.
 */
final class ValidCommandTraceRecorder {

    private static final Locale LOCALE = Locale.ROOT;
    private static final String HEADER =
            "tick,lat,lon,alt,hdg,track,gs,tas,instrHdg,instrAlt,instrGs,mode,va,climb,goAround,tower,wpIndex,vaCourse,deferred";

    private ValidCommandTraceRecorder() {}

    static String record() {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        CommercialAircraft a = commercial("TRC1", 35.40, 139.70, 5000, 90, 220);
        int tick = 0;
        tick = sample(lines, a, tick);

        // Heading vectoring
        a.setInstructedVector(new InstructedVector(new Heading(120), new Altitude(6000), new GroundSpeed(220)));
        for (int i = 0; i < 30; i++) {
            step(a);
            tick = sample(lines, a, tick);
        }

        // Flight plan
        double[] wp = GeodeticUtils.offsetPointNm(35.40, 139.70, 45.0, 8.0);
        FlightPlan plan = new FlightPlan(
                new Callsign("TRC1"),
                "RJTT",
                "RJTT",
                List.of(
                        new FlightPlanWaypoint(
                                "WP1",
                                new FixPosition(new Latitude(wp[0]), new Longitude(wp[1])),
                                new Altitude(7000),
                                null,
                                AltitudeConstraint.AT_OR_ABOVE,
                                WaypointAction.CONTINUE)),
                new Altitude(7000),
                new GroundSpeed(220));
        a.setFlightPlan(plan);
        for (int i = 0; i < 40; i++) {
            step(a);
            tick = sample(lines, a, tick);
        }

        // Direct To + Resume
        double[] dto = GeodeticUtils.offsetPointNm(
                a.getAircraftPosition().latitude.toDouble(),
                a.getAircraftPosition().longitude.toDouble(),
                10.0,
                5.0);
        a.setDirectTo(new FixPosition(new Latitude(dto[0]), new Longitude(dto[1])), "DTO", true);
        for (int i = 0; i < 40; i++) {
            step(a);
            tick = sample(lines, a, tick);
        }
        a.setResumeNavigation();
        for (int i = 0; i < 20; i++) {
            step(a);
            tick = sample(lines, a, tick);
        }

        // Hold then resume
        FixPosition holdFix = new FixPosition(
                new Latitude(a.getAircraftPosition().latitude.toDouble() + 0.05),
                new Longitude(a.getAircraftPosition().longitude.toDouble()));
        a.setHoldAtFix(
                holdFix,
                new HoldInstruction(
                        "HOLD1", HoldTurnDirection.RIGHT, 360.0, new HoldLeg.TimedMinutes(1.0)));
        for (int i = 0; i < 120; i++) {
            step(a);
            tick = sample(lines, a, tick);
        }
        a.setResumeNavigation();
        for (int i = 0; i < 10; i++) {
            step(a);
            tick = sample(lines, a, tick);
        }

        // DEPART with deferred heading (no rejected handoff)
        CommercialAircraft dep = commercial("TRC2", 35.55, 139.77, 0, 150, 180);
        dep.beginInitialClimb(35.55998611, 139.76906667, 20.5, 149.88, 18.2, 90.0, 5000);
        tick = sample(lines, dep, tick);
        for (int i = 0; i < 80; i++) {
            step(dep);
            tick = sample(lines, dep, tick);
        }

        // VAMOS natural VA release (no Resume)
        CommercialAircraft sid = commercial("TRC3", 35.52, 139.78, 200, 330, 180);
        ProcedureCatalog catalog = new ProcedureCatalog();
        ProcedureFlightPlanMapper mapper =
                new ProcedureFlightPlanMapper(new AtsRouteFixPositionRepository());
        var mapped = mapper.map(
                new Callsign("TRC3"),
                catalog.findById("VAMOS4.RWY34L_34R").orElseThrow(),
                Optional.empty(),
                9000,
                250);
        sid.assignProcedureRoute(mapped.flightPlan(), mapped.vaConstraint());
        tick = sample(lines, sid, tick);
        for (int i = 0; i < 200 && sid.isProcedureVaActive(); i++) {
            step(sid);
            tick = sample(lines, sid, tick);
        }
        for (int i = 0; i < 30; i++) {
            step(sid);
            tick = sample(lines, sid, tick);
        }

        // JOIN FINAL to establishment (no reassignment)
        CommercialAircraft fin = commercial("TRC4", 35.0, 139.0, 3000, 120, 180);
        fin.joinFinalApproachIntercept(
                new FixPosition(new Latitude(35.02), new Longitude(139.04)), "FA", 90.0);
        tick = sample(lines, fin, tick);
        for (int i = 0; i < 200; i++) {
            step(fin);
            tick = sample(lines, fin, tick);
            if ("HEADING".equals(fin.getNavigationMode().name()) && !isFinalPending(fin)) {
                break;
            }
        }
        for (int i = 0; i < 40; i++) {
            step(fin);
            tick = sample(lines, fin, tick);
        }

        // Accepted tower handoff near threshold
        CommercialAircraft twr = commercial("TRC5", 35.0, 139.0, 3000, 90, 180);
        twr.setInstructedVector(
                new InstructedVector(new Heading(90), new Altitude(3000), new GroundSpeed(180)));
        twr.beginTowerHandoff(35.0, 139.08);
        tick = sample(lines, twr, tick);
        for (int i = 0; i < 60; i++) {
            step(twr);
            tick = sample(lines, twr, tick);
        }

        return String.join("\n", lines) + "\n";
    }

    private static int sample(List<String> lines, AircraftBase a, int tick) {
        lines.add(format(tick, a));
        return tick + 1;
    }

    private static void step(AircraftBase a) {
        a.calculateNextAircraftVector(1.0);
        a.calculateNextAircraftPosition(1.0);
    }

    private static String format(int tick, AircraftBase a) {
        Double vaCourse = a.getProcedureVaCourseTrueDeg();
        Double deferred = deferredHeading(a);
        return String.format(
                LOCALE,
                "%d,%.8f,%.8f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s,%d,%d,%d,%d,%d,%s,%s",
                tick,
                a.getAircraftPosition().latitude.toDouble(),
                a.getAircraftPosition().longitude.toDouble(),
                a.getAircraftPosition().altitude.toDouble(),
                a.getAircraftVector().heading.toDouble(),
                a.getAircraftVector().trackDeg,
                a.getAircraftVector().groundSpeed.toDouble(),
                a.getAircraftVector().trueAirspeedKnots,
                a.getInstructedVector().instructedHeading.toDouble(),
                a.getInstructedVector().instructedAltitude.toDouble(),
                a.getInstructedVector().instructedGroundSpeed.toDouble(),
                a.getNavigationMode().name(),
                a.isProcedureVaActive() ? 1 : 0,
                a.isInitialClimbActive() ? 1 : 0,
                a.isGoAroundActive() ? 1 : 0,
                a.isTowerHandoffActive() ? 1 : 0,
                a.getCurrentWaypointIndex(),
                vaCourse == null ? "" : String.format(LOCALE, "%.4f", vaCourse),
                deferred == null ? "" : String.format(LOCALE, "%.4f", deferred));
    }

    private static boolean isFinalPending(AircraftBase a) {
        Object pending = reflect(a, "finalApproachCoursePending");
        if (pending instanceof Boolean b) {
            return b;
        }
        Object lateral = reflect(a, "lateral");
        if (lateral != null) {
            try {
                var m = lateral.getClass().getMethod("approachCoursePending");
                Object result = m.invoke(lateral);
                return result instanceof Boolean b && b;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    private static Double deferredHeading(AircraftBase a) {
        Object value = reflect(a, "deferredInitialHeadingDeg");
        if (value instanceof Double d) {
            return d;
        }
        Object lateral = reflect(a, "lateral");
        if (lateral != null) {
            try {
                var m = lateral.getClass().getMethod("deferredInitialHeadingDeg");
                Object result = m.invoke(lateral);
                return result instanceof Double d ? d : null;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object reflect(AircraftBase a, String field) {
        try {
            var f = AircraftBase.class.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(a);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static CommercialAircraft commercial(
            String cs, double lat, double lon, double alt, double hdg, double gs) {
        return new CommercialAircraft(
                new Callsign(cs),
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
