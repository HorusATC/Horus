package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft;

import java.util.List;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.exception.ApproachControlReleasedException;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.behavior.FlightBehavior;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.characteristics.AircraftCharacteristics;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlan;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldEntryProcedure;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldInstruction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldPhase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldTurnDirection;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldingPatternGeometry;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.WaypointAltitudeTargetResolver;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.GroundSpeed;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Heading;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.InstructedVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Type.AircraftType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.IndicatedAirspeedConverter;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.PositionUtils;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.StringUtils;

/**
 * 航空機の基底クラス
 * Strategy パターンとComposition を活用して、異なる航空機タイプの実装を可能にする
 */
public abstract class AircraftBase implements Aircraft {
    protected final Callsign callsign;
    protected AircraftPosition aircraftPosition;
    protected AircraftVector aircraftVector;
    protected InstructedVector instructedVector;
    /** Controller-recorded clearance memo (altitude / heading / speed); does not drive the aircraft. */
    protected InstructedVector atcClearance;
    /** Optional SSR transponder code; null means unassigned. */
    protected String squawk;
    protected final AircraftType aircraftType;

    // Strategy パターンによる飛行動作の委譲
    protected final FlightBehavior flightBehavior;

    // 航空機の物理的特性
    protected final AircraftCharacteristics characteristics;

    protected FlightPlan flightPlan;
    protected int currentWaypointIndex;
    protected NavigationMode navigationMode;
    protected double previousDistanceToWaypoint;
    protected FixPosition directToTarget;
    protected String directToFixName;
    protected boolean resumeFlightPlanAfterDirectTo;
    protected boolean markedForRemoval;
    protected FixPosition holdingFix;
    protected String holdingFixName;
    protected FixPosition holdingOutboundTarget;
    protected HoldTurnDirection holdTurnDirection;
    protected HoldLeg holdLeg;
    protected double holdInboundCourseDeg;
    protected HoldPhase holdPhase;
    protected boolean holdingEntryCompleted;
    protected HoldEntryProcedure holdEntryProcedure;
    protected boolean holdEntryProcedureFrozen;
    protected double holdFrozenTurnRateDegPerSec;
    protected double holdFrozenTurnRadiusNm;
    protected double holdFrozenOutboundLegNm;
    protected double holdFrozenEntryLegNm;
    /** Elapsed sim seconds on the outbound leg after abeam/rollout timing start. */
    protected double holdOutboundLegElapsedSec;
    protected boolean holdOutboundTimingStarted;
    /** Elapsed sim seconds on a teardrop/parallel entry leg after rollout. */
    protected double holdEntryLegElapsedSec;
    protected boolean holdEntryLegTimingStarted;
    /** Forced turn side during entry maneuvers; null uses pattern direction / shortest turn. */
    protected HoldTurnDirection holdEntryForcedTurnDirection;
    /** Accumulated forced-turn degrees in the current entry turn phase (for parallel &gt;180°). */
    protected double holdEntryTurnAccumDeg;
    protected int resumeFlightPlanIndexAfterHold;
    protected Integer instructedIndicatedAirspeedKnots;
    protected boolean finalApproachCoursePending;
    protected Double finalApproachCourseHeading;
    /** True after a go-around until the next navigation instruction. */
    protected boolean goAroundActive;
    /** True after the approach seat has transferred the aircraft to the tower. */
    protected boolean towerHandoffActive;
    protected FixPosition towerThreshold;

    public static final double MAX_TOWER_HANDOFF_DISTANCE_NM = 10.0;
    public static final double TOWER_HANDOFF_REMOVAL_NM = 0.5;
    private static final int TOWER_HANDOFF_TRACK_HORIZON_SECONDS = 20 * 60;

    private static final double WAYPOINT_PASS_THRESHOLD_MIN_NM = 1.5;
    private static final double WAYPOINT_PASS_THRESHOLD_MAX_NM = 5.0;
    private static final double WAYPOINT_PASS_SECONDS = 5.0;
    private static final double HOLD_WAYPOINT_PASS_THRESHOLD_NM = 0.25;
    private static final double HOLD_HEADING_CAPTURE_DEG = 2.0;
    private static final double HOLD_ABEAM_TOLERANCE_NM = 0.15;
    private static final double HOLD_INTERCEPT_XTK_NM = 0.5;
    private static final double HOLD_PARALLEL_MIN_TURN_DEG = 180.0;

    private static final boolean WP_DEBUG = "true".equalsIgnoreCase(System.getProperty("flightplan.wp.debug"));

    public AircraftBase(Callsign callsign, AircraftType aircraftType, AircraftPosition aircraftPosition,
                       AircraftVector aircraftVector, FlightBehavior flightBehavior,
                       AircraftCharacteristics characteristics) {
        this.callsign = callsign;
        this.aircraftType = aircraftType;
        this.aircraftPosition = aircraftPosition;
        this.aircraftVector = aircraftVector;
        this.flightBehavior = flightBehavior;
        this.characteristics = characteristics;
        this.instructedVector = new InstructedVector(aircraftVector.heading, aircraftPosition.altitude, aircraftVector.groundSpeed);
        this.squawk = null;
        this.flightPlan = null;
        this.currentWaypointIndex = 0;
        this.navigationMode = NavigationMode.HEADING;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        this.directToTarget = null;
        this.directToFixName = null;
        this.resumeFlightPlanAfterDirectTo = false;
        this.markedForRemoval = false;
        this.holdingFix = null;
        this.holdingFixName = null;
        this.holdingOutboundTarget = null;
        this.holdTurnDirection = HoldTurnDirection.RIGHT;
        this.holdLeg = new HoldLeg.DistanceNm(HoldLeg.DEFAULT_OUTBOUND_LEG_NM);
        this.holdInboundCourseDeg = 0.0;
        this.holdPhase = HoldPhase.ENTRY;
        this.holdingEntryCompleted = false;
        this.holdEntryProcedure = HoldEntryProcedure.DIRECT;
        this.holdEntryProcedureFrozen = false;
        this.holdFrozenTurnRateDegPerSec = HoldingPatternGeometry.STANDARD_RATE_DEG_PER_SEC;
        this.holdFrozenTurnRadiusNm = 0.0;
        this.holdFrozenOutboundLegNm = HoldLeg.DEFAULT_OUTBOUND_LEG_NM;
        this.holdFrozenEntryLegNm = HoldLeg.DEFAULT_OUTBOUND_LEG_NM;
        this.holdOutboundLegElapsedSec = 0.0;
        this.holdOutboundTimingStarted = false;
        this.holdEntryLegElapsedSec = 0.0;
        this.holdEntryLegTimingStarted = false;
        this.holdEntryForcedTurnDirection = null;
        this.holdEntryTurnAccumDeg = 0.0;
        this.resumeFlightPlanIndexAfterHold = -1;
    }

