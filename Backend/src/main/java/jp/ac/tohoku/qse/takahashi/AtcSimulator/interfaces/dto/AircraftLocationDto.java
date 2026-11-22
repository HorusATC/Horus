package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON response DTO for aircraft location API.
 * Matches the structure expected by the frontend radar display.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AircraftLocationDto(
    String callsign,
    PositionDto position,
    VectorDto vector,
    InstructedVectorDto instructedVector,
    InstructedVectorDto atcClearance,
    String type,
    String model,
    String originIata,
    String originIcao,
    String destinationIata,
    String destinationIcao,
    String eta,
    @JsonInclude(JsonInclude.Include.ALWAYS) String squawk,
    String navigationMode,
    @JsonInclude(JsonInclude.Include.ALWAYS) boolean goAround,
    @JsonInclude(JsonInclude.Include.ALWAYS) boolean towerHandoff,
    double riskLevel,
    @JsonInclude(JsonInclude.Include.ALWAYS) MsawAlertDto msawAlert,
    /** Present only while {@code navigationMode} is HOLDING; omitted otherwise. */
    HoldingDto holding
) {
    public record PositionDto(double latitude, double longitude, double altitude) {}
    public record VectorDto(double heading, double groundSpeed, double verticalSpeed) {}
    public record InstructedVectorDto(
            double heading,
            double groundSpeed,
            double altitude,
            Integer indicatedAirspeed) {}
    public record MsawAlertDto(String type, double msaFt, String trigger) {}

    /**
     * Instructed holding racetrack from the single backend geometry definition.
     * {@code legMinutes} and {@code legNm} mirror the instruction (one may be null).
     * {@code entryProcedure} is predicted before the fix and frozen at first passage.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HoldingDto(
            String fixName,
            double fixLatitude,
            double fixLongitude,
            double inboundCourseDeg,
            String turnDirection,
            Double legMinutes,
            Double legNm,
            double outboundLegNm,
            double turnRateDegPerSec,
            double turnRadiusNm,
            List<PatternPointDto> pattern,
            String entryProcedure
    ) {}

    public record PatternPointDto(double latitude, double longitude) {}
}
