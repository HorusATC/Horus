package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.SimulationTiming;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.behavior.FixedWingFlightBehavior;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.characteristics.AircraftCharacteristics;
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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

@DisplayName("HoldingPatternGeometry")
class HoldingPatternGeometryTest {

    private static final double DISTANCE_TOLERANCE_NM = 0.05;
    private static final double BEARING_TOLERANCE_DEG = 1.0;
    private static final double HEADING_TOLERANCE_DEG = 2.0;

    @Test
    @DisplayName("inbound 090°, 180 kt, 1.00 min → outbound 3.00 NM / bearing 270°")
    void timedLeg_oneMinuteAt180kt_isThreeNmOutboundWest() {
        double distanceNm = HoldingPatternGeometry.outboundDistanceNm(new HoldLeg.TimedMinutes(1.0), 180.0);
        assertEquals(3.00, distanceNm, DISTANCE_TOLERANCE_NM);
        assertEquals(270.0, HoldingPatternGeometry.outboundBearingDeg(90.0), BEARING_TOLERANCE_DEG);
    }

    @Test
    @DisplayName("180 kt / 3°/s → r = 3/π NM")
    void turnRadius_180ktStandardRate_isThreeOverPiNm() {
        double rate = HoldingPatternGeometry.holdTurnRateDegPerSec(180.0);
        assertEquals(3.0, rate, 1e-9);
        double radius = HoldingPatternGeometry.turnRadiusNm(180.0, rate);
        assertEquals(3.0 / Math.PI, radius, DISTANCE_TOLERANCE_NM);
    }

    @Test
    @DisplayName("250 kt selects bank-30° limit below 3°/s")
    void turnRate_250kt_isBankLimited() {
        double rate = HoldingPatternGeometry.holdTurnRateDegPerSec(250.0);
        double speedMps = 250.0 * HoldingPatternGeometry.KT_TO_M_PER_S;
        double expectedBank = Math.toDegrees(
                HoldingPatternGeometry.GRAVITY_M_PER_S2
                        * Math.tan(Math.toRadians(30.0))
                        / speedMps);
        assertEquals(expectedBank, rate, 0.01);
        assertTrue(rate < 3.0, "250 kt bank-30 rate must be below standard rate, was " + rate);
        double radius = HoldingPatternGeometry.turnRadiusNm(250.0, rate);
        assertEquals(250.0 / (20.0 * Math.PI * rate), radius, 1e-9);
    }

    @Test
    @DisplayName("RIGHT 090° / 1 min / 180 kt: turn exit at 180° / 2r")
    void rightTurnExit_inbound090_isSouthAtDiameter() {
        FixPosition fix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        double radius = HoldingPatternGeometry.turnRadiusNm(180.0);
        FixPosition exit = HoldingPatternGeometry.outboundTurnExit(
                fix, 90.0, HoldTurnDirection.RIGHT, radius);
        double bearing = HoldingPatternGeometry.planarBearingDeg(fix, exit);
        double distance = HoldingPatternGeometry.planarDistanceNm(fix, exit);
        assertEquals(180.0, bearing, BEARING_TOLERANCE_DEG);
        assertEquals(2.0 * radius, distance, DISTANCE_TOLERANCE_NM);
    }

    @Test
    @DisplayName("LEFT 090° is the north mirror of RIGHT at the same radius")
    void leftTurnExit_isMirrorOfRight() {
        FixPosition fix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        double radius = HoldingPatternGeometry.turnRadiusNm(180.0);
        FixPosition right = HoldingPatternGeometry.outboundTurnExit(
                fix, 90.0, HoldTurnDirection.RIGHT, radius);
        FixPosition left = HoldingPatternGeometry.outboundTurnExit(
                fix, 90.0, HoldTurnDirection.LEFT, radius);
        assertEquals(180.0, HoldingPatternGeometry.planarBearingDeg(fix, right), BEARING_TOLERANCE_DEG);
        assertEquals(0.0, HoldingPatternGeometry.planarBearingDeg(fix, left), BEARING_TOLERANCE_DEG);
        assertEquals(
                HoldingPatternGeometry.planarDistanceNm(fix, right),
                HoldingPatternGeometry.planarDistanceNm(fix, left),
                DISTANCE_TOLERANCE_NM);
        assertNotEquals(right.latitude.toDouble(), left.latitude.toDouble(), 1e-6);
    }