    @Override
    public void calculateNextAircraftPosition(double simDeltaSeconds) {
        this.aircraftPosition = flightBehavior.calculateNextPosition(
            this.aircraftPosition,
            this.aircraftVector,
            simDeltaSeconds
        );
    }

    @Override
    public void calculateNextAircraftVector(double simDeltaSeconds) {
        updateInstructedVectorFromNavigation();
        advanceHoldingPhase(simDeltaSeconds);

        double turnRate = resolveActiveTurnRateDegPerSec();
        double headingBefore = this.aircraftVector.heading.toDouble();
        Heading nextHeading;
        // Parallel re-intercept: continuous turn past 180° until the inbound axis is reached,
        // then a normal forced turn captures the inbound heading.
        boolean parallelInbound = holdPhase == HoldPhase.ENTRY_PARALLEL_INBOUND_TURN
                && holdEntryForcedTurnDirection != null;
        boolean parallelContinuous = parallelInbound
                && !(holdEntryTurnAccumDeg >= HOLD_PARALLEL_MIN_TURN_DEG && inboundAxisCaptured());
        if (parallelContinuous) {
            nextHeading = calculateNextHeadingContinuousTurn(headingBefore, holdEntryForcedTurnDirection, turnRate);
        } else if (parallelInbound) {
            // Axis reached after >180°: shortest turn to roll out on inbound (avoid long-way force).
            nextHeading = flightBehavior.calculateNextHeading(
                    headingBefore,
                    this.instructedVector.instructedHeading.toDouble(),
                    turnRate);
        } else if (shouldForceHoldTurn(HoldTurnDirection.RIGHT)) {
            nextHeading = calculateNextHeadingRightTurn(
                    headingBefore,
                    this.instructedVector.instructedHeading.toDouble(),
                    turnRate);
        } else if (shouldForceHoldTurn(HoldTurnDirection.LEFT)) {
            nextHeading = calculateNextHeadingLeftTurn(
                    headingBefore,
                    this.instructedVector.instructedHeading.toDouble(),
                    turnRate);
        } else {
            nextHeading = flightBehavior.calculateNextHeading(
                    headingBefore,
                    this.instructedVector.instructedHeading.toDouble(),
                    turnRate);
        }
        if (navigationMode == NavigationMode.HOLDING && holdEntryForcedTurnDirection != null) {
            holdEntryTurnAccumDeg += Math.abs(
                    HoldingPatternGeometry.signedHeadingDeltaDeg(headingBefore, nextHeading.toDouble()));
        }

        double targetGroundSpeedKnots = resolveEffectiveTargetGroundSpeedKnots();
        var nextGroundSpeed = flightBehavior.calculateNextGroundSpeed(
            this.aircraftVector.groundSpeed.toDouble(),
            targetGroundSpeedKnots,
            this.characteristics.getMaxAcceleration()
        );

        var nextVerticalSpeed = flightBehavior.calculateNextVerticalSpeed(
            this.aircraftPosition.altitude.toDouble(),
            this.instructedVector.instructedAltitude.toDouble(),
            this.characteristics.getMaxClimbRate(),
            simDeltaSeconds
        );

        this.aircraftVector = new AircraftVector(nextHeading, nextGroundSpeed, nextVerticalSpeed);

        double currentAltitude = this.aircraftPosition.altitude.toDouble();
        double targetAltitude = this.instructedVector.instructedAltitude.toDouble();
        double altitudeDifference = Math.abs(currentAltitude - targetAltitude);

        if (altitudeDifference <= 5.0 && Math.abs(nextVerticalSpeed.toDouble()) <= 50.0) {
            this.aircraftPosition = new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition(
                this.aircraftPosition.latitude,
                this.aircraftPosition.longitude,
                this.instructedVector.instructedAltitude
            );
            this.aircraftVector = new AircraftVector(nextHeading, nextGroundSpeed, new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.VerticalSpeed(0.0));
        }
    }

    private double resolveActiveTurnRateDegPerSec() {
        if (navigationMode == NavigationMode.HOLDING && holdingEntryCompleted) {
            return holdFrozenTurnRateDegPerSec;
        }
        return this.characteristics.getMaxTurnRate();
    }

    private boolean shouldForceHoldTurn(HoldTurnDirection direction) {
        if (navigationMode != NavigationMode.HOLDING || !holdingEntryCompleted) {
            return false;
        }
        if (holdEntryForcedTurnDirection != null) {
            return holdEntryForcedTurnDirection == direction;
        }
        if (holdTurnDirection != direction) {
            return false;
        }
        return holdPhase == HoldPhase.OUTBOUND_TURN || holdPhase == HoldPhase.INBOUND_TURN;
    }

    private void updateInstructedVectorFromNavigation() {
        if (navigationMode == NavigationMode.HEADING) {
            return;
        }
        if (navigationMode == NavigationMode.HOLDING && holdingFix != null && holdingEntryCompleted) {
            updateInstructedVectorForHolding();
            return;
        }
        if (navigationMode == NavigationMode.HOLDING && holdingFix != null && !holdingEntryCompleted) {
            refreshPredictedEntryProcedure();
        }
        FixPosition target = resolveNavigationTarget();
        if (target == null) {
            return;
        }
        double bearing = flightBehavior.calculateTurnAngle(aircraftPosition, aircraftVector.heading.toDouble(), target);
        Altitude targetAltitude = resolveTargetAltitude();
        GroundSpeed targetSpeed = resolveTargetSpeed();
        this.instructedVector = new InstructedVector(new Heading(bearing), targetAltitude, targetSpeed);
    }

    private void updateInstructedVectorForHolding() {
        Altitude targetAltitude = resolveTargetAltitude();
        GroundSpeed targetSpeed = resolveTargetSpeed();
        double outbound = HoldingPatternGeometry.outboundBearingDeg(holdInboundCourseDeg);
        double teardrop = HoldingPatternGeometry.teardropHeadingDeg(holdInboundCourseDeg, holdTurnDirection);
        double heading = switch (holdPhase) {
            case ENTRY_TEARDROP_TURN, ENTRY_TEARDROP_LEG -> teardrop;
            case ENTRY_TEARDROP_INBOUND_TURN, ENTRY_PARALLEL_INBOUND_TURN, INBOUND_TURN -> holdInboundCourseDeg;
            case ENTRY_PARALLEL_OUTBOUND_TURN, ENTRY_PARALLEL_LEG, OUTBOUND_TURN -> outbound;
            case OUTBOUND_LEG -> outbound;
            case INBOUND_LEG, ENTRY -> flightBehavior.calculateTurnAngle(
                    aircraftPosition, aircraftVector.heading.toDouble(), holdingFix);
        };
        if (holdPhase == HoldPhase.OUTBOUND_LEG && holdingOutboundTarget != null) {
            heading = flightBehavior.calculateTurnAngle(
                    aircraftPosition, aircraftVector.heading.toDouble(), holdingOutboundTarget);
        }
        if (holdPhase == HoldPhase.ENTRY_PARALLEL_LEG || holdPhase == HoldPhase.ENTRY_TEARDROP_LEG) {
            // Hold heading; timing drives the leg end.
            heading = holdPhase == HoldPhase.ENTRY_TEARDROP_LEG ? teardrop : outbound;
        }
        this.instructedVector = new InstructedVector(new Heading(heading), targetAltitude, targetSpeed);
    }

