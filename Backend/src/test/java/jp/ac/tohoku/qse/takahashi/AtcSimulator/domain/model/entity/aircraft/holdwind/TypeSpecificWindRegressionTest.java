package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.holdwind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.helicopter.Helicopter;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.military.FighterJet;
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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.MathUtils;

/** Inverted ⑧: helicopter / fighter wind-aware motion. */
class TypeSpecificWindRegressionTest {

    @Test
    @DisplayName("⑧ inverted: helicopter moves along track under wind")
    void helicopterMovesAlongTrack() {
        Helicopter h = new Helicopter(
                new Callsign("HELI1"),
                new AircraftType("H135"),
                new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(1500)),
                new AircraftVector(new Heading(90), new GroundSpeed(100), new VerticalSpeed(0)),
                "op",
                "purpose",
                "base");
        h.setAmbientWind(UniformWind.measured(360, 30));
        AircraftVector v = h.getAircraftVector();
        AircraftPosition p0 = h.getAircraftPosition();
        h.calculateNextAircraftPosition(60.0);
        AircraftPosition p1 = h.getAircraftPosition();
        double moved = MathUtils.normalizeAngle(Math.toDegrees(Math.atan2(
                (p1.longitude.toDouble() - p0.longitude.toDouble()) * Math.cos(Math.toRadians(35.0)),
                p1.latitude.toDouble() - p0.latitude.toDouble())));
        assertTrue(Math.abs(v.trackDeg - 90.0) > 5, "track should differ from heading under crosswind");
        assertEquals(v.trackDeg, moved, 2.0);
        assertTrue(Math.abs(moved - 90.0) > 5, "motion must not follow raw heading");
    }

    @Test
    @DisplayName("⑧ inverted: fighter tactical maneuver keeps wind triangle state")
    void fighterTacticalManeuverKeepsWindState() {
        FighterJet f = new FighterJet(
                new Callsign("FTR1"),
                new AircraftType("F15"),
                new AircraftPosition(new Latitude(35.0), new Longitude(139.0), new Altitude(10000)),
                new AircraftVector(new Heading(90), new GroundSpeed(400), new VerticalSpeed(0)),
                "sq",
                "msn",
                "base");
        f.setAmbientWind(UniformWind.measured(360, 50));
        f.performTacticalManeuver(120);
        AircraftVector v = f.getAircraftVector();
        assertTrue(Math.abs(v.heading.toDouble() - v.trackDeg) > 1.0 || v.trueAirspeedKnots != v.groundSpeed.toDouble());
        assertTrue(Math.abs(v.groundSpeed.toDouble() - v.trueAirspeedKnots) > 1e-6
                || Math.abs(v.trackDeg - v.heading.toDouble()) > 1e-6);
    }
}
