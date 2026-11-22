package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WindDto(
        @Min(1) @Max(360) Integer directionDeg,
        @NotNull @Min(0) @Max(99) Integer speedKt) {
}