    private FixPosition resolveNavigationTarget() {
        if (navigationMode == NavigationMode.DIRECT_TO && directToTarget != null) {
            return directToTarget;
        }
        if (navigationMode == NavigationMode.FLIGHT_PLAN && flightPlan != null) {
            return flightPlan.getNextWaypoint(currentWaypointIndex)
                    .map(FlightPlanWaypoint::getPosition)
                    .orElse(null);
        }
        if (navigationMode == NavigationMode.HOLDING && holdingFix != null) {
            if (!holdingEntryCompleted || holdPhase == HoldPhase.ENTRY || holdPhase == HoldPhase.INBOUND_LEG) {
                return holdingFix;
            }
            if (holdPhase == HoldPhase.OUTBOUND_LEG) {
                return holdingOutboundTarget;
            }
            return null;
        }
        return null;
    }

    private void advanceHoldingPhase(double simDeltaSeconds) {
        if (navigationMode != NavigationMode.HOLDING || !holdingEntryCompleted || holdingFix == null) {
            return;
        }
        double heading = aircraftVector.heading.toDouble();
        double outbound = HoldingPatternGeometry.outboundBearingDeg(holdInboundCourseDeg);
        double teardrop = HoldingPatternGeometry.teardropHeadingDeg(holdInboundCourseDeg, holdTurnDirection);
        switch (holdPhase) {
            case ENTRY_TEARDROP_TURN -> {
                if (headingCapture(heading, teardrop)) {
                    holdPhase = HoldPhase.ENTRY_TEARDROP_LEG;
                    holdEntryForcedTurnDirection = null;
                    holdEntryLegElapsedSec = 0.0;
                    holdEntryLegTimingStarted = true;
                }
            }
            case ENTRY_TEARDROP_LEG -> {
                holdEntryLegElapsedSec += simDeltaSeconds;
                if (entryLegComplete()) {
                    holdPhase = HoldPhase.ENTRY_TEARDROP_INBOUND_TURN;
                    holdEntryForcedTurnDirection = holdTurnDirection;
                    holdEntryTurnAccumDeg = 0.0;
                }
            }
            case ENTRY_TEARDROP_INBOUND_TURN -> {
                if (inboundCourseIntercepted()) {
                    holdPhase = HoldPhase.INBOUND_LEG;
                    holdEntryForcedTurnDirection = null;
                    previousDistanceToWaypoint = Double.MAX_VALUE;
                }
            }
            case ENTRY_PARALLEL_OUTBOUND_TURN -> {
                if (headingCapture(heading, outbound)) {
                    holdPhase = HoldPhase.ENTRY_PARALLEL_LEG;
                    holdEntryForcedTurnDirection = null;
                    holdEntryLegElapsedSec = 0.0;
                    holdEntryLegTimingStarted = true;
                }
            }
            case ENTRY_PARALLEL_LEG -> {
                holdEntryLegElapsedSec += simDeltaSeconds;
                if (entryLegComplete()) {
                    holdPhase = HoldPhase.ENTRY_PARALLEL_INBOUND_TURN;
                    holdEntryForcedTurnDirection =
                            HoldingPatternGeometry.parallelInboundTurnDirection(holdTurnDirection);
                    holdEntryTurnAccumDeg = 0.0;
                }
            }
            case ENTRY_PARALLEL_INBOUND_TURN -> {
                if (holdEntryTurnAccumDeg >= HOLD_PARALLEL_MIN_TURN_DEG
                        && inboundAxisCaptured()
                        && headingCapture(heading, holdInboundCourseDeg)) {
                    holdPhase = HoldPhase.INBOUND_LEG;
                    holdEntryForcedTurnDirection = null;
                    previousDistanceToWaypoint = Double.MAX_VALUE;
                }
            }
            case OUTBOUND_TURN -> {
                if (headingCapture(heading, outbound)) {
                    holdPhase = HoldPhase.OUTBOUND_LEG;
                    holdOutboundLegElapsedSec = 0.0;
                    holdOutboundTimingStarted = false;
                    maybeStartOutboundTiming();
                }
            }
            case OUTBOUND_LEG -> {
                maybeStartOutboundTiming();
                if (holdOutboundTimingStarted) {
                    holdOutboundLegElapsedSec += simDeltaSeconds;
                }
                if (outboundLegComplete()) {
                    holdPhase = HoldPhase.INBOUND_TURN;
                }
            }
            case INBOUND_TURN -> {
                if (headingCapture(heading, holdInboundCourseDeg)) {
                    holdPhase = HoldPhase.INBOUND_LEG;
                    previousDistanceToWaypoint = Double.MAX_VALUE;
                }
            }
            default -> {
            }
        }
    }

    private boolean entryLegComplete() {
        if (!holdEntryLegTimingStarted) {
            return false;
        }
        if (holdLeg instanceof HoldLeg.TimedMinutes) {
            return holdEntryLegElapsedSec >= HoldingPatternGeometry.ENTRY_LEG_MINUTES * 60.0;
        }
        double groundSpeedKt = aircraftVector.groundSpeed.toDouble();
        double nmPerSec = Math.max(groundSpeedKt, HoldingPatternGeometry.MIN_GROUND_SPEED_KT_FOR_TIMED_LEG) / 3600.0;
        return holdEntryLegElapsedSec * nmPerSec >= holdFrozenEntryLegNm - HOLD_WAYPOINT_PASS_THRESHOLD_NM;
    }

    private boolean inboundCourseIntercepted() {
        return inboundAxisCaptured()
                && headingCapture(aircraftVector.heading.toDouble(), holdInboundCourseDeg);
    }

    private boolean inboundAxisCaptured() {
        if (holdingFix == null) {
            return false;
        }
        FixPosition here = new FixPosition(aircraftPosition.latitude, aircraftPosition.longitude);
        double xtk = HoldingPatternGeometry.crossTrackNm(holdingFix, here, holdInboundCourseDeg);
        return Math.abs(xtk) <= HOLD_INTERCEPT_XTK_NM;
    }

