package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.SimulationTiming;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.behavior.FixedWingFlightBehavior;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.characteristics.AircraftCharacteristics;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldingPatternGeometry;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atmosphere.WindTriangle;
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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.PositionUtils;

@DisplayName("JOIN FINAL wind correction")
class FinalApproachCrosswindWcaTest {

    private static final double APPROACH_COURSE_DEG = 149.88;
    private static final double SIM_DELTA = SimulationTiming.SIM_DELTA_SECONDS;
    private static final int FINAL_TICKS = 240;
    private static final UniformWind CROSSWIND = UniformWind.measured(250, 25);
    private static final int WARMUP_TICKS = 150;

    @Test
  @DisplayName("crosswind: track on final stays closer with WCA than flying course heading alone")
  void trackOnFinalStaysCloserWithWcaThanWithout() throws Exception {
    AircraftBase withWca = aircraftEstablishedOnFinal(120.0);
    assertEquals(NavigationMode.HEADING, withWca.getNavigationMode());
    warmUpOnFinal(withWca, WARMUP_TICKS);

    FixPosition courseAnchor = new FixPosition(new Latitude(35.0), new Longitude(139.0));
    double maxCrossWithWca = maxAbsCrossTrack(withWca, courseAnchor, FINAL_TICKS);

    AircraftPosition start = withWca.getAircraftPosition();
    double tas = withWca.getAircraftVector().trueAirspeedKnots;
    double maxCrossWithoutWca =
        maxAbsCrossTrackNoWca(courseAnchor, start, APPROACH_COURSE_DEG, tas, CROSSWIND, FINAL_TICKS);

    assertTrue(
        maxCrossWithWca < maxCrossWithoutWca - 0.05,
        "WCA should reduce cross-track; with="
            + maxCrossWithWca
            + " NM, without="
            + maxCrossWithoutWca
            + " NM");
    assertTrue(
        maxCrossWithWca < maxCrossWithoutWca * 0.55,
        "WCA should keep materially less drift: with="
            + maxCrossWithWca
            + " without="
            + maxCrossWithoutWca);
  }

  @Test
  @DisplayName("crosswind on final: non-zero WCA and track follows approach course")
  void crosswindOnFinal_appliesNonZeroWca() throws Exception {
    AircraftBase aircraft = aircraftEstablishedOnFinal(118.0);
    for (int i = 0; i < 60; i++) {
      aircraft.calculateNextAircraftVector(SIM_DELTA);
      aircraft.calculateNextAircraftPosition(SIM_DELTA);
    }
  AircraftVector vector = aircraft.getAircraftVector();
    double wca =
        WindTriangle.windCorrectionAngleDeg(APPROACH_COURSE_DEG, 250.0, CROSSWIND);
    assertTrue(Math.abs(wca) > 0.3, "expected meaningful WCA, was " + wca);
    assertTrue(
        Math.abs(vector.heading.toDouble() - APPROACH_COURSE_DEG) > 0.3,
        "heading should crab into wind");
    assertEquals(APPROACH_COURSE_DEG, vector.trackDeg, 1.0);
  }

    private static AircraftBase aircraftEstablishedOnFinal(double initialHeadingDeg) throws Exception {
        AircraftBase aircraft = createTestAircraft(initialHeadingDeg);
        aircraft.setAmbientWind(CROSSWIND);
        aircraft.joinFinalApproachIntercept(
                new FixPosition(new Latitude(35.1), new Longitude(139.1)),
                "RJTT/16R-FA",
                APPROACH_COURSE_DEG);
        invokeOnDirectToTargetReached(aircraft);
        assertEquals(
                APPROACH_COURSE_DEG,
                aircraft.getInstructedVector().instructedHeading.toDouble(),
                0.01);
        return aircraft;
    }

    private static void warmUpOnFinal(AircraftBase aircraft, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            aircraft.calculateNextAircraftVector(SIM_DELTA);
            aircraft.calculateNextAircraftPosition(SIM_DELTA);
        }
    }

    private static double maxAbsCrossTrack(
            AircraftBase aircraft, FixPosition courseAnchor, int ticks) {
        double max = 0.0;
        for (int tick = 0; tick < ticks; tick++) {
            aircraft.calculateNextAircraftVector(SIM_DELTA);
            aircraft.calculateNextAircraftPosition(SIM_DELTA);
            FixPosition here = positionAsFix(aircraft.getAircraftPosition());
            double xtk =
                    Math.abs(
                            HoldingPatternGeometry.crossTrackNm(
                                    courseAnchor, here, APPROACH_COURSE_DEG));
            max = Math.max(max, xtk);
        }
        return max;
    }

    /**
     * Baseline with WCA disabled: hold course as heading while the same crosswind deflects track.
     */
    private static double maxAbsCrossTrackNoWca(
            FixPosition courseAnchor,
            AircraftPosition start,
            double courseDeg,
            double trueAirspeedKt,
            UniformWind wind,
            int ticks) {
        AircraftPosition pos = start;
        double max = 0.0;
        for (int tick = 0; tick < ticks; tick++) {
            WindTriangle.GroundVelocity gv =
                    WindTriangle.groundVelocity(courseDeg, trueAirspeedKt, wind);
            FixPosition here = positionAsFix(pos);
            double xtk =
                    Math.abs(HoldingPatternGeometry.crossTrackNm(courseAnchor, here, courseDeg));
            max = Math.max(max, xtk);
            pos =
                    PositionUtils.calculateNextPosition(
                            pos, gv.groundSpeedKt(), gv.trackDeg(), 0.0, SIM_DELTA);
        }
        return max;
    }

    private static FixPosition positionAsFix(AircraftPosition position) {
        return new FixPosition(position.latitude, position.longitude);
    }

    private static void invokeOnDirectToTargetReached(AircraftBase aircraft) throws Exception {
        Method method = AircraftBase.class.getDeclaredMethod("onDirectToTargetReached");
        method.setAccessible(true);
        method.invoke(aircraft);
    }

    private static AircraftBase createTestAircraft(double initialHeadingDeg) {
        var characteristics =
                new AircraftCharacteristics(
                        3.0,
                        3.0,
                        1640.0,
                        500.0,
                        140.0,
                        42000.0,
                        0.0,
                        AircraftCharacteristics.AircraftCategory.COMMERCIAL_PASSENGER);
        return new AircraftBase(
                new Callsign("FINL01"),
                new AircraftType("B738"),
                new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(5000)),
                new AircraftVector(
                        new Heading(initialHeadingDeg),
                        new GroundSpeed(250),
                        new VerticalSpeed(0)),
                new FixedWingFlightBehavior(),
                characteristics) {};
    }
}
