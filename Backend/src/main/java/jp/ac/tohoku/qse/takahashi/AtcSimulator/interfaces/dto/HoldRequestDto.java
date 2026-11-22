package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan.HoldTurnDirection;

import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HoldRequestDto(
        @NotBlank String fixName,
        String turnDirection,
        Double inboundCourseDeg,
        Double legMinutes,
        Double legNm
) {
    public static final double INBOUND_COURSE_MIN_INCLUSIVE = 0.0;
    public static final double INBOUND_COURSE_MAX_EXCLUSIVE = 360.0;

    public HoldTurnDirection resolvedTurnDirection() {
        if (turnDirection == null || turnDirection.isBlank()) {
            return HoldTurnDirection.RIGHT;
        }
        return HoldTurnDirection.valueOf(turnDirection.trim().toUpperCase());
    }

    /**
     * @return a clear 400 message, or empty when the request is valid for hold acceptance
     */
    public java.util.Optional<String> validationMessage() {
        if (inboundCourseDeg != null
                && (inboundCourseDeg < INBOUND_COURSE_MIN_INCLUSIVE
                        || inboundCourseDeg >= INBOUND_COURSE_MAX_EXCLUSIVE)) {
            return java.util.Optional.of(
                    "inboundCourseDeg must be ≥ 0 and < 360");
        }
        try {
            HoldLeg.fromRequest(legMinutes, legNm);
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.of(ex.getMessage());
        }
        return java.util.Optional.empty();
    }

    public HoldLeg resolvedLeg() {
        return HoldLeg.fromRequest(legMinutes, legNm);
    }
}