    private void maybeStartOutboundTiming() {
        if (holdOutboundTimingStarted || holdingFix == null) {
            return;
        }
        FixPosition here = new FixPosition(aircraftPosition.latitude, aircraftPosition.longitude);
        boolean abeam = HoldingPatternGeometry.isAbeamOutbound(
                here,
                holdingFix,
                holdInboundCourseDeg,
                holdTurnDirection,
                holdFrozenTurnRadiusNm,
                HOLD_ABEAM_TOLERANCE_NM);
        boolean rolledOut = headingCapture(
                aircraftVector.heading.toDouble(),
                HoldingPatternGeometry.outboundBearingDeg(holdInboundCourseDeg));
        // FAA AIP ENR 1.5: start at later of abeam/fix; if abeam unknown, at outbound turn complete.
        if (abeam || rolledOut) {
            holdOutboundTimingStarted = true;
        }
    }

    private boolean outboundLegComplete() {
        if (!holdOutboundTimingStarted || holdingFix == null) {
            return false;
        }
        if (holdLeg instanceof HoldLeg.TimedMinutes timed) {
            return holdOutboundLegElapsedSec >= timed.minutes() * 60.0;
        }
        FixPosition here = new FixPosition(aircraftPosition.latitude, aircraftPosition.longitude);
        FixPosition turnExit = HoldingPatternGeometry.outboundTurnExit(
                holdingFix, holdInboundCourseDeg, holdTurnDirection, holdFrozenTurnRadiusNm);
        double alongOutbound = HoldingPatternGeometry.alongTrackNm(
                turnExit, here, HoldingPatternGeometry.outboundBearingDeg(holdInboundCourseDeg));
        return alongOutbound >= holdFrozenOutboundLegNm - HOLD_WAYPOINT_PASS_THRESHOLD_NM;
    }

    private void refreshPredictedEntryProcedure() {
        if (holdEntryProcedureFrozen || holdingFix == null) {
            return;
        }
        holdEntryProcedure = HoldingPatternGeometry.classifyEntryProcedure(
                aircraftVector.heading.toDouble(), holdInboundCourseDeg, holdTurnDirection);
    }

    private static boolean headingCapture(double currentHeading, double targetHeading) {
        double delta = Math.abs(GeodeticUtils.normalizeAngle(currentHeading) - GeodeticUtils.normalizeAngle(targetHeading));
        delta = Math.min(delta, 360.0 - delta);
        return delta <= HOLD_HEADING_CAPTURE_DEG;
    }

    private Altitude resolveTargetAltitude() {
        if (navigationMode == NavigationMode.DIRECT_TO
                || navigationMode == NavigationMode.HOLDING
                || flightPlan == null) {
            return instructedVector.instructedAltitude;
        }
        double currentAlt = aircraftPosition.altitude.toDouble();
        return flightPlan.getNextWaypoint(currentWaypointIndex)
                .map(wp -> {
                    Altitude resolved = WaypointAltitudeTargetResolver.resolveTarget(wp, currentAlt);
                    if (resolved != null) {
                        return resolved;
                    }
                    return flightPlan.getCruiseAltitude();
                })
                .orElse(instructedVector.instructedAltitude);
    }

    protected double resolveEffectiveTargetGroundSpeedKnots() {
        if (instructedIndicatedAirspeedKnots == null || navigationMode != NavigationMode.HEADING) {
            return instructedVector.instructedGroundSpeed.toDouble();
        }
        return IndicatedAirspeedConverter.indicatedToTrueAirspeedKnots(
                instructedIndicatedAirspeedKnots,
                aircraftPosition.altitude.toDouble());
    }

    private GroundSpeed resolveTargetSpeed() {
        if (navigationMode == NavigationMode.DIRECT_TO
                || navigationMode == NavigationMode.HOLDING
                || flightPlan == null) {
            return instructedVector.instructedGroundSpeed;
        }
        return flightPlan.getNextWaypoint(currentWaypointIndex)
                .map(wp -> wp.getTargetSpeed() != null ? wp.getTargetSpeed() : flightPlan.getCruiseSpeed())
                .orElse(instructedVector.instructedGroundSpeed);
    }

    protected void applyWaypointPassCheck() {
        if (markTowerHandoffArrival()) {
            return;
        }
        FixPosition target = resolveNavigationTarget();
        if (target == null) {
            return;
        }
        double currentDistance = GeodeticUtils.distanceToFix(aircraftPosition, target);
        double threshold = navigationMode == NavigationMode.HOLDING
                ? HOLD_WAYPOINT_PASS_THRESHOLD_NM
                : calculateDynamicThreshold();

        boolean withinThreshold = currentDistance < threshold;
        boolean movingAway = currentDistance > previousDistanceToWaypoint;
        boolean passed = withinThreshold && movingAway;
        previousDistanceToWaypoint = currentDistance;

        if (WP_DEBUG && currentDistance < threshold * 2
                && (navigationMode == NavigationMode.FLIGHT_PLAN || navigationMode == NavigationMode.DIRECT_TO)) {
            String targetName = navigationMode == NavigationMode.DIRECT_TO ? directToFixName : flightPlan.getNextWaypoint(currentWaypointIndex).map(w -> w.getFixName()).orElse("?");
            System.out.printf("[WP_DEBUG] %s %s dist=%.4f NM thresh=%.4f NM within=%s movingAway=%s passed=%s%n",
                    callsign, targetName, currentDistance, threshold, withinThreshold, movingAway, passed);
        }

        if (!withinThreshold || !movingAway) {
            return;
        }

        if (WP_DEBUG) {
            System.out.printf("[WP_DEBUG] %s *** WAYPOINT PASSED ***%n", callsign);
        }

        if (navigationMode == NavigationMode.DIRECT_TO) {
            onDirectToTargetReached();
            return;
        }
        if (navigationMode == NavigationMode.HOLDING) {
            // Only fix passages advance the racetrack; outbound end is handled in advanceHoldingPhase.
            if (!holdingEntryCompleted
                    || holdPhase == HoldPhase.ENTRY
                    || holdPhase == HoldPhase.INBOUND_LEG) {
                onHoldingTargetReached();
            }
            return;
        }

        if (navigationMode == NavigationMode.FLIGHT_PLAN && flightPlan != null) {
            flightPlan.getNextWaypoint(currentWaypointIndex).ifPresent(this::onWaypointPassed);
        }
    }

