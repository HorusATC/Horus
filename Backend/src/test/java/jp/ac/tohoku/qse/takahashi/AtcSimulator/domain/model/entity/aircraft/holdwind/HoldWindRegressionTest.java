package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.holdwind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldInstruction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldPhase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldTurnDirection;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldingPatternGeometry;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Heading;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.VerticalSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.MathUtils;

/**
 * Inverted N1 / ② regressions from the external review of #117–#139:
 * holds must stay on the racetrack under wind with WCA on both straight legs.
 */
class HoldWindRegressionTest {

    private static final int THIRTY_MINUTES = 1800;
    private static final double MAX_DIST_FROM_FIX_NM = 9.0;
    private static final double OUTBOUND_TRACK_TOLERANCE_DEG = 2.0;
    private static final double STRAIGHT_LEG_XTK_NM = 0.3;
    /** Skip wind-distorted rollout before judging settled track / xtk. */
    private static final int SETTLE_TICKS = 15;

    record HoldRun(
            double outboundMeanTrack,
            double maxDistFromFixNm,
            int circuits,
            HoldPhase finalPhase,
            double maxStraightLegXtkNm,
            boolean phasesCyclic) {}

    static FixPosition fix(double lat, double lon) {
        return new FixPosition(new Latitude(lat), new Longitude(lon));
    }

