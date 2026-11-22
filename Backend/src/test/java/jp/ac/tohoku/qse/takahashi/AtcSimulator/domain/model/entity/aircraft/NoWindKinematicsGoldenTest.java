package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.SimulationTiming;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.behavior.FixedWingFlightBehavior;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.characteristics.AircraftCharacteristics;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldInstruction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldTurnDirection;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.InstructedVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.PositionUtils;

class NoWindKinematicsGoldenTest {

  private static final int TICKS = 120;

  @Test
  void missingWindKeepsTrackAlignedWithHeadingAndLegacyPlanarMotion() {
    CommercialAircraft aircraft = newCommercialHeading45();
    aircraft.setAmbientWind(UniformWind.missing());
    assertGoldenNoWindMotion(aircraft);
  }

  @Test
  @DisplayName("default unset wind matches explicit missing() histories")
  void unsetWindMatchesExplicitMissing() {
    CommercialAircraft unset = newCommercialHeading45();
    CommercialAircraft missing = newCommercialHeading45();
    missing.setAmbientWind(UniformWind.missing());

    for (int tick = 0; tick < TICKS; tick++) {
      stepBoth(unset, missing);
      assertSamePosition(unset.getAircraftPosition(), missing.getAircraftPosition(), tick);
      assertSameVector(unset.getAircraftVector(), missing.getAircraftVector(), tick);
    }
  }

  @Test
  @DisplayName("heading-based and track-based PositionUtils paths coincide with no wind")
  void headingPathEqualsTrackPathWhenWindAbsent() {
    CommercialAircraft aircraft = newCommercialHeading45();
    AircraftPosition headingPath =
        new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(10_000));
    AircraftPosition trackPath = copyPosition(headingPath);
    double dt = SimulationTiming.SIM_DELTA_SECONDS;