    private void onDirectToTargetReached() {
        directToTarget = null;
        previousDistanceToWaypoint = Double.MAX_VALUE;
        if (finalApproachCoursePending && finalApproachCourseHeading != null) {
            finalApproachCoursePending = false;
            navigationMode = NavigationMode.HEADING;
            this.instructedVector = new InstructedVector(
                    new Heading(finalApproachCourseHeading),
                    instructedVector.instructedAltitude,
                    instructedVector.instructedGroundSpeed);
            directToFixName = null;
            resumeFlightPlanAfterDirectTo = false;
            return;
        }
        if (resumeFlightPlanAfterDirectTo && flightPlan != null && directToFixName != null) {
            int idx = flightPlan.findWaypointIndex(directToFixName);
            if (idx >= 0) {
                currentWaypointIndex = idx;
                navigationMode = NavigationMode.FLIGHT_PLAN;
            } else {
                navigationMode = NavigationMode.HEADING;
            }
            resumeFlightPlanAfterDirectTo = false;
        } else {
            navigationMode = NavigationMode.HEADING;
        }
        directToFixName = null;
    }

    private void onWaypointPassed(FlightPlanWaypoint wp) {
        if (!WaypointAltitudeTargetResolver.satisfiesConstraintAtPass(
                wp, aircraftPosition.altitude.toDouble())) {
            return;
        }
        if (wp.shouldRemoveAircraft()) {
            markedForRemoval = true;
            return;
        }
        currentWaypointIndex++;
        previousDistanceToWaypoint = Double.MAX_VALUE;
        if (currentWaypointIndex >= flightPlan.getWaypoints().size()) {
            navigationMode = NavigationMode.HEADING;
        }
    }

    private void onHoldingTargetReached() {
        if (holdingFix == null) {
            setResumeNavigation();
            return;
        }
        if (!holdingEntryCompleted || holdPhase == HoldPhase.ENTRY) {
            beginEntryOrRacetrackFromFix();
            return;
        }
        if (holdPhase == HoldPhase.INBOUND_LEG) {
            beginOutboundFromFix();
            return;
        }
        previousDistanceToWaypoint = Double.MAX_VALUE;
    }

    /**
     * First fix passage: freeze the entry procedure from the arrival heading, then either
     * join the racetrack (DIRECT) or fly the teardrop/parallel entry.
     */
    private void beginEntryOrRacetrackFromFix() {
        freezeHoldGeometryAtFix();
        double arrivalHeading = aircraftVector.heading.toDouble();
        this.holdEntryProcedure = HoldingPatternGeometry.classifyEntryProcedure(
                arrivalHeading, holdInboundCourseDeg, holdTurnDirection);
        this.holdEntryProcedureFrozen = true;
        this.holdingEntryCompleted = true;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        switch (holdEntryProcedure) {
            case DIRECT -> beginOutboundFromFix();
            case TEARDROP -> {
                this.holdPhase = HoldPhase.ENTRY_TEARDROP_TURN;
                this.holdEntryForcedTurnDirection = holdTurnDirection;
                this.holdEntryTurnAccumDeg = 0.0;
                this.holdEntryLegElapsedSec = 0.0;
                this.holdEntryLegTimingStarted = false;
            }
            case PARALLEL -> {
                this.holdPhase = HoldPhase.ENTRY_PARALLEL_OUTBOUND_TURN;
                this.holdEntryForcedTurnDirection =
                        HoldingPatternGeometry.parallelOutboundTurnDirection(holdTurnDirection);
                this.holdEntryTurnAccumDeg = 0.0;
                this.holdEntryLegElapsedSec = 0.0;
                this.holdEntryLegTimingStarted = false;
            }
        }
    }

    private void freezeHoldGeometryAtFix() {
        double groundSpeedKt = this.aircraftVector.groundSpeed.toDouble();
        this.holdFrozenTurnRateDegPerSec = HoldingPatternGeometry.holdTurnRateDegPerSec(groundSpeedKt);
        this.holdFrozenTurnRadiusNm = HoldingPatternGeometry.turnRadiusNm(
                groundSpeedKt, this.holdFrozenTurnRateDegPerSec);
        this.holdFrozenOutboundLegNm = HoldingPatternGeometry.outboundDistanceNm(holdLeg, groundSpeedKt);
        this.holdFrozenEntryLegNm = HoldingPatternGeometry.entryLegDistanceNm(holdLeg, groundSpeedKt);
        this.holdingOutboundTarget = HoldingPatternGeometry.outboundLegEnd(
                holdingFix,
                holdInboundCourseDeg,
                holdTurnDirection,
                holdLeg,
                groundSpeedKt,
                holdFrozenTurnRadiusNm);
    }

    /**
     * Freeze turn rate / radius / outbound length at the start of each outbound circuit
     * so flight and overlay stay aligned until the next fix passage.
     */
    private void beginOutboundFromFix() {
        freezeHoldGeometryAtFix();
        this.holdingEntryCompleted = true;
        this.holdPhase = HoldPhase.OUTBOUND_TURN;
        this.holdEntryForcedTurnDirection = null;
        this.holdEntryTurnAccumDeg = 0.0;
        this.holdOutboundLegElapsedSec = 0.0;
        this.holdOutboundTimingStarted = false;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
    }

    private double calculateDynamicThreshold() {
        double groundSpeedKnots = aircraftVector.groundSpeed.toDouble();
        double groundSpeedNmPerSec = groundSpeedKnots / 3600.0;
        double threshold = groundSpeedNmPerSec * WAYPOINT_PASS_SECONDS;
        return Math.max(WAYPOINT_PASS_THRESHOLD_MIN_NM, Math.min(threshold, WAYPOINT_PASS_THRESHOLD_MAX_NM));
    }

    @Override
    public boolean shouldBeRemovedFromSimulation() {
        return markedForRemoval;
    }

    @Override
    public double calculateTurnAngle(FixPosition fixPosition) {
        return flightBehavior.calculateTurnAngle(
            this.aircraftPosition,
            this.aircraftVector.heading.toDouble(),
            fixPosition
        );
    }

    @Override
    public boolean isEqualCallsign(Callsign callsign) {
        return this.callsign.equals(callsign);
    }

    // Getters
    @Override
    public Callsign getCallsign() {
        return this.callsign;
    }

    @Override
    public AircraftPosition getAircraftPosition() {
        return this.aircraftPosition;
    }

    @Override
    public AircraftVector getAircraftVector() {
        return this.aircraftVector;
    }

    @Override
    public InstructedVector getInstructedVector() {
        return this.instructedVector;
    }

    @Override
    public AircraftType getAircraftType() {
        return this.aircraftType;
    }

    public AircraftCharacteristics getCharacteristics() {
        return this.characteristics;
    }

    public FlightBehavior getFlightBehavior() {
        return this.flightBehavior;
    }

