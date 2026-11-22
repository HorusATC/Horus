package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("inbound 090°, 180 kt, 1.00 min → outbound 3.00 NM / bearing 270°")
    void timedLeg_oneMinuteAt180kt_isThreeNmOutboundWest() {
        double distanceNm = HoldingPatternGeometry.outboundDistanceNm(new HoldLeg.TimedMinutes(1.0), 180.0);
        assertEquals(3.00, distanceNm, DISTANCE_TOLERANCE_NM);
        assertEquals(270.0, HoldingPatternGeometry.outboundBearingDeg(90.0), BEARING_TOLERANCE_DEG);
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
    @DisplayName("LEFT and RIGHT share the same outbound point for the same inbound and leg")
    void leftAndRight_sameOutboundTarget() {
        FixPosition fix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        HoldLeg leg = new HoldLeg.DistanceNm(4.0);
        FixPosition right = HoldingPatternGeometry.outboundTarget(fix, 90.0, leg, 250.0);
        FixPosition left = HoldingPatternGeometry.outboundTarget(fix, 90.0, leg, 250.0);
        assertEquals(right.latitude.toDouble(), left.latitude.toDouble(), 1e-9);
        assertEquals(right.longitude.toDouble(), left.longitude.toDouble(), 1e-9);
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
    @DisplayName("LEFT vs RIGHT force opposite turn signs after entry")
    void leftAndRight_forceOppositeTurnSigns() throws Exception {
        FixPosition holdFix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
        AircraftBase right = createAircraftAt(holdFix, 250.0);
        AircraftBase left = createAircraftAt(holdFix, 250.0);

        right.setHoldAtFix(holdFix, new HoldInstruction(
                "ABENO", HoldTurnDirection.RIGHT, 90.0, new HoldLeg.DistanceNm(4.0)));
        left.setHoldAtFix(holdFix, new HoldInstruction(
                "ABENO", HoldTurnDirection.LEFT, 90.0, new HoldLeg.DistanceNm(4.0)));

        markHoldingEntryCompleted(right);
        markHoldingEntryCompleted(left);

        // Heading north while outbound target is west (270°) — shortest turn is left,
        // so RIGHT must force clockwise and LEFT must force counter-clockwise.
        setHeading(right, 0.0);
        setHeading(left, 0.0);

        right.calculateNextAircraftVector(SimulationTiming.SIM_DELTA_SECONDS);
        left.calculateNextAircraftVector(SimulationTiming.SIM_DELTA_SECONDS);

        double rightDelta = signedHeadingDelta(0.0, right.getAircraftVector().heading.toDouble());
        double leftDelta = signedHeadingDelta(0.0, left.getAircraftVector().heading.toDouble());
        assertTrue(rightDelta > 0.0, "RIGHT hold should turn clockwise (positive delta), was " + rightDelta);
        assertTrue(leftDelta < 0.0, "LEFT hold should turn counter-clockwise (negative delta), was " + leftDelta);
        assertEquals(
                right.getHoldingOutboundTarget().latitude.toDouble(),
                left.getHoldingOutboundTarget().latitude.toDouble(),
                1e-9);
        assertEquals(
                right.getHoldingOutboundTarget().longitude.toDouble(),
                left.getHoldingOutboundTarget().longitude.toDouble(),
                1e-9);
    }

    /**
     * Proves the timed-leg formula is {@code groundSpeedKt * (minutes / 60)}.
     * Mutating the product divisor to 30 makes {@link #timedLeg_oneAndHalfMinutesAt200kt_isFiveNm} fail.
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

    private static AircraftBase createAircraftAt(FixPosition fix, double speed) {
        var characteristics = new AircraftCharacteristics(3.0, 3.0, 1640.0, 500.0, 140.0, 42000.0, 0.0,
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

    private static void markHoldingEntryCompleted(AircraftBase aircraft) throws Exception {
        var entry = AircraftBase.class.getDeclaredField("holdingEntryCompleted");
        entry.setAccessible(true);
        entry.setBoolean(aircraft, true);
        var toOutbound = AircraftBase.class.getDeclaredField("holdingToOutboundLeg");
        toOutbound.setAccessible(true);
        toOutbound.setBoolean(aircraft, true);
    }

    private static void setHeading(AircraftBase aircraft, double headingDeg) {
        var v = aircraft.getAircraftVector();
        aircraft.setAircraftVector(new AircraftVector(
                new Heading(headingDeg), v.groundSpeed, v.verticalSpeed));
    }
}