    for (int tick = 0; tick < TICKS; tick++) {
      aircraft.calculateNextAircraftVector(dt);
      AircraftVector vector = aircraft.getAircraftVector();
      assertEquals(vector.heading.toDouble(), vector.trackDeg, 1e-6, "track tick " + tick);
      assertEquals(
          vector.groundSpeed.toDouble(), vector.trueAirspeedKnots, 1e-6, "TAS tick " + tick);

      headingPath =
          PositionUtils.calculateNextPosition(
              headingPath,
              vector.groundSpeed.toDouble(),
              vector.heading.toDouble(),
              vector.verticalSpeed.toDouble(),
              dt);
      trackPath =
          PositionUtils.calculateNextPosition(
              trackPath,
              vector.groundSpeed.toDouble(),
              vector.motionDirectionDeg(),
              vector.verticalSpeed.toDouble(),
              dt);

      aircraft.calculateNextAircraftPosition(dt);
      assertSamePosition(headingPath, aircraft.getAircraftPosition(), tick);
      assertSamePosition(trackPath, aircraft.getAircraftPosition(), tick);
      assertSamePosition(headingPath, trackPath, tick);
    }
  }

  @Test
  @DisplayName("missing wind: hold overlay vertices match default ambient wind")
  void missingWind_holdingOverlayUnchanged() {
    FixPosition holdFix = new FixPosition(new Latitude(35.0), new Longitude(139.0));
    HoldInstruction instruction =
        new HoldInstruction("ABENO", HoldTurnDirection.RIGHT, 90.0, new HoldLeg.TimedMinutes(1.0));

    AircraftBase unset = createHoldAircraft(holdFix);
    AircraftBase missing = createHoldAircraft(holdFix);
    missing.setAmbientWind(UniformWind.missing());

    unset.setHoldAtFix(holdFix, instruction);
    missing.setHoldAtFix(holdFix, instruction);

    List<FixPosition> unsetPattern = unset.getHoldingPatternVertices();
    List<FixPosition> missingPattern = missing.getHoldingPatternVertices();
    assertEquals(unsetPattern.size(), missingPattern.size());
    for (int i = 0; i < unsetPattern.size(); i++) {
      assertEquals(
          unsetPattern.get(i).latitude.toDouble(),
          missingPattern.get(i).latitude.toDouble(),
          1e-9,
          "lat " + i);
      assertEquals(
          unsetPattern.get(i).longitude.toDouble(),
          missingPattern.get(i).longitude.toDouble(),
          1e-9,
          "lon " + i);
    }
  }

  @Test
  @DisplayName("missing wind: JOIN FINAL instructed course unchanged after intercept")
  void missingWind_joinFinalCourseUnchanged() throws Exception {
    double course = 149.88;
    AircraftBase aircraft = createHoldAircraft(new FixPosition(new Latitude(35.0), new Longitude(139.0)));
    aircraft.joinFinalApproachIntercept(
        new FixPosition(new Latitude(35.1), new Longitude(139.1)), "RJTT/16R-FA", course);
    Method onReached = AircraftBase.class.getDeclaredMethod("onDirectToTargetReached");
    onReached.setAccessible(true);
    onReached.invoke(aircraft);

    assertEquals(NavigationMode.HEADING, aircraft.getNavigationMode());
    assertEquals(course, aircraft.getInstructedVector().instructedHeading.toDouble(), 0.01);
    double dt = SimulationTiming.SIM_DELTA_SECONDS;
    for (int tick = 0; tick < 80; tick++) {
      aircraft.calculateNextAircraftVector(dt);
      aircraft.calculateNextAircraftPosition(dt);
      assertEquals(
          course,
          aircraft.getInstructedVector().instructedHeading.toDouble(),
          0.01,
          "missing wind must not rewrite final approach course at tick " + tick);
    }
    assertEquals(course, aircraft.getAircraftVector().trackDeg, 1.0);
  }

  private static void assertGoldenNoWindMotion(CommercialAircraft aircraft) {
    AircraftPosition expected =
        new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(10_000));
    double dt = SimulationTiming.SIM_DELTA_SECONDS;

    for (int tick = 0; tick < TICKS; tick++) {
      AircraftVector vector = aircraft.getAircraftVector();
      assertEquals(
          vector.heading.toDouble(), vector.trackDeg, 1e-6, "track should equal heading at tick " + tick);
      assertEquals(
          vector.groundSpeed.toDouble(),
          vector.trueAirspeedKnots,
          1e-6,
          "TAS should equal GS with missing wind at tick " + tick);

      expected =
          PositionUtils.calculateNextPosition(
              expected,
              vector.groundSpeed.toDouble(),
              vector.motionDirectionDeg(),
              vector.verticalSpeed.toDouble(),
              dt);

      aircraft.calculateNextAircraftVector(dt);
      aircraft.calculateNextAircraftPosition(dt);

      AircraftPosition actual = aircraft.getAircraftPosition();
      assertEquals(expected.latitude.toDouble(), actual.latitude.toDouble(), 1e-8, "latitude tick " + tick);
      assertEquals(expected.longitude.toDouble(), actual.longitude.toDouble(), 1e-8, "longitude tick " + tick);
      assertEquals(expected.altitude.toDouble(), actual.altitude.toDouble(), 1e-6, "altitude tick " + tick);
    }
  }

  private static CommercialAircraft newCommercialHeading45() {
    CommercialAircraft aircraft =
        new CommercialAircraft(
            new Callsign("NOWIND"),
            new AircraftType("B738"),
            new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(10_000)),
            new AircraftVector(new Heading(45.0), new GroundSpeed(250), new VerticalSpeed(0)),
            "HND",
            "RJTT",
            "NRT",
            "RJAA",
            "2026-01-01T00:00:00Z");
    aircraft.setNavigationMode(NavigationMode.HEADING);
    aircraft.setInstructedVector(
        new InstructedVector(new Heading(45.0), new Altitude(10_000), new GroundSpeed(250)));
    return aircraft;
  }

  private static AircraftBase createHoldAircraft(FixPosition fix) {
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
            new Callsign("HOLDNW"),
            new AircraftType("B738"),
            new AircraftPosition(fix.latitude, fix.longitude, new Altitude(5000)),
            new AircraftVector(new Heading(90), new GroundSpeed(180), new VerticalSpeed(0)),
            new FixedWingFlightBehavior(),
            characteristics) {};
  }

  private static void stepBoth(CommercialAircraft a, CommercialAircraft b) {
    double dt = SimulationTiming.SIM_DELTA_SECONDS;
    a.calculateNextAircraftVector(dt);
    b.calculateNextAircraftVector(dt);
    a.calculateNextAircraftPosition(dt);
    b.calculateNextAircraftPosition(dt);
  }

  private static AircraftPosition copyPosition(AircraftPosition pos) {
    return new AircraftPosition(pos.latitude, pos.longitude, pos.altitude);
  }

  private static void assertSamePosition(AircraftPosition a, AircraftPosition b, int tick) {
    assertEquals(a.latitude.toDouble(), b.latitude.toDouble(), 1e-8, "lat tick " + tick);
    assertEquals(a.longitude.toDouble(), b.longitude.toDouble(), 1e-8, "lon tick " + tick);
    assertEquals(a.altitude.toDouble(), b.altitude.toDouble(), 1e-6, "alt tick " + tick);
  }

  private static void assertSameVector(AircraftVector a, AircraftVector b, int tick) {
    assertEquals(a.heading.toDouble(), b.heading.toDouble(), 1e-8, "hdg tick " + tick);
    assertEquals(a.groundSpeed.toDouble(), b.groundSpeed.toDouble(), 1e-8, "gs tick " + tick);
    assertEquals(a.trackDeg, b.trackDeg, 1e-8, "trk tick " + tick);
    assertEquals(a.trueAirspeedKnots, b.trueAirspeedKnots, 1e-8, "tas tick " + tick);
  }
}
