package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

import java.util.List;
import java.util.Map;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.exception.AircraftNotFoundException;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.Aircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftRepository;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.types.commercial.CommercialAircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.FlightPlan;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.NavigationMode;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.conflict.ConflictDetector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw.MsawDetector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Conflict.RiskAssessment;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Msaw.MsawAlert;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.FixPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.AircraftLocationDto;

/**
 * Use case: get all aircraft locations with risk assessment for radar display.
 */
public class GetAllAircraftLocationsWithRiskUseCase {

    private final AircraftRepository aircraftRepository;
    private final ConflictAlertService conflictAlertService;
    private final MsawDetector msawDetector;

    public GetAllAircraftLocationsWithRiskUseCase(AircraftRepository aircraftRepository,
                                                  ConflictAlertService conflictAlertService,
                                                  MsawDetector msawDetector) {
        this.aircraftRepository = aircraftRepository;
        this.conflictAlertService = conflictAlertService;
        this.msawDetector = msawDetector;
    }

    public List<AircraftLocationDto> execute() {
        List<Aircraft> allAircraft = aircraftRepository.findAll();
        Map<ConflictDetector.ConflictPair, RiskAssessment> allConflicts = conflictAlertService.getAllConflictAlerts();

        return allAircraft.stream()
                .map(aircraft -> toDto(aircraft, allConflicts, allAircraft))
                .toList();
    }

    public AircraftLocationDto execute(String callsign) {
        if (!aircraftRepository.isAircraftExist(new Callsign(callsign))) {
            throw new AircraftNotFoundException(callsign);
        }
        return execute().stream()
                .filter(dto -> dto.callsign().equals(callsign))
                .findFirst()
                .orElseThrow();
    }

    private AircraftLocationDto toDto(Aircraft aircraft, Map<ConflictDetector.ConflictPair, RiskAssessment> allConflicts,
                                      List<Aircraft> allAircraft) {
        if (!(aircraft instanceof AircraftBase base)) {
            return fallbackDto(aircraft, 0.0, null);
        }

        double riskLevel = calculateMaxRiskForAircraft(
                base.getCallsign().toString(), allConflicts, allAircraft);

        MsawAlert msawAlert = msawDetector.detectAlert(aircraft);

        String originIata = "";
        String originIcao = "";
        String destinationIata = "";
        String destinationIcao = "";
        String eta = "";

        if (aircraft instanceof CommercialAircraft commercial) {
            originIata = commercial.getOriginIata();
            originIcao = commercial.getOriginIcao();
            destinationIata = commercial.getDestinationIata();
            destinationIcao = commercial.getDestinationIcao();
            eta = commercial.getEta();
        }

        return new AircraftLocationDto(
                base.getCallsign().toString(),
                new AircraftLocationDto.PositionDto(
                        base.getAircraftPosition().latitude.toDouble(),
                        base.getAircraftPosition().longitude.toDouble(),
                        base.getAircraftPosition().altitude.toDouble()
                ),
                new AircraftLocationDto.VectorDto(
                        base.getAircraftVector().heading.toDouble(),
                        base.getAircraftVector().groundSpeed.toDouble(),
                        base.getAircraftVector().verticalSpeed.toDouble(),
                        base.getAircraftVector().trackDeg
                ),
                new AircraftLocationDto.InstructedVectorDto(
                        base.getInstructedVector().instructedHeading.toDouble(),
                        base.getInstructedVector().instructedGroundSpeed.toDouble(),
                        base.getInstructedVector().instructedAltitude.toDouble(),
                        base.getInstructedIndicatedAirspeedKnots()
                ),
                toAtcClearanceDto(base),
                base.getCharacteristics().getCategory().name(),
                base.getAircraftType().toString(),
                originIata,
                originIcao,
                destinationIata,
                destinationIcao,
                eta,
                base.getSquawk(),
                base.getNavigationMode().name(),
                base.isGoAroundActive(),
                base.isInitialClimbActive(),
                base.isTowerHandoffActive(),
                riskLevel,
                toMsawAlertDto(msawAlert),
                toHoldingDto(base),
                toUnreachableAltitudeConstraints(base),
                toFlightPlanRoute(base)
        );
    }