    // Setters
    public void setAircraftPosition(final AircraftPosition newAircraftPosition) {
        this.aircraftPosition = newAircraftPosition;
    }

    public void setAircraftVector(final AircraftVector newAircraftVector) {
        this.aircraftVector = newAircraftVector;
    }

    public void setInstructedVector(final InstructedVector newInstructedVector) {
        setInstructedVector(newInstructedVector, null);
    }

    public void setInstructedVector(final InstructedVector newInstructedVector, Integer indicatedAirspeedKnots) {
        requireApproachControl();
        this.instructedVector = newInstructedVector;
        this.instructedIndicatedAirspeedKnots = indicatedAirspeedKnots;
    }

    public Integer getInstructedIndicatedAirspeedKnots() {
        return instructedIndicatedAirspeedKnots;
    }

    public boolean isGoAroundActive() {
        return goAroundActive;
    }

    public boolean isTowerHandoffActive() {
        return towerHandoffActive;
    }

    /**
     * Release the aircraft to the tower without changing the current heading, altitude, or speed target.
     * The aircraft leaves the simulation once it reaches the runway threshold.
     */
    public void beginTowerHandoff(double thresholdLatitude, double thresholdLongitude) {
        requireApproachControl();
        if (goAroundActive) {
            throw new IllegalArgumentException("Cancel the go-around before handing off to the tower");
        }
        FixPosition threshold = new FixPosition(
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude(thresholdLatitude),
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude(thresholdLongitude));
        double distanceNm = GeodeticUtils.distanceToFix(aircraftPosition, threshold);
        if (navigationMode != NavigationMode.HEADING) {
            throw new IllegalArgumentException(
                    "Establish on the final approach course before tower handoff");
        }
        if (distanceNm > MAX_TOWER_HANDOFF_DISTANCE_NM) {
            throw new IllegalArgumentException(
                    "Aircraft is too far from the runway for tower handoff ("
                            + String.format("%.1f", distanceNm) + " NM, max "
                            + MAX_TOWER_HANDOFF_DISTANCE_NM + " NM)");
        }
        if (distanceNm > TOWER_HANDOFF_REMOVAL_NM
                && !maintainedTrackReachesThreshold(thresholdLatitude, thresholdLongitude)) {
            throw new IllegalArgumentException(
                    "Aircraft track does not reach the runway threshold");
        }
        this.towerThreshold = threshold;
        this.towerHandoffActive = true;
    }

    /**
     * Steps heading, indicated-airspeed conversion, vertical speed, and position in the same
     * order as a live tick. Acceptance is the removal radius, not a wider margin.
     */
    private boolean maintainedTrackReachesThreshold(double thresholdLatitude, double thresholdLongitude) {
        double lat = aircraftPosition.latitude.toDouble();
        double lon = aircraftPosition.longitude.toDouble();
        double altitude = aircraftPosition.altitude.toDouble();
        double heading = aircraftVector.heading.toDouble();
        double speed = aircraftVector.groundSpeed.toDouble();
        double verticalSpeed = aircraftVector.verticalSpeed.toDouble();
        double targetHeading = instructedVector.instructedHeading.toDouble();
        double targetAltitude = instructedVector.instructedAltitude.toDouble();
        double fallbackGroundSpeed = instructedVector.instructedGroundSpeed.toDouble();
        double previous = horizontalDistanceNm(lat, lon, thresholdLatitude, thresholdLongitude);
        double minimum = previous;
        int recedingSeconds = 0;
        for (int second = 0; second < TOWER_HANDOFF_TRACK_HORIZON_SECONDS; second++) {
            if (previous <= TOWER_HANDOFF_REMOVAL_NM) {
                return true;
            }
            heading = flightBehavior.calculateNextHeading(heading, targetHeading, characteristics.getMaxTurnRate())
                    .toDouble();
            double targetSpeed = instructedIndicatedAirspeedKnots == null
                    ? fallbackGroundSpeed
                    : IndicatedAirspeedConverter.indicatedToTrueAirspeedKnots(
                            instructedIndicatedAirspeedKnots, altitude);
            speed = flightBehavior.calculateNextGroundSpeed(
                    speed, targetSpeed, characteristics.getMaxAcceleration()).toDouble();
            verticalSpeed = flightBehavior.calculateNextVerticalSpeed(
                    altitude, targetAltitude, characteristics.getMaxClimbRate(), 1.0).toDouble();
            if (Math.abs(altitude - targetAltitude) <= 5.0 && Math.abs(verticalSpeed) <= 50.0) {
                altitude = targetAltitude;
                verticalSpeed = 0.0;
            }
            AircraftPosition stepped = PositionUtils.calculateNextPosition(
                    new AircraftPosition(
                            new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude(lat),
                            new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude(lon),
                            new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude(altitude)),
                    Math.max(0.0, speed),
                    heading,
                    verticalSpeed,
                    1.0);
            lat = stepped.latitude.toDouble();
            lon = stepped.longitude.toDouble();
            altitude = stepped.altitude.toDouble();
            double distanceNm = horizontalDistanceNm(lat, lon, thresholdLatitude, thresholdLongitude);
            minimum = Math.min(minimum, distanceNm);
            boolean aligned = smallestHeadingDelta(heading, targetHeading) < 1.0;
            if (aligned && distanceNm > previous) {
                recedingSeconds++;
                if (recedingSeconds >= 5) {
                    return minimum <= TOWER_HANDOFF_REMOVAL_NM;
                }
            } else {
                recedingSeconds = 0;
            }
            previous = distanceNm;
        }
        return minimum <= TOWER_HANDOFF_REMOVAL_NM;
    }

    private static double horizontalDistanceNm(double latitude, double longitude,
                                               double thresholdLatitude, double thresholdLongitude) {
        AircraftPosition from = new AircraftPosition(
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude(latitude),
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude(longitude),
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude(0));
        FixPosition threshold = new FixPosition(
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude(thresholdLatitude),
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude(thresholdLongitude));
        return GeodeticUtils.distanceToFix(from, threshold);
    }

    /** @return true when the aircraft has reached the threshold and is marked for removal */
    public boolean markTowerHandoffArrival() {
        if (!towerHandoffActive || towerThreshold == null) {
            return false;
        }
        double distanceNm = GeodeticUtils.distanceToFix(aircraftPosition, towerThreshold);
        if (distanceNm <= TOWER_HANDOFF_REMOVAL_NM) {
            markedForRemoval = true;
            return true;
        }
        return false;
    }

