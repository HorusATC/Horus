package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DepartRequestDto(
        @NotBlank String airportIcao,
        @NotBlank String runwayDesignator,
        @NotNull @Min(1000) @Max(45000) Integer climbAltitudeFt,
        @DecimalMin("0.0") @DecimalMax(value = "360.0", inclusive = false) Double initialHeadingDeg) {
}