    @Test
    @DisplayName("inbound 209°, 1.5 min, 200 kt → outbound 5.00 NM")
    void timedLeg_oneAndHalfMinutesAt200kt_isFiveNm() {
        double distanceNm = HoldingPatternGeometry.outboundDistanceNm(new HoldLeg.TimedMinutes(1.5), 200.0);
        assertEquals(5.00, distanceNm, DISTANCE_TOLERANCE_NM);
        assertEquals(
                GeodeticUtils.normalizeAngle(209.0 + 180.0),
                HoldingPatternGeometry.outboundBearingDeg(209.0),
                BEARING_TOLERANCE_DEG);
    }

    @Test
    @DisplayName("7.00 NM leg ignores ground speed")
    void distanceLeg_sevenNm_ignoresSpeed() {
        assertEquals(7.00, HoldingPatternGeometry.outboundDistanceNm(new HoldLeg.DistanceNm(7.0), 90.0),
                DISTANCE_TOLERANCE_NM);
        assertEquals(7.00, HoldingPatternGeometry.outboundDistanceNm(new HoldLeg.DistanceNm(7.0), 450.0),
                DISTANCE_TOLERANCE_NM);
    }

    @Test
    @DisplayName("pattern is closed and includes fix and outbound end")
    void pattern_isClosedAndMatchesGeometry() {
        FixPosition fix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        double radius = HoldingPatternGeometry.turnRadiusNm(180.0);
        double legNm = 3.0;
        List<FixPosition> pattern = HoldingPatternGeometry.buildPattern(
                fix, 90.0, HoldTurnDirection.RIGHT, legNm, radius);
        assertTrue(pattern.size() >= 4 + 2 * (HoldingPatternGeometry.ARC_SEGMENTS - 1));
        FixPosition first = pattern.get(0);
        FixPosition last = pattern.get(pattern.size() - 1);
        assertEquals(first.latitude.toDouble(), last.latitude.toDouble(), 1e-9);
        assertEquals(first.longitude.toDouble(), last.longitude.toDouble(), 1e-9);
        FixPosition outboundEnd = HoldingPatternGeometry.outboundLegEnd(
                fix, 90.0, HoldTurnDirection.RIGHT, new HoldLeg.DistanceNm(legNm), 180.0, radius);
        boolean sawOutboundEnd = pattern.stream().anyMatch(p ->
                HoldingPatternGeometry.planarDistanceNm(p, outboundEnd) < DISTANCE_TOLERANCE_NM);
        assertTrue(sawOutboundEnd, "pattern must include outbound leg end");
    }

    @Test
    @DisplayName("omitted inboundCourseDeg keeps fix-name hash inbound (compatibility)")
    void omittedInbound_usesDeterministicHash() {
        FixPosition fix = new FixPosition(new Latitude(35.1), new Longitude(139.0));
        double first = HoldingPatternGeometry.resolveInboundCourseDeg(null, "ABENO", fix);
        double second = HoldingPatternGeometry.resolveInboundCourseDeg(null, "ABENO", fix);
        assertEquals(first, second, 0.0);
        assertEquals(
                HoldingPatternGeometry.deterministicInboundBearing(fix, "ABENO"),
                first,
                0.0);
        assertNotEquals(
                first,
                HoldingPatternGeometry.resolveInboundCourseDeg(90.0, "ABENO", fix),
                0.0);
    }

