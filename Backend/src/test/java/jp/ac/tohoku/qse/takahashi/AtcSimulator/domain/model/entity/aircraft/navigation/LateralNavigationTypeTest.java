package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;

/**
 * Sealed lateral mode makes illegal flag mixes unrepresentable.
 */
class LateralNavigationTypeTest {

    @Test
    void flightPlanProjectionHasNoVaOrApproachCourse() {
        LateralNavigation mode = new LateralNavigation.FlightPlanNav();
        assertEquals(NavigationMode.FLIGHT_PLAN, mode.projectedNavigationMode());
        assertTrue(!mode.projectedProcedureVa());
        assertEquals(null, mode.approachCourseHeadingOrNull());
    }

    @Test
    void procedureVaProjectsAsHeadingWithVaFlag() {
        LateralNavigation mode = new LateralNavigation.ProcedureVa(330.0, 700);
        assertEquals(NavigationMode.HEADING, mode.projectedNavigationMode());
        assertTrue(mode.projectedProcedureVa());
        assertTrue(!mode.projectedInitialClimb());
    }

    @Test
    void finalApproachPendingMustUseDirectToJoinFinal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LateralNavigation.FinalApproach(149.88, true));
    }

    @Test
    void directToRejectsNonPendingCourseAndPendingWithoutCourse() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LateralNavigation.DirectToNav(149.88, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LateralNavigation.DirectToNav(null, true));
    }

    @Test
    void towerHandoffAndInitialClimbAreDistinctVariants() {
        LateralNavigation climb =
                new LateralNavigation.InitialClimb(35.0, 139.0, 150.0, 90.0, 18.0);
        LateralNavigation handoff =
                new LateralNavigation.TowerHandoff(
                        new FixPosition(new Latitude(35.0), new Longitude(139.08)), 149.88);
        assertTrue(climb.projectedInitialClimb());
        assertTrue(!climb.projectedTowerHandoff());
        assertTrue(handoff.projectedTowerHandoff());
        assertTrue(!handoff.projectedInitialClimb());
        assertEquals(NavigationMode.HEADING, climb.projectedNavigationMode());
        assertEquals(NavigationMode.HEADING, handoff.projectedNavigationMode());
    }
}
