package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.exception.ApproachControlReleasedException;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.behavior.FlightBehavior;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.characteristics.AircraftCharacteristics;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.guidance.Guidance;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.guidance.KinematicStep;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.guidance.TurnConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.navigation.LateralNavigation;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlan;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlanWaypoint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldEntryProcedure;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldInstruction;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldPhase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldTurnDirection;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldingPatternGeometry;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.ProcedureVerticalGuidance;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.WaypointAltitudeTargetResolver;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureVaConstraint;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atmosphere.WindTriangle;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.departure.InitialClimbGeometry;
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
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.IndicatedAirspeedConverter;
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
    /** Single source of truth for lateral navigation (mode + VA + climb + go-around + final + handoff). */
    protected LateralNavigation lateral;
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
    private UniformWind ambientWind = UniformWind.missing();
    /**
     * When true (after {@link #assignProcedureRoute}), vertical targeting look-ahead applies.
     * Plain {@link #setFlightPlan} leaves this false so off-procedure motion stays unchanged.
     */
    private boolean procedureVerticalGuidance;
    private final Set<String> unreachableAltitudeConstraintFixes = new LinkedHashSet<>();

    public static final double MAX_TOWER_HANDOFF_DISTANCE_NM = 10.0;
    public static final double TOWER_HANDOFF_REMOVAL_NM = 0.5;
    private static final int TOWER_HANDOFF_TRACK_HORIZON_SECONDS = 20 * 60;

    private static final double WAYPOINT_PASS_THRESHOLD_MIN_NM = 1.5;
    private static final double WAYPOINT_PASS_THRESHOLD_MAX_NM = 5.0;
    private static final double WAYPOINT_PASS_SECONDS = 5.0;
    private static final double HOLD_WAYPOINT_PASS_THRESHOLD_NM = 0.25;
    /** First ENTRY arrival under wind; wider than the calm 0.25 NM racetrack threshold. */
    private static final double HOLD_ENTRY_PASS_THRESHOLD_NM = 1.0;
    /** Inbound fix passage allows up to this |xtk| when along-track crosses zero. */
    private static final double HOLD_INBOUND_PASS_XTK_NM = 1.0;
    private static final double HOLD_ABEAM_TOLERANCE_NM = 0.15;
    private static final double HOLD_INTERCEPT_XTK_NM = 0.5;
    private static final double HOLD_PARALLEL_MIN_TURN_DEG = 180.0;
    /** Along-track NM from the holding fix on the inbound course (for abeam crossing). */
    private double previousHoldAlongTrackNm = Double.NaN;

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
        this.lateral = new LateralNavigation.PlainHeading();
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
        maybeReleaseInitialClimbTurn();
        maybeCompleteProcedureVa();
        updateInstructedVectorFromNavigation();
        advanceHoldingPhase(simDeltaSeconds);

        Guidance guidance = resolveCurrentGuidance();
        TurnConstraint turnConstraint = resolveTurnConstraint();
        double headingBefore = this.aircraftVector.heading.toDouble();

        Integer iasForStep = lateral.usesInstructedIndicatedAirspeed()
                ? instructedIndicatedAirspeedKnots
                : null;
        KinematicStep.State before = KinematicStep.State.from(aircraftPosition, aircraftVector);
        KinematicStep.State after = KinematicStep.next(
                before,
                guidance,
                ambientWind,
                new KinematicStep.Targets(
                        instructedVector.instructedAltitude.toDouble(),
                        instructedVector.instructedGroundSpeed.toDouble(),
                        iasForStep),
                new KinematicStep.Limits(
                        resolveActiveTurnRateDegPerSec(),
                        characteristics.getMaxAcceleration(),
                        characteristics.getMaxClimbRate()),
                turnConstraint,
                simDeltaSeconds);

        if (lateral instanceof LateralNavigation.HoldingNav && holdEntryForcedTurnDirection != null) {
            holdEntryTurnAccumDeg += Math.abs(
                    HoldingPatternGeometry.signedHeadingDeltaDeg(headingBefore, after.headingDeg()));
        }

        this.aircraftVector = after.toVector();
        if (Math.abs(before.altitudeFt() - instructedVector.instructedAltitude.toDouble()) <= 5.0
                && Math.abs(after.verticalSpeedFpm()) <= 50.0) {
            this.aircraftPosition = new AircraftPosition(
                    this.aircraftPosition.latitude,
                    this.aircraftPosition.longitude,
                    this.instructedVector.instructedAltitude);
            this.aircraftVector = new AircraftVector(
                    new Heading(after.headingDeg()),
                    new GroundSpeed(after.groundSpeedKt()),
                    new VerticalSpeed(0.0),
                    after.trackDeg(),
                    after.trueAirspeedKt());
        }
    }

    private double resolveActiveTurnRateDegPerSec() {
        if (lateral instanceof LateralNavigation.HoldingNav && holdingEntryCompleted) {
            return holdFrozenTurnRateDegPerSec;
        }
        return this.characteristics.getMaxTurnRate();
    }

    private TurnConstraint resolveTurnConstraint() {
        if (!(lateral instanceof LateralNavigation.HoldingNav) || !holdingEntryCompleted) {
            return TurnConstraint.SHORTEST;
        }
        boolean parallelInbound = holdPhase == HoldPhase.ENTRY_PARALLEL_INBOUND_TURN
                && holdEntryForcedTurnDirection != null;
        boolean parallelContinuous = parallelInbound
                && !(holdEntryTurnAccumDeg >= HOLD_PARALLEL_MIN_TURN_DEG && inboundAxisCaptured());
        if (parallelContinuous) {
            return holdEntryForcedTurnDirection == HoldTurnDirection.RIGHT
                    ? TurnConstraint.CONTINUOUS_RIGHT
                    : TurnConstraint.CONTINUOUS_LEFT;
        }
        if (parallelInbound) {
            // Axis reached after >180°: shortest turn to roll out on inbound.
            return TurnConstraint.SHORTEST;
        }
        HoldTurnDirection forced = holdEntryForcedTurnDirection != null
                ? holdEntryForcedTurnDirection
                : ((holdPhase == HoldPhase.OUTBOUND_TURN || holdPhase == HoldPhase.INBOUND_TURN)
                        ? holdTurnDirection
                        : null);
        if (forced == HoldTurnDirection.RIGHT) {
            return TurnConstraint.FORCE_RIGHT;
        }
        if (forced == HoldTurnDirection.LEFT) {
            return TurnConstraint.FORCE_LEFT;
        }
        return TurnConstraint.SHORTEST;
    }

    /**
     * Exactly one guidance kind for this tick. WCA applies only for {@link Guidance.TrackCourse}.
     */
    private Guidance resolveCurrentGuidance() {
        return switch (lateral) {
            case LateralNavigation.ProcedureVa va -> new Guidance.FlyHeading(va.courseTrueDeg());
            case LateralNavigation.FinalApproach fa -> {
                // Anchor at the aircraft so xtk≈0: maintain course with WCA (JOIN FINAL).
                FixPosition anchor = new FixPosition(aircraftPosition.latitude, aircraftPosition.longitude);
                yield new Guidance.TrackCourse(fa.courseHeadingDeg(), anchor);
            }
            case LateralNavigation.TowerHandoff handoff -> {
                if (handoff.approachCourseHeadingDeg() != null) {
                    FixPosition anchor = new FixPosition(aircraftPosition.latitude, aircraftPosition.longitude);
                    yield new Guidance.TrackCourse(handoff.approachCourseHeadingDeg(), anchor);
                }
                yield new Guidance.FlyHeading(instructedVector.instructedHeading.toDouble());
            }
            case LateralNavigation.DirectToNav d when directToTarget != null ->
                    new Guidance.SteerTo(directToTarget);
            case LateralNavigation.FlightPlanNav f when flightPlan != null ->
                    flightPlan.getNextWaypoint(currentWaypointIndex)
                            .map(wp -> (Guidance) new Guidance.SteerTo(wp.getPosition()))
                            .orElseGet(() ->
                                    new Guidance.FlyHeading(instructedVector.instructedHeading.toDouble()));
            case LateralNavigation.HoldingNav h when holdingFix != null -> {
                if (!holdingEntryCompleted || holdPhase == HoldPhase.ENTRY) {
                    yield new Guidance.SteerTo(holdingFix);
                }
                yield resolveHoldingGuidance();
            }
            case LateralNavigation.PlainHeading h ->
                    new Guidance.FlyHeading(instructedVector.instructedHeading.toDouble());
            case LateralNavigation.InitialClimb i ->
                    new Guidance.FlyHeading(instructedVector.instructedHeading.toDouble());
            case LateralNavigation.GoAround g ->
                    new Guidance.FlyHeading(instructedVector.instructedHeading.toDouble());
            case LateralNavigation.DirectToNav d ->
                    new Guidance.FlyHeading(instructedVector.instructedHeading.toDouble());
            case LateralNavigation.FlightPlanNav f ->
                    new Guidance.FlyHeading(instructedVector.instructedHeading.toDouble());
            case LateralNavigation.HoldingNav h ->
                    new Guidance.FlyHeading(instructedVector.instructedHeading.toDouble());
        };
    }

    private Guidance resolveHoldingGuidance() {
        double outbound = HoldingPatternGeometry.outboundBearingDeg(holdInboundCourseDeg);
        double teardrop = HoldingPatternGeometry.teardropHeadingDeg(holdInboundCourseDeg, holdTurnDirection);
        FixPosition turnExit = HoldingPatternGeometry.outboundTurnExit(
                holdingFix, holdInboundCourseDeg, holdTurnDirection, holdFrozenTurnRadiusNm);
        return switch (holdPhase) {
            case ENTRY_TEARDROP_TURN -> new Guidance.FlyHeading(teardrop);
            // Entry straight legs keep FlyHeading so AIM entry geometry matches no-wind tests;
            // racetrack legs use TrackCourse (WCA + XTK).
            case ENTRY_TEARDROP_LEG -> new Guidance.FlyHeading(teardrop);
            case ENTRY_TEARDROP_INBOUND_TURN, ENTRY_PARALLEL_INBOUND_TURN, INBOUND_TURN ->
                new Guidance.FlyHeading(holdInboundCourseDeg);
            case ENTRY_PARALLEL_OUTBOUND_TURN, OUTBOUND_TURN -> new Guidance.FlyHeading(outbound);
            case ENTRY_PARALLEL_LEG -> new Guidance.FlyHeading(outbound);
            case OUTBOUND_LEG -> new Guidance.TrackCourse(outbound, turnExit);
            case INBOUND_LEG -> new Guidance.TrackCourse(holdInboundCourseDeg, holdingFix);
            case ENTRY -> new Guidance.SteerTo(holdingFix);
        };
    }

    private void updateInstructedVectorFromNavigation() {
        switch (lateral) {
            case LateralNavigation.ProcedureVa va -> {
                refreshProcedureVaInstructedVector();
                return;
            }
            case LateralNavigation.PlainHeading h -> {
                return;
            }
            case LateralNavigation.FinalApproach f -> {
                return;
            }
            case LateralNavigation.InitialClimb i -> {
                return;
            }
            case LateralNavigation.GoAround g -> {
                return;
            }
            case LateralNavigation.TowerHandoff t -> {
                return;
            }
            case LateralNavigation.HoldingNav h when holdingFix != null && holdingEntryCompleted -> {
                updateInstructedVectorForHolding();
                return;
            }
            case LateralNavigation.HoldingNav h when holdingFix != null && !holdingEntryCompleted ->
                    refreshPredictedEntryProcedure();
            default -> {
            }
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
        Guidance guidance = resolveHoldingGuidance();
        double heading = switch (guidance) {
            case Guidance.FlyHeading fly -> fly.headingDeg();
            case Guidance.TrackCourse track -> track.courseDeg();
            case Guidance.SteerTo steer -> flightBehavior.calculateTurnAngle(
                    aircraftPosition, aircraftVector.heading.toDouble(), steer.fix());
        };
        this.instructedVector = new InstructedVector(new Heading(heading), targetAltitude, targetSpeed);
    }

    private FixPosition resolveNavigationTarget() {
        return switch (lateral) {
            case LateralNavigation.DirectToNav d when directToTarget != null -> directToTarget;
            case LateralNavigation.FlightPlanNav f when flightPlan != null ->
                    flightPlan.getNextWaypoint(currentWaypointIndex)
                            .map(FlightPlanWaypoint::getPosition)
                            .orElse(null);
            case LateralNavigation.HoldingNav h when holdingFix != null -> {
                if (!holdingEntryCompleted || holdPhase == HoldPhase.ENTRY || holdPhase == HoldPhase.INBOUND_LEG) {
                    yield holdingFix;
                }
                if (holdPhase == HoldPhase.OUTBOUND_LEG) {
                    yield holdingOutboundTarget;
                }
                yield null;
            }
            default -> null;
        };
    }

    private void advanceHoldingPhase(double simDeltaSeconds) {
        if (!(lateral instanceof LateralNavigation.HoldingNav) || !holdingEntryCompleted || holdingFix == null) {
            return;
        }
        double heading = aircraftVector.heading.toDouble();
        double outbound = HoldingPatternGeometry.outboundBearingDeg(holdInboundCourseDeg);
        double teardrop = HoldingPatternGeometry.teardropHeadingDeg(holdInboundCourseDeg, holdTurnDirection);
        switch (holdPhase) {
            case ENTRY_TEARDROP_TURN -> {
                if (holdTurnCaptured(heading, teardrop)) {
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
                    enterInboundLeg();
                }
            }
            case ENTRY_PARALLEL_OUTBOUND_TURN -> {
                if (holdTurnCaptured(heading, outbound)) {
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
                        && holdTurnCaptured(heading, holdInboundCourseDeg)) {
                    enterInboundLeg();
                }
            }
            case OUTBOUND_TURN -> {
                if (holdTurnCaptured(heading, outbound)) {
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
                if (holdTurnCaptured(heading, holdInboundCourseDeg)) {
                    enterInboundLeg();
                }
            }
            default -> {
            }
        }
    }

    private void enterInboundLeg() {
        holdPhase = HoldPhase.INBOUND_LEG;
        holdEntryForcedTurnDirection = null;
        previousDistanceToWaypoint = Double.MAX_VALUE;
        FixPosition here = new FixPosition(aircraftPosition.latitude, aircraftPosition.longitude);
        previousHoldAlongTrackNm =
                HoldingPatternGeometry.alongTrackNm(holdingFix, here, holdInboundCourseDeg);
    }

    /**
     * Turn complete when heading matches the FlyHeading target (the course).
     * Track-only capture is not used: under wind track can reach the course while heading is still
     * catching up, which would enter OUTBOUND_LEG before rollout and never start leg timing.
     */
    private boolean holdTurnCaptured(double headingDeg, double courseDeg) {
        return KinematicStep.headingCaptured(headingDeg, courseDeg);
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
                && holdTurnCaptured(aircraftVector.heading.toDouble(), holdInboundCourseDeg);
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
        double outbound = HoldingPatternGeometry.outboundBearingDeg(holdInboundCourseDeg);
        boolean rolledOut = holdTurnCaptured(aircraftVector.heading.toDouble(), outbound);
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
        return KinematicStep.headingCaptured(currentHeading, targetHeading);
    }

    private Altitude resolveTargetAltitude() {
        NavigationMode mode = lateral.projectedNavigationMode();
        if (mode == NavigationMode.DIRECT_TO
                || mode == NavigationMode.HOLDING
                || flightPlan == null) {
            return instructedVector.instructedAltitude;
        }
        if (procedureVerticalGuidance) {
            List<FlightPlanWaypoint> remaining =
                    ProcedureVerticalGuidance.remainingFrom(flightPlan, currentWaypointIndex);
            ProcedureVerticalGuidance.Resolution resolution = ProcedureVerticalGuidance.resolve(
                    remaining,
                    aircraftPosition,
                    flightPlan.getCruiseAltitude().toDouble(),
                    characteristics.getMaxClimbRate(),
                    aircraftVector.groundSpeed.toDouble(),
                    unreachableAltitudeConstraintFixes);
            unreachableAltitudeConstraintFixes.addAll(resolution.newlyUnreachableFixes());
            return resolution.targetAltitude();
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

    protected double resolveEffectiveTargetTrueAirspeedKnots(double headingDeg) {
        if (instructedIndicatedAirspeedKnots != null
                && lateral.projectedNavigationMode() == NavigationMode.HEADING) {
            return IndicatedAirspeedConverter.indicatedToTrueAirspeedKnots(
                    instructedIndicatedAirspeedKnots,
                    aircraftPosition.altitude.toDouble());
        }
        double instructedGroundSpeedKnots = instructedVector.instructedGroundSpeed.toDouble();
        if (!ambientWind.affectsMotion()) {
            return instructedGroundSpeedKnots;
        }
        return WindTriangle.trueAirspeedForGroundSpeed(headingDeg, instructedGroundSpeedKnots, ambientWind);
    }

    protected double resolveEffectiveTargetGroundSpeedKnots() {
        return resolveEffectiveTargetTrueAirspeedKnots(aircraftVector.heading.toDouble());
    }

    public UniformWind getAmbientWind() {
        return ambientWind;
    }

    public void setAmbientWind(UniformWind wind) {
        UniformWind next = wind != null ? wind : UniformWind.missing();
        if (ambientWind.equals(next)) {
            return;
        }
        ambientWind = next;
        double tas = aircraftVector.trueAirspeedKnots;
        WindTriangle.GroundVelocity gv = WindTriangle.groundVelocity(
                aircraftVector.heading.toDouble(), tas, ambientWind);
        aircraftVector = new AircraftVector(
                aircraftVector.heading,
                new GroundSpeed(gv.groundSpeedKt()),
                aircraftVector.verticalSpeed,
                gv.trackDeg(),
                tas);
    }

    private GroundSpeed resolveTargetSpeed() {
        NavigationMode mode = lateral.projectedNavigationMode();
        if (mode == NavigationMode.DIRECT_TO
                || mode == NavigationMode.HOLDING
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
        if (lateral instanceof LateralNavigation.HoldingNav) {
            if (!holdingEntryCompleted || holdPhase == HoldPhase.ENTRY) {
                double currentDistance = GeodeticUtils.distanceToFix(aircraftPosition, target);
                boolean withinThreshold = currentDistance < HOLD_ENTRY_PASS_THRESHOLD_NM;
                boolean movingAway = currentDistance > previousDistanceToWaypoint;
                previousDistanceToWaypoint = currentDistance;
                if (withinThreshold && movingAway) {
                    onHoldingTargetReached();
                }
                return;
            }
            if (holdPhase == HoldPhase.INBOUND_LEG && holdingFix != null) {
                FixPosition here = new FixPosition(aircraftPosition.latitude, aircraftPosition.longitude);
                double along = HoldingPatternGeometry.alongTrackNm(holdingFix, here, holdInboundCourseDeg);
                double xtk = HoldingPatternGeometry.crossTrackNm(holdingFix, here, holdInboundCourseDeg);
                boolean crossed = !Double.isNaN(previousHoldAlongTrackNm)
                        && previousHoldAlongTrackNm < 0.0
                        && along >= 0.0
                        && Math.abs(xtk) <= HOLD_INBOUND_PASS_XTK_NM;
                previousHoldAlongTrackNm = along;
                previousDistanceToWaypoint = GeodeticUtils.distanceToFix(aircraftPosition, holdingFix);
                if (crossed) {
                    onHoldingTargetReached();
                }
                return;
            }
            // Outbound end is handled in advanceHoldingPhase.
            return;
        }

        double currentDistance = GeodeticUtils.distanceToFix(aircraftPosition, target);
        double threshold = calculateDynamicThreshold();

        boolean withinThreshold = currentDistance < threshold;
        boolean movingAway = currentDistance > previousDistanceToWaypoint;
        boolean passed = withinThreshold && movingAway;
        previousDistanceToWaypoint = currentDistance;

        if (WP_DEBUG && currentDistance < threshold * 2
                && (lateral instanceof LateralNavigation.FlightPlanNav
                        || lateral instanceof LateralNavigation.DirectToNav)) {
            String targetName = lateral instanceof LateralNavigation.DirectToNav
                    ? directToFixName
                    : flightPlan.getNextWaypoint(currentWaypointIndex).map(w -> w.getFixName()).orElse("?");
            System.out.printf("[WP_DEBUG] %s %s dist=%.4f NM thresh=%.4f NM within=%s movingAway=%s passed=%s%n",
                    callsign, targetName, currentDistance, threshold, withinThreshold, movingAway, passed);
        }

        if (!withinThreshold || !movingAway) {
            return;
        }

        if (WP_DEBUG) {
            System.out.printf("[WP_DEBUG] %s *** WAYPOINT PASSED ***%n", callsign);
        }

        if (lateral instanceof LateralNavigation.DirectToNav) {
            onDirectToTargetReached();
            return;
        }

        if (lateral instanceof LateralNavigation.FlightPlanNav && flightPlan != null) {
            flightPlan.getNextWaypoint(currentWaypointIndex).ifPresent(this::onWaypointPassed);
        }
    }

    private void onDirectToTargetReached() {
        previousDistanceToWaypoint = Double.MAX_VALUE;
        if (lateral instanceof LateralNavigation.DirectToNav d
                && d.approachCoursePending()
                && d.approachCourseHeadingDeg() != null) {
            double course = d.approachCourseHeadingDeg();
            this.lateral = new LateralNavigation.FinalApproach(course, false);
            this.instructedVector = new InstructedVector(
                    new Heading(course),
                    instructedVector.instructedAltitude,
                    instructedVector.instructedGroundSpeed);
            directToTarget = null;
            directToFixName = null;
            resumeFlightPlanAfterDirectTo = false;
            return;
        }
        directToTarget = null;
        if (resumeFlightPlanAfterDirectTo && flightPlan != null && directToFixName != null) {
            int idx = flightPlan.findWaypointIndex(directToFixName);
            if (idx >= 0) {
                currentWaypointIndex = idx;
                this.lateral = new LateralNavigation.FlightPlanNav();
            } else {
                this.lateral = new LateralNavigation.PlainHeading();
            }
            resumeFlightPlanAfterDirectTo = false;
        } else {
            this.lateral = new LateralNavigation.PlainHeading();
        }
        directToFixName = null;
    }

    private void onWaypointPassed(FlightPlanWaypoint wp) {
        double altitudeFt = aircraftPosition.altitude.toDouble();
        if (!WaypointAltitudeTargetResolver.satisfiesConstraintAtPass(wp, altitudeFt)) {
            return;
        }
        // Domain pass tolerances are wider than published intent. Keep/raise the unreachable
        // flag when the fly-by altitude still misses the published constraint tightly.
        if (WaypointAltitudeTargetResolver.meetsPublishedConstraint(wp, altitudeFt)) {
            unreachableAltitudeConstraintFixes.remove(wp.getFixName());
        } else if (wp.hasAltitudeConstraint()) {
            unreachableAltitudeConstraintFixes.add(wp.getFixName());
        }
        if (wp.shouldRemoveAircraft()) {
            markedForRemoval = true;
            return;
        }
        currentWaypointIndex++;
        previousDistanceToWaypoint = Double.MAX_VALUE;
        if (currentWaypointIndex >= flightPlan.getWaypoints().size()) {
            this.lateral = new LateralNavigation.PlainHeading();
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
        LateralNavigation nextLateral = switch (lateral) {
            case LateralNavigation.FinalApproach f -> new LateralNavigation.PlainHeading();
            case LateralNavigation.DirectToNav d when d.approachCourseHeadingDeg() != null ->
                    LateralNavigation.DirectToNav.plain();
            default -> lateral;
        };
        this.instructedVector = newInstructedVector;
        this.instructedIndicatedAirspeedKnots = indicatedAirspeedKnots;
        this.lateral = nextLateral;
    }

    public Integer getInstructedIndicatedAirspeedKnots() {
        return instructedIndicatedAirspeedKnots;
    }

    public boolean isGoAroundActive() {
        return lateral.projectedGoAround();
    }

    public boolean isInitialClimbActive() {
        return lateral.projectedInitialClimb();
    }

    public Double getInitialClimbStartLatitude() {
        return lateral instanceof LateralNavigation.InitialClimb climb ? climb.startLatitude() : null;
    }

    public Double getInitialClimbStartLongitude() {
        return lateral instanceof LateralNavigation.InitialClimb climb ? climb.startLongitude() : null;
    }

    public Double getInitialClimbRunwayHeadingDeg() {
        return lateral instanceof LateralNavigation.InitialClimb climb ? climb.runwayHeadingDeg() : null;
    }

    public Double getDeferredInitialHeadingDeg() {
        return lateral instanceof LateralNavigation.InitialClimb climb
                ? climb.deferredInitialHeadingDeg()
                : null;
    }

    public boolean isTowerHandoffActive() {
        return lateral.projectedTowerHandoff();
    }

    /** True while JOIN FINAL is Direct-To the intercept (approach course not yet established). */
    public boolean isFinalApproachCoursePending() {
        return lateral.approachCoursePending();
    }

    /**
     * Release the aircraft to the tower without changing the current heading, altitude, or speed target.
     * The aircraft leaves the simulation once it reaches the runway threshold.
     */
    public void beginTowerHandoff(double thresholdLatitude, double thresholdLongitude) {
        requireApproachControl();
        if (lateral instanceof LateralNavigation.GoAround) {
            throw new IllegalArgumentException("Cancel the go-around before handing off to the tower");
        }
        FixPosition threshold = new FixPosition(
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude(thresholdLatitude),
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude(thresholdLongitude));
        double distanceNm = GeodeticUtils.distanceToFix(aircraftPosition, threshold);
        if (lateral.projectedNavigationMode() != NavigationMode.HEADING) {
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
        Double approachCourseFromCurrent = lateral.approachCourseHeadingOrNull();
        this.lateral = new LateralNavigation.TowerHandoff(threshold, approachCourseFromCurrent);
    }

    /**
     * Steps with the same {@link KinematicStep} as the live tick (guidance + wind + track).
     * Acceptance is the removal radius, not a wider margin.
     */
    private boolean maintainedTrackReachesThreshold(double thresholdLatitude, double thresholdLongitude) {
        Guidance guidance = resolveCurrentGuidance();
        KinematicStep.State state = KinematicStep.State.from(aircraftPosition, aircraftVector);
        KinematicStep.Targets targets = new KinematicStep.Targets(
                instructedVector.instructedAltitude.toDouble(),
                instructedVector.instructedGroundSpeed.toDouble(),
                instructedIndicatedAirspeedKnots);
        KinematicStep.Limits limits = new KinematicStep.Limits(
                characteristics.getMaxTurnRate(),
                characteristics.getMaxAcceleration(),
                characteristics.getMaxClimbRate());
        double previous = horizontalDistanceNm(
                state.latitude(), state.longitude(), thresholdLatitude, thresholdLongitude);
        double minimum = previous;
        int recedingSeconds = 0;
        double targetHeading = KinematicStep.resolveGuidanceHeading(
                guidance,
                state.latitude(),
                state.longitude(),
                state.altitudeFt(),
                state.headingDeg(),
                state.trueAirspeedKt(),
                ambientWind);
        for (int second = 0; second < TOWER_HANDOFF_TRACK_HORIZON_SECONDS; second++) {
            if (previous <= TOWER_HANDOFF_REMOVAL_NM) {
                return true;
            }
            // Re-resolve TrackCourse anchors (JOIN FINAL) from the simulated position each second.
            Guidance stepGuidance = guidance;
            if (guidance instanceof Guidance.TrackCourse track
                    && lateral.approachCourseHeadingOrNull() != null
                    && !lateral.approachCoursePending()) {
                stepGuidance = new Guidance.TrackCourse(
                        track.courseDeg(),
                        new FixPosition(new Latitude(state.latitude()), new Longitude(state.longitude())));
            }
            state = KinematicStep.next(
                    state, stepGuidance, ambientWind, targets, limits, TurnConstraint.SHORTEST, 1.0);
            targetHeading = KinematicStep.resolveGuidanceHeading(
                    stepGuidance,
                    state.latitude(),
                    state.longitude(),
                    state.altitudeFt(),
                    state.headingDeg(),
                    state.trueAirspeedKt(),
                    ambientWind);
            double distanceNm = horizontalDistanceNm(
                    state.latitude(), state.longitude(), thresholdLatitude, thresholdLongitude);
            minimum = Math.min(minimum, distanceNm);
            boolean aligned = smallestHeadingDelta(state.headingDeg(), targetHeading) < 1.0;
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
        if (!(lateral instanceof LateralNavigation.TowerHandoff handoff)) {
            return false;
        }
        double distanceNm = GeodeticUtils.distanceToFix(aircraftPosition, handoff.threshold());
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
        clearHoldingState();
        this.directToTarget = null;
        this.directToFixName = null;
        this.resumeFlightPlanAfterDirectTo = false;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        this.instructedVector = new InstructedVector(
                new Heading(runwayHeading),
                new Altitude(climbAltitudeFeet),
                this.instructedVector.instructedGroundSpeed);
        this.lateral = new LateralNavigation.GoAround();
    }

    /**
     * Place the aircraft at the catalog departure start (physical threshold / TORA begin)
     * at threshold elevation + 35 ft and climb on runway heading (FLY RUNWAY HEADING).
     * When {@code initialHeadingDeg} differs from the runway heading, runway heading is
     * held until DER elevation + 400 ft, then the deferred heading is applied.
     */
    public void beginInitialClimb(
            double startLatitude,
            double startLongitude,
            double startElevationFt,
            double runwayHeadingDeg,
            double derElevationFt,
            Double initialHeadingDeg,
            double climbAltitudeFeet) {
        requireApproachControl();
        double startAltitudeFt = InitialClimbGeometry.startAltitudeFt(startElevationFt);
        if (!InitialClimbGeometry.climbAltitudeValid(climbAltitudeFeet, startAltitudeFt)) {
            throw new IllegalArgumentException(
                    "Departure climb altitude must be at least 500 ft above the start altitude");
        }
        double runwayHeading = InitialClimbGeometry.normalizeHeading(runwayHeadingDeg);
        Double deferred = null;
        if (initialHeadingDeg != null
                && !InitialClimbGeometry.headingsEqual(initialHeadingDeg, runwayHeading)) {
            deferred = InitialClimbGeometry.normalizeHeading(initialHeadingDeg);
        }

        GroundSpeed instructedGs;
        if (instructedIndicatedAirspeedKnots != null) {
            instructedGs = this.instructedVector.instructedGroundSpeed;
        } else {
            instructedGs = new GroundSpeed(InitialClimbGeometry.DEFAULT_DEPARTURE_GROUND_SPEED_KT);
        }
        InstructedVector nextInstructed = new InstructedVector(
                new Heading(runwayHeading),
                new Altitude(climbAltitudeFeet),
                instructedGs);

        AircraftPosition nextPosition = new AircraftPosition(
                new Latitude(startLatitude),
                new Longitude(startLongitude),
                new Altitude(startAltitudeFt));

        double tasKt = instructedGs.toDouble();
        if (instructedIndicatedAirspeedKnots != null) {
            tasKt = IndicatedAirspeedConverter.indicatedToTrueAirspeedKnots(
                    instructedIndicatedAirspeedKnots, startAltitudeFt);
        } else if (ambientWind.affectsMotion()) {
            tasKt = WindTriangle.trueAirspeedForGroundSpeed(
                    runwayHeading, instructedGs.toDouble(), ambientWind);
        }

        WindTriangle.GroundVelocity groundVelocity =
                WindTriangle.groundVelocity(runwayHeading, tasKt, ambientWind);
        AircraftVector nextVector = new AircraftVector(
                new Heading(runwayHeading),
                new GroundSpeed(groundVelocity.groundSpeedKt()),
                new VerticalSpeed(0.0),
                groundVelocity.trackDeg(),
                tasKt);

        clearHoldingState();
        this.directToTarget = null;
        this.directToFixName = null;
        this.resumeFlightPlanAfterDirectTo = false;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        this.instructedVector = nextInstructed;
        this.aircraftPosition = nextPosition;
        this.aircraftVector = nextVector;
        this.lateral = new LateralNavigation.InitialClimb(
                startLatitude, startLongitude, runwayHeading, deferred, derElevationFt);
    }

    private void maybeReleaseInitialClimbTurn() {
        if (!(lateral instanceof LateralNavigation.InitialClimb climb)
                || climb.deferredInitialHeadingDeg() == null) {
            return;
        }
        if (aircraftPosition.altitude.toDouble()
                >= InitialClimbGeometry.turnReleaseAltitudeFt(climb.derElevationFt())) {
            this.instructedVector = new InstructedVector(
                    new Heading(climb.deferredInitialHeadingDeg()),
                    this.instructedVector.instructedAltitude,
                    this.instructedVector.instructedGroundSpeed);
            this.lateral = new LateralNavigation.InitialClimb(
                    climb.startLatitude(),
                    climb.startLongitude(),
                    climb.runwayHeadingDeg(),
                    null,
                    climb.derElevationFt());
        }
    }

    public void joinFinalApproachIntercept(FixPosition interceptPoint, String fixLabel, double approachCourseHeading) {
        requireApproachControl();
        this.directToTarget = interceptPoint;
        this.directToFixName = fixLabel;
        this.resumeFlightPlanAfterDirectTo = false;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        clearHoldingState();
        this.lateral = LateralNavigation.DirectToNav.joinFinal(approachCourseHeading);
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
        this.flightPlan = flightPlan;
        this.currentWaypointIndex = 0;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        this.procedureVerticalGuidance = false;
        this.unreachableAltitudeConstraintFixes.clear();
        clearHoldingState();
        this.lateral = flightPlan != null
                ? new LateralNavigation.FlightPlanNav()
                : new LateralNavigation.PlainHeading();
    }

    public List<String> getUnreachableAltitudeConstraintFixes() {
        return List.copyOf(unreachableAltitudeConstraintFixes);
    }

    public boolean isProcedureVerticalGuidance() {
        return procedureVerticalGuidance;
    }

    /**
     * Assigns a procedure-derived flight plan. When {@code va} is set, holds that true course
     * until the published AT_OR_ABOVE altitude is reached (or already met), then continues
     * on the flight-plan fixes.
     */
    public void assignProcedureRoute(FlightPlan flightPlan, ProcedureVaConstraint va) {
        requireApproachControl();
        if (flightPlan == null) {
            throw new IllegalArgumentException("flightPlan is required");
        }
        this.flightPlan = flightPlan;
        this.currentWaypointIndex = 0;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        this.procedureVerticalGuidance = true;
        this.unreachableAltitudeConstraintFixes.clear();
        clearHoldingState();
        this.directToTarget = null;
        this.directToFixName = null;
        this.resumeFlightPlanAfterDirectTo = false;
        if (va != null && !va.isSatisfiedByAltitudeFt(aircraftPosition.altitude.toDouble())) {
            double course = InitialClimbGeometry.normalizeHeading(va.courseTrueDeg());
            this.lateral = new LateralNavigation.ProcedureVa(course, va.atOrAboveAltitudeFt());
            refreshProcedureVaInstructedVector();
        } else {
            this.lateral = new LateralNavigation.FlightPlanNav();
        }
        // Seed unreachable flags from the assigned path at current performance.
        resolveTargetAltitude();
    }

    public boolean isProcedureVaActive() {
        return lateral.projectedProcedureVa();
    }

    public Double getProcedureVaCourseTrueDeg() {
        return lateral instanceof LateralNavigation.ProcedureVa va ? va.courseTrueDeg() : null;
    }

    public Integer getProcedureVaAltitudeFt() {
        return lateral instanceof LateralNavigation.ProcedureVa va ? va.atOrAboveAltitudeFt() : null;
    }

    public void setDirectTo(FixPosition target, String fixName, boolean resumeFlightPlan) {
        requireApproachControl();
        this.directToTarget = target;
        this.directToFixName = fixName;
        this.resumeFlightPlanAfterDirectTo = resumeFlightPlan;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        clearHoldingState();
        this.lateral = LateralNavigation.DirectToNav.plain();
    }

    public void setHoldAtFix(FixPosition fix, HoldInstruction instruction) {
        requireApproachControl();
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
        this.directToTarget = null;
        this.directToFixName = null;
        this.resumeFlightPlanAfterDirectTo = false;
        this.previousDistanceToWaypoint = Double.MAX_VALUE;
        this.lateral = new LateralNavigation.HoldingNav();
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
        if (!(lateral instanceof LateralNavigation.HoldingNav)) {
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
        if (!(lateral instanceof LateralNavigation.HoldingNav) || holdingFix == null) {
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
        this.directToTarget = null;
        this.directToFixName = null;
        this.resumeFlightPlanAfterDirectTo = false;
        if (lateral instanceof LateralNavigation.ProcedureVa) {
            // SID VA is own-nav: accept Resume, keep ProcedureVa (projection stays HEADING + VA).
            clearHoldingState();
            return;
        }
        if (flightPlan != null) {
            if (resumeFlightPlanIndexAfterHold >= flightPlan.getWaypoints().size()) {
                this.lateral = new LateralNavigation.PlainHeading();
                clearHoldingState();
                return;
            }
            if (resumeFlightPlanIndexAfterHold >= 0) {
                this.currentWaypointIndex = resumeFlightPlanIndexAfterHold;
            }
            this.lateral = new LateralNavigation.FlightPlanNav();
            clearHoldingState();
            return;
        }
        // No flight plan: still end the hold (and any prior HOLDING) so Resume always clears overlay.
        this.lateral = new LateralNavigation.PlainHeading();
        clearHoldingState();
    }

    private void refreshProcedureVaInstructedVector() {
        if (!(lateral instanceof LateralNavigation.ProcedureVa va)) {
            return;
        }
        Altitude climbTarget;
        if (procedureVerticalGuidance && flightPlan != null) {
            climbTarget = resolveTargetAltitude();
        } else {
            climbTarget = flightPlan != null
                    ? flightPlan.getCruiseAltitude()
                    : instructedVector.instructedAltitude;
        }
        if (climbTarget.toDouble() < va.atOrAboveAltitudeFt()) {
            climbTarget = new Altitude(va.atOrAboveAltitudeFt());
        }
        GroundSpeed speed = resolveTargetSpeed();
        this.instructedVector = new InstructedVector(
                new Heading(va.courseTrueDeg()), climbTarget, speed);
    }

    private void maybeCompleteProcedureVa() {
        if (!(lateral instanceof LateralNavigation.ProcedureVa va)) {
            return;
        }
        ProcedureVaConstraint constraint =
                new ProcedureVaConstraint(va.courseTrueDeg(), va.atOrAboveAltitudeFt());
        if (constraint.isSatisfiedByAltitudeFt(aircraftPosition.altitude.toDouble())) {
            this.lateral = flightPlan != null
                    ? new LateralNavigation.FlightPlanNav()
                    : new LateralNavigation.PlainHeading();
            this.previousDistanceToWaypoint = Double.MAX_VALUE;
        }
    }

    private void requireApproachControl() {
        if (lateral.projectedTowerHandoff()) {
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
        this.previousHoldAlongTrackNm = Double.NaN;
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

    public void setNavigationMode(NavigationMode mode) {
        requireApproachControl();
        this.lateral = switch (mode) {
            case HEADING -> new LateralNavigation.PlainHeading();
            case FLIGHT_PLAN -> new LateralNavigation.FlightPlanNav();
            case DIRECT_TO -> LateralNavigation.DirectToNav.plain();
            case HOLDING -> new LateralNavigation.HoldingNav();
        };
    }

    public FlightPlan getFlightPlan() {
        return flightPlan;
    }

    public NavigationMode getNavigationMode() {
        return lateral.projectedNavigationMode();
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