    @Test
    @DisplayName("timed leg rejects ground speed below 1 kt")
    void timedLeg_rejectsSubKtGroundSpeed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HoldingPatternGeometry.outboundDistanceNm(new HoldLeg.TimedMinutes(1.0), 0.5));
    }

    @Test
    @DisplayName("LEFT vs RIGHT force opposite turn signs after entry (mirror outbound ends)")
    void leftAndRight_forceOppositeTurnSigns() {
        FixPosition holdFix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        AircraftBase right = createAircraftAt(holdFix, 180.0, 3.0);
        AircraftBase left = createAircraftAt(holdFix, 180.0, 3.0);

        right.setHoldAtFix(holdFix, new HoldInstruction(
                "ABENO", HoldTurnDirection.RIGHT, 90.0, new HoldLeg.TimedMinutes(1.0)));
        left.setHoldAtFix(holdFix, new HoldInstruction(
                "ABENO", HoldTurnDirection.LEFT, 90.0, new HoldLeg.TimedMinutes(1.0)));

        placeOnFixAndStartOutbound(right, holdFix, 90.0);
        placeOnFixAndStartOutbound(left, holdFix, 90.0);

        right.calculateNextAircraftVector(SimulationTiming.SIM_DELTA_SECONDS);
        left.calculateNextAircraftVector(SimulationTiming.SIM_DELTA_SECONDS);

        double rightDelta = signedHeadingDelta(90.0, right.getAircraftVector().heading.toDouble());
        double leftDelta = signedHeadingDelta(90.0, left.getAircraftVector().heading.toDouble());
        assertTrue(rightDelta > 0.0, "RIGHT hold should turn clockwise, was " + rightDelta);
        assertTrue(leftDelta < 0.0, "LEFT hold should turn counter-clockwise, was " + leftDelta);

        double rightCross = HoldingPatternGeometry.crossTrackNm(
                holdFix, right.getHoldingOutboundTarget(), 90.0);
        double leftCross = HoldingPatternGeometry.crossTrackNm(
                holdFix, left.getHoldingOutboundTarget(), 90.0);
        assertTrue(rightCross > 0.5, "RIGHT outbound end must be right of inbound axis");
        assertTrue(leftCross < -0.5, "LEFT outbound end must be left of inbound axis");
        assertEquals(Math.abs(rightCross), Math.abs(leftCross), DISTANCE_TOLERANCE_NM);
    }

    @Test
    @DisplayName("fighter maxTurnRate 9°/s is capped to hold formula while holding")
    void fighterTurnRate_cappedDuringHold() {
        FixPosition holdFix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        AircraftBase fighter = createAircraftAt(holdFix, 180.0, 9.0);
        fighter.setHoldAtFix(holdFix, new HoldInstruction(
                "ABENO", HoldTurnDirection.RIGHT, 90.0, new HoldLeg.TimedMinutes(1.0)));
        placeOnFixAndStartOutbound(fighter, holdFix, 90.0);
        assertEquals(3.0, fighter.getHoldFrozenTurnRateDegPerSec(), 1e-9);
        double before = fighter.getAircraftVector().heading.toDouble();
        fighter.calculateNextAircraftVector(SimulationTiming.SIM_DELTA_SECONDS);
        double after = fighter.getAircraftVector().heading.toDouble();
        double delta = signedHeadingDelta(before, after);
        assertEquals(3.0, delta, 0.05);
    }

    @Test
    @DisplayName("after fix: 180° standard-rate turn then outbound heading; not axial shuttle")
    void simulation_racetrackNotAxialShuttle() {
        FixPosition holdFix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        AircraftBase aircraft = createAircraftAt(holdFix, 180.0, 3.0);
        aircraft.setHoldAtFix(holdFix, new HoldInstruction(
                "ABENO", HoldTurnDirection.RIGHT, 90.0, new HoldLeg.TimedMinutes(1.0)));
        placeOnFixAndStartOutbound(aircraft, holdFix, 90.0);

        double radius = aircraft.getHoldFrozenTurnRadiusNm();
        boolean sawOutboundHeading = false;
        boolean sawOffsetSouth = false;
        for (int i = 0; i < 70; i++) {
            aircraft.calculateNextAircraftVector(SimulationTiming.SIM_DELTA_SECONDS);
            aircraft.calculateNextAircraftPosition(SimulationTiming.SIM_DELTA_SECONDS);
            double heading = aircraft.getAircraftVector().heading.toDouble();
            FixPosition here = new FixPosition(
                    aircraft.getAircraftPosition().latitude,
                    aircraft.getAircraftPosition().longitude);
            double cross = HoldingPatternGeometry.crossTrackNm(holdFix, here, 90.0);
            if (Math.abs(GeodeticUtils.normalizeAngle(heading - 270.0)) < HEADING_TOLERANCE_DEG
                    || Math.abs(GeodeticUtils.normalizeAngle(heading - 270.0) - 360.0) < HEADING_TOLERANCE_DEG) {
                sawOutboundHeading = true;
            }
            if (cross > radius) {
                sawOffsetSouth = true;
            }
        }
        assertTrue(sawOutboundHeading, "should roll out on outbound heading 270°");
        assertTrue(sawOffsetSouth, "path must leave the inbound axis (racetrack offset)");
        assertEquals(HoldPhase.OUTBOUND_LEG, aircraft.getHoldPhase());

        FixPosition axialOnly = HoldingPatternGeometry.outboundTarget(
                holdFix, 90.0, new HoldLeg.TimedMinutes(1.0), 180.0);
        FixPosition racetrackEnd = aircraft.getHoldingOutboundTarget();
        assertTrue(
                HoldingPatternGeometry.planarDistanceNm(axialOnly, racetrackEnd) > DISTANCE_TOLERANCE_NM,
                "outbound end must not be the old axial shuttle target");
    }

    @Test
    @DisplayName("DIRECT entry at fix heading 090° starts outbound turn immediately")
    void simulation_directEntry_startsOutboundTurn() {
        FixPosition holdFix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        AircraftBase aircraft = createAircraftAt(holdFix, 180.0, 3.0);
        aircraft.setHoldAtFix(holdFix, new HoldInstruction(
                "ABENO", HoldTurnDirection.RIGHT, 90.0, new HoldLeg.TimedMinutes(1.0)));
        placeOnFixAndBeginEntry(aircraft, holdFix, 90.0);
        assertEquals(HoldEntryProcedure.DIRECT, aircraft.getHoldEntryProcedure());
        assertEquals(HoldPhase.OUTBOUND_TURN, aircraft.getHoldPhase());
    }

    @Test
    @DisplayName("TEARDROP entry flies 240° for 1.00 min then intercepts inbound before racetrack")
    void simulation_teardropEntry_offsetThenInbound() {
        FixPosition holdFix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        AircraftBase aircraft = createAircraftAt(holdFix, 180.0, 3.0);
        aircraft.setHoldAtFix(holdFix, new HoldInstruction(
                "ABENO", HoldTurnDirection.RIGHT, 90.0, new HoldLeg.TimedMinutes(1.0)));
        placeOnFixAndBeginEntry(aircraft, holdFix, 240.0);
        assertEquals(HoldEntryProcedure.TEARDROP, aircraft.getHoldEntryProcedure());
        assertEquals(HoldPhase.ENTRY_TEARDROP_TURN, aircraft.getHoldPhase());

        boolean sawTeardropHeading = false;
        boolean sawTeardropLeg = false;
        boolean sawInboundLeg = false;
        boolean sawRacetrackOutbound = false;
        for (int i = 0; i < 500; i++) {
            aircraft.calculateNextAircraftVector(SimulationTiming.SIM_DELTA_SECONDS);
            aircraft.calculateNextAircraftPosition(SimulationTiming.SIM_DELTA_SECONDS);
            // Re-arm waypoint pass when flying inbound to the fix after entry.
            if (aircraft.getHoldPhase() == HoldPhase.INBOUND_LEG) {
                sawInboundLeg = true;
                maybePassFix(aircraft, holdFix);
            }
            double heading = aircraft.getAircraftVector().heading.toDouble();
            if (aircraft.getHoldPhase() == HoldPhase.ENTRY_TEARDROP_LEG) {
                sawTeardropLeg = true;
                if (smallestHeadingDelta(heading, 240.0) <= HEADING_TOLERANCE_DEG) {
                    sawTeardropHeading = true;
                    FixPosition here = new FixPosition(
                            aircraft.getAircraftPosition().latitude,
                            aircraft.getAircraftPosition().longitude);
                    double cross = HoldingPatternGeometry.crossTrackNm(holdFix, here, 90.0);
                    assertTrue(cross > 0.0, "teardrop path must be on the holding (right) side");
                }
            }
            if (aircraft.getHoldPhase() == HoldPhase.OUTBOUND_TURN && sawInboundLeg) {
                sawRacetrackOutbound = true;
                break;
            }
        }
        assertTrue(sawTeardropHeading, "should fly teardrop heading 240°");
        assertTrue(sawTeardropLeg, "should enter teardrop leg");
        assertTrue(sawInboundLeg, "should intercept inbound and fly to fix");
        assertTrue(sawRacetrackOutbound, "second fix passage should start racetrack outbound turn");
    }

    @Test
    @DisplayName("PARALLEL entry flies non-holding outbound then >180° back to inbound")
    void simulation_parallelEntry_nonHoldingThenReturn() {
        FixPosition holdFix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        AircraftBase aircraft = createAircraftAt(holdFix, 180.0, 3.0);
        aircraft.setHoldAtFix(holdFix, new HoldInstruction(
                "ABENO", HoldTurnDirection.RIGHT, 90.0, new HoldLeg.TimedMinutes(1.0)));
        // Arrive from the north (non-holding) so the first left turn to outbound builds offset.
        placeOnFixAndBeginEntry(aircraft, holdFix, 10.0);
        assertEquals(HoldEntryProcedure.PARALLEL, aircraft.getHoldEntryProcedure());
        assertEquals(HoldPhase.ENTRY_PARALLEL_OUTBOUND_TURN, aircraft.getHoldPhase());

        boolean sawNonHoldingOutbound = false;
        boolean sawParallelLeg = false;
        boolean sawInboundLeg = false;
        boolean sawRacetrackOutbound = false;
        for (int i = 0; i < 700; i++) {
            aircraft.calculateNextAircraftVector(SimulationTiming.SIM_DELTA_SECONDS);
            aircraft.calculateNextAircraftPosition(SimulationTiming.SIM_DELTA_SECONDS);
            if (aircraft.getHoldPhase() == HoldPhase.INBOUND_LEG) {
                sawInboundLeg = true;
                maybePassFix(aircraft, holdFix);
            }
            if (aircraft.getHoldPhase() == HoldPhase.ENTRY_PARALLEL_LEG) {
                sawParallelLeg = true;
                FixPosition here = new FixPosition(
                        aircraft.getAircraftPosition().latitude,
                        aircraft.getAircraftPosition().longitude);
                double cross = HoldingPatternGeometry.crossTrackNm(holdFix, here, 90.0);
                if (cross < -0.2) {
                    sawNonHoldingOutbound = true;
                }
            }
            if (aircraft.getHoldPhase() == HoldPhase.OUTBOUND_TURN && sawInboundLeg) {
                sawRacetrackOutbound = true;
                break;
            }
        }
        assertTrue(sawParallelLeg, "should fly parallel outbound leg");
        assertTrue(sawNonHoldingOutbound, "parallel outbound must be on the non-holding side");
        assertTrue(sawInboundLeg, "should return to inbound after >180° turn");
        assertTrue(sawRacetrackOutbound, "second fix passage should start racetrack");
    }

    @Test
    @DisplayName("RIGHT I=090° sector: DIRECT/TEARDROP/PARALLEL and 70° edges")
    void entrySectors_rightInbound090() {
        assertEquals(HoldEntryProcedure.DIRECT,
                HoldingPatternGeometry.classifyEntryProcedure(90.0, 90.0, HoldTurnDirection.RIGHT));
        // Half-open [270, 020): 019° is PARALLEL; 020° starts DIRECT.
        assertEquals(HoldEntryProcedure.PARALLEL,
                HoldingPatternGeometry.classifyEntryProcedure(19.0, 90.0, HoldTurnDirection.RIGHT));
        assertEquals(HoldEntryProcedure.DIRECT,
                HoldingPatternGeometry.classifyEntryProcedure(20.0, 90.0, HoldTurnDirection.RIGHT));
        assertEquals(HoldEntryProcedure.DIRECT,
                HoldingPatternGeometry.classifyEntryProcedure(199.0, 90.0, HoldTurnDirection.RIGHT));
        assertEquals(HoldEntryProcedure.TEARDROP,
                HoldingPatternGeometry.classifyEntryProcedure(200.0, 90.0, HoldTurnDirection.RIGHT));
        assertEquals(HoldEntryProcedure.TEARDROP,
                HoldingPatternGeometry.classifyEntryProcedure(240.0, 90.0, HoldTurnDirection.RIGHT));
        assertEquals(HoldEntryProcedure.PARALLEL,
                HoldingPatternGeometry.classifyEntryProcedure(270.0, 90.0, HoldTurnDirection.RIGHT));
        assertEquals(HoldEntryProcedure.PARALLEL,
                HoldingPatternGeometry.classifyEntryProcedure(0.0, 90.0, HoldTurnDirection.RIGHT));
        assertEquals(HoldEntryProcedure.PARALLEL,
                HoldingPatternGeometry.classifyEntryProcedure(10.0, 90.0, HoldTurnDirection.RIGHT));
    }

    @Test
    @DisplayName("LEFT I=090° sector is the mirror of RIGHT across the inbound axis")
    void entrySectors_leftInbound090_isMirror() {
        assertEquals(HoldEntryProcedure.DIRECT,
                HoldingPatternGeometry.classifyEntryProcedure(90.0, 90.0, HoldTurnDirection.LEFT));
        assertEquals(HoldEntryProcedure.TEARDROP,
                HoldingPatternGeometry.classifyEntryProcedure(340.0, 90.0, HoldTurnDirection.LEFT));
        assertEquals(HoldEntryProcedure.TEARDROP,
                HoldingPatternGeometry.classifyEntryProcedure(300.0, 90.0, HoldTurnDirection.LEFT));
        assertEquals(HoldEntryProcedure.PARALLEL,
                HoldingPatternGeometry.classifyEntryProcedure(270.0, 90.0, HoldTurnDirection.LEFT));
        assertEquals(HoldEntryProcedure.PARALLEL,
                HoldingPatternGeometry.classifyEntryProcedure(200.0, 90.0, HoldTurnDirection.LEFT));
        assertEquals(HoldEntryProcedure.DIRECT,
                HoldingPatternGeometry.classifyEntryProcedure(160.0, 90.0, HoldTurnDirection.LEFT));
        assertEquals(HoldEntryProcedure.DIRECT,
                HoldingPatternGeometry.classifyEntryProcedure(20.0, 90.0, HoldTurnDirection.LEFT));
        // Mirror map: RIGHT 200 TEARDROP → LEFT 340 TEARDROP; RIGHT 270 PARALLEL → LEFT 270 PARALLEL
        assertEquals(
                HoldingPatternGeometry.classifyEntryProcedure(200.0, 90.0, HoldTurnDirection.RIGHT),
                HoldingPatternGeometry.classifyEntryProcedure(340.0, 90.0, HoldTurnDirection.LEFT));
        assertEquals(
                HoldingPatternGeometry.classifyEntryProcedure(270.0, 90.0, HoldTurnDirection.RIGHT),
                HoldingPatternGeometry.classifyEntryProcedure(270.0, 90.0, HoldTurnDirection.LEFT));
    }

    @Test
    @DisplayName("teardrop heading RIGHT I=090 → 240°; LEFT → 300°")
    void teardropHeading_offsetThirtyFromOutbound() {
        assertEquals(240.0,
                HoldingPatternGeometry.teardropHeadingDeg(90.0, HoldTurnDirection.RIGHT),
                BEARING_TOLERANCE_DEG);
        assertEquals(300.0,
                HoldingPatternGeometry.teardropHeadingDeg(90.0, HoldTurnDirection.LEFT),
                BEARING_TOLERANCE_DEG);
    }

    /**
     * Proves the AIM 70° holding-side line. Mutating ENTRY_TEARDROP_SECTOR_DEG 70→110
     * would reclassify heading 200° (RIGHT I=090) away from TEARDROP.
     */
    @Test
    @DisplayName("mutation guard: teardrop sector must be 70° from outbound (not 110°)")
    void mutationGuard_teardropSectorIsSeventyNotOneTen() {
        assertEquals(70.0, HoldingPatternGeometry.ENTRY_TEARDROP_SECTOR_DEG, 0.0);
        assertEquals(110.0, HoldingPatternGeometry.ENTRY_PARALLEL_SECTOR_DEG, 0.0);
        assertEquals(HoldEntryProcedure.TEARDROP,
                HoldingPatternGeometry.classifyEntryProcedure(200.0, 90.0, HoldTurnDirection.RIGHT));
        // If the teardrop arc were 110° from outbound, 200° (delta=-70) would still be inside,
        // but the direct/teardrop edge at 200° would move to 160° — 199° must stay DIRECT.
        assertEquals(HoldEntryProcedure.DIRECT,
                HoldingPatternGeometry.classifyEntryProcedure(199.0, 90.0, HoldTurnDirection.RIGHT));
        assertNotEquals(HoldEntryProcedure.TEARDROP,
                HoldingPatternGeometry.classifyEntryProcedure(160.0, 90.0, HoldTurnDirection.RIGHT));
    }

    /**
     * Proves r = gs / (20 π ω). Mutating the divisor 20→10 makes
     * {@link #turnRadius_180ktStandardRate_isThreeOverPiNm} fail against 3/π.
     */
    @Test
    @DisplayName("mutation guard: turn radius must use 20π in the denominator")
    void mutationGuard_turnRadiusUsesTwentyPi() {
        double groundSpeedKt = 180.0;
        double omega = 3.0;
        double product = HoldingPatternGeometry.turnRadiusNm(groundSpeedKt, omega);
        double correct = groundSpeedKt / (20.0 * Math.PI * omega);
        double mutatedTen = groundSpeedKt / (10.0 * Math.PI * omega);
        assertEquals(correct, product, 1e-9);
        assertNotEquals(mutatedTen, product, 1e-9);
        assertEquals(3.0 / Math.PI, product, DISTANCE_TOLERANCE_NM);
    }

    /**
     * Proves the timed-leg formula is {@code groundSpeedKt * (minutes / 60)}.
     * Mutating the product divisor to 30 makes the 1.5 min / 200 kt case fail.
     */
    @Test
    @DisplayName("mutation guard: timed distance must use minutes/60 (not /30)")
    void mutationGuard_timedFormulaUsesMinutesOverSixty() {
        double minutes = 1.5;
        double groundSpeedKt = 200.0;
        double product = HoldingPatternGeometry.outboundDistanceNm(new HoldLeg.TimedMinutes(minutes), groundSpeedKt);
        double correct = groundSpeedKt * (minutes / 60.0);
        double mutatedDivisorThirty = groundSpeedKt * (minutes / 30.0);
        assertEquals(correct, product, 1e-9);
        assertNotEquals(mutatedDivisorThirty, product, 1e-9);
        assertEquals(5.0, product, DISTANCE_TOLERANCE_NM);
    }

    private static double signedHeadingDelta(double from, double to) {
        double delta = GeodeticUtils.normalizeAngle(to - from);
        if (delta > 180.0) {
            delta -= 360.0;
        }
        return delta;
    }

    private static AircraftBase createAircraftAt(FixPosition fix, double speed, double maxTurnRate) {
        var characteristics = new AircraftCharacteristics(3.0, maxTurnRate, 1640.0, 500.0, 140.0, 42000.0, 0.0,
                AircraftCharacteristics.AircraftCategory.COMMERCIAL_PASSENGER);
        return new AircraftBase(
                new Callsign("HOLD01"),
                new AircraftType("B738"),
                new AircraftPosition(fix.latitude, fix.longitude, new Altitude(5000)),
                new AircraftVector(new Heading(90), new GroundSpeed(speed), new VerticalSpeed(0)),
                new FixedWingFlightBehavior(),
                characteristics
        ) {
        };
    }

    private static void placeOnFixAndStartOutbound(AircraftBase aircraft, FixPosition fix, double inboundHeading) {
        aircraft.setAircraftPosition(new AircraftPosition(
                fix.latitude, fix.longitude, aircraft.getAircraftPosition().altitude));
        aircraft.setAircraftVector(new AircraftVector(
                new Heading(inboundHeading),
                aircraft.getAircraftVector().groundSpeed,
                aircraft.getAircraftVector().verticalSpeed));
        // Simulate fix passage: calculateNext alone won't trigger pass without moving-away.
        try {
            var method = AircraftBase.class.getDeclaredMethod("beginOutboundFromFix");
            method.setAccessible(true);
            method.invoke(aircraft);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void placeOnFixAndBeginEntry(AircraftBase aircraft, FixPosition fix, double arrivalHeading) {
        aircraft.setAircraftPosition(new AircraftPosition(
                fix.latitude, fix.longitude, aircraft.getAircraftPosition().altitude));
        aircraft.setAircraftVector(new AircraftVector(
                new Heading(arrivalHeading),
                aircraft.getAircraftVector().groundSpeed,
                aircraft.getAircraftVector().verticalSpeed));
        try {
            var method = AircraftBase.class.getDeclaredMethod("beginEntryOrRacetrackFromFix");
            method.setAccessible(true);
            method.invoke(aircraft);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void maybePassFix(AircraftBase aircraft, FixPosition fix) {
        FixPosition here = new FixPosition(
                aircraft.getAircraftPosition().latitude,
                aircraft.getAircraftPosition().longitude);
        if (HoldingPatternGeometry.planarDistanceNm(here, fix) < 0.25) {
            try {
                var method = AircraftBase.class.getDeclaredMethod("onHoldingTargetReached");
                method.setAccessible(true);
                method.invoke(aircraft);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static double smallestHeadingDelta(double a, double b) {
        double delta = Math.abs(GeodeticUtils.normalizeAngle(a) - GeodeticUtils.normalizeAngle(b));
        return Math.min(delta, 360.0 - delta);
    }
}
