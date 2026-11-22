package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.holdwind;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldInstruction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldPhase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldTurnDirection;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

/** Combination paths: DEPART / STAR-like traffic into hold under wind. */
class HoldCombinationRegressionTest {

    @Test
    @DisplayName("DEPART then Hold under crosswind keeps racetrack")
    void departThenHoldUnderWind() {
        CommercialAircraft a = HoldWindRegressionTest.commercial("DEPW", 35.55, 139.77, 0, 150, 180);
        a.beginInitialClimb(35.55998611, 139.76906667, 20.5, 149.88, 18.2, 90.0, 5000);
        a.setAmbientWind(UniformWind.measured(90, 20));
        for (int s = 0; s < 90; s++) {
            HoldWindRegressionTest.tick(a);
        }
        FixPosition holdFix = HoldWindRegressionTest.fix(
                a.getAircraftPosition().latitude.toDouble() + 0.05,
                a.getAircraftPosition().longitude.toDouble());
        a.setHoldAtFix(
                holdFix,
                new HoldInstruction("HOLD1", HoldTurnDirection.RIGHT, 360.0, new HoldLeg.TimedMinutes(1.0)));
        double maxDist = 0;
        int circuits = 0;
        HoldPhase last = null;
        for (int s = 0; s < 1500; s++) {
            HoldWindRegressionTest.tick(a);
            HoldPhase p = a.getHoldPhase();
            if (p == HoldPhase.INBOUND_LEG && last != HoldPhase.INBOUND_LEG) {
                circuits++;
            }
            last = p;
            maxDist = Math.max(maxDist, GeodeticUtils.distanceToFix(a.getAircraftPosition(), holdFix));
        }
        assertTrue(circuits >= 3, "circuits=" + circuits);
        assertTrue(maxDist <= 9.0, "maxDist=" + maxDist);
    }

    @Test
    @DisplayName("STAR-like FLIGHT_PLAN aircraft into Hold under wind")
    void starLikeThenHoldUnderWind() {
        CommercialAircraft a = HoldWindRegressionTest.commercial("STARW", 34.85, 139.0, 7000, 0, 220);
        a.setAmbientWind(UniformWind.measured(270, 25));
        FixPosition holdFix = HoldWindRegressionTest.fix(35.0, 139.0);
        a.setHoldAtFix(
                holdFix,
                new HoldInstruction("ABENO", HoldTurnDirection.LEFT, 360.0, new HoldLeg.TimedMinutes(1.0)));
        double maxDist = 0;
        int circuits = 0;
        HoldPhase last = null;
        for (int s = 0; s < 1800; s++) {
            HoldWindRegressionTest.tick(a);
            HoldPhase p = a.getHoldPhase();
            if (p == HoldPhase.INBOUND_LEG && last != HoldPhase.INBOUND_LEG) {
                circuits++;
            }
            last = p;
            maxDist = Math.max(maxDist, GeodeticUtils.distanceToFix(a.getAircraftPosition(), holdFix));
        }
        assertTrue(circuits >= 5, "circuits=" + circuits);
        assertTrue(maxDist <= 9.0, "maxDist=" + maxDist);
    }
}
