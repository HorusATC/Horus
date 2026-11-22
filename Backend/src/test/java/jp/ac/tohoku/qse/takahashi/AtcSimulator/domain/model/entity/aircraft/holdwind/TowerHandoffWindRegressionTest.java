package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.holdwind;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

/**
 * Inverted ④ regressions: handoff acceptance matches live wind-aware kinematics.
 */
class TowerHandoffWindRegressionTest {

    static final double THR_LAT = 35.0;
    static final double THR_LON = 139.08;

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

    static double removalRun(CommercialAircraft a, int seconds) {
        FixPosition thr = fix(THR_LAT, THR_LON);
        double min = Double.MAX_VALUE;
        for (int s = 0; s < seconds && !a.shouldBeRemovedFromSimulation(); s++) {
            tick(a);
            min = Math.min(min, GeodeticUtils.distanceToFix(a.getAircraftPosition(), thr));
        }
        return min;
    }

    @Test
    @DisplayName("④a inverted: crosswind HEADING handoff that would miss is rejected")
    void handoffThatMissesUnderCrosswindIsRejected() {
        double lon5nm = THR_LON - 5.0 / (60.0 * Math.cos(Math.toRadians(35.0)));
        CommercialAircraft a = commercial("TWRX1", THR_LAT, lon5nm, 1500, 90, 250);
        a.setInstructedVector(new InstructedVector(new Heading(90), new Altitude(1500), new GroundSpeed(250)));
        a.setAmbientWind(UniformWind.measured(360, 30));
        assertThrows(IllegalArgumentException.class, () -> a.beginTowerHandoff(THR_LAT, THR_LON));
        assertFalse(a.isTowerHandoffActive());
        double min = removalRun(a, 1200);
        assertTrue(min > AircraftBase.TOWER_HANDOFF_REMOVAL_NM, "live min=" + min);
    }

    @Test
    @DisplayName("④b inverted: offset that reaches under drift is accepted and removed")
    void handoffThatReachesUnderDriftIsAcceptedAndRemoved() {
        double lon5nm = THR_LON - 5.0 / (60.0 * Math.cos(Math.toRadians(35.0)));
        CommercialAircraft a = commercial("TWRX2", THR_LAT + 0.6 / 60.0, lon5nm, 1500, 90, 250);
        a.setInstructedVector(new InstructedVector(new Heading(90), new Altitude(1500), new GroundSpeed(250)));
        a.setAmbientWind(UniformWind.measured(360, 30));
        a.beginTowerHandoff(THR_LAT, THR_LON);
        assertTrue(a.isTowerHandoffActive());
        double min = removalRun(a, 1200);
        assertTrue(a.shouldBeRemovedFromSimulation(), "accepted aircraft must be removed; min=" + min);
        assertTrue(min <= AircraftBase.TOWER_HANDOFF_REMOVAL_NM);
    }

    @Test
    @DisplayName("JOIN FINAL under crosswind: handoff accepted and removed")
    void joinFinalUnderCrosswindHandoffRemoves() throws Exception {
        double lon5nm = THR_LON - 5.0 / (60.0 * Math.cos(Math.toRadians(35.0)));
        CommercialAircraft a = commercial("TWRX3", THR_LAT, lon5nm, 1500, 90, 250);
        a.setAmbientWind(UniformWind.measured(360, 30));
        a.joinFinalApproachIntercept(fix(THR_LAT, lon5nm), "FA", 90.0);
        Method m = AircraftBase.class.getDeclaredMethod("onDirectToTargetReached");
        m.setAccessible(true);
        m.invoke(a);
        a.beginTowerHandoff(THR_LAT, THR_LON);
        double min = removalRun(a, 600);
        assertTrue(a.shouldBeRemovedFromSimulation(), "min=" + min);
    }

    @Test
    @DisplayName("combination: HEADING under crosswind never orphans (reject or remove)")
    void headingUnderCrosswindNeverOrphans() {
        double lon5nm = THR_LON - 5.0 / (60.0 * Math.cos(Math.toRadians(35.0)));
        for (double latOffsetNm : new double[] {-1.0, -0.6, 0.0, 0.6, 1.0}) {
            CommercialAircraft a = commercial(
                    "GRID", THR_LAT + latOffsetNm / 60.0, lon5nm, 1500, 90, 250);
            a.setInstructedVector(new InstructedVector(new Heading(90), new Altitude(1500), new GroundSpeed(250)));
            a.setAmbientWind(UniformWind.measured(360, 30));
            boolean accepted;
            try {
                a.beginTowerHandoff(THR_LAT, THR_LON);
                accepted = true;
            } catch (IllegalArgumentException ex) {
                accepted = false;
            }
            if (accepted) {
                removalRun(a, 1200);
                assertTrue(
                        a.shouldBeRemovedFromSimulation(),
                        "accepted offset " + latOffsetNm + " must be removed");
            } else {
                assertFalse(a.isTowerHandoffActive());
            }
        }
    }
}