    /**
     * Abandon the approach and climb on the runway heading.
     * Keeps the current ground-speed target and any indicated-airspeed instruction.
     */
    public void beginGoAround(double runwayHeading, double climbAltitudeFeet) {
        requireApproachControl();
        double currentAlt = aircraftPosition.altitude.toDouble();
        if (climbAltitudeFeet < currentAlt + 500.0) {
            throw new IllegalArgumentException(
                    "Go-around climb altitude must be at least 500 ft above current altitude");
        }
        setNavigationMode(NavigationMode.HEADING);
        clearHoldingState();
        this.directToTarget = null;
        this.directToFixName = null;
        this.resumeFlightPlanAfterDirectTo = false;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        this.instructedVector = new InstructedVector(
                new Heading(runwayHeading),
                new Altitude(climbAltitudeFeet),
                this.instructedVector.instructedGroundSpeed);
        this.goAroundActive = true;
    }

    public void joinFinalApproachIntercept(FixPosition interceptPoint, String fixLabel, double approachCourseHeading) {
        requireApproachControl();
        clearFinalApproachInterceptState();
        finalApproachCoursePending = true;
        finalApproachCourseHeading = approachCourseHeading;
        applyDirectToNavigation(interceptPoint, fixLabel, false);
    }

    private void clearFinalApproachInterceptState() {
        finalApproachCoursePending = false;
        finalApproachCourseHeading = null;
    }

    public InstructedVector getAtcClearance() {
        return atcClearance;
    }

    public void setAtcClearance(final InstructedVector clearance) {
        requireApproachControl();
        this.atcClearance = clearance;
    }

    public boolean hasAtcClearance() {
        return atcClearance != null;
    }

    public String getSquawk() {
        return squawk;
    }

    public void setSquawk(String squawk) {
        requireApproachControl();
        this.squawk = squawk;
    }

    public void setFlightPlan(FlightPlan flightPlan) {
        requireApproachControl();
        clearGoAroundActive();
        this.flightPlan = flightPlan;
        this.currentWaypointIndex = 0;
        this.navigationMode = flightPlan != null ? NavigationMode.FLIGHT_PLAN : NavigationMode.HEADING;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        clearHoldingState();
    }

    public void setDirectTo(FixPosition target, String fixName, boolean resumeFlightPlan) {
        requireApproachControl();
        clearFinalApproachInterceptState();
        applyDirectToNavigation(target, fixName, resumeFlightPlan);
    }

    private void applyDirectToNavigation(FixPosition target, String fixName, boolean resumeFlightPlan) {
        clearGoAroundActive();
        this.directToTarget = target;
        this.directToFixName = fixName;
        this.resumeFlightPlanAfterDirectTo = resumeFlightPlan;
        this.navigationMode = NavigationMode.DIRECT_TO;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        clearHoldingState();
    }

    public void setHoldAtFix(FixPosition fix, HoldInstruction instruction) {
        requireApproachControl();
        clearGoAroundActive();
        clearFinalApproachInterceptState();
        this.holdingFix = fix;
        this.holdingFixName = instruction.fixName();
        this.holdTurnDirection = instruction.turnDirection();
        this.holdLeg = instruction.leg();
        this.holdInboundCourseDeg = HoldingPatternGeometry.resolveInboundCourseDeg(
                instruction.inboundCourseDeg(), instruction.fixName(), fix);
        this.holdPhase = HoldPhase.ENTRY;
        this.holdingEntryCompleted = false;
        this.holdEntryProcedureFrozen = false;
        double groundSpeedKt = this.aircraftVector.groundSpeed.toDouble();
        this.holdFrozenTurnRateDegPerSec = HoldingPatternGeometry.holdTurnRateDegPerSec(groundSpeedKt);
        this.holdFrozenTurnRadiusNm = HoldingPatternGeometry.turnRadiusNm(
                groundSpeedKt, this.holdFrozenTurnRateDegPerSec);
        this.holdFrozenOutboundLegNm = HoldingPatternGeometry.outboundDistanceNm(holdLeg, groundSpeedKt);
        this.holdFrozenEntryLegNm = HoldingPatternGeometry.entryLegDistanceNm(holdLeg, groundSpeedKt);
        this.holdingOutboundTarget = HoldingPatternGeometry.outboundLegEnd(
                fix,
                this.holdInboundCourseDeg,
                this.holdTurnDirection,
                this.holdLeg,
                groundSpeedKt,
                this.holdFrozenTurnRadiusNm);
        this.holdOutboundLegElapsedSec = 0.0;
        this.holdOutboundTimingStarted = false;
        this.holdEntryLegElapsedSec = 0.0;
        this.holdEntryLegTimingStarted = false;
        this.holdEntryForcedTurnDirection = null;
        this.holdEntryTurnAccumDeg = 0.0;
        this.holdEntryProcedure = HoldingPatternGeometry.classifyEntryProcedure(
                this.aircraftVector.heading.toDouble(),
                this.holdInboundCourseDeg,
                this.holdTurnDirection);
        this.resumeFlightPlanIndexAfterHold = computeResumeFlightPlanIndexAfterHold(instruction.fixName());
        this.navigationMode = NavigationMode.HOLDING;
        this.directToTarget = null;
        this.directToFixName = null;
        this.resumeFlightPlanAfterDirectTo = false;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
    }

    /** Exposed for geometry / turn-direction unit tests. */
    public HoldTurnDirection getHoldTurnDirection() {
        return holdTurnDirection;
    }

    public double getHoldInboundCourseDeg() {
        return holdInboundCourseDeg;
    }

    public HoldPhase getHoldPhase() {
        return holdPhase;
    }

    public HoldEntryProcedure getHoldEntryProcedure() {
        if (navigationMode != NavigationMode.HOLDING) {
            return null;
        }
        if (!holdEntryProcedureFrozen) {
            refreshPredictedEntryProcedure();
        }
        return holdEntryProcedure;
    }

    public HoldLeg getHoldLeg() {
        return holdLeg;
    }

    public String getHoldingFixName() {
        return holdingFixName;
    }

    public FixPosition getHoldingFix() {
        return holdingFix;
    }

    public double getHoldFrozenTurnRateDegPerSec() {
        return holdFrozenTurnRateDegPerSec;
    }

    public double getHoldFrozenTurnRadiusNm() {
        return holdFrozenTurnRadiusNm;
    }

    public double getHoldFrozenOutboundLegNm() {
        return holdFrozenOutboundLegNm;
    }

    public FixPosition getHoldingOutboundTarget() {
        return holdingOutboundTarget;
    }

    /**
     * Instructed racetrack vertices from the frozen geometry used for both flight and overlay.
     */
    public List<FixPosition> getHoldingPatternVertices() {
        if (navigationMode != NavigationMode.HOLDING || holdingFix == null) {
            return List.of();
        }
        return HoldingPatternGeometry.buildPattern(
                holdingFix,
                holdInboundCourseDeg,
                holdTurnDirection,
                holdFrozenOutboundLegNm,
                holdFrozenTurnRadiusNm);
    }

