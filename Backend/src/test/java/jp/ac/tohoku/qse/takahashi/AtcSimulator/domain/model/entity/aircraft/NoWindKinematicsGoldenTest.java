package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.config.SimulationTiming;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.InstructedVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.PositionUtils;

class NoWindKinematicsGoldenTest {

  private static final int TICKS = 120;

  @Test
  void missingWindKeepsTrackAlignedWithHeadingAndLegacyPlanarMotion() {
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
    aircraft.setAmbientWind(UniformWind.missing());
    aircraft.setInstructedVector(
        new InstructedVector(new Heading(45.0), new Altitude(10_000), new GroundSpeed(250)));

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
}