    static CommercialAircraft commercial(String cs, double lat, double lon, double alt, double hdg, double gs) {
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

    static void tick(CommercialAircraft a) {
        a.calculateNextAircraftVector(1.0);
        a.calculateNextAircraftPosition(1.0);
    }

    static HoldRun runHold(UniformWind wind, HoldTurnDirection dir, HoldLeg leg, int seconds) {
        FixPosition holdFix = fix(35.0, 139.0);
        CommercialAircraft a = commercial("HOLD" + dir.name().charAt(0), 34.9, 139.0, 5000, 0, 180);
        a.setAmbientWind(wind);
        a.setHoldAtFix(holdFix, new HoldInstruction("ABENO", dir, 360.0, leg));

        double sumSin = 0;
        double sumCos = 0;
        int n = 0;
        boolean firstOutboundActive = false;
        boolean firstOutboundSeen = false;
        int ticksOnStraight = 0;
        double maxDist = 0;
        int circuits = 0;
        HoldPhase last = null;
        double maxEndOfLegXtk = 0;
        double currentLegXtk = Double.NaN;
        List<HoldPhase> phaseOrder = new ArrayList<>();

        for (int s = 0; s < seconds; s++) {
            tick(a);
            HoldPhase p = a.getHoldPhase();
            FixPosition here = fix(
                    a.getAircraftPosition().latitude.toDouble(),
                    a.getAircraftPosition().longitude.toDouble());
            if (p != last) {
                if (last == HoldPhase.OUTBOUND_LEG || last == HoldPhase.INBOUND_LEG) {
                    if (!Double.isNaN(currentLegXtk)) {
                        maxEndOfLegXtk = Math.max(maxEndOfLegXtk, currentLegXtk);
                    }
                }
                if (p == HoldPhase.INBOUND_LEG) {
                    circuits++;
                }
                phaseOrder.add(p);
                ticksOnStraight = 0;
                currentLegXtk = Double.NaN;
                if (p == HoldPhase.OUTBOUND_LEG && !firstOutboundSeen) {
                    firstOutboundActive = true;
                    firstOutboundSeen = true;
                } else if (p != HoldPhase.OUTBOUND_LEG) {
                    firstOutboundActive = false;
                }
                last = p;
            }

            if (p == HoldPhase.OUTBOUND_LEG || p == HoldPhase.INBOUND_LEG) {
                ticksOnStraight++;
                if (ticksOnStraight > SETTLE_TICKS) {
                    double course = p == HoldPhase.OUTBOUND_LEG
                            ? HoldingPatternGeometry.outboundBearingDeg(360.0)
                            : 360.0;
                    FixPosition anchor = p == HoldPhase.OUTBOUND_LEG
                            ? HoldingPatternGeometry.outboundTurnExit(
                                    holdFix, 360.0, dir, a.getHoldFrozenTurnRadiusNm())
                            : holdFix;
                    currentLegXtk = Math.abs(HoldingPatternGeometry.crossTrackNm(anchor, here, course));
                    // Mean track only while nearly on-course (intercept ≤ ~2° at gain 40°/NM).
                    if (firstOutboundActive
                            && p == HoldPhase.OUTBOUND_LEG
                            && currentLegXtk <= 0.05) {
                        double t = Math.toRadians(a.getAircraftVector().trackDeg);
                        sumSin += Math.sin(t);
                        sumCos += Math.cos(t);
                        n++;
                    }
                }
            }
            maxDist = Math.max(maxDist, GeodeticUtils.distanceToFix(a.getAircraftPosition(), holdFix));
        }
        if ((last == HoldPhase.OUTBOUND_LEG || last == HoldPhase.INBOUND_LEG) && !Double.isNaN(currentLegXtk)) {
            maxEndOfLegXtk = Math.max(maxEndOfLegXtk, currentLegXtk);
        }

        double meanTrack = n == 0 ? Double.NaN : MathUtils.normalizeAngle(Math.toDegrees(Math.atan2(sumSin, sumCos)));
        boolean cyclic = phaseOrder.contains(HoldPhase.OUTBOUND_TURN)
                && phaseOrder.contains(HoldPhase.OUTBOUND_LEG)
                && phaseOrder.contains(HoldPhase.INBOUND_TURN)
                && phaseOrder.contains(HoldPhase.INBOUND_LEG);
        return new HoldRun(meanTrack, maxDist, circuits, a.getHoldPhase(), maxEndOfLegXtk, cyclic);
    }

    static Stream<Arguments> holdMatrix() {
        HoldLeg timed = new HoldLeg.TimedMinutes(1.0);
        HoldLeg distance = new HoldLeg.DistanceNm(5.0);
        List<UniformWind> winds = List.of(
                UniformWind.missing(),
                UniformWind.measured(90, 5),
                UniformWind.measured(90, 30),
                UniformWind.measured(270, 30),
                UniformWind.measured(360, 30));
        List<Arguments> args = new ArrayList<>();
        for (HoldTurnDirection dir : HoldTurnDirection.values()) {
            for (HoldLeg leg : List.of(timed, distance)) {
                for (UniformWind wind : winds) {
                    args.add(Arguments.of(wind, dir, leg));
                }
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("holdMatrix")
    @DisplayName("30 min hold stays on racetrack under wind matrix")
    void holdStaysOnRacetrack(UniformWind wind, HoldTurnDirection dir, HoldLeg leg) {
        HoldRun run = runHold(wind, dir, leg, THIRTY_MINUTES);
        assertTrue(run.circuits() >= 5, "circuits=" + run.circuits() + " " + run);
        assertTrue(run.phasesCyclic(), "phases not cyclic: " + run);
        assertTrue(
                run.maxDistFromFixNm() <= MAX_DIST_FROM_FIX_NM,
                "flew away: " + run.maxDistFromFixNm());
        assertTrue(
                run.maxStraightLegXtkNm() <= STRAIGHT_LEG_XTK_NM,
                "straight-leg xtk=" + run.maxStraightLegXtkNm());
        if (!Double.isNaN(run.outboundMeanTrack())) {
            double err = smallestAngleDelta(run.outboundMeanTrack(), 180.0);
            assertTrue(
                    err <= OUTBOUND_TRACK_TOLERANCE_DEG,
                    "outbound mean track " + run.outboundMeanTrack() + " err=" + err);
        }
    }

    @Test
    @DisplayName("② inverted: outbound leg mean track within 2° of course under crosswind")
    void outboundLegHasWcaUnderCrosswind() {
        HoldRun calm = runHold(UniformWind.missing(), HoldTurnDirection.RIGHT, new HoldLeg.TimedMinutes(1.0), 1800);
        HoldRun west = runHold(UniformWind.measured(270, 30), HoldTurnDirection.RIGHT, new HoldLeg.TimedMinutes(1.0), 1800);
        assertEquals(180.0, calm.outboundMeanTrack(), 3.5);
        assertTrue(
                smallestAngleDelta(west.outboundMeanTrack(), 180.0) <= OUTBOUND_TRACK_TOLERANCE_DEG,
                "outbound track drifted: " + west.outboundMeanTrack());
    }

    @Test
    @DisplayName("N1 inverted: wind does not trap INBOUND_LEG or fly away")
    void holdUnderWindKeepsCirclingNearFix() {
        for (HoldRun r : List.of(
                runHold(UniformWind.measured(90, 30), HoldTurnDirection.RIGHT, new HoldLeg.TimedMinutes(1.0), 1800),
                runHold(UniformWind.measured(270, 30), HoldTurnDirection.LEFT, new HoldLeg.TimedMinutes(1.0), 1800),
                runHold(UniformWind.measured(90, 30), HoldTurnDirection.RIGHT, new HoldLeg.DistanceNm(5.0), 1800),
                runHold(UniformWind.measured(90, 5), HoldTurnDirection.RIGHT, new HoldLeg.TimedMinutes(1.0), 1800))) {
            assertTrue(r.circuits() >= 5, "circuits=" + r.circuits());
            assertTrue(r.maxDistFromFixNm() <= MAX_DIST_FROM_FIX_NM, "dist=" + r.maxDistFromFixNm());
        }
        HoldRun calm = runHold(UniformWind.missing(), HoldTurnDirection.RIGHT, new HoldLeg.TimedMinutes(1.0), 1800);
        assertTrue(calm.maxDistFromFixNm() < 7);
        assertTrue(calm.circuits() >= 6);
    }

    @Test
    @DisplayName("combination: assign hold then set wind mid-outbound stays on pattern")
    void assignHoldThenChangeWindMidOutbound() {
        FixPosition holdFix = fix(35.0, 139.0);
        CommercialAircraft a = commercial("WCHG", 34.9, 139.0, 5000, 0, 180);
        a.setHoldAtFix(holdFix, new HoldInstruction("ABENO", HoldTurnDirection.RIGHT, 360.0, new HoldLeg.TimedMinutes(1.0)));
        for (int s = 0; s < 400 && a.getHoldPhase() != HoldPhase.OUTBOUND_LEG; s++) {
            tick(a);
        }
        assertEquals(HoldPhase.OUTBOUND_LEG, a.getHoldPhase());
        a.setAmbientWind(UniformWind.measured(90, 30));
        double maxDist = 0;
        int circuits = 0;
        HoldPhase last = a.getHoldPhase();
        for (int s = 0; s < 1200; s++) {
            tick(a);
            if (a.getHoldPhase() == HoldPhase.INBOUND_LEG && last != HoldPhase.INBOUND_LEG) {
                circuits++;
            }
            last = a.getHoldPhase();
            maxDist = Math.max(maxDist, GeodeticUtils.distanceToFix(a.getAircraftPosition(), holdFix));
        }
        assertTrue(circuits >= 2, "circuits after wind change=" + circuits);
        assertTrue(maxDist <= MAX_DIST_FROM_FIX_NM, "maxDist=" + maxDist);
    }

    private static double smallestAngleDelta(double a, double b) {
        double d = Math.abs(MathUtils.normalizeAngle(a) - MathUtils.normalizeAngle(b));
        return Math.min(d, 360.0 - d);
    }
}