    private AircraftLocationDto fallbackDto(Aircraft aircraft, double riskLevel, MsawAlert msawAlert) {
        return new AircraftLocationDto(
                aircraft.getCallsign().toString(),
                new AircraftLocationDto.PositionDto(0, 0, 0),
                new AircraftLocationDto.VectorDto(0, 0, 0, 0),
                new AircraftLocationDto.InstructedVectorDto(0, 0, 0, null),
                null,
                "UNKNOWN",
                "UNKNOWN",
                "", "", "", "", "", null, "UNKNOWN",
                false,
                false,
                false,
                riskLevel,
                toMsawAlertDto(msawAlert),
                null,
                null,
                null
        );
    }

    private static List<String> toUnreachableAltitudeConstraints(AircraftBase base) {
        List<String> fixes = base.getUnreachableAltitudeConstraintFixes();
        return fixes.isEmpty() ? null : fixes;
    }

    private static List<AircraftLocationDto.FlightPlanRoutePointDto> toFlightPlanRoute(AircraftBase base) {
        FlightPlan plan = base.getFlightPlan();
        if (plan == null || plan.getWaypoints().isEmpty()) {
            return null;
        }
        int start = Math.max(0, base.getCurrentWaypointIndex());
        List<AircraftLocationDto.FlightPlanRoutePointDto> route = plan.getWaypoints().stream()
                .skip(start)
                .map(wp -> new AircraftLocationDto.FlightPlanRoutePointDto(
                        wp.getFixName(),
                        wp.getPosition().latitude.toDouble(),
                        wp.getPosition().longitude.toDouble()))
                .toList();
        return route.isEmpty() ? null : route;
    }

    private static AircraftLocationDto.HoldingDto toHoldingDto(AircraftBase base) {
        if (base.getNavigationMode() != NavigationMode.HOLDING || base.getHoldingFix() == null) {
            return null;
        }
        FixPosition fix = base.getHoldingFix();
        HoldLeg leg = base.getHoldLeg();
        Double legMinutes = leg instanceof HoldLeg.TimedMinutes timed ? timed.minutes() : null;
        Double legNm = leg instanceof HoldLeg.DistanceNm distance ? distance.nauticalMiles() : null;
        List<AircraftLocationDto.PatternPointDto> pattern = base.getHoldingPatternVertices().stream()
                .map(p -> new AircraftLocationDto.PatternPointDto(
                        p.latitude.toDouble(), p.longitude.toDouble()))
                .toList();
        var entryProcedure = base.getHoldEntryProcedure();
        return new AircraftLocationDto.HoldingDto(
                base.getHoldingFixName() != null ? base.getHoldingFixName() : "",
                fix.latitude.toDouble(),
                fix.longitude.toDouble(),
                base.getHoldInboundCourseDeg(),
                base.getHoldTurnDirection().name(),
                legMinutes,
                legNm,
                base.getHoldFrozenOutboundLegNm(),
                base.getHoldFrozenTurnRateDegPerSec(),
                base.getHoldFrozenTurnRadiusNm(),
                pattern,
                entryProcedure != null ? entryProcedure.name() : null
        );
    }

    private double calculateMaxRiskForAircraft(String targetCallsign,
                                              Map<ConflictDetector.ConflictPair, RiskAssessment> allConflicts,
                                              List<Aircraft> allAircraft) {
        double maxRisk = 0.0;
        for (Aircraft other : allAircraft) {
            if (!other.getCallsign().toString().equals(targetCallsign)) {
                var pair = new ConflictDetector.ConflictPair(targetCallsign, other.getCallsign().toString());
                RiskAssessment risk = allConflicts.get(pair);
                if (risk != null && risk.getRiskLevel() > maxRisk) {
                    maxRisk = risk.getRiskLevel();
                }
            }
        }
        return maxRisk;
    }

    private static AircraftLocationDto.InstructedVectorDto toAtcClearanceDto(AircraftBase base) {
        if (!base.hasAtcClearance()) {
            return null;
        }
        var c = base.getAtcClearance();
        return new AircraftLocationDto.InstructedVectorDto(
                c.instructedHeading.toDouble(),
                c.instructedGroundSpeed.toDouble(),
                c.instructedAltitude.toDouble(),
                null
        );
    }

    private static AircraftLocationDto.MsawAlertDto toMsawAlertDto(MsawAlert alert) {
        if (alert == null) {
            return null;
        }
        return new AircraftLocationDto.MsawAlertDto(
                alert.type().name(),
                alert.msaFt(),
                alert.trigger().name()
        );
    }
}
