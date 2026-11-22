package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;

class WindTriangleTest {

  private static final double HEADING = 90.0;
  private static final double TAS = 120.0;

  @Test
  void phakTailwindAndHeadwindExamples() {
    WindTriangle.GroundVelocity tail =
        WindTriangle.groundVelocity(HEADING, TAS, UniformWind.measured(270, 20));
    assertEquals(140.0, tail.groundSpeedKt(), 0.1);
    assertEquals(90.0, tail.trackDeg(), 0.1);

    WindTriangle.GroundVelocity head =
        WindTriangle.groundVelocity(HEADING, TAS, UniformWind.measured(90, 20));
    assertEquals(100.0, head.groundSpeedKt(), 0.1);
    assertEquals(90.0, head.trackDeg(), 0.1);
  }

  @Test
  void crosswindTrackAndWcaMatchPhakStyle() {
    WindTriangle.GroundVelocity cross =
        WindTriangle.groundVelocity(HEADING, TAS, UniformWind.measured(360, 20));
    double expectedGs = Math.hypot(TAS, 20.0);
    assertEquals(expectedGs, cross.groundSpeedKt(), 0.1);

    double wca = WindTriangle.windCorrectionAngleDeg(HEADING, TAS, UniformWind.measured(360, 20));
    double expectedWca = Math.toDegrees(Math.asin(-20.0 / TAS));
    assertEquals(expectedWca, wca, 0.1);

    double heading =
        WindTriangle.headingToMaintainCourseDeg(HEADING, TAS, UniformWind.measured(360, 20));
    WindTriangle.GroundVelocity corrected =
        WindTriangle.groundVelocity(heading, TAS, UniformWind.measured(360, 20));
    assertTrue(Math.abs(heading - HEADING) > 0.5);
    assertEquals(HEADING, corrected.trackDeg(), 0.5);
  }

  @Test
  void missingWindLeavesHeadingAlignedGroundVelocity() {
    WindTriangle.GroundVelocity calm =
        WindTriangle.groundVelocity(HEADING, TAS, UniformWind.missing());
    assertEquals(TAS, calm.groundSpeedKt(), 1e-6);
    assertEquals(HEADING, calm.trackDeg(), 1e-6);
    assertEquals(
        0.0, WindTriangle.windCorrectionAngleDeg(HEADING, TAS, UniformWind.missing()), 1e-6);
    assertEquals(
        TAS, WindTriangle.trueAirspeedForGroundSpeed(HEADING, TAS, UniformWind.missing()), 1e-6);
  }

  @Test
  void windMutationsChangeGroundSpeedPredictably() {
    WindTriangle.GroundVelocity baseline =
        WindTriangle.groundVelocity(HEADING, TAS, UniformWind.measured(270, 20));
    WindTriangle.GroundVelocity stronger =
        WindTriangle.groundVelocity(HEADING, TAS, UniformWind.measured(270, 25));
    assertTrue(stronger.groundSpeedKt() > baseline.groundSpeedKt());

    WindTriangle.GroundVelocity crossBase =
        WindTriangle.groundVelocity(HEADING, TAS, UniformWind.measured(360, 15));
    WindTriangle.GroundVelocity crossStrong =
        WindTriangle.groundVelocity(HEADING, TAS, UniformWind.measured(360, 25));
    assertNotEquals(crossBase.trackDeg(), crossStrong.trackDeg());
  }
}