    public void setResumeNavigation() {
        requireApproachControl();
        clearGoAroundActive();
        clearFinalApproachInterceptState();
        this.directToTarget = null;
        this.directToFixName = null;
        this.resumeFlightPlanAfterDirectTo = false;
        if (flightPlan != null) {
            if (resumeFlightPlanIndexAfterHold >= flightPlan.getWaypoints().size()) {
                this.navigationMode = NavigationMode.HEADING;
                clearHoldingState();
                return;
            }
            if (resumeFlightPlanIndexAfterHold >= 0) {
                this.currentWaypointIndex = resumeFlightPlanIndexAfterHold;
            }
            this.navigationMode = NavigationMode.FLIGHT_PLAN;
            clearHoldingState();
            return;
        }
        // No flight plan: still end the hold (and any prior HOLDING) so Resume always clears overlay.
        this.navigationMode = NavigationMode.HEADING;
        clearHoldingState();
    }

    private void clearGoAroundActive() {
        this.goAroundActive = false;
    }

    private void requireApproachControl() {
        if (towerHandoffActive) {
            throw new ApproachControlReleasedException(callsign.toString());
        }
    }

    private static double smallestHeadingDelta(double left, double right) {
        double delta = Math.abs(GeodeticUtils.normalizeAngle(left) - GeodeticUtils.normalizeAngle(right));
        return Math.min(delta, 360.0 - delta);
    }

    private void clearHoldingState() {
        this.holdingFix = null;
        this.holdingFixName = null;
        this.holdingOutboundTarget = null;
        this.holdTurnDirection = HoldTurnDirection.RIGHT;
        this.holdLeg = new HoldLeg.DistanceNm(HoldLeg.DEFAULT_OUTBOUND_LEG_NM);
        this.holdInboundCourseDeg = 0.0;
        this.holdPhase = HoldPhase.ENTRY;
        this.holdingEntryCompleted = false;
        this.holdEntryProcedure = HoldEntryProcedure.DIRECT;
        this.holdEntryProcedureFrozen = false;
        this.holdFrozenTurnRateDegPerSec = HoldingPatternGeometry.STANDARD_RATE_DEG_PER_SEC;
        this.holdFrozenTurnRadiusNm = 0.0;
        this.holdFrozenOutboundLegNm = HoldLeg.DEFAULT_OUTBOUND_LEG_NM;
        this.holdFrozenEntryLegNm = HoldLeg.DEFAULT_OUTBOUND_LEG_NM;
        this.holdOutboundLegElapsedSec = 0.0;
        this.holdOutboundTimingStarted = false;
        this.holdEntryLegElapsedSec = 0.0;
        this.holdEntryLegTimingStarted = false;
        this.holdEntryForcedTurnDirection = null;
        this.holdEntryTurnAccumDeg = 0.0;
        this.resumeFlightPlanIndexAfterHold = -1;
    }

    private int computeResumeFlightPlanIndexAfterHold(String fixName) {
        if (flightPlan == null) {
            return -1;
        }
        if (fixName == null || fixName.isBlank()) {
            return currentWaypointIndex;
        }
        int holdFixIndex = flightPlan.findWaypointIndex(fixName);
        if (holdFixIndex < 0) {
            return currentWaypointIndex;
        }
        int nextIndex = holdFixIndex + 1;
        int waypointCount = flightPlan.getWaypoints().size();
        if (waypointCount <= 0) {
            return -1;
        }
        if (nextIndex >= waypointCount) {
            return waypointCount;
        }
        return nextIndex;
    }

    private Heading calculateNextHeadingRightTurn(double currentHeading, double targetHeading, double maxTurnRate) {
        double clockwiseDelta = GeodeticUtils.normalizeAngle(targetHeading - currentHeading);
        double turnAmount = Math.min(maxTurnRate, clockwiseDelta);
        return new Heading(GeodeticUtils.normalizeAngle(currentHeading + turnAmount));
    }

    private Heading calculateNextHeadingLeftTurn(double currentHeading, double targetHeading, double maxTurnRate) {
        double counterClockwiseDelta = GeodeticUtils.normalizeAngle(currentHeading - targetHeading);
        double turnAmount = Math.min(maxTurnRate, counterClockwiseDelta);
        return new Heading(GeodeticUtils.normalizeAngle(currentHeading - turnAmount));
    }

    /** Continuous rate turn used when the maneuver must exceed 180° (parallel re-intercept). */
    private Heading calculateNextHeadingContinuousTurn(
            double currentHeading, HoldTurnDirection direction, double maxTurnRate) {
        if (direction == HoldTurnDirection.RIGHT) {
            return new Heading(GeodeticUtils.normalizeAngle(currentHeading + maxTurnRate));
        }
        return new Heading(GeodeticUtils.normalizeAngle(currentHeading - maxTurnRate));
    }

    public void setNavigationMode(NavigationMode mode) {
        requireApproachControl();
        clearGoAroundActive();
        if (mode != NavigationMode.DIRECT_TO) {
            clearFinalApproachInterceptState();
        }
        this.navigationMode = mode;
    }

    public FlightPlan getFlightPlan() {
        return flightPlan;
    }

    public NavigationMode getNavigationMode() {
        return navigationMode;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    public void setCurrentWaypointIndex(int index) {
        requireApproachControl();
        this.currentWaypointIndex = index;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
    }

    /**
     * 航空機の詳細情報を取得（デバッグ用）
     */
    public String getDetailedInfo() {
        return String.format("%s - %s (Category: %s, MaxSpeed: %.1f knots)",
            callsign, aircraftType, characteristics.getCategory().getDescription(), characteristics.getMaxSpeed());
    }

    /**
     * フロントエンドのレーダー表示用フォーマットで航空機情報を出力
     * 既存のフロントエンドとの互換性を保つため
     */
    public String toRadarString() {
        return StringUtils.formatAircraftBaseInfo(
            callsign.toString(),
            aircraftPosition.latitude.toDouble(), aircraftPosition.longitude.toDouble(), aircraftPosition.altitude.toDouble(),
            aircraftVector.heading.toDouble(), aircraftVector.groundSpeed.toDouble(), aircraftVector.verticalSpeed.toDouble(),
            instructedVector.instructedHeading.toDouble(), instructedVector.instructedGroundSpeed.toDouble(), instructedVector.instructedAltitude.toDouble(),
            getAircraftCategory(), aircraftType.toString()
        ) + "}";
    }

    /**
     * 航空機カテゴリを取得（サブクラスでオーバーライド可能）
     */
    protected String getAircraftCategory() {
        return characteristics.getCategory().name();
    }
}
